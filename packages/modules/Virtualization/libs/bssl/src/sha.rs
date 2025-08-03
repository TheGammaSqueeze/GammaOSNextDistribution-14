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

//! Wrappers of the SHA functions in BoringSSL sha.h.

use crate::util::to_call_failed_error;
use bssl_avf_error::{ApiName, Result};
use bssl_sys::SHA256;

/// The length of a SHA256 digest.
pub(crate) const SHA256_DIGEST_LENGTH: usize = bssl_sys::SHA256_DIGEST_LENGTH as usize;

/// Computes the SHA256 digest of the provided `data``.
pub fn sha256(data: &[u8]) -> Result<[u8; SHA256_DIGEST_LENGTH]> {
    let mut out = [0u8; SHA256_DIGEST_LENGTH];
    // SAFETY: This function reads `data` and writes to `out` within its bounds.
    // `out` has `SHA256_DIGEST_LENGTH` bytes of space for write.
    let ret = unsafe { SHA256(data.as_ptr(), data.len(), out.as_mut_ptr()) };
    if ret.is_null() {
        Err(to_call_failed_error(ApiName::SHA256))
    } else {
        Ok(out)
    }
}
