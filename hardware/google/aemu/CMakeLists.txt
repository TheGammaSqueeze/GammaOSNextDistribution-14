cmake_minimum_required(VERSION 3.18)

include(CMake/Version.cmake)

include(GoogleTest)
enable_testing()
# Disable test discovery after build.
# By default, `gtest_discover_tests()` add a post-build step to run test executables in order to
# discover the test targets. This is problematic in some build environments. (for example: if
# cross-compiling)
set(CMAKE_GTEST_DISCOVER_TESTS_DISCOVERY_MODE "PRE_TEST")

option(BUILD_SHARED_LIBS "Build using shared libraries" OFF)

if (AEMU_COMMON_GEN_PKGCONFIG)
   set(LOGGING_LIB_NAME aemu-logging)
   set(SNAPSHOT_LIB_NAME aemu-snapshot)
else()
   set(LOGGING_LIB_NAME logging-base)
   set(SNAPSHOT_LIB_NAME gfxstream-snapshot)
endif()

project(AEMUCommon
        DESCRIPTION "Android emulation utilities library"
        VERSION ${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH}
        LANGUAGES CXX C)

set(AEMU_COMMON_REPO_ROOT ${CMAKE_CURRENT_SOURCE_DIR})

if(NOT ANGLE_REPO_ROOT)
    set(ANGLE_REPO_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../external/angle)
endif()

option(AEMU_COMMON_GEN_PKGCONFIG
       "Generate install files used by pkg-config."
       OFF)
option(ENABLE_CLANG_THREAD_SAFETY_CHECKS
       "Enable clang thread-safety checks (-Wthread-safety)."
       OFF)
option(AEMU_COMMON_USE_PERFETTO "Use perfotto for tracing." OFF)
option(AEMU_BASE_USE_LZ4 "The lz4 dependency is provided, and compile the compressing stream." OFF)

if (WIN32)
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /Zi")
else()
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g3 -O3")
endif()
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

if (MSVC)
    # ask msvc not to warn not secure C ISO functions
    add_compile_definitions(_CRT_SECURE_NO_WARNINGS)
    # ask msvc not to warn non C ISO POSIX functions
    add_compile_definitions(_CRT_NONSTDC_NO_DEPRECATE)
endif()

# Set AEMU_BUILD_CONFIG_NAME to use a custom cmake build script located in
# build-config/$AEMU_BUILD_CONFIG_NAME. If AEMU_BUILD_CONFIG_NAME is unset,
# it will default to building everything. See build-config/gfxstream/CMakeLists.txt
# for an example.
if (NOT ${AEMU_COMMON_BUILD_CONFIG} STREQUAL "")
    if (NOT EXISTS "${AEMU_COMMON_REPO_ROOT}/build-config/${AEMU_COMMON_BUILD_CONFIG}")
        message(FATAL_ERROR "build-config/${AEMU_COMMON_BUILD_CONFIG} does not exist")
    endif()
    message(STATUS "aemu-common: Using custom build script in \
${AEMU_COMMON_REPO_ROOT}/build-config/${AEMU_COMMON_BUILD_CONFIG}")
    add_subdirectory(build-config/${AEMU_COMMON_BUILD_CONFIG})
endif()

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-extern-c-compat -Wno-return-type-c-linkage")

add_subdirectory(base)
add_subdirectory(snapshot)
add_subdirectory(host-common)
add_subdirectory(third-party)

add_library(aemu_common INTERFACE)
target_link_libraries(
    aemu_common
    INTERFACE
    aemu-base
    aemu-base.headers
    aemu-host-common
    aemu-host-common.headers)

if(AEMU_COMMON_GEN_PKGCONFIG)
    include(GNUInstallDirs)
    set(INSTALL_PC_FILES
        aemu_base
        aemu_logging
        aemu_host_common
        aemu_snapshot)
    if(ENABLE_VKCEREAL_TESTS)
        list(APPEND INSTALL_PC_FILES aemu_base_testing_support aemu_host_common_testing_support)
    endif()
    foreach(PC_FILE IN LISTS INSTALL_PC_FILES)
        configure_file(CMake/${PC_FILE}.pc.in ${PC_FILE}.pc @ONLY)
        install(
            FILES "${CMAKE_CURRENT_BINARY_DIR}/${PC_FILE}.pc"
            DESTINATION ${CMAKE_INSTALL_LIBDIR}/pkgconfig)
    endforeach()

    install(DIRECTORY base/include/aemu DESTINATION ${CMAKE_INSTALL_INCLUDEDIR})
    install(DIRECTORY snapshot/include/ DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}/aemu/snapshot)
    install(DIRECTORY host-common/include/ DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}/aemu/host-common)
    install(DIRECTORY third-party/cuda/include/ DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}/aemu/host-common)
    set(INSTALL_TARGETS
        aemu-base
        aemu-host-common
        ${SNAPSHOT_LIB_NAME}
        ${LOGGING_LIB_NAME})
    if(ENABLE_VKCEREAL_TESTS)
        list(APPEND INSTALL_TARGETS aemu-base-testing-support aemu-host-common-testing-support)
    endif()
    install(TARGETS ${INSTALL_TARGETS}
            LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
            INCLUDES DESTINATION ${CMAKE_INSTALL_INCLUDEDIR})
endif()
