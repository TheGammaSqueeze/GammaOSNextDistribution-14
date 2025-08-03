// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

//! Authenticated Key Exchange (AKE) related operations. AKE is executed between two parties P1
//! (source) and P2 (sink).
use crate::{
    ag_err, arc,
    error::Error,
    key::{
        check_cose_key_params, AesKey, EcExchangeKey, EcExchangeKeyPriv, EcExchangeKeyPub,
        EcSignKey, EcVerifyKey, EcdhSecret, HmacKey, Identity, Key, Nonce16, PseudoRandKey,
        SaltInput, SessionIdInput, AES_256_KEY_LEN, SHA_256_LEN,
    },
    traits::{CryptoTraitImpl, Device, EcDh, Hkdf, Sha256},
    try_to_vec, FallibleAllocExt,
};
use alloc::collections::VecDeque;
use alloc::rc::Rc;
use alloc::vec::Vec;
use authgraph_wire as wire;
use core::cell::RefCell;
use core::mem::take;
use coset::{
    cbor::value::Value, iana, AsCborValue, CborSerializable, CoseKey, CoseSign1, CoseSign1Builder,
    HeaderBuilder, Label,
};
use log::info;
use wire::{ErrorCode, SESSION_ID_LEN};

pub use wire::{KeInitResult, SessionInfo, SessionInitiationInfo};

/// Context for deriving the first shared encryption key
pub const CONTEXT_KE_ENCRYPTION_KEY_1: &[u8] = b"KE_ENCRYPTION_KEY_SOURCE_TO_SINK";
/// Context for deriving the second shared encryption key
pub const CONTEXT_KE_ENCRYPTION_KEY_2: &[u8] = b"KE_ENCRYPTION_KEY_SINK_TO_SOURCE";
/// Context for deriving the shared HMAC key
pub const CONTEXT_KE_HMAC_KEY: &[u8] = b"KE_HMAC_KEY";
/// Default limit for opened sessions for either source or sink operationss
pub const MAX_OPENED_SESSIONS: usize = 16;

/// Enum defining the roles played by the two AuthGraph participants
#[derive(Clone, Copy)]
pub enum Role {
    /// AuthGraph source
    Source = 1,
    /// AuthGraph sink
    Sink = 2,
}

/// Encapsulation of an AuthGraph Source
pub struct AuthGraphParticipant {
    crypto: CryptoTraitImpl,
    device: Rc<RefCell<dyn Device>>,
    opened_ke_sessions: OpenedSessions,
    opened_shared_sessions: OpenedSessions,
}

// Session identifier is the computation of HMAC over the concatenation of the nonces of source
// and sink with the HMAC key derived from the shared secret computed via the key exchange protocol.
type SessionIdentifier = [u8; SESSION_ID_LEN];

// Session digest is computed as the SHA-256 digest of the concatenation of the peer identity and
// the session identifier
type SessionDigest = [u8; SHA_256_LEN];

/// Data structure to hold the information about opened sessions during key exchange
struct OpenedSessions {
    capacity: usize,
    sessions: VecDeque<SessionDigest>,
}

impl OpenedSessions {
    fn new(max_num_sessions: usize) -> Result<Self, Error> {
        let mut sessions = VecDeque::new();
        sessions.try_reserve(max_num_sessions)?;
        Ok(OpenedSessions { capacity: max_num_sessions, sessions })
    }

    fn add(&mut self, opened_session: SessionDigest) {
        if self.sessions.len() == self.capacity {
            info!("Max number of opened sessions reached. Removing the oldest session.");
            self.sessions.pop_front();
        }
        self.sessions.push_back(opened_session);
    }

    // Remove a tracked session; return `InternalError`` if the provided session is not tracked.
    fn remove(&mut self, opened_session: SessionDigest) -> Result<(), Error> {
        if self.sessions.contains(&opened_session) {
            self.sessions.retain(|&session| session != opened_session);
            Ok(())
        } else {
            Err(ag_err!(InternalError, "session not found"))
        }
    }
}

