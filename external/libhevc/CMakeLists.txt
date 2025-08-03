cmake_minimum_required(VERSION 3.9.1)
project(libhevc C CXX)
enable_language(ASM)

set(HEVC_ROOT "${CMAKE_CURRENT_SOURCE_DIR}")
set(HEVC_CONFIG_DIR "${CMAKE_CURRENT_BINARY_DIR}")

if("${HEVC_ROOT}" STREQUAL "${HEVC_CONFIG_DIR}")
  message(
    FATAL_ERROR
      "Building from within the libhevc source tree is not supported.\n"
      "Hint: Run these commands\n"
      "$ rm -rf CMakeCache.txt CMakeFiles\n"
      "$ mkdir -p ./build\n"
      "$ cd ./build\n"
      "And re-run CMake from the build directory.")
endif()

set(THREADS_PREFER_PTHREAD_FLAG TRUE)
find_package(Threads REQUIRED)

set(CMAKE_STATIC_LIBRARY_PREFIX "")

if(${CMAKE_CROSSCOMPILING})
  set(THREADS_PTHREAD_ARG
      "2"
      CACHE STRING
            "Refer https://gitlab.kitware.com/cmake/cmake/-/issues/16920" FORCE)
endif()

include("${HEVC_ROOT}/cmake/utils.cmake")

libhevc_add_compile_options()
libhevc_add_definitions()
libhevc_set_link_libraries()

include("${HEVC_ROOT}/common/common.cmake")
include("${HEVC_ROOT}/decoder/libhevcdec.cmake")
include("${HEVC_ROOT}/encoder/libhevcenc.cmake")

include("${HEVC_ROOT}/test/decoder/hevcdec.cmake")
include("${HEVC_ROOT}/test/encoder/hevcenc.cmake")

include("${HEVC_ROOT}/fuzzer/hevc_dec_fuzzer.cmake")
include("${HEVC_ROOT}/fuzzer/hevc_enc_fuzzer.cmake")
