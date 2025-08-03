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

load("//build/bazel/rules/android:android_app_certificate.bzl", "android_app_certificate")
load(":apex.bzl", "apex")
load(":apex_key.bzl", "apex_key")

# Set up test-local dependencies required for every apex.
def _setup_apex_required_deps(
        file_contexts,
        key,
        manifest,
        certificate):
    # Use the same shared common deps for all test apexes.
    if file_contexts and not native.existing_rule(file_contexts):
        native.genrule(
            name = file_contexts,
            outs = [file_contexts + ".out"],
            cmd = "echo unused && exit 1",
            tags = ["manual"],
        )

    if manifest and not native.existing_rule(manifest):
        native.genrule(
            name = manifest,
            outs = [manifest + ".json"],
            cmd = "echo unused && exit 1",
            tags = ["manual"],
        )

    # Required for ApexKeyInfo provider
    if key and not native.existing_rule(key):
        apex_key(
            name = key,
            private_key = key + ".pem",
            public_key = key + ".avbpubkey",
            tags = ["manual"],
        )

    # Required for AndroidAppCertificate provider
    if certificate and not native.existing_rule(certificate):
        android_app_certificate(
            name = certificate,
            certificate = certificate + ".cert",
            tags = ["manual"],
        )

def test_apex(
        name,
        file_contexts = "test_file_contexts",
        key = "test_key",
        manifest = "test_manifest",
        certificate = "test_certificate",
        **kwargs):
    _setup_apex_required_deps(
        file_contexts = file_contexts,
        key = key,
        manifest = manifest,
        certificate = certificate,
    )

    apex(
        name = name,
        file_contexts = file_contexts,
        key = key,
        manifest = manifest,
        certificate = certificate,
        tags = ["manual"],
        **kwargs
    )
