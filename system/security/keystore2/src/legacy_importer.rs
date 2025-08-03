// Copyright 2021, The Android Open Source Project
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

//! This module acts as a bridge between the legacy key database and the keystore2 database.

use crate::database::{
    BlobInfo, BlobMetaData, BlobMetaEntry, CertificateInfo, DateTime, EncryptedBy, KeyMetaData,
    KeyMetaEntry, KeyType, KeystoreDB, Uuid, KEYSTORE_UUID,
};
use crate::error::{map_km_error, Error};
use crate::key_parameter::{KeyParameter, KeyParameterValue};
use crate::ks_err;
use crate::legacy_blob::{self, Blob, BlobValue, LegacyKeyCharacteristics};
use crate::super_key::USER_AFTER_FIRST_UNLOCK_SUPER_KEY;
use crate::utils::{
    key_characteristics_to_internal, uid_to_android_user, upgrade_keyblob_if_required_with,
    watchdog as wd, AesGcm,
};
use crate::{async_task::AsyncTask, legacy_blob::LegacyBlobLoader};
use android_hardware_security_keymint::aidl::android::hardware::security::keymint::SecurityLevel::SecurityLevel;
use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, KeyDescriptor::KeyDescriptor, ResponseCode::ResponseCode,
};
use anyhow::{Context, Result};
use core::ops::Deref;
use keystore2_crypto::{Password, ZVec};
use std::collections::{HashMap, HashSet};
use std::sync::atomic::{AtomicU8, Ordering};
use std::sync::mpsc::channel;
use std::sync::{Arc, Mutex};

/// Represents LegacyImporter.
pub struct LegacyImporter {
    async_task: Arc<AsyncTask>,
    initializer: Mutex<
        Option<
            Box<
                dyn FnOnce() -> (KeystoreDB, HashMap<SecurityLevel, Uuid>, Arc<LegacyBlobLoader>)
                    + Send
                    + 'static,
            >,
        >,
    >,
    /// This atomic is used for cheap interior mutability. It is intended to prevent
    /// expensive calls into the legacy importer when the legacy database is empty.
    /// When transitioning from READY to EMPTY, spurious calls may occur for a brief period
    /// of time. This is tolerable in favor of the common case.
    state: AtomicU8,
}

#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
struct RecentImport {
    uid: u32,
    alias: String,
}

impl RecentImport {
    fn new(uid: u32, alias: String) -> Self {
        Self { uid, alias }
    }
}

enum BulkDeleteRequest {
    Uid(u32),
    User(u32),
}

struct LegacyImporterState {
    recently_imported: HashSet<RecentImport>,
    recently_imported_super_key: HashSet<u32>,
    legacy_loader: Arc<LegacyBlobLoader>,
    sec_level_to_km_uuid: HashMap<SecurityLevel, Uuid>,
    db: KeystoreDB,
}

impl LegacyImporter {
    const WIFI_NAMESPACE: i64 = 102;
    const AID_WIFI: u32 = 1010;

    const STATE_UNINITIALIZED: u8 = 0;
    const STATE_READY: u8 = 1;
    const STATE_EMPTY: u8 = 2;

    /// Constructs a new LegacyImporter using the given AsyncTask object as import
    /// worker.
    pub fn new(async_task: Arc<AsyncTask>) -> Self {
        Self {
            async_task,
            initializer: Default::default(),
            state: AtomicU8::new(Self::STATE_UNINITIALIZED),
        }
    }

    #[cfg(test)]
    pub fn set_empty(&mut self) {
        self.state = AtomicU8::new(Self::STATE_EMPTY);
    }

    /// The legacy importer must be initialized deferred, because keystore starts very early.
    /// At this time the data partition may not be mounted. So we cannot open database connections
    /// until we get actual key load requests. This sets the function that the legacy loader
    /// uses to connect to the database.
    pub fn set_init<F>(&self, f_init: F) -> Result<()>
    where
        F: FnOnce() -> (KeystoreDB, HashMap<SecurityLevel, Uuid>, Arc<LegacyBlobLoader>)
            + Send
            + 'static,
    {
        let mut initializer = self.initializer.lock().expect("Failed to lock initializer.");

        // If we are not uninitialized we have no business setting the initializer.
        if self.state.load(Ordering::Relaxed) != Self::STATE_UNINITIALIZED {
            return Ok(());
        }

        // Only set the initializer if it hasn't been set before.
        if initializer.is_none() {
            *initializer = Some(Box::new(f_init))
        }

        Ok(())
    }

