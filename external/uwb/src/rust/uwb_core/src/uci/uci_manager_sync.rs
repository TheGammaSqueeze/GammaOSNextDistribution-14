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

//! This module offers a synchornized interface at UCI level.
//!
//! The module is designed with the replacement for Android UCI JNI adaptation in mind. The handling
//! of UciNotifications is different in UciManager and UciManagerSyncImpl as the sync version has
//! its behavior aligned with the Android JNI UCI, and routes the UciNotifications to
//! NotificationManager.

use log::{debug, error};
use tokio::runtime::{Builder as RuntimeBuilder, Handle};
use tokio::sync::mpsc;
use tokio::task;

use crate::error::{Error, Result};
use crate::params::{
    AndroidRadarConfigResponse, AppConfigTlv, AppConfigTlvType, CapTlv, CoreSetConfigResponse,
    CountryCode, DeviceConfigId, DeviceConfigTlv, GetDeviceInfoResponse, PowerStats,
    RadarConfigTlv, RadarConfigTlvType, RawUciMessage, ResetConfig, SessionId, SessionState,
    SessionType, SessionUpdateDtTagRangingRoundsResponse, SetAppConfigResponse,
    UpdateMulticastListAction, UpdateTime,
};
#[cfg(any(test, feature = "mock-utils"))]
use crate::uci::mock_uci_manager::MockUciManager;
use crate::uci::notification::{
    CoreNotification, DataRcvNotification, RadarDataRcvNotification, SessionNotification,
};
use crate::uci::uci_hal::UciHal;
use crate::uci::uci_logger::{UciLogger, UciLoggerMode};
use crate::uci::uci_manager::{UciManager, UciManagerImpl};
use uwb_uci_packets::{Controlees, PhaseList};

/// The NotificationManager processes UciNotification relayed from UciManagerSync in a sync fashion.
/// The UciManagerSync assumes the NotificationManager takes the responsibility to properly handle
/// the notifications, including tracking the state of HAL. UciManagerSync and lower levels only
/// redirect and categorize the notifications. The notifications are processed through callbacks.
/// NotificationManager can be !Send and !Sync, as interfacing with other programs may require.
pub trait NotificationManager: 'static {
    /// Callback for CoreNotification.
    fn on_core_notification(&mut self, core_notification: CoreNotification) -> Result<()>;

    /// Callback for SessionNotification.
    fn on_session_notification(&mut self, session_notification: SessionNotification) -> Result<()>;

    /// Callback for RawUciMessage.
    fn on_vendor_notification(&mut self, vendor_notification: RawUciMessage) -> Result<()>;

    /// Callback for DataRcvNotification.
    fn on_data_rcv_notification(
        &mut self,
        data_rcv_notification: DataRcvNotification,
    ) -> Result<()>;

    /// Callback for RadarDataRcvNotification.
    fn on_radar_data_rcv_notification(
        &mut self,
        radar_data_rcv_notification: RadarDataRcvNotification,
    ) -> Result<()>;
}

/// Builder for NotificationManager. Builder is sent between threads.
pub trait NotificationManagerBuilder: 'static + Send + Sync {
    /// Type of NotificationManager built.
    type NotificationManager: NotificationManager;
    /// Builds NotificationManager. The build operation Consumes Builder.
    fn build(self) -> Option<Self::NotificationManager>;
}

