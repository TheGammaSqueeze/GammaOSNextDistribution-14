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

load("//build/bazel/rules/java:bootclasspath.bzl", "bootclasspath")
load("//build/bazel/rules/java:java_system_modules.bzl", "java_system_modules")
load("//build/bazel/rules/java:import.bzl", "java_import")
load("//build/bazel/rules/java:versions.bzl", "java_versions")
load("//prebuilts/sdk:utils.bzl", "prebuilt_sdk_utils")
load("@bazel_tools//tools/jdk:default_java_toolchain.bzl", "DEFAULT_JAVACOPTS", "default_java_toolchain")
load("//build/bazel/rules/common:api.bzl", "api")
load("//build/bazel/rules/common:sdk_version.bzl", "sdk_version")
load("@soong_injection//java_toolchain:constants.bzl", "constants")
load("//build/bazel/rules/java/sdk:config_setting_names.bzl", sdk_config_setting = "config_setting_names")
load("//build/bazel/rules/java/errorprone:errorprone.bzl", "errorprone_global_flags")

# //prebuilts/sdk/current is a package, but the numbered directories under //prebuilts/sdk/ are not.
def _prebuilt_path_prefix(kind, api_level):
    common_prefix = "//prebuilts/sdk"
    if api_level == api.FUTURE_API_LEVEL:
        return "%s/current:%s" % (common_prefix, prebuilt_sdk_utils.to_kind_dir(kind))
    return "%s:%s/%s" % (common_prefix, api_level, prebuilt_sdk_utils.to_kind_dir(kind))

def _core_system_module_name(kind, api_level):
    return "%s_%s_core_system_module" % (kind, api_level)

def _android_jar_import_name(kind, api_level):
    return "%s_%s_android_jar" % (kind, api_level)

def _android_jar_file_name(kind, api_level):
    return "%s/android.jar" % _prebuilt_path_prefix(kind, api_level)

_SDK_PACKAGE_PREFIX = "//build/bazel/rules/java/sdk:"
_JAVA_PACKAGE_PREFIX = "//build/bazel/rules/java:"

