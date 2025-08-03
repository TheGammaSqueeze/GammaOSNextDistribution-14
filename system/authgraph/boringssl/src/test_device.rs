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

//! An example implementation for the Authgraph `Device` trait for testing purposes.

use authgraph_core::{
    ag_err,
    error::Error,
    key::{
        AesKey, CertChain, EcSignKey, EcVerifyKey, Identity, IdentityVerificationDecision,
        EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION, IDENTITY_VERSION,
    },
    traits,
};
use authgraph_wire::{ErrorCode, SESSION_ID_LEN};
use core::cell::RefCell;
use coset::{iana, CborOrdering};

/// The struct implementing the Authgraph `Device` trait.
pub struct AgDevice {
    per_boot_key: RefCell<Option<AesKey>>,
    identity: RefCell<Option<(EcSignKey, Identity)>>,
    cose_sign_algorithm: RefCell<Option<iana::Algorithm>>,
    // Make the (source/sink) version configurable for testing purposes
    version: RefCell<i32>,
}

impl Default for AgDevice {
    fn default() -> Self {
        AgDevice {
            per_boot_key: RefCell::new(None),
            identity: RefCell::new(None),
            cose_sign_algorithm: RefCell::new(None),
            version: RefCell::new(1),
        }
    }
}

impl AgDevice {
    /// Set the given identity
    pub fn set_identity(
        &self,
        identity: (EcSignKey, Identity),
        cose_sign_algorithm: iana::Algorithm,
    ) {
        *self.identity.borrow_mut() = Some(identity);
        *self.cose_sign_algorithm.borrow_mut() = Some(cose_sign_algorithm);
    }
}

impl traits::Device for AgDevice {
    fn get_or_create_per_boot_key(
        &self,
        aes: &dyn traits::AesGcm,
        rng: &mut dyn traits::Rng,
    ) -> Result<AesKey, Error> {
        if self.per_boot_key.borrow().is_none() {
            let pbk = aes.generate_key(rng)?;
            *self.per_boot_key.borrow_mut() = Some(pbk);
        }
        self.per_boot_key
            .borrow()
            .as_ref()
            .cloned()
            .ok_or(ag_err!(InternalError, "per boot key cannot be none at this point"))
    }

    fn get_per_boot_key(&self) -> Result<AesKey, Error> {
        self.per_boot_key
            .borrow()
            .as_ref()
            .cloned()
            .ok_or(ag_err!(InternalError, "per boot key is missing"))
    }

    /// If the `identity` field is not set (e.g. via `set_identity`), the default implementation
    /// creates identity with a ExplicitKeyDiceCertChain that only contains a
    /// DiceCertChainInitialPayload (i.e. no DiceChainEntry) and without a policy.
    /// DiceCertChainInitialPayload is an EC public key on P-256 curve in the default implementation
    fn get_identity(&self) -> Result<(Option<EcSignKey>, Identity), Error> {
        if self.identity.borrow().is_none() {
            let (priv_key, mut pub_key) = crate::ec::create_p256_key_pair(iana::Algorithm::ES256)?;
            pub_key.canonicalize(CborOrdering::Lexicographic);
            let identity = Identity {
                version: IDENTITY_VERSION,
                cert_chain: CertChain {
                    version: EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION,
                    root_key: EcVerifyKey::P256(pub_key),
                    dice_cert_chain: None,
                },
                policy: None,
            };
            self.set_identity((EcSignKey::P256(priv_key), identity), iana::Algorithm::ES256);
        }
        let (sign_key, identity) = self
            .identity
            .borrow()
            .as_ref()
            .cloned()
            .ok_or(ag_err!(InternalError, "identity is missing"))?;
        Ok((Some(sign_key), identity))
    }

    fn get_cose_sign_algorithm(&self) -> Result<iana::Algorithm, Error> {
        self.cose_sign_algorithm
            .borrow()
            .as_ref()
            .cloned()
            .ok_or(ag_err!(InternalError, "cose sign algorithm is missing"))
    }

    fn sign_data(&self, _ecdsa: &dyn traits::EcDsa, _data: &[u8]) -> Result<Vec<u8>, Error> {
        // Since the private signing key is returned in the `get_identity` method of this test
        // implementation of the `device` trait, and therefore we can use `EcDsa::sign` method, this
        // method is marked as `Unimplemented`.
        Err(ag_err!(Unimplemented, "unexpected signing request when the signing key available"))
    }

    fn evaluate_identity(
        &self,
        _latest_identity: &Identity,
        _previous_identity: &Identity,
    ) -> Result<IdentityVerificationDecision, Error> {
        // TODO (b/304623554): this trait method is not used in the key exchange protocol. This will
        // be implemented in the next phase of AuthGraph
        Err(ag_err!(Unimplemented, ""))
    }

    fn get_version(&self) -> i32 {
        *self.version.borrow()
    }

    fn get_negotiated_version(&self, peer_version: i32) -> i32 {
        let self_version = *self.version.borrow();
        if peer_version < self_version {
            return peer_version;
        }
        self_version
    }

    fn record_shared_sessions(
        &mut self,
        _peer_identity: &Identity,
        _session_id: &[u8; SESSION_ID_LEN],
        _shared_keys: &[Vec<u8>; 2],
        _sha256: &dyn traits::Sha256,
    ) -> Result<(), Error> {
        // The test implementation does not need to store the shared keys because there is no
        // application protocol to run using the shared keys.
        Ok(())
    }

    fn validate_shared_sessions(
        &self,
        _peer_identity: &Identity,
        _session_id: &[u8; SESSION_ID_LEN],
        _shared_keys: &[Vec<u8>],
        _sha256: &dyn traits::Sha256,
    ) -> Result<(), Error> {
        // The test implementation does not need to validate the shared keys because there is no
        // application protocol that depends on the shared keys.
        Ok(())
    }
}

impl AgDevice {
    /// Make the version configurable for testing purposes
    pub fn set_version(&self, version: i32) {
        *self.version.borrow_mut() = version
    }
}
