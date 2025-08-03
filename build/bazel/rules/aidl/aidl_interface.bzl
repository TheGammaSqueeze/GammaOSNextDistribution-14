# Copyright (C) 2022 The Android Open Source Project
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

load("//build/bazel/rules/aidl:aidl_library.bzl", "aidl_library")
load("//build/bazel/rules/cc:cc_aidl_library.bzl", "cc_aidl_library")
load("//build/bazel/rules/java:java_aidl_library.bzl", "java_aidl_library")

JAVA = "java"
CPP = "cpp"
NDK = "ndk"
#TODO(b/246803961) Add support for rust backend

def _hash_file(name, version):
    return "aidl_api/{}/{}/.hash".format(name, version)

def _check_versions_with_info(versions_with_info):
    for version_with_info in versions_with_info:
        for dep in version_with_info.get("deps", []):
            parts = dep.split("-V")
            if len(parts) < 2 or not parts[-1].isdigit():
                fail("deps in versions_with_info must specify its version, but", dep)

    versions = []

    # ensure that all versions are ints
    for info in versions_with_info:
        version = info["version"]
        if version.isdigit() == False:
            fail("version %s is not an integer".format(version))

        versions.append(int(version))

    if versions != sorted(versions):
        fail("versions should be sorted")

    for i, v in enumerate(versions):
        if i > 0:
            if v == versions[i - 1]:
                fail("duplicate version found:", v)
        if v <= 0:
            fail("all versions should be > 0, but found version:", v)

def _create_latest_version_aliases(name, last_version_name, backend_configs, **kwargs):
    latest_name = name + "-latest"
    native.alias(
        name = latest_name,
        actual = ":" + last_version_name,
        **kwargs
    )
    for lang in backend_configs.keys():
        language_binding_name = last_version_name + "-" + lang
        native.alias(
            name = latest_name + "-" + lang,
            actual = ":" + language_binding_name,
            **kwargs
        )

def _versioned_name(name, version):
    if version == "":
        return name

    return name + "-V" + version

# https://cs.android.com/android/platform/superproject/+/master:system/tools/aidl/build/aidl_interface.go;l=782-799;drc=5390d9a42f5e4f99ccb3a84068f554d948cb62b9
def _next_version(versions_with_info, unstable):
    if unstable:
        return ""

    if versions_with_info == None or len(versions_with_info) == 0:
        return "1"

    return str(int(versions_with_info[-1]["version"]) + 1)

def _is_config_enabled(config):
    if config == None:
        return False

    for key in config:
        if key not in ["enabled", "min_sdk_version", "tags", "additional_dynamic_deps"]:
            fail("unknown property in aidl configuration: " + str(key))

    return config.get("enabled", False) == True

