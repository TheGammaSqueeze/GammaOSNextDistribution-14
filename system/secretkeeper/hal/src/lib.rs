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

//! Crate that holds common code for a Secretkeeper HAL service.

use android_hardware_security_secretkeeper::aidl::android::hardware::security::secretkeeper::{
    ISecretkeeper::{
        ISecretkeeper, BnSecretkeeper
    },
    SecretId::SecretId as AidlSecretId,
};
use android_hardware_security_authgraph::aidl::android::hardware::security::authgraph::{
    IAuthGraphKeyExchange::IAuthGraphKeyExchange,
};
use authgraph_hal::channel::SerializedChannel;
use authgraph_hal::service::AuthGraphService;
use coset::CborSerializable;
use secretkeeper_comm::wire::{PerformOpSuccessRsp, PerformOpResponse, ApiError, PerformOpReq, AidlErrorCode, SecretId};
use std::ffi::CString;

/// Implementation of the Secretkeeper HAL service, communicating with a TA
/// in a secure environment via a communication channel.
pub struct SecretkeeperService<T: SerializedChannel + 'static> {
    authgraph: binder::Strong<dyn IAuthGraphKeyExchange>,
    channel: T,
}

impl<T: SerializedChannel + 'static> SecretkeeperService<T> {
    /// Construct a new instance that uses the provided channels.
    pub fn new<S: SerializedChannel + 'static>(sk_channel: T, ag_channel: S) -> Self {
        Self { authgraph: AuthGraphService::new_as_binder(ag_channel), channel: sk_channel }
    }

    /// Create a new instance wrapped in a proxy object.
    pub fn new_as_binder<S: SerializedChannel + 'static>(
        sk_channel: T,
        ag_channel: S,
    ) -> binder::Strong<dyn ISecretkeeper> {
        BnSecretkeeper::new_binder(
            Self::new(sk_channel, ag_channel),
            binder::BinderFeatures::default(),
        )
    }
}

impl<T: SerializedChannel> binder::Interface for SecretkeeperService<T> {}

/// Implement the `ISecretkeeper` interface.
impl<T: SerializedChannel> ISecretkeeper for SecretkeeperService<T> {
    fn processSecretManagementRequest(&self, req: &[u8]) -> binder::Result<Vec<u8>> {
        let wrapper = PerformOpReq::SecretManagement(req.to_vec());
        let wrapper_data = wrapper.to_vec().map_err(failed_cbor)?;
        let rsp_data = self.channel.execute(&wrapper_data)?;
        let rsp = PerformOpResponse::from_slice(&rsp_data).map_err(failed_cbor)?;
        match rsp {
            PerformOpResponse::Success(PerformOpSuccessRsp::ProtectedResponse(data)) => Ok(data),
            PerformOpResponse::Success(_) => Err(unexpected_response_type()),
            PerformOpResponse::Failure(err) => Err(service_specific_error(err)),
        }
    }

    fn getAuthGraphKe(&self) -> binder::Result<binder::Strong<dyn IAuthGraphKeyExchange>> {
        Ok(self.authgraph.clone())
    }

    fn deleteIds(&self, ids: &[AidlSecretId]) -> binder::Result<()> {
        let ids: Vec<SecretId> = ids
            .iter()
            .map(|id| SecretId::try_from(id.id.as_slice()))
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_e| {
                binder::Status::new_exception(
                    binder::ExceptionCode::ILLEGAL_ARGUMENT,
                    Some(&std::ffi::CString::new("secret ID of wrong length".to_string()).unwrap()),
                )
            })?;
        let wrapper = PerformOpReq::DeleteIds(ids);
        let wrapper_data = wrapper.to_vec().map_err(failed_cbor)?;
        let rsp_data = self.channel.execute(&wrapper_data)?;
        let rsp = PerformOpResponse::from_slice(&rsp_data).map_err(failed_cbor)?;
        match rsp {
            PerformOpResponse::Success(PerformOpSuccessRsp::Empty) => Ok(()),
            PerformOpResponse::Success(_) => Err(unexpected_response_type()),
            PerformOpResponse::Failure(err) => Err(service_specific_error(err)),
        }
    }

    fn deleteAll(&self) -> binder::Result<()> {
        let wrapper = PerformOpReq::DeleteAll;
        let wrapper_data = wrapper.to_vec().map_err(failed_cbor)?;
        let rsp_data = self.channel.execute(&wrapper_data)?;
        let rsp = PerformOpResponse::from_slice(&rsp_data).map_err(failed_cbor)?;
        match rsp {
            PerformOpResponse::Success(PerformOpSuccessRsp::Empty) => Ok(()),
            PerformOpResponse::Success(_) => Err(unexpected_response_type()),
            PerformOpResponse::Failure(err) => Err(service_specific_error(err)),
        }
    }
}

/// Emit a failure for a failed CBOR conversion.
fn failed_cbor(err: coset::CoseError) -> binder::Status {
    binder::Status::new_exception(
        binder::ExceptionCode::BAD_PARCELABLE,
        Some(&std::ffi::CString::new(format!("CBOR conversion failed: {err:?}")).unwrap()),
    )
}

/// Emit a service specific error.
fn service_specific_error(err: ApiError) -> binder::Status {
    binder::Status::new_service_specific_error(
        err.err_code as i32,
        Some(&CString::new(err.msg).unwrap()),
    )
}

/// Emit an error indicating an unexpected type of response received from the TA.
fn unexpected_response_type() -> binder::Status {
    service_specific_error(ApiError {
        err_code: AidlErrorCode::InternalError,
        msg: "unexpected response type".to_string(),
    })
}
