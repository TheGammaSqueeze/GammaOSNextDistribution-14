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
r"""Restart args.

Defines the restart arg parser that holds restart specific args.
"""
import argparse


CMD_RESTART = "restart"


def GetRestartArgParser(subparser):
    """Return the restart arg parser.

    Args:
       subparser: argparse.ArgumentParser that is attached to main acloud cmd.

    Returns:
        argparse.ArgumentParser with restart options defined.
    """
    restart_parser = subparser.add_parser(CMD_RESTART)
    restart_parser.required = False
    restart_parser.set_defaults(which=CMD_RESTART)
    restart_group = restart_parser.add_mutually_exclusive_group()
    restart_group.add_argument(
        "--instance-name",
        dest="instance_name",
        type=str,
        required=False,
        help="The name of the remote instance that need to restart the AVDs.")
    # TODO(b/118439885): Old arg formats to support transition, delete when
    # transistion is done.
    restart_group.add_argument(
        "--instance_name",
        dest="instance_name",
        type=str,
        required=False,
        help=argparse.SUPPRESS)
    restart_parser.add_argument(
        "--instance-id",
        dest="instance_id",
        type=int,
        required=False,
        default=1,
        help="The instance id of the remote instance that need to be restart.")
    restart_parser.add_argument(
        "--powerwash",
        dest="powerwash",
        action="store_true",
        required=False,
        help="Erase all userdata in the AVD.")

    return restart_parser
