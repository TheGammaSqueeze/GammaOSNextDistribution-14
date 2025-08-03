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
load("android_app_certificate.bzl", "AndroidAppCertificateInfo")

AndroidAppKeystoreInfo = provider(
    "Info needed for Android app keystores",
    fields = {
        "keystore": "JKS .keystore file housing certificate info",
    },
)

def _pk8_to_private_pem(ctx, openssl, pk8_file, private_pem_file):
    """Converts a .pk8 private key file in DER format to a .pem private key file in PEM format."""
    args = ctx.actions.args()
    args.add("pkcs8")
    args.add_all(["-in", pk8_file])
    args.add_all(["-inform", "DER"])
    args.add_all(["-outform", "PEM"])
    args.add_all(["-out", private_pem_file])
    args.add("-nocrypt")  # don't bother encrypting this private key since it is just an intermediate file

    ctx.actions.run(
        inputs = [pk8_file],
        executable = openssl,
        outputs = [private_pem_file],
        arguments = [args],
        mnemonic = "CreatePrivPEM",
    )

def _pem_to_pk12(ctx, openssl, certificate_pem, private_key_pem, pk12_file):
    """Converts an X.509 certificate and private key pair of PEM files to a single PKCS12 keystore file."""
    args = ctx.actions.args()
    args.add("pkcs12")
    args.add("-export")
    args.add_all(["-in", certificate_pem])
    args.add_all(["-inkey", private_key_pem])
    args.add_all(["-out", pk12_file])
    args.add_all(["-name", "android"])

    # openssl requires a password and will request a
    # password from STDIN if we don't supply one here
    args.add_all(["-passout", "pass:android"])

    ctx.actions.run(
        inputs = [
            certificate_pem,
            private_key_pem,
        ],
        executable = openssl,
        outputs = [pk12_file],
        arguments = [args],
        mnemonic = "CreatePK12",
    )

def _pk12_to_keystore(ctx, pk12_file, keystore_file):
    """Converts a PKCS12 keystore file to a JKS keystore file."""
    java_runtime = ctx.attr._java_runtime[java_common.JavaRuntimeInfo]
    keytool = paths.join(java_runtime.java_home, "bin", "keytool")
    args = ctx.actions.args()
    args.add("-importkeystore")
    args.add_all(["-destkeystore", keystore_file])
    args.add_all(["-srckeystore", pk12_file])
    args.add_all(["-srcstoretype", "PKCS12"])
    args.add_all(["-srcstorepass", "android"])

    # apksigner expects keystores provided by the debug_signing_keys attribute
    # to be secured with the password "android"
    args.add_all(["-deststorepass", "android"])

    ctx.actions.run(
        inputs = [pk12_file],
        executable = keytool,
        tools = [java_runtime.files],
        outputs = [keystore_file],
        arguments = [args],
        mnemonic = "CreateKeystore",
    )

def _android_app_keystore_rule_impl(ctx):
    openssl = ctx.executable._openssl

    private_pem = ctx.actions.declare_file(ctx.attr.name + ".priv.pem")
    pk12 = ctx.actions.declare_file(ctx.attr.name + ".pk12")
    keystore = ctx.actions.declare_file(ctx.attr.name + ".keystore")

    pk8_file = ctx.attr.certificate[AndroidAppCertificateInfo].pk8
    pem_file = ctx.attr.certificate[AndroidAppCertificateInfo].pem
    _pk8_to_private_pem(ctx, openssl, pk8_file, private_pem)
    _pem_to_pk12(ctx, openssl, pem_file, private_pem, pk12)
    _pk12_to_keystore(ctx, pk12, keystore)

    return [
        AndroidAppKeystoreInfo(
            keystore = keystore,
        ),
        DefaultInfo(files = depset(direct = [keystore])),
    ]

"""Converts an android_app_certificate (i.e. pem/pk8 pair) into a JKS keystore"""
android_app_keystore = rule(
    implementation = _android_app_keystore_rule_impl,
    attrs = {
        "certificate": attr.label(mandatory = True, providers = [AndroidAppCertificateInfo]),
        "_openssl": attr.label(
            default = Label("//prebuilts/build-tools:linux-x86/bin/openssl"),
            allow_single_file = True,
            executable = True,
            cfg = "exec",
            doc = "An OpenSSL compatible tool.",
        ),
        "_java_runtime": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
            cfg = "exec",
            providers = [java_common.JavaRuntimeInfo],
        ),
    },
    provides = [AndroidAppKeystoreInfo],
)
