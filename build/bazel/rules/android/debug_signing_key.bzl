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

load(":android_app_certificate.bzl", "android_app_certificate_with_default_cert")
load(":android_app_keystore.bzl", "android_app_keystore")

def debug_signing_key(name, certificate, certificate_name):
    if not certificate and not certificate_name:
        return []
    if certificate and certificate_name:
        fail("Cannot use both certificate_name and certificate attributes together. Use only one of them.")
    if certificate_name:
        app_cert_name = name + "_app_certificate"
        android_app_certificate_with_default_cert(
            name = app_cert_name,
            cert_name = certificate_name,
        )
        certificate = ":" + app_cert_name

    app_keystore_name = name + "_keystore"
    android_app_keystore(
        name = app_keystore_name,
        certificate = certificate,
    )

    return [app_keystore_name]
