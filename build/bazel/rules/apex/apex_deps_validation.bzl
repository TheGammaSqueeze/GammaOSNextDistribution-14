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
load("//build/bazel/rules:common.bzl", "get_dep_targets", "strip_bp2build_label_suffix")
load("//build/bazel/rules/android:android_app_certificate.bzl", "AndroidAppCertificateInfo")
load(":apex_available.bzl", "ApexAvailableInfo")
load(":apex_info.bzl", "ApexInfo")
load(":apex_key.bzl", "ApexKeyInfo")
load(":cc.bzl", "get_min_sdk_version")

ApexDepsInfo = provider(
    "ApexDepsInfo collects transitive deps for dependency validation.",
    fields = {
        "transitive_deps": "Labels of targets that are depended on by this APEX.",
    },
)

ApexDepInfo = provider(
    "ApexDepInfo collects metadata about dependencies of APEXs.",
    fields = {
        "is_external": "True if this target is an external dep to the APEX.",
        "label": "Label of target",
        "min_sdk_version": "min_sdk_version of target",
    },
)

_IGNORED_PACKAGES = [
    "build/bazel/platforms",
]
_IGNORED_REPOSITORIES = [
    "bazel_tools",
]
_IGNORED_RULE_KINDS = [
    # No validation for language-agnostic targets.  In general language
    # agnostic rules to support AIDL, HIDL, Sysprop do not have an analogous
    # module type in Soong and do not have an apex_available property, often
    # relying on language-specific apex_available properties.  Because a
    # language-specific rule is required for a language-agnostic rule to be
    # within the transitive deps of an apex and impact the apex contents, this
    # is safe.
    "aidl_library",
    "hidl_library",
    "sysprop_library",

    # Build settings, these have no built artifact and thus will not be
    # included in an apex.
    "string_list_setting",
    "string_setting",

    # These rule kinds cannot be skipped by checking providers because most
    # targets have a License provider
    "_license",
    "_license_kind",
]
_IGNORED_PROVIDERS = [
    AndroidAppCertificateInfo,
    ApexKeyInfo,
    ProtoInfo,
]
_IGNORED_ATTRS = [
    "androidmk_static_deps",
    "androidmk_whole_archive_deps",
    "androidmk_dynamic_deps",
    "androidmk_deps",
]
_IGNORED_TARGETS = [
    "default_metadata_file",
]

def _should_skip_apex_dep(target, ctx):
    # Ignore Bazel-specific targets like platform/os/arch constraints,
    # anything from @bazel_tools, and rule types that we dont care about
    # for dependency validation like licenses, certificates, etc.
    #TODO(b/261715581) update allowed_deps.txt to include Bazel-specific targets
    return (
        ctx.label.workspace_name in _IGNORED_REPOSITORIES or
        ctx.label.package in _IGNORED_PACKAGES or
        ctx.rule.kind in _IGNORED_RULE_KINDS or
        True in [p in target for p in _IGNORED_PROVIDERS] or
        target.label.name in _IGNORED_TARGETS
    )

def _apex_dep_validation_aspect_impl(target, ctx):
    transitive_deps = []
    for attr, attr_deps in get_dep_targets(ctx.rule.attr, predicate = lambda target: ApexDepsInfo in target).items():
        if attr in _IGNORED_ATTRS:
            continue
        for dep in attr_deps:
            transitive_deps.append(dep[ApexDepsInfo].transitive_deps)

    if _should_skip_apex_dep(target, ctx):
        return ApexDepsInfo(
            transitive_deps = depset(
                transitive = transitive_deps,
            ),
        )

    is_external = False
    include_self_in_transitive_deps = True

    if "manual" in ctx.rule.attr.tags and "apex_available_checked_manual_for_testing" not in ctx.rule.attr.tags:
        include_self_in_transitive_deps = False
    else:
        apex_available_names = target[ApexAvailableInfo].apex_available_names
        apex_name = ctx.attr._apex_name[BuildSettingInfo].value
        base_apex_name = ctx.attr._base_apex_name[BuildSettingInfo].value
        if not (
            "//apex_available:anyapex" in apex_available_names or
            base_apex_name in apex_available_names or
            apex_name in apex_available_names
        ):
            # APEX deps validation stops when the dependency graph crosses the APEX boundary
            # Record that this is a boundary target, so that we exclude can it later from validation
            is_external = True
            transitive_deps = []

        if not target[ApexAvailableInfo].platform_available:
            # Skip dependencies that are only available to APEXes; they are
            # developed with updatability in mind and don't need manual approval.
            include_self_in_transitive_deps = False

    if ApexInfo in target:
        include_self_in_transitive_deps = False

    direct_deps = []
    if include_self_in_transitive_deps:
        direct_deps = [
            ApexDepInfo(
                label = ctx.label,
                is_external = is_external,
                min_sdk_version = get_min_sdk_version(ctx),
            ),
        ]

    return ApexDepsInfo(
        transitive_deps = depset(
            direct = direct_deps,
            transitive = transitive_deps,
        ),
    )

