/*
 * Copyright (C) 2022 The Android Open Source Project
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
//! Trusty implementation of StorageKeyWrapper trait.
use alloc::vec::Vec;
use core::ffi::CStr;
use hwwsk;
use kmr_common::{
    crypto,
    crypto::{aes, Aes, KeyMaterial, OpaqueKeyMaterial, OpaqueOr},
    get_bool_tag_value, get_opt_tag_value, get_tag_value, km_err, vec_try, vec_try_with_capacity,
    Error,
};
use kmr_crypto_boring::aes::BoringAes;
use kmr_ta::device::StorageKeyWrapper;
use kmr_wire::{keymint, keymint::ErrorCode, KeySizeInBits};
use log::warn;
use tipc::Handle;

/// TIPC port used for communication with the `hwwsk` service.
const HWWSK_PORT: &'static [u8] = b"com.android.trusty.hwwsk\0";

/// Create a session for `hwwsk` communication.
fn hwwsk_session() -> Result<Handle, Error> {
    let port = CStr::from_bytes_with_nul(HWWSK_PORT).expect("HWWSK_PORT was not null terminated");
    Handle::connect(port)
        .map_err(|e| km_err!(SecureHwCommunicationFailed, "failed to connect to hwwsk: {:?}", e))
}

/// Storage key wrapper implementation for Trusty.
pub struct TrustyStorageKeyWrapper;

impl StorageKeyWrapper for TrustyStorageKeyWrapper {
    fn ephemeral_wrap(&self, key_material: &KeyMaterial) -> Result<Vec<u8>, Error> {
        let wrapped_key = match key_material {
            KeyMaterial::Aes(OpaqueOr::Opaque(key)) => key,
            _ => {
                return Err(km_err!(
                    UnsupportedAlgorithm,
                    "only opaque AES storage keys are supported"
                ))
            }
        };
        let key = &wrapped_key.0;
        let session = hwwsk_session()?;
        let buf = &mut [0u8; hwwsk::HWWSK_MAX_MSG_SIZE as usize];
        let wrapped_key_buffer = hwwsk::export_key(&session, buf, key).map_err(|e| {
            km_err!(SecureHwCommunicationFailed, "hwwsk failed to wrap key: {:?}", e)
        })?;
        let mut wrapped_key = vec_try_with_capacity!(wrapped_key_buffer.len())?;
        wrapped_key.extend_from_slice(wrapped_key_buffer);
        Ok(wrapped_key)
    }
}

/// Wrapper around `BoringAes` implementation that intercepts storage keys.
pub struct TrustyAes(BoringAes);

impl Default for TrustyAes {
    fn default() -> Self {
        Self(BoringAes)
    }
}
impl TrustyAes {
    fn create_storage_key(
        &self,
        key: Option<aes::Key>,
        params: &[keymint::KeyParam],
    ) -> Result<crypto::KeyMaterial, Error> {
        // Storage keys should not work for normal operations.  The TA code polices this by watching
        // for `Tag::StorageKey`; also police here by rejecting keys that have a `Tag::BlockMode`
        // attached.
        if get_opt_tag_value!(params, BlockMode)?.is_some() {
            return Err(km_err!(UnsupportedTag, "don't expect block mode on storage key"));
        }
        let key_size = get_tag_value!(params, KeySize, ErrorCode::UnsupportedTag)?;
        let key_size = key_size.0 as usize;
        let mut key_flags = hwwsk::KeyFlags::new();
        let rollback_resistance = get_bool_tag_value!(params, RollbackResistance)?;
        if rollback_resistance {
            key_flags = key_flags.rollback_resistance();
        };

        let session = hwwsk_session()?;
        let mut buf = vec_try![0; hwwsk::HWWSK_MAX_MSG_SIZE as usize]?;
        let key_material: Option<&[u8]> = match key.as_ref() {
            None => None,
            Some(aes_key) => Some(match aes_key {
                aes::Key::Aes128(key) => key,
                aes::Key::Aes192(key) => key,
                aes::Key::Aes256(key) => key,
            }),
        };
        let mut result = match key_material {
            None => hwwsk::generate_key(&session, &mut buf, key_size, key_flags),
            Some(key) => hwwsk::import_key(&session, &mut buf, key_size, key_flags, key),
        };
        if result == Err(hwwsk::HwWskError::NotSupported) && rollback_resistance {
            warn!("failed to generate rollback-resistant storage key, retrying without resistance");
            key_flags = hwwsk::KeyFlags::new();
            result = match key_material {
                None => hwwsk::generate_key(&session, &mut buf, key_size, key_flags),
                Some(key) => hwwsk::import_key(&session, &mut buf, key_size, key_flags, key),
            };
        }

        let wrapped_key_buffer =
            result.map_err(|e| km_err!(UnknownError, "hwwsk failed to create key: {:?}", e))?;

        let mut wrapped_key = vec_try_with_capacity!(wrapped_key_buffer.len())?;
        wrapped_key.extend_from_slice(wrapped_key_buffer);
        Ok(crypto::KeyMaterial::Aes(OpaqueOr::Opaque(OpaqueKeyMaterial(wrapped_key))))
    }
}

impl Aes for TrustyAes {
    fn generate_key(
        &self,
        rng: &mut dyn crypto::Rng,
        variant: aes::Variant,
        params: &[keymint::KeyParam],
    ) -> Result<crypto::KeyMaterial, Error> {
        if !get_bool_tag_value!(params, StorageKey)? {
            // For normal (non-storage) keys, pass on to BoringSSL implementation.
            return self.0.generate_key(rng, variant, params);
        }
        self.create_storage_key(None, params)
    }

    fn import_key(
        &self,
        data: &[u8],
        params: &[keymint::KeyParam],
    ) -> Result<(crypto::KeyMaterial, KeySizeInBits), Error> {
        if !get_bool_tag_value!(params, StorageKey)? {
            // For normal (non-storage) keys, pass on to BoringSSL implementation.
            return self.0.import_key(data, params);
        }

        let aes_key = aes::Key::new_from(data)?;
        let key_size = aes_key.size();
        Ok((self.create_storage_key(Some(aes_key), params)?, key_size))
    }

    fn begin(
        &self,
        key: OpaqueOr<aes::Key>,
        mode: aes::CipherMode,
        dir: crypto::SymmetricOperation,
    ) -> Result<Box<dyn crypto::EmittingOperation>, Error> {
        match key {
            OpaqueOr::Explicit(_) => self.0.begin(key, mode, dir),
            OpaqueOr::Opaque(_) => {
                Err(km_err!(StorageKeyUnsupported, "attempt to use storage key"))
            }
        }
    }

    fn begin_aead(
        &self,
        key: OpaqueOr<aes::Key>,
        mode: aes::GcmMode,
        dir: crypto::SymmetricOperation,
    ) -> Result<Box<dyn crypto::AadOperation>, Error> {
        match key {
            OpaqueOr::Explicit(_) => self.0.begin_aead(key, mode, dir),
            OpaqueOr::Opaque(_) => {
                Err(km_err!(StorageKeyUnsupported, "attempt to use storage key"))
            }
        }
    }
}

// Not adding unit tests because we do not have a mock server on AOSP for hwwsk.
