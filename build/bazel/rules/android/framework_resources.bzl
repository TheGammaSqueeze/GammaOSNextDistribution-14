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

# framework-res is a highly customized android_app module in Soong.
# Direct translation to an android_binary rule (as is done for other
# android_app modules) is made difficult due to Soong code name checking
# for this specific module, e.g. to:
# - Skip java compilation and dexing of R.java generated from resources
# - Provide custom aapt linking flags that are exclusive to this module,
#   some of which depend on product configuration.
# - Provide custom output groups exclusively used by reverse dependencies
#   of this module.
# A separate rule, implemented below is preferred over implementing a similar
# customization within android_binary.

load(":debug_signing_key.bzl", "debug_signing_key")
load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@rules_android//rules/android_binary_internal:rule.bzl", "sanitize_attrs")
load("@rules_android//rules/android_binary_internal:attrs.bzl", _BASE_ATTRS = "ATTRS")
load("@rules_android//rules:busybox.bzl", _busybox = "busybox")
load("@rules_android//rules:common.bzl", "common")
load("@rules_android//rules:utils.bzl", "get_android_toolchain")
load("//build/bazel/rules/android:manifest_fixer.bzl", "manifest_fixer")
load("//build/bazel/rules/common:api.bzl", "api")
load("//build/bazel/rules/common:config.bzl", "has_unbundled_build_apps")

def _fix_manifest(ctx):
    fixed_manifest = ctx.actions.declare_file(
        paths.join(ctx.label.name, "AndroidManifest.xml"),
    )
    target_sdk_version = manifest_fixer.target_sdk_version_for_manifest_fixer(
        target_sdk_version = "current",
        platform_sdk_final = ctx.attr._platform_sdk_final[BuildSettingInfo].value,
        has_unbundled_build_apps = has_unbundled_build_apps(ctx.attr._unbundled_build_apps),
    )

    manifest_fixer.fix(
        ctx,
        manifest_fixer = ctx.executable._manifest_fixer,
        in_manifest = ctx.file.manifest,
        out_manifest = fixed_manifest,
        min_sdk_version = api.effective_version_string("current"),
        target_sdk_version = target_sdk_version,
    )
    return fixed_manifest

def _compile_resources(ctx):
    host_javabase = common.get_host_javabase(ctx)
    aapt = get_android_toolchain(ctx).aapt2.files_to_run
    busybox = get_android_toolchain(ctx).android_resources_busybox.files_to_run

    # Unzip resource zips so they can be compiled by aapt and packaged with the
    # proper directory structure at linking.
    unzip = get_android_toolchain(ctx).unzip_tool

    # TODO: b/301457407 - support declare_directory in mixed builds or don't use it here
    resource_unzip_dir = ctx.actions.declare_directory(ctx.label.name + "_resource_zips")
    zip_args = ctx.actions.args()
    zip_args.add("-qq")
    zip_args.add_all(ctx.files.resource_zips)
    zip_args.add("-d", resource_unzip_dir.path)
    ctx.actions.run(
        inputs = ctx.files.resource_zips,
        outputs = [resource_unzip_dir],
        executable = unzip.files_to_run,
        arguments = [zip_args],
        toolchain = None,
        mnemonic = "UnzipResourceZips",
    )
    compiled_resources = ctx.actions.declare_file(
        paths.join(ctx.label.name + "_symbols", "symbols.zip"),
    )
    _busybox.compile(
        ctx,
        out_file = compiled_resources,
        resource_files = ctx.files.resource_files + [resource_unzip_dir],
        aapt = aapt,
        busybox = busybox,
        host_javabase = host_javabase,
    )

    # The resource processor busybox runs the same aapt2 compile command with
    # and without --pseudo-localize, and places the output in the "default" and
    # "generated" top-level folders of symbol.zip, respectively. This results in
    # duplicated resources under "default" and "generated", which would normally
    # be resolved by resource merging (when using the android rules). Resource
    # merging, however, does not properly handle product tags, and should not be
    # needed to build framework resources as they have no dependencies. As Soong
    # always calls aapt2 with --pseudo-localize, this is resolved by deleting
    # the "default" top-level directory from the symbols.zip output of the
    # compile step.
    merged_resources = ctx.actions.declare_file(
        paths.join(ctx.label.name + "_symbols", "symbols_merged.zip"),
    )
    merge_args = ctx.actions.args()
    merge_args.add("-i", compiled_resources)
    merge_args.add("-o", merged_resources)
    merge_args.add("-x", "default/**/*")
    ctx.actions.run(
        inputs = [compiled_resources],
        outputs = [merged_resources],
        executable = ctx.executable._zip2zip,
        arguments = [merge_args],
        toolchain = None,
        mnemonic = "ExcludeDefaultResources",
    )
    return merged_resources

