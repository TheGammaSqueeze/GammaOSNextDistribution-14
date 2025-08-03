set(BT_ROOT ${AOSP_ROOT}/packages/modules/Bluetooth)
set(ROOTCANAL_ROOT ${AOSP_ROOT}/packages/modules/Bluetooth/tools/rootcanal)
set(PDL_ROOT ${AOSP_ROOT}/external/rust/crates/pdl-compiler)

corrosion_import_crate(
    MANIFEST_PATH ${PDL_ROOT}/Cargo.toml
    FLAGS --offline --verbose --verbose)

corrosion_set_env_vars(generate-canonical-tests CARGO_HOME=${Rust_CARGO_HOME})
corrosion_set_env_vars(pdlc CARGO_HOME=${Rust_CARGO_HOME})
corrosion_set_hostbuild(pdlc)

get_property(pdlc_EXECUTABLE TARGET pdlc PROPERTY EXECUTABLE_PATH)

# These tests depend on the tempfile crate which was not imported because
# the crate remove_dir_all does not have a compatible version.
set_tests_properties(cargo-test_pdlc PROPERTIES DISABLED True)
set_tests_properties(cargo-test_generate-canonical-tests PROPERTIES DISABLED True)

android_license(
    TARGET pdlc
    LIBNAME None
    SPDX None
    LICENSE None
    LOCAL None)

# Generate the Rust/C++ backend for a .pdl specification file.
function(pdl_gen)
  # Parse arguments.
  set(options)
  set(oneValueArgs NAME INPUT OUTPUT LANG NAMESPACE)
  set(multiValueArgs USING INCLUDE)
  cmake_parse_arguments(pdl "${options}" "${oneValueArgs}"
                        "${multiValueArgs}" ${ARGN})

  if(NOT pdl_NAME)
    message(FATAL_ERROR "Error: name not specified")
  endif()

  if(NOT pdl_INPUT)
    message(FATAL_ERROR "Error: output file not specified")
  endif()

  if(NOT pdl_OUTPUT)
    message(FATAL_ERROR "Error: output file not specified")
  endif()

  set(pdl_OUTPUT_DIR ${CMAKE_CURRENT_BINARY_DIR}/pdl_gen)
  set(pdl_OUTPUT "${pdl_OUTPUT_DIR}/${pdl_OUTPUT}")

  get_filename_component(pdl_INPUT_ABSOLUTE ${pdl_INPUT} ABSOLUTE)
  get_filename_component(pdl_OUTPUT_ABSOLUTE ${pdl_OUTPUT} ABSOLUTE)
  get_filename_component(pdl_OUTPUT_DIR ${pdl_OUTPUT_ABSOLUTE} DIRECTORY)
  set(${pdl_NAME} "${pdl_OUTPUT_ABSOLUTE}" CACHE STRING "PDL output filepath for ${pdl_NAME}" FORCE)

  file(MAKE_DIRECTORY ${pdl_OUTPUT_DIR})

  if(pdl_LANG STREQUAL "rust")
    add_custom_command(
        OUTPUT "${pdl_OUTPUT_ABSOLUTE}"
        COMMAND
        ${pdlc_EXECUTABLE}
                --output-format rust
                "${pdl_INPUT_ABSOLUTE}"
                    > "${pdl_OUTPUT_ABSOLUTE}"
        COMMENT "Generating rust module from ${pdl_INPUT}"
        VERBATIM
        DEPENDS pdlc ${pdl_INPUT_ABSOLUTE})
  endif()

  if(pdl_LANG STREQUAL "c++")
    if(NOT pdl_NAMESPACE)
        message(FATAL_ERROR "Error: namespace not specified")
    endif()

    foreach(namespace ${pdl_USING})
        list(APPEND pdl_FLAGS --using-namespace)
        list(APPEND pdl_FLAGS ${namespace})
    endforeach()
    foreach(header ${pdl_INCLUDE})
        list(APPEND pdl_FLAGS --include-header)
        list(APPEND pdl_FLAGS ${header})
    endforeach()

    add_custom_command(
        OUTPUT "${pdl_OUTPUT_ABSOLUTE}.json"
        COMMAND
        ${pdlc_EXECUTABLE}
                --output-format json
                "${pdl_INPUT_ABSOLUTE}"
                    > "${pdl_OUTPUT_ABSOLUTE}.json"
        COMMENT "Analyzing ${pdl_INPUT}"
        VERBATIM
        DEPENDS pdlc ${pdl_INPUT_ABSOLUTE})

    add_custom_command(
        OUTPUT "${pdl_OUTPUT_ABSOLUTE}"
        COMMAND
        ${PDL_ROOT}/scripts/generate_cxx_backend.py
                --input "${pdl_OUTPUT_ABSOLUTE}.json"
                --output "${pdl_OUTPUT_ABSOLUTE}"
                --namespace ${pdl_NAMESPACE}
                ${pdl_FLAGS}
        COMMENT "Generating c++ header from ${pdl_INPUT}"
        VERBATIM
        DEPENDS pdlc ${pdl_OUTPUT_ABSOLUTE}.json)
  endif()

  add_custom_target("pdl_gen-${pdl_NAME}" DEPENDS ${pdl_OUTPUT_ABSOLUTE})
endfunction()

pdl_gen(
  NAME BluetoothGeneratedPackets_h
  INPUT ${ROOTCANAL_ROOT}/packets/hci_packets.pdl
  OUTPUT packets/hci_packets.h
  LANG c++
  NAMESPACE "bluetooth::hci"
  INCLUDE "hci/address.h")

