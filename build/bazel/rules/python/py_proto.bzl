# Copyright (C) 2022 The Android Open Source Project
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

_TYPE_DICTIONARY = {".py": "_pb2.py"}

def _py_proto_sources_gen_rule_impl(ctx):
    out_files_map = proto_file_utils.generate_proto_action(
        proto_infos = [dep[ProtoInfo] for dep in ctx.attr.deps],
        protoc = ctx.executable._protoc,
        ctx = ctx,
        type_dictionary = _TYPE_DICTIONARY,
        out_flags = [],
        plugin_executable = None,
        out_arg = "--python_out",
        mnemonic = "PyProtoGen",
        transitive_proto_infos = [dep[ProtoInfo] for dep in ctx.attr.transitive_deps],
    )

    # proto_file_utils generates the files at <package>/<label>
    # interesting examples
    # 1. foo.proto will be generated in <package>/<label>/foo_pb2.py
    # 2. foo.proto with an import prefix in proto_library will be generated in <package>/<label>/<import_prefix>/foo_pb2.py
    imports = [paths.join("__main__", ctx.label.package, ctx.label.name)]

    output_depset = depset(direct = out_files_map[".py"])

    return [
        DefaultInfo(files = output_depset),
        PyInfo(
            transitive_sources = output_depset,
            imports = depset(direct = imports),
        ),
    ]

_py_proto_sources_gen = rule(
    implementation = _py_proto_sources_gen_rule_impl,
    attrs = {
        "deps": attr.label_list(
            providers = [ProtoInfo],
            doc = "proto_library or any other target exposing ProtoInfo provider with *.proto files",
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
    },
)

def py_proto_library(
        name,
        deps = [],
        transitive_deps = [],
        target_compatible_with = [],
        data = [],
        **kwargs):
    proto_lib_name = name + "_proto_gen"

    _py_proto_sources_gen(
        name = proto_lib_name,
        deps = deps,
        transitive_deps = transitive_deps,
        **kwargs
    )

    # There may be a better way to do this, but proto_lib_name appears in both srcs
    # and deps because it must appear in srcs to cause the protobuf files to
    # actually be compiled, and it must appear in deps for the PyInfo provider to
    # be respected and the "imports" path to be included in this library.
    native.py_library(
        name = name,
        srcs = [":" + proto_lib_name],
        deps = [":" + proto_lib_name] + (["//external/protobuf:libprotobuf-python"] if "libprotobuf-python" not in name else []),
        data = data,
        target_compatible_with = target_compatible_with,
        **kwargs
    )
