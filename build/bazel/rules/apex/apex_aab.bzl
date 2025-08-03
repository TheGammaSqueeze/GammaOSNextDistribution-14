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
load("//build/bazel/rules:toolchain_utils.bzl", "verify_toolchain_exists")
load(":apex_info.bzl", "ApexInfo")
load(":bundle.bzl", "build_bundle_config")

def _arch_transition_impl(settings, _attr):
    """Implementation of arch_transition.

    Six arch products are included for mainline modules: x86, x86_64, x86_64only, arm, arm64, arm64only.
    """
    old_platform = str(settings["//command_line_option:platforms"][0])

    # We can't use platforms alone to differentiate between x86_64 and x86_64
    # with a secondary arch, which is significant for apex packaging that can
    # optionally include the secondary arch's libs. That is currently determined
    # by DeviceSecondaryArch in apex's lib inclusion logic, so we explicitly set
    # DeviceSecondaryArch to "" for the 64bit only cases.

    # TODO(b/249685973) Instead of using these __internal_x86 platforms, use
    # the mainline_modules_<arch> android products
    return {
        # these key names must correspond to mainline_modules_<arch> product name suffixes.
        "arm": {
            "//command_line_option:platforms": old_platform + "__internal_arm",
        },
        "arm64": {
            "//command_line_option:platforms": old_platform + "__internal_arm64",
        },
        "arm64only": {
            "//command_line_option:platforms": old_platform + "__internal_arm64only",
        },
        "x86": {
            "//command_line_option:platforms": old_platform + "__internal_x86",
        },
        "x86_64": {
            "//command_line_option:platforms": old_platform + "__internal_x86_64",
        },
        "x86_64only": {
            "//command_line_option:platforms": old_platform + "__internal_x86_64only",
        },
    }

# Multi-arch transition.
arch_transition = transition(
    implementation = _arch_transition_impl,
    inputs = [
        "//command_line_option:platforms",
    ],
    outputs = [
        "//command_line_option:platforms",
    ],
)

def _merge_base_files(ctx, module_name, base_files):
    """Run merge_zips to merge all files created for each arch by _apex_base_file."""
    merged_base_file = ctx.actions.declare_file(module_name + "/" + module_name + ".zip")

    # Arguments
    args = ctx.actions.args()
    args.add("--ignore-duplicates")
    args.add(merged_base_file)
    args.add_all(base_files)

    ctx.actions.run(
        inputs = base_files,
        outputs = [merged_base_file],
        executable = ctx.executable._merge_zips,
        arguments = [args],
        mnemonic = "ApexMergeBaseFiles",
    )
    return merged_base_file

def _apex_bundle(ctx, module_name, merged_base_file, bundle_config_file):
    """Run bundletool to create the aab file."""

    # Outputs
    bundle_file = ctx.actions.declare_file(module_name + "/" + module_name + ".aab")

    # Arguments
    args = ctx.actions.args()
    args.add("build-bundle")
    args.add_all(["--config", bundle_config_file])
    args.add_all(["--modules", merged_base_file])
    args.add_all(["--output", bundle_file])

    ctx.actions.run(
        inputs = [
            bundle_config_file,
            merged_base_file,
        ],
        outputs = [bundle_file],
        executable = ctx.executable._bundletool,
        arguments = [args],
        mnemonic = "ApexBundleFile",
    )
    return bundle_file

