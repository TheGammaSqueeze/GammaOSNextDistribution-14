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
"""Core logic for generating, syncing, and cleaning up a Bazel environment."""
import abc
import datetime
import logging
import os
import pathlib
import re
import shutil
import subprocess
from typing import Set, Dict, List

# Regex for BUILD files used to identify them since they can be named
# BUILD or BUILD.bazel.
BUILD_FILENAME_REGEX = re.compile("(BUILD|BUILD.bazel)")


class Error(Exception):
    """Base Error that all other errors the system throws are descendants of."""
    pass


class SoongExecutionError(Error):
    """Raised when Soong fails to build provided targets."""
    pass


class Soong:
    """Interface for the Soong build system.

    Attributes:
        soong_workspace: the top of the Android workspace that Soong
        will be operating in.
        soong_executable: the path to the executable for the soong_ui.bash
        launcher.
    """
    soong_workspace: pathlib.Path
    soong_executable: pathlib.Path

    def __init__(self, soong_workspace: pathlib.Path):
        self.soong_workspace = soong_workspace

        self.soong_executable = self.soong_workspace.joinpath(
            "build/soong/soong_ui.bash")
        if not self.soong_executable.exists():
            raise SoongExecutionError(
                "Unable to find Soong executable, expected location: %s" %
                self.soong_executable)

    def build(self, build_targets: Set[str]) -> None:
        """Builds the provided set of targets with Soong.

        Of note, there is no verification for the targets that get passed in,
        rather that responsibility passes to Soong which will fail to build if a
        target is invalid.

        Args:
            build_targets: a set of targets to build with Soong.
        """
        cmd_args = [
            str(self.soong_executable), "--build-mode", "--all-modules",
            f"--dir={self.soong_workspace}"
        ]
        cmd_args.extend(build_targets)

        logging.info("Building targets with Soong: %s", build_targets)
        logging.info("Please be patient, this may take a while...")
        logging.debug("Soong Command is: %s", " ".join(cmd_args))

        try:
            subprocess.run(cmd_args,
                           cwd=self.soong_workspace,
                           check=True,
                           stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT)
        except subprocess.CalledProcessError as cpe:
            raise SoongExecutionError(
                "There was an error during the Soong build process. Please "
                "correct the error with Soong, and try running this script "
                "again. Soong output follows:\n\n"
                f"{cpe.stdout.decode('utf-8')}") from cpe

        logging.info("Soong command completed successfully.")


class Resource(abc.ABC):
    """
    Represents a resource file that is used for scaffolding the Bazel env.

    Resource is an abstract class. Inheriting classes must provide the following
    attributes which are used by the default method implementations below.

    Attributes:
        stage_path: the path where this resource should be written when staged.
        workspace_path: the path where this resource should be synced to in the
        workspace.
    """
    stage_path: pathlib.Path
    workspace_path: pathlib.Path

    def build_targets(self) -> set:
        return set()

    @abc.abstractmethod
    def stage(self, _):
        """Writes a resource to its stage location."""
        pass

    @abc.abstractmethod
    def sync(self):
        """Syncs a resource to its workspace location."""

        # Overwrite any existing file in the workspace when synced.
        self.workspace_path.unlink(missing_ok=True)
        self.workspace_path.symlink_to(self.stage_path)

    @abc.abstractmethod
    def clean(self):
        """Cleans a resource from its workspace location."""
        self.workspace_path.unlink(missing_ok=True)


class StaticResource(Resource):
    """Resource representing a static file to be copied.

    Attributes:
        resource_path: path to the resource on disk.
    """
    def __init__(self, stage_path: pathlib.Path, workspace_path: pathlib.Path,
                 resource_path: pathlib.Path):
        self.stage_path = stage_path
        self.workspace_path = workspace_path
        self._resource_path = resource_path

    def stage(self, _):
        _verify_directory(self.stage_path.parent)
        shutil.copy(self._resource_path, self.stage_path)
        _make_executable_if_script(self.stage_path)

    def sync(self):
        super().sync()

    def clean(self):
        super().clean()

    def __repr__(self):
        return (f"StaticResource(stage_path={self.stage_path}, "
                f"workspace_path={self.workspace_path}, "
                f"resource_path={self._resource_path})")


