// Copyright 2023, The Android Open Source Project
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

#![cfg_attr(not(test), no_std)]

// Library error type.
#[derive(Debug)]
pub enum BootError {
    IntegerOverflow,
    InvalidInput,
    InvalidZImage,
    UnsupportedZImage,
    E820MemoryMapCallbackError(i64),
}

/// Library result type,
pub type Result<T> = core::result::Result<T, BootError>;

#[cfg(target_arch = "aarch64")]
pub mod aarch64;
#[cfg(target_arch = "riscv64")]
pub mod riscv64;
#[cfg(any(target_arch = "x86_64", target_arch = "x86"))]
pub mod x86;