impl AuthGraphParticipant {
    /// Creates an instance of AuthGraphParticipant
    pub fn new(
        crypto: CryptoTraitImpl,
        device: Rc<RefCell<dyn Device>>,
        max_num_sessions: usize,
    ) -> Result<Self, Error> {
        Ok(AuthGraphParticipant {
            crypto,
            device,
            opened_ke_sessions: OpenedSessions::new(max_num_sessions)?,
            opened_shared_sessions: OpenedSessions::new(max_num_sessions)?,
        })
    }

    /// First step in the key exchange protocol.
    pub fn create(&mut self) -> Result<SessionInitiationInfo, Error> {
        // Create an EC key for ECDH
        let EcExchangeKey { pub_key: pub_key_for_ecdh, priv_key: mut priv_key_for_ecdh } =
            self.crypto.ecdh.generate_key()?;
        // Create a nonce for key agreement
        let nonce_for_ke = Nonce16::new(&*self.crypto.rng);

        let pbk = self
            .device
            .borrow()
            .get_or_create_per_boot_key(&*self.crypto.aes_gcm, &mut *self.crypto.rng)?;
        let mut protected_headers = Vec::<(Label, Value)>::new();
        protected_headers
            .try_push((arc::KE_NONCE, Value::Bytes(nonce_for_ke.0.clone().to_vec())))?;
        let priv_key_arc = arc::create_arc(
            &pbk,
            arc::ArcContent {
                payload: arc::ArcPayload(take(&mut priv_key_for_ecdh.0)),
                protected_headers,
                ..Default::default()
            },
            &*self.crypto.aes_gcm,
            &mut *self.crypto.rng,
        )?;
        let (_sign_key, identity) = self.device.borrow().get_identity()?;
        // Validate the identity returned from the trait implementation during the first time it is
        // retrieved from the AuthGraphParticipant.
        identity.validate(&*self.crypto.ecdsa)?;
        let ke_key =
            Key { pub_key: Some(pub_key_for_ecdh.0.to_vec()?), arc_from_pbk: Some(priv_key_arc) };
        let ke_digest = compute_ke_key_digest(&ke_key, &*self.crypto.sha256)?;
        self.opened_ke_sessions.add(ke_digest);
        Ok(SessionInitiationInfo {
            ke_key,
            identity: identity.to_vec()?,
            nonce: nonce_for_ke.0.to_vec(),
            version: self.get_version(),
        })
    }

    /// Second step in the key exchange protocol.
    pub fn init(
        &mut self,
        peer_key: &[u8],
        peer_id: &[u8],
        peer_nonce: &[u8],
        peer_version: i32,
    ) -> Result<KeInitResult, Error> {
        if self.device.borrow().get_negotiated_version(peer_version) > peer_version {
            return Err(ag_err!(InternalError, "protocol version is greater than peer's version"));
        }
        let key_for_ecdh = self.crypto.ecdh.generate_key()?;
        let own_nonce = Nonce16::new(&*self.crypto.rng);

        let (peer_pub_key, peer_identity, peer_nonce) =
            decode_peer_info(peer_key, peer_id, peer_nonce)?;

        let ecdh_secret =
            compute_shared_secret(&*self.crypto.ecdh, &key_for_ecdh.priv_key, &peer_pub_key)?;
        let (sign_key, own_identity) = self.device.borrow().get_identity()?;
        // Validate the identity returned from the trait implementation during the first time it is
        // retrieved from the AuthGraphParticipant.
        own_identity.validate(&*self.crypto.ecdsa)?;
        let salt_input = SaltInput {
            source_version: peer_version,
            sink_ke_pub_key: key_for_ecdh.pub_key.clone(),
            source_ke_pub_key: peer_pub_key,
            sink_ke_nonce: own_nonce.clone(),
            source_ke_nonce: peer_nonce.clone(),
            sink_cert_chain: own_identity.cert_chain.clone(),
            source_cert_chain: peer_identity.cert_chain.clone(),
        };

        let salt = compute_salt(salt_input, &*self.crypto.sha256)?;
        let (in_encrypt_key, out_encrypt_key, hmac_key) =
            derive_shared_keys(&ecdh_secret, &salt, Role::Sink, &*self.crypto.hkdf)?;

        let (session_id, signature) = self.compute_sign_session_id(
            SessionIdInput { sink_ke_nonce: own_nonce.clone(), source_ke_nonce: peer_nonce },
            HmacKey(hmac_key.0),
            sign_key,
        )?;

        // create two arcs encrypting the two shared encrypting keys with the per-boot-key
        let shared_keys = self.create_shared_key_arcs(
            own_identity.clone(),
            peer_identity.clone(),
            AesKey(in_encrypt_key.0),
            AesKey(out_encrypt_key.0),
            &session_id,
            arc::AuthenticationCompleted(false),
        )?;
        let session_digest =
            compute_session_digest(&peer_identity, &session_id, &*self.crypto.sha256)?;
        self.opened_shared_sessions.add(session_digest);
        Ok(KeInitResult {
            session_init_info: SessionInitiationInfo {
                ke_key: Key { pub_key: Some(key_for_ecdh.pub_key.0.to_vec()?), arc_from_pbk: None },
                identity: own_identity.to_vec()?,
                nonce: own_nonce.0.to_vec(),
                version: self.device.borrow().get_negotiated_version(peer_version),
            },
            session_info: SessionInfo {
                shared_keys,
                session_id: session_id.to_vec(),
                session_id_signature: signature,
            },
        })
    }

