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
r"""hostcleanup args.

Defines the hostcleanup arg parser.
"""

CMD_HOSTCLEANUP = "hostcleanup"


def GetHostcleanupArgParser(subparser):
    """Return the hostcleanup arg parser.

    Args:
        subparser: argparse.ArgumentParser that is attached to main acloud cmd.

    Returns:
        argparse.ArgumentParser with hostcleanup options defined.
    """
    hostcleanup_parser = subparser.add_parser(CMD_HOSTCLEANUP)
    hostcleanup_parser.required = False
    hostcleanup_parser.set_defaults(which=CMD_HOSTCLEANUP)
    hostcleanup_parser.add_argument(
        "--packages",
        action="store_true",
        dest="cleanup_pkgs",
        required=False,
        default=True,
        help="This feature will purge all packages installed by the acloud.")

    return hostcleanup_parser
