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

//! Types and macros for communication between HAL and TA

// Allow missing docs in this crate as the types here are generally 1:1 with the HAL
// interface definitions.
#![allow(missing_docs)]
#![no_std]
extern crate alloc;

use crate::cbor::{cbor_type_error, AsCborValue, CborError};
use alloc::{vec, vec::Vec};
use authgraph_derive::AsCborValue;
use ciborium::value::Value;
use enumn::N;

pub mod cbor;
pub mod fragmentation;

#[cfg(test)]
mod tests;

/// Length of a session identifier in bytes
pub const SESSION_ID_LEN: usize = 32;

// Request/Response pairs corresponding to each of the methods on the `IAuthGraphKeyExchange`
// interface.  Structures that have a single field containing CBOR-encoded data are directly
// expanded.

#[derive(Debug, Clone, PartialEq, Eq, AsCborValue)]
pub struct CreateRequest {}
#[derive(Debug, Clone, PartialEq, Eq, AsCborValue)]
pub struct CreateResponse {
    pub ret: SessionInitiationInfo,
}
#[derive(Debug, Clone, PartialEq, Eq, AsCborValue)]
pub struct InitRequest {
    // Note that the AIDL definition for this field has type `PubKey`, which allows either a
    // `PlainPubKey` or a `SignedPubKey`, to allow for re-used in identity chains.  However, only
    // the `PlainPubKey` variant appears here.
    pub peer_pub_key: Vec<u8>, // PlainPubKey.cddl
    pub peer_id: Vec<u8>,      // Identity.cddl
    pub peer_nonce: Vec<u8>,
    pub peer_version: i32,
}
#[derive(Debug, Clone, PartialEq, Eq, AsCborValue)]
pub struct InitResponse {
    pub ret: KeInitResult,
}
#[derive(Debug, Clone, PartialEq, Eq, AsCborValue)]
pub struct FinishRequest {
    // Note that the AIDL definition for this field has type `PubKey`, which allows either a
    // `PlainPubKey` or a `SignedPubKey`, to allow for re-used in identity chains.  However, only
    // the `PlainPubKey` variant appears here.
    pub peer_pub_key: Vec<u8>,   // PlainPubKey.cddl
    pub peer_id: Vec<u8>,        // Identity.cddl
    pub peer_signature: Vec<u8>, // SessionIdSignature.cddl
    pub peer_nonce: Vec<u8>,
    pub peer_version: i32,
    pub own_key: Key,
}
#[derive(Debug, Clone, PartialEq, Eq, AsCborValue)]
pub struct FinishResponse {
    pub ret: SessionInfo,
}
#[derive(Debug, Clone, PartialEq, Eq, AsCborValue)]
pub struct AuthenticationCompleteRequest {
    pub peer_signature: Vec<u8>,   // SessionIdSignature.cddl
    pub shared_keys: [Vec<u8>; 2], // Arc.cddl
}
#[derive(Debug, Clone, PartialEq, Eq, AsCborValue)]
pub struct AuthenticationCompleteResponse {
    pub ret: [Vec<u8>; 2], // Arc.cddl
}

// Rust `struct`s corresponding to AIDL messages on the `IAuthGraphKeyExchange` interface.
// Structures that have a single field containing CBOR-encoded data are directly expanded.

#[derive(Debug, Clone, PartialEq, Eq, AsCborValue)]
pub struct SessionInitiationInfo {
    pub ke_key: Key,
    pub identity: Vec<u8>, // Identity.cddl
    pub nonce: Vec<u8>,
    pub version: i32,
}

#[derive(Debug, Clone, PartialEq, Eq, AsCborValue)]
pub struct SessionInfo {
    pub shared_keys: [Vec<u8>; 2], // Arc.cddl
    pub session_id: Vec<u8>,
    pub session_id_signature: Vec<u8>, // SessionIdSignature.cddl
}

#[derive(Debug, Clone, PartialEq, Eq, AsCborValue)]
pub struct KeInitResult {
    pub session_init_info: SessionInitiationInfo,
    pub session_info: SessionInfo,
}

#[derive(Debug, Clone, PartialEq, Eq, AsCborValue)]
pub struct Key {
    // Note that the AIDL definition for this field has type `PubKey`, which allows either a
    // `PlainPubKey` or a `SignedPubKey`, to allow for re-used in identity chains.  However, only
    // the `PlainPubKey` variant appears here.
    pub pub_key: Option<Vec<u8>>,      // PlainPubKey.cddl
    pub arc_from_pbk: Option<Vec<u8>>, // Arc.cddl
}

