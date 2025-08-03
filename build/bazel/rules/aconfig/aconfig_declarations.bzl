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

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load(":aconfig_value_set.bzl", "AconfigValueSetInfo")
load(":aconfig_value_sets.bzl", "AconfigValueSetsInfo")

AconfigDeclarationsInfo = provider(fields = [
    "package",
    "intermediate_path",
])

def _aconfig_declarations_rule_impl(ctx):
    value_files = []
    transitive = []
    for value_set in ctx.attr._value_sets[AconfigValueSetsInfo].value_sets:
        value_set_info = value_set[AconfigValueSetInfo].available_packages.get(ctx.attr.package)
        if value_set_info != None:
            value_files.extend(value_set_info.to_list())
            transitive.append(value_set_info)

    output = ctx.actions.declare_file(paths.join(ctx.label.name, "intermediate.pb"))

    args = ctx.actions.args()
    args.add("create-cache")
    args.add_all(["--package", ctx.attr.package])
    for src in ctx.files.srcs:
        args.add_all(["--declarations", src.path])
    for value in value_files:
        args.add_all(["--values", value])
    args.add_all(["--default-permission", ctx.attr._default_permission[BuildSettingInfo].value])
    args.add_all(["--cache", output.path])

    inputs = depset(
        direct = ctx.files.srcs,
        transitive = transitive,
    )

    ctx.actions.run(
        inputs = inputs,
        executable = ctx.executable._aconfig,
        outputs = [output],
        arguments = [args],
        mnemonic = "AconfigCreateCache",
    )

    return [
        DefaultInfo(files = depset(direct = [output])),
        AconfigDeclarationsInfo(
            package = ctx.attr.package,
            intermediate_path = output,
        ),
    ]

aconfig_declarations = rule(
    implementation = _aconfig_declarations_rule_impl,
    attrs = {
        "srcs": attr.label_list(
            mandatory = True,
            allow_files = True,
        ),
        "package": attr.string(mandatory = True),
        "_aconfig": attr.label(
            allow_single_file = True,
            executable = True,
            cfg = "exec",
            default = Label("//build/make/tools/aconfig:aconfig"),
        ),
        "_value_sets": attr.label(
            default = "//build/bazel/product_config:release_aconfig_value_sets",
            providers = [AconfigValueSetsInfo],
        ),
        "_release_version": attr.label(
            default = "//build/bazel/product_config:release_version",
        ),
        "_default_permission": attr.label(
            default = "//build/bazel/product_config:release_aconfig_flag_default_permission",
        ),
    },
    provides = [AconfigDeclarationsInfo],
)