struct NotificationDriver<U: NotificationManager> {
    core_notification_receiver: mpsc::UnboundedReceiver<CoreNotification>,
    session_notification_receiver: mpsc::UnboundedReceiver<SessionNotification>,
    vendor_notification_receiver: mpsc::UnboundedReceiver<RawUciMessage>,
    data_rcv_notification_receiver: mpsc::UnboundedReceiver<DataRcvNotification>,
    radar_data_rcv_notification_receiver: mpsc::UnboundedReceiver<RadarDataRcvNotification>,
    notification_manager: U,
}
impl<U: NotificationManager> NotificationDriver<U> {
    fn new(
        core_notification_receiver: mpsc::UnboundedReceiver<CoreNotification>,
        session_notification_receiver: mpsc::UnboundedReceiver<SessionNotification>,
        vendor_notification_receiver: mpsc::UnboundedReceiver<RawUciMessage>,
        data_rcv_notification_receiver: mpsc::UnboundedReceiver<DataRcvNotification>,
        radar_data_rcv_notification_receiver: mpsc::UnboundedReceiver<RadarDataRcvNotification>,
        notification_manager: U,
    ) -> Self {
        Self {
            core_notification_receiver,
            session_notification_receiver,
            vendor_notification_receiver,
            data_rcv_notification_receiver,
            radar_data_rcv_notification_receiver,
            notification_manager,
        }
    }
    async fn run(&mut self) {
        loop {
            tokio::select! {
                Some(ntf) = self.core_notification_receiver.recv() =>{
                    self.notification_manager.on_core_notification(ntf).unwrap_or_else(|e|{
                        error!("NotificationDriver: CoreNotification callback error: {:?}",e);
                    });
                }
                Some(ntf) = self.session_notification_receiver.recv() =>{
                    self.notification_manager.on_session_notification(ntf).unwrap_or_else(|e|{
                        error!("NotificationDriver: SessionNotification callback error: {:?}",e);
                    });
                }
                Some(ntf) = self.vendor_notification_receiver.recv() =>{
                    self.notification_manager.on_vendor_notification(ntf).unwrap_or_else(|e|{
                        error!("NotificationDriver: RawUciMessage callback error: {:?}",e);
                });
                }
                Some(data) = self.data_rcv_notification_receiver.recv() =>{
                    self.notification_manager.on_data_rcv_notification(data).unwrap_or_else(|e|{
                        error!("NotificationDriver: OnDataRcv callback error: {:?}",e);
                });
                }
                Some(data) = self.radar_data_rcv_notification_receiver.recv() =>{
                    self.notification_manager.on_radar_data_rcv_notification(data).unwrap_or_else(|e|{
                        error!("NotificationDriver: OnRadarDataRcv callback error: {:?}",e);
                });
                }
                else =>{
                    debug!("NotificationDriver dropping.");
                    break;
                }
            }
        }
    }
}

/// The UciManagerSync provides a synchornized version of UciManager.
///
/// Note the processing of UciNotification is different:
/// set_X_notification_sender methods are removed. Instead, the method
/// redirect_notification(NotificationManagerBuilder) is introduced to avoid the
/// exposure of async tokio::mpsc.
pub struct UciManagerSync<U: UciManager> {
    runtime_handle: Handle,
    uci_manager: U,
}
impl<U: UciManager> UciManagerSync<U> {
    /// Redirects notification to a new NotificationManager using the notification_manager_builder.
    /// The NotificationManager will live on a separate thread.
    pub fn redirect_notification<T: NotificationManagerBuilder>(
        &mut self,
        notification_manager_builder: T,
    ) -> Result<()> {
        let (core_notification_sender, core_notification_receiver) =
            mpsc::unbounded_channel::<CoreNotification>();
        let (session_notification_sender, session_notification_receiver) =
            mpsc::unbounded_channel::<SessionNotification>();
        let (vendor_notification_sender, vendor_notification_receiver) =
            mpsc::unbounded_channel::<RawUciMessage>();
        let (data_rcv_notification_sender, data_rcv_notification_receiver) =
            mpsc::unbounded_channel::<DataRcvNotification>();
        let (radar_data_rcv_notification_sender, radar_data_rcv_notification_receiver) =
            mpsc::unbounded_channel::<RadarDataRcvNotification>();
        self.runtime_handle.to_owned().block_on(async {
            self.uci_manager.set_core_notification_sender(core_notification_sender).await;
            self.uci_manager.set_session_notification_sender(session_notification_sender).await;
            self.uci_manager.set_vendor_notification_sender(vendor_notification_sender).await;
            self.uci_manager.set_data_rcv_notification_sender(data_rcv_notification_sender).await;
            self.uci_manager
                .set_radar_data_rcv_notification_sender(radar_data_rcv_notification_sender)
                .await;
        });
        // The potentially !Send NotificationManager is created in a separate thread.
        let (driver_status_sender, mut driver_status_receiver) = mpsc::unbounded_channel::<bool>();
        std::thread::spawn(move || {
            let notification_runtime =
                match RuntimeBuilder::new_current_thread().enable_all().build() {
                    Ok(nr) => nr,
                    Err(_) => {
                        // unwrap safe since receiver is in scope
                        driver_status_sender.send(false).unwrap();
                        return;
                    }
                };

            let local = task::LocalSet::new();
            let notification_manager = match notification_manager_builder.build() {
                Some(nm) => {
                    // unwrap safe since receiver is in scope
                    driver_status_sender.send(true).unwrap();
                    nm
                }
                None => {
                    // unwrap safe since receiver is in scope
                    driver_status_sender.send(false).unwrap();
                    return;
                }
            };
            let mut notification_driver = NotificationDriver::new(
                core_notification_receiver,
                session_notification_receiver,
                vendor_notification_receiver,
                data_rcv_notification_receiver,
                radar_data_rcv_notification_receiver,
                notification_manager,
            );
            local.spawn_local(async move {
                task::spawn_local(async move { notification_driver.run().await }).await.unwrap();
            });
            notification_runtime.block_on(local);
        });
        match driver_status_receiver.blocking_recv() {
            Some(true) => Ok(()),
            _ => Err(Error::Unknown),
        }
    }

