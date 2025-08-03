#!/usr/bin/env python3
#
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
"""Builds SDK snapshots.

If the environment variable TARGET_BUILD_APPS is nonempty then only the SDKs for
the APEXes in it are built, otherwise all configured SDKs are built.
"""
import argparse
import dataclasses
import datetime
import enum
import functools
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import typing
from collections import defaultdict
from typing import Callable, List
import zipfile

COPYRIGHT_BOILERPLATE = """
//
// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
""".lstrip()


@dataclasses.dataclass(frozen=True)
class ConfigVar:
    """Represents a Soong configuration variable"""
    # The config variable namespace, e.g. ANDROID.
    namespace: str

    # The name of the variable within the namespace.
    name: str


@dataclasses.dataclass(frozen=True)
class FileTransformation:
    """Performs a transformation on a file within an SDK snapshot zip file."""

    # The path of the file within the SDK snapshot zip file.
    path: str

    def apply(self, producer, path, build_release):
        """Apply the transformation to the path; changing it in place."""
        with open(path, "r+", encoding="utf8") as file:
            self._apply_transformation(producer, file, build_release)

    def _apply_transformation(self, producer, file, build_release):
        """Apply the transformation to the file.

        The file has been opened in read/write mode so the implementation of
        this must read the contents and then reset the file to the beginning
        and write the altered contents.
        """
        raise NotImplementedError


@dataclasses.dataclass(frozen=True)
class SoongConfigVarTransformation(FileTransformation):

    # The configuration variable that will control the prefer setting.
    configVar: ConfigVar

    # The line containing the prefer property.
    PREFER_LINE = "    prefer: false,"

    def _apply_transformation(self, producer, file, build_release):
        raise NotImplementedError


@dataclasses.dataclass(frozen=True)
class SoongConfigBoilerplateInserter(SoongConfigVarTransformation):
    """Transforms an Android.bp file to add soong config boilerplate.

    The boilerplate allows the prefer setting of the modules to be controlled
    through a Soong configuration variable.
    """

    # The configuration variable that will control the prefer setting.
    configVar: ConfigVar

    # The prefix to use for the soong config module types.
    configModuleTypePrefix: str

    def config_module_type(self, module_type):
        return self.configModuleTypePrefix + module_type

    def _apply_transformation(self, producer, file, build_release):
        # TODO(b/174997203): Remove this when we have a proper way to control
        #  prefer flags in Mainline modules.

        header_lines = []
        for line in file:
            line = line.rstrip("\n")
            if not line.startswith("//"):
                break
            header_lines.append(line)

        config_module_types = set()

        content_lines = []
        for line in file:
            line = line.rstrip("\n")

            # Check to see whether the line is the start of a new module type,
            # e.g. <module-type> {
            module_header = re.match("([a-z0-9_]+) +{$", line)
            if not module_header:
                # It is not so just add the line to the output and skip to the
                # next line.
                content_lines.append(line)
                continue

            module_type = module_header.group(1)
            module_content = []

            # Iterate over the Soong module contents
            for module_line in file:
                module_line = module_line.rstrip("\n")

                # When the end of the module has been reached then exit.
                if module_line == "}":
                    break

                # Check to see if the module is an unversioned module, i.e.
                # without @<version>. If it is then it needs to have the soong
                # config boilerplate added to control the setting of the prefer
                # property. Versioned modules do not need that because they are
                # never preferred.
                # At the moment this differentiation between versioned and
                # unversioned relies on the fact that the unversioned modules
                # set "prefer: false", while the versioned modules do not. That
                # is a little bit fragile so may require some additional checks.
                if module_line != self.PREFER_LINE:
                    # The line does not indicate that the module needs the
                    # soong config boilerplate so add the line and skip to the
                    # next one.
                    module_content.append(module_line)
                    continue

                # Add the soong config boilerplate instead of the line:
                #     prefer: false,
                namespace = self.configVar.namespace
                name = self.configVar.name
                module_content.append(f"""\
    // Do not prefer prebuilt if the Soong config variable "{name}" in namespace "{namespace}" is true.
    prefer: true,
    soong_config_variables: {{
        {name}: {{
            prefer: false,
        }},
    }},""")

                # Add the module type to the list of module types that need to
                # have corresponding config module types.
                config_module_types.add(module_type)

                # Change the module type to the corresponding soong config
                # module type by adding the prefix.
                module_type = self.config_module_type(module_type)

            # Generate the module, possibly with the new module type and
            # containing the soong config variables entry.
            content_lines.append(module_type + " {")
            content_lines.extend(module_content)
            content_lines.append("}")

        # Add the soong_config_module_type module definitions to the header
        # lines so that they appear before any uses.
        header_lines.append("")
        for module_type in sorted(config_module_types):
            # Create the corresponding soong config module type name by adding
            # the prefix.
            config_module_type = self.configModuleTypePrefix + module_type
            header_lines.append(f"""
// Soong config variable module type added by {producer.script}.
soong_config_module_type {{
    name: "{config_module_type}",
    module_type: "{module_type}",
    config_namespace: "{self.configVar.namespace}",
    bool_variables: ["{self.configVar.name}"],
    properties: ["prefer"],
}}
""".lstrip())

        # Overwrite the file with the updated contents.
        file.seek(0)
        file.truncate()
        file.write("\n".join(header_lines + content_lines) + "\n")


@dataclasses.dataclass(frozen=True)
class UseSourceConfigVarTransformation(SoongConfigVarTransformation):

    def _apply_transformation(self, producer, file, build_release):
        lines = []
        for line in file:
            line = line.rstrip("\n")
            if line != self.PREFER_LINE:
                lines.append(line)
                continue

            # Replace "prefer: false" with "use_source_config_var {...}".
            namespace = self.configVar.namespace
            name = self.configVar.name
            lines.append(f"""\
    // Do not prefer prebuilt if the Soong config variable "{name}" in namespace "{namespace}" is true.
    use_source_config_var: {{
        config_namespace: "{namespace}",
        var_name: "{name}",
    }},""")

        # Overwrite the file with the updated contents.
        file.seek(0)
        file.truncate()
        file.write("\n".join(lines) + "\n")


@dataclasses.dataclass()
class SubprocessRunner:
    """Runs subprocesses"""

    # Destination for stdout from subprocesses.
    #
    # This (and the following stderr) are needed to allow the tests to be run
    # in Intellij. This ensures that the tests are run with stdout/stderr
    # objects that work when passed to subprocess.run(stdout/stderr). Without it
    # the tests are run with a FlushingStringIO object that has no fileno
    # attribute - https://youtrack.jetbrains.com/issue/PY-27883.
    stdout: io.TextIOBase = sys.stdout

    # Destination for stderr from subprocesses.
    stderr: io.TextIOBase = sys.stderr

    def run(self, *args, **kwargs):
        return subprocess.run(
            *args, check=True, stdout=self.stdout, stderr=self.stderr, **kwargs)


