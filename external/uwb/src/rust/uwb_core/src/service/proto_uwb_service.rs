// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! This module provides a thin adapter of UwbService and UwbServiceCallback that encodes the
//! arguments to protobuf.

use log::{debug, error};
use protobuf::EnumOrUnknown;

use crate::error::{Error, Result};
use crate::params::{AppConfigParams, DeviceState, ReasonCode, SessionId, SessionState};
use crate::proto::bindings::{
    AndroidGetPowerStatsResponse, AndroidSetCountryCodeRequest, AndroidSetCountryCodeResponse,
    DeinitSessionRequest, DeinitSessionResponse, DisableResponse, EnableResponse,
    InitSessionRequest, InitSessionResponse, RangeDataReceivedSignal, ReconfigureRequest,
    ReconfigureResponse, SendVendorCmdRequest, SendVendorCmdResponse, ServiceResetSignal,
    SessionParamsRequest, SessionParamsResponse, SessionStateChangedSignal, SetLoggerModeRequest,
    SetLoggerModeResponse, StartRangingRequest, StartRangingResponse, Status as ProtoStatus,
    StopRangingRequest, StopRangingResponse, UciDeviceStatusChangedSignal,
    UpdateControllerMulticastListRequest, UpdateControllerMulticastListResponse,
    VendorNotificationReceivedSignal,
};
use crate::proto::utils::{parse_from_bytes, write_to_bytes};
use crate::service::uwb_service::{UwbService, UwbServiceCallback};
use crate::uci::notification::SessionRangeData;

/// A thin adapter of UwbService. The argument and the response of each method are protobuf-encoded
/// buffer. The definition of the protobuf is at protos/uwb_core_protos.proto.
///
/// For the naming of the protobuf struct, the argument of a method `do_something()` will be called
/// `DoSomethingRequest`, and the result will be called `DoSomethingResponse`.
pub struct ProtoUwbService {
    service: UwbService,
}

impl ProtoUwbService {
    /// Create a ProtoUwbService.
    pub fn new(service: UwbService) -> Self {
        Self { service }
    }

    /// Set UCI log mode.
    pub fn set_logger_mode(&self, request: &[u8]) -> Result<Vec<u8>> {
        let request = parse_from_bytes::<SetLoggerModeRequest>(request)?;
        let mut resp = SetLoggerModeResponse::new();
        let res = self.service.set_logger_mode(
            request
                .logger_mode
                .enum_value()
                .map_err(|e| {
                    error!("Failed to convert logger_mode: {e}");
                    Error::BadParameters
                })?
                .into(),
        );
        resp.status = Into::<crate::proto::bindings::Status>::into(res).into();
        write_to_bytes(&resp)
    }

    /// Enable the UWB service.
    pub fn enable(&self) -> Result<Vec<u8>> {
        let mut resp = EnableResponse::new();
        resp.status = EnumOrUnknown::new(self.service.enable().into());
        write_to_bytes(&resp)
    }

    /// Disable the UWB service.
    pub fn disable(&self) -> Result<Vec<u8>> {
        let mut resp = DisableResponse::new();
        resp.status = EnumOrUnknown::new(self.service.disable().into());
        write_to_bytes(&resp)
    }

    /// Initialize a new ranging session with the given parameters.
    ///
    /// Note: Currently the protobuf only support Fira parameters, but not support CCC parameters.
    pub fn init_session(&self, request: &[u8]) -> Result<Vec<u8>> {
        let mut request = parse_from_bytes::<InitSessionRequest>(request)?;
        let params = request
            .params
            .take()
            .ok_or_else(|| {
                error!("InitSessionRequest.params is empty");
                Error::BadParameters
            })?
            .try_into()
            .map_err(|e| {
                error!("Failed to convert to AppConfigParams: {}", e);
                Error::BadParameters
            })?;

        let mut resp = InitSessionResponse::new();
        resp.status = EnumOrUnknown::new(
            self.service
                .init_session(
                    request.session_id,
                    request
                        .session_type
                        .enum_value()
                        .map_err(|e| {
                            error!("Failed to convert session_type: {:?}", e);
                            Error::BadParameters
                        })?
                        .into(),
                    params,
                )
                .into(),
        );
        write_to_bytes(&resp)
    }

    /// Destroy the session.
    pub fn deinit_session(&self, request: &[u8]) -> Result<Vec<u8>> {
        let request = parse_from_bytes::<DeinitSessionRequest>(request)?;
        let mut resp = DeinitSessionResponse::new();
        resp.status = EnumOrUnknown::new(self.service.deinit_session(request.session_id).into());
        write_to_bytes(&resp)
    }

