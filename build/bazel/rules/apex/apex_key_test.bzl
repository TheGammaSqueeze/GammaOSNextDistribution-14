# Copyright (C) 2022 The Android Open Source Project
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

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":apex_key.bzl", "ApexKeyInfo", "apex_key")

def _apex_key_test(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    asserts.equals(
        env,
        ctx.attr.expected_private_key_short_path,
        target_under_test[ApexKeyInfo].private_key.short_path,
    )
    asserts.equals(
        env,
        ctx.attr.expected_public_key_short_path,
        target_under_test[ApexKeyInfo].public_key.short_path,
    )
    return analysistest.end(env)

apex_key_test = analysistest.make(
    _apex_key_test,
    attrs = {
        "expected_private_key_short_path": attr.string(mandatory = True),
        "expected_public_key_short_path": attr.string(mandatory = True),
    },
)

apex_key_with_default_app_cert_test = analysistest.make(
    _apex_key_test,
    attrs = {
        "expected_private_key_short_path": attr.string(mandatory = True),
        "expected_public_key_short_path": attr.string(mandatory = True),
    },
    config_settings = {
        # This product sets DefaultAppCertificate to build/bazel/rules/apex/testdata/devkey,
        # so we expect the apex_key to look for key_name in build/bazel/rules/apex/testdata.
        "//command_line_option:platforms": "@//build/bazel/tests/products:aosp_arm64_for_testing_with_overrides_and_app_cert",
    },
)

def _test_apex_key_file_targets_with_key_name_attribute():
    name = "apex_key_file_targets_with_key_name_attribute"
    test_name = name + "_test"
    private_key = name + ".pem"
    public_key = name + ".avbpubkey"

    apex_key(
        name = name,
        private_key_name = private_key,
        public_key_name = public_key,
    )

    apex_key_test(
        name = test_name,
        target_under_test = name,
        expected_private_key_short_path = native.package_name() + "/" + private_key,
        expected_public_key_short_path = native.package_name() + "/" + public_key,
    )

    return test_name

def _test_apex_key_file_targets_with_key_name_attribute_with_default_app_cert():
    name = "apex_key_file_targets_with_key_attribute_with_default_app_cert"
    test_name = name + "_test"
    private_key = "devkey.pem"
    public_key = "devkey.avbpubkey"

    apex_key(
        name = name,
        private_key_name = private_key,
        public_key_name = public_key,
    )

    apex_key_with_default_app_cert_test(
        name = test_name,
        target_under_test = name,
        expected_private_key_short_path = "build/bazel/rules/apex/testdata/" + private_key,
        expected_public_key_short_path = "build/bazel/rules/apex/testdata/" + public_key,
    )

    return test_name

def _test_apex_key_file_targets_with_key_attribute():
    name = "apex_key_file_targets_with_key_attribute"
    test_name = name + "_test"
    private_key = name + ".pem"
    public_key = name + ".avbpubkey"

    apex_key(
        name = name,
        # Referring to file targets with plain strings work as well, as bazel
        # will parse these labels as file targets in the same package.
        private_key = private_key,
        public_key = public_key,
    )

    apex_key_test(
        name = test_name,
        target_under_test = name,
        expected_private_key_short_path = native.package_name() + "/" + private_key,
        expected_public_key_short_path = native.package_name() + "/" + public_key,
    )

    return test_name

def _test_apex_key_generated_keys():
    name = "apex_key_generated_keys"
    test_name = name + "_test"
    private_key = name + ".pem"
    public_key = name + ".avbpubkey"

    native.genrule(
        name = private_key,
        outs = ["priv/" + name + ".generated"],
        cmd = "noop",
        tags = ["manual"],
    )

    native.genrule(
        name = public_key,
        outs = ["pub/" + name + ".generated"],
        cmd = "noop",
        tags = ["manual"],
    )

    apex_key(
        name = name,
        private_key = private_key,
        public_key = public_key,
    )

    apex_key_test(
        name = test_name,
        target_under_test = name,
        expected_private_key_short_path = native.package_name() + "/priv/" + name + ".generated",
        expected_public_key_short_path = native.package_name() + "/pub/" + name + ".generated",
    )

    return test_name

def apex_key_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_apex_key_file_targets_with_key_name_attribute(),
            _test_apex_key_file_targets_with_key_name_attribute_with_default_app_cert(),
            _test_apex_key_file_targets_with_key_attribute(),
            _test_apex_key_generated_keys(),
        ],
    )
