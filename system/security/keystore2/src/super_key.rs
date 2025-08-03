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

use crate::{
    boot_level_keys::{get_level_zero_key, BootLevelKeyCache},
    database::BlobMetaData,
    database::BlobMetaEntry,
    database::EncryptedBy,
    database::KeyEntry,
    database::KeyType,
    database::{KeyEntryLoadBits, KeyIdGuard, KeyMetaData, KeyMetaEntry, KeystoreDB},
    ec_crypto::ECDHPrivateKey,
    enforcements::Enforcements,
    error::Error,
    error::ResponseCode,
    key_parameter::{KeyParameter, KeyParameterValue},
    ks_err,
    legacy_importer::LegacyImporter,
    raw_device::KeyMintDevice,
    utils::{watchdog as wd, AesGcm, AID_KEYSTORE},
};
use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Algorithm::Algorithm, BlockMode::BlockMode, HardwareAuthToken::HardwareAuthToken,
    HardwareAuthenticatorType::HardwareAuthenticatorType, KeyFormat::KeyFormat,
    KeyParameter::KeyParameter as KmKeyParameter, KeyPurpose::KeyPurpose, PaddingMode::PaddingMode,
    SecurityLevel::SecurityLevel,
};
use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, KeyDescriptor::KeyDescriptor,
};
use anyhow::{Context, Result};
use keystore2_crypto::{
    aes_gcm_decrypt, aes_gcm_encrypt, generate_aes256_key, generate_salt, Password, ZVec,
    AES_256_KEY_LENGTH,
};
use rustutils::system_properties::PropertyWatcher;
use std::{
    collections::HashMap,
    sync::Arc,
    sync::{Mutex, RwLock, Weak},
};
use std::{convert::TryFrom, ops::Deref};

const MAX_MAX_BOOT_LEVEL: usize = 1_000_000_000;
/// Allow up to 15 seconds between the user unlocking using a biometric, and the auth
/// token being used to unlock in [`SuperKeyManager::try_unlock_user_with_biometric`].
/// This seems short enough for security purposes, while long enough that even the
/// very slowest device will present the auth token in time.
const BIOMETRIC_AUTH_TIMEOUT_S: i32 = 15; // seconds

type UserId = u32;

/// Encryption algorithm used by a particular type of superencryption key
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SuperEncryptionAlgorithm {
    /// Symmetric encryption with AES-256-GCM
    Aes256Gcm,
    /// Public-key encryption with ECDH P-521
    EcdhP521,
}

/// A particular user may have several superencryption keys in the database, each for a
/// different purpose, distinguished by alias. Each is associated with a static
/// constant of this type.
pub struct SuperKeyType<'a> {
    /// Alias used to look up the key in the `persistent.keyentry` table.
    pub alias: &'a str,
    /// Encryption algorithm
    pub algorithm: SuperEncryptionAlgorithm,
    /// What to call this key in log messages. Not used for anything else.
    pub name: &'a str,
}

/// The user's AfterFirstUnlock super key. This super key is loaded into memory when the user first
/// unlocks the device, and it remains in memory until the device reboots. This is used to encrypt
/// keys that require user authentication but not an unlocked device.
pub const USER_AFTER_FIRST_UNLOCK_SUPER_KEY: SuperKeyType = SuperKeyType {
    alias: "USER_SUPER_KEY",
    algorithm: SuperEncryptionAlgorithm::Aes256Gcm,
    name: "AfterFirstUnlock super key",
};

/// The user's UnlockedDeviceRequired symmetric super key. This super key is loaded into memory each
/// time the user unlocks the device, and it is cleared from memory each time the user locks the
/// device. This is used to encrypt keys that use the UnlockedDeviceRequired key parameter.
pub const USER_UNLOCKED_DEVICE_REQUIRED_SYMMETRIC_SUPER_KEY: SuperKeyType = SuperKeyType {
    alias: "USER_SCREEN_LOCK_BOUND_KEY",
    algorithm: SuperEncryptionAlgorithm::Aes256Gcm,
    name: "UnlockedDeviceRequired symmetric super key",
};

/// The user's UnlockedDeviceRequired asymmetric super key. This is used to allow, while the device
/// is locked, the creation of keys that use the UnlockedDeviceRequired key parameter. The private
/// part of this key is loaded and cleared when the symmetric key is loaded and cleared.
pub const USER_UNLOCKED_DEVICE_REQUIRED_P521_SUPER_KEY: SuperKeyType = SuperKeyType {
    alias: "USER_SCREEN_LOCK_BOUND_P521_KEY",
    algorithm: SuperEncryptionAlgorithm::EcdhP521,
    name: "UnlockedDeviceRequired asymmetric super key",
};

/// Superencryption to apply to a new key.
#[derive(Debug, Clone, Copy)]
pub enum SuperEncryptionType {
    /// Do not superencrypt this key.
    None,
    /// Superencrypt with the AfterFirstUnlock super key.
    AfterFirstUnlock,
    /// Superencrypt with an UnlockedDeviceRequired super key.
    UnlockedDeviceRequired,
    /// Superencrypt with a key based on the desired boot level
    BootLevel(i32),
}

#[derive(Debug, Clone, Copy)]
pub enum SuperKeyIdentifier {
    /// id of the super key in the database.
    DatabaseId(i64),
    /// Boot level of the encrypting boot level key
    BootLevel(i32),
}

impl SuperKeyIdentifier {
    fn from_metadata(metadata: &BlobMetaData) -> Option<Self> {
        if let Some(EncryptedBy::KeyId(key_id)) = metadata.encrypted_by() {
            Some(SuperKeyIdentifier::DatabaseId(*key_id))
        } else {
            metadata.max_boot_level().map(|boot_level| SuperKeyIdentifier::BootLevel(*boot_level))
        }
    }

    fn add_to_metadata(&self, metadata: &mut BlobMetaData) {
        match self {
            SuperKeyIdentifier::DatabaseId(id) => {
                metadata.add(BlobMetaEntry::EncryptedBy(EncryptedBy::KeyId(*id)));
            }
            SuperKeyIdentifier::BootLevel(level) => {
                metadata.add(BlobMetaEntry::MaxBootLevel(*level));
            }
        }
    }
}

pub struct SuperKey {
    algorithm: SuperEncryptionAlgorithm,
    key: ZVec,
    /// Identifier of the encrypting key, used to write an encrypted blob
    /// back to the database after re-encryption eg on a key update.
    id: SuperKeyIdentifier,
    /// ECDH is more expensive than AES. So on ECDH private keys we set the
    /// reencrypt_with field to point at the corresponding AES key, and the
    /// keys will be re-encrypted with AES on first use.
    reencrypt_with: Option<Arc<SuperKey>>,
}

impl AesGcm for SuperKey {
    fn decrypt(&self, data: &[u8], iv: &[u8], tag: &[u8]) -> Result<ZVec> {
        if self.algorithm == SuperEncryptionAlgorithm::Aes256Gcm {
            aes_gcm_decrypt(data, iv, tag, &self.key).context(ks_err!("Decryption failed."))
        } else {
            Err(Error::sys()).context(ks_err!("Key is not an AES key."))
        }
    }

    fn encrypt(&self, plaintext: &[u8]) -> Result<(Vec<u8>, Vec<u8>, Vec<u8>)> {
        if self.algorithm == SuperEncryptionAlgorithm::Aes256Gcm {
            aes_gcm_encrypt(plaintext, &self.key).context(ks_err!("Encryption failed."))
        } else {
            Err(Error::sys()).context(ks_err!("Key is not an AES key."))
        }
    }
}

/// A SuperKey that has been encrypted with an AES-GCM key. For
/// encryption the key is in memory, and for decryption it is in KM.
struct LockedKey {
    algorithm: SuperEncryptionAlgorithm,
    id: SuperKeyIdentifier,
    nonce: Vec<u8>,
    ciphertext: Vec<u8>, // with tag appended
}

impl LockedKey {
    fn new(key: &[u8], to_encrypt: &Arc<SuperKey>) -> Result<Self> {
        let (mut ciphertext, nonce, mut tag) = aes_gcm_encrypt(&to_encrypt.key, key)?;
        ciphertext.append(&mut tag);
        Ok(LockedKey { algorithm: to_encrypt.algorithm, id: to_encrypt.id, nonce, ciphertext })
    }

