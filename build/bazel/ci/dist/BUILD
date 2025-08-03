load("//build/bazel_common_rules/dist:dist.bzl", "copy_to_dist_dir")

# bazel run --package_path=out/soong/workspace //build/bazel/ci/dist:mainline_modules -- --dist_dir=/tmp/dist
# TODO(jingwen): use a split transition on --platforms to dist all 4 architectures in a single invocation.
copy_to_dist_dir(
    name = "mainline_modules",
    data = [
        "//frameworks/av/apex:com.android.media.swcodec",
        "//packages/modules/NeuralNetworks/apex:com.android.neuralnetworks",
        "//packages/modules/adb/apex:com.android.adbd",
        "//system/timezone/apex:com.android.tzdata",
    ],
    flat = True,
)