    /// Set logger mode.
    pub fn set_logger_mode(&self, logger_mode: UciLoggerMode) -> Result<()> {
        self.runtime_handle.block_on(self.uci_manager.set_logger_mode(logger_mode))
    }
    /// Start UCI HAL and blocking until UCI commands can be sent.
    pub fn open_hal(&self) -> Result<GetDeviceInfoResponse> {
        self.runtime_handle.block_on(self.uci_manager.open_hal())
    }

    /// Stop the UCI HAL.
    pub fn close_hal(&self, force: bool) -> Result<()> {
        self.runtime_handle.block_on(self.uci_manager.close_hal(force))
    }

    // Methods for sending UCI commands. Functions are blocked until UCI response is received.
    /// Send UCI command for device reset.
    pub fn device_reset(&self, reset_config: ResetConfig) -> Result<()> {
        self.runtime_handle.block_on(self.uci_manager.device_reset(reset_config))
    }

    /// Send UCI command for getting device info.
    pub fn core_get_device_info(&self) -> Result<GetDeviceInfoResponse> {
        self.runtime_handle.block_on(self.uci_manager.core_get_device_info())
    }

    /// Send UCI command for getting capability info
    pub fn core_get_caps_info(&self) -> Result<Vec<CapTlv>> {
        self.runtime_handle.block_on(self.uci_manager.core_get_caps_info())
    }

    /// Send UCI command for setting core configuration.
    pub fn core_set_config(
        &self,
        config_tlvs: Vec<DeviceConfigTlv>,
    ) -> Result<CoreSetConfigResponse> {
        self.runtime_handle.block_on(self.uci_manager.core_set_config(config_tlvs))
    }

    /// Send UCI command for getting core configuration.
    pub fn core_get_config(&self, config_ids: Vec<DeviceConfigId>) -> Result<Vec<DeviceConfigTlv>> {
        self.runtime_handle.block_on(self.uci_manager.core_get_config(config_ids))
    }

    /// Send UCI command for getting uwbs timestamp.
    pub fn core_query_uwb_timestamp(&self) -> Result<u64> {
        self.runtime_handle.block_on(self.uci_manager.core_query_uwb_timestamp())
    }

    /// Send UCI command for initiating session.
    pub fn session_init(&self, session_id: SessionId, session_type: SessionType) -> Result<()> {
        self.runtime_handle.block_on(self.uci_manager.session_init(session_id, session_type))
    }

    /// Send UCI command for deinitiating session.
    pub fn session_deinit(&self, session_id: SessionId) -> Result<()> {
        self.runtime_handle.block_on(self.uci_manager.session_deinit(session_id))
    }

