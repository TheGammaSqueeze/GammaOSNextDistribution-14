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

use std::convert::TryInto;
use std::sync::Arc;
use std::time::Duration;

use async_trait::async_trait;
use log::{debug, error, info, warn};
use tokio::sync::{mpsc, oneshot, Mutex};

use crate::error::{Error, Result};
use crate::params::uci_packets::{
    AndroidRadarConfigResponse, AppConfigTlv, AppConfigTlvType, CapTlv, CapTlvType, Controlees,
    CoreSetConfigResponse, CountryCode, CreditAvailability, DeviceConfigId, DeviceConfigTlv,
    DeviceState, GetDeviceInfoResponse, GroupId, MessageType, PowerStats, RadarConfigTlv,
    RadarConfigTlvType, RawUciMessage, ResetConfig, SessionId, SessionState, SessionToken,
    SessionType, SessionUpdateDtTagRangingRoundsResponse, SetAppConfigResponse, UciDataPacket,
    UciDataPacketHal, UpdateMulticastListAction, UpdateTime,
};
use crate::params::utils::{bytes_to_u16, bytes_to_u64};
use crate::uci::command::UciCommand;
use crate::uci::message::UciMessage;
use crate::uci::notification::{
    CoreNotification, DataRcvNotification, RadarDataRcvNotification, SessionNotification,
    SessionRangeData, UciNotification,
};
use crate::uci::response::UciResponse;
use crate::uci::timeout_uci_hal::TimeoutUciHal;
use crate::uci::uci_hal::{UciHal, UciHalPacket};
use crate::uci::uci_logger::{UciLogger, UciLoggerMode, UciLoggerWrapper};
use crate::utils::{clean_mpsc_receiver, PinSleep};
use pdl_runtime::Packet;
use std::collections::{HashMap, VecDeque};
use uwb_uci_packets::{
    fragment_data_msg_send, PhaseList, RawUciControlPacket, UciDataSnd, UciDefragPacket,
};

// Conditional compilation: In production code, use the "prod" variant of the Aconfig flags
// library (this only allows reading the flags). When the same uci_manager.rs code is built
// for unit tests, use the "test" variant of the Aconfig flags library, so that the unit tests
// have the ability to set the flag value (and the changed value is visible in the code).
#[cfg(not(test))]
use uwb_aconfig_flags_rust::parse_cap_tlv_rust_uses_uwbs_uci_version;
#[cfg(test)]
use uwb_aconfig_flags_rust_test::parse_cap_tlv_rust_uses_uwbs_uci_version;

const UCI_TIMEOUT_MS: u64 = 800;
const MAX_RETRY_COUNT: usize = 3;
// Initialize to a safe (minimum) value for a Data packet fragment's payload size.
const MAX_DATA_PACKET_PAYLOAD_SIZE: usize = 255;

/// The UciManager organizes the state machine of the UWB HAL, and provides the interface which
/// abstracts the UCI commands, responses, and notifications.
#[async_trait]
pub trait UciManager: 'static + Send + Sync + Clone {
    async fn set_logger_mode(&self, logger_mode: UciLoggerMode) -> Result<()>;
    // Set the sendor of the UCI notificaions.
    async fn set_core_notification_sender(
        &mut self,
        core_notf_sender: mpsc::UnboundedSender<CoreNotification>,
    );
    async fn set_session_notification_sender(
        &mut self,
        session_notf_sender: mpsc::UnboundedSender<SessionNotification>,
    );
    async fn set_vendor_notification_sender(
        &mut self,
        vendor_notf_sender: mpsc::UnboundedSender<RawUciMessage>,
    );
    async fn set_data_rcv_notification_sender(
        &mut self,
        data_rcv_notf_sender: mpsc::UnboundedSender<DataRcvNotification>,
    );
    async fn set_radar_data_rcv_notification_sender(
        &mut self,
        radar_data_rcv_notf_sender: mpsc::UnboundedSender<RadarDataRcvNotification>,
    );

    // Open the UCI HAL.
    // All the UCI commands should be called after the open_hal() completes successfully.
    async fn open_hal(&self) -> Result<GetDeviceInfoResponse>;

    // Close the UCI HAL.
    async fn close_hal(&self, force: bool) -> Result<()>;

    // Send the standard UCI Commands.
    async fn device_reset(&self, reset_config: ResetConfig) -> Result<()>;
    async fn core_get_device_info(&self) -> Result<GetDeviceInfoResponse>;
    async fn core_get_caps_info(&self) -> Result<Vec<CapTlv>>;
    async fn core_set_config(
        &self,
        config_tlvs: Vec<DeviceConfigTlv>,
    ) -> Result<CoreSetConfigResponse>;
    async fn core_get_config(
        &self,
        config_ids: Vec<DeviceConfigId>,
    ) -> Result<Vec<DeviceConfigTlv>>;
    async fn core_query_uwb_timestamp(&self) -> Result<u64>;
    async fn session_init(&self, session_id: SessionId, session_type: SessionType) -> Result<()>;
    async fn session_deinit(&self, session_id: SessionId) -> Result<()>;
    async fn session_set_app_config(
        &self,
        session_id: SessionId,
        config_tlvs: Vec<AppConfigTlv>,
    ) -> Result<SetAppConfigResponse>;
    async fn session_get_app_config(
        &self,
        session_id: SessionId,
        config_ids: Vec<AppConfigTlvType>,
    ) -> Result<Vec<AppConfigTlv>>;
    async fn session_get_count(&self) -> Result<u8>;
    async fn session_get_state(&self, session_id: SessionId) -> Result<SessionState>;
    async fn session_update_controller_multicast_list(
        &self,
        session_id: SessionId,
        action: UpdateMulticastListAction,
        controlees: Controlees,
    ) -> Result<()>;

    // Update ranging rounds for DT Tag
    async fn session_update_dt_tag_ranging_rounds(
        &self,
        session_id: u32,
        ranging_round_indexes: Vec<u8>,
    ) -> Result<SessionUpdateDtTagRangingRoundsResponse>;

    async fn session_query_max_data_size(&self, session_id: SessionId) -> Result<u16>;

    async fn range_start(&self, session_id: SessionId) -> Result<()>;
    async fn range_stop(&self, session_id: SessionId) -> Result<()>;
    async fn range_get_ranging_count(&self, session_id: SessionId) -> Result<usize>;

    // Send the Android-specific UCI commands
    async fn android_set_country_code(&self, country_code: CountryCode) -> Result<()>;
    async fn android_get_power_stats(&self) -> Result<PowerStats>;
    async fn android_set_radar_config(
        &self,
        session_id: SessionId,
        config_tlvs: Vec<RadarConfigTlv>,
    ) -> Result<AndroidRadarConfigResponse>;
    async fn android_get_radar_config(
        &self,
        session_id: SessionId,
        config_ids: Vec<RadarConfigTlvType>,
    ) -> Result<Vec<RadarConfigTlv>>;

    // Send a raw uci command.
    async fn raw_uci_cmd(
        &self,
        mt: u32,
        gid: u32,
        oid: u32,
        payload: Vec<u8>,
    ) -> Result<RawUciMessage>;

    // Send a Data packet.
    async fn send_data_packet(
        &self,
        session_id: SessionId,
        address: Vec<u8>,
        uci_sequence_number: u16,
        app_payload_data: Vec<u8>,
    ) -> Result<()>;

    // set Data transfer phase config
    async fn session_data_transfer_phase_config(
        &self,
        session_id: SessionId,
        dtpcm_repetition: u8,
        data_transfer_control: u8,
        dtpml_size: u8,
        mac_address: Vec<u8>,
        slot_bitmap: Vec<u8>,
    ) -> Result<()>;

    // Get Session token from session id
    async fn get_session_token_from_session_id(
        &self,
        session_id: SessionId,
    ) -> Result<SessionToken>;
    /// Send UCI command for setting hybrid config
    async fn session_set_hybrid_config(
        &self,
        session_id: SessionId,
        number_of_phases: u8,
        update_time: UpdateTime,
        phase_list: Vec<PhaseList>,
    ) -> Result<()>;
}

/// UciManagerImpl is the main implementation of UciManager. Using the actor model, UciManagerImpl
/// delegates the requests to UciManagerActor.
#[derive(Clone)]
pub struct UciManagerImpl {
    cmd_sender: mpsc::UnboundedSender<(UciManagerCmd, oneshot::Sender<Result<UciResponse>>)>,

    // FIRA version 2 introduces a UWBS generated session handle to use as identifier for all
    // session related commands. This map stores the app provided session id to UWBS generated
    // session handle mapping if provided, else reuses session id.
    session_id_to_token_map: Arc<Mutex<HashMap<SessionId, SessionToken>>>,
}

impl UciManagerImpl {
    /// Constructor. Need to be called in an async context.
    pub(crate) fn new<T: UciHal, U: UciLogger>(
        hal: T,
        logger: U,
        logger_mode: UciLoggerMode,
    ) -> Self {
        let (cmd_sender, cmd_receiver) = mpsc::unbounded_channel();
        let session_id_to_token_map: Arc<Mutex<HashMap<SessionId, SessionToken>>> =
            Arc::new(Mutex::new(HashMap::new()));
        let mut actor = UciManagerActor::new(
            hal,
            logger,
            logger_mode,
            cmd_receiver,
            session_id_to_token_map.clone(),
        );
        tokio::spawn(async move { actor.run().await });

        Self { cmd_sender, session_id_to_token_map }
    }

    // Send the |cmd| to the UciManagerActor.
    async fn send_cmd(&self, cmd: UciManagerCmd) -> Result<UciResponse> {
        let (result_sender, result_receiver) = oneshot::channel();
        match self.cmd_sender.send((cmd, result_sender)) {
            Ok(()) => result_receiver.await.unwrap_or(Err(Error::Unknown)),
            Err(cmd) => {
                error!("Failed to send cmd: {:?}", cmd.0);
                Err(Error::Unknown)
            }
        }
    }

    async fn get_session_token(&self, session_id: &SessionId) -> Result<SessionToken> {
        self.session_id_to_token_map
            .lock()
            .await
            .get(session_id)
            .ok_or(Error::BadParameters)
            .copied()
    }
}