    /// This function is called by the import requestor to check if it is worth
    /// making an import request. It also transitions the state from UNINITIALIZED
    /// to READY or EMPTY on first use. The deferred initialization is necessary, because
    /// Keystore 2.0 runs early during boot, where data may not yet be mounted.
    /// Returns Ok(STATE_READY) if an import request is worth undertaking and
    /// Ok(STATE_EMPTY) if the database is empty. An error is returned if the loader
    /// was not initialized and cannot be initialized.
    fn check_state(&self) -> Result<u8> {
        let mut first_try = true;
        loop {
            match (self.state.load(Ordering::Relaxed), first_try) {
                (Self::STATE_EMPTY, _) => {
                    return Ok(Self::STATE_EMPTY);
                }
                (Self::STATE_UNINITIALIZED, true) => {
                    // If we find the legacy loader uninitialized, we grab the initializer lock,
                    // check if the legacy database is empty, and if not, schedule an initialization
                    // request. Coming out of the initializer lock, the state is either EMPTY or
                    // READY.
                    let mut initializer = self.initializer.lock().unwrap();

                    if let Some(initializer) = initializer.take() {
                        let (db, sec_level_to_km_uuid, legacy_loader) = (initializer)();

                        if legacy_loader.is_empty().context(
                            "In check_state: Trying to check if the legacy database is empty.",
                        )? {
                            self.state.store(Self::STATE_EMPTY, Ordering::Relaxed);
                            return Ok(Self::STATE_EMPTY);
                        }

                        self.async_task.queue_hi(move |shelf| {
                            shelf.get_or_put_with(|| LegacyImporterState {
                                recently_imported: Default::default(),
                                recently_imported_super_key: Default::default(),
                                legacy_loader,
                                sec_level_to_km_uuid,
                                db,
                            });
                        });

                        // It is safe to set this here even though the async task may not yet have
                        // run because any thread observing this will not be able to schedule a
                        // task that can run before the initialization.
                        // Also we can only transition out of this state while having the
                        // initializer lock and having found an initializer.
                        self.state.store(Self::STATE_READY, Ordering::Relaxed);
                        return Ok(Self::STATE_READY);
                    } else {
                        // There is a chance that we just lost the race from state.load() to
                        // grabbing the initializer mutex. If that is the case the state must
                        // be EMPTY or READY after coming out of the lock. So we can give it
                        // one more try.
                        first_try = false;
                        continue;
                    }
                }
                (Self::STATE_UNINITIALIZED, false) => {
                    // Okay, tough luck. The legacy loader was really completely uninitialized.
                    return Err(Error::sys())
                        .context(ks_err!("Legacy loader should not be called uninitialized."));
                }
                (Self::STATE_READY, _) => return Ok(Self::STATE_READY),
                (s, _) => panic!("Unknown legacy importer state. {} ", s),
            }
        }
    }

    /// List all aliases for uid in the legacy database.
    pub fn list_uid(&self, domain: Domain, namespace: i64) -> Result<Vec<KeyDescriptor>> {
        let _wp = wd::watch_millis("LegacyImporter::list_uid", 500);

        let uid = match (domain, namespace) {
            (Domain::APP, namespace) => namespace as u32,
            (Domain::SELINUX, Self::WIFI_NAMESPACE) => Self::AID_WIFI,
            _ => return Ok(Vec::new()),
        };
        self.do_serialized(move |state| state.list_uid(uid)).unwrap_or_else(|| Ok(Vec::new())).map(
            |v| {
                v.into_iter()
                    .map(|alias| KeyDescriptor {
                        domain,
                        nspace: namespace,
                        alias: Some(alias),
                        blob: None,
                    })
                    .collect()
            },
        )
    }

