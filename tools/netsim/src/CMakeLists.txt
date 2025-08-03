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
add_subdirectory(util)
if(NOT NETSIM_EXT)
  add_subdirectory(backend)
endif()

if(TARGET Rust::Rustc)
  set(cxx_bridge_binary_folder
      ${CMAKE_BINARY_DIR}/cargo/build/${Rust_CARGO_TARGET_CACHED}/cxxbridge)
  set(common_header ${cxx_bridge_binary_folder}/rust/cxx.h)
  set(cxx_bridge_source_file "src/ffi.rs")
  set(crate_name "netsim-cli")
  set(binding_header
      ${cxx_bridge_binary_folder}/${crate_name}/${cxx_bridge_source_file}.h)
  set(binding_source
      ${cxx_bridge_binary_folder}/${crate_name}/${cxx_bridge_source_file}.cc)

  # Make sure we have the cxx files generated before we build them.
  add_custom_command(OUTPUT ${common_header} ${binding_header} ${binding_source}
                     COMMAND DEPENDS ${crate_name}-static)

  android_add_library(
    TARGET frontend-client
    LICENSE Apache-2.0
    SRC ${binding_header} ${binding_source} ${common_header}
        frontend/frontend_client.cc frontend/frontend_client.h
    DEPS grpc++ netsim-cli-proto-lib protobuf::libprotobuf util-lib)
  target_include_directories(frontend-client PRIVATE .
                             PUBLIC ${cxx_bridge_binary_folder})

  set(cxx_bridge_binary_folder
      ${CMAKE_BINARY_DIR}/cargo/build/${Rust_CARGO_TARGET_CACHED}/cxxbridge)
  set(common_header ${cxx_bridge_binary_folder}/rust/cxx.h)
  set(cxx_bridge_source_file "src/ffi.rs")
  set(crate_name "netsim-daemon")
  set(binding_header
      ${cxx_bridge_binary_folder}/${crate_name}/${cxx_bridge_source_file}.h)
  set(binding_source
      ${cxx_bridge_binary_folder}/${crate_name}/${cxx_bridge_source_file}.cc)

  # Make sure we have the cxx files generated before we build them.
  add_custom_command(OUTPUT ${common_header} ${binding_header} ${binding_source}
                     COMMAND DEPENDS cargo-build_${crate_name})

  android_add_library(
    TARGET netsimd-lib
    LICENSE Apache-2.0
    SRC ${binding_header}
        ${binding_source}
        ${common_header}
        backend/backend_packet_hub.h
        backend/grpc_server.cc
        backend/grpc_server.h
        core/server.cc
        core/server.h
        frontend/frontend_client_stub.cc
        frontend/frontend_client_stub.h
        frontend/frontend_server.cc
        frontend/frontend_server.h
        frontend/server_response_writable.h
        hci/async_manager.cc
        hci/bluetooth_facade.cc
        hci/bluetooth_facade.h
        hci/hci_packet_transport.cc
        hci/hci_packet_transport.h
        hci/rust_device.cc
        hci/rust_device.h
        wifi/wifi_facade.cc
        wifi/wifi_facade.h
        wifi/wifi_packet_hub.h
    DEPS android-emu-base
         android-emu-base-headers
         android-wifi-service
         grpc++
         libbt-rootcanal
         netsimd-proto-lib
         packet-streamer-proto-lib
         protobuf::libprotobuf
         util-lib)
  target_include_directories(netsimd-lib PRIVATE . ${PROTOBUF_INCLUDE_DIR}
                             PUBLIC ${cxx_bridge_binary_folder})
  target_compile_definitions(netsimd-lib PUBLIC NETSIM_ANDROID_EMULATOR)
  # Make sure we have the cxx files generated before we build them.
  add_dependencies(netsimd-lib cargo-build_netsim-daemon)
endif()
