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

//! Module that implements the [`SecureDeletionSecretManager`] trait.
use alloc::rc::Rc;
use core::{cell::RefCell, cmp, ops::DerefMut};
use kmr_common::{
    crypto,
    keyblob::{SecureDeletionData, SecureDeletionSecretManager, SecureDeletionSlot, SlotPurpose},
    km_err, Error,
};
use log::{debug, error, info, warn};
use storage::{self as storage_session, OpenMode, Port, SecureFile, Session, Transaction};
use trusty_sys;
use zeroize::{Zeroize, ZeroizeOnDrop};

// Maximum number of attempts to perform a secure storage transaction to read or
// delete a secure deletion secret.  Because the storageproxy may be restarted
// while this code is running, it may be necessary to retry.  But because it's
// unclear exactly what error codes may be returned when the proxy is shut down,
// we conservatively retry all unexpected errors.  To avoid an infinite loop, we
// set a limit on the number of retries (though hitting the limit and returning
// an error will likely break the boot anyway).  Ideally, we should never need
// more than one retry.  We allow three.
const MAX_TRIES: usize = 3;

// Each secret is 16 bytes.
const SECRET_SIZE: usize = 16;
// The factory reset secret is composed of two secrets, so 32 bytes, and it is
// stored at offset 0. After this position (offset 32), all the secrets are
// stored one after the other. On each secret a single bit on the first byte is
// set to indicate that the secret is valid (mask 0x80). Any unused secret/newly
// allocated space on the file is set to 0
const FACTORY_RESET_SECRET_SIZE: usize = SECRET_SIZE * 2;
const FACTORY_RESET_SECRET_POS: usize = 0;
const FACTORY_FIRST_SECURE_DELETION_SECRET_POS: usize =
    FACTORY_RESET_SECRET_POS + FACTORY_RESET_SECRET_SIZE;

// We read secrets in blocks of 32, so 512 bytes.
const BLOCK_SIZE: usize = SECRET_SIZE * 32;

// Limit file size to 16 KiB (except for key upgrades, see
// MAX_SECRET_FILE_SIZE_FOR_UPGRADES).
const MAX_SECRET_FILE_SIZE: usize = BLOCK_SIZE * 32;

// This is a higher file size limit, with the space above MAX_SECRET_FILE_SIZE
// usable only for key IDs that need to be written as part of a key upgrade.
// This is to reduce the probability that keys are degraded as a result of
// upgrading.
const MAX_SECRET_FILE_SIZE_FOR_UPGRADES: usize = MAX_SECRET_FILE_SIZE + 8 * BLOCK_SIZE;

// We set a bit in the first byte of each slot to indicate that the slot is in
// use. This reduces the maximum entropy of each slot to 127 bits.
const IN_USE_FLAG: u8 = 0x80;

// Name of the file to store secrets. The "_1" suffix is to allow for new file
// formats/versions in the future.
const SECURE_DELETION_SECRET_FILENAME: &'static str = "SecureDeletionSecrets_1";

// TODO: Add crate static_assertions to trusty to replace these with static_assert!
const _: () = assert!(
    BLOCK_SIZE >= FACTORY_RESET_SECRET_SIZE,
    "BLOCK_SIZE should be bigger than FACTORY_RESET_SECRET_SIZE"
);
const _: () = assert!((BLOCK_SIZE % SECRET_SIZE) == 0, "Broke find_empty_slot assumption");
const _: () =
    assert!((FACTORY_RESET_SECRET_SIZE % SECRET_SIZE) == 0, "Broke find_empty_slot assumption");

fn get_secure_deletion_secret_file_session(wait_on_port: bool) -> Result<Session, Error> {
    let session = Session::new(Port::TamperProof, wait_on_port).map_err(|e| {
        km_err!(
            SecureHwCommunicationFailed,
            "failed to connect to secure storage port for opening secure deletion file: {:?}",
            e
        )
    })?;
    Ok(session)
}

fn delete_secure_deletion_secret_file() -> Result<(), Error> {
    let mut session = get_secure_deletion_secret_file_session(true)?;
    // We do not consider the file not existing an error when trying to delete it because the
    // end result is the same.
    match session.remove(SECURE_DELETION_SECRET_FILENAME) {
        Ok(_) => Ok(()),
        Err(storage_session::Error::Code(trusty_sys::Error::NotFound)) => Ok(()),
        Err(e) => Err(km_err!(
            SecureHwCommunicationFailed,
            "couldn't delete secure secrets file: {:?}",
            e
        )),
    }
}

enum RetrieveSecureDeletionSecretFileData<'a> {
    EmptyFileFound(SecureDeletionSecretFile<'a>),
    CachedDataFound(SecureDeletionData),
    DataFoundOnFile(SecureDeletionData),
}

struct SecureDeletionSecretFile<'a> {
    file: SecureFile,
    transaction: Transaction<'a>,
}