def _link_resources(ctx, fixed_manifest, compiled_resources):
    aapt = get_android_toolchain(ctx).aapt2.files_to_run
    apk = ctx.actions.declare_file(
        paths.join(ctx.label.name + "_files", "library.apk"),
    )
    r_txt = ctx.actions.declare_file(
        paths.join(ctx.label.name + "_symbols", "R.txt"),
    )
    proguard_cfg = ctx.actions.declare_file(
        paths.join(ctx.label.name + "_proguard", "_%s_proguard.cfg" % ctx.label.name),
    )

    # TODO: b/301457407 - support declare_directory in mixed builds or don't use it here
    java_srcs_dir = ctx.actions.declare_directory(ctx.label.name + "_resource_jar_sources")
    link_args = ctx.actions.args()
    link_args.add("link")

    # outputs
    link_args.add("-o", apk)
    link_args.add("--java", java_srcs_dir.path)
    link_args.add("--proguard", proguard_cfg)
    link_args.add("--output-text-symbols", r_txt)

    # args from aaptflags of the framework-res module definition
    link_args.add("--private-symbols", "com.android.internal")
    link_args.add("--no-auto-version")
    link_args.add("--auto-add-overlay")
    link_args.add("--enable-sparse-encoding")

    # flags from Soong's aapt2Flags function in build/soong/java/aar.go
    link_args.add("--no-static-lib-packages")
    link_args.add("--min-sdk-version", api.effective_version_string("current"))
    link_args.add("--target-sdk-version", api.effective_version_string("current"))
    link_args.add("--version-code", ctx.attr._platform_sdk_version[BuildSettingInfo].value)

    # Some builds set AppsDefaultVersionName() to include the build number ("O-123456").  aapt2 copies the
    # version name of framework-res into app manifests as compileSdkVersionCodename, which confuses things
    # if it contains the build number.  Use the PlatformVersionName instead.
    # Unique to framework-res, see https://cs.android.com/android/platform/superproject/main/+/main:build/soong/java/aar.go;l=271-275;drc=ee51bd6588ceb122dbf5f6d12bc398a1ce7f37ed.
    link_args.add("--version-name", ctx.attr._platform_version_name[BuildSettingInfo].value)

    # extra link flags from Soong's aaptBuildActions in build/soong/java/app.go
    link_args.add("--product", ctx.attr._aapt_characteristics[BuildSettingInfo].value)
    for config in ctx.attr._aapt_config[BuildSettingInfo].value:
        # TODO: b/301593550 - commas can't be escaped in a string-list passed in a platform mapping,
        # so commas are switched for ":" in soong injection, and back-substituted into commas
        # wherever the AAPTCharacteristics product config variable is used.
        link_args.add("-c", config.replace(":", ","))
    if ctx.attr._aapt_preferred_config[BuildSettingInfo].value:
        link_args.add("--preferred-density", ctx.attr._aapt_preferred_config[BuildSettingInfo].value)

    # inputs
    link_args.add("--manifest", fixed_manifest)
    link_args.add("-A", paths.join(paths.dirname(ctx.build_file_path), ctx.attr.assets_dir))
    link_args.add(compiled_resources)

    ctx.actions.run(
        inputs = [compiled_resources, fixed_manifest] + ctx.files.assets,
        outputs = [apk, java_srcs_dir, proguard_cfg, r_txt],
        executable = aapt,
        arguments = [link_args],
        toolchain = None,
        mnemonic = "AaptLinkFrameworkRes",
        progress_message = "Linking Framework Resources with Aapt...",
    )
    return apk, r_txt, proguard_cfg, java_srcs_dir

def _package_resource_source_jar(ctx, java_srcs_dir):
    r_java = ctx.actions.declare_file(
        ctx.label.name + ".srcjar",
    )
    srcjar_args = ctx.actions.args()
    srcjar_args.add("-write_if_changed")
    srcjar_args.add("-jar")
    srcjar_args.add("-o", r_java)
    srcjar_args.add("-C", java_srcs_dir.path)
    srcjar_args.add("-D", java_srcs_dir.path)
    ctx.actions.run(
        inputs = [java_srcs_dir],
        outputs = [r_java],
        executable = ctx.executable._soong_zip,
        arguments = [srcjar_args],
        toolchain = None,
        mnemonic = "FrameworkResSrcJar",
    )
    return r_java

def _generate_binary_r(ctx, r_txt, fixed_manifest):
    host_javabase = common.get_host_javabase(ctx)
    busybox = get_android_toolchain(ctx).android_resources_busybox.files_to_run
    out_class_jar = ctx.actions.declare_file(
        ctx.label.name + "_resources.jar",
    )

    _busybox.generate_binary_r(
        ctx,
        out_class_jar = out_class_jar,
        r_txt = r_txt,
        manifest = fixed_manifest,
        busybox = busybox,
        host_javabase = host_javabase,
    )
    return out_class_jar

