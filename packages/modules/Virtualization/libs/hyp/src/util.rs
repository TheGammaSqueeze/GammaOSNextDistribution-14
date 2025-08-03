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

//! Utility functions.

pub(crate) const SIZE_4KB: usize = 4 << 10;

/// Computes the low memory page address of the 4KiB page containing a given address.
pub(crate) fn page_address(addr: usize) -> u64 {
    (addr & !(SIZE_4KB - 1)).try_into().unwrap()
}