def prebuilts_toolchain(java_toolchain_name, android_sdk_toolchain_name):
    """Creates a device java and android toolchain and their dependencies.

    Defines all java_import, java_system_modules, and bootclasspath targets that enable building
    java/android/kotlin targets against a prebuilt SDK. Defines config settings at the proper
    granularity (based on java version and sdk version), and the mappings between config setting and
    proper toolchain attributes.
    """
    java_version_select_dict = {
        _JAVA_PACKAGE_PREFIX + setting: str(version)
        for version, setting in java_versions.VERSION_TO_CONFIG_SETTING.items()
    }

    bootclasspath_select_dict = {
        _SDK_PACKAGE_PREFIX + sdk_config_setting.SDK_NONE: [],
    }

    # android_jar_select_dict and framework_aidl_select_dict are only used by the android
    # toolchain. We should never be in a situation where sdk_version = "none" and we're trying to
    # build an android_* target, but:
    #    1. a Bazel check enforces that every toolchain attribute resolves under every
    #       configuration, and
    #    2. the DexArchiveAspect can propagate to java_host_for_device dependencies, and
    #       tries to access an android.jar from there (b/278596841).
    # We should map "none" to an explicitly failing target to address 1, but until 2 is resolved,
    # we point to the public current artifacts.
    android_jar_select_dict = {
        _SDK_PACKAGE_PREFIX + sdk_config_setting.SDK_NONE: _android_jar_file_name(
            sdk_version.KIND_PUBLIC,
            api.FUTURE_API_LEVEL,
        ),  # ":failed_android.jar",
    }
    framework_aidl_select_dict = {
        _SDK_PACKAGE_PREFIX + sdk_config_setting.SDK_NONE: "%s/framework.aidl" % (
            _prebuilt_path_prefix(
                prebuilt_sdk_utils.to_aidl_kind(sdk_version.KIND_PUBLIC, api.FUTURE_API_LEVEL),
                api.FUTURE_API_LEVEL,
            )
        ),  # ":failed_framework.aidl",
    }

    for api_level in prebuilt_sdk_utils.API_LEVELS:
        for kind in prebuilt_sdk_utils.available_core_kinds_for_api_level(api_level):
            java_import(
                name = "%s_%s_core_jar" % (kind, api_level),
                jars = ["%s/core-for-system-modules.jar" % _prebuilt_path_prefix(kind, api_level)],
            )
            java_system_modules(
                name = _core_system_module_name(kind, api_level),
                deps = [":%s_%s_core_jar" % (kind, api_level)],
            )
        for kind in prebuilt_sdk_utils.available_kinds_for_api_level(api_level):
            java_import(
                name = _android_jar_import_name(kind, api_level),
                jars = [_android_jar_file_name(kind, api_level)],
            )
            config_setting = _SDK_PACKAGE_PREFIX + sdk_config_setting.kind_api(kind, api_level)
            android_jar_select_dict[config_setting] = _android_jar_file_name(
                kind,
                api_level,
            )

            framework_aidl_select_dict[config_setting] = "%s/framework.aidl" % (
                _prebuilt_path_prefix(
                    prebuilt_sdk_utils.to_aidl_kind(kind, api_level),
                    api_level,
                )
            )

            if java_versions.supports_pre_java_9(api_level):
                config_setting = _SDK_PACKAGE_PREFIX + sdk_config_setting.kind_api_pre_java_9(kind, api_level)
                bootclasspath_select_dict[config_setting] = [_gen_bootclasspath(
                    pre_java_9 = True,
                    kind = kind,
                    api_level = api_level,
                )]
            if java_versions.supports_post_java_9(api_level):
                config_setting = _SDK_PACKAGE_PREFIX + sdk_config_setting.kind_api_post_java_9(kind, api_level)
                bootclasspath_select_dict[config_setting] = [_gen_bootclasspath(
                    pre_java_9 = False,
                    kind = kind,
                    api_level = api_level,
                )]

    default_java_toolchain(
        name = java_toolchain_name,
        bootclasspath = select(bootclasspath_select_dict),
        source_version = select(java_version_select_dict),
        target_version = select(java_version_select_dict),
        # TODO(b/218720643): Support switching between multiple JDKs.
        java_runtime = "//prebuilts/jdk/jdk17:jdk17_runtime",
        toolchain_definition = False,
        misc = errorprone_global_flags + DEFAULT_JAVACOPTS + constants.CommonJdkFlags + select({
            _SDK_PACKAGE_PREFIX + sdk_config_setting.SDK_NONE: ["--system=none"],
            "//conditions:default": [],
        }),
    )

    native.android_sdk(
        name = android_sdk_toolchain_name,
        aapt = "//prebuilts/sdk/tools:linux/bin/aapt",
        aapt2 = "//prebuilts/sdk/tools:linux/bin/aapt2",
        adb = ":fail",  # TODO: use system/core/adb ?
        aidl = "//prebuilts/sdk/tools:linux/bin/aidl",
        android_jar = select(android_jar_select_dict),
        apksigner = ":apksigner",
        dx = "//prebuilts/sdk/tools:dx",  # TODO: add D8
        framework_aidl = select(framework_aidl_select_dict),
        main_dex_classes = "//prebuilts/sdk/tools:mainDexClasses.rules",
        main_dex_list_creator = ":fail",
        proguard = ":fail",  # TODO: add R8
        system = select({
            key: bootclasspath_select_dict[key][0]
            for key in bootclasspath_select_dict.keys()
            if "none" not in key
        } | {"//conditions:default": ":failed_bootclasspath"}),
        shrinked_android_jar = select(android_jar_select_dict),
        visibility = ["//visibility:public"],
        zipalign = "//prebuilts/sdk/tools:linux/bin/zipalign",
    )

def _gen_bootclasspath(pre_java_9, kind, api_level):
    bootclasspath_name = "toolchain_%s_java_9_android_%s_%s_bootclasspath" % ("pre" if pre_java_9 else "post", kind, api_level)
    auxiliary = [_android_jar_import_name(kind, api_level)]
    bootclasspath_attr = [_android_jar_import_name(kind, api_level), ":core_lambda_stubs"]
    system = None
    if pre_java_9:
        auxiliary = auxiliary + [":core_lambda_stubs"]
    else:
        system = _core_system_module_name(prebuilt_sdk_utils.to_core_kind(kind, api_level), api_level)

    bootclasspath(
        name = bootclasspath_name,
        auxiliary = auxiliary,
        bootclasspath = bootclasspath_attr,
        system = system,
    )
    return bootclasspath_name
