# Copyright (C) 2021 The Android Open Source Project
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

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_tools//tools/cpp:toolchain_utils.bzl", "find_cpp_toolchain")
load("@soong_injection//android:constants.bzl", android_constants = "constants")
load("@soong_injection//api_levels:platform_versions.bzl", "platform_versions")
load("@soong_injection//cc_toolchain:config_constants.bzl", cc_constants = "constants")
load("//build/bazel/rules:common.bzl", "strip_bp2build_label_suffix")
load("//build/bazel/rules/common:api.bzl", "api")

_static_bionic_targets = ["//bionic/libc:libc_bp2build_cc_library_static", "//bionic/libdl:libdl_bp2build_cc_library_static", "//bionic/libm:libm_bp2build_cc_library_static"]

# When building a APEX, stub libraries of libc, libdl, libm should be used in linking.
_bionic_stub_targets = [
    "//bionic/libc:libc_stub_libs_current",
    "//bionic/libdl:libdl_stub_libs_current",
    "//bionic/libm:libm_stub_libs_current",
]

# When building an android_app/android_test that set an sdk_version, NDK variant of stub libraries of libc, libdl, libm should be used in linking.
_bionic_ndk_stub_targets = [
    "//bionic/libc:libc.ndk_stub_libs_current",
    "//bionic/libdl:libdl.ndk_stub_libs_current",
    "//bionic/libm:libm.ndk_stub_libs_current",
]

# The default system_dynamic_deps value for cc libraries. This value should be
# used if no value for system_dynamic_deps is specified.
system_dynamic_deps_defaults = select({
    "//build/bazel/rules/apex:android-in_apex": _bionic_stub_targets,
    "//build/bazel/rules/apex:android-non_apex": _bionic_stub_targets,
    "//build/bazel/rules/apex:linux_bionic-in_apex": _bionic_stub_targets,
    "//build/bazel/rules/apex:linux_bionic-non_apex": _bionic_stub_targets,
    "//build/bazel/rules/apex:unbundled_app": _bionic_ndk_stub_targets,
    "//conditions:default": [],
})

system_static_deps_defaults = select({
    "//build/bazel/rules/apex:android-in_apex": _bionic_stub_targets,
    "//build/bazel/rules/apex:android-non_apex": _static_bionic_targets,
    "//build/bazel/rules/apex:linux_bionic-in_apex": _bionic_stub_targets,
    "//build/bazel/rules/apex:linux_bionic-non_apex": _static_bionic_targets,
    "//build/bazel/rules/apex:unbundled_app": _bionic_ndk_stub_targets,
    "//conditions:default": [],
})

# List comes from here:
# https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/cc.go;l=1441;drc=9fd9129b5728602a4768e8e8e695660b683c405e
_bionic_libs = ["libc", "libm", "libdl", "libdl_android", "linker", "linkerconfig"]

# Comes from here:
# https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/cc.go;l=1450;drc=9fd9129b5728602a4768e8e8e695660b683c405e
_bootstrap_libs = ["libclang_rt.hwasan"]

future_version = 10000

CcSanitizerLibraryInfo = provider(
    "Denotes which sanitizer libraries to include",
    fields = {
        "propagate_ubsan_deps": ("True if any ubsan sanitizers are " +
                                 "enabled on any transitive deps, or " +
                                 "the current target. False otherwise"),
    },
)

# Must be called from within a rule (not a macro) so that the features select
# has been resolved.
def get_sanitizer_lib_info(features, deps):
    propagate_ubsan_deps = False
    for feature in features:
        if feature.startswith("ubsan_"):
            propagate_ubsan_deps = True
            break
    if not propagate_ubsan_deps:
        for dep in deps:
            if (CcSanitizerLibraryInfo in dep and
                dep[CcSanitizerLibraryInfo].propagate_ubsan_deps):
                propagate_ubsan_deps = True
                break
    return CcSanitizerLibraryInfo(
        propagate_ubsan_deps = propagate_ubsan_deps,
    )

def _sanitizer_deps_impl(ctx):
    if (CcSanitizerLibraryInfo in ctx.attr.dep and
        ctx.attr.dep[CcSanitizerLibraryInfo].propagate_ubsan_deps):
        # To operate correctly with native cc_binary and cc_sharedLibrary,
        # copy the linker inputs and ensure that this target is marked as the
        # "owner". Otherwise, upstream targets may drop these linker inputs.
        # See b/264894507.
        libraries = [
            lib
            for input in ctx.attr._ubsan_library[CcInfo].linking_context.linker_inputs.to_list()
            for lib in input.libraries
        ]
        new_linker_input = cc_common.create_linker_input(
            owner = ctx.label,
            libraries = depset(direct = libraries),
        )
        linking_context = cc_common.create_linking_context(
            linker_inputs = depset(direct = [new_linker_input]),
        )
        return [CcInfo(linking_context = linking_context)]
    return [CcInfo()]

