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

// [cfg(test)] gets compiled during local Rust unit tests
// [cfg(not(test))] avoids getting compiled during local Rust unit tests

#![allow(unused)]
mod beacon;
#[cfg(test)]
mod mocked;
pub(crate) use self::beacon::*;
#[cfg(test)]
pub(crate) use self::mocked::*;
pub(crate) mod advertise_data;
pub(crate) mod advertise_settings;
pub(crate) mod chip;
pub(crate) mod packets;
