//! Functionality for converting legacy keyblob formats.

use alloc::vec::Vec;
use kmr_common::keyblob::{
    legacy, SecureDeletionData, SecureDeletionSecretManager, SecureDeletionSlot,
};
use kmr_common::{
    crypto,
    crypto::{aes, OpaqueKeyMaterial, OpaqueOr},
    explicit, get_bool_tag_value, get_opt_tag_value, get_tag_value, keyblob, km_err, tag,
    try_to_vec, vec_try, Error, FallibleAllocExt,
};
use kmr_ta::device;
use kmr_wire::{
    keymint,
    keymint::{Algorithm, BootInfo, EcCurve, ErrorCode, KeyParam, KeyPurpose, SecurityLevel},
    KeySizeInBits,
};
use log::error;

/// Prefix for KEK derivation input when secure deletion not supported.
const AES_GCM_DESCRIPTOR_V1: &[u8] = b"AES-256-GCM-HKDF-SHA-256, version 1\0";
/// Prefix for KEK derivation input when secure deletion supported.
const AES_GCM_DESCRIPTOR_V2: &[u8] = b"AES-256-GCM-HKDF-SHA-256, version 2\0";

/// Slot number used to indicate that a key has no per-key secure deletion data.
const NO_SDD_SLOT_IDX: u32 = 0;

/// Legacy key handler that detects and converts `EncryptedKeyBlob` instances from
/// the previous Trusty implementation of KeyMint/Keymaster.
pub struct TrustyLegacyKeyBlobHandler {
    pub aes: Box<dyn crypto::Aes>,
    pub hkdf: Box<dyn crypto::Hkdf>,
    pub sdd_mgr: Option<Box<dyn SecureDeletionSecretManager>>,
    pub keys: Box<dyn device::RetrieveKeyMaterial>,
}

impl TrustyLegacyKeyBlobHandler {
    /// Build the derivation information needed for KEK derivation that is compatible with the
    /// previous C++ implementation.
    fn build_derivation_info(
        &self,
        encrypted_keyblob: &legacy::EncryptedKeyBlob,
        hidden: &[KeyParam],
        sdd_info: Option<(SecureDeletionData, u32)>,
    ) -> Result<Vec<u8>, Error> {
        let mut info = if sdd_info.is_some() {
            try_to_vec(AES_GCM_DESCRIPTOR_V2)?
        } else {
            try_to_vec(AES_GCM_DESCRIPTOR_V1)?
        };
        info.try_extend_from_slice(&tag::legacy::serialize(hidden)?)?;
        info.try_extend_from_slice(&tag::legacy::serialize(&encrypted_keyblob.hw_enforced)?)?;
        info.try_extend_from_slice(&tag::legacy::serialize(&encrypted_keyblob.sw_enforced)?)?;
        if let Some((sdd_data, slot)) = sdd_info {
            info.try_extend_from_slice(
                &(sdd_data.factory_reset_secret.len() as u32).to_ne_bytes(),
            )?;
            info.try_extend_from_slice(&sdd_data.factory_reset_secret)?;

            // If the slot is zero, the per-key secret is empty.
            let secret: &[u8] = if slot == 0 { &[] } else { &sdd_data.secure_deletion_secret };
            info.try_extend_from_slice(&(secret.len() as u32).to_ne_bytes())?;
            info.try_extend_from_slice(&secret)?;

            info.try_extend_from_slice(&slot.to_ne_bytes())?;
        }
        Ok(info)
    }