def sdk_snapshot_zip_file(snapshots_dir, sdk_name):
    """Get the path to the sdk snapshot zip file."""
    return os.path.join(snapshots_dir, f"{sdk_name}-{SDK_VERSION}.zip")


def sdk_snapshot_info_file(snapshots_dir, sdk_name):
    """Get the path to the sdk snapshot info file."""
    return os.path.join(snapshots_dir, f"{sdk_name}-{SDK_VERSION}.info")


def sdk_snapshot_api_diff_file(snapshots_dir, sdk_name):
    """Get the path to the sdk snapshot api diff file."""
    return os.path.join(snapshots_dir, f"{sdk_name}-{SDK_VERSION}-api-diff.txt")


def sdk_snapshot_gantry_metadata_json_file(snapshots_dir, sdk_name):
    """Get the path to the sdk snapshot gantry metadata json file."""
    return os.path.join(snapshots_dir,
                        f"{sdk_name}-{SDK_VERSION}-gantry-metadata.json")


# The default time to use in zip entries. Ideally, this should be the same as is
# used by soong_zip and ziptime but there is no strict need for that to be the
# case. What matters is this is a fixed time so that the contents of zip files
# created by this script do not depend on when it is run, only the inputs.
default_zip_time = datetime.datetime(2008, 1, 1, 0, 0, 0, 0,
                                     datetime.timezone.utc)


# set the timestamps of the paths to the default_zip_time.
def set_default_timestamp(base_dir, paths):
    for path in paths:
        timestamp = default_zip_time.timestamp()
        p = os.path.join(base_dir, path)
        os.utime(p, (timestamp, timestamp))