# This rule is essentially a workaround to be able to add dependencies
# conditionally based on provider values
sanitizer_deps = rule(
    implementation = _sanitizer_deps_impl,
    doc = "A rule that propagates given sanitizer dependencies if the " +
          "proper conditions are met",
    attrs = {
        "dep": attr.label(
            mandatory = True,
            doc = "library to check for sanitizer dependency propagation",
        ),
        "_ubsan_library": attr.label(
            default = "//prebuilts/clang/host/linux-x86:libclang_rt.ubsan_minimal",
            doc = "The library target corresponding to the undefined " +
                  "behavior sanitizer library to be used",
        ),
    },
    provides = [CcInfo],
)

def sdk_version_feature_from_parsed_version(version):
    return "sdk_version_" + str(version)

def _create_sdk_version_features_map():
    version_feature_map = {}
    for level in api.api_levels.values():
        version_feature_map["//build/bazel/rules/apex:min_sdk_version_" + str(level)] = [sdk_version_feature_from_parsed_version(level)]
    version_feature_map["//conditions:default"] = [sdk_version_feature_from_parsed_version(future_version)]

    return version_feature_map

sdk_version_features = select(_create_sdk_version_features_map())

def add_lists_defaulting_to_none(*args):
    """Adds multiple lists, but is well behaved with a `None` default."""
    combined = None
    for arg in args:
        if arg != None:
            if combined == None:
                combined = []
            combined += arg

    return combined

# get_includes_paths expects a rule context, a list of directories, and
# whether the directories are package-relative and returns a list of exec
# root-relative paths. This handles the need to search for files both in the
# source tree and generated files.
def get_includes_paths(ctx, dirs, package_relative = True):
    execution_relative_dirs = []
    for rel_dir in dirs:
        if rel_dir == ".":
            rel_dir = ""
        execution_rel_dir = rel_dir
        if package_relative:
            execution_rel_dir = ctx.label.package
            if len(rel_dir) > 0:
                execution_rel_dir = execution_rel_dir + "/" + rel_dir

        # To allow this repo to be used as an external one.
        repo_prefix_dir = execution_rel_dir
        if ctx.label.workspace_root != "":
            repo_prefix_dir = ctx.label.workspace_root + "/" + execution_rel_dir
        execution_relative_dirs.append(repo_prefix_dir)

        # to support generated files, we also need to export includes relatives to the bin directory
        if not execution_rel_dir.startswith("/"):
            execution_relative_dirs.append(ctx.bin_dir.path + "/" + execution_rel_dir)
    return execution_relative_dirs

def create_ccinfo_for_includes(
        ctx,
        hdrs = [],
        includes = [],
        absolute_includes = [],
        system_includes = [],
        deps = []):
    # Create a compilation context using the string includes of this target.
    compilation_context = cc_common.create_compilation_context(
        headers = depset(hdrs),
        includes = depset(
            get_includes_paths(ctx, includes) +
            get_includes_paths(ctx, absolute_includes, package_relative = False),
        ),
        system_includes = depset(get_includes_paths(ctx, system_includes)),
    )

    # Combine this target's compilation context with those of the deps; use only
    # the compilation context of the combined CcInfo.
    cc_infos = [dep[CcInfo] for dep in deps]
    cc_infos.append(CcInfo(compilation_context = compilation_context))
    combined_info = cc_common.merge_cc_infos(cc_infos = cc_infos)

    return CcInfo(compilation_context = combined_info.compilation_context)

def is_external_directory(package_name):
    if package_name.startswith("external"):
        return True
    if package_name.startswith("hardware"):
        paths = package_name.split("/")
        if len(paths) < 2:
            return True
        secondary_path = paths[1]
        if secondary_path in ["google", "interfaces", "ril"]:
            return False
        return not secondary_path.startswith("libhardware")
    if package_name.startswith("vendor"):
        paths = package_name.split("/")
        if len(paths) < 2:
            return True
        secondary_path = paths[1]
        return "google" not in secondary_path
    return False

# TODO: Move this to a common rule dir, instead of a cc rule dir. Nothing here
# should be cc specific, except that the current callers are (only) cc rules.
def parse_sdk_version(version):
    if version == "apex_inherit":
        # use the version determined by the transition value.
        return sdk_version_features + [sdk_version_feature_from_parsed_version("apex_inherit")]

    return [sdk_version_feature_from_parsed_version(parse_apex_sdk_version(version))]

