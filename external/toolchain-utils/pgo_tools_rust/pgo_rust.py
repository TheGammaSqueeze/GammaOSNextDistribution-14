#!/usr/bin/env python3
#
# Copyright 2022 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# pylint: disable=line-too-long
"""Handle most aspects of creating and benchmarking PGO profiles for Rust.

This is meant to be done at Rust uprev time. Ultimately profdata files need
to be placed at

gs://chromeos-localmirror/distfiles/rust-pgo-{rust_version}-frontend.profdata{s}.tz
and
gs://chromeos-localmirror/distfiles/rust-pgo-{rust_version}-llvm.profdata{s}.tz

Here {s} is an optional suffix to distinguish between profdata files on the same
Rust version.

The intended flow is that you first get the new Rust version in a shape so that
it builds, for instance modifying or adding patches as necessary. Note that if
you need to generate manifests for dev-lang/rust and dev-lang/rust-host before
the profdata files are created, which will cause the `ebuild manifest` command
to fail. One way to handle this is to temporarily delete the lines of the
variable SRC_URI in cros-rustc.eclass which refer to profdata files.

After you have a new working Rust version, you can run the following.

```
$ ./pgo_rust.py generate         # generate profdata files
$ ./pgo_rust.py benchmark-pgo    # benchmark with PGO
$ ./pgo_rust.py benchmark-nopgo  # benchmark without PGO
$ ./pgo_rust.py upload-profdata  # upload profdata to localmirror
```

The benchmark steps aren't strictly necessary, but are recommended and will
upload benchmark data to

gs://chromeos-toolchain-artifacts/rust-pgo/benchmarks/{rust_version}/

Currently by default ripgrep 13.0.0 is used as both the crate to build using an
instrumented Rust while generating profdata, and the crate to build to
benchmark Rust. You may wish to experiment with other crates for either role.
In that case upload your crate to

gs://chromeos-toolchain-artifacts/rust-pgo/crates/{name}-{version}.tar.xz

and use `--crate-name` and `--crate-version` to indicate which crate to build
to generate profdata (or which crate's generated profdata to use), and
`--bench-crate-name` to indicate which crate to build in benchmarks.

Notes on various local and GS locations follow.

Note that currently we need to keep separate profdata files for the LLVM and
frontend components of Rust. This is because LLVM profdata is instrumented by
the system LLVM, but Rust's profdata is instrumented by its own LLVM, which
may have separate profdata.

profdata files accessed by ebuilds must be stored in

gs://chromeos-localmirror/distfiles

Specifically, they go to

gs://chromeos-localmirror/distfiles/rust-pgo-{rust-version}-llvm.profdata.xz

gs://chromeos-localmirror/distfiles/
  rust-pgo-{rust-version}-frontend.profdata.xz

But we can store other data elsewhere, like gs://chromeos-toolchain-artifacts.

GS locations:

{GS_BASE}/crates/ - store crates we may use for generating profiles or
benchmarking PGO optimized Rust compilers

{GS_BASE}/benchmarks/{rust_version}/nopgo/
  {bench_crate_name}-{bench_crate_version}-{triple}

{GS_BASE}/benchmarks/{rust_version}/{crate_name}-{crate_version}/
  {bench_crate_name}-{bench_crate_version}-{triple}

Local locations:

{LOCAL_BASE}/crates/

{LOCAL_BASE}/llvm-profraw/

{LOCAL_BASE}/frontend-profraw/

{LOCAL_BASE}/profdata/{crate_name}-{crate_version}/llvm.profdata

{LOCAL_BASE}/profdata/{crate_name}-{crate_version}/frontend.profdata

{LOCAL_BASE}/benchmarks/{rust_version}/nopgo/
  {bench_crate_name}-{bench_crate_version}-{triple}

{LOCAL_BASE}/benchmarks/{rust_version}/{crate_name}-{crate_version}/
  {bench_crate_name}-{bench_crate_version}-{triple}

{LOCAL_BASE}/llvm.profdata     - must go here to be used by Rust ebuild
{LOCAL_BASE}/frontend.profdata - must go here to be used by Rust ebuild
"""

