#!/usr/bin/env python
#
# Copyright (C) 2016 The Android Open Source Project
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
import argparse
import logging
import os
from pathlib import Path
import shutil
import subprocess
from tempfile import TemporaryDirectory
import textwrap


THIS_DIR = Path(__file__).resolve().parent


def logger():
    return logging.getLogger(__name__)


def check_call(cmd):
    logger().debug("Running `%s`", " ".join(cmd))
    subprocess.check_call(cmd)


def remove(path):
    logger().debug("remove `%s`", path)
    os.remove(path)


def fetch_artifact(branch: str, build: str, pattern: str) -> None:
    """Fetches an artifact from the build server.

    Use OAuth2 authentication and the gLinux android-fetch-artifact package,
    which work with both on-corp and off-corp workstations."""
    fetch_artifact_path = shutil.which("fetch_artifact")
    if fetch_artifact_path is None:
        raise RuntimeError(
            "error: cannot find fetch_artifact in PATH. Install it using:\n"
            "  sudo glinux-add-repo android\n"
            "  sudo apt update\n"
            "  sudo apt install android-fetch-artifact\n"
        )
    cmd = [
        fetch_artifact_path,
        "--use_oauth2",
        "--branch",
        branch,
        "--target=linux",
        "--bid",
        build,
        pattern,
    ]
    check_call(cmd)


def api_str(api_level):
    return f"android-{api_level}"


def start_branch(build):
    branch_name = "update-" + (build or "latest")
    logger().info("Creating branch %s", branch_name)
    check_call(["repo", "start", branch_name, "."])


def remove_old_release(install_dir: Path) -> None:
    if (install_dir / ".git").exists():
        logger().info('Removing old install directory "%s"', install_dir)
        check_call(["git", "rm", "-rf", install_dir])

    # Need to check again because git won't remove directories if they have
    # non-git files in them.
    if install_dir.exists():
        shutil.rmtree(install_dir)


LIBUNWIND_GLOB = "toolchains/llvm/prebuilt/*/lib64/clang/*/lib/linux/*/libunwind.a"
LIBCXX_SHARED_GLOB = (
    "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/*/libc++_shared.so"
)
LIBCXX_STATIC_GLOB = (
    "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/*/libc++_static.a"
)
LIBCXXABI_GLOB = "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/*/libc++abi.a"
LIBANDROID_SUPPORT_GLOB = (
    "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/*/libandroid_support.a"
)


def unzip_single_directory(artifact: Path, destination: Path) -> None:
    # Use cwd so that we can use rename without having to worry about crossing
    # file systems.
    with TemporaryDirectory(dir=os.getcwd()) as temp_dir:
        cmd = [
            "unzip",
            str(artifact),
            "-d",
            temp_dir,
            "*/sources/android/cpufeatures/*",
            "*/sources/android/native_app_glue/*",
            "*/sources/android/support/*",
            "*/sources/cxx-stl/*",
            "*/source.properties",
            os.path.join("*", LIBUNWIND_GLOB),
            os.path.join("*", LIBCXX_SHARED_GLOB),
            os.path.join("*", LIBCXX_STATIC_GLOB),
            os.path.join("*", LIBCXXABI_GLOB),
            os.path.join("*", LIBANDROID_SUPPORT_GLOB),
        ]
        check_call(cmd)

        dirs = os.listdir(temp_dir)
        assert len(dirs) == 1
        ndk_dir = Path(temp_dir) / dirs[0]
        for child in ndk_dir.iterdir():
            child.rename(destination / child.name)


def relocate_libcxx(install_dir: Path) -> None:
    """Copies the libc++ libraries from the toolchain to sources.

    New versions of the NDK have removed the libraries in the sources directory because
    they are duplicates and they aren't needed in typical builds. Soong still expects to
    find them in that directory though. We could fix Soong, but since this whole
    directory should be dead soon we'll just fix-up the install for now.
    """
    dest_base = install_dir / "sources/cxx-stl/llvm-libc++/libs"
    for glob in {
        LIBCXX_SHARED_GLOB,
        LIBCXX_STATIC_GLOB,
        LIBCXXABI_GLOB,
        LIBANDROID_SUPPORT_GLOB,
    }:
        file_name = Path(glob).name
        for file_path in install_dir.glob(glob):
            triple = file_path.parent.name
            abi = {
                "arm-linux-androideabi": "armeabi-v7a",
                "aarch64-linux-android": "arm64-v8a",
                "i686-linux-android": "x86",
                "x86_64-linux-android": "x86_64",
            }[triple]
            dest_dir = dest_base / abi
            dest_dir.mkdir(parents=True, exist_ok=True)
            dest = dest_dir / file_name
            logger().info("Relocating %s to %s", file_path, dest)
            file_path.rename(dest)


def relocate_libunwind(install_dir: Path) -> None:
    dest_base = install_dir / "sources/cxx-stl/llvm-libc++/libs"
    for libunwind in install_dir.glob(LIBUNWIND_GLOB):
        arch = libunwind.parent.name
        abi = {
            "arm": "armeabi-v7a",
            "aarch64": "arm64-v8a",
            "i386": "x86",
            "x86_64": "x86_64",
        }[arch]
        dest_dir = dest_base / abi
        dest = dest_dir / "libunwind.a"
        logger().info("Relocating %s to %s", libunwind, dest)
        libunwind.rename(dest)


def delete_android_mks(install_dir: Path) -> None:
    for android_mk in install_dir.glob("**/Android.mk"):
        android_mk.unlink()


def install_new_release(branch: str, build: str, install_dir: Path) -> None:
    install_dir.mkdir()

    artifact_pattern = "android-ndk-*.zip"
    logger().info(
        "Fetching %s from %s (artifacts matching %s)", build, branch, artifact_pattern
    )
    fetch_artifact(branch, build, artifact_pattern)
    artifacts = list(Path().glob("android-ndk-*.zip"))
    try:
        assert len(artifacts) == 1
        artifact = artifacts[0]

        logger().info("Extracting release")
        unzip_single_directory(artifact, install_dir)
        relocate_libcxx(install_dir)
        relocate_libunwind(install_dir)
        delete_android_mks(install_dir)
    finally:
        for artifact in artifacts:
            artifact.unlink()


def commit(branch: str, build: str, install_dir: Path) -> None:
    logger().info("Making commit")
    check_call(["git", "add", str(install_dir)])
    message = textwrap.dedent(
        f"""\
        Update NDK prebuilts to build {build}.

        Taken from branch {branch}.

        Bug: None
        Test: treehugger
        """
    )
    check_call(["git", "commit", "-m", message])


def get_args():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-b", "--branch", default="master-ndk", help="Branch to pull build from."
    )
    parser.add_argument("--build", required=True, help="Build number to pull.")
    parser.add_argument(
        "--use-current-branch",
        action="store_true",
        help="Perform the update in the current branch. Do not repo start.",
    )
    parser.add_argument(
        "-v", "--verbose", action="count", default=0, help="Increase output verbosity."
    )
    return parser.parse_args()


def main() -> None:
    os.chdir(THIS_DIR)

    args = get_args()
    verbose_map = (logging.WARNING, logging.INFO, logging.DEBUG)
    verbosity = min(args.verbose, 2)
    logging.basicConfig(level=verbose_map[verbosity])

    install_dir = THIS_DIR / "current"

    if not args.use_current_branch:
        start_branch(args.build)
    remove_old_release(install_dir)
    install_new_release(args.branch, args.build, install_dir)
    commit(args.branch, args.build, install_dir)


if __name__ == "__main__":
    main()
