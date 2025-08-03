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

//! Definitions related to different types of keys and other cryptographic artifacts.
use crate::arc;
use crate::error::Error;
use crate::traits::{EcDsa, Rng};
use crate::FallibleAllocExt;
use crate::{ag_err, ag_verr};
use alloc::{string::String, vec::Vec};
use authgraph_wire as wire;
use coset::{
    cbor, cbor::value::Value, iana, AsCborValue, CborOrdering, CborSerializable, CoseError,
    CoseKey, CoseSign1, Label,
};
use wire::ErrorCode;
use zeroize::ZeroizeOnDrop;

pub use wire::Key;

/// Length of an AES 256-bits key in bytes
pub const AES_256_KEY_LEN: usize = 32;

/// Size (in bytes) of a curve 25519 private key.
pub const CURVE25519_PRIV_KEY_LEN: usize = 32;

/// Version of the cert chain as per
/// hardware/interfaces/security/authgraph/aidl/android/hardware/security/authgraph/
/// ExplicitKeyDiceCertChain.cddl
pub const EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION: i32 = 1;

/// Version of the identity as per
/// hardware/interfaces/security/authgraph/aidl/android/hardware/security/authgraph/Identity.cddl
pub const IDENTITY_VERSION: i32 = 1;

/// Length of a SHA 256 digest in bytes
pub const SHA_256_LEN: usize = 32;

// Following constants represent the keys of the (key, value) pairs in a Dice certificate
const ISS: i64 = 1;
const SUB: i64 = 2;
const PROFILE_NAME: i64 = -4670554;
const SUBJECT_PUBLIC_KEY: i64 = -4670552;
const KEY_USAGE: i64 = -4670553;
const CODE_HASH: i64 = -4670545;
const CODE_DESC: i64 = -4670546;
const CONFIG_HASH: i64 = -4670547;
const CONFIG_DESC: i64 = -4670548;
const AUTHORITY_HASH: i64 = -4670549;
const AUTHORITY_DESC: i64 = -4670550;
const MODE: i64 = -4670551;

const COMPONENT_NAME: i64 = -70002;
const COMPONENT_VERSION: i64 = -70003;
const RESETTABLE: i64 = -70004;
const SECURITY_VERSION: i64 = -70005;
const RKP_VM_MARKER: i64 = -70006;

/// AES key of 256 bits
#[derive(Clone, ZeroizeOnDrop)]
pub struct AesKey(pub [u8; AES_256_KEY_LEN]);

impl TryFrom<arc::ArcPayload> for AesKey {
    type Error = Error;
    fn try_from(payload: arc::ArcPayload) -> Result<AesKey, Self::Error> {
        if payload.0.len() != AES_256_KEY_LEN {
            return Err(ag_err!(
                InvalidSharedKeyArcs,
                "payload key has invalid length: {}",
                payload.0.len()
            ));
        }
        let mut key = AesKey([0; AES_256_KEY_LEN]);
        key.0.copy_from_slice(&payload.0);
        Ok(key)
    }
}

/// EC key pair on P256 curve, created for ECDH.
pub struct EcExchangeKey {
    /// Public key
    pub pub_key: EcExchangeKeyPub,
    /// Private key
    pub priv_key: EcExchangeKeyPriv,
}

/// Public key of an EC key pair created for ECDH
#[derive(Clone)]
pub struct EcExchangeKeyPub(pub CoseKey);

/// Private key of an EC key pair created for ECDH.
/// It is up to the implementers of the AuthGraph traits to decide how to encode the private key.
#[derive(ZeroizeOnDrop)]
pub struct EcExchangeKeyPriv(pub Vec<u8>);

/// Shared secret agreed via ECDH
#[derive(ZeroizeOnDrop)]
pub struct EcdhSecret(pub Vec<u8>);

/// Pseudo random key of 256 bits that is output by extract/expand functions of key derivation
#[derive(ZeroizeOnDrop)]
pub struct PseudoRandKey(pub [u8; 32]);

/// A nonce of 16 bytes, used for key exchange
#[derive(Clone)]
pub struct Nonce16(pub [u8; 16]);

impl Nonce16 {
    /// Create a random nonce of 16 bytes
    pub fn new(rng: &dyn Rng) -> Self {
        let mut nonce = Nonce16([0u8; 16]);
        rng.fill_bytes(&mut nonce.0);
        nonce
    }
}

/// A nonce of 12 bytes, used for AES-GCM encryption
pub struct Nonce12(pub [u8; 12]);

