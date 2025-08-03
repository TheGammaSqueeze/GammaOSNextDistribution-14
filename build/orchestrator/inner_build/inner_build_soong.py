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

import json
import os
import shutil
import subprocess
import sys
from typing import List, Tuple

import common
from find_api_packages import ApiPackageFinder

# Additional env vars to pass to the SOONG UI invocation.
_ENV_VARS = {
    # Orchestrator runs in a Read-only workspace, but inner_build does not
    # (by default). Set this environment variable so that inner_build can
    # setup its own nsjail.
    "BUILD_BROKEN_SRC_DIR_IS_WRITABLE": "false",

    # TODO: Once we are publishing a lightweight API surfaces tree, we
    # should not need to set the environment variables.
    "ALLOW_MISSING_DEPENDENCIES": "true",
    "SKIP_VNDK_VARIANTS_CHECK": "true",
}

# Default TARGET_PRODUCT.  See bazel/rules/make_injection.bzl, and envsetup.sh.
DEFAULT_TARGET_PRODUCT = "aosp_arm"


class InnerBuildSoong(common.Commands):
    def __init__(self, env_vars=None):
        """Initialize the instance.

        Args:
          env_vars: Environment variable updates.  See common.setenv()
        """
        self.env_vars = dict(**_ENV_VARS)
        self.env_vars.update(env_vars or {})

    def export_api_contributions(self, args):
        with common.setenv(**self.env_vars):
            self._export_api_contributions(args)

    def _export_api_contributions(self, args):
        # Bazel is used to export API contributions even when the primary build
        # system is Soong.
        exporter = ApiExporterBazel(inner_tree=args.inner_tree,
                                    out_dir=args.out_dir,
                                    api_domains=args.api_domain)
        exporter.export_api_contributions()

    def analyze(self, args):
        with common.setenv(**self.env_vars):
            self._analyze(args)

    def _analyze(self, args):
        """Run analysis on this tree."""
        cmd = [
            "build/soong/soong_ui.bash", "--build-mode",
            f"--dir={args.inner_tree}", "-all-modules", "nothing",
            "--skip-soong-tests", "--search-api-dir", "--multitree-build"
        ]

        p = subprocess.run(cmd, shell=False, check=False)
        if p.returncode:
            sys.stderr.write(f"analyze: {cmd} failed with error message:\n"
                             f"{p.stderr.decode() if p.stderr else ''}")
            sys.exit(p.returncode)

        # Capture the environment variables passed by soong_ui to single-tree
        # ninja.
        env_path = os.path.join(args.out_dir, 'soong', 'ninja.environment')
        with open(env_path, "r", encoding='iso-8859-1') as f:
            try:
                env_json = json.load(f)
            except json.decoder.JSONDecodeError as ex:
                sys.stderr.write(f"failed to parse {env_path}: {ex.msg}\n")
                raise ex
        shutil.copyfile(env_path, os.path.join(args.out_dir, "inner_tree.env"))

        # Deliver the innertree's ninja file at `inner_tree.ninja`.
        product = os.environ.get("TARGET_PRODUCT", DEFAULT_TARGET_PRODUCT)
        src_path = os.path.join(args.out_dir, f"combined-{product}.ninja")
        dst_path = os.path.join(args.out_dir, f"inner_tree.ninja")
        shutil.copyfile(src_path, dst_path)

        # TODO: Create an empty file for now. orchestrator will subninja the
        # primary ninja file only if build_targets.json is not empty.
        with open(os.path.join(args.out_dir, "build_targets.json"),
                  "w",
                  encoding='iso-8859-1') as f:
            json.dump({"staging": []}, f, indent=2)