class TemplateError(Error):
    """Raised when there is an issue while templating a template file."""
    pass


class TemplateResource(Resource):
    """Resource that represents a file to be templated.

    When staged, the resource is templated using the "mapping" provided
    to the stage function.

    Attributes:
        resource_path: path to the resource on disk.
    """
    resource_path: pathlib.Path

    # Key within templates that identifies a Soong target to build for a
    # provided template.
    SOONG_TARGET_KEY = "SOONG_TARGET"
    _KEY_VALUE_SEP = ":"

    # For a provided template, lines matching this regex are ignored when
    # loading the template.
    #
    # This enables to contain metadata that, while visible to the script, is
    # not visible in the generated templates. This is currently used with the
    # SOONG_TARGET key/value pairs in templates.
    _IGNORE_LINE_REGEX = re.compile(f"({SOONG_TARGET_KEY})")

    def __init__(self, stage_path: pathlib.Path, workspace_path: pathlib.Path,
                 resource_path: pathlib.Path):
        self.stage_path = stage_path
        self.workspace_path = workspace_path
        self.resource_path = resource_path

    def __repr__(self):
        return (f"TemplateResource(stage_path={self.stage_path}, "
                f"workspace_path={self.workspace_path}, "
                f"resource_path={self.resource_path})")

    def stage(self, mapping: Dict[str, str]):
        _verify_directory(self.stage_path.parent)
        lines = self.resource_path.open().readlines()
        lines = [
            line for line in lines if not self._IGNORE_LINE_REGEX.search(line)
        ]

        try:
            output = "".join(lines).format_map(mapping)
        except KeyError as ke:
            raise TemplateError(
                f"Malformed template file: {self.resource_path}") from ke

        with self.stage_path.open("w") as output_file:
            output_file.write(output)
        _make_executable_if_script(self.stage_path)

    def sync(self):
        super().sync()

    def clean(self):
        super().clean()

    @classmethod
    def read_value_from_template_var(cls, line: str) -> str:
        value = line.split(cls._KEY_VALUE_SEP)[-1]
        return value.strip()


class BuildTemplateResource(Resource):
    """Resource that represents a BUILD file.

    This operates in the same way as a TemplateResource, however also
    sets up the prebuilts directory needed by BUILD files.

    Attributes:
        _template_resource: underlying TemplateResource for this resource.
        global_prebuilts_dir: the directory in the filesystem where prebuilts
        live, this directory is symlinked to by a directory adjacent
        to the BUILD file represented by this Resource.
        prebuilts_stage_path: the path of the staged prebuilts directory
        which is a symlink to the global_prebuilts_dir.
        prebuilts_workspace_path: the path of the workspace prebuilts directory,
        which is a symlink to the global_prebuilts_dir.
    """
    _template_resource: TemplateResource
    global_prebuilts_dir: pathlib.Path
    prebuilts_stage_path: pathlib.Path
    prebuilts_workspace_path: pathlib.Path

    def __init__(self, stage_path: pathlib.Path, workspace_path: pathlib.Path,
                 resource_path: pathlib.Path,
                 global_prebuilts_dir: pathlib.Path, prebuilts_dir_name: str):
        self.stage_path = stage_path
        self.workspace_path = workspace_path

        self._template_resource = TemplateResource(stage_path, workspace_path,
                                                   resource_path)
        self.global_prebuilts_dir = global_prebuilts_dir
        self.prebuilts_stage_path = self.stage_path.parent.joinpath(
            prebuilts_dir_name)
        self.prebuilts_workspace_path = self.workspace_path.parent.joinpath(
            prebuilts_dir_name)

    def __repr__(self):
        return ("BuildTemplateResource("
                f"template_resource={self._template_resource}, "
                f"global_prebuilts_dir={self.global_prebuilts_dir}, "
                f"prebuilts_stage_path={self.prebuilts_stage_path}, "
                f"prebuilts_workspace_path={self.prebuilts_workspace_path})")

    def build_targets(self) -> set:
        """Overrides build_targets() to read targets from the BUILD template."""
        targets = set()
        with self._template_resource.resource_path.open() as build_file:
            while line := build_file.readline():
                if self._template_resource.SOONG_TARGET_KEY in line:
                    targets.add(
                        self._template_resource.read_value_from_template_var(
                            line))

        return targets

    def stage(self, mapping: Dict[str, str]):
        """Overrides stage() to stage a BUILD resource.

        Delegates most actions to the _template_resource, while ensuring that
        a generated prebuilts directory is also staged.
        """
        self._template_resource.stage(mapping)
        self.prebuilts_stage_path.symlink_to(self.global_prebuilts_dir,
                                             target_is_directory=True)

    def sync(self):
        """Overrides sync() to sync a BUILD resource with prebuilts.

        Delegates to the _template_resource while also ensuring that the
        generated prebuilts directory is written to the workspace.
        """
        self._template_resource.sync()

        # Overwrite the existing prebuilts directory, if it exists.
        self.prebuilts_workspace_path.unlink(missing_ok=True)
        self.prebuilts_workspace_path.symlink_to(self.global_prebuilts_dir,
                                                 target_is_directory=True)

    def clean(self):
        """Overrides clean() to clean a BUILD resource.

        Delegates most actions to the _template_resource, while also ensuring
        that the generated prebuilts directory is removed from the workspace.
        """
        self._template_resource.clean()
        self.prebuilts_workspace_path.unlink(missing_ok=True)


