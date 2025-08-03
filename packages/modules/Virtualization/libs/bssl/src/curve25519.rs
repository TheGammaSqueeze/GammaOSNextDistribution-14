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

//! Wrappers of the Curve25519 related functions in BoringSSL curve25519.h.

use crate::util::check_int_result;
use bssl_avf_error::{ApiName, Result};

const ED25519_PUBLIC_KEY_LEN: usize = bssl_sys::ED25519_PUBLIC_KEY_LEN as usize;
const ED25519_SIGNATURE_LEN: usize = bssl_sys::ED25519_SIGNATURE_LEN as usize;

/// Verifies the signature of a message with the given ED25519 public key.
pub fn ed25519_verify(
    message: &[u8],
    signature: &[u8; ED25519_SIGNATURE_LEN],
    public_key: &[u8; ED25519_PUBLIC_KEY_LEN],
) -> Result<()> {
    // SAFETY: The function only reads the parameters within their bounds.
    let ret = unsafe {
        bssl_sys::ED25519_verify(
            message.as_ptr(),
            message.len(),
            signature.as_ptr(),
            public_key.as_ptr(),
        )
    };
    check_int_result(ret, ApiName::ED25519_verify)
}