impl Nonce12 {
    /// Create a random nonce of 12 bytes
    pub fn new(rng: &dyn Rng) -> Self {
        let mut nonce = Nonce12([0u8; 12]);
        rng.fill_bytes(&mut nonce.0);
        nonce
    }
}

impl TryFrom<&[u8]> for Nonce12 {
    type Error = Error;
    fn try_from(v: &[u8]) -> Result<Self, Self::Error> {
        if v.len() != 12 {
            return Err(ag_err!(InvalidSharedKeyArcs, "nonce has invalid length: {}", v.len()));
        }
        let mut nonce = Nonce12([0; 12]);
        nonce.0.copy_from_slice(v);
        Ok(nonce)
    }
}

/// Milliseconds since an epoch that is common between source and sink
pub struct MillisecondsSinceEpoch(pub i64);

/// Variants of EC private key used to create signature
#[derive(Clone, ZeroizeOnDrop)]
pub enum EcSignKey {
    /// On curve Ed25519
    Ed25519([u8; CURVE25519_PRIV_KEY_LEN]),
    /// On NIST curve P-256
    P256(Vec<u8>),
    /// On NIST curve P-384
    P384(Vec<u8>),
}

/// Variants of EC public key used to verify signature
#[derive(Clone, PartialEq)]
pub enum EcVerifyKey {
    /// On curve Ed25519
    Ed25519(CoseKey),
    /// On NIST curve P-256
    P256(CoseKey),
    /// On NIST curve P-384
    P384(CoseKey),
}

impl Default for EcVerifyKey {
    fn default() -> Self {
        EcVerifyKey::P256(CoseKey::default())
    }
}

impl EcVerifyKey {
    /// Return the `CoseKey` contained in any variant of this enum.
    /// Assume that the `CoseKey` is checked for appropriate header parameters before it used for
    /// signature verifictation.
    pub fn get_key(self) -> CoseKey {
        match self {
            EcVerifyKey::Ed25519(k) | EcVerifyKey::P256(k) | EcVerifyKey::P384(k) => k,
        }
    }

    /// Similar to `get_key()`, return the `CoseKey` contained in any variant of this enum
    /// (but by reference).
    pub fn get_key_ref(&self) -> &CoseKey {
        match self {
            EcVerifyKey::Ed25519(k) | EcVerifyKey::P256(k) | EcVerifyKey::P384(k) => k,
        }
    }

    /// Validate whether the CoseKey is in the expected canonical form as per the spec.
    pub fn is_canonicalized(&self) -> bool {
        let mut expected = self.clone();
        expected.canonicalize_cose_key();
        *self == expected
    }

    /// Order the labels of the Cose Key, in order to ensure canonical encoding in accordance with
    /// Core Deterministic Encoding Requirements [RFC 8949 s4.2.1].
    pub fn canonicalize_cose_key(&mut self) {
        match self {
            EcVerifyKey::Ed25519(k) | EcVerifyKey::P256(k) | EcVerifyKey::P384(k) => {
                k.canonicalize(CborOrdering::Lexicographic);
            }
        }
    }

    /// Return the Cose signing algorithm corresponds to the given public signing key.
    /// Assume that the `CoseKey` is checked for appropriate header parameters before it is used for
    /// signature verification.
    pub fn get_cose_sign_algorithm(&self) -> iana::Algorithm {
        match *self {
            EcVerifyKey::Ed25519(_) => iana::Algorithm::EdDSA,
            EcVerifyKey::P256(_) => iana::Algorithm::ES256,
            EcVerifyKey::P384(_) => iana::Algorithm::ES384,
        }
    }

    /// Construct `EcVerifyKey` from `CoseKey`.
    pub fn from_cose_key(cose_key: CoseKey) -> Result<Self, CoseError> {
        // Only the algorithm is checked while decoding, other parameters are
        // checked during validation of the `EcVerifykey`.
        match cose_key.alg {
            Some(coset::Algorithm::Assigned(iana::Algorithm::EdDSA)) => {
                Ok(EcVerifyKey::Ed25519(cose_key))
            }
            Some(coset::Algorithm::Assigned(iana::Algorithm::ES256)) => {
                Ok(EcVerifyKey::P256(cose_key))
            }
            Some(coset::Algorithm::Assigned(iana::Algorithm::ES384)) => {
                Ok(EcVerifyKey::P384(cose_key))
            }
            Some(_) => {
                Err(CoseError::UnexpectedItem("unsupported algorithm", "Ed25519 or P256 or P384"))
            }
            None => Err(CoseError::UnexpectedItem("algorithm is none", "Ed25519 or P256 or P384")),
        }
    }

