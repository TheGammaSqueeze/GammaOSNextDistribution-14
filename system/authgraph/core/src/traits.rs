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

//! Traits representing crypto abstractions and device specific functionality.
use crate::error::Error;
use crate::key::{
    AesKey, EcExchangeKey, EcExchangeKeyPriv, EcExchangeKeyPub, EcSignKey, EcVerifyKey, EcdhSecret,
    HmacKey, Identity, IdentityVerificationDecision, MillisecondsSinceEpoch, Nonce12,
    PseudoRandKey, AES_256_KEY_LEN, SHA_256_LEN,
};
use alloc::boxed::Box;
use alloc::vec::Vec;
use authgraph_wire::SESSION_ID_LEN;
use coset::iana::{Algorithm, EllipticCurve};

/// The first version of the AuthGraph key exchange protocol
pub const AG_KEY_EXCHANGE_PROTOCOL_VERSION_1: i32 = 1;

/// These trait implementations must be provided by an implementation of the Authgraph API.
pub struct CryptoTraitImpl {
    /// Implementation of AES-GCM functionality.
    pub aes_gcm: Box<dyn AesGcm>,
    /// Implementation of ECDH functionality
    pub ecdh: Box<dyn EcDh>,
    /// Implementation of ECDSA functionality
    pub ecdsa: Box<dyn EcDsa>,
    /// Implementation of HMAC functionality
    pub hmac: Box<dyn Hmac>,
    /// Implementation of HKDF functionality
    pub hkdf: Box<dyn Hkdf>,
    /// Implementation of cryptographic hash function
    pub sha256: Box<dyn Sha256>,
    /// Implementation of secure random number generation
    pub rng: Box<dyn Rng>,
}

/// Trait methods for AES-GCM with 256 bit key (and 16 bytes tag).
pub trait AesGcm: Send {
    /// Generate a key
    fn generate_key(&self, rng: &mut dyn Rng) -> Result<AesKey, Error> {
        let mut key = AesKey([0; AES_256_KEY_LEN]);
        rng.fill_bytes(&mut key.0);
        Ok(key)
    }
    /// Encrypt
    fn encrypt(
        &self,
        key: &AesKey,
        payload: &[u8],
        aad: &[u8],
        nonce: &Nonce12,
    ) -> Result<Vec<u8>, Error>;
    /// Decrypt
    fn decrypt(
        &self,
        key: &AesKey,
        cipher_text: &[u8],
        aad: &[u8],
        nonce: &Nonce12,
    ) -> Result<Vec<u8>, Error>;
    /// Emit a copy of the trait implementation, as a boxed trait object.
    fn box_clone(&self) -> Box<dyn AesGcm>;
}

/// Trait methods for ECDH key exchange with P256 curve
pub trait EcDh: Send {
    /// Generate an EC key with P256 curve for ECDH
    fn generate_key(&self) -> Result<EcExchangeKey, Error>;
    /// Compute the ECDH secret. You can safely assume that the `CoseKey` of `EcExchangeKeyPub` has
    /// been checked for containing approrpriate parameters.
    fn compute_shared_secret(
        &self,
        own_key: &EcExchangeKeyPriv,
        peer_key: &EcExchangeKeyPub,
    ) -> Result<EcdhSecret, Error>;
}

/// Trait methods for EC sign/verify with EdDSA (with Ed25519 curve) and ECDSA (with P256 curve and
/// SHA-256 digest, and P384 curve and SHA-384 digest).
pub trait EcDsa: Send {
    /// Generate an ECDSA keypair identified by the given `curve`.
    ///
    /// This method is included in the trait for convenience of AuthGraph users; it is not required
    /// by AuthGraph itself.
    fn generate_key(&self, _curve: EllipticCurve) -> Result<(EcSignKey, EcVerifyKey), Error> {
        unimplemented!();
    }
    /// Compute signature over the given data, using the given EC private key. You can mark this as
    /// unimplemented and instead implement the `sign_data` method in the `Device` trait if the
    /// private signing key is not readily available (i.e. `get_identity` in the `Device` trait
    /// returns `None` for the first element of the returned tuple).
    fn sign(&self, sign_key: &EcSignKey, data: &[u8]) -> Result<Vec<u8>, Error>;
    /// Verify the signature over the data using the given EC public key. You can safely assume that
    /// the `Cosekey` of `EcVerifyKey` has been checked for containing the appropriate parameters.
    fn verify_signature(
        &self,
        verify_key: &EcVerifyKey,
        data: &[u8],
        signature: &[u8],
    ) -> Result<(), Error>;
}

/// Trait method for computing HMAC with 256-bit key and SHA-256 digest
pub trait Hmac: Send {
    /// Compute HMAC over the given data using the given key
    fn compute_hmac(&self, key: &HmacKey, data: &[u8]) -> Result<Vec<u8>, Error>;
}

/// Trait methods for extract and expand used in the key derivation process
pub trait Hkdf: Send {
    /// Extract step in key derivation
    fn extract(&self, salt: &[u8], ikm: &EcdhSecret) -> Result<PseudoRandKey, Error>;
    /// Expand step in key derivation
    fn expand(&self, prk: &PseudoRandKey, context: &[u8]) -> Result<PseudoRandKey, Error>;
}

