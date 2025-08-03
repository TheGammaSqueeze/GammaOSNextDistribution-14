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

load("//build/bazel/rules/java:sdk_library.bzl", "JavaSdkLibraryInfo")

METALAVA_ARGS = [
    "--quiet",
    "--format=v2",
]

def _get_inputs(ctx):
    inputs = []
    inputs.extend(ctx.files.base)
    from_deps = []
    if ctx.attr.scope == "public":
        from_deps = [d[JavaSdkLibraryInfo].public for d in ctx.attr.deps]
    elif ctx.attr.scope == "system":
        from_deps = [d[JavaSdkLibraryInfo].system for d in ctx.attr.deps]
    elif ctx.attr.scope == "module-lib":
        from_deps = [d[JavaSdkLibraryInfo].module_lib for d in ctx.attr.deps]
    elif ctx.attr.scope == "system-server":
        from_deps = [d[JavaSdkLibraryInfo].system_server for d in ctx.attr.deps]
    inputs.extend(from_deps)
    return depset(inputs)

def _get_output_name(ctx):
    output_name = "current.txt"
    if ctx.attr.scope != "public":
        output_name = ctx.attr.scope + "-" + output_name
    return output_name

def _merged_txts_impl(ctx):
    output = ctx.actions.declare_file(_get_output_name(ctx))
    inputs = _get_inputs(ctx)
    args = ctx.actions.args()
    args.add_all(METALAVA_ARGS)
    args.add_all(inputs)
    args.add("--api", output)
    ctx.actions.run(
        outputs = [output],
        inputs = inputs,
        executable = ctx.executable._metalava,
        arguments = [args],
    )
    return [DefaultInfo(files = depset([output]))]

merged_txts = rule(
    implementation = _merged_txts_impl,
    attrs = {
        "scope": attr.string(
            doc = "api scope",
        ),
        "base": attr.label(
            mandatory = True,
            allow_single_file = True,
            doc = "the target used to get the checked-in base current.txt",
        ),
        "deps": attr.label_list(
            mandatory = True,
            allow_empty = False,
            providers = [JavaSdkLibraryInfo],
        ),
        "_metalava": attr.label(
            default = "//tools/metalava/metalava:metalava",
            executable = True,
            cfg = "exec",
        ),
    },
)