    /// Validate the key parameters
    pub fn validate_cose_key_params(&self) -> Result<(), Error> {
        match self {
            EcVerifyKey::Ed25519(cose_key) => check_cose_key_params(
                cose_key,
                iana::KeyType::OKP,
                iana::Algorithm::EdDSA,
                iana::EllipticCurve::Ed25519,
                ErrorCode::InvalidCertChain,
            ),
            EcVerifyKey::P256(cose_key) => check_cose_key_params(
                cose_key,
                iana::KeyType::EC2,
                iana::Algorithm::ES256,
                iana::EllipticCurve::P_256,
                ErrorCode::InvalidCertChain,
            ),
            EcVerifyKey::P384(cose_key) => check_cose_key_params(
                cose_key,
                iana::KeyType::EC2,
                iana::Algorithm::ES384,
                iana::EllipticCurve::P_384,
                ErrorCode::InvalidCertChain,
            ),
        }
    }
}

/// HMAC key of 256 bits
#[derive(ZeroizeOnDrop)]
pub struct HmacKey(pub [u8; 32]);

/// Identity of an AuthGraph participant. The CDDL is listed in hardware/interfaces/security/
/// authgraph/aidl/android/hardware/security/Identity.cddl
#[derive(Clone, PartialEq)]
pub struct Identity {
    /// Version of the cddl
    pub version: i32,
    /// Certificate chain
    pub cert_chain: CertChain,
    /// Identity verification policy
    pub policy: Option<Policy>,
}

/// Certificate chain containing the public signing key. The CDDL is listed in
/// hardware/interfaces/security/authgraph/aidl/android/hardware/security/
/// authgraph/ExplicitKeyDiceCertChain.cddl
#[derive(Clone, PartialEq)]
pub struct CertChain {
    /// Version of the cddl
    pub version: i32,
    /// Root public key used to verify the signature in the first DiceChainEntry. If `cert_chain`
    /// is none, this is the key used to verify the signature created by the AuthGraph participant.
    pub root_key: EcVerifyKey,
    /// Dice certificate chain.
    pub dice_cert_chain: Option<Vec<DiceChainEntry>>,
}

/// An entry in the certificate chain (i.e. a certificate).
#[derive(Clone, PartialEq)]
pub struct DiceChainEntry {
    /// A certificate is represented as CoseSign1. The `payload` field of CoseSign1 holds the CBOR
    /// encoded payload that was signed.
    pub signature: CoseSign1,
    /// The payload signed in the certificate is decoded as `DiceChainEntryPayload` for validation
    /// purposes.
    pub payload: DiceChainEntryPayload,
}

/// Payload for each entry in the DICE chain
#[derive(Default, Clone, PartialEq)]
pub struct DiceChainEntryPayload {
    /// Issuer of the DiceChainEntry. Required as per the CDDL.
    pub issuer: Option<String>,
    /// The party whom the certificate is issued to. Required as per the CDDL.
    pub subject: Option<String>,
    /// Profile name. Required as per the CDDL.
    pub profile_name: Option<String>,
    /// Public signing key of the party whom the certificate is issued to. Required as per the CDDL.
    pub subject_pub_key: Option<EcVerifyKey>,
    /// Usage of the key pair corresponding to `subject_public_key`. Required as per the CDDL.
    pub key_usage: Option<Vec<u8>>,
    /// Code hash. Required as per the CDDL.
    pub code_hash: Option<Vec<u8>>,
    /// Code descriptor. Optional as per the CDDL.
    pub code_descriptor: Option<Vec<u8>>,
    /// Configuration hash. Required as per the CDDL.
    pub configuration_hash: Option<Vec<u8>>,
    /// Configuration descriptor. Required as per the CDDL.
    pub configuration_descriptor: Option<ConfigurationDescriptor>,
    /// Authority hash. Required as per the CDDL.
    pub authority_hash: Option<Vec<u8>>,
    /// Authority descriptor. Optional as per the CDDL.
    pub authority_descriptor: Option<Vec<u8>>,
    /// Mode. Required as per the CDDL.
    pub mode: Option<Vec<u8>>,
    /// Any custom fields, if present
    pub custom_fields: Vec<(i64, Value)>,
}

/// Configuration descriptor in `DiceChainEntryPayload`. All the fields are optional
#[derive(Default, Clone, PartialEq)]
pub struct ConfigurationDescriptor {
    /// Component name
    pub component_name: Option<String>,
    /// Component version
    pub component_version: Option<ComponentVersion>,
    /// Resettable. If the field is present, the value is true, otherwise, it is false.
    pub resettable: bool,
    /// Security version
    pub security_version: Option<u32>,
    /// RKP VM Marker. If the field is present, the value is true, otherwise, it is false.
    pub rkp_vm_marker: bool,
    /// Any custom fields, if present
    pub custom_fields: Vec<(i64, Value)>,
}

