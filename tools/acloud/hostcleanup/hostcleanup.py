# Copyright 2021 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
r"""Hostcleanup entry point.

Hostcleanup will rollback acloud host setup steps.
"""
from acloud.hostcleanup import host_cleanup_runner

def Run(args):
    """Run Hostcleanup.

    Hostcleanup options:
        -cleanup_pkgs: Uninstall packages.

    Args:
        args: Namespace object from argparse.parse_args.
    """
    # TODO(b/145763747): Need to implement cleanup configs and usergroup.
    if args.cleanup_pkgs:
        host_cleanup_runner.PackagesUninstaller().Run()