    /// Third step in the key exchange protocol.
    pub fn finish(
        &mut self,
        peer_key: &[u8],
        peer_id: &[u8],
        peer_sig: &[u8],
        peer_nonce: &[u8],
        peer_version: i32,
        own_ke_key: Key,
    ) -> Result<SessionInfo, Error> {
        let ke_digest = compute_ke_key_digest(&own_ke_key, &*self.crypto.sha256)?;
        self.opened_ke_sessions
            .remove(ke_digest)
            .map_err(|_| ag_err!(InvalidKeKey, "finish is called on invalid session"))?;
        if self.get_version() < peer_version {
            return Err(ag_err!(
                IncompatibleProtocolVersion,
                "peer's protocol version is incompatible"
            ));
        }
        let pbk = self.per_boot_key()?;
        let mut own_ke_key_arc_content = arc::decipher_arc(
            &pbk,
            &own_ke_key
                .arc_from_pbk
                .ok_or(ag_err!(InvalidPrivKeyArcInKey, "missing priv key arc"))?,
            &*self.crypto.aes_gcm,
        )
        .map_err(|e| ag_err!(InvalidPrivKeyArcInKey, "failed to decrypt arc: {e:?}"))?;

        let own_ke_nonce = extract_nonce(&own_ke_key_arc_content.protected_headers)?;

        let (peer_pub_key, peer_identity, peer_nonce) =
            decode_peer_info(peer_key, peer_id, peer_nonce)?;

        let ecdh_secret = compute_shared_secret(
            &*self.crypto.ecdh,
            &EcExchangeKeyPriv(take(&mut own_ke_key_arc_content.payload.0)),
            &peer_pub_key,
        )?;

        let (sign_key, own_identity) = self.device.borrow().get_identity()?;
        let own_ke_pub_key = own_ke_key
            .pub_key
            .ok_or(ag_err!(InvalidPubKeyInKey, "own public key is missing for key agreement"))?;
        let own_ke_pub_key =
            EcExchangeKeyPub(CoseKey::from_slice(&own_ke_pub_key).map_err(|e| {
                ag_err!(InvalidPubKeyInKey, "invalid own key for key agreement: {:?}", e)
            })?);

        let salt_input = SaltInput {
            source_version: self.get_version(),
            sink_ke_pub_key: peer_pub_key,
            source_ke_pub_key: own_ke_pub_key,
            sink_ke_nonce: peer_nonce.clone(),
            source_ke_nonce: own_ke_nonce.clone(),
            sink_cert_chain: peer_identity.cert_chain.clone(),
            source_cert_chain: own_identity.cert_chain.clone(),
        };

        let salt = compute_salt(salt_input, &*self.crypto.sha256)?;

        let (in_encrypt_key, out_encrypt_key, hmac_key) =
            derive_shared_keys(&ecdh_secret, &salt, Role::Source, &*self.crypto.hkdf)?;

        let (session_id, signature) = self.compute_sign_session_id(
            SessionIdInput { sink_ke_nonce: peer_nonce, source_ke_nonce: own_ke_nonce },
            HmacKey(hmac_key.0),
            sign_key,
        )?;

        // Validate peer's identity and the peer's signature on the session id
        let verify_key =
            self.device.borrow().validate_peer_identity(&peer_identity, &*self.crypto.ecdsa)?;
        self.verify_signature_on_session_id(&verify_key, &session_id, peer_sig)?;

        // create two arcs encrypting the two shared encrypting keys with the per-boot-key
        let shared_keys = self.create_shared_key_arcs(
            peer_identity.clone(),
            own_identity,
            AesKey(in_encrypt_key.0),
            AesKey(out_encrypt_key.0),
            &session_id,
            arc::AuthenticationCompleted(true),
        )?;

        self.device.borrow_mut().record_shared_sessions(
            &peer_identity,
            &session_id,
            &shared_keys,
            &*self.crypto.sha256,
        )?;
        Ok(SessionInfo {
            shared_keys,
            session_id: session_id.to_vec(),
            session_id_signature: signature,
        })
    }

