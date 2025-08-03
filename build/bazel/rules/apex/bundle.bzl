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

# Arch to ABI map
_arch_abi_map = {
    "arm": "armeabi-v7a",
    "arm64": "arm64-v8a",
    "x86": "x86",
    "x86_64": "x86_64",
}

def _proto_convert(actions, name, aapt2, apex_file):
    """Run 'aapt2 convert' to convert resource files to protobuf format."""

    root, ext = paths.split_extension(apex_file.basename)
    output_file = actions.declare_file(paths.join(
        name,
        root + ".pb" + ext,
    ))

    # Arguments
    args = actions.args()
    args.add("convert")
    args.add("--output-format", "proto")
    args.add(apex_file)
    args.add("-o", output_file)

    actions.run(
        inputs = [apex_file],
        outputs = [output_file],
        executable = aapt2,
        arguments = [args],
        mnemonic = "ApexProtoConvert",
    )
    return output_file

def _base_file(actions, name, zip2zip, arch, secondary_arch, apex_proto_file):
    """Transforms the apex file to the expected directory structure with all files that will be included in the base module of aab file."""

    output_file = actions.declare_file(name + "-base.zip")

    # Arguments
    args = actions.args()
    args.add("-i", apex_proto_file)
    args.add("-o", output_file)
    abi = _arch_abi_map[arch]
    if secondary_arch:
        abi += "." + _arch_abi_map[secondary_arch]
    args.add_all([
        "apex_payload.img:apex/%s.img" % abi,
        "apex_build_info.pb:apex/%s.build_info.pb" % abi,
        "apex_manifest.json:root/apex_manifest.json",
        "apex_manifest.pb:root/apex_manifest.pb",
        "AndroidManifest.xml:manifest/AndroidManifest.xml",
        "assets/NOTICE.html.gz:assets/NOTICE.html.gz",
    ])

    actions.run(
        inputs = [apex_proto_file],
        outputs = [output_file],
        executable = zip2zip,
        arguments = [args],
        mnemonic = "ApexBaseFile",
    )
    return output_file

def build_bundle_config(actions, name):
    """Create bundle_config.json as configuration for running bundletool.

    Args:
      actions: ctx.actions from a rule, used to declare outputs and actions.
      name: name of target creating action
    Returns:
      The bundle_config.json file
    """
    file_content = {
        # TODO(b/257459237): Config should collect manifest names and paths of android apps if their manifest name is overridden.
        "apex_config": {},
        "compression": {
            "uncompressed_glob": [
                "apex_payload.img",
                "apex_manifest.*",
            ],
        },
    }
    bundle_config_file = actions.declare_file(paths.join(name, "bundle_config.json"))

    actions.write(bundle_config_file, json.encode(file_content))

    return bundle_config_file

def _merge_apex_zip_with_config(actions, name, soong_zip, merge_zips, apex_zip, apex_file):
    # TODO(): Only used as compatibility with mixed builds
    bundle_config = build_bundle_config(actions, name)
    apex_config_zip = actions.declare_file(name + ".config")

    args = actions.args()
    args.add("-o", apex_config_zip)
    args.add("-C", bundle_config.dirname)
    args.add("-f", bundle_config)
    actions.run(
        inputs = [bundle_config],
        outputs = [apex_config_zip],
        executable = soong_zip,
        arguments = [args],
        mnemonic = "ApexBaseConfigZip",
    )

    merged_zip = actions.declare_file(apex_file.basename + "-base.zip")
    merge_args = actions.args()
    merge_args.add(merged_zip)
    merge_args.add(apex_zip)
    merge_args.add(apex_config_zip)
    actions.run(
        inputs = [apex_config_zip, apex_zip],
        outputs = [merged_zip],
        executable = merge_zips,
        arguments = [merge_args],
        mnemonic = "ApexMergeBaseFileAndConfig",
    )
    return merged_zip

def apex_zip_files(actions, name, tools, apex_file, arch, secondary_arch):
    """Create apex zip files used to create an APEX bundle.

    Args:
        actions: Actions, ctx.actions from a rule, used to declare outputs and actions.
        name: string, name of the target creating the action
        tools: struct containing fields with executables: aapt2, zip2zip, soong_zip, merge_zips
        apex_file: File, APEX file
        arch: string, the arch of the target configuration of the target requesting the action
    Returns:
        A struct with these fields:
        apex_only: the regular "base" apex zip
        apex_with_config: a zipfile that's identical to apex_only, but with the addition of bundle_config.json
    """
    apex_proto = _proto_convert(actions, name, tools.aapt2, apex_file)
    apex_zip = _base_file(actions, name, tools.zip2zip, arch, secondary_arch, apex_proto)
    merged_zip = _merge_apex_zip_with_config(actions, name, tools.soong_zip, tools.merge_zips, apex_zip, apex_file)

    return struct(
        apex_only = apex_zip,
        apex_with_config = merged_zip,
    )
