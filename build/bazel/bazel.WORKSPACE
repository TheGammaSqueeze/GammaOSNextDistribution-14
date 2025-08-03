# This repository provides files that Soong emits during bp2build (other than
# converted BUILD files), mostly .bzl files containing constants to support the
# converted BUILD files.
load("//build/bazel/rules:soong_injection.bzl", "soong_injection_repository")

soong_injection_repository(name = "soong_injection")

load("//build/bazel/rules:env.bzl", "env_repository")

env_repository(
    name = "env",
)

# This repository is a containter for API surface stub libraries.
load("//build/bazel/rules:api_surfaces_injection.bzl", "api_surfaces_repository")

# TODO: Once BUILD files for stubs are checked-in, this should be converted to a local_repository.
api_surfaces_repository(name = "api_surfaces")

load("//build/bazel_common_rules/workspace:external.bzl", "import_external_repositories")

import_external_repositories(
    bazel_skylib = True,
    io_abseil_py = True,
)

load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")

bazel_skylib_workspace()

local_repository(
    name = "rules_android",
    path = "external/bazelbuild-rules_android",
)

local_repository(
    name = "rules_license",
    path = "external/bazelbuild-rules_license",
)

local_repository(
    name = "rules_python",
    path = "external/bazelbuild-rules_python",
)

local_repository(
    name = "rules_cc",
    path = "external/bazelbuild-rules_cc",
)

register_toolchains(
    "//prebuilts/build-tools:py_toolchain",

    # For Android rules
    "//prebuilts/sdk:all",

    # For APEX rules
    "//build/bazel/rules/apex:all",

    # For partition rules
    "//build/bazel/rules/partitions:all",
)

bind(
    name = "databinding_annotation_processor",
    actual = "//prebuilts/sdk:compiler_annotation_processor",
)

bind(
    name = "android/dx_jar_import",
    actual = "//prebuilts/sdk:dx_jar_import",
)

# The r8.jar in prebuilts/r8 happens to have the d8 classes needed
# for Android app building, whereas the d8.jar in prebuilts/sdk/tools doesn't.
bind(
    name = "android/d8_jar_import",
    actual = "//prebuilts/r8:r8lib-prebuilt",
)

# TODO(b/201242197): Avoid downloading remote_coverage_tools (on CI) by creating
# a stub workspace. Test rules (e.g. sh_test) depend on this external dep, but
# we don't support coverage yet. Either vendor the external dep into AOSP, or
# cut the dependency from test rules to the external repo.
local_repository(
    name = "remote_coverage_tools",
    path = "build/bazel_common_rules/rules/coverage/remote_coverage_tools",
)

# Stubbing the local_jdk both ensures that we don't accidentally download remote
# repositories and allows us to let the Kotlin rules continue to access
# @local_jdk//jar.
local_repository(
    name = "local_jdk",
    path = "build/bazel/rules/java/stub_local_jdk",
)

# The following 2 repositories contain prebuilts that are necessary to the Java Rules.
# They are vendored locally to avoid the need for CI bots to download them.
local_repository(
    name = "remote_java_tools",
    path = "prebuilts/bazel/common/remote_java_tools",
)

local_repository(
    name = "remote_java_tools_linux",
    path = "prebuilts/bazel/linux-x86_64/remote_java_tools_linux",
)

# TODO(b/253667328): the below 3 repositories are all pointed to shim
# repositories with targets that will cause failures if they are
# actually depended on. Eventually we should properly vendor these
# repositories.
local_repository(
    name = "remote_java_tools_darwin_x86_64",
    path = "prebuilts/bazel/darwin-x86_64/remote_java_tools_darwin",
)

local_repository(
    name = "remote_java_tools_darwin_arm64",
    path = "prebuilts/bazel/darwin-x86_64/remote_java_tools_darwin",
)

local_repository(
    name = "remote_java_tools_windows",
    path = "prebuilts/bazel/darwin-x86_64/remote_java_tools_darwin",
)

# The following repository contains android_tools prebuilts.
local_repository(
    name = "android_tools",
    path = "prebuilts/bazel/common/android_tools",
)

# The vendored rules_java repository.
local_repository(
    name = "rules_java",
    path = "external/bazelbuild-rules_java",
)

register_toolchains(
    "//prebuilts/jdk/jdk17:runtime_toolchain_definition",
    "//build/bazel/rules/java:jdk17_host_toolchain_java_definition",
)

