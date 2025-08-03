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

//! Implementation of a HAL service for AuthGraph.

use crate::{channel::SerializedChannel, errcode_to_binder, Innto, TryInnto};
use android_hardware_security_authgraph::aidl::android::hardware::security::authgraph::{
    Arc::Arc, IAuthGraphKeyExchange::BnAuthGraphKeyExchange,
    IAuthGraphKeyExchange::IAuthGraphKeyExchange, Identity::Identity, KeInitResult::KeInitResult,
    Key::Key, PubKey::PubKey, SessionIdSignature::SessionIdSignature, SessionInfo::SessionInfo,
    SessionInitiationInfo::SessionInitiationInfo,
};
use authgraph_wire as wire;
use log::{error, warn};
use std::ffi::CString;
use wire::{
    cbor::{AsCborValue, CborError},
    Code, ErrorCode, PerformOpReq, PerformOpResponse, PerformOpRsp,
};

/// Emit a failure for a failed CBOR conversion.
#[inline]
pub fn failed_cbor(err: CborError) -> binder::Status {
    binder::Status::new_service_specific_error(
        ErrorCode::InternalError as i32,
        Some(&CString::new(format!("CBOR conversion failed: {:?}", err)).unwrap()),
    )
}

/// Implementation of the AuthGraph key exchange HAL service, communicating with a TA
/// in a secure environment via a communication channel.
pub struct AuthGraphService<T: SerializedChannel + 'static> {
    channel: T,
}

impl<T: SerializedChannel + 'static> AuthGraphService<T> {
    /// Construct a new instance that uses the provided channel.
    pub fn new(channel: T) -> Self {
        Self { channel }
    }

    /// Create a new instance wrapped in a proxy object.
    pub fn new_as_binder(channel: T) -> binder::Strong<dyn IAuthGraphKeyExchange> {
        BnAuthGraphKeyExchange::new_binder(Self::new(channel), binder::BinderFeatures::default())
    }

    /// Execute the given request, by serializing it and sending it down the internal channel.  Then
    /// read and deserialize the response.
    fn execute_req(&self, req: PerformOpReq) -> binder::Result<PerformOpRsp> {
        let code = req.code();
        let req_data = req.into_vec().map_err(failed_cbor)?;
        if req_data.len() > T::MAX_SIZE {
            error!(
                "HAL operation {:?} encodes bigger {} than max size {}",
                code,
                req_data.len(),
                T::MAX_SIZE
            );
            return Err(errcode_to_binder(ErrorCode::InternalError));
        }

        // Pass the request to the channel, and read the response.
        let rsp_data = self.channel.execute(&req_data)?;

        let outer_rsp = PerformOpResponse::from_slice(&rsp_data).map_err(failed_cbor)?;
        if outer_rsp.error_code != ErrorCode::Ok {
            warn!("HAL: command {:?} failed: {:?}", code, outer_rsp.error_code);
            return Err(errcode_to_binder(outer_rsp.error_code));
        }
        // On an OK outer response, the inner response should be populated with a message that
        // matches the request.
        outer_rsp.rsp.ok_or_else(|| {
            binder::Status::new_service_specific_error(
                ErrorCode::InternalError as i32,
                Some(&CString::new("missing inner rsp in OK outer rsp".to_string()).unwrap()),
            )
        })
    }
}

/// Macro to extract a specific variant from a [`PerformOpRsp`].
macro_rules! require_op_rsp {
    { $rsp:expr => $variant:ident } => {
        match $rsp {
            PerformOpRsp::$variant(rsp) => Ok(rsp),
            _ => Err(failed_cbor(CborError::UnexpectedItem("wrong rsp code", "rsp code"))),
        }
    }
}

impl<T: SerializedChannel> binder::Interface for AuthGraphService<T> {}

/// Implement the `IAuthGraphKeyExchange` interface by translating incoming method invocations
/// into request messages, which are passed to the TA.  The corresponding response from the TA
/// is then parsed and the return values extracted.
impl<T: SerializedChannel> IAuthGraphKeyExchange for AuthGraphService<T> {
    fn create(&self) -> binder::Result<SessionInitiationInfo> {
        let req = PerformOpReq::Create(wire::CreateRequest {});
        let wire::CreateResponse { ret } = require_op_rsp!(self.execute_req(req)? => Create)?;
        Ok(ret.innto())
    }
    fn init(
        &self,
        peer_pub_key: &PubKey,
        peer_id: &Identity,
        peer_nonce: &[u8],
        peer_version: i32,
    ) -> binder::Result<KeInitResult> {
        let req = PerformOpReq::Init(wire::InitRequest {
            peer_pub_key: unsigned_pub_key(peer_pub_key)?,
            peer_id: peer_id.identity.to_vec(),
            peer_nonce: peer_nonce.to_vec(),
            peer_version,
        });
        let wire::InitResponse { ret } = require_op_rsp!(self.execute_req(req)? => Init)?;
        Ok(ret.innto())
    }

    fn finish(
        &self,
        peer_pub_key: &PubKey,
        peer_id: &Identity,
        peer_signature: &SessionIdSignature,
        peer_nonce: &[u8],
        peer_version: i32,
        own_key: &Key,
    ) -> binder::Result<SessionInfo> {
        let req = PerformOpReq::Finish(wire::FinishRequest {
            peer_pub_key: unsigned_pub_key(peer_pub_key)?,
            peer_id: peer_id.identity.to_vec(),
            peer_signature: peer_signature.signature.to_vec(),
            peer_nonce: peer_nonce.to_vec(),
            peer_version,
            own_key: own_key.clone().try_innto()?,
        });
        let wire::FinishResponse { ret } = require_op_rsp!(self.execute_req(req)? => Finish)?;
        Ok(ret.innto())
    }

    fn authenticationComplete(
        &self,
        peer_signature: &SessionIdSignature,
        shared_keys: &[Arc; 2],
    ) -> binder::Result<[Arc; 2]> {
        let req = PerformOpReq::AuthenticationComplete(wire::AuthenticationCompleteRequest {
            peer_signature: peer_signature.signature.to_vec(),
            shared_keys: [shared_keys[0].arc.to_vec(), shared_keys[1].arc.to_vec()],
        });
        let wire::AuthenticationCompleteResponse { ret } =
            require_op_rsp!(self.execute_req(req)? => AuthenticationComplete)?;
        let [arc0, arc1] = ret;
        Ok([Arc { arc: arc0 }, Arc { arc: arc1 }])
    }
}

/// Extract (and require) an unsigned public key as bytes from a [`PubKey`].
fn unsigned_pub_key(pub_key: &PubKey) -> binder::Result<Vec<u8>> {
    match pub_key {
        PubKey::PlainKey(key) => Ok(key.plainPubKey.to_vec()),
        PubKey::SignedKey(_) => Err(binder::Status::new_exception(
            binder::ExceptionCode::ILLEGAL_ARGUMENT,
            Some(&CString::new("expected unsigned public key").unwrap()),
        )),
    }
}
