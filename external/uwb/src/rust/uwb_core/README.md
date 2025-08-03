# Development with Cargo

Although this library are built by Android build system officially, we can also
build and test the library by cargo.

## Building `uwb_uci_packets` package

The `uwb_uci_packets` package depends on `pdlc and thus simply using `cargo
build` will fail. Follow the steps below before using cargo.

1. Enter Android environment by `source build/make/rbesetup.sh; lunch <target>`
2. Run `m pdlc` to compile the `pdlc` Rust binary.

After that, we could build or test the package by `cargo test --features proto`.

## Enable logging for a certain test case of uwb\_core library

When debugging a certain test case, we could enable the logging and run the
single test case.

1. Add `crate::utils::init_test_logging();` at the beginning of the test case
2. Run the single test case by:
```
RUST_LOG=debug cargo test -p uwb_core <test_case_name> -- --nocapture
```

# Code Architecture

This section describes the main modules of this library. The modules below are
listed in reversed topological order.

## The uwb\_uci\_packets crate

The `uwb_uci_packets` crate is aimed for encoding and decoding the UCI packets.
All the details of the UCI packet format should be encapsulated here. That
means, the client of this crate should not be aware of how the UCI messages are
constructed to or parsed from raw byte buffers.

The crate is mainly generated from the PDL file. However, in the case where a
UCI feature cannot be achieved using PDL alone, a workaround should be created
inside this crate to complete this feature (i.e. define structs and implement
the parsing methods manually) to encapsulate the details of UCI packet format.

Note that the interface of the workaround should be as close to PDL-generated
code as possible.


## params

The params modules defines the parameters types, including UCI, FiRa, and CCC
specification.

This module depends on the `uwb_uci_packets` crate. To prevent the client of
this module directly depending on the `uwb_uci_packets` crate, we re-expose all
the needed enums and structs at `params/uci_packets.rs`.

## UCI

The `uci` module is aimed to provide a rust-idiomatic way that implements the
UCI interface, such as:
- Declare meaningful arguments types
- Create a public method for each UCI command, and wait for its corresponding
  response
- Create a callback method for each UCI notification

According to the asynchronous nature of the UCI interface, the `UciManager`
struct provides asynchronous methods using the actor model. For easier usage,
the `UciManagerSync` struct works as a thin synchronous wrapper.

This module depends on the `params` module.

## Session

The `session` module implements the ranging session-related logic. We support
the FiRa and CCC specification here.

This module depends on the `params` and `UCI` modules.

## service

The `service` module is aimed to provide a "top-shim", the main entry of this
library. Similar to the `UciManagerSync`, the `UwbService` struct provides a
simple synchronous interface to the client of the library. `examples/main.rs` is
a simple example for using the `UwbService` struct.

If we want to provide the UWB across the process or language boundary, then
`ProtoUwbService` provices a simple wrapper that converts all the arguments and
responses to protobuf-encoded byte buffers.

The `service` module depends on `params`, `uci`, and `session` modules.
