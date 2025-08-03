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

//! Wrappers of the HMAC functions in BoringSSL hmac.h.

use crate::digest::Digester;
use crate::sha::SHA256_DIGEST_LENGTH;
use crate::util::to_call_failed_error;
use bssl_avf_error::{ApiName, Result};
use bssl_sys::HMAC;

/// Computes the HMAC using SHA-256 for the given `data` with the given `key`.
pub fn hmac_sha256(key: &[u8], data: &[u8]) -> Result<[u8; SHA256_DIGEST_LENGTH]> {
    hmac::<SHA256_DIGEST_LENGTH>(key, data, Digester::sha256())
}

/// Computes the HMAC for the given `data` with the given `key` and `digester`.
///
/// The output size `HASH_LEN` should correspond to the length of the hash function's
/// digest size in bytes.
fn hmac<const HASH_LEN: usize>(
    key: &[u8],
    data: &[u8],
    digester: Digester,
) -> Result<[u8; HASH_LEN]> {
    assert_eq!(digester.size(), HASH_LEN);

    let mut out = [0u8; HASH_LEN];
    let mut out_len = 0;
    // SAFETY: Only reads from/writes to the provided slices and the digester was non-null.
    let ret = unsafe {
        HMAC(
            digester.0,
            key.as_ptr() as *const _,
            key.len(),
            data.as_ptr(),
            data.len(),
            out.as_mut_ptr(),
            &mut out_len,
        )
    };
    if !ret.is_null() && out_len == (out.len() as u32) {
        Ok(out)
    } else {
        Err(to_call_failed_error(ApiName::HMAC))
    }
}