def _sign_bundle(ctx, aapt2, avbtool, module_name, bundle_file, apex_info):
    """ Run dev_sign_bundle to sign the bundle_file."""

    # Python3 interpreter for dev_sign_bundle to run other python scripts.
    python_interpreter = ctx.toolchains["@bazel_tools//tools/python:toolchain_type"].py3_runtime.interpreter
    if python_interpreter.basename != "python3":
        python3 = ctx.actions.declare_file("python3")
        ctx.actions.symlink(
            output = python3,
            target_file = python_interpreter,
            is_executable = True,
        )
        python_interpreter = python3

    # Input directory for dev_sign_bundle.
    input_bundle_file = ctx.actions.declare_file(module_name + "/sign_bundle/input_dir/" + bundle_file.basename)
    ctx.actions.symlink(
        output = input_bundle_file,
        target_file = bundle_file,
    )

    # Output directory  for dev_sign_bundle
    output_dir = ctx.actions.declare_directory(module_name + "/sign_bundle/output_dir")

    # Temporary directory for dev_sign_bundle
    tmp_dir = ctx.actions.declare_directory(module_name + "/sign_bundle/tmp_dir")

    # Jar file of prebuilts/bundletool
    bundletool_jarfile = ctx.attr._bundletool_lib.files.to_list()[0]

    # Keystore file
    keystore_file = ctx.attr.dev_keystore.files.to_list()[0]

    # ANDROID_HOST_OUT environment
    debugfs_static = ctx.actions.declare_file(module_name + "/sign_bundle/android_host_out/bin/debugfs_static")
    ctx.actions.symlink(
        output = debugfs_static,
        target_file = ctx.executable._debugfs,
        is_executable = True,
    )
    fsck_erofs = ctx.actions.declare_file(module_name + "/sign_bundle/android_host_out/bin/fsck.erofs")
    ctx.actions.symlink(
        output = fsck_erofs,
        target_file = ctx.executable._fsck_erofs,
        is_executable = True,
    )
    signapk_jar = ctx.actions.declare_file(module_name + "/sign_bundle/android_host_out/framework/signapk.jar")
    ctx.actions.symlink(
        output = signapk_jar,
        target_file = ctx.attr._signapk_jar.files.to_list()[0],
        is_executable = False,
    )
    libconscrypt_openjdk_jni_so = ctx.actions.declare_file(module_name + "/sign_bundle/android_host_out/lib64/libconscrypt_openjdk_jni.so")
    ctx.actions.symlink(
        output = libconscrypt_openjdk_jni_so,
        target_file = ctx.attr._libconscrypt_openjdk_jni.files.to_list()[1],
        is_executable = False,
    )

    java_runtime = ctx.attr._java_runtime[java_common.JavaRuntimeInfo]

    # Tools
    tools = [
        ctx.executable.dev_sign_bundle,
        ctx.executable._deapexer,
        ctx.executable._sign_apex,
        ctx.executable._openssl,
        ctx.executable._zip2zip,
        aapt2,
        avbtool.files_to_run.executable,
        python_interpreter,
        debugfs_static,
        fsck_erofs,
        bundletool_jarfile,
        signapk_jar,
        libconscrypt_openjdk_jni_so,
        java_runtime.files,
    ]

    # Inputs
    inputs = [
        input_bundle_file,
        keystore_file,
        apex_info.bundle_key_info.private_key,
        apex_info.container_key_info.pem,
        apex_info.container_key_info.pk8,
    ]

    # Outputs
    outputs = [output_dir, tmp_dir]

    # Arguments
    java_bin = paths.join(java_runtime.java_home, "bin")
    args = ctx.actions.args()
    args.add_all(["--input_dir", input_bundle_file.dirname])
    args.add_all(["--output_dir", output_dir.path])
    args.add_all(["--temp_dir", tmp_dir.path])
    args.add_all(["--aapt2_path", aapt2.path])
    args.add_all(["--bundletool_path", bundletool_jarfile.path])
    args.add_all(["--deapexer_path", ctx.executable._deapexer.path])
    args.add_all(["--debugfs_path", ctx.executable._debugfs.path])
    args.add_all(["--java_binary_path", paths.join(java_bin, "java")])
    args.add_all(["--apex_signer_path", ctx.executable._sign_apex])

    ctx.actions.run(
        inputs = inputs,
        outputs = outputs,
        executable = ctx.executable.dev_sign_bundle,
        arguments = [args],
        tools = tools,
        env = {
            # necessary for dev_sign_bundle.
            "BAZEL_ANDROID_HOST_OUT": paths.dirname(debugfs_static.dirname),
            "PATH": ":".join(
                [
                    python_interpreter.dirname,
                    ctx.executable._deapexer.dirname,
                    avbtool.files_to_run.executable.dirname,
                    ctx.executable._openssl.dirname,
                    ctx.executable._zip2zip.dirname,
                    java_bin,
                ],
            ),
        },
        mnemonic = "ApexSignBundleFile",
    )

    apks_file = ctx.actions.declare_file(module_name + "/" + module_name + ".apks")
    cert_info_file = ctx.actions.declare_file(module_name + "/" + module_name + ".cert_info.txt")
    ctx.actions.run_shell(
        inputs = [output_dir],
        outputs = [apks_file, cert_info_file],
        command = " ".join(["cp", output_dir.path + "/" + module_name + "/*", apks_file.dirname]),
    )

    return [apks_file, cert_info_file]

