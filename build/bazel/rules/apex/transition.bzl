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

"""
Configuration transitions for APEX rules.

Transitions are a Bazel mechanism to analyze/build dependencies in a different
configuration (i.e. options and flags). The APEX transition is applied from a
top level APEX rule to its dependencies via an outgoing edge, so that the
dependencies can be built specially for APEXes (vs the platform).

e.g. if an apex A depends on some target T, building T directly as a top level target
will use a different configuration from building T indirectly as a dependency of A. The
latter will contain APEX specific configuration settings that its rule or an aspect can
use to create different actions or providers for APEXes specifically..

The outgoing transitions are similar to ApexInfo propagation in Soong's
top-down ApexInfoMutator:
https://cs.android.com/android/platform/superproject/+/master:build/soong/apex/apex.go;l=948-962;drc=539d41b686758eeb86236c0e0dcf75478acb77f3
"""

load("@bazel_skylib//lib:collections.bzl", "collections")
load("@bazel_skylib//lib:dicts.bzl", "dicts")
load("//build/bazel/rules/apex:sdk_versions.bzl", "maybe_override_min_sdk_version")

def _get_api_domain(apex_name, base_apex_name):
    # AOSP and Google variants of apexes are part of the same API domain.
    # Test apexes and source apexes are part of the same API domain.
    # Override test apexes should return the api domain of the overriden test apex
    # Return base_apex_name if it is not empty.
    if base_apex_name:
        # TODO (b/282058578): Deprecate this special handling.
        # TODO: This does not handle special cases like test1_com.android.tzdata.
        # This is fine for now since tzdata does not have native code.
        return base_apex_name.lstrip("test_")

    return apex_name

def _create_apex_configuration(settings, attr, additional = {}):
    min_sdk_version = maybe_override_min_sdk_version(
        attr.min_sdk_version,
        settings["//build/bazel/product_config:apex_global_min_sdk_version_override"],
    )

    apex_name = attr.name
    if attr.apex_available_name != "":
        apex_name = attr.apex_available_name

    return dicts.add({
        "//build/bazel/rules/apex:apex_name": apex_name,  # Name of the APEX
        "//build/bazel/rules/apex:base_apex_name": attr.base_apex_name,  # Name of the base APEX, if exists
        "//build/bazel/rules/apex:min_sdk_version": min_sdk_version,
        "//build/bazel/rules/apex:within_apex": True,  # Building a APEX
        "//build/bazel/rules/apex:api_domain": _get_api_domain(attr.name, attr.base_apex_name),
    }, additional)

def _impl(settings, attr):
    # Perform a transition to apply APEX specific build settings on the
    # destination target (i.e. an APEX dependency).

    # At this point, the configurable attributes native_shared_libs_32 and
    # native_shared_libs_64 are already resolved according to the lunch target
    direct_deps = [str(dep) for dep in attr.native_shared_libs_32]
    direct_deps += [str(dep) for dep in attr.native_shared_libs_64]
    direct_deps += [str(dep) for dep in attr.binaries]

    return _create_apex_configuration(settings, attr, {
        "//build/bazel/rules/apex:apex_direct_deps": collections.uniq(sorted(direct_deps)),
    })

_TRANSITION_INPUTS = [
    "//build/bazel/product_config:apex_global_min_sdk_version_override",
]

_TRANSITION_OUTPUTS = [
    "//build/bazel/rules/apex:apex_name",
    "//build/bazel/rules/apex:base_apex_name",
    "//build/bazel/rules/apex:within_apex",
    "//build/bazel/rules/apex:min_sdk_version",
    "//build/bazel/rules/apex:apex_direct_deps",
    "//build/bazel/rules/apex:api_domain",
]

apex_transition = transition(
    implementation = _impl,
    inputs = _TRANSITION_INPUTS,
    outputs = _TRANSITION_OUTPUTS,
)

# The following table describes how target platform of shared_lib_transition_32 and shared_lib_transition_64
# look like when building APEXes for different primary/secondary architecture.
#
# |---------------------------+----------------------------------------------------+----------------------------------------------------|
# | Primary arch              | Platform for                                       | Platform for                                       |
# |       /  Secondary arch   | 32b libs transition                                | 64b libs transition                                |
# |---------------------------+----------------------------------------------------+----------------------------------------------------|
# | 32bit / N/A               | android_target                                     | android_target                                     |
# | (android_target is 32bit) |                                                    | (wrong target platform indicates the transition    |
# |                           |                                                    | is not needed, and the 64bit libs are not included |
# |                           |                                                    | in APEXes for 32bit devices, see                   |
# |                           |                                                    | _create_file_mapping() in apex.bzl)                |
# |---------------------------+----------------------------------------------------+----------------------------------------------------|
# | 64bit / 32bit             | android_target_secondary                           | android_target                                     |
# | (android_target is 64bit) |                                                    |                                                    |
# |---------------------------+----------------------------------------------------+----------------------------------------------------|
# | 64bit / N/A               | android_target                                     | android_target                                     |
# | (android_target is 64bit) | (wrong target platform indicates the transition    |                                                    |
# |                           | is not needed, and the 32bit libs are not included |                                                    |
# |                           | in APEXes for 64bit ONLY devices, see              |                                                    |
# |                           | _create_file_mapping() in apex.bzl)                |                                                    |
# |---------------------------+----------------------------------------------------+----------------------------------------------------|

def _impl_shared_lib_transition_32(settings, attr):
    # Perform a transition to apply APEX specific build settings on the
    # destination target (i.e. an APEX dependency).

    direct_deps = [str(dep) for dep in attr.native_shared_libs_32]
    direct_deps += [str(dep) for dep in attr.binaries]

    old_platform = str(settings["//command_line_option:platforms"][0])

    return _create_apex_configuration(settings, attr, {
        "//build/bazel/rules/apex:apex_direct_deps": collections.uniq(sorted(direct_deps)),
        "//command_line_option:platforms": old_platform + "_secondary",
    })

shared_lib_transition_32 = transition(
    implementation = _impl_shared_lib_transition_32,
    inputs = _TRANSITION_INPUTS + ["//command_line_option:platforms"],
    outputs = _TRANSITION_OUTPUTS + ["//command_line_option:platforms"],
)

def _impl_shared_lib_transition_64(settings, attr):
    # Perform a transition to apply APEX specific build settings on the
    # destination target (i.e. an APEX dependency).

    direct_deps = [str(dep) for dep in attr.native_shared_libs_64]
    direct_deps += [str(dep) for dep in attr.binaries]

    # For the 64 bit transition, we don't actually change the arch, because
    # we only read the value of native_shared_libs_64 when the target
    # is 64-bit already
    return _create_apex_configuration(settings, attr, {
        "//build/bazel/rules/apex:apex_direct_deps": collections.uniq(sorted(direct_deps)),
    })

shared_lib_transition_64 = transition(
    implementation = _impl_shared_lib_transition_64,
    inputs = _TRANSITION_INPUTS,
    outputs = _TRANSITION_OUTPUTS,
)