    fn decrypt(
        &self,
        db: &mut KeystoreDB,
        km_dev: &KeyMintDevice,
        key_id_guard: &KeyIdGuard,
        key_entry: &KeyEntry,
        auth_token: &HardwareAuthToken,
        reencrypt_with: Option<Arc<SuperKey>>,
    ) -> Result<Arc<SuperKey>> {
        let key_blob = key_entry
            .key_blob_info()
            .as_ref()
            .map(|(key_blob, _)| KeyBlob::Ref(key_blob))
            .ok_or(Error::Rc(ResponseCode::KEY_NOT_FOUND))
            .context(ks_err!("Missing key blob info."))?;
        let key_params = vec![
            KeyParameterValue::Algorithm(Algorithm::AES),
            KeyParameterValue::KeySize(256),
            KeyParameterValue::BlockMode(BlockMode::GCM),
            KeyParameterValue::PaddingMode(PaddingMode::NONE),
            KeyParameterValue::Nonce(self.nonce.clone()),
            KeyParameterValue::MacLength(128),
        ];
        let key_params: Vec<KmKeyParameter> = key_params.into_iter().map(|x| x.into()).collect();
        let key = ZVec::try_from(km_dev.use_key_in_one_step(
            db,
            key_id_guard,
            &key_blob,
            KeyPurpose::DECRYPT,
            &key_params,
            Some(auth_token),
            &self.ciphertext,
        )?)?;
        Ok(Arc::new(SuperKey { algorithm: self.algorithm, key, id: self.id, reencrypt_with }))
    }
}

/// A user's UnlockedDeviceRequired super keys, encrypted with a biometric-bound key, and
/// information about that biometric-bound key.
struct BiometricUnlock {
    /// List of auth token SIDs that are accepted by the encrypting biometric-bound key.
    sids: Vec<i64>,
    /// Key descriptor of the encrypting biometric-bound key.
    key_desc: KeyDescriptor,
    /// The UnlockedDeviceRequired super keys, encrypted with a biometric-bound key.
    symmetric: LockedKey,
    private: LockedKey,
}

#[derive(Default)]
struct UserSuperKeys {
    /// The AfterFirstUnlock super key is used for synthetic password binding of authentication
    /// bound keys. There is one key per android user. The key is stored on flash encrypted with a
    /// key derived from a secret, that is itself derived from the user's synthetic password. (In
    /// most cases, the user's synthetic password can, in turn, only be decrypted using the user's
    /// Lock Screen Knowledge Factor or LSKF.) When the user unlocks the device for the first time,
    /// this key is unlocked, i.e., decrypted, and stays memory resident until the device reboots.
    after_first_unlock: Option<Arc<SuperKey>>,
    /// The UnlockedDeviceRequired symmetric super key works like the AfterFirstUnlock super key
    /// with the distinction that it is cleared from memory when the device is locked.
    unlocked_device_required_symmetric: Option<Arc<SuperKey>>,
    /// When the device is locked, keys that use the UnlockedDeviceRequired key parameter can still
    /// be created, using ECDH public-key encryption. This field holds the decryption private key.
    unlocked_device_required_private: Option<Arc<SuperKey>>,
    /// Versions of the above two keys, locked behind a biometric.
    biometric_unlock: Option<BiometricUnlock>,
}

#[derive(Default)]
struct SkmState {
    user_keys: HashMap<UserId, UserSuperKeys>,
    key_index: HashMap<i64, Weak<SuperKey>>,
    boot_level_key_cache: Option<Mutex<BootLevelKeyCache>>,
}

impl SkmState {
    fn add_key_to_key_index(&mut self, super_key: &Arc<SuperKey>) -> Result<()> {
        if let SuperKeyIdentifier::DatabaseId(id) = super_key.id {
            self.key_index.insert(id, Arc::downgrade(super_key));
            Ok(())
        } else {
            Err(Error::sys()).context(ks_err!("Cannot add key with ID {:?}", super_key.id))
        }
    }
}

#[derive(Default)]
pub struct SuperKeyManager {
    data: SkmState,
}

impl SuperKeyManager {
    pub fn set_up_boot_level_cache(skm: &Arc<RwLock<Self>>, db: &mut KeystoreDB) -> Result<()> {
        let mut skm_guard = skm.write().unwrap();
        if skm_guard.data.boot_level_key_cache.is_some() {
            log::info!("In set_up_boot_level_cache: called for a second time");
            return Ok(());
        }
        let level_zero_key =
            get_level_zero_key(db).context(ks_err!("get_level_zero_key failed"))?;
        skm_guard.data.boot_level_key_cache =
            Some(Mutex::new(BootLevelKeyCache::new(level_zero_key)));
        log::info!("Starting boot level watcher.");
        let clone = skm.clone();
        std::thread::spawn(move || {
            Self::watch_boot_level(clone)
                .unwrap_or_else(|e| log::error!("watch_boot_level failed:\n{:?}", e));
        });
        Ok(())
    }

    /// Watch the `keystore.boot_level` system property, and keep boot level up to date.
    /// Blocks waiting for system property changes, so must be run in its own thread.
    fn watch_boot_level(skm: Arc<RwLock<Self>>) -> Result<()> {
        let mut w = PropertyWatcher::new("keystore.boot_level")
            .context(ks_err!("PropertyWatcher::new failed"))?;
        loop {
            let level = w
                .read(|_n, v| v.parse::<usize>().map_err(std::convert::Into::into))
                .context(ks_err!("read of property failed"))?;

            // This scope limits the skm_guard life, so we don't hold the skm_guard while
            // waiting.
            {
                let mut skm_guard = skm.write().unwrap();
                let boot_level_key_cache = skm_guard
                    .data
                    .boot_level_key_cache
                    .as_mut()
                    .ok_or_else(Error::sys)
                    .context(ks_err!("Boot level cache not initialized"))?
                    .get_mut()
                    .unwrap();
                if level < MAX_MAX_BOOT_LEVEL {
                    log::info!("Read keystore.boot_level value {}", level);
                    boot_level_key_cache
                        .advance_boot_level(level)
                        .context(ks_err!("advance_boot_level failed"))?;
                } else {
                    log::info!(
                        "keystore.boot_level {} hits maximum {}, finishing.",
                        level,
                        MAX_MAX_BOOT_LEVEL
                    );
                    boot_level_key_cache.finish();
                    break;
                }
            }
            w.wait(None).context(ks_err!("property wait failed"))?;
        }
        Ok(())
    }

    pub fn level_accessible(&self, boot_level: i32) -> bool {
        self.data
            .boot_level_key_cache
            .as_ref()
            .map_or(false, |c| c.lock().unwrap().level_accessible(boot_level as usize))
    }

    pub fn forget_all_keys_for_user(&mut self, user: UserId) {
        self.data.user_keys.remove(&user);
    }

    fn install_after_first_unlock_key_for_user(
        &mut self,
        user: UserId,
        super_key: Arc<SuperKey>,
    ) -> Result<()> {
        self.data
            .add_key_to_key_index(&super_key)
            .context(ks_err!("add_key_to_key_index failed"))?;
        self.data.user_keys.entry(user).or_default().after_first_unlock = Some(super_key);
        Ok(())
    }

    fn lookup_key(&self, key_id: &SuperKeyIdentifier) -> Result<Option<Arc<SuperKey>>> {
        Ok(match key_id {
            SuperKeyIdentifier::DatabaseId(id) => {
                self.data.key_index.get(id).and_then(|k| k.upgrade())
            }
            SuperKeyIdentifier::BootLevel(level) => self
                .data
                .boot_level_key_cache
                .as_ref()
                .map(|b| b.lock().unwrap().aes_key(*level as usize))
                .transpose()
                .context(ks_err!("aes_key failed"))?
                .flatten()
                .map(|key| {
                    Arc::new(SuperKey {
                        algorithm: SuperEncryptionAlgorithm::Aes256Gcm,
                        key,
                        id: *key_id,
                        reencrypt_with: None,
                    })
                }),
        })
    }

