// Copyright 2020, The Android Open Source Project
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

//! Provide a wrapper around a KeyMint device that allows up-level features to
//! be emulated on back-level devices.

use crate::ks_err;
use crate::error::{map_binder_status, map_binder_status_code, map_or_log_err, Error, ErrorCode};
use android_hardware_security_keymint::binder::{BinderFeatures, StatusCode, Strong};
use android_hardware_security_secureclock::aidl::android::hardware::security::secureclock::TimeStampToken::TimeStampToken;
use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    AttestationKey::AttestationKey, BeginResult::BeginResult, EcCurve::EcCurve,
    HardwareAuthToken::HardwareAuthToken, IKeyMintDevice::BnKeyMintDevice,
    IKeyMintDevice::IKeyMintDevice, KeyCharacteristics::KeyCharacteristics,
    KeyCreationResult::KeyCreationResult, KeyFormat::KeyFormat,
    KeyMintHardwareInfo::KeyMintHardwareInfo, KeyParameter::KeyParameter,
    KeyParameterValue::KeyParameterValue, KeyPurpose::KeyPurpose, SecurityLevel::SecurityLevel,
    Tag::Tag,
};
use android_security_compat::aidl::android::security::compat::IKeystoreCompatService::IKeystoreCompatService;
use anyhow::Context;
use keystore2_crypto::{hmac_sha256, HMAC_SHA256_LEN};

/// Magic prefix used by the km_compat C++ code to mark a key that is owned by an
/// underlying Keymaster hardware device that has been wrapped by km_compat. (The
/// final zero byte indicates that the blob is not software emulated.)
pub const KEYMASTER_BLOB_HW_PREFIX: &[u8] = b"pKMblob\x00";

/// Magic prefix used by the km_compat C++ code to mark a key that is owned by an
/// software emulation device that has been wrapped by km_compat. (The final one
/// byte indicates that the blob is software emulated.)
pub const KEYMASTER_BLOB_SW_PREFIX: &[u8] = b"pKMblob\x01";

