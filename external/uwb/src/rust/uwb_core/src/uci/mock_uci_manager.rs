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

//! This module offers a mocked version of UciManager for testing.
//!
//! The mocked version of UciManager mimics the behavior of the UCI manager and
//! stacks below it, such that tests can be run on a target without the UWB
//! hardware.
use std::collections::VecDeque;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use async_trait::async_trait;
use tokio::sync::{mpsc, Notify};
use tokio::time::timeout;

use crate::error::{Error, Result};
use crate::params::uci_packets::{
    app_config_tlvs_eq, device_config_tlvs_eq, radar_config_tlvs_eq, AndroidRadarConfigResponse,
    AppConfigTlv, AppConfigTlvType, CapTlv, Controlees, CoreSetConfigResponse, CountryCode,
    DeviceConfigId, DeviceConfigTlv, GetDeviceInfoResponse, PhaseList, PowerStats, RadarConfigTlv,
    RadarConfigTlvType, RawUciMessage, ResetConfig, SessionId, SessionState, SessionToken,
    SessionType, SessionUpdateDtTagRangingRoundsResponse, SetAppConfigResponse,
    UpdateMulticastListAction, UpdateTime,
};
use crate::uci::notification::{
    CoreNotification, DataRcvNotification, RadarDataRcvNotification, SessionNotification,
    UciNotification,
};
use crate::uci::uci_logger::UciLoggerMode;
use crate::uci::uci_manager::UciManager;

#[derive(Clone)]
/// Mock version of UciManager for testing.
pub struct MockUciManager {
    expected_calls: Arc<Mutex<VecDeque<ExpectedCall>>>,
    expect_call_consumed: Arc<Notify>,
    core_notf_sender: mpsc::UnboundedSender<CoreNotification>,
    session_notf_sender: mpsc::UnboundedSender<SessionNotification>,
    vendor_notf_sender: mpsc::UnboundedSender<RawUciMessage>,
    data_rcv_notf_sender: mpsc::UnboundedSender<DataRcvNotification>,
    radar_data_rcv_notf_sender: mpsc::UnboundedSender<RadarDataRcvNotification>,
}

#[allow(dead_code)]
impl MockUciManager {
    /// Constructor.
    pub fn new() -> Self {
        Self {
            expected_calls: Default::default(),
            expect_call_consumed: Default::default(),
            core_notf_sender: mpsc::unbounded_channel().0,
            session_notf_sender: mpsc::unbounded_channel().0,
            vendor_notf_sender: mpsc::unbounded_channel().0,
            data_rcv_notf_sender: mpsc::unbounded_channel().0,
            radar_data_rcv_notf_sender: mpsc::unbounded_channel().0,
        }
    }

    /// Wait until expected calls are done.
    ///
    /// Returns false if calls are pending after 1 second.
    pub async fn wait_expected_calls_done(&mut self) -> bool {
        while !self.expected_calls.lock().unwrap().is_empty() {
            if timeout(Duration::from_secs(1), self.expect_call_consumed.notified()).await.is_err()
            {
                return false;
            }
        }
        true
    }

