// Copyright 2021, The Android Open Source Project
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

#![allow(missing_docs)]
#![no_main]

use libfuzzer_sys::fuzz_target;

fn heap_oob() {
    let xs = [0, 1, 2, 3];
    // SAFETY: This is in fact intentionally unsound, but we hope the fuzzer will catch it.
    let val = unsafe { *xs.as_ptr().offset(4) };
    println!("Out-of-bounds heap value: {}", val);
}

fuzz_target!(|data: &[u8]| {
    let magic_number = 327;
    if data.len() == magic_number {
        heap_oob();
    }
});
