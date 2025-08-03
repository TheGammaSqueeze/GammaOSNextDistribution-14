load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@soong_injection//product_config:product_variable_constants.bzl", "product_var_constant_info")
load(
    "@soong_injection//product_config:soong_config_variables.bzl",
    _soong_config_value_variables = "soong_config_value_variables",
)

_vars_to_labels = {
    var.lower(): "//build/bazel/product_config/soong_config_variables:" + var.lower()
    for var in _soong_config_value_variables
} | {
    var: "//build/bazel/product_config:" + var.lower()
    for var, info in product_var_constant_info.items()
    if info.selectable and var != "Debuggable" and var != "Eng"
}

def _product_variables_for_attributes_impl(ctx):
    result = {}

    def value_to_string(value):
        typ = type(value)
        if typ == "bool":
            return "1" if value else "0"
        elif typ == "list":
            return ",".join(value)
        elif typ == "int":
            return str(value)
        elif typ == "string":
            return value
        else:
            fail("Unknown type")

    for var in _vars_to_labels:
        result[var] = value_to_string(getattr(ctx.attr, "_" + var)[BuildSettingInfo].value)

    result["debuggable"] = value_to_string(ctx.attr._target_build_variant[BuildSettingInfo].value in ["userdebug", "eng"])
    result["eng"] = value_to_string(ctx.attr._target_build_variant[BuildSettingInfo].value == "eng")

    return [platform_common.TemplateVariableInfo(result)]

# Provides product variables for templated string replacement.
product_variables_for_attributes = rule(
    implementation = _product_variables_for_attributes_impl,
    attrs = {
        "_" + var: attr.label(default = label)
        for var, label in _vars_to_labels.items()
    } | {
        "_target_build_variant": attr.label(default = "//build/bazel/product_config:target_build_variant"),
    },
)