    /// Prepare Mock to expect for open_hal.
    ///
    /// MockUciManager expects call, returns out as response, followed by notfs sent.
    pub fn expect_open_hal(
        &mut self,
        notfs: Vec<UciNotification>,
        out: Result<GetDeviceInfoResponse>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::OpenHal { notfs, out });
    }

    /// Prepare Mock to expect for close_call.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_close_hal(&mut self, expected_force: bool, out: Result<()>) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::CloseHal { expected_force, out });
    }

    /// Prepare Mock to expect device_reset.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_device_reset(&mut self, expected_reset_config: ResetConfig, out: Result<()>) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::DeviceReset { expected_reset_config, out });
    }

    /// Prepare Mock to expect core_get_device_info.
    ///
    /// MockUciManager expects call, returns out as response.
    pub fn expect_core_get_device_info(&mut self, out: Result<GetDeviceInfoResponse>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::CoreGetDeviceInfo { out });
    }

    /// Prepare Mock to expect core_get_caps_info.
    ///
    /// MockUciManager expects call, returns out as response.
    pub fn expect_core_get_caps_info(&mut self, out: Result<Vec<CapTlv>>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::CoreGetCapsInfo { out });
    }

    /// Prepare Mock to expect core_set_config.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_core_set_config(
        &mut self,
        expected_config_tlvs: Vec<DeviceConfigTlv>,
        out: Result<CoreSetConfigResponse>,
    ) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::CoreSetConfig { expected_config_tlvs, out });
    }

    /// Prepare Mock to expect core_get_config.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_core_get_config(
        &mut self,
        expected_config_ids: Vec<DeviceConfigId>,
        out: Result<Vec<DeviceConfigTlv>>,
    ) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::CoreGetConfig { expected_config_ids, out });
    }

    /// Prepare Mock to expect core_query_uwb_timestamp.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_core_query_uwb_timestamp(&mut self, out: Result<u64>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::CoreQueryTimeStamp { out });
    }

    /// Prepare Mock to expect session_init.
    ///
    /// MockUciManager expects call with parameters, returns out as response, followed by notfs
    /// sent.
    pub fn expect_session_init(
        &mut self,
        expected_session_id: SessionId,
        expected_session_type: SessionType,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SessionInit {
            expected_session_id,
            expected_session_type,
            notfs,
            out,
        });
    }

    /// Prepare Mock to expect session_deinit.
    ///
    /// MockUciManager expects call with parameters, returns out as response, followed by notfs
    /// sent.
    pub fn expect_session_deinit(
        &mut self,
        expected_session_id: SessionId,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SessionDeinit {
            expected_session_id,
            notfs,
            out,
        });
    }

    /// Prepare Mock to expect session_set_app_config.
    ///
    /// MockUciManager expects call with parameters, returns out as response, followed by notfs
    /// sent.
    pub fn expect_session_set_app_config(
        &mut self,
        expected_session_id: SessionId,
        expected_config_tlvs: Vec<AppConfigTlv>,
        notfs: Vec<UciNotification>,
        out: Result<SetAppConfigResponse>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SessionSetAppConfig {
            expected_session_id,
            expected_config_tlvs,
            notfs,
            out,
        });
    }

    /// Prepare Mock to expect session_get_app_config.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_session_get_app_config(
        &mut self,
        expected_session_id: SessionId,
        expected_config_ids: Vec<AppConfigTlvType>,
        out: Result<Vec<AppConfigTlv>>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SessionGetAppConfig {
            expected_session_id,
            expected_config_ids,
            out,
        });
    }

    /// Prepare Mock to expect session_get_count.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_session_get_count(&mut self, out: Result<u8>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SessionGetCount { out });
    }

    /// Prepare Mock to expect session_get_state.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_session_get_state(
        &mut self,
        expected_session_id: SessionId,
        out: Result<SessionState>,
    ) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::SessionGetState { expected_session_id, out });
    }

    /// Prepare Mock to expect update_controller_multicast_list.
    ///
    /// MockUciManager expects call with parameters, returns out as response, followed by notfs
    /// sent.
    pub fn expect_session_update_controller_multicast_list(
        &mut self,
        expected_session_id: SessionId,
        expected_action: UpdateMulticastListAction,
        expected_controlees: Controlees,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(
            ExpectedCall::SessionUpdateControllerMulticastList {
                expected_session_id,
                expected_action,
                expected_controlees,
                notfs,
                out,
            },
        );
    }

    /// Prepare Mock to expect session_update_active_rounds_dt_tag.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_session_update_dt_tag_ranging_rounds(
        &mut self,
        expected_session_id: u32,
        expected_ranging_round_indexes: Vec<u8>,
        out: Result<SessionUpdateDtTagRangingRoundsResponse>,
    ) {
        self.expected_calls.lock().unwrap().push_back(
            ExpectedCall::SessionUpdateDtTagRangingRounds {
                expected_session_id,
                expected_ranging_round_indexes,
                out,
            },
        );
    }

    /// Prepare Mock to expect for session_query_max_data_size.
    ///
    /// MockUciManager expects call, returns out as response.
    pub fn expect_session_query_max_data_size(
        &mut self,
        expected_session_id: SessionId,
        out: Result<u16>,
    ) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::SessionQueryMaxDataSize { expected_session_id, out });
    }

    /// Prepare Mock to expect range_start.
    ///
    /// MockUciManager expects call with parameters, returns out as response, followed by notfs
    /// sent.
    pub fn expect_range_start(
        &mut self,
        expected_session_id: SessionId,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::RangeStart {
            expected_session_id,
            notfs,
            out,
        });
    }

    /// Prepare Mock to expect range_stop.
    ///
    /// MockUciManager expects call with parameters, returns out as response, followed by notfs
    /// sent.
    pub fn expect_range_stop(
        &mut self,
        expected_session_id: SessionId,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::RangeStop {
            expected_session_id,
            notfs,
            out,
        });
    }

    /// Prepare Mock to expect range_get_ranging_count.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_range_get_ranging_count(
        &mut self,
        expected_session_id: SessionId,
        out: Result<usize>,
    ) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::RangeGetRangingCount { expected_session_id, out });
    }

    /// Prepare Mock to expect android_set_country_code.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_android_set_country_code(
        &mut self,
        expected_country_code: CountryCode,
        out: Result<()>,
    ) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::AndroidSetCountryCode { expected_country_code, out });
    }

    /// Prepare Mock to expect android_set_country_code.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_android_get_power_stats(&mut self, out: Result<PowerStats>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::AndroidGetPowerStats { out });
    }

    /// Prepare Mock to expect android_set_radar_config.
    ///
    /// MockUciManager expects call with parameters, returns out as response, followed by notfs
    /// sent.
    pub fn expect_android_set_radar_config(
        &mut self,
        expected_session_id: SessionId,
        expected_config_tlvs: Vec<RadarConfigTlv>,
        notfs: Vec<UciNotification>,
        out: Result<AndroidRadarConfigResponse>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::AndroidSetRadarConfig {
            expected_session_id,
            expected_config_tlvs,
            notfs,
            out,
        });
    }

    /// Prepare Mock to expect android_get_app_config.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_android_get_radar_config(
        &mut self,
        expected_session_id: SessionId,
        expected_config_ids: Vec<RadarConfigTlvType>,
        out: Result<Vec<RadarConfigTlv>>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::AndroidGetRadarConfig {
            expected_session_id,
            expected_config_ids,
            out,
        });
    }

    /// Prepare Mock to expect raw_uci_cmd.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_raw_uci_cmd(
        &mut self,
        expected_mt: u32,
        expected_gid: u32,
        expected_oid: u32,
        expected_payload: Vec<u8>,
        out: Result<RawUciMessage>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::RawUciCmd {
            expected_mt,
            expected_gid,
            expected_oid,
            expected_payload,
            out,
        });
    }

    /// Prepare Mock to expect send_data_packet.
    ///
    /// MockUciManager expects call with parameters, returns out as response.
    pub fn expect_send_data_packet(
        &mut self,
        expected_session_id: SessionId,
        expected_address: Vec<u8>,
        expected_uci_sequence_num: u16,
        expected_app_payload_data: Vec<u8>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SendDataPacket {
            expected_session_id,
            expected_address,
            expected_uci_sequence_num,
            expected_app_payload_data,
            out,
        });
    }

    /// Prepare Mock to expect session_set_hybrid_config.
    ///
    /// MockUciManager expects call with parameters, returns out as response
    pub fn expect_session_set_hybrid_config(
        &mut self,
        expected_session_id: SessionId,
        expected_number_of_phases: u8,
        expected_update_time: UpdateTime,
        expected_phase_list: Vec<PhaseList>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SessionSetHybridConfig {
            expected_session_id,
            expected_number_of_phases,
            expected_update_time,
            expected_phase_list,
            out,
        });
    }

    /// Prepare Mock to expect session_data_transfer_phase_config
    /// MockUciManager expects call with parameters, returns out as response
    #[allow(clippy::too_many_arguments)]
    pub fn expect_session_data_transfer_phase_config(
        &mut self,
        expected_session_id: SessionId,
        expected_dtpcm_repetition: u8,
        expected_data_transfer_control: u8,
        expected_dtpml_size: u8,
        expected_mac_address: Vec<u8>,
        expected_slot_bitmap: Vec<u8>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(
            ExpectedCall::SessionDataTransferPhaseConfig {
                expected_session_id,
                expected_dtpcm_repetition,
                expected_data_transfer_control,
                expected_dtpml_size,
                expected_mac_address,
                expected_slot_bitmap,
                out,
            },
        );
    }

    /// Call Mock to send notifications.
    fn send_notifications(&self, notfs: Vec<UciNotification>) {
        for notf in notfs.into_iter() {
            match notf {
                UciNotification::Core(notf) => {
                    let _ = self.core_notf_sender.send(notf);
                }
                UciNotification::Session(notf) => {
                    let _ = self.session_notf_sender.send(notf);
                }
                UciNotification::Vendor(notf) => {
                    let _ = self.vendor_notf_sender.send(notf);
                }
            }
        }
    }
}

