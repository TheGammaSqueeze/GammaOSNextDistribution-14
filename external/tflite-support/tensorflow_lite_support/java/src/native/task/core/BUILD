package(
    default_visibility = ["//tensorflow_lite_support:users"],
    licenses = ["notice"],  # Apache 2.0
)

# Default provider for BuiltInOpResolver. Create your own target, overwrite the
# function to provide a MutableOpResolver for customized OPs and/or a subset of
# builtin OPs.
cc_library(
    name = "builtin_op_resolver",
    srcs = ["builtin_op_resolver.cc"],
    deps = [
        "@org_tensorflow//tensorflow/lite:framework",
        "@org_tensorflow//tensorflow/lite/kernels:builtin_ops",
    ],
)

cc_library(
    name = "rbtml_op_resolver",
    srcs = ["rbtml_op_resolver.cc"],
    deps = [
        "//tensorflow_lite_support/custom_ops/kernel:unsorted_segment",
        "@org_tensorflow//tensorflow/lite:framework",
        "@org_tensorflow//tensorflow/lite/kernels:builtin_ops",
    ],
)
