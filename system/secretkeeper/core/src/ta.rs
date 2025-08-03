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

//! Implementation of a Secretkeeper trusted application (TA).

use crate::cipher;
use crate::store::{KeyValueStore, PolicyGatedStorage};
use alloc::boxed::Box;
use alloc::collections::VecDeque;
use alloc::vec::Vec;
use authgraph_core::ag_err;
use authgraph_core::error::Error;
use authgraph_core::key::{
    AesKey, CertChain, EcSignKey, Identity, IdentityVerificationDecision,
    EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION, IDENTITY_VERSION,
};
use authgraph_core::traits::{AesGcm, CryptoTraitImpl, Device, EcDsa, Rng, Sha256};
use authgraph_wire::{ErrorCode, SESSION_ID_LEN};
use coset::{iana, CborSerializable, CoseEncrypt0};
use log::{error, trace, warn};
use secretkeeper_comm::data_types::{
    error::Error as SkInternalError,
    error::SecretkeeperError,
    packet::{RequestPacket, ResponsePacket},
    request::Request,
    request_response_impl::{
        GetSecretRequest, GetSecretResponse, GetVersionRequest, GetVersionResponse, Opcode,
        StoreSecretRequest, StoreSecretResponse,
    },
    response::Response,
    Id, SeqNum,
};
use secretkeeper_comm::wire::{
    AidlErrorCode, ApiError, PerformOpReq, PerformOpResponse, PerformOpSuccessRsp, SecretId,
};

pub mod bootloader;

/// Current Secretkeeper version.
const CURRENT_VERSION: u64 = 1;

/// Default maximum number of live session keys.
const MAX_SESSIONS_DEFAULT: usize = 8;

/// Macro to build an [`ApiError`] instance.
/// E.g. use: `aidl_err!(InternalError, "some {} format", arg)`.
#[macro_export]
macro_rules! aidl_err {
    { $error_code:ident, $($arg:tt)+ } => {
        ApiError {
            err_code: AidlErrorCode::$error_code,
            msg: alloc::format!("{}:{}: {}", file!(), line!(), format_args!($($arg)+))
        }
    };
}

/// Secretkeeper trusted application instance.
pub struct SecretkeeperTa {
    /// AES-GCM implementation.
    aes_gcm: Box<dyn AesGcm>,

    /// RNG implementation.
    rng: Box<dyn Rng>,

    /// AuthGraph per-boot-key.
    per_boot_key: AesKey,

    /// The signing key corresponding to the leaf of the Secretkeeper identity DICE chain.
    identity_sign_key: EcSignKey,

    /// Secretkeeper identity.
    identity: Identity,

    /// Current sessions.
    session_artifacts: VecDeque<SessionArtifacts>,

    /// Maximum number of current sessions.
    max_sessions: usize,

    /// Storage of secrets (& sealing policy)
    store: PolicyGatedStorage,
}

impl SecretkeeperTa {
    /// Create a TA instance using the provided trait implementations.
    pub fn new(
        ag_impls: &mut CryptoTraitImpl,
        storage_impl: Box<dyn KeyValueStore>,
        identity_curve: iana::EllipticCurve,
    ) -> Result<Self, SkInternalError> {
        Self::new_with_session_limit(ag_impls, storage_impl, identity_curve, MAX_SESSIONS_DEFAULT)
    }

    /// Create a TA instance using the provided trait implementations.
    pub fn new_with_session_limit(
        ag_impls: &mut CryptoTraitImpl,
        storage_impl: Box<dyn KeyValueStore>,
        identity_curve: iana::EllipticCurve,
        max_sessions: usize,
    ) -> Result<Self, SkInternalError> {
        // Create a per-boot-key for AuthGraph to use.
        let aes_gcm = ag_impls.aes_gcm.box_clone();
        let rng = ag_impls.rng.box_clone();
        let per_boot_key = aes_gcm.generate_key(&mut *ag_impls.rng).map_err(|e| {
            error!("Failed to generate per-boot key: {e:?}");
            SkInternalError::UnexpectedError
        })?;

        let (identity_sign_key, mut pub_key) =
            ag_impls.ecdsa.generate_key(identity_curve).map_err(|e| {
                error!("Failed to generate {identity_curve:?} keypair: {e:?}");
                SkInternalError::UnexpectedError
            })?;
        pub_key.canonicalize_cose_key();
        let identity = Identity {
            version: IDENTITY_VERSION,
            cert_chain: CertChain {
                version: EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION,
                root_key: pub_key,
                dice_cert_chain: None,
            },
            policy: None,
        };
        let store = PolicyGatedStorage::init(storage_impl);

        Ok(Self {
            aes_gcm,
            rng,
            per_boot_key,
            identity_sign_key,
            identity,
            max_sessions,
            session_artifacts: VecDeque::new(),
            store,
        })
    }