impl Default for MockUciManager {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl UciManager for MockUciManager {
    async fn set_logger_mode(&self, _logger_mode: UciLoggerMode) -> Result<()> {
        Ok(())
    }
    async fn set_core_notification_sender(
        &mut self,
        core_notf_sender: mpsc::UnboundedSender<CoreNotification>,
    ) {
        self.core_notf_sender = core_notf_sender;
    }
    async fn set_session_notification_sender(
        &mut self,
        session_notf_sender: mpsc::UnboundedSender<SessionNotification>,
    ) {
        self.session_notf_sender = session_notf_sender;
    }
    async fn set_vendor_notification_sender(
        &mut self,
        vendor_notf_sender: mpsc::UnboundedSender<RawUciMessage>,
    ) {
        self.vendor_notf_sender = vendor_notf_sender;
    }
    async fn set_data_rcv_notification_sender(
        &mut self,
        data_rcv_notf_sender: mpsc::UnboundedSender<DataRcvNotification>,
    ) {
        self.data_rcv_notf_sender = data_rcv_notf_sender;
    }
    async fn set_radar_data_rcv_notification_sender(
        &mut self,
        radar_data_rcv_notf_sender: mpsc::UnboundedSender<RadarDataRcvNotification>,
    ) {
        self.radar_data_rcv_notf_sender = radar_data_rcv_notf_sender;
    }

