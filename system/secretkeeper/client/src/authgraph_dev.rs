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

//! Secretkeeper client acts as the "source" `AuthGraphParticipant` in context of AuthGraph
//! Key Exchange, while Secretkeeper itself acts as "sink" `AuthGraphParticipant`. This module
//! supports functionality to configure the local "source" `AuthGraphParticipant` for clients.

extern crate alloc;

use crate::dice::OwnedDiceArtifactsWithExplicitKey;
use authgraph_boringssl::{BoringAes, BoringRng};
use authgraph_core::ag_err;
use authgraph_core::error::Error as AgError;
use authgraph_core::error::Error;
use authgraph_core::key::{
    AesKey, CertChain, EcSignKey, EcVerifyKey, Identity, IdentityVerificationDecision,
    CURVE25519_PRIV_KEY_LEN, IDENTITY_VERSION,
};
use authgraph_core::traits;
use authgraph_core::traits::AG_KEY_EXCHANGE_PROTOCOL_VERSION_1;
use authgraph_core::traits::{AesGcm, Device, EcDsa};
use authgraph_wire::{ErrorCode, SESSION_ID_LEN};
use coset::CborSerializable;
use coset::{iana, CoseKey};
use diced_open_dice::derive_cdi_leaf_priv;

/// Implementation of `authgraph_core::traits::Device` required for configuring the local
/// `AuthGraphParticipant` for client.
pub struct AgDevice {
    per_boot_key: AesKey,
    identity: (EcSignKey, Identity),
    expected_peer_key: Option<CoseKey>,
}

impl AgDevice {
    const AG_KE_VERSION: i32 = AG_KEY_EXCHANGE_PROTOCOL_VERSION_1;
    /// Create a new `AgDevice`, using dice_artifacts for `Identity`.
    pub fn new(
        dice_artifacts: &OwnedDiceArtifactsWithExplicitKey,
        expected_peer_key: Option<CoseKey>,
    ) -> Result<Self, AgError> {
        let cdi_leaf_priv = derive_cdi_leaf_priv(dice_artifacts)
            .map_err(|_| ag_err!(InternalError, "Failed to get private key"))?;
        let identity = Identity {
            version: IDENTITY_VERSION,
            cert_chain: CertChain::from_slice(
                dice_artifacts
                    .explicit_key_dice_chain()
                    .ok_or(ag_err!(InternalError, "Dice chain missing"))?,
            )?,
            policy: None,
        };

        let per_boot_key = BoringAes {}.generate_key(&mut BoringRng {})?;

        Ok(Self {
            per_boot_key,
            identity: (
                EcSignKey::Ed25519(
                    // AuthGraph supports storing only the Ed25519 "seed", which is first 32 bytes
                    // of the private key, instead of the full private key.
                    cdi_leaf_priv.as_array()[0..CURVE25519_PRIV_KEY_LEN].try_into().map_err(
                        |e| {
                            ag_err!(
                                InternalError,
                                "Failed to construct private signing key {:?}",
                                e
                            )
                        },
                    )?,
                ),
                identity,
            ),
            expected_peer_key,
        })
    }
}

impl Device for AgDevice {
    fn get_or_create_per_boot_key(
        &self,
        _aes: &dyn traits::AesGcm,
        _rng: &mut dyn traits::Rng,
    ) -> Result<AesKey, AgError> {
        Ok(self.per_boot_key.clone())
    }

    fn get_per_boot_key(&self) -> Result<AesKey, AgError> {
        Ok(self.per_boot_key.clone())
    }

    fn get_identity(&self) -> Result<(Option<EcSignKey>, Identity), AgError> {
        let (sign_key, identity) = self.identity.clone();
        Ok((Some(sign_key), identity))
    }

    fn get_cose_sign_algorithm(&self) -> Result<iana::Algorithm, AgError> {
        // Microdroid Manager uses EdDSA as the signing algorithm while doing DICE derivation.
        Ok(iana::Algorithm::EdDSA)
    }

    fn sign_data(&self, _ecdsa: &dyn traits::EcDsa, _data: &[u8]) -> Result<Vec<u8>, AgError> {
        // Since the private signing key is already returned in the `get_identity` method of this
        // implementation, this method can be marked `Unimplemented`.
        Err(ag_err!(Unimplemented, "Unexpected signing request when the signing key available"))
    }

    fn evaluate_identity(
        &self,
        _latest_identity: &Identity,
        _previous_identity: &Identity,
    ) -> Result<IdentityVerificationDecision, AgError> {
        // AuthGraph Key Exchange protocol does not require this. Additionally, Secretkeeper
        // clients create fresh, short-lived sessions and their identity does not change within
        // a session.
        Err(ag_err!(Unimplemented, "Not required"))
    }

    fn get_version(&self) -> i32 {
        Self::AG_KE_VERSION
    }

    fn record_shared_sessions(
        &mut self,
        _peer_identity: &Identity,
        _session_id: &[u8; SESSION_ID_LEN],
        _shared_keys: &[Vec<u8>; 2],
        _sha256: &dyn traits::Sha256,
    ) -> Result<(), AgError> {
        // There are alternative ways of recording the shared session such as inspecting the output
        // of KE methods.
        Ok(())
    }

    fn validate_peer_identity(
        &self,
        identity: &Identity,
        ecdsa: &dyn EcDsa,
    ) -> Result<EcVerifyKey, Error> {
        if identity.cert_chain.dice_cert_chain.is_some() {
            return Err(ag_err!(InvalidPeerKeKey, "Expected peer's DICE chain to be None"));
        }
        let root_key = identity.validate(ecdsa)?;

        if let Some(expected_key) = &self.expected_peer_key {
            // Do bit by bit comparison of the keys. This assumes the 2 keys are equivalently
            // canonicalized
            if root_key.get_key_ref() != expected_key {
                return Err(ag_err!(
                    InvalidPeerKeKey,
                    "Peer identity did not match the expected identity"
                ));
            }
        }
        Ok(root_key)
    }

    fn validate_shared_sessions(
        &self,
        _peer_identity: &Identity,
        _session_id: &[u8; SESSION_ID_LEN],
        _shared_keys: &[Vec<u8>],
        _sha256: &dyn traits::Sha256,
    ) -> Result<(), Error> {
        // Currently, there is no use of validation of shared session to application protocol.
        Ok(())
    }
}
