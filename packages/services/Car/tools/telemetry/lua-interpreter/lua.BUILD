package(default_visibility = ["//visibility:public"])

cc_library(
    name = "lua_library",
    srcs =
        glob(
            [
                "**/*.c",
                "**/*.h",
            ],
            exclude = [
                "**/lauxlib.h",
                "**/lua.h",
                "**/lua.hpp",
                "**/luaconf.h",
                "**/lualib.h",
                "**/lua.c",
                "**/luac.c",
            ],
        ),
    hdrs = glob([
        "**/lauxlib.h",
        "**/lua.h",
        "**/lua.hpp",
        "**/luaconf.h",
        "**/lualib.h",
    ]),
    copts = [
        "-w",
        "-fPIC",
    ],
    defines = ["LUA_USE_LINUX"],
    includes = ["src"],
    linkopts = [
        "-lm",
        "-ldl",
    ],
)
