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

//! This library exposes [`PolicyGatedStorage`] and related traits that are useful for
//! Secretkeeper service implementation.

use alloc::boxed::Box;
use alloc::vec;
use alloc::vec::Vec;
use ciborium::Value;
use coset::{AsCborValue, CborSerializable, CoseError, CoseError::UnexpectedItem};
use dice_policy::chain_matches_policy;
use log::error;
use log::info;
use secretkeeper_comm::data_types::error::{Error, SecretkeeperError};
use secretkeeper_comm::data_types::{Id, Secret};

/// `PolicyGatedStorage` encapsulates the storage layer of Secretkeeper, which, in addition to
/// conventional storage, provides DICE policy based access control.  A client can restrict the
/// access to its stored entry.
///
/// 1) Storage: `PolicyGatedStorage` allows storing a Secret (and sealing_policy) which is indexed
/// by an [`Id`]. Under the hood, it uses a Key-Value based storage, which should be provided on
/// initialization.  The security properties (Confidentiality/Integrity/Persistence) expected from
/// the Storage are listed in ISecretkeeper.aidl
///
/// 2) Access control: Secretkeeper uses DICE policy based access control. Each secret is
/// associated with a sealing_policy, which is a DICE policy. This is a required input while
/// storing a secret. Further access to this secret is restricted to clients whose DICE chain
/// adheres to the sealing_policy.
pub struct PolicyGatedStorage {
    secure_store: Box<dyn KeyValueStore>,
}

impl PolicyGatedStorage {
    /// Initialize Secretkeeper with a Key-Value store. Note: this Key-Value storage is the
    /// only `persistent` part of Secretkeeper HAL.
    pub fn init(secure_store: Box<dyn KeyValueStore>) -> Self {
        Self { secure_store }
    }

    /// Store or update a secret (only if the stored policy allows access to client).
    ///
    /// # Arguments
    /// * `id`: Unique identifier of the `secret`. A client is allowed to have multiple entries
    ///    each with a distinct `id`. If an entry corresponding to `id` is already present AND
    ///    `dice_chain` matches the (already present) `sealing_policy` -> update the
    ///    corresponding [`Secret`] & its `sealing_policy`.
    ///
    /// * `secret`: The [`Secret`] the client wishes to store.
    ///
    /// * `sealing_policy`: The DICE policy corresponding to the secret. Only clients with DICE
    ///    chain that matches the sealing_policy are allowed to access Secret.
    ///
    /// * `dice_chain`: The serialized CBOR encoded DICE chain of the client, adhering to
    ///    Android Profile for DICE.
    ///    <https://pigweed.googlesource.com/open-dice/+/refs/heads/main/docs/android.md>
    ///    This method verifies that the dice_chain matches the provided sealing_policy but does
    ///    not check that the DICE chain indeed belongs to the client.
    ///    The caller must ensure that the DICE chain indeed belongs to client.
    pub fn store(
        &mut self,
        id: Id,
        secret: Secret,
        sealing_policy: Vec<u8>,
        dice_chain: &[u8],
    ) -> Result<(), SecretkeeperError> {
        // Check if an entry for the id is already present & if so, whether the dice_chain matches
        // the already present sealing_policy.
        match self.get(&id, dice_chain, None) {
            Ok(..) => {
                info!("Found an existing entry, access check succeeded, updating the secret");
            }
            Err(SecretkeeperError::EntryNotFound) => {
                info!("No existing entry, attempting to create a new entry..");
            }
            Err(e) => {
                info!("There may have been an existing entry, but reading it failed {:?}", e);
                return Err(e);
            }
        }

        // Check that the dice_chain matches the sealing_policy on the secret it is trying
        // to store. This ensures client can not store a secret that it cannot access itself.
        // Such requests are considered malformed.
        chain_matches_policy(dice_chain, &sealing_policy).map_err(request_malformed)?;

        let id = id.0.as_slice();
        let entry = Entry { secret, sealing_policy }.to_vec().map_err(serial_err)?;
        self.secure_store.store(id, &entry).map_err(unexpected_err)?;
        Ok(())
    }

