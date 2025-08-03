# Copyright 2020 - The Android Open Source Project
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
r"""Powerwash args.

Defines the powerwash arg parser that holds powerwash specific args.
"""
import argparse


CMD_POWERWASH = "powerwash"


def GetPowerwashArgParser(subparser):
    """Return the powerwash arg parser.

    Args:
       subparser: argparse.ArgumentParser that is attached to main acloud cmd.

    Returns:
        argparse.ArgumentParser with powerwash options defined.
    """
    powerwash_parser = subparser.add_parser(CMD_POWERWASH)
    powerwash_parser.required = False
    powerwash_parser.set_defaults(which=CMD_POWERWASH)
    powerwash_group = powerwash_parser.add_mutually_exclusive_group()
    powerwash_group.add_argument(
        "--instance-name",
        dest="instance_name",
        type=str,
        required=False,
        help="The name of the remote instance that need to reset the AVDs.")
    # TODO(b/118439885): Old arg formats to support transition, delete when
    # transistion is done.
    powerwash_group.add_argument(
        "--instance_name",
        dest="instance_name",
        type=str,
        required=False,
        help=argparse.SUPPRESS)
    powerwash_parser.add_argument(
        "--instance-id",
        dest="instance_id",
        type=int,
        required=False,
        default=1,
        help="The instance id of the remote instance that need to be reset.")

    return powerwash_parser
