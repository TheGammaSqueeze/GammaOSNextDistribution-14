# Build only support in Android

if(AEMU_BASE_USE_LZ4 AND NOT TARGET lz4_static)
    message(FATAL_ERROR "lz4 is not provided.")
endif()

if(AEMU_COMMON_USE_PERFETTO AND NOT TARGET perfetto-tracing-only)
    message(FATAL_ERROR "perfetto-tracing-only is not provided.")
endif()

if(ENABLE_VKCEREAL_TESTS AND NOT TARGET gtest)
    set(INSTALL_GTEST OFF)

    # If gtest target is not provided, find it on the system.
    find_package(PkgConfig REQUIRED)
    pkg_search_module(gtest REQUIRED IMPORTED_TARGET GLOBAL gtest>=0.0.0)
    add_library(gtest ALIAS PkgConfig::gtest)

    pkg_search_module(gmock REQUIRED IMPORTED_TARGET GLOBAL gmock>=0.0.0)
    add_library(gmock ALIAS PkgConfig::gmock)

    pkg_search_module(gmock_main REQUIRED IMPORTED_TARGET GLOBAL gmock_main>=0.0.0)
    add_library(gmock_main ALIAS PkgConfig::gmock_main)
endif()