def parse_apex_sdk_version(version):
    if version == "" or version == "current" or version == "10000":
        return future_version
    elif version in api.api_levels.keys():
        return api.api_levels[version]
    elif version.isdigit():
        version = int(version)
        if version in api.api_levels.values():
            return version
        elif version == platform_versions.platform_sdk_version:
            # For internal branch states, support parsing a finalized version number
            # that's also still in
            # platform_versions.platform_version_active_codenames, but not api.api_levels.
            #
            # This happens a few months each year on internal branches where the
            # internal master branch has a finalized API, but is not released yet,
            # therefore the Platform_sdk_version is usually latest AOSP dessert
            # version + 1. The generated api.api_levels map sets these to 9000 + i,
            # where i is the index of the current/future version, so version is not
            # in the api.api_levels.values() list, but it is a valid sdk version.
            #
            # See also b/234321488#comment2
            return version
    fail("Unknown sdk version: %s, could not be parsed as " % version +
         "an integer and/or is not a recognized codename. Valid api levels are:" +
         str(api.api_levels))

CPP_EXTENSIONS = ["cc", "cpp", "c++"]

C_EXTENSIONS = ["c"]

_HEADER_EXTENSIONS = ["h", "hh", "hpp", "hxx", "h++", "inl", "inc", "ipp", "h.generic"]

def get_non_header_srcs(input_srcs, exclude_srcs = [], source_extensions = None, header_extensions = _HEADER_EXTENSIONS):
    """get_non_header_srcs returns a list of srcs that do not have header extensions and aren't in the exclude srcs list

    Args:
        input_srcs (list[File]): list of files to filter
        exclude_srcs (list[File]): list of files that should be excluded from the returned list
        source_extensions (list[str]): list of extensions that designate sources.
            If None, all extensions are valid. Otherwise only source with these extensions are returned
        header_extensions (list[str]): list of extensions that designate headers
    Returns:
        srcs, hdrs (list[File], list[File]): tuple of lists of files; srcs have non-header extension and are not excluded,
            and hdrs are files with header extensions
    """
    srcs = []
    hdrs = []
    for s in input_srcs:
        is_source = not source_extensions or s.extension in source_extensions
        if s.extension in header_extensions:
            hdrs.append(s)
        elif is_source and s not in exclude_srcs:
            srcs.append(s)
    return srcs, hdrs

def prefix_in_list(str, prefixes):
    """returns the prefix if any element of prefixes is a prefix of path

    Args:
        str (str): the string to compare prefixes against
        prefixes (list[str]): a list of prefixes to check against str
    Returns:
        prefix (str or None): the prefix (if any) that str starts with
    """
    for prefix in prefixes:
        if str.startswith(prefix):
            return prefix
    return None

_DISALLOWED_INCLUDE_DIRS = android_constants.NeverAllowNotInIncludeDir
_PACKAGES_DISALLOWED_TO_SPECIFY_INCLUDE_DIRS = android_constants.NeverAllowNoUseIncludeDir

def check_absolute_include_dirs_disabled(target_package, absolute_includes):
    """checks that absolute include dirs are disabled for some directories

    Args:
        target_package (str): package of current target
        absolute_includes (list[str]): list of absolute include directories
    """
    if len(absolute_includes) > 0:
        disallowed_prefix = prefix_in_list(
            target_package,
            _PACKAGES_DISALLOWED_TO_SPECIFY_INCLUDE_DIRS,
        )
        if disallowed_prefix != None:
            fail("include_dirs is deprecated, all usages of them in '" +
                 disallowed_prefix + "' have been migrated to use alternate" +
                 " mechanisms and so can no longer be used.")

    for path in absolute_includes:
        if path in _DISALLOWED_INCLUDE_DIRS:
            fail("include_dirs is deprecated, all usages of '" + path + "' have" +
                 " been migrated to use alternate mechanisms and so can no longer" +
                 " be used.")

def get_compilation_args(toolchain, feature_config, flags, compilation_ctx, action_name):
    compilation_vars = cc_common.create_compile_variables(
        cc_toolchain = toolchain,
        feature_configuration = feature_config,
        user_compile_flags = flags,
        include_directories = compilation_ctx.includes,
        quote_include_directories = compilation_ctx.quote_includes,
        system_include_directories = compilation_ctx.system_includes,
        framework_include_directories = compilation_ctx.framework_includes,
    )

    return cc_common.get_memory_inefficient_command_line(
        feature_configuration = feature_config,
        action_name = action_name,
        variables = compilation_vars,
    )

