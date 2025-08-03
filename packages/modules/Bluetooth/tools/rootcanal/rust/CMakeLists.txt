message(STATUS "Enabling bluetooth LMP module.")

pdl_gen(
    NAME hci_packets_rs
    INPUT ${ROOTCANAL_ROOT}/packets/hci_packets.pdl
    OUTPUT hci_packets.rs
    LANG rust)

pdl_gen(
    NAME lmp_packets_rs
    INPUT ${ROOTCANAL_ROOT}/rust/lmp_packets.pdl
    OUTPUT lmp_packets.rs
    LANG rust)

pdl_gen(
    NAME llcp_packets_rs
    INPUT ${ROOTCANAL_ROOT}/rust/llcp_packets.pdl
    OUTPUT llcp_packets.rs
    LANG rust)

# This will automatically register all the tests as well.
corrosion_import_crate(MANIFEST_PATH ${ROOTCANAL_ROOT}/rust/Cargo.toml FLAGS
                       --offline --verbose --verbose)
corrosion_set_env_vars(rootcanal-rs
  LMP_PACKETS_PREBUILT=${lmp_packets_rs}
  LLCP_PACKETS_PREBUILT=${llcp_packets_rs}
  HCI_PACKETS_PREBUILT=${hci_packets_rs}
  CARGO_HOME=${Rust_CARGO_HOME})

android_license(TARGET rootcanal-rs LIBNAME None SPDX None LICENSE None LOCAL None)

add_custom_target(lmp_rust_packets DEPENDS ${lmp_packets_rs}
                                           ${llcp_packets_rs}
                                           ${hci_packets_rs})

# Make sure we have the rust packets generated before we build them.
add_dependencies(cargo-build_rootcanal-rs lmp_rust_packets)

# Enable lmp in rootcanal.
target_link_libraries(libbt-rootcanal PUBLIC rootcanal-rs)
target_include_directories(libbt-rootcanal
    PUBLIC ${ROOTCANAL_ROOT}/rust/include)
