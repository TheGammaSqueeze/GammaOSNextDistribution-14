#!/usr/bin/python3
#
# Copyright (C) 2022 The Android Open Source Project
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

import argparse
import contextlib
import json
import os


def _parser():
    """Return the argument parser"""
    # Top-level parser
    parser = argparse.ArgumentParser(prog=".inner_build")

    # --out-dir is always provided.
    parser.add_argument("--out_dir",
                        action="store",
                        required=True,
                        help="output directory path")

    sub = parser.add_subparsers(required=True,
                                dest="command",
                                help="subcommands")

    # inner_build describe command
    describe = sub.add_parser(
        "describe",
        help="describe the capabilities of this inner tree's build system")
    describe.add_argument('--input_json',
                          required=True,
                          help="The json encoded request information.")
    describe.add_argument('--output_json',
                          required=True,
                          help="The json encoded description.")

    # create the parser for the "export_api_contributions" command.
    export = sub.add_parser(
        "export_api_contributions",
        help="export the API contributions of this inner tree")
    export.add_argument(
        "--api_domain",
        action="append",
        required=True,
        help="which API domains are to be built in this inner tree")
    # This is not needed now that we have nsjail, since it launches inner_build
    # with cwd at the top of the inner tree.
    export.add_argument("--inner_tree",
                        action="store",
                        required=True,
                        help="path to the inner tree")

    # create the parser for the "analyze" command.
    analyze = sub.add_parser("analyze",
                             help="main build analysis for this inner tree")
    # TODO: do we need this, or does it just need to be mapped in nsjail?
    analyze.add_argument("--api_surfaces_dir",
                         action="append",
                         required=True,
                         help="the api_surfaces directory path")
    analyze.add_argument("--generate_ninja",
                         action="store_true",
                         help="generate ninja rules")
    # This is not needed now that we have nsjail, since it launches inner_build
    # with cwd at the top of the inner tree.
    analyze.add_argument("--inner_tree",
                         action="store",
                         required=True,
                         help="path to the inner tree")

    return parser


class Commands(object):
    """Base class for inner_build commands."""

    valid_commands = ("describe", "export_api_contributions", "analyze")

    def Run(self, argv):
        """Parse command arguments and call the named subcommand.

        Throws AttributeError if the method for the command wasn't found.
        """
        args = _parser().parse_args(argv[1:])
        if args.command not in self.valid_commands:
            raise Exception(f"invalid command: {args.command}")
        return getattr(self, args.command)(args)

    def describe(self, args):
        """Perform the default 'describe' processing."""

        with open(args.input_json, encoding='iso-8859-1') as f:
            query = json.load(f)

        # This version of describe() simply replies with the build_domains
        # requested.  If the inner tree can't build the requested build_domain,
        # then the build will fail later.  If the inner tree has knowledge of
        # what can be built, it should override this method with a method that
        # returns the appropriate information.
        # TODO: bazel-only builds will need to figure this out.
        domain_data = [{"domains": [query.get("build_domains", [])]}]
        reply = {"version": 0, "domain_data": domain_data}

        filename = args.output_json or os.path.join(args.out_dir,
                                                    "tree_info.json")
        os.makedirs(os.path.dirname(filename), exist_ok=True)
        with open(filename, "w", encoding='iso-8859-1') as f:
            json.dump(reply, f, indent=4)

    def export_api_contributions(self, args):
        raise Exception(f"export_api_contributions({args}) not implemented")

    def analyze(self, args):
        raise Exception(f"analyze({args}) not implemented")


@contextlib.contextmanager
def setenv(**kwargs):
    """Context to adjust environment."""
    old_values = {}
    delete_vars = set()
    for k, v in kwargs.items():
        # Save the prior state.
        if k in os.environ:
            old_values[k] = os.environ[k]
        else:
            delete_vars.add(k)

        # Set the new value.
        if v is None:
            del os.environ[k]
        else:
            os.environ[k] = v
    try:
        yield
    finally:
        # Restore the old values.
        for k, v in old_values.items():
            os.environ[k] = v
        for k in delete_vars:
            del os.environ[k]
