message(STATUS "Adding rust ${CMAKE_CURRENT_LIST_DIR}/Cargo.toml")

# Delete any old Cargo.lock files
file(REMOVE ${CMAKE_CURRENT_LIST_DIR}/Cargo.lock)

# This will automatically register all the tests as well.
corrosion_import_crate(MANIFEST_PATH Cargo.toml FLAGS --offline --verbose)
corrosion_set_env_vars(netsim-common CARGO_HOME=${Rust_CARGO_HOME})
corrosion_set_env_vars(
  netsim-daemon
  LINK_LAYER_PACKETS_PREBUILT=${RootCanalGeneratedPackets_rs}
  NETLINK_PACKETS_PREBUILT=${NetlinkPackets_rs}
  MAC80211_HWSIM_PACKETS_PREBUILT=${Mac80211HwsimPackets_rs}
  IEEE80211_PACKETS_PREBUILT=${Ieee80211Packets_rs}
  CARGO_HOME=${Rust_CARGO_HOME})

corrosion_set_env_vars(netsim-cli CARGO_HOME=${Rust_CARGO_HOME})
corrosion_set_env_vars(netsim-proto CARGO_HOME=${Rust_CARGO_HOME})

add_custom_target(
  netsim_rust_packets
  DEPENDS pdl_gen-RootCanalGeneratedPackets_rs pdl_gen-NetlinkPackets_rs
          pdl_gen-Ieee80211Packets_rs pdl_gen-Mac80211HwsimPackets_rs)

# Make sure we have the rust packets generated before we build them.
add_dependencies(cargo-build_netsim-daemon netsim_rust_packets)

# cxx crates
if(WIN32)
  # 1. Set CMAKE_CXX_FLAGS, as it is not set in Rust crates imported by
  #    corrosion.
  # 2. Set RUST_CXX_NO_EXCEPTIONS preprocesor definition for cxx crate to disable
  #    compilation error message of "cannot use 'throw' with exceptions".
  # 3. MSVC uses environment variable "CL" to prepend arguments to the
  #    command-line arguments. However, we verified MSVC also supports CXXFLAGS,
  #    which is used by cc crate.
  # 4. This isn't needed in mac and Linux because corrosion doesn't pass
  #    -fno-exception flag.
  set(CXXFLAGS "${CMAKE_CXX_FLAGS} /DRUST_CXX_NO_EXCEPTIONS")
  corrosion_set_env_vars(netsim-cli CXXFLAGS=${CXXFLAGS})
  corrosion_set_env_vars(netsim-daemon CXXFLAGS=${CXXFLAGS})
endif()

if(LINUX)
  # b/260271048
  set(RUSTFLAGS "-Clink-args=-Wl,-rpath,${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
  corrosion_add_target_rustflags(netsim-cli ${RUSTFLAGS})
  corrosion_add_target_rustflags(netsim-daemon ${RUSTFLAGS})
endif()

add_subdirectory(proto)

# Steps to generate protobuf -> rust files are as follows:
#
# 1. Install protobuf for protoc binary.
#
# Linux: sudo apt-get install protobuf-compiler
#
# Mac: brew install protobuf
#
# 1. Uncomment the following lines in rust/proto/Cargo.toml. build =
#    "build_cargo.rs" protobuf = "=3.2.0" protobuf-codegen = "3.2.0"
# 2. export CARGO_HOME=$REPO_EMU/tools/netsim/objs/rust/.cargo
# 3. cd $REPO_EMU/tools/netsim
# 4. cargo build --manifest-path rust/proto/Cargo.toml
# 5. git checkout rust/proto/Cargo.toml
# 6. bash scripts/format_code.sh

android_license(TARGET "netsim-daemon" LIBNAME None SPDX None LICENSE None
                LOCAL None)
android_license(TARGET "netsim-proto" LIBNAME None SPDX None LICENSE None
                LOCAL None)

add_library(netsim-cli-rust-lib INTERFACE)

target_link_libraries(netsim-cli-rust-lib INTERFACE netsim-proto netsim-cli)

android_license(TARGET netsim-cli-rust-lib LIBNAME None SPDX None LICENSE None
                LOCAL None)
