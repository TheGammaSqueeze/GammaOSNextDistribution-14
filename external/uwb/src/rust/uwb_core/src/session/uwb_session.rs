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

use std::collections::HashMap;
use std::iter::FromIterator;
use std::time::Duration;

use log::{debug, error, warn};
use tokio::sync::{mpsc, oneshot, watch};
use tokio::time::timeout;

use crate::error::{Error, Result};
use crate::params::app_config_params::AppConfigParams;
use crate::params::ccc_started_app_config_params::CccStartedAppConfigParams;
use crate::params::uci_packets::{
    Controlee, ControleeStatus, Controlees, MulticastUpdateStatusCode, SessionId, SessionState,
    SessionType, UpdateMulticastListAction,
};
use crate::uci::error::status_code_to_result;
use crate::uci::uci_manager::UciManager;

const NOTIFICATION_TIMEOUT_MS: u64 = 1000;

#[derive(Debug)]
pub(super) enum Response {
    Null,
    AppConfigParams(AppConfigParams),
}
pub(super) type ResponseSender = oneshot::Sender<Result<Response>>;

pub(super) struct UwbSession {
    cmd_sender: mpsc::UnboundedSender<(Command, ResponseSender)>,
    state_sender: watch::Sender<SessionState>,
    controlee_status_notf_sender: Option<oneshot::Sender<Vec<ControleeStatus>>>,
}

impl UwbSession {
    pub fn new<T: UciManager>(
        uci_manager: T,
        session_id: SessionId,
        session_type: SessionType,
    ) -> Self {
        let (cmd_sender, cmd_receiver) = mpsc::unbounded_channel();
        let (state_sender, mut state_receiver) = watch::channel(SessionState::SessionStateDeinit);
        // Mark the initial value of state as seen.
        let _ = state_receiver.borrow_and_update();

        let mut actor = UwbSessionActor::new(
            cmd_receiver,
            state_receiver,
            uci_manager,
            session_id,
            session_type,
        );
        tokio::spawn(async move { actor.run().await });

        Self { cmd_sender, state_sender, controlee_status_notf_sender: None }
    }

    pub fn initialize(&mut self, params: AppConfigParams, result_sender: ResponseSender) {
        let _ = self.cmd_sender.send((Command::Initialize { params }, result_sender));
    }

    pub fn deinitialize(&mut self, result_sender: ResponseSender) {
        let _ = self.cmd_sender.send((Command::Deinitialize, result_sender));
    }

    pub fn start_ranging(&mut self, result_sender: ResponseSender) {
        let _ = self.cmd_sender.send((Command::StartRanging, result_sender));
    }

    pub fn stop_ranging(&mut self, result_sender: ResponseSender) {
        let _ = self.cmd_sender.send((Command::StopRanging, result_sender));
    }

    pub fn reconfigure(&mut self, params: AppConfigParams, result_sender: ResponseSender) {
        let _ = self.cmd_sender.send((Command::Reconfigure { params }, result_sender));
    }

    pub fn update_controller_multicast_list(
        &mut self,
        action: UpdateMulticastListAction,
        controlees: Vec<Controlee>,
        result_sender: ResponseSender,
    ) {
        let (notf_sender, notf_receiver) = oneshot::channel();
        self.controlee_status_notf_sender = Some(notf_sender);
        let _ = self.cmd_sender.send((
            Command::UpdateControllerMulticastList { action, controlees, notf_receiver },
            result_sender,
        ));
    }

    pub fn params(&mut self, result_sender: ResponseSender) {
        let _ = self.cmd_sender.send((Command::GetParams, result_sender));
    }

    pub fn on_session_status_changed(&mut self, state: SessionState) {
        let _ = self.state_sender.send(state);
    }

    pub fn on_controller_multicast_list_udpated(&mut self, status_list: Vec<ControleeStatus>) {
        if let Some(sender) = self.controlee_status_notf_sender.take() {
            let _ = sender.send(status_list);
        }
    }
}

struct UwbSessionActor<T: UciManager> {
    cmd_receiver: mpsc::UnboundedReceiver<(Command, ResponseSender)>,
    state_receiver: watch::Receiver<SessionState>,
    uci_manager: T,
    session_id: SessionId,
    session_type: SessionType,
    params: Option<AppConfigParams>,
}

