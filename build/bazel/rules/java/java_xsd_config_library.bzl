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

load(":library.bzl", "java_library")

# Return the relative directory where the java file should be generated
def _java_gen_dir(ctx):
    return ctx.attr.name

# Return the filename of the srcjar containing the generated .java files
def _java_gen_srcjar(ctx):
    return ctx.attr.name + ".srcjar"

def _xsdc_args(ctx, intermediate_dir):
    args = ctx.actions.args()
    args.add(ctx.file.src)
    args.add("-p", ctx.attr.package_name)
    args.add("-j")  # Generate java
    args.add("-o", intermediate_dir.path)  # Pass the output directory

    if ctx.attr.nullability:
        args.add("-n")

    if ctx.attr.gen_has:
        args.add("-g")

    if ctx.attr.gen_writer:
        args.add("-w")

    if ctx.attr.boolean_getter:
        args.add("-b")

    for root_element in ctx.attr.root_elements:
        args.add("-r", root_element)

    return args

def _zip_args(ctx, output_srcjar, intermediate_dir):
    args = ctx.actions.args()
    args.add("-jar")
    args.add("-o", output_srcjar)
    args.add("-C", intermediate_dir.path)
    args.add("-D", intermediate_dir.path)

    # The java files inside the sandbox are symlinks
    # Instruct soong_zip to follow the symlinks
    args.add("-symlinks=false")
    return args

def _java_xsd_codegen_impl(ctx):
    intermediate_dir = ctx.actions.declare_directory(
        _java_gen_dir(ctx),
    )
    output_srcjar = ctx.actions.declare_file(_java_gen_srcjar(ctx))

    # Run xsdc to generate the .java files in an intermedite directory
    ctx.actions.run(
        executable = ctx.executable._xsdc,
        inputs = [ctx.file.src] + ctx.files.include_files,
        outputs = [intermediate_dir],
        arguments = [
            _xsdc_args(ctx, intermediate_dir),
        ],
        tools = [
            ctx.executable._xsdc,
        ],
        mnemonic = "XsdcJavaCompile",
        progress_message = "Generating java files for %s" % ctx.file.src,
    )

    # Zip the intermediate directory to a srcjar
    ctx.actions.run(
        executable = ctx.executable._soong_zip,
        inputs = [intermediate_dir],
        outputs = [output_srcjar],
        arguments = [
            _zip_args(ctx, output_srcjar, intermediate_dir),
        ],
        tools = [
            ctx.executable._soong_zip,
        ],
        mnemonic = "XsdcJavaZip",
        progress_message = "Generating srcjar for %s" % ctx.file.src,
    )

    return [
        DefaultInfo(
            files = depset([output_srcjar]),
        ),
    ]

_java_xsd_codegen = rule(
    implementation = _java_xsd_codegen_impl,
    doc = "This rule generates .java parser files from an xsd file using xsdc",
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
            doc = "Package name to use in the generated .java files",
            mandatory = True,
        ),
        "nullability": attr.bool(
            doc = "Add @NonNull or @Nullable annotation to the generated .java files",
            default = False,
        ),
        "gen_has": attr.bool(
            doc = "Generate public hasX() method",
            default = False,
        ),
        "gen_writer": attr.bool(
            doc = "Add xml writer to the generated .java files",
        ),
        "boolean_getter": attr.bool(
            doc = "Whether getter name of boolean element or attribute is getX or isX. If true, getter name is isX",
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
        "_soong_zip": attr.label(
            executable = True,
            cfg = "exec",
            allow_single_file = True,
            default = Label("//build/soong/zip/cmd:soong_zip"),
        ),
    },
)

def java_xsd_config_library(
        name,
        src,
        sdk_version = "none",
        include_files = [],
        package_name = "",
        nullability = False,
        gen_has = False,
        gen_writer = False,
        boolean_getter = False,
        root_elements = [],
        deps = [],
        **kwargs):
    """
    Generate .java parser file from .xsd file using xsdc and wrap it in a java_library.

    """
    _gen = name + "_gen"

    _java_xsd_codegen(
        name = _gen,
        src = src,
        include_files = include_files,
        package_name = package_name,
        nullability = nullability,
        gen_has = gen_has,
        gen_writer = gen_writer,
        boolean_getter = boolean_getter,
        root_elements = root_elements,
        **kwargs
    )

    java_library(
        name = name,
        srcs = [_gen],
        deps = deps,
        sdk_version = sdk_version,
        **kwargs
    )