def _apex_aab_impl(ctx):
    """Implementation of apex_aab rule.

    This drives the process of creating aab file from apex files created for each arch."""
    verify_toolchain_exists(ctx, "//build/bazel/rules/apex:apex_toolchain_type")
    apex_toolchain = ctx.toolchains["//build/bazel/rules/apex:apex_toolchain_type"].toolchain_info

    prefixed_apex_files = []
    apex_base_files = []
    bundle_config_file = None
    module_name = ctx.attr.mainline_module[0].label.name
    for arch in ctx.split_attr.mainline_module:
        apex_info = ctx.split_attr.mainline_module[arch][ApexInfo]
        apex_base_files.append(apex_info.base_file)

        arch_subdir = "mainline_modules_%s" % arch

        # A mapping of files to a prefix directory they should be copied to.
        # These files will be accessible with the apex_files output_group.
        mapping = {
            apex_info.base_file: arch_subdir,
            apex_info.signed_output: arch_subdir,
            apex_info.symbols_used_by_apex: arch_subdir + "/ndk_apis_usedby_apex",
            apex_info.backing_libs: arch_subdir + "/ndk_apis_backedby_apex",
            apex_info.java_symbols_used_by_apex: arch_subdir + "/java_apis_used_by_apex",
            # TODO(b/262267680): create licensetexts
            # TODO(b/262267551): create shareprojects
        }

        # Forward the individual files for all variants in an additional output group,
        # so dependents can easily access the multi-arch base APEX files by building
        # this target with --output_groups=apex_files.
        #
        # Copy them into an arch-specific directory, since they have the same basename.
        for _file, _dir in mapping.items():
            _out = ctx.actions.declare_file(_dir + "/" + _file.basename)
            ctx.actions.run_shell(
                inputs = [_file],
                outputs = [_out],
                command = " ".join(["cp", _file.path, _out.path]),
            )
            prefixed_apex_files.append(_out)

    # Create .aab file
    bundle_config_file = build_bundle_config(ctx.actions, ctx.label.name)
    merged_base_file = _merge_base_files(ctx, module_name, apex_base_files)
    bundle_file = _apex_bundle(ctx, module_name, merged_base_file, bundle_config_file)

    # Create .apks file
    apex_info = ctx.attr.mainline_module[0][ApexInfo]
    package_name = apex_info.package_name

    if ctx.attr.dev_sign_bundle and ctx.attr.dev_keystore and (package_name.startswith("com.google.android") or package_name.startswith("com.google.mainline")):
        signed_files = _sign_bundle(ctx, apex_toolchain.aapt2, apex_toolchain.avbtool, module_name, bundle_file, apex_info)
        return [
            DefaultInfo(files = depset([bundle_file] + signed_files)),
            OutputGroupInfo(apex_files = depset(prefixed_apex_files), signed_files = signed_files),
        ]

    return [
        DefaultInfo(files = depset([bundle_file])),
        OutputGroupInfo(apex_files = depset(prefixed_apex_files)),
    ]