#[derive(Debug, Clone, Copy, PartialOrd, Ord, PartialEq, Eq, Hash, N)]
pub enum AuthGraphOperationCode {
    Create = 0x10,
    Init = 0x11,
    Finish = 0x12,
    AuthenticationComplete = 0x13,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PerformOpReq {
    Create(CreateRequest),
    Init(InitRequest),
    Finish(FinishRequest),
    AuthenticationComplete(AuthenticationCompleteRequest),
}

impl Code for PerformOpReq {
    fn code(&self) -> AuthGraphOperationCode {
        match self {
            Self::Create(_) => AuthGraphOperationCode::Create,
            Self::Init(_) => AuthGraphOperationCode::Init,
            Self::Finish(_) => AuthGraphOperationCode::Finish,
            Self::AuthenticationComplete(_) => AuthGraphOperationCode::AuthenticationComplete,
        }
    }
}

impl AsCborValue for PerformOpReq {
    fn from_cbor_value(value: Value) -> Result<Self, CborError> {
        let mut a = match value {
            Value::Array(a) if a.len() == 2 => a,
            _ => return crate::cbor_type_error(&value, "arr len 2"),
        };
        let val = a.remove(1);
        let code = i32::from_cbor_value(a.remove(0))?;
        let code = AuthGraphOperationCode::n(code).ok_or(CborError::NonEnumValue)?;
        Ok(match code {
            AuthGraphOperationCode::Create => Self::Create(CreateRequest::from_cbor_value(val)?),
            AuthGraphOperationCode::Init => Self::Init(InitRequest::from_cbor_value(val)?),
            AuthGraphOperationCode::Finish => Self::Finish(FinishRequest::from_cbor_value(val)?),
            AuthGraphOperationCode::AuthenticationComplete => {
                Self::AuthenticationComplete(AuthenticationCompleteRequest::from_cbor_value(val)?)
            }
        })
    }
    fn to_cbor_value(self) -> Result<Value, CborError> {
        Ok(Value::Array(match self {
            Self::Create(req) => vec![req.value(), req.to_cbor_value()?],
            Self::Init(req) => vec![req.value(), req.to_cbor_value()?],
            Self::Finish(req) => vec![req.value(), req.to_cbor_value()?],
            Self::AuthenticationComplete(req) => vec![req.value(), req.to_cbor_value()?],
        }))
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PerformOpRsp {
    Create(CreateResponse),
    Init(InitResponse),
    Finish(FinishResponse),
    AuthenticationComplete(AuthenticationCompleteResponse),
}

impl Code for PerformOpRsp {
    fn code(&self) -> AuthGraphOperationCode {
        match self {
            Self::Create(_) => AuthGraphOperationCode::Create,
            Self::Init(_) => AuthGraphOperationCode::Init,
            Self::Finish(_) => AuthGraphOperationCode::Finish,
            Self::AuthenticationComplete(_) => AuthGraphOperationCode::AuthenticationComplete,
        }
    }
}

impl AsCborValue for PerformOpRsp {
    fn from_cbor_value(value: Value) -> Result<Self, CborError> {
        let mut a = match value {
            Value::Array(a) if a.len() == 2 => a,
            _ => return crate::cbor_type_error(&value, "arr len 2"),
        };
        let val = a.remove(1);
        let code = i32::from_cbor_value(a.remove(0))?;
        let code = AuthGraphOperationCode::n(code).ok_or(CborError::NonEnumValue)?;
        Ok(match code {
            AuthGraphOperationCode::Create => Self::Create(CreateResponse::from_cbor_value(val)?),
            AuthGraphOperationCode::Init => Self::Init(InitResponse::from_cbor_value(val)?),
            AuthGraphOperationCode::Finish => Self::Finish(FinishResponse::from_cbor_value(val)?),
            AuthGraphOperationCode::AuthenticationComplete => {
                Self::AuthenticationComplete(AuthenticationCompleteResponse::from_cbor_value(val)?)
            }
        })
    }
    fn to_cbor_value(self) -> Result<Value, CborError> {
        Ok(Value::Array(match self {
            Self::Create(req) => vec![req.value(), req.to_cbor_value()?],
            Self::Init(req) => vec![req.value(), req.to_cbor_value()?],
            Self::Finish(req) => vec![req.value(), req.to_cbor_value()?],
            Self::AuthenticationComplete(req) => vec![req.value(), req.to_cbor_value()?],
        }))
    }
}

// Result of an operation, as an error code and a response message (only present when
// `error_code` is zero).
#[derive(Debug, Clone, PartialEq, Eq, AsCborValue)]
pub struct PerformOpResponse {
    pub error_code: ErrorCode,
    pub rsp: Option<PerformOpRsp>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, AsCborValue)]
#[repr(i32)]
pub enum ErrorCode {
    // Internal error codes corresponding to values in `Error.aidl`.
    /// Success
    Ok = 0,
    /// Invalid peer nonce for key agreement
    InvalidPeerNonce = -1,
    /// Invalid key agreement public key by the peer
    InvalidPeerKeKey = -2,
    /// Invalid identity of the peer
    InvalidIdentity = -3,
    /// Invalid certificate chain in the identity of the peer
    InvalidCertChain = -4,
    /// Invalid signature by the peer
    InvalidSignature = -5,
    /// Invalid key agreement key created by a particular party themselves to be used as a handle
    InvalidKeKey = -6,
    /// Invalid public key in the `Key` struct
    InvalidPubKeyInKey = -7,
    /// Invalid private key arc in the `Key` struct
    InvalidPrivKeyArcInKey = -8,
    /// Invalid shared key arcs
    InvalidSharedKeyArcs = -9,
    /// Memory allocation failed
    MemoryAllocationFailed = -10,
    /// The protocol version negotiated with the sink is incompatible
    IncompatibleProtocolVersion = -11,

    // Error codes corresponding to Binder error values.
    /// Internal processing error
    InternalError = -12,
    /// Unimplemented
    Unimplemented = -13,
}

/// Trait that associates an [`AuthGraphOperationCode`] with a message.
pub trait Code {
    /// Return the enum value associated with the underlying type of this item.
    fn code(&self) -> AuthGraphOperationCode;

    /// Return a [`Value`] holding the enum value.
    fn value(&self) -> Value {
        Value::Integer((self.code() as i32).into())
    }
}

macro_rules! impl_code {
    { $req:ident => $code:ident } => {
        impl Code for $req {
            fn code(&self) -> AuthGraphOperationCode {
                AuthGraphOperationCode::$code
            }
        }
    }
}

impl_code!(CreateRequest => Create);
impl_code!(InitRequest => Init);
impl_code!(FinishRequest => Finish);
impl_code!(AuthenticationCompleteRequest => AuthenticationComplete);
impl_code!(CreateResponse => Create);
impl_code!(InitResponse => Init);
impl_code!(FinishResponse => Finish);
impl_code!(AuthenticationCompleteResponse => AuthenticationComplete);
