# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Rule used to generate a Cuttlefish device environment.

This rule creates a device environment rule to run tests on a Cuttlefish Android
Virtual Device. Test targets that run in this environment will start a new
dedicated virtual device for each execution.

Device properties such as the image used can be configured via an attribute.
"""

load("//build/bazel_common_rules/rules/remote_device/device:device_environment.bzl", "DeviceEnvironment")
load("//build/bazel_common_rules/rules/remote_device/downloader:download_cvd_artifact.bzl", "ImageProvider")

_BAZEL_WORK_DIR = "${TEST_SRCDIR}/${TEST_WORKSPACE}/"

def _cuttlefish_device_impl(ctx):
    path_additions = [_BAZEL_WORK_DIR]
    image_file = ctx.attr.cvd_build_artifacts[ImageProvider].image
    cvd_host_file = ctx.attr.cvd_build_artifacts[ImageProvider].cvd_host_package
    ctx.actions.expand_template(
        template = ctx.file._create_script_template,
        output = ctx.outputs.out,
        is_executable = True,
        substitutions = {
            "{img_path}": _BAZEL_WORK_DIR + image_file.short_path,
            "{cvd_host_package_path}": _BAZEL_WORK_DIR + cvd_host_file.short_path,
            "{path_additions}": ":".join(path_additions),
        },
    )

    return DeviceEnvironment(
        runner = depset([ctx.outputs.out]),
        data = ctx.runfiles(files = [
            cvd_host_file,
            ctx.outputs.out,
            image_file,
        ]),
    )

cuttlefish_device = rule(
    attrs = {
        "cvd_build_artifacts": attr.label(
            providers = [ImageProvider],
            mandatory = True,
        ),
        "out": attr.output(mandatory = True),
        "_create_script_template": attr.label(
            default = ":create_cuttlefish.sh.template",
            allow_single_file = True,
        ),
    },
    implementation = _cuttlefish_device_impl,
)
