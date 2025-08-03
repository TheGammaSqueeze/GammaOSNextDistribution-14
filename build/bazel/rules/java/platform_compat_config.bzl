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

"""Bazel rules for platform_compat_config"""

PlatformCompatConfigInfo = provider(
    "platform_compat_config extracts and installs compat_config",
    fields = {
        "config_file": "File containing config for embedding on the device",
        "metadata_file": "File containing metadata about merged config",
    },
)

def _platform_compat_config_impl(ctx):
    config_file = ctx.actions.declare_file(ctx.attr.name + ".xml")
    metadata_file = ctx.actions.declare_file(ctx.attr.name + "_meta.xml")

    input_jar_files = ctx.attr.src[JavaInfo].compile_jars.to_list()

    args = ctx.actions.args()
    args.add_all(["--jar"] + input_jar_files)
    args.add_all(["--device-config", config_file])
    args.add_all(["--merged-config", metadata_file])

    ctx.actions.run(
        outputs = [config_file, metadata_file],
        inputs = input_jar_files,
        executable = ctx.executable._tool_name,
        arguments = [args],
        mnemonic = "ExtractCompatConfig",
    )

    return PlatformCompatConfigInfo(
        config_file = config_file,
        metadata_file = metadata_file,
    )

platform_compat_config = rule(
    implementation = _platform_compat_config_impl,
    attrs = {
        "src": attr.label(
            mandatory = True,
            providers = [JavaInfo],
        ),
        "_tool_name": attr.label(
            default = "//tools/platform-compat/build:process-compat-config",
            executable = True,
            cfg = "exec",
        ),
    },
    provides = [PlatformCompatConfigInfo],
)