apex_deps_validation_aspect = aspect(
    doc = "apex_deps_validation_aspect walks the deps of an APEX and records" +
          " its transitive dependencies so that they can be validated against" +
          " allowed_deps.txt.",
    implementation = _apex_dep_validation_aspect_impl,
    attr_aspects = ["*"],
    apply_to_generating_rules = True,
    attrs = {
        "_apex_name": attr.label(default = "//build/bazel/rules/apex:apex_name"),
        "_base_apex_name": attr.label(default = "//build/bazel/rules/apex:base_apex_name"),
        "_direct_deps": attr.label(default = "//build/bazel/rules/apex:apex_direct_deps"),
    },
    required_aspect_providers = [ApexAvailableInfo],
    provides = [ApexDepsInfo],
)

def _min_sdk_version_string(version):
    if version.apex_inherit:
        return "apex_inherit"
    elif version.min_sdk_version == None:
        return "(no version)"
    return version.min_sdk_version

def _apex_dep_to_string(apex_dep_info):
    return "{name}(minSdkVersion:{min_sdk_version})".format(
        name = strip_bp2build_label_suffix(apex_dep_info.label.name),
        min_sdk_version = _min_sdk_version_string(apex_dep_info.min_sdk_version),
    )

def apex_dep_infos_to_allowlist_strings(apex_dep_infos):
    """apex_dep_infos_to_allowlist_strings converts outputs a string that can be compared against allowed_deps.txt

    Args:
        apex_dep_infos (list[ApexDepInfo]): list of deps to convert
    Returns:
        a list of strings conforming to the format of allowed_deps.txt
    """
    return [
        _apex_dep_to_string(d)
        for d in apex_dep_infos
        if not d.is_external
    ]

def validate_apex_deps(ctx, transitive_deps, allowed_deps_manifest):
    """validate_apex_deps generates actions to validate that all deps in transitive_deps exist in the allowed_deps file

    Args:
        ctx (rule context): a rule context
        transitive_deps (depset[ApexDepsInfo]): list of transitive dependencies
            of an APEX. This is most likely generated by collecting the output
            of apex_deps_validation_aspect
        allowed_deps_manifest (File): a file containing an allowlist of modules
            that can be included in an APEX. This is expected to be in the format
            of //packages/modules/common/build/allowed_deps.txt
    Returns:
        validation_marker (File): an empty file created if validation succeeds
    """
    apex_deps_file = ctx.actions.declare_file(ctx.label.name + ".current_deps")
    ctx.actions.write(
        apex_deps_file,
        "\n".join(apex_dep_infos_to_allowlist_strings(transitive_deps.to_list())),
    )
    validation_marker = ctx.actions.declare_file(ctx.label.name + ".allowed_deps")
    shell_command = """
        export module_diff=$(
            cat {allowed_deps_manifest} |
            sed -e 's/^prebuilt_//g' |
            sort |
            comm -23 <(sort -u {apex_deps_file}) -
        );
        export diff_size=$(echo "$module_diff" | wc -w);
        if [[ $diff_size -eq 0 ]]; then
            touch {validation_marker};
        else
            echo -e "\n******************************";
            echo "ERROR: go/apex-allowed-deps-error contains more information";
            echo "******************************";
            echo "Detected changes to allowed dependencies in updatable modules.";
            echo "There are $diff_size dependencies of APEX {target_label} on modules not in {allowed_deps_manifest}:";
            echo "$module_diff";
            echo "To fix and update packages/modules/common/build/allowed_deps.txt, please run:";
            echo -e "$ (croot && packages/modules/common/build/update-apex-allowed-deps.sh)\n";
            echo "When submitting the generated CL, you must include the following information";
            echo "in the commit message if you are adding a new dependency:";
            echo "Apex-Size-Increase: Expected binary size increase for affected APEXes (or the size of the .jar / .so file of the new library)";
            echo "Previous-Platform-Support: Are the maintainers of the new dependency committed to supporting previous platform releases?";
            echo "Aosp-First: Is the new dependency being developed AOSP-first or internal?";
            echo "Test-Info: What’s the testing strategy for the new dependency? Does it have its own tests, and are you adding integration tests? How/when are the tests run?";
            echo "You do not need OWNERS approval to submit the change, but mainline-modularization@";
            echo "will periodically review additions and may require changes.";
            echo -e "******************************\n";
            exit 1;
        fi;
    """.format(
        allowed_deps_manifest = allowed_deps_manifest.path,
        apex_deps_file = apex_deps_file.path,
        validation_marker = validation_marker.path,
        target_label = ctx.label,
    )
    ctx.actions.run_shell(
        inputs = [allowed_deps_manifest, apex_deps_file],
        outputs = [validation_marker],
        command = shell_command,
        mnemonic = "ApexDepValidation",
        progress_message = "Validating APEX dependencies",
    )

    return validation_marker
