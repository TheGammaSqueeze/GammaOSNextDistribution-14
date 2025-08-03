load("@//build/bazel/product_config:android_product.bzl", "android_product")
load(":product_labels.bzl", "products")

package(default_visibility = [
    "@//build/bazel/product_config:__subpackages__",
])

# This package contains pregenerated soong.variables files for the aosp_<arch> products, used to
# make platforms for testing. This is an optimization, we could generate these directly from source
# at build time but it would add time to every `m nothing`. Converting the product config makefiles
# to starlark and checking them in would also solve this performance issue.
#
# This is also where we can define platforms that have set product config variables to certain
# values for testing. Unfortunately we cannot just transition on a single product config variable
# due to limitations in bazel.

[
    android_product(
        name = k,
        soong_variables = v,
    )
    for k, v in products.items()
]
