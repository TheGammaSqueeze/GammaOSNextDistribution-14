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

def get_dep_targets(attrs, *, predicate = lambda _: True):
    """get_dep_targets returns all targets listed in the current rule's attributes

    Args:
        attrs (dict[str, attr]): dictionary containing the rule's attributes.
            This may come from `ctx.attr` if called from a rule, or
            `ctx.rule.attr` if called from an aspect.
        predicate (function(Target) -> bool): a function used to filter out
            unwanted targets; if predicate(target) == False, then do not include
            target
    Returns:
        targets (dict[str, list[Target]]): map of attr to list of Targets for which
            predicate returns True
    """
    targets = {}
    for a in dir(attrs):
        if a.startswith("_"):
            # Ignore private attributes
            continue
        targets[a] = []
        value = getattr(attrs, a)
        vlist = value if type(value) == type([]) else [value]
        for item in vlist:
            if type(item) == "Target" and predicate(item):
                targets[a].append(item)
    return targets

_BP2BUILD_LABEL_SUFFIXES = [
    # cc rules
    "_bp2build_cc_library_static",
    "_cc_proto_lite",
    "_aidl_code_gen",
    "_cc_aidl_library",
]

def strip_bp2build_label_suffix(name):
    for suffix in _BP2BUILD_LABEL_SUFFIXES:
        name = name.removesuffix(suffix)
    return name

def _repeatable_string_flag_impl(ctx):
    return [BuildSettingInfo(value = [v for v in ctx.build_setting_value])]

repeatable_string_flag = rule(
    implementation = _repeatable_string_flag_impl,
    build_setting = config.string(flag = True, allow_multiple = True),
)