# apex_aab rule creates multi-arch outputs of a Mainline module, such as the
# Android Apk Bundle (.aab) file of the APEX specified in mainline_module.
# There is no equivalent Soong module, and it is currently done in shell script
# by invoking Soong multiple times.
_apex_aab = rule(
    implementation = _apex_aab_impl,
    toolchains = [
        # The apex toolchain is not mandatory so that we don't get toolchain resolution errors
        # even when the aab is not compatible with the current target (via target_compatible_with).
        config_common.toolchain_type("//build/bazel/rules/apex:apex_toolchain_type", mandatory = False),
        "@bazel_tools//tools/python:toolchain_type",
    ],
    attrs = {
        "dev_keystore": attr.label(
            cfg = "exec",
            executable = False,
        ),
        "dev_sign_bundle": attr.label(
            cfg = "exec",
            executable = True,
        ),
        "mainline_module": attr.label(
            mandatory = True,
            cfg = arch_transition,
            providers = [ApexInfo],
            doc = "The label of a mainline module target",
        ),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
            doc = "Allow transition.",
        ),
        "_bundletool": attr.label(
            cfg = "exec",
            executable = True,
            default = "//prebuilts/bundletool",
        ),
        "_bundletool_lib": attr.label(
            cfg = "exec",
            executable = False,
            default = "//prebuilts/bundletool:bundletool-lib",
        ),
        "_deapexer": attr.label(
            cfg = "exec",
            executable = True,
            default = "//system/apex/tools:deapexer",
        ),
        "_debugfs": attr.label(
            cfg = "exec",
            executable = True,
            default = "//external/e2fsprogs/debugfs:debugfs_static",
        ),
        "_fsck_erofs": attr.label(
            cfg = "exec",
            executable = True,
            default = "//external/erofs-utils:fsck.erofs",
        ),
        "_java_runtime": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
            cfg = "exec",
            providers = [java_common.JavaRuntimeInfo],
        ),
        "_libconscrypt_openjdk_jni": attr.label(
            cfg = "exec",
            executable = False,
            default = "//external/conscrypt:libconscrypt_openjdk_jni",
        ),
        "_merge_zips": attr.label(
            allow_single_file = True,
            cfg = "exec",
            executable = True,
            default = "//build/soong/cmd/merge_zips",
        ),
        "_openssl": attr.label(
            allow_single_file = True,
            cfg = "exec",
            executable = True,
            default = "//prebuilts/build-tools:linux-x86/bin/openssl",
        ),
        "_sign_apex": attr.label(
            cfg = "exec",
            executable = True,
            default = "//build/make/tools/releasetools:sign_apex",
        ),
        "_signapk_jar": attr.label(
            cfg = "exec",
            executable = False,
            default = "//build/bazel/rules/apex:signapk_deploy_jar",
        ),
        "_zip2zip": attr.label(
            allow_single_file = True,
            cfg = "exec",
            executable = True,
            default = "//build/soong/cmd/zip2zip:zip2zip",
        ),
        "_zipper": attr.label(
            cfg = "exec",
            executable = True,
            default = "@bazel_tools//tools/zip:zipper",
        ),
    },
)

def apex_aab(name, mainline_module, dev_sign_bundle = None, dev_keystore = None, target_compatible_with = [], **kwargs):
    target_compatible_with = select({
        "//build/bazel_common_rules/platforms/os:android": [],
        "//conditions:default": ["@platforms//:incompatible"],
    }) + target_compatible_with

    _apex_aab(
        name = name,
        mainline_module = mainline_module,
        dev_sign_bundle = dev_sign_bundle,
        dev_keystore = dev_keystore,
        target_compatible_with = target_compatible_with,
        **kwargs
    )
