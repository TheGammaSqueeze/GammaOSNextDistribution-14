# Standard cpu name constraint_setting and constraint_values

licenses(["notice"])

package(
    default_visibility = ["//visibility:public"],
)

alias(
    name = "arm",
    actual = "@platforms//cpu:arm",
)

alias(
    name = "arm64",
    actual = "@platforms//cpu:arm64",
)

alias(
    name = "riscv64",
    actual = "@platforms//cpu:riscv64",
)

alias(
    name = "x86",
    actual = "@platforms//cpu:x86_32",
)

# Alias to the local_jdk's toolchain constraint to make local_jdk resolve
# correctly with --tool_java_runtime_version=local_jdk and the checked-in JDK.
alias(
    name = "x86_64",
    actual = "@platforms//cpu:x86_64",
)

constraint_setting(
    name = "secondary_arch_constraint",
)

constraint_value(
    name = "secondary_arm",
    constraint_setting = ":secondary_arch_constraint",
)

constraint_value(
    name = "secondary_arm64",
    constraint_setting = ":secondary_arch_constraint",
)

constraint_value(
    name = "secondary_riscv64",
    constraint_setting = ":secondary_arch_constraint",
)

constraint_value(
    name = "secondary_x86",
    constraint_setting = ":secondary_arch_constraint",
)

constraint_value(
    name = "secondary_x86_64",
    constraint_setting = ":secondary_arch_constraint",
)