def _make_executable_if_script(path: pathlib.Path) -> None:
    """Makes the provided path executable if it is a script.
    Args:
        path: the path to check and conditionally make executable.
    """
    if path.name.endswith(".sh"):
        # Grant full permissions (read/write/execute) for current user and
        # read/write permissions for group.
        path.chmod(mode=0o750)


class ResourcesNotFoundError(Error):
    """Raised when the required resources are not found."""
    pass


class Resources:
    """Manages and loads resources from disk.

    Attributes:
        workspace_base_path: the path to the root of the workspace where
        staged files will be synced to.
        gendir_base_path: the path to the root of the staging directory.
        global_prebuilts_dir_path: the path to the root of the global
        prebuilts directory to which all generated prebuilts directories will
        symlink to.
        prebuilts_dir_name: the name to use for generated prebuilts
        directories.
        static_path: the path to the static resources directory.
        template_path: the path to the template resources directory.
    """
    workspace_base_path: pathlib.Path
    gendir_base_path: pathlib.Path
    global_prebuilts_dir_path: pathlib.Path
    prebuilts_dir_name: str
    static_path: pathlib.Path
    template_path: pathlib.Path

    # Name of the directory where script runfiles are located.
    _DATA_DIRNAME = "data"

    # Name of the directory where the templates should be located.
    _TEMPLATES_DIRNAME = "templates"

    # Name of the directory where static files (to be copied to the environment)
    # should be located.
    _STATIC_DIRNAME = "static"

    # If the script is executed from the root of the runfiles directory, this is
    # where the data should be.
    _RESOURCES_RUNFILES_BASEPATH = pathlib.Path(
        "build/pesto/experiments/prepare_bazel_test_env", _DATA_DIRNAME)

    # File extension for templates, determining whether or not a given file is a
    # template.
    _TEMPLATE_FILE_EXT = ".template"

    def __init__(self,
                 workspace_base_path: pathlib.Path,
                 gendir_base_path: pathlib.Path,
                 global_prebuilts_dir_path: pathlib.Path,
                 prebuilts_dir_name: str,
                 path: pathlib.Path = _RESOURCES_RUNFILES_BASEPATH):
        logging.debug("Resources(path=%s)", path)

        self.workspace_base_path = workspace_base_path
        self.gendir_base_path = gendir_base_path
        self.global_prebuilts_dir_path = global_prebuilts_dir_path
        self.prebuilts_dir_name = prebuilts_dir_name

        path = path.resolve()

        self.template_path = path.joinpath(Resources._TEMPLATES_DIRNAME)
        self.static_path = path.joinpath(Resources._STATIC_DIRNAME)

        if not self.template_path.exists() or not self.static_path.exists():
            raise ResourcesNotFoundError("Unable to find resources at path "
                                         f"{path}, expected the following "
                                         "directories: "
                                         f"{Resources._TEMPLATES_DIRNAME}, "
                                         f"{Resources._STATIC_DIRNAME}")

    def __repr__(self):
        return ("Resources("
                f"template_path={self.template_path}"
                f"static_path={self.static_path})")

    def stage(self, mapping: Dict[str, str]) -> None:
        for resource in self.load():
            logging.debug("Staging resource: %s", resource)
            resource.stage(mapping)

    def sync(self) -> None:
        for resource in self.load():
            logging.debug("Syncing resource: %s", resource)
            resource.sync()

    def clean(self) -> None:
        for resource in self.load():
            logging.debug("Cleaning resource: %s", resource)
            resource.clean()

    def build_targets(self) -> Set[str]:
        return {
            t
            for resource in self.load() for t in resource.build_targets()
        }

    def load(self) -> List[Resource]:
        """Loads the Resources used to scaffold the Bazel env.

        Returns:
            a list of Resource objects representing the files
            that should be used to template the environment.
        """
        resources = []

        # Add all templates.
        for p in self._template_resource_paths():
            template_relpath = Resources._strip_template_identifier_from_path(
                p.relative_to(self.template_path))
            stage_path = self.gendir_base_path.joinpath(template_relpath)
            workspace_path = self.workspace_base_path.joinpath(
                template_relpath)

            if BUILD_FILENAME_REGEX.match(template_relpath.name):
                resources.append(
                    BuildTemplateResource(
                        stage_path=stage_path,
                        workspace_path=workspace_path,
                        resource_path=p,
                        global_prebuilts_dir=self.global_prebuilts_dir_path,
                        prebuilts_dir_name=self.prebuilts_dir_name))
            else:
                resources.append(
                    TemplateResource(stage_path=stage_path,
                                     workspace_path=workspace_path,
                                     resource_path=p))

        # Add all static files.
        for p in self._static_resource_paths():
            static_relpath = p.relative_to(self.static_path)
            stage_path = self.gendir_base_path.joinpath(static_relpath)
            workspace_path = self.workspace_base_path.joinpath(static_relpath)
            resources.append(
                StaticResource(stage_path=stage_path,
                               workspace_path=workspace_path,
                               resource_path=p))

        return resources

    def _static_resource_paths(self) -> List[pathlib.Path]:
        return [p for p in self.static_path.glob("**/*") if p.is_file()]

    def _template_resource_paths(self) -> List[pathlib.Path]:
        return [p for p in self.template_path.glob("**/*") if p.is_file()]

    @staticmethod
    def _strip_template_identifier_from_path(p: pathlib.Path):
        """Strips the template file extension from a provided path."""
        if p.name.endswith(Resources._TEMPLATE_FILE_EXT):
            p = p.with_name(p.name[:len(p.name) -
                                   len(Resources._TEMPLATE_FILE_EXT)])
        return p


