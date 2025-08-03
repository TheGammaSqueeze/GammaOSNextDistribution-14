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

use std::collections::BTreeMap;

use log::{debug, error, warn};
use tokio::sync::{mpsc, oneshot};

use crate::error::{Error, Result};
use crate::params::app_config_params::AppConfigParams;
use crate::params::uci_packets::{
    Controlee, ReasonCode, SessionId, SessionState, SessionType, UpdateMulticastListAction,
};
use crate::session::uwb_session::{Response as SessionResponse, ResponseSender, UwbSession};
use crate::uci::notification::{SessionNotification as UciSessionNotification, SessionRangeData};
use crate::uci::uci_manager::UciManager;
use crate::utils::clean_mpsc_receiver;

const MAX_SESSION_COUNT: usize = 5;

/// The notifications that are sent from SessionManager to its caller.
#[derive(Debug, PartialEq)]
pub(crate) enum SessionNotification {
    SessionState { session_id: SessionId, session_state: SessionState, reason_code: ReasonCode },
    RangeData { session_id: SessionId, range_data: SessionRangeData },
}

/// The SessionManager organizes the state machine of the existing UWB ranging sessions, sends
/// the session-related requests to the UciManager, and handles the session notifications from the
/// UciManager.
/// Using the actor model, SessionManager delegates the requests to SessionManagerActor.
pub(crate) struct SessionManager {
    cmd_sender: mpsc::UnboundedSender<(SessionCommand, ResponseSender)>,
}

impl SessionManager {
    pub fn new<T: UciManager>(
        uci_manager: T,
        uci_notf_receiver: mpsc::UnboundedReceiver<UciSessionNotification>,
        session_notf_sender: mpsc::UnboundedSender<SessionNotification>,
    ) -> Self {
        let (cmd_sender, cmd_receiver) = mpsc::unbounded_channel();
        let mut actor = SessionManagerActor::new(
            cmd_receiver,
            uci_manager,
            uci_notf_receiver,
            session_notf_sender,
        );
        tokio::spawn(async move { actor.run().await });

        Self { cmd_sender }
    }

    pub async fn init_session(
        &mut self,
        session_id: SessionId,
        session_type: SessionType,
        params: AppConfigParams,
    ) -> Result<()> {
        let result = self
            .send_cmd(SessionCommand::InitSession { session_id, session_type, params })
            .await
            .map(|_| ());
        if result.is_err() && result != Err(Error::DuplicatedSessionId) {
            let _ = self.deinit_session(session_id).await;
        }
        result
    }

    pub async fn deinit_session(&mut self, session_id: SessionId) -> Result<()> {
        self.send_cmd(SessionCommand::DeinitSession { session_id }).await?;
        Ok(())
    }

    pub async fn start_ranging(&mut self, session_id: SessionId) -> Result<AppConfigParams> {
        match self.send_cmd(SessionCommand::StartRanging { session_id }).await? {
            SessionResponse::AppConfigParams(params) => Ok(params),
            _ => panic!("start_ranging() should reply AppConfigParams result"),
        }
    }

    pub async fn stop_ranging(&mut self, session_id: SessionId) -> Result<()> {
        self.send_cmd(SessionCommand::StopRanging { session_id }).await?;
        Ok(())
    }

    pub async fn reconfigure(
        &mut self,
        session_id: SessionId,
        params: AppConfigParams,
    ) -> Result<()> {
        self.send_cmd(SessionCommand::Reconfigure { session_id, params }).await?;
        Ok(())
    }

    pub async fn update_controller_multicast_list(
        &mut self,
        session_id: SessionId,
        action: UpdateMulticastListAction,
        controlees: Vec<Controlee>,
    ) -> Result<()> {
        self.send_cmd(SessionCommand::UpdateControllerMulticastList {
            session_id,
            action,
            controlees,
        })
        .await?;
        Ok(())
    }

    pub async fn session_params(&mut self, session_id: SessionId) -> Result<AppConfigParams> {
        match self.send_cmd(SessionCommand::GetParams { session_id }).await? {
            SessionResponse::AppConfigParams(params) => Ok(params),
            _ => panic!("session_params() should reply AppConfigParams result"),
        }
    }

    // Send the |cmd| to the SessionManagerActor.
    async fn send_cmd(&self, cmd: SessionCommand) -> Result<SessionResponse> {
        let (result_sender, result_receiver) = oneshot::channel();
        self.cmd_sender.send((cmd, result_sender)).map_err(|cmd| {
            error!("Failed to send cmd: {:?}", cmd.0);
            Error::Unknown
        })?;
        result_receiver.await.unwrap_or_else(|e| {
            error!("Failed to receive the result for cmd: {:?}", e);
            Err(Error::Unknown)
        })
    }
}

