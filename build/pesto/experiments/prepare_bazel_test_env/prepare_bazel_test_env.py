# Copyright (C) 2021 The Android Open Source Project
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
"""Binary that generates a simulated Bazel environment in the Android source.

The script utilizes an internal repository of templates to determine the targets
to build, builds them via Soong, then imports them into a Bazel environment
all relying on templated BUILD files. These files can then be placed
directly within the Android source tree to simulate what a real Bazel
environment would look like.
"""
import argparse
import logging

import bazelenv

_LOG_PRINT_FORMAT = ("%(asctime)s %(filename)s:%(lineno)s:%(levelname)s: "
                     "%(message)s")
_LOG_DATE_FORMAT = "%Y-%m-%d %H:%M:%S"


def _configure_logging(verbose: bool) -> None:
    """Configures logging for the application.

  Args:
    verbose: if True, all messages are logged, otherwise only INFO and above
    are logged.
  """
    logging.basicConfig(format=_LOG_PRINT_FORMAT, datefmt=_LOG_DATE_FORMAT)
    level = logging.DEBUG if verbose else logging.INFO
    logging.root.setLevel(level)


def _create_arg_parser():
    parser = argparse.ArgumentParser(description=(
        "Prepares a simulated Bazel environment that can be used to "
        "execute tests in a Bazel environment based on Soong "
        "produced artifacts."))

    parser.add_argument("-v",
                        "--verbose",
                        help="Enables verbose logging.",
                        action="store_true")

    subparsers = parser.add_subparsers(dest="action", required=True)

    # For each subparser, provide a default 'func' argument that calls the
    # corresponding method on the generator instance.
    subparsers.add_parser(
        "generate",
        help="Generates the Bazel environment to the staging directory."
    ).set_defaults(func=lambda g: g.generate())

    subparsers.add_parser(
        "sync",
        help="Synchronizes the staged Bazel environment to the source tree."
    ).set_defaults(func=lambda g: g.sync())

    subparsers.add_parser(
        "clean",
        help=
        ("Cleans up the Bazel environment by clearing anything that has been "
         "synced to the source tree as well as the staging directory itself."
         )).set_defaults(func=lambda g: g.clean())

    return parser


if __name__ == "__main__":
    args = _create_arg_parser().parse_args()
    logging.debug("prepare_bazel_test_env(%s)", args)

    _configure_logging(args.verbose)

    try:
        generator = bazelenv.BazelTestEnvGenerator()
        args.func(generator)
    except bazelenv.Error:
        logging.exception(
            "A known error occurred, check the error description "
            "or logs for more details.")