@dataclasses.dataclass()
class SnapshotBuilder:
    """Builds sdk snapshots"""

    # The path to this tool.
    tool_path: str

    # Used to run subprocesses for building snapshots.
    subprocess_runner: SubprocessRunner

    # The OUT_DIR environment variable.
    out_dir: str

    # The out/soong/mainline-sdks directory.
    mainline_sdks_dir: str = ""

    # True if apex-allowed-deps-check is to be skipped.
    skip_allowed_deps_check: bool = False

    def __post_init__(self):
        self.mainline_sdks_dir = os.path.join(self.out_dir,
                                              "soong/mainline-sdks")

    def get_sdk_path(self, sdk_name):
        """Get the path to the sdk snapshot zip file produced by soong"""
        return os.path.join(self.mainline_sdks_dir,
                            f"{sdk_name}-{SDK_VERSION}.zip")

    def build_target_paths(self, build_release, target_paths):
        # Extra environment variables to pass to the build process.
        extraEnv = {
            # TODO(ngeoffray): remove SOONG_ALLOW_MISSING_DEPENDENCIES, but
            #  we currently break without it.
            "SOONG_ALLOW_MISSING_DEPENDENCIES": "true",
            # Set SOONG_SDK_SNAPSHOT_USE_SRCJAR to generate .srcjars inside
            # sdk zip files as expected by prebuilt drop.
            "SOONG_SDK_SNAPSHOT_USE_SRCJAR": "true",
        }
        extraEnv.update(build_release.soong_env)

        # Unless explicitly specified in the calling environment set
        # TARGET_BUILD_VARIANT=user.
        # This MUST be identical to the TARGET_BUILD_VARIANT used to build
        # the corresponding APEXes otherwise it could result in different
        # hidden API flags, see http://b/202398851#comment29 for more info.
        target_build_variant = os.environ.get("TARGET_BUILD_VARIANT", "user")
        cmd = [
            "build/soong/soong_ui.bash",
            "--make-mode",
            "--soong-only",
            f"TARGET_BUILD_VARIANT={target_build_variant}",
            "TARGET_PRODUCT=mainline_sdk",
            "MODULE_BUILD_FROM_SOURCE=true",
        ] + target_paths
        if not self.skip_allowed_deps_check:
            cmd += ["apex-allowed-deps-check"]
        print_command(extraEnv, cmd)
        env = os.environ.copy()
        env.update(extraEnv)
        self.subprocess_runner.run(cmd, env=env)

    def build_snapshots(self, build_release, modules):
        # Compute the paths to all the Soong generated sdk snapshot files
        # required by this script.
        paths = [
            sdk_snapshot_zip_file(self.mainline_sdks_dir, sdk)
            for module in modules
            for sdk in module.sdks
        ]

        if paths:
            self.build_target_paths(build_release, paths)
        return self.mainline_sdks_dir

    def build_snapshots_for_build_r(self, build_release, modules):
        # Build the snapshots as standard.
        snapshot_dir = self.build_snapshots(build_release, modules)

        # Each module will extract needed files from the original snapshot zip
        # file and then use that to create a replacement zip file.
        r_snapshot_dir = os.path.join(snapshot_dir, "for-R-build")
        shutil.rmtree(r_snapshot_dir, ignore_errors=True)

        build_number_file = os.path.join(self.out_dir, "soong/build_number.txt")

        for module in modules:
            apex = module.apex
            dest_dir = os.path.join(r_snapshot_dir, apex)
            os.makedirs(dest_dir, exist_ok=True)

            # Write the bp file in the sdk_library sub-directory rather than the
            # root of the zip file as it will be unpacked in a directory that
            # already contains an Android.bp file that defines the corresponding
            # apex_set.
            bp_file = os.path.join(dest_dir, "sdk_library/Android.bp")
            os.makedirs(os.path.dirname(bp_file), exist_ok=True)

            # The first sdk in the list is the name to use.
            sdk_name = module.sdks[0]

            with open(bp_file, "w", encoding="utf8") as bp:
                bp.write("// DO NOT EDIT. Auto-generated by the following:\n")
                bp.write(f"//     {self.tool_path}\n")
                bp.write(COPYRIGHT_BOILERPLATE)
                aosp_apex = google_to_aosp_name(apex)

                for library in module.for_r_build.sdk_libraries:
                    module_name = library.name
                    shared_library = str(library.shared_library).lower()
                    sdk_file = sdk_snapshot_zip_file(snapshot_dir, sdk_name)
                    extract_matching_files_from_zip(
                        sdk_file, dest_dir,
                        sdk_library_files_pattern(
                            scope_pattern=r"(public|system|module-lib)",
                            name_pattern=fr"({module_name}(-removed|-stubs)?)"))

                    available_apexes = [f'"{aosp_apex}"']
                    if aosp_apex != "com.android.tethering":
                        available_apexes.append(f'"test_{aosp_apex}"')
                    apex_available = ",\n        ".join(available_apexes)

                    bp.write(f"""
java_sdk_library_import {{
    name: "{module_name}",
    owner: "google",
    prefer: true,
    shared_library: {shared_library},
    apex_available: [
        {apex_available},
    ],
    public: {{
        jars: ["public/{module_name}-stubs.jar"],
        current_api: "public/{module_name}.txt",
        removed_api: "public/{module_name}-removed.txt",
        sdk_version: "module_current",
    }},
    system: {{
        jars: ["system/{module_name}-stubs.jar"],
        current_api: "system/{module_name}.txt",
        removed_api: "system/{module_name}-removed.txt",
        sdk_version: "module_current",
    }},
    module_lib: {{
        jars: ["module-lib/{module_name}-stubs.jar"],
        current_api: "module-lib/{module_name}.txt",
        removed_api: "module-lib/{module_name}-removed.txt",
        sdk_version: "module_current",
    }},
}}
""")

                # Copy the build_number.txt file into the snapshot.
                snapshot_build_number_file = os.path.join(
                    dest_dir, "snapshot-creation-build-number.txt")
                shutil.copy(build_number_file, snapshot_build_number_file)

            # Make sure that all the paths being added to the zip file have a
            # fixed timestamp so that the contents of the zip file do not depend
            # on when this script is run, only the inputs.
            for root, dirs, files in os.walk(dest_dir):
                set_default_timestamp(root, dirs)
                set_default_timestamp(root, files)

            # Now zip up the files into a snapshot zip file.
            base_file = os.path.join(r_snapshot_dir, sdk_name + "-current")
            shutil.make_archive(base_file, "zip", dest_dir)

        return r_snapshot_dir

    @staticmethod
    def does_sdk_library_support_latest_api(sdk_library):
        if sdk_library == "conscrypt.module.platform.api" or \
            sdk_library == "conscrypt.module.intra.core.api":
            return False
        return True

    def latest_api_file_targets(self, sdk_info_file):
        # Read the sdk info file and fetch the latest scope targets.
        with open(sdk_info_file, "r", encoding="utf8") as sdk_info_file_object:
            sdk_info_file_json = json.loads(sdk_info_file_object.read())

        target_paths = []
        target_dict = {}
        for jsonItem in sdk_info_file_json:
            if not jsonItem["@type"] == "java_sdk_library":
                continue

            sdk_library = jsonItem["@name"]
            if not self.does_sdk_library_support_latest_api(sdk_library):
                continue

            target_dict[sdk_library] = {}
            for scope in jsonItem["scopes"]:
                scope_json = jsonItem["scopes"][scope]
                target_dict[sdk_library][scope] = {}
                target_list = [
                    "current_api", "latest_api", "removed_api",
                    "latest_removed_api"
                ]
                for target in target_list:
                    target_dict[sdk_library][scope][target] = scope_json[target]
                target_paths.append(scope_json["latest_api"])
                target_paths.append(scope_json["latest_removed_api"])
                target_paths.append(scope_json["latest_api"]
                    .replace(".latest", ".latest.extension_version"))
                target_paths.append(scope_json["latest_removed_api"]
                    .replace(".latest", ".latest.extension_version"))

        return target_paths, target_dict

    def build_sdk_scope_targets(self, build_release, modules):
        # Build the latest scope targets for each module sdk
        # Compute the paths to all the latest scope targets for each module sdk.
        target_paths = []
        target_dict = {}
        for module in modules:
            for sdk in module.sdks:
                sdk_type = sdk_type_from_name(sdk)
                if not sdk_type.providesApis:
                    continue

                sdk_info_file = sdk_snapshot_info_file(self.mainline_sdks_dir,
                                                       sdk)
                paths, dict_item = self.latest_api_file_targets(sdk_info_file)
                target_paths.extend(paths)
                target_dict[sdk_info_file] = dict_item
        if target_paths:
            self.build_target_paths(build_release, target_paths)
        return target_dict

    def appendDiffToFile(self, file_object, sdk_zip_file, current_api,
                         latest_api, snapshots_dir):
        """Extract current api and find its diff with the latest api."""
        with zipfile.ZipFile(sdk_zip_file, "r") as zipObj:
            extracted_current_api = zipObj.extract(
                member=current_api, path=snapshots_dir)
            # The diff tool has an exit code of 0, 1 or 2 depending on whether
            # it find no differences, some differences or an error (like missing
            # file). As 0 or 1 are both valid results this cannot use check=True
            # so disable the pylint check.
            # pylint: disable=subprocess-run-check
            diff = subprocess.run([
                "diff", "-u0", latest_api, extracted_current_api, "--label",
                latest_api, "--label", extracted_current_api
            ],
                                  capture_output=True).stdout.decode("utf-8")
            file_object.write(diff)

    def create_snapshot_gantry_metadata_and_api_diff(self, sdk, target_dict,
                                                     snapshots_dir,
                                                     module_extension_version):
        """Creates gantry metadata and api diff files for each module sdk.

        For each module sdk, the scope targets are obtained for each java sdk
        library and the api diff files are generated by performing a diff
        operation between the current api file vs the latest api file.
        """
        sdk_info_file = sdk_snapshot_info_file(snapshots_dir, sdk)
        sdk_zip_file = sdk_snapshot_zip_file(snapshots_dir, sdk)
        sdk_api_diff_file = sdk_snapshot_api_diff_file(snapshots_dir, sdk)

        gantry_metadata_dict = {}
        with open(
                sdk_api_diff_file, "w",
                encoding="utf8") as sdk_api_diff_file_object:
            last_finalized_version_set = set()
            for sdk_library in target_dict[sdk_info_file]:
                for scope in target_dict[sdk_info_file][sdk_library]:
                    scope_json = target_dict[sdk_info_file][sdk_library][scope]
                    current_api = scope_json["current_api"]
                    latest_api = scope_json["latest_api"]
                    self.appendDiffToFile(sdk_api_diff_file_object,
                                          sdk_zip_file, current_api, latest_api,
                                          snapshots_dir)

                    removed_api = scope_json["removed_api"]
                    latest_removed_api = scope_json["latest_removed_api"]
                    self.appendDiffToFile(sdk_api_diff_file_object,
                                          sdk_zip_file, removed_api,
                                          latest_removed_api, snapshots_dir)

                    def read_extension_version(target):
                        extension_target = target.replace(
                            ".latest", ".latest.extension_version")
                        with open(
                            extension_target, "r", encoding="utf8") as file:
                            version = int(file.read())
                            # version equal to -1 means "not an extension version".
                            if version != -1:
                                last_finalized_version_set.add(version)

                    read_extension_version(scope_json["latest_api"])
                    read_extension_version(scope_json["latest_removed_api"])

            if len(last_finalized_version_set) == 0:
                # Either there is no java sdk library or all java sdk libraries
                # have not been finalized in sdk extensions yet and hence have
                # last finalized version set as -1.
                gantry_metadata_dict["last_finalized_version"] = -1
            elif len(last_finalized_version_set) == 1:
                # All java sdk library extension version match.
                gantry_metadata_dict["last_finalized_version"] =\
                    last_finalized_version_set.pop()
            else:
                # Fail the build
                raise ValueError(
                    "Not all sdk libraries finalized with the same version.\n")

        gantry_metadata_dict["api_diff_file"] = sdk_api_diff_file.rsplit(
            "/", 1)[-1]
        gantry_metadata_dict["api_diff_file_size"] = os.path.getsize(
            sdk_api_diff_file)
        gantry_metadata_dict[
            "module_extension_version"] = module_extension_version
        sdk_metadata_json_file = sdk_snapshot_gantry_metadata_json_file(
            snapshots_dir, sdk)

        gantry_metadata_json_object = json.dumps(gantry_metadata_dict, indent=4)
        with open(sdk_metadata_json_file,
                  "w") as gantry_metadata_json_file_object:
            gantry_metadata_json_file_object.write(gantry_metadata_json_object)

        if os.path.getsize(sdk_metadata_json_file) > 1048576: # 1 MB
            raise ValueError("Metadata file size should not exceed 1 MB.\n")

    def get_module_extension_version(self):
        return int(
            subprocess.run([
                "build/soong/soong_ui.bash", "--dumpvar-mode",
                "PLATFORM_SDK_EXTENSION_VERSION"
            ],
                           capture_output=True).stdout.decode("utf-8").strip())

    def build_snapshot_gantry_metadata_and_api_diff(self, modules, target_dict,
                                                    snapshots_dir):
        """For each module sdk, create the metadata and api diff file."""
        module_extension_version = self.get_module_extension_version()
        for module in modules:
            for sdk in module.sdks:
                sdk_type = sdk_type_from_name(sdk)
                if not sdk_type.providesApis:
                    continue
                self.create_snapshot_gantry_metadata_and_api_diff(
                    sdk, target_dict, snapshots_dir, module_extension_version)