    /// Start ranging of the session.
    pub fn start_ranging(&self, request: &[u8]) -> Result<Vec<u8>> {
        let request = parse_from_bytes::<StartRangingRequest>(request)?;
        // Currently we only support FiRa session, not CCC. For FiRa session, the returned
        // AppConfigParams is the same as the configured one before start_ranging(). Therefore, we
        // don't reply the AppConfigParams received from uwb_core.
        let mut resp = StartRangingResponse::new();
        resp.status = EnumOrUnknown::new(self.service.start_ranging(request.session_id).into());
        write_to_bytes(&resp)
    }

    /// Stop ranging.
    pub fn stop_ranging(&self, request: &[u8]) -> Result<Vec<u8>> {
        let request = parse_from_bytes::<StopRangingRequest>(request)?;
        let mut resp = StopRangingResponse::new();
        resp.status = EnumOrUnknown::new(self.service.stop_ranging(request.session_id).into());
        write_to_bytes(&resp)
    }

    /// Reconfigure the parameters of the session.
    pub fn reconfigure(&self, request: &[u8]) -> Result<Vec<u8>> {
        let mut request = parse_from_bytes::<ReconfigureRequest>(request)?;
        let params = request
            .params
            .take()
            .ok_or_else(|| {
                error!("ReconfigureRequest.params is empty");
                Error::BadParameters
            })?
            .try_into()
            .map_err(|e| {
                error!("Failed to convert to AppConfigParams: {}", e);
                Error::BadParameters
            })?;

        let mut resp = ReconfigureResponse::new();
        resp.status =
            EnumOrUnknown::new(self.service.reconfigure(request.session_id, params).into());
        write_to_bytes(&resp)
    }

    /// Update the list of the controlees to the ongoing session.
    pub fn update_controller_multicast_list(&self, request: &[u8]) -> Result<Vec<u8>> {
        let request = parse_from_bytes::<UpdateControllerMulticastListRequest>(request)?;
        let mut controlees = vec![];
        for controlee in request.controlees.into_iter() {
            let controlee = controlee.try_into().map_err(|e| {
                error!("Failed to convert Controlee: {:?}", e);
                Error::BadParameters
            })?;
            controlees.push(controlee);
        }

        let mut resp = UpdateControllerMulticastListResponse::new();
        resp.status = EnumOrUnknown::new(
            self.service
                .update_controller_multicast_list(
                    request.session_id,
                    request
                        .action
                        .enum_value()
                        .map_err(|e| {
                            error!("Failed to convert action: {:?}", e);
                            Error::BadParameters
                        })?
                        .into(),
                    controlees,
                )
                .into(),
        );
        write_to_bytes(&resp)
    }

    /// Set the country code. Android-specific method.
    pub fn android_set_country_code(&self, request: &[u8]) -> Result<Vec<u8>> {
        let request = parse_from_bytes::<AndroidSetCountryCodeRequest>(request)?;
        let country_code = request.country_code.try_into()?;

        let mut resp = AndroidSetCountryCodeResponse::new();
        resp.status =
            EnumOrUnknown::new(self.service.android_set_country_code(country_code).into());
        write_to_bytes(&resp)
    }

    /// Get the power statistics. Android-specific method.
    pub fn android_get_power_stats(&self) -> Result<Vec<u8>> {
        let mut resp = AndroidGetPowerStatsResponse::new();
        match self.service.android_get_power_stats() {
            Ok(power_stats) => {
                resp.status = EnumOrUnknown::new(Ok(()).into());
                resp.power_stats = Some(power_stats.into()).into();
            }
            Err(e) => {
                let err: Result<()> = Err(e);
                resp.status = crate::proto::bindings::Status::from(err).into();
            }
        }
        write_to_bytes(&resp)
    }

    /// Send a raw UCI message.
    pub fn raw_uci_cmd(&self, request: &[u8]) -> Result<Vec<u8>> {
        let request = parse_from_bytes::<SendVendorCmdRequest>(request)?;
        let mut resp = SendVendorCmdResponse::new();
        match self.service.raw_uci_cmd(request.mt, request.gid, request.oid, request.payload) {
            Ok(msg) => {
                resp.status = EnumOrUnknown::new(Ok(()).into());
                resp.gid = msg.gid;
                resp.oid = msg.oid;
                resp.payload = msg.payload;
            }
            Err(e) => {
                let err: Result<()> = Err(e);
                resp.status = (Into::<crate::proto::bindings::Status>::into(err)).into();
            }
        }
        write_to_bytes(&resp)
    }