    /// Send UCI command for setting app config.
    pub fn session_set_app_config(
        &self,
        session_id: SessionId,
        config_tlvs: Vec<AppConfigTlv>,
    ) -> Result<SetAppConfigResponse> {
        self.runtime_handle
            .block_on(self.uci_manager.session_set_app_config(session_id, config_tlvs))
    }

    /// Send UCI command for getting app config.
    pub fn session_get_app_config(
        &self,
        session_id: SessionId,
        config_ids: Vec<AppConfigTlvType>,
    ) -> Result<Vec<AppConfigTlv>> {
        self.runtime_handle
            .block_on(self.uci_manager.session_get_app_config(session_id, config_ids))
    }

    /// Send UCI command for getting count of sessions.
    pub fn session_get_count(&self) -> Result<u8> {
        self.runtime_handle.block_on(self.uci_manager.session_get_count())
    }

    /// Send UCI command for getting state of session.
    pub fn session_get_state(&self, session_id: SessionId) -> Result<SessionState> {
        self.runtime_handle.block_on(self.uci_manager.session_get_state(session_id))
    }

    /// Send UCI command for updating multicast list for multicast session.
    pub fn session_update_controller_multicast_list(
        &self,
        session_id: SessionId,
        action: UpdateMulticastListAction,
        controlees: Controlees,
    ) -> Result<()> {
        self.runtime_handle.block_on(
            self.uci_manager
                .session_update_controller_multicast_list(session_id, action, controlees),
        )
    }

    /// Update ranging rounds for DT Tag
    pub fn session_update_dt_tag_ranging_rounds(
        &self,
        session_id: u32,
        ranging_round_indexes: Vec<u8>,
    ) -> Result<SessionUpdateDtTagRangingRoundsResponse> {
        self.runtime_handle.block_on(
            self.uci_manager
                .session_update_dt_tag_ranging_rounds(session_id, ranging_round_indexes),
        )
    }

    /// Send UCI command for getting max data size for session.
    pub fn session_query_max_data_size(&self, session_id: SessionId) -> Result<u16> {
        self.runtime_handle.block_on(self.uci_manager.session_query_max_data_size(session_id))
    }

    /// Send UCI command for starting ranging of the session.
    pub fn range_start(&self, session_id: SessionId) -> Result<()> {
        self.runtime_handle.block_on(self.uci_manager.range_start(session_id))
    }

    /// Send UCI command for stopping ranging of the session.
    pub fn range_stop(&self, session_id: SessionId) -> Result<()> {
        self.runtime_handle.block_on(self.uci_manager.range_stop(session_id))
    }

    /// Send UCI command for getting ranging count.
    pub fn range_get_ranging_count(&self, session_id: SessionId) -> Result<usize> {
        self.runtime_handle.block_on(self.uci_manager.range_get_ranging_count(session_id))
    }

    /// Set the country code. Android-specific method.
    pub fn android_set_country_code(&self, country_code: CountryCode) -> Result<()> {
        self.runtime_handle.block_on(self.uci_manager.android_set_country_code(country_code))
    }

    /// Get the power statistics. Android-specific method.
    pub fn android_get_power_stats(&self) -> Result<PowerStats> {
        self.runtime_handle.block_on(self.uci_manager.android_get_power_stats())
    }

    /// Set radar config. Android-specific method.
    pub fn android_set_radar_config(
        &self,
        session_id: SessionId,
        config_tlvs: Vec<RadarConfigTlv>,
    ) -> Result<AndroidRadarConfigResponse> {
        self.runtime_handle
            .block_on(self.uci_manager.android_set_radar_config(session_id, config_tlvs))
    }

    /// Get radar config. Android-specific method.
    pub fn android_get_radar_config(
        &self,
        session_id: SessionId,
        config_ids: Vec<RadarConfigTlvType>,
    ) -> Result<Vec<RadarConfigTlv>> {
        self.runtime_handle
            .block_on(self.uci_manager.android_get_radar_config(session_id, config_ids))
    }