# The sdk version to build
#
# This is legacy from the time when this could generate versioned sdk snapshots.
SDK_VERSION = "current"

# The initially empty list of build releases. Every BuildRelease that is created
# automatically appends itself to this list.
ALL_BUILD_RELEASES = []


class PreferHandling(enum.Enum):
    """Enumeration of the various ways of handling prefer properties"""

    # No special prefer property handling is required.
    NONE = enum.auto()

    # Apply the SoongConfigBoilerplateInserter transformation.
    SOONG_CONFIG = enum.auto()

    # Use the use_source_config_var property added in T.
    USE_SOURCE_CONFIG_VAR_PROPERTY = enum.auto()


@dataclasses.dataclass(frozen=True)
@functools.total_ordering
class BuildRelease:
    """Represents a build release"""

    # The name of the build release, e.g. Q, R, S, T, etc.
    name: str

    # The function to call to create the snapshot in the dist, that covers
    # building and copying the snapshot into the dist.
    creator: Callable[
        ["BuildRelease", "SdkDistProducer", List["MainlineModule"]], None]

    # The sub-directory of dist/mainline-sdks into which the build release
    # specific snapshots will be copied.
    #
    # Defaults to for-<name>-build.
    sub_dir: str = None

    # Additional environment variables to pass to Soong when building the
    # snapshots for this build release.
    #
    # Defaults to {
    #     "SOONG_SDK_SNAPSHOT_TARGET_BUILD_RELEASE": <name>,
    # }
    soong_env: typing.Dict[str, str] = None

    # The position of this instance within the BUILD_RELEASES list.
    ordinal: int = dataclasses.field(default=-1, init=False)

    # Whether this build release supports the Soong config boilerplate that is
    # used to control the prefer setting of modules via a Soong config variable.
    preferHandling: PreferHandling = \
        PreferHandling.USE_SOURCE_CONFIG_VAR_PROPERTY

    # Whether the generated snapshots should include flagged APIs. Defaults to
    # false because flagged APIs are not suitable for use outside Android.
    include_flagged_apis: bool = False

    # Whether the build release should generate Gantry metadata and API diff.
    generate_gantry_metadata_and_api_diff: bool = False

    def __post_init__(self):
        # The following use object.__setattr__ as this object is frozen and
        # attempting to set the fields directly would cause an exception to be
        # thrown.
        object.__setattr__(self, "ordinal", len(ALL_BUILD_RELEASES))
        # Add this to the end of the list of all build releases.
        ALL_BUILD_RELEASES.append(self)
        # If no sub_dir was specified then set the default.
        if self.sub_dir is None:
            object.__setattr__(self, "sub_dir", f"for-{self.name}-build")
        # If no soong_env was specified then set the default.
        if self.soong_env is None:
            object.__setattr__(
                self,
                "soong_env",
                {
                    # Set SOONG_SDK_SNAPSHOT_TARGET_BUILD_RELEASE to generate a
                    # snapshot suitable for a specific target build release.
                    "SOONG_SDK_SNAPSHOT_TARGET_BUILD_RELEASE": self.name,
                })

    def __eq__(self, other):
        return self.ordinal == other.ordinal

    def __le__(self, other):
        return self.ordinal <= other.ordinal


def create_no_dist_snapshot(_: BuildRelease, __: "SdkDistProducer",
                            modules: List["MainlineModule"]):
    """A place holder dist snapshot creation function that does nothing."""
    print(f"create_no_dist_snapshot for modules {[m.apex for m in modules]}")


def create_dist_snapshot_for_r(build_release: BuildRelease,
                               producer: "SdkDistProducer",
                               modules: List["MainlineModule"]):
    """Generate a snapshot suitable for use in an R build."""
    producer.product_dist_for_build_r(build_release, modules)


def create_sdk_snapshots_in_soong(build_release: BuildRelease,
                                  producer: "SdkDistProducer",
                                  modules: List["MainlineModule"]):
    """Builds sdks and populates the dist for unbundled modules."""
    producer.produce_unbundled_dist_for_build_release(build_release, modules)


def create_latest_sdk_snapshots(build_release: BuildRelease,
                                producer: "SdkDistProducer",
                                modules: List["MainlineModule"]):
    """Builds and populates the latest release, including bundled modules."""
    producer.produce_unbundled_dist_for_build_release(build_release, modules)
    producer.produce_bundled_dist_for_build_release(build_release, modules)