    /// Returns the AfterFirstUnlock superencryption key for the given user ID, or None if the user
    /// has not yet unlocked the device since boot.
    pub fn get_after_first_unlock_key_by_user_id(
        &self,
        user_id: UserId,
    ) -> Option<Arc<dyn AesGcm + Send + Sync>> {
        self.get_after_first_unlock_key_by_user_id_internal(user_id)
            .map(|sk| -> Arc<dyn AesGcm + Send + Sync> { sk })
    }

    fn get_after_first_unlock_key_by_user_id_internal(
        &self,
        user_id: UserId,
    ) -> Option<Arc<SuperKey>> {
        self.data.user_keys.get(&user_id).and_then(|e| e.after_first_unlock.as_ref().cloned())
    }

    /// Check if a given key is super-encrypted, from its metadata. If so, unwrap the key using
    /// the relevant super key.
    pub fn unwrap_key_if_required<'a>(
        &self,
        metadata: &BlobMetaData,
        blob: &'a [u8],
    ) -> Result<KeyBlob<'a>> {
        Ok(if let Some(key_id) = SuperKeyIdentifier::from_metadata(metadata) {
            let super_key = self
                .lookup_key(&key_id)
                .context(ks_err!("lookup_key failed"))?
                .ok_or(Error::Rc(ResponseCode::LOCKED))
                .context(ks_err!("Required super decryption key is not in memory."))?;
            KeyBlob::Sensitive {
                key: Self::unwrap_key_with_key(blob, metadata, &super_key)
                    .context(ks_err!("unwrap_key_with_key failed"))?,
                reencrypt_with: super_key.reencrypt_with.as_ref().unwrap_or(&super_key).clone(),
                force_reencrypt: super_key.reencrypt_with.is_some(),
            }
        } else {
            KeyBlob::Ref(blob)
        })
    }

    /// Unwraps an encrypted key blob given an encryption key.
    fn unwrap_key_with_key(blob: &[u8], metadata: &BlobMetaData, key: &SuperKey) -> Result<ZVec> {
        match key.algorithm {
            SuperEncryptionAlgorithm::Aes256Gcm => match (metadata.iv(), metadata.aead_tag()) {
                (Some(iv), Some(tag)) => {
                    key.decrypt(blob, iv, tag).context(ks_err!("Failed to decrypt the key blob."))
                }
                (iv, tag) => Err(Error::Rc(ResponseCode::VALUE_CORRUPTED)).context(ks_err!(
                    "Key has incomplete metadata. Present: iv: {}, aead_tag: {}.",
                    iv.is_some(),
                    tag.is_some(),
                )),
            },
            SuperEncryptionAlgorithm::EcdhP521 => {
                match (metadata.public_key(), metadata.salt(), metadata.iv(), metadata.aead_tag()) {
                    (Some(public_key), Some(salt), Some(iv), Some(aead_tag)) => {
                        ECDHPrivateKey::from_private_key(&key.key)
                            .and_then(|k| k.decrypt_message(public_key, salt, iv, blob, aead_tag))
                            .context(ks_err!("Failed to decrypt the key blob with ECDH."))
                    }
                    (public_key, salt, iv, aead_tag) => {
                        Err(Error::Rc(ResponseCode::VALUE_CORRUPTED)).context(ks_err!(
                            concat!(
                                "Key has incomplete metadata. ",
                                "Present: public_key: {}, salt: {}, iv: {}, aead_tag: {}."
                            ),
                            public_key.is_some(),
                            salt.is_some(),
                            iv.is_some(),
                            aead_tag.is_some(),
                        ))
                    }
                }
            }
        }
    }

    /// Checks if the user's AfterFirstUnlock super key exists in the database (or legacy database).
    /// The reference to self is unused but it is required to prevent calling this function
    /// concurrently with skm state database changes.
    fn super_key_exists_in_db_for_user(
        &self,
        db: &mut KeystoreDB,
        legacy_importer: &LegacyImporter,
        user_id: UserId,
    ) -> Result<bool> {
        let key_in_db = db
            .key_exists(
                Domain::APP,
                user_id as u64 as i64,
                USER_AFTER_FIRST_UNLOCK_SUPER_KEY.alias,
                KeyType::Super,
            )
            .context(ks_err!())?;

        if key_in_db {
            Ok(key_in_db)
        } else {
            legacy_importer.has_super_key(user_id).context(ks_err!("Trying to query legacy db."))
        }
    }

    // Helper function to populate super key cache from the super key blob loaded from the database.
    fn populate_cache_from_super_key_blob(
        &mut self,
        user_id: UserId,
        algorithm: SuperEncryptionAlgorithm,
        entry: KeyEntry,
        pw: &Password,
    ) -> Result<Arc<SuperKey>> {
        let super_key = Self::extract_super_key_from_key_entry(algorithm, entry, pw, None)
            .context(ks_err!("Failed to extract super key from key entry"))?;
        self.install_after_first_unlock_key_for_user(user_id, super_key.clone())
            .context(ks_err!("Failed to install AfterFirstUnlock super key for user!"))?;
        Ok(super_key)
    }

    /// Extracts super key from the entry loaded from the database.
    pub fn extract_super_key_from_key_entry(
        algorithm: SuperEncryptionAlgorithm,
        entry: KeyEntry,
        pw: &Password,
        reencrypt_with: Option<Arc<SuperKey>>,
    ) -> Result<Arc<SuperKey>> {
        if let Some((blob, metadata)) = entry.key_blob_info() {
            let key = match (
                metadata.encrypted_by(),
                metadata.salt(),
                metadata.iv(),
                metadata.aead_tag(),
            ) {
                (Some(&EncryptedBy::Password), Some(salt), Some(iv), Some(tag)) => {
                    // Note that password encryption is AES no matter the value of algorithm.
                    let key = pw
                        .derive_key_hkdf(salt, AES_256_KEY_LENGTH)
                        .context(ks_err!("Failed to derive key from password."))?;

                    aes_gcm_decrypt(blob, iv, tag, &key).or_else(|_e| {
                        // Handle old key stored before the switch to HKDF.
                        let key = pw
                            .derive_key_pbkdf2(salt, AES_256_KEY_LENGTH)
                            .context(ks_err!("Failed to derive key from password (PBKDF2)."))?;
                        aes_gcm_decrypt(blob, iv, tag, &key)
                            .context(ks_err!("Failed to decrypt key blob."))
                    })?
                }
                (enc_by, salt, iv, tag) => {
                    return Err(Error::Rc(ResponseCode::VALUE_CORRUPTED)).context(ks_err!(
                        concat!(
                            "Super key has incomplete metadata.",
                            "encrypted_by: {:?}; Present: salt: {}, iv: {}, aead_tag: {}."
                        ),
                        enc_by,
                        salt.is_some(),
                        iv.is_some(),
                        tag.is_some()
                    ));
                }
            };
            Ok(Arc::new(SuperKey {
                algorithm,
                key,
                id: SuperKeyIdentifier::DatabaseId(entry.id()),
                reencrypt_with,
            }))
        } else {
            Err(Error::Rc(ResponseCode::VALUE_CORRUPTED)).context(ks_err!("No key blob info."))
        }
    }

    /// Encrypts the super key from a key derived from the password, before storing in the database.
    /// This does not stretch the password; i.e., it assumes that the password is a high-entropy
    /// synthetic password, not a low-entropy user provided password.
    pub fn encrypt_with_password(
        super_key: &[u8],
        pw: &Password,
    ) -> Result<(Vec<u8>, BlobMetaData)> {
        let salt = generate_salt().context("In encrypt_with_password: Failed to generate salt.")?;
        let derived_key = if android_security_flags::fix_unlocked_device_required_keys_v2() {
            pw.derive_key_hkdf(&salt, AES_256_KEY_LENGTH)
                .context(ks_err!("Failed to derive key from password."))?
        } else {
            pw.derive_key_pbkdf2(&salt, AES_256_KEY_LENGTH)
                .context(ks_err!("Failed to derive password."))?
        };
        let mut metadata = BlobMetaData::new();
        metadata.add(BlobMetaEntry::EncryptedBy(EncryptedBy::Password));
        metadata.add(BlobMetaEntry::Salt(salt));
        let (encrypted_key, iv, tag) = aes_gcm_encrypt(super_key, &derived_key)
            .context(ks_err!("Failed to encrypt new super key."))?;
        metadata.add(BlobMetaEntry::Iv(iv));
        metadata.add(BlobMetaEntry::AeadTag(tag));
        Ok((encrypted_key, metadata))
    }

    // Helper function to encrypt a key with the given super key. Callers should select which super
    // key to be used. This is called when a key is super encrypted at its creation as well as at
    // its upgrade.
    fn encrypt_with_aes_super_key(
        key_blob: &[u8],
        super_key: &SuperKey,
    ) -> Result<(Vec<u8>, BlobMetaData)> {
        if super_key.algorithm != SuperEncryptionAlgorithm::Aes256Gcm {
            return Err(Error::sys()).context(ks_err!("unexpected algorithm"));
        }
        let mut metadata = BlobMetaData::new();
        let (encrypted_key, iv, tag) = aes_gcm_encrypt(key_blob, &(super_key.key))
            .context(ks_err!("Failed to encrypt new super key."))?;
        metadata.add(BlobMetaEntry::Iv(iv));
        metadata.add(BlobMetaEntry::AeadTag(tag));
        super_key.id.add_to_metadata(&mut metadata);
        Ok((encrypted_key, metadata))
    }

    // Encrypts a given key_blob using a hybrid approach, which can either use the symmetric super
    // key or the public super key depending on which is available.
    //
    // If the symmetric_key is available, the key_blob is encrypted using symmetric encryption with
    // the provided symmetric super key.  Otherwise, the function loads the public super key from
    // the KeystoreDB and encrypts the key_blob using ECDH encryption and marks the keyblob to be
    // re-encrypted with the symmetric super key on the first use.
    //
    // This hybrid scheme allows keys that use the UnlockedDeviceRequired key parameter to be
    // created while the device is locked.
    fn encrypt_with_hybrid_super_key(
        key_blob: &[u8],
        symmetric_key: Option<&SuperKey>,
        public_key_type: &SuperKeyType,
        db: &mut KeystoreDB,
        user_id: UserId,
    ) -> Result<(Vec<u8>, BlobMetaData)> {
        if let Some(super_key) = symmetric_key {
            Self::encrypt_with_aes_super_key(key_blob, super_key).context(ks_err!(
                "Failed to encrypt with UnlockedDeviceRequired symmetric super key."
            ))
        } else {
            // Symmetric key is not available, use public key encryption
            let loaded = db
                .load_super_key(public_key_type, user_id)
                .context(ks_err!("load_super_key failed."))?;
            let (key_id_guard, key_entry) =
                loaded.ok_or_else(Error::sys).context(ks_err!("User ECDH super key missing."))?;
            let public_key = key_entry
                .metadata()
                .sec1_public_key()
                .ok_or_else(Error::sys)
                .context(ks_err!("sec1_public_key missing."))?;
            let mut metadata = BlobMetaData::new();
            let (ephem_key, salt, iv, encrypted_key, aead_tag) =
                ECDHPrivateKey::encrypt_message(public_key, key_blob)
                    .context(ks_err!("ECDHPrivateKey::encrypt_message failed."))?;
            metadata.add(BlobMetaEntry::PublicKey(ephem_key));
            metadata.add(BlobMetaEntry::Salt(salt));
            metadata.add(BlobMetaEntry::Iv(iv));
            metadata.add(BlobMetaEntry::AeadTag(aead_tag));
            SuperKeyIdentifier::DatabaseId(key_id_guard.id()).add_to_metadata(&mut metadata);
            Ok((encrypted_key, metadata))
        }
    }

    /// Check if super encryption is required and if so, super-encrypt the key to be stored in
    /// the database.
    #[allow(clippy::too_many_arguments)]
    pub fn handle_super_encryption_on_key_init(
        &self,
        db: &mut KeystoreDB,
        legacy_importer: &LegacyImporter,
        domain: &Domain,
        key_parameters: &[KeyParameter],
        flags: Option<i32>,
        user_id: UserId,
        key_blob: &[u8],
    ) -> Result<(Vec<u8>, BlobMetaData)> {
        match Enforcements::super_encryption_required(domain, key_parameters, flags) {
            SuperEncryptionType::None => Ok((key_blob.to_vec(), BlobMetaData::new())),
            SuperEncryptionType::AfterFirstUnlock => {
                // Encrypt the given key blob with the user's AfterFirstUnlock super key. If the
                // user has not unlocked the device since boot or the super keys were never
                // initialized for the user for some reason, an error is returned.
                match self
                    .get_user_state(db, legacy_importer, user_id)
                    .context(ks_err!("Failed to get user state for user {user_id}"))?
                {
                    UserState::AfterFirstUnlock(super_key) => {
                        Self::encrypt_with_aes_super_key(key_blob, &super_key).context(ks_err!(
                            "Failed to encrypt with AfterFirstUnlock super key for user {user_id}"
                        ))
                    }
                    UserState::BeforeFirstUnlock => {
                        Err(Error::Rc(ResponseCode::LOCKED)).context(ks_err!("Device is locked."))
                    }
                    UserState::Uninitialized => Err(Error::Rc(ResponseCode::UNINITIALIZED))
                        .context(ks_err!("User {user_id} does not have super keys")),
                }
            }
            SuperEncryptionType::UnlockedDeviceRequired => {
                let symmetric_key = self
                    .data
                    .user_keys
                    .get(&user_id)
                    .and_then(|e| e.unlocked_device_required_symmetric.as_ref())
                    .map(|arc| arc.as_ref());
                Self::encrypt_with_hybrid_super_key(
                    key_blob,
                    symmetric_key,
                    &USER_UNLOCKED_DEVICE_REQUIRED_P521_SUPER_KEY,
                    db,
                    user_id,
                )
                .context(ks_err!("Failed to encrypt with UnlockedDeviceRequired hybrid scheme."))
            }
            SuperEncryptionType::BootLevel(level) => {
                let key_id = SuperKeyIdentifier::BootLevel(level);
                let super_key = self
                    .lookup_key(&key_id)
                    .context(ks_err!("lookup_key failed"))?
                    .ok_or(Error::Rc(ResponseCode::LOCKED))
                    .context(ks_err!("Boot stage key absent"))?;
                Self::encrypt_with_aes_super_key(key_blob, &super_key)
                    .context(ks_err!("Failed to encrypt with BootLevel key."))
            }
        }
    }

    /// Check if a given key needs re-super-encryption, from its KeyBlob type.
    /// If so, re-super-encrypt the key and return a new set of metadata,
    /// containing the new super encryption information.
    pub fn reencrypt_if_required<'a>(
        key_blob_before_upgrade: &KeyBlob,
        key_after_upgrade: &'a [u8],
    ) -> Result<(KeyBlob<'a>, Option<BlobMetaData>)> {
        match key_blob_before_upgrade {
            KeyBlob::Sensitive { reencrypt_with: super_key, .. } => {
                let (key, metadata) =
                    Self::encrypt_with_aes_super_key(key_after_upgrade, super_key)
                        .context(ks_err!("Failed to re-super-encrypt key."))?;
                Ok((KeyBlob::NonSensitive(key), Some(metadata)))
            }
            _ => Ok((KeyBlob::Ref(key_after_upgrade), None)),
        }
    }

    fn create_super_key(
        &mut self,
        db: &mut KeystoreDB,
        user_id: UserId,
        key_type: &SuperKeyType,
        password: &Password,
        reencrypt_with: Option<Arc<SuperKey>>,
    ) -> Result<Arc<SuperKey>> {
        log::info!("Creating {} for user {}", key_type.name, user_id);
        let (super_key, public_key) = match key_type.algorithm {
            SuperEncryptionAlgorithm::Aes256Gcm => {
                (generate_aes256_key().context(ks_err!("Failed to generate AES-256 key."))?, None)
            }
            SuperEncryptionAlgorithm::EcdhP521 => {
                let key =
                    ECDHPrivateKey::generate().context(ks_err!("Failed to generate ECDH key"))?;
                (
                    key.private_key().context(ks_err!("private_key failed"))?,
                    Some(key.public_key().context(ks_err!("public_key failed"))?),
                )
            }
        };
        // Derive an AES-256 key from the password and re-encrypt the super key before we insert it
        // in the database.
        let (encrypted_super_key, blob_metadata) =
            Self::encrypt_with_password(&super_key, password).context(ks_err!())?;
        let mut key_metadata = KeyMetaData::new();
        if let Some(pk) = public_key {
            key_metadata.add(KeyMetaEntry::Sec1PublicKey(pk));
        }
        let key_entry = db
            .store_super_key(user_id, key_type, &encrypted_super_key, &blob_metadata, &key_metadata)
            .context(ks_err!("Failed to store super key."))?;
        Ok(Arc::new(SuperKey {
            algorithm: key_type.algorithm,
            key: super_key,
            id: SuperKeyIdentifier::DatabaseId(key_entry.id()),
            reencrypt_with,
        }))
    }

    /// Fetch a superencryption key from the database, or create it if it doesn't already exist.
    /// When this is called, the caller must hold the lock on the SuperKeyManager.
    /// So it's OK that the check and creation are different DB transactions.
    fn get_or_create_super_key(
        &mut self,
        db: &mut KeystoreDB,
        user_id: UserId,
        key_type: &SuperKeyType,
        password: &Password,
        reencrypt_with: Option<Arc<SuperKey>>,
    ) -> Result<Arc<SuperKey>> {
        let loaded_key = db.load_super_key(key_type, user_id)?;
        if let Some((_, key_entry)) = loaded_key {
            Ok(Self::extract_super_key_from_key_entry(
                key_type.algorithm,
                key_entry,
                password,
                reencrypt_with,
            )?)
        } else {
            self.create_super_key(db, user_id, key_type, password, reencrypt_with)
        }
    }

    /// Decrypt the UnlockedDeviceRequired super keys for this user using the password and store
    /// them in memory. If these keys don't exist yet, create them.
    pub fn unlock_unlocked_device_required_keys(
        &mut self,
        db: &mut KeystoreDB,
        user_id: UserId,
        password: &Password,
    ) -> Result<()> {
        let (symmetric, private) = self
            .data
            .user_keys
            .get(&user_id)
            .map(|e| {
                (
                    e.unlocked_device_required_symmetric.clone(),
                    e.unlocked_device_required_private.clone(),
                )
            })
            .unwrap_or((None, None));

        if symmetric.is_some() && private.is_some() {
            // Already unlocked.
            return Ok(());
        }

        let aes = if let Some(symmetric) = symmetric {
            // This is weird. If this point is reached only one of the UnlockedDeviceRequired super
            // keys was initialized. This should never happen.
            symmetric
        } else {
            self.get_or_create_super_key(
                db,
                user_id,
                &USER_UNLOCKED_DEVICE_REQUIRED_SYMMETRIC_SUPER_KEY,
                password,
                None,
            )
            .context(ks_err!("Trying to get or create symmetric key."))?
        };

        let ecdh = if let Some(private) = private {
            // This is weird. If this point is reached only one of the UnlockedDeviceRequired super
            // keys was initialized. This should never happen.
            private
        } else {
            self.get_or_create_super_key(
                db,
                user_id,
                &USER_UNLOCKED_DEVICE_REQUIRED_P521_SUPER_KEY,
                password,
                Some(aes.clone()),
            )
            .context(ks_err!("Trying to get or create asymmetric key."))?
        };

        self.data.add_key_to_key_index(&aes)?;
        self.data.add_key_to_key_index(&ecdh)?;
        let entry = self.data.user_keys.entry(user_id).or_default();
        entry.unlocked_device_required_symmetric = Some(aes);
        entry.unlocked_device_required_private = Some(ecdh);
        Ok(())
    }

    /// Protects the user's UnlockedDeviceRequired super keys in a way such that they can only be
    /// unlocked by the enabled unlock methods.
    pub fn lock_unlocked_device_required_keys(
        &mut self,
        db: &mut KeystoreDB,
        user_id: UserId,
        unlocking_sids: &[i64],
        weak_unlock_enabled: bool,
    ) {
        let entry = self.data.user_keys.entry(user_id).or_default();
        if unlocking_sids.is_empty() {
            if android_security_flags::fix_unlocked_device_required_keys_v2() {
                entry.biometric_unlock = None;
            }
        } else if let (Some(aes), Some(ecdh)) = (
            entry.unlocked_device_required_symmetric.as_ref().cloned(),
            entry.unlocked_device_required_private.as_ref().cloned(),
        ) {
            // If class 3 biometric unlock methods are enabled, create a biometric-encrypted copy of
            // the keys.  Do this even if weak unlock methods are enabled too; in that case we'll
            // also retain a plaintext copy of the keys, but that copy will be wiped later if weak
            // unlock methods expire.  So we need the biometric-encrypted copy too just in case.
            let res = (|| -> Result<()> {
                let key_desc =
                    KeyMintDevice::internal_descriptor(format!("biometric_unlock_key_{}", user_id));
                let encrypting_key = generate_aes256_key()?;
                let km_dev: KeyMintDevice = KeyMintDevice::get(SecurityLevel::TRUSTED_ENVIRONMENT)
                    .context(ks_err!("KeyMintDevice::get failed"))?;
                let mut key_params = vec![
                    KeyParameterValue::Algorithm(Algorithm::AES),
                    KeyParameterValue::KeySize(256),
                    KeyParameterValue::BlockMode(BlockMode::GCM),
                    KeyParameterValue::PaddingMode(PaddingMode::NONE),
                    KeyParameterValue::CallerNonce,
                    KeyParameterValue::KeyPurpose(KeyPurpose::DECRYPT),
                    KeyParameterValue::MinMacLength(128),
                    KeyParameterValue::AuthTimeout(BIOMETRIC_AUTH_TIMEOUT_S),
                    KeyParameterValue::HardwareAuthenticatorType(
                        HardwareAuthenticatorType::FINGERPRINT,
                    ),
                ];
                for sid in unlocking_sids {
                    key_params.push(KeyParameterValue::UserSecureID(*sid));
                }
                let key_params: Vec<KmKeyParameter> =
                    key_params.into_iter().map(|x| x.into()).collect();
                km_dev.create_and_store_key(
                    db,
                    &key_desc,
                    KeyType::Client, /* TODO Should be Super b/189470584 */
                    |dev| {
                        let _wp = wd::watch_millis(
                            "In lock_unlocked_device_required_keys: calling importKey.",
                            500,
                        );
                        dev.importKey(key_params.as_slice(), KeyFormat::RAW, &encrypting_key, None)
                    },
                )?;
                entry.biometric_unlock = Some(BiometricUnlock {
                    sids: unlocking_sids.into(),
                    key_desc,
                    symmetric: LockedKey::new(&encrypting_key, &aes)?,
                    private: LockedKey::new(&encrypting_key, &ecdh)?,
                });
                Ok(())
            })();
            if let Err(e) = res {
                log::error!("Error setting up biometric unlock: {:#?}", e);
                // The caller can't do anything about the error, and for security reasons we still
                // wipe the keys (unless a weak unlock method is enabled).  So just log the error.
            }
        }
        // Wipe the plaintext copy of the keys, unless a weak unlock method is enabled.
        if !weak_unlock_enabled {
            entry.unlocked_device_required_symmetric = None;
            entry.unlocked_device_required_private = None;
        }
        Self::log_status_of_unlocked_device_required_keys(user_id, entry);
    }

    pub fn wipe_plaintext_unlocked_device_required_keys(&mut self, user_id: UserId) {
        let entry = self.data.user_keys.entry(user_id).or_default();
        entry.unlocked_device_required_symmetric = None;
        entry.unlocked_device_required_private = None;
        Self::log_status_of_unlocked_device_required_keys(user_id, entry);
    }

    pub fn wipe_all_unlocked_device_required_keys(&mut self, user_id: UserId) {
        let entry = self.data.user_keys.entry(user_id).or_default();
        entry.unlocked_device_required_symmetric = None;
        entry.unlocked_device_required_private = None;
        entry.biometric_unlock = None;
        Self::log_status_of_unlocked_device_required_keys(user_id, entry);
    }

    fn log_status_of_unlocked_device_required_keys(user_id: UserId, entry: &UserSuperKeys) {
        let status = match (
            // Note: the status of the symmetric and private keys should always be in sync.
            // So we only check one here.
            entry.unlocked_device_required_symmetric.is_some(),
            entry.biometric_unlock.is_some(),
        ) {
            (false, false) => "fully protected",
            (false, true) => "biometric-encrypted",
            (true, false) => "retained in plaintext",
            (true, true) => "retained in plaintext, with biometric-encrypted copy too",
        };
        log::info!("UnlockedDeviceRequired super keys for user {user_id} are {status}.");
    }

    /// User has unlocked, not using a password. See if any of our stored auth tokens can be used
    /// to unlock the keys protecting UNLOCKED_DEVICE_REQUIRED keys.
    pub fn try_unlock_user_with_biometric(
        &mut self,
        db: &mut KeystoreDB,
        user_id: UserId,
    ) -> Result<()> {
        let entry = self.data.user_keys.entry(user_id).or_default();
        if android_security_flags::fix_unlocked_device_required_keys_v2()
            && entry.unlocked_device_required_symmetric.is_some()
            && entry.unlocked_device_required_private.is_some()
        {
            // If the keys are already cached in plaintext, then there is no need to decrypt the
            // biometric-encrypted copy.  Both copies can be present here if the user has both
            // class 3 biometric and weak unlock methods enabled, and the device was unlocked before
            // the weak unlock methods expired.
            return Ok(());
        }
        if let Some(biometric) = entry.biometric_unlock.as_ref() {
            let (key_id_guard, key_entry) = db
                .load_key_entry(
                    &biometric.key_desc,
                    KeyType::Client, // This should not be a Client key.
                    KeyEntryLoadBits::KM,
                    AID_KEYSTORE,
                    |_, _| Ok(()),
                )
                .context(ks_err!("load_key_entry failed"))?;
            let km_dev: KeyMintDevice = KeyMintDevice::get(SecurityLevel::TRUSTED_ENVIRONMENT)
                .context(ks_err!("KeyMintDevice::get failed"))?;
            let mut errs = vec![];
            for sid in &biometric.sids {
                let sid = *sid;
                if let Some((auth_token_entry, _)) = db.find_auth_token_entry(|entry| {
                    entry.auth_token().userId == sid || entry.auth_token().authenticatorId == sid
                }) {
                    let res: Result<(Arc<SuperKey>, Arc<SuperKey>)> = (|| {
                        let symmetric = biometric.symmetric.decrypt(
                            db,
                            &km_dev,
                            &key_id_guard,
                            &key_entry,
                            auth_token_entry.auth_token(),
                            None,
                        )?;
                        let private = biometric.private.decrypt(
                            db,
                            &km_dev,
                            &key_id_guard,
                            &key_entry,
                            auth_token_entry.auth_token(),
                            Some(symmetric.clone()),
                        )?;
                        Ok((symmetric, private))
                    })();
                    match res {
                        Ok((symmetric, private)) => {
                            entry.unlocked_device_required_symmetric = Some(symmetric.clone());
                            entry.unlocked_device_required_private = Some(private.clone());
                            self.data.add_key_to_key_index(&symmetric)?;
                            self.data.add_key_to_key_index(&private)?;
                            log::info!("Successfully unlocked user {user_id} with biometric {sid}",);
                            return Ok(());
                        }
                        Err(e) => {
                            // Don't log an error yet, as some other biometric SID might work.
                            errs.push((sid, e));
                        }
                    }
                }
            }
            if !errs.is_empty() {
                log::warn!("biometric unlock failed for all SIDs, with errors:");
                for (sid, err) in errs {
                    log::warn!("  biometric {sid}: {err}");
                }
            }
        }
        Ok(())
    }

    /// Returns the keystore locked state of the given user. It requires the thread local
    /// keystore database and a reference to the legacy migrator because it may need to
    /// import the super key from the legacy blob database to the keystore database.
    pub fn get_user_state(
        &self,
        db: &mut KeystoreDB,
        legacy_importer: &LegacyImporter,
        user_id: UserId,
    ) -> Result<UserState> {
        match self.get_after_first_unlock_key_by_user_id_internal(user_id) {
            Some(super_key) => Ok(UserState::AfterFirstUnlock(super_key)),
            None => {
                // Check if a super key exists in the database or legacy database.
                // If so, return locked user state.
                if self
                    .super_key_exists_in_db_for_user(db, legacy_importer, user_id)
                    .context(ks_err!())?
                {
                    Ok(UserState::BeforeFirstUnlock)
                } else {
                    Ok(UserState::Uninitialized)
                }
            }
        }
    }

    /// Deletes all keys and super keys for the given user.
    /// This is called when a user is deleted.
    pub fn remove_user(
        &mut self,
        db: &mut KeystoreDB,
        legacy_importer: &LegacyImporter,
        user_id: UserId,
    ) -> Result<()> {
        log::info!("remove_user(user={user_id})");
        // Mark keys created on behalf of the user as unreferenced.
        legacy_importer
            .bulk_delete_user(user_id, false)
            .context(ks_err!("Trying to delete legacy keys."))?;
        db.unbind_keys_for_user(user_id, false).context(ks_err!("Error in unbinding keys."))?;

        // Delete super key in cache, if exists.
        self.forget_all_keys_for_user(user_id);
        Ok(())
    }

    /// Deletes all authentication bound keys and super keys for the given user.  The user must be
    /// unlocked before this function is called.  This function is used to transition a user to
    /// swipe.
    pub fn reset_user(
        &mut self,
        db: &mut KeystoreDB,
        legacy_importer: &LegacyImporter,
        user_id: UserId,
    ) -> Result<()> {
        log::info!("reset_user(user={user_id})");
        match self.get_user_state(db, legacy_importer, user_id)? {
            UserState::Uninitialized => {
                Err(Error::sys()).context(ks_err!("Tried to reset an uninitialized user!"))
            }
            UserState::BeforeFirstUnlock => {
                Err(Error::sys()).context(ks_err!("Tried to reset a locked user's password!"))
            }
            UserState::AfterFirstUnlock(_) => {
                // Mark keys created on behalf of the user as unreferenced.
                legacy_importer
                    .bulk_delete_user(user_id, true)
                    .context(ks_err!("Trying to delete legacy keys."))?;
                db.unbind_keys_for_user(user_id, true)
                    .context(ks_err!("Error in unbinding keys."))?;

                // Delete super key in cache, if exists.
                self.forget_all_keys_for_user(user_id);
                Ok(())
            }
        }
    }

    /// If the user hasn't been initialized yet, then this function generates the user's
    /// AfterFirstUnlock super key and sets the user's state to AfterFirstUnlock. Otherwise this
    /// function returns an error.
    pub fn init_user(
        &mut self,
        db: &mut KeystoreDB,
        legacy_importer: &LegacyImporter,
        user_id: UserId,
        password: &Password,
    ) -> Result<()> {
        log::info!("init_user(user={user_id})");
        match self.get_user_state(db, legacy_importer, user_id)? {
            UserState::AfterFirstUnlock(_) | UserState::BeforeFirstUnlock => {
                Err(Error::sys()).context(ks_err!("Tried to re-init an initialized user!"))
            }
            UserState::Uninitialized => {
                // Generate a new super key.
                let super_key =
                    generate_aes256_key().context(ks_err!("Failed to generate AES 256 key."))?;
                // Derive an AES256 key from the password and re-encrypt the super key
                // before we insert it in the database.
                let (encrypted_super_key, blob_metadata) =
                    Self::encrypt_with_password(&super_key, password)
                        .context(ks_err!("Failed to encrypt super key with password!"))?;

                let key_entry = db
                    .store_super_key(
                        user_id,
                        &USER_AFTER_FIRST_UNLOCK_SUPER_KEY,
                        &encrypted_super_key,
                        &blob_metadata,
                        &KeyMetaData::new(),
                    )
                    .context(ks_err!("Failed to store super key."))?;

                self.populate_cache_from_super_key_blob(
                    user_id,
                    USER_AFTER_FIRST_UNLOCK_SUPER_KEY.algorithm,
                    key_entry,
                    password,
                )
                .context(ks_err!("Failed to initialize user!"))?;
                Ok(())
            }
        }
    }

    /// Initializes the given user by creating their super keys, both AfterFirstUnlock and
    /// UnlockedDeviceRequired. If allow_existing is true, then the user already being initialized
    /// is not considered an error.
    pub fn initialize_user(
        &mut self,
        db: &mut KeystoreDB,
        legacy_importer: &LegacyImporter,
        user_id: UserId,
        password: &Password,
        allow_existing: bool,
    ) -> Result<()> {
        // Create the AfterFirstUnlock super key.
        if self.super_key_exists_in_db_for_user(db, legacy_importer, user_id)? {
            log::info!("AfterFirstUnlock super key already exists");
            if !allow_existing {
                return Err(Error::sys()).context(ks_err!("Tried to re-init an initialized user!"));
            }
        } else {
            let super_key = self
                .create_super_key(db, user_id, &USER_AFTER_FIRST_UNLOCK_SUPER_KEY, password, None)
                .context(ks_err!("Failed to create AfterFirstUnlock super key"))?;

            self.install_after_first_unlock_key_for_user(user_id, super_key)
                .context(ks_err!("Failed to install AfterFirstUnlock super key for user"))?;
        }

        // Create the UnlockedDeviceRequired super keys.
        self.unlock_unlocked_device_required_keys(db, user_id, password)
            .context(ks_err!("Failed to create UnlockedDeviceRequired super keys"))
    }

    /// Unlocks the given user with the given password.
    ///
    /// If the user state is BeforeFirstUnlock:
    /// - Unlock the user's AfterFirstUnlock super key
    /// - Unlock the user's UnlockedDeviceRequired super keys
    ///
    /// If the user state is AfterFirstUnlock:
    /// - Unlock the user's UnlockedDeviceRequired super keys only
    ///
    pub fn unlock_user(
        &mut self,
        db: &mut KeystoreDB,
        legacy_importer: &LegacyImporter,
        user_id: UserId,
        password: &Password,
    ) -> Result<()> {
        log::info!("unlock_user(user={user_id})");
        match self.get_user_state(db, legacy_importer, user_id)? {
            UserState::AfterFirstUnlock(_) => {
                self.unlock_unlocked_device_required_keys(db, user_id, password)
            }
            UserState::Uninitialized => {
                Err(Error::sys()).context(ks_err!("Tried to unlock an uninitialized user!"))
            }
            UserState::BeforeFirstUnlock => {
                let alias = &USER_AFTER_FIRST_UNLOCK_SUPER_KEY;
                let result = legacy_importer
                    .with_try_import_super_key(user_id, password, || {
                        db.load_super_key(alias, user_id)
                    })
                    .context(ks_err!("Failed to load super key"))?;

                match result {
                    Some((_, entry)) => {
                        self.populate_cache_from_super_key_blob(
                            user_id,
                            alias.algorithm,
                            entry,
                            password,
                        )
                        .context(ks_err!("Failed when unlocking user."))?;
                        self.unlock_unlocked_device_required_keys(db, user_id, password)
                    }
                    None => {
                        Err(Error::sys()).context(ks_err!("Locked user does not have a super key!"))
                    }
                }
            }
        }
    }
}