    /// Process a single serialized request from the bootloader, returning a serialized response
    /// (even on failure).
    pub fn process_bootloader(&self, req_data: &[u8]) -> Vec<u8> {
        use bootloader as bl;
        let req = match bl::Request::from_slice(req_data) {
            Ok(req) => req,
            Err(e) => {
                error!("Failed to deserialize bootloader request: {e:?}");
                return bl::Response::Error(bl::OpCode::Unknown, bl::ErrorCode::CborFailure)
                    .into_vec();
            }
        };
        let rsp = match req {
            bl::Request::GetIdentityKey => {
                let cose_key = self.identity.cert_chain.root_key.clone().get_key();
                let cose_data = match cose_key.to_vec() {
                    Ok(data) => data,
                    Err(e) => {
                        error!("Failed to serialize COSE key: {e:?}");
                        return bl::Response::Error(
                            bl::OpCode::GetIdentityKey,
                            bl::ErrorCode::CborFailure,
                        )
                        .into_vec();
                    }
                };
                bl::Response::IdentityKey(cose_data)
            }
        };
        rsp.into_vec()
    }

    /// Process a single serialized request, returning a serialized response (even on failure).
    pub fn process(&mut self, req_data: &[u8]) -> Vec<u8> {
        let (req_code, rsp) = match PerformOpReq::from_slice(req_data) {
            Ok(req) => {
                trace!("-> TA: received request {:?}", req.code());
                (Some(req.code()), self.process_req(req))
            }
            Err(e) => {
                error!("failed to decode CBOR request: {:?}", e);
                (None, PerformOpResponse::Failure(aidl_err!(InternalError, "CBOR decode failure")))
            }
        };
        trace!("<- TA: send response for {:?} rc {:?}", req_code, rsp.err_code());
        rsp.to_vec().unwrap_or_else(|e| {
            error!("failed to encode CBOR response: {:?}", e);
            invalid_cbor_rsp_data().to_vec()
        })
    }

    /// Process a single request, returning a [`PerformOpResponse`].
    fn process_req(&mut self, req: PerformOpReq) -> PerformOpResponse {
        let code = req.code();
        let result = match req {
            PerformOpReq::SecretManagement(encrypt0) => {
                self.secret_management(&encrypt0).map(PerformOpSuccessRsp::ProtectedResponse)
            }
            PerformOpReq::DeleteIds(ids) => {
                self.delete_ids(ids).map(|_| PerformOpSuccessRsp::Empty)
            }
            PerformOpReq::DeleteAll => self.delete_all().map(|_| PerformOpSuccessRsp::Empty),
        };
        match result {
            Ok(rsp) => PerformOpResponse::Success(rsp),
            Err(err) => {
                warn!("failing {:?} request: {:?}", code, err);
                PerformOpResponse::Failure(err)
            }
        }
    }

    fn delete_ids(&mut self, ids: Vec<SecretId>) -> Result<(), ApiError> {
        for id in ids {
            self.store
                .delete(&Id(id))
                .map_err(|e| aidl_err!(InternalError, "failed to delete id: {e:?}"))?;
        }
        Ok(())
    }

    fn delete_all(&mut self) -> Result<(), ApiError> {
        warn!("deleting all Secretkeeper secrets");
        self.store.delete_all().map_err(|e| aidl_err!(InternalError, "failed to delete all: {e:?}"))
    }

    // "SSL added and removed here :-)"
    fn secret_management(&mut self, encrypt0: &[u8]) -> Result<Vec<u8>, ApiError> {
        let encrypt0 = CoseEncrypt0::from_slice(encrypt0)
            .map_err(|_e| aidl_err!(RequestMalformed, "malformed COSE_Encrypt0"))?;
        let kid: &[u8; SESSION_ID_LEN] = key_id(&encrypt0)
            .try_into()
            .map_err(|e| aidl_err!(RequestMalformed, "session key of unexpected size: {e:?}"))?;
        let peer_cert_chain = self.peer_cert_chain(kid)?;
        let req = self.decrypt_request(&encrypt0, kid)?;
        let result = self.process_inner(&req, &peer_cert_chain);

        // An inner failure still converts to a response message that gets encrypted.
        let rsp_data = match result {
            Ok(data) => data,
            Err(e) => e
                .serialize_to_packet()
                .to_vec()
                .map_err(|_e| aidl_err!(InternalError, "failed to encode err rsp"))?,
        };
        self.encrypt_response(&rsp_data, kid)
    }