    /// Sends the given closure to the importer thread for execution after calling check_state.
    /// Returns None if the database was empty and the request was not executed.
    /// Otherwise returns Some with the result produced by the import request.
    /// The loader state may transition to STATE_EMPTY during the execution of this function.
    fn do_serialized<F, T: Send + 'static>(&self, f: F) -> Option<Result<T>>
    where
        F: FnOnce(&mut LegacyImporterState) -> Result<T> + Send + 'static,
    {
        // Short circuit if the database is empty or not initialized (error case).
        match self.check_state().context(ks_err!("Checking state.")) {
            Ok(LegacyImporter::STATE_EMPTY) => return None,
            Ok(LegacyImporter::STATE_READY) => {}
            Err(e) => return Some(Err(e)),
            Ok(s) => panic!("Unknown legacy importer state. {} ", s),
        }

        // We have established that there may be a key in the legacy database.
        // Now we schedule an import request.
        let (sender, receiver) = channel();
        self.async_task.queue_hi(move |shelf| {
            // Get the importer state from the shelf.
            // There may not be a state. This can happen if this import request was scheduled
            // before a previous request established that the legacy database was empty
            // and removed the state from the shelf. Since we know now that the database
            // is empty, we can return None here.
            let (new_state, result) = if let Some(legacy_importer_state) =
                shelf.get_downcast_mut::<LegacyImporterState>()
            {
                let result = f(legacy_importer_state);
                (legacy_importer_state.check_empty(), Some(result))
            } else {
                (Self::STATE_EMPTY, None)
            };

            // If the import request determined that the database is now empty, we discard
            // the state from the shelf to free up the resources we won't need any longer.
            if result.is_some() && new_state == Self::STATE_EMPTY {
                shelf.remove_downcast_ref::<LegacyImporterState>();
            }

            // Send the result to the requester.
            if let Err(e) = sender.send((new_state, result)) {
                log::error!("In do_serialized. Error in sending the result. {:?}", e);
            }
        });

        let (new_state, result) = match receiver.recv() {
            Err(e) => {
                return Some(Err(e).context(ks_err!("Failed to receive from the sender.")));
            }
            Ok(r) => r,
        };

        // We can only transition to EMPTY but never back.
        // The importer never creates any legacy blobs.
        if new_state == Self::STATE_EMPTY {
            self.state.store(Self::STATE_EMPTY, Ordering::Relaxed)
        }

        result
    }

    /// Runs the key_accessor function and returns its result. If it returns an error and the
    /// root cause was KEY_NOT_FOUND, tries to import a key with the given parameters from
    /// the legacy database to the new database and runs the key_accessor function again if
    /// the import request was successful.
    pub fn with_try_import<F, T>(
        &self,
        key: &KeyDescriptor,
        caller_uid: u32,
        super_key: Option<Arc<dyn AesGcm + Send + Sync>>,
        key_accessor: F,
    ) -> Result<T>
    where
        F: Fn() -> Result<T>,
    {
        let _wp = wd::watch_millis("LegacyImporter::with_try_import", 500);

        // Access the key and return on success.
        match key_accessor() {
            Ok(result) => return Ok(result),
            Err(e) => match e.root_cause().downcast_ref::<Error>() {
                Some(&Error::Rc(ResponseCode::KEY_NOT_FOUND)) => {}
                _ => return Err(e),
            },
        }

        // Filter inputs. We can only load legacy app domain keys and some special rules due
        // to which we import keys transparently to an SELINUX domain.
        let uid = match key {
            KeyDescriptor { domain: Domain::APP, alias: Some(_), .. } => caller_uid,
            KeyDescriptor { domain: Domain::SELINUX, nspace, alias: Some(_), .. } => {
                match *nspace {
                    Self::WIFI_NAMESPACE => Self::AID_WIFI,
                    _ => {
                        return Err(Error::Rc(ResponseCode::KEY_NOT_FOUND))
                            .context(format!("No legacy keys for namespace {}", nspace))
                    }
                }
            }
            _ => {
                return Err(Error::Rc(ResponseCode::KEY_NOT_FOUND))
                    .context("No legacy keys for key descriptor.")
            }
        };

        let key_clone = key.clone();
        let result = self.do_serialized(move |importer_state| {
            let super_key = super_key.map(|sk| -> Arc<dyn AesGcm> { sk });
            importer_state.check_and_import(uid, key_clone, super_key)
        });

        if let Some(result) = result {
            result?;
            // After successful import try again.
            key_accessor()
        } else {
            Err(Error::Rc(ResponseCode::KEY_NOT_FOUND)).context("Legacy database is empty.")
        }
    }