Q = BuildRelease(
    name="Q",
    # At the moment we do not generate a snapshot for Q.
    creator=create_no_dist_snapshot,
    # This does not support or need any special prefer property handling.
    preferHandling=PreferHandling.NONE,
)
R = BuildRelease(
    name="R",
    # Generate a simple snapshot for R.
    creator=create_dist_snapshot_for_r,
    # By default a BuildRelease creates an environment to pass to Soong that
    # creates a release specific snapshot. However, Soong does not yet (and is
    # unlikely to) support building an sdk snapshot for R so create an empty
    # environment to pass to Soong instead.
    soong_env={},
    # This does not support or need any special prefer property handling.
    preferHandling=PreferHandling.NONE,
)
S = BuildRelease(
    name="S",
    # Generate a snapshot for this build release using Soong.
    creator=create_sdk_snapshots_in_soong,
    # This requires the SoongConfigBoilerplateInserter transformation to be
    # applied.
    preferHandling=PreferHandling.SOONG_CONFIG,
)
Tiramisu = BuildRelease(
    name="Tiramisu",
    # Generate a snapshot for this build release using Soong.
    creator=create_sdk_snapshots_in_soong,
    # This build release supports the use_source_config_var property.
    preferHandling=PreferHandling.USE_SOURCE_CONFIG_VAR_PROPERTY,
)
UpsideDownCake = BuildRelease(
    name="UpsideDownCake",
    # Generate a snapshot for this build release using Soong.
    creator=create_sdk_snapshots_in_soong,
    # This build release supports the use_source_config_var property.
    preferHandling=PreferHandling.USE_SOURCE_CONFIG_VAR_PROPERTY,
)

# Insert additional BuildRelease definitions for following releases here,
# before LATEST.

# A build release for the latest build excluding flagged apis.
NEXT = BuildRelease(
    name="next",
    creator=create_latest_sdk_snapshots,
    # There are no build release specific environment variables to pass to
    # Soong.
    soong_env={},
    generate_gantry_metadata_and_api_diff=True,
)

# The build release for the latest build supported by this build, i.e. the
# current build. This must be the last BuildRelease defined in this script.
LATEST = BuildRelease(
    name="latest",
    creator=create_latest_sdk_snapshots,
    # There are no build release specific environment variables to pass to
    # Soong.
    soong_env={},
    # Latest must include flagged APIs because it may be dropped into the main
    # Android branches.
    include_flagged_apis=True,
    generate_gantry_metadata_and_api_diff=True,
)


@dataclasses.dataclass(frozen=True)
class SdkLibrary:
    """Information about a java_sdk_library."""

    # The name of java_sdk_library module.
    name: str

    # True if the sdk_library module is a shared library.
    shared_library: bool = False


@dataclasses.dataclass(frozen=True)
class ForRBuild:
    """Data structure needed for generating a snapshot for an R build."""

    # The java_sdk_library modules to export to the r snapshot.
    sdk_libraries: typing.List[SdkLibrary] = dataclasses.field(
        default_factory=list)


@dataclasses.dataclass(frozen=True)
class MainlineModule:
    """Represents an unbundled mainline module.

    This is a module that is distributed as a prebuilt and intended to be
    updated with Mainline trains.
    """
    # The name of the apex.
    apex: str

    # The names of the sdk and module_exports.
    sdks: list[str]

    # The first build release in which the SDK snapshot for this module is
    # needed.
    #
    # Note: This is not necessarily the same build release in which the SDK
    #       source was first included. So, a module that was added in build T
    #       could potentially be used in an S release and so its SDK will need
    #       to be made available for S builds.
    first_release: BuildRelease

    # The configuration variable, defaults to ANDROID:module_build_from_source
    configVar: ConfigVar = ConfigVar(
        namespace="ANDROID",
        name="module_build_from_source",
    )

    for_r_build: typing.Optional[ForRBuild] = None

    # The last release on which this module was optional.
    #
    # Some modules are optional when they are first released, usually because
    # some vendors of Android devices have their own customizations of the
    # module that they would like to preserve and which cannot yet be achieved
    # through the existing APIs. Once those issues have been resolved then they
    # will become mandatory.
    #
    # This field records the last build release in which they are optional. It
    # defaults to None which indicates that the module was never optional.
    #
    # TODO(b/238203992): remove the following warning once all modules can be
    #  treated as optional at build time.
    #
    # DO NOT use this attr for anything other than controlling whether the
    # generated snapshot uses its own Soong config variable or the common one.
    # That is because this is being temporarily used to force Permission to have
    # its own Soong config variable even though Permission is not actually
    # optional at runtime on a GMS capable device.
    #
    # b/238203992 will make all modules have their own Soong config variable by
    # default at which point this will no longer be needed on Permission and so
    # it can be used to indicate that a module is optional at runtime.
    last_optional_release: typing.Optional[BuildRelease] = None

    # The short name for the module.
    #
    # Defaults to the last part of the apex name.
    short_name: str = ""

    # Additional transformations
    additional_transformations: list[FileTransformation] = None

    def __post_init__(self):
        # If short_name is not set then set it to the last component of the apex
        # name.
        if not self.short_name:
            short_name = self.apex.rsplit(".", 1)[-1]
            object.__setattr__(self, "short_name", short_name)

    def is_bundled(self):
        """Returns true for bundled modules. See BundledMainlineModule."""
        return False

    def transformations(self, build_release, sdk_type):
        """Returns the transformations to apply to this module's snapshot(s)."""
        transformations = []

        config_var = self.configVar

        # If the module is optional then it needs its own Soong config
        # variable to allow it to be managed separately from other modules.
        if self.last_optional_release:
            config_var = ConfigVar(
                namespace=f"{self.short_name}_module",
                name="source_build",
            )

        prefer_handling = build_release.preferHandling
        if prefer_handling == PreferHandling.SOONG_CONFIG:
            sdk_type_prefix = sdk_type.configModuleTypePrefix
            config_module_type_prefix = \
                f"{self.short_name}{sdk_type_prefix}_prebuilt_"
            inserter = SoongConfigBoilerplateInserter(
                "Android.bp",
                configVar=config_var,
                configModuleTypePrefix=config_module_type_prefix)
            transformations.append(inserter)
        elif prefer_handling == PreferHandling.USE_SOURCE_CONFIG_VAR_PROPERTY:
            transformation = UseSourceConfigVarTransformation(
                "Android.bp", configVar=config_var)
            transformations.append(transformation)

        if self.additional_transformations and build_release > R:
            transformations.extend(self.additional_transformations)

        return transformations

    def is_required_for(self, target_build_release):
        """True if this module is required for the target build release."""
        return self.first_release <= target_build_release


@dataclasses.dataclass(frozen=True)
class BundledMainlineModule(MainlineModule):
    """Represents a bundled Mainline module or a platform SDK for module use.

    A bundled module is always preloaded into the platform images.
    """

    # Defaults to the latest build, i.e. the build on which this script is run
    # as bundled modules are, by definition, only needed in this build.
    first_release: BuildRelease = LATEST

    def is_bundled(self):
        return True

    def transformations(self, build_release, sdk_type):
        # Bundled modules are only used on thin branches where the corresponding
        # sources are absent, so skip transformations and keep the default
        # `prefer: false`.
        return []


