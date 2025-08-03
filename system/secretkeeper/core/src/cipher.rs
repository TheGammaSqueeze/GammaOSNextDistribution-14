/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! Utilities for encryption and decryption.

use crate::aidl_err;
use alloc::vec::Vec;
use authgraph_core::key::{AesKey, Nonce12};
use authgraph_core::traits::{AesGcm, Rng};
use coset::{iana, CborSerializable, CoseEncrypt0, CoseEncrypt0Builder, HeaderBuilder};
use secretkeeper_comm::wire::{AidlErrorCode, ApiError};

/// Decrypt a message.
pub fn decrypt_message(
    aes_gcm: &dyn AesGcm,
    key: &AesKey,
    encrypt0: &CoseEncrypt0,
    external_aad: &[u8],
) -> Result<Vec<u8>, ApiError> {
    let nonce12 = Nonce12::try_from(&encrypt0.unprotected.iv[..])
        .map_err(|e| aidl_err!(InternalError, "nonce of unexpected size: {e:?}"))?;
    encrypt0
        .decrypt(external_aad, |ct, aad| aes_gcm.decrypt(key, ct, aad, &nonce12))
        .map_err(|e| aidl_err!(InternalError, "failed to decrypt message: {e:?}"))
}

/// Encrypt a message.
pub fn encrypt_message(
    aes_gcm: &dyn AesGcm,
    rng: &dyn Rng,
    key: &AesKey,
    session_id: &[u8],
    msg: &[u8],
    external_aad: &[u8],
) -> Result<Vec<u8>, ApiError> {
    let nonce12 = Nonce12::new(rng);
    let encrypt0 = CoseEncrypt0Builder::new()
        .protected(
            HeaderBuilder::new()
                .algorithm(iana::Algorithm::A256GCM)
                .key_id(session_id.to_vec())
                .build(),
        )
        .unprotected(HeaderBuilder::new().iv(nonce12.0.to_vec()).build())
        .try_create_ciphertext(msg, external_aad, |plaintext, aad| {
            aes_gcm.encrypt(key, plaintext, aad, &nonce12)
        })
        .map_err(|e| aidl_err!(InternalError, "failed to encrypt message: {e:?}"))?
        .build();
    encrypt0
        .to_vec()
        .map_err(|e| aidl_err!(InternalError, "failed to serialize CoseEncrypt0: {e:?}"))
}