import argparse
import contextlib
import logging
import os
from pathlib import Path
from pathlib import PurePosixPath
import re
import shutil
import subprocess
import sys
from typing import cast, List, Mapping, Optional


TARGET_TRIPLES = [
    "x86_64-cros-linux-gnu",
    "x86_64-pc-linux-gnu",
    "armv7a-cros-linux-gnueabihf",
    "aarch64-cros-linux-gnu",
]

LOCAL_BASE = Path("/tmp/rust-pgo")

GS_BASE = PurePosixPath("/chromeos-toolchain-artifacts/rust-pgo")

GS_DISTFILES = PurePosixPath("/chromeos-localmirror/distfiles")

CRATE_NAME = "ripgrep"

CRATE_VERSION = "13.0.0"


@contextlib.contextmanager
def chdir(new_directory: Path):
    initial_directory = Path.cwd()
    os.chdir(new_directory)
    try:
        yield
    finally:
        os.chdir(initial_directory)


def run(
    args: List,
    *,
    indent: int = 4,
    env: Optional[Mapping[str, str]] = None,
    capture_stdout: bool = False,
    message: bool = True,
) -> Optional[str]:
    args = [str(arg) for arg in args]

    if env is None:
        new_env: Mapping[str, str] = os.environ
    else:
        new_env = os.environ.copy()
        new_env.update(env)

    if message:
        if env is None:
            logging.info("Running %s", args)
        else:
            logging.info("Running %s in environment %s", args, env)

    result = subprocess.run(
        args,
        env=new_env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        encoding="utf-8",
        check=False,
    )

    stdout = result.stdout
    stderr = result.stderr
    if indent != 0:
        stdout = re.sub("^", " " * indent, stdout, flags=re.MULTILINE)
        stderr = re.sub("^", " " * indent, stderr, flags=re.MULTILINE)

    if capture_stdout:
        ret = result.stdout
    else:
        logging.info("STDOUT:")
        logging.info(stdout)
        logging.info("STDERR:")
        logging.info(stderr)
        ret = None

    result.check_returncode()

    if message:
        if env is None:
            logging.info("Ran %s\n", args)
        else:
            logging.info("Ran %s in environment %s\n", args, env)

    return ret


def get_command_output(args: List, **kwargs) -> str:
    """Runs a command and returns its stdout and stderr as a string."""
    return cast(str, run(args, capture_stdout=True, **kwargs))


def get_rust_version() -> str:
    s = get_command_output(["rustc", "--version"])
    m = re.search(r"\d+\.\d+\.\d+", s)
    assert m is not None, repr(s)
    return m.group(0)


def download_unpack_crate(*, crate_name: str, crate_version: str):
    filename_no_extension = f"{crate_name}-{crate_version}"
    gs_path = GS_BASE / "crates" / f"{filename_no_extension}.tar.xz"
    local_path = LOCAL_BASE / "crates"
    shutil.rmtree(
        local_path / f"{crate_name}-{crate_version}", ignore_errors=True
    )
    with chdir(local_path):
        run(["gsutil", "cp", f"gs:/{gs_path}", "."])
        run(["tar", "xaf", f"{filename_no_extension}.tar.xz"])


def build_crate(
    *,
    crate_name: str,
    crate_version: str,
    target_triple: str,
    time_file: Optional[str] = None,
):
    local_path = LOCAL_BASE / "crates" / f"{crate_name}-{crate_version}"
    with chdir(local_path):
        Path(".cargo").mkdir(exist_ok=True)
        with open(".cargo/config.toml", "w", encoding="utf-8") as f:
            f.write(
                "\n".join(
                    (
                        "[source.crates-io]",
                        'replace-with = "vendored-sources"',
                        "",
                        "[source.vendored-sources]",
                        'directory = "vendor"',
                        "",
                        f"[target.{target_triple}]",
                        f'linker = "{target_triple}-clang"',
                        "",
                        "[target.'cfg(all())']",
                        "rustflags = [",
                        '    "-Clto=thin",',
                        '    "-Cembed-bitcode=yes",',
                        "]",
                    )
                )
            )

        run(["cargo", "clean"])

        cargo_cmd = ["cargo", "build", "--release", "--target", target_triple]

        if time_file is None:
            run(cargo_cmd)
        else:
            time_cmd = [
                "/usr/bin/time",
                f"--output={time_file}",
                "--format=wall time (s) %e\nuser time (s) %U\nmax RSS %M\n",
            ]
            run(time_cmd + cargo_cmd)


