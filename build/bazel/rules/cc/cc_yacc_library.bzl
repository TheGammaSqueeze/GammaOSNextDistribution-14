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

load("@bazel_skylib//lib:paths.bzl", "paths")
load(":cc_library_common.bzl", "create_ccinfo_for_includes")
load(":cc_library_static.bzl", "cc_library_static")

"""
Generate C/C++ parser from a .y/.yy grammar file.
The generated file might depend on external headers and has to be compiled
separately.
"""

def _cc_yacc_parser_gen_impl(ctx):
    ext = ".c"
    if paths.split_extension(ctx.file.src.basename)[1] == ".yy":
        ext = ".cpp"

    # C/CPP file
    output_c_file = ctx.actions.declare_file(
        paths.join(
            ctx.attr.name,  # Prevent name collisions (esp for tests)
            paths.replace_extension(ctx.file.src.basename, ext),
        ),
    )

    # Header file
    output_h_file = ctx.actions.declare_file(
        paths.join(
            ctx.attr.name,  # Prevent name collisions (esp for tests)
            paths.replace_extension(ctx.file.src.basename, ".h"),
        ),
    )
    outputs = [
        output_c_file,
        output_h_file,
    ]
    output_hdrs = [
        output_h_file,
    ]

    # Path of location.hh in the same dir as the generated C/CPP file
    if ctx.attr.gen_location_hh:
        location_hh_file = ctx.actions.declare_file(
            paths.join(
                ctx.attr.name,
                "location.hh",
            ),
        )
        outputs.append(location_hh_file)
        output_hdrs.append(location_hh_file)

    # Path of position.hh in the same dir as the generated C/CPP file
    if ctx.attr.gen_position_hh:
        position_hh_file = ctx.actions.declare_file(
            paths.join(
                ctx.attr.name,
                "position.hh",
            ),
        )
        outputs.append(position_hh_file)
        output_hdrs.append(position_hh_file)

    args = ctx.actions.args()
    args.add("-d")  # Generate headers
    args.add_all(ctx.attr.flags)
    args.add("--defines=" + output_h_file.path)
    args.add("-o", output_c_file)
    args.add(ctx.file.src)

    ctx.actions.run(
        executable = ctx.executable._bison,
        inputs = [ctx.file.src],
        outputs = outputs,
        arguments = [args],
        # Explicitly set some environment variables to ensure Android's hermetic tools are used.
        env = {
            "BISON_PKGDATADIR": "prebuilts/build-tools/common/bison",
            "M4": ctx.executable._m4.path,
        },
        tools = [ctx.executable._m4] + ctx.files._bison_runfiles,
        mnemonic = "YaccCompile",
    )

    return [
        DefaultInfo(
            # Return the C/C++ file.
            # Skip headers since rdep does not compile headers.
            files = depset([output_c_file]),
        ),
        create_ccinfo_for_includes(
            ctx,
            hdrs = output_hdrs,
            includes = [ctx.attr.name],
        ),
    ]

_cc_yacc_parser_gen = rule(
    implementation = _cc_yacc_parser_gen_impl,
    doc = "This rule generates a C/C++ parser from a .y/.yy grammar file using bison",
    attrs = {
        "src": attr.label(
            allow_single_file = [".y", ".yy"],
            doc = "The grammar file for the parser",
        ),
        "flags": attr.string_list(
            default = [],
            doc = "List of flags that will be used in yacc compile",
        ),
        "gen_location_hh": attr.bool(
            default = False,
            doc = "Whether the yacc file will produce a location.hh file.",
        ),
        "gen_position_hh": attr.bool(
            default = False,
            doc = "Whether the yacc file will produce a location.hh file.",
        ),
        "_m4": attr.label(
            executable = True,
            cfg = "exec",
            default = Label("//prebuilts/build-tools:m4"),
        ),
        "_bison": attr.label(
            executable = True,
            cfg = "exec",
            default = Label("//prebuilts/build-tools:bison"),
        ),
        "_bison_runfiles": attr.label(
            default = Label("//prebuilts/build-tools:bison.runfiles"),
        ),
    },
    provides = [
        CcInfo,
    ],
)

def cc_yacc_static_library(
        name,
        src,
        flags = [],
        gen_location_hh = False,
        gen_position_hh = False,
        local_includes = [],
        implementation_deps = [],
        implementation_dynamic_deps = [],
        **kwargs):
    """
    Generate C/C++ parser from .y/.yy grammar file and wrap it in a cc_library_static target.

    """
    _output_parser = name + "_parser"

    _cc_yacc_parser_gen(
        name = _output_parser,
        src = src,
        flags = flags,
        gen_location_hh = gen_location_hh,
        gen_position_hh = gen_position_hh,
        **kwargs
    )

    cc_library_static(
        name = name,
        srcs = [_output_parser],
        deps = [_output_parser],  # Generated hdrs
        local_includes = local_includes,
        implementation_deps = implementation_deps,
        implementation_dynamic_deps = implementation_dynamic_deps,
        **kwargs
    )
