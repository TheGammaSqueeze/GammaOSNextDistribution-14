Rust build
======

Currently, the Rust components are built differently on Android vs Linux. We are
missing Rust support in our GN toolchain so we currently build the Rust
libraries as a staticlib and link in C++. This may change in the future once we
have better support.

For now, you can build all of the Rust code using Cargo.

There are some dependencies:
* You must have the protobuf-compiler package installed
* You must have a recent version of Cargo + Rust

You should use `build.py` at the root to do your Rust builds so that it
correctly points your dependencies towards the vendored crates and sets your
$CARGO_HOME to the correct location.

### Building `packets` package
This package depends on `bluetooth_packetgen` and thus simply using
`cargo build` will fail. Follow the steps below to ensure the dependency is
found in `$CARGO_HOME/bin`.

1. Run `m -j32 bluetooth_packetgen` to compile `bluetooth_packetgen` c++ binary.
2. Change directory to `$CARGO_HOME/bin`.
3. Create a symlink in `$CARGO_HOME/bin` to compiled `bluetooth_packetgen`.
`ln -s ~/aosp/out/host/linux-x86/bin/bluetooth_packetgen bluetooth_packetgen`

### Enable GD Rust
1. `adb shell device_config put bluetooth INIT_gd_rust true`
2. Restart the device