    /// Fourth step in the key exchange protocol.
    pub fn authentication_complete(
        &mut self,
        peer_sig: &[u8],
        shared_keys: [Vec<u8>; 2],
    ) -> Result<[Vec<u8>; 2], Error> {
        let in_key_arc = &shared_keys[0];
        let out_key_arc = &shared_keys[1];

        let pbk = self.per_boot_key()?;

        let in_key_arc_content = arc::decipher_arc(&pbk, in_key_arc, &*self.crypto.aes_gcm)
            .map_err(|e| {
                ag_err!(InvalidSharedKeyArcs, "failed to decrypt inbound shared key: {e:?}")
            })?;
        let (in_session_id, in_source_identity, _) = process_shared_key_arc_headers(
            &in_key_arc_content.protected_headers,
            &arc::AuthenticationCompleted(false),
        )?;

        let out_key_arc_content = arc::decipher_arc(&pbk, out_key_arc, &*self.crypto.aes_gcm)
            .map_err(|e| {
                ag_err!(InvalidSharedKeyArcs, "failed to decrypt inbound shared key: {e:?}")
            })?;
        let (out_session_id, out_source_identity, _) = process_shared_key_arc_headers(
            &out_key_arc_content.protected_headers,
            &arc::AuthenticationCompleted(false),
        )?;

        if in_session_id != out_session_id {
            return Err(ag_err!(InvalidSharedKeyArcs, "session id mismatch"));
        }

        if in_source_identity != out_source_identity {
            return Err(ag_err!(InvalidSharedKeyArcs, "peer identity mismatch"));
        }
        // Remove the corresponding entry from opened shared sessions which was inserted in `init`
        let session_digest =
            compute_session_digest(&in_source_identity, &in_session_id, &*self.crypto.sha256)?;
        self.opened_shared_sessions.remove(session_digest).map_err(|_| {
            ag_err!(InvalidSharedKeyArcs, "authentication_complete is called on invalid session")
        })?;

        // Validate peer's identity and the peer's signature on the session id
        let verification_key = self
            .device
            .borrow()
            .validate_peer_identity(&in_source_identity, &*self.crypto.ecdsa)?;
        self.verify_signature_on_session_id(&verification_key, &in_session_id, peer_sig)?;

        let updated_shared_key_arcs =
            self.auth_complete_shared_key_arcs(in_key_arc_content, out_key_arc_content)?;

        self.device.borrow_mut().record_shared_sessions(
            &in_source_identity,
            &in_session_id,
            &updated_shared_key_arcs,
            &*self.crypto.sha256,
        )?;
        Ok(updated_shared_key_arcs)
    }