    /// Derive the key encryption key for a keyblob.
    fn derive_kek(
        &self,
        root_kek: &OpaqueOr<kmr_common::crypto::hmac::Key>,
        encrypted_keyblob: &legacy::EncryptedKeyBlob,
        hidden: &[KeyParam],
        sdd_data: Option<(SecureDeletionData, u32)>,
    ) -> Result<crypto::aes::Key, Error> {
        let info = self.build_derivation_info(encrypted_keyblob, hidden, sdd_data)?;
        // Trusty uses explicit keys for legacy keyblobs.
        let raw_key = self.hkdf.hkdf(&[], &explicit!(root_kek)?.0, &info, 256 / 8)?;
        let aes_key = crypto::aes::Key::Aes256(
            raw_key.try_into().map_err(|_e| km_err!(UnknownError, "unexpected HKDF output len"))?,
        );
        Ok(aes_key)
    }

    /// Convert a keyblob from the legacy C++ format to the current format.
    fn convert_key(
        &self,
        keyblob: &[u8],
        params: &[KeyParam],
        root_of_trust: &BootInfo,
        sec_level: SecurityLevel,
    ) -> Result<keyblob::PlaintextKeyBlob, Error> {
        let encrypted_keyblob = legacy::EncryptedKeyBlob::deserialize(keyblob)?;

        // Find the secure deletion data (if any) for the key.
        let sdd_info = match (
            encrypted_keyblob.format.requires_secure_deletion(),
            &self.sdd_mgr,
            encrypted_keyblob.key_slot,
        ) {
            (true, Some(sdd_mgr), None) | (true, Some(sdd_mgr), Some(NO_SDD_SLOT_IDX)) => {
                // Zero slot index implies that just the factory reset secret is populated.
                let sdd_data = sdd_mgr.get_factory_reset_secret()?;
                Some((sdd_data, NO_SDD_SLOT_IDX))
            }
            (true, Some(sdd_mgr), Some(slot_idx)) => {
                let slot = SecureDeletionSlot(slot_idx);
                let sdd_data = sdd_mgr.get_secret(slot)?;

                Some((sdd_data, slot_idx as u32))
            }
            (true, None, _) => {
                return Err(km_err!(
                    InvalidKeyBlob,
                    "keyblob requires secure deletion but no implementation available",
                ))
            }
            (false, _, Some(slot)) => {
                return Err(km_err!(
                    InvalidKeyBlob,
                    "unexpected SDD slot {} for format {:?}",
                    slot,
                    encrypted_keyblob.format
                ))
            }
            (false, _, None) => None,
        };

        // Convert the key characteristics to current form.
        let mut characteristics = vec_try![keymint::KeyCharacteristics {
            security_level: sec_level,
            authorizations: try_to_vec(&encrypted_keyblob.hw_enforced)?,
        }]?;
        if !encrypted_keyblob.sw_enforced.is_empty() {
            characteristics.try_push(keymint::KeyCharacteristics {
                security_level: keymint::SecurityLevel::Keystore,
                authorizations: try_to_vec(&encrypted_keyblob.sw_enforced)?,
            })?;
        }

        // Derive the KEK, using hidden inputs from params and root-of-trust.
        let rots = &[
            &root_of_trust.verified_boot_key[..],
            &(root_of_trust.verified_boot_state as u32).to_ne_bytes(),
            &[if root_of_trust.device_boot_locked { 0x01u8 } else { 0x00u8 }],
        ];
        let hidden_params = legacy::hidden(params, rots)?;

        let rollback_version = match encrypted_keyblob.addl_info {
            Some(v) => Some(
                hwkey::OsRollbackVersion::try_from(v as i32)
                    .map_err(|e| km_err!(InvalidKeyBlob, "unexpected addl_info={} : {:?}", v, e))?,
            ),
            None => None,
        };
        let kek_context = super::TrustyKekContext::new(
            encrypted_keyblob.format.is_versioned(),
            encrypted_keyblob.kdf_version.map(|v| hwkey::KdfVersion::from(v)),
            rollback_version,
        )?
        .to_raw()?;

        let root_kek = self.keys.root_kek(&kek_context)?;
        let aes_key = self.derive_kek(&root_kek, &encrypted_keyblob, &hidden_params, sdd_info)?;

        // Key material is encrypted with AES-GCM; decrypt it.
        let nonce: [u8; aes::GCM_NONCE_SIZE] = encrypted_keyblob
            .nonce
            .try_into()
            .map_err(|_e| km_err!(InvalidKeyBlob, "unexpected nonce len",))?;
        let mode = match encrypted_keyblob.tag.len() {
            12 => crypto::aes::GcmMode::GcmTag12 { nonce },
            13 => crypto::aes::GcmMode::GcmTag13 { nonce },
            14 => crypto::aes::GcmMode::GcmTag14 { nonce },
            15 => crypto::aes::GcmMode::GcmTag15 { nonce },
            16 => crypto::aes::GcmMode::GcmTag16 { nonce },
            l => return Err(km_err!(InvalidKeyBlob, "unexpected AES-GCM tag length {}", l)),
        };
        let mut op =
            self.aes.begin_aead(aes_key.into(), mode, crypto::SymmetricOperation::Decrypt)?;
        let mut raw_key_material = op.update(&encrypted_keyblob.ciphertext)?;
        raw_key_material.try_extend_from_slice(&op.update(&encrypted_keyblob.tag)?)?;
        raw_key_material.try_extend_from_slice(&op.finish()?)?;
        if raw_key_material.len() != encrypted_keyblob.ciphertext.len() {
            return Err(km_err!(
                UnknownError,
                "deciphered len {} != encrypted len {}",
                raw_key_material.len(),
                encrypted_keyblob.ciphertext.len()
            ));
        }

        // Convert the key material into current form.
        let chars = &encrypted_keyblob.hw_enforced;
        let key_material = match get_tag_value!(chars, Algorithm, ErrorCode::InvalidKeyBlob)? {
            // Symmetric keys have the key material stored as raw bytes.
            Algorithm::Aes => {
                // Special case: an AES key might be a storage key.
                if get_bool_tag_value!(chars, StorageKey)? {
                    // Storage key is opaque data.
                    crypto::KeyMaterial::Aes(OpaqueOr::Opaque(OpaqueKeyMaterial(raw_key_material)))
                } else {
                    // Normal case: expect explicit AES key material.
                    crypto::KeyMaterial::Aes(crypto::aes::Key::new(raw_key_material)?.into())
                }
            }
            Algorithm::TripleDes => {
                crypto::KeyMaterial::TripleDes(crypto::des::Key::new(raw_key_material)?.into())
            }
            Algorithm::Hmac => {
                crypto::KeyMaterial::Hmac(crypto::hmac::Key::new(raw_key_material).into())
            }

            // RSA keys have key material stored as a PKCS#1 `RSAPrivateKey` structure, DER-encoded,
            // as decoded by the BoringSSL `RSA_parse_private_key()` function. This matches the
            // internal form of a [`crypto::rsa::Key`].
            Algorithm::Rsa => crypto::KeyMaterial::Rsa(crypto::rsa::Key(raw_key_material).into()),

            Algorithm::Ec => {
                // Determine the EC curve, allowing for old keys that don't include `EC_CURVE` tag.
                let ec_curve = match get_opt_tag_value!(chars, EcCurve)? {
                    Some(c) => *c,
                    None => match get_tag_value!(chars, KeySize, ErrorCode::InvalidKeyBlob)? {
                        KeySizeInBits(224) => EcCurve::P224,
                        KeySizeInBits(384) => EcCurve::P384,
                        KeySizeInBits(256) => {
                            return Err(km_err!(InvalidKeyBlob, "key size 256 ambiguous for EC"))
                        }
                        KeySizeInBits(521) => EcCurve::P521,
                        sz => return Err(km_err!(InvalidKeyBlob, "key size {:?} invalid", sz)),
                    },
                };
                match ec_curve {
                    // NIST curve EC keys are stored as an `ECPrivateKey` structure, DER-encoded, as
                    // decoded by the BoringSSL `EC_KEY_parse_private_key()` function. This matches
                    // the internal form of a [`crypto::ec::NistKey`].
                    EcCurve::P224 => crypto::KeyMaterial::Ec(
                        ec_curve,
                        crypto::CurveType::Nist,
                        crypto::ec::Key::P224(crypto::ec::NistKey(raw_key_material)).into(),
                    ),
                    EcCurve::P256 => crypto::KeyMaterial::Ec(
                        ec_curve,
                        crypto::CurveType::Nist,
                        crypto::ec::Key::P256(crypto::ec::NistKey(raw_key_material)).into(),
                    ),
                    EcCurve::P384 => crypto::KeyMaterial::Ec(
                        ec_curve,
                        crypto::CurveType::Nist,
                        crypto::ec::Key::P384(crypto::ec::NistKey(raw_key_material)).into(),
                    ),
                    EcCurve::P521 => crypto::KeyMaterial::Ec(
                        ec_curve,
                        crypto::CurveType::Nist,
                        crypto::ec::Key::P521(crypto::ec::NistKey(raw_key_material)).into(),
                    ),
                    EcCurve::Curve25519 => {
                        let key = crypto::ec::import_pkcs8_key(&raw_key_material)?;
                        if let crypto::KeyMaterial::Ec(EcCurve::Curve25519, curve_type, _ec_key) =
                            &key
                        {
                            match curve_type {
                                crypto::CurveType::Nist => {
                                    return Err(km_err!(
                                        InvalidKeyBlob,
                                        "unexpected NIST key with curve25519"
                                    ))
                                }
                                crypto::CurveType::Xdh => {
                                    if tag::primary_purpose(chars)? != KeyPurpose::AgreeKey {
                                        return Err(km_err!(
                                            InvalidKeyBlob,
                                            "purpose not AGREE_KEY for X25519 key"
                                        ));
                                    }
                                }
                                crypto::CurveType::EdDsa => {
                                    if tag::primary_purpose(chars)? == KeyPurpose::AgreeKey {
                                        return Err(km_err!(
                                            InvalidKeyBlob,
                                            "AGREE_KEY purpose for non-XDH 25519 key"
                                        ));
                                    }
                                }
                            }
                        } else {
                            return Err(km_err!(
                                InvalidKeyBlob,
                                "curve25519 key with wrong contents"
                            ));
                        }
                        key
                    }
                }
            }
        };

        Ok(keyblob::PlaintextKeyBlob { characteristics, key_material })
    }
}