    /// Get the secret.
    ///
    /// # Arguments
    /// * `id`: Unique identifier of the secret.
    ///
    /// * `dice_chain`: The serialized CBOR encoded DICE chain of the client, adhering to
    ///    Android Profile for DICE.
    ///    <https://pigweed.googlesource.com/open-dice/+/refs/heads/main/docs/android.md>
    ///    This method verifies that the dice_chain matches the provided sealing_policy but does
    ///    not check that the DICE chain indeed belongs to the client. The caller must ensure that
    ///    the DICE chain indeed belongs to client.
    ///
    /// * `updated_sealing_policy`: The updated dice_policy corresponding to the [`Secret`].
    ///    This is an optional parameter and can be used to replace the sealing_policy associated
    ///    with the [`Secret`].
    pub fn get(
        &mut self,
        id: &Id,
        dice_chain: &[u8],
        updated_sealing_policy: Option<Vec<u8>>,
    ) -> Result<Secret, SecretkeeperError> {
        let id = id.0.as_slice();
        match self.secure_store.get(id).map_err(unexpected_err)? {
            Some(entry_serialized) => {
                let entry = Entry::from_slice(&entry_serialized).map_err(serial_err)?;
                chain_matches_policy(dice_chain, &entry.sealing_policy).map_err(policy_err)?;

                // Replace the entry with updated_sealing_policy.
                if let Some(updated_sealing_policy) = updated_sealing_policy {
                    chain_matches_policy(dice_chain, &updated_sealing_policy)
                        .map_err(policy_err)?;
                    let new_entry = Entry {
                        secret: entry.secret.clone(),
                        sealing_policy: updated_sealing_policy,
                    }
                    .to_vec()
                    .map_err(serial_err)?;
                    self.secure_store.store(id, &new_entry).map_err(unexpected_err)?;
                }
                Ok(entry.secret)
            }
            None => {
                info!("Entry for id: {:?} not found", id);
                Err(SecretkeeperError::EntryNotFound)
            }
        }
    }

    /// Delete the `value` corresponding to the given `key`.
    pub fn delete(&mut self, key: &Id) -> Result<(), SecretkeeperError> {
        self.secure_store.delete(key.0.as_slice()).map_err(unexpected_err)
    }

    /// Delete all stored key-value pairs.
    pub fn delete_all(&mut self) -> Result<(), SecretkeeperError> {
        self.secure_store.delete_all().map_err(unexpected_err)
    }
}

#[inline]
fn unexpected_err<E: core::fmt::Debug>(err: E) -> SecretkeeperError {
    sk_err(SecretkeeperError::UnexpectedServerError, err)
}

#[inline]
fn serial_err<E: core::fmt::Debug>(err: E) -> SecretkeeperError {
    sk_err(SecretkeeperError::SerializationError, err)
}

#[inline]
fn policy_err<E: core::fmt::Debug>(err: E) -> SecretkeeperError {
    sk_err(SecretkeeperError::DicePolicyError, err)
}
#[inline]
fn request_malformed<E: core::fmt::Debug>(err: E) -> SecretkeeperError {
    sk_err(SecretkeeperError::RequestMalformed, err)
}

fn sk_err<E: core::fmt::Debug>(sk_error: SecretkeeperError, err: E) -> SecretkeeperError {
    error!("Got error {:?}, :{:?}", sk_error, err);
    sk_error
}

// Entry describes the `value` stored in the Key-Value store by PolicyGatedStorage.
// ```cddl
//  Entry = [
//    secret : bstr .size 32,
//    sealing_policy : bstr .cbor DicePolicy
//  ]
// ```
#[derive(Debug)]
struct Entry {
    secret: Secret,
    sealing_policy: Vec<u8>, // DicePolicy serialized into bytes
}

impl AsCborValue for Entry {
    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        let [secret, sealing_policy] = value
            .into_array()
            .map_err(|_| UnexpectedItem("-", "Array"))?
            .try_into()
            .map_err(|_| UnexpectedItem("Array", "Array of size 2"))?;
        let secret = Secret::from_cbor_value(secret)?;
        let sealing_policy =
            sealing_policy.into_bytes().map_err(|_| UnexpectedItem("-", "Bytes"))?;
        Ok(Self { secret, sealing_policy })
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        Ok(Value::Array(vec![self.secret.to_cbor_value()?, Value::from(self.sealing_policy)]))
    }
}

impl CborSerializable for Entry {}

/// Defines the behavior of a simple Key-Value based storage, where both key & value are bytes.
/// Expected persistence property is dictated by the concrete type implementing the trait.
pub trait KeyValueStore {
    /// Store a key-value pair. If the key is already present, replace the corresponding value.
    fn store(&mut self, key: &[u8], value: &[u8]) -> Result<(), Error>;

    /// Get the `value` corresponding to the given `key`. Return None if the key is not found.
    fn get(&self, key: &[u8]) -> Result<Option<Vec<u8>>, Error>;

    /// Delete the `value` corresponding to the given `key`.
    fn delete(&mut self, key: &[u8]) -> Result<(), Error>;

    /// Delete all stored key-value pairs.
    fn delete_all(&mut self) -> Result<(), Error>;
}