/// Component version can be either an integer or a string, as per the CDDL.
#[derive(Clone, PartialEq)]
pub enum ComponentVersion {
    /// Version represented as an integer
    IntVersion(u32),
    /// Version represented as a string
    TextVersion(String),
}

/// Identity verification policy specifying how to validate the certificate chain. The CDDL is
/// listed in hardware/interfaces/security/authgraph/aidl/android/hardware/security/authgraph/
/// DicePolicy.cddl
#[derive(Clone, Eq, PartialEq)]
pub struct Policy(pub Vec<u8>);

/// The output of identity verification.
pub enum IdentityVerificationDecision {
    /// The latest certificate chain is allowed by the identity verification policy, the identity
    /// owner is not updated
    Match,
    /// The latest certificate chain is not allowed by the identity verification policy
    Mismatch,
    /// The latest certificate chain is allowed by the identity verification policy and the identity
    /// owner is updated
    Updated,
}

/// The structure containing the inputs for the `salt` used in extracting a pseudo random key
/// from the Diffie-Hellman secret.
/// salt = bstr .cbor [
///     source_version:    int,
///     sink_ke_pub_key:   bstr .cbor PlainPubKey,
///     source_ke_pub_key: bstr .cbor PlainPubKey,
///     sink_ke_nonce:     bstr .size 16,
///     source_ke_nonce:   bstr .size 16,
///     sink_cert_chain:   bstr .cbor ExplicitKeyDiceCertChain,
///     source_cert_chain: bstr .cbor ExplicitKeyDiceCertChain,
/// ]
pub struct SaltInput {
    /// Version advertised by the source (P1).
    pub source_version: i32,
    /// Public key from sink for key exchange
    pub sink_ke_pub_key: EcExchangeKeyPub,
    /// Public key from source for ke exchange
    pub source_ke_pub_key: EcExchangeKeyPub,
    /// Nonce from sink for key exchange
    pub sink_ke_nonce: Nonce16,
    /// Nonce from source for key exchange
    pub source_ke_nonce: Nonce16,
    /// ExplicitKeyDiceCertChain of sink
    pub sink_cert_chain: CertChain,
    /// ExplicitKeyDiceCertChain of source
    pub source_cert_chain: CertChain,
}

/// The structure containing the inputs for the `session_id` computed during key agreement.
/// session_id = bstr .cbor [
///     sink_ke_nonce:     bstr .size 16,
///     source_ke_nonce:   bstr .size 16,
/// ]
pub struct SessionIdInput {
    /// Nonce from sink for key exchange
    pub sink_ke_nonce: Nonce16,
    /// Nonce from source for key exchange
    pub source_ke_nonce: Nonce16,
}

impl Identity {
    /// A helper function to validate the peer's identity. The validation is mainly about the
    /// Dice certificate chain (see `validate` method on `CertChain`), which is part of the
    /// identity. Peer's identity is validated when the peer is authenticated (i.e. during
    /// verification of the signature of the peer). Return the signature verification key upon
    /// successful validation.
    pub fn validate(&self, ecdsa: &dyn EcDsa) -> Result<EcVerifyKey, Error> {
        if self.version != IDENTITY_VERSION {
            return Err(ag_err!(InvalidIdentity, "version mismatch"));
        }
        self.cert_chain.validate(ecdsa)
        // TODO: Assume that the policy is None for now.
    }
}

impl AsCborValue for Identity {
    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        let mut array = match value {
            Value::Array(a) if a.len() == 3 || a.len() == 2 => a,
            _ => {
                return Err(CoseError::UnexpectedItem("_", "array with two or three items"));
            }
        };
        // TODO: Assume policy is none for now
        let cert_chain = match array.remove(1) {
            Value::Bytes(cert_chain_encoded) => CertChain::from_slice(&cert_chain_encoded)?,
            _ => {
                return Err(CoseError::UnexpectedItem("_", "encoded CertChain"));
            }
        };
        let version: i32 = match array.remove(0) {
            Value::Integer(i) => i.try_into()?,
            _ => {
                return Err(CoseError::UnexpectedItem("_", "Integer"));
            }
        };
        Ok(Identity { version, cert_chain, policy: None })
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        let mut array = Vec::<Value>::new();
        array.try_push(Value::Integer(self.version.into())).map_err(|_| CoseError::EncodeFailed)?;
        array
            .try_push(Value::Bytes(self.cert_chain.to_vec()?))
            .map_err(|_| CoseError::EncodeFailed)?;
        // TODO: encode policy if present
        Ok(Value::Array(array))
    }
}