    fn create_shared_key_arcs(
        &mut self,
        sink_id: Identity,
        source_id: Identity,
        in_encrypt_key: AesKey,
        out_encrypt_key: AesKey,
        session_id: &[u8],
        auth_complete: arc::AuthenticationCompleted,
    ) -> Result<[Vec<u8>; 2], Error> {
        let pbk = match auth_complete.0 {
            false => self
                .device
                .borrow()
                .get_or_create_per_boot_key(&*self.crypto.aes_gcm, &mut *self.crypto.rng)?,
            true => self.per_boot_key()?,
        };

        // auth complete protected header
        let auth_complete_hdr = (arc::AUTHENTICATION_COMPLETE, Value::Bool(auth_complete.0));
        // session id protected header
        let session_id_hdr = (arc::SESSION_ID, Value::Bytes(try_to_vec(session_id)?));
        // direction of encryption
        let in_direction_hdr =
            (arc::DIRECTION, Value::Integer((arc::DirectionOfEncryption::In as i32).into()));
        // permissions header
        let permissions_encoded = arc::Permissions {
            source_id: Some(source_id),
            sink_id: Some(sink_id),
            ..Default::default()
        }
        .to_cbor_value()?;
        let permission_hdr = (arc::PERMISSIONS, permissions_encoded);

        let mut in_protected_headers = Vec::<(Label, Value)>::new();
        in_protected_headers.try_reserve(4)?;
        in_protected_headers.push(auth_complete_hdr.clone());
        in_protected_headers.push(session_id_hdr.clone());
        in_protected_headers.push(permission_hdr.clone());
        in_protected_headers.push(in_direction_hdr);

        let in_encrypt_key_arc = arc::create_arc(
            &pbk,
            arc::ArcContent {
                payload: arc::ArcPayload(in_encrypt_key.0.to_vec()),
                protected_headers: in_protected_headers,
                ..Default::default()
            },
            &*self.crypto.aes_gcm,
            &mut *self.crypto.rng,
        )?;

        // direction of encryption
        let out_direction_hdr =
            (arc::DIRECTION, Value::Integer((arc::DirectionOfEncryption::Out as i32).into()));
        let mut out_protected_headers = Vec::<(Label, Value)>::new();
        out_protected_headers.try_reserve(4)?;
        out_protected_headers.push(auth_complete_hdr);
        out_protected_headers.push(session_id_hdr);
        out_protected_headers.push(permission_hdr);
        out_protected_headers.push(out_direction_hdr);

        let out_encrypt_key_arc = arc::create_arc(
            &pbk,
            arc::ArcContent {
                payload: arc::ArcPayload(out_encrypt_key.0.to_vec()),
                protected_headers: out_protected_headers,
                ..Default::default()
            },
            &*self.crypto.aes_gcm,
            &mut *self.crypto.rng,
        )?;
        Ok([in_encrypt_key_arc, out_encrypt_key_arc])
    }

    /// Update the `authentication_complete` protected header in both arcs to be true
    pub fn auth_complete_shared_key_arcs(
        &mut self,
        mut in_arc_content: arc::ArcContent,
        mut out_arc_content: arc::ArcContent,
    ) -> Result<[Vec<u8>; 2], Error> {
        in_arc_content.protected_headers.retain(|v| v.0 != arc::AUTHENTICATION_COMPLETE);
        out_arc_content.protected_headers.retain(|v| v.0 != arc::AUTHENTICATION_COMPLETE);

        let auth_complete_hdr = (arc::AUTHENTICATION_COMPLETE, Value::Bool(true));
        in_arc_content.protected_headers.try_push(auth_complete_hdr.clone())?;
        out_arc_content.protected_headers.try_push(auth_complete_hdr)?;

        let pbk = self.per_boot_key()?;
        let in_encrypt_key_arc =
            arc::create_arc(&pbk, in_arc_content, &*self.crypto.aes_gcm, &mut *self.crypto.rng)?;
        let out_encrypt_key_arc =
            arc::create_arc(&pbk, out_arc_content, &*self.crypto.aes_gcm, &mut *self.crypto.rng)?;
        Ok([in_encrypt_key_arc, out_encrypt_key_arc])
    }

    fn compute_sign_session_id(
        &self,
        session_id_input: SessionIdInput,
        hmac_key: HmacKey,
        sign_key: Option<EcSignKey>,
    ) -> Result<(SessionIdentifier, Vec<u8>), Error> {
        let session_id = self.crypto.hmac.compute_hmac(&hmac_key, &session_id_input.to_vec()?)?;

        // Construct `CoseSign1`
        let protected =
            HeaderBuilder::new().algorithm(self.device.borrow().get_cose_sign_algorithm()?).build();
        let cose_sign1 = CoseSign1Builder::new()
            .protected(protected)
            .try_create_detached_signature(&session_id, &[], |input| match sign_key {
                Some(key) => self.crypto.ecdsa.sign(&key, input),
                None => self.device.borrow().sign_data(&*self.crypto.ecdsa, input),
            })?
            .build();

        Ok((
            session_id
                .as_slice()
                .try_into()
                .map_err(|e| ag_err!(InternalError, "invalid session id: {:?}", e))?,
            cose_sign1.to_vec()?,
        ))
    }

