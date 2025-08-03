# This BUILD file mimics what bp2build will convert cc modules with aidl srcs to
load("//build/bazel/rules/cc:cc_aidl_library.bzl", "cc_aidl_library")
load("//build/bazel/rules/cc:cc_binary.bzl", "cc_binary")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "cc_library_shared")
load("//build/bazel/rules/cc:cc_library_static.bzl", "cc_library_static")

# Use aidl sources from another package
cc_aidl_library(
    name = "foo_cc_aidl_library",
    implementation_dynamic_deps = [
        "//frameworks/native/libs/binder:libbinder",
        "//system/core/libutils:libutils",
    ],
    deps = ["//build/bazel/examples/cc/aidl:foo"],
)

cc_library_shared(
    name = "foo",
    srcs = ["foo.cpp"],
    implementation_dynamic_deps = [
        "//frameworks/native/libs/binder:libbinder",
        "//system/core/libutils:libutils",
    ],
    whole_archive_deps = [
        "foo_cc_aidl_library",
    ],
)

cc_library_static(
    name = "foo_bp2build_cc_library_static",
    srcs = ["foo.cpp"],
    implementation_dynamic_deps = [
        "//frameworks/native/libs/binder:libbinder",
        "//system/core/libutils",
    ],
    whole_archive_deps = [
        "foo_cc_aidl_library",
    ],
)

cc_binary(
    name = "program_cc_binary",
    srcs = ["program.cpp"],
    dynamic_deps = [
        "//frameworks/native/libs/binder:libbinder",
        "//system/core/libutils:libutils",
    ],
    whole_archive_deps = [
        "foo_cc_aidl_library",
    ],
)