# List of mainline modules.
MAINLINE_MODULES = [
    MainlineModule(
        apex="com.android.adservices",
        sdks=["adservices-module-sdk"],
        first_release=Tiramisu,
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.appsearch",
        sdks=["appsearch-sdk"],
        first_release=Tiramisu,
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.art",
        sdks=[
            "art-module-sdk",
            "art-module-test-exports",
            "art-module-host-exports",
        ],
        first_release=S,
        # Override the config... fields.
        configVar=ConfigVar(
            namespace="art_module",
            name="source_build",
        ),
    ),
    MainlineModule(
        apex="com.android.btservices",
        sdks=["btservices-module-sdk"],
        first_release=UpsideDownCake,
        # Bluetooth has always been and is still optional.
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.configinfrastructure",
        sdks=["configinfrastructure-sdk"],
        first_release=UpsideDownCake,
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.conscrypt",
        sdks=[
            "conscrypt-module-sdk",
            "conscrypt-module-test-exports",
            "conscrypt-module-host-exports",
        ],
        first_release=Q,
        # No conscrypt java_sdk_library modules are exported to the R snapshot.
        # Conscrypt was updatable in R but the generate_ml_bundle.sh does not
        # appear to generate a snapshot for it.
        for_r_build=None,
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.devicelock",
        sdks=["devicelock-module-sdk"],
        first_release=UpsideDownCake,
        # Treat DeviceLock as optional at build time
        # TODO(b/238203992): remove once all modules are optional at build time.
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.healthfitness",
        sdks=["healthfitness-module-sdk"],
        first_release=UpsideDownCake,
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.ipsec",
        sdks=["ipsec-module-sdk"],
        first_release=R,
        for_r_build=ForRBuild(sdk_libraries=[
            SdkLibrary(
                name="android.net.ipsec.ike",
                shared_library=True,
            ),
        ]),
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.media",
        sdks=["media-module-sdk"],
        first_release=R,
        for_r_build=ForRBuild(sdk_libraries=[
            SdkLibrary(name="framework-media"),
        ]),
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.mediaprovider",
        sdks=["mediaprovider-module-sdk"],
        first_release=R,
        for_r_build=ForRBuild(sdk_libraries=[
            SdkLibrary(name="framework-mediaprovider"),
        ]),
        # MP is a mandatory mainline module but in some cases (b/294190883) this
        # needs to be optional for Android Go on T. GTS tests might be needed to
        # to check the specific condition mentioned in the bug.
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.ondevicepersonalization",
        sdks=["ondevicepersonalization-module-sdk"],
        first_release=Tiramisu,
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.permission",
        sdks=["permission-module-sdk"],
        first_release=R,
        for_r_build=ForRBuild(sdk_libraries=[
            SdkLibrary(name="framework-permission"),
            # framework-permission-s is not needed on R as it contains classes
            # that are provided in R by non-updatable parts of the
            # bootclasspath.
        ]),
        # Although Permission is not, and has never been, optional for GMS
        # capable devices it does need to be treated as optional at build time
        # when building non-GMS devices.
        # TODO(b/238203992): remove once all modules are optional at build time.
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.rkpd",
        sdks=["rkpd-sdk"],
        first_release=UpsideDownCake,
        # Rkpd has always been and is still optional.
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.scheduling",
        sdks=["scheduling-sdk"],
        first_release=S,
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.sdkext",
        sdks=["sdkextensions-sdk"],
        first_release=R,
        for_r_build=ForRBuild(sdk_libraries=[
            SdkLibrary(name="framework-sdkextensions"),
        ]),
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.os.statsd",
        sdks=["statsd-module-sdk"],
        first_release=R,
        for_r_build=ForRBuild(sdk_libraries=[
            SdkLibrary(name="framework-statsd"),
        ]),
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.tethering",
        sdks=["tethering-module-sdk"],
        first_release=R,
        for_r_build=ForRBuild(sdk_libraries=[
            SdkLibrary(name="framework-tethering"),
        ]),
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.uwb",
        sdks=["uwb-module-sdk"],
        first_release=Tiramisu,
        # Uwb has always been and is still optional.
        last_optional_release=LATEST,
    ),
    MainlineModule(
        apex="com.android.wifi",
        sdks=["wifi-module-sdk"],
        first_release=R,
        for_r_build=ForRBuild(sdk_libraries=[
            SdkLibrary(name="framework-wifi"),
        ]),
        # Wifi has always been and is still optional.
        last_optional_release=LATEST,
    ),
]

# List of Mainline modules that currently are never built unbundled. They must
# not specify first_release, and they don't have com.google.android
# counterparts.
BUNDLED_MAINLINE_MODULES = [
    BundledMainlineModule(
        apex="com.android.i18n",
        sdks=[
            "i18n-module-sdk",
            "i18n-module-test-exports",
            "i18n-module-host-exports",
        ],
    ),
    BundledMainlineModule(
        apex="com.android.runtime",
        sdks=[
            "runtime-module-host-exports",
            "runtime-module-sdk",
        ],
    ),
    BundledMainlineModule(
        apex="com.android.tzdata",
        sdks=["tzdata-module-test-exports"],
    ),
]

# List of platform SDKs for Mainline module use.
PLATFORM_SDKS_FOR_MAINLINE = [
    BundledMainlineModule(
        apex="platform-mainline",
        sdks=[
            "platform-mainline-sdk",
            "platform-mainline-test-exports",
        ],
    ),
]


