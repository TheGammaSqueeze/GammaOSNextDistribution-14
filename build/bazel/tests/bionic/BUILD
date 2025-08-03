load("@soong_injection//cc_toolchain:config_constants.bzl", "constants")

# This test requires bp2build to run and the generated BUILD files in the source tree.
sh_test(
    name = "verify_bionic_outputs",
    srcs = ["verify_bionic_outputs.sh"],
    args = [
        "$(location //prebuilts/clang/host/linux-x86:llvm-nm)",
        "$(location //prebuilts/clang/host/linux-x86:llvm-readelf)",
    ],
    data = [
        "//bionic/libc",
        "//bionic/libc:libc_bp2build_cc_library_static",
        "//bionic/libdl:libdl_android",
        "//bionic/libdl:libdl_android_bp2build_cc_library_static",
        "//bionic/linker:ld-android",
        "//bionic/linker:ld-android_bp2build_cc_library_static",
        "//prebuilts/clang/host/linux-x86:llvm-nm",
        "//prebuilts/clang/host/linux-x86:llvm-readelf",
    ],
    deps = ["@bazel_tools//tools/bash/runfiles"],
)

sh_test(
    name = "compare_libc_stripping",
    srcs = ["compare_libc_stripping.sh"],
    data = [
        "//bionic/libc",
        "//bionic/libc:libc_unstripped",
    ],
    deps = ["@bazel_tools//tools/bash/runfiles"],
)