def build_rust(
    *,
    generate_frontend_profile: bool = False,
    generate_llvm_profile: bool = False,
    use_frontend_profile: bool = False,
    use_llvm_profile: bool = False,
):

    if use_frontend_profile or use_llvm_profile:
        assert not generate_frontend_profile and not generate_llvm_profile, (
            "Can't build a compiler to both use profile information "
            "and generate it"
        )

    assert (
        not generate_frontend_profile or not generate_llvm_profile
    ), "Can't generate both frontend and LLVM profile information"

    use = "-rust_profile_frontend_use -rust_profile_llvm_use "
    if generate_frontend_profile:
        use += "rust_profile_frontend_generate "
    if generate_llvm_profile:
        use += "rust_profile_llvm_generate "
    if use_frontend_profile:
        use += "rust_profile_frontend_use_local "
    if use_llvm_profile:
        use += "rust_profile_llvm_use_local "

    env_use = os.getenv("USE", "").rstrip()
    use = (env_use + " " + use).strip()
    # -E to preserve environment variables like USE, FEATURES, etc.
    run(
        [
            "sudo",
            "-E",
            "emerge",
            "dev-lang/rust-host",
            "dev-lang/rust",
        ],
        env={"USE": use},
    )


def merge_profdata(llvm_or_frontend, *, source_directory: Path, dest: Path):
    assert llvm_or_frontend in ("llvm", "frontend")

    # The two `llvm-profdata` programs come from different LLVM versions, and
    # may support different versions of the profdata format, so make sure to
    # use the right one.
    llvm_profdata = (
        "/usr/bin/llvm-profdata"
        if llvm_or_frontend == "llvm"
        else "/usr/libexec/rust/llvm-profdata"
    )

    dest.parent.mkdir(parents=True, exist_ok=True)

    files = list(source_directory.glob("*.profraw"))
    assert files, f"No profraw files found in {source_directory}"
    run([llvm_profdata, "merge", f"--output={dest}"] + files)


def do_upload_profdata(*, source: Path, dest: PurePosixPath):
    new_path = source.parent / (source.name + ".xz")
    run(["xz", "--keep", "--compress", "--force", source])
    upload_file(source=new_path, dest=dest, public_read=True)


def upload_file(
    *, source: Path, dest: PurePosixPath, public_read: bool = False
):
    if public_read:
        run(["gsutil", "cp", "-a", "public-read", source, f"gs:/{dest}"])
    else:
        run(["gsutil", "cp", source, f"gs:/{dest}"])


def maybe_download_crate(*, crate_name: str, crate_version: str):
    """Downloads a crate if its download directory does not already exist."""
    directory = LOCAL_BASE / "crates" / f"{crate_name}-{crate_version}"
    if directory.is_dir():
        logging.info("Crate already downloaded")
    else:
        logging.info("Downloading crate")
        download_unpack_crate(
            crate_name=crate_name, crate_version=crate_version
        )


def generate(args):
    maybe_download_crate(
        crate_name=args.crate_name, crate_version=args.crate_version
    )

    llvm_dir = LOCAL_BASE / "llvm-profraw"
    shutil.rmtree(llvm_dir, ignore_errors=True)
    frontend_dir = LOCAL_BASE / "frontend-profraw"
    shutil.rmtree(frontend_dir, ignore_errors=True)

    logging.info("Building Rust instrumented for llvm")
    build_rust(generate_llvm_profile=True)

    llvm_dir.mkdir(parents=True, exist_ok=True)
    for triple in TARGET_TRIPLES:
        logging.info(
            "Building crate with LLVM instrumentation, for triple %s", triple
        )
        build_crate(
            crate_name=args.crate_name,
            crate_version=args.crate_version,
            target_triple=triple,
        )

    logging.info("Merging LLVM profile data")
    merge_profdata(
        "llvm",
        source_directory=LOCAL_BASE / "llvm-profraw",
        dest=(
            LOCAL_BASE
            / "profdata"
            / f"{args.crate_name}-{args.crate_version}"
            / "llvm.profdata"
        ),
    )

    logging.info("Building Rust instrumented for frontend")
    build_rust(generate_frontend_profile=True)

    frontend_dir.mkdir(parents=True, exist_ok=True)
    for triple in TARGET_TRIPLES:
        logging.info(
            "Building crate with frontend instrumentation, for triple %s",
            triple,
        )
        build_crate(
            crate_name=args.crate_name,
            crate_version=args.crate_version,
            target_triple=triple,
        )

    logging.info("Merging frontend profile data")
    merge_profdata(
        "frontend",
        source_directory=LOCAL_BASE / "frontend-profraw",
        dest=(
            LOCAL_BASE
            / "profdata"
            / f"{args.crate_name}-{args.crate_version}"
            / "frontend.profdata"
        ),
    )