    async fn open_hal(&self) -> Result<GetDeviceInfoResponse> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::OpenHal { notfs, out }) => {
                self.expect_call_consumed.notify_one();
                self.send_notifications(notfs);
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn close_hal(&self, force: bool) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::CloseHal { expected_force, out }) if expected_force == force => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn device_reset(&self, reset_config: ResetConfig) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::DeviceReset { expected_reset_config, out })
                if expected_reset_config == reset_config =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn core_get_device_info(&self) -> Result<GetDeviceInfoResponse> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::CoreGetDeviceInfo { out }) => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn core_get_caps_info(&self) -> Result<Vec<CapTlv>> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::CoreGetCapsInfo { out }) => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn core_set_config(
        &self,
        config_tlvs: Vec<DeviceConfigTlv>,
    ) -> Result<CoreSetConfigResponse> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::CoreSetConfig { expected_config_tlvs, out })
                if device_config_tlvs_eq(&expected_config_tlvs, &config_tlvs) =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn core_get_config(
        &self,
        config_ids: Vec<DeviceConfigId>,
    ) -> Result<Vec<DeviceConfigTlv>> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::CoreGetConfig { expected_config_ids, out })
                if expected_config_ids == config_ids =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn core_query_uwb_timestamp(&self) -> Result<u64> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::CoreQueryTimeStamp { out }) => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn session_init(&self, session_id: SessionId, session_type: SessionType) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionInit {
                expected_session_id,
                expected_session_type,
                notfs,
                out,
            }) if expected_session_id == session_id && expected_session_type == session_type => {
                self.expect_call_consumed.notify_one();
                self.send_notifications(notfs);
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn session_deinit(&self, session_id: SessionId) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionDeinit { expected_session_id, notfs, out })
                if expected_session_id == session_id =>
            {
                self.expect_call_consumed.notify_one();
                self.send_notifications(notfs);
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn session_set_app_config(
        &self,
        session_id: SessionId,
        config_tlvs: Vec<AppConfigTlv>,
    ) -> Result<SetAppConfigResponse> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionSetAppConfig {
                expected_session_id,
                expected_config_tlvs,
                notfs,
                out,
            }) if expected_session_id == session_id
                && app_config_tlvs_eq(&expected_config_tlvs, &config_tlvs) =>
            {
                self.expect_call_consumed.notify_one();
                self.send_notifications(notfs);
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn session_get_app_config(
        &self,
        session_id: SessionId,
        config_ids: Vec<AppConfigTlvType>,
    ) -> Result<Vec<AppConfigTlv>> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionGetAppConfig {
                expected_session_id,
                expected_config_ids,
                out,
            }) if expected_session_id == session_id && expected_config_ids == config_ids => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn session_get_count(&self) -> Result<u8> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionGetCount { out }) => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn session_get_state(&self, session_id: SessionId) -> Result<SessionState> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionGetState { expected_session_id, out })
                if expected_session_id == session_id =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn session_update_controller_multicast_list(
        &self,
        session_id: SessionId,
        action: UpdateMulticastListAction,
        controlees: Controlees,
    ) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionUpdateControllerMulticastList {
                expected_session_id,
                expected_action,
                expected_controlees,
                notfs,
                out,
            }) if expected_session_id == session_id
                && expected_action == action
                && expected_controlees == controlees =>
            {
                self.expect_call_consumed.notify_one();
                self.send_notifications(notfs);
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn session_data_transfer_phase_config(
        &self,
        session_id: SessionId,
        dtpcm_repetition: u8,
        data_transfer_control: u8,
        dtpml_size: u8,
        mac_address: Vec<u8>,
        slot_bitmap: Vec<u8>,
    ) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionDataTransferPhaseConfig {
                expected_session_id,
                expected_dtpcm_repetition,
                expected_data_transfer_control,
                expected_dtpml_size,
                expected_mac_address,
                expected_slot_bitmap,
                out,
            }) if expected_session_id == session_id
                && expected_dtpcm_repetition == dtpcm_repetition
                && expected_data_transfer_control == data_transfer_control
                && expected_dtpml_size == dtpml_size
                && expected_mac_address == mac_address
                && expected_slot_bitmap == slot_bitmap =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn session_update_dt_tag_ranging_rounds(
        &self,
        session_id: u32,
        ranging_round_indexes: Vec<u8>,
    ) -> Result<SessionUpdateDtTagRangingRoundsResponse> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionUpdateDtTagRangingRounds {
                expected_session_id,
                expected_ranging_round_indexes,
                out,
            }) if expected_session_id == session_id
                && expected_ranging_round_indexes == ranging_round_indexes =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn session_query_max_data_size(&self, session_id: SessionId) -> Result<u16> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionQueryMaxDataSize { expected_session_id, out })
                if expected_session_id == session_id =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn range_start(&self, session_id: SessionId) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::RangeStart { expected_session_id, notfs, out })
                if expected_session_id == session_id =>
            {
                self.expect_call_consumed.notify_one();
                self.send_notifications(notfs);
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn range_stop(&self, session_id: SessionId) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::RangeStop { expected_session_id, notfs, out })
                if expected_session_id == session_id =>
            {
                self.expect_call_consumed.notify_one();
                self.send_notifications(notfs);
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn range_get_ranging_count(&self, session_id: SessionId) -> Result<usize> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::RangeGetRangingCount { expected_session_id, out })
                if expected_session_id == session_id =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn android_set_country_code(&self, country_code: CountryCode) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::AndroidSetCountryCode { expected_country_code, out })
                if expected_country_code == country_code =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn android_get_power_stats(&self) -> Result<PowerStats> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::AndroidGetPowerStats { out }) => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn android_set_radar_config(
        &self,
        session_id: SessionId,
        config_tlvs: Vec<RadarConfigTlv>,
    ) -> Result<AndroidRadarConfigResponse> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::AndroidSetRadarConfig {
                expected_session_id,
                expected_config_tlvs,
                notfs,
                out,
            }) if expected_session_id == session_id
                && radar_config_tlvs_eq(&expected_config_tlvs, &config_tlvs) =>
            {
                self.expect_call_consumed.notify_one();
                self.send_notifications(notfs);
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn android_get_radar_config(
        &self,
        session_id: SessionId,
        config_ids: Vec<RadarConfigTlvType>,
    ) -> Result<Vec<RadarConfigTlv>> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::AndroidGetRadarConfig {
                expected_session_id,
                expected_config_ids,
                out,
            }) if expected_session_id == session_id && expected_config_ids == config_ids => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn raw_uci_cmd(
        &self,
        mt: u32,
        gid: u32,
        oid: u32,
        payload: Vec<u8>,
    ) -> Result<RawUciMessage> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::RawUciCmd {
                expected_mt,
                expected_gid,
                expected_oid,
                expected_payload,
                out,
            }) if expected_mt == mt
                && expected_gid == gid
                && expected_oid == oid
                && expected_payload == payload =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn send_data_packet(
        &self,
        session_id: SessionId,
        address: Vec<u8>,
        uci_sequence_num: u16,
        app_payload_data: Vec<u8>,
    ) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SendDataPacket {
                expected_session_id,
                expected_address,
                expected_uci_sequence_num,
                expected_app_payload_data,
                out,
            }) if expected_session_id == session_id
                && expected_address == address
                && expected_uci_sequence_num == uci_sequence_num
                && expected_app_payload_data == app_payload_data =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn get_session_token_from_session_id(
        &self,
        _session_id: SessionId,
    ) -> Result<SessionToken> {
        Ok(1) // No uci call here, no mock required.
    }

    async fn session_set_hybrid_config(
        &self,
        session_id: SessionId,
        number_of_phases: u8,
        update_time: UpdateTime,
        phase_list: Vec<PhaseList>,
    ) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionSetHybridConfig {
                expected_session_id,
                expected_number_of_phases,
                expected_update_time,
                expected_phase_list,
                out,
            }) if expected_session_id == session_id
                && expected_number_of_phases == number_of_phases
                && expected_update_time == update_time
                && expected_phase_list.len() == phase_list.len()
                && expected_phase_list == phase_list =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }
}

