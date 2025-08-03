# Copyright 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

# Netsim - a network simulator for discovery, ranging and communication

project(netsim)
cmake_minimum_required(VERSION 3.5)
cmake_policy(SET CMP0079 NEW)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED True)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_LIST_DIR}/cmake")

if(NOT ANDROID_EMULATOR_BUILD)
  message(STATUS "Building netsim standalone.")
  include(netsim_dependencies)
endif()

if(TARGET Rust::Rustc)
  add_subdirectory(pdl)
  add_subdirectory(rust)
else()
  message(WARNING "Only building client side dependencies.")
endif()

add_subdirectory(proto)
add_subdirectory(src)

if(TARGET Rust::Rustc)
  android_add_executable(
    TARGET netsim LICENSE Apache-2.0 INSTALL . SRC rust/netsim.cc
    DEPS frontend-client grpc++ netsim-cli-proto-lib netsim-cli-rust-lib
         protobuf::libprotobuf util-lib)

  android_add_executable(
    TARGET netsimd
    LICENSE Apache-2.0 INSTALL .
    SRC rust/netsimd.cc
    DEPS grpc++
         libbt-rootcanal
         netsim-cli-proto-lib
         netsim-daemon
         netsimd-lib
         netsimd-proto-lib
         packet-streamer-proto-lib)

  if(NOT DARWIN_AARCH64 AND NOT DARWIN_X86_64)
    # Prevent duplicate symbol for cxx Rust crate.
    target_link_libraries(netsimd PRIVATE -Wl,--allow-multiple-definition)
  endif()

  android_target_dependency(netsimd linux TCMALLOC_OS_DEPENDENCIES)

  android_add_test(
    TARGET netsim-test LICENSE Apache-2.0
    SRC src/util/ini_file_test.cc src/util/os_utils_test.cc
        src/util/string_utils_test.cc src/wifi/wifi_facade_test.cc
    DEPS android-emu-base-headers
         grpc++
         gtest
         gtest_main
         libbt-rootcanal
         netsim-cli-proto-lib
         netsim-daemon
         netsim-proto
         netsimd-lib
         netsimd-proto-lib
         protobuf::libprotobuf
         util-lib)

  target_compile_definitions(netsim-test PUBLIC NETSIM_ANDROID_EMULATOR)
  target_include_directories(netsim-test PRIVATE src)

  # Link NtDll to netsim executables.
  if(WIN32)
    target_link_libraries(netsim PRIVATE ntdll)
    target_link_libraries(netsimd PRIVATE ntdll)
    target_link_libraries(netsim-test PRIVATE ntdll)
    android_license(TARGET "ntdll" LIBNAME None SPDX None LICENSE None
                    LOCAL None)
  endif()
endif()

if(NOT NETSIM_EXT)
  android_add_executable(
    TARGET netsim-packet-streamer-client
    LICENSE Apache-2.0
    SRC src/netsim-packet-streamer-client.cc
    DEPS grpc++ packet-streamer-client-lib packet-streamer-proto-lib
         protobuf::libprotobuf)
endif()

add_subdirectory(ui)