struct SessionManagerActor<T: UciManager> {
    // Receive the commands and the corresponding response senders from SessionManager.
    cmd_receiver: mpsc::UnboundedReceiver<(SessionCommand, ResponseSender)>,
    // Send the notification to SessionManager's caller.
    session_notf_sender: mpsc::UnboundedSender<SessionNotification>,

    // The UciManager for delegating UCI requests.
    uci_manager: T,
    // Receive the notification from |uci_manager|.
    uci_notf_receiver: mpsc::UnboundedReceiver<UciSessionNotification>,

    active_sessions: BTreeMap<SessionId, UwbSession>,
}

impl<T: UciManager> SessionManagerActor<T> {
    fn new(
        cmd_receiver: mpsc::UnboundedReceiver<(SessionCommand, ResponseSender)>,
        uci_manager: T,
        uci_notf_receiver: mpsc::UnboundedReceiver<UciSessionNotification>,
        session_notf_sender: mpsc::UnboundedSender<SessionNotification>,
    ) -> Self {
        Self {
            cmd_receiver,
            session_notf_sender,
            uci_manager,
            uci_notf_receiver,
            active_sessions: BTreeMap::new(),
        }
    }

    async fn run(&mut self) {
        loop {
            tokio::select! {
                cmd = self.cmd_receiver.recv() => {
                    match cmd {
                        None => {
                            debug!("SessionManager is about to drop.");
                            break;
                        },
                        Some((cmd, result_sender)) => {
                            self.handle_cmd(cmd, result_sender);
                        }
                    }
                }

                Some(notf) = self.uci_notf_receiver.recv() => {
                    self.handle_uci_notification(notf);
                }
            }
        }
    }

    fn handle_cmd(&mut self, cmd: SessionCommand, result_sender: ResponseSender) {
        match cmd {
            SessionCommand::InitSession { session_id, session_type, params } => {
                if self.active_sessions.contains_key(&session_id) {
                    warn!("Session {} already exists", session_id);
                    let _ = result_sender.send(Err(Error::DuplicatedSessionId));
                    return;
                }
                if self.active_sessions.len() == MAX_SESSION_COUNT {
                    warn!("The amount of active sessions already reached {}", MAX_SESSION_COUNT);
                    let _ = result_sender.send(Err(Error::MaxSessionsExceeded));
                    return;
                }

                if !params.is_type_matched(session_type) {
                    warn!(
                        "session_type {:?} doesn't match with the params {:?}",
                        session_type, params
                    );
                    let _ = result_sender.send(Err(Error::BadParameters));
                    return;
                }

                let mut session =
                    UwbSession::new(self.uci_manager.clone(), session_id, session_type);
                session.initialize(params, result_sender);

                // We store the session first. If the initialize() fails, then SessionManager will
                // call deinit_session() to remove it.
                self.active_sessions.insert(session_id, session);
            }
            SessionCommand::DeinitSession { session_id } => {
                match self.active_sessions.remove(&session_id) {
                    None => {
                        warn!("Session {} doesn't exist", session_id);
                        let _ = result_sender.send(Err(Error::BadParameters));
                    }
                    Some(mut session) => {
                        session.deinitialize(result_sender);
                    }
                }
            }
            SessionCommand::StartRanging { session_id } => {
                match self.active_sessions.get_mut(&session_id) {
                    None => {
                        warn!("Session {} doesn't exist", session_id);
                        let _ = result_sender.send(Err(Error::BadParameters));
                    }
                    Some(session) => {
                        session.start_ranging(result_sender);
                    }
                }
            }
            SessionCommand::StopRanging { session_id } => {
                match self.active_sessions.get_mut(&session_id) {
                    None => {
                        warn!("Session {} doesn't exist", session_id);
                        let _ = result_sender.send(Err(Error::BadParameters));
                    }
                    Some(session) => {
                        session.stop_ranging(result_sender);
                    }
                }
            }
            SessionCommand::Reconfigure { session_id, params } => {
                match self.active_sessions.get_mut(&session_id) {
                    None => {
                        warn!("Session {} doesn't exist", session_id);
                        let _ = result_sender.send(Err(Error::BadParameters));
                    }
                    Some(session) => {
                        session.reconfigure(params, result_sender);
                    }
                }
            }
            SessionCommand::UpdateControllerMulticastList { session_id, action, controlees } => {
                match self.active_sessions.get_mut(&session_id) {
                    None => {
                        warn!("Session {} doesn't exist", session_id);
                        let _ = result_sender.send(Err(Error::BadParameters));
                    }
                    Some(session) => {
                        session.update_controller_multicast_list(action, controlees, result_sender);
                    }
                }
            }
            SessionCommand::GetParams { session_id } => {
                match self.active_sessions.get_mut(&session_id) {
                    None => {
                        warn!("Session {} doesn't exist", session_id);
                        let _ = result_sender.send(Err(Error::BadParameters));
                    }
                    Some(session) => {
                        session.params(result_sender);
                    }
                }
            }
        }
    }

