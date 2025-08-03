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

//! Crate that holds common code for an AuthGraph HAL service.

use android_hardware_security_authgraph::aidl::android::hardware::security::authgraph::{
    Arc::Arc, Identity::Identity, KeInitResult::KeInitResult, Key::Key, PlainPubKey::PlainPubKey,
    PubKey::PubKey, SessionIdSignature::SessionIdSignature, SessionInfo::SessionInfo,
    SessionInitiationInfo::SessionInitiationInfo,
};
use authgraph_wire as wire;
use log::warn;

pub mod channel;
pub mod service;

// Neither the AIDL types nor the `authgraph_core` types are local to this crate, which means that
// Rust's orphan rule means we cannot implement the standard conversion traits.  So instead define
// our own equivalent conversion traits that are local, and for which we're allowed to provide
// implementations.  Give them an odd name to avoid confusion with the standard traits.

/// Local equivalent of `From` trait, with a different name to avoid clashes.
pub trait Fromm<T>: Sized {
    /// Convert `val` into type `Self`.
    fn fromm(val: T) -> Self;
}
/// Local equivalent of `TryFrom` trait, with a different name to avoid clashes.
pub trait TryFromm<T>: Sized {
    /// Error type emitted on conversion failure.
    type Error;
    /// Try to convert `val` into type `Self`.
    fn try_fromm(val: T) -> Result<Self, Self::Error>;
}
/// Local equivalent of `Into` trait, with a different name to avoid clashes.
pub trait Innto<T> {
    /// Convert `self` into type `T`.
    fn innto(self) -> T;
}
/// Local equivalent of `TryInto` trait, with a different name to avoid clashes.
pub trait TryInnto<T> {
    /// Error type emitted on conversion failure.
    type Error;
    /// Try to convert `self` into type `T`.
    fn try_innto(self) -> Result<T, Self::Error>;
}
/// Blanket implementation of `Innto` from `Fromm`
impl<T, U> Innto<U> for T
where
    U: Fromm<T>,
{
    fn innto(self) -> U {
        U::fromm(self)
    }
}
/// Blanket implementation of `TryInnto` from `TryFromm`
impl<T, U> TryInnto<U> for T
where
    U: TryFromm<T>,
{
    type Error = U::Error;
    fn try_innto(self) -> Result<U, Self::Error> {
        U::try_fromm(self)
    }
}

// Conversions from internal types to HAL-defined types.

impl Fromm<wire::SessionInitiationInfo> for SessionInitiationInfo {
    fn fromm(val: wire::SessionInitiationInfo) -> Self {
        Self {
            key: val.ke_key.innto(),
            identity: Identity { identity: val.identity },
            nonce: val.nonce,
            version: val.version,
        }
    }
}

impl Fromm<wire::Key> for Key {
    fn fromm(val: wire::Key) -> Self {
        Self {
            pubKey: val
                .pub_key
                .map(|pub_key| PubKey::PlainKey(PlainPubKey { plainPubKey: pub_key })),
            arcFromPBK: val.arc_from_pbk.map(|arc| Arc { arc }),
        }
    }
}

impl Fromm<wire::KeInitResult> for KeInitResult {
    fn fromm(val: wire::KeInitResult) -> Self {
        Self {
            sessionInitiationInfo: val.session_init_info.innto(),
            sessionInfo: val.session_info.innto(),
        }
    }
}

impl Fromm<wire::SessionInfo> for SessionInfo {
    fn fromm(val: wire::SessionInfo) -> Self {
        Self {
            sharedKeys: val.shared_keys.map(|arc| Arc { arc }),
            sessionId: val.session_id,
            signature: SessionIdSignature { signature: val.session_id_signature },
        }
    }
}

// Conversions from HAL-defined types to internal types.

impl TryFromm<Key> for wire::Key {
    type Error = binder::Status;
    fn try_fromm(aidl: Key) -> Result<Self, Self::Error> {
        let pub_key = match aidl.pubKey {
            None => None,
            Some(PubKey::PlainKey(k)) => Some(k.plainPubKey),
            Some(PubKey::SignedKey(_)) => return Err(arg_err("expect plain pubkey")),
        };
        Ok(Self { pub_key, arc_from_pbk: aidl.arcFromPBK.map(|a| a.arc) })
    }
}

/// Generate a binder illegal argument error with the given message.
fn arg_err(msg: &str) -> binder::Status {
    binder::Status::new_exception(
        binder::ExceptionCode::ILLEGAL_ARGUMENT,
        Some(&std::ffi::CString::new(msg).unwrap()),
    )
}

/// Convert a [`wire::ErrorCore`] into a binder error.
pub fn errcode_to_binder(err: wire::ErrorCode) -> binder::Status {
    warn!("operation failed: {err:?}");
    // Translate the internal errors for `Unimplemented` and `InternalError` to their counterparts
    // in binder errors to have uniformity in the Android HAL layer
    match err {
        wire::ErrorCode::Unimplemented => {
            binder::Status::new_exception(binder::ExceptionCode::UNSUPPORTED_OPERATION, None)
        }
        wire::ErrorCode::InternalError => {
            binder::Status::new_exception(binder::ExceptionCode::SERVICE_SPECIFIC, None)
        }
        _ => binder::Status::new_service_specific_error(err as i32, None),
    }
}