@dataclasses.dataclass
class SdkDistProducer:
    """Produces the DIST_DIR/mainline-sdks and DIST_DIR/stubs directories.

    Builds SDK snapshots for mainline modules and then copies them into the
    DIST_DIR/mainline-sdks directory. Also extracts the sdk_library txt, jar and
    srcjar files from each SDK snapshot and copies them into the DIST_DIR/stubs
    directory.
    """

    # Used to run subprocesses for this.
    subprocess_runner: SubprocessRunner

    # Builds sdk snapshots
    snapshot_builder: SnapshotBuilder

    # The DIST_DIR environment variable.
    dist_dir: str = "uninitialized-dist"

    # The path to this script. It may be inserted into files that are
    # transformed to document where the changes came from.
    script: str = sys.argv[0]

    # The path to the mainline-sdks dist directory for unbundled modules.
    #
    # Initialized in __post_init__().
    mainline_sdks_dir: str = dataclasses.field(init=False)

    # The path to the mainline-sdks dist directory for bundled modules and
    # platform SDKs.
    #
    # Initialized in __post_init__().
    bundled_mainline_sdks_dir: str = dataclasses.field(init=False)

    def __post_init__(self):
        self.mainline_sdks_dir = os.path.join(self.dist_dir, "mainline-sdks")
        self.bundled_mainline_sdks_dir = os.path.join(self.dist_dir,
                                                      "bundled-mainline-sdks")

    def prepare(self):
        pass

    def produce_dist(self, modules, build_releases):
        # Prepare the dist directory for the sdks.
        self.prepare()

        # Group build releases so that those with the same Soong environment are
        # run consecutively to avoid having to regenerate ninja files.
        grouped_by_env = defaultdict(list)
        for build_release in build_releases:
            grouped_by_env[str(build_release.soong_env)].append(build_release)
        ordered = [br for _, group in grouped_by_env.items() for br in group]

        for build_release in ordered:
            # Only build modules that are required for this build release.
            filtered_modules = [
                m for m in modules if m.is_required_for(build_release)
            ]
            if filtered_modules:
                print(f"Building SDK snapshots for {build_release.name}"
                      f" build release")
                build_release.creator(build_release, self, filtered_modules)

    def product_dist_for_build_r(self, build_release, modules):
        # Although we only need a subset of the files that a java_sdk_library
        # adds to an sdk snapshot generating the whole snapshot is the simplest
        # way to ensure that all the necessary files are produced.

        # Filter out any modules that do not provide sdk for R.
        modules = [m for m in modules if m.for_r_build]

        snapshot_dir = self.snapshot_builder.build_snapshots_for_build_r(
            build_release, modules)
        self.populate_unbundled_dist(build_release, modules, snapshot_dir)

    def produce_unbundled_dist_for_build_release(self, build_release, modules):
        modules = [m for m in modules if not m.is_bundled()]
        snapshots_dir = self.snapshot_builder.build_snapshots(
            build_release, modules)
        if build_release.generate_gantry_metadata_and_api_diff:
            target_dict = self.snapshot_builder.build_sdk_scope_targets(
                build_release, modules)
            self.snapshot_builder.build_snapshot_gantry_metadata_and_api_diff(
                modules, target_dict, snapshots_dir)
        self.populate_unbundled_dist(build_release, modules, snapshots_dir)
        return snapshots_dir

    def produce_bundled_dist_for_build_release(self, build_release, modules):
        modules = [m for m in modules if m.is_bundled()]
        if modules:
            snapshots_dir = self.snapshot_builder.build_snapshots(
                build_release, modules)
            self.populate_bundled_dist(build_release, modules, snapshots_dir)

    def dist_sdk_snapshot_gantry_metadata_and_api_diff(self, sdk_dist_dir, sdk,
                                                       module, snapshots_dir):
        """Copy the sdk snapshot api diff file to a dist directory."""
        sdk_type = sdk_type_from_name(sdk)
        if not sdk_type.providesApis:
            return

        sdk_dist_module_subdir = os.path.join(sdk_dist_dir, module.apex)
        sdk_dist_subdir = os.path.join(sdk_dist_module_subdir, "sdk")
        os.makedirs(sdk_dist_subdir, exist_ok=True)
        sdk_api_diff_path = sdk_snapshot_api_diff_file(snapshots_dir, sdk)
        shutil.copy(sdk_api_diff_path, sdk_dist_subdir)

        sdk_gantry_metadata_json_path = sdk_snapshot_gantry_metadata_json_file(
            snapshots_dir, sdk)
        sdk_dist_gantry_metadata_json_path = os.path.join(
            sdk_dist_module_subdir, "gantry-metadata.json")
        shutil.copy(sdk_gantry_metadata_json_path,
                    sdk_dist_gantry_metadata_json_path)

    def dist_generate_sdk_supported_modules_file(self, modules):
        sdk_modules_file = os.path.join(self.dist_dir, "sdk-modules.txt")
        os.makedirs(os.path.dirname(sdk_modules_file), exist_ok=True)
        with open(sdk_modules_file, "w", encoding="utf8") as file:
            for module in modules:
                if module in MAINLINE_MODULES:
                    file.write(aosp_to_google_name(module.apex) + "\n")

    def populate_unbundled_dist(self, build_release, modules, snapshots_dir):
        build_release_dist_dir = os.path.join(self.mainline_sdks_dir,
                                              build_release.sub_dir)
        for module in modules:
            for sdk in module.sdks:
                sdk_dist_dir = os.path.join(build_release_dist_dir, SDK_VERSION)
                if build_release.generate_gantry_metadata_and_api_diff:
                    self.dist_sdk_snapshot_gantry_metadata_and_api_diff(
                        sdk_dist_dir, sdk, module, snapshots_dir)
                self.populate_dist_snapshot(build_release, module, sdk,
                                            sdk_dist_dir, snapshots_dir)

    def populate_bundled_dist(self, build_release, modules, snapshots_dir):
        sdk_dist_dir = self.bundled_mainline_sdks_dir
        for module in modules:
            for sdk in module.sdks:
                self.populate_dist_snapshot(build_release, module, sdk,
                                            sdk_dist_dir, snapshots_dir)

    def populate_dist_snapshot(self, build_release, module, sdk, sdk_dist_dir,
                               snapshots_dir):
        sdk_type = sdk_type_from_name(sdk)
        subdir = sdk_type.name

        sdk_dist_subdir = os.path.join(sdk_dist_dir, module.apex, subdir)
        sdk_path = sdk_snapshot_zip_file(snapshots_dir, sdk)
        sdk_type = sdk_type_from_name(sdk)
        transformations = module.transformations(build_release, sdk_type)
        self.dist_sdk_snapshot_zip(
            build_release, sdk_path, sdk_dist_subdir, transformations)

    def dist_sdk_snapshot_zip(
        self, build_release, src_sdk_zip, sdk_dist_dir, transformations):
        """Copy the sdk snapshot zip file to a dist directory.

        If no transformations are provided then this simply copies the show sdk
        snapshot zip file to the dist dir. However, if transformations are
        provided then the files to be transformed are extracted from the
        snapshot zip file, they are transformed to files in a separate directory
        and then a new zip file is created in the dist directory with the
        original files replaced by the newly transformed files. build_release is
        provided for transformations if it is needed.
        """
        os.makedirs(sdk_dist_dir, exist_ok=True)
        dest_sdk_zip = os.path.join(sdk_dist_dir, os.path.basename(src_sdk_zip))
        print(f"Copying sdk snapshot {src_sdk_zip} to {dest_sdk_zip}")

        # If no transformations are provided then just copy the zip file
        # directly.
        if len(transformations) == 0:
            shutil.copy(src_sdk_zip, sdk_dist_dir)
            return

        with tempfile.TemporaryDirectory() as tmp_dir:
            # Create a single pattern that will match any of the paths provided
            # in the transformations.
            pattern = "|".join(
                [f"({re.escape(t.path)})" for t in transformations])

            # Extract the matching files from the zip into the temporary
            # directory.
            extract_matching_files_from_zip(src_sdk_zip, tmp_dir, pattern)

            # Apply the transformations to the extracted files in situ.
            apply_transformations(self, tmp_dir, transformations, build_release)

            # Replace the original entries in the zip with the transformed
            # files.
            paths = [transformation.path for transformation in transformations]
            copy_zip_and_replace(self, src_sdk_zip, dest_sdk_zip, tmp_dir,
                                 paths)


