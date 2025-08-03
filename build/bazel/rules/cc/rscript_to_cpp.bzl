"""Copyright (C) 2022 The Android Open Source Project
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
load("@soong_injection//cc_toolchain:config_constants.bzl", "constants")
load("//build/bazel/platforms:platform_utils.bzl", "platforms")

def rs_flags(ctx):
    # TODO handle target api flags once there is support for sdk versions b/245584567
    flags = ["-Wall", "-Werror"]

    flags.extend(ctx.attr.flags)

    arch = platforms.get_target_arch(ctx.attr._platform_utils)

    if arch in ["x86", "arm"]:
        flags.append("-m32")
    elif arch in ["x86_64", "arm64"]:
        flags.append("-m64")

    for flag in constants.RsGlobalIncludes:
        flags.extend(["-I", flag])

    return flags

def _rscript_to_cpp_impl(ctx):
    rs_files = ctx.files.srcs

    outputs = []

    for f in rs_files:
        out_file_base = "ScriptC_" + paths.replace_extension(f.basename, "")
        outputs.append(ctx.actions.declare_file(out_file_base + ".cpp"))
        outputs.append(ctx.actions.declare_file(out_file_base + ".h"))

    args = ctx.actions.args()
    output_path = paths.join(ctx.bin_dir.path, ctx.label.package)
    args.add("-o", output_path)
    args.add("-reflect-c++")
    args.add_all(rs_flags(ctx))
    args.add_all([f.path for f in rs_files])

    ctx.actions.run(
        outputs = outputs,
        inputs = rs_files + ctx.files._rs_headers,
        executable = ctx.executable._rs_to_cc_tool,
        arguments = [args],
    )

    return [DefaultInfo(files = depset(outputs))]

rscript_to_cpp = rule(
    implementation = _rscript_to_cpp_impl,
    doc = "Generate C/C++ langauge sources from renderscript files",
    attrs = {
        "srcs": attr.label_list(
            allow_files = [".rscript", ".fs"],
            mandatory = True,
        ),
        "flags": attr.string_list(
            doc = "",
        ),
        "_rs_headers": attr.label_list(
            default = [
                "//external/clang/lib:rs_clang_headers",
                "//frameworks/rs/script_api:rs_script_api",
            ],
        ),
        "_rs_to_cc_tool": attr.label(
            # TODO use non-prebuilt llvm-rs-cc b/245736162
            default = "//prebuilts/sdk/tools:linux/bin/llvm-rs-cc",
            allow_files = True,
            cfg = "exec",
            executable = True,
        ),
        "_platform_utils": attr.label(
            default = Label("//build/bazel/platforms:platform_utils"),
        ),
    },
)