    fn peer_cert_chain(&self, kid: &[u8; SESSION_ID_LEN]) -> Result<Vec<u8>, ApiError> {
        let cert_chain = self
            .session_artifacts
            .iter()
            .find_map(|info| {
                if info.session_keys.session_id == *kid {
                    Some(info.session_keys.peer_cert_chain.clone())
                } else {
                    None
                }
            })
            .ok_or_else(|| aidl_err!(UnknownKeyId, "session info not found"))?;
        Ok(cert_chain)
    }

    fn process_inner(
        &mut self,
        req: &[u8],
        peer_cert_chain: &[u8],
    ) -> Result<Vec<u8>, SecretkeeperError> {
        let req_packet = RequestPacket::from_slice(req).map_err(|e| {
            error!("Failed to get Request packet from bytes: {e:?}");
            SecretkeeperError::RequestMalformed
        })?;
        let rsp_packet =
            match req_packet.opcode().map_err(|_| SecretkeeperError::RequestMalformed)? {
                Opcode::GetVersion => Self::get_version(req_packet)?,
                Opcode::StoreSecret => self.store_secret(req_packet, peer_cert_chain)?,
                Opcode::GetSecret => self.get_secret(req_packet, peer_cert_chain)?,
                _ => panic!("Unknown operation.."),
            };
        rsp_packet.to_vec().map_err(|_| SecretkeeperError::UnexpectedServerError)
    }

    fn get_version(req: RequestPacket) -> Result<ResponsePacket, SecretkeeperError> {
        // Deserialization really just verifies the structural integrity of the request such
        // as args being empty.
        let _req = GetVersionRequest::deserialize_from_packet(req)
            .map_err(|_| SecretkeeperError::RequestMalformed)?;
        let rsp = GetVersionResponse { version: CURRENT_VERSION };
        Ok(rsp.serialize_to_packet())
    }

    fn store_secret(
        &mut self,
        request: RequestPacket,
        peer_cert_chain: &[u8],
    ) -> Result<ResponsePacket, SecretkeeperError> {
        let request = StoreSecretRequest::deserialize_from_packet(request)
            .map_err(|_| SecretkeeperError::RequestMalformed)?;
        self.store.store(request.id, request.secret, request.sealing_policy, peer_cert_chain)?;
        let response = StoreSecretResponse {};
        Ok(response.serialize_to_packet())
    }

    fn get_secret(
        &mut self,
        request: RequestPacket,
        peer_cert_chain: &[u8],
    ) -> Result<ResponsePacket, SecretkeeperError> {
        let request = GetSecretRequest::deserialize_from_packet(request)
            .map_err(|_| SecretkeeperError::RequestMalformed)?;
        let secret =
            self.store.get(&request.id, peer_cert_chain, request.updated_sealing_policy)?;
        let response = GetSecretResponse { secret };
        Ok(response.serialize_to_packet())
    }

    fn keys_for_session(
        &mut self,
        session_id: &[u8; SESSION_ID_LEN],
    ) -> Option<&mut SessionArtifacts> {
        self.session_artifacts.iter_mut().find(|info| info.session_keys.session_id == *session_id)
    }

    fn decrypt_request(
        &mut self,
        encrypt0: &CoseEncrypt0,
        kid: &[u8; SESSION_ID_LEN],
    ) -> Result<Vec<u8>, ApiError> {
        let session_artifacts = self
            .keys_for_session(kid)
            .ok_or_else(|| aidl_err!(UnknownKeyId, "session info not found"))?;

        let seq_num_incoming =
            session_artifacts.seq_num_incoming.get_then_increment().map_err(|e| {
                aidl_err!(InternalError, "Couldn't get the expected request seq_num: {e:?}")
            })?;
        let recv_key: &AesKey = &session_artifacts.session_keys.recv_key.clone();

        cipher::decrypt_message(self.aes_gcm.as_ref(), recv_key, encrypt0, &seq_num_incoming)
    }

    fn encrypt_response(
        &mut self,
        rsp: &[u8],
        kid: &[u8; SESSION_ID_LEN],
    ) -> Result<Vec<u8>, ApiError> {
        let session_artifacts = self
            .keys_for_session(kid)
            .ok_or_else(|| aidl_err!(UnknownKeyId, "session info not found"))?;
        let seq_num_outgoing = session_artifacts
            .seq_num_outgoing
            .get_then_increment()
            .map_err(|e| aidl_err!(InternalError, "Couldn't get a seq number: {e:?}"))?;
        let send_key: &AesKey = &session_artifacts.session_keys.send_key.clone();
        cipher::encrypt_message(&*self.aes_gcm, &*self.rng, send_key, kid, rsp, &seq_num_outgoing)
    }
}

