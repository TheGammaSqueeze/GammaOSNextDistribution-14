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

//! JNI crate connecting UWB core service written in rust with Android's adaptation for the UWB service
//! written in java.

// temporary while developing this lib
#![allow(unused_variables)]

mod callback;
mod context;
mod error;
mod object_mapping;
mod unique_jvm;

pub mod api;