    fn handle_uci_notification(&mut self, notf: UciSessionNotification) {
        match notf {
            UciSessionNotification::Status { session_token, session_state, reason_code } => {
                let reason_code = match ReasonCode::try_from(reason_code) {
                    Ok(r) => r,
                    Err(_) => {
                        error!(
                            "Received unknown reason_code {:?} in UciSessionNotification",
                            reason_code
                        );
                        return;
                    }
                };
                if session_state == SessionState::SessionStateDeinit {
                    debug!("Session {} is deinitialized", session_token);
                    let _ = self.active_sessions.remove(&session_token);
                    let _ = self.session_notf_sender.send(SessionNotification::SessionState {
                        session_id: session_token,
                        session_state,
                        reason_code,
                    });
                    return;
                }

                match self.active_sessions.get_mut(&session_token) {
                    Some(session) => {
                        session.on_session_status_changed(session_state);
                        let _ = self.session_notf_sender.send(SessionNotification::SessionState {
                            session_id: session_token,
                            session_state,
                            reason_code,
                        });
                    }
                    None => {
                        warn!(
                            "Received notification of the unknown Session {}: {:?}, {:?}",
                            session_token, session_state, reason_code
                        );
                    }
                }
            }
            UciSessionNotification::UpdateControllerMulticastList {
                session_token,
                remaining_multicast_list_size: _,
                status_list,
            } => match self.active_sessions.get_mut(&session_token) {
                Some(session) => session.on_controller_multicast_list_udpated(status_list),
                None => {
                    warn!(
                        "Received the notification of the unknown Session {}: {:?}",
                        session_token, status_list
                    );
                }
            },
            UciSessionNotification::SessionInfo(range_data) => {
                if self.active_sessions.get(&range_data.session_token).is_some() {
                    let _ = self.session_notf_sender.send(SessionNotification::RangeData {
                        session_id: range_data.session_token,
                        range_data,
                    });
                } else {
                    warn!("Received range data of the unknown Session: {:?}", range_data);
                }
            }
            UciSessionNotification::DataCredit { session_token, credit_availability: _ } => {
                match self.active_sessions.get(&session_token) {
                    Some(_) => {
                        /*
                         * TODO(b/270443790): Handle the DataCredit notification in the new
                         * code flow.
                         */
                    }
                    None => {
                        warn!(
                            "Received the Data Credit notification for an unknown Session {}",
                            session_token
                        );
                    }
                }
            }
            UciSessionNotification::DataTransferStatus {
                session_token,
                uci_sequence_number: _,
                status: _,
                tx_count: _,
            } => {
                match self.active_sessions.get(&session_token) {
                    Some(_) => {
                        /*
                         * TODO(b/270443790): Handle the DataTransferStatus notification in the
                         * new code flow.
                         */
                    }
                    None => {
                        warn!(
                            "Received a Data Transfer Status notification for unknown Session {}",
                            session_token
                        );
                    }
                }
            }
            UciSessionNotification::DataTransferPhaseConfig { session_token, status } => {
                match self.active_sessions.get_mut(&session_token) {
                    Some(_) => {
                        /*
                         *TODO
                         */
                    }
                    None => {
                        warn!(
                            "Received data transfer phase configuration notification of the unknown
                            Session {:?}",
                            status
                        );
                    }
                }
            }
        }
    }
}

impl<T: UciManager> Drop for SessionManagerActor<T> {
    fn drop(&mut self) {
        // mpsc receiver is about to be dropped. Clean shutdown the mpsc message.
        clean_mpsc_receiver(&mut self.uci_notf_receiver);
    }
}

#[derive(Debug)]
enum SessionCommand {
    InitSession {
        session_id: SessionId,
        session_type: SessionType,
        params: AppConfigParams,
    },
    DeinitSession {
        session_id: SessionId,
    },
    StartRanging {
        session_id: SessionId,
    },
    StopRanging {
        session_id: SessionId,
    },
    Reconfigure {
        session_id: SessionId,
        params: AppConfigParams,
    },
    UpdateControllerMulticastList {
        session_id: SessionId,
        action: UpdateMulticastListAction,
        controlees: Vec<Controlee>,
    },
    GetParams {
        session_id: SessionId,
    },
}

#[cfg(test)]
pub(crate) mod test_utils {
    use super::*;