/// This enum represents different states of the user's life cycle in the device.
/// For now, only three states are defined. More states may be added later.
pub enum UserState {
    // The user's super keys exist, and the user has unlocked the device at least once since boot.
    // Hence, the AfterFirstUnlock super key is available in the cache.
    AfterFirstUnlock(Arc<SuperKey>),
    // The user's super keys exist, but the user hasn't unlocked the device at least once since
    // boot. Hence, the AfterFirstUnlock and UnlockedDeviceRequired super keys are not available in
    // the cache. However, they exist in the database in encrypted form.
    BeforeFirstUnlock,
    // The user's super keys don't exist. I.e., there's no user with the given user ID, or the user
    // is in the process of being created or destroyed.
    Uninitialized,
}

/// This enum represents three states a KeyMint Blob can be in, w.r.t super encryption.
/// `Sensitive` holds the non encrypted key and a reference to its super key.
/// `NonSensitive` holds a non encrypted key that is never supposed to be encrypted.
/// `Ref` holds a reference to a key blob when it does not need to be modified if its
/// life time allows it.
pub enum KeyBlob<'a> {
    Sensitive {
        key: ZVec,
        /// If KeyMint reports that the key must be upgraded, we must
        /// re-encrypt the key before writing to the database; we use
        /// this key.
        reencrypt_with: Arc<SuperKey>,
        /// If this key was decrypted with an ECDH key, we want to
        /// re-encrypt it on first use whether it was upgraded or not;
        /// this field indicates that that's necessary.
        force_reencrypt: bool,
    },
    NonSensitive(Vec<u8>),
    Ref(&'a [u8]),
}