#[async_trait]
impl UciManager for UciManagerImpl {
    async fn set_logger_mode(&self, logger_mode: UciLoggerMode) -> Result<()> {
        match self.send_cmd(UciManagerCmd::SetLoggerMode { logger_mode }).await {
            Ok(UciResponse::SetLoggerMode) => Ok(()),
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }
    async fn set_core_notification_sender(
        &mut self,
        core_notf_sender: mpsc::UnboundedSender<CoreNotification>,
    ) {
        let _ = self.send_cmd(UciManagerCmd::SetCoreNotificationSender { core_notf_sender }).await;
    }
    async fn set_session_notification_sender(
        &mut self,
        session_notf_sender: mpsc::UnboundedSender<SessionNotification>,
    ) {
        let _ = self
            .send_cmd(UciManagerCmd::SetSessionNotificationSender { session_notf_sender })
            .await;
    }
    async fn set_vendor_notification_sender(
        &mut self,
        vendor_notf_sender: mpsc::UnboundedSender<RawUciMessage>,
    ) {
        let _ =
            self.send_cmd(UciManagerCmd::SetVendorNotificationSender { vendor_notf_sender }).await;
    }
    async fn set_data_rcv_notification_sender(
        &mut self,
        data_rcv_notf_sender: mpsc::UnboundedSender<DataRcvNotification>,
    ) {
        let _ = self
            .send_cmd(UciManagerCmd::SetDataRcvNotificationSender { data_rcv_notf_sender })
            .await;
    }
    async fn set_radar_data_rcv_notification_sender(
        &mut self,
        radar_data_rcv_notf_sender: mpsc::UnboundedSender<RadarDataRcvNotification>,
    ) {
        let _ = self
            .send_cmd(UciManagerCmd::SetRadarDataRcvNotificationSender {
                radar_data_rcv_notf_sender,
            })
            .await;
    }

    async fn open_hal(&self) -> Result<GetDeviceInfoResponse> {
        match self.send_cmd(UciManagerCmd::OpenHal).await {
            Ok(UciResponse::OpenHal) => {
                // According to the UCI spec: "The Host shall send CORE_GET_DEVICE_INFO_CMD to
                // retrieve the device information.", we call get_device_info() after successfully
                // opening the HAL.
                let device_info = match self.core_get_device_info().await {
                    Ok(resp) => resp,
                    Err(e) => {
                        return Err(e);
                    }
                };
                debug!("UCI device info: {:?}", device_info);

                Ok(device_info)
            }
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn close_hal(&self, force: bool) -> Result<()> {
        match self.send_cmd(UciManagerCmd::CloseHal { force }).await {
            Ok(UciResponse::CloseHal) => Ok(()),
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn device_reset(&self, reset_config: ResetConfig) -> Result<()> {
        let cmd = UciCommand::DeviceReset { reset_config };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::DeviceReset(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn core_get_device_info(&self) -> Result<GetDeviceInfoResponse> {
        let cmd = UciCommand::CoreGetDeviceInfo;
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::CoreGetDeviceInfo(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn core_get_caps_info(&self) -> Result<Vec<CapTlv>> {
        let cmd = UciCommand::CoreGetCapsInfo;
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::CoreGetCapsInfo(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn core_set_config(
        &self,
        config_tlvs: Vec<DeviceConfigTlv>,
    ) -> Result<CoreSetConfigResponse> {
        let cmd = UciCommand::CoreSetConfig { config_tlvs };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::CoreSetConfig(resp)) => Ok(resp),
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn core_get_config(&self, cfg_id: Vec<DeviceConfigId>) -> Result<Vec<DeviceConfigTlv>> {
        let cmd = UciCommand::CoreGetConfig { cfg_id };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::CoreGetConfig(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn core_query_uwb_timestamp(&self) -> Result<u64> {
        let cmd = UciCommand::CoreQueryTimeStamp;
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::CoreQueryTimeStamp(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn session_init(&self, session_id: SessionId, session_type: SessionType) -> Result<()> {
        let cmd = UciCommand::SessionInit { session_id, session_type };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionInit(resp)) => resp.map(|_| {}),
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn session_deinit(&self, session_id: SessionId) -> Result<()> {
        let cmd =
            UciCommand::SessionDeinit { session_token: self.get_session_token(&session_id).await? };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionDeinit(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn session_set_app_config(
        &self,
        session_id: SessionId,
        config_tlvs: Vec<AppConfigTlv>,
    ) -> Result<SetAppConfigResponse> {
        let cmd = UciCommand::SessionSetAppConfig {
            session_token: self.get_session_token(&session_id).await?,
            config_tlvs,
        };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionSetAppConfig(resp)) => Ok(resp),
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn session_get_app_config(
        &self,
        session_id: SessionId,
        app_cfg: Vec<AppConfigTlvType>,
    ) -> Result<Vec<AppConfigTlv>> {
        let cmd = UciCommand::SessionGetAppConfig {
            session_token: self.get_session_token(&session_id).await?,
            app_cfg,
        };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionGetAppConfig(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn session_get_count(&self) -> Result<u8> {
        let cmd = UciCommand::SessionGetCount;
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionGetCount(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn session_get_state(&self, session_id: SessionId) -> Result<SessionState> {
        let cmd = UciCommand::SessionGetState {
            session_token: self.get_session_token(&session_id).await?,
        };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionGetState(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn session_update_controller_multicast_list(
        &self,
        session_id: SessionId,
        action: UpdateMulticastListAction,
        controlees: Controlees,
    ) -> Result<()> {
        let controlees_len = match controlees {
            Controlees::NoSessionKey(ref controlee_vec) => controlee_vec.len(),
            Controlees::ShortSessionKey(ref controlee_vec) => controlee_vec.len(),
            Controlees::LongSessionKey(ref controlee_vec) => controlee_vec.len(),
        };
        if !(1..=8).contains(&controlees_len) {
            warn!("Number of controlees should be between 1 to 8");
            return Err(Error::BadParameters);
        }
        let cmd = UciCommand::SessionUpdateControllerMulticastList {
            session_token: self.get_session_token(&session_id).await?,
            action,
            controlees,
        };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionUpdateControllerMulticastList(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn session_update_dt_tag_ranging_rounds(
        &self,
        session_id: u32,
        ranging_round_indexes: Vec<u8>,
    ) -> Result<SessionUpdateDtTagRangingRoundsResponse> {
        let cmd = UciCommand::SessionUpdateDtTagRangingRounds {
            session_token: self.get_session_token(&session_id).await?,
            ranging_round_indexes,
        };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionUpdateDtTagRangingRounds(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn session_query_max_data_size(&self, session_id: SessionId) -> Result<u16> {
        let cmd = UciCommand::SessionQueryMaxDataSize {
            session_token: self.get_session_token(&session_id).await?,
        };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionQueryMaxDataSize(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn range_start(&self, session_id: SessionId) -> Result<()> {
        let cmd =
            UciCommand::SessionStart { session_token: self.get_session_token(&session_id).await? };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionStart(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn range_stop(&self, session_id: SessionId) -> Result<()> {
        let cmd =
            UciCommand::SessionStop { session_token: self.get_session_token(&session_id).await? };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionStop(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn range_get_ranging_count(&self, session_id: SessionId) -> Result<usize> {
        let cmd = UciCommand::SessionGetRangingCount {
            session_token: self.get_session_token(&session_id).await?,
        };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionGetRangingCount(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn android_set_country_code(&self, country_code: CountryCode) -> Result<()> {
        let cmd = UciCommand::AndroidSetCountryCode { country_code };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::AndroidSetCountryCode(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn android_get_power_stats(&self) -> Result<PowerStats> {
        let cmd = UciCommand::AndroidGetPowerStats;
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::AndroidGetPowerStats(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn android_set_radar_config(
        &self,
        session_id: SessionId,
        config_tlvs: Vec<RadarConfigTlv>,
    ) -> Result<AndroidRadarConfigResponse> {
        let cmd = UciCommand::AndroidSetRadarConfig {
            session_token: self.get_session_token(&session_id).await?,
            config_tlvs,
        };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::AndroidSetRadarConfig(resp)) => Ok(resp),
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn android_get_radar_config(
        &self,
        session_id: SessionId,
        radar_cfg: Vec<RadarConfigTlvType>,
    ) -> Result<Vec<RadarConfigTlv>> {
        let cmd = UciCommand::AndroidGetRadarConfig {
            session_token: self.get_session_token(&session_id).await?,
            radar_cfg,
        };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::AndroidGetRadarConfig(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    async fn raw_uci_cmd(
        &self,
        mt: u32,
        gid: u32,
        oid: u32,
        payload: Vec<u8>,
    ) -> Result<RawUciMessage> {
        let cmd = UciCommand::RawUciCmd { mt, gid, oid, payload };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::RawUciCmd(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    // Send a data packet to the UWBS (use the UciManagerActor).
    async fn send_data_packet(
        &self,
        session_id: SessionId,
        dest_mac_address_bytes: Vec<u8>,
        uci_sequence_number: u16,
        data: Vec<u8>,
    ) -> Result<()> {
        debug!(
            "send_data_packet(): will Tx a data packet, session_id {}, sequence_number {}",
            session_id, uci_sequence_number
        );
        let dest_mac_address = bytes_to_u64(dest_mac_address_bytes).ok_or(Error::BadParameters)?;
        let data_snd_packet = uwb_uci_packets::UciDataSndBuilder {
            session_token: self.get_session_token(&session_id).await?,
            dest_mac_address,
            uci_sequence_number,
            data,
        }
        .build();

        match self.send_cmd(UciManagerCmd::SendUciData { data_snd_packet }).await {
            Ok(UciResponse::SendUciData(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    // set Data transfer phase config
    async fn session_data_transfer_phase_config(
        &self,
        session_id: SessionId,
        dtpcm_repetition: u8,
        data_transfer_control: u8,
        dtpml_size: u8,
        mac_address: Vec<u8>,
        slot_bitmap: Vec<u8>,
    ) -> Result<()> {
        let cmd = UciCommand::SessionDataTransferPhaseConfig {
            session_token: self.get_session_token(&session_id).await?,
            dtpcm_repetition,
            data_transfer_control,
            dtpml_size,
            mac_address,
            slot_bitmap,
        };

        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionDataTransferPhaseConfig(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }

    // Get session token from session id (no uci call).
    async fn get_session_token_from_session_id(
        &self,
        session_id: SessionId,
    ) -> Result<SessionToken> {
        Ok(self.get_session_token(&session_id).await?)
    }

    /// Send UCI command for setting hybrid config
    async fn session_set_hybrid_config(
        &self,
        session_id: SessionId,
        number_of_phases: u8,
        update_time: UpdateTime,
        phase_list: Vec<PhaseList>,
    ) -> Result<()> {
        let cmd = UciCommand::SessionSetHybridConfig {
            session_token: self.get_session_token(&session_id).await?,
            number_of_phases,
            update_time,
            phase_list,
        };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionSetHybridConfig(resp)) => resp,
            Ok(_) => Err(Error::Unknown),
            Err(e) => Err(e),
        }
    }
}

struct UciManagerActor<T: UciHal, U: UciLogger> {
    // The UCI HAL.
    hal: TimeoutUciHal<T>,
    // UCI Log.
    logger: UciLoggerWrapper<U>,
    // Receive the commands and the corresponding response senders from UciManager.
    cmd_receiver: mpsc::UnboundedReceiver<(UciManagerCmd, oneshot::Sender<Result<UciResponse>>)>,

    // Set to true when |hal| is opened successfully.
    is_hal_opened: bool,
    // Receive response, notification and data packets from |mut hal|. Only used when |hal| is opened
    // successfully.
    packet_receiver: mpsc::UnboundedReceiver<UciHalPacket>,
    // Defrag the UCI packets.
    defrager: uwb_uci_packets::PacketDefrager,

    // The response sender of UciManager's open_hal() method. Used to wait for the device ready
    // notification.
    open_hal_result_sender: Option<oneshot::Sender<Result<UciResponse>>>,

    // Store per-session CreditAvailability. This should be initialized when a UWB session becomes
    // ACTIVE, and updated every time a Data packet fragment is sent or a DataCreditNtf is received.
    data_credit_map: HashMap<SessionToken, CreditAvailability>,

    // Store the Uci Data packet fragments to be sent to the UWBS, keyed by the SessionId. This
    // helps to retrieve the next packet fragment to be sent, when the UWBS is ready to accept it.
    data_packet_fragments_map: HashMap<SessionToken, VecDeque<UciDataPacketHal>>,

    // The timeout of waiting for the notification of device ready notification.
    wait_device_status_timeout: PinSleep,

    // Used for the logic of retrying the command. Only valid when waiting for the response of a
    // UCI command.
    uci_cmd_retryer: Option<UciCmdRetryer>,
    // The timeout of waiting for the response. Only used when waiting for the response of a UCI
    // command.
    wait_resp_timeout: PinSleep,

    // Used for the logic of retrying the DataSnd packet. Only valid when waiting for the
    // DATA_TRANSFER_STATUS_NTF.
    uci_data_snd_retryer: Option<UciDataSndRetryer>,

    // Used to identify if response corresponds to the last vendor command, if so return
    // a raw packet as a response to the sender.
    last_raw_cmd: Option<RawUciControlPacket>,

    // Send the notifications to the caller of UciManager.
    core_notf_sender: mpsc::UnboundedSender<CoreNotification>,
    session_notf_sender: mpsc::UnboundedSender<SessionNotification>,
    vendor_notf_sender: mpsc::UnboundedSender<RawUciMessage>,
    data_rcv_notf_sender: mpsc::UnboundedSender<DataRcvNotification>,
    radar_data_rcv_notf_sender: mpsc::UnboundedSender<RadarDataRcvNotification>,

    // Used to store the last init session id to help map the session handle sent
    // in session int response can be correctly mapped.
    last_init_session_id: Option<SessionId>,
    // FIRA version 2 introduces a UWBS generated session handle to use as identifier for all
    // session related commands. This map stores the app provided session id to UWBS generated
    // session handle mapping if provided, else reuses session id.
    session_id_to_token_map: Arc<Mutex<HashMap<SessionId, SessionToken>>>,

    // Used to store the UWBS response for the UCI CMD CORE_GET_DEVICE_INFO. This will help us
    // identify the UWBS supported UCI version and change our behavior accordingly.
    get_device_info_rsp: Option<GetDeviceInfoResponse>,

    // The maximum payload size that can be sent in one Data packet fragment to the UWBS. The UCI
    // DATA_MSG_SEND packets (from Host to UWBS), larger than this should be fragmented into
    // multiple packets with this as the payload size.
    max_data_packet_payload_size: usize,
}

impl<T: UciHal, U: UciLogger> UciManagerActor<T, U> {
    fn new(
        hal: T,
        logger: U,
        logger_mode: UciLoggerMode,
        cmd_receiver: mpsc::UnboundedReceiver<(
            UciManagerCmd,
            oneshot::Sender<Result<UciResponse>>,
        )>,
        session_id_to_token_map: Arc<Mutex<HashMap<SessionId, SessionToken>>>,
    ) -> Self {
        Self {
            hal: TimeoutUciHal::new(hal),
            logger: UciLoggerWrapper::new(logger, logger_mode),
            cmd_receiver,
            is_hal_opened: false,
            packet_receiver: mpsc::unbounded_channel().1,
            defrager: Default::default(),
            open_hal_result_sender: None,
            data_credit_map: HashMap::new(),
            data_packet_fragments_map: HashMap::new(),
            wait_device_status_timeout: PinSleep::new(Duration::MAX),
            uci_cmd_retryer: None,
            uci_data_snd_retryer: None,
            wait_resp_timeout: PinSleep::new(Duration::MAX),
            last_raw_cmd: None,
            core_notf_sender: mpsc::unbounded_channel().0,
            session_notf_sender: mpsc::unbounded_channel().0,
            vendor_notf_sender: mpsc::unbounded_channel().0,
            data_rcv_notf_sender: mpsc::unbounded_channel().0,
            radar_data_rcv_notf_sender: mpsc::unbounded_channel().0,
            last_init_session_id: None,
            session_id_to_token_map,
            get_device_info_rsp: None,
            max_data_packet_payload_size: MAX_DATA_PACKET_PAYLOAD_SIZE,
        }
    }

    async fn run(&mut self) {
        loop {
            tokio::select! {
                // Handle the next command. Only when the previous command already received the
                // response.
                cmd = self.cmd_receiver.recv(), if !self.is_waiting_resp() => {
                    match cmd {
                        None => {
                            debug!("UciManager is about to drop.");
                            break;
                        },
                        Some((cmd, result_sender)) => {
                            self.handle_cmd(cmd, result_sender).await;
                        }
                    }
                }

                // Handle the UCI response, notification or data packet from HAL. Only when HAL
                // is opened.
                packet = self.packet_receiver.recv(), if self.is_hal_opened => {
                    self.handle_hal_packet(packet).await;
                }

                // Timeout waiting for the response of the UCI command.
                _ = &mut self.wait_resp_timeout, if self.is_waiting_resp() => {
                    if let Some(uci_cmd_retryer) = self.uci_cmd_retryer.take() {
                        uci_cmd_retryer.send_result(Err(Error::Timeout));
                    }
                }

                // Timeout waiting for the notification of the device status.
                _ = &mut self.wait_device_status_timeout, if self.is_waiting_device_status() => {
                    if let Some(result_sender) = self.open_hal_result_sender.take() {
                        let _ = result_sender.send(Err(Error::Timeout));
                    }
                }
            }
        }

        if self.is_hal_opened {
            debug!("The HAL is still opened when exit, close the HAL");
            let _ = self.hal.close().await;
            self.on_hal_closed().await;
        }
    }

    async fn insert_session_token(&self, session_id: SessionId, session_token: SessionToken) {
        self.session_id_to_token_map.lock().await.insert(session_id, session_token);
    }

    async fn remove_session_token(&self, session_token: &SessionToken) {
        self.session_id_to_token_map.lock().await.retain(|_, val| *val != *session_token);
    }

    async fn get_session_id(&self, session_token: &SessionToken) -> Result<SessionId> {
        self.session_id_to_token_map
            .lock()
            .await
            .iter()
            .find_map(|(key, &val)| if val == *session_token { Some(key) } else { None })
            .ok_or(Error::BadParameters)
            .copied()
    }

    fn save_session_id_if_init_cmd(&mut self, cmd: &UciCommand) {
        // Store the last init session id to help map the session handle sent
        // in session init response.
        if let UciCommand::SessionInit { session_id, .. } = cmd {
            self.last_init_session_id = Some(*session_id);
        }
    }

    async fn store_session_token_if_init_resp(&mut self, resp: &UciResponse) -> Result<()> {
        // Store the session_id to session_token mapping for this new session.
        if let UciResponse::SessionInit(session_init_resp) = resp {
            let session_id = match self.last_init_session_id.take() {
                Some(session_id) => session_id,
                None => {
                    return Err(Error::Unknown);
                }
            };
            if let Ok(opt_session_handle) = session_init_resp {
                let session_handle = match opt_session_handle {
                    // Session Handle provided by UWBS, use as token for further commands.
                    Some(session_handle) => {
                        info!(
                            "session handle: {:?} provided for session id: {:?}",
                            session_handle, session_id
                        );
                        *session_handle
                    }
                    // Session Handle not provided by UWBS, reuse session id as token for further commands.
                    None => session_id,
                };
                self.insert_session_token(session_id, session_handle).await;
            }
        }
        Ok(())
    }

    // Store the GET_DEVICE_INFO RSP from UWBS.
    fn store_if_uwbs_device_info(&mut self, resp: &UciResponse) {
        if let UciResponse::CoreGetDeviceInfo(Ok(get_device_info_rsp)) = resp {
            self.get_device_info_rsp = Some(get_device_info_rsp.clone());
        }
    }

    #[allow(unknown_lints)]
    #[allow(clippy::unnecessary_fallible_conversions)]
    fn store_if_uwbs_caps_info(&mut self, resp: &UciResponse) {
        if !parse_cap_tlv_rust_uses_uwbs_uci_version() {
            return;
        }

        if let UciResponse::CoreGetCapsInfo(Ok(tlvs)) = resp {
            if let Some(core_get_device_info_rsp) = &self.get_device_info_rsp {
                let major_uci_version = core_get_device_info_rsp.uci_version & 0xFF; // Byte 0
                let tlvtag = if major_uci_version >= 2 {
                    CapTlvType::SupportedV1FiraMacVersionRangeV2MaxDataPayloadSize
                } else {
                    CapTlvType::SupportedV1MaxDataPacketPayloadSizeV2AoaSupport
                };
                for tlv in tlvs {
                    if tlv.t == tlvtag {
                        // Convert the 2-byte UWBS capability value (stored as Vec<u8>) into usize.
                        self.max_data_packet_payload_size = match bytes_to_u16(tlv.v.clone()) {
                            Some(u16size) => match u16size.try_into() {
                                Ok(size) => size,
                                Err(_) => MAX_DATA_PACKET_PAYLOAD_SIZE,
                            },
                            None => MAX_DATA_PACKET_PAYLOAD_SIZE,
                        };
                    }
                }
            }
        }
    }

    async fn handle_cmd(
        &mut self,
        cmd: UciManagerCmd,
        result_sender: oneshot::Sender<Result<UciResponse>>,
    ) {
        debug!("Received cmd: {:?}", cmd);

        match cmd {
            UciManagerCmd::SetLoggerMode { logger_mode } => {
                self.logger.set_logger_mode(logger_mode);
                let _ = result_sender.send(Ok(UciResponse::SetLoggerMode));
            }
            UciManagerCmd::SetCoreNotificationSender { core_notf_sender } => {
                self.core_notf_sender = core_notf_sender;
                let _ = result_sender.send(Ok(UciResponse::SetNotification));
            }
            UciManagerCmd::SetSessionNotificationSender { session_notf_sender } => {
                self.session_notf_sender = session_notf_sender;
                let _ = result_sender.send(Ok(UciResponse::SetNotification));
            }
            UciManagerCmd::SetVendorNotificationSender { vendor_notf_sender } => {
                self.vendor_notf_sender = vendor_notf_sender;
                let _ = result_sender.send(Ok(UciResponse::SetNotification));
            }
            UciManagerCmd::SetDataRcvNotificationSender { data_rcv_notf_sender } => {
                self.data_rcv_notf_sender = data_rcv_notf_sender;
                let _ = result_sender.send(Ok(UciResponse::SetNotification));
            }
            UciManagerCmd::SetRadarDataRcvNotificationSender { radar_data_rcv_notf_sender } => {
                self.radar_data_rcv_notf_sender = radar_data_rcv_notf_sender;
                let _ = result_sender.send(Ok(UciResponse::SetNotification));
            }
            UciManagerCmd::OpenHal => {
                if self.is_hal_opened {
                    warn!("The UCI HAL is already opened, skip.");
                    let _ = result_sender.send(Err(Error::BadParameters));
                    return;
                }

                let (packet_sender, packet_receiver) = mpsc::unbounded_channel();
                let result = self.hal.open(packet_sender).await;
                self.logger.log_hal_open(&result);
                match result {
                    Ok(()) => {
                        self.on_hal_open(packet_receiver);
                        self.wait_device_status_timeout =
                            PinSleep::new(Duration::from_millis(UCI_TIMEOUT_MS));
                        self.open_hal_result_sender.replace(result_sender);
                    }
                    Err(e) => {
                        error!("Failed to open hal: {:?}", e);
                        let _ = result_sender.send(Err(e));
                    }
                }
            }

            UciManagerCmd::CloseHal { force } => {
                if force {
                    debug!("Force closing the UCI HAL");
                    let close_result = self.hal.close().await;
                    self.logger.log_hal_close(&close_result);
                    self.on_hal_closed().await;
                    let _ = result_sender.send(Ok(UciResponse::CloseHal));
                } else {
                    if !self.is_hal_opened {
                        warn!("The UCI HAL is already closed, skip.");
                        let _ = result_sender.send(Err(Error::BadParameters));
                        return;
                    }

                    let result = self.hal.close().await;
                    self.logger.log_hal_close(&result);
                    if result.is_ok() {
                        self.on_hal_closed().await;
                    }
                    let _ = result_sender.send(result.map(|_| UciResponse::CloseHal));
                }
            }

            UciManagerCmd::SendUciCommand { cmd } => {
                debug_assert!(self.uci_cmd_retryer.is_none());

                self.save_session_id_if_init_cmd(&cmd);

                // Remember that this command is a raw UCI command, we'll use this later
                // to send a raw UCI response.
                if let UciCommand::RawUciCmd { mt: _, gid, oid, payload: _ } = cmd.clone() {
                    let gid_u8 = u8::try_from(gid);
                    if gid_u8.is_err() || GroupId::try_from(gid_u8.unwrap()).is_err() {
                        error!("Received an invalid GID={} for RawUciCmd", gid);
                        let _ = result_sender.send(Err(Error::BadParameters));
                        return;
                    }

                    let oid_u8 = u8::try_from(oid);
                    if oid_u8.is_err() {
                        error!("Received an invalid OID={} for RawUciCmd", oid);
                        let _ = result_sender.send(Err(Error::BadParameters));
                        return;
                    }
                    self.last_raw_cmd = Some(RawUciControlPacket {
                        mt: u8::from(MessageType::Command),
                        gid: gid_u8.unwrap(), // Safe as we check gid_u8.is_err() above.
                        oid: oid_u8.unwrap(), // Safe as we check uid_i8.is_err() above.
                        payload: Vec::new(),  // There's no need to store the Raw UCI CMD's payload.
                    });
                }

                self.uci_cmd_retryer =
                    Some(UciCmdRetryer { cmd, result_sender, retry_count: MAX_RETRY_COUNT });

                // Reset DataSndRetryer so if a CORE_GENERIC_ERROR_NTF with STATUS_UCI_PACKET_RETRY
                // is received, only this UCI CMD packet will be retried.
                let _ = self.uci_data_snd_retryer.take();

                self.retry_uci_cmd().await;
            }

            UciManagerCmd::SendUciData { data_snd_packet } => {
                let result = self.handle_data_snd_packet(data_snd_packet).await;
                let _ = result_sender.send(result);
            }
        }
    }

    async fn retry_uci_cmd(&mut self) {
        if let Some(mut uci_cmd_retryer) = self.uci_cmd_retryer.take() {
            if !uci_cmd_retryer.could_retry() {
                error!("Out of retries for Uci Cmd packet");
                uci_cmd_retryer.send_result(Err(Error::Timeout));
                return;
            }

            match self.send_uci_command(uci_cmd_retryer.cmd.clone()).await {
                Ok(_) => {
                    self.wait_resp_timeout = PinSleep::new(Duration::from_millis(UCI_TIMEOUT_MS));
                    self.uci_cmd_retryer = Some(uci_cmd_retryer);
                }
                Err(e) => {
                    error!("Uci Cmd send resulted in error:{}", e);
                    uci_cmd_retryer.send_result(Err(e));
                }
            }
        }
    }

    async fn retry_uci_data_snd(&mut self) {
        if let Some(mut uci_data_snd_retryer) = self.uci_data_snd_retryer.take() {
            let data_packet_session_token = uci_data_snd_retryer.data_packet_session_token;
            if !uci_data_snd_retryer.could_retry() {
                error!(
                    "Out of retries for Uci DataSnd packet, last DataSnd packet session_id:{}",
                    data_packet_session_token
                );
                return;
            }

            match self.hal.send_packet(uci_data_snd_retryer.data_packet.clone().to_vec()).await {
                Ok(_) => {
                    self.uci_data_snd_retryer = Some(uci_data_snd_retryer);
                }
                Err(e) => {
                    error!(
                        "DataSnd packet fragment session_id:{} retry failed with error:{}",
                        data_packet_session_token, e
                    );
                }
            }
        }
    }

    async fn send_uci_command(&mut self, cmd: UciCommand) -> Result<()> {
        if !self.is_hal_opened {
            warn!("The UCI HAL is already closed, skip.");
            return Err(Error::BadParameters);
        }
        let result = self.hal.send_command(cmd.clone()).await;
        if result.is_ok() {
            self.logger.log_uci_command(&cmd);
        }
        result
    }

    async fn handle_data_snd_packet(&mut self, data_snd_packet: UciDataSnd) -> Result<UciResponse> {
        // Verify that there's an entry for the Session in the CreditAvailability map.
        let data_packet_session_token = data_snd_packet.get_session_token();
        let data_packet_sequence_number = data_snd_packet.get_uci_sequence_number();

        if !self.data_credit_map.contains_key(&data_packet_session_token) {
            error!(
                "DataSnd packet session_token:{}, sequence_number:{} cannot be sent as unknown \
                credit availability for the session",
                data_packet_session_token, data_packet_sequence_number
            );
            return Err(Error::PacketTxError);
        }

        // Enqueue the data packet fragments, from the data packet to be sent to UWBS.
        let mut packet_fragments: Vec<UciDataPacketHal> =
            fragment_data_msg_send(data_snd_packet, self.max_data_packet_payload_size);
        if packet_fragments.is_empty() {
            error!(
                "DataSnd packet session_token:{}, sequence number:{} could not be split into fragments",
                data_packet_session_token, data_packet_sequence_number
            );
            return Err(Error::PacketTxError);
        }

        match self.data_packet_fragments_map.get_mut(&data_packet_session_token) {
            Some(q) => {
                for p in packet_fragments.drain(..) {
                    q.push_back(p);
                }
            }
            None => {
                error!(
                    "DataSnd packet fragments map not found for session_token:{}",
                    data_packet_session_token
                );
                return Err(Error::PacketTxError);
            }
        }

        self.send_data_packet_fragment(data_packet_session_token).await
    }

    async fn send_data_packet_fragment(
        &mut self,
        data_packet_session_token: SessionToken,
    ) -> Result<UciResponse> {
        // Check if a credit is available before sending this data packet fragment. If not, return
        // for now, and send this packet later when the credit becomes available (indicated by
        // receiving a DataCreditNtf).
        let credit = self.data_credit_map.get(&data_packet_session_token);
        if credit.is_none() {
            error!(
                "DataSnd packet fragment cannot be sent for session_token:{} as unknown \
                credit availability for the session",
                data_packet_session_token
            );
            return Err(Error::PacketTxError);
        }
        if credit == Some(&CreditAvailability::CreditNotAvailable) {
            return Ok(UciResponse::SendUciData(Ok(())));
        }

        // We have credit available, let's send the packet to UWBS.
        let hal_data_packet_fragment =
            match self.data_packet_fragments_map.get_mut(&data_packet_session_token) {
                Some(q) => {
                    match q.pop_front() {
                        Some(p) => p,
                        None => {
                            // No more packets left to send.
                            return Ok(UciResponse::SendUciData(Ok(())));
                        }
                    }
                }
                None => {
                    return Err(Error::PacketTxError);
                }
            };

        // Create and save a retryer for sending this data packet fragment.
        self.uci_data_snd_retryer = Some(UciDataSndRetryer {
            data_packet: hal_data_packet_fragment.clone(),
            data_packet_session_token,
            retry_count: MAX_RETRY_COUNT,
        });

        let result = self.hal.send_packet(hal_data_packet_fragment.to_vec()).await;
        if result.is_err() {
            error!(
                "Result {:?} of sending data packet fragment SessionToken: {} to HAL",
                result, data_packet_session_token
            );
            return Err(Error::PacketTxError);
        }

        // Update the map after the successful write.
        self.data_credit_map
            .insert(data_packet_session_token, CreditAvailability::CreditNotAvailable);
        Ok(UciResponse::SendUciData(Ok(())))
    }

    async fn handle_hal_packet(&mut self, packet: Option<UciHalPacket>) {
        let defrag_packet = match packet {
            Some(rx_packet) => {
                self.defrager.defragment_packet(&rx_packet, self.last_raw_cmd.clone())
            }
            None => {
                warn!("UciHal dropped the packet_sender unexpectedly.");
                self.on_hal_closed().await;
                return;
            }
        };
        let defrag_packet = match defrag_packet {
            Some(p) => p,
            None => return,
        };

        match defrag_packet {
            UciDefragPacket::Control(packet) => {
                self.logger.log_uci_response_or_notification(&packet);

                match packet.try_into() {
                    Ok(UciMessage::Response(resp)) => {
                        self.handle_response(resp).await;
                    }
                    Ok(UciMessage::Notification(notf)) => {
                        self.handle_notification(notf).await;
                    }
                    Err(e) => {
                        error!("Failed to parse received message: {:?}", e);
                    }
                }
            }
            UciDefragPacket::Data(packet) => {
                self.logger.log_uci_data(&packet);
                self.handle_data_rcv(packet).await;
            }
            UciDefragPacket::Raw(result, raw_uci_control_packet) => {
                // Handle response to raw UCI cmd. We want to send it back as
                // raw UCI message instead of standard response message.
                let resp = match result {
                    Ok(()) => {
                        // We should receive only a valid UCI response packet here.
                        UciResponse::RawUciCmd(Ok(RawUciMessage {
                            gid: raw_uci_control_packet.gid.into(),
                            oid: raw_uci_control_packet.oid.into(),
                            payload: raw_uci_control_packet.payload,
                        }))
                    }
                    // TODO: Implement conversion between Error::InvalidPacketError (returned by
                    // lib.rs and defined in the PDL uci_packets.rs) and the uwb_core::Error enums.
                    Err(_) => UciResponse::RawUciCmd(Err(Error::Unknown)),
                };
                self.handle_response(resp).await;
                self.last_raw_cmd = None;
            }
        }
    }

    async fn handle_response(&mut self, resp: UciResponse) {
        if resp.need_retry() {
            self.retry_uci_cmd().await;
            return;
        }

        if let Err(_e) = self.store_session_token_if_init_resp(&resp).await {
            error!("Session init response received without a sesson id stored! Something has gone badly wrong: {:?}", resp);
            return;
        }
        self.store_if_uwbs_device_info(&resp);
        self.store_if_uwbs_caps_info(&resp);

        if let Some(uci_cmd_retryer) = self.uci_cmd_retryer.take() {
            uci_cmd_retryer.send_result(Ok(resp));
        } else {
            warn!("Received an UCI response unexpectedly: {:?}", resp);
        }
    }

    async fn handle_notification(&mut self, notf: UciNotification) {
        if notf.need_retry() {
            // Retry sending both last sent UCI CMD and UCI DataSnd packet since the notification
            // could be for either of them.
            self.retry_uci_cmd().await;
            self.retry_uci_data_snd().await;
            return;
        }

        match notf {
            UciNotification::Core(core_notf) => {
                if let CoreNotification::DeviceStatus(status) = core_notf {
                    if let Some(result_sender) = self.open_hal_result_sender.take() {
                        let result = match status {
                            DeviceState::DeviceStateReady | DeviceState::DeviceStateActive => {
                                Ok(UciResponse::OpenHal)
                            }
                            _ => Err(Error::Unknown),
                        };
                        let _ = result_sender.send(result);
                    }
                }
                let _ = self.core_notf_sender.send(core_notf);
            }
            UciNotification::Session(orig_session_notf) => {
                let mod_session_notf = {
                    match self
                        .replace_session_token_with_session_id(orig_session_notf.clone())
                        .await
                    {
                        Ok(session_notf) => session_notf,
                        Err(e) => {
                            error!("Failed to find corresponding session id, discarding session notification {:?}: {:?}", orig_session_notf, e);
                            return;
                        }
                    }
                };
                match orig_session_notf {
                    SessionNotification::Status {
                        session_token,
                        session_state,
                        reason_code: _,
                    } => self.handle_session_state_notification(session_token, session_state).await,
                    SessionNotification::DataCredit { session_token, credit_availability } => {
                        if !self.data_credit_map.contains_key(&session_token) {
                            // Currently just log, as this is unexpected (the entry should exist once
                            // the ranging session is Active and be removed once it is Idle).
                            debug!(
                                "Received a DataCreditNtf for non-existent session_token: {}",
                                session_token
                            );
                        }
                        self.data_credit_map.insert(session_token, credit_availability);
                        if credit_availability == CreditAvailability::CreditAvailable {
                            if let Err(e) = self.send_data_packet_fragment(session_token).await {
                                error!(
                                    "Sending data packet fragment failed with Err:{}, after a\
                                   DataCreditNtf is received, for session_token:{}",
                                    e, session_token
                                );
                            }
                        } else {
                            // Log as this should usually not happen (it's not an error).
                            debug!(
                            "Received a DataCreditNtf with no credit available for session_token:{}",
                            session_token
                        );
                        }
                        return; // We consume these here and don't need to send to upper layer.
                    }
                    SessionNotification::DataTransferStatus {
                        session_token: _,
                        uci_sequence_number: _,
                        status: _,
                        tx_count: _,
                    } => {
                        // Reset the UciDataSnd Retryer since we received a DataTransferStatusNtf.
                        let _ = self.uci_data_snd_retryer.take();
                    }
                    _ => {}
                }
                let _ = self.session_notf_sender.send(mod_session_notf);
            }
            UciNotification::Vendor(vendor_notf) => {
                let _ = self.vendor_notf_sender.send(vendor_notf);
            }
        }
    }

    // Modify session_token field in all session related notifications with session id.
    // TODO: Sharing of structs across UCI (PDL) & JNI layer like this makes this ugly. Ideally
    // the struct sent to JNI layer should only contain |session_id| and at uci layer
    // it could be |session_id| or |session_handle|.
    async fn replace_session_token_with_session_id(
        &self,
        session_notification: SessionNotification,
    ) -> Result<SessionNotification> {
        match session_notification {
            SessionNotification::Status { session_token, session_state, reason_code } => {
                Ok(SessionNotification::Status {
                    session_token: self.get_session_id(&session_token).await?,
                    session_state,
                    reason_code,
                })
            }
            SessionNotification::UpdateControllerMulticastList {
                session_token,
                remaining_multicast_list_size,
                status_list,
            } => Ok(SessionNotification::UpdateControllerMulticastList {
                session_token: self.get_session_id(&session_token).await?,
                remaining_multicast_list_size,
                status_list,
            }),
            SessionNotification::SessionInfo(session_range_data) => {
                Ok(SessionNotification::SessionInfo(SessionRangeData {
                    sequence_number: session_range_data.sequence_number,
                    session_token: self.get_session_id(&session_range_data.session_token).await?,
                    current_ranging_interval_ms: session_range_data.current_ranging_interval_ms,
                    ranging_measurement_type: session_range_data.ranging_measurement_type,
                    ranging_measurements: session_range_data.ranging_measurements,
                    rcr_indicator: session_range_data.rcr_indicator,
                    raw_ranging_data: session_range_data.raw_ranging_data,
                }))
            }
            SessionNotification::DataTransferStatus {
                session_token,
                uci_sequence_number,
                status,
                tx_count,
            } => Ok(SessionNotification::DataTransferStatus {
                session_token: self.get_session_id(&session_token).await?,
                uci_sequence_number,
                status,
                tx_count,
            }),
            SessionNotification::DataCredit { session_token, credit_availability } => {
                Ok(SessionNotification::DataCredit {
                    session_token: self.get_session_id(&session_token).await?,
                    credit_availability,
                })
            }
            SessionNotification::DataTransferPhaseConfig { session_token, status } => {
                Ok(SessionNotification::DataTransferPhaseConfig {
                    session_token: self.get_session_id(&session_token).await?,
                    status,
                })
            }
        }
    }

    async fn handle_session_state_notification(
        &mut self,
        session_token: SessionToken,
        session_state: SessionState,
    ) {
        match session_state {
            SessionState::SessionStateInit => {
                if let Err(e) = self.hal.notify_session_initialized(session_token).await {
                    warn!("notify_session_initialized() failed: {:?}", e);
                }
            }
            SessionState::SessionStateActive => {
                self.data_credit_map.insert(session_token, CreditAvailability::CreditAvailable);
                self.data_packet_fragments_map.insert(session_token, VecDeque::new());
            }
            SessionState::SessionStateIdle => {
                self.data_credit_map.remove(&session_token);
                self.data_packet_fragments_map.remove(&session_token);
            }
            SessionState::SessionStateDeinit => {
                self.remove_session_token(&session_token).await;
            }
        }
    }

    async fn handle_data_rcv(&mut self, packet: UciDataPacket) {
        if let Ok(data) = DataRcvNotification::try_from(packet.clone()) {
            match self.get_session_id(&data.session_token).await {
                Ok(session_id) => {
                    let _ = self.data_rcv_notf_sender.send(DataRcvNotification {
                        session_token: session_id,
                        status: data.status,
                        uci_sequence_num: data.uci_sequence_num,
                        source_address: data.source_address,
                        payload: data.payload,
                    });
                }
                Err(e) => {
                    error!("Unable to find session Id, error {:?}", e);
                }
            }
        } else if let Ok(data) = RadarDataRcvNotification::try_from(packet.clone()) {
            match self.get_session_id(&data.session_token).await {
                Ok(session_id) => {
                    let _ = self.radar_data_rcv_notf_sender.send(RadarDataRcvNotification {
                        session_token: session_id,
                        status: data.status,
                        radar_data_type: data.radar_data_type,
                        number_of_sweeps: data.number_of_sweeps,
                        samples_per_sweep: data.samples_per_sweep,
                        bits_per_sample: data.bits_per_sample,
                        sweep_offset: data.sweep_offset,
                        sweep_data: data.sweep_data,
                    });
                }
                Err(e) => {
                    error!("Unable to find session Id, error {:?}", e);
                }
            }
        } else {
            error!("Unable to parse incoming Data packet, packet {:?}", packet);
        }
    }

    fn on_hal_open(&mut self, packet_receiver: mpsc::UnboundedReceiver<UciHalPacket>) {
        self.is_hal_opened = true;
        self.packet_receiver = packet_receiver;
    }

    async fn on_hal_closed(&mut self) {
        self.session_id_to_token_map.lock().await.clear();
        self.is_hal_opened = false;
        self.packet_receiver = mpsc::unbounded_channel().1;
        self.last_raw_cmd = None;
    }

    fn is_waiting_resp(&self) -> bool {
        self.uci_cmd_retryer.is_some()
    }
    fn is_waiting_device_status(&self) -> bool {
        self.open_hal_result_sender.is_some()
    }
}

impl<T: UciHal, U: UciLogger> Drop for UciManagerActor<T, U> {
    fn drop(&mut self) {
        // mpsc receiver is about to be dropped. Clean shutdown the mpsc message.
        clean_mpsc_receiver(&mut self.packet_receiver);
    }
}

struct UciCmdRetryer {
    cmd: UciCommand,
    result_sender: oneshot::Sender<Result<UciResponse>>,
    retry_count: usize,
}

impl UciCmdRetryer {
    fn could_retry(&mut self) -> bool {
        if self.retry_count == 0 {
            return false;
        }
        self.retry_count -= 1;
        true
    }

    fn send_result(self, result: Result<UciResponse>) {
        let _ = self.result_sender.send(result);
    }
}

struct UciDataSndRetryer {
    // Store the last-sent DataSnd packet fragment across all the active UWB session, as the UCI
    // spec states that the "last UCI packet should be re-transmitted from Host".
    //
    // TODO(b/273376343): The spec is open to a race condition in the scenario of multiple active
    // sessions, as there can be outstanding DataSnd packet fragments across them. We could do an
    // alternative implementation of sending all of them.
    data_packet: UciDataPacketHal,
    data_packet_session_token: SessionToken,
    retry_count: usize,
}

impl UciDataSndRetryer {
    fn could_retry(&mut self) -> bool {
        if self.retry_count == 0 {
            return false;
        }
        self.retry_count -= 1;
        true
    }
}

#[derive(Debug)]
enum UciManagerCmd {
    SetLoggerMode {
        logger_mode: UciLoggerMode,
    },
    SetCoreNotificationSender {
        core_notf_sender: mpsc::UnboundedSender<CoreNotification>,
    },
    SetSessionNotificationSender {
        session_notf_sender: mpsc::UnboundedSender<SessionNotification>,
    },
    SetVendorNotificationSender {
        vendor_notf_sender: mpsc::UnboundedSender<RawUciMessage>,
    },
    SetDataRcvNotificationSender {
        data_rcv_notf_sender: mpsc::UnboundedSender<DataRcvNotification>,
    },
    SetRadarDataRcvNotificationSender {
        radar_data_rcv_notf_sender: mpsc::UnboundedSender<RadarDataRcvNotification>,
    },
    OpenHal,
    CloseHal {
        force: bool,
    },
    SendUciCommand {
        cmd: UciCommand,
    },
    SendUciData {
        data_snd_packet: UciDataSnd,
    },
}

#[cfg(test)]
mod tests {
    use super::*;

    use bytes::Bytes;
    use tokio::macros::support::Future;
    use uwb_uci_packets::{SessionGetCountCmdBuilder, SessionGetCountRspBuilder};

    use crate::params::uci_packets::{
        AppConfigStatus, AppConfigTlvType, BitsPerSample, CapTlvType, Controlee, DataRcvStatusCode,
        DataTransferNtfStatusCode, RadarDataType, StatusCode,
    };
    use crate::params::UwbAddress;
    use crate::uci::mock_uci_hal::MockUciHal;
    use crate::uci::mock_uci_logger::{MockUciLogger, UciLogEvent};
    use crate::uci::notification::CoreNotification;
    use crate::uci::notification::RadarSweepData;
    use crate::uci::uci_logger::NopUciLogger;
    use crate::utils::init_test_logging;
    use uwb_aconfig_flags_rust_test::set_parse_cap_tlv_rust_uses_uwbs_uci_version;

    fn into_uci_hal_packets<T: Into<uwb_uci_packets::UciControlPacket>>(
        builder: T,
    ) -> Vec<UciHalPacket> {
        let packets: Vec<uwb_uci_packets::UciControlPacketHal> = builder.into().into();
        packets.into_iter().map(|packet| packet.into()).collect()
    }

    // Construct a UCI packet, with the header fields and payload bytes.
    fn build_uci_packet(mt: u8, pbf: u8, gid: u8, oid: u8, mut payload: Vec<u8>) -> Vec<u8> {
        let len: u16 = payload.len() as u16;
        let mut bytes: Vec<u8> = vec![(mt & 0x7) << 5 | (pbf & 0x1) << 4 | (gid & 0xF), oid & 0x3F];
        if mt == 0 {
            // UCI Data packet
            // Store 16-bit payload length in LSB format.
            bytes.push((len & 0xFF).try_into().unwrap());
            bytes.push((len >> 8).try_into().unwrap());
        } else {
            // One byte RFU, followed by one-byte payload length.
            bytes.push(0);
            bytes.push((len & 0xFF).try_into().unwrap());
        }
        bytes.append(&mut payload);
        bytes
    }

    fn setup_hal_for_open(hal: &mut MockUciHal) {
        // Setup Open the hal.
        let notf = into_uci_hal_packets(uwb_uci_packets::DeviceStatusNtfBuilder {
            device_state: uwb_uci_packets::DeviceState::DeviceStateReady,
        });
        hal.expected_open(Some(notf), Ok(()));

        // Setup Get the device info.
        let cmd = UciCommand::CoreGetDeviceInfo;
        let resp = into_uci_hal_packets(uwb_uci_packets::GetDeviceInfoRspBuilder {
            status: uwb_uci_packets::StatusCode::UciStatusOk,
            uci_version: 0x1234,
            mac_version: 0x5678,
            phy_version: 0x90ab,
            uci_test_version: 0x1357,
            vendor_spec_info: vec![0x1, 0x2],
        });
        hal.expected_send_command(cmd, resp, Ok(()));
    }

    async fn setup_uci_manager_with_open_hal<F, Fut>(
        setup_hal_fn: F,
        uci_logger_mode: UciLoggerMode,
        log_sender: mpsc::UnboundedSender<UciLogEvent>,
    ) -> (UciManagerImpl, MockUciHal)
    where
        F: FnOnce(MockUciHal) -> Fut,
        Fut: Future<Output = ()>,
    {
        init_test_logging();

        let mut hal = MockUciHal::new();
        // Open the hal.
        setup_hal_for_open(&mut hal);

        // Verify open_hal() is working.
        let uci_manager =
            UciManagerImpl::new(hal.clone(), MockUciLogger::new(log_sender), uci_logger_mode);
        let result = uci_manager.open_hal().await;
        assert!(result.is_ok());
        assert!(hal.wait_expected_calls_done().await);

        setup_hal_fn(hal.clone()).await;

        (uci_manager, hal)
    }

    #[tokio::test]
    async fn test_open_hal_without_notification() {
        init_test_logging();

        let mut hal = MockUciHal::new();
        hal.expected_open(None, Ok(()));
        let uci_manager =
            UciManagerImpl::new(hal.clone(), NopUciLogger::default(), UciLoggerMode::Disabled);

        let result = uci_manager.open_hal().await;
        assert!(matches!(result, Err(Error::Timeout)));
        assert!(hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_close_hal_explicitly() {
        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                hal.expected_close(Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let result = uci_manager.close_hal(false).await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_close_hal_when_exit() {
        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                // UciManager should close the hal if the hal is still opened when exit.
                hal.expected_close(Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        drop(uci_manager);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_close_hal_without_open_hal() {
        init_test_logging();

        let mut hal = MockUciHal::new();
        let uci_manager =
            UciManagerImpl::new(hal.clone(), NopUciLogger::default(), UciLoggerMode::Disabled);

        let result = uci_manager.close_hal(false).await;
        assert!(matches!(result, Err(Error::BadParameters)));
        assert!(hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_device_reset_ok() {
        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::DeviceReset { reset_config: ResetConfig::UwbsReset };
                let resp = into_uci_hal_packets(uwb_uci_packets::DeviceResetRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                });
                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let result = uci_manager.device_reset(ResetConfig::UwbsReset).await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_priority_device_status_error_ntf() {
        // Send DEVICE_STATE_ERROR notification while waiting for remaining fragments,
        // verify that notification is processed on priority without waiting for the
        // further fragmen
        let mt: u8 = 0x3;
        let pbf_not_set: u8 = 0x00;
        let gid_core: u8 = 0x0;
        let oid_device_status: u8 = 0x1;
        let payload_1 = vec![0xFF];
        let pbf_set: u8 = 0x1;
        let gid_session: u8 = 0x02;
        let oid_session_ntf: u8 = 0x03;
        let payload_range_dat = vec![0, 251];
        let dev_state_err_packet =
            build_uci_packet(mt, pbf_not_set, gid_core, oid_device_status, payload_1);
        let range_data_ntf_packet =
            build_uci_packet(mt, pbf_set, gid_session, oid_session_ntf, payload_range_dat);
        let (mut uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |_| async move {},
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let (session_notification_sender, mut session_notification_receiver) =
            mpsc::unbounded_channel::<SessionNotification>();
        uci_manager.set_session_notification_sender(session_notification_sender).await;
        let result = mock_hal.receive_packet(range_data_ntf_packet);
        assert!(result.is_ok());

        let device_status_ntf_packet = uwb_uci_packets::DeviceStatusNtfBuilder {
            device_state: uwb_uci_packets::DeviceState::DeviceStateError,
        }
        .build();
        let core_notification =
            uwb_uci_packets::CoreNotification::try_from(device_status_ntf_packet).unwrap();
        let expected_uci_notification = CoreNotification::try_from(core_notification).unwrap();

        let (core_notification_sender, mut core_notification_receiver) =
            mpsc::unbounded_channel::<CoreNotification>();
        uci_manager.set_core_notification_sender(core_notification_sender).await;

        let result = mock_hal.receive_packet(dev_state_err_packet);
        assert!(result.is_ok());

        let result =
            tokio::time::timeout(Duration::from_millis(100), core_notification_receiver.recv())
                .await;
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), Some(expected_uci_notification));
        assert!(mock_hal.wait_expected_calls_done().await);

        // DEVICE_STATE_ERROR is received in middle while waiting for the fragmented packet,
        // no fragmented packet will be processed
        assert!(session_notification_receiver.try_recv().is_err());
    }

    #[tokio::test]
    async fn test_core_get_device_info_ok() {
        let status = StatusCode::UciStatusOk;
        let uci_version = 0x1234;
        let mac_version = 0x5678;
        let phy_version = 0x90ab;
        let uci_test_version = 0x1357;
        let vendor_spec_info = vec![0x1, 0x2];
        let vendor_spec_info_clone = vendor_spec_info.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::CoreGetDeviceInfo;
                let resp = into_uci_hal_packets(uwb_uci_packets::GetDeviceInfoRspBuilder {
                    status,
                    uci_version,
                    mac_version,
                    phy_version,
                    uci_test_version,
                    vendor_spec_info: vendor_spec_info_clone,
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let expected_result = GetDeviceInfoResponse {
            status,
            uci_version,
            mac_version,
            phy_version,
            uci_test_version,
            vendor_spec_info,
        };
        let result = uci_manager.core_get_device_info().await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_core_get_caps_info_fira_v1_0_ok() {
        let tlv = CapTlv {
            t: CapTlvType::SupportedV1FiraPhyVersionRangeV2MaxMessageSize,
            v: vec![0x12, 0x34, 0x56],
        };
        let tlv_clone = tlv.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::CoreGetCapsInfo;
                let resp = into_uci_hal_packets(uwb_uci_packets::GetCapsInfoRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                    tlvs: vec![tlv_clone],
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let result = uci_manager.core_get_caps_info().await.unwrap();
        assert_eq!(result[0], tlv);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_core_set_config_ok() {
        let tlv = DeviceConfigTlv {
            cfg_id: uwb_uci_packets::DeviceConfigId::DeviceState,
            v: vec![0x12, 0x34, 0x56],
        };
        let tlv_clone = tlv.clone();
        let status = StatusCode::UciStatusOk;
        let config_status = vec![];
        let config_status_clone = config_status.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::CoreSetConfig { config_tlvs: vec![tlv_clone] };
                let resp = into_uci_hal_packets(uwb_uci_packets::SetConfigRspBuilder {
                    status,
                    cfg_status: config_status_clone,
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let expected_result = CoreSetConfigResponse { status, config_status };
        let result = uci_manager.core_set_config(vec![tlv]).await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_core_get_config_ok() {
        let cfg_id = DeviceConfigId::DeviceState;
        let tlv = DeviceConfigTlv { cfg_id, v: vec![0x12, 0x34, 0x56] };
        let tlv_clone = tlv.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::CoreGetConfig { cfg_id: vec![cfg_id] };
                let resp = into_uci_hal_packets(uwb_uci_packets::GetConfigRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                    tlvs: vec![tlv_clone],
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let expected_result = vec![tlv];
        let result = uci_manager.core_get_config(vec![cfg_id]).await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    fn setup_hal_for_session_initialize(
        hal: &mut MockUciHal,
        session_type: SessionType,
        session_id: u32,
        session_token: u32,
    ) {
        // Setup for hal open.
        setup_hal_for_open(hal);

        // Setup session init.
        let cmd = UciCommand::SessionInit { session_id, session_type };
        let mut resp = if session_id == session_token {
            into_uci_hal_packets(uwb_uci_packets::SessionInitRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
            })
        } else {
            // This is testing FIRA v2 flow where a session handle is provided by UWBS.
            into_uci_hal_packets(uwb_uci_packets::SessionInitRsp_V2Builder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
                session_handle: session_token,
            })
        };
        let mut notf = into_uci_hal_packets(uwb_uci_packets::SessionStatusNtfBuilder {
            session_token,
            session_state: uwb_uci_packets::SessionState::SessionStateInit,
            reason_code: uwb_uci_packets::ReasonCode::StateChangeWithSessionManagementCommands
                .into(),
        });
        resp.append(&mut notf);
        hal.expected_send_command(cmd, resp, Ok(()));
        hal.expected_notify_session_initialized(session_token, Ok(()));
    }

    async fn setup_uci_manager_with_session_initialized<F, Fut>(
        setup_hal_fn: F,
        uci_logger_mode: UciLoggerMode,
        log_sender: mpsc::UnboundedSender<UciLogEvent>,
        session_id: u32,
        session_token: u32,
    ) -> (UciManagerImpl, MockUciHal)
    where
        F: FnOnce(MockUciHal) -> Fut,
        Fut: Future<Output = ()>,
    {
        let session_type = SessionType::FiraRangingSession;

        init_test_logging();

        let mut hal = MockUciHal::new();
        setup_hal_for_session_initialize(&mut hal, session_type, session_id, session_token);

        // Verify open_hal() is working.
        let uci_manager =
            UciManagerImpl::new(hal.clone(), MockUciLogger::new(log_sender), uci_logger_mode);
        let result = uci_manager.open_hal().await;
        assert!(result.is_ok());

        // Verify session is initialized.
        let result = uci_manager.session_init(session_id, session_type).await;
        assert!(result.is_ok());
        assert!(hal.wait_expected_calls_done().await);

        setup_hal_fn(hal.clone()).await;

        (uci_manager, hal)
    }

    #[tokio::test]
    async fn test_session_init_ok() {
        let session_id = 0x123;
        let session_token = 0x123;
        let (_, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |_hal| async move {},
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_init_v2_ok() {
        let session_id = 0x123;
        let session_token = 0x321; // different session handle
        let (_, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |_hal| async move {},
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_deinit_ok() {
        let session_id = 0x123;
        let session_token = 0x123;

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |mut hal| async move {
                let cmd = UciCommand::SessionDeinit { session_token };
                let resp = into_uci_hal_packets(uwb_uci_packets::SessionDeinitRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let result = uci_manager.session_deinit(session_id).await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_deinit_v2_ok() {
        let session_id = 0x123;
        let session_token = 0x321; // different session handle

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |mut hal| async move {
                let cmd = UciCommand::SessionDeinit { session_token };
                let resp = into_uci_hal_packets(uwb_uci_packets::SessionDeinitRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let result = uci_manager.session_deinit(session_id).await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_set_app_config_ok() {
        let session_id = 0x123;
        let session_token = 0x123;
        let config_tlv = AppConfigTlv::new(AppConfigTlvType::DeviceType, vec![0x12, 0x34, 0x56]);
        let config_tlv_clone = config_tlv.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |mut hal| async move {
                let cmd = UciCommand::SessionSetAppConfig {
                    session_token,
                    config_tlvs: vec![config_tlv_clone],
                };
                let resp = into_uci_hal_packets(uwb_uci_packets::SessionSetAppConfigRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                    cfg_status: vec![],
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let expected_result =
            SetAppConfigResponse { status: StatusCode::UciStatusOk, config_status: vec![] };
        let result =
            uci_manager.session_set_app_config(session_id, vec![config_tlv]).await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_set_app_config_v2_ok() {
        let session_id = 0x123;
        let session_token = 0x321;
        let config_tlv = AppConfigTlv::new(AppConfigTlvType::DeviceType, vec![0x12, 0x34, 0x56]);
        let config_tlv_clone = config_tlv.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |mut hal| async move {
                let cmd = UciCommand::SessionSetAppConfig {
                    session_token,
                    config_tlvs: vec![config_tlv_clone],
                };
                let resp = into_uci_hal_packets(uwb_uci_packets::SessionSetAppConfigRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                    cfg_status: vec![],
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let expected_result =
            SetAppConfigResponse { status: StatusCode::UciStatusOk, config_status: vec![] };
        let result =
            uci_manager.session_set_app_config(session_id, vec![config_tlv]).await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_set_hybrid_config_ok() {
        let session_id = 0x123;
        let session_token = 0x123;
        let number_of_phases = 0x02;
        let update_time = UpdateTime::new(&[0x0; 8]).unwrap();
        let phase_list = vec![
            PhaseList { session_token: 0x12, start_slot_index: 0x13, end_slot_index: 0x01 },
            PhaseList { session_token: 0x14, start_slot_index: 0x13, end_slot_index: 0x01 },
        ];
        let phase_list_clone = phase_list.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_active(
            |mut hal| async move {
                let cmd = UciCommand::SessionSetHybridConfig {
                    session_token,
                    number_of_phases,
                    update_time,
                    phase_list: phase_list_clone,
                };
                let resp =
                    into_uci_hal_packets(uwb_uci_packets::SessionSetHybridConfigRspBuilder {
                        status: uwb_uci_packets::StatusCode::UciStatusOk,
                    });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let result = uci_manager
            .session_set_hybrid_config(session_token, number_of_phases, update_time, phase_list)
            .await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_data_transfer_phase_config_ok() {
        let session_id = 0x123;
        let session_token = 0x123;
        let dtpcm_repetition = 0x00;
        let data_transfer_control = 0x00;
        let dtpml_size = 0x02;
        let mac_address = vec![0x22, 0x11, 0x44, 0x33];
        let slot_bitmap = vec![0xF0, 0x0F];
        let mac_address_clone = mac_address.clone();
        let slot_bitmap_clone = slot_bitmap.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_active(
            |mut hal| async move {
                let cmd = UciCommand::SessionDataTransferPhaseConfig {
                    session_token,
                    dtpcm_repetition,
                    data_transfer_control,
                    dtpml_size,
                    mac_address,
                    slot_bitmap,
                };
                let resp = into_uci_hal_packets(
                    uwb_uci_packets::SessionDataTransferPhaseConfigRspBuilder {
                        status: uwb_uci_packets::StatusCode::UciStatusOk,
                    },
                );

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let result = uci_manager
            .session_data_transfer_phase_config(
                session_token,
                dtpcm_repetition,
                data_transfer_control,
                dtpml_size,
                mac_address_clone,
                slot_bitmap_clone,
            )
            .await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_get_app_config_ok() {
        let session_id = 0x123;
        let session_token = 0x123;
        let config_id = AppConfigTlvType::DeviceType;
        let tlv = AppConfigTlv::new(AppConfigTlvType::DeviceType, vec![0x12, 0x34, 0x56]);
        let tlv_clone = tlv.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |mut hal| async move {
                let cmd =
                    UciCommand::SessionGetAppConfig { session_token, app_cfg: vec![config_id] };
                let resp = into_uci_hal_packets(uwb_uci_packets::SessionGetAppConfigRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                    tlvs: vec![tlv_clone.into_inner()],
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let expected_result = vec![tlv];
        let result = uci_manager.session_get_app_config(session_id, vec![config_id]).await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_get_count_ok() {
        let session_count = 5;

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::SessionGetCount;
                let resp = into_uci_hal_packets(uwb_uci_packets::SessionGetCountRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                    session_count,
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let result = uci_manager.session_get_count().await.unwrap();
        assert_eq!(result, session_count);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_get_state_ok() {
        let session_id = 0x123;
        let session_token = 0x123;
        let session_state = SessionState::SessionStateActive;

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |mut hal| async move {
                let cmd = UciCommand::SessionGetState { session_token };
                let resp = into_uci_hal_packets(uwb_uci_packets::SessionGetStateRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                    session_state,
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let result = uci_manager.session_get_state(session_id).await.unwrap();
        assert_eq!(result, session_state);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_update_controller_multicast_list_ok() {
        let session_id = 0x123;
        let session_token = 0x123;
        let action = UpdateMulticastListAction::AddControlee;
        let short_address: [u8; 2] = [0x45, 0x67];
        let controlee = Controlee { short_address, subsession_id: 0x90ab };
        let controlee_clone = controlee.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |mut hal| async move {
                let cmd = UciCommand::SessionUpdateControllerMulticastList {
                    session_token,
                    action,
                    controlees: Controlees::NoSessionKey(vec![controlee_clone]),
                };
                let resp = into_uci_hal_packets(
                    uwb_uci_packets::SessionUpdateControllerMulticastListRspBuilder {
                        status: uwb_uci_packets::StatusCode::UciStatusOk,
                    },
                );

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let result = uci_manager
            .session_update_controller_multicast_list(
                session_id,
                action,
                uwb_uci_packets::Controlees::NoSessionKey(vec![controlee]),
            )
            .await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_set_active_dt_tag_ranging_rounds() {
        let session_id = 0x123;
        let session_token = 0x123;

        let ranging_rounds = SessionUpdateDtTagRangingRoundsResponse {
            status: StatusCode::UciStatusErrorRoundIndexNotActivated,
            ranging_round_indexes: vec![3],
        };

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |mut hal| async move {
                let cmd = UciCommand::SessionUpdateDtTagRangingRounds {
                    session_token,
                    ranging_round_indexes: vec![3, 5],
                };
                let resp = into_uci_hal_packets(
                    uwb_uci_packets::SessionUpdateDtTagRangingRoundsRspBuilder {
                        status: StatusCode::UciStatusErrorRoundIndexNotActivated,
                        ranging_round_indexes: vec![3],
                    },
                );

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let result =
            uci_manager.session_update_dt_tag_ranging_rounds(session_id, vec![3, 5]).await.unwrap();

        assert_eq!(result, ranging_rounds);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_range_start_ok() {
        let session_id = 0x123;
        let session_token = 0x123;

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |mut hal| async move {
                let cmd = UciCommand::SessionStart { session_token };
                let resp = into_uci_hal_packets(uwb_uci_packets::SessionStartRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let result = uci_manager.range_start(session_id).await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_range_stop_ok() {
        let session_id = 0x123;
        let session_token = 0x123;

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |mut hal| async move {
                let cmd = UciCommand::SessionStop { session_token };
                let resp = into_uci_hal_packets(uwb_uci_packets::SessionStopRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let result = uci_manager.range_stop(session_id).await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_range_get_ranging_count_ok() {
        let session_id = 0x123;
        let session_token = 0x123;
        let count = 3;

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |mut hal| async move {
                let cmd = UciCommand::SessionGetRangingCount { session_token };
                let resp =
                    into_uci_hal_packets(uwb_uci_packets::SessionGetRangingCountRspBuilder {
                        status: uwb_uci_packets::StatusCode::UciStatusOk,
                        count,
                    });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let result = uci_manager.range_get_ranging_count(session_id).await.unwrap();
        assert_eq!(result, count as usize);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_android_set_country_code_ok() {
        let country_code = CountryCode::new(b"US").unwrap();
        let country_code_clone = country_code.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::AndroidSetCountryCode { country_code: country_code_clone };
                let resp = into_uci_hal_packets(uwb_uci_packets::AndroidSetCountryCodeRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let result = uci_manager.android_set_country_code(country_code).await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_android_get_power_stats_ok() {
        let power_stats = PowerStats {
            status: StatusCode::UciStatusOk,
            idle_time_ms: 123,
            tx_time_ms: 456,
            rx_time_ms: 789,
            total_wake_count: 5,
        };
        let power_stats_clone = power_stats.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::AndroidGetPowerStats;
                let resp = into_uci_hal_packets(uwb_uci_packets::AndroidGetPowerStatsRspBuilder {
                    stats: power_stats_clone,
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let result = uci_manager.android_get_power_stats().await.unwrap();
        assert_eq!(result, power_stats);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_android_set_radar_config_ok() {
        let session_id = 0x123;
        let session_token = 0x123;
        let config_tlv =
            RadarConfigTlv { cfg_id: RadarConfigTlvType::SamplesPerSweep, v: vec![0x12, 0x34] };
        let config_tlv_clone = config_tlv.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |mut hal| async move {
                let cmd = UciCommand::AndroidSetRadarConfig {
                    session_token,
                    config_tlvs: vec![config_tlv_clone],
                };
                let resp = into_uci_hal_packets(uwb_uci_packets::AndroidSetRadarConfigRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                    cfg_status: vec![],
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let expected_result =
            AndroidRadarConfigResponse { status: StatusCode::UciStatusOk, config_status: vec![] };
        let result =
            uci_manager.android_set_radar_config(session_id, vec![config_tlv]).await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_android_get_radar_config_ok() {
        let session_id = 0x123;
        let session_token = 0x123;
        let config_id = RadarConfigTlvType::SamplesPerSweep;
        let tlv =
            RadarConfigTlv { cfg_id: RadarConfigTlvType::SamplesPerSweep, v: vec![0x12, 0x34] };
        let tlv_clone = tlv.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_initialized(
            |mut hal| async move {
                let cmd =
                    UciCommand::AndroidGetRadarConfig { session_token, radar_cfg: vec![config_id] };
                let resp = into_uci_hal_packets(uwb_uci_packets::AndroidGetRadarConfigRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                    tlvs: vec![tlv_clone],
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let expected_result = vec![tlv];
        let result =
            uci_manager.android_get_radar_config(session_id, vec![config_id]).await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_raw_uci_cmd_vendor_gid_ok() {
        let mt = 0x1;
        let gid = 0xF; // Vendor reserved GID.
        let oid = 0x3;
        let cmd_payload = vec![0x11, 0x22, 0x33, 0x44];
        let cmd_payload_clone = cmd_payload.clone();
        let resp_payload = vec![0x55, 0x66, 0x77, 0x88];
        let resp_payload_clone = resp_payload.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::RawUciCmd { mt, gid, oid, payload: cmd_payload_clone };
                let resp = into_uci_hal_packets(uwb_uci_packets::UciVendor_F_ResponseBuilder {
                    opcode: oid as u8,
                    payload: Some(Bytes::from(resp_payload_clone)),
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let expected_result = RawUciMessage { gid, oid, payload: resp_payload };
        let result = uci_manager.raw_uci_cmd(mt, gid, oid, cmd_payload).await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_raw_uci_cmd_fira_gid_ok() {
        let mt = 0x1;
        let gid = 0x1; // SESSION_CONFIG GID.
        let oid = 0x3;
        let cmd_payload = vec![0x11, 0x22, 0x33, 0x44];
        let cmd_payload_clone = cmd_payload.clone();
        let resp_payload = vec![0x00, 0x01, 0x07, 0x00];
        let status = StatusCode::UciStatusOk;
        let cfg_id = AppConfigTlvType::DstMacAddress;
        let app_config = AppConfigStatus { cfg_id, status };
        let cfg_status = vec![app_config];

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::RawUciCmd { mt, gid, oid, payload: cmd_payload_clone };
                let resp = into_uci_hal_packets(uwb_uci_packets::SessionSetAppConfigRspBuilder {
                    status,
                    cfg_status,
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let expected_result = RawUciMessage { gid, oid, payload: resp_payload };
        let result = uci_manager.raw_uci_cmd(mt, gid, oid, cmd_payload).await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_raw_uci_cmd_undefined_mt_ok() {
        let mt = 0x4;
        let gid = 0x1; // SESSION_CONFIG GID.
        let oid = 0x3;
        let cmd_payload = vec![0x11, 0x22, 0x33, 0x44];
        let cmd_payload_clone = cmd_payload.clone();
        let resp_payload = vec![0x00, 0x01, 0x07, 0x00];
        let status = StatusCode::UciStatusOk;
        let cfg_id = AppConfigTlvType::DstMacAddress;
        let app_config = AppConfigStatus { cfg_id, status };
        let cfg_status = vec![app_config];

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::RawUciCmd { mt, gid, oid, payload: cmd_payload_clone };
                let resp = into_uci_hal_packets(uwb_uci_packets::SessionSetAppConfigRspBuilder {
                    status,
                    cfg_status,
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let expected_result = RawUciMessage { gid, oid, payload: resp_payload };
        let result = uci_manager.raw_uci_cmd(mt, gid, oid, cmd_payload).await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_raw_uci_cmd_custom_payload_format() {
        // Send a raw UCI command with a FiRa defined GID, OID (SESSION_SET_APP_CONFIG), and the
        // UCI HAL returns a UCI response with a custom payload format. The UCI response packet
        // should still be successfully parsed and returned, since it's a Raw UCI RSP.
        let cmd_mt: u8 = 0x1;
        let gid: u8 = 0x1; // Session Config.
        let oid: u8 = 0x3; // SESSION_SET_APP_CONFIG
        let cmd_payload = vec![0x11, 0x22, 0x33, 0x44];
        let cmd_payload_clone = cmd_payload.clone();
        let resp_mt: u8 = 0x2;
        let resp_payload = vec![0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08];
        let resp_payload_clone = resp_payload.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::RawUciCmd {
                    mt: cmd_mt.into(),
                    gid: gid.into(),
                    oid: oid.into(),
                    payload: cmd_payload_clone,
                };
                let resp = build_uci_packet(resp_mt, 0, gid, oid, resp_payload_clone);
                hal.expected_send_command(cmd, vec![resp], Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let expected_result =
            Ok(RawUciMessage { gid: gid.into(), oid: oid.into(), payload: resp_payload });
        let result =
            uci_manager.raw_uci_cmd(cmd_mt.into(), gid.into(), oid.into(), cmd_payload).await;
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_raw_uci_cmd_fragmented_responses() {
        // Send a raw UCI command with a FiRa defined GID, OID (SESSION_SET_APP_CONFIG), and the
        // UCI HAL returns a UCI response with a custom payload format, in 2 UCI packet fragments.
        let cmd_mt: u8 = 0x1;
        let gid: u8 = 0x1; // Session Config.
        let oid: u8 = 0x3; // SESSION_SET_APP_CONFIG
        let cmd_payload = vec![0x11, 0x22, 0x33, 0x44];
        let cmd_payload_clone = cmd_payload.clone();
        let resp_mt: u8 = 0x2;
        let resp_payload_fragment_1 = vec![0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08];
        let resp_payload_fragment_2 = vec![0x09, 0x0a, 0x0b];
        let mut resp_payload_expected = resp_payload_fragment_1.clone();
        resp_payload_expected.extend(resp_payload_fragment_2.clone());

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::RawUciCmd {
                    mt: cmd_mt.into(),
                    gid: gid.into(),
                    oid: oid.into(),
                    payload: cmd_payload_clone,
                };
                let resp_fragment_1 = build_uci_packet(
                    resp_mt,
                    /* pbf = */ 1,
                    gid,
                    oid,
                    resp_payload_fragment_1,
                );
                let resp_fragment_2 = build_uci_packet(
                    resp_mt,
                    /* pbf = */ 0,
                    gid,
                    oid,
                    resp_payload_fragment_2,
                );
                hal.expected_send_command(cmd, vec![resp_fragment_1, resp_fragment_2], Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let expected_result =
            Ok(RawUciMessage { gid: gid.into(), oid: oid.into(), payload: resp_payload_expected });
        let result =
            uci_manager.raw_uci_cmd(cmd_mt.into(), gid.into(), oid.into(), cmd_payload).await;
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_raw_uci_cmd_wrong_gid() {
        // Send a raw UCI command with CORE GID, but UCI HAL returns a UCI response with
        // SESSION_CONFIG GID. In this case, UciManager should return Error::Unknown, as the
        // RawUciSignature fields (GID, OID) of the CMD and RSP packets don't match.

        let mt = 0x1;
        let gid = 0x0; // CORE GID.
        let oid = 0x1;
        let cmd_payload = vec![0x11, 0x22, 0x33, 0x44];
        let cmd_payload_clone = cmd_payload.clone();
        let status = StatusCode::UciStatusOk;
        let cfg_id = AppConfigTlvType::DstMacAddress;
        let app_config = AppConfigStatus { cfg_id, status };
        let cfg_status = vec![app_config];

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::RawUciCmd { mt, gid, oid, payload: cmd_payload_clone };
                let resp = into_uci_hal_packets(uwb_uci_packets::SessionSetAppConfigRspBuilder {
                    status,
                    cfg_status,
                });

                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let expected_result = Err(Error::Unknown);
        let result = uci_manager.raw_uci_cmd(mt, gid, oid, cmd_payload).await;
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_raw_uci_cmd_out_of_range_gid() {
        // Send a raw UCI command with a GID value outside it's 8-bit size. This should result in
        // an error since the input GID value cannot be encoded into the UCI packet.
        let mt = 0x1;
        let gid = 0x1FF;
        let oid = 0x1;
        let cmd_payload = vec![0x11, 0x22, 0x33, 0x44];

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            move |_hal| async {},
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let expected_result = Err(Error::BadParameters);
        let result = uci_manager.raw_uci_cmd(mt, gid, oid, cmd_payload).await;
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_raw_uci_cmd_out_of_range_oid() {
        // Send a raw UCI command with a valid GID (CORE), but an OID value outside it's 8-bit
        // size. This should result in an error since the input OID value cannot be encoded into
        // the UCI packet.
        let mt = 0x1;
        let gid = 0x0; // CORE GID.
        let oid = 0x1FF;
        let cmd_payload = vec![0x11, 0x22, 0x33, 0x44];

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |_hal| async move {},
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let expected_result = Err(Error::BadParameters);
        let result = uci_manager.raw_uci_cmd(mt, gid, oid, cmd_payload).await;
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_raw_uci_cmd_uwbs_response_notification() {
        // Send a raw UCI command with a FiRa defined GID, OID (SESSION_SET_APP_CONFIG), and the
        // UCI HAL returns a valid UCI Notification packet before the raw UCI response.
        let cmd_mt: u8 = 0x1;
        let gid: u8 = 0x1; // Session Config.
        let oid: u8 = 0x3; // SESSION_SET_APP_CONFIG
        let cmd_payload = vec![0x11, 0x22, 0x33, 0x44];
        let cmd_payload_clone = cmd_payload.clone();
        let session_token = 0x123;
        let resp_mt: u8 = 0x2;
        let resp_payload = vec![0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08];
        let resp_payload_clone = resp_payload.clone();

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::RawUciCmd {
                    mt: cmd_mt.into(),
                    gid: gid.into(),
                    oid: oid.into(),
                    payload: cmd_payload_clone,
                };
                let raw_resp = build_uci_packet(resp_mt, 0, gid, oid, resp_payload_clone);
                let mut responses =
                    into_uci_hal_packets(uwb_uci_packets::SessionStatusNtfBuilder {
                        session_token,
                        session_state: uwb_uci_packets::SessionState::SessionStateInit,
                        reason_code:
                            uwb_uci_packets::ReasonCode::StateChangeWithSessionManagementCommands
                                .into(),
                    });
                responses.push(raw_resp);
                hal.expected_send_command(cmd, responses, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let expected_result =
            Ok(RawUciMessage { gid: gid.into(), oid: oid.into(), payload: resp_payload });
        let result =
            uci_manager.raw_uci_cmd(cmd_mt.into(), gid.into(), oid.into(), cmd_payload).await;
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_raw_uci_cmd_uwbs_response_undefined_mt() {
        // Send a raw UCI command with a FiRa defined GID, OID (SESSION_SET_APP_CONFIG), and the
        // UCI HAL returns a UCI packet with an undefined MessageType in response.
        let cmd_mt: u8 = 0x1;
        let gid: u8 = 0x1; // Session Config.
        let oid: u8 = 0x3; // SESSION_SET_APP_CONFIG
        let cmd_payload = vec![0x11, 0x22, 0x33, 0x44];
        let cmd_payload_clone = cmd_payload.clone();
        let resp_mt: u8 = 0x7; // Undefined MessageType
        let resp_payload = vec![0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08];

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::RawUciCmd {
                    mt: cmd_mt.into(),
                    gid: gid.into(),
                    oid: oid.into(),
                    payload: cmd_payload_clone,
                };
                let resp = build_uci_packet(resp_mt, /* pbf = */ 0, gid, oid, resp_payload);
                hal.expected_send_command(cmd, vec![resp], Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let expected_result = Err(Error::Unknown);
        let result =
            uci_manager.raw_uci_cmd(cmd_mt.into(), gid.into(), oid.into(), cmd_payload).await;
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    fn setup_hal_for_session_active(
        hal: &mut MockUciHal,
        session_type: SessionType,
        session_id: u32,
        session_token: u32,
    ) {
        // Setup session init.
        setup_hal_for_session_initialize(hal, session_type, session_id, session_token);

        // Setup session active.
        let cmd = UciCommand::SessionStart { session_token };
        let mut responses = into_uci_hal_packets(uwb_uci_packets::SessionStartRspBuilder {
            status: uwb_uci_packets::StatusCode::UciStatusOk,
        });
        responses.append(&mut into_uci_hal_packets(uwb_uci_packets::SessionStatusNtfBuilder {
            session_token,
            session_state: SessionState::SessionStateActive,
            reason_code: 0, /* ReasonCode::StateChangeWithSessionManagementCommands */
        }));
        hal.expected_send_command(cmd, responses, Ok(()));
    }

    async fn setup_uci_manager_with_session_active<F, Fut>(
        setup_hal_fn: F,
        uci_logger_mode: UciLoggerMode,
        log_sender: mpsc::UnboundedSender<UciLogEvent>,
        session_id: u32,
        session_token: u32,
    ) -> (UciManagerImpl, MockUciHal)
    where
        F: FnOnce(MockUciHal) -> Fut,
        Fut: Future<Output = ()>,
    {
        let session_type = SessionType::FiraRangingSession;

        init_test_logging();

        let mut hal = MockUciHal::new();
        setup_hal_for_session_active(&mut hal, session_type, session_id, session_token);

        // Verify open_hal() is working.
        let uci_manager =
            UciManagerImpl::new(hal.clone(), MockUciLogger::new(log_sender), uci_logger_mode);
        let result = uci_manager.open_hal().await;
        assert!(result.is_ok());

        // Verify session is initialized.
        let result = uci_manager.session_init(session_id, session_type).await;
        assert!(result.is_ok());

        // Verify session is started.
        let result = uci_manager.range_start(session_id).await;
        assert!(result.is_ok());
        assert!(hal.wait_expected_calls_done().await);

        setup_hal_fn(hal.clone()).await;

        (uci_manager, hal)
    }

    // Test Data packet receive for a single packet (on an active UWB session).
    #[tokio::test]
    async fn test_data_packet_recv_ok() {
        let mt_data = 0x0;
        let pbf = 0x0;
        let dpf = 0x2;
        let oid = 0x0;
        let session_id = 0x3;
        let session_token = 0x5;
        let uci_sequence_num = 0xa;
        let source_address = UwbAddress::Extended([0xa0, 0xb0, 0xc0, 0xd0, 0xa1, 0xb1, 0xc1, 0xd1]);
        let app_data = vec![0x01, 0x02, 0x03];
        let data_rcv_payload = vec![
            0x05, 0x00, 0x00, 0x00, // SessionToken
            0x00, // StatusCode
            0xa0, 0xb0, 0xc0, 0xd0, 0xa1, 0xb1, 0xc1, 0xd1, // MacAddress
            0x0a, 0x00, // UciSequenceNumber
            0x03, 0x00, // AppDataLen
            0x01, 0x02, 0x03, // AppData
        ];

        // Setup the DataPacketRcv (Rx by HAL) and the expected DataRcvNotification.
        let data_packet_rcv = build_uci_packet(mt_data, pbf, dpf, oid, data_rcv_payload);
        let expected_data_rcv_notification = DataRcvNotification {
            session_token: session_id,
            status: StatusCode::UciStatusOk,
            uci_sequence_num,
            source_address,
            payload: app_data,
        };

        // Setup an active UWBS session over which the DataPacket will be received by the Host.
        let (mut uci_manager, mut mock_hal) = setup_uci_manager_with_session_active(
            |_| async move {},
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let (data_rcv_notification_sender, mut data_rcv_notification_receiver) =
            mpsc::unbounded_channel::<DataRcvNotification>();
        uci_manager.set_data_rcv_notification_sender(data_rcv_notification_sender).await;

        // Inject the UCI DataPacketRcv into HAL.
        let result = mock_hal.receive_packet(data_packet_rcv);
        assert!(result.is_ok());

        // UciManager should send a DataRcvNotification (for the valid Rx packet).
        let result =
            tokio::time::timeout(Duration::from_millis(100), data_rcv_notification_receiver.recv())
                .await;
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), Some(expected_data_rcv_notification));
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    // Test Data packet receive for two packet fragments (on an active UWB session).
    #[tokio::test]
    async fn test_data_packet_recv_fragmented_packets_ok() {
        let mt_data = 0x0;
        let pbf_fragment_1 = 0x1;
        let pbf_fragment_2 = 0x0;
        let dpf = 0x2;
        let oid = 0x0;
        let session_id = 0x3;
        let session_token = 0x5;
        let uci_sequence_num = 0xa;
        let source_address = UwbAddress::Extended([0xa0, 0xb0, 0xc0, 0xd0, 0xa1, 0xb1, 0xc1, 0xd1]);
        let app_data_len = 300;
        let app_data_fragment_1_len = 200;
        let mut data_rcv_payload_fragment_1: Vec<u8> = vec![
            0x05, 0x00, 0x00, 0x00, // SessionToken
            0x00, // StatusCode
            0xa0, 0xb0, 0xc0, 0xd0, 0xa1, 0xb1, 0xc1, 0xd1, // MacAddress
            0x0a, 0x00, // UciSequenceNumber
            0x2c, 0x01, // AppData Length (300)
        ];

        // Setup the application data (payload) for the 2 DataPacketRcv fragments.
        let mut app_data: Vec<u8> = Vec::new();
        for i in 0..app_data_len {
            app_data.push((i & 0xff).try_into().unwrap());
        }
        data_rcv_payload_fragment_1.extend_from_slice(&app_data[0..app_data_fragment_1_len]);
        let mut data_rcv_payload_fragment_2: Vec<u8> = Vec::new();
        data_rcv_payload_fragment_2.extend_from_slice(&app_data[app_data_fragment_1_len..]);

        // Setup the DataPacketRcv fragments (Rx by HAL) and the expected DataRcvNotification.
        let data_packet_rcv_fragment_1 =
            build_uci_packet(mt_data, pbf_fragment_1, dpf, oid, data_rcv_payload_fragment_1);
        let data_packet_rcv_fragment_2 =
            build_uci_packet(mt_data, pbf_fragment_2, dpf, oid, data_rcv_payload_fragment_2);
        let expected_data_rcv_notification = DataRcvNotification {
            session_token: session_id,
            status: StatusCode::UciStatusOk,
            uci_sequence_num,
            source_address,
            payload: app_data,
        };

        // Setup an active UWBS session over which the DataPacket will be received by the Host.
        let (mut uci_manager, mut mock_hal) = setup_uci_manager_with_session_active(
            |_| async move {},
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let (data_rcv_notification_sender, mut data_rcv_notification_receiver) =
            mpsc::unbounded_channel::<DataRcvNotification>();
        uci_manager.set_data_rcv_notification_sender(data_rcv_notification_sender).await;

        // Inject the 2 UCI DataPacketRcv into HAL.
        let result = mock_hal.receive_packet(data_packet_rcv_fragment_1);
        assert!(result.is_ok());
        let result = mock_hal.receive_packet(data_packet_rcv_fragment_2);
        assert!(result.is_ok());

        // UciManager should send a DataRcvNotification (for the valid Rx packet).
        let result =
            tokio::time::timeout(Duration::from_millis(100), data_rcv_notification_receiver.recv())
                .await;
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), Some(expected_data_rcv_notification));
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_data_packet_recv_bad_payload_len_failure() {}

    // Test Radar Data packet receive for a single packet (on an active UWB session).
    #[tokio::test]
    async fn test_radar_data_packet_recv_ok() {
        let mt_data = 0x0;
        let pbf = 0x0;
        let dpf = 0xf;
        let oid = 0x0;
        let session_id = 0x3;
        let session_token = 0x5;
        let radar_data_type = RadarDataType::RadarSweepSamples;
        let number_of_sweeps = 0x02;
        let samples_per_sweep = 0x02;
        let bits_per_sample = BitsPerSample::Value32;
        let sweep_offset = 0x0;
        let sequence_number_1 = 0xa;
        let sequence_number_2 = 0xb;
        let timestamp_1 = 0xc;
        let timestamp_2 = 0xd;
        let vendor_specific_data_1 = vec![0x0b];
        let vendor_specific_data_2 = vec![0x0b, 0x0c];
        let sample_data_1 = vec![0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xa];
        let sample_data_2 = vec![0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8];
        let radar_data_rcv_payload = vec![
            0x05, 0x00, 0x00, 0x00, // session_handle
            0x00, // status
            0x00, // radar data type
            0x02, // number of sweeps
            0x02, // samples per sweep
            0x00, // bits per sample
            0x00, 0x00, // sweep offset
            0x10, 0x11, // sweep data size
            // sweep data 1
            0x0a, 0x00, 0x00, 0x00, // sequence number
            0x0c, 0x00, 0x00, 0x00, // timestamp
            0x01, // vendor specific data length
            0x0b, // vendor specific data
            0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, // sample data
            // sweep data 2
            0x0b, 0x00, 0x00, 0x00, // sequence number
            0x0d, 0x00, 0x00, 0x00, // timestamp
            0x02, // vendor specific data length
            0x0b, 0x0c, // vendor specific data
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, // sample data
        ];

        // Setup the DataPacketRcv (Rx by HAL) and the expected DataRcvNotification.
        let radar_data_packet_rcv =
            build_uci_packet(mt_data, pbf, dpf, oid, radar_data_rcv_payload);
        let expected_radar_data_rcv_notification = RadarDataRcvNotification {
            session_token: session_id,
            status: DataRcvStatusCode::UciStatusSuccess,
            radar_data_type,
            number_of_sweeps,
            samples_per_sweep,
            bits_per_sample,
            sweep_offset,
            sweep_data: vec![
                RadarSweepData {
                    sequence_number: sequence_number_1,
                    timestamp: timestamp_1,
                    vendor_specific_data: vendor_specific_data_1,
                    sample_data: sample_data_1,
                },
                RadarSweepData {
                    sequence_number: sequence_number_2,
                    timestamp: timestamp_2,
                    vendor_specific_data: vendor_specific_data_2,
                    sample_data: sample_data_2,
                },
            ],
        };

        // Setup an active UWBS session over which the DataPacket will be received by the Host.
        let (mut uci_manager, mut mock_hal) = setup_uci_manager_with_session_active(
            |_| async move {},
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let (radar_data_rcv_notification_sender, mut radar_data_rcv_notification_receiver) =
            mpsc::unbounded_channel::<RadarDataRcvNotification>();
        uci_manager
            .set_radar_data_rcv_notification_sender(radar_data_rcv_notification_sender)
            .await;

        // Inject the UCI DataPacketRcv into HAL.
        let result = mock_hal.receive_packet(radar_data_packet_rcv);
        assert!(result.is_ok());

        // UciManager should send a DataRcvNotification (for the valid Rx packet).
        let result = tokio::time::timeout(
            Duration::from_millis(100),
            radar_data_rcv_notification_receiver.recv(),
        )
        .await;
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), Some(expected_radar_data_rcv_notification));
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_data_packet_send_ok() {
        // Test Data packet send for a single packet (on a UWB session).
        let mt_data = 0x0;
        let pbf = 0x0;
        let dpf = 0x1;
        let oid = 0x0;
        let session_id = 0x5;
        let session_token = 0x5;
        let dest_mac_address = vec![0xa0, 0xb0, 0xc0, 0xd0, 0xa1, 0xb1, 0xc1, 0xd1];
        let uci_sequence_number: u16 = 0xa;
        let app_data = vec![0x01, 0x02, 0x03];
        let expected_data_snd_payload = vec![
            0x05, 0x00, 0x00, 0x00, // SessionID
            0xa0, 0xb0, 0xc0, 0xd0, 0xa1, 0xb1, 0xc1, 0xd1, // MacAddress
            0x0a, 0x00, // UciSequenceNumber
            0x03, 0x00, // AppDataLen
            0x01, 0x02, 0x03, // AppData
        ];
        let status = DataTransferNtfStatusCode::UciDataTransferStatusRepetitionOk;
        let tx_count = 0x00;

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_active(
            |mut hal| async move {
                // Now setup the notifications that should be received after a Data packet send.
                let data_packet_snd =
                    build_uci_packet(mt_data, pbf, dpf, oid, expected_data_snd_payload);
                let mut ntfs = into_uci_hal_packets(uwb_uci_packets::DataCreditNtfBuilder {
                    session_token,
                    credit_availability: CreditAvailability::CreditAvailable,
                });
                ntfs.append(&mut into_uci_hal_packets(
                    uwb_uci_packets::DataTransferStatusNtfBuilder {
                        session_token,
                        uci_sequence_number,
                        status,
                        tx_count,
                    },
                ));
                hal.expected_send_packet(data_packet_snd, ntfs, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let result = uci_manager
            .send_data_packet(session_id, dest_mac_address, uci_sequence_number, app_data)
            .await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);

        // TODO(b/276320369): Verify that session_notf_sender is called (once implemented), as a
        // DataTransferStatusNtf is received in this test scenario.
    }

    // Test the Host sending a DATA packet to UWBS that needs to be fragmented, where the
    // fragment size is based on a default value (MAX_PAYLOAD_LEN).
    #[tokio::test]
    async fn test_data_packet_send_fragmented_packet_ok_uses_default_fragment_size() {
        // Set flag to "false" for this unit test (it checks behavior of using a default value).
        set_parse_cap_tlv_rust_uses_uwbs_uci_version(false);

        // Test Data packet send for a set of data packet fragments (on a UWB session).
        let mt_data = 0x0;
        let pbf_fragment_1 = 0x1;
        let pbf_fragment_2 = 0x0;
        let dpf = 0x1;
        let oid = 0x0;
        let session_id = 0x5;
        let session_token = 0x5;
        let dest_mac_address = vec![0xa0, 0xb0, 0xc0, 0xd0, 0xa1, 0xb1, 0xc1, 0xd1];
        let uci_sequence_number: u16 = 0xa;
        let app_data_len = 300; // Larger than MAX_PAYLOAD_LEN=255, so fragmentation occurs.
        let mut app_data = Vec::new();
        let mut expected_data_snd_payload_fragment_1 = vec![
            0x05, 0x00, 0x00, 0x00, // SessionID
            0xa0, 0xb0, 0xc0, 0xd0, 0xa1, 0xb1, 0xc1, 0xd1, // MacAddress
            0x0a, 0x00, // UciSequenceNumber
            0x2c, 0x01, // AppDataLen = 300
        ];
        let mut expected_data_snd_payload_fragment_2 = Vec::new();
        let status = DataTransferNtfStatusCode::UciDataTransferStatusRepetitionOk;
        let tx_count = 0x00;

        // Setup the app data for both the Tx data packet and expected packet fragments.
        let app_data_len_fragment_1 = 255 - expected_data_snd_payload_fragment_1.len();
        for i in 0..app_data_len {
            app_data.push((i & 0xff).try_into().unwrap());
            if i < app_data_len_fragment_1 {
                expected_data_snd_payload_fragment_1.push((i & 0xff).try_into().unwrap());
            } else {
                expected_data_snd_payload_fragment_2.push((i & 0xff).try_into().unwrap());
            }
        }

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_active(
            |mut hal| async move {
                // Expected data packet fragment #1 (UCI Header + Initial App data bytes).
                let data_packet_snd_fragment_1 = build_uci_packet(
                    mt_data,
                    pbf_fragment_1,
                    dpf,
                    oid,
                    expected_data_snd_payload_fragment_1,
                );
                let ntfs = into_uci_hal_packets(uwb_uci_packets::DataCreditNtfBuilder {
                    session_token,
                    credit_availability: CreditAvailability::CreditAvailable,
                });
                hal.expected_send_packet(data_packet_snd_fragment_1, ntfs, Ok(()));

                // Expected data packet fragment #2 (UCI Header + Remaining App data bytes).
                let data_packet_snd_fragment_2 = build_uci_packet(
                    mt_data,
                    pbf_fragment_2,
                    dpf,
                    oid,
                    expected_data_snd_payload_fragment_2,
                );
                let mut ntfs = into_uci_hal_packets(uwb_uci_packets::DataCreditNtfBuilder {
                    session_token,
                    credit_availability: CreditAvailability::CreditAvailable,
                });
                ntfs.append(&mut into_uci_hal_packets(
                    uwb_uci_packets::DataTransferStatusNtfBuilder {
                        session_token,
                        uci_sequence_number,
                        status,
                        tx_count,
                    },
                ));
                hal.expected_send_packet(data_packet_snd_fragment_2, ntfs, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let result = uci_manager
            .send_data_packet(session_id, dest_mac_address, uci_sequence_number, app_data)
            .await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    async fn run_test_data_packet_send_fragmented_packet_uwbs_max_data_payload_size(
        uci_version: u16,
        uwbs_caps_info_tlv: CapTlv,
    ) {
        // Set the flag to true for this unit test.
        set_parse_cap_tlv_rust_uses_uwbs_uci_version(true);

        let status = StatusCode::UciStatusOk;
        let mac_version = 0;
        let phy_version = 0;
        let uci_test_version = 0;
        let vendor_spec_info = vec![0x1, 0x2];
        let uwbs_device_info_rsp = GetDeviceInfoResponse {
            status,
            uci_version,
            mac_version,
            phy_version,
            uci_test_version,
            vendor_spec_info: vendor_spec_info.clone(),
        };

        let uwbs_caps_info_tlv_clone = uwbs_caps_info_tlv.clone();

        // Test Data packet send for a set of data packet fragments (on a UWB session).
        let mt_data = 0x0;
        let pbf_fragment_1 = 0x1;
        let pbf_fragment_2 = 0x0;
        let dpf = 0x1;
        let oid = 0x0;
        let session_id = 0x5;
        let session_token = 0x5;
        let dest_mac_address = vec![0xa0, 0xb0, 0xc0, 0xd0, 0xa1, 0xb1, 0xc1, 0xd1];
        let uci_sequence_number: u16 = 0xa;
        let max_data_packet_payload_size = 275;
        let app_data_len = 300; // > max_data_packet_payload_size, so fragmentation occurs.
        let mut app_data = Vec::new();
        let mut expected_data_snd_payload_fragment_1 = vec![
            0x05, 0x00, 0x00, 0x00, // SessionID
            0xa0, 0xb0, 0xc0, 0xd0, 0xa1, 0xb1, 0xc1, 0xd1, // MacAddress
            0x0a, 0x00, // UciSequenceNumber
            0x2c, 0x01, // AppDataLen = 300
        ];
        let mut expected_data_snd_payload_fragment_2 = Vec::new();
        let data_status = DataTransferNtfStatusCode::UciDataTransferStatusRepetitionOk;
        let tx_count = 0x00;

        // Setup the app data for both the Tx data packet and expected packet fragments.
        let app_data_len_fragment_1 =
            max_data_packet_payload_size - expected_data_snd_payload_fragment_1.len();
        for i in 0..app_data_len {
            app_data.push((i & 0xff).try_into().unwrap());
            if i < app_data_len_fragment_1 {
                expected_data_snd_payload_fragment_1.push((i & 0xff).try_into().unwrap());
            } else {
                expected_data_snd_payload_fragment_2.push((i & 0xff).try_into().unwrap());
            }
        }

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_active(
            |mut hal| async move {
                // Expected UCI CMD CORE_GET_DEVICE_INFO
                let cmd = UciCommand::CoreGetDeviceInfo;
                let resp = into_uci_hal_packets(uwb_uci_packets::GetDeviceInfoRspBuilder {
                    status,
                    uci_version,
                    mac_version,
                    phy_version,
                    uci_test_version,
                    vendor_spec_info,
                });
                hal.expected_send_command(cmd, resp, Ok(()));

                // Expected UCI CMD CORE_GET_CAPS_INFO
                let cmd = UciCommand::CoreGetCapsInfo;
                let resp = into_uci_hal_packets(uwb_uci_packets::GetCapsInfoRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                    tlvs: vec![uwbs_caps_info_tlv_clone],
                });
                hal.expected_send_command(cmd, resp, Ok(()));

                // Expected data packet fragment #1 (UCI Header + Initial App data bytes).
                let data_packet_snd_fragment_1 = build_uci_packet(
                    mt_data,
                    pbf_fragment_1,
                    dpf,
                    oid,
                    expected_data_snd_payload_fragment_1,
                );
                let ntfs = into_uci_hal_packets(uwb_uci_packets::DataCreditNtfBuilder {
                    session_token,
                    credit_availability: CreditAvailability::CreditAvailable,
                });
                hal.expected_send_packet(data_packet_snd_fragment_1, ntfs, Ok(()));

                // Expected data packet fragment #2 (UCI Header + Remaining App data bytes).
                let data_packet_snd_fragment_2 = build_uci_packet(
                    mt_data,
                    pbf_fragment_2,
                    dpf,
                    oid,
                    expected_data_snd_payload_fragment_2,
                );
                let mut ntfs = into_uci_hal_packets(uwb_uci_packets::DataCreditNtfBuilder {
                    session_token,
                    credit_availability: CreditAvailability::CreditAvailable,
                });
                ntfs.append(&mut into_uci_hal_packets(
                    uwb_uci_packets::DataTransferStatusNtfBuilder {
                        session_token,
                        uci_sequence_number,
                        status: data_status,
                        tx_count,
                    },
                ));
                hal.expected_send_packet(data_packet_snd_fragment_2, ntfs, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        // First send the UCI CMD CORE_GET_DEVICE_INFO, so the UWBS returns it's UCI version.
        let result = uci_manager.core_get_device_info().await.unwrap();
        assert_eq!(result, uwbs_device_info_rsp);

        // Next send the UCI CMD CORE_GET_CAPS_INFO, so the UWBS returns it's capabilities.
        let result = uci_manager.core_get_caps_info().await.unwrap();
        assert_eq!(result[0], uwbs_caps_info_tlv);

        let result = uci_manager
            .send_data_packet(session_id, dest_mac_address, uci_sequence_number, app_data)
            .await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    // Test the Host sending a DATA packet to UWBS that needs to be fragmented, where the
    // fragment size is based on the UWBS MAX_DATA_PACKET_PAYLOAD_SIZE capability value.
    #[tokio::test]
    async fn test_data_packet_send_fragmented_packet_ok_fira_v1_uwbs_max_data_payload_size() {
        let uci_version = 0x1001;
        let uwbs_caps_info_tlv = CapTlv {
            t: CapTlvType::SupportedV1MaxDataPacketPayloadSizeV2AoaSupport,
            v: vec![0x13, 0x01],
        };

        run_test_data_packet_send_fragmented_packet_uwbs_max_data_payload_size(
            uci_version,
            uwbs_caps_info_tlv,
        )
        .await;
    }

    // Test the Host sending a DATA packet to UWBS that needs to be fragmented, where the
    // fragment size is based on the UWBS MAX_DATA_PACKET_PAYLOAD_SIZE capability value.
    #[tokio::test]
    async fn test_data_packet_send_fragmented_packet_ok_fira_v2_uwbs_max_data_payload_size() {
        let uci_version = 0x2002; // UCI version: Fira 2.x
        let uwbs_caps_info_tlv = CapTlv {
            t: CapTlvType::SupportedV1FiraMacVersionRangeV2MaxDataPayloadSize,
            v: vec![0x13, 0x01],
        };

        run_test_data_packet_send_fragmented_packet_uwbs_max_data_payload_size(
            uci_version,
            uwbs_caps_info_tlv,
        )
        .await;
    }

    #[tokio::test]
    async fn test_data_packet_send_retry_ok() {
        // Test Data packet send for a single packet (on a UWB session).
        let mt_data = 0x0;
        let pbf = 0x0;
        let dpf = 0x1;
        let oid = 0x0;
        let session_id = 0x5;
        let session_token = 0x5;
        let tx_count = 0x01;
        let dest_mac_address = vec![0xa0, 0xb0, 0xc0, 0xd0, 0xa1, 0xb1, 0xc1, 0xd1];
        let uci_sequence_number: u16 = 0xa;
        let app_data = vec![0x01, 0x02, 0x03];
        let expected_data_snd_payload = vec![
            0x05, 0x00, 0x00, 0x00, // SessionID
            0xa0, 0xb0, 0xc0, 0xd0, 0xa1, 0xb1, 0xc1, 0xd1, // MacAddress
            0x0a, 0x00, // UciSequenceNumber
            0x03, 0x00, // AppDataLen
            0x01, 0x02, 0x03, // AppData
        ];
        let status = DataTransferNtfStatusCode::UciDataTransferStatusRepetitionOk;

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_session_active(
            |mut hal| async move {
                // Setup receiving a CORE_GENERIC_ERROR_NTF with STATUS_COMMAND_RETRY after a
                // failed Data packet send attempt.
                let data_packet_snd =
                    build_uci_packet(mt_data, pbf, dpf, oid, expected_data_snd_payload);
                let error_ntf = into_uci_hal_packets(uwb_uci_packets::GenericErrorBuilder {
                    status: StatusCode::UciStatusCommandRetry,
                });
                hal.expected_send_packet(data_packet_snd.clone(), error_ntf, Ok(()));

                // Setup the notifications that should be received after the Data packet send
                // is successfully retried.
                let mut ntfs = into_uci_hal_packets(uwb_uci_packets::DataCreditNtfBuilder {
                    session_token,
                    credit_availability: CreditAvailability::CreditAvailable,
                });
                ntfs.append(&mut into_uci_hal_packets(
                    uwb_uci_packets::DataTransferStatusNtfBuilder {
                        session_token,
                        uci_sequence_number,
                        status,
                        tx_count,
                    },
                ));
                hal.expected_send_packet(data_packet_snd, ntfs, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
            session_id,
            session_token,
        )
        .await;

        let result = uci_manager
            .send_data_packet(session_id, dest_mac_address, uci_sequence_number, app_data)
            .await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);

        // TODO(b/276320369): Verify that session_notf_sender is called (once implemented), as a
        // DataTransferStatusNtf is received in this test scenario.
    }

    // TODO(b/276320369): Listing down the Data Packet Tx scenarios below, will add unit tests
    // for them in subsequent CLs.

    // Sending one data packet should succeed, when no DataCreditNtf is received.
    #[tokio::test]
    async fn test_data_packet_send_missing_data_credit_ntf_success() {}

    // Sending the second data packet should fail, when no DataCreditNtf is received after
    // sending the first data packet.
    #[tokio::test]
    async fn test_data_packet_send_missing_data_credit_ntf_subsequent_send_failure() {}

    #[tokio::test]
    async fn test_data_packet_send_data_credit_ntf_bad_session_id() {}

    #[tokio::test]
    async fn test_data_packet_send_data_credit_ntf_no_credit_available() {}

    #[tokio::test]
    async fn test_data_packet_send_missing_data_transfer_status_ntf() {}

    #[tokio::test]
    async fn test_data_packet_send_data_transfer_status_ntf_bad_session_id() {}

    #[tokio::test]
    async fn test_data_packet_send_data_transfer_status_ntf_bad_uci_sequence_number() {}

    // Tests for the multiple Status values that indicate success
    #[tokio::test]
    async fn test_data_packet_send_data_transfer_status_ntf_status_ok() {}

    #[tokio::test]
    async fn test_data_packet_send_data_transfer_status_ntf_status_repetition_ok() {}

    // Tests for some of the multiple Status values that indicate error.
    #[tokio::test]
    async fn test_data_packet_send_data_transfer_status_ntf_status_error() {}

    #[tokio::test]
    async fn test_session_get_count_retry_no_response() {
        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::SessionGetCount;
                hal.expected_send_command(cmd, vec![], Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let result = uci_manager.session_get_count().await;
        assert!(matches!(result, Err(Error::Timeout)));
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_get_count_timeout() {
        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::SessionGetCount;
                hal.expected_send_command(cmd, vec![], Err(Error::Timeout));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let result = uci_manager.session_get_count().await;
        assert!(matches!(result, Err(Error::Timeout)));
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_get_count_retry_too_many_times() {
        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::SessionGetCount;
                let retry_resp = into_uci_hal_packets(uwb_uci_packets::SessionGetCountRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusCommandRetry,
                    session_count: 0,
                });

                for _ in 0..MAX_RETRY_COUNT {
                    hal.expected_send_command(cmd.clone(), retry_resp.clone(), Ok(()));
                }
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let result = uci_manager.session_get_count().await;
        assert!(matches!(result, Err(Error::Timeout)));
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_get_count_retry_notification() {
        let session_count = 5;

        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::SessionGetCount;
                let retry_resp = into_uci_hal_packets(uwb_uci_packets::SessionGetCountRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusCommandRetry,
                    session_count: 0,
                });
                let resp = into_uci_hal_packets(uwb_uci_packets::SessionGetCountRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                    session_count,
                });

                hal.expected_send_command(cmd.clone(), retry_resp.clone(), Ok(()));
                hal.expected_send_command(cmd.clone(), retry_resp, Ok(()));
                hal.expected_send_command(cmd, resp, Ok(()));
            },
            UciLoggerMode::Disabled,
            mpsc::unbounded_channel::<UciLogEvent>().0,
        )
        .await;

        let result = uci_manager.session_get_count().await.unwrap();
        assert_eq!(result, session_count);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_log_manager_interaction() {
        let (log_sender, mut log_receiver) = mpsc::unbounded_channel::<UciLogEvent>();
        let (uci_manager, mut mock_hal) = setup_uci_manager_with_open_hal(
            |mut hal| async move {
                let cmd = UciCommand::SessionGetCount;
                let resp1 = into_uci_hal_packets(uwb_uci_packets::SessionGetCountRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                    session_count: 1,
                });
                let resp2 = into_uci_hal_packets(uwb_uci_packets::SessionGetCountRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                    session_count: 2,
                });
                hal.expected_send_command(cmd.clone(), resp1, Ok(()));
                hal.expected_send_command(cmd, resp2, Ok(()));
            },
            UciLoggerMode::Disabled,
            log_sender,
        )
        .await;

        // Under Disabled mode, initialization and first command and response are not logged.
        uci_manager.session_get_count().await.unwrap();
        assert!(log_receiver.try_recv().is_err());

        // Second command and response after change in logger mode are logged.
        uci_manager.set_logger_mode(UciLoggerMode::Filtered).await.unwrap();
        uci_manager.session_get_count().await.unwrap();
        let packet: Vec<u8> = log_receiver.recv().await.unwrap().try_into().unwrap();
        let cmd_packet: Vec<u8> = SessionGetCountCmdBuilder {}.build().into();
        assert_eq!(&packet, &cmd_packet);
        let packet: Vec<u8> = log_receiver.recv().await.unwrap().try_into().unwrap();
        let rsp_packet: Vec<u8> =
            SessionGetCountRspBuilder { status: StatusCode::UciStatusOk, session_count: 2 }
                .build()
                .into();
        assert_eq!(&packet, &rsp_packet);

        assert!(mock_hal.wait_expected_calls_done().await);
    }
}
