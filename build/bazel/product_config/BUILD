load("@bazel_skylib//rules:common_settings.bzl", "bool_flag", "string_flag", "string_list_flag")
load("//build/bazel/rules/aconfig:aconfig_value_sets.bzl", "aconfig_value_sets")
load(":product_variables_for_attributes.bzl", "product_variables_for_attributes")

package(default_visibility = ["//visibility:public"])

# This rule type outputs a TemplateVariableInfo provider that contains a subset of the product
# variables to be available for expansion using make-like syntax on certain rule attributes.
# Soong had this feature, so it was ported to bazel.
product_variables_for_attributes(name = "product_variables_for_attributes")

# This label flag will always point to the device platform for the current android product.
# It can be used to transition into the device configuration from the host configuration.
label_flag(
    name = "device_platform",
    build_setting_default = "//build/bazel/utils:fail",
)

string_flag(
    name = "target_build_variant",
    build_setting_default = "eng",
    values = [
        "user",
        "userdebug",
        "eng",
    ],
)

_string_list_variables = [
    # keep-sorted start
    "aapt_config",
    "build_version_tags",
    "cfi_exclude_paths",
    "cfi_include_paths",
    "device_abi",
    "manifest_package_name_overrides",
    "memtag_heap_async_include_paths",
    "memtag_heap_exclude_paths",
    "memtag_heap_sync_include_paths",
    "tidy_checks",
    "unbundled_build_apps",
    # keep-sorted end
]

_string_variables = [
    # keep-sorted start
    # TODO: b/301593550 - commas can't be escaped in a string-list passed in a platform mapping,
    # so commas are switched for ":" in soong injection, and must be back-substituted
    # into commas wherever the AAPTCharacteristics product config variable is used.
    "aapt_characteristics",
    "aapt_preferred_config",
    "apex_global_min_sdk_version_override",
    "build_id",
    "default_app_certificate",
    "device_max_page_size_supported",
    "device_name",
    "device_product",
    "override_rs_driver",
    "platform_sdk_extension_version",
    "platform_sdk_version",
    "platform_sdk_version_or_codename",
    "platform_security_patch",
    "platform_version_last_stable",
    "platform_version_name",
    "product_brand",
    "product_manufacturer",
    "release_aconfig_flag_default_permission",
    "release_version",
    # keep-sorted end
]

_bool_variables = [
    # keep-sorted start
    "always_use_prebuilt_sdks",
    "arc",
    "binder32bit",
    "build_broken_incorrect_partition_images",
    "build_from_text_stub",
    "compressed_apex",
    "device_no_bionic_page_size_macro",
    "enforce_vintf_manifest",
    "malloc_not_svelte",
    "malloc_pattern_fill_contents",
    "malloc_zero_contents",
    "native_coverage",
    "pdk",
    "platform_sdk_final",
    "release_aidl_use_unfrozen",
    "safestack",
    "treble_linker_namespaces",
    "uml",
    "unbundled_build",
    # keep-sorted end
]

[
    string_list_flag(
        name = name,
        build_setting_default = [],
    )
    for name in _string_list_variables
]

[
    string_flag(
        name = name,
        build_setting_default = "",
    )
    for name in _string_variables
]

[
    bool_flag(
        name = name,
        build_setting_default = False,
    )
    for name in _bool_variables
]

bool_flag(
    name = "enable_cfi",
    build_setting_default = True,
)

label_flag(
    name = "default_app_certificate_filegroup",
    build_setting_default = "//build/bazel/utils:empty_filegroup",
)

label_flag(
    name = "release_aconfig_value_sets",
    build_setting_default = ":empty_aconfig_value_sets",
)

aconfig_value_sets(
    name = "empty_aconfig_value_sets",
    value_sets = [],
    visibility = ["//visibility:public"],
)
