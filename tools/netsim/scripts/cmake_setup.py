#!/usr/bin/env python3
#
# Copyright 2024 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the',  help="License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an',  help="AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
from __future__ import absolute_import, division, print_function

import argparse
import os
from pathlib import Path
import platform
import shutil

from environment import get_default_environment
from utils import (
    AOSP_ROOT,
    cmake_toolchain,
    config_logging,
    log_system_info,
    run,
)


def main():
  config_logging()
  log_system_info()

  parser = argparse.ArgumentParser(
      description=(
          "Configures the android netsim cmake project so it can be build"
      )
  )
  parser.add_argument(
      "--out_dir",
      type=str,
      default=Path("objs").absolute(),
      help="The output directory",
  )

  parser.add_argument(
      "--target",
      type=str,
      default=platform.system(),
      help="The build target, defaults to current os",
  )
  parser.add_argument(
      "--enable_system_rust",
      action="store_true",
      help="Build the netsim with the System Rust on the host machine",
  )
  parser.add_argument(
      "--with_debug", action="store_true", help="Build debug instead of release"
  )

  args = parser.parse_args()

  os.environ["GIT_DISCOVERY_ACROSS_FILESYSTEM"] = "1"
  os.environ["CMAKE_EXPORT_COMPILE_COMMANDS"] = "1"

  target = platform.system().lower()

  if args.target:
    target = args.target.lower()

  if not os.path.isabs(args.out_dir):
    args.out_dir = os.path.join(AOSP_ROOT, args.out_dir)

  out = Path(args.out_dir)
  if out.exists():
    shutil.rmtree(out)
  out.mkdir(exist_ok=True, parents=True)

  cmake = shutil.which(
      "cmake",
      path=str(
          AOSP_ROOT
          / "prebuilts"
          / "cmake"
          / f"{platform.system().lower()}-x86"
          / "bin"
      ),
  )
  launcher = [
      cmake,
      f"-B{out}",
      "-G Ninja",
      f"-DCMAKE_TOOLCHAIN_FILE={cmake_toolchain(target)}",
      AOSP_ROOT / "tools" / "netsim",
  ]

  # Configure
  run(launcher, get_default_environment(AOSP_ROOT), "bld")


if __name__ == "__main__":
  main()