impl Device for SecretkeeperTa {
    fn get_or_create_per_boot_key(&self, _: &dyn AesGcm, _: &mut dyn Rng) -> Result<AesKey, Error> {
        self.get_per_boot_key()
    }
    fn get_per_boot_key(&self) -> Result<AesKey, Error> {
        Ok(self.per_boot_key.clone())
    }
    fn get_identity(&self) -> Result<(Option<EcSignKey>, Identity), Error> {
        Ok((Some(self.identity_sign_key.clone()), self.identity.clone()))
    }
    fn get_cose_sign_algorithm(&self) -> Result<iana::Algorithm, Error> {
        match &self.identity_sign_key {
            EcSignKey::Ed25519(_) => Ok(iana::Algorithm::EdDSA),
            EcSignKey::P256(_) => Ok(iana::Algorithm::ES256),
            EcSignKey::P384(_) => Ok(iana::Algorithm::ES384),
        }
    }

    fn sign_data(&self, _ecdsa: &dyn EcDsa, _data: &[u8]) -> Result<Vec<u8>, Error> {
        // `get_identity()` returns a key, so signing should be handled elsewhere.
        Err(ag_err!(Unimplemented, "unexpected signing request"))
    }

    fn evaluate_identity(
        &self,
        _curr: &Identity,
        _prev: &Identity,
    ) -> Result<IdentityVerificationDecision, Error> {
        Err(ag_err!(Unimplemented, "not yet required"))
    }

    fn record_shared_sessions(
        &mut self,
        peer_identity: &Identity,
        session_id: &[u8; 32],
        shared_keys: &[Vec<u8>; 2],
        _sha256: &dyn Sha256,
    ) -> Result<(), Error> {
        if self.session_artifacts.len() >= self.max_sessions {
            warn!("Dropping oldest session key");
            self.session_artifacts.pop_front();
        }
        let send_key =
            authgraph_core::arc::decipher_arc(&self.per_boot_key, &shared_keys[0], &*self.aes_gcm)?;
        let recv_key =
            authgraph_core::arc::decipher_arc(&self.per_boot_key, &shared_keys[1], &*self.aes_gcm)?;
        let peer_cert_chain = peer_identity
            .cert_chain
            .clone()
            .to_vec()
            .map_err(|e| ag_err!(InternalError, "Failed to encode peer's cert chain: {e:?}"))?;

        // We assume that the session ID is unique and not already present in `session_keys`.
        self.session_artifacts.push_back(SessionArtifacts {
            session_keys: AgSessionKeys {
                peer_cert_chain,
                session_id: *session_id,
                send_key: send_key.payload.try_into()?,
                recv_key: recv_key.payload.try_into()?,
            },
            seq_num_outgoing: SeqNum::new(),
            seq_num_incoming: SeqNum::new(),
        });
        Ok(())
    }

    fn validate_shared_sessions(
        &self,
        _peer_identity: &Identity,
        _session_id: &[u8; 32],
        _shared_keys: &[Vec<u8>],
        _sha256: &dyn Sha256,
    ) -> Result<(), Error> {
        // TODO: implement
        Ok(())
    }
}

fn key_id(encrypted_packet: &CoseEncrypt0) -> &[u8] {
    &encrypted_packet.protected.header.key_id
}

// In addition to `session_keys` (received from authgraph key exchange protocol), Secretkeeper
// session maintains `sequence_numbers`. These change within session (for each message).
// Avoid cloning!
struct SessionArtifacts {
    session_keys: AgSessionKeys,
    // Sequence number to be used in next outgoing message encryption.
    seq_num_outgoing: SeqNum,
    // Sequence number to be used for decrypting the next incoming message.
    seq_num_incoming: SeqNum,
}

struct AgSessionKeys {
    peer_cert_chain: Vec<u8>,
    session_id: [u8; 32],
    send_key: AesKey,
    recv_key: AesKey,
}

/// Hand-encoded [`PerformOpResponse`] data for [`AidlErrorCode::InternalError`].
/// Does not perform CBOR serialization (and so is suitable for error reporting if/when
/// CBOR serialization fails).
fn invalid_cbor_rsp_data() -> [u8; 3] {
    [
        0x82, // 2-arr
        0x02, // int, value 2
        0x60, // 0-tstr
    ]
}

#[cfg(test)]
mod tests {
    use super::*;
    use secretkeeper_comm::wire::{AidlErrorCode, ApiError, PerformOpResponse};

    #[test]
    fn test_invalid_data() {
        // Cross-check that the hand-encoded invalid CBOR data matches an auto-encoded equivalent.
        let rsp = PerformOpResponse::Failure(ApiError {
            err_code: AidlErrorCode::InternalError,
            msg: alloc::string::String::new(),
        });
        let rsp_data = rsp.to_vec().unwrap();
        assert_eq!(rsp_data, invalid_cbor_rsp_data());
    }
}
