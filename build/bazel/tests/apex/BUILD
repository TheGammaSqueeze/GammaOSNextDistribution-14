load("@bazel_skylib//rules:diff_test.bzl", "diff_test")
load(":apex_compression_test.bzl", "apex_compression_test")
load(":apex_diff_test.bzl", "apex_diff_test")
load(":apex_package_name_test.bzl", "apex_package_name_test")

filegroup(
    name = "com.android.adbd.capex",
    srcs = ["//packages/modules/adb/apex:com.android.adbd"],
    output_group = "signed_compressed_output",
    tags = ["manual"],
)

# Test that the compressed apex and uncompressed apex have the same contents
apex_diff_test(
    name = "com.android.adbd_compressed_vs_uncompressed",
    apex1 = ":com.android.adbd.capex",
    apex2 = "//packages/modules/adb/apex:com.android.adbd",
    target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
)

apex_compression_test(
    name = "build.bazel.examples.apex.minimal_apex",
    apex = "//build/bazel/examples/apex/minimal:build.bazel.examples.apex.minimal",
    compressed = False,
)

filegroup(
    name = "build.bazel.examples.apex.minimal_compressed.capex",
    srcs = ["//build/bazel/examples/apex/minimal:build.bazel.examples.apex.minimal_compressed"],
    output_group = "signed_compressed_output",
    tags = ["manual"],
)

apex_compression_test(
    name = "build.bazel.examples.apex.minimal_capex",
    apex = ":build.bazel.examples.apex.minimal_compressed.capex",
    compressed = True,
)

genrule(
    name = "build.bazel.examples.apex.minimal_android_manifest",
    testonly = True,
    srcs = ["//build/bazel/examples/apex/minimal:build.bazel.examples.apex.minimal"],
    outs = ["minimal_apex_android_manifest.txt"],
    cmd = "$(location //frameworks/base/tools/aapt2) dump xmltree --file AndroidManifest.xml $< > $@",
    tools = ["//frameworks/base/tools/aapt2"],
)

diff_test(
    name = "minimal_apex_android_manifest_test",
    file1 = "minimal_apex_android_manifest.txt",
    file2 = "minimal_apex_android_manifest.golden",
)

genrule(
    name = "adbd_aab_zipinfo",
    testonly = True,
    srcs = ["//build/bazel/vendor/google:com.android.adbd_apex_aab"],
    outs = ["adbd_aab_zipinfo.actual"],
    cmd = "$(location @bazel_tools//tools/zip:zipper) v $< | sort > $@",
    tools = ["@bazel_tools//tools/zip:zipper"],
)

diff_test(
    name = "aab_filelist_diff_test",
    file1 = "adbd_aab_zipinfo.golden",
    file2 = "adbd_aab_zipinfo.actual",
)

py_binary(
    name = "apks_binary_arches",
    srcs = ["apks_binary_arches.py"],
)

genrule(
    name = "adbd_apks_arches",
    testonly = True,
    srcs = ["//build/bazel/vendor/google:com.android.adbd_apks"],
    outs = ["adb_apks_arches.actual"],
    cmd = " ".join([
        "$(location :apks_binary_arches)",
        "--deapexer-path $(location //system/apex/tools:deapexer)",
        "--readelf-path $(location //prebuilts/clang/host/linux-x86:llvm-readelf)",
        "--debugfs-path $(location //external/e2fsprogs/debugfs)",
        "--fsckerofs-path $(location //external/erofs-utils:fsck.erofs)",
        "$<",
        "$@",
    ]),
    tags = ["manual"],
    tools = [
        ":apks_binary_arches",
        "//external/e2fsprogs/debugfs",
        "//external/erofs-utils:fsck.erofs",
        "//prebuilts/clang/host/linux-x86:llvm-readelf",
        "//system/apex/tools:deapexer",
    ],
)

diff_test(
    name = "apks_arch_diff_test",
    file1 = "adb_apks_arches.golden",
    file2 = "adb_apks_arches.actual",
)

