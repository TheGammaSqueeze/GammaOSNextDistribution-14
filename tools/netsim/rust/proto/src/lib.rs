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

//! protobuf library for netsim
#[cfg(feature = "cuttlefish")]
use protobuf::well_known_types::empty;

pub mod common;
pub mod config;
pub mod configuration;
pub mod frontend;
// TODO: Remove feature check once crate dependency is resolved
#[cfg(feature = "cuttlefish")]
pub mod frontend_grpc;
pub mod hci_packet;
pub mod model;
pub mod packet_streamer;
pub mod startup;
pub mod stats;
