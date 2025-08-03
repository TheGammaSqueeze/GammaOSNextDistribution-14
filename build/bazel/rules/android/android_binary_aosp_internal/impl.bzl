"""
Copyright (C) 2023 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@rules_android//rules:java.bzl", "java")
load(
    "@rules_android//rules:processing_pipeline.bzl",
    "ProviderInfo",
    "processing_pipeline",
)
load("@rules_android//rules:resources.bzl", _resources = "resources")
load("@rules_android//rules:utils.bzl", "utils")
load("@rules_android//rules/android_binary_internal:impl.bzl", "finalize", _BASE_PROCESSORS = "PROCESSORS")
load("//build/bazel/rules/android:manifest_fixer.bzl", "manifest_fixer")
load("//build/bazel/rules/cc:cc_stub_library.bzl", "CcStubInfo")
load("//build/bazel/rules/common:api.bzl", "api")
load("//build/bazel/rules/common:config.bzl", "has_unbundled_build_apps")
load("//build/bazel/rules/common:sdk_version.bzl", "sdk_version")

CollectedCcStubsInfo = provider(
    "Tracks cc stub libraries to exclude from APK packaging.",
    fields = {
        "stubs": "A depset of Cc stub library files",
    },
)

_ATTR_ASPECTS = ["deps", "dynamic_deps", "implementation_dynamic_deps", "system_dynamic_deps", "src", "shared_debuginfo", "shared"]

def _collect_cc_stubs_aspect_impl(_target, ctx):
    """An aspect that traverses the dep tree along _ATTR_ASPECTS and collects all deps with cc stubs.

    For all discovered deps with cc stubs, add its linker_input and all dynamic_deps' linker_inputs to the returned list.

    Args:
        _target: Unused
        ctx: The current aspect context.

    Returns:
        A list of CollectedCcStubsInfos which point to linker_inputs of discovered cc stubs.
    """
    stubs = []
    extra_infos = []
    for attr in _ATTR_ASPECTS:
        if hasattr(ctx.rule.attr, attr):
            gotten_attr = getattr(ctx.rule.attr, attr)
            attr_as_list = gotten_attr
            if type(getattr(ctx.rule.attr, attr)) == "Target":
                attr_as_list = [gotten_attr]
            for dep in attr_as_list:
                if CcStubInfo in dep:
                    stubs.append(dep[CcSharedLibraryInfo].linker_input)
            extra_infos.extend(utils.collect_providers(CollectedCcStubsInfo, attr_as_list))

    for info in extra_infos:
        stubs.extend(info.stubs.to_list())
    return [CollectedCcStubsInfo(stubs = depset(stubs))]

collect_cc_stubs_aspect = aspect(
    implementation = _collect_cc_stubs_aspect_impl,
    attr_aspects = _ATTR_ASPECTS,
)

def _get_lib_name_from_ctx(ctx):
    # Use the device ABI for the arch name when naming subdirectories within the APK's lib/ dir
    # Note that the value from _product_config_abi[BuildSettingInfo].value is a list of strings
    # where only the first element matters.
    return ctx.attr._product_config_device_abi[BuildSettingInfo].value[0]

def _process_native_deps_aosp(ctx, **_unused_ctxs):
    """AOSP-specific native dep processof for android_binary.

    Bypasses any C++ toolchains or compiling or linking, as AOSP's JNI dependencies are all
    built via the cc_library_shared() macro, which already handles compilation and linking.

    Args:
        ctx: The build context
        **_unused_ctxs: Unused

    Returns:
        An AndroidBinaryNativeLibsInfo provider that informs downstream native.android_binary of the native libs.
    """

    # determine where in the APK to put the .so files
    lib_dir_name = _get_lib_name_from_ctx(ctx)

    # determine list of stub cc libraries
    stubs_to_ignore = []
    for dep in ctx.attr.deps:
        if CollectedCcStubsInfo in dep:
            stubs_to_ignore = dep[CollectedCcStubsInfo].stubs.to_list()

    # get all CcSharedLibraryInfo linker_inputs
    shared_libs = []
    for dep in ctx.attr.deps:
        if CcSharedLibraryInfo in dep:
            shared_libs.append(dep[CcSharedLibraryInfo].linker_input)

            for dyndep in dep[CcSharedLibraryInfo].dynamic_deps.to_list():
                if dyndep.linker_input not in stubs_to_ignore:
                    shared_libs.append(dyndep.linker_input)

    shared_lib_files = [lib.libraries[0].dynamic_library for lib in shared_libs]

    libs = dict()
    libs[lib_dir_name] = depset(shared_lib_files)

    return struct(
        name = "native_libs_ctx",
        value = struct(providers = [
            AndroidBinaryNativeLibsInfo(
                libs,
                None,
                None,
            ),
        ]),
    )

# Starlark implementation of AndroidApp.MinSdkVersion from build/soong/java/app.go
def _maybe_override_min_sdk_version(ctx):
    min_sdk_version = sdk_version.api_level_string_with_fallback(
        ctx.attr.manifest_values.get("minSdkVersion"),
        ctx.attr.sdk_version,
    )
    override_apex_manifest_default_version = ctx.attr._override_apex_manifest_default_version[BuildSettingInfo].value
    if (ctx.attr.updatable and
        override_apex_manifest_default_version and
        (api.parse_api_level_from_version(override_apex_manifest_default_version) >
         api.parse_api_level_from_version(min_sdk_version))):
        return override_apex_manifest_default_version
    return min_sdk_version

def _maybe_override_manifest_values(ctx):
    min_sdk_version = api.effective_version_string(_maybe_override_min_sdk_version(ctx))

    # TODO: b/300916281 - When Api fingerprinting is used, it should be appended to the target SDK version here.
    target_sdk_version = manifest_fixer.target_sdk_version_for_manifest_fixer(
        target_sdk_version = sdk_version.api_level_string_with_fallback(
            ctx.attr.manifest_values.get("targetSdkVersion"),
            ctx.attr.sdk_version,
        ),
        platform_sdk_final = ctx.attr._platform_sdk_final[BuildSettingInfo].value,
        has_unbundled_build_apps = has_unbundled_build_apps(ctx.attr._unbundled_build_apps),
    )
    return struct(
        min_sdk_version = min_sdk_version,
        target_sdk_version = target_sdk_version,
    )

def _process_manifest_aosp(ctx, **_unused_ctxs):
    maybe_overriden_values = _maybe_override_manifest_values(ctx)
    out_manifest = ctx.actions.declare_file("fixed_manifest/" + ctx.label.name + "/" + "AndroidManifest.xml")
    manifest_fixer.fix(
        ctx,
        manifest_fixer = ctx.executable._manifest_fixer,
        in_manifest = ctx.file.manifest,
        out_manifest = out_manifest,
        min_sdk_version = maybe_overriden_values.min_sdk_version,
        target_sdk_version = maybe_overriden_values.target_sdk_version,
    )

    updated_manifest_values = {
        key: ctx.attr.manifest_values[key]
        for key in ctx.attr.manifest_values.keys()
        if key not in ("minSdkVersion", "targetSdkVersion")
    }

    return ProviderInfo(
        name = "manifest_ctx",
        value = _resources.ManifestContextInfo(
            processed_manifest = out_manifest,
            processed_manifest_values = updated_manifest_values,
        ),
    )

# TODO: b/303862657 - Populate with any needed validation
def _validate_manifest_aosp(
        ctx,  # @unused
        **_unused_ctxs):
    return

# (b/274150785)  validation processor does not allow min_sdk that are a string
PROCESSORS = processing_pipeline.replace(
    _BASE_PROCESSORS,
    ManifestProcessor = _process_manifest_aosp,
    ValidateManifestProcessor = _validate_manifest_aosp,
    NativeLibsProcessor = _process_native_deps_aosp,
)

_PROCESSING_PIPELINE = processing_pipeline.make_processing_pipeline(
    processors = PROCESSORS,
    finalize = finalize,
)

def impl(ctx):
    """The rule implementation.

    Args:
      ctx: The context.

    Returns:
      A list of providers.
    """
    java_package = java.resolve_package_from_label(ctx.label, ctx.attr.custom_package)
    return processing_pipeline.run(ctx, java_package, _PROCESSING_PIPELINE)