impl<'a> SecureDeletionSecretFile<'a> {
    fn open_or_create(session: &'a mut Session) -> Result<SecureDeletionSecretFile<'a>, Error> {
        let mut transaction = session.begin_transaction();
        let file = transaction
            .open_file(SECURE_DELETION_SECRET_FILENAME, OpenMode::Create)
            .map_err(|e| {
                km_err!(
                    SecureHwCommunicationFailed,
                    "failed to open secure deletion secret file: {:?}",
                    e
                )
            })?;
        Ok(SecureDeletionSecretFile { transaction, file })
    }

    fn read_block<'buf>(
        &mut self,
        start: usize,
        buffer: &'buf mut [u8],
    ) -> Result<&'buf [u8], Error> {
        let req_len = buffer.len();
        let data = self.transaction.read_at(&self.file, start, buffer).map_err(|e| {
            km_err!(
                SecureHwCommunicationFailed,
                "failed to read secure deletion secret file at offset {} with len {}: {:?}",
                start,
                req_len,
                e
            )
        })?;
        if data.len() != req_len {
            Err(km_err!(
                UnknownError,
                "couldn't read {} bytes of secure deletion secret file at offset {}. Read {} bytes",
                req_len,
                start,
                data.len()
            ))
        } else {
            Ok(data)
        }
    }

    // Find empty slot is used to find the first [SECRET_SIZE] position on the secure file that
    // isn't currently in use. For this it will read the secure file in [BLOCK_SIZE] chunks and
    // move in [SECRET_SIZE] increments; checking if the IN_USE_FLAG is set on that position.
    fn find_empty_slot(&mut self, is_upgrade: bool) -> Result<Option<usize>, Error> {
        let end = SecureDeletionSecretFile::get_max_file_size(is_upgrade);
        let file_size = self.get_file_size()?;
        let end = cmp::min(end, file_size);
        let mut block_buffer = [0; BLOCK_SIZE];
        for start_pos in (0..end).step_by(BLOCK_SIZE) {
            let read_data = match self.read_block(start_pos, &mut block_buffer) {
                Ok(read_data) => read_data,
                Err(e) => {
                    error!("Failed to read block of secrets");
                    return Err(e);
                }
            };
            // Code assumes that we always read a complete block. API called could potentially
            // return less data.
            if read_data.len() != BLOCK_SIZE {
                return Err(km_err!(
                    SecureHwCommunicationFailed,
                    "failed to read complete block from storage. Received {} bytes",
                    read_data.len()
                ));
            }

            let block_start = match start_pos {
                FACTORY_RESET_SECRET_POS => FACTORY_FIRST_SECURE_DELETION_SECRET_POS,
                _ => 0,
            };

            for (chunk_num, secret) in read_data[block_start..].chunks(SECRET_SIZE).enumerate() {
                if (secret[0] & IN_USE_FLAG) == 0 {
                    let key_slot =
                        (start_pos + block_start + (chunk_num * SECRET_SIZE)) / SECRET_SIZE;
                    return Ok(Some(key_slot));
                }
            }
        }
        Ok(None)
    }

    fn write_block(&mut self, start: usize, buffer: &[u8]) -> Result<(), Error> {
        self.transaction.write_at(&mut self.file, start, buffer).map_err(|e| {
            km_err!(
                SecureHwCommunicationFailed,
                "failed to write to deletion secret file at pos {}: {:?}",
                start,
                e
            )
        })
    }

    fn get_file_size(&mut self) -> Result<usize, Error> {
        self.transaction.get_size(&self.file).map_err(|e| {
            km_err!(
                SecureHwCommunicationFailed,
                "couldn't get secure deletion secret file size: {:?}",
                e
            )
        })
    }

    fn get_max_file_size(is_upgrade: bool) -> usize {
        match is_upgrade {
            true => MAX_SECRET_FILE_SIZE_FOR_UPGRADES,
            false => MAX_SECRET_FILE_SIZE,
        }
    }

    fn resize(&mut self, new_size: usize) -> Result<(), Error> {
        self.transaction.set_size(&mut self.file, new_size).map_err(|e| {
            km_err!(
                SecureHwCommunicationFailed,
                "failed to resize secure deletion secret file to {}: {:?}",
                new_size,
                e
            )
        })?;
        Ok(())
    }

    fn zero_entries(&mut self, begin: usize, end: usize) -> Result<(), Error> {
        if (begin % SECRET_SIZE) != 0 {
            return Err(km_err!(
                InvalidArgument,
                "zero_entries called with invalid offset {}",
                begin
            ));
        }
        let zero_buff = [0; SECRET_SIZE];
        for start_pos in (begin..end).step_by(SECRET_SIZE) {
            self.write_block(start_pos, &zero_buff)?;
        }
        Ok(())
    }

    fn finish_transaction(self) -> Result<(), Error> {
        self.transaction.commit().map_err(|e| {
            km_err!(
                SecureHwCommunicationFailed,
                "failed to commit transaction on secure deletion secret file: {:?}",
                e
            )
        })
    }
}

#[derive(Clone, PartialEq, Eq, Zeroize, ZeroizeOnDrop)]
struct FactoryResetSecret([u8; FACTORY_RESET_SECRET_SIZE]);

