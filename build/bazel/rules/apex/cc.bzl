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

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("//build/bazel/rules:metadata.bzl", "MetadataFileInfo")
load("//build/bazel/rules/cc:cc_library_common.bzl", "parse_apex_sdk_version")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "CcSharedLibraryOutputInfo", "CcStubLibrariesInfo")
load("//build/bazel/rules/cc:cc_stub_library.bzl", "CcStubLibrarySharedInfo")
load("//build/bazel/rules/cc:stripped_cc_common.bzl", "CcUnstrippedInfo")
load("//build/bazel/rules/license:license_aspect.bzl", "license_aspect")

ApexCcInfo = provider(
    "Info needed to use CC targets in APEXes",
    fields = {
        "provides_native_libs": "Labels of native shared libs that this apex provides.",
        "requires_native_libs": "Labels of native shared libs that this apex requires.",
        "transitive_shared_libs": "File references to transitive .so libs produced by the CC targets and should be included in the APEX.",
    },
)

ApexCcMkInfo = provider(
    "AndroidMk data about CC targets in APEXes",
    fields = {
        "make_modules_to_install": "List of module names that should be installed into the system, along with this APEX",
    },
)

# Special libraries that are installed to the bootstrap subdirectory. Bionic
# libraries are assumed to be provided by the system, and installed automatically
# as a symlink to the runtime APEX.
#
# This list is from https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/cc.go;l=1439-1452;drc=9c667416ded33b93a44c5f1894ea23cae6699a17
#
# NOTE: Keep this list in sync with the Soong list.
#
# See cc/binary.go#install for more information.
def _installed_to_bootstrap(label):
    label = str(label)

    # hwasan
    if label == "@//prebuilts/clang/host/linux-x86:libclang_rt.hwasan":
        return True

    # bionic libs
    if label in [
        "@//bionic/libc:libc",
        "@//bionic/libc:libc_hwasan",  # For completeness, but no one should be depending on this.
        "@//bionic/libm:libm",
        "@//bionic/libdl:libdl",
        "@//bionic/libdl_android:libdl_android",
        "@//bionic/linker:linker",
    ]:
        return True

    return False

def has_cc_stubs(target):
    """
    Return True if this target provides stubs.

    There is no need to check versions of stubs any more, see aosp/1609533.

    These stable ABI libraries are intentionally omitted from APEXes as they are
    provided from another APEX or the platform.  By omitting them from APEXes, we
    ensure that there are no multiple copies of such libraries on a device.

    Args:
      target: The target to check for stubs on.
    Returns:
      If the target has cc stubs
    """
    if CcStubLibrarySharedInfo in target:
        # This is a stub lib (direct or transitive).
        return True

    if CcStubLibrariesInfo in target and target[CcStubLibrariesInfo].has_stubs:
        # Direct deps of the apex. The apex would depend on the source lib, not stub lib,
        # so check for CcStubLibrariesInfo.has_stubs.
        return True

    return False

# Check if this target is specified as a direct dependency of the APEX,
# as opposed to a transitive dependency, as the transitivity impacts
# the files that go into an APEX.
def is_apex_direct_dep(label, ctx):
    apex_direct_deps = ctx.attr._apex_direct_deps[BuildSettingInfo].value
    return str(label) in apex_direct_deps

MinSdkVersionInfo = provider(
    "MinSdkVersionInfo provides metadata about the min_sdk_version attribute of a target",
    fields = {
        "apex_inherit": "true if min_sdk_version: \"apex_inherit\" is present on the module",
        "min_sdk_version": "value of min_sdk_version",
    },
)

