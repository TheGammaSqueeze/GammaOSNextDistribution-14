load("@bazel_skylib//rules:common_settings.bzl", "bool_flag", "bool_setting", "string_list_setting", "string_setting")
load("@env//:env.bzl", "env")
load("@soong_injection//api_levels:platform_versions.bzl", "platform_versions")
load("//build/bazel/flags:common.bzl", "is_env_true")
load("//build/bazel/platforms/arch/variants:constants.bzl", "arch_variant_to_constraints")
load("//build/bazel/rules:metadata.bzl", "metadata")
load("//build/bazel/rules/apex:toolchain.bzl", "apex_toolchain")
load("//build/bazel/rules/common:api.bzl", "api")
load(":apex_aab_test.bzl", "apex_aab_test_suite")
load(":apex_key_test.bzl", "apex_key_test_suite")
load(":apex_mk_test.bzl", "apex_mk_test_suite")
load(":apex_test.bzl", "apex_test_suite")

# Setup package and default_metadata_file for _test_apex_sbom in apex_test.bzl
package(default_package_metadata = [":default_metadata_file"])

metadata(name = "default_metadata_file")

string_setting(
    name = "api_domain",
    build_setting_default = "system",
    visibility = ["//visibility:public"],
)

string_setting(
    name = "apex_name",
    build_setting_default = "",
    visibility = ["//visibility:public"],
)

string_setting(
    name = "base_apex_name",
    build_setting_default = "",
    visibility = ["//visibility:public"],
)

string_setting(
    name = "override_apex_manifest_default_version",
    build_setting_default = env.get("OVERRIDE_APEX_MANIFEST_DEFAULT_VERSION", ""),
    visibility = ["//visibility:public"],
)

bool_setting(
    name = "within_apex",
    build_setting_default = False,
    visibility = ["//visibility:public"],
)

config_setting(
    name = "system",
    constraint_values = [
        arch_variant_to_constraints["android"],
    ],
    flag_values = {
        ":api_domain": "system",
        ":within_apex": "False",
    },
)

# config setting to build sdk variants
config_setting(
    name = "unbundled_app",
    constraint_values = [
        arch_variant_to_constraints["android"],
    ],
    flag_values = {
        ":api_domain": "unbundled_app",
        ":within_apex": "False",
    },
)

config_setting(
    name = "unbundled_app.arm",
    constraint_values = [
        arch_variant_to_constraints["android"],
        arch_variant_to_constraints["arm"],
    ],
    flag_values = {
        ":api_domain": "unbundled_app",
        ":within_apex": "False",
    },
)

config_setting(
    name = "in_apex",
    flag_values = {
        ":within_apex": "True",
    },
)

config_setting(
    name = "non_apex",
    flag_values = {
        ":apex_name": "",
    },
)

config_setting(
    name = "android-in_apex",
    constraint_values = [
        arch_variant_to_constraints["android"],
    ],
    flag_values = {
        ":within_apex": "True",
    },
)

config_setting(
    name = "android-non_apex",
    constraint_values = [
        arch_variant_to_constraints["android"],
    ],
    flag_values = {
        ":within_apex": "False",
    },
)

config_setting(
    name = "linux_bionic-in_apex",
    constraint_values = [
        arch_variant_to_constraints["linux_bionic"],
    ],
    flag_values = {
        ":within_apex": "True",
    },
)

config_setting(
    name = "linux_bionic-non_apex",
    constraint_values = [
        arch_variant_to_constraints["linux_bionic"],
    ],
    flag_values = {
        ":within_apex": "False",
    },
)

string_list_setting(
    name = "apex_direct_deps",
    build_setting_default = [],
    visibility = ["//visibility:public"],
)

string_setting(
    name = "min_sdk_version",
    build_setting_default = "",
    visibility = ["//visibility:public"],
)

[
    config_setting(
        name = "min_sdk_version_" + str(level),
        flag_values = {
            ":min_sdk_version": str(level),
        },
    )
    for level in api.api_levels.values()
]

bool_flag(
    name = "unsafe_disable_apex_allowed_deps_check",
    build_setting_default = is_env_true(env.get("UNSAFE_DISABLE_APEX_ALLOWED_DEPS_CHECK")),
    visibility = ["//visibility:public"],
)

bool_flag(
    name = "unbundled_build_target_sdk_with_api_fingerprint",
    build_setting_default = is_env_true(env.get("UNBUNDLED_BUILD_TARGET_SDK_WITH_API_FINGERPRINT")),
    visibility = ["//visibility:public"],
)

string_setting(
    name = "platform_sdk_codename",
    build_setting_default = platform_versions.platform_sdk_codename,
    visibility = ["//visibility:public"],
)

toolchain_type(name = "apex_toolchain_type")

apex_toolchain(
    name = "apex_toolchain",
    aapt2 = "//frameworks/base/tools/aapt2",
    android_jar = "//prebuilts/sdk/current:public/android.jar",
    apex_compression_tool = "//system/apex/tools:apex_compression_tool",
    apexer = "//system/apex/apexer",
    avbtool = "//external/avb:avbtool",
    conv_apex_manifest = "//system/apex/apexer:conv_apex_manifest",
    dexdeps = "//dalvik/tools/dexdeps",  # tool input for gen_java_usedby_apex
    e2fsdroid = "//external/e2fsprogs/contrib/android:e2fsdroid",
    gen_java_usedby_apex = "//build/soong/scripts:gen_java_usedby_apex.sh",
    gen_ndk_usedby_apex = "//build/soong/scripts:gen_ndk_usedby_apex.sh",
    jsonmodify = "//build/soong/scripts:jsonmodify",
    manifest_fixer = "//build/soong/scripts:manifest_fixer",
    mke2fs = "//external/e2fsprogs/misc:mke2fs",
    notice_generator = "//build/bazel/compliance:bazel_notice_gen",
    readelf = "//prebuilts/clang/host/linux-x86:llvm-readelf",
    resize2fs = "//external/e2fsprogs/resize:resize2fs",
    sefcontext_compile = "//external/selinux/libselinux:sefcontext_compile",
    soong_zip = "//build/soong/zip/cmd:soong_zip",
)

toolchain(
    name = "apex_toolchain_def",
    exec_compatible_with = [
        "//build/bazel_common_rules/platforms/arch:x86_64",
        "//build/bazel_common_rules/platforms/os:linux",
    ],
    target_compatible_with = [
        "//build/bazel_common_rules/platforms/os:android",
    ],
    toolchain = ":apex_toolchain",
    toolchain_type = "//build/bazel/rules/apex:apex_toolchain_type",
)

apex_test_suite(
    name = "apex_tests",
)

apex_aab_test_suite(
    name = "apex_aab_tests",
)

apex_key_test_suite(
    name = "apex_key_tests",
)

apex_mk_test_suite(
    name = "apex_mk_tests",
)

filegroup(
    name = "signapk_deploy_jar",
    srcs = ["//build/make/tools/signapk:signapk_deploy.jar"],
    visibility = ["//visibility:public"],
)