impl CborSerializable for Identity {}

impl CertChain {
    /// Perform the following validations on the decoded DICE cert chain:
    /// 1. correctness of the `version`
    /// 2. `root_key` is in accordance with Core Deterministic Encoding Requirements
    ///    [RFC 8949 s4.2.1]
    /// 3. correctness of Cose key parameters of the `root_key`
    /// 4. if dice_cert_chain is present, check for each DiceChainEntry,
    ///    i.  Cose key parameters of `subject_pub_key`
    ///    ii. the signature is verified with the parent's `subject_pub_key` or with the `root_key`
    ///        for the first DiceChainEntry
    ///    iii.`subject` in the parent's DiceChainEntryPayload matches the `issuer` in the current
    ///        DiceChainEntryPayload (except for the first DiceChainEntry)
    ///    iv. no two identical `subject` or `subject_pub_key` in the DiceChainEntryPayloads.
    pub fn validate(&self, ecdsa: &dyn EcDsa) -> Result<EcVerifyKey, Error> {
        if self.version != EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION {
            return Err(ag_err!(InvalidCertChain, "version mismatch"));
        }
        if !self.root_key.is_canonicalized() {
            return Err(ag_err!(
                InvalidCertChain,
                "root key is not in the required canonical form"
            ));
        }
        self.root_key.validate_cose_key_params()?;
        match &self.dice_cert_chain {
            None => Ok(self.root_key.clone()),
            Some(dice_chain_entries) => {
                let mut parent_pub_sign_key = &self.root_key;
                let mut parent_subj: Option<&String> = None;
                let mut subj_pub_key_list = Vec::<&EcVerifyKey>::new();
                subj_pub_key_list.try_reserve(dice_chain_entries.len())?;
                for (i, dice_chain_entry) in dice_chain_entries.iter().enumerate() {
                    let subject_pub_key =
                        &dice_chain_entry.payload.subject_pub_key.as_ref().ok_or_else(|| {
                            ag_err!(InternalError, "subject public key is missing")
                        })?;
                    subject_pub_key.validate_cose_key_params()?;

                    let subject = &dice_chain_entry
                        .payload
                        .subject
                        .as_ref()
                        .ok_or_else(|| ag_err!(InternalError, "subject is missing"))?;
                    dice_chain_entry.signature.verify_signature(&[], |sig, data| {
                        ecdsa.verify_signature(parent_pub_sign_key, data, sig)
                    })?;

                    if i != 0
                        && *parent_subj.ok_or_else(|| {
                            ag_err!(InvalidCertChain, "parent's subject field is not initialized")
                        })? != *dice_chain_entry
                            .payload
                            .issuer
                            .as_ref()
                            .ok_or_else(|| ag_err!(InvalidCertChain, "issuer is missing"))?
                    {
                        return Err(ag_err!(
                            InvalidCertChain,
                            "parent's subject does not match the current issuer"
                        ));
                    }

                    if subj_pub_key_list.contains(subject_pub_key) {
                        return Err(ag_err!(InvalidCertChain, "subject public key is repeated"));
                    }

                    parent_pub_sign_key = subject_pub_key;
                    subj_pub_key_list.push(subject_pub_key);
                    parent_subj = Some(subject);
                }
                Ok(parent_pub_sign_key.clone())
            }
        }
    }
}

