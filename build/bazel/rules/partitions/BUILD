load(":toolchain.bzl", "partition_toolchain")

toolchain_type(name = "partition_toolchain_type")

partition_toolchain(
    name = "partition_toolchain",
    avbtool = "//external/avb:avbtool",
    build_image = "//build/make/tools/releasetools:build_image",
    e2fsdroid = "//external/e2fsprogs/contrib/android:e2fsdroid",
    fec = "//system/extras/verity/fec",
    mke2fs = "//external/e2fsprogs/misc:mke2fs",
    mkfs_erofs = "//external/erofs-utils:mkfs.erofs",
    mkuserimg_mke2fs = "//system/extras/ext4_utils:mkuserimg_mke2fs",
    openssl = "//prebuilts/build-tools:linux-x86/bin/openssl",
    simg2img = "//system/core/libsparse:simg2img",
    tags = ["manual"],
    toybox = "//external/toybox",
    tune2fs = "//external/e2fsprogs/misc:tune2fs",
)

toolchain(
    name = "partition_toolchain_def",
    exec_compatible_with = [
        "//build/bazel_common_rules/platforms/arch:x86_64",
        "//build/bazel_common_rules/platforms/os:linux",
    ],
    tags = ["manual"],
    target_compatible_with = [
        "//build/bazel_common_rules/platforms/os:android",
    ],
    toolchain = ":partition_toolchain",
    toolchain_type = ":partition_toolchain_type",
)