    /// Get app config params for the given session id
    pub fn session_params(&self, request: &[u8]) -> Result<Vec<u8>> {
        let request = parse_from_bytes::<SessionParamsRequest>(request)?;
        let mut resp = SessionParamsResponse::new();
        match self.service.session_params(request.session_id) {
            Ok(AppConfigParams::Fira(params)) => {
                resp.status =
                    EnumOrUnknown::from(Into::<crate::proto::bindings::Status>::into(Ok(())));
                resp.params = Some(params.into()).into();
            }
            Ok(params) => {
                error!("Received non-Fira session parameters: {:?}", params);
                resp.status = ProtoStatus::UNKNOWN.into();
            }
            Err(e) => {
                let err: Result<()> = Err(e);
                resp.status = Into::<crate::proto::bindings::Status>::into(err).into();
            }
        }
        write_to_bytes(&resp)
    }
}

/// The trait that provides the same callbacks of UwbServiceCallback. It has the blanket
/// implementation of UwbServiceCallback trait that converts the arguments to one protobuf-encoded
/// payload.
///
/// For the naming of the protobuf struct, the payload of a callback `on_something_happened()`
/// will be called `SomethingHappenedSignal`.
pub trait ProtoUwbServiceCallback: 'static {
    /// Notify the UWB service has been reset due to internal error. All the sessions are closed.
    fn on_service_reset(&mut self, payload: Vec<u8>);
    /// Notify the status of the UCI device.
    fn on_uci_device_status_changed(&mut self, payload: Vec<u8>);
    /// Notify the state of the session is changed.
    fn on_session_state_changed(&mut self, payload: Vec<u8>);
    /// Notify the ranging data of the session is received.
    fn on_range_data_received(&mut self, payload: Vec<u8>);
    /// Notify the vendor notification is received.
    fn on_vendor_notification_received(&mut self, payload: Vec<u8>);
}

impl<C: ProtoUwbServiceCallback> UwbServiceCallback for C {
    fn on_service_reset(&mut self, success: bool) {
        debug!("UwbService is reset, success: {}", success);
        let mut msg = ServiceResetSignal::new();
        msg.success = success;
        if let Ok(payload) = write_to_bytes(&msg) {
            ProtoUwbServiceCallback::on_service_reset(self, payload);
        } else {
            error!("Failed to call on_service_reset()");
        }
    }

    fn on_uci_device_status_changed(&mut self, state: DeviceState) {
        debug!("UCI device status is changed: {:?}", state);
        let mut msg = UciDeviceStatusChangedSignal::new();
        msg.state = EnumOrUnknown::new(state.into());
        if let Ok(payload) = write_to_bytes(&msg) {
            ProtoUwbServiceCallback::on_uci_device_status_changed(self, payload);
        } else {
            error!("Failed to call on_uci_device_status_changed()");
        }
    }

    fn on_session_state_changed(
        &mut self,
        session_id: SessionId,
        session_state: SessionState,
        reason_code: ReasonCode,
    ) {
        debug!(
            "Session {:?}'s state is changed to {:?}, reason: {:?}",
            session_id, session_state, reason_code
        );
        let mut msg = SessionStateChangedSignal::new();
        msg.session_id = session_id;
        msg.session_state = EnumOrUnknown::new(session_state.into());
        msg.reason_code = EnumOrUnknown::new(reason_code.into());
        if let Ok(payload) = write_to_bytes(&msg) {
            ProtoUwbServiceCallback::on_session_state_changed(self, payload);
        } else {
            error!("Failed to call on_session_state_changed()");
        }
    }

    fn on_range_data_received(&mut self, session_id: SessionId, range_data: SessionRangeData) {
        debug!("Received range data {:?} from Session {:?}", range_data, session_id);
        let mut msg = RangeDataReceivedSignal::new();
        msg.session_id = session_id;
        msg.range_data = Some(range_data.into()).into();
        if let Ok(payload) = write_to_bytes(&msg) {
            ProtoUwbServiceCallback::on_range_data_received(self, payload);
        } else {
            error!("Failed to call on_range_data_received()");
        }
    }

    fn on_vendor_notification_received(&mut self, gid: u32, oid: u32, payload: Vec<u8>) {
        debug!("Received vendor notification: gid={}, oid={}, payload={:?}", gid, oid, payload);
        let mut msg = VendorNotificationReceivedSignal::new();
        msg.gid = gid;
        msg.oid = oid;
        msg.payload = payload;
        if let Ok(payload) = write_to_bytes(&msg) {
            ProtoUwbServiceCallback::on_vendor_notification_received(self, payload);
        } else {
            error!("Failed to call on_vendor_notification_received()");
        }
    }
}