def build_compilation_flags(ctx, deps, user_flags, action_name):
    cc_toolchain = find_cpp_toolchain(ctx)

    feature_config = cc_common.configure_features(
        ctx = ctx,
        cc_toolchain = cc_toolchain,
        language = "c++",
        requested_features = ctx.features,
        unsupported_features = ctx.disabled_features,
    )

    cc_info = cc_common.merge_cc_infos(direct_cc_infos = [d[CcInfo] for d in deps])

    compilation_flags = get_compilation_args(
        toolchain = cc_toolchain,
        feature_config = feature_config,
        flags = user_flags,
        compilation_ctx = cc_info.compilation_context,
        action_name = action_name,
    )

    return cc_info.compilation_context, compilation_flags

def is_bionic_lib(name):
    return name in _bionic_libs

def is_bootstrap_lib(name):
    return name in _bootstrap_libs

CcAndroidMkInfo = provider(
    "Provides information to be passed to AndroidMk in Soong",
    fields = {
        "local_static_libs": "list of target names passed to LOCAL_STATIC_LIBRARIES AndroidMk variable",
        "local_whole_static_libs": "list of target names passed to LOCAL_WHOLE_STATIC_LIBRARIES AndroidMk variable",
        "local_shared_libs": "list of target names passed to LOCAL_SHARED_LIBRARIES AndroidMk variable",
    },
)

def create_cc_androidmk_provider(*, static_deps, whole_archive_deps, dynamic_deps):
    # Since this information is provided to Soong for mixed builds,
    # we are just taking the Soong module name rather than the Bazel
    # label.
    # TODO(b/266197834) consider moving this logic to the mixed builds
    # handler in Soong
    local_static_libs = [
        strip_bp2build_label_suffix(d.label.name)
        for d in static_deps
    ]
    local_whole_static_libs = [
        strip_bp2build_label_suffix(d.label.name)
        for d in whole_archive_deps
    ]
    local_shared_libs = [
        strip_bp2build_label_suffix(d.label.name)
        for d in dynamic_deps
    ]
    return CcAndroidMkInfo(
        local_static_libs = local_static_libs,
        local_whole_static_libs = local_whole_static_libs,
        local_shared_libs = local_shared_libs,
    )

def create_cc_prebuilt_library_info(ctx, lib_to_link):
    "Create the CcInfo for a prebuilt_library_{shared,static}"

    compilation_context = cc_common.create_compilation_context(
        includes = depset(get_includes_paths(ctx, ctx.attr.export_includes)),
        system_includes = depset(get_includes_paths(ctx, ctx.attr.export_system_includes)),
    )
    linker_input = cc_common.create_linker_input(
        owner = ctx.label,
        libraries = depset(direct = [lib_to_link] if lib_to_link != None else []),
    )
    linking_context = cc_common.create_linking_context(
        linker_inputs = depset(direct = [linker_input]),
    )
    return [
        CcInfo(
            compilation_context = compilation_context,
            linking_context = linking_context,
        ),
        linker_input,
    ]

# Check that -l<lib> requested via linkopts is supported by the toolchain.
def check_valid_ldlibs(ctx, linkopts):
    libs_in_linkopts = [lo for lo in linkopts if lo.startswith("-l")]
    if not libs_in_linkopts:
        return

    # Android
    if ctx.target_platform_has_constraint(ctx.attr._android_constraint[platform_common.ConstraintValueInfo]):
        fail("Library requested via -l is not supported for device builds. Use implementation_deps instead.")

    libs_available = []

    # linux
    if ctx.target_platform_has_constraint(ctx.attr._linux_constraint[platform_common.ConstraintValueInfo]):
        libs_available = cc_constants.LinuxAvailableLibraries

    # darwin
    if ctx.target_platform_has_constraint(ctx.attr._darwin_constraint[platform_common.ConstraintValueInfo]):
        libs_available = cc_constants.DarwinAvailableLibraries

    # windows
    if ctx.target_platform_has_constraint(ctx.attr._windows_constraint[platform_common.ConstraintValueInfo]):
        libs_available = cc_constants.WindowsAvailableLibraries

    bad_libs = [lib for lib in libs_in_linkopts if lib not in libs_available]
    if bad_libs:
        fail("Host library(s) requested via -l is not available in the toolchain. Got: %s, Supported: %s" % (bad_libs, libs_available))

def path_in_list(path, list):
    path_parts = paths.normalize(path).split("/")
    found = False
    for value in list:
        value_parts = paths.normalize(value).split("/")
        if len(value_parts) > len(path_parts):
            continue
        match = True
        for i in range(len(value_parts)):
            if path_parts[i] != value_parts[i]:
                match = False
                break
        if match == True:
            found = True
    return found