impl<T: UciManager> UwbSessionActor<T> {
    fn new(
        cmd_receiver: mpsc::UnboundedReceiver<(Command, ResponseSender)>,
        state_receiver: watch::Receiver<SessionState>,
        uci_manager: T,
        session_id: SessionId,
        session_type: SessionType,
    ) -> Self {
        Self { cmd_receiver, state_receiver, uci_manager, session_id, session_type, params: None }
    }

    async fn run(&mut self) {
        loop {
            tokio::select! {
                cmd = self.cmd_receiver.recv() => {
                    match cmd {
                        None => {
                            debug!("UwbSession is about to drop.");
                            break;
                        }
                        Some((cmd, result_sender)) => {
                            let result = match cmd {
                                Command::Initialize { params } => self.initialize(params).await,
                                Command::Deinitialize => self.deinitialize().await,
                                Command::StartRanging => self.start_ranging().await,
                                Command::StopRanging => self.stop_ranging().await,
                                Command::Reconfigure { params } => self.reconfigure(params).await,
                                Command::UpdateControllerMulticastList {
                                    action,
                                    controlees,
                                    notf_receiver,
                                } => {
                                    self.update_controller_multicast_list(
                                        action,
                                        controlees,
                                        notf_receiver,
                                    )
                                    .await
                                },
                                Command::GetParams => self.params().await,
                            };
                            let _ = result_sender.send(result);
                        }
                    }
                }
            }
        }
    }

    async fn initialize(&mut self, params: AppConfigParams) -> Result<Response> {
        debug_assert!(*self.state_receiver.borrow() == SessionState::SessionStateDeinit);

        // TODO(b/279669973): Support CR-461 fully here. Need to wait for session init rsp.
        // But, that does not seem to be fully plumbed up in session_manager yet.
        self.uci_manager.session_init(self.session_id, self.session_type).await?;
        self.wait_state(SessionState::SessionStateInit).await?;

        self.reconfigure(params).await?;
        self.wait_state(SessionState::SessionStateIdle).await?;

        Ok(Response::Null)
    }

    async fn deinitialize(&mut self) -> Result<Response> {
        self.uci_manager.session_deinit(self.session_id).await?;
        Ok(Response::Null)
    }

    async fn start_ranging(&mut self) -> Result<Response> {
        let state = *self.state_receiver.borrow();
        match state {
            SessionState::SessionStateActive => {
                warn!("Session {} is already running", self.session_id);
                Err(Error::BadParameters)
            }
            SessionState::SessionStateIdle => {
                self.uci_manager.range_start(self.session_id).await?;
                self.wait_state(SessionState::SessionStateActive).await?;

                let params = if self.session_type != SessionType::Ccc {
                    // self.params should be Some() in this state.
                    self.params.clone().unwrap()
                } else {
                    // Get the CCC specific app config after ranging started.
                    let tlvs = self
                        .uci_manager
                        .session_get_app_config(self.session_id, vec![])
                        .await
                        .map_err(|e| {
                            error!("Failed to get CCC app config after start ranging: {:?}", e);
                            e
                        })?;
                    let config_map = HashMap::from_iter(tlvs.into_iter().map(|tlv| {
                        let tlv = tlv.into_inner();
                        (tlv.cfg_id, tlv.v.clone())
                    }));
                    let params = CccStartedAppConfigParams::from_config_map(config_map)
                        .ok_or_else(|| {
                            error!("Failed to generate CccStartedAppConfigParams");
                            Error::Unknown
                        })?;
                    AppConfigParams::CccStarted(params)
                };
                Ok(Response::AppConfigParams(params))
            }
            _ => {
                error!("Session {} cannot start running at {:?}", self.session_id, state);
                Err(Error::BadParameters)
            }
        }
    }

    async fn stop_ranging(&mut self) -> Result<Response> {
        let state = *self.state_receiver.borrow();
        match state {
            SessionState::SessionStateIdle => {
                warn!("Session {} is already stopped", self.session_id);
                Ok(Response::Null)
            }
            SessionState::SessionStateActive => {
                self.uci_manager.range_stop(self.session_id).await?;
                self.wait_state(SessionState::SessionStateIdle).await?;

                Ok(Response::Null)
            }
            _ => {
                error!("Session {} cannot stop running at {:?}", self.session_id, state);
                Err(Error::BadParameters)
            }
        }
    }