class ApiMetadataFile(object):
    """Utility class that wraps the generated API surface metadata files"""

    def __init__(self, inner_tree: str, path: str,
                 bazel_output_user_root: str):
        self.inner_tree = inner_tree
        self.path = path
        self.bazel_output_user_root = bazel_output_user_root

    def fullpath(self) -> str:
        # The Bazel convenience symlinks do not exist inside the nsjail
        # workspace, since the workspace is read-only.
        # Inject the output_user_root prefix into cquery result so that Build
        # orchestrator can find the metadata files.
        #
        # e.g. cquery returns bazel-out/android_target-fastbuild/bin/... which
        # does not exist.
        # replace with <output_user_root>/... which does exist.
        cleaned_path = self.path.replace("bazel-out",
                                         self.bazel_output_user_root)
        return os.path.join(self.inner_tree, cleaned_path)

    def name(self) -> str:
        """Returns filename"""
        return os.path.basename(self.fullpath())

    def newerthan(self, otherpath: str) -> bool:
        """Returns true if this file is newer than the file at `otherpath`"""
        return not os.path.exists(otherpath) or os.path.getmtime(
            otherpath) < os.path.getmtime(self.fullpath())


# ApiPackageFinder filters for special chars in .mcombo files
_MCOMBO_WILDCARD_FILTERS = {
    "*": lambda x: x.is_apex,
    # TODO: Support more wildcards if necessary (e.g. vendor apex, google
    # variants etc.)
}


