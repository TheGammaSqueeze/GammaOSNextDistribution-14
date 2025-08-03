# Copyright (C) 2023 The Android Open Source Project
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

load(":aconfig_values.bzl", "AconfigValuesInfo")

AconfigValueSetInfo = provider(fields = [
    "available_packages",
])

def _aconfig_value_set_rule_impl(ctx):
    value_set_info = dict()
    for value in ctx.attr.values:
        value_set_info[value[AconfigValuesInfo].package] = value[AconfigValuesInfo].values
    return [
        AconfigValueSetInfo(
            available_packages = value_set_info,
        ),
    ]

aconfig_value_set = rule(
    implementation = _aconfig_value_set_rule_impl,
    attrs = {
        "values": attr.label_list(mandatory = True, providers = [AconfigValuesInfo]),
    },
    provides = [AconfigValueSetInfo],
)