def get_min_sdk_version(ctx):
    """get_min_sdk_version returns the min_sdk_version for the existing target

    Args:
        ctx (rule context): a rule context
    Returns:
        MinSdkVersionInfo
    """
    min_sdk_version = None
    apex_inherit = False
    if hasattr(ctx.rule.attr, "min_sdk_version"):
        if ctx.rule.attr.min_sdk_version == "apex_inherit":
            apex_inherit = True
        elif ctx.rule.attr.min_sdk_version:
            min_sdk_version = parse_apex_sdk_version(ctx.rule.attr.min_sdk_version)
    else:
        # min_sdk_version in cc targets are represented as features
        for f in ctx.rule.attr.features:
            if f.startswith("sdk_version_"):
                # e.g. sdk_version_29 or sdk_version_10000 or sdk_version_apex_inherit
                sdk_version = f.removeprefix("sdk_version_")
                if sdk_version == "apex_inherit":
                    apex_inherit = True
                elif min_sdk_version == None:
                    min_sdk_version = int(sdk_version)
                else:
                    fail(
                        "found more than one sdk_version feature on {target}; features = {features}",
                        target = ctx.label,
                        features = ctx.rule.attr.features,
                    )
    return MinSdkVersionInfo(
        min_sdk_version = min_sdk_version,
        apex_inherit = apex_inherit,
    )

def _validate_min_sdk_version(ctx):
    dep_min_version = get_min_sdk_version(ctx).min_sdk_version
    apex_min_version = parse_apex_sdk_version(ctx.attr._min_sdk_version[BuildSettingInfo].value)
    if dep_min_version and apex_min_version < dep_min_version:
        fail("The apex %s's min_sdk_version %s cannot be lower than the dep's min_sdk_version %s" %
             (ctx.attr._apex_name[BuildSettingInfo].value, apex_min_version, dep_min_version))

