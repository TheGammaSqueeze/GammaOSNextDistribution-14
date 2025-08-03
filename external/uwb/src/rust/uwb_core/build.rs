// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use std::fs::File;
use std::io::Write;
use std::path::PathBuf;

/// Generate the protobuf bindings inside the uwb_core library.
///
/// The protobuf are mainly used to represent the elements of uwb_uci_packets. If we use
/// Android's rust_protobuf build target to split the protobuf bindings to a dedicated crate, then
/// we cannot implement the conversion trait (i.e. std::convert::From) between the protobuf
/// bindings and the uwb_uci_packets's elements due to Rust's orphan rule.
fn generate_proto_bindings() {
    let out_dir = std::env::var_os("OUT_DIR").unwrap();

    // Generate the protobuf bindings to "${OUT_DIR}/uwb_service.rs".
    protoc_rust::Codegen::new()
        .out_dir(&out_dir)
        .input("./protos/uwb_service.proto")
        .run()
        .expect("Running protoc failed.");

    // Including the protobuf bindings directly hits the issue:
    // "error: an inner attribute is not permitted in this context".
    //
    // To workaround this, first we create the file "${OUT_DIR}/proto_bindings.rs" that contains
    // ```
    // #[path = "${OUT_DIR}/uwb_service.rs"]
    // pub mod bindings;
    // ```
    //
    // Then include the generated file at proto.rs by:
    // ```
    // include!(concat!(env!("OUT_DIR"), "/proto_bindings.rs"));
    // ```
    let file_path = PathBuf::from(&out_dir).join("proto_bindings.rs");
    let file = File::create(file_path).expect("Failed to create the generated file");
    writeln!(&file, "#[path = \"{}/uwb_service.rs\"]", out_dir.to_str().unwrap())
        .expect("Failed to write to the generated file");
    writeln!(&file, "pub mod bindings;").expect("Failed to write to the generated file");
}

fn main() {
    if std::env::var("CARGO_FEATURE_PROTO") == Ok("1".to_string()) {
        generate_proto_bindings();
    }
}