    use crate::params::ccc_app_config_params::*;
    use crate::params::fira_app_config_params::*;
    use crate::params::uci_packets::{
        RangingMeasurementType, ReasonCode, ShortAddressTwoWayRangingMeasurement, StatusCode,
    };
    use crate::params::GetDeviceInfoResponse;
    use crate::uci::mock_uci_manager::MockUciManager;
    use crate::uci::notification::{RangingMeasurements, UciNotification};
    use crate::utils::init_test_logging;
    use uwb_uci_packets::StatusCode::UciStatusOk;

    const GET_DEVICE_INFO_RSP: GetDeviceInfoResponse = GetDeviceInfoResponse {
        status: UciStatusOk,
        uci_version: 0,
        mac_version: 0,
        phy_version: 0,
        uci_test_version: 0,
        vendor_spec_info: vec![],
    };

    pub(crate) fn generate_params() -> AppConfigParams {
        FiraAppConfigParamsBuilder::new()
            .device_type(DeviceType::Controller)
            .multi_node_mode(MultiNodeMode::Unicast)
            .device_mac_address(UwbAddress::Short([1, 2]))
            .dst_mac_address(vec![UwbAddress::Short([3, 4])])
            .device_role(DeviceRole::Initiator)
            .vendor_id([0xFE, 0xDC])
            .static_sts_iv([0xDF, 0xCE, 0xAB, 0x12, 0x34, 0x56])
            .build()
            .unwrap()
    }

    pub(crate) fn generate_ccc_params() -> AppConfigParams {
        CccAppConfigParamsBuilder::new()
            .protocol_version(CccProtocolVersion { major: 2, minor: 1 })
            .uwb_config(CccUwbConfig::Config0)
            .pulse_shape_combo(CccPulseShapeCombo {
                initiator_tx: PulseShape::PrecursorFree,
                responder_tx: PulseShape::PrecursorFreeSpecial,
            })
            .ran_multiplier(3)
            .channel_number(CccUwbChannel::Channel9)
            .chaps_per_slot(ChapsPerSlot::Value9)
            .num_responder_nodes(1)
            .slots_per_rr(3)
            .sync_code_index(12)
            .hopping_mode(CccHoppingMode::ContinuousAes)
            .build()
            .unwrap()
    }

    // TODO(b/321757248): Add a unit test generate_aliro_params().

    pub(crate) fn session_range_data(session_id: SessionId) -> SessionRangeData {
        SessionRangeData {
            sequence_number: 1,
            session_token: session_id,
            current_ranging_interval_ms: 3,
            ranging_measurement_type: RangingMeasurementType::TwoWay,
            ranging_measurements: RangingMeasurements::ShortAddressTwoWay(vec![
                ShortAddressTwoWayRangingMeasurement {
                    mac_address: 0x123,
                    status: StatusCode::UciStatusOk,
                    nlos: 0,
                    distance: 4,
                    aoa_azimuth: 5,
                    aoa_azimuth_fom: 6,
                    aoa_elevation: 7,
                    aoa_elevation_fom: 8,
                    aoa_destination_azimuth: 9,
                    aoa_destination_azimuth_fom: 10,
                    aoa_destination_elevation: 11,
                    aoa_destination_elevation_fom: 12,
                    slot_index: 0,
                    rssi: u8::MAX,
                },
            ]),
            rcr_indicator: 0,
            raw_ranging_data: vec![0x12, 0x34],
        }
    }

    pub(crate) fn session_status_notf(
        session_id: SessionId,
        session_state: SessionState,
    ) -> UciNotification {
        UciNotification::Session(UciSessionNotification::Status {
            session_token: session_id,
            session_state,
            reason_code: ReasonCode::StateChangeWithSessionManagementCommands.into(),
        })
    }

    pub(crate) fn range_data_notf(range_data: SessionRangeData) -> UciNotification {
        UciNotification::Session(UciSessionNotification::SessionInfo(range_data))
    }

    pub(super) async fn setup_session_manager<F>(
        setup_uci_manager_fn: F,
    ) -> (SessionManager, MockUciManager, mpsc::UnboundedReceiver<SessionNotification>)
    where
        F: FnOnce(&mut MockUciManager),
    {
        init_test_logging();
        let (uci_notf_sender, uci_notf_receiver) = mpsc::unbounded_channel();
        let (session_notf_sender, session_notf_receiver) = mpsc::unbounded_channel();
        let mut uci_manager = MockUciManager::new();
        uci_manager.expect_open_hal(vec![], Ok(GET_DEVICE_INFO_RSP));
        setup_uci_manager_fn(&mut uci_manager);
        uci_manager.set_session_notification_sender(uci_notf_sender).await;
        let _ = uci_manager.open_hal().await;

        (
            SessionManager::new(uci_manager.clone(), uci_notf_receiver, session_notf_sender),
            uci_manager,
            session_notf_receiver,
        )
    }
}

#[cfg(test)]
mod tests {
    use super::test_utils::*;
    use super::*;

