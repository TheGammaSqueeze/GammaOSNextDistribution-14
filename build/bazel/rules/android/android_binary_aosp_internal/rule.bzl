"""
Copyright (C) 2023 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

load("@rules_android//rules/android_binary_internal:rule.bzl", "make_rule", "sanitize_attrs")
load("@rules_android//rules:attrs.bzl", _attrs = "attrs")
load("@rules_android//rules/android_binary_internal:attrs.bzl", _BASE_ATTRS = "ATTRS", _DEPS_ALLOW_RULES = "DEPS_ALLOW_RULES", _DEPS_ASPECTS = "DEPS_ASPECTS", _DEPS_PROVIDERS = "DEPS_PROVIDERS", _make_deps = "make_deps")
load(":impl.bzl", "collect_cc_stubs_aspect", _impl = "impl")

def _make_aspects_for_deps(default_deps_aspects = [], additional_aspects = []):
    """Generates a list of aspects to apply to the android_binary deps attribute.

    Args:
        default_deps_aspects: A list that contains the default list of aspects for deps. Usually loaded from android_binary_internal:attrs.bzl.
        additional_aspects: A list of additional aspects to append to the aspects list.

    Returns:
        A list of aspects to apply to android_binary's deps attr.
    """
    aspects = []
    aspects.extend(default_deps_aspects)
    aspects.extend(additional_aspects)
    return aspects

DEPS_ASPECTS = _make_aspects_for_deps(default_deps_aspects = _DEPS_ASPECTS, additional_aspects = [collect_cc_stubs_aspect])

_ATTRS = _attrs.add(
    _attrs.replace(
        _BASE_ATTRS,
        deps = _make_deps(_DEPS_ALLOW_RULES, _DEPS_PROVIDERS, DEPS_ASPECTS),
    ),
    dict(
        _product_config_device_abi = attr.label(
            default = Label("//build/bazel/product_config:device_abi"),
            doc = "Implicit attr used to extract target device ABI information (for apk lib naming).",
        ),
        _platform_sdk_final = attr.label(
            default = "//build/bazel/product_config:platform_sdk_final",
            doc = "PlatformSdkFinal product variable",
        ),
        _unbundled_build_apps = attr.label(
            default = "//build/bazel/product_config:unbundled_build_apps",
            doc = "UnbundledBuildApps product variable",
        ),
        _override_apex_manifest_default_version = attr.label(
            default = "//build/bazel/rules/apex:override_apex_manifest_default_version",
            doc = "If the app is updatable, and this attribute is specified, and higher than the value specified in manifest_values, will override minSdkVersion in manifest with this value instead of the value in manifest_values.",
        ),
        _manifest_fixer = attr.label(
            cfg = "exec",
            executable = True,
            default = "//build/soong/scripts:manifest_fixer",
        ),
        sdk_version = attr.string(
            doc = "The sdk_version this app should build against.",
        ),
        # TODO: b/301425155 - Handle all of the ways updatable affects this rule.
        updatable = attr.bool(
            default = False,
            doc = "Whether this app is updatable.",
        ),
    ),
)

android_binary_aosp_internal = make_rule(attrs = _ATTRS, implementation = _impl)

def android_binary_aosp_internal_macro(**attrs):
    """android_binary_internal rule.

    Args:
      **attrs: Rule attributes
    """
    android_binary_aosp_internal(**sanitize_attrs(attrs, _ATTRS))
