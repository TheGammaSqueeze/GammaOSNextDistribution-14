# Copyright (C) 2023 The Android Open Source Project
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

"""Macros to generate config settings for Soong config variables."""

load("@bazel_skylib//lib:selects.bzl", "selects")
load(
    "@soong_injection//product_config:soong_config_variables.bzl",
    _soong_config_bool_variables = "soong_config_bool_variables",
    _soong_config_string_variables = "soong_config_string_variables",
    _soong_config_value_variables = "soong_config_value_variables",
)
load("//build/bazel/platforms/arch/variants:constants.bzl", "arch_variant_to_constraints")
load("//build/bazel/utils:config_setting_boolean_algebra.bzl", "config_setting_boolean_algebra")

def soong_config_variable_config_settings():
    """
    soong_config_variable_config_settings creates all the config settings that represent the
    soong config variables.
    """
    for variable in _soong_config_bool_variables.keys():
        variable = variable.lower()
        native.config_setting(
            name = variable,
            flag_values = {
                "//build/bazel/product_config/soong_config_variables:" + variable: "True",
            },
        )

        # We need a "conditions_default" config setting so that we can select on the variable
        # being off, but there being some arch/os constraint in addition. This won't be used
        # directly in build files, but the below combinations of it + and arch will. For example:
        # soong_config_variables: {
        #   my_bool_var: {
        #     conditions_default: {
        #       target: {
        #         android: {
        #           cflags: ["-DFOO"],
        #         },
        #       },
        #     },
        #   },
        # },
        # would become:
        # select({
        #   "//build/bazel/product_variables:my_namespace__my_bool_variable__conditions_default__android": ["-DFOO"],
        #   "//conditions:default": [],
        # })
        # TODO(b/281568854) Update the bp2build converter to emit something like the following instead:
        # select({
        #   "//build/bazel/product_variables:my_namespace__my_bool_variable__android": [],
        #   "//build/bazel/os:android": ["-DFOO"],
        #   "//conditions:default": [],
        # })
        native.alias(
            name = variable + "__conditions_default",
            actual = select({
                ":" + variable: "//build/bazel/utils:always_off_config_setting",
                "//conditions:default": "//build/bazel/utils:always_on_config_setting",
            }),
        )

        # see the comment below about these arch-specific config settings
        for arch, archConstraint in arch_variant_to_constraints.items():
            selects.config_setting_group(
                name = variable + "__" + arch,
                match_all = [
                    ":" + variable,
                    archConstraint,
                ],
            )
            selects.config_setting_group(
                name = variable + "__conditions_default__" + arch,
                match_all = [
                    ":" + variable + "__conditions_default",
                    archConstraint,
                ],
            )

    for variable in _soong_config_value_variables.keys():
        # We want a config setting for when the variable is set to any non-empty string.
        # To do that, generate an _inverse config setting that's set when the value is an empty
        # string, and then invert it.
        variable = variable.lower()
        native.config_setting(
            name = variable + "_inverse",
            flag_values = {
                "//build/bazel/product_config/soong_config_variables:" + variable: "",
            },
        )
        native.alias(
            name = variable,
            actual = select({
                variable + "_inverse": "//build/bazel/utils:always_off_config_setting",
                "//conditions:default": "//build/bazel/utils:always_on_config_setting",
            }),
        )

    for variable, choices in _soong_config_string_variables.items():
        for choice in choices:
            variable = variable.lower()
            choice = choice.lower()
            var_with_choice = (variable + "__" + choice).lower()
            native.config_setting(
                name = var_with_choice,
                flag_values = {
                    "//build/bazel/product_config/soong_config_variables:" + variable: choice,
                },
            )

            # These config settings combine the soong config variable with architecture,
            # so that when a user has a build file like:
            # my_soong_config_module {
            #     name: "foo",
            #     target: {
            #         android: {
            #             cflags: ["-DFOO"],
            #         },
            #     },
            #     soong_config_variables: {
            #         my_string_variable: {
            #             value1: {
            #                 cflags: ["-DVALUE1_NOT_ANDROID"],
            #                 target: {
            #                     android: {
            #                         cflags: ["-DVALUE1"],
            #                     },
            #                 },
            #             },
            #             conditions_default: {
            #                 target: {
            #                     android: {
            #                         cflags: ["-DSTRING_VAR_CONDITIONS_DEFAULT"],
            #                     },
            #                 },
            #             },
            #         },
            #     },
            # }
            #
            # We emit something like:
            #
            # cflags = select({
            #     "//build/bazel_common_rules/platforms/os:android": ["-DFOO"],
            #     "//conditions:default": [],
            # }) + select({
            #     "//build/bazel/product_variables:my_namespace__my_string_variable__value1": ["-DVALUE1_NOT_ANDROID"],
            #     "//conditions:default": [],
            # }) + select({
            #     "//build/bazel/product_variables:my_namespace__my_string_variable__conditions_default__android": ["-DSTRING_VAR_CONDITIONS_DEFAULT"],
            #     "//build/bazel/product_variables:my_namespace__my_string_variable__value1__android": ["-DVALUE1"],
            #     "//conditions:default": [],
            # }),
            for arch, archConstraint in arch_variant_to_constraints.items():
                selects.config_setting_group(
                    name = var_with_choice + "__" + arch,
                    match_all = [
                        ":" + var_with_choice,
                        archConstraint,
                    ],
                )

        # Emit config settings that represent the string setting being off (as in, none of the
        # possible choices), but other arch/os requirements being active.
        for arch, archConstraint in arch_variant_to_constraints.items():
            config_setting_boolean_algebra(
                name = (variable + "__conditions_default__" + arch).lower(),
                expr = {"AND": [
                    archConstraint,
                ] + [
                    {"NOT": ":" + (variable + "__" + choice).lower()}
                    for choice in choices
                ]},
            )