impl AsCborValue for CertChain {
    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        let mut array = match value {
            Value::Array(a) if a.len() >= 2 => a,
            _ => {
                return Err(CoseError::UnexpectedItem("_", "array with two or more items"));
            }
        };
        let dice_chain_entries_optional = if array.len() > 2 {
            let mut dice_chain_entries = Vec::<DiceChainEntry>::new();
            // TODO: find the correct CoseError to return
            dice_chain_entries.try_reserve(array.len() - 2).map_err(|_| CoseError::EncodeFailed)?;
            for i in (2..array.len()).rev() {
                let dice_chain_entry_encoded = array.remove(i);
                let dice_chain_entry = DiceChainEntry::from_cbor_value(dice_chain_entry_encoded)?;
                dice_chain_entries.push(dice_chain_entry);
            }
            dice_chain_entries.reverse();
            Some(dice_chain_entries)
        } else {
            None
        };
        let root_cose_key = match array.remove(1) {
            Value::Bytes(root_key_encoded) => {
                let cose_key = CoseKey::from_slice(&root_key_encoded)?;
                EcVerifyKey::from_cose_key(cose_key)?
            }
            _ => {
                return Err(CoseError::UnexpectedItem("_", "encoded CoseKey"));
            }
        };
        let version: i32 = match array.remove(0) {
            Value::Integer(i) => i.try_into()?,
            _ => {
                return Err(CoseError::UnexpectedItem("_", "Integer"));
            }
        };
        Ok(CertChain {
            version,
            root_key: root_cose_key,
            dice_cert_chain: dice_chain_entries_optional,
        })
    }

    fn to_cbor_value(mut self) -> Result<Value, CoseError> {
        let mut array = Vec::<Value>::new();
        array.try_reserve(2).map_err(|_| CoseError::EncodeFailed)?;
        array.push(Value::Integer(self.version.into()));
        // Prepare the root key to be encoded in accordance with
        // Core Deterministic Encoding Requirements [RFC 8949 s4.2.1], as specified in
        // hardware/interfaces/security/authgraph/aidl/android/hardware/security/authgraph/
        // ExplicitKeyDiceCertChain.cddl
        self.root_key.canonicalize_cose_key();
        array.push(Value::Bytes(self.root_key.get_key().to_vec()?));
        if let Some(dice_chain_entries) = self.dice_cert_chain {
            let len = dice_chain_entries.len();
            array.try_reserve(len).map_err(|_| CoseError::EncodeFailed)?;
            for dice_chain_entry in dice_chain_entries {
                array.push(dice_chain_entry.to_cbor_value()?);
            }
        }
        Ok(Value::Array(array))
    }
}

impl CborSerializable for CertChain {}

impl AsCborValue for DiceChainEntry {
    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        let signature = CoseSign1::from_cbor_value(value)?;
        let payload = DiceChainEntryPayload::from_slice(
            &signature.payload.clone().ok_or(CoseError::EncodeFailed)?,
        )?;
        Ok(DiceChainEntry { signature, payload })
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        // We only need to encode the first field (i.e. `signature`) of `DiceChainEntry` because as
        // per the CDDL, `DiceChainEntry` is just a CoseSign1. The corresponding Rust struct
        // contains the additional `payload` field only for the purpose of validation, therefore, it
        // does not need to be included in the CBOR encoding.
        self.signature.to_cbor_value()
    }
}

impl CborSerializable for DiceChainEntry {}