#[derive(Clone, PartialEq, Eq)]
pub struct TrustySecureDeletionSecretManager {
    factory_reset_secret: RefCell<Option<FactoryResetSecret>>,
}

impl TrustySecureDeletionSecretManager {
    pub fn new() -> Self {
        TrustySecureDeletionSecretManager { factory_reset_secret: RefCell::new(None) }
    }

    // TODO: Check if this code can be refactored to not pass session as an option
    // get_factory_reset_secret_impl will just try to get the factory reset secret either from cache
    // or from the file if it exists. If the secret is read and not cached, it will cache it.
    // If the file doesn't exist, or if it is empty, it will return a File object/session; so the
    // caller can initialize it. In case the caller doesn't initialize it, we will end up with an
    // empty file on the file system, but this should be fine because we treat an empty file in the
    // same way we treat a non-existing file.
    fn get_factory_reset_secret_impl<'a>(
        &'a self,
        session: Option<&'a mut Session>,
    ) -> Result<RetrieveSecureDeletionSecretFileData, Error> {
        // Checking if we already have a cached secret we can return
        if let Some(secret) = self.factory_reset_secret.borrow_mut().deref_mut() {
            return Ok(RetrieveSecureDeletionSecretFileData::CachedDataFound(SecureDeletionData {
                factory_reset_secret: secret.0.clone(),
                secure_deletion_secret: [0; SECRET_SIZE],
            }));
        }

        // Looking now if we can read the secret from file
        debug!("Trying to open a session to read factory reset secret");
        let session = session.ok_or(km_err!(
            SecureHwCommunicationFailed,
            "couldn't get a session to open the secure deletion secret file"
        ))?;
        let mut sdsf_file = SecureDeletionSecretFile::open_or_create(session)?;
        let file_size = sdsf_file.get_file_size().map_err(|e| {
            km_err!(
                SecureHwCommunicationFailed,
                "couldn't get secure deletion secret file size: {:?}",
                e
            )
        })?;

        // Found an empty file
        if file_size <= 0 {
            return Ok(RetrieveSecureDeletionSecretFileData::EmptyFileFound(sdsf_file));
        }

        // The file isn't empty, read the secret and cache it before returning the data
        debug!("Opened non-empty secure secrets file");
        let mut buffer = [0; FACTORY_RESET_SECRET_SIZE];
        let block = sdsf_file.read_block(FACTORY_RESET_SECRET_POS, &mut buffer)?;
        debug!("Read factory-reset secret, size {}", block.len());
        if block.len() != FACTORY_RESET_SECRET_SIZE {
            return Err(km_err!(
                SecureHwCommunicationFailed,
                "failed to read complete secret data from storage. Received {} bytes",
                block.len()
            ));
        }
        self.factory_reset_secret
            .borrow_mut()
            .deref_mut()
            .replace(FactoryResetSecret(buffer.clone()));
        Ok(RetrieveSecureDeletionSecretFileData::DataFoundOnFile(SecureDeletionData {
            factory_reset_secret: buffer,
            secure_deletion_secret: [0; SECRET_SIZE],
        }))
    }

    // get_or_create_factory_reset_secret_impl will use get_factory_reset_secret_impl to try to get
    // the factory reset secret. If the secure deletion secret file doesn't exist on secure storage,
    // it will create it and will also initialize it.
    fn get_or_create_factory_reset_secret_impl(
        &mut self,
        rng: &mut dyn crypto::Rng,
        wait_for_port: bool,
    ) -> Result<SecureDeletionData, Error> {
        let mut session = get_secure_deletion_secret_file_session(wait_for_port).ok();
        let secret_file_data = self.get_factory_reset_secret_impl(session.as_mut())?;
        match secret_file_data {
            RetrieveSecureDeletionSecretFileData::CachedDataFound(data) => Ok(data),
            RetrieveSecureDeletionSecretFileData::DataFoundOnFile(data) => Ok(data),
            RetrieveSecureDeletionSecretFileData::EmptyFileFound(mut sdsf_file) => {
                sdsf_file.resize(BLOCK_SIZE)?;
                debug!("Resized secure secrets file to size {}", BLOCK_SIZE);
                let mut buffer = [0; FACTORY_RESET_SECRET_SIZE];
                rng.fill_bytes(&mut buffer);
                sdsf_file.write_block(FACTORY_RESET_SECRET_POS, &buffer)?;
                debug!("Wrote new factory reset secret");
                sdsf_file.zero_entries(FACTORY_FIRST_SECURE_DELETION_SECRET_POS, BLOCK_SIZE)?;
                debug!("Zeroed secrets");
                sdsf_file.finish_transaction()?;
                debug!("Committed new secrets file");
                self.factory_reset_secret
                    .borrow_mut()
                    .deref_mut()
                    .replace(FactoryResetSecret(buffer.clone()));
                Ok(SecureDeletionData {
                    factory_reset_secret: buffer,
                    secure_deletion_secret: [0; SECRET_SIZE],
                })
            }
        }
    }

    fn read_slot_data(&self, slot: SecureDeletionSlot, buffer: &mut [u8]) -> Result<(), Error> {
        let buffer_size = buffer.len();
        if buffer_size != SECRET_SIZE {
            return Err(km_err!(
                InsufficientBufferSpace,
                "needed {} bytes to read slot, received {}",
                SECRET_SIZE,
                buffer.len()
            ));
        }
        let requested_slot = slot.0 as usize;
        let key_slot_pos = requested_slot * SECRET_SIZE;
        let mut session = match get_secure_deletion_secret_file_session(true) {
            Ok(session) => session,
            Err(e) => {
                error!("Failed to open session to get secure deletion data: {:?}", e);
                return Err(e);
            }
        };
        let mut sdsf_file = match SecureDeletionSecretFile::open_or_create(&mut session) {
            Ok(sdsf_file) => sdsf_file,
            Err(e) => {
                error!("Failed to open file to get secure deletion data: {:?}", e);
                return Err(e);
            }
        };
        let file_size = match sdsf_file.get_file_size() {
            Ok(file_size) => file_size,
            Err(e) => {
                error!("Failed to read secure deletion data file size: {:?}", e);
                return Err(e);
            }
        };
        if (key_slot_pos + SECRET_SIZE) > file_size {
            return Err(km_err!(
                InvalidArgument,
                "invalid key slot {} would read past end of file of size {}",
                requested_slot,
                file_size
            ));
        }
        match sdsf_file.read_block(key_slot_pos, buffer) {
            Ok(read_data) => {
                if buffer_size == read_data.len() {
                    Ok(())
                } else {
                    Err(km_err!(
                        SecureHwCommunicationFailed,
                        "failed to read complete slot data from storage. Received {} bytes",
                        read_data.len()
                    ))
                }
            }
            Err(e) => {
                error!("Failed to read secret from slot {}: {:?}", requested_slot, e);
                Err(e)
            }
        }
    }
}

