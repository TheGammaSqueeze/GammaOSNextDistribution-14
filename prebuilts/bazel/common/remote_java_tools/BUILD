package(default_visibility = ["//visibility:public"])

filegroup(
    name = "GenClass",
    srcs = ["java_tools/GenClass_deploy.jar"],
)

filegroup(
    name = "jacoco_coverage_runner_filegroup",
    srcs = ["java_tools/JacocoCoverage_jarjar_deploy.jar"],
)

java_import(
    name = "jacoco_coverage_runner",
    jars = ["java_tools/JacocoCoverage_jarjar_deploy.jar"],
)

filegroup(
    name = "JacocoCoverage",
    srcs = ["java_tools/JacocoCoverage_jarjar_deploy.jar"],
)

filegroup(
    name = "JavaBuilder",
    srcs = ["java_tools/JavaBuilder_deploy.jar"],
)

filegroup(
    name = "Runner",
    srcs = ["java_tools/Runner_deploy.jar"],
)

filegroup(
    name = "VanillaJavaBuilder",
    srcs = ["java_tools/VanillaJavaBuilder_deploy.jar"],
)

filegroup(
    name = "TurbineDirect",
    srcs = ["java_tools/turbine_direct_binary_deploy.jar"],
)

################### Proguard ###################
java_import(
    name = "proguard_import",
    jars = ["java_tools/proguard.jar"],
)

java_binary(
    name = "proguard",
    main_class = "proguard.ProGuard",
    visibility = ["//visibility:public"],
    runtime_deps = [":proguard_import"],
)

# The java tools contain sources for the below targets, which are a
# fallback when a prebuilt is not available on a given platform. We
# don't currently need these, but the below aliases let
# @bazel_tools//tools/jdk:ijar_prebuilt_binary_or_cc_binary and
# @bazel_tools//tools/jdk:singlejar_prebuilt_or_cc_binary resolve
# under any condition, which in turn enables bazel queries on targets
# that depend on them (e.g. default java toolchains).

alias(
    name = "ijar_cc_binary",
    actual = "@remote_java_tools_linux//:ijar_prebuilt_binary",
)

alias(
    name = "singlejar_cc_bin",
    actual = "@remote_java_tools_linux//:prebuilt_singlejar",
)