    /// Send a raw UCI command.
    pub fn raw_uci_cmd(
        &self,
        mt: u32,
        gid: u32,
        oid: u32,
        payload: Vec<u8>,
    ) -> Result<RawUciMessage> {
        self.runtime_handle.block_on(self.uci_manager.raw_uci_cmd(mt, gid, oid, payload))
    }

    /// Send a data packet
    pub fn send_data_packet(
        &self,
        session_id: SessionId,
        address: Vec<u8>,
        uci_sequence_num: u16,
        app_payload_data: Vec<u8>,
    ) -> Result<()> {
        self.runtime_handle.block_on(self.uci_manager.send_data_packet(
            session_id,
            address,
            uci_sequence_num,
            app_payload_data,
        ))
    }

    /// Get session token for session id.
    pub fn get_session_token(&self, session_id: SessionId) -> Result<u32> {
        self.runtime_handle.block_on(self.uci_manager.get_session_token_from_session_id(session_id))
    }

    /// Send UCI command for setting hybrid configuration
    pub fn session_set_hybrid_config(
        &self,
        session_id: SessionId,
        number_of_phases: u8,
        update_time: UpdateTime,
        phase_list: Vec<PhaseList>,
    ) -> Result<()> {
        self.runtime_handle.block_on(self.uci_manager.session_set_hybrid_config(
            session_id,
            number_of_phases,
            update_time,
            phase_list,
        ))
    }

    /// Send UCI command for session data transfer phase config
    pub fn session_data_transfer_phase_config(
        &self,
        session_id: SessionId,
        dtpcm_repetition: u8,
        data_transfer_control: u8,
        dtpml_size: u8,
        mac_address: Vec<u8>,
        slot_bitmap: Vec<u8>,
    ) -> Result<()> {
        self.runtime_handle.block_on(self.uci_manager.session_data_transfer_phase_config(
            session_id,
            dtpcm_repetition,
            data_transfer_control,
            dtpml_size,
            mac_address,
            slot_bitmap,
        ))
    }
}

impl UciManagerSync<UciManagerImpl> {
    /// Constructor.
    ///
    /// UciHal and NotificationManagerBuilder required at construction as they are required before
    /// open_hal is called. runtime_handle must be a Handle to a multithread runtime that outlives
    /// UciManagerSyncImpl.
    ///
    /// Implementation note: An explicit decision is made to not use UciManagerImpl as a parameter.
    /// UciManagerImpl::new() appears to be sync, but needs an async context to be called, but the
    /// user is unlikely to be aware of this technicality.
    pub fn new<H, B, L>(
        hal: H,
        notification_manager_builder: B,
        logger: L,
        logger_mode: UciLoggerMode,
        runtime_handle: Handle,
    ) -> Result<Self>
    where
        H: UciHal,
        B: NotificationManagerBuilder,
        L: UciLogger,
    {
        // UciManagerImpl::new uses tokio::spawn, so it is called inside the runtime as async fn.
        let uci_manager =
            runtime_handle.block_on(async { UciManagerImpl::new(hal, logger, logger_mode) });
        let mut uci_manager_sync = UciManagerSync { runtime_handle, uci_manager };
        uci_manager_sync.redirect_notification(notification_manager_builder)?;
        Ok(uci_manager_sync)
    }
}

