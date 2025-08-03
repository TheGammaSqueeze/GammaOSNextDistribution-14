#
# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""Updater for rr prebuilts."""
import argparse
import asyncio
import logging
from pathlib import Path

from rrprebuiltupdater import BuildApi, Updater

THIS_DIR = Path(__file__).parent
TOP = THIS_DIR.parent.parent
INSTALL_PATH = THIS_DIR / "rr/android/x86_64"


BRANCH = "aosp-rr-dev"
BUILD_TARGET = "linux"
ARTIFACT_NAME = "rr-5.6.0-Android-x86_64.tar.gz"


def parse_args() -> argparse.Namespace:
    """Returns arguments parsed from sys.argv."""
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--no-upload",
        dest="upload",
        action="store_false",
        default=True,
        help="Do not upload the generated CL",
    )
    parser.add_argument(
        "--use-current-branch",
        action="store_true",
        help=(
            "Use the current branch for the update. By default, a new branch will be "
            "created"
        ),
    )
    parser.add_argument("-v", "--verbose", action="count", help="Increase verbosity.")
    parser.add_argument(
        "build_id",
        nargs="?",
        help=(
            f"The build ID of the artifacts to fetch from {BRANCH}. If omitted, the "
            "latest completed build will be used."
        ),
    )
    return parser.parse_args()


async def main() -> None:
    """Program entry point."""
    args = parse_args()
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO)

    build_id = args.build_id
    if build_id is None:
        build_id = await BuildApi(BRANCH, BUILD_TARGET).get_latest_build_id()
        logging.info(
            "Determined %s to be the latest build ID of branch %s target %s",
            build_id,
            BRANCH,
            BUILD_TARGET,
        )
    await Updater(build_id, args.use_current_branch, args.upload).run()


if __name__ == "__main__":
    asyncio.run(main())
