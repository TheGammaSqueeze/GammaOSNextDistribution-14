# Copyright 2022 Google LLC. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the License);
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

"""Convenience macro for getting `kt_compiler_plugin` into uncooperative rules.

Some targets (Z), where we would like to export a plugin (Y), use rules that don't
support `exported_plugins` (e.g android_library). To solve this, we create a dummy
target (X) that has `X.exported_plugins = [Y]`, and then set `Z.exports = [Y]`.
This creates a chain of exports for `kt_traverse_exports` to follow when discovering
`kt_compiler_plugin`s.
"""

load(":compiler_plugin.bzl", "kt_compiler_plugin")
load(":jvm_import.bzl", "kt_jvm_import")

def _kt_compiler_plugin_export(
        name,
        visibility = [],
        compatible_with = [],
        **kwargs):
    if not name.endswith("_plugin_export"):
        fail()

    basename = name.replace("_plugin_export", "")

    kt_jvm_import(
        name = name,
        exported_plugins = [basename + "_plugin"],
        jars = [basename + "_empty_jar"],
                compatible_with = compatible_with,
        visibility = visibility,
        neverlink = True,  # Don't link kotlin stdlib into Java users
    )

    kt_compiler_plugin(
        name = basename + "_plugin",
        visibility = visibility,
        compatible_with = compatible_with,
        **kwargs
    )

    native.genrule(
        name = basename + "_empty_jar",
        visibility = visibility,
        outs = [name + "_empty.jar"],
        cmd = """$(location @bazel_tools//tools/zip:zipper) c $@ "assets/_empty=" """,
        compatible_with = compatible_with,
        tools = ["@bazel_tools//tools/zip:zipper"],
    )

kt_compiler_plugin_export = _kt_compiler_plugin_export