    fn per_boot_key(&self) -> Result<AesKey, Error> {
        self.device.borrow().get_per_boot_key()
    }

    /// Returns the latest supported version
    pub fn get_version(&self) -> i32 {
        self.device.borrow().get_version()
    }

    /// Validate and extract signature verification key from the peer's identity
    pub fn peer_verification_key_from_identity(
        &self,
        identity: &[u8],
    ) -> Result<EcVerifyKey, Error> {
        let identity = Identity::from_slice(identity)?;
        self.device.borrow().validate_peer_identity(&identity, &*self.crypto.ecdsa)
    }

    /// Verify the signature in a CBOR-encoded `COSE_Sign1` with detached payload `session_id`.
    pub fn verify_signature_on_session_id(
        &self,
        verify_key: &EcVerifyKey,
        session_id: &[u8],
        signature: &[u8],
    ) -> Result<(), Error> {
        let cose_sign1 = CoseSign1::from_slice(signature)?;
        verify_key.validate_cose_key_params()?;
        cose_sign1.verify_detached_signature(session_id, &[], |signature, data| {
            self.crypto.ecdsa.verify_signature(verify_key, data, signature)
        })
    }

    /// An external entry point for retrieving the decrypted shared keys, given the arcs containing
    /// the shared keys. The given arcs are validated against the records created at the end of each
    /// key exchange. The number of arcs can be either one or two, depending on whether a particular
    /// application protocol involves one-way (i.e. either outgoing or incoming messages) or
    /// two-way communication (i.e. both outgoing and incoming messages).
    pub fn decipher_shared_keys_from_arcs(&self, arcs: &[Vec<u8>]) -> Result<Vec<AesKey>, Error> {
        if arcs.len() > 2 || arcs.is_empty() {
            return Err(ag_err!(
                InvalidSharedKeyArcs,
                "expected one or two arcs, provided {} arcs",
                arcs.len()
            ));
        }
        let arc_contents = self.decrypt_and_validate_shared_key_arcs(arcs)?;
        let mut shared_keys = Vec::<AesKey>::new();
        for arc_content in arc_contents {
            let key = AesKey::try_from(arc_content.payload)?;
            if key.0 == [0; AES_256_KEY_LEN] {
                return Err(ag_err!(InvalidSharedKeyArcs, "payload key is all zero",));
            }
            shared_keys.try_push(key)?;
        }
        Ok(shared_keys)
    }
    /// Decrypt the provided arcs, validate and return the arc contents.
    fn decrypt_and_validate_shared_key_arcs(
        &self,
        arcs: &[Vec<u8>],
    ) -> Result<Vec<arc::ArcContent>, Error> {
        if arcs.is_empty() {
            return Err(ag_err!(InvalidSharedKeyArcs, "array of arcs is empty"));
        }
        let mut session_identifier: Option<[u8; SESSION_ID_LEN]> = None;
        let mut source_identity: Option<Identity> = None;
        let mut sink_identity: Option<Identity> = None;
        let mut arc_contents = Vec::<arc::ArcContent>::new();
        for arc in arcs {
            let pbk = self.per_boot_key()?;
            let arc_content = arc::decipher_arc(&pbk, arc, &*self.crypto.aes_gcm).map_err(|e| {
                ag_err!(InvalidSharedKeyArcs, "failed to decrypt shared key: {e:?}")
            })?;
            let (session_id, source_id, sink_id) = process_shared_key_arc_headers(
                &arc_content.protected_headers,
                &arc::AuthenticationCompleted(true),
            )?;
            arc_contents.try_push(arc_content)?;
            match &session_identifier {
                Some(id) if *id == session_id => {}
                Some(_) => {
                    return Err(ag_err!(InvalidSharedKeyArcs, "session id mismatch"));
                }
                None => {
                    session_identifier = Some(session_id);
                }
            }
            match &source_identity {
                Some(identity) if *identity == source_id => {}
                Some(_) => {
                    return Err(ag_err!(InvalidSharedKeyArcs, "source identity mismatch"));
                }
                None => {
                    source_identity = Some(source_id);
                }
            }
            match &sink_identity {
                Some(identity) if *identity == sink_id => {}
                Some(_) => {
                    return Err(ag_err!(InvalidSharedKeyArcs, "sink identity mismatch"));
                }
                None => {
                    sink_identity = Some(sink_id);
                }
            }
        }
        // It is safe to unwrap because we ensure that there is at least one arc provided in the
        // method call
        let session_identifier = session_identifier.unwrap();
        let source_identity = source_identity.unwrap();
        let sink_identity = sink_identity.unwrap();
        let (_, self_identity) = self.device.borrow().get_identity()?;
        if self_identity == source_identity {
            self.device.borrow().validate_shared_sessions(
                &sink_identity,
                &session_identifier,
                arcs,
                &*self.crypto.sha256,
            )?;
        } else if self_identity == sink_identity {
            self.device.borrow().validate_shared_sessions(
                &source_identity,
                &session_identifier,
                arcs,
                &*self.crypto.sha256,
            )?;
        } else {
            // Note: it is not possible to reach this branch because if the arc is decrypted by the
            // per-boot key, the correct self identity should have been included in the arc.
            return Err(ag_err!(InvalidSharedKeyArcs, "self identity is not included in the arc."));
        }
        Ok(arc_contents)
    }
}

