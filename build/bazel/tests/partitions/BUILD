load(":image_contents_test.bzl", "image_contents_test")

image_contents_test(
    name = "image_contains_apex_test",
    expected = "//build/bazel/examples/apex/minimal:build.bazel.examples.apex.minimal",
    image = "//build/bazel/examples/partitions:system_image",
    path = "/system/apex/build.bazel.examples.apex.minimal.apex",
    tags = ["manual"],  # b/234509030
)
