# SMCCC and PSCI functions for bare-metal Rust on aarch64

[![crates.io page](https://img.shields.io/crates/v/smccc.svg)](https://crates.io/crates/smccc)
[![docs.rs page](https://docs.rs/smccc/badge.svg)](https://docs.rs/smccc)

This crate provides support for the Arm SMC Calling Convention version 1.4, including standard Arm
Architecture Calls constants, and version 1.1 of the Arm Power State Coordination Interface (PSCI).
It includes constants, functions to make the calls (on aarch64 targets), and error types.

Note that the PSCI and SMCCC arch calls may be made via either HVC or SMC. You can choose which one
to use by passing either `Hvc` or `Smc` as a type parameter to the relevant function.

This crate currently only supports aarch64 and the SMC64 versions of the PSCI calls, in the cases
that both SMC32 and SMC64 versions exist.

This is not an officially supported Google product.

## License

Licensed under either of

- Apache License, Version 2.0
  ([LICENSE-APACHE](LICENSE-APACHE) or http://www.apache.org/licenses/LICENSE-2.0)
- MIT license
  ([LICENSE-MIT](LICENSE-MIT) or http://opensource.org/licenses/MIT)

at your option.

## Contributing

If you want to contribute to the project, see details of
[how we accept contributions](CONTRIBUTING.md).
