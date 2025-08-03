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

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")

visibility(["//build/bazel/..."])

BuildFingerprintInfo = provider(
    fields = {
        "fingerprint_blank_build_number": "The fingerprint, but without the build number",
        "fingerprint_placeholder_build_number": "The fingerprint, but with the build number replaced with {BUILD_NUMBER}",
    },
)

def _build_fingerprint_impl(ctx):
    build_version_tags = ctx.attr._build_version_tags[BuildSettingInfo].value
    default_app_certificate = ctx.attr._default_app_certificate[BuildSettingInfo].value
    if not default_app_certificate or default_app_certificate == "build/make/target/product/security/testkey":
        build_version_tags = build_version_tags + ["test-keys"]
    else:
        build_version_tags = build_version_tags + ["dev-keys"]
    build_version_tags = sorted(build_version_tags)

    build_fingerprint_blank = "%s/%s/%s:%s/%s/%s:%s/%s" % (
        ctx.attr._product_brand[BuildSettingInfo].value,
        ctx.attr._device_product[BuildSettingInfo].value,
        ctx.attr._device_name[BuildSettingInfo].value,
        ctx.attr._platform_version_name[BuildSettingInfo].value,
        ctx.attr._build_id[BuildSettingInfo].value,
        "",
        ctx.attr._target_build_variant[BuildSettingInfo].value,
        ",".join(build_version_tags),
    )
    build_fingerprint_placeholder = "%s/%s/%s:%s/%s/%s:%s/%s" % (
        ctx.attr._product_brand[BuildSettingInfo].value,
        ctx.attr._device_product[BuildSettingInfo].value,
        ctx.attr._device_name[BuildSettingInfo].value,
        ctx.attr._platform_version_name[BuildSettingInfo].value,
        ctx.attr._build_id[BuildSettingInfo].value,
        "{BUILD_NUMBER}",
        ctx.attr._target_build_variant[BuildSettingInfo].value,
        ",".join(build_version_tags),
    )
    return [
        BuildFingerprintInfo(
            fingerprint_blank_build_number = build_fingerprint_blank,
            fingerprint_placeholder_build_number = build_fingerprint_placeholder,
        ),
    ]

build_fingerprint = rule(
    implementation = _build_fingerprint_impl,
    attrs = {
        "_build_id": attr.label(
            default = "//build/bazel/product_config:build_id",
        ),
        "_build_version_tags": attr.label(
            default = "//build/bazel/product_config:build_version_tags",
        ),
        "_default_app_certificate": attr.label(
            default = "//build/bazel/product_config:default_app_certificate",
        ),
        "_device_name": attr.label(
            default = "//build/bazel/product_config:device_name",
        ),
        "_device_product": attr.label(
            default = "//build/bazel/product_config:device_product",
        ),
        "_product_brand": attr.label(
            default = "//build/bazel/product_config:product_brand",
        ),
        "_platform_version_name": attr.label(
            default = "//build/bazel/product_config:platform_version_name",
        ),
        "_target_build_variant": attr.label(
            default = "//build/bazel/product_config:target_build_variant",
        ),
    },
)
