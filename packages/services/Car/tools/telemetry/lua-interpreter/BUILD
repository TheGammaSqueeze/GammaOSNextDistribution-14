load("@dependencies//:requirements.bzl", "requirement")

package(default_visibility = ["//visibility:public"])

cc_binary(
    name = "lua_engine",
    srcs = [
        "lua_engine.cc",
        "lua_engine.h",
    ],
    copts = [
        "-fPIC",
        "-Wall",
        "-Wextra",
        "-O2",
    ],
    data = ["json.lua"],
    linkopts = ["-ldl"],
    linkshared = True,
    deps = ["@lua//:lua_library"],
)

cc_library(
    name = "lua_engine_library",
    srcs = ["lua_engine.cc"],
    hdrs = ["lua_engine.h"],
    data = ["json.lua"],
    deps = ["@lua//:lua_library"],
)

filegroup(
    name = "publisher_jsons",
    srcs = glob(["data/**"]),
)

filegroup(
    name = "templates",
    srcs = glob(["templates/**"]),
)

filegroup(
    name = "static_files",
    srcs = glob(["static/**"]),
)

py_library(
    name = "server_lib",
    srcs = ["app.py"],
    data = [
        ":lua_engine",
        ":publisher_jsons",
        ":static_files",
        ":templates",
    ],
    srcs_version = "PY3ONLY",
    deps = [requirement("Flask")],
)

py_binary(
    name = "server",
    srcs = ["app.py"],
    data = [
        ":lua_engine",
        ":publisher_jsons",
        ":static_files",
        ":templates",
    ],
    main = "app.py",
    srcs_version = "PY3ONLY",
    deps = [requirement("Flask")],
)
