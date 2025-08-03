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

load("@bazel_skylib//lib:paths.bzl", "paths")
load("//build/bazel/rules:proto_file_utils.bzl", "proto_file_utils")
load(":cc_library_common.bzl", "create_ccinfo_for_includes")
load(":cc_library_static.bzl", "cc_library_static")

_SOURCES_KEY = "sources"
_HEADERS_KEY = "headers"
PROTO_GEN_NAME_SUFFIX = "_proto_gen"

def _cc_proto_sources_gen_rule_impl(ctx):
    out_flags = []
    plugin_executable = None
    out_arg = None
    if ctx.attr.plugin:
        plugin_executable = ctx.executable.plugin
    else:
        out_arg = "--cpp_out"
        if ctx.attr.out_format:
            out_flags.append(ctx.attr.out_format)

    srcs = []
    hdrs = []
    includes = []
    proto_infos = []
    transitive_proto_infos = []

    for dep in ctx.attr.deps:
        proto_info = dep[ProtoInfo]
        proto_infos.append(proto_info)
        if proto_info.proto_source_root == ".":
            includes.append(paths.join(ctx.label.name, ctx.label.package))

        includes.append(ctx.label.name)

    for transitive_dep in ctx.attr.transitive_deps:
        transitive_proto_infos.append(transitive_dep[ProtoInfo])

    outs = _generate_cc_proto_action(
        proto_infos = proto_infos,
        protoc = ctx.executable._protoc,
        ctx = ctx,
        is_cc = True,
        out_flags = out_flags,
        plugin_executable = plugin_executable,
        out_arg = out_arg,
        transitive_proto_infos = transitive_proto_infos,
    )
    srcs.extend(outs[_SOURCES_KEY])
    hdrs.extend(outs[_HEADERS_KEY])

    return [
        DefaultInfo(files = depset(direct = srcs + hdrs)),
        create_ccinfo_for_includes(ctx, hdrs = hdrs, includes = includes),
    ]

_cc_proto_sources_gen = rule(
    implementation = _cc_proto_sources_gen_rule_impl,
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
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
    provides = [CcInfo],
)

def _src_extension(is_cc):
    if is_cc:
        return "cc"
    return "c"

def _generate_cc_proto_action(
        proto_infos,
        protoc,
        ctx,
        transitive_proto_infos,
        plugin_executable,
        out_arg,
        out_flags,
        is_cc):
    type_dictionary = {
        _SOURCES_KEY: ".pb." + _src_extension(is_cc),
        _HEADERS_KEY: ".pb.h",
    }
    return proto_file_utils.generate_proto_action(
        proto_infos,
        protoc,
        ctx,
        type_dictionary,
        out_flags,
        plugin_executable = plugin_executable,
        out_arg = out_arg,
        mnemonic = "CcProtoGen",
        transitive_proto_infos = transitive_proto_infos,
    )

def _cc_proto_library(
        name,
        deps = [],
        transitive_deps = [],
        cc_deps = [],
        plugin = None,
        tags = [],
        target_compatible_with = [],
        out_format = None,
        **kwargs):
    proto_lib_name = name + PROTO_GEN_NAME_SUFFIX

    _cc_proto_sources_gen(
        name = proto_lib_name,
        deps = deps,
        transitive_deps = transitive_deps,
        plugin = plugin,
        out_format = out_format,
        tags = ["manual"],
    )

    cc_library_static(
        name = name,
        srcs = [":" + proto_lib_name],
        deps = [proto_lib_name] + cc_deps,
        local_includes = ["."],
        tags = tags,
        target_compatible_with = target_compatible_with,
        **kwargs
    )

def cc_lite_proto_library(
        name,
        deps = [],
        transitive_deps = [],
        cc_deps = [],
        plugin = None,
        tags = [],
        target_compatible_with = [],
        **kwargs):
    _cc_proto_library(
        name,
        deps = deps,
        transitive_deps = transitive_deps,
        cc_deps = cc_deps + ["//external/protobuf:libprotobuf-cpp-lite"],
        plugin = plugin,
        tags = tags,
        target_compatible_with = target_compatible_with,
        out_format = "lite",
        **kwargs
    )

def cc_proto_library(
        name,
        deps = [],
        transitive_deps = [],
        cc_deps = [],
        plugin = None,
        tags = [],
        target_compatible_with = [],
        **kwargs):
    _cc_proto_library(
        name,
        deps = deps,
        transitive_deps = transitive_deps,
        cc_deps = cc_deps + ["//external/protobuf:libprotobuf-cpp-full"],
        plugin = plugin,
        tags = tags,
        target_compatible_with = target_compatible_with,
        **kwargs
    )