#[cfg(any(test, feature = "mock-utils"))]
impl UciManagerSync<MockUciManager> {
    /// Constructor for mock version.
    pub fn new_mock<T: NotificationManagerBuilder>(
        uci_manager: MockUciManager,
        runtime_handle: Handle,
        notification_manager_builder: T,
    ) -> Result<Self> {
        let mut uci_manager_sync = UciManagerSync { uci_manager, runtime_handle };
        uci_manager_sync.redirect_notification(notification_manager_builder)?;
        Ok(uci_manager_sync)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    use std::cell::RefCell;
    use std::rc::Rc;

    use tokio::runtime::Builder;
    use uwb_uci_packets::DeviceState::DeviceStateReady;

    use crate::params::uci_packets::GetDeviceInfoResponse;
    use crate::uci::mock_uci_manager::MockUciManager;
    use crate::uci::{CoreNotification, UciNotification};
    use uwb_uci_packets::StatusCode::UciStatusOk;

    /// Mock NotificationManager forwarding notifications received.
    /// The nonsend_counter is deliberately !send to check UciManagerSync::redirect_notification.
    struct MockNotificationManager {
        notf_sender: mpsc::UnboundedSender<UciNotification>,
        // nonsend_counter is an example of a !Send property.
        nonsend_counter: Rc<RefCell<usize>>,
    }

    impl NotificationManager for MockNotificationManager {
        fn on_core_notification(&mut self, core_notification: CoreNotification) -> Result<()> {
            self.nonsend_counter.replace_with(|&mut prev| prev + 1);
            self.notf_sender
                .send(UciNotification::Core(core_notification))
                .map_err(|_| Error::Unknown)
        }
        fn on_session_notification(
            &mut self,
            session_notification: SessionNotification,
        ) -> Result<()> {
            self.nonsend_counter.replace_with(|&mut prev| prev + 1);
            self.notf_sender
                .send(UciNotification::Session(session_notification))
                .map_err(|_| Error::Unknown)
        }
        fn on_vendor_notification(&mut self, vendor_notification: RawUciMessage) -> Result<()> {
            self.nonsend_counter.replace_with(|&mut prev| prev + 1);
            self.notf_sender
                .send(UciNotification::Vendor(vendor_notification))
                .map_err(|_| Error::Unknown)
        }
        fn on_data_rcv_notification(&mut self, _data_rcv_notf: DataRcvNotification) -> Result<()> {
            self.nonsend_counter.replace_with(|&mut prev| prev + 1);
            Ok(())
        }
        fn on_radar_data_rcv_notification(
            &mut self,
            _data_rcv_notf: RadarDataRcvNotification,
        ) -> Result<()> {
            self.nonsend_counter.replace_with(|&mut prev| prev + 1);
            Ok(())
        }
    }

    /// Builder for MockNotificationManager.
    struct MockNotificationManagerBuilder {
        notf_sender: mpsc::UnboundedSender<UciNotification>,
        // initial_count is an example for a parameter undetermined at compile time.
    }

    impl MockNotificationManagerBuilder {
        /// Constructor for builder.
        fn new(notf_sender: mpsc::UnboundedSender<UciNotification>) -> Self {
            Self { notf_sender }
        }
    }

    impl NotificationManagerBuilder for MockNotificationManagerBuilder {
        type NotificationManager = MockNotificationManager;

        fn build(self) -> Option<Self::NotificationManager> {
            Some(MockNotificationManager {
                notf_sender: self.notf_sender,
                nonsend_counter: Rc::new(RefCell::new(0)),
            })
        }
    }

    #[test]
    /// Tests that the Command, Response, and Notification pipeline are functional.
    fn test_sync_uci_basic_sequence() {
        let test_rt = Builder::new_multi_thread().enable_all().build().unwrap();
        let (notf_sender, mut notf_receiver) = mpsc::unbounded_channel::<UciNotification>();
        let mut uci_manager_impl = MockUciManager::new();
        let get_device_info_rsp = GetDeviceInfoResponse {
            status: UciStatusOk,
            uci_version: 0,
            mac_version: 0,
            phy_version: 0,
            uci_test_version: 0,
            vendor_spec_info: vec![],
        };

        uci_manager_impl.expect_open_hal(
            vec![UciNotification::Core(CoreNotification::DeviceStatus(DeviceStateReady))],
            Ok(get_device_info_rsp.clone()),
        );
        uci_manager_impl.expect_core_get_device_info(Ok(get_device_info_rsp));
        let uci_manager_sync = UciManagerSync::new_mock(
            uci_manager_impl,
            test_rt.handle().to_owned(),
            MockNotificationManagerBuilder::new(notf_sender),
        )
        .unwrap();
        assert!(uci_manager_sync.open_hal().is_ok());
        let device_state = test_rt.block_on(async { notf_receiver.recv().await });
        assert!(device_state.is_some());
        assert!(uci_manager_sync.core_get_device_info().is_ok());
    }
}
