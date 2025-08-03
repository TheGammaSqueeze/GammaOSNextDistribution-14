load("@dependencies//:requirements.bzl", "requirement")

package(default_visibility = ["//visibility:public"])

cc_test(
    name = "lua_engine_test",
    size = "small",
    srcs = ["lua_engine_test.cc"],
    deps = [
        "//:lua_engine_library",
        "@gtest//:gtest_main",
    ],
)

py_test(
    name = "app_test",
    size = "small",
    srcs = ["app_test.py"],
    deps = [
        "//:server_lib",
        requirement("beautifulsoup4"),
    ],
)

test_suite(
    name = "lua_interpreter_tests",
    tests = [
        "app_test",
        "lua_engine_test",
    ],
)