pdl_gen(
  NAME RootCanalGeneratedPackets_h
  INPUT ${ROOTCANAL_ROOT}/packets/link_layer_packets.pdl
  OUTPUT packets/link_layer_packets.h
  LANG c++
  NAMESPACE model::packets
  INCLUDE "hci/address.h"
  USING "bluetooth::hci")

pdl_gen(
  NAME RootCanalGeneratedPackets_rs
  INPUT ${ROOTCANAL_ROOT}/packets/link_layer_packets.pdl
  OUTPUT link_layer_packets.rs
  LANG rust)

android_add_library(
  TARGET libscriptedbeaconpayload-protos-lite LICENSE Apache-2.0
  SOURCE_DIR ${ROOTCANAL_ROOT} SRC ${libscriptedbeaconpayload_protos_lite_src})

protobuf_generate_with_plugin(
  TARGET libscriptedbeaconpayload-protos-lite
  PROTOS ${ROOTCANAL_ROOT}/model/devices/scripted_beacon_ble_payload.proto
  APPEND_PATH
  PROTOPATH -I${AOSP_ROOT}/external/protobuf/src
            -I${ROOTCANAL_ROOT}/model/devices
  PROTOC_OUT_DIR ${CMAKE_CURRENT_BINARY_DIR}/model/devices)

target_include_directories(
  libscriptedbeaconpayload-protos-lite
  PUBLIC ${CMAKE_CURRENT_BINARY_DIR} ${AOSP_ROOT}/external/protobuf/src)

target_link_libraries(libscriptedbeaconpayload-protos-lite
                      PRIVATE protobuf::libprotobuf)

android_add_library(
  TARGET librootcanal_config LICENSE Apache-2.0
  SOURCE_DIR ${ROOTCANAL_ROOT} SRC ${librootcanal_config_src})

protobuf_generate_with_plugin(
  TARGET librootcanal_config
  PROTOS ${ROOTCANAL_ROOT}/proto/rootcanal/configuration.proto
  APPEND_PATH
  PROTOPATH -I${AOSP_ROOT}/external/protobuf/src
  PROTOC_OUT_DIR ${CMAKE_CURRENT_BINARY_DIR}/proto/rootcanal)

target_include_directories(
  librootcanal_config
  PUBLIC ${CMAKE_CURRENT_BINARY_DIR}/proto ${AOSP_ROOT}/external/protobuf/src)

target_link_libraries(librootcanal_config
                      PRIVATE protobuf::libprotobuf)

add_library(libbt-rootcanal.headers INTERFACE)
target_include_directories(libbt-rootcanal.headers INTERFACE ${ROOTCANAL_ROOT})
target_link_libraries(libbt-rootcanal.headers
                      INTERFACE android-emu-base-headers)
android_license(TARGET "libbt-rootcanal.headers" LIBNAME None SPDX Apache-2.0
                LICENSE Apache-2.0 LOCAL "${BT_ROOT}/NOTICE")

android_add_library(
  TARGET librootcanal_log
  LICENSE Apache-2.0
  SOURCE_DIR ${ROOTCANAL_ROOT}
  SRC lib/log.cc
      ${AOSP_ROOT}/external/fmtlib/src/format.cc)

target_include_directories(
  librootcanal_log PUBLIC
    ${ROOTCANAL_ROOT}/include
    ${AOSP_ROOT}/external/fmtlib/include)

android_add_library(
  TARGET libbt-rootcanal
  LICENSE Apache-2.0
  SOURCE_DIR ${ROOTCANAL_ROOT}
  SRC ${BluetoothGeneratedPackets_h}
      ${RootCanalGeneratedPackets_h}
      lib/crypto/crypto.cc
      lib/hci/address.cc
      lib/hci/pcap_filter.cc
      lib/log.cc
      model/controller/acl_connection.cc
      model/controller/acl_connection_handler.cc
      model/controller/controller_properties.cc
      model/controller/dual_mode_controller.cc
      model/controller/le_advertiser.cc
      model/controller/link_layer_controller.cc
      model/controller/sco_connection.cc
      model/controller/vendor_commands/le_apcf.cc
      model/devices/beacon.cc
      model/devices/beacon_swarm.cc
      model/devices/device.cc
      model/devices/hci_device.cc
      model/devices/link_layer_socket_device.cc
      model/devices/scripted_beacon.cc
      model/devices/sniffer.cc
      model/hci/h4_data_channel_packetizer.cc
      model/hci/h4_parser.cc
      model/hci/hci_sniffer.cc
      model/hci/hci_socket_transport.cc
      model/setup/device_boutique.cc
      model/setup/phy_device.cc
      model/setup/phy_layer.cc
      model/setup/test_channel_transport.cc
      model/setup/test_command_handler.cc
      model/setup/test_model.cc
  LINUX net/posix/posix_async_socket.cc
        net/posix/posix_async_socket_connector.cc
        net/posix/posix_async_socket_server.cc
  DARWIN net/posix/posix_async_socket.cc
         net/posix/posix_async_socket_connector.cc
         net/posix/posix_async_socket_server.cc
  DEPS android-emu-base
       android-emu-base-headers
       android-emu-base-logging
       crypto
       librootcanal_config
       libscriptedbeaconpayload-protos-lite)

target_link_libraries(
  libbt-rootcanal
  PUBLIC librootcanal_log)

target_include_directories(
  libbt-rootcanal
  PUBLIC ${ROOTCANAL_ROOT}/include
         ${ROOTCANAL_ROOT}
         ${PDL_ROOT}/scripts
         ${CMAKE_CURRENT_BINARY_DIR}/pdl_gen
         ${CMAKE_CURRENT_BINARY_DIR}/config)

target_compile_options(libbt-rootcanal
                       PUBLIC -Wno-inconsistent-missing-override)

add_subdirectory(rust)
