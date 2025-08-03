cc_library(
    name = "nos_headers",
    hdrs = [
        "nugget/include/app_nugget.h",
        "nugget/include/app_transport_test.h",
        "nugget/include/application.h",
        "nugget/include/avb.h",
        "nugget/include/citadel_events.h",
        "nugget/include/feature_map.h",
        "nugget/include/flash_layout.h",
        "nugget/include/keymaster.h",
        "nugget/include/nos/device.h",
        "nugget/include/signed_header.h",
    ],
    deps = [
        "nos_headers_hals",
    ],
    strip_include_prefix = "nugget/include/",
    visibility = ["//visibility:public"],
)

cc_library(
    name = "nos_headers_hals",
    hdrs = [
        "nugget/include/hals/common.h",
        "nugget/include/hals/weaver.h",
        "nugget/include/hals/avb.h",
    ],
    strip_include_prefix = "nugget/include/",
    visibility = ["//visibility:public"],
)
