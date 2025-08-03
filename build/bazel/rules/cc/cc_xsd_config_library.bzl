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

# Return the relative directory where the cpp files should be generated
def _cpp_gen_dir(ctx):
    return ctx.attr.name

# Return the relative directory where the h files should be generated
def _h_gen_dir(ctx):
    return paths.join(
        _cpp_gen_dir(ctx),
        "include",
    )

def _cc_xsdc_args(ctx):
    args = ctx.actions.args()
    args.add(ctx.file.src)
    args.add("-p", ctx.attr.package_name)
    args.add("-c")  # Generate cpp sources

    if ctx.attr.gen_writer:
        args.add("-w")

    if ctx.attr.enums_only:
        args.add("-e")

    if ctx.attr.parser_only:
        # parser includes the enum.h file so generate both
        # in the wrapper cc_static_library, we will only include the .o file of the parser
        pass

    if ctx.attr.boolean_getter:
        args.add("-b")

    if ctx.attr.tinyxml:
        args.add("-t")

    for root_element in ctx.attr.root_elements:
        args.add("-r", root_element)

    return args

# Returns a tuple of cpp and h filenames that should be generated
def _cc_xsdc_outputs(ctx):
    filename_stem = ctx.attr.package_name.replace(".", "_")
    parser_cpp = filename_stem + ".cpp"
    parser_h = filename_stem + ".h"
    enums_cpp = filename_stem + "_enums.cpp"
    enums_h = filename_stem + "_enums.h"
    if ctx.attr.parser_only:
        # parser_cpp includes enums_h, so we need to return both .h files
        return [parser_cpp], [parser_h, enums_h]
    elif ctx.attr.enums_only:
        return [enums_cpp], [enums_h]

    # Default: Generate both parser and enums
    return [parser_cpp, enums_cpp], [parser_h, enums_h]

def _cc_xsd_codegen_impl(ctx):
    outputs_cpp, outputs_h = [], []
    cpp_filenames, h_filenames = _cc_xsdc_outputs(ctx)

    # Declare the cpp files
    for cpp_filename in cpp_filenames:
        outputs_cpp.append(
            ctx.actions.declare_file(
                paths.join(
                    _cpp_gen_dir(ctx),
                    cpp_filename,
                ),
            ),
        )

    # Declare the h files
    for h_filename in h_filenames:
        outputs_h.append(
            ctx.actions.declare_file(
                paths.join(
                    _h_gen_dir(ctx),
                    h_filename,
                ),
            ),
        )

    args = _cc_xsdc_args(ctx)

    # Pass the output directory
    args.add("-o", outputs_cpp[0].dirname)

    ctx.actions.run(
        executable = ctx.executable._xsdc,
        inputs = [ctx.file.src] + ctx.files.include_files,
        outputs = outputs_cpp + outputs_h,
        arguments = [args],
        mnemonic = "XsdcCppCompile",
    )

    return [
        DefaultInfo(
            # Return the CPP files.
            # Skip headers since rdep does not compile headers.
            files = depset(outputs_cpp),
        ),
        create_ccinfo_for_includes(
            ctx,
            hdrs = outputs_h,
            includes = [_h_gen_dir(ctx)],
        ),
    ]

_cc_xsd_codegen = rule(
    implementation = _cc_xsd_codegen_impl,
    doc = "This rule generates .cpp/.h files from an xsd file using xsdc",
    attrs = {
        "src": attr.label(
            allow_single_file = [".xsd"],
            doc = "The main xsd file",
            mandatory = True,
        ),
        "include_files": attr.label_list(
            allow_files = [".xsd"],
            doc = "The (transitive) xsd files included by `src` using xs:include",
        ),
        "package_name": attr.string(
            doc = "Namespace to use in the generated .cpp file",
            mandatory = True,
        ),
        "gen_writer": attr.bool(
            doc = "Add xml writer to the generated .cpp file",
        ),
        "enums_only": attr.bool(),
        "parser_only": attr.bool(),
        "boolean_getter": attr.bool(
            doc = "Whether getter name of boolean element or attribute is getX or isX. If true, getter name is isX",
            default = False,
        ),
        "tinyxml": attr.bool(
            doc = "Generate code that uses libtinyxml2 instead of libxml2",
            default = False,
        ),
        "root_elements": attr.string_list(
            doc = "If set, xsdc will generate parser code only for the specified root elements",
        ),
        "_xsdc": attr.label(
            executable = True,
            cfg = "exec",
            default = Label("//system/tools/xsdc"),
        ),
    },
    provides = [
        CcInfo,
    ],
)

def cc_xsd_config_library(
        name,
        src,
        include_files = [],
        package_name = "",
        gen_writer = False,
        enums_only = False,
        parser_only = False,
        boolean_getter = False,
        tinyxml = False,
        root_elements = [],
        deps = [],
        implementation_dynamic_deps = [],
        **kwargs):
    """
    Generate .cpp/.h sources from .xsd file using xsdc and wrap it in a cc_static_library.

    """
    _gen = name + "_gen"

    _cc_xsd_codegen(
        name = _gen,
        src = src,
        include_files = include_files,
        package_name = package_name,
        gen_writer = gen_writer,
        enums_only = enums_only,
        parser_only = parser_only,
        boolean_getter = boolean_getter,
        tinyxml = tinyxml,
        root_elements = root_elements,
        **kwargs
    )

    cc_library_static(
        name = name,
        srcs = [_gen],
        deps = deps + [_gen],  # Generated hdrs
        implementation_dynamic_deps = implementation_dynamic_deps,
        **kwargs
    )