impl<'a> KeyBlob<'a> {
    pub fn force_reencrypt(&self) -> bool {
        if let KeyBlob::Sensitive { force_reencrypt, .. } = self {
            *force_reencrypt
        } else {
            false
        }
    }
}

/// Deref returns a reference to the key material in any variant.
impl<'a> Deref for KeyBlob<'a> {
    type Target = [u8];

    fn deref(&self) -> &Self::Target {
        match self {
            Self::Sensitive { key, .. } => key,
            Self::NonSensitive(key) => key,
            Self::Ref(key) => key,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::database::tests::make_bootlevel_key_entry;
    use crate::database::tests::make_test_key_entry;
    use crate::database::tests::new_test_db;
    use rand::prelude::*;
    const USER_ID: u32 = 0;
    const TEST_KEY_ALIAS: &str = "TEST_KEY";
    const TEST_BOOT_KEY_ALIAS: &str = "TEST_BOOT_KEY";

    pub fn generate_password_blob() -> Password<'static> {
        let mut rng = rand::thread_rng();
        let mut password = vec![0u8; 64];
        rng.fill_bytes(&mut password);

        let mut zvec = ZVec::new(64).expect("Failed to create ZVec");
        zvec[..].copy_from_slice(&password[..]);

        Password::Owned(zvec)
    }

    fn setup_test(pw: &Password) -> (Arc<RwLock<SuperKeyManager>>, KeystoreDB, LegacyImporter) {
        let mut keystore_db = new_test_db().unwrap();
        let mut legacy_importer = LegacyImporter::new(Arc::new(Default::default()));
        legacy_importer.set_empty();
        let skm: Arc<RwLock<SuperKeyManager>> = Default::default();
        assert!(skm
            .write()
            .unwrap()
            .init_user(&mut keystore_db, &legacy_importer, USER_ID, pw)
            .is_ok());
        (skm, keystore_db, legacy_importer)
    }

    fn assert_unlocked(
        skm: &Arc<RwLock<SuperKeyManager>>,
        keystore_db: &mut KeystoreDB,
        legacy_importer: &LegacyImporter,
        user_id: u32,
        err_msg: &str,
    ) {
        let user_state =
            skm.write().unwrap().get_user_state(keystore_db, legacy_importer, user_id).unwrap();
        match user_state {
            UserState::AfterFirstUnlock(_) => {}
            _ => panic!("{}", err_msg),
        }
    }

    fn assert_locked(
        skm: &Arc<RwLock<SuperKeyManager>>,
        keystore_db: &mut KeystoreDB,
        legacy_importer: &LegacyImporter,
        user_id: u32,
        err_msg: &str,
    ) {
        let user_state =
            skm.write().unwrap().get_user_state(keystore_db, legacy_importer, user_id).unwrap();
        match user_state {
            UserState::BeforeFirstUnlock => {}
            _ => panic!("{}", err_msg),
        }
    }

    fn assert_uninitialized(
        skm: &Arc<RwLock<SuperKeyManager>>,
        keystore_db: &mut KeystoreDB,
        legacy_importer: &LegacyImporter,
        user_id: u32,
        err_msg: &str,
    ) {
        let user_state =
            skm.write().unwrap().get_user_state(keystore_db, legacy_importer, user_id).unwrap();
        match user_state {
            UserState::Uninitialized => {}
            _ => panic!("{}", err_msg),
        }
    }

    #[test]
    fn test_init_user() {
        let pw: Password = generate_password_blob();
        let (skm, mut keystore_db, legacy_importer) = setup_test(&pw);
        assert_unlocked(
            &skm,
            &mut keystore_db,
            &legacy_importer,
            USER_ID,
            "The user was not unlocked after initialization!",
        );
    }

    #[test]
    fn test_unlock_user() {
        let pw: Password = generate_password_blob();
        let (skm, mut keystore_db, legacy_importer) = setup_test(&pw);
        assert_unlocked(
            &skm,
            &mut keystore_db,
            &legacy_importer,
            USER_ID,
            "The user was not unlocked after initialization!",
        );

        skm.write().unwrap().data.user_keys.clear();
        assert_locked(
            &skm,
            &mut keystore_db,
            &legacy_importer,
            USER_ID,
            "Clearing the cache did not lock the user!",
        );

        assert!(skm
            .write()
            .unwrap()
            .unlock_user(&mut keystore_db, &legacy_importer, USER_ID, &pw)
            .is_ok());
        assert_unlocked(
            &skm,
            &mut keystore_db,
            &legacy_importer,
            USER_ID,
            "The user did not unlock!",
        );
    }

    #[test]
    fn test_unlock_wrong_password() {
        let pw: Password = generate_password_blob();
        let wrong_pw: Password = generate_password_blob();
        let (skm, mut keystore_db, legacy_importer) = setup_test(&pw);
        assert_unlocked(
            &skm,
            &mut keystore_db,
            &legacy_importer,
            USER_ID,
            "The user was not unlocked after initialization!",
        );

        skm.write().unwrap().data.user_keys.clear();
        assert_locked(
            &skm,
            &mut keystore_db,
            &legacy_importer,
            USER_ID,
            "Clearing the cache did not lock the user!",
        );

        assert!(skm
            .write()
            .unwrap()
            .unlock_user(&mut keystore_db, &legacy_importer, USER_ID, &wrong_pw)
            .is_err());
        assert_locked(
            &skm,
            &mut keystore_db,
            &legacy_importer,
            USER_ID,
            "The user was unlocked with an incorrect password!",
        );
    }

    #[test]
    fn test_unlock_user_idempotent() {
        let pw: Password = generate_password_blob();
        let (skm, mut keystore_db, legacy_importer) = setup_test(&pw);
        assert_unlocked(
            &skm,
            &mut keystore_db,
            &legacy_importer,
            USER_ID,
            "The user was not unlocked after initialization!",
        );

        skm.write().unwrap().data.user_keys.clear();
        assert_locked(
            &skm,
            &mut keystore_db,
            &legacy_importer,
            USER_ID,
            "Clearing the cache did not lock the user!",
        );

        for _ in 0..5 {
            assert!(skm
                .write()
                .unwrap()
                .unlock_user(&mut keystore_db, &legacy_importer, USER_ID, &pw)
                .is_ok());
            assert_unlocked(
                &skm,
                &mut keystore_db,
                &legacy_importer,
                USER_ID,
                "The user did not unlock!",
            );
        }
    }

    fn test_user_removal(locked: bool) {
        let pw: Password = generate_password_blob();
        let (skm, mut keystore_db, legacy_importer) = setup_test(&pw);
        assert_unlocked(
            &skm,
            &mut keystore_db,
            &legacy_importer,
            USER_ID,
            "The user was not unlocked after initialization!",
        );

        assert!(make_test_key_entry(
            &mut keystore_db,
            Domain::APP,
            USER_ID.into(),
            TEST_KEY_ALIAS,
            None
        )
        .is_ok());
        assert!(make_bootlevel_key_entry(
            &mut keystore_db,
            Domain::APP,
            USER_ID.into(),
            TEST_BOOT_KEY_ALIAS,
            false
        )
        .is_ok());

        assert!(keystore_db
            .key_exists(Domain::APP, USER_ID.into(), TEST_KEY_ALIAS, KeyType::Client)
            .unwrap());
        assert!(keystore_db
            .key_exists(Domain::APP, USER_ID.into(), TEST_BOOT_KEY_ALIAS, KeyType::Client)
            .unwrap());

        if locked {
            skm.write().unwrap().data.user_keys.clear();
            assert_locked(
                &skm,
                &mut keystore_db,
                &legacy_importer,
                USER_ID,
                "Clearing the cache did not lock the user!",
            );
        }

        assert!(skm
            .write()
            .unwrap()
            .remove_user(&mut keystore_db, &legacy_importer, USER_ID)
            .is_ok());
        assert_uninitialized(
            &skm,
            &mut keystore_db,
            &legacy_importer,
            USER_ID,
            "The user was not removed!",
        );

        assert!(!skm
            .write()
            .unwrap()
            .super_key_exists_in_db_for_user(&mut keystore_db, &legacy_importer, USER_ID)
            .unwrap());

        assert!(!keystore_db
            .key_exists(Domain::APP, USER_ID.into(), TEST_KEY_ALIAS, KeyType::Client)
            .unwrap());
        assert!(!keystore_db
            .key_exists(Domain::APP, USER_ID.into(), TEST_BOOT_KEY_ALIAS, KeyType::Client)
            .unwrap());
    }

    fn test_user_reset(locked: bool) {
        let pw: Password = generate_password_blob();
        let (skm, mut keystore_db, legacy_importer) = setup_test(&pw);
        assert_unlocked(
            &skm,
            &mut keystore_db,
            &legacy_importer,
            USER_ID,
            "The user was not unlocked after initialization!",
        );

        assert!(make_test_key_entry(
            &mut keystore_db,
            Domain::APP,
            USER_ID.into(),
            TEST_KEY_ALIAS,
            None
        )
        .is_ok());
        assert!(make_bootlevel_key_entry(
            &mut keystore_db,
            Domain::APP,
            USER_ID.into(),
            TEST_BOOT_KEY_ALIAS,
            false
        )
        .is_ok());
        assert!(keystore_db
            .key_exists(Domain::APP, USER_ID.into(), TEST_KEY_ALIAS, KeyType::Client)
            .unwrap());
        assert!(keystore_db
            .key_exists(Domain::APP, USER_ID.into(), TEST_BOOT_KEY_ALIAS, KeyType::Client)
            .unwrap());

        if locked {
            skm.write().unwrap().data.user_keys.clear();
            assert_locked(
                &skm,
                &mut keystore_db,
                &legacy_importer,
                USER_ID,
                "Clearing the cache did not lock the user!",
            );
            assert!(skm
                .write()
                .unwrap()
                .reset_user(&mut keystore_db, &legacy_importer, USER_ID)
                .is_err());
            assert_locked(
                &skm,
                &mut keystore_db,
                &legacy_importer,
                USER_ID,
                "User state should not have changed!",
            );

            // Keys should still exist.
            assert!(keystore_db
                .key_exists(Domain::APP, USER_ID.into(), TEST_KEY_ALIAS, KeyType::Client)
                .unwrap());
            assert!(keystore_db
                .key_exists(Domain::APP, USER_ID.into(), TEST_BOOT_KEY_ALIAS, KeyType::Client)
                .unwrap());
        } else {
            assert!(skm
                .write()
                .unwrap()
                .reset_user(&mut keystore_db, &legacy_importer, USER_ID)
                .is_ok());
            assert_uninitialized(
                &skm,
                &mut keystore_db,
                &legacy_importer,
                USER_ID,
                "The user was not reset!",
            );
            assert!(!skm
                .write()
                .unwrap()
                .super_key_exists_in_db_for_user(&mut keystore_db, &legacy_importer, USER_ID)
                .unwrap());

            // Auth bound key should no longer exist.
            assert!(!keystore_db
                .key_exists(Domain::APP, USER_ID.into(), TEST_KEY_ALIAS, KeyType::Client)
                .unwrap());
            assert!(keystore_db
                .key_exists(Domain::APP, USER_ID.into(), TEST_BOOT_KEY_ALIAS, KeyType::Client)
                .unwrap());
        }
    }

    #[test]
    fn test_remove_unlocked_user() {
        test_user_removal(false);
    }

    #[test]
    fn test_remove_locked_user() {
        test_user_removal(true);
    }

    #[test]
    fn test_reset_unlocked_user() {
        test_user_reset(false);
    }

    #[test]
    fn test_reset_locked_user() {
        test_user_reset(true);
    }
}
