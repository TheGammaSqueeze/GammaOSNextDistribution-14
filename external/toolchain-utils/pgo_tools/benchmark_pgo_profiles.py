#!/usr/bin/env python3
# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Runs benchmarks, given potentially multiple PGO profiles.

**This script is meant to be run from inside of the chroot.**

This script overwrites your chroot's LLVM temporarily, but if it runs to
completion, it will restore you to your previous version of LLVM. Care is taken
so that the same baseline LLVM is used to build all LLVM versions this script
benchmarks.
"""

import argparse
import dataclasses
import enum
import json
import logging
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
from typing import IO, List, Optional, Union

import pgo_tools


# The full path to where `sys-devel/llvm` expects local profiles to be if
# `USE=llvm_pgo_use_local` is specified.
LOCAL_PROFILE_LOCATION = Path(
    "/mnt/host/source/src/third_party/chromiumos-overlay",
    "sys-devel/llvm/files/llvm-local.profdata",
).resolve()

CHROOT_HYPERFINE = Path.home() / ".cargo/bin/hyperfine"


class SpecialProfile(enum.Enum):
    """An enum representing a 'special' (non-Path) profile."""

    REMOTE = enum.auto()
    NONE = enum.auto()

    def __str__(self) -> str:
        if self is self.REMOTE:
            return "@remote"
        if self is self.NONE:
            return "@none"
        raise ValueError(f"Unknown SpecialProfile value: {repr(self)}")


@dataclasses.dataclass(frozen=True, eq=True)
class RunData:
    """Data describing the results of one hyperfine run."""

    tag: str
    user_time: float
    system_time: float

    @staticmethod
    def from_json(tag: str, json_contents: IO) -> "RunData":
        """Converts a hyperfine JSON file's contents into a RunData."""
        results = json.load(json_contents)["results"]
        if len(results) != 1:
            raise ValueError(f"Expected one run result; got {results}")
        return RunData(
            tag=tag,
            user_time=results[0]["user"],
            system_time=results[0]["system"],
        )


ProfilePath = Union[SpecialProfile, Path]


def parse_profile_path(path: str) -> ProfilePath:
    for p in SpecialProfile:
        if path == str(p):
            return p
    return Path(path).resolve()


def ensure_hyperfine_is_installed():
    if CHROOT_HYPERFINE.exists():
        return

    logging.info("Installing hyperfine for benchmarking...")
    pgo_tools.run(
        [
            "cargo",
            "install",
            "hyperfine",
        ]
    )
    assert (
        CHROOT_HYPERFINE.exists()
    ), f"hyperfine was installed, but wasn't at {CHROOT_HYPERFINE}"


def construct_hyperfine_cmd(
    llvm_ebuild: Path,
    profile: ProfilePath,
    llvm_binpkg: Path,
    use_thinlto: bool,
    export_json: Optional[Path] = None,
) -> pgo_tools.Command:
    if isinstance(profile, Path):
        if profile != LOCAL_PROFILE_LOCATION:
            shutil.copyfile(profile, LOCAL_PROFILE_LOCATION)
        use_flags = "-llvm_pgo_use llvm_pgo_use_local"
    elif profile is SpecialProfile.NONE:
        use_flags = "-llvm_pgo_use"
    elif profile is SpecialProfile.REMOTE:
        use_flags = "llvm_pgo_use"
    else:
        raise ValueError(f"Unknown profile type: {type(profile)}")

    quickpkg_restore = " ".join(
        shlex.quote(str(x))
        for x in pgo_tools.generate_quickpkg_restoration_command(llvm_binpkg)
    )

    setup_cmd = (
        f"{quickpkg_restore} && "
        f"sudo FEATURES=ccache USE={shlex.quote(use_flags)}"
        # Use buildpkg-exclude so our existing llvm binpackage isn't
        # overwritten.
        "  emerge sys-devel/llvm --buildpkg-exclude=sys-devel/llvm"
    )

    if use_thinlto:
        benchmark_use = "thinlto"
    else:
        benchmark_use = "-thinlto"

    ebuild_llvm = (
        f"sudo USE={shlex.quote(benchmark_use)} "
        f"ebuild {shlex.quote(str(llvm_ebuild))}"
    )
    cmd: pgo_tools.Command = [
        CHROOT_HYPERFINE,
        "--max-runs=3",
        f"--setup={setup_cmd}",
        f"--prepare={ebuild_llvm} clean prepare",
    ]

    if export_json:
        cmd.append(f"--export-json={export_json}")

    cmd += (
        "--",
        # At the moment, building LLVM seems to be an OK benchmark. It has some
        # C in it, some C++, and each pass on Cloudtops takes no more than 7
        # minutes.
        f"{ebuild_llvm} compile",
    )
    return cmd


