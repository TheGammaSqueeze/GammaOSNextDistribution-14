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
import os
import subprocess
import sys

import api_assembly
import api_domain
import api_export
import final_packaging
import inner_tree
import tree_analysis
import interrogate
import lunch
import ninja_runner
import nsjail
import utils

EXIT_STATUS_OK = 0
EXIT_STATUS_ERROR = 1

API_DOMAIN_SYSTEM = "system"
API_DOMAIN_VENDOR = "vendor"
API_DOMAIN_MODULE = "module"


class Orchestrator():

    def __init__(self, argv):
        """Initialize the object."""
        self.argv = argv
        self.opts = self._get_parser().parse_args(argv[1:])
        self._validate_options()

        # Choose the out directory, set up error handling, etc.
        self.context = utils.Context(utils.choose_out_dir(),
                                     utils.Errors(sys.stderr))

        # Read the lunch config file
        config_file, config, variant = lunch.load_current_config()
        sys.stdout.write(lunch.make_config_header(config_file, config,
                                                  variant))

        self.config_file = config_file
        self.config = config
        self.variant = variant

        # Construct the trees and domains dicts.
        self.inner_trees = self.process_config(self.config)

    def _get_parser(self):
        """Return the argument parser."""
        p = argparse.ArgumentParser()
        p.add_argument("--shell",
                       action="store_true",
                       help="invoke a shell instead of building")
        # Mount source tree read-write.
        p.add_argument("--debug", action="store_true", help=argparse.SUPPRESS)

        p.add_argument('targets',
                       metavar="TARGET",
                       nargs="*",
                       help="ninja targets")

        return p

    def _validate_options(self):
        """Validate the options provided by the user."""
        # Debug includes shell.
        if self.opts.debug:
            self.opts.shell = True

    def process_config(self, lunch_config: dict) -> inner_tree.InnerTrees:
        """Process the config file.

        Args:
          lunch_config: JSON encoded config from the mcombo file.

        Returns:
          The inner_trees definition for this build.
        """

        trees = {}
        domains = {}
        context = self.context

        def add(domain_name, tree_root, product):
            tree_key = inner_tree.InnerTreeKey(tree_root, product)
            if tree_key in trees:
                tree = trees[tree_key]
            else:
                tree = inner_tree.InnerTree(context, tree_root, product,
                                            self.variant)
                trees[tree_key] = tree
            domain = api_domain.ApiDomain(domain_name, tree, product)
            domains[domain_name] = domain
            tree.domains[domain_name] = domain

        system_entry = lunch_config.get("system")
        if system_entry:
            add(API_DOMAIN_SYSTEM, system_entry["inner-tree"],
                system_entry["product"])

        vendor_entry = lunch_config.get("vendor")
        if vendor_entry:
            add(API_DOMAIN_VENDOR, vendor_entry["inner-tree"],
                vendor_entry["product"])

        for name, entry in lunch_config.get("modules", {}).items():
            add(name, entry["inner-tree"], None)

        return inner_tree.InnerTrees(trees, domains)

    def _create_nsjail_config(self):
        """Create the nsjail config."""
        # The filesystem layout for the nsjail has binaries, libraries, and such
        # where we expect them to be.  Outside of that, we mount the workspace
        # (which is presumably the current working directory thanks to lunch),
        # and those are the only files present in the tree.
        #
        # The orchestrator needs to have the outer tree as the top of the tree,
        # with all of the inner tree nsjail configs merged with it, so that we
        # can do one ninja run this step.  The source workspace is mounted
        # read-only, with the out_dir mounted read-write.
        root = os.path.abspath('.')
        jail_cfg = nsjail.Nsjail(root)
        jail_cfg.add_mountpt(src=root,
                             dst=root,
                             is_bind=True,
                             rw=False,
                             mandatory=True)
        # Add the outer-tree outdir (which may be unrelated to the workspace
        # root). The inner-trees will additionally mount their outdir under as
        # {inner_tree}/out, so that all access to the out_dir in a subninja
        # stays within the inner-tree's workspace.
        out = self.context.out
        jail_cfg.add_mountpt(src=out.root(abspath=True),
                             dst=out.root(base=out.Base.OUTER, abspath=True),
                             is_bind=True,
                             rw=True,
                             mandatory=True)

        for tree in self.inner_trees.trees.values():
            jail_cfg.add_nsjail(tree.meld_config)

        return jail_cfg

    def _build(self):
        """Orchestrate the build."""

        context = self.context
        inner_trees = self.inner_trees
        jail_cfg = self._create_nsjail_config()

        # 1. Interrogate the trees
        description = inner_trees.for_each_tree(interrogate.interrogate_tree)
        # TODO: Do something when bazel_only is True.  Provided now as an
        # example of how we can query the interrogation results.
        _bazel_only = len(inner_trees.keys()) == 1 and all(
            x.get("single_bazel_optimization_available")
            for x in description.values())

        # 2a. API Export
        inner_trees.for_each_tree(api_export.export_apis_from_tree)

        # 2b. API Surface Assembly
        api_assembly.assemble_apis(context, inner_trees)

        # 3a. Inner tree analysis
        tree_analysis.analyze_trees(context, inner_trees)

        # 3b. Final Packaging Rules
        final_packaging.final_packaging(context, inner_trees)

        # 4. Build Execution
        # TODO: determine the targets from the lunch command and mcombo files.
        # For now, use a default that is consistent with having the build work.
        targets = self.opts.targets or ["vendor/nothing"]
        print("Running ninja...")
        # Disable network access in the combined ninja execution
        jail_cfg.add_option(name="clone_newnet", value="true")
        ninja_runner.run_ninja(context, jail_cfg, targets)

        # Success!
        return EXIT_STATUS_OK

    def _shell(self):
        """Launch a shell."""

        jail_cfg = self._create_nsjail_config()
        jail_cfg.add_envar(name='TERM')  # Pass TERM to the nsjail environment.

        if self.opts.debug:
            home = os.environ.get('HOME')
            jail_cfg.make_cwd_writable()
            if home:
                print(f"setting HOME={home}")
                jail_cfg.add_envar(name='HOME', value=home)

        nsjail_bin = self.context.tools.nsjail
        # Write the nsjail config
        nsjail_config_file = self.context.out.nsjail_config_file() + "-shell"
        jail_cfg.generate_config(nsjail_config_file)

        # Construct the command
        cmd = [nsjail_bin, "--config", nsjail_config_file, "--", "/bin/bash"]

        # Run the shell, and ignore errors from the interactive shell.
        print(f"Running: {' '.join(cmd)}")
        subprocess.run(cmd, shell=False, check=False)
        return EXIT_STATUS_OK

    def Run(self):
        """Orchestrate the build."""

        if self.opts.shell:
            return self._shell()

        return self._build()


if __name__ == "__main__":
    try:
        orch = Orchestrator(sys.argv)
    except lunch.ConfigException as e:
        print(f"{e}", file=sys.stderr)
        sys.exit(EXIT_STATUS_ERROR)

    sys.exit(orch.Run())

# vim: sts=4:ts=4:sw=4
