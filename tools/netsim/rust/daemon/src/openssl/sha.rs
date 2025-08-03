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

use std::ffi::*;
use std::mem::MaybeUninit;

extern "C" {
    fn SHA1(d: *const c_uchar, n: usize, md: *mut c_uchar) -> *mut c_uchar;
}

pub fn sha1(data: &[u8]) -> [u8; 20] {
    unsafe {
        let mut hash = MaybeUninit::<[u8; 20]>::uninit();
        SHA1(data.as_ptr(), data.len(), hash.as_mut_ptr() as *mut _);
        hash.assume_init()
    }
}
