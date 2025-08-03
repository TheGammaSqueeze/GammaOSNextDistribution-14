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
load("//build/bazel/rules/aconfig:aconfig_declarations.bzl", "AconfigDeclarationsInfo")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "cc_library_shared")
load("//build/bazel/rules/cc:cc_library_static.bzl", "cc_library_static")

def _cc_aconfig_code_gen_rule_impl(ctx):
    gen_dir_str = paths.join(ctx.label.name, "gen")
    header_dir_str = paths.join(gen_dir_str, "include")

    aconfig_declarations = ctx.attr.aconfig_declarations[AconfigDeclarationsInfo]
    basename = aconfig_declarations.package.replace(".", "_")
    gen_cpp = ctx.actions.declare_file(paths.join(gen_dir_str, basename + ".cc"))
    gen_header = ctx.actions.declare_file(paths.join(header_dir_str, basename + ".h"))
    intermediate_path = aconfig_declarations.intermediate_path

    args = ctx.actions.args()
    args.add("create-cpp-lib")
    args.add_all(["--cache", intermediate_path])
    args.add_all(["--out", gen_cpp.dirname])

    outputs = [gen_cpp, gen_header]

    ctx.actions.run(
        inputs = [intermediate_path],
        executable = ctx.executable._aconfig,
        outputs = outputs,
        arguments = [args],
        tools = [
            ctx.executable._aconfig,
        ],
        mnemonic = "AconfigCreateCppLib",
    )

    compilation_context = cc_common.create_compilation_context(
        headers = depset([gen_header]),
        includes = depset([gen_header.dirname]),
    )

    return [
        DefaultInfo(files = depset(direct = outputs)),
        CcInfo(compilation_context = compilation_context),
    ]

_cc_aconfig_code_gen = rule(
    implementation = _cc_aconfig_code_gen_rule_impl,
    attrs = {
        "aconfig_declarations": attr.label(
            providers = [AconfigDeclarationsInfo],
            mandatory = True,
        ),
        "_aconfig": attr.label(
            allow_single_file = True,
            executable = True,
            cfg = "exec",
            default = Label("//build/make/tools/aconfig:aconfig"),
        ),
    },
    provides = [CcInfo],
)

def cc_aconfig_library(
        name,
        aconfig_declarations,
        **kwargs):
    gen_name = name + "_gen"

    _cc_aconfig_code_gen(
        name = gen_name,
        aconfig_declarations = aconfig_declarations,
        tags = ["manual"],
    )

    common_attrs = dict(
        kwargs,
        srcs = [":" + gen_name],
        deps = [":" + gen_name],
    )

    cc_library_shared(
        name = name,
        **common_attrs
    )

    cc_library_static(
        name = name + "_bp2build_cc_library_static",
        **common_attrs
    )
