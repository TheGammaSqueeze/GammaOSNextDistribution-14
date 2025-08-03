# Copyright 2022 The Android Open Source Project
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

cmake_minimum_required(VERSION 3.5)

set(ROOTCANAL_PROTO_DIR
    ${AOSP_ROOT}/packages/modules/Bluetooth/tools/rootcanal/proto)

# For netsimd (netsimd-proto-lib)
protobuf_generate_grpc_cpp(
  SOURCE_DIR ${ROOTCANAL_PROTO_DIR} SOURCES rootcanal/configuration.proto
  OUTPUT_DIR ${CMAKE_CURRENT_BINARY_DIR} GENERATED ROOTCANAL_PROTO_SRC)

# For netsimd (netsimd-proto-lib)
protobuf_generate_grpc_cpp(
  SOURCE_DIR ${CMAKE_CURRENT_LIST_DIR}
  SOURCES netsim/common.proto netsim/config.proto netsim/hci_packet.proto
          netsim/model.proto netsim/startup.proto netsim/stats.proto
  INCLUDES ${ROOTCANAL_PROTO_DIR}
  OUTPUT_DIR ${CMAKE_CURRENT_BINARY_DIR}
  GENERATED NETSIM_PROTO_SRC)

android_add_library(
  TARGET netsimd-proto-lib LICENSE Apache-2.0 SRC ${NETSIM_PROTO_SRC}
                                                  ${ROOTCANAL_PROTO_SRC}
  DEPS grpc++ protobuf::libprotobuf)

target_include_directories(netsimd-proto-lib PRIVATE ${PROTOBUF_INCLUDE_DIR} ..
                           PUBLIC ${CMAKE_CURRENT_BINARY_DIR})

# For netsim-cli (netsim-cli-proto-lib)
protobuf_generate_grpc_cpp(
  SOURCE_DIR ${CMAKE_CURRENT_LIST_DIR} SOURCES netsim/frontend.proto
  INCLUDES ${ROOTCANAL_PROTO_DIR} OUTPUT_DIR ${CMAKE_CURRENT_BINARY_DIR}
  GENERATED NETSIM_FRONTEND_GRPC_SRC)

android_add_library(
  TARGET netsim-cli-proto-lib LICENSE Apache-2.0 SRC ${NETSIM_FRONTEND_GRPC_SRC}
  DEPS grpc++ netsimd-proto-lib protobuf::libprotobuf)

target_include_directories(
  netsim-cli-proto-lib PRIVATE ${PROTOBUF_INCLUDE_DIR} ..
  PUBLIC ${CMAKE_CURRENT_BINARY_DIR})

# For network packet streamer client (packet-streamer-proto-lib)
protobuf_generate_grpc_cpp(
  SOURCE_DIR ${CMAKE_CURRENT_LIST_DIR} SOURCES netsim/packet_streamer.proto
  OUTPUT_DIR ${CMAKE_CURRENT_BINARY_DIR} INCLUDES ${ROOTCANAL_PROTO_DIR}
  GENERATED NETSIM_PACKET_STREAMER_GRPC_SRC)

android_add_library(
  TARGET packet-streamer-proto-lib LICENSE Apache-2.0
  SRC ${NETSIM_PACKET_STREAMER_GRPC_SRC} DEPS grpc++ netsimd-proto-lib
                                              protobuf::libprotobuf)

target_include_directories(
  packet-streamer-proto-lib PRIVATE ${PROTOBUF_INCLUDE_DIR} ..
  PUBLIC ${CMAKE_CURRENT_BINARY_DIR})

android_license(TARGET "packet-streamer-proto-lib" LIBNAME None SPDX None
                LICENSE None LOCAL None)
android_license(TARGET "netsim-cli-proto-lib" LIBNAME None SPDX None
                LICENSE None LOCAL None)
