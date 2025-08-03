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

load(":prebuilt_file.bzl", "PrebuiltFileInfo")

def _prebuilt_xml_impl(ctx):
    schema = ctx.file.schema

    src = ctx.file.src

    args = ctx.actions.args()
    inputs = [src]

    if schema != None:
        if schema.extension == "dtd":
            args.add("--dtdvalid", schema.path)
        elif schema.extension == "xsd":
            args.add("--schema", schema.path)
        inputs.append(schema)

    args.add(src)
    args.add(">")
    args.add("/dev/null")
    args.add("&&")
    args.add("touch")
    args.add("-a")

    validation_output = ctx.actions.declare_file(ctx.attr.name + ".validation")
    args.add(validation_output.path)

    ctx.actions.run(
        outputs = [validation_output],
        inputs = inputs,
        executable = ctx.executable._xml_validation_tool,
        arguments = [args],
        mnemonic = "XMLValidation",
    )

    filename = ""

    if ctx.attr.filename_from_src and ctx.attr.filename != "":
        fail("filename is set. filename_from_src cannot be true")
    elif ctx.attr.filename != "":
        filename = ctx.attr.filename
    elif ctx.attr.filename_from_src:
        filename = src
    else:
        filename = ctx.attrs.name

    return [
        PrebuiltFileInfo(
            src = src,
            dir = "etc/xml",
            filename = filename,
        ),
        DefaultInfo(files = depset([src])),
        OutputGroupInfo(_validation = depset([validation_output])),
    ]

prebuilt_xml = rule(
    doc = """
    prebuilt_etc_xml installs an xml file under <partition>/etc/<subdir>.
    It also optionally validates the xml file against the schema.
    """,
    implementation = _prebuilt_xml_impl,
    attrs = {
        "src": attr.label(
            mandatory = True,
            allow_single_file = True,
        ),
        "schema": attr.label(
            allow_single_file = [".dtd", ".xsd"],
            doc = "Optional DTD or XSD that will be used to validate the xml file",
        ),
        "filename": attr.string(doc = "Optional name for the installed file"),
        "filename_from_src": attr.bool(
            doc = "Optional. When filename is not provided and" +
                  "filename_from_src is true, name for the installed file" +
                  "will be set from src",
        ),
        "_xml_validation_tool": attr.label(
            default = "//external/libxml2:xmllint",
            executable = True,
            cfg = "exec",
        ),
    },
)