class ApiExporterBazel(object):
    """Generate API surface metadata files into a well-known directory

    Intended Use:
        This directory is subsequently scanned by the build orchestrator for API
        surface assembly.
    """

    def __init__(self, inner_tree: str, out_dir: str, api_domains: List[str]):
        """Initialize the instance.

        Args:
            inner_tree: Root of the exporting tree
            out_dir: output directory. The files will be copied to
                     $our_dir/api_contribtutions
            api_domains: The API domains whose contributions should be exported
        """
        self.inner_tree = inner_tree
        self.out_dir = out_dir
        self.api_domains = api_domains

    def export_api_contributions(self):
        contribution_targets = self._find_api_domain_contribution_targets()
        metadata_files = self._build_api_domain_contribution_targets(
            contribution_targets)
        self._copy_api_domain_contribution_metadata_files(files=metadata_files)

    def _find_api_domain_contribution_targets(self) -> List[str]:
        """Return the label of the Bazel contribution targets to build"""
        print(f"Finding api_domain_contribution Bazel BUILD targets "
              f"in tree rooted at {self.inner_tree}")
        finder = ApiPackageFinder(inner_tree_root=self.inner_tree)
        contribution_targets = []
        for api_domain in self.api_domains:
            default_name_filter = lambda x: x.api_domain == api_domain
            api_domain_filter = _MCOMBO_WILDCARD_FILTERS.get(
                api_domain, default_name_filter)
            labels = finder.find_api_label_string_using_filter(
                api_domain_filter)
            contribution_targets.extend(labels)
        return contribution_targets

    def _build_api_domain_contribution_targets(self,
                                               contribution_targets: List[str]
                                               ) -> List[ApiMetadataFile]:
        """Build the contribution targets

        Return:
            the filepath of the generated files.
        """
        print(f"Running Bazel build on api_domain_contribution targets in "
              f"tree rooted at {self.inner_tree}")
        if not contribution_targets:
            return None
        self._run_bazel_cmd(
            subcmd="build",
            targets=contribution_targets,
            capture_output=False,  # log everything to terminal
        )
        # Determine the output_user_root where the artifacts are created.
        print("Running Bazel info in tree rooted at {self.inner_tree}")
        proc = self._run_bazel_cmd(
            subcmd="info",
            targets=["output_path"],
            capture_output=True,
            run_bp2build=False,
        )
        output_path = proc.stdout.decode().rstrip()

        print("Running Bazel cquery on api_domain_contribution targets "
              f"in tree rooted at {self.inner_tree}")
        proc = self._run_bazel_cmd(
            subcmd="cquery",
            # cquery raises an error if multiple targets are provided.
            # Create a union expression instead.
            targets=[" union ".join(contribution_targets)],
            subcmd_options=[
                "--output=files",
            ],
            capture_output=True,  # parse cquery result from stdout
            # we just ran bp2build. We can run it in again,
            # but this adds time.
            run_bp2build=False,
        )
        # The cquery response contains a blank line at the end.
        # Remove this before creating the filepaths array.
        filepaths = proc.stdout.decode().rstrip().split("\n")
        return [
            ApiMetadataFile(inner_tree=self.inner_tree,
                            path=filepath,
                            bazel_output_user_root=output_path)
            for filepath in filepaths
        ]

    def _copy_api_domain_contribution_metadata_files(
            self, files: List[ApiMetadataFile]):
        """Copies the metadata files to a well-known location"""
        target_dir = os.path.join(self.out_dir, "api_contributions")
        print(f"Copying API contribution metadata files of tree rooted at "
              f"{self.inner_tree} to {target_dir}")
        # Create the directory if it does not exist, even if that inner_tree has
        # no contributions.
        if not os.path.exists(target_dir):
            os.makedirs(target_dir)
        if not files:
            return
        # Delete stale API contribution files
        filenames = {file.name() for file in files}
        with os.scandir(target_dir) as it:
            for dirent in it:
                if dirent.name not in filenames:
                    os.remove(dirent.path)
        # Copy API contribution files if mtime has changed
        for file in files:
            target = os.path.join(target_dir, file.name())
            if file.newerthan(target):
                # Copy file without metadata like read-only
                shutil.copyfile(file.fullpath(), target)

    def _run_bazel_cmd(self,
                       subcmd: str,
                       targets: List[str],
                       subcmd_options: Tuple[str] = (),
                       run_bp2build=True,
                       **kwargs) -> subprocess.CompletedProcess:
        """Runs Bazel subcmd with Multi-tree specific configuration"""
        # TODO (b/244766775): Replace the two discrete cmds once the new
        # b-equivalent entrypoint is available.
        if run_bp2build:
            self._run_bp2build_cmd()
        output_user_root = self._output_user_root()
        cmd = [
            # Android's Bazel-entrypoint. Contains configs like the JDK to use.
            "build/bazel/bin/bazel",
            subcmd,
            # Run Bazel on the synthetic api_bp2build workspace.
            "--config=api_bp2build",
            "--config=android",
            f"--symlink_prefix={output_user_root}",  # Use prefix hack to create the convenience symlinks in out/
        ]
        subcmd_options = list(subcmd_options)
        cmd += subcmd_options + targets
        return self._run_cmd(cmd, **kwargs)

    # Create a unique output root for this workspace inside the nsjail.
    # This ensures that we do not share a single Bazel server between the
    # workspace inside and outside the nsjail.
    def _output_user_root(self) -> str:
        return os.path.join(self.inner_tree, self.out_dir, "bazel")

    def _run_bp2build_cmd(self, **kwargs) -> subprocess.CompletedProcess:
        """Runs b2pbuild to generate the synthetic Bazel workspace"""
        cmd = [
            "build/soong/soong_ui.bash",
            "--build-mode",
            "--all-modules",
            f"--dir={self.inner_tree}",
            "api_bp2build",
            "--skip-soong-tests",
            "--multitree-build",
            "--search-api-dir",  # This ensures that Android.bp.list remains the same in the analysis step.
        ]
        return self._run_cmd(cmd, **kwargs)

    def _run_cmd(self, cmd, **kwargs) -> subprocess.CompletedProcess:
        proc = subprocess.run(cmd,
                              cwd=self.inner_tree,
                              shell=False,
                              check=False,
                              **kwargs)
        if proc.returncode:
            sys.stderr.write(
                f"export_api_contributions: {cmd} failed with error message:\n"
            )
            if proc.stderr:
                sys.stderr.write(proc.stderr.decode())
            sys.exit(proc.returncode)
        return proc


def main(argv):
    return InnerBuildSoong().Run(argv)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