def _impl(ctx):
    fixed_manifest = _fix_manifest(ctx)

    compiled_resources = _compile_resources(ctx)

    apk, r_txt, proguard_cfg, java_srcs_dir = _link_resources(ctx, fixed_manifest, compiled_resources)

    r_java = _package_resource_source_jar(ctx, java_srcs_dir)

    out_class_jar = _generate_binary_r(ctx, r_txt, fixed_manifest)

    # Unused but required to satisfy the native android_binary rule consuming this rule's JavaInfo provider.
    fake_proto_manifest = ctx.actions.declare_file("fake/proto_manifest.pb")
    ctx.actions.run_shell(
        inputs = [],
        outputs = [fake_proto_manifest],
        command = "touch {}".format(fake_proto_manifest.path),
        tools = [],
        mnemonic = "TouchFakeProtoManifest",
    )

    return [
        AndroidApplicationResourceInfo(
            resource_apk = apk,
            resource_java_src_jar = r_java,
            resource_java_class_jar = out_class_jar,
            manifest = fixed_manifest,
            resource_proguard_config = proguard_cfg,
            main_dex_proguard_config = None,
            r_txt = r_txt,
            resources_zip = None,
            databinding_info = None,
            should_compile_java_srcs = False,
        ),
        JavaInfo(
            output_jar = out_class_jar,
            compile_jar = out_class_jar,
            source_jar = r_java,
            manifest_proto = fake_proto_manifest,
        ),
        DataBindingV2Info(
            databinding_v2_providers_in_deps = [],
            databinding_v2_providers_in_exports = [],
        ),
        DefaultInfo(files = depset([apk])),
        OutputGroupInfo(
            srcjar = depset([r_java]),
            classjar = depset([out_class_jar]),
            resource_apk = depset([apk]),
        ),
        AndroidDexInfo(
            # Though there is no dexing happening in this rule, this class jar is
            # forwarded to the native android_binary rule because it outputs a pre-dex
            # deploy jar in a provider.
            deploy_jar = out_class_jar,
            final_classes_dex_zip = None,
            java_resource_jar = None,
        ),
    ]

_framework_resources_internal = rule(
    attrs = {
        "assets": _BASE_ATTRS["assets"],
        "assets_dir": _BASE_ATTRS["assets_dir"],
        "manifest": _BASE_ATTRS["manifest"],
        "resource_files": _BASE_ATTRS["resource_files"],
        "resource_zips": attr.label_list(
            allow_files = True,
            doc = "list of zip files containing Android resources.",
        ),
        "_host_javabase": _BASE_ATTRS["_host_javabase"],
        "_soong_zip": attr.label(allow_single_file = True, cfg = "exec", executable = True, default = "//build/soong/zip/cmd:soong_zip"),
        "_zip2zip": attr.label(allow_single_file = True, cfg = "exec", executable = True, default = "//build/soong/cmd/zip2zip:zip2zip"),
        "_manifest_fixer": attr.label(cfg = "exec", executable = True, default = "//build/soong/scripts:manifest_fixer"),
        "_platform_sdk_version": attr.label(
            default = Label("//build/bazel/product_config:platform_sdk_version"),
        ),
        "_platform_version_name": attr.label(
            default = Label("//build/bazel/product_config:platform_version_name"),
        ),
        "_aapt_characteristics": attr.label(
            default = Label("//build/bazel/product_config:aapt_characteristics"),
        ),
        "_aapt_config": attr.label(
            default = Label("//build/bazel/product_config:aapt_config"),
        ),
        "_aapt_preferred_config": attr.label(
            default = Label("//build/bazel/product_config:aapt_preferred_config"),
        ),
        "_platform_sdk_final": attr.label(
            default = "//build/bazel/product_config:platform_sdk_final",
            doc = "PlatformSdkFinal product variable",
        ),
        "_unbundled_build_apps": attr.label(
            default = "//build/bazel/product_config:unbundled_build_apps",
            doc = "UnbundledBuildApps product variable",
        ),
    },
    implementation = _impl,
    provides = [AndroidApplicationResourceInfo, OutputGroupInfo],
    toolchains = [
        "@rules_android//toolchains/android:toolchain_type",
    ],
    fragments = ["android"],
)

def framework_resources(
        name,
        certificate = None,
        certificate_name = None,
        tags = [],
        target_compatible_with = [],
        visibility = None,
        manifest = None,
        **kwargs):
    framework_resources_internal_name = ":" + name + common.PACKAGED_RESOURCES_SUFFIX
    _framework_resources_internal(
        name = framework_resources_internal_name[1:],
        tags = tags + ["manual"],
        target_compatible_with = target_compatible_with,
        visibility = ["//visibility:private"],
        manifest = manifest,
        **kwargs
    )

    # Rely on native android_binary until apk packaging and signing is starlarkified
    # TODO: b/301986521 - use starlark version of this logic once implemented.
    native.android_binary(
        name = name,
        application_resources = framework_resources_internal_name,
        debug_signing_keys = debug_signing_key(name, certificate, certificate_name),
        target_compatible_with = target_compatible_with,
        visibility = visibility,
        tags = tags,
        manifest = manifest,
    )

    native.filegroup(
        name = name + ".aapt.srcjar",
        srcs = [name],
        output_group = "srcjar",
        visibility = visibility,
        tags = tags,
    )

    native.filegroup(
        name = name + ".aapt.jar",
        srcs = [name],
        output_group = "classjar",
        visibility = visibility,
        tags = tags,
    )

    native.filegroup(
        name = name + ".export-package.apk",
        srcs = [name],
        output_group = "resource_apk",
        visibility = visibility,
        tags = tags,
    )
