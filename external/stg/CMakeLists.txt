# SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
#
# Copyright 2023 Google LLC
#
# Licensed under the Apache License v2.0 with LLVM Exceptions (the
# "License"); you may not use this file except in compliance with the
# License.  You may obtain a copy of the License at
#
#     https://llvm.org/LICENSE.txt
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Author: Aleksei Vetrov

cmake_minimum_required(VERSION 3.14)

project(
  stg
  VERSION 0.0.1
  LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)

add_compile_options(-fstrict-enums -Wall -Wextra)

set(MINIMUM_GNU_VERSION 11)
set(MINIMUM_Clang_VERSION 15)

# Note, the quotes around the variable are significant. If we use a compiler
# that does not resolve to a definition above, the empty string corresponds to
# a version where all components are omitted and hence treated as zero.
if(CMAKE_CXX_COMPILER_VERSION VERSION_LESS "${MINIMUM_${CMAKE_CXX_COMPILER_ID}_VERSION}")
  message(FATAL_ERROR "Unsupported Compiler Version!\n"
          "Need at least ${CMAKE_CXX_COMPILER_ID} ${MINIMUM_${CMAKE_CXX_COMPILER_ID}_VERSION}")
endif()

if (CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
  # GCC has problems detecting "no-return" switches and destructors.
  add_compile_options(-Wno-return-type)
endif()

# Enable LTO for release builds
set(CMAKE_INTERPROCEDURAL_OPTIMIZATION_RELEASE TRUE)
set(CMAKE_INTERPROCEDURAL_OPTIMIZATION_RELWITHDEBINFO TRUE)

list(APPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake)

find_package(LibElf REQUIRED)
find_package(LibDw REQUIRED)
find_package(LibXml2 REQUIRED)
find_package(Protobuf REQUIRED)

if(NOT Protobuf_PROTOC_EXECUTABLE)
  message(FATAL_ERROR "Could NOT find protobuf::protoc.
Please install protobuf-compiler or set Protobuf_PROTOC_EXECUTABLE to the location of the \"protoc\" binary.")
endif()

set(COMMON_LIBRARIES
    LibElf::LibElf
    LibDw::LibDw
    LibXml2::LibXml2
    protobuf::libprotobuf)

if(NOT Jemalloc_DISABLE)
  find_package(Jemalloc)
  if(Jemalloc_FOUND)
    list(APPEND COMMON_LIBRARIES Jemalloc::Jemalloc)
  else()
    message(WARNING "jemalloc significantly improves performance, but is not functionally required to build STG.
Use -DJemalloc_DISABLE=TRUE to disable jemalloc and suppress this warning.")
  endif()
endif()

protobuf_generate_cpp(PROTO_SRCS PROTO_HDRS stg.proto)

add_library(libstg OBJECT
  abigail_reader.cc
  btf_reader.cc
  comparison.cc
  deduplication.cc
  dwarf_processor.cc
  dwarf_wrappers.cc
  elf_loader.cc
  elf_reader.cc
  fidelity.cc
  file_descriptor.cc
  filter.cc
  fingerprint.cc
  graph.cc
  input.cc
  naming.cc
  post_processing.cc
  proto_reader.cc
  proto_writer.cc
  reporting.cc
  runtime.cc
  stable_hash.cc
  type_normalisation.cc
  type_resolution.cc
  unification.cc
  ${PROTO_SRCS}
  ${PROTO_HDRS})
# Needed for generated .pb.h files
target_include_directories(libstg PRIVATE ${CMAKE_CURRENT_BINARY_DIR})
target_link_libraries(libstg PUBLIC ${COMMON_LIBRARIES})

set(STG_EXECUTABLE_TARGETS stg stgdiff)

foreach(TARGET IN LISTS STG_EXECUTABLE_TARGETS)
  add_executable("${TARGET}" "${TARGET}.cc")
  target_link_libraries("${TARGET}" PRIVATE libstg)
endforeach()

# Testing

find_package(Catch2 QUIET)

if(NOT Catch2_FOUND)

  message(NOTICE "Catch2 testing framework not found. Disabling tests.")

else()
  enable_testing()

  add_custom_command(OUTPUT testdata
    COMMAND ${CMAKE_COMMAND} -E create_symlink
            ${CMAKE_SOURCE_DIR}/testdata/
            ${CMAKE_BINARY_DIR}/testdata
  )
  add_custom_target(testdata_symlink DEPENDS testdata)

  set(TEST_FILES
    abigail_reader_test
    elf_reader_test
    error_test
    file_descriptor_test
    filter_test
    order_test
    reporting_test
    runtime_test
    scc_test
    stgdiff_test
  )

  foreach(test_file ${TEST_FILES})
    add_executable(${test_file} ${test_file}.cc)
    target_link_libraries(${test_file} Catch2::Catch2WithMain libstg)
    add_test(NAME ${test_file} COMMAND ${test_file})
    add_dependencies(${test_file} testdata_symlink)
  endforeach()

endif()  # Catch2_FOUND

# Installation and packaging

include(GNUInstallDirs)

install(
  TARGETS ${STG_EXECUTABLE_TARGETS}
  COMPONENT Binaries
  RUNTIME DESTINATION "${CMAKE_INSTALL_BINDIR}")
