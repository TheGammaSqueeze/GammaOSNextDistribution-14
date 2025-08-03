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

//! This library is a stub for Secretkeeper, which can be used by clients. It exposes
//! Secretkeeper Session which can be used for using the SecretManagement API.
//! It encapsulates the Cryptography! AuthgraphKeyExchange is triggered on creation of a session
//! and the messages is encrypted/decrypted using the shared keys.

mod authgraph_dev;
pub mod dice;

use crate::authgraph_dev::AgDevice;
use crate::dice::OwnedDiceArtifactsWithExplicitKey;

use authgraph_boringssl as boring;
use authgraph_core::keyexchange as ke;
use authgraph_core::key;
use android_hardware_security_secretkeeper::aidl::android::hardware::security::secretkeeper
    ::ISecretkeeper::ISecretkeeper;
use android_hardware_security_authgraph::aidl::android::hardware::security::authgraph::{
    IAuthGraphKeyExchange::IAuthGraphKeyExchange, PlainPubKey::PlainPubKey, PubKey::PubKey,
    SessionIdSignature::SessionIdSignature, Identity::Identity,
};
use coset::{CoseKey, CborSerializable, CoseEncrypt0};
use secretkeeper_core::cipher;
use secretkeeper_comm::data_types::SeqNum;
use secretkeeper_comm::wire::ApiError;
use std::cell::RefCell;
use std::fmt;
use std::rc::Rc;

/// A Secretkeeper session that can be used by client, this encapsulates the Authgraph Key exchange
/// session as well as the encryption/decryption of request/response to/from Secretkeeper.
pub struct SkSession {
    sk: binder::Strong<dyn ISecretkeeper>,
    encryption_key: key::AesKey,
    decryption_key: key::AesKey,
    session_id: Vec<u8>,
    // We allow seq numbers to be 0 to u64::MAX-1, increment beyond that fails
    // Sequence number for next outgoing message encryption.
    seq_num_outgoing: SeqNum,
    // Sequence number for decrypting the next incoming message.
    seq_num_incoming: SeqNum,
}

impl SkSession {
    /// Create a new Secretkeeper session. This triggers an AuthgraphKeyExchange protocol with a
    /// local `source` and remote `sink`.
    ///
    /// # Arguments
    /// `sk`: Secretkeeper instance
    ///
    /// `dice`: DiceArtifacts of the caller (i.e, Sk client)
    ///
    /// `expected_sk_key`: Expected Identity of Secretkeeper. If set, the claimed peer identity is
    /// matched against this and in cases of mismatch, error is returned.
    pub fn new(
        sk: binder::Strong<dyn ISecretkeeper>,
        dice: &OwnedDiceArtifactsWithExplicitKey,
        expected_sk_key: Option<CoseKey>,
    ) -> Result<Self, Error> {
        let ag_dev = Rc::new(RefCell::new(AgDevice::new(dice, expected_sk_key)?));
        let ([encryption_key, decryption_key], session_id) =
            authgraph_key_exchange(sk.clone(), ag_dev.clone())?;
        Ok(Self {
            sk,
            encryption_key,
            decryption_key,
            session_id,
            seq_num_outgoing: SeqNum::new(),
            seq_num_incoming: SeqNum::new(),
        })
    }

    /// Wrapper around `ISecretkeeper::processSecretManagementRequest`. This additionally handles
    /// encryption and decryption.
    pub fn secret_management_request(&mut self, req_data: &[u8]) -> Result<Vec<u8>, Error> {
        let aes_gcm = boring::BoringAes;
        let rng = boring::BoringRng;
        let req_aad = self.seq_num_outgoing.get_then_increment()?;
        let request_bytes = cipher::encrypt_message(
            &aes_gcm,
            &rng,
            &self.encryption_key,
            &self.session_id,
            req_data,
            &req_aad,
        )
        .map_err(Error::CipherError)?;

        let response_bytes = self.sk.processSecretManagementRequest(&request_bytes)?;

        let response_encrypt0 = CoseEncrypt0::from_slice(&response_bytes)?;
        let expected_res_aad = self.seq_num_incoming.get_then_increment()?;
        cipher::decrypt_message(
            &aes_gcm,
            &self.decryption_key,
            &response_encrypt0,
            &expected_res_aad,
        )
        .map_err(Error::CipherError)
    }

    /// Get the encryption key corresponding to the session. Note on usage: This (along with other
    /// getters [`decryption_key()`] & [`session_id()`]) can be used for custom encrypting requests
    /// (for ex, with different aad, instead of using [`secret_management_request`] method.
    /// In that case, seq_num_outgoing & seq_num_incoming will be out of sync. Recommended use for
    /// testing only.
    pub fn encryption_key(&self) -> &key::AesKey {
        &self.encryption_key
    }

    /// Get the decryption key corresponding to the session.
    pub fn decryption_key(&self) -> &key::AesKey {
        &self.decryption_key
    }

    /// Get the session_id.
    pub fn session_id(&self) -> &[u8] {
        &self.session_id
    }
}

impl fmt::Debug for SkSession {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        f.debug_struct("SkSession").field("session_id", &hex::encode(&self.session_id)).finish()
    }
}