impl Drop for TrustySecureDeletionSecretManager {
    fn drop(&mut self) {
        self.factory_reset_secret.borrow_mut().deref_mut().zeroize();
    }
}

impl ZeroizeOnDrop for TrustySecureDeletionSecretManager {}

impl SecureDeletionSecretManager for TrustySecureDeletionSecretManager {
    fn get_or_create_factory_reset_secret(
        &mut self,
        rng: &mut dyn crypto::Rng,
    ) -> Result<SecureDeletionData, Error> {
        self.get_or_create_factory_reset_secret_impl(rng, true)
    }

    fn get_factory_reset_secret(&self) -> Result<SecureDeletionData, Error> {
        let mut session = get_secure_deletion_secret_file_session(true).ok();
        let secret_file_data = self.get_factory_reset_secret_impl(session.as_mut())?;
        match secret_file_data {
            RetrieveSecureDeletionSecretFileData::CachedDataFound(data) => Ok(data),
            RetrieveSecureDeletionSecretFileData::DataFoundOnFile(data) => Ok(data),
            RetrieveSecureDeletionSecretFileData::EmptyFileFound(_) => {
                Err(km_err!(UnknownError, "factory reset secret not found"))
            }
        }
    }

    fn new_secret(
        &mut self,
        rng: &mut dyn crypto::Rng,
        slot_purpose: SlotPurpose,
    ) -> Result<(SecureDeletionSlot, SecureDeletionData), Error> {
        let is_upgrade = slot_purpose == SlotPurpose::KeyUpgrade;
        // We are not waiting on the connection if the TA port is not available. This follows the
        // behavior of the original code.
        let mut secure_deletion_data =
            match self.get_or_create_factory_reset_secret_impl(rng, false) {
                Ok(data) => data,
                Err(e) => {
                    info!("Unable to get factory reset secret: {:?}", e);
                    return Err(e);
                }
            };
        rng.fill_bytes(&mut secure_deletion_data.secure_deletion_secret);
        secure_deletion_data.secure_deletion_secret[0] |= IN_USE_FLAG;
        // Next call will block on the port. It should be fine, because if we reach this point, the
        // TA should have been available before. Also, the original code follows a similar flow on
        // which they use a blocking call if this point is reached.
        let mut session = get_secure_deletion_secret_file_session(true)?;
        let mut sdsf_file = SecureDeletionSecretFile::open_or_create(&mut session)?;
        let empty_slot = match sdsf_file.find_empty_slot(is_upgrade) {
            Ok(slot) => slot,
            Err(e) => {
                error!("Error while searching for key slot: {:?}", e);
                return Err(e);
            }
        };

        let original_file_size = sdsf_file.get_file_size()?;
        let empty_slot = match empty_slot {
            Some(slot_number) => slot_number,
            None => {
                // No empty slot found, try to increase file size
                let max_file_size = SecureDeletionSecretFile::get_max_file_size(is_upgrade);
                if original_file_size >= max_file_size {
                    error!(
                        "Didn't find a slot and can't grow the file larger than {}",
                        original_file_size
                    );
                    return Err(km_err!(
                        UnknownError,
                        "didn't find a slot and can't grow the file larger than {}",
                        original_file_size
                    ));
                }
                let new_size = original_file_size + BLOCK_SIZE;
                debug!("Attempting to resize file from {} to {}", original_file_size, new_size);
                if let Err(e) = sdsf_file.resize(new_size) {
                    error!("Failed to grow file to make room for a key slot: {:?}", e);
                    return Err(e);
                }
                debug!("Resized file to {}", new_size);
                if let Err(e) = sdsf_file.zero_entries(original_file_size, new_size) {
                    error!("Error zeroing space in extended file: {:?}", e);
                    return Err(e);
                }
                let slot_number = original_file_size / SECRET_SIZE;
                slot_number
            }
        };

        debug!("Writing new deletion secret to key slot {}", empty_slot);
        if let Err(e) = sdsf_file
            .write_block(empty_slot * SECRET_SIZE, &secure_deletion_data.secure_deletion_secret)
        {
            error!("Failed to write new deletion secret to key slot {}: {:?}", empty_slot, e);
            return Err(e);
        }
        if let Err(e) = sdsf_file.finish_transaction() {
            error!(
                "Failed to commit transaction writing new deletion secret to slot {}: {:?}",
                empty_slot, e
            );
            return Err(e);
        }
        debug!("Committed new secret");
        Ok((SecureDeletionSlot(empty_slot as u32), secure_deletion_data))
    }

