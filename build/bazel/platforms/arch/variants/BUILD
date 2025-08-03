# Cpu/Arch Variants and features

load(":constants.bzl", "arch_variant_to_constraints", "constants", "power_set")
load(":constants_test.bzl", "power_set_test_suite")

constraint_setting(
    name = "arch_variant_constraint",
)

licenses(["notice"])

package(
    default_visibility = ["//visibility:public"],
)

[
    constraint_value(
        name = arch_variant,
        constraint_setting = "arch_variant_constraint",
    )
    for arch_variant in constants.AvailableArchVariants
]

[
    [
        config_setting(
            name = variant + "-" + arch,
            constraint_values = [
                arch_variant_to_constraints[arch],
                ":" + variant,
            ],
        )
        for variant in variants
    ]
    for arch, variants in constants.ArchToVariants.items()
]

constraint_setting(
    name = "cpu_variant_constraint",
)

[
    constraint_value(
        name = cpu_variant,
        constraint_setting = "cpu_variant_constraint",
    )
    for cpu_variant in constants.AvailableCpuVariants
]

[
    [
        config_setting(
            name = variant + "-" + arch,
            constraint_values = [
                arch_variant_to_constraints[arch],
                ":" + variant,
            ],
        )
        for variant in variants
    ]
    for arch, variants in constants.CpuToVariants.items()
]

[
    (
        constraint_setting(
            name = "arch_feature_constraint_" + arch_feature,
        ),
        constraint_value(
            name = arch_feature,
            constraint_setting = "arch_feature_constraint_" + arch_feature,
        ),
    )
    for arch_feature in constants.AvailableArchFeatures
]

[
    [
        config_setting(
            name = arch + "-" + "-".join(sorted(features)),
            constraint_values = [
                arch_variant_to_constraints[arch],
            ] + [":" + feature for feature in features],
        )
        for features in power_set(
            all_features,
            include_empty = False,
        )
    ]
    for arch, all_features in constants.ArchToFeatures.items()
]

power_set_test_suite(name = "power_set_tests")
