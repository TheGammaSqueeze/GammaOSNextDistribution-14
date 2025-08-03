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

LANGUAGE_CC_HEADERS = "c++-headers"
LANGUAGE_CC_SOURCES = "c++-sources"
INTERFACE_HEADER_PREFIXES = ["I", "Bs", "BnHw", "BpHw", "IHw"]
TYPE_HEADER_PREFIXES = ["", "hw"]

def _generate_hidl_action(
        hidl_info,
        language,
        ctx):
    """ Utility function for generating code for the given language from HIDL interface."""

    output_dir = paths.join(ctx.bin_dir.path, ctx.label.package)

    args = ctx.actions.args()

    args.add("-R")
    args.add_all(["-p", "."])
    args.add_all(["-o", output_dir])
    args.add_all(["-L", language])
    for root in hidl_info.transitive_roots.to_list():
        args.add_all(["-r", root])

    args.add(hidl_info.fq_name)

    hidl_srcs = hidl_info.srcs.to_list()
    inputs = depset(
        direct = hidl_srcs,
        # These are needed for hidl-gen to correctly generate the code.
        transitive = [hidl_info.transitive_srcs, hidl_info.transitive_root_interface_files],
    )

    outputs = _generate_and_declare_output_files(
        ctx,
        hidl_info.fq_name,
        language,
        hidl_srcs,
    )

    ctx.actions.run(
        inputs = inputs,
        executable = ctx.executable._hidl_gen,
        outputs = outputs,
        arguments = [args],
        mnemonic = "HidlGen" + _get_language_string(language),
    )

    return outputs

def _get_language_string(language):
    if language == LANGUAGE_CC_HEADERS:
        return "CcHeader"
    elif language == LANGUAGE_CC_SOURCES:
        return "Cc"

def _generate_and_declare_output_files(
        ctx,
        fq_name,
        language,
        hidl_srcs):
    files = []

    # Break FQ name such as android.hardware.neuralnetworks@1.3 into
    # android/hardware/neuralnetworks/1.3 which is the directory structure
    # that hidl-gen uses to generate files.
    parts = fq_name.split("@")
    dirname = paths.join(parts[0].replace(".", "/"), parts[1])

    for src in hidl_srcs:
        filename = src.basename

        # "I" prefix indicates that this file is a interface file, the rest are
        # files that define types. Interface files and type files are treated
        # differently when generating code using hidl-gen.
        basename = filename.removeprefix("I").removesuffix(".hal")
        interface = _is_interface(filename)
        if language == LANGUAGE_CC_HEADERS:
            if interface:
                prefixes = INTERFACE_HEADER_PREFIXES
            else:
                prefixes = TYPE_HEADER_PREFIXES
            for prefix in prefixes:
                out_name = paths.join(dirname, prefix + basename + ".h")
                declared = ctx.actions.declare_file(out_name)
                files.append(declared)
        elif language == LANGUAGE_CC_SOURCES:
            if interface:
                out_name = paths.join(dirname, basename + "All.cpp")
            else:
                out_name = paths.join(dirname, basename + ".cpp")
            declared = ctx.actions.declare_file(out_name)
            files.append(declared)

    return files

def _is_interface(filename):
    if not filename.endswith(".hal"):
        fail("HIDL source file must be a .hal file: %s" % filename)

    return filename.startswith("I")

hidl_file_utils = struct(
    generate_hidl_action = _generate_hidl_action,
)
