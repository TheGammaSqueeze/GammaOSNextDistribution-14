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
load("//build/bazel/rules:prebuilt_file.bzl", "PrebuiltFileInfo")

def _linker_config_impl(ctx):
    output_file = ctx.actions.declare_file(paths.replace_extension(ctx.file.src.basename, ".pb"))

    args = ctx.actions.args()
    args.add("proto")
    args.add("-s", ctx.file.src.path)
    args.add("-o", output_file.path)

    ctx.actions.run(
        outputs = [output_file],
        inputs = [ctx.file.src],
        executable = ctx.executable._conv_linker_config,
        arguments = [args],
    )

    return [
        DefaultInfo(
            files = depset([output_file]),
        ),
        PrebuiltFileInfo(
            src = output_file,
            dir = "etc",
            filename = "linker.config.pb",
        ),
    ]

linker_config = rule(
    doc = """
    linker_config generates protobuf file from json file. This protobuf file will
    be used from linkerconfig while generating ld.config.txt. Format of this file
    can be found from
    https://android.googlesource.com/platform/system/linkerconfig/+/master/README.md
    """,
    implementation = _linker_config_impl,
    attrs = {
        "src": attr.label(allow_single_file = [".json"], mandatory = True, doc = "source linker configuration property file"),
        "_conv_linker_config": attr.label(default = "//build/soong/scripts:conv_linker_config", cfg = "exec", executable = True),
    },
)