def benchmark_nopgo(args):
    maybe_download_crate(
        crate_name=args.bench_crate_name, crate_version=args.bench_crate_version
    )

    logging.info("Building Rust, no PGO")
    build_rust()

    time_directory = LOCAL_BASE / "benchmarks" / "nopgo"
    logging.info("Benchmarking crate build with no PGO")
    time_directory.mkdir(parents=True, exist_ok=True)
    for triple in TARGET_TRIPLES:
        build_crate(
            crate_name=args.bench_crate_name,
            crate_version=args.bench_crate_version,
            target_triple=triple,
            time_file=(
                time_directory
                / f"{args.bench_crate_name}-{args.bench_crate_version}-{triple}"
            ),
        )

    rust_version = get_rust_version()
    dest_directory = (
        GS_BASE / "benchmarks" / rust_version / f"nopgo{args.suffix}"
    )
    logging.info("Uploading benchmark data")
    for file in time_directory.iterdir():
        upload_file(
            source=time_directory / file.name, dest=dest_directory / file.name
        )


def benchmark_pgo(args):
    maybe_download_crate(
        crate_name=args.bench_crate_name, crate_version=args.bench_crate_version
    )

    files_dir = Path(
        "/mnt/host/source/src/third_party/chromiumos-overlay",
        "dev-lang/rust/files",
    )

    logging.info("Copying profile data to be used in building Rust")
    run(
        [
            "cp",
            (
                LOCAL_BASE
                / "profdata"
                / f"{args.crate_name}-{args.crate_version}"
                / "llvm.profdata"
            ),
            files_dir,
        ]
    )
    run(
        [
            "cp",
            (
                LOCAL_BASE
                / "profdata"
                / f"{args.crate_name}-{args.crate_version}"
                / "frontend.profdata"
            ),
            files_dir,
        ]
    )

    logging.info("Building Rust with PGO")
    build_rust(use_llvm_profile=True, use_frontend_profile=True)

    time_directory = (
        LOCAL_BASE / "benchmarks" / f"{args.crate_name}-{args.crate_version}"
    )
    time_directory.mkdir(parents=True, exist_ok=True)
    logging.info("Benchmarking crate built with PGO")
    for triple in TARGET_TRIPLES:
        build_crate(
            crate_name=args.bench_crate_name,
            crate_version=args.bench_crate_version,
            target_triple=triple,
            time_file=(
                time_directory
                / f"{args.bench_crate_name}-{args.bench_crate_version}-{triple}"
            ),
        )

    rust_version = get_rust_version()
    dest_directory = (
        GS_BASE
        / "benchmarks"
        / rust_version
        / f"{args.crate_name}-{args.crate_version}{args.suffix}"
    )
    logging.info("Uploading benchmark data")
    for file in time_directory.iterdir():
        upload_file(
            source=time_directory / file.name, dest=dest_directory / file.name
        )


def upload_profdata(args):
    directory = (
        LOCAL_BASE / "profdata" / f"{args.crate_name}-{args.crate_version}"
    )
    rust_version = get_rust_version()

    logging.info("Uploading LLVM profdata")
    do_upload_profdata(
        source=directory / "llvm.profdata",
        dest=(
            GS_DISTFILES
            / f"rust-pgo-{rust_version}-llvm{args.suffix}.profdata.xz"
        ),
    )

    logging.info("Uploading frontend profdata")
    do_upload_profdata(
        source=directory / "frontend.profdata",
        dest=(
            GS_DISTFILES
            / f"rust-pgo-{rust_version}-frontend{args.suffix}.profdata.xz"
        ),
    )


