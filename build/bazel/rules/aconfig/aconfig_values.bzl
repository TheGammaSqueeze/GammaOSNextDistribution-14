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

AconfigValuesInfo = provider(fields = [
    "package",
    "values",
])

def _aconfig_values_rule_impl(ctx):
    return [
        DefaultInfo(files = depset(ctx.files.srcs)),
        AconfigValuesInfo(
            package = ctx.attr.package,
            values = depset(ctx.files.srcs),
        ),
    ]

aconfig_values = rule(
    implementation = _aconfig_values_rule_impl,
    attrs = {
        "srcs": attr.label_list(allow_files = True),
        "package": attr.string(mandatory = True),
    },
    provides = [AconfigValuesInfo],
)