    async fn reconfigure(&mut self, params: AppConfigParams) -> Result<Response> {
        debug_assert!(*self.state_receiver.borrow() != SessionState::SessionStateDeinit);

        let state = *self.state_receiver.borrow();
        let tlvs = match self.params.as_ref() {
            Some(prev_params) => {
                if let Some(tlvs) = params.generate_updated_tlvs(prev_params, state) {
                    tlvs
                } else {
                    error!("Cannot update the app config at state {:?}: {:?}", state, params);
                    return Err(Error::BadParameters);
                }
            }
            None => params.generate_tlvs(),
        };

        let result = self.uci_manager.session_set_app_config(self.session_id, tlvs).await?;
        for config_status in result.config_status.iter() {
            warn!(
                "AppConfig {:?} is not applied: {:?}",
                config_status.cfg_id, config_status.status
            );
        }
        if let Err(e) = status_code_to_result(result.status) {
            error!("Failed to set app_config. StatusCode: {:?}", result.status);
            return Err(e);
        }

        self.params = Some(params);
        Ok(Response::Null)
    }

    async fn update_controller_multicast_list(
        &mut self,
        action: UpdateMulticastListAction,
        controlees: Vec<Controlee>,
        notf_receiver: oneshot::Receiver<Vec<ControleeStatus>>,
    ) -> Result<Response> {
        if self.session_type == SessionType::Ccc {
            error!("Cannot update multicast list for CCC session");
            return Err(Error::BadParameters);
        }

        let state = *self.state_receiver.borrow();
        if !matches!(state, SessionState::SessionStateIdle | SessionState::SessionStateActive) {
            error!("Cannot update multicast list at state {:?}", state);
            return Err(Error::BadParameters);
        }

        self.uci_manager
            .session_update_controller_multicast_list(
                self.session_id,
                action,
                Controlees::NoSessionKey(controlees),
            )
            .await?;

        // Wait for the notification of the update status.
        let results = timeout(Duration::from_millis(NOTIFICATION_TIMEOUT_MS), notf_receiver)
            .await
            .map_err(|_| {
                error!("Timeout waiting for the multicast list notification");
                Error::Timeout
            })?
            .map_err(|_| {
                error!("oneshot sender is dropped.");
                Error::Unknown
            })?;

        // Check the update status for adding new controlees.
        if action == UpdateMulticastListAction::AddControlee {
            for result in results.iter() {
                if result.status != MulticastUpdateStatusCode::StatusOkMulticastListUpdate {
                    error!("Failed to update multicast list: {:?}", result);
                    return Err(Error::Unknown);
                }
            }
        }

        Ok(Response::Null)
    }

    async fn wait_state(&mut self, expected_state: SessionState) -> Result<()> {
        // Wait for the notification of the session status.
        timeout(Duration::from_millis(NOTIFICATION_TIMEOUT_MS), self.state_receiver.changed())
            .await
            .map_err(|_| {
                error!("Timeout waiting for the session status notification");
                Error::Timeout
            })?
            .map_err(|_| {
                debug!("UwbSession is about to drop.");
                Error::Unknown
            })?;

        // Check if the latest session status is expected or not.
        let state = *self.state_receiver.borrow();
        if state != expected_state {
            error!(
                "Transit to wrong Session state {:?}. The expected state is {:?}",
                state, expected_state
            );
            return Err(Error::BadParameters);
        }

        Ok(())
    }

    async fn params(&mut self) -> Result<Response> {
        match &self.params {
            None => Err(Error::BadParameters),
            Some(params) => Ok(Response::AppConfigParams(params.clone())),
        }
    }
}

enum Command {
    Initialize {
        params: AppConfigParams,
    },
    Deinitialize,
    StartRanging,
    StopRanging,
    Reconfigure {
        params: AppConfigParams,
    },
    UpdateControllerMulticastList {
        action: UpdateMulticastListAction,
        controlees: Vec<Controlee>,
        notf_receiver: oneshot::Receiver<Vec<ControleeStatus>>,
    },
    GetParams,
}
