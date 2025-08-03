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

load("@bazel_skylib//lib:paths.bzl", "paths")

def _java_resources_impl(ctx):
    java_runtime = ctx.attr._runtime[java_common.JavaRuntimeInfo]

    output_file = ctx.actions.declare_file(ctx.attr.name + "_java_resources.jar")

    ctx.actions.run_shell(
        outputs = [output_file],
        inputs = ctx.files.resources,
        tools = java_runtime.files,
        command = "{} cvf {} -C {} .".format(
            paths.join(java_runtime.java_home, "bin", "jar"),
            output_file.path,
            ctx.attr.resource_strip_prefix,
        ),
    )

    compile_jar = ctx.actions.declare_file(ctx.attr.name + "_java_resources-ijar.jar")
    java_common.run_ijar(
        actions = ctx.actions,
        jar = output_file,
        java_toolchain = ctx.toolchains["@bazel_tools//tools/jdk:toolchain_type"].java,
    )

    return [
        JavaInfo(
            output_jar = output_file,
            compile_jar = compile_jar,
        ),
        DefaultInfo(files = depset([output_file])),
    ]

java_resources = rule(
    doc = """
    Package srcs into a jar, with the option of stripping a path prefix
    """,
    implementation = _java_resources_impl,
    attrs = {
        "resources": attr.label_list(allow_files = True),
        "resource_strip_prefix": attr.string(
            doc = """The path prefix to strip from resources.
                   If specified, this path prefix is stripped from every fil
                   in the resources attribute. It is an error for a resource
                   file not to be under this directory. If not specified
                   (the default), the path of resource file is determined
                   according to the same logic as the Java package of source
                   files. For example, a source file at stuff/java/foo/bar/a.txt
                    will be located at foo/bar/a.txt.""",
        ),
        "_runtime": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
            cfg = "exec",
            providers = [java_common.JavaRuntimeInfo],
        ),
    },
    toolchains = ["@bazel_tools//tools/jdk:toolchain_type"],
    provides = [JavaInfo],
)
