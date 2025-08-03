exports_files([
    "devkey.priv",
    "devkey.pub",
])

filegroup(
    name = "android_certificate_directory",
    srcs = glob([
        "*.pk8",
        "*.pem",
        "*.avbpubkey",
    ]),
    visibility = ["//build/bazel:__subpackages__"],
)

filegroup(
    name = "dev-keystore",
    srcs = ["devkey.keystore"],
    visibility = ["//visibility:public"],
)