class AndroidBuildEnvironmentError(Error):
    """Raised when the Android Build Environment is not properly set."""
    pass


class BazelTestEnvGenerator:
    """Context for the Bazel environment generation.

  This class provides access to locations within the filesystem pertinent to the
  current execution as members that can be accessed by users of the class.

  Attributes:
      workspace_top: the top of the codebase, which also serves as the top of
      the Bazel WORKSPACE.
      host_out: Host artifact staging directory location.
      host_testcases: Host testcase staging directory location.
      product_out: Product/Target artifact staging directory location.
      target_testcases: Product/Target testcase staging directory location.
      staging_dir: Staging directory for generated Bazel artifacts.
      prebuilts_dir_name: Name of the directory that should be used for the
      directories that prebuilts are placed into when synced into the source
      tree.
      global_prebuilts_dir_path: The absolute path to the global prebuilts
      directory.
      year: the current calendar year.
      gen_dir: Subdirectory of the staging dir where the Bazel environment
      should be generated to.
  """
    workspace_top: pathlib.Path
    host_out: pathlib.Path
    host_testcases: pathlib.Path
    product_out: pathlib.Path
    target_testcases: pathlib.Path
    staging_dir: pathlib.Path
    prebuilts_dir_name: str = ".soong_prebuilts"
    prebuilts_dir_path: pathlib.Path
    gen_dir: pathlib.Path
    year: str
    _resources: Resources
    _soong: Soong

    # Name of the subdirectory, within the output directory, where the
    # prebuilts directory is scaffolded.
    #
    # This directory then serves as the target of symlinks from across the
    # source tree that gives any Bazel target access to the Soong staging
    # directories.
    GLOBAL_PREBUILTS_DIR_PATH = "prebuilts"

    def __init__(self, env_dict: Dict[str, str] = os.environ):
        try:
            self.workspace_top = pathlib.Path(env_dict["ANDROID_BUILD_TOP"])
            self.host_out = pathlib.Path(env_dict["ANDROID_HOST_OUT"])
            self.host_testcases = pathlib.Path(
                env_dict["ANDROID_HOST_OUT_TESTCASES"])
            self.product_out = pathlib.Path(env_dict["ANDROID_PRODUCT_OUT"])
            self.target_testcases = pathlib.Path(
                env_dict["ANDROID_TARGET_OUT_TESTCASES"])
        except KeyError as e:
            raise AndroidBuildEnvironmentError(
                "Missing expected environment variable.") from e

        self.staging_dir = pathlib.Path(self.workspace_top,
                                        "out/pesto-environment")
        self.prebuilts_dir_name = BazelTestEnvGenerator.prebuilts_dir_name
        self.global_prebuilts_dir_path = self.staging_dir.joinpath(
            self.GLOBAL_PREBUILTS_DIR_PATH)
        self.year = str(datetime.date.today().year)
        self.gen_dir = pathlib.Path(self.staging_dir, "gen")

        self._resources = Resources(self.workspace_top, self.gen_dir,
                                    self.global_prebuilts_dir_path,
                                    self.prebuilts_dir_name)
        self._soong = Soong(self.workspace_top)

    def __repr__(self):
        return "GenerationContext(%s)" % vars(self)

    def generate(self):
        logging.info("Starting generation of Bazel environment.")

        self._soong.build(self._resources.build_targets())

        logging.debug("Creating fresh staging dir at: %s", self.staging_dir)
        _verify_directory(self.staging_dir, clean=True)

        logging.debug("Creating fresh gen dir at: %s", self.gen_dir)
        _verify_directory(self.gen_dir, clean=True)

        logging.debug("Creating global prebuilts directory at: %s",
                      self.global_prebuilts_dir_path)
        _verify_directory(self.global_prebuilts_dir_path, clean=True)

        # Symlink the build system provided staging directories to the
        # global prebuilts directory.
        self.global_prebuilts_dir_path.joinpath("host").symlink_to(
            self.host_out)
        self.global_prebuilts_dir_path.joinpath("host_testcases").symlink_to(
            self.host_testcases)
        self.global_prebuilts_dir_path.joinpath("product").symlink_to(
            self.product_out)
        self.global_prebuilts_dir_path.joinpath("target_testcases").symlink_to(
            self.target_testcases)

        # Load and process each resource into the gen directory.
        self._resources.stage(mapping=vars(self))

        logging.info(
            "Generation of Bazel environment to staging directory "
            "(%s) completed successfully.", self.staging_dir)

    def sync(self):
        logging.info(
            "Starting synchronization of generated environment to source tree."
        )

        if not self.staging_dir.exists():
            raise FileNotFoundError("Staging directory does not exist, "
                                    "the generate function should be called "
                                    " to create this directory.")

        self._resources.sync()

        logging.info(
            "Successfully synchronized generated Bazel environment from "
            "%s to %s", self.gen_dir, self.workspace_top)

    def clean(self):
        logging.info("Starting clean of generated environment.")

        logging.info("Cleaning up synchronized files from the source tree.")
        # For all of our configured templates, attempt to find the corresponding
        # location in the source tree and remove them.
        self._resources.clean()

        logging.info("Cleaning up staging directory: %s", self.staging_dir)
        try:
            shutil.rmtree(self.staging_dir)
        except FileNotFoundError:
            logging.debug("Staging directory not found during cleanup "
                          "and may have already been removed.")
        logging.info("Successfully cleaned up generated environment.")


def _verify_directory(directory: pathlib.Path, clean: bool = False) -> None:
    """Verifies that the provided directory exists, creating it if it does not.

    Args:
      directory: path to the directory to create.
      clean: whether or not the existing directory should be removed if found or
        reused.
    """
    if directory.exists() and clean:
        logging.debug("Cleaning existing directory %s", directory)
        shutil.rmtree(directory)

    logging.debug("Verifying directory exists at %s", directory)
    directory.mkdir(parents=True, exist_ok=True)
