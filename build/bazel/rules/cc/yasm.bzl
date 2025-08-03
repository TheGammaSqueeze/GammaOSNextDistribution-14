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
load("//build/bazel/platforms:platform_utils.bzl", "platforms")

"""Build rule for converting `.asm` files to `.o` files with yasm."""

def globalFlags(ctx):
    arch = platforms.get_target_arch(ctx.attr._platform_utils)
    linux = platforms.is_target_linux_or_android(ctx.attr._platform_utils)
    darwin = platforms.is_target_darwin(ctx.attr._platform_utils)

    if linux and arch == "x86_64":
        return ["-f", "elf64", "-m", "amd64"]
    if linux and arch == "x86":
        return ["-f", "elf32", "-m", "x86"]
    if linux and arch == "arm64":
        return ["-f", "elf64", "-m", "aarch64"]
    if linux and arch == "arm":
        return ["-f", "elf32", "-m", "arm"]
    if darwin:
        return ["-f", "macho", "-m", "amd64"]

    fail("Unable to detect target platform for compiling .asm files")

def _yasm_impl(ctx):
    common_args = (globalFlags(ctx) + ctx.attr.flags +
                   ["-I" + paths.join(ctx.label.package, d) for d in ctx.attr.include_dirs])

    outputs = [ctx.actions.declare_file(paths.replace_extension(src.path, ".o")) for src in ctx.files.srcs]
    for src, out in zip(ctx.files.srcs, outputs):
        ctx.actions.run(
            inputs = ctx.files.include_srcs,  # include_srcs will contain src
            outputs = [out],
            executable = ctx.executable._yasm,
            arguments = common_args + ["-o", out.path, src.path],
            mnemonic = "yasm",
        )

    return [DefaultInfo(files = depset(outputs))]

_yasm = rule(
    implementation = _yasm_impl,
    doc = "Generate object files from a .asm file using yasm.",
    attrs = {
        "srcs": attr.label_list(
            mandatory = True,
            allow_files = [".asm"],
            doc = "The asm source files for this rule",
        ),
        "include_srcs": attr.label_list(
            allow_files = [".inc", ".asm"],
            doc = "All files that could possibly be included from source files. " +
                  "This is necessary because starlark doesn't allow adding dependencies " +
                  "via .d files.",
        ),
        "include_dirs": attr.string_list(
            doc = "Include directories",
        ),
        "flags": attr.string_list(
            doc = "A list of options to be added to the yasm command line.",
        ),
        "_yasm": attr.label(
            default = "//prebuilts/misc:yasm",
            executable = True,
            cfg = "exec",
        ),
        "_platform_utils": attr.label(
            default = Label("//build/bazel/platforms:platform_utils"),
        ),
    },
)

def yasm(
        name,
        srcs,
        include_dirs = [],
        flags = [],
        target_compatible_with = [],
        tags = []):
    _yasm(
        name = name,
        srcs = srcs,
        flags = flags,
        include_dirs = include_dirs,
        include_srcs = native.glob(["**/*.inc", "**/*.asm"]),
        target_compatible_with = target_compatible_with,
        tags = tags,
    )