    /// Calls key_accessor and returns the result on success. In the case of a KEY_NOT_FOUND error
    /// this function makes an import request and on success retries the key_accessor.
    pub fn with_try_import_super_key<F, T>(
        &self,
        user_id: u32,
        pw: &Password,
        mut key_accessor: F,
    ) -> Result<Option<T>>
    where
        F: FnMut() -> Result<Option<T>>,
    {
        let _wp = wd::watch_millis("LegacyImporter::with_try_import_super_key", 500);

        match key_accessor() {
            Ok(Some(result)) => return Ok(Some(result)),
            Ok(None) => {}
            Err(e) => return Err(e),
        }
        let pw = pw.try_clone().context(ks_err!("Cloning password."))?;
        let result = self.do_serialized(move |importer_state| {
            importer_state.check_and_import_super_key(user_id, &pw)
        });

        if let Some(result) = result {
            result?;
            // After successful import try again.
            key_accessor()
        } else {
            Ok(None)
        }
    }

    /// Deletes all keys belonging to the given namespace, importing them into the database
    /// for subsequent garbage collection if necessary.
    pub fn bulk_delete_uid(&self, domain: Domain, nspace: i64) -> Result<()> {
        let _wp = wd::watch_millis("LegacyImporter::bulk_delete_uid", 500);

        let uid = match (domain, nspace) {
            (Domain::APP, nspace) => nspace as u32,
            (Domain::SELINUX, Self::WIFI_NAMESPACE) => Self::AID_WIFI,
            // Nothing to do.
            _ => return Ok(()),
        };

        let result = self.do_serialized(move |importer_state| {
            importer_state.bulk_delete(BulkDeleteRequest::Uid(uid), false)
        });

        result.unwrap_or(Ok(()))
    }

    /// Deletes all keys belonging to the given android user, importing them into the database
    /// for subsequent garbage collection if necessary.
    pub fn bulk_delete_user(
        &self,
        user_id: u32,
        keep_non_super_encrypted_keys: bool,
    ) -> Result<()> {
        let _wp = wd::watch_millis("LegacyImporter::bulk_delete_user", 500);

        let result = self.do_serialized(move |importer_state| {
            importer_state
                .bulk_delete(BulkDeleteRequest::User(user_id), keep_non_super_encrypted_keys)
        });

        result.unwrap_or(Ok(()))
    }

    /// Queries the legacy database for the presence of a super key for the given user.
    pub fn has_super_key(&self, user_id: u32) -> Result<bool> {
        let result =
            self.do_serialized(move |importer_state| importer_state.has_super_key(user_id));
        result.unwrap_or(Ok(false))
    }
}

impl LegacyImporterState {
    fn get_km_uuid(&self, is_strongbox: bool) -> Result<Uuid> {
        let sec_level = if is_strongbox {
            SecurityLevel::STRONGBOX
        } else {
            SecurityLevel::TRUSTED_ENVIRONMENT
        };

        self.sec_level_to_km_uuid.get(&sec_level).copied().ok_or_else(|| {
            anyhow::anyhow!(Error::sys()).context(ks_err!("No KM instance for blob."))
        })
    }

    fn list_uid(&mut self, uid: u32) -> Result<Vec<String>> {
        self.legacy_loader
            .list_keystore_entries_for_uid(uid)
            .context("In list_uid: Trying to list legacy entries.")
    }

    /// Checks if the key can potentially be unlocked. And deletes the key entry otherwise.
    /// If the super_key has already been imported, the super key database id is returned.
    fn get_super_key_id_check_unlockable_or_delete(
        &mut self,
        uid: u32,
        alias: &str,
    ) -> Result<i64> {
        let user_id = uid_to_android_user(uid);

        match self
            .db
            .load_super_key(&USER_AFTER_FIRST_UNLOCK_SUPER_KEY, user_id)
            .context(ks_err!("Failed to load super key"))?
        {
            Some((_, entry)) => Ok(entry.id()),
            None => {
                // This might be the first time we access the super key,
                // and it may not have been imported. We cannot import
                // the legacy super_key key now, because we need to reencrypt
                // it which we cannot do if we are not unlocked, which we are
                // not because otherwise the key would have been imported.
                // We can check though if the key exists. If it does,
                // we can return Locked. Otherwise, we can delete the
                // key and return NotFound, because the key will never
                // be unlocked again.
                if self.legacy_loader.has_super_key(user_id) {
                    Err(Error::Rc(ResponseCode::LOCKED)).context(ks_err!(
                        "Cannot import super key of this key while user is locked."
                    ))
                } else {
                    self.legacy_loader
                        .remove_keystore_entry(uid, alias)
                        .context(ks_err!("Trying to remove obsolete key."))?;
                    Err(Error::Rc(ResponseCode::KEY_NOT_FOUND)).context(ks_err!("Obsolete key."))
                }
            }
        }
    }

