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

//! Wrappers of the HKDF functions in BoringSSL hkdf.h.

use crate::digest::Digester;
use crate::util::check_int_result;
use bssl_avf_error::{ApiName, Result};
use bssl_sys::HKDF;
use zeroize::Zeroizing;

/// Computes HKDF (as specified by [RFC 5869]) of initial keying material `secret` with
/// `salt` and `info` using the given `digester`.
///
/// [RFC 5869]: https://www.rfc-editor.org/rfc/rfc5869.html
pub fn hkdf<const N: usize>(
    secret: &[u8],
    salt: &[u8],
    info: &[u8],
    digester: Digester,
) -> Result<Zeroizing<[u8; N]>> {
    let mut key = Zeroizing::new([0u8; N]);
    // SAFETY: Only reads from/writes to the provided slices and the digester was non-null.
    let ret = unsafe {
        HKDF(
            key.as_mut_ptr(),
            key.len(),
            digester.0,
            secret.as_ptr(),
            secret.len(),
            salt.as_ptr(),
            salt.len(),
            info.as_ptr(),
            info.len(),
        )
    };
    check_int_result(ret, ApiName::HKDF)?;
    Ok(key)
}
