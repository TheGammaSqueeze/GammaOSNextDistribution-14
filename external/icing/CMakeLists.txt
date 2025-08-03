# Copyright 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

cmake_minimum_required(VERSION 3.10.2)

project(icing)

add_definitions("-DICING_REVERSE_JNI_SEGMENTATION=1")
set(VERSION_SCRIPT "${CMAKE_CURRENT_SOURCE_DIR}/icing/jni.lds")
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_SHARED_LINKER_FLAGS
    "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,--version-script=${VERSION_SCRIPT}")

set(
    Protobuf_PREBUILTS_DIR
    "${CMAKE_CURRENT_SOURCE_DIR}/../../prebuilts/protobuf")
set(Protobuf_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../protobuf")
set(Protobuf_TARGET_BINARY_DIR "${CMAKE_CURRENT_BINARY_DIR}/protobuf-target")
set(Icing_PROTO_GEN_DIR "${CMAKE_CURRENT_BINARY_DIR}/icing-protobuf-gen")

## Configure libprotobuf ##
# Find the right protoc to compile our proto files
if(${CMAKE_HOST_SYSTEM_NAME} STREQUAL "Darwin")
  set(Protobuf_PROTOC_PATH "${Protobuf_PREBUILTS_DIR}/darwin-x86_64/protoc")
elseif(${CMAKE_HOST_SYSTEM_NAME} STREQUAL "Linux")
  set(Protobuf_PROTOC_PATH "${Protobuf_PREBUILTS_DIR}/linux-x86_64/protoc")
elseif(${CMAKE_HOST_SYSTEM_NAME} STREQUAL "Windows")
  set(Protobuf_PROTOC_PATH "${Protobuf_PREBUILTS_DIR}/windows-x86/protoc.exe")
else()
  message(
      FATAL_ERROR
      "No protoc prebuilt found for host OS ${CMAKE_HOST_SYSTEM_NAME}")
endif()
message(STATUS "Using prebuilt protoc at: ${Protobuf_PROTOC_PATH}")

# Compile libprotobuf
set(protobuf_BUILD_TESTS OFF CACHE BOOL "")
add_subdirectory("${Protobuf_SOURCE_DIR}/cmake" ${Protobuf_TARGET_BINARY_DIR})

# Compile libandroidicu
set(ICU_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../icu/libandroidicu")
set(ICU_TARGET_BINARY_DIR "${CMAKE_CURRENT_BINARY_DIR}/icu-target")
add_subdirectory("${ICU_SOURCE_DIR}/static_shim" ${ICU_TARGET_BINARY_DIR})

# Glob Icing proto sources. Results look like this: icing/proto/document.proto
file(
    GLOB_RECURSE
    Icing_PROTO_FILES
    RELATIVE "${CMAKE_CURRENT_SOURCE_DIR}/proto"
    "*.proto")
message(STATUS "Icing_PROTO_FILES=${Icing_PROTO_FILES}")


# Run protoc on Icing_PROTO_FILES to generate pb.cc and pb.h files
# The DEPENDS section of add_custom_command could trigger a remake if any proto
# source file has been updated.
file(MAKE_DIRECTORY ${Icing_PROTO_GEN_DIR})
foreach(FILE ${Icing_PROTO_FILES})
    # Find the name of the proto file without the .proto extension
    string(REGEX REPLACE "\.proto$" "" FILE_NOEXT ${FILE})
    list(APPEND Icing_PROTO_SOURCES
      "${Icing_PROTO_GEN_DIR}/${FILE_NOEXT}.pb.cc"
      "${Icing_PROTO_GEN_DIR}/${FILE_NOEXT}.pb.h")
    add_custom_command(
        OUTPUT "${Icing_PROTO_GEN_DIR}/${FILE_NOEXT}.pb.cc"
          "${Icing_PROTO_GEN_DIR}/${FILE_NOEXT}.pb.h"
        COMMAND ${Protobuf_PROTOC_PATH}
          --proto_path "${CMAKE_CURRENT_SOURCE_DIR}/proto"
          --cpp_out "lite:${Icing_PROTO_GEN_DIR}"
          ${FILE}
        WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
        DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/proto/${FILE}
    )
endforeach()
message(STATUS "Icing_PROTO_SOURCES=${Icing_PROTO_SOURCES}")

# Glob Icing C++ sources
# TODO: When supporting cmake v3.12 or higher, use CONFIGURE_DEPENDS in the glob
# below so that cmake knows when to re-generate the makefiles.
file(
    # List files recursively
    GLOB_RECURSE
    # Store into a variable of this name
    Icing_CC_SOURCES
    # Return paths that are relative to the project root
    RELATIVE ${CMAKE_CURRENT_SOURCE_DIR}
    # Glob expressions
    icing/*.cc icing/*.h
)

# TODO(b/170611579): When supporting cmake v3.12 or higher, use CONFIGURE_DEPENDS
# in the glob and remove this section.
include(synced_AOSP_CL_number.txt)

# Exclude the same types of files as Android.bp. See the comments there.
list(FILTER Icing_CC_SOURCES EXCLUDE REGEX "^icing/.*[^a-zA-Z0-9]test[^a-zA-Z0-9].*$")
list(FILTER Icing_CC_SOURCES EXCLUDE REGEX "^icing/.*_benchmark\.cc$")
list(FILTER Icing_CC_SOURCES EXCLUDE REGEX "^icing/helpers/icu/.*$")
list(FILTER Icing_CC_SOURCES EXCLUDE REGEX "^icing/testing/.*$")
list(FILTER Icing_CC_SOURCES EXCLUDE REGEX "^icing/tokenization/icu/.*$")
list(FILTER Icing_CC_SOURCES EXCLUDE REGEX "^icing/tokenization/simple/.*$")
list(FILTER Icing_CC_SOURCES EXCLUDE REGEX "^icing/tools/.*$")
list(FILTER Icing_CC_SOURCES EXCLUDE REGEX "^icing/transform/icu/.*$")
list(FILTER Icing_CC_SOURCES EXCLUDE REGEX "^icing/transform/simple/.*$")
message(STATUS "Icing_CC_SOURCES=${Icing_CC_SOURCES}")

add_library(
    # .so name
    icing

    # Shared or static
    SHARED

    # Provides a relative path to your source file(s).
    ${Icing_CC_SOURCES}
    ${Icing_PROTO_SOURCES}
)
target_include_directories(icing PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_include_directories(icing PRIVATE ${Icing_PROTO_GEN_DIR})
target_include_directories(icing PRIVATE "${Protobuf_SOURCE_DIR}/src")
target_include_directories(icing PRIVATE "${ICU_SOURCE_DIR}/include")
target_link_libraries(icing protobuf::libprotobuf-lite libandroidicu log z)