    use std::collections::HashMap;

    use crate::params::ccc_started_app_config_params::CccStartedAppConfigParams;
    use crate::params::uci_packets::{
        AppConfigTlv, AppConfigTlvType, ControleeStatus, Controlees, MulticastUpdateStatusCode,
        ReasonCode, SetAppConfigResponse, StatusCode,
    };
    use crate::params::utils::{u32_to_bytes, u64_to_bytes, u8_to_bytes};
    use crate::params::{FiraAppConfigParamsBuilder, KeyRotation};
    use crate::uci::notification::UciNotification;

    #[tokio::test]
    async fn test_init_deinit_session() {
        let session_id = 0x123;
        let session_type = SessionType::FiraRangingSession;
        let params = generate_params();

        let tlvs = params.generate_tlvs();
        let (mut session_manager, mut mock_uci_manager, mut session_notf_receiver) =
            setup_session_manager(move |uci_manager| {
                uci_manager.expect_session_init(
                    session_id,
                    session_type,
                    vec![session_status_notf(session_id, SessionState::SessionStateInit)],
                    Ok(()),
                );
                uci_manager.expect_session_set_app_config(
                    session_id,
                    tlvs,
                    vec![session_status_notf(session_id, SessionState::SessionStateIdle)],
                    Ok(SetAppConfigResponse {
                        status: StatusCode::UciStatusOk,
                        config_status: vec![],
                    }),
                );
                uci_manager.expect_session_deinit(
                    session_id,
                    vec![session_status_notf(session_id, SessionState::SessionStateDeinit)],
                    Ok(()),
                );
            })
            .await;

        // Deinit a session before initialized should fail.
        let result = session_manager.deinit_session(session_id).await;
        assert_eq!(result, Err(Error::BadParameters));

        // Initialize a normal session should be successful.
        let result = session_manager.init_session(session_id, session_type, params.clone()).await;
        assert_eq!(result, Ok(()));
        let session_notf = session_notf_receiver.recv().await.unwrap();
        assert_eq!(
            session_notf,
            SessionNotification::SessionState {
                session_id,
                session_state: SessionState::SessionStateInit,
                reason_code: ReasonCode::StateChangeWithSessionManagementCommands
            }
        );
        let session_notf = session_notf_receiver.recv().await.unwrap();
        assert_eq!(
            session_notf,
            SessionNotification::SessionState {
                session_id,
                session_state: SessionState::SessionStateIdle,
                reason_code: ReasonCode::StateChangeWithSessionManagementCommands
            }
        );

        // Initialize a session multiple times without deinitialize should fail.
        let result = session_manager.init_session(session_id, session_type, params).await;
        assert_eq!(result, Err(Error::DuplicatedSessionId));

        // Deinitialize the session should be successful, and should receive the deinitialized
        // notification.
        let result = session_manager.deinit_session(session_id).await;
        assert_eq!(result, Ok(()));
        let session_notf = session_notf_receiver.recv().await.unwrap();
        assert_eq!(
            session_notf,
            SessionNotification::SessionState {
                session_id,
                session_state: SessionState::SessionStateDeinit,
                reason_code: ReasonCode::StateChangeWithSessionManagementCommands
            }
        );

        // Deinit a session after deinitialized should fail.
        let result = session_manager.deinit_session(session_id).await;
        assert_eq!(result, Err(Error::BadParameters));

        assert!(mock_uci_manager.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_init_session_timeout() {
        let session_id = 0x123;
        let session_type = SessionType::FiraRangingSession;
        let params = generate_params();

        let (mut session_manager, mut mock_uci_manager, _) =
            setup_session_manager(move |uci_manager| {
                let notfs = vec![]; // Not sending SessionStatus notification.
                uci_manager.expect_session_init(session_id, session_type, notfs, Ok(()));
            })
            .await;

        let result = session_manager.init_session(session_id, session_type, params).await;
        assert_eq!(result, Err(Error::Timeout));

        assert!(mock_uci_manager.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_start_stop_ranging() {
        let session_id = 0x123;
        let session_type = SessionType::FiraRangingSession;
        let params = generate_params();
        let tlvs = params.generate_tlvs();

        let (mut session_manager, mut mock_uci_manager, _) =
            setup_session_manager(move |uci_manager| {
                uci_manager.expect_session_init(
                    session_id,
                    session_type,
                    vec![session_status_notf(session_id, SessionState::SessionStateInit)],
                    Ok(()),
                );
                uci_manager.expect_session_set_app_config(
                    session_id,
                    tlvs,
                    vec![session_status_notf(session_id, SessionState::SessionStateIdle)],
                    Ok(SetAppConfigResponse {
                        status: StatusCode::UciStatusOk,
                        config_status: vec![],
                    }),
                );
                uci_manager.expect_range_start(
                    session_id,
                    vec![session_status_notf(session_id, SessionState::SessionStateActive)],
                    Ok(()),
                );
                uci_manager.expect_range_stop(
                    session_id,
                    vec![session_status_notf(session_id, SessionState::SessionStateIdle)],
                    Ok(()),
                );
            })
            .await;

        let result = session_manager.init_session(session_id, session_type, params.clone()).await;
        assert_eq!(result, Ok(()));
        let result = session_manager.start_ranging(session_id).await;
        assert_eq!(result, Ok(params));
        let result = session_manager.stop_ranging(session_id).await;
        assert_eq!(result, Ok(()));

        assert!(mock_uci_manager.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_ccc_start_ranging() {
        let session_id = 0x123;
        let session_type = SessionType::Ccc;
        // params that is passed to UciManager::session_set_app_config().
        let params = generate_ccc_params();
        let tlvs = params.generate_tlvs();
        // The params that is received from UciManager::session_get_app_config().
        let received_config_map = HashMap::from([
            (AppConfigTlvType::StsIndex, u32_to_bytes(3)),
            (AppConfigTlvType::CccHopModeKey, u32_to_bytes(5)),
            (AppConfigTlvType::CccUwbTime0, u64_to_bytes(7)),
            (AppConfigTlvType::RangingDuration, u32_to_bytes(96)),
            (AppConfigTlvType::PreambleCodeIndex, u8_to_bytes(9)),
        ]);
        let received_tlvs = received_config_map
            .iter()
            .map(|(key, value)| AppConfigTlv::new(*key, value.clone()))
            .collect();
        let started_params =
            CccStartedAppConfigParams::from_config_map(received_config_map).unwrap();

        let (mut session_manager, mut mock_uci_manager, _) =
            setup_session_manager(move |uci_manager| {
                uci_manager.expect_session_init(
                    session_id,
                    session_type,
                    vec![session_status_notf(session_id, SessionState::SessionStateInit)],
                    Ok(()),
                );
                uci_manager.expect_session_set_app_config(
                    session_id,
                    tlvs,
                    vec![session_status_notf(session_id, SessionState::SessionStateIdle)],
                    Ok(SetAppConfigResponse {
                        status: StatusCode::UciStatusOk,
                        config_status: vec![],
                    }),
                );
                uci_manager.expect_range_start(
                    session_id,
                    vec![session_status_notf(session_id, SessionState::SessionStateActive)],
                    Ok(()),
                );
                uci_manager.expect_session_get_app_config(session_id, vec![], Ok(received_tlvs));
            })
            .await;

        let result = session_manager.init_session(session_id, session_type, params.clone()).await;
        assert_eq!(result, Ok(()));
        let result = session_manager.start_ranging(session_id).await;
        assert_eq!(result, Ok(AppConfigParams::CccStarted(started_params)));

        assert!(mock_uci_manager.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_update_controller_multicast_list() {
        let session_id = 0x123;
        let session_type = SessionType::FiraRangingSession;
        let params = generate_params();
        let tlvs = params.generate_tlvs();
        let action = UpdateMulticastListAction::AddControlee;
        let short_address: [u8; 2] = [0x12, 0x34];
        let controlees = vec![Controlee { short_address, subsession_id: 0x24 }];

        let controlees_clone = controlees.clone();
        let (mut session_manager, mut mock_uci_manager, _) =
            setup_session_manager(move |uci_manager| {
                let multicast_list_notf = vec![UciNotification::Session(
                    UciSessionNotification::UpdateControllerMulticastList {
                        session_token: session_id,
                        remaining_multicast_list_size: 1,
                        status_list: vec![ControleeStatus {
                            mac_address: [0x34, 0x12],
                            subsession_id: 0x24,
                            status: MulticastUpdateStatusCode::StatusOkMulticastListUpdate,
                        }],
                    },
                )];
                uci_manager.expect_session_init(
                    session_id,
                    session_type,
                    vec![session_status_notf(session_id, SessionState::SessionStateInit)],
                    Ok(()),
                );
                uci_manager.expect_session_set_app_config(
                    session_id,
                    tlvs,
                    vec![session_status_notf(session_id, SessionState::SessionStateIdle)],
                    Ok(SetAppConfigResponse {
                        status: StatusCode::UciStatusOk,
                        config_status: vec![],
                    }),
                );
                uci_manager.expect_session_update_controller_multicast_list(
                    session_id,
                    action,
                    Controlees::NoSessionKey(controlees_clone),
                    multicast_list_notf,
                    Ok(()),
                );
            })
            .await;

        let result = session_manager.init_session(session_id, session_type, params).await;
        assert_eq!(result, Ok(()));
        let result =
            session_manager.update_controller_multicast_list(session_id, action, controlees).await;
        assert_eq!(result, Ok(()));

        assert!(mock_uci_manager.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_ccc_update_controller_multicast_list() {
        let session_id = 0x123;
        let session_type = SessionType::Ccc;
        let params = generate_ccc_params();
        let tlvs = params.generate_tlvs();
        let action = UpdateMulticastListAction::AddControlee;
        let short_address: [u8; 2] = [0x12, 0x34];
        let controlees = vec![Controlee { short_address, subsession_id: 0x24 }];

        let (mut session_manager, mut mock_uci_manager, _) =
            setup_session_manager(move |uci_manager| {
                uci_manager.expect_session_init(
                    session_id,
                    session_type,
                    vec![session_status_notf(session_id, SessionState::SessionStateInit)],
                    Ok(()),
                );
                uci_manager.expect_session_set_app_config(
                    session_id,
                    tlvs,
                    vec![session_status_notf(session_id, SessionState::SessionStateIdle)],
                    Ok(SetAppConfigResponse {
                        status: StatusCode::UciStatusOk,
                        config_status: vec![],
                    }),
                );
            })
            .await;

        let result = session_manager.init_session(session_id, session_type, params).await;
        assert_eq!(result, Ok(()));
        // CCC session doesn't support update_controller_multicast_list.
        let result =
            session_manager.update_controller_multicast_list(session_id, action, controlees).await;
        assert_eq!(result, Err(Error::BadParameters));

        assert!(mock_uci_manager.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_update_controller_multicast_list_without_notification() {
        let session_id = 0x123;
        let session_type = SessionType::FiraRangingSession;
        let params = generate_params();
        let tlvs = params.generate_tlvs();
        let action = UpdateMulticastListAction::AddControlee;
        let short_address: [u8; 2] = [0x12, 0x34];
        let controlees = vec![Controlee { short_address, subsession_id: 0x24 }];

        let controlees_clone = controlees.clone();
        let (mut session_manager, mut mock_uci_manager, _) =
            setup_session_manager(move |uci_manager| {
                uci_manager.expect_session_init(
                    session_id,
                    session_type,
                    vec![session_status_notf(session_id, SessionState::SessionStateInit)],
                    Ok(()),
                );
                uci_manager.expect_session_set_app_config(
                    session_id,
                    tlvs,
                    vec![session_status_notf(session_id, SessionState::SessionStateIdle)],
                    Ok(SetAppConfigResponse {
                        status: StatusCode::UciStatusOk,
                        config_status: vec![],
                    }),
                );
                uci_manager.expect_session_update_controller_multicast_list(
                    session_id,
                    action,
                    uwb_uci_packets::Controlees::NoSessionKey(controlees_clone),
                    vec![], // Not sending notification.
                    Ok(()),
                );
            })
            .await;

        let result = session_manager.init_session(session_id, session_type, params).await;
        assert_eq!(result, Ok(()));
        // This method should timeout waiting for the notification.
        let result =
            session_manager.update_controller_multicast_list(session_id, action, controlees).await;
        assert_eq!(result, Err(Error::Timeout));

        assert!(mock_uci_manager.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_receive_session_range_data() {
        let session_id = 0x123;
        let session_type = SessionType::FiraRangingSession;
        let params = generate_params();
        let tlvs = params.generate_tlvs();
        let range_data = session_range_data(session_id);
        let range_data_clone = range_data.clone();

        let (mut session_manager, mut mock_uci_manager, mut session_notf_receiver) =
            setup_session_manager(move |uci_manager| {
                uci_manager.expect_session_init(
                    session_id,
                    session_type,
                    vec![session_status_notf(session_id, SessionState::SessionStateInit)],
                    Ok(()),
                );
                uci_manager.expect_session_set_app_config(
                    session_id,
                    tlvs,
                    vec![
                        session_status_notf(session_id, SessionState::SessionStateIdle),
                        range_data_notf(range_data_clone),
                    ],
                    Ok(SetAppConfigResponse {
                        status: StatusCode::UciStatusOk,
                        config_status: vec![],
                    }),
                );
            })
            .await;

        let result = session_manager.init_session(session_id, session_type, params).await;
        assert_eq!(result, Ok(()));
        let session_notf = session_notf_receiver.recv().await.unwrap();
        assert_eq!(
            session_notf,
            SessionNotification::SessionState {
                session_id,
                session_state: SessionState::SessionStateInit,
                reason_code: ReasonCode::StateChangeWithSessionManagementCommands
            }
        );
        let session_notf = session_notf_receiver.recv().await.unwrap();
        assert_eq!(
            session_notf,
            SessionNotification::SessionState {
                session_id,
                session_state: SessionState::SessionStateIdle,
                reason_code: ReasonCode::StateChangeWithSessionManagementCommands
            }
        );

        let session_notf = session_notf_receiver.recv().await.unwrap();
        assert_eq!(session_notf, SessionNotification::RangeData { session_id, range_data });

        assert!(mock_uci_manager.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_reconfigure_app_config() {
        let session_id = 0x123;
        let session_type = SessionType::FiraRangingSession;

        let initial_params = generate_params();
        let initial_tlvs = initial_params.generate_tlvs();

        let non_default_key_rotation_val = KeyRotation::Enable;
        let idle_params = FiraAppConfigParamsBuilder::from_params(&initial_params)
            .unwrap()
            .key_rotation(non_default_key_rotation_val)
            .build()
            .unwrap();
        let idle_tlvs = idle_params
            .generate_updated_tlvs(&initial_params, SessionState::SessionStateIdle)
            .unwrap();

        let non_default_block_stride_val = 2u8;
        let active_params = FiraAppConfigParamsBuilder::from_params(&idle_params)
            .unwrap()
            .block_stride_length(non_default_block_stride_val)
            .build()
            .unwrap();
        let active_tlvs = active_params
            .generate_updated_tlvs(&idle_params, SessionState::SessionStateIdle)
            .unwrap();

        let (mut session_manager, mut mock_uci_manager, _) =
            setup_session_manager(move |uci_manager| {
                uci_manager.expect_session_init(
                    session_id,
                    session_type,
                    vec![session_status_notf(session_id, SessionState::SessionStateInit)],
                    Ok(()),
                );
                uci_manager.expect_session_set_app_config(
                    session_id,
                    initial_tlvs,
                    vec![session_status_notf(session_id, SessionState::SessionStateIdle)],
                    Ok(SetAppConfigResponse {
                        status: StatusCode::UciStatusOk,
                        config_status: vec![],
                    }),
                );
                uci_manager.expect_session_set_app_config(
                    session_id,
                    idle_tlvs,
                    vec![],
                    Ok(SetAppConfigResponse {
                        status: StatusCode::UciStatusOk,
                        config_status: vec![],
                    }),
                );
                uci_manager.expect_range_start(
                    session_id,
                    vec![session_status_notf(session_id, SessionState::SessionStateActive)],
                    Ok(()),
                );
                uci_manager.expect_session_set_app_config(
                    session_id,
                    active_tlvs,
                    vec![],
                    Ok(SetAppConfigResponse {
                        status: StatusCode::UciStatusOk,
                        config_status: vec![],
                    }),
                );
            })
            .await;

        // Reconfiguring without first initing a session should fail.
        let result = session_manager.reconfigure(session_id, initial_params.clone()).await;
        assert_eq!(result, Err(Error::BadParameters));

        let result =
            session_manager.init_session(session_id, session_type, initial_params.clone()).await;
        assert_eq!(result, Ok(()));

        // Reconfiguring any parameters during idle state should succeed.
        let result = session_manager.reconfigure(session_id, idle_params.clone()).await;
        assert_eq!(result, Ok(()));

        let result = session_manager.start_ranging(session_id).await;
        assert_eq!(result, Ok(idle_params));

        // Reconfiguring most parameters during active state should fail.
        let result = session_manager.reconfigure(session_id, initial_params).await;
        assert_eq!(result, Err(Error::BadParameters));

        // Only some parameters are allowed to be reconfigured during active state.
        let result = session_manager.reconfigure(session_id, active_params).await;
        assert_eq!(result, Ok(()));

        assert!(mock_uci_manager.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_params() {
        let session_id = 0x123;
        let session_type = SessionType::FiraRangingSession;

        let params = generate_params();
        let tlvs = params.generate_tlvs();

        let (mut session_manager, mut mock_uci_manager, _) =
            setup_session_manager(move |uci_manager| {
                uci_manager.expect_session_init(
                    session_id,
                    session_type,
                    vec![session_status_notf(session_id, SessionState::SessionStateInit)],
                    Ok(()),
                );
                uci_manager.expect_session_set_app_config(
                    session_id,
                    tlvs,
                    vec![session_status_notf(session_id, SessionState::SessionStateIdle)],
                    Ok(SetAppConfigResponse {
                        status: StatusCode::UciStatusOk,
                        config_status: vec![],
                    }),
                );
            })
            .await;

        // Getting session params without initing a session should fail
        let result = session_manager.session_params(session_id).await;
        assert_eq!(result, Err(Error::BadParameters));

        let result = session_manager.init_session(session_id, session_type, params.clone()).await;
        result.unwrap();

        // Getting session params after they've been properly set should succeed
        let result = session_manager.session_params(session_id).await;
        assert_eq!(result, Ok(params));

        assert!(mock_uci_manager.wait_expected_calls_done().await);
    }
}
