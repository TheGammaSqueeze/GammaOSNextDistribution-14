message(STATUS "Adding rust ${CMAKE_CURRENT_LIST_DIR}/Cargo.toml")

corrosion_set_env_vars(netsim-proto RUST_BACKTRACE=full)

cmake_path(APPEND PROTOBUF_INCLUDE_DIR "${AOSP_ROOT}" "external" "protobuf"
           "src")
cmake_path(CONVERT ${PROTOBUF_INCLUDE_DIR} TO_NATIVE_PATH_LIST
           NATIVE_PROTOBUF_INCLUDE_DIR)

corrosion_set_env_vars(netsim-proto
                       PROTOBUF_INCLUDE_DIR=${NATIVE_PROTOBUF_INCLUDE_DIR})

# Set CMake executable filepath because it's not found by cmake Rust crate. From
# https://github.com/rust-lang/cmake-rs, the CMake executable is assumed to be
# cmake unless the CMAKE environmental variable is set.

corrosion_set_env_vars(netsim-proto CMAKE=${CMAKE_COMMAND})

if(LINUX)
  # b/260271048
  corrosion_add_target_rustflags(
    netsim-proto "-Clink-args=-Wl,-rpath,${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
endif()