impl AsCborValue for DiceChainEntryPayload {
    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        let payload_map = match value {
            Value::Map(map) => map,
            _ => {
                return Err(CoseError::UnexpectedItem("non-map", "map of entries"));
            }
        };
        let mut dice_chain_entry_payload = DiceChainEntryPayload::default();
        for (key, val) in payload_map {
            let key_int: i64 = key
                .as_integer()
                .ok_or(CoseError::UnexpectedItem("None", "an Integer"))?
                .try_into()
                .map_err(|_| CoseError::UnexpectedItem("error", "an Integer convertible to i64"))?;
            match (key_int, val) {
                (ISS, Value::Text(issuer)) => match dice_chain_entry_payload.issuer {
                    None => dice_chain_entry_payload.issuer = Some(issuer),
                    Some(_) => {
                        return Err(CoseError::UnexpectedItem(
                            "single entry for issuer",
                            "repeated entries for issuer",
                        ));
                    }
                },
                (SUB, Value::Text(subject)) => match dice_chain_entry_payload.subject {
                    None => dice_chain_entry_payload.subject = Some(subject),
                    Some(_) => {
                        return Err(CoseError::UnexpectedItem(
                            "single entry for subject",
                            "repeated entries for subject",
                        ));
                    }
                },
                (PROFILE_NAME, Value::Text(profile_name)) => {
                    match dice_chain_entry_payload.profile_name {
                        None => dice_chain_entry_payload.profile_name = Some(profile_name),
                        Some(_) => {
                            return Err(CoseError::UnexpectedItem(
                                "single entry for profile name",
                                "repeated entries for profile name",
                            ));
                        }
                    }
                }
                (SUBJECT_PUBLIC_KEY, Value::Bytes(sp_key_bytes)) => {
                    match dice_chain_entry_payload.subject_pub_key {
                        None => {
                            let cose_key = CoseKey::from_slice(&sp_key_bytes)?;
                            let ec_verify_key = EcVerifyKey::from_cose_key(cose_key)?;
                            dice_chain_entry_payload.subject_pub_key = Some(ec_verify_key);
                        }
                        Some(_) => {
                            return Err(CoseError::UnexpectedItem(
                                "single entry for subject public key",
                                "repeated entries for subject public key",
                            ));
                        }
                    }
                }
                (KEY_USAGE, Value::Bytes(key_usage)) => match dice_chain_entry_payload.key_usage {
                    None => dice_chain_entry_payload.key_usage = Some(key_usage),
                    Some(_) => {
                        return Err(CoseError::UnexpectedItem(
                            "single entry for key usage",
                            "repeated entries for key usage",
                        ));
                    }
                },
                (CODE_HASH, Value::Bytes(code_hash)) => match dice_chain_entry_payload.code_hash {
                    None => dice_chain_entry_payload.code_hash = Some(code_hash),
                    Some(_) => {
                        return Err(CoseError::UnexpectedItem(
                            "single entry for code hash",
                            "repeated entries for code hash",
                        ));
                    }
                },
                (CODE_DESC, Value::Bytes(code_desc)) => {
                    match dice_chain_entry_payload.code_descriptor {
                        None => dice_chain_entry_payload.code_descriptor = Some(code_desc),
                        Some(_) => {
                            return Err(CoseError::UnexpectedItem(
                                "single or no entry for code descriptors",
                                "repeated entries for code descriptor",
                            ));
                        }
                    }
                }
                (CONFIG_HASH, Value::Bytes(config_hash)) => {
                    match dice_chain_entry_payload.configuration_hash {
                        None => dice_chain_entry_payload.configuration_hash = Some(config_hash),
                        Some(_) => {
                            return Err(CoseError::UnexpectedItem(
                                "single entry for configuration hash",
                                "repeated entries for configuration hash",
                            ));
                        }
                    }
                }
                (CONFIG_DESC, Value::Bytes(config_desc)) => {
                    match dice_chain_entry_payload.configuration_descriptor {
                        None => {
                            dice_chain_entry_payload.configuration_descriptor =
                                Some(ConfigurationDescriptor::from_slice(&config_desc)?);
                        }
                        Some(_) => {
                            return Err(CoseError::UnexpectedItem(
                                "single entry for configuration descriptor",
                                "repeated entries for configuration descriptor",
                            ));
                        }
                    }
                }
                (AUTHORITY_HASH, Value::Bytes(authority_hash)) => {
                    match dice_chain_entry_payload.authority_hash {
                        None => dice_chain_entry_payload.authority_hash = Some(authority_hash),
                        Some(_) => {
                            return Err(CoseError::UnexpectedItem(
                                "single entry for authority hash",
                                "repeated entries for authority hash",
                            ));
                        }
                    }
                }
                (AUTHORITY_DESC, Value::Bytes(authority_desc)) => {
                    match dice_chain_entry_payload.authority_descriptor {
                        None => {
                            dice_chain_entry_payload.authority_descriptor = Some(authority_desc)
                        }
                        Some(_) => {
                            return Err(CoseError::UnexpectedItem(
                                "single or no entry for authority descriptors",
                                "repeated entries for authority descriptor",
                            ));
                        }
                    }
                }
                (MODE, Value::Bytes(mode)) => match dice_chain_entry_payload.mode {
                    None => dice_chain_entry_payload.mode = Some(mode),
                    Some(_) => {
                        return Err(CoseError::UnexpectedItem(
                            "single entry for mode",
                            "repeated entries for mode",
                        ));
                    }
                },
                (k, v) => {
                    dice_chain_entry_payload
                        .custom_fields
                        .try_push((k, v))
                        .map_err(|_| CoseError::EncodeFailed)?;
                }
            }
        }
        Ok(dice_chain_entry_payload)
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        // This is not implemented because Authgraph protocol retrieves an already encoded DICE
        // chain via `Device` trait and the first field of `DiceChainEntry` has the encoded payload
        // that is signed.
        unimplemented!()
    }
}

impl CborSerializable for DiceChainEntryPayload {}