fn compute_ke_key_digest(ke_key: &Key, sha256: &dyn Sha256) -> Result<[u8; SHA_256_LEN], Error> {
    let mut key_input = Vec::new();
    key_input.try_extend_from_slice(
        ke_key.pub_key.as_ref().ok_or_else(|| ag_err!(InvalidPubKeyInKey, "missing public key"))?,
    )?;
    key_input.try_extend_from_slice(
        ke_key
            .arc_from_pbk
            .as_ref()
            .ok_or_else(|| ag_err!(InvalidPrivKeyArcInKey, "missing private key arc"))?,
    )?;
    sha256.compute_sha256(&key_input)
}

fn compute_session_digest(
    peer_identity: &Identity,
    session_id: &[u8; SESSION_ID_LEN],
    sha256: &dyn Sha256,
) -> Result<SessionDigest, Error> {
    let mut session_info = Vec::new();
    session_info.try_extend_from_slice(&peer_identity.clone().to_vec()?)?;
    session_info.try_extend_from_slice(session_id)?;
    sha256.compute_sha256(&session_info)
}

/// A helper function to process the headers of the shared key arcs, verify the given authentication
/// completed status, extract and return the session id, source identity and sink identity.
pub fn process_shared_key_arc_headers(
    protected_headers: &[(Label, Value)],
    expected_status: &arc::AuthenticationCompleted,
) -> Result<(SessionIdentifier, Identity, Identity), Error> {
    let (_, val) = protected_headers
        .iter()
        .find(|(l, _v)| *l == arc::SESSION_ID)
        .ok_or_else(|| ag_err!(InvalidSharedKeyArcs, "session id is missing"))?;
    let session_id = match val {
        Value::Bytes(b) => b.clone(),
        _ => return Err(ag_err!(InvalidSharedKeyArcs, "invalid encoding of session id")),
    };
    let session_id: SessionIdentifier = session_id
        .try_into()
        .map_err(|e| ag_err!(InvalidSharedKeyArcs, "invalid session id: {:?}", e))?;

    let (_, val) = protected_headers
        .iter()
        .find(|(l, _v)| *l == arc::PERMISSIONS)
        .ok_or_else(|| ag_err!(InvalidSharedKeyArcs, "permissions are missing"))?;
    let permissions = arc::Permissions::from_cbor_value(val.clone())?;
    let source_identity = permissions.source_id.ok_or_else(|| {
        ag_err!(InvalidSharedKeyArcs, "source identity is missing in the permissions")
    })?;
    let sink_identity = permissions.sink_id.ok_or_else(|| {
        ag_err!(InvalidSharedKeyArcs, "sink identity is missing in the permissions")
    })?;

    let (_, val) = protected_headers
        .iter()
        .find(|(l, _v)| *l == arc::AUTHENTICATION_COMPLETE)
        .ok_or_else(|| ag_err!(InvalidSharedKeyArcs, "authentication complete is missing"))?;
    let status = match val {
        Value::Bool(b) => *b,
        _ => {
            return Err(ag_err!(
                InvalidSharedKeyArcs,
                "invalid encoding of authentication complete"
            ))
        }
    };
    if status != expected_status.0 {
        return Err(ag_err!(InvalidSharedKeyArcs, "invalid authentication complete status"));
    }
    Ok((session_id, source_identity, sink_identity))
}