/// Key data associated with key generation/import.
#[derive(Debug, PartialEq, Eq)]
pub enum KeyImportData<'a> {
    None,
    Pkcs8(&'a [u8]),
    Raw(&'a [u8]),
}

impl<'a> KeyImportData<'a> {
    /// Translate import parameters into a `KeyImportData` instance.
    fn new(key_format: KeyFormat, key_data: &'a [u8]) -> binder::Result<Self> {
        match key_format {
            KeyFormat::PKCS8 => Ok(KeyImportData::Pkcs8(key_data)),
            KeyFormat::RAW => Ok(KeyImportData::Raw(key_data)),
            _ => Err(binder::Status::new_service_specific_error(
                ErrorCode::UNSUPPORTED_KEY_FORMAT.0,
                None,
            )),
        }
    }
}

/// A key blob that may be software-emulated or may be directly produced by an
/// underlying device.  In either variant the inner data is the keyblob itself,
/// as seen by the relevant device.
#[derive(Debug, PartialEq, Eq)]
pub enum KeyBlob<'a> {
    Raw(&'a [u8]),
    Wrapped(&'a [u8]),
}

/// Trait for detecting that software emulation of a current-version KeyMint
/// feature is required for a back-level KeyMint implementation.
pub trait EmulationDetector: Send + Sync {
    /// Indicate whether software emulation is required for key
    /// generation/import using the provided parameters.
    fn emulation_required(&self, params: &[KeyParameter], import_data: &KeyImportData) -> bool;
}

const KEYBLOB_PREFIX: &[u8] = b"SoftKeyMintForV1Blob";
const KEYBLOB_HMAC_KEY: &[u8] = b"SoftKeyMintForV1HMACKey";

/// Wrap the provided keyblob:
/// - prefix it with an identifier specific to this wrapper
/// - suffix it with an HMAC tag, using the [`KEYBLOB_HMAC_KEY`] and `keyblob`.
fn wrap_keyblob(keyblob: &[u8]) -> anyhow::Result<Vec<u8>> {
    let mut result = Vec::with_capacity(KEYBLOB_PREFIX.len() + keyblob.len() + HMAC_SHA256_LEN);
    result.extend_from_slice(KEYBLOB_PREFIX);
    result.extend_from_slice(keyblob);
    let tag = hmac_sha256(KEYBLOB_HMAC_KEY, keyblob)
        .context(ks_err!("failed to calculate HMAC-SHA256"))?;
    result.extend_from_slice(&tag);
    Ok(result)
}

/// Return an unwrapped version of the provided `keyblob`, which may or may
/// not be associated with the software emulation.
pub fn unwrap_keyblob(keyblob: &[u8]) -> KeyBlob {
    if !keyblob.starts_with(KEYBLOB_PREFIX) {
        return KeyBlob::Raw(keyblob);
    }
    let without_prefix = &keyblob[KEYBLOB_PREFIX.len()..];
    if without_prefix.len() < HMAC_SHA256_LEN {
        return KeyBlob::Raw(keyblob);
    }
    let (inner_keyblob, want_tag) = without_prefix.split_at(without_prefix.len() - HMAC_SHA256_LEN);
    let got_tag = match hmac_sha256(KEYBLOB_HMAC_KEY, inner_keyblob) {
        Ok(tag) => tag,
        Err(e) => {
            log::error!("Error calculating HMAC-SHA256 for keyblob unwrap: {:?}", e);
            return KeyBlob::Raw(keyblob);
        }
    };
    // Comparison does not need to be constant-time here.
    if want_tag == got_tag {
        KeyBlob::Wrapped(inner_keyblob)
    } else {
        KeyBlob::Raw(keyblob)
    }
}

/// Wrapper around a real device that implements a back-level version of
/// `IKeyMintDevice`
pub struct BacklevelKeyMintWrapper<T: EmulationDetector> {
    /// The `real` device implements some earlier version of `IKeyMintDevice`
    real: Strong<dyn IKeyMintDevice>,
    /// The `soft`ware device implements the current version of `IKeyMintDevice`
    soft: Strong<dyn IKeyMintDevice>,
    /// Detector for operations that are not supported by the earlier version of
    /// `IKeyMintDevice`. Or possibly a large flightless bird, who can tell.
    emu: T,
}

impl<T> BacklevelKeyMintWrapper<T>
where
    T: EmulationDetector + 'static,
{
    /// Create a wrapper around the provided back-level KeyMint device, so that
    /// software emulation can be performed for any current-version features not
    /// provided by the real device.
    pub fn wrap(
        emu: T,
        real: Strong<dyn IKeyMintDevice>,
    ) -> anyhow::Result<Strong<dyn IKeyMintDevice>> {
        // This is a no-op if it was called before.
        keystore2_km_compat::add_keymint_device_service();

        let keystore_compat_service: Strong<dyn IKeystoreCompatService> =
            map_binder_status_code(binder::get_interface("android.security.compat"))
                .context(ks_err!("Trying to connect to compat service."))?;
        let soft =
            map_binder_status(keystore_compat_service.getKeyMintDevice(SecurityLevel::SOFTWARE))
                .map_err(|e| match e {
                    Error::BinderTransaction(StatusCode::NAME_NOT_FOUND) => {
                        Error::Km(ErrorCode::HARDWARE_TYPE_UNAVAILABLE)
                    }
                    e => e,
                })
                .context(ks_err!("Trying to get software device."))?;

        Ok(BnKeyMintDevice::new_binder(
            Self { real, soft, emu },
            BinderFeatures { set_requesting_sid: true, ..BinderFeatures::default() },
        ))
    }
}

impl<T> binder::Interface for BacklevelKeyMintWrapper<T> where T: EmulationDetector + 'static {}

impl<T> IKeyMintDevice for BacklevelKeyMintWrapper<T>
where
    T: EmulationDetector + 'static,
{
    // For methods that don't involve keyblobs, forward to either the real
    // device, or to both real & emulated devices.
    fn getHardwareInfo(&self) -> binder::Result<KeyMintHardwareInfo> {
        self.real.getHardwareInfo()
    }
    fn addRngEntropy(&self, data: &[u8]) -> binder::Result<()> {
        self.real.addRngEntropy(data)
    }
    fn deleteAllKeys(&self) -> binder::Result<()> {
        self.real.deleteAllKeys()
    }
    fn destroyAttestationIds(&self) -> binder::Result<()> {
        self.real.destroyAttestationIds()
    }
    fn deviceLocked(
        &self,
        password_only: bool,
        timestamp_token: Option<&TimeStampToken>,
    ) -> binder::Result<()> {
        // Propagate to both real and software devices, but only pay attention
        // to the result from the real device.
        let _ = self.soft.deviceLocked(password_only, timestamp_token);
        self.real.deviceLocked(password_only, timestamp_token)
    }
    fn earlyBootEnded(&self) -> binder::Result<()> {
        // Propagate to both real and software devices, but only pay attention
        // to the result from the real device.
        let _ = self.soft.earlyBootEnded();
        self.real.earlyBootEnded()
    }
    fn getRootOfTrustChallenge(&self) -> binder::Result<[u8; 16]> {
        self.real.getRootOfTrustChallenge()
    }
    fn getRootOfTrust(&self, challenge: &[u8; 16]) -> binder::Result<Vec<u8>> {
        self.real.getRootOfTrust(challenge)
    }
    fn sendRootOfTrust(&self, root_of_trust: &[u8]) -> binder::Result<()> {
        self.real.sendRootOfTrust(root_of_trust)
    }

    // For methods that emit keyblobs, check whether the underlying real device
    // supports the relevant parameters, and forward to the appropriate device.
    // If the emulated device is used, ensure that the created keyblob gets
    // prefixed so we can recognize it in future.
    fn generateKey(
        &self,
        key_params: &[KeyParameter],
        attestation_key: Option<&AttestationKey>,
    ) -> binder::Result<KeyCreationResult> {
        if self.emu.emulation_required(key_params, &KeyImportData::None) {
            let mut result = self.soft.generateKey(key_params, attestation_key)?;
            result.keyBlob = map_or_log_err(wrap_keyblob(&result.keyBlob), Ok)?;
            Ok(result)
        } else {
            self.real.generateKey(key_params, attestation_key)
        }
    }
    fn importKey(
        &self,
        key_params: &[KeyParameter],
        key_format: KeyFormat,
        key_data: &[u8],
        attestation_key: Option<&AttestationKey>,
    ) -> binder::Result<KeyCreationResult> {
        if self.emu.emulation_required(key_params, &KeyImportData::new(key_format, key_data)?) {
            let mut result =
                self.soft.importKey(key_params, key_format, key_data, attestation_key)?;
            result.keyBlob = map_or_log_err(wrap_keyblob(&result.keyBlob), Ok)?;
            Ok(result)
        } else {
            self.real.importKey(key_params, key_format, key_data, attestation_key)
        }
    }
    fn importWrappedKey(
        &self,
        wrapped_key_data: &[u8],
        wrapping_key_blob: &[u8],
        masking_key: &[u8],
        unwrapping_params: &[KeyParameter],
        password_sid: i64,
        biometric_sid: i64,
    ) -> binder::Result<KeyCreationResult> {
        // A wrapped key cannot be software-emulated, as the wrapping key is
        // likely hardware-bound.
        self.real.importWrappedKey(
            wrapped_key_data,
            wrapping_key_blob,
            masking_key,
            unwrapping_params,
            password_sid,
            biometric_sid,
        )
    }

    // For methods that use keyblobs, determine which device to forward the
    // operation to based on whether the keyblob is appropriately prefixed.
    fn upgradeKey(
        &self,
        keyblob_to_upgrade: &[u8],
        upgrade_params: &[KeyParameter],
    ) -> binder::Result<Vec<u8>> {
        match unwrap_keyblob(keyblob_to_upgrade) {
            KeyBlob::Raw(keyblob) => self.real.upgradeKey(keyblob, upgrade_params),
            KeyBlob::Wrapped(keyblob) => {
                // Re-wrap the upgraded keyblob.
                let upgraded_keyblob = self.soft.upgradeKey(keyblob, upgrade_params)?;
                map_or_log_err(wrap_keyblob(&upgraded_keyblob), Ok)
            }
        }
    }
    fn deleteKey(&self, keyblob: &[u8]) -> binder::Result<()> {
        match unwrap_keyblob(keyblob) {
            KeyBlob::Raw(keyblob) => self.real.deleteKey(keyblob),
            KeyBlob::Wrapped(keyblob) => {
                // Forward to the software implementation for completeness, but
                // this should always be a no-op.
                self.soft.deleteKey(keyblob)
            }
        }
    }
    fn begin(
        &self,
        purpose: KeyPurpose,
        keyblob: &[u8],
        params: &[KeyParameter],
        auth_token: Option<&HardwareAuthToken>,
    ) -> binder::Result<BeginResult> {
        match unwrap_keyblob(keyblob) {
            KeyBlob::Raw(keyblob) => self.real.begin(purpose, keyblob, params, auth_token),
            KeyBlob::Wrapped(keyblob) => self.soft.begin(purpose, keyblob, params, auth_token),
        }
    }
    fn getKeyCharacteristics(
        &self,
        keyblob: &[u8],
        app_id: &[u8],
        app_data: &[u8],
    ) -> binder::Result<Vec<KeyCharacteristics>> {
        match unwrap_keyblob(keyblob) {
            KeyBlob::Raw(keyblob) => self.real.getKeyCharacteristics(keyblob, app_id, app_data),
            KeyBlob::Wrapped(keyblob) => self.soft.getKeyCharacteristics(keyblob, app_id, app_data),
        }
    }
    fn convertStorageKeyToEphemeral(&self, storage_keyblob: &[u8]) -> binder::Result<Vec<u8>> {
        // Storage keys should never be associated with a software emulated device.
        self.real.convertStorageKeyToEphemeral(storage_keyblob)
    }
}

/// Detector for current features that are not implemented by KeyMint V1.
#[derive(Debug)]
pub struct KeyMintV1 {
    sec_level: SecurityLevel,
}

impl KeyMintV1 {
    pub fn new(sec_level: SecurityLevel) -> Self {
        Self { sec_level }
    }
}

impl EmulationDetector for KeyMintV1 {
    fn emulation_required(&self, params: &[KeyParameter], _import_data: &KeyImportData) -> bool {
        // No current difference from KeyMint v1 for STRONGBOX (it doesn't
        // support curve 25519).
        if self.sec_level == SecurityLevel::STRONGBOX {
            return false;
        }

        // KeyMint V1 does not support the use of curve 25519, so hunt for that
        // in the parameters.
        if params.iter().any(|p| {
            p.tag == Tag::EC_CURVE && p.value == KeyParameterValue::EcCurve(EcCurve::CURVE_25519)
        }) {
            return true;
        }
        // In theory, if the `import_data` is `KeyImportData::Pkcs8` we could
        // check the imported keymaterial for the Ed25519 / X25519 OIDs in the
        // PKCS8 keydata, and use that to decide to route to software. However,
        // the KeyMint spec doesn't require that so don't attempt to parse the
        // key material here.
        false
    }
}

/// Detector for current features that are not implemented by KeyMaster, via the
/// km_compat wrapper.
#[derive(Debug)]
pub struct Keymaster {
    v1: KeyMintV1,
}

/// TODO(b/216434270): This could be used this to replace the emulation routing
/// in the km_compat C++ code, and allow support for imported ECDH keys along
/// the way. Would need to figure out what would happen to existing emulated
/// keys though.
#[allow(dead_code)]
impl Keymaster {
    pub fn new(sec_level: SecurityLevel) -> Self {
        Self { v1: KeyMintV1::new(sec_level) }
    }
}

impl EmulationDetector for Keymaster {
    fn emulation_required(&self, params: &[KeyParameter], import_data: &KeyImportData) -> bool {
        // The km_compat wrapper on top of Keymaster emulates the KeyMint V1
        // interface, so any feature from > v1 needs to be emulated.
        if self.v1.emulation_required(params, import_data) {
            return true;
        }

        // Keymaster does not support ECDH (KeyPurpose::AGREE_KEY), so hunt for
        // that in the parameters.
        if params.iter().any(|p| {
            p.tag == Tag::PURPOSE && p.value == KeyParameterValue::KeyPurpose(KeyPurpose::AGREE_KEY)
        }) {
            return true;
        }
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_key_import_data() {
        let data = vec![1, 2, 3];
        assert_eq!(KeyImportData::new(KeyFormat::PKCS8, &data), Ok(KeyImportData::Pkcs8(&data)));
        assert_eq!(KeyImportData::new(KeyFormat::RAW, &data), Ok(KeyImportData::Raw(&data)));
        assert!(KeyImportData::new(KeyFormat::X509, &data).is_err());
    }

    #[test]
    fn test_wrap_keyblob() {
        let keyblob = vec![1, 2, 3];
        let wrapped = wrap_keyblob(&keyblob).unwrap();
        assert_eq!(&wrapped[..KEYBLOB_PREFIX.len()], KEYBLOB_PREFIX);
        assert_eq!(&wrapped[KEYBLOB_PREFIX.len()..KEYBLOB_PREFIX.len() + keyblob.len()], &keyblob);
        assert_eq!(unwrap_keyblob(&keyblob), KeyBlob::Raw(&keyblob));
        assert_eq!(unwrap_keyblob(&wrapped), KeyBlob::Wrapped(&keyblob));

        let mut corrupt_prefix = wrapped.clone();
        corrupt_prefix[0] ^= 0x01;
        assert_eq!(unwrap_keyblob(&corrupt_prefix), KeyBlob::Raw(&corrupt_prefix));

        let mut corrupt_suffix = wrapped.clone();
        corrupt_suffix[wrapped.len() - 1] ^= 0x01;
        assert_eq!(unwrap_keyblob(&corrupt_suffix), KeyBlob::Raw(&corrupt_suffix));

        let too_short = &wrapped[..wrapped.len() - 4];
        assert_eq!(unwrap_keyblob(too_short), KeyBlob::Raw(too_short));
    }

    #[test]
    fn test_keymintv1_emulation_required() {
        let tests = vec![
            (SecurityLevel::TRUSTED_ENVIRONMENT, vec![], false),
            (
                SecurityLevel::TRUSTED_ENVIRONMENT,
                vec![
                    KeyParameter {
                        tag: Tag::PURPOSE,
                        value: KeyParameterValue::KeyPurpose(KeyPurpose::SIGN),
                    },
                    KeyParameter {
                        tag: Tag::PURPOSE,
                        value: KeyParameterValue::KeyPurpose(KeyPurpose::VERIFY),
                    },
                ],
                false,
            ),
            (
                SecurityLevel::TRUSTED_ENVIRONMENT,
                vec![KeyParameter {
                    tag: Tag::PURPOSE,
                    value: KeyParameterValue::KeyPurpose(KeyPurpose::AGREE_KEY),
                }],
                false,
            ),
            (
                SecurityLevel::TRUSTED_ENVIRONMENT,
                vec![
                    KeyParameter {
                        tag: Tag::PURPOSE,
                        value: KeyParameterValue::KeyPurpose(KeyPurpose::AGREE_KEY),
                    },
                    KeyParameter {
                        tag: Tag::EC_CURVE,
                        value: KeyParameterValue::EcCurve(EcCurve::P_256),
                    },
                ],
                false,
            ),
            (
                SecurityLevel::TRUSTED_ENVIRONMENT,
                vec![
                    KeyParameter {
                        tag: Tag::PURPOSE,
                        value: KeyParameterValue::KeyPurpose(KeyPurpose::AGREE_KEY),
                    },
                    KeyParameter {
                        tag: Tag::EC_CURVE,
                        value: KeyParameterValue::EcCurve(EcCurve::CURVE_25519),
                    },
                ],
                true,
            ),
            (
                SecurityLevel::STRONGBOX,
                vec![
                    KeyParameter {
                        tag: Tag::PURPOSE,
                        value: KeyParameterValue::KeyPurpose(KeyPurpose::AGREE_KEY),
                    },
                    KeyParameter {
                        tag: Tag::EC_CURVE,
                        value: KeyParameterValue::EcCurve(EcCurve::CURVE_25519),
                    },
                ],
                false,
            ),
        ];
        for (sec_level, params, want) in tests {
            let v1 = KeyMintV1::new(sec_level);
            let got = v1.emulation_required(&params, &KeyImportData::None);
            assert_eq!(got, want, "emulation_required({:?})={}, want {}", params, got, want);
        }
    }

    #[test]
    fn test_keymaster_emulation_required() {
        let tests = vec![
            (SecurityLevel::TRUSTED_ENVIRONMENT, vec![], false),
            (
                SecurityLevel::TRUSTED_ENVIRONMENT,
                vec![
                    KeyParameter {
                        tag: Tag::PURPOSE,
                        value: KeyParameterValue::KeyPurpose(KeyPurpose::SIGN),
                    },
                    KeyParameter {
                        tag: Tag::PURPOSE,
                        value: KeyParameterValue::KeyPurpose(KeyPurpose::VERIFY),
                    },
                ],
                false,
            ),
            (
                SecurityLevel::TRUSTED_ENVIRONMENT,
                vec![KeyParameter {
                    tag: Tag::PURPOSE,
                    value: KeyParameterValue::KeyPurpose(KeyPurpose::AGREE_KEY),
                }],
                true,
            ),
            (
                SecurityLevel::TRUSTED_ENVIRONMENT,
                vec![
                    KeyParameter {
                        tag: Tag::PURPOSE,
                        value: KeyParameterValue::KeyPurpose(KeyPurpose::AGREE_KEY),
                    },
                    KeyParameter {
                        tag: Tag::EC_CURVE,
                        value: KeyParameterValue::EcCurve(EcCurve::P_256),
                    },
                ],
                true,
            ),
            (
                SecurityLevel::TRUSTED_ENVIRONMENT,
                vec![
                    KeyParameter {
                        tag: Tag::PURPOSE,
                        value: KeyParameterValue::KeyPurpose(KeyPurpose::AGREE_KEY),
                    },
                    KeyParameter {
                        tag: Tag::EC_CURVE,
                        value: KeyParameterValue::EcCurve(EcCurve::CURVE_25519),
                    },
                ],
                true,
            ),
            (
                SecurityLevel::STRONGBOX,
                vec![
                    KeyParameter {
                        tag: Tag::PURPOSE,
                        value: KeyParameterValue::KeyPurpose(KeyPurpose::AGREE_KEY),
                    },
                    KeyParameter {
                        tag: Tag::EC_CURVE,
                        value: KeyParameterValue::EcCurve(EcCurve::CURVE_25519),
                    },
                ],
                true,
            ),
            (
                SecurityLevel::STRONGBOX,
                vec![
                    KeyParameter {
                        tag: Tag::PURPOSE,
                        value: KeyParameterValue::KeyPurpose(KeyPurpose::SIGN),
                    },
                    KeyParameter {
                        tag: Tag::EC_CURVE,
                        value: KeyParameterValue::EcCurve(EcCurve::CURVE_25519),
                    },
                ],
                false,
            ),
        ];
        for (sec_level, params, want) in tests {
            let v0 = Keymaster::new(sec_level);
            let got = v0.emulation_required(&params, &KeyImportData::None);
            assert_eq!(got, want, "emulation_required({:?})={}, want {}", params, got, want);
        }
    }
}