genrule(
    name = "adbd_apks_zipinfo",
    testonly = True,
    srcs = ["//build/bazel/vendor/google:com.android.adbd_apks"],
    outs = ["adbd_apks_zipinfo.actual"],
    cmd = "$(location @bazel_tools//tools/zip:zipper) v $< | sort > $@",
    tools = ["@bazel_tools//tools/zip:zipper"],
)

diff_test(
    name = "apks_filelist_diff_test",
    file1 = "adbd_apks_zipinfo.golden",
    file2 = "adbd_apks_zipinfo.actual",
)

apex_package_name_test(
    name = "build.bazel.examples.apex.minimal_package_name",
    apex = "//build/bazel/examples/apex/minimal:build.bazel.examples.apex.minimal",
    expected_package_name = "build.bazel.examples.apex.minimal",
)

apex_package_name_test(
    name = "build.bazel.examples.apex.override.minimal_package_name",
    apex = "//build/bazel/examples/apex/minimal:build.bazel.examples.apex.override.minimal",
    expected_package_name = "build.bazel.examples.apex.override.minimal",
)

apex_package_name_test(
    name = "com.android.adbd_package_name",
    apex = "//packages/modules/adb/apex:com.android.adbd",
    expected_package_name = "com.android.adbd",
)

genrule(
    name = "com.android.adbd_manifest_json",
    srcs = ["//packages/modules/adb/apex:com.android.adbd"],
    outs = ["com.android.adbd_manifest.json"],
    cmd = "$(location //system/apex/tools:deapexer) info $< > $@",
    tools = ["//system/apex/tools:deapexer"],
)

diff_test(
    name = "com.android.adbd_manifest_diff_test",
    file1 = "com.android.adbd_manifest.json",
    file2 = "com.android.adbd_manifest.json.golden",
)

filegroup(
    name = "minimal_apex_coverage_files",
    srcs = ["//build/bazel/examples/apex/minimal:build.bazel.examples.apex.minimal"],
    output_group = "coverage_files",
)

genrule(
    name = "minimal_apex_using_txt",
    srcs = [":minimal_apex_coverage_files"],
    outs = ["minimal_apex_using.txt"],
    cmd = "cat $(location :minimal_apex_coverage_files) | sort > $@",
)

diff_test(
    name = "minimal_apex_symbols_used_by_apex_diff_test",
    file1 = ":minimal_apex_using.txt",
    file2 = ":minimal_apex_using.txt.golden",
    target_compatible_with = [
        "//build/bazel_common_rules/platforms/os:android",
        "//build/bazel_common_rules/platforms/arch:arm64",
    ],
)

filegroup(
    name = "minimal_apex_java_coverage_files",
    srcs = ["//build/bazel/examples/apex/minimal:build.bazel.examples.apex.minimal"],
    output_group = "java_coverage_files",
)

genrule(
    name = "minimal_apex_using_xml",
    srcs = [":minimal_apex_java_coverage_files"],
    outs = ["minimal_apex_using.xml"],
    cmd = "cat $(location :minimal_apex_java_coverage_files) | sort > $@",
)

diff_test(
    name = "minimal_apex_java_symbols_used_by_apex_diff_test",
    file1 = ":minimal_apex_using.xml",
    file2 = ":minimal_apex_using.xml.golden",
    target_compatible_with = [
        "//build/bazel_common_rules/platforms/os:android",
        "//build/bazel_common_rules/platforms/arch:arm64",
    ],
)

filegroup(
    name = "com.android.adbd_backing_libs",
    srcs = ["//packages/modules/adb/apex:com.android.adbd"],
    output_group = "backing_libs",
)

diff_test(
    name = "com.android.adbd_backing_libs_diff_test",
    file1 = ":com.android.adbd_backing_libs",
    file2 = "com.android.adbd_backing.txt.golden",
)

filegroup(
    name = "com.android.adbd_installed_files",
    srcs = ["//packages/modules/adb/apex:com.android.adbd"],
    output_group = "installed_files",
)
