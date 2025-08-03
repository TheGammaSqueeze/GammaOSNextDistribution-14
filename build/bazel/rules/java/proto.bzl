# Copyright (C) 2021 The Android Open Source Project
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

load("//build/bazel/rules:proto_file_utils.bzl", "proto_file_utils")
load(":library.bzl", "java_library")

def _java_proto_sources_gen_rule_impl(ctx):
    out_flags = []
    plugin_executable = None
    out_arg = None
    if ctx.attr.plugin:
        plugin_executable = ctx.executable.plugin
    else:
        out_arg = "--java_out"
        if ctx.attr.out_format:
            out_flags.append(ctx.attr.out_format)

    srcs = []
    proto_infos = []

    for dep in ctx.attr.deps:
        proto_infos.append(dep[ProtoInfo])

    out_jar = _generate_java_proto_action(
        proto_infos = proto_infos,
        protoc = ctx.executable._protoc,
        ctx = ctx,
        out_flags = out_flags,
        plugin_executable = plugin_executable,
        out_arg = out_arg,
        transitive_proto_infos = [dep[ProtoInfo] for dep in ctx.attr.transitive_deps],
    )
    srcs.append(out_jar)

    return [
        DefaultInfo(files = depset(direct = srcs)),
    ]

java_proto_sources_gen = rule(
    implementation = _java_proto_sources_gen_rule_impl,
    attrs = {
        "deps": attr.label_list(
            providers = [ProtoInfo],
            doc = """
proto_library or any other target exposing ProtoInfo provider with *.proto files
""",
            mandatory = True,
        ),
        "transitive_deps": attr.label_list(
            providers = [ProtoInfo],
            doc = """
proto_library that will be added to aprotoc -I when compiling the direct .proto sources.
WARNING: This is an experimental attribute and is expected to be deprecated in the future.
""",
        ),
        "_protoc": attr.label(
            default = Label("//external/protobuf:aprotoc"),
            executable = True,
            cfg = "exec",
        ),
        "plugin": attr.label(
            executable = True,
            cfg = "exec",
        ),
        "out_format": attr.string(
            doc = """
Optional argument specifying the out format, e.g. lite.
If not provided, defaults to full protos.
""",
        ),
    },
    toolchains = ["@bazel_tools//tools/jdk:toolchain_type"],
)

def _generate_java_proto_action(
        proto_infos,
        protoc,
        ctx,
        plugin_executable,
        out_arg,
        out_flags,
        transitive_proto_infos):
    return proto_file_utils.generate_jar_proto_action(
        proto_infos,
        protoc,
        ctx,
        out_flags,
        plugin_executable = plugin_executable,
        out_arg = out_arg,
        mnemonic = "JavaProtoGen",
        transitive_proto_infos = transitive_proto_infos,
    )

def _java_proto_library(
        name,
        deps = [],
        transitive_deps = [],
        plugin = None,
        out_format = None,
        proto_dep = None,
        sdk_version = "core_current",
        **kwargs):
    proto_sources_name = name + "_proto_gen"

    java_proto_sources_gen(
        name = proto_sources_name,
        deps = deps,
        transitive_deps = transitive_deps,
        plugin = plugin,
        out_format = out_format,
        tags = ["manual"],
    )

    deps = kwargs.pop("additional_proto_deps", [])
    if proto_dep and proto_dep not in deps:
        deps.append(proto_dep)

    java_library(
        name = name,
        srcs = [proto_sources_name],
        deps = deps,
        sdk_version = sdk_version,
        exports = [proto_dep],
        **kwargs
    )

def java_nano_proto_library(
        name,
        plugin = "//external/protobuf:protoc-gen-javanano",
        **kwargs):
    _java_proto_library(
        name,
        plugin = plugin,
        proto_dep = "//external/protobuf:libprotobuf-java-nano",
        **kwargs
    )

def java_micro_proto_library(
        name,
        plugin = "//external/protobuf:protoc-gen-javamicro",
        **kwargs):
    _java_proto_library(
        name,
        plugin = plugin,
        proto_dep = "//external/protobuf:libprotobuf-java-micro",
        **kwargs
    )

def java_lite_proto_library(
        name,
        plugin = None,
        **kwargs):
    _java_proto_library(
        name,
        plugin = plugin,
        out_format = "lite",
        proto_dep = "//external/protobuf:libprotobuf-java-lite",
        **kwargs
    )

def java_stream_proto_library(
        name,
        plugin = "//frameworks/base/tools/streaming_proto:protoc-gen-javastream",
        **kwargs):
    _java_proto_library(
        name,
        plugin = plugin,
        **kwargs
    )

def java_proto_library(
        name,
        plugin = None,
        **kwargs):
    _java_proto_library(
        name,
        plugin = plugin,
        proto_dep = "//external/protobuf:libprotobuf-java-full",
        **kwargs
    )
