// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Netsim daemon libraries.

mod args;
mod bluetooth;
pub mod captures;
mod config;
mod config_file;
mod devices;
mod echip;
mod events;
mod ffi;
mod http_server;
mod ranging;
mod resource;
mod rust_main;
mod service;
mod session;
mod transport;
mod version;
mod wifi;

// This feature is enabled only for CMake builds
#[cfg(feature = "local_ssl")]
mod openssl;
