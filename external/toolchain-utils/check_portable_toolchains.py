#!/usr/bin/env python3
# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Verify that a given portable toolchain SDK version can link and compile.

Used to test that new portable toolchain SDKs work. See go/crostc-mage for
when to use this script.
"""

import argparse
import json
import logging
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from typing import List, Optional, Tuple


ABIS = (
    "aarch64-cros-linux-gnu",
    "armv7a-cros-linux-gnueabihf",
    "x86_64-cros-linux-gnu",
)

GS_PREFIX = "gs://staging-chromiumos-sdk"

# Type alias to make clear when a string is a specially
# formatted timestamp-version string.
Version = str

HELLO_WORLD = """#include <iostream>

int main() {
  std::cout << "Hello world!" << std::endl;
}
"""

_COLOR_RED = "\033[91m"
_COLOR_GREEN = "\033[92m"
_COLOR_RESET = "\033[0m"


def main() -> int:
    logging.basicConfig(
        format=">> %(asctime)s: %(levelname)s: %(filename)s:%(lineno)d: "
        "%(message)s",
        level=logging.INFO,
    )
    args = parse_args()

    version = args.version
    if not version:
        version = _autodetect_latest_llvm_next_sdk_version()

    errors: List[Tuple[str, Exception]] = []
    for abi in ABIS:
        res = check_abi(args.bucket_prefix, abi, version)
        if res:
            errors.append((abi, res))
    if errors:
        logging.error(
            "%sAt least one ABI failed to validate: %s%s",
            _COLOR_RED,
            ", ".join(abi for (abi, _) in errors),
            _COLOR_RESET,
        )
        return 1
    logging.info(
        "%sAll ABIs successfully validated :)%s",
        _COLOR_GREEN,
        _COLOR_RESET,
    )
    return 0


def check_abi(
    bucket_prefix: str, abi: str, version: Version
) -> Optional[Exception]:
    """Verify that a given ABI target triplet is okay."""
    year, month, _ = _split_version(version)
    toolchain_name = f"{abi}-{version}.tar.xz"
    artifact_path = f"{bucket_prefix}/{year}/{month}/{toolchain_name}"
    try:
        with tempfile.TemporaryDirectory() as tmpdir_str:
            tmpdir = Path(tmpdir_str)

            def run(*args, **kwargs):
                return subprocess.run(*args, check=True, cwd=tmpdir, **kwargs)

            logging.info(
                "Downloading the toolchain %s into %s",
                artifact_path,
                tmpdir,
            )
            run(["gsutil.py", "cp", artifact_path, tmpdir])

            logging.info("Extracting the toolchain %s", toolchain_name)
            run(["tar", "-axf", tmpdir / toolchain_name])

            logging.info("Checking if can find ld linker")
            proc = run(
                [f"bin/{abi}-clang", "-print-prog-name=ld"],
                stdout=subprocess.PIPE,
                encoding="utf-8",
            )
            linker_path = tmpdir / proc.stdout.strip()
            logging.info("linker binary path: %s", linker_path)
            if not linker_path.exists():
                raise RuntimeError(f"{linker_path} does not exist")
            if not os.access(linker_path, os.X_OK):
                raise RuntimeError(f"{linker_path} is not executable")

            logging.info("Building a simple c++ binary")
            hello_world_file = tmpdir / "hello_world.cc"
            hello_world_file.write_text(HELLO_WORLD, encoding="utf-8")
            hello_world_output = tmpdir / "hello_world"
            cmd = [
                f"bin/{abi}-clang++",
                "-o",
                hello_world_output,
                hello_world_file,
            ]
            run(cmd)
            if not hello_world_output.exists():
                raise RuntimeError(f"{hello_world_output} does not exist")
            proc = run(
                [f"bin/{abi}-clang++", "--version"],
                stdout=subprocess.PIPE,
                encoding="utf-8",
            )
            logging.info(
                "%s-clang++ --version:\n%s",
                abi,
                "> " + "\n> ".join(proc.stdout.strip().split("\n")),
            )

        logging.info(
            "%s[PASS] %s was validated%s", _COLOR_GREEN, abi, _COLOR_RESET
        )
    except Exception as e:
        logging.exception(
            "%s[FAIL] %s could not be validated%s",
            _COLOR_RED,
            abi,
            _COLOR_RESET,
        )
        return e
    return None


def _autodetect_latest_llvm_next_sdk_version() -> str:
    output = subprocess.run(
        [
            "bb",
            "ls",
            "-json",
            "-n",
            "1",
            "-status",
            "success",
            "chromeos/infra/build-chromiumos-sdk-llvm-next",
        ],
        check=True,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
    ).stdout
    builder_summary = json.loads(output)["summaryMarkdown"]
    # Builder summary looks like:
    # ```
    # Built SDK version [2023.12.11.140022](https://link-redacted)
    # Launched SDK uprev build: https://link-redacted
    # ```
    matches = re.findall(r"\[(\d+\.\d+\.\d+\.\d+)\]\(", builder_summary)
    if len(matches) != 1:
        raise ValueError(
            f"Expected exactly 1 match of version in {builder_summary!r}."
            f" Got {matches}. You can pass --version to disable auto-detection."
        )
    version = matches[0]
    logging.info("Found latest llvm-next SDK version: %s", version)
    return version


def _split_version(version: Version) -> Tuple[str, str, str]:
    y, m, rest = version.split(".", 2)
    return y, m, rest


def _verify_version(version: str) -> Version:
    _split_version(version)  # Raises a ValueError if invalid.
    return version


def parse_args() -> argparse.Namespace:
    """Parse arguments."""
    parser = argparse.ArgumentParser(
        "check_portable_toolchains", description=__doc__
    )
    parser.add_argument(
        "--version",
        help="""
        Version/Timestamp formatted as 'YYYY.MM.DD.HHMMSS'. e.g.
        '2023.09.01.221258'. Generally this comes from a
        'build-chromiumos-sdk-llvm-next' run. Will autodetect if none is
        specified.
        """,
        type=_verify_version,
    )
    parser.add_argument(
        "-p",
        "--bucket-prefix",
        default=GS_PREFIX,
        help="Top level gs:// path. (default: %(default)s)",
    )
    return parser.parse_args()


if __name__ == "__main__":
    sys.exit(main())