#[derive(Clone)]
enum ExpectedCall {
    OpenHal {
        notfs: Vec<UciNotification>,
        out: Result<GetDeviceInfoResponse>,
    },
    CloseHal {
        expected_force: bool,
        out: Result<()>,
    },
    DeviceReset {
        expected_reset_config: ResetConfig,
        out: Result<()>,
    },
    CoreGetDeviceInfo {
        out: Result<GetDeviceInfoResponse>,
    },
    CoreGetCapsInfo {
        out: Result<Vec<CapTlv>>,
    },
    CoreSetConfig {
        expected_config_tlvs: Vec<DeviceConfigTlv>,
        out: Result<CoreSetConfigResponse>,
    },
    CoreGetConfig {
        expected_config_ids: Vec<DeviceConfigId>,
        out: Result<Vec<DeviceConfigTlv>>,
    },
    CoreQueryTimeStamp {
        out: Result<u64>,
    },
    SessionInit {
        expected_session_id: SessionId,
        expected_session_type: SessionType,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    },
    SessionDeinit {
        expected_session_id: SessionId,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    },
    SessionSetAppConfig {
        expected_session_id: SessionId,
        expected_config_tlvs: Vec<AppConfigTlv>,
        notfs: Vec<UciNotification>,
        out: Result<SetAppConfigResponse>,
    },
    SessionGetAppConfig {
        expected_session_id: SessionId,
        expected_config_ids: Vec<AppConfigTlvType>,
        out: Result<Vec<AppConfigTlv>>,
    },
    SessionGetCount {
        out: Result<u8>,
    },
    SessionGetState {
        expected_session_id: SessionId,
        out: Result<SessionState>,
    },
    SessionUpdateControllerMulticastList {
        expected_session_id: SessionId,
        expected_action: UpdateMulticastListAction,
        expected_controlees: Controlees,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    },
    SessionUpdateDtTagRangingRounds {
        expected_session_id: u32,
        expected_ranging_round_indexes: Vec<u8>,
        out: Result<SessionUpdateDtTagRangingRoundsResponse>,
    },
    SessionQueryMaxDataSize {
        expected_session_id: SessionId,
        out: Result<u16>,
    },
    RangeStart {
        expected_session_id: SessionId,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    },
    RangeStop {
        expected_session_id: SessionId,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    },
    RangeGetRangingCount {
        expected_session_id: SessionId,
        out: Result<usize>,
    },
    AndroidSetCountryCode {
        expected_country_code: CountryCode,
        out: Result<()>,
    },
    AndroidGetPowerStats {
        out: Result<PowerStats>,
    },
    AndroidSetRadarConfig {
        expected_session_id: SessionId,
        expected_config_tlvs: Vec<RadarConfigTlv>,
        notfs: Vec<UciNotification>,
        out: Result<AndroidRadarConfigResponse>,
    },
    AndroidGetRadarConfig {
        expected_session_id: SessionId,
        expected_config_ids: Vec<RadarConfigTlvType>,
        out: Result<Vec<RadarConfigTlv>>,
    },
    RawUciCmd {
        expected_mt: u32,
        expected_gid: u32,
        expected_oid: u32,
        expected_payload: Vec<u8>,
        out: Result<RawUciMessage>,
    },
    SendDataPacket {
        expected_session_id: SessionId,
        expected_address: Vec<u8>,
        expected_uci_sequence_num: u16,
        expected_app_payload_data: Vec<u8>,
        out: Result<()>,
    },
    SessionSetHybridConfig {
        expected_session_id: SessionId,
        expected_number_of_phases: u8,
        expected_update_time: UpdateTime,
        expected_phase_list: Vec<PhaseList>,
        out: Result<()>,
    },
    SessionDataTransferPhaseConfig {
        expected_session_id: SessionId,
        expected_dtpcm_repetition: u8,
        expected_data_transfer_control: u8,
        expected_dtpml_size: u8,
        expected_mac_address: Vec<u8>,
        expected_slot_bitmap: Vec<u8>,
        out: Result<()>,
    },
}
