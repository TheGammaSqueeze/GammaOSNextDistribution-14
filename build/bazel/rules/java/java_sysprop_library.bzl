# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Rules for generating java code from sysprop_library modules
"""

load("//build/bazel/rules/sysprop:sysprop_library.bzl", "SyspropGenInfo")
load(":sdk_transition.bzl", "sdk_transition")

# TODO: b/301122615 - Implement stubs rule and macro for both

_java_sysprop_library_attrs = {
    "dep": attr.label(mandatory = True),
    "_sdk_version": attr.string(default = "core_current"),
    # TODO: TBD - Add other possible stub libs
    "_platform_stubs": attr.label(
        default = "//system/tools/sysprop:sysprop-library-stub-platform",
    ),
    "_sysprop_java": attr.label(
        default = "//system/tools/sysprop:sysprop_java",
        executable = True,
        cfg = "exec",
    ),
    "_soong_zip": attr.label(
        default = "//build/soong/zip/cmd:soong_zip",
        executable = True,
        cfg = "exec",
    ),
    "_allowlist_function_transition": attr.label(
        default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
    ),
}

def _gen_java(
        ctx,
        srcs,
        scope):
    outputs = []
    all_srcs = []
    for src in srcs:
        all_srcs.extend(src.files.to_list())

    for src_file in all_srcs:
        output_subpath = src_file.short_path.replace(
            ctx.label.package + "/",
            "",
            1,
        )
        output_srcjar_file = ctx.actions.declare_file(
            "%s.srcjar" % output_subpath,
        )
        output_tmp_dir_path = "%s.tmp" % output_srcjar_file.path
        ctx.actions.run_shell(
            tools = [
                ctx.executable._sysprop_java,
                ctx.executable._soong_zip,
            ],
            inputs = [src_file],
            outputs = [output_srcjar_file],
            command = """
            rm -rf {dir} && mkdir -p {dir} &&
            {sysprop_java} --scope {scope} --java-output-dir {dir} {input} &&
            {soong_zip} -jar -o {output_srcjar} -C {dir} -D {dir}
            """.format(
                dir = output_tmp_dir_path,
                sysprop_java = ctx.executable._sysprop_java.path,
                scope = scope,
                input = src_file.path,
                soong_zip = ctx.executable._soong_zip.path,
                output_srcjar = output_srcjar_file.path,
            ),
            mnemonic = "SyspropJava",
            progress_message = "Generating srcjar from {}".format(
                src_file.basename,
            ),
        )
        outputs.append(output_srcjar_file)
    return outputs

def _compile_java(
        name,
        ctx,
        srcs,
        deps):
    out_jar = ctx.actions.declare_file("%s.jar" % name)
    java_info = java_common.compile(
        ctx,
        source_jars = srcs,
        deps = deps,
        output = out_jar,
        java_toolchain = ctx.toolchains["@bazel_tools//tools/jdk:toolchain_type"].java,
    )
    return java_info, out_jar

def _java_sysprop_library_impl(ctx):
    gen_srcjars = _gen_java(
        ctx,
        ctx.attr.dep[SyspropGenInfo].srcs,
        "internal",  # TODO: b/302677541 - Determine based on props
    )

    java_info, out_jar = _compile_java(
        ctx.attr.name,
        ctx,
        gen_srcjars,
        # TODO: b/302677539 - Determine based on props
        [ctx.attr._platform_stubs[JavaInfo]],
    )

    return [
        java_info,
        DefaultInfo(
            files = depset([out_jar]),
            runfiles = ctx.runfiles(
                transitive_files = depset(
                    transitive = [java_info.transitive_runtime_jars],
                ),
            ),
        ),
        OutputGroupInfo(default = depset()),
    ]

java_sysprop_library = rule(
    implementation = _java_sysprop_library_impl,
    cfg = sdk_transition,
    doc = """
    Generates java sources from the sources in the supplied sysprop_library
    target and compiles them into a jar.
    """,
    attrs = _java_sysprop_library_attrs,
    toolchains = ["@bazel_tools//tools/jdk:toolchain_type"],
    fragments = ["java"],
    provides = [JavaInfo],
)
