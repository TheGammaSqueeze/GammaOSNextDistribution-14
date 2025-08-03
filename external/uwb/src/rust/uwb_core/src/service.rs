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

//! This module provides the public interface of the UWB core library.

#[cfg(feature = "proto")]
pub mod proto_uwb_service;
pub mod uwb_service;
pub mod uwb_service_builder;
pub mod uwb_service_callback_builder;

#[cfg(test)]
mod mock_uwb_service_callback;

// Re-export the public elements.
#[cfg(feature = "proto")]
pub use proto_uwb_service::{ProtoUwbService, ProtoUwbServiceCallback};
pub use uwb_service::{
    NopUwbServiceCallback, UwbService, UwbServiceCallback, UwbServiceCallbackBuilder,
};
pub use uwb_service_builder::{default_runtime, UwbServiceBuilder};
pub use uwb_service_callback_builder::UwbServiceCallbackSendBuilder;
