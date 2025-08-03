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

"""This file defines the rule that builds android partitions."""

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("//build/bazel/rules:build_fingerprint.bzl", "BuildFingerprintInfo")

_IMAGE_TYPES = [
    "system",
    "system_other",
    "userdata",
    "cache",
    "vendor",
    "product",
    "system_ext",
    "odm",
    "vendor_dlkm",
    "system_dlkm",
    "oem",
]

def _get_python3(ctx):
    python_interpreter = ctx.toolchains["@bazel_tools//tools/python:toolchain_type"].py3_runtime.interpreter
    if python_interpreter.basename == "python3":
        return python_interpreter

    renamed = ctx.actions.declare_file(ctx.attr.name + "/python3")
    ctx.actions.symlink(
        output = renamed,
        target_file = python_interpreter,
        is_executable = True,
    )
    return renamed

def _partition_impl(ctx):
    if ctx.attr.type != "system":
        fail("currently only system images are supported")

    toolchain = ctx.toolchains[":partition_toolchain_type"].toolchain_info
    python_interpreter = _get_python3(ctx)

    du = ctx.actions.declare_file(ctx.attr.name + "/du")
    ctx.actions.symlink(
        output = du,
        target_file = toolchain.toybox[DefaultInfo].files_to_run.executable,
        is_executable = True,
    )
    find = ctx.actions.declare_file(ctx.attr.name + "/find")
    ctx.actions.symlink(
        output = find,
        target_file = toolchain.toybox[DefaultInfo].files_to_run.executable,
        is_executable = True,
    )

    # build_image requires that the output file be named specifically <type>.img, so
    # put all the outputs under a name-qualified folder.
    output_image = ctx.actions.declare_file(ctx.attr.name + "/" + ctx.attr.type + ".img")

    # TODO(b/297269187) Fill this out with the contents of ctx.attr.deps
    files = {}

    staging_dir_builder_options = {
        "file_mapping": {k: v.path for k, v in files.items()},
    }

    extra_inputs = []
    if ctx.attr.base_staging_dir:
        staging_dir_builder_options["base_staging_dir"] = ctx.file.base_staging_dir.path
        extra_inputs.append(ctx.file.base_staging_dir)
        bbipi = ctx.attr._build_broken_incorrect_partition_images[BuildSettingInfo].value
        if ctx.attr.base_staging_dir_file_list and not bbipi:
            staging_dir_builder_options["base_staging_dir_file_list"] = ctx.file.base_staging_dir_file_list.path
            extra_inputs.append(ctx.file.base_staging_dir_file_list)

    if "{BUILD_NUMBER}" in ctx.attr.image_properties:
        fail("Can't have {BUILD_NUMBER} in image_properties")
    for line in ctx.attr.image_properties.splitlines():
        if line.startswith("avb_"):
            fail("avb properties should be managed by their bespoke attributes: " + line)

    image_info_contents = ctx.attr.image_properties + "\n\n"
    image_info_contents += "ext_mkuserimg=mkuserimg_mke2fs\n"
    if ctx.attr.root_dir:
        extra_inputs.append(ctx.file.root_dir)
        image_info_contents += "root_dir=" + ctx.file.root_dir.path + "\n"
    if ctx.attr.selinux_file_contexts:
        extra_inputs.append(ctx.file.selinux_file_contexts)
        image_info_contents += ctx.attr.type + "_selinux_fc=" + ctx.file.selinux_file_contexts.path + "\n"

    if not ctx.attr.avb_enable:
        if ctx.attr.avb_add_hashtree_footer_args:
            fail("Must specify avb_enable = True to use avb_add_hashtree_footer_args")
        if ctx.attr.avb_key:
            fail("Must specify avb_enable = True to use avb_key")
        if ctx.attr.avb_algorithm:
            fail("Must specify avb_enable = True to use avb_key")
        if ctx.attr.avb_rollback_index >= 0:
            fail("Must specify avb_enable = True to use avb_rollback_index")
        if ctx.attr.avb_rollback_index_location >= 0:
            fail("Must specify avb_enable = True to use avb_rollback_index_location")
    else:
        image_info_contents += "avb_avbtool=avbtool\n"
        image_info_contents += "avb_" + ctx.attr.type + "_hashtree_enable=true" + "\n"
        footer_args = ctx.attr.avb_add_hashtree_footer_args
        if footer_args:
            footer_args += " "
        footer_args += "--prop com.android.build.system.os_version:" + ctx.attr._platform_version_last_stable[BuildSettingInfo].value
        footer_args += " --prop com.android.build.system.fingerprint:" + ctx.attr._build_fingerprint[BuildFingerprintInfo].fingerprint_placeholder_build_number
        footer_args += " --prop com.android.build.system.security_patch:" + ctx.attr._platform_security_patch[BuildSettingInfo].value
        if not ctx.attr.type.startswith("vbmeta_") and ctx.attr.avb_rollback_index >= 0:
            footer_args += " --rollback_index " + str(ctx.attr.avb_rollback_index)
        image_info_contents += "avb_" + ctx.attr.type + "_add_hashtree_footer_args=" + footer_args + "\n"
        if ctx.attr.avb_key:
            image_info_contents += "avb_" + ctx.attr.type + "_key_path=" + ctx.file.avb_key.path + "\n"
            extra_inputs.append(ctx.file.avb_key)
            image_info_contents += "avb_" + ctx.attr.type + "_algorithm=" + ctx.attr.avb_algorithm + "\n"
            if ctx.attr.avb_rollback_index_location >= 0:
                image_info_contents += "avb_" + ctx.attr.type + "_rollback_index_location=" + str(ctx.attr.avb_rollback_index_location) + "\n"

    image_info_without_build_number = ctx.actions.declare_file(ctx.attr.name + "/image_info_without_build_number.txt")
    ctx.actions.write(image_info_without_build_number, image_info_contents)
    image_info = ctx.actions.declare_file(ctx.attr.name + "/image_info.txt")
    ctx.actions.run(
        inputs = [
            ctx.version_file,
            image_info_without_build_number,
        ],
        outputs = [image_info],
        executable = ctx.executable._status_file_reader,
        arguments = [
            "replace",
            ctx.version_file.path,
            image_info_without_build_number.path,
            image_info.path,
            "--var",
            "BUILD_NUMBER",
        ],
    )

    staging_dir_builder_options_file = ctx.actions.declare_file(ctx.attr.name + "/staging_dir_builder_options.json")
    ctx.actions.write(staging_dir_builder_options_file, json.encode(staging_dir_builder_options))

    build_image_files = toolchain.build_image[DefaultInfo].files_to_run

    # These are tools that are run from build_image or another tool that build_image runs.
    # They are all expected to be available in the PATH.
    extra_tools = [
        toolchain.avbtool[DefaultInfo].files_to_run,
        toolchain.e2fsdroid[DefaultInfo].files_to_run,
        toolchain.fec[DefaultInfo].files_to_run,
        toolchain.mke2fs[DefaultInfo].files_to_run,
        toolchain.mkfs_erofs[DefaultInfo].files_to_run,
        toolchain.mkuserimg_mke2fs[DefaultInfo].files_to_run,
        toolchain.simg2img[DefaultInfo].files_to_run,
        toolchain.tune2fs[DefaultInfo].files_to_run,
    ]

    ctx.actions.run(
        inputs = [
            image_info,
            staging_dir_builder_options_file,
            toolchain.openssl,
        ] + files.values() + extra_inputs,
        tools = extra_tools + [
            build_image_files,
            du,
            find,
            python_interpreter,
            toolchain.toybox[DefaultInfo].files_to_run,
        ],
        outputs = [output_image],
        executable = ctx.executable._staging_dir_builder,
        arguments = [
            staging_dir_builder_options_file.path,
            build_image_files.executable.path,
            "STAGING_DIR_PLACEHOLDER",
            image_info.path,
            output_image.path,
            "STAGING_DIR_PLACEHOLDER",
        ],
        mnemonic = "BuildPartition",
        env = {
            # The dict + .keys() is to dedup the path elements, as some tools are in the same folder
            "PATH": ":".join(({t.executable.dirname: True for t in extra_tools} | {
                python_interpreter.dirname: True,
            } | {
                du.dirname: True,
            } | {
                find.dirname: True,
            } | {
                toolchain.openssl.dirname: True,
            }).keys()),
        },
    )

    return DefaultInfo(files = depset([output_image]))