    fn get_secret(&self, slot: SecureDeletionSlot) -> Result<SecureDeletionData, Error> {
        let mut current_try = 0;
        let mut secure_deletion_data = loop {
            let data = self.get_factory_reset_secret();
            if (data.is_ok()) || (current_try >= MAX_TRIES) {
                break data?;
            }
            current_try += 1;
        };
        let requested_slot = slot.0 as usize;
        if requested_slot == 0 || requested_slot == 1 {
            warn!("Requested slot {} which corresponds to factory reset secret", requested_slot);
            return Err(km_err!(
                InvalidArgument,
                "requested slot {} which does not contain a per-key secret",
                requested_slot
            ));
        }

        current_try = 0;
        loop {
            match self.read_slot_data(slot, &mut secure_deletion_data.secure_deletion_secret) {
                Ok(_) => {
                    debug!(
                        "Read secure deletion secret, size: {}",
                        secure_deletion_data.secure_deletion_secret.len()
                    );
                    break Ok(secure_deletion_data);
                }
                Err(e) => {
                    if current_try >= MAX_TRIES {
                        break Err(e);
                    }
                }
            }
            current_try += 1;
        }
    }

    fn delete_secret(&mut self, slot: SecureDeletionSlot) -> Result<(), Error> {
        let requested_slot = slot.0 as usize;
        if requested_slot == 0 || requested_slot == 1 {
            debug!("key_slot == {}, nothing to delete", requested_slot);
            return Err(km_err!(
                InvalidArgument,
                "requested slot {} which does not contain a per-key secret",
                requested_slot
            ));
        }
        let key_slot_start = requested_slot * SECRET_SIZE;
        let key_slot_end = key_slot_start + SECRET_SIZE;
        if key_slot_start < FACTORY_FIRST_SECURE_DELETION_SECRET_POS {
            // This should be unhittable (the test for slot 0 or 1 above will trigger) but keep
            // in just in case of future arithmetic errors.
            return Err(km_err!(
                InvalidArgument,
                "attempted to delete invalid key slot {}",
                requested_slot
            ));
        }
        // TODO: Check if we should also stop trying to delete the key after some number of retries.
        //       C++ code doesn't stop retrying, which is the current behavior here.
        loop {
            let mut session = match get_secure_deletion_secret_file_session(true) {
                Ok(session) => session,
                Err(e) => {
                    error!("Failed to open session to retrieve secure deletion data: {:?}", e);
                    continue;
                }
            };
            let mut sdsf_file = match SecureDeletionSecretFile::open_or_create(&mut session) {
                Ok(sdsf_file) => sdsf_file,
                Err(e) => {
                    error!("Failed to open file to retrieve secure deletion data: {:?}", e);
                    continue;
                }
            };
            let file_size = match sdsf_file.get_file_size() {
                Ok(size) => size,
                Err(_) => continue,
            };
            if key_slot_end > file_size {
                return Err(km_err!(
                    InvalidArgument,
                    "attempted to delete invalid key slot {}",
                    requested_slot
                ));
            }
            if let Err(_) = sdsf_file.zero_entries(key_slot_start, key_slot_end) {
                continue;
            }
            debug!(
                "Deleted secure key slot {}, zeroing {} to {}",
                requested_slot, key_slot_start, key_slot_end
            );
            if let Err(e) = sdsf_file.finish_transaction() {
                error!(
                    "Failed to commit transaction deleting key at slot {}: {:?}",
                    requested_slot, e
                );
                continue;
            }
            debug!("Committed deletion");
            break;
        }
        Ok(())
    }

