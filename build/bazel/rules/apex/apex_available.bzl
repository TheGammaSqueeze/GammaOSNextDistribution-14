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

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@soong_injection//apex_toolchain:constants.bzl", "apex_available_baseline")
load("//build/bazel/rules:common.bzl", "get_dep_targets", "strip_bp2build_label_suffix")
load("//build/bazel/rules:prebuilt_file.bzl", "PrebuiltFileInfo")
load("//build/bazel/rules/apex:cc.bzl", "CC_ATTR_ASPECTS")
load("//build/bazel/rules/cc:cc_library_static.bzl", "CcStaticLibraryInfo")
load("//build/bazel/rules/cc:cc_stub_library.bzl", "CcStubLibrarySharedInfo")

ApexAvailableInfo = provider(
    "ApexAvailableInfo collects APEX availability metadata.",
    fields = {
        "apex_available_names": "names of APEXs that this target is available to",
        "platform_available": "whether this target is available for the platform",
        "transitive_invalid_targets": "list of targets that had an invalid apex_available attribute",
        "transitive_unvalidated_targets": "list of targets that were skipped in the apex_available_validation function",
    },
)

# Validates if a target is made available as a transitive dependency of an APEX. The return
# value is tri-state: True, False, string. Strings are used when a target is _not checked_
# and the string itself contains the reason.
def _validate_apex_available(target, ctx, *, apex_available_tags, apex_name, base_apex_name):
    # testonly apexes aren't checked.
    if ctx.attr.testonly:
        return "testonly"

    # Macro-internal manual targets aren't checked.
    if "manual" in ctx.rule.attr.tags and "apex_available_checked_manual_for_testing" not in ctx.rule.attr.tags:
        return "manual"

    # prebuilt_file targets don't specify apex_available, and aren't checked.
    if PrebuiltFileInfo in target:
        return "prebuilt"

    # stubs are APIs, and don't specify apex_available, and aren't checked.
    if CcStubLibrarySharedInfo in target:
        return "stubs"

    if "//apex_available:anyapex" in apex_available_tags:
        return "//apex_available:anyapex"

    # https://cs.android.com/android/platform/superproject/+/master:build/soong/apex/apex.go;l=2910;drc=862c0d68fff500d7fe59bc2fcfc9c7d75596e5b5
    # Bp2build-generated cc_library_static target from stubs-providing lib
    # doesn't have apex_available tag.
    # If its shared variant is directly in the apex, skip validation
    # Otherwise, it will be invalidated.
    direct_deps = ctx.attr._direct_deps[BuildSettingInfo].value
    if CcStaticLibraryInfo in target and str(target.label).removesuffix("_bp2build_cc_library_static") in direct_deps:
        return "has shared variant directly included"

    if base_apex_name in apex_available_tags or apex_name in apex_available_tags:
        return True

    target_name = strip_bp2build_label_suffix(target.label.name)
    baselines = [
        apex_available_baseline.get(base_apex_name, []),
        apex_available_baseline.get(apex_name, []),
        apex_available_baseline.get("//apex_available:anyapex", []),
    ]
    if any([target_name in b for b in baselines]):
        return True

    return False

_IGNORED_ATTRS = [
    "certificate",
    "key",
    "android_manifest",
    "applicable_licenses",
    "androidmk_static_deps",
    "androidmk_whole_archive_deps",
    "androidmk_dynamic_deps",
    "androidmk_deps",
]

def _apex_available_aspect_impl(target, ctx):
    apex_available_tags = [
        t.removeprefix("apex_available=")
        for t in ctx.rule.attr.tags
        if t.startswith("apex_available=")
    ]
    platform_available = (
        "//apex_available:platform" in apex_available_tags or
        len(apex_available_tags) == 0
    )
    apex_name = ctx.attr._apex_name[BuildSettingInfo].value

    dep_targets = get_dep_targets(
        ctx.rule.attr,
        predicate = lambda target: ApexAvailableInfo in target,
    )
    transitive_unvalidated_targets = []
    transitive_invalid_targets = []
    for attr, attr_targets in dep_targets.items():
        for t in attr_targets:
            info = t[ApexAvailableInfo]
            transitive_unvalidated_targets.append(info.transitive_unvalidated_targets)
            if attr in CC_ATTR_ASPECTS:
                transitive_invalid_targets.append(info.transitive_invalid_targets)
            if attr not in _IGNORED_ATTRS:
                if info.platform_available != None:
                    platform_available = platform_available and info.platform_available

    if "manual" in ctx.rule.attr.tags and "apex_available_checked_manual_for_testing" not in ctx.rule.attr.tags:
        platform_available = None

    if CcStubLibrarySharedInfo in target:
        # stub libraries libraries are always available to platform
        # https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/cc.go;l=3670;drc=89ff729d1d65fb0ce2945ec6b8c4777a9d78dcab
        platform_available = True

    skipped_reason = _validate_apex_available(
        target,
        ctx,
        apex_available_tags = apex_available_tags,
        apex_name = apex_name,
        base_apex_name = ctx.attr._base_apex_name[BuildSettingInfo].value,
    )

    return [
        ApexAvailableInfo(
            platform_available = platform_available,
            apex_available_names = apex_available_tags,
            transitive_unvalidated_targets = depset(
                direct = [(ctx.label, skipped_reason)] if type(skipped_reason) == type("") else None,
                transitive = transitive_unvalidated_targets,
            ),
            transitive_invalid_targets = depset(
                direct = [(target, tuple(apex_available_tags))] if skipped_reason == False else None,
                transitive = transitive_invalid_targets,
            ),
        ),
    ]

apex_available_aspect = aspect(
    implementation = _apex_available_aspect_impl,
    provides = [ApexAvailableInfo],
    apply_to_generating_rules = True,
    attr_aspects = ["*"],
    attrs = {
        "testonly": attr.bool(default = False),  # propagated from the apex
        "_apex_name": attr.label(default = "//build/bazel/rules/apex:apex_name"),
        "_base_apex_name": attr.label(default = "//build/bazel/rules/apex:base_apex_name"),
        "_direct_deps": attr.label(default = "//build/bazel/rules/apex:apex_direct_deps"),
    },
)