def print_command(env, cmd):
    print(" ".join([f"{name}={value}" for name, value in env.items()] + cmd))


def sdk_library_files_pattern(*, scope_pattern=r"[^/]+", name_pattern=r"[^/]+"):
    """Return a pattern to match sdk_library related files in an sdk snapshot"""
    return rf"sdk_library/{scope_pattern}/{name_pattern}\.(txt|jar|srcjar)"


def extract_matching_files_from_zip(zip_path, dest_dir, pattern):
    """Extracts files from a zip file into a destination directory.

    The extracted files are those that match the specified regular expression
    pattern.
    """
    os.makedirs(dest_dir, exist_ok=True)
    with zipfile.ZipFile(zip_path) as zip_file:
        for filename in zip_file.namelist():
            if re.match(pattern, filename):
                print(f"    extracting {filename}")
                zip_file.extract(filename, dest_dir)


def copy_zip_and_replace(producer, src_zip_path, dest_zip_path, src_dir, paths):
    """Copies a zip replacing some of its contents in the process.

     The files to replace are specified by the paths parameter and are relative
     to the src_dir.
    """
    # Get the absolute paths of the source and dest zip files so that they are
    # not affected by a change of directory.
    abs_src_zip_path = os.path.abspath(src_zip_path)
    abs_dest_zip_path = os.path.abspath(dest_zip_path)

    # Make sure that all the paths being added to the zip file have a fixed
    # timestamp so that the contents of the zip file do not depend on when this
    # script is run, only the inputs.
    set_default_timestamp(src_dir, paths)

    producer.subprocess_runner.run(
        ["zip", "-q", abs_src_zip_path, "--out", abs_dest_zip_path] + paths,
        # Change into the source directory before running zip.
        cwd=src_dir)


def apply_transformations(producer, tmp_dir, transformations, build_release):
    for transformation in transformations:
        path = os.path.join(tmp_dir, transformation.path)

        # Record the timestamp of the file.
        modified = os.path.getmtime(path)

        # Transform the file.
        transformation.apply(producer, path, build_release)

        # Reset the timestamp of the file to the original timestamp before the
        # transformation was applied.
        os.utime(path, (modified, modified))


def create_producer(tool_path, skip_allowed_deps_check):
    # Variables initialized from environment variables that are set by the
    # calling mainline_modules_sdks.sh.
    out_dir = os.environ["OUT_DIR"]
    dist_dir = os.environ["DIST_DIR"]

    top_dir = os.environ["ANDROID_BUILD_TOP"]
    tool_path = os.path.relpath(tool_path, top_dir)
    tool_path = tool_path.replace(".py", ".sh")

    subprocess_runner = SubprocessRunner()
    snapshot_builder = SnapshotBuilder(
        tool_path=tool_path,
        subprocess_runner=subprocess_runner,
        out_dir=out_dir,
        skip_allowed_deps_check=skip_allowed_deps_check,
    )
    return SdkDistProducer(
        subprocess_runner=subprocess_runner,
        snapshot_builder=snapshot_builder,
        dist_dir=dist_dir,
    )


def aosp_to_google(module):
    """Transform an AOSP module into a Google module"""
    new_apex = aosp_to_google_name(module.apex)
    # Create a copy of the AOSP module with the internal specific APEX name.
    return dataclasses.replace(module, apex=new_apex)


def aosp_to_google_name(name):
    """Transform an AOSP module name into a Google module name"""
    return name.replace("com.android.", "com.google.android.")


def google_to_aosp_name(name):
    """Transform a Google module name into an AOSP module name"""
    return name.replace("com.google.android.", "com.android.")


@dataclasses.dataclass(frozen=True)
class SdkType:
    name: str

    configModuleTypePrefix: str

    providesApis: bool = False


Sdk = SdkType(
    name="sdk",
    configModuleTypePrefix="",
    providesApis=True,
)
HostExports = SdkType(
    name="host-exports",
    configModuleTypePrefix="_host_exports",
)
TestExports = SdkType(
    name="test-exports",
    configModuleTypePrefix="_test_exports",
)


def sdk_type_from_name(name):
    if name.endswith("-sdk"):
        return Sdk
    if name.endswith("-host-exports"):
        return HostExports
    if name.endswith("-test-exports"):
        return TestExports

    raise Exception(f"{name} is not a valid sdk name, expected it to end"
                    f" with -(sdk|host-exports|test-exports)")


def filter_modules(modules, target_build_apps):
    if target_build_apps:
        target_build_apps = target_build_apps.split()
        return [m for m in modules if m.apex in target_build_apps]
    return modules


def main(args):
    """Program entry point."""
    if not os.path.exists("build/make/core/Makefile"):
        sys.exit("This script must be run from the top of the tree.")

    args_parser = argparse.ArgumentParser(
        description="Build snapshot zips for consumption by Gantry.")
    args_parser.add_argument(
        "--tool-path",
        help="The path to this tool.",
        default="unspecified",
    )
    args_parser.add_argument(
        "--build-release",
        action="append",
        choices=[br.name for br in ALL_BUILD_RELEASES],
        help="A target build for which snapshots are required. "
        "If it is \"latest\" then Mainline module SDKs from platform and "
        "bundled modules are included.",
    )
    args_parser.add_argument(
        "--build-platform-sdks-for-mainline",
        action="store_true",
        help="Also build the platform SDKs for Mainline modules. "
        "Defaults to true when TARGET_BUILD_APPS is not set. "
        "Applicable only if the \"latest\" build release is built.",
    )
    args_parser.add_argument(
        "--skip-allowed-deps-check",
        action="store_true",
        help="Skip apex-allowed-deps-check.",
    )
    args = args_parser.parse_args(args)

    build_releases = ALL_BUILD_RELEASES
    if args.build_release:
        selected_build_releases = {b.lower() for b in args.build_release}
        build_releases = [
            b for b in build_releases
            if b.name.lower() in selected_build_releases
        ]

    target_build_apps = os.environ.get("TARGET_BUILD_APPS")
    modules = filter_modules(MAINLINE_MODULES + BUNDLED_MAINLINE_MODULES,
                             target_build_apps)

    # Also build the platform Mainline SDKs either if no specific modules are
    # requested or if --build-platform-sdks-for-mainline is given.
    if not target_build_apps or args.build_platform_sdks_for_mainline:
        modules += PLATFORM_SDKS_FOR_MAINLINE

    producer = create_producer(args.tool_path, args.skip_allowed_deps_check)
    producer.dist_generate_sdk_supported_modules_file(modules)
    producer.produce_dist(modules, build_releases)


if __name__ == "__main__":
    main(sys.argv[1:])
