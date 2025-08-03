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

load(
    "@rules_android//rules/android_library:rule.bzl",
    _attrs_metadata = "attrs_metadata",
    _make_rule = "make_rule",
)
load("@rules_kotlin//toolchains/kotlin_jvm:kt_jvm_toolchains.bzl", _kt_jvm_toolchains = "kt_jvm_toolchains")
load(":attrs.bzl", "ATTRS")
load(
    ":impl.bzl",
    _impl = "impl",
)

android_library = _make_rule(
    attrs = ATTRS,
    implementation = _impl,
    additional_toolchains = [_kt_jvm_toolchains.type],
)

def android_library_aosp_internal_macro(**attrs):
    """AOSP android_library rule.

    Args:
      **attrs: Rule attributes
    """
    android_library(**_attrs_metadata(attrs))