    fn delete_all(&mut self) {
        // TODO: Check if we should also stop trying to delete all keys after some number of
        //       retries. C++ code doesn't stop retrying, which is the current behavior here.
        loop {
            match delete_secure_deletion_secret_file() {
                Ok(_) => break,
                Err(e) => error!("Couldn't delete file. Received error: {:?}", e),
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use kmr_crypto_boring::rng::BoringRng;
    use test::{expect, expect_eq, expect_ne};

    fn secret_manager_file_exists() -> bool {
        let mut session =
            Session::new(Port::TamperProof, true).expect("Couldn't connect to storage");
        session.open_file(SECURE_DELETION_SECRET_FILENAME, OpenMode::Open).is_ok()
    }

    #[test]
    fn secret_data_is_cached() {
        let mut sdsf = TrustySecureDeletionSecretManager::new();
        sdsf.delete_all();
        expect!(!secret_manager_file_exists(), "Couldn't delete secret manager file");
        let mut rng = BoringRng::default();
        let secret1 =
            sdsf.get_or_create_factory_reset_secret(&mut rng).expect("Couldn't create secret");
        let mut session = get_secure_deletion_secret_file_session(true).ok();
        let secret2 = match sdsf.get_factory_reset_secret_impl(session.as_mut()) {
            Ok(RetrieveSecureDeletionSecretFileData::CachedDataFound(secret)) => secret,
            _ => panic!("Data should have been cached"),
        };
        let secret3 =
            sdsf.get_or_create_factory_reset_secret(&mut rng).expect("Couldn't get secret");
        expect_eq!(
            secret1.factory_reset_secret,
            secret2.factory_reset_secret,
            "Should have retrieved the same secret"
        );
        expect_eq!(
            secret1.secure_deletion_secret,
            secret2.secure_deletion_secret,
            "Should have retrieved the same secret"
        );
        expect_eq!(secret1.secure_deletion_secret, [0; SECRET_SIZE], "Deletion secret should be 0");
        expect_ne!(
            secret1.factory_reset_secret,
            [0; FACTORY_RESET_SECRET_SIZE],
            "Factory reset secret should not be 0"
        );
        expect_eq!(
            secret1.factory_reset_secret,
            secret3.factory_reset_secret,
            "Should have retrieved the same secret"
        );
        sdsf.factory_reset_secret.replace(None);
        let secret3 = match sdsf.get_factory_reset_secret_impl(session.as_mut()) {
            Ok(RetrieveSecureDeletionSecretFileData::DataFoundOnFile(secret)) => secret,
            _ => panic!("Data couldn't be read from file."),
        };
        expect_eq!(
            secret1.factory_reset_secret,
            secret3.factory_reset_secret,
            "Should have retrieved the same secret"
        );
        sdsf.delete_all();
        expect!(!secret_manager_file_exists(), "Couldn't delete secret manager file");
    }

    #[test]
    fn new_secret_data_file_is_clean() {
        let mut sdsf = TrustySecureDeletionSecretManager::new();
        sdsf.delete_all();
        expect!(!secret_manager_file_exists(), "Couldn't delete secret manager file");
        let mut rng = BoringRng::default();
        let secret1 =
            sdsf.get_or_create_factory_reset_secret(&mut rng).expect("Couldn't create secret");
        let num_initial_slots = BLOCK_SIZE / SECRET_SIZE;
        for slot_num in 2..num_initial_slots {
            let secret =
                sdsf.get_secret(SecureDeletionSlot(slot_num as u32)).expect("Couldn't read slot");
            expect_eq!(
                secret.secure_deletion_secret,
                [0; SECRET_SIZE],
                "Deletion secret should be 0"
            );
            expect_eq!(
                secret.factory_reset_secret,
                secret1.factory_reset_secret,
                "Factory reset secret should match"
            );
        }
        let secret = sdsf.get_secret(SecureDeletionSlot(num_initial_slots as u32));
        expect!(secret.is_err(), "Read outside of initial range should fail");
        sdsf.delete_all();
        expect!(!secret_manager_file_exists(), "Couldn't delete secret manager file");
    }

    // Not running next test because it takes too long when run on build server, which causes unit
    // tests to timeout sometimes. Also not using #[ignore] because it doesn't seem to be supported
    // yet.

    //#[test]
    #[allow(dead_code)]
    fn new_secret_data_file_expands() {
        let mut sdsf = TrustySecureDeletionSecretManager::new();
        sdsf.delete_all();
        expect!(!secret_manager_file_exists(), "Couldn't delete secret manager file");
        let mut rng = BoringRng::default();
        let _secret1 =
            sdsf.get_or_create_factory_reset_secret(&mut rng).expect("Couldn't create secret");
        let max_num_slots = MAX_SECRET_FILE_SIZE / SECRET_SIZE;
        for slot_num in 2..max_num_slots {
            let (deletion_slot, deletion_data) = sdsf
                .new_secret(&mut rng, SlotPurpose::KeyGeneration)
                .expect("Couldn't create secret");
            // This test assumes order of secret creation on an empty file; next line can be changed
            // to something like a map (to check that an empty slot if chosen every time) if order
            // is not sequential anymore.
            expect_eq!(deletion_slot.0 as usize, slot_num, "Wrong slot used for new secret");
            expect_ne!(
                deletion_data.secure_deletion_secret,
                [0; SECRET_SIZE],
                "Deletion secret should not be 0"
            );
            expect_ne!(
                deletion_data.secure_deletion_secret[0] & IN_USE_FLAG,
                0,
                "Slot should be marked as in use"
            );
            let slot_data = sdsf.get_secret(deletion_slot).expect("Couldn't read back secret");
            expect_eq!(
                deletion_data.secure_deletion_secret,
                slot_data.secure_deletion_secret,
                "Secret data should match"
            );
            expect_eq!(
                deletion_data.factory_reset_secret,
                slot_data.factory_reset_secret,
                "Factory reset secret should match"
            );
        }
        let size_failure = sdsf.new_secret(&mut rng, SlotPurpose::KeyGeneration);
        expect!(size_failure.is_err(), "Shouldn't be able to increase secret file size any larger");
        // Testing upgrade flow
        let max_num_upgrade_slots = (MAX_SECRET_FILE_SIZE_FOR_UPGRADES) / SECRET_SIZE;
        for slot_num in max_num_slots..max_num_upgrade_slots {
            let (deletion_slot, deletion_data) = sdsf
                .new_secret(&mut rng, SlotPurpose::KeyUpgrade)
                .expect("Couldn't create secret for upgrade flow");
            expect_eq!(deletion_slot.0 as usize, slot_num, "Wrong slot used for new secret");
            expect_ne!(
                deletion_data.secure_deletion_secret,
                [0; SECRET_SIZE],
                "Deletion secret should not be 0"
            );
            expect_ne!(
                deletion_data.secure_deletion_secret[0] & IN_USE_FLAG,
                0,
                "Slot should be marked as in use"
            );
            let slot_data = sdsf.get_secret(deletion_slot).expect("Couldn't read back secret");
            expect_eq!(
                deletion_data.secure_deletion_secret,
                slot_data.secure_deletion_secret,
                "Secret data should match"
            );
            expect_eq!(
                deletion_data.factory_reset_secret,
                slot_data.factory_reset_secret,
                "Factory reset secret should match"
            );
        }
        let size_failure = sdsf.new_secret(&mut rng, SlotPurpose::KeyUpgrade);
        expect!(size_failure.is_err(), "Shouldn't be able to increase secret file size any larger");
        // Testing deletion
        for slot_num in (2..max_num_upgrade_slots).rev() {
            let slot = SecureDeletionSlot(slot_num as u32);
            sdsf.delete_secret(slot).expect("Couldn't delete secret");
            let slot_data = sdsf.get_secret(slot).expect("Couldn't read back secret");
            expect_eq!(
                slot_data.secure_deletion_secret,
                [0; SECRET_SIZE],
                "Deletion secret should be 0"
            );
            let (deletion_slot, deletion_data) =
                sdsf.new_secret(&mut rng, SlotPurpose::KeyUpgrade).expect("Couldn't create secret");
            expect_eq!(deletion_slot.0 as usize, slot_num, "Wrong slot used for new secret");
            expect_ne!(
                deletion_data.secure_deletion_secret,
                [0; SECRET_SIZE],
                "Deletion secret should not be 0"
            );
            expect_ne!(
                deletion_data.secure_deletion_secret[0] & IN_USE_FLAG,
                0,
                "Slot should be marked as in use"
            );
        }
        sdsf.delete_all();
        expect!(!secret_manager_file_exists(), "Couldn't delete secret manager file");
    }

    #[test]
    fn new_secret_data_dont_affect_neighbors() {
        let mut sdsf = TrustySecureDeletionSecretManager::new();
        sdsf.delete_all();
        expect!(!secret_manager_file_exists(), "Couldn't delete secret manager file");
        let mut rng = BoringRng::default();
        let reset_secret = sdsf
            .get_or_create_factory_reset_secret(&mut rng)
            .expect("Couldn't create factory reset secret");
        let (deletion_slot_1, _deletion_data_1) =
            sdsf.new_secret(&mut rng, SlotPurpose::KeyGeneration).expect("Couldn't create secret");
        sdsf.delete_secret(deletion_slot_1).expect("Couldn't delete secret");
        // Delete cached data
        sdsf.factory_reset_secret.replace(None);
        let reset_secret_1 =
            sdsf.get_factory_reset_secret().expect("Couldn't get factory reset secret");
        expect_eq!(
            reset_secret.factory_reset_secret,
            reset_secret_1.factory_reset_secret,
            "Factory reset secret should match"
        );
        let (deletion_slot_1, deletion_data_1) =
            sdsf.new_secret(&mut rng, SlotPurpose::KeyGeneration).expect("Couldn't create secret");
        let (deletion_slot_2, deletion_data_2) =
            sdsf.new_secret(&mut rng, SlotPurpose::KeyGeneration).expect("Couldn't create secret");
        let (deletion_slot_3, deletion_data_3) =
            sdsf.new_secret(&mut rng, SlotPurpose::KeyGeneration).expect("Couldn't create secret");
        let (deletion_slot_4, deletion_data_4) =
            sdsf.new_secret(&mut rng, SlotPurpose::KeyGeneration).expect("Couldn't create secret");
        let (deletion_slot_5, deletion_data_5) =
            sdsf.new_secret(&mut rng, SlotPurpose::KeyGeneration).expect("Couldn't create secret");
        let slot_data = sdsf.get_secret(deletion_slot_1).expect("Couldn't read back secret");
        expect_eq!(
            slot_data.secure_deletion_secret,
            deletion_data_1.secure_deletion_secret,
            "Secret data should match"
        );
        let slot_data = sdsf.get_secret(deletion_slot_2).expect("Couldn't read back secret");
        expect_eq!(
            slot_data.secure_deletion_secret,
            deletion_data_2.secure_deletion_secret,
            "Secret data should match"
        );
        let slot_data = sdsf.get_secret(deletion_slot_3).expect("Couldn't read back secret");
        expect_eq!(
            slot_data.secure_deletion_secret,
            deletion_data_3.secure_deletion_secret,
            "Secret data should match"
        );
        let slot_data = sdsf.get_secret(deletion_slot_4).expect("Couldn't read back secret");
        expect_eq!(
            slot_data.secure_deletion_secret,
            deletion_data_4.secure_deletion_secret,
            "Secret data should match"
        );
        let slot_data = sdsf.get_secret(deletion_slot_5).expect("Couldn't read back secret");
        expect_eq!(
            slot_data.secure_deletion_secret,
            deletion_data_5.secure_deletion_secret,
            "Secret data should match"
        );
        sdsf.delete_secret(deletion_slot_3).expect("Couldn't delete secret");
        let slot_data = sdsf.get_secret(deletion_slot_1).expect("Couldn't read back secret");
        expect_eq!(
            slot_data.secure_deletion_secret,
            deletion_data_1.secure_deletion_secret,
            "Secret data should match"
        );
        let slot_data = sdsf.get_secret(deletion_slot_2).expect("Couldn't read back secret");
        expect_eq!(
            slot_data.secure_deletion_secret,
            deletion_data_2.secure_deletion_secret,
            "Secret data should match"
        );
        let slot_data = sdsf.get_secret(deletion_slot_3).expect("Couldn't read back secret");
        expect_ne!(
            slot_data.secure_deletion_secret,
            deletion_data_3.secure_deletion_secret,
            "Secret data should not match anymore"
        );
        let slot_data = sdsf.get_secret(deletion_slot_4).expect("Couldn't read back secret");
        expect_eq!(
            slot_data.secure_deletion_secret,
            deletion_data_4.secure_deletion_secret,
            "Secret data should match"
        );
        let slot_data = sdsf.get_secret(deletion_slot_5).expect("Couldn't read back secret");
        expect_eq!(
            slot_data.secure_deletion_secret,
            deletion_data_5.secure_deletion_secret,
            "Secret data should match"
        );
        // Delete cached data
        sdsf.factory_reset_secret.replace(None);
        let reset_secret_1 =
            sdsf.get_factory_reset_secret().expect("Couldn't get factory reset secret");
        expect_eq!(
            reset_secret.factory_reset_secret,
            reset_secret_1.factory_reset_secret,
            "Factory reset secret should match"
        );
        sdsf.delete_all();
        expect!(!secret_manager_file_exists(), "Couldn't delete secret manager file");
    }
}

/// Wrapper to allow a single instance of [`SecureDeletionSecretManager`] to be shared.
#[derive(Clone)]
pub struct SharedSddManager<T> {
    inner: Rc<RefCell<T>>,
}

impl<T> SharedSddManager<T> {
    /// Move a [`SecureDeletionSecretManager`] into a shareable wrapper.
    pub fn new(inner: T) -> Self {
        Self { inner: Rc::new(RefCell::new(inner)) }
    }
}

impl<T: SecureDeletionSecretManager> SecureDeletionSecretManager for SharedSddManager<T> {
    fn get_or_create_factory_reset_secret(
        &mut self,
        rng: &mut dyn crypto::Rng,
    ) -> Result<SecureDeletionData, Error> {
        self.inner.borrow_mut().get_or_create_factory_reset_secret(rng)
    }

    fn get_factory_reset_secret(&self) -> Result<SecureDeletionData, Error> {
        self.inner.borrow_mut().get_factory_reset_secret()
    }

    fn new_secret(
        &mut self,
        rng: &mut dyn crypto::Rng,
        purpose: kmr_common::keyblob::SlotPurpose,
    ) -> Result<(SecureDeletionSlot, SecureDeletionData), Error> {
        self.inner.borrow_mut().new_secret(rng, purpose)
    }

    fn get_secret(&self, slot: SecureDeletionSlot) -> Result<SecureDeletionData, Error> {
        self.inner.borrow().get_secret(slot)
    }

    fn delete_secret(&mut self, slot: SecureDeletionSlot) -> Result<(), Error> {
        self.inner.borrow_mut().delete_secret(slot)
    }

    fn delete_all(&mut self) {
        self.inner.borrow_mut().delete_all()
    }
}