impl AsCborValue for ConfigurationDescriptor {
    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        let config_desc_map = match value {
            Value::Map(map) => map,
            _ => {
                return Err(CoseError::UnexpectedItem("non-map", "map of entries"));
            }
        };
        let mut config_descriptor = ConfigurationDescriptor::default();
        for (key, val) in config_desc_map {
            let key_int: i64 = key
                .as_integer()
                .ok_or(CoseError::UnexpectedItem("None", "an Integer"))?
                .try_into()
                .map_err(|_| CoseError::UnexpectedItem("error", "an Integer convertible to i64"))?;
            match (key_int, val) {
                (COMPONENT_NAME, Value::Text(comp_name)) => {
                    config_descriptor.component_name = Some(comp_name);
                }
                (COMPONENT_VERSION, Value::Text(comp_version)) => {
                    config_descriptor.component_version =
                        Some(ComponentVersion::TextVersion(comp_version));
                }
                (COMPONENT_VERSION, Value::Integer(comp_version)) => {
                    config_descriptor.component_version =
                        Some(ComponentVersion::IntVersion(comp_version.try_into().map_err(
                            |_| CoseError::UnexpectedItem("error", "an Integer convertible to u32"),
                        )?));
                }
                (RESETTABLE, Value::Null) => {
                    config_descriptor.resettable = true;
                }
                (SECURITY_VERSION, Value::Integer(security_version)) => {
                    config_descriptor.security_version =
                        Some(security_version.try_into().map_err(|_| {
                            CoseError::UnexpectedItem("error", "an Integer convertible to u32")
                        })?);
                }
                (RKP_VM_MARKER, Value::Null) => {
                    config_descriptor.rkp_vm_marker = true;
                }
                (k, v) => {
                    config_descriptor
                        .custom_fields
                        .try_push((k, v))
                        .map_err(|_| CoseError::EncodeFailed)?;
                }
            }
        }
        Ok(config_descriptor)
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        // This is not implemented because Authgraph protocol retrieves an already encoded DICE
        // chain via `Device` trait and the first field of `DiceChainEntry` has the encoded payload
        // that is signed.
        unimplemented!()
    }
}

impl CborSerializable for ConfigurationDescriptor {}

impl AsCborValue for SaltInput {
    fn from_cbor_value(_value: Value) -> Result<Self, CoseError> {
        // This method will never be called, except (maybe) in case of unit testing
        Err(CoseError::EncodeFailed)
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        let mut array = Vec::<Value>::new();
        array.try_reserve(7).map_err(|_| CoseError::EncodeFailed)?;
        array.push(Value::Integer(self.source_version.into()));
        array.push(Value::Bytes(self.sink_ke_pub_key.0.to_vec()?));
        array.push(Value::Bytes(self.source_ke_pub_key.0.to_vec()?));
        array.push(Value::Bytes(self.sink_ke_nonce.0.to_vec()));
        array.push(Value::Bytes(self.source_ke_nonce.0.to_vec()));
        array.push(Value::Bytes(self.sink_cert_chain.to_vec()?));
        array.push(Value::Bytes(self.source_cert_chain.to_vec()?));
        Ok(Value::Array(array))
    }
}

impl CborSerializable for SaltInput {}

impl AsCborValue for SessionIdInput {
    fn from_cbor_value(_value: Value) -> Result<Self, CoseError> {
        // This method will never be called, except (maybe) in case of unit testing
        Err(CoseError::EncodeFailed)
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        let mut array = Vec::<Value>::new();
        array.try_reserve(2).map_err(|_| CoseError::EncodeFailed)?;
        array.push(Value::Bytes(self.sink_ke_nonce.0.to_vec()));
        array.push(Value::Bytes(self.source_ke_nonce.0.to_vec()));
        Ok(Value::Array(array))
    }
}

impl CborSerializable for SessionIdInput {}

/// Given a `CoseKey` and the set of expected parameters, check if the `CoseKey` contains them.
pub fn check_cose_key_params(
    cose_key: &coset::CoseKey,
    want_kty: iana::KeyType,
    want_alg: iana::Algorithm,
    want_curve: iana::EllipticCurve,
    err_code: ErrorCode,
) -> Result<(), Error> {
    if cose_key.kty != coset::KeyType::Assigned(want_kty) {
        return Err(ag_verr!(err_code, "invalid kty {:?}, expect {want_kty:?}", cose_key.kty));
    }
    if cose_key.alg != Some(coset::Algorithm::Assigned(want_alg)) {
        return Err(ag_verr!(err_code, "invalid alg {:?}, expect {want_alg:?}", cose_key.alg));
    }
    let curve = cose_key
        .params
        .iter()
        .find_map(|(l, v)| match (l, v) {
            (Label::Int(l), Value::Integer(v)) if *l == iana::Ec2KeyParameter::Crv as i64 => {
                Some(*v)
            }
            _ => None,
        })
        .ok_or_else(|| ag_verr!(err_code, "no curve"))?;
    if curve != cbor::value::Integer::from(want_curve as u64) {
        return Err(ag_verr!(err_code, "invalid curve {curve:?}, expect {want_curve:?}"));
    }
    Ok(())
}