def aidl_interface(
        name,
        deps = [],
        hdrs = [],
        strip_import_prefix = "",
        srcs = None,
        flags = None,
        java_config = None,
        cpp_config = None,
        ndk_config = None,
        stability = None,
        versions_with_info = [],
        unstable = False,
        tags = [],
        # TODO(b/261208761): Support frozen attr
        frozen = False,
        **kwargs):
    """aidl_interface creates a versioned aidl_libraries and language-specific *_aidl_libraries

    This macro loops over the list of required versions and searches for all
    *.aidl source files located under the path `aidl_api/<version label/`.
    For each version, an `aidl_library` is created with the corresponding sources.
    For each `aidl_library`, a language-binding library *_aidl_library is created
    based on the values passed to the `backends` argument.

    Arguments:
        name:                   string, base name of generated targets: <module-name>-V<version number>-<language-type>
        deps:                   List[AidlGenInfo], a list of other aidl_libraries that all versions of this interface depend on
        hdrs:                   List[AidlGenInfo], a list of other aidl_libraries that all versions of this interface depend on but will not link against for C++
        strip_import_prefix:    str, a local directory to pass to the AIDL compiler to satisfy imports
        srcs:                   List[file], a list of files to include in the development (unversioned) version of the aidl_interface
        flags:                  List[string], a list of flags to pass to the AIDL compiler
        java_config:            Dict{"enabled": bool}, config for java backend
        cpp_config:             Dict{"enabled": bool, "min_sdk_version": string, "additional_dynamic_deps": List[Label]}, config for cpp backend
        ndk_config:             Dict{"enabled": bool, "min_sdk_version": string, "additional_dynamic_deps": List[Label]}, config for ndk backend
        stability:              string, stability promise of the interface. Currently, only supports "vintf"
        backends:               List[string], a list of the languages to generate bindings for
    """

    # When versions_with_info is set, versions is no-op.
    # TODO(b/244349745): Modify bp2build to skip convert versions if versions_with_info is set
    if (len(versions_with_info) == 0 and srcs == None):
        fail("must specify at least versions_with_info or srcs")

    if len(versions_with_info) == 0:
        if frozen == True:
            fail("frozen cannot be set without versions_with_info attr being set")
    elif unstable == True:
        # https://cs.android.com/android/platform/superproject/+/master:system/tools/aidl/build/aidl_interface.go;l=872;drc=5390d9a42f5e4f99ccb3a84068f554d948cb62b9
        fail("cannot have versions for unstable interface")

    aidl_flags = ["--structured"]

    enabled_backend_configs = {}
    if _is_config_enabled(java_config):
        enabled_backend_configs[JAVA] = java_config
    if _is_config_enabled(cpp_config):
        enabled_backend_configs[CPP] = cpp_config
    if _is_config_enabled(ndk_config):
        enabled_backend_configs[NDK] = ndk_config

    if stability != None:
        if unstable == True:
            fail("stability must be unset when unstable is true")
        if stability == "vintf":
            aidl_flags.append("--stability=" + stability)

            # TODO(b/245738285): Add support for vintf stability in java backend
            if JAVA in enabled_backend_configs:
                enabled_backend_configs.pop(JAVA)
        else:
            # https://cs.android.com/android/platform/superproject/+/master:system/tools/aidl/build/aidl_interface.go;l=329;drc=e88d9a9b14eafb064a234d555a5cd96de97ca9e2
            # only vintf is allowed currently
            fail("stability must be unset or \"vintf\"")

    # next_version will be the last specified version + 1.
    # https://cs.android.com/android/platform/superproject/+/master:system/tools/aidl/build/aidl_interface.go;l=791?q=system%2Ftools%2Faidl%2Fbuild%2Faidl_interface.go
    next_version = None

    if len(versions_with_info) > 0:
        _check_versions_with_info(versions_with_info)
        next_version = _next_version(versions_with_info, False)

        for version_with_info in versions_with_info:
            deps_for_version = version_with_info.get("deps", [])
            version = version_with_info.get("version")
            flags_for_version = aidl_flags

            if version == next_version and frozen == False and flags != None:
                flags_for_version.extend(flags)

            create_aidl_binding_for_backends(
                name = name,
                version = version_with_info["version"],
                deps = deps_for_version,
                hdrs = hdrs,
                aidl_flags = flags_for_version,
                backend_configs = enabled_backend_configs,
                tags = tags,
                **kwargs
            )

        _create_latest_version_aliases(
            name,
            _versioned_name(name, versions_with_info[-1]["version"]),
            enabled_backend_configs,
            tags = tags,
            **kwargs
        )
    else:
        next_version = _next_version(versions_with_info, unstable)

    # https://cs.android.com/android/platform/superproject/+/master:system/tools/aidl/build/aidl_interface.go;l=941;drc=5390d9a42f5e4f99ccb3a84068f554d948cb62b9
    # Create aidl binding for next_version with srcs
    if srcs and len(srcs) > 0:
        create_aidl_binding_for_backends(
            name = name,
            version = next_version,
            srcs = srcs,
            strip_import_prefix = strip_import_prefix,
            deps = deps,
            hdrs = hdrs,
            aidl_flags = aidl_flags,
            backend_configs = enabled_backend_configs,
            tags = tags,
            **kwargs
        )

