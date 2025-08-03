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

//! Implementation of an AuthGraph trusted application (TA).

use crate::{ag_err, error::Error, keyexchange};
use alloc::vec::Vec;
use authgraph_wire as wire;
use log::{debug, error, trace, warn};
use wire::{
    cbor::AsCborValue, AuthenticationCompleteResponse, Code, CreateResponse, ErrorCode,
    FinishResponse, InitResponse, PerformOpReq, PerformOpResponse, PerformOpRsp,
};

/// Indication of which roles to support.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Role {
    /// Act as (just) a source.
    Source,
    /// Act as (just) a sink.
    Sink,
    /// Act as both a source and a sink.
    Both,
}

impl Role {
    /// Indicate whether role supports acting as a source.
    pub fn is_source(&self) -> bool {
        match self {
            Role::Source | Role::Both => true,
            Role::Sink => false,
        }
    }
    /// Indicate whether role supports acting as a sink.
    pub fn is_sink(&self) -> bool {
        match self {
            Role::Sink | Role::Both => true,
            Role::Source => false,
        }
    }
}

/// AuthGraph trusted application instance.
pub struct AuthGraphTa {
    ag_participant: keyexchange::AuthGraphParticipant,
    role: Role,
}

impl AuthGraphTa {
    /// Create a TA instance using the provided trait implementations.
    pub fn new(ag_participant: keyexchange::AuthGraphParticipant, role: Role) -> Self {
        Self { ag_participant, role }
    }

    /// Process a single serialized request, returning a serialized response.
    pub fn process(&mut self, req_data: &[u8]) -> Vec<u8> {
        let (req_code, rsp) = match PerformOpReq::from_slice(req_data) {
            Ok(req) => {
                trace!("-> TA: received request {:?}", req.code());
                (Some(req.code()), self.process_req(req))
            }
            Err(e) => {
                error!("failed to decode CBOR request: {:?}", e);
                (None, error_rsp(ErrorCode::InternalError))
            }
        };
        trace!("<- TA: send response {:?} rc {:?}", req_code, rsp.error_code);
        match rsp.into_vec() {
            Ok(rsp_data) => rsp_data,
            Err(e) => {
                error!("failed to encode CBOR response: {:?}", e);
                invalid_cbor_rsp_data().to_vec()
            }
        }
    }

    /// Process a single request, returning a [`PerformOpResponse`].
    ///
    /// Select the appropriate method based on the request type, and use the
    /// request fields as parameters to the method.  In the opposite direction,
    /// build a response message from the values returned by the method.
    fn process_req(&mut self, req: PerformOpReq) -> PerformOpResponse {
        let code = req.code();
        let result = match req {
            PerformOpReq::Create(_req) => {
                self.create().map(|ret| PerformOpRsp::Create(CreateResponse { ret }))
            }
            PerformOpReq::Init(req) => self
                .init(&req.peer_pub_key, &req.peer_id, &req.peer_nonce, req.peer_version)
                .map(|ret| PerformOpRsp::Init(InitResponse { ret })),
            PerformOpReq::Finish(req) => self
                .finish(
                    &req.peer_pub_key,
                    &req.peer_id,
                    &req.peer_signature,
                    &req.peer_nonce,
                    req.peer_version,
                    req.own_key,
                )
                .map(|ret| PerformOpRsp::Finish(FinishResponse { ret })),
            PerformOpReq::AuthenticationComplete(req) => {
                self.auth_complete(&req.peer_signature, req.shared_keys).map(|ret| {
                    PerformOpRsp::AuthenticationComplete(AuthenticationCompleteResponse { ret })
                })
            }
        };
        match result {
            Ok(rsp) => PerformOpResponse { error_code: ErrorCode::Ok, rsp: Some(rsp) },
            Err(err) => {
                warn!("failing {:?} request with error {:?}", code, err);
                error_rsp(err.0)
            }
        }
    }

    fn create(&mut self) -> Result<wire::SessionInitiationInfo, Error> {
        if !self.role.is_source() {
            return Err(ag_err!(Unimplemented, "create() invoked on non-source"));
        }
        debug!("create()");
        self.ag_participant.create()
    }
    fn init(
        &mut self,
        peer_pub_key: &[u8],
        peer_id: &[u8],
        peer_nonce: &[u8],
        peer_version: i32,
    ) -> Result<wire::KeInitResult, Error> {
        if !self.role.is_sink() {
            return Err(ag_err!(Unimplemented, "init() invoked on non-sink"));
        }
        debug!("init()");
        self.ag_participant.init(peer_pub_key, peer_id, peer_nonce, peer_version)
    }
    fn finish(
        &mut self,
        peer_pub_key: &[u8],
        peer_id: &[u8],
        peer_signature: &[u8],
        peer_nonce: &[u8],
        peer_version: i32,
        own_key: wire::Key,
    ) -> Result<wire::SessionInfo, Error> {
        if !self.role.is_source() {
            return Err(ag_err!(Unimplemented, "finish() invoked on non-source"));
        }
        debug!("finish()");
        self.ag_participant.finish(
            peer_pub_key,
            peer_id,
            peer_signature,
            peer_nonce,
            peer_version,
            own_key,
        )
    }
    fn auth_complete(
        &mut self,
        peer_signature: &[u8],
        shared_keys: [Vec<u8>; 2],
    ) -> Result<[Vec<u8>; 2], Error> {
        if !self.role.is_sink() {
            return Err(ag_err!(Unimplemented, "auth_complete() invoked on non-sink"));
        }
        debug!("auth_complete()");
        self.ag_participant.authentication_complete(peer_signature, shared_keys)
    }
}

/// Create an error outer response structure with the given error code.
fn error_rsp(error_code: ErrorCode) -> PerformOpResponse {
    PerformOpResponse { error_code, rsp: None }
}

/// Hand-encoded [`PerformOpResponse`] data for [`ErrorCode::InternalError`].
/// Does not perform CBOR serialization (and so is suitable for error reporting if/when
/// CBOR serialization fails).
fn invalid_cbor_rsp_data() -> [u8; 3] {
    [
        0x82, // 2-arr
        0x2b, // nint, value -12
        0x80, // 0-arr
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_invalid_data() {
        // Cross-check that the hand-encoded invalid CBOR data matches an auto-encoded equivalent.
        let rsp = error_rsp(ErrorCode::InternalError);
        let rsp_data = rsp.into_vec().unwrap();
        assert_eq!(rsp_data, invalid_cbor_rsp_data());
    }
}
