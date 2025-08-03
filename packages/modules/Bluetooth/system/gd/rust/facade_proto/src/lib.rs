//
//  Copyright 2021 Google, Inc.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

// protoc-rust and protoc-grpcio generates all modules and exports them in mod.rs
// We have to include them all here to make them available for crate export.
include!(concat!(env!("OUT_DIR"), "/proto_out/mod.rs"));
include!(concat!(env!("OUT_DIR"), "/grpc_out/mod.rs"));

// empty.proto is missing so add a workaround
// See github.com/stepancheg/grpc-rust/issues/156
pub mod empty {
    pub use protobuf::well_known_types::empty::Empty;
}
