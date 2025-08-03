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

"""Build rule for converting `.l` or `.ll` to C or C++ sources with Flex.

Uses flex (and m4 under the hood) to convert .l and .ll source files into
.c and .cc files. Does not support .lex or .lpp extensions

Examples
--------

This is a simple example.
```
genlex(
    name = "html_lex",
    src = "html.l",
)
```

This example uses some options for flex.
```
genlex(
    name = "rules_l",
    src = "rules.l",
    lexopts = ["-d", "-v"],
)
```
"""

def _genlex_impl(ctx):
    """Implementation for genlex rule."""

    # TODO(b/190006308): When fixed, l and ll sources can coexist. Remove this.
    exts = [f.extension for f in ctx.files.srcs]
    contains_l = False
    contains_ll = False
    for ext in exts:
        if ext == "l":
            contains_l = True
        if ext == "ll":
            contains_ll = True
    if contains_l and contains_ll:
        fail(
            "srcs contains both .l and .ll files. Please use separate targets.",
        )

    outputs = []
    for src_file in ctx.files.srcs:
        args = ctx.actions.args()
        output_filename = ""

        src_ext = src_file.extension
        split_filename = src_file.basename.partition(".")
        filename_without_ext = split_filename[0]

        if src_ext == "l":
            output_filename = paths.replace_extension(filename_without_ext, ".c")
        elif src_ext == "ll":
            output_filename = paths.replace_extension(filename_without_ext, ".cc")
        output_file = ctx.actions.declare_file(output_filename)
        outputs.append(output_file)
        args.add("-o", output_file.path)

        args.add_all(ctx.attr.lexopts)
        args.add(src_file)

        ctx.actions.run(
            executable = ctx.executable._flex,
            env = {
                "M4": ctx.executable._m4.path,
            },
            arguments = [args],
            inputs = [src_file],
            tools = [ctx.executable._m4],
            outputs = [output_file],
            mnemonic = "Flex",
            progress_message = "Generating %s from %s" % (
                output_filename,
                src_file.short_path,
            ),
        )
    return [DefaultInfo(files = depset(outputs))]

genlex = rule(
    implementation = _genlex_impl,
    doc = "Generate C/C++-language sources from a lex file using Flex.",
    attrs = {
        "srcs": attr.label_list(
            mandatory = True,
            allow_files = [".l", ".ll"],
            doc = "The lex source file for this rule",
        ),
        "lexopts": attr.string_list(
            doc = "A list of options to be added to the flex command line.",
        ),
        "_flex": attr.label(
            default = "//prebuilts/build-tools:flex",
            executable = True,
            cfg = "exec",
        ),
        "_m4": attr.label(
            default = "//prebuilts/build-tools:m4",
            executable = True,
            cfg = "exec",
        ),
    },
)
