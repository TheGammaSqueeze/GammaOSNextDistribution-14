# Copyright (C) 2023 The Android Open Source Project
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

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")

ImageProvider = provider(
    "Provide device image and host package files",
    fields = {
        "image": "device image file to launch virtual device",
        "cvd_host_package": "cvd host package to launch virtual device",
    },
)

# TODO(b/273846592): Move to a common file, same for other files.
_BAZEL_WORK_DIR = "${TEST_SRCDIR}/${TEST_WORKSPACE}/"

_IMAGE_ARTIFACT_PATH = "cf_x86_64_phone-img-{BUILD_ID}.zip"
_HOST_PACKAGE_ARTIFACT_PATH = "cvd-host_package.tar.gz"

def _download_cvd_artifact_impl(ctx):
    build_id = ctx.attr.build_id[BuildSettingInfo].value
    branch = ctx.attr.branch[BuildSettingInfo].value
    image_artifact_path = _IMAGE_ARTIFACT_PATH.replace("{BUILD_ID}", build_id)
    target = ctx.attr.target[BuildSettingInfo].value
    if not build_id:
        fail("build_id must be specified to download build image.")
    if not branch:
        fail("branch must be specified to download build image.")
    if not target:
        fail("Target must be specified to download build image.")

    # Add "aosp_" prefix to target and artifact name if the branch is AOSP.
    if "aosp" in branch:
        image_artifact_path = "aosp_" + image_artifact_path

    image_out_file = _download_helper(
        ctx,
        image_artifact_path,
        build_id,
        branch,
        target,
    )
    cvd_host_out_file = _download_helper(
        ctx,
        _HOST_PACKAGE_ARTIFACT_PATH,
        build_id,
        branch,
        target,
    )
    return ImageProvider(
        image = image_out_file,
        cvd_host_package = cvd_host_out_file,
    )

def _download_helper(ctx, artifact_path, build_id, branch, target):
    script = ctx.actions.declare_file("download_cvd_build_%s_%s.sh" %
                                      (ctx.label.name, artifact_path))

    out_file = ctx.actions.declare_file(artifact_path)
    ctx.actions.expand_template(
        template = ctx.file._create_script_template,
        output = script,
        is_executable = True,
        substitutions = {
            "{build_id}": build_id,
            "{artifact_path}": artifact_path,
            "{output_dir}": out_file.dirname,
            "{branch}": branch,
            "{target}": target,
        },
    )
    ctx.actions.run_shell(
        inputs = [script],
        outputs = [out_file],
        mnemonic = "DownloadCvd",
        command = "source %s" % (script.path),
        progress_message = "Downloading Android Build artifact %s for Build ID %s." % (artifact_path, build_id),
    )
    return out_file

download_cvd_artifact = rule(
    attrs = {
        "_create_script_template": attr.label(
            default = ":download_cvd_build.sh.template",
            allow_single_file = True,
        ),
        "build_id": attr.label(
            mandatory = True,
            doc = "sets the build id of the Android image",
        ),
        "target": attr.label(
            mandatory = True,
            doc = "sets the build target of the Android image. Example: " +
                  "aosp_cf_x86_64_phone-trunk_staging-userdebug.",
        ),
        "branch": attr.label(
            mandatory = True,
            doc = "sets the branch of the Android image",
        ),
    },
    implementation = _download_cvd_artifact_impl,
    doc = "A rule used to download cuttlefish image files.",
)