_partition = rule(
    implementation = _partition_impl,
    attrs = {
        "type": attr.string(
            mandatory = True,
            values = _IMAGE_TYPES,
        ),
        "image_properties": attr.string(
            doc = "The image property dictionary in key=value format. TODO: consider replacing this with explicit bazel properties for each property in this file.",
        ),
        "avb_enable": attr.bool(),
        "avb_add_hashtree_footer_args": attr.string(),
        "avb_key": attr.label(allow_single_file = True),
        "avb_algorithm": attr.string(),
        "avb_rollback_index": attr.int(default = -1),
        "avb_rollback_index_location": attr.int(default = -1),
        "base_staging_dir": attr.label(
            allow_single_file = True,
            doc = "A staging dir that the deps will be added to. This is intended to be used to import a make-built staging directory when building the partition with bazel.",
        ),
        "base_staging_dir_file_list": attr.label(
            allow_single_file = True,
            doc = "A file list that will be used to filter the base_staging_dir.",
        ),
        "deps": attr.label_list(),
        "root_dir": attr.label(
            allow_single_file = True,
            doc = "A folder to add as the root_dir property in the property file",
        ),
        "selinux_file_contexts": attr.label(
            allow_single_file = True,
            doc = "The file specifying the selinux rules for all the files in this partition.",
        ),
        "_build_broken_incorrect_partition_images": attr.label(
            default = "//build/bazel/product_config:build_broken_incorrect_partition_images",
        ),
        "_build_fingerprint": attr.label(
            default = "//build/bazel/rules:build_fingerprint",
        ),
        "_platform_version_last_stable": attr.label(
            default = "//build/bazel/product_config:platform_version_last_stable",
        ),
        "_platform_security_patch": attr.label(
            default = "//build/bazel/product_config:platform_security_patch",
        ),
        "_staging_dir_builder": attr.label(
            cfg = "exec",
            doc = "The tool used to build a staging directory, because if bazel were to build it it would be entirely symlinks.",
            executable = True,
            default = "//build/bazel/rules:staging_dir_builder",
        ),
        "_status_file_reader": attr.label(
            cfg = "exec",
            executable = True,
            default = "//build/bazel/rules:status_file_reader",
        ),
    },
    toolchains = [
        ":partition_toolchain_type",
        "@bazel_tools//tools/python:toolchain_type",
    ],
)

def partition(target_compatible_with = [], **kwargs):
    target_compatible_with = select({
        "//build/bazel_common_rules/platforms/os:android": [],
        "//conditions:default": ["@platforms//:incompatible"],
    }) + target_compatible_with
    _partition(
        target_compatible_with = target_compatible_with,
        **kwargs
    )
