#!/usr/bin/env python3
# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Generates a PGO profile for LLVM.

**This script is meant to be run from inside of the chroot.**

Note that this script has a few (perhaps surprising) side-effects:
1. The first time this is run in a chroot, it will pack up your existing llvm
   and save it as a binpkg.
2. This script clobbers your llvm installation. If the script is run to
   completion, your old installation will be restored. If it does not, it may
   not be.
"""

import argparse
import dataclasses
import logging
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile
import textwrap
from typing import Dict, FrozenSet, List, Optional

import pgo_tools


# This script runs `quickpkg` on LLVM. This file saves the version of LLVM that
# was quickpkg'ed.
SAVED_LLVM_BINPKG_STAMP = Path("/tmp/generate_pgo_profile_old_llvm.txt")

# Triple to build with when not trying to get backend coverage.
HOST_TRIPLE = "x86_64-pc-linux-gnu"

# List of triples we want coverage for.
IMPORTANT_TRIPLES = (
    HOST_TRIPLE,
    "x86_64-cros-linux-gnu",
    "armv7a-cros-linux-gnueabihf",
    "aarch64-cros-linux-gnu",
)

# Set of all of the cross-* libraries we need.
ALL_NEEDED_CROSS_LIBS = frozenset(
    f"cross-{triple}/{package}"
    for triple in IMPORTANT_TRIPLES
    if triple != HOST_TRIPLE
    for package in ("glibc", "libcxx", "llvm-libunwind", "linux-headers")
)


def ensure_llvm_binpkg_exists() -> bool:
    """Verifies that we have an LLVM binpkg to fall back on.

    Returns:
        True if this function actually created a binpkg, false if one already
        existed.
    """
    if SAVED_LLVM_BINPKG_STAMP.exists():
        pkg = Path(SAVED_LLVM_BINPKG_STAMP.read_text(encoding="utf-8"))
        # Double-check this, since this package is considered a cache artifact
        # by portage. Ergo, it can _technically_ be GC'ed at any time.
        if pkg.exists():
            return False

    pkg = pgo_tools.quickpkg_llvm()
    SAVED_LLVM_BINPKG_STAMP.write_text(str(pkg), encoding="utf-8")
    return True


def restore_llvm_binpkg():
    """Installs the binpkg created by ensure_llvm_binpkg_exists."""
    logging.info("Restoring non-PGO'ed LLVM installation")
    pkg = Path(SAVED_LLVM_BINPKG_STAMP.read_text(encoding="utf-8"))
    assert (
        pkg.exists()
    ), f"Non-PGO'ed binpkg at {pkg} does not exist. Can't restore"
    pgo_tools.run(pgo_tools.generate_quickpkg_restoration_command(pkg))


def find_missing_cross_libs() -> FrozenSet[str]:
    """Returns cross-* libraries that need to be installed for workloads."""
    equery_result = pgo_tools.run(
        ["equery", "l", "--format=$cp", "cross-*/*"],
        check=False,
        stdout=subprocess.PIPE,
    )

    # If no matching package is found, equery will exit with code 3.
    if equery_result.returncode == 3:
        return ALL_NEEDED_CROSS_LIBS

    equery_result.check_returncode()
    has_packages = {x.strip() for x in equery_result.stdout.splitlines()}
    return ALL_NEEDED_CROSS_LIBS - has_packages


def ensure_cross_libs_are_installed():
    """Ensures that we have cross-* libs for all `IMPORTANT_TRIPLES`."""
    missing_packages = find_missing_cross_libs()
    if not missing_packages:
        logging.info("All cross-compiler libraries are already installed")
        return

    missing_packages = sorted(missing_packages)
    logging.info("Installing cross-compiler libs: %s", missing_packages)
    pgo_tools.run(
        ["sudo", "emerge", "-j", "-G"] + missing_packages,
    )


def emerge_pgo_generate_llvm():
    """Emerges a sys-devel/llvm with PGO instrumentation enabled."""
    force_use = (
        "llvm_pgo_generate -llvm_pgo_use"
        # Turn ThinLTO off, since doing so results in way faster builds.
        # This is assumed to be OK, since:
        #   - ThinLTO should have no significant impact on where Clang puts
        #     instrprof counters.
        #   - In practice, both "PGO generated with ThinLTO enabled," and "PGO
        #     generated without ThinLTO enabled," were benchmarked, and the
        #     performance difference between the two was in the noise.
        " -thinlto"
        # Turn ccache off, since if there are valid ccache artifacts from prior
        # runs of this script, ccache will lead to us not getting profdata from
        # those.
        " -wrapper_ccache"
    )
    use = (os.environ.get("USE", "") + " " + force_use).strip()

    # Use FEATURES=ccache since it's not much of a CPU time penalty, and if a
    # user runs this script repeatedly, they'll appreciate it. :)
    force_features = "ccache"
    features = (os.environ.get("FEATURES", "") + " " + force_features).strip()
    logging.info("Building LLVM with USE=%s", shlex.quote(use))
    pgo_tools.run(
        [
            "sudo",
            f"FEATURES={features}",
            f"USE={use}",
            "emerge",
            "sys-devel/llvm",
        ]
    )


def build_profiling_env(profile_dir: Path) -> Dict[str, str]:
    profile_pattern = str(profile_dir / "profile-%m.profraw")
    return {
        "LLVM_PROFILE_OUTPUT_FORMAT": "profraw",
        "LLVM_PROFILE_FILE": profile_pattern,
    }


def ensure_clang_invocations_generate_profiles(clang_bin: str, tmpdir: Path):
    """Raises an exception if clang doesn't generate profraw files.

    Args:
        clang_bin: the path to a clang binary.
        tmpdir: a place where this function can put temporary files.
    """
    tmpdir = tmpdir / "ensure_profiles_generated"
    tmpdir.mkdir(parents=True)
    pgo_tools.run(
        [clang_bin, "--help"],
        extra_env=build_profiling_env(tmpdir),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    is_empty = next(tmpdir.iterdir(), None) is None
    if is_empty:
        raise ValueError(
            f"The clang binary at {clang_bin} generated no profile"
        )
    shutil.rmtree(tmpdir)


def write_unified_cmake_file(
    into_dir: Path, absl_subdir: Path, gtest_subdir: Path
):
    (into_dir / "CMakeLists.txt").write_text(
        textwrap.dedent(
            f"""\
            cmake_minimum_required(VERSION 3.10)

            project(generate_pgo)

            add_subdirectory({gtest_subdir})
            add_subdirectory({absl_subdir})"""
        ),
        encoding="utf-8",
    )


def fetch_workloads_into(target_dir: Path):
    """Fetches PGO generation workloads into `target_dir`."""
    # The workload here is absl and gtest. The reasoning behind that selection
    # was essentially a mix of:
    # - absl is reasonably-written and self-contained
    # - gtest is needed if tests are to be built; in order to have absl do much
    #   of any linking, gtest is necessary.
    #
    # Use the version of absl that's bundled with ChromeOS at the time of
    # writing.
    target_dir.mkdir(parents=True)

    def fetch_and_extract(gs_url: str, into_dir: Path):
        tgz_full = target_dir / os.path.basename(gs_url)
        pgo_tools.run(
            [
                "gsutil",
                "cp",
                gs_url,
                tgz_full,
            ],
        )
        into_dir.mkdir()

        pgo_tools.run(
            ["tar", "xaf", tgz_full],
            cwd=into_dir,
        )

    absl_dir = target_dir / "absl"
    fetch_and_extract(
        gs_url="gs://chromeos-localmirror/distfiles/"
        "abseil-cpp-a86bb8a97e38bc1361289a786410c0eb5824099c.tar.bz2",
        into_dir=absl_dir,
    )

    gtest_dir = target_dir / "gtest"
    fetch_and_extract(
        gs_url="gs://chromeos-mirror/gentoo/distfiles/"
        "gtest-1b18723e874b256c1e39378c6774a90701d70f7a.tar.gz",
        into_dir=gtest_dir,
    )

    unpacked_absl_dir = read_exactly_one_dirent(absl_dir)
    unpacked_gtest_dir = read_exactly_one_dirent(gtest_dir)
    write_unified_cmake_file(
        into_dir=target_dir,
        absl_subdir=unpacked_absl_dir.relative_to(target_dir),
        gtest_subdir=unpacked_gtest_dir.relative_to(target_dir),
    )


@dataclasses.dataclass(frozen=True)
class WorkloadRunner:
    """Runs benchmark workloads."""

    profraw_dir: Path
    target_dir: Path
    out_dir: Path

    def run(
        self,
        triple: str,
        extra_cflags: Optional[str] = None,
        sysroot: Optional[str] = None,
    ):
        logging.info(
            "Running workload for triple %s, extra cflags %r",
            triple,
            extra_cflags,
        )
        if self.out_dir.exists():
            shutil.rmtree(self.out_dir)
        self.out_dir.mkdir(parents=True)

        clang = triple + "-clang"
        profiling_env = build_profiling_env(self.profraw_dir)
        if sysroot:
            profiling_env["SYSROOT"] = sysroot

        cmake_command: pgo_tools.Command = [
            "cmake",
            "-G",
            "Ninja",
            "-DCMAKE_BUILD_TYPE=RelWithDebInfo",
            f"-DCMAKE_C_COMPILER={clang}",
            f"-DCMAKE_CXX_COMPILER={clang}++",
            "-DABSL_BUILD_TESTING=ON",
            "-DABSL_USE_EXTERNAL_GOOGLETEST=ON",
            "-DABSL_USE_GOOGLETEST_HEAD=OFF",
            "-DABSL_FIND_GOOGLETEST=OFF",
        ]

        if extra_cflags:
            cmake_command += (
                f"-DCMAKE_C_FLAGS={extra_cflags}",
                f"-DCMAKE_CXX_FLAGS={extra_cflags}",
            )

        cmake_command.append(self.target_dir)
        pgo_tools.run(
            cmake_command,
            extra_env=profiling_env,
            cwd=self.out_dir,
        )

        pgo_tools.run(
            ["ninja", "-v", "all"],
            extra_env=profiling_env,
            cwd=self.out_dir,
        )


def read_exactly_one_dirent(directory: Path) -> Path:
    """Returns the single Path under the given directory. Raises otherwise."""
    ents = directory.iterdir()
    ent = next(ents, None)
    if ent is not None:
        if next(ents, None) is None:
            return ent
    raise ValueError(f"Expected exactly one entry under {directory}")


def run_workloads(target_dir: Path) -> Path:
    """Runs all of our workloads in target_dir.

    Args:
        target_dir: a directory that already had `fetch_workloads_into` called
            on it.

    Returns:
        A directory in which profraw files from running the workloads are
        saved.
    """
    profraw_dir = target_dir / "profiles"
    profraw_dir.mkdir()

    out_dir = target_dir / "out"
    runner = WorkloadRunner(
        profraw_dir=profraw_dir,
        target_dir=target_dir,
        out_dir=out_dir,
    )

    # Run the workload once per triple.
    for triple in IMPORTANT_TRIPLES:
        runner.run(
            triple, sysroot=None if triple == HOST_TRIPLE else f"/usr/{triple}"
        )

    # Add a run of ThinLTO, so any ThinLTO-specific lld bits get exercised.
    # Also, since CrOS uses -Os often, exercise that.
    runner.run(HOST_TRIPLE, extra_cflags="-flto=thin -Os")
    return profraw_dir


def convert_profraw_to_pgo_profile(profraw_dir: Path) -> Path:
    """Creates a PGO profile from the profraw profiles in profraw_dir."""
    output = profraw_dir / "merged.prof"
    profile_files = list(profraw_dir.glob("profile-*profraw"))
    if not profile_files:
        raise ValueError("No profraw files generated?")

    logging.info(
        "Creating a PGO profile from %d profraw files", len(profile_files)
    )
    generate_command = [
        "llvm-profdata",
        "merge",
        "--instr",
        f"--output={output}",
    ]
    pgo_tools.run(generate_command + profile_files)
    return output


def main(argv: List[str]):
    logging.basicConfig(
        format=">> %(asctime)s: %(levelname)s: %(filename)s:%(lineno)d: "
        "%(message)s",
        level=logging.DEBUG,
    )

    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--output",
        required=True,
        type=Path,
        help="Where to put the PGO profile",
    )
    parser.add_argument(
        "--use-old-binpkg",
        action="store_true",
        help="""
        This script saves your initial LLVM installation as a binpkg, so it may
        restore that installation later in the build. Passing --use-old-binpkg
        allows this script to use a binpkg from a prior invocation of this
        script.
        """,
    )
    opts = parser.parse_args(argv)

    pgo_tools.exit_if_not_in_chroot()

    output = opts.output

    llvm_binpkg_is_fresh = ensure_llvm_binpkg_exists()
    if not llvm_binpkg_is_fresh and not opts.use_old_binpkg:
        sys.exit(
            textwrap.dedent(
                f"""\
                A LLVM binpkg packed by a previous run of this script is
                available. If you intend this run to be another attempt at the
                previous run, please pass --use-old-binpkg (so the old LLVM
                binpkg is used as our 'baseline'). If you don't, please remove
                the file referring to it at {SAVED_LLVM_BINPKG_STAMP}.
                """
            )
        )

    logging.info("Ensuring `cross-` libraries are installed")
    ensure_cross_libs_are_installed()
    tempdir = Path(tempfile.mkdtemp(prefix="generate_llvm_pgo_profile_"))
    try:
        workloads_path = tempdir / "workloads"
        logging.info("Fetching workloads")
        fetch_workloads_into(workloads_path)

        # If our binpkg is not fresh, we may be operating with a weird LLVM
        # (e.g., a PGO'ed one ;) ). Ensure we always start with that binpkg as
        # our baseline.
        if not llvm_binpkg_is_fresh:
            restore_llvm_binpkg()

        logging.info("Building PGO instrumented LLVM")
        emerge_pgo_generate_llvm()

        logging.info("Ensuring instrumented compilers generate profiles")
        for triple in IMPORTANT_TRIPLES:
            ensure_clang_invocations_generate_profiles(
                triple + "-clang", tempdir
            )

        logging.info("Running workloads")
        profraw_dir = run_workloads(workloads_path)

        # This is a subtle but critical step. The LLVM we're currently working
        # with was built by the LLVM represented _by our binpkg_, which may be
        # a radically different version of LLVM than what was installed (e.g.,
        # it could be from our bootstrap SDK, which could be many months old).
        #
        # If our current LLVM's llvm-profdata is used to interpret the profraw
        # files:
        # 1. The profile generated will be for our new version of clang, and
        #    may therefore be too new for the older version that we still have
        #    to support.
        # 2. There may be silent incompatibilities, as the stability guarantees
        #    of profraw files are not immediately apparent.
        logging.info("Restoring LLVM's binpkg")
        restore_llvm_binpkg()
        pgo_profile = convert_profraw_to_pgo_profile(profraw_dir)
        shutil.copyfile(pgo_profile, output)
    except:
        # Leave the tempdir, as it might help people debug.
        logging.info("NOTE: Tempdir will remain at %s", tempdir)
        raise

    logging.info("Removing now-obsolete tempdir")
    shutil.rmtree(tempdir)
    logging.info("PGO profile is available at %s.", output)


if __name__ == "__main__":
    main(sys.argv[1:])