    fn characteristics_file_to_cache(
        &mut self,
        km_blob_params: Option<(Blob, LegacyKeyCharacteristics)>,
        super_key: &Option<Arc<dyn AesGcm>>,
        uid: u32,
        alias: &str,
    ) -> Result<(Option<(Blob, Vec<KeyParameter>)>, Option<(LegacyBlob<'static>, BlobMetaData)>)>
    {
        let (km_blob, params) = match km_blob_params {
            Some((km_blob, LegacyKeyCharacteristics::File(params))) => (km_blob, params),
            Some((km_blob, LegacyKeyCharacteristics::Cache(params))) => {
                return Ok((Some((km_blob, params)), None));
            }
            None => return Ok((None, None)),
        };

        let km_uuid =
            self.get_km_uuid(km_blob.is_strongbox()).context(ks_err!("Trying to get KM UUID"))?;

        let blob = match (&km_blob.value(), super_key.as_ref()) {
            (BlobValue::Encrypted { iv, tag, data }, Some(super_key)) => {
                let blob =
                    super_key.decrypt(data, iv, tag).context(ks_err!("Decryption failed."))?;
                LegacyBlob::ZVec(blob)
            }
            (BlobValue::Encrypted { .. }, None) => {
                return Err(Error::Rc(ResponseCode::LOCKED)).context(ks_err!(
                    "Oh uh, so close. \
                     This ancient key cannot be imported unless the user is unlocked."
                ));
            }
            (BlobValue::Decrypted(data), _) => LegacyBlob::Ref(data),
            _ => {
                return Err(Error::sys()).context(ks_err!("Unexpected blob type."));
            }
        };

        let (km_params, upgraded_blob) = get_key_characteristics_without_app_data(&km_uuid, &blob)
            .context(ks_err!("Failed to get key characteristics from device.",))?;

        let flags = km_blob.get_flags();

        let (current_blob, superseded_blob) =
            if let Some(upgraded_blob) = upgraded_blob {
                match (km_blob.take_value(), super_key.as_ref()) {
                    (BlobValue::Encrypted { iv, tag, data }, Some(super_key)) => {
                        let super_key_id = self
                            .get_super_key_id_check_unlockable_or_delete(uid, alias)
                            .context(ks_err!("How is there a super key but no super key id?"))?;

                        let mut superseded_metadata = BlobMetaData::new();
                        superseded_metadata.add(BlobMetaEntry::Iv(iv.to_vec()));
                        superseded_metadata.add(BlobMetaEntry::AeadTag(tag.to_vec()));
                        superseded_metadata
                            .add(BlobMetaEntry::EncryptedBy(EncryptedBy::KeyId(super_key_id)));
                        superseded_metadata.add(BlobMetaEntry::KmUuid(km_uuid));
                        let superseded_blob = (LegacyBlob::Vec(data), superseded_metadata);

                        let (data, iv, tag) = super_key
                            .encrypt(&upgraded_blob)
                            .context(ks_err!("Failed to encrypt upgraded key blob."))?;
                        (
                            Blob::new(flags, BlobValue::Encrypted { data, iv, tag }),
                            Some(superseded_blob),
                        )
                    }
                    (BlobValue::Encrypted { .. }, None) => {
                        return Err(Error::sys()).context(ks_err!(
                            "This should not be reachable. \
                         The blob could not have been decrypted above."
                        ));
                    }
                    (BlobValue::Decrypted(data), _) => {
                        let mut superseded_metadata = BlobMetaData::new();
                        superseded_metadata.add(BlobMetaEntry::KmUuid(km_uuid));
                        let superseded_blob = (LegacyBlob::ZVec(data), superseded_metadata);
                        (
                            Blob::new(
                                flags,
                                BlobValue::Decrypted(upgraded_blob.try_into().context(ks_err!(
                                    "Failed to convert upgraded blob to ZVec."
                                ))?),
                            ),
                            Some(superseded_blob),
                        )
                    }
                    _ => {
                        return Err(Error::sys()).context(ks_err!(
                            "This should not be reachable. \
                         Any other variant should have resulted in a different error."
                        ));
                    }
                }
            } else {
                (km_blob, None)
            };

        let params =
            augment_legacy_characteristics_file_with_key_characteristics(km_params, params);
        Ok((Some((current_blob, params)), superseded_blob))
    }

    /// This is a key import request that must run in the importer thread. This must
    /// be passed to do_serialized.
    fn check_and_import(
        &mut self,
        uid: u32,
        mut key: KeyDescriptor,
        super_key: Option<Arc<dyn AesGcm>>,
    ) -> Result<()> {
        let alias = key.alias.clone().ok_or_else(|| {
            anyhow::anyhow!(Error::sys()).context(ks_err!(
                "Must be Some because \
                 our caller must not have called us otherwise."
            ))
        })?;

        if self.recently_imported.contains(&RecentImport::new(uid, alias.clone())) {
            return Ok(());
        }

        if key.domain == Domain::APP {
            key.nspace = uid as i64;
        }

        // If the key is not found in the cache, try to load from the legacy database.
        let (km_blob_params, user_cert, ca_cert) = self
            .legacy_loader
            .load_by_uid_alias(uid, &alias, &super_key)
            .map_err(|e| {
                if e.root_cause().downcast_ref::<legacy_blob::Error>()
                    == Some(&legacy_blob::Error::LockedComponent)
                {
                    // There is no chance to succeed at this point. We just check if there is
                    // a super key so that this entry might be unlockable in the future.
                    // If not the entry will be deleted and KEY_NOT_FOUND is returned.
                    // If a super key id was returned we still have to return LOCKED but the key
                    // may be imported when the user unlocks the device.
                    self.get_super_key_id_check_unlockable_or_delete(uid, &alias)
                        .and_then::<i64, _>(|_| {
                            Err(Error::Rc(ResponseCode::LOCKED))
                                .context("Super key present but locked.")
                        })
                        .unwrap_err()
                } else {
                    e
                }
            })
            .context(ks_err!("Trying to load legacy blob."))?;

        let (km_blob_params, superseded_blob) = self
            .characteristics_file_to_cache(km_blob_params, &super_key, uid, &alias)
            .context(ks_err!("Trying to update legacy characteristics."))?;

        let result = match km_blob_params {
            Some((km_blob, params)) => {
                let is_strongbox = km_blob.is_strongbox();

                let (blob, mut blob_metadata) = match km_blob.take_value() {
                    BlobValue::Encrypted { iv, tag, data } => {
                        // Get super key id for user id.
                        let super_key_id = self
                            .get_super_key_id_check_unlockable_or_delete(uid, &alias)
                            .context(ks_err!("Failed to get super key id."))?;

                        let mut blob_metadata = BlobMetaData::new();
                        blob_metadata.add(BlobMetaEntry::Iv(iv.to_vec()));
                        blob_metadata.add(BlobMetaEntry::AeadTag(tag.to_vec()));
                        blob_metadata
                            .add(BlobMetaEntry::EncryptedBy(EncryptedBy::KeyId(super_key_id)));
                        (LegacyBlob::Vec(data), blob_metadata)
                    }
                    BlobValue::Decrypted(data) => (LegacyBlob::ZVec(data), BlobMetaData::new()),
                    _ => {
                        return Err(Error::Rc(ResponseCode::KEY_NOT_FOUND))
                            .context(ks_err!("Legacy key has unexpected type."));
                    }
                };

                let km_uuid =
                    self.get_km_uuid(is_strongbox).context(ks_err!("Trying to get KM UUID"))?;
                blob_metadata.add(BlobMetaEntry::KmUuid(km_uuid));

                let mut metadata = KeyMetaData::new();
                let creation_date =
                    DateTime::now().context(ks_err!("Trying to make creation time."))?;
                metadata.add(KeyMetaEntry::CreationDate(creation_date));

                let blob_info = BlobInfo::new_with_superseded(
                    &blob,
                    &blob_metadata,
                    superseded_blob.as_ref().map(|(b, m)| (&**b, m)),
                );
                // Store legacy key in the database.
                self.db
                    .store_new_key(
                        &key,
                        KeyType::Client,
                        &params,
                        &blob_info,
                        &CertificateInfo::new(user_cert, ca_cert),
                        &metadata,
                        &km_uuid,
                    )
                    .context(ks_err!())?;
                Ok(())
            }
            None => {
                if let Some(ca_cert) = ca_cert {
                    self.db
                        .store_new_certificate(&key, KeyType::Client, &ca_cert, &KEYSTORE_UUID)
                        .context(ks_err!("Failed to insert new certificate."))?;
                    Ok(())
                } else {
                    Err(Error::Rc(ResponseCode::KEY_NOT_FOUND))
                        .context(ks_err!("Legacy key not found."))
                }
            }
        };

        match result {
            Ok(()) => {
                // Add the key to the imported_keys list.
                self.recently_imported.insert(RecentImport::new(uid, alias.clone()));
                // Delete legacy key from the file system
                self.legacy_loader
                    .remove_keystore_entry(uid, &alias)
                    .context(ks_err!("Trying to remove imported key."))?;
                Ok(())
            }
            Err(e) => Err(e),
        }
    }

    fn check_and_import_super_key(&mut self, user_id: u32, pw: &Password) -> Result<()> {
        if self.recently_imported_super_key.contains(&user_id) {
            return Ok(());
        }

        if let Some(super_key) = self
            .legacy_loader
            .load_super_key(user_id, pw)
            .context(ks_err!("Trying to load legacy super key."))?
        {
            let (blob, blob_metadata) =
                crate::super_key::SuperKeyManager::encrypt_with_password(&super_key, pw)
                    .context(ks_err!("Trying to encrypt super key."))?;

            self.db
                .store_super_key(
                    user_id,
                    &USER_AFTER_FIRST_UNLOCK_SUPER_KEY,
                    &blob,
                    &blob_metadata,
                    &KeyMetaData::new(),
                )
                .context(ks_err!("Trying to insert legacy super_key into the database."))?;
            self.legacy_loader.remove_super_key(user_id);
            self.recently_imported_super_key.insert(user_id);
            Ok(())
        } else {
            Err(Error::Rc(ResponseCode::KEY_NOT_FOUND)).context(ks_err!("No key found do import."))
        }
    }

    /// Key importer request to be run by do_serialized.
    /// See LegacyImporter::bulk_delete_uid and LegacyImporter::bulk_delete_user.
    fn bulk_delete(
        &mut self,
        bulk_delete_request: BulkDeleteRequest,
        keep_non_super_encrypted_keys: bool,
    ) -> Result<()> {
        let (aliases, user_id) = match bulk_delete_request {
            BulkDeleteRequest::Uid(uid) => (
                self.legacy_loader
                    .list_keystore_entries_for_uid(uid)
                    .context(ks_err!("Trying to get aliases for uid."))
                    .map(|aliases| {
                        let mut h = HashMap::<u32, HashSet<String>>::new();
                        h.insert(uid, aliases.into_iter().collect());
                        h
                    })?,
                uid_to_android_user(uid),
            ),
            BulkDeleteRequest::User(user_id) => (
                self.legacy_loader
                    .list_keystore_entries_for_user(user_id)
                    .context(ks_err!("Trying to get aliases for user_id."))?,
                user_id,
            ),
        };

        let super_key_id = self
            .db
            .load_super_key(&USER_AFTER_FIRST_UNLOCK_SUPER_KEY, user_id)
            .context(ks_err!("Failed to load super key"))?
            .map(|(_, entry)| entry.id());

        for (uid, alias) in aliases
            .into_iter()
            .flat_map(|(uid, aliases)| aliases.into_iter().map(move |alias| (uid, alias)))
        {
            let (km_blob_params, _, _) = self
                .legacy_loader
                .load_by_uid_alias(uid, &alias, &None)
                .context(ks_err!("Trying to load legacy blob."))?;

            // Determine if the key needs special handling to be deleted.
            let (need_gc, is_super_encrypted) = km_blob_params
                .as_ref()
                .map(|(blob, params)| {
                    let params = match params {
                        LegacyKeyCharacteristics::Cache(params)
                        | LegacyKeyCharacteristics::File(params) => params,
                    };
                    (
                        params.iter().any(|kp| {
                            KeyParameterValue::RollbackResistance == *kp.key_parameter_value()
                        }),
                        blob.is_encrypted(),
                    )
                })
                .unwrap_or((false, false));

            if keep_non_super_encrypted_keys && !is_super_encrypted {
                continue;
            }

            if need_gc {
                let mark_deleted = match km_blob_params
                    .map(|(blob, _)| (blob.is_strongbox(), blob.take_value()))
                {
                    Some((is_strongbox, BlobValue::Encrypted { iv, tag, data })) => {
                        let mut blob_metadata = BlobMetaData::new();
                        if let (Ok(km_uuid), Some(super_key_id)) =
                            (self.get_km_uuid(is_strongbox), super_key_id)
                        {
                            blob_metadata.add(BlobMetaEntry::KmUuid(km_uuid));
                            blob_metadata.add(BlobMetaEntry::Iv(iv.to_vec()));
                            blob_metadata.add(BlobMetaEntry::AeadTag(tag.to_vec()));
                            blob_metadata
                                .add(BlobMetaEntry::EncryptedBy(EncryptedBy::KeyId(super_key_id)));
                            Some((LegacyBlob::Vec(data), blob_metadata))
                        } else {
                            // Oh well - we tried our best, but if we cannot determine which
                            // KeyMint instance we have to send this blob to, we cannot
                            // do more than delete the key from the file system.
                            // And if we don't know which key wraps this key we cannot
                            // unwrap it for KeyMint either.
                            None
                        }
                    }
                    Some((_, BlobValue::Decrypted(data))) => {
                        Some((LegacyBlob::ZVec(data), BlobMetaData::new()))
                    }
                    _ => None,
                };

                if let Some((blob, blob_metadata)) = mark_deleted {
                    self.db.set_deleted_blob(&blob, &blob_metadata).context(ks_err!(
                        "Trying to insert deleted \
                            blob into the database for garbage collection."
                    ))?;
                }
            }

            self.legacy_loader
                .remove_keystore_entry(uid, &alias)
                .context(ks_err!("Trying to remove imported key."))?;
        }
        Ok(())
    }

    fn has_super_key(&mut self, user_id: u32) -> Result<bool> {
        Ok(self.recently_imported_super_key.contains(&user_id)
            || self.legacy_loader.has_super_key(user_id))
    }

    fn check_empty(&self) -> u8 {
        if self.legacy_loader.is_empty().unwrap_or(false) {
            LegacyImporter::STATE_EMPTY
        } else {
            LegacyImporter::STATE_READY
        }
    }
}

enum LegacyBlob<'a> {
    Vec(Vec<u8>),
    ZVec(ZVec),
    Ref(&'a [u8]),
}

impl Deref for LegacyBlob<'_> {
    type Target = [u8];

    fn deref(&self) -> &Self::Target {
        match self {
            Self::Vec(v) => v,
            Self::ZVec(v) => v,
            Self::Ref(v) => v,
        }
    }
}

/// This function takes two KeyParameter lists. The first is assumed to have been retrieved from the
/// KM back end using km_dev.getKeyCharacteristics. The second is assumed to have been retrieved
/// from a legacy key characteristics file (not cache) as used in Android P and older. The function
/// augments the former with entries from the latter only if no equivalent entry is present ignoring.
/// the security level of enforcement. All entries in the latter are assumed to have security level
/// KEYSTORE.
fn augment_legacy_characteristics_file_with_key_characteristics<T>(
    mut from_km: Vec<KeyParameter>,
    legacy: T,
) -> Vec<KeyParameter>
where
    T: IntoIterator<Item = KeyParameter>,
{
    for legacy_kp in legacy.into_iter() {
        if !from_km
            .iter()
            .any(|km_kp| km_kp.key_parameter_value() == legacy_kp.key_parameter_value())
        {
            from_km.push(legacy_kp);
        }
    }
    from_km
}

/// Attempts to retrieve the key characteristics for the given blob from the KM back end with the
/// given UUID. It may upgrade the key blob in the process. In that case the upgraded blob is
/// returned as the second tuple member.
fn get_key_characteristics_without_app_data(
    uuid: &Uuid,
    blob: &[u8],
) -> Result<(Vec<KeyParameter>, Option<Vec<u8>>)> {
    let (km_dev, info) = crate::globals::get_keymint_dev_by_uuid(uuid)
        .with_context(|| ks_err!("Trying to get km device for id {:?}", uuid))?;

    let (characteristics, upgraded_blob) = upgrade_keyblob_if_required_with(
        &*km_dev,
        info.versionNumber,
        blob,
        &[],
        |blob| {
            let _wd = wd::watch_millis("Calling GetKeyCharacteristics.", 500);
            map_km_error(km_dev.getKeyCharacteristics(blob, &[], &[]))
        },
        |_| Ok(()),
    )
    .context(ks_err!())?;
    Ok((key_characteristics_to_internal(characteristics), upgraded_blob))
}
