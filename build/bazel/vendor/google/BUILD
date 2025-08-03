load("//build/bazel/rules/apex:apex_aab.bzl", "apex_aab")

package(default_visibility = ["//build/bazel/tests/apex:__subpackages__"])

# This package contains the multi-architecture aab and apks packages of
# Bazel-buildable Mainline modules. These are exclusively used for development
# and testing purposes and not for distribution.
#
# We have added testonly and visibility guardrails to ensure that these do not
# get used unintentionally.

modules = [
    "//build/bazel/examples/apex/minimal:build.bazel.examples.apex.minimal",
    "//frameworks/av/apex:com.android.media.swcodec",
    "//packages/modules/NeuralNetworks/apex:com.android.neuralnetworks",
    "//packages/modules/adb/apex:com.android.adbd",
    "//system/timezone/apex:com.android.tzdata",
]

name_label_map = {module[module.index(":") + 1:]: module for module in modules}

[
    (
        apex_aab(
            name = "%s_apex_aab" % name,
            testonly = True,
            mainline_module = label,
        ),
        genrule(
            name = "%s_apks" % name,
            testonly = True,
            srcs = ["%s_apex_aab" % name],
            outs = ["%s.apks" % name],
            cmd = "$(location //prebuilts/bundletool) build-apks --bundle $(location %s_apex_aab) --output $@" % name,
            tools = ["//prebuilts/bundletool"],
        ),
    )
    for name, label in name_label_map.items()
]

filegroup(
    name = "mainline_modules",
    testonly = True,
    srcs = ["%s_apex_aab" % name for name, label in name_label_map.items()],
)
