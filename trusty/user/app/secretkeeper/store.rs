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

//! Implementation of the `KeyValueStore` trait using Trusty secure storage.
//!
//! Store each secret in a file of its own (mapping the secret ID to the filename) as there are not
//! expected to be many extant secrets.  However, use a prefix for the filename to allow easier
//! migration to a different scheme in future if this assumption changes.

use alloc::string::String;
use secretkeeper_comm::data_types::error::Error;
use secretkeeper_core::store::KeyValueStore;
use storage::{OpenMode, Port, Session};
use trusty_sys;

/// Store each secret in a file named "v1_<hex>", using the hex representation of the key/secret ID.
/// Note that IDs are not confidential, so can appear in logs.
const PREFIX_V1: &str = "v1_";

/// Generate the filename corresponding to a key.
fn filename(key: &[u8]) -> String {
    let mut result = String::with_capacity(PREFIX_V1.len() + 2 * key.len());
    result += PREFIX_V1;
    for byte in key {
        result += &format!("{:02x}", byte);
    }
    result
}

/// Helper macro for emitting an error.
macro_rules! ss_err {
    {  $e:expr, $($arg:tt)+ } => {
        {
            log::error!("{}: {:?}", format_args!($($arg)+), $e);
            Error::UnexpectedError
        }
    };
}

/// Create a storage session.
fn create_session() -> Result<Session, Error> {
    // Use TD storage, which means that:
    // - storage is only available after Android has booted
    // - storage is wiped on factory reset
    // - size of stored data isn't problematic
    Session::new(Port::TamperDetect, /* wait_for_port= */ true)
        .map_err(|e| ss_err!(e, "Couldn't create storage session"))
}

/// An implementation of `KeyValueStore` backed by secure storage.
#[derive(Default)]
pub struct SecureStore;

impl KeyValueStore for SecureStore {
    fn store(&mut self, key: &[u8], val: &[u8]) -> Result<(), Error> {
        let filename = filename(key);
        let mut session = create_session()?;

        // This will overwrite the value if key is already present.
        let mut file = session
            .open_file(&filename, OpenMode::Create)
            .map_err(|e| ss_err!(e, "Couldn't create file '{filename}'"))?;
        session.write_all(&mut file, val).map_err(|e| ss_err!(e, "Failed to write data"))?;
        Ok(())
    }

    fn get(&self, key: &[u8]) -> Result<Option<Vec<u8>>, Error> {
        let filename = filename(key);
        let mut session = create_session()?;
        let file = match session.open_file(&filename, OpenMode::Open) {
            Ok(f) => f,
            Err(storage::Error::Code(trusty_sys::Error::NotFound)) => return Ok(None),
            Err(e) => return Err(ss_err!(e, "Failed to open file '{filename}'")),
        };
        let size = session
            .get_size(&file)
            .map_err(|e| ss_err!(e, "Failed to get size for '{filename}'"))?;
        let mut buffer = vec![0; size];
        let content = session
            .read_all(&file, buffer.as_mut_slice())
            .map_err(|e| ss_err!(e, "Failed to read '{filename}'"))?;
        let total_size = content.len();
        buffer.resize(total_size, 0);
        Ok(Some(buffer))
    }

    fn delete(&mut self, key: &[u8]) -> Result<(), Error> {
        let filename = filename(key);
        let mut session = create_session()?;
        match session.remove(&filename) {
            Ok(_) => Ok(()),
            Err(storage::Error::Code(trusty_sys::Error::NotFound)) => Ok(()),
            Err(e) => Err(ss_err!(e, "Failed to delete file '{filename}'")),
        }
    }

    fn delete_all(&mut self) -> Result<(), Error> {
        let mut failed = false;
        let mut session = create_session()?;
        for entry in session.list_files().map_err(|e| ss_err!(e, "Failed to list files"))? {
            match entry {
                Ok((filename, _state)) if filename.starts_with(PREFIX_V1) => {
                    let result = session
                        .remove(&filename)
                        .map_err(|e| ss_err!(e, "Failed to delete '{filename}'"));
                    if result.is_err() {
                        failed = true;
                    }
                }
                Ok((filename, _state)) => log::info!("Skipping unrelated file {filename}"),
                Err(e) => log::error!("Failed to delete an entry: {e:?}"),
            };
        }
        if failed {
            Err(Error::UnexpectedError)
        } else {
            Ok(())
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use test::expect_eq;

    const KEY1: &[u8] = b"bogus key 1";
    const KEY2: &[u8] = b"bogus key 2";
    const SECRET1: &[u8] = b"bogus secret 1";
    const SECRET2: &[u8] = b"bogus secret 2";

    #[test]
    fn test_secretkeeper_store() {
        let mut store = SecureStore::default();

        // Ensure consistent state before.
        store.delete(KEY1).unwrap();
        store.delete(KEY2).unwrap();

        store.store(KEY1, SECRET1).unwrap();
        expect_eq!(store.get(KEY1), Ok(Some(SECRET1.to_vec())));
        expect_eq!(store.get(KEY2), Ok(None));
        store.store(KEY1, SECRET2).unwrap();
        expect_eq!(store.get(KEY1), Ok(Some(SECRET2.to_vec())));
        expect_eq!(store.get(KEY2), Ok(None));
        store.store(KEY1, SECRET1).unwrap();
        expect_eq!(store.get(KEY1), Ok(Some(SECRET1.to_vec())));
        expect_eq!(store.get(KEY2), Ok(None));
        store.store(KEY2, SECRET1).unwrap();
        expect_eq!(store.get(KEY1), Ok(Some(SECRET1.to_vec())));
        expect_eq!(store.get(KEY2), Ok(Some(SECRET1.to_vec())));
        store.delete(KEY1).unwrap();
        expect_eq!(store.get(KEY1), Ok(None));
        expect_eq!(store.get(KEY2), Ok(Some(SECRET1.to_vec())));
        store.delete(KEY2).unwrap();
        expect_eq!(store.get(KEY1), Ok(None));
        expect_eq!(store.get(KEY2), Ok(None));

        // Ensure consistent state after.
        store.delete(KEY1).unwrap();
        store.delete(KEY2).unwrap();
    }
}
