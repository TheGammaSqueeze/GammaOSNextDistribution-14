# Copyright (C) 2021 The Android Open Source Project
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
load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")

ApexKeyInfo = provider(
    "Info needed to sign APEX bundles",
    fields = {
        "private_key": "File containing the private key",
        "public_key": "File containing the public_key",
    },
)

def _apex_key_rule_impl(ctx):
    public_key = ctx.file.public_key
    private_key = ctx.file.private_key

    # If the DefaultAppCertificate directory is specified, then look for this
    # key in that directory instead, with the exact same basenames for both the
    # avbpubkey and pem files.
    product_var_cert = ctx.attr._default_app_certificate[BuildSettingInfo].value
    cert_files_to_search = ctx.attr._default_app_certificate_filegroup[DefaultInfo]
    if product_var_cert and cert_files_to_search:
        for f in cert_files_to_search.files.to_list():
            if f.basename == ctx.file.public_key.basename:
                public_key = f
            elif f.basename == ctx.file.private_key.basename:
                private_key = f

    public_keyname = paths.split_extension(public_key.basename)[0]
    private_keyname = paths.split_extension(private_key.basename)[0]
    if public_keyname != private_keyname:
        fail("public_key %s (keyname:%s) and private_key %s (keyname:%s) do not have same keyname" % (
            ctx.attr.public_key.label,
            public_keyname,
            ctx.attr.private_key.label,
            private_keyname,
        ))

    return [
        ApexKeyInfo(
            public_key = public_key,
            private_key = private_key,
        ),
    ]

_apex_key = rule(
    implementation = _apex_key_rule_impl,
    attrs = {
        "private_key": attr.label(mandatory = True, allow_single_file = True),
        "public_key": attr.label(mandatory = True, allow_single_file = True),
        "_default_app_certificate": attr.label(
            default = "//build/bazel/product_config:default_app_certificate",
        ),
        "_default_app_certificate_filegroup": attr.label(
            default = "//build/bazel/product_config:default_app_certificate_filegroup",
        ),
    },
)

def _get_key_label(label, name):
    if label and name:
        fail("Cannot use both {public,private}_key_name and {public,private}_key attributes together. " +
             "Use only one of them.")

    if label:
        return label

    # Ensure that the name references the calling package's local BUILD target
    return ":" + name

def apex_key(
        name,
        public_key = None,
        private_key = None,
        public_key_name = None,
        private_key_name = None,
        **kwargs):
    # The keys are labels that point to either a file, or a target that provides
    # a single file (e.g. a filegroup or rule that provides the key itself only).
    _apex_key(
        name = name,
        public_key = _get_key_label(public_key, public_key_name),
        private_key = _get_key_label(private_key, private_key_name),
        **kwargs
    )