def validate_profiles(
    parser: argparse.ArgumentParser, profiles: List[ProfilePath]
):
    number_of_path_profiles = 0
    nonexistent_profiles = []
    seen_profile_at_local_profile_location = False
    for profile in profiles:
        if not isinstance(profile, Path):
            continue

        if not profile.exists():
            nonexistent_profiles.append(profile)

        number_of_path_profiles += 1
        if profile == LOCAL_PROFILE_LOCATION:
            seen_profile_at_local_profile_location = True

    if number_of_path_profiles > 1 and seen_profile_at_local_profile_location:
        parser.error(
            f"Cannot use the path {LOCAL_PROFILE_LOCATION} as a profile if "
            "there are other profiles specified by path."
        )

    if nonexistent_profiles:
        nonexistent_profiles.sort()
        parser.error(
            "One or more profiles do not exist: " f"{nonexistent_profiles}"
        )


def run_benchmark(
    use_thinlto: bool,
    profiles: List[ProfilePath],
) -> List[RunData]:
    """Runs the PGO benchmark with the given parameters.

    Args:
        use_thinlto: whether to benchmark the use of ThinLTO
        profiles: profiles to benchmark with
        collect_run_data: whether to return a CombinedRunData

    Returns:
        A CombinedRunData instance capturing the performance of the benchmark
        runs.
    """
    ensure_hyperfine_is_installed()

    llvm_ebuild_path = Path(
        pgo_tools.run(
            ["equery", "w", "sys-devel/llvm"], stdout=subprocess.PIPE
        ).stdout.strip()
    )

    baseline_llvm_binpkg = pgo_tools.quickpkg_llvm()
    accumulated_run_data = []
    with pgo_tools.temporary_file(
        prefix="benchmark_pgo_profile"
    ) as tmp_json_file:
        for profile in profiles:
            cmd = construct_hyperfine_cmd(
                llvm_ebuild_path,
                profile,
                baseline_llvm_binpkg,
                use_thinlto=use_thinlto,
                export_json=tmp_json_file,
            )
            # Format the profile with `repr(str(profile))` so that we always
            # get a quoted, but human-friendly, representation of the profile.
            logging.info(
                "Profile %r: Running %s",
                str(profile),
                " ".join(shlex.quote(str(x)) for x in cmd),
            )
            pgo_tools.run(cmd)

            with tmp_json_file.open(encoding="utf-8") as f:
                accumulated_run_data.append(RunData.from_json(str(profile), f))

    logging.info("Restoring original LLVM...")
    pgo_tools.run(
        pgo_tools.generate_quickpkg_restoration_command(baseline_llvm_binpkg)
    )
    return accumulated_run_data


def main(argv: List[str]):
    logging.basicConfig(
        format=">> %(asctime)s: %(levelname)s: %(filename)s:%(lineno)d: "
        "%(message)s",
        level=logging.INFO,
    )

    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--thinlto",
        action="store_true",
        help="If specified, this will benchmark builds with ThinLTO enabled.",
    )
    parser.add_argument(
        "profile",
        nargs="+",
        type=parse_profile_path,
        help=f"""
        The path to a profile to benchmark. There are two special values here:
        '{SpecialProfile.REMOTE}' and '{SpecialProfile.NONE}'. For
        '{SpecialProfile.REMOTE}', this will just use the default LLVM PGO
        profile for a benchmark run. For '{SpecialProfile.NONE}', all PGO will
        be disabled for a benchmark run.
        """,
    )
    opts = parser.parse_args(argv)

    pgo_tools.exit_if_not_in_chroot()

    profiles = opts.profile
    validate_profiles(parser, profiles)

    run_benchmark(opts.thinlto, profiles)


if __name__ == "__main__":
    main(sys.argv[1:])
