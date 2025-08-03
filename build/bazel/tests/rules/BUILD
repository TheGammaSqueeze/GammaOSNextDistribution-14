load("//build/bazel/rules:sh_binary.bzl", "sh_binary")

sh_library(
    name = "lib1",
    srcs = ["lib1.sh"],
)

sh_library(
    name = "lib2",
    srcs = ["lib2.sh"],
    deps = [":lib3"],
)

sh_library(
    name = "lib3",
    srcs = ["lib3.sh"],
)

sh_binary(
    name = "bin_with_deps",
    srcs = ["bin_with_deps.sh"],
    deps = [
        "lib1",
        "lib2",
    ],
)

genrule(
    name = "test_bin_with_deps",
    outs = ["out.txt"],
    cmd = "$(location :bin_with_deps) > $@",
    tools = [":bin_with_deps"],
)