def _apex_cc_aspect_impl(target, ctx):
    # Ensure that dependencies are compatible with this apex's min_sdk_level
    if not ctx.attr.testonly:
        _validate_min_sdk_version(ctx)

    # Whether this dep is a direct dep of an APEX or makes a difference in dependency
    # traversal, and aggregation of libs that are required from the platform/other APEXes,
    # and libs that this APEX will provide to others.
    is_direct_dep = is_apex_direct_dep(target.label, ctx)

    provides = []
    requires = []
    make_modules_to_install = []

    # The APEX manifest records the stub-providing libs (ABI-stable) in its
    # direct and transitive deps.
    #
    # If a stub-providing lib is in the direct deps of an apex, then the apex
    # provides the symbols.
    #
    # If a stub-providing lib is in the transitive deps of an apex, then the
    # apex requires the symbols from the platform or other apexes.
    if has_cc_stubs(target):
        if is_direct_dep:
            # Mark this target as "stub-providing" exports of this APEX,
            # which the system and other APEXes can depend on, and propagate
            # this list.
            provides.append(target.label)
        else:
            # If this is not a direct dep and the build is in not unbundled mode,
            # and stubs are available, don't propagate the libraries.

            # Mark this target as required from the system either via
            # the system partition, or another APEX, and propagate this list.
            if CcStubLibrarySharedInfo not in target:
                fail("Analysis of target: %s in apex: %s failed. This target does not provide CcStubLibrarySharedInfo. \
This apex should likely use stubs of the target instead." % (target, ctx.attr._apex_name[BuildSettingInfo].value))
            source_library_label = target[CcStubLibrarySharedInfo].source_library_label

            # If a stub library is in the "provides" of the apex, it doesn't need to be in the "requires"
            if not is_apex_direct_dep(source_library_label, ctx):
                requires.append(source_library_label)
                if not ctx.attr._unbundled_build[BuildSettingInfo].value and not _installed_to_bootstrap(source_library_label):
                    # It's sufficient to pass the make module name, not the fully qualified bazel label.
                    make_modules_to_install.append(source_library_label.name)

            return [
                ApexCcInfo(
                    transitive_shared_libs = depset(),
                    requires_native_libs = depset(direct = requires),
                    provides_native_libs = depset(direct = provides),
                ),
                ApexCcMkInfo(
                    make_modules_to_install = depset(direct = make_modules_to_install),
                ),
            ]

    shared_object_files = []

    # Transitive deps containing shared libraries to be propagated the apex.
    transitive_deps = []
    rules_propagate_src = [
        "_bssl_hash_injection",
        "stripped_shared_library",
        "versioned_shared_library",
        "stripped_binary",
        "versioned_binary",
    ]

    # Exclude the stripped and unstripped so files
    if ctx.rule.kind == "_cc_library_shared_proxy":
        shared_object_files.append(struct(
            stripped = target[CcSharedLibraryOutputInfo].output_file,
            unstripped = target[CcUnstrippedInfo].unstripped,
            metadata_file = target[MetadataFileInfo].metadata_file,
            generating_rule_owner = target[CcSharedLibraryOutputInfo].output_file.owner,
        ))
        if hasattr(ctx.rule.attr, "shared"):
            transitive_deps.append(ctx.rule.attr.shared[0])
    elif ctx.rule.kind == "cc_prebuilt_library_shared":
        files = target[DefaultInfo].files.to_list()
        if len(files) != 1:
            fail("expected only 1 file in %s[DefaultInfo].files, but got %d" % (target.label, len(files)))
        shared_object_files.append(struct(
            # TODO: This file needs to actually be stripped.
            stripped = files[0],
            unstripped = files[0],
            metadata_file = None,
            # Normally the generating_rule_owner is the owner of the stripped
            # output file, but the owner of files[0] has slashes in its name,
            # and the APEX's make_module_name must not contain a slash.
            generating_rule_owner = target.label,
        ))
    elif ctx.rule.kind in ["cc_shared_library", "cc_binary"]:
        # Propagate along the dynamic_deps edges for binaries and shared libs
        if hasattr(ctx.rule.attr, "dynamic_deps"):
            for dep in ctx.rule.attr.dynamic_deps:
                transitive_deps.append(dep)
    elif ctx.rule.kind in rules_propagate_src and hasattr(ctx.rule.attr, "src"):
        # Propagate along the src edge
        if ctx.rule.kind == "stripped_binary":
            transitive_deps.append(ctx.rule.attr.src[0])
        else:
            transitive_deps.append(ctx.rule.attr.src)

    # We only collect runtime dependencies from binaries and shared libraries,
    # we _explicitly_ omit static libraries (kind = _cc_library_combiner)
    if ctx.rule.kind in ["stripped_binary", "_cc_library_shared_proxy"] and hasattr(ctx.rule.attr, "runtime_deps"):
        for dep in ctx.rule.attr.runtime_deps:
            unstripped = None
            if CcUnstrippedInfo in dep:
                unstripped = dep[CcUnstrippedInfo].unstripped
            for output_file in dep[DefaultInfo].files.to_list():
                if output_file.extension == "so":
                    shared_object_files.append(struct(
                        stripped = output_file,
                        unstripped = unstripped,
                        metadata_file = dep[MetadataFileInfo].metadata_file,
                        generating_rule_owner = output_file.owner,
                    ))
            transitive_deps.append(dep)

    return [
        ApexCcInfo(
            transitive_shared_libs = depset(
                shared_object_files,
                transitive = [info[ApexCcInfo].transitive_shared_libs for info in transitive_deps],
            ),
            requires_native_libs = depset(
                [],
                transitive = [info[ApexCcInfo].requires_native_libs for info in transitive_deps],
            ),
            provides_native_libs = depset(
                provides,
                transitive = [info[ApexCcInfo].provides_native_libs for info in transitive_deps],
            ),
        ),
        ApexCcMkInfo(
            make_modules_to_install = depset(
                [],
                transitive = [info[ApexCcMkInfo].make_modules_to_install for info in transitive_deps],
            ),
        ),
    ]

# The list of attributes in a cc dep graph where this aspect will traverse on.
CC_ATTR_ASPECTS = [
    "dynamic_deps",
    "deps",
    "shared",
    "src",
    "runtime_deps",
    "static_deps",
    "whole_archive_deps",
]

# This aspect is intended to be applied on a apex.native_shared_libs attribute
apex_cc_aspect = aspect(
    implementation = _apex_cc_aspect_impl,
    provides = [ApexCcInfo, ApexCcMkInfo],
    attrs = {
        # This is propagated from the apex
        "testonly": attr.bool(default = False),
        "_apex_direct_deps": attr.label(default = "//build/bazel/rules/apex:apex_direct_deps"),
        "_apex_name": attr.label(default = "//build/bazel/rules/apex:apex_name"),
        "_min_sdk_version": attr.label(default = "//build/bazel/rules/apex:min_sdk_version"),
        "_unbundled_build": attr.label(default = "//build/bazel/product_config:unbundled_build"),
    },
    attr_aspects = CC_ATTR_ASPECTS,
    requires = [license_aspect],
    # TODO: Have this aspect also propagate along attributes of native_shared_libs?
)