impl keyblob::LegacyKeyHandler for TrustyLegacyKeyBlobHandler {
    fn convert_legacy_key(
        &self,
        keyblob: &[u8],
        params: &[KeyParam],
        root_of_trust: &BootInfo,
        sec_level: SecurityLevel,
    ) -> Result<keyblob::PlaintextKeyBlob, Error> {
        self.convert_key(keyblob, params, root_of_trust, sec_level)
    }

    fn delete_legacy_key(&mut self, keyblob: &[u8]) -> Result<(), Error> {
        let encrypted_keyblob = legacy::EncryptedKeyBlob::deserialize(keyblob)?;
        if let Some(slot) = encrypted_keyblob.key_slot {
            if slot != NO_SDD_SLOT_IDX {
                if !encrypted_keyblob.format.requires_secure_deletion() {
                    return Err(km_err!(
                        UnknownError,
                        "legacy keyblob of non-SDD format {:?} has non-empty SDD slot {:?}!",
                        encrypted_keyblob.format,
                        slot
                    ));
                }
                if let Some(sdd_mgr) = self.sdd_mgr.as_mut() {
                    if let Err(e) = sdd_mgr.delete_secret(SecureDeletionSlot(slot)) {
                        error!("failed to delete SDD slot {:?} for legacy key: {:?}", slot, e);
                    }
                } else {
                    error!("legacy key has SDD slot {:?} but no SDD mgr available!", slot);
                }
            }
        }
        Ok(())
    }
}