def create_aidl_binding_for_backends(
        name,
        version = None,
        srcs = None,
        strip_import_prefix = "",
        deps = None,
        hdrs = None,
        aidl_flags = [],
        backend_configs = {},
        tags = [],
        **kwargs):
    """
    Create aidl_library target and corrending <backend>_aidl_library target for a given version

    Arguments:
        name:                   string, base name of the aidl interface
        version:                string, version of the aidl interface
        srcs:                   List[Label] list of unversioned AIDL srcs
        strip_import_prefix     string, the prefix to strip the paths of the .aidl files in srcs
        deps:                   List[AidlGenInfo], a list of other aidl_libraries that the version depends on
        hdrs:                   List[AidlGenInfo], a list of other aidl_libraries that the version depends on but will not link against for C++
                                the label of the targets have format <aidl-interface>-V<version_number>
        aidl_flags:             List[string], a list of flags to pass to the AIDL compiler
        backends:               List[string], a list of the languages to generate bindings for
    """
    aidl_library_name = _versioned_name(name, version)

    # srcs is None when create_aidl_binding_for_backends is called with a
    # frozen version specified via versions or versions_with_info.
    # next_version being equal to "" means this is an unstable version and
    # we should use srcs instead
    if version != "":
        aidl_flags = aidl_flags + ["--version=" + version]

    hash_file = None

    if srcs == None:
        if version == "":
            fail("need srcs for unversioned interface")
        strip_import_prefix = "aidl_api/{}/{}".format(name, version)
        srcs = native.glob([strip_import_prefix + "/**/*.aidl"])
        hash_file = _hash_file(name, version)

    aidl_library(
        name = aidl_library_name,
        deps = deps + hdrs,
        hash_file = hash_file,
        version = version,
        strip_import_prefix = strip_import_prefix,
        srcs = srcs,
        flags = aidl_flags,
        # The language-specific backends will set more appropriate apex_available values.
        tags = tags + ["apex_available=//apex_available:anyapex"],
        **kwargs
    )

    for lang, config in backend_configs.items():
        # https://cs.android.com/android/platform/superproject/+/master:system/tools/aidl/build/aidl_gen_rule.go;l=207;drc=a858ae7039b876a30002a1130f24196915a859a4
        min_sdk_version = "current"
        if "min_sdk_version" in config:
            min_sdk_version = config["min_sdk_version"]

        if lang == JAVA:
            #TODO(b/285574832) re-enable Java backend
            continue
            java_aidl_library(
                name = aidl_library_name + "-java",
                deps = [":" + aidl_library_name],
                tags = tags + config.get("tags", []),
                # TODO(b/249276008): Pass min_sdk_version to java_aidl_library
                **(kwargs | {"target_compatible_with": ["//build/bazel_common_rules/platforms/os:android"]})
            )
        elif lang == CPP or lang == NDK:
            dynamic_deps = []
            cppflags = []

            # https://cs.android.com/android/platform/superproject/+/master:system/tools/aidl/build/aidl_interface_backends.go;l=564;drc=0517d97079d4b08f909e7f35edfa33b88fcc0d0e
            if deps != None:
                # For each aidl_library target label versioned_name, there's an
                # associated cc_library_shared target with label versioned_name-<cpp|ndk>
                dynamic_deps.extend(["{}-{}".format(dep, lang) for dep in deps])

            # https://cs.android.com/android/platform/superproject/+/master:system/tools/aidl/build/aidl_interface_backends.go;l=111;drc=ef9f1352a1a8fec7bb134b1c713e13fc3ccee651
            if lang == CPP:
                dynamic_deps.extend([
                    "//frameworks/native/libs/binder:libbinder",
                    "//system/core/libutils:libutils",
                ])
            elif lang == NDK:
                dynamic_deps = dynamic_deps + select({
                    "//build/bazel/rules/apex:android-in_apex": ["//frameworks/native/libs/binder/ndk:libbinder_ndk_stub_libs_current"],
                    "//conditions:default": ["//frameworks/native/libs/binder/ndk:libbinder_ndk"],
                })

                # https://source.corp.google.com/android/system/tools/aidl/build/aidl_interface_backends.go;l=120;rcl=18dd931bde35b502545b7a52987e2363042c151c
                cppflags = ["-DBINDER_STABILITY_SUPPORT"]

            if "additional_dynamic_deps" in config:
                dynamic_deps += config["additional_dynamic_deps"]

            if hasattr(kwargs, "tidy_checks_as_errors"):
                fail("tidy_checks_as_errors cannot be overriden for aidl_interface cc_libraries")
            tidy_checks_as_errors = [
                "*",
                "-clang-analyzer-deadcode.DeadStores",  # b/253079031
                "-clang-analyzer-cplusplus.NewDeleteLeaks",  # b/253079031
                "-clang-analyzer-optin.performance.Padding",  # b/253079031
            ]

            cc_aidl_library(
                name = "{}-{}".format(aidl_library_name, lang),
                make_shared = True,
                cppflags = cppflags,
                deps = [":" + aidl_library_name],
                dynamic_deps = dynamic_deps,
                lang = lang,
                min_sdk_version = min_sdk_version,
                tidy = "local",
                tidy_checks_as_errors = tidy_checks_as_errors,
                tidy_gen_header_filter = True,
                tags = tags + config.get("tags", []),
                **kwargs
            )