/// Errors thrown by this SkSession.
#[derive(Debug)]
pub enum Error {
    /// These are server errors : thrown from remote instance (of Authgraph or Secretkeeper)
    BinderStatus(binder::Status),
    /// These are errors thrown from (local) source Authgraph instance.
    AuthgraphError(authgraph_core::error::Error),
    /// Error originating while encryption/decryption of packets (at source).
    CipherError(ApiError),
    /// Errors originating in the coset library.
    CoseError(coset::CoseError),
    /// Unexpected item encountered (got, want).
    UnexpectedItem(&'static str, &'static str),
    /// The set of errors from secretkeeper_comm library.
    SkCommError(secretkeeper_comm::data_types::error::Error),
}

impl std::error::Error for Error {}

impl std::fmt::Display for Error {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        match self {
            Self::BinderStatus(e) => write!(f, "Binder error {e:?}"),
            Self::AuthgraphError(e) => write!(f, "Local Authgraph instance error {e:?}"),
            Self::CipherError(e) => write!(f, "Error in encryption/decryption of packets {e:?}"),
            Self::CoseError(e) => write!(f, "Errors originating in the coset library {e:?}"),
            Self::UnexpectedItem(got, want) => {
                write!(f, "Unexpected item - Got:{got}, Expected:{want}")
            }
            Self::SkCommError(e) => write!(f, "secretkeeper_comm error: {e:?}"),
        }
    }
}

impl From<authgraph_core::error::Error> for Error {
    fn from(e: authgraph_core::error::Error) -> Self {
        Self::AuthgraphError(e)
    }
}

impl From<binder::Status> for Error {
    fn from(s: binder::Status) -> Self {
        Self::BinderStatus(s)
    }
}

impl From<coset::CoseError> for Error {
    fn from(e: coset::CoseError) -> Self {
        Self::CoseError(e)
    }
}

impl From<secretkeeper_comm::data_types::error::Error> for Error {
    fn from(e: secretkeeper_comm::data_types::error::Error) -> Self {
        Self::SkCommError(e)
    }
}

/// Perform AuthGraph key exchange, returning the session keys and session ID.
fn authgraph_key_exchange(
    sk: binder::Strong<dyn ISecretkeeper>,
    ag_dev: Rc<RefCell<AgDevice>>,
) -> Result<([key::AesKey; 2], Vec<u8>), Error> {
    let sink = sk.getAuthGraphKe()?;
    let mut source = ke::AuthGraphParticipant::new(
        boring::crypto_trait_impls(),
        ag_dev,
        1, // Each SkSession supports only 1 open Authgraph session.
    )?;
    key_exchange(&mut source, sink)
}

/// Perform AuthGraph key exchange with the provided sink and local source implementation.
/// Return the agreed AES keys in plaintext, together with the session ID.
fn key_exchange(
    local_source: &mut ke::AuthGraphParticipant,
    sink: binder::Strong<dyn IAuthGraphKeyExchange>,
) -> Result<([key::AesKey; 2], Vec<u8>), Error> {
    // Step 1: create an ephemeral ECDH key at the (local) source.
    let source_init_info = local_source.create()?;

    // Step 2: pass the source's ECDH public key and other session info to the (remote) sink.
    let init_result = sink.init(
        &build_plain_pub_key(&source_init_info.ke_key.pub_key)?,
        &vec_to_identity(&source_init_info.identity),
        &source_init_info.nonce,
        source_init_info.version,
    )?;
    let sink_init_info = init_result.sessionInitiationInfo;
    let sink_pub_key = extract_plain_pub_key(&sink_init_info.key.pubKey)?;
    let sink_info = init_result.sessionInfo;

    // Step 3: pass the sink's ECDH public key and other session info to the (local) source, so it
    // can calculate the same pair of symmetric keys.
    let source_info = local_source.finish(
        &sink_pub_key.plainPubKey,
        &sink_init_info.identity.identity,
        &sink_info.signature.signature,
        &sink_init_info.nonce,
        sink_init_info.version,
        source_init_info.ke_key,
    )?;

    // Step 4: pass the (local) source's session ID signature back to the sink, so it can check it
    // and update the symmetric keys so they're marked as authentication complete.
    let _sink_arcs = sink.authenticationComplete(
        &vec_to_signature(&source_info.session_id_signature),
        &sink_info.sharedKeys,
    )?;

    // Decrypt and return the session keys.
    let decrypted_shared_keys_array = local_source
        .decipher_shared_keys_from_arcs(&source_info.shared_keys)?
        .try_into()
        .map_err(|_| Error::UnexpectedItem("Array", "Array of size 2"))?;

    Ok((decrypted_shared_keys_array, source_info.session_id))
}

fn build_plain_pub_key(pub_key: &Option<Vec<u8>>) -> Result<PubKey, Error> {
    Ok(PubKey::PlainKey(PlainPubKey {
        plainPubKey: pub_key.clone().ok_or(Error::UnexpectedItem("None", "Some bytes"))?,
    }))
}

fn extract_plain_pub_key(pub_key: &Option<PubKey>) -> Result<&PlainPubKey, Error> {
    match pub_key {
        Some(PubKey::PlainKey(pub_key)) => Ok(pub_key),
        Some(PubKey::SignedKey(_)) => Err(Error::UnexpectedItem("Signed Key", "Public Key")),
        None => Err(Error::UnexpectedItem("No key", "Public key")),
    }
}

fn vec_to_identity(data: &[u8]) -> Identity {
    Identity { identity: data.to_vec() }
}

fn vec_to_signature(data: &[u8]) -> SessionIdSignature {
    SessionIdSignature { signature: data.to_vec() }
}

#[cfg(test)]
rdroidtest::test_main!();