fn decode_peer_info(
    pub_key: &[u8],
    identity: &[u8],
    nonce: &[u8],
) -> Result<(EcExchangeKeyPub, Identity, Nonce16), Error> {
    let pub_key =
        EcExchangeKeyPub(CoseKey::from_slice(pub_key).map_err(|e| {
            ag_err!(InvalidPeerKeKey, "invalid peer key for key agreement: {:?}", e)
        })?);
    let identity = Identity::from_slice(identity)
        .map_err(|e| ag_err!(InvalidIdentity, "invalid peer identity: {:?}", e))?;
    let nonce = Nonce16(
        nonce.try_into().map_err(|e| ag_err!(InvalidPeerNonce, "invalid nonce size: {:?}", e))?,
    );
    Ok((pub_key, identity, nonce))
}

fn compute_shared_secret(
    ecdh: &dyn EcDh,
    own_key: &EcExchangeKeyPriv,
    peer_key: &EcExchangeKeyPub,
) -> Result<EcdhSecret, Error> {
    check_cose_key_params(
        &peer_key.0,
        iana::KeyType::EC2,
        iana::Algorithm::ECDH_ES_HKDF_256, // ECDH
        iana::EllipticCurve::P_256,
        ErrorCode::InvalidPeerKeKey,
    )?;
    ecdh.compute_shared_secret(own_key, peer_key)
}

fn compute_salt(salt_input: SaltInput, sha_256: &dyn Sha256) -> Result<[u8; SHA_256_LEN], Error> {
    sha_256.compute_sha256(&salt_input.to_vec()?)
}

fn derive_shared_keys(
    ecdh_secret: &EcdhSecret,
    salt: &[u8; SHA_256_LEN],
    role: Role,
    hkdf: &dyn Hkdf,
) -> Result<(PseudoRandKey, PseudoRandKey, PseudoRandKey), Error> {
    let pseudo_rand_key = hkdf.extract(salt, ecdh_secret)?;
    let hmac_key = hkdf.expand(&pseudo_rand_key, CONTEXT_KE_HMAC_KEY)?;
    match role {
        Role::Sink => {
            let in_encrypt_key = hkdf.expand(&pseudo_rand_key, CONTEXT_KE_ENCRYPTION_KEY_1)?;
            let out_encrypt_key = hkdf.expand(&pseudo_rand_key, CONTEXT_KE_ENCRYPTION_KEY_2)?;
            Ok((in_encrypt_key, out_encrypt_key, hmac_key))
        }
        Role::Source => {
            let in_encrypt_key = hkdf.expand(&pseudo_rand_key, CONTEXT_KE_ENCRYPTION_KEY_2)?;
            let out_encrypt_key = hkdf.expand(&pseudo_rand_key, CONTEXT_KE_ENCRYPTION_KEY_1)?;
            Ok((in_encrypt_key, out_encrypt_key, hmac_key))
        }
    }
}

/// Process the given protected headers of an arc and return the nonce used for key agreement
pub fn extract_nonce(protected_headers: &[(Label, Value)]) -> Result<Nonce16, Error> {
    for (label, val) in protected_headers {
        if *label == arc::KE_NONCE {
            match val {
                Value::Bytes(ref b) => {
                    return Ok(Nonce16(b.clone().try_into().map_err(|e| {
                        ag_err!(
                            InvalidPrivKeyArcInKey,
                            "invalid length of own nonce for key agreement: {:?}",
                            e
                        )
                    })?))
                }
                _ => {
                    return Err(ag_err!(
                        InvalidPrivKeyArcInKey,
                        "invalid encoding of own nonce for key agreement"
                    ))
                }
            };
        }
    }

    Err(ag_err!(InvalidPrivKeyArcInKey, "nonce for key agreement is missing in the headers"))
}