def main(argv: List[str]) -> int:
    logging.basicConfig(
        stream=sys.stdout, level=logging.NOTSET, format="%(message)s"
    )

    parser = argparse.ArgumentParser(
        prog=argv[0],
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    subparsers = parser.add_subparsers(dest="command", help="")
    subparsers.required = True

    parser_generate = subparsers.add_parser(
        "generate",
        help="Generate LLVM and frontend profdata files by building "
        "instrumented Rust compilers, and using them to build the "
        "indicated crate (downloading the crate if necessary).",
    )
    parser_generate.set_defaults(func=generate)
    parser_generate.add_argument(
        "--crate-name", default=CRATE_NAME, help="Name of the crate to build"
    )
    parser_generate.add_argument(
        "--crate-version",
        default=CRATE_VERSION,
        help="Version of the crate to build",
    )

    parser_benchmark_nopgo = subparsers.add_parser(
        "benchmark-nopgo",
        help="Build the Rust compiler without PGO, benchmark "
        "the build of the indicated crate, and upload "
        "the benchmark data.",
    )
    parser_benchmark_nopgo.set_defaults(func=benchmark_nopgo)
    parser_benchmark_nopgo.add_argument(
        "--bench-crate-name",
        default=CRATE_NAME,
        help="Name of the crate whose build to benchmark",
    )
    parser_benchmark_nopgo.add_argument(
        "--bench-crate-version",
        default=CRATE_VERSION,
        help="Version of the crate whose benchmark to build",
    )
    parser_benchmark_nopgo.add_argument(
        "--suffix",
        default="",
        help="Suffix to distinguish benchmarks and profdata with identical "
        "rustc versions",
    )

    parser_benchmark_pgo = subparsers.add_parser(
        "benchmark-pgo",
        help="Build the Rust compiler using PGO with the indicated "
        "profdata files, benchmark the build of the indicated crate, "
        "and upload the benchmark data.",
    )
    parser_benchmark_pgo.set_defaults(func=benchmark_pgo)
    parser_benchmark_pgo.add_argument(
        "--bench-crate-name",
        default=CRATE_NAME,
        help="Name of the crate whose build to benchmark",
    )
    parser_benchmark_pgo.add_argument(
        "--bench-crate-version",
        default=CRATE_VERSION,
        help="Version of the crate whose benchmark to build",
    )
    parser_benchmark_pgo.add_argument(
        "--crate-name",
        default=CRATE_NAME,
        help="Name of the crate whose profile to use",
    )
    parser_benchmark_pgo.add_argument(
        "--crate-version",
        default=CRATE_VERSION,
        help="Version of the crate whose profile to use",
    )
    parser_benchmark_pgo.add_argument(
        "--suffix",
        default="",
        help="Suffix to distinguish benchmarks and profdata with identical "
        "rustc versions",
    )

    parser_upload_profdata = subparsers.add_parser(
        "upload-profdata", help="Upload the profdata files"
    )
    parser_upload_profdata.set_defaults(func=upload_profdata)
    parser_upload_profdata.add_argument(
        "--crate-name",
        default=CRATE_NAME,
        help="Name of the crate whose profile to use",
    )
    parser_upload_profdata.add_argument(
        "--crate-version",
        default=CRATE_VERSION,
        help="Version of the crate whose profile to use",
    )
    parser_upload_profdata.add_argument(
        "--suffix",
        default="",
        help="Suffix to distinguish benchmarks and profdata with identical "
        "rustc versions",
    )

    args = parser.parse_args(argv[1:])

    (LOCAL_BASE / "crates").mkdir(parents=True, exist_ok=True)
    (LOCAL_BASE / "llvm-profraw").mkdir(parents=True, exist_ok=True)
    (LOCAL_BASE / "frontend-profraw").mkdir(parents=True, exist_ok=True)
    (LOCAL_BASE / "benchmarks").mkdir(parents=True, exist_ok=True)

    args.func(args)

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