/// Trait method for computing cryptographic hash function on an input that outputs 256-bit hash.
pub trait Sha256: Send {
    /// Compute SHA256 over an input
    fn compute_sha256(&self, data: &[u8]) -> Result<[u8; SHA_256_LEN], Error>;
}

/// Trait methods for generating cryptographically secure random numbers
pub trait Rng {
    /// Create a cryptographically secure random bytes
    fn fill_bytes(&self, nonce: &mut [u8]);
    /// Emit a copy of the trait implementation, as a boxed trait object.
    fn box_clone(&self) -> Box<dyn Rng>;
}

/// Trait methods for retrieving device specific information and for device specific functionality
pub trait Device {
    /// Retrieve (or create) the ephemeral (with per-boot life time) 256-bits encryption key to be
    /// used with AES-GCM
    fn get_or_create_per_boot_key(
        &self,
        aes: &dyn AesGcm,
        rng: &mut dyn Rng,
    ) -> Result<AesKey, Error>;
    /// Retrieve the already created ephemeral (with per-boot life time) 256-bits encryption key to
    /// be used with AES-GCM. Return an error if it does not exist at the time of this method call.
    fn get_per_boot_key(&self) -> Result<AesKey, Error>;
    /// Retrieve the identity of the AuthGraph participant, along with the signing key
    fn get_identity(&self) -> Result<(Option<EcSignKey>, Identity), Error>;
    /// Retrieve the Cose signing algorithm corresponding to the signing key pair of the party.
    fn get_cose_sign_algorithm(&self) -> Result<Algorithm, Error>;
    /// Compute signature over the given data. This is an alternative to the `sign` method in the
    /// `EcDsa` trait, when the private signing key is not readily available (i.e. `get_identity` in
    /// the `Device` trait returns `None` for the first element of the returned tuple). If the
    /// private signing key is returned from `get_identity` method, you can mark this as
    /// unimplemented and instead implement `sign` method in the `EcDsa` trait.
    fn sign_data(&self, ecdsa: &dyn EcDsa, data: &[u8]) -> Result<Vec<u8>, Error>;
    /// Validate the peer's identity (e.g. if `cert_chain` is present, validate the chain of
    /// signatures and any other required information in the certificate chain) and return the
    /// signature verification key.
    /// The default implementation calls the helper functions that perform the minimum required
    /// validation and extraction of the signature verification key.
    fn validate_peer_identity(
        &self,
        identity: &Identity,
        ecdsa: &dyn EcDsa,
    ) -> Result<EcVerifyKey, Error> {
        identity.validate(ecdsa)
    }
    /// Obtain the identity verification decision, by evaluating the latest identity against the
    /// previous identity.
    /// 1. If the `previous_identity` contains a `policy`, the `cert_chain` of the `latest_identity`
    ///    is evaluated against that policy to obtain `Match`/`Mismatch` decision. If the decision
    ///    is `Match`, then the `policy` in the `latest_identity` is compared against the `policy`
    ///    in the `previous_identity` to see if the final decision in `Updated`.
    /// 2. If the `previous_identity` does not contain a `policy`, the `cert_chain` of the
    ///    `latest_identity` is compared byte to byte with the `cert_chain` in the
    ///    `previous_identity`.
    fn evaluate_identity(
        &self,
        latest_identity: &Identity,
        previous_identity: &Identity,
    ) -> Result<IdentityVerificationDecision, Error>;
    /// Retrieve the latest supported version of the key exchange protocol.
    fn get_version(&self) -> i32 {
        // There is only one version now
        AG_KEY_EXCHANGE_PROTOCOL_VERSION_1
    }
    /// Given the latest supported version supported by the peer, select the matching version. This
    /// is relevant only when there are multiple versions of the protocol and a given party supports
    /// multiple versions. This is called on sink, as sink is the one who picks the matching version
    /// based on source's version.
    fn get_negotiated_version(&self, _peer_version: i32) -> i32 {
        // There is only one version now
        AG_KEY_EXCHANGE_PROTOCOL_VERSION_1
    }
    /// An internal entry point that hands over the finalized shared key arcs to the device specific
    /// implementation so that the application protocol that uses the shared keys for secure
    /// communication can store them in an appropriate way.
    fn record_shared_sessions(
        &mut self,
        peer_identity: &Identity,
        session_id: &[u8; SESSION_ID_LEN],
        shared_keys: &[Vec<u8>; 2],
        sha256: &dyn Sha256,
    ) -> Result<(), Error>;
    /// The counterpart of `record_shared_sessions` above. Given arc(s) containing the shared keys,
    /// validate them against the records created at the end of each key exchange protocol instance.
    /// The validation logic depends on the application protocol which uses the shared keys.
    fn validate_shared_sessions(
        &self,
        peer_identity: &Identity,
        session_id: &[u8; SESSION_ID_LEN],
        shared_keys: &[Vec<u8>],
        sha256: &dyn Sha256,
    ) -> Result<(), Error>;
}

/// Trait method to get current time  w.r.t a monotonic clock since an epoch that is common between
/// the source and sink.
pub trait MonotonicClock: Send {
    /// Get the current time
    fn now(&self) -> MillisecondsSinceEpoch;
}