local_repository(
    name = "kotlin_maven_interface",
    path = "build/bazel/rules/kotlin/maven_interface",
)

local_repository(
    name = "rules_kotlin",
    path = "external/bazelbuild-kotlin-rules",
    repo_mapping = {
        "@maven": "@kotlin_maven_interface",
        "@bazel_platforms": "@platforms",
    },
)

new_local_repository(
    name = "kotlinc",
    build_file = "//build/bazel/rules/kotlin:kotlinc.BUILD",
    path = "external/kotlinc",
)

register_toolchains("//build/bazel/rules/kotlin:kt_jvm_toolchain_linux")

load("//build/bazel/toolchains/clang/host/linux-x86:cc_toolchain_config.bzl", "cc_register_toolchains")

cc_register_toolchains()

local_repository(
    name = "io_bazel_rules_go",
    path = "external/bazelbuild-rules_go",
)

load(
    "@io_bazel_rules_go//go:deps.bzl",
    "go_register_toolchains",
    "go_rules_dependencies",
    "go_wrap_sdk",
)

go_wrap_sdk(
    name = "go_sdk",
    # Add coveragedesign to experiments. This is a temporary hack due to two separate issues combinining together
    # 1. android:
    # Starting with go 1.20, the standard go sdk does not ship with .a files for the standard libraries
    # However, this breaks the go rules in build/blueprint. As a temporary workaround, we distribute prebuilts of the standard libaries
    # in prebuilts/go using GODEBUG='installgoroot=all' in the prebuilt update script
    #
    # 2. rules_go:
    # coverage is not supported in rules_go, and therefore it adds nocoveragedesign to GOEXPERIMENT. This is not an issue for non Android cases
    # since the go SDK used in those cases does not contain prebuilts of standard libraries. The stdlib is built from scratch with `nocoverageredesign`.
    #
    # Without this, we run into isues during compilation
    # ```
    # GoCompilePkg build/blueprint/blueprint-deptools.a failed
    # build/blueprint/deptools/depfile.go:18:2: could not import fmt (object is [go object linux amd64 go1.20.2 GOAMD64=v1 X:unified,regabiwrappers,regabiargs,coverageredesign
    #                                                                                                                                                          ^^^^^^^^^^^^^^^^
    # ] expected [go object linux amd64 go1.20.2 GOAMD64=v1 X:unified,regabiwrappers,regabiargs
    # ```
    # TODO - b/288456805: Remove this hardcoded experiment value
    experiments = ["coverageredesign"],
    # The expected key format is <goos>_<goarch>
    # The value is any file in the GOROOT for that platform
    root_files = {
        "linux_amd64": "@//:prebuilts/go/linux-x86/README.md",
        "darwin_amd64": "@//prebuilts/go/darwin-x86/README.md",
    },
)

go_rules_dependencies()

go_register_toolchains(experiments = [])

local_repository(
    name = "rules_proto",
    path = "build/bazel/rules/proto",
)

local_repository(
    name = "rules_rust",
    path = "external/bazelbuild-rules_rust",
)

new_local_repository(
    name = "rules_rust_tinyjson",
    build_file = "@rules_rust//util/process_wrapper:BUILD.tinyjson.bazel",
    path = "external/rust/crates/tinyjson",
)

local_repository(
    name = "rules_testing",
    path = "external/bazelbuild-rules_testing",
)

register_toolchains(
    # The base toolchains need to be registered before <os_arch>
    # to ensure it is preferably resolved when it's enabled by
    # base_toolchain_enabled config_setting
    "//build/bazel/toolchains/rust/bootstrap:rust_toolchain_android_arm64_base",
    "//build/bazel/toolchains/rust/bootstrap:rust_toolchain_android_arm_base",
    "//build/bazel/toolchains/rust/bootstrap:rust_toolchain_android_x86_64_base",
    "//build/bazel/toolchains/rust/bootstrap:rust_toolchain_android_x86_base",
    "//build/bazel/toolchains/rust:rust_toolchain_android_arm64",
    "//build/bazel/toolchains/rust:rust_toolchain_android_arm",
    "//build/bazel/toolchains/rust:rust_toolchain_android_x86_64",
    "//build/bazel/toolchains/rust:rust_toolchain_android_x86",
    "build/bazel/toolchains/rust:rust_toolchain_x86_64_unknown-linux-gnu",
    "build/bazel/toolchains/rust:proto-toolchain",
)
