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

//! Implements UciHal trait for Android.

use std::sync::Arc;

use android_hardware_uwb::aidl::android::hardware::uwb::{
    IUwb::IUwbAsync,
    IUwbChip::IUwbChipAsync,
    IUwbClientCallback::{BnUwbClientCallback, IUwbClientCallbackAsyncServer},
    UwbEvent::UwbEvent,
    UwbStatus::UwbStatus,
};
use android_hardware_uwb::binder::{
    BinderFeatures, DeathRecipient, ExceptionCode, IBinder, Interface, Result as BinderResult,
    Status as BinderStatus, Strong,
};
use async_trait::async_trait;
use binder_tokio::{Tokio, TokioRuntime};
use log::error;
use tokio::runtime::Handle;
use tokio::sync::{mpsc, Mutex};
use uwb_core::error::{Error as UwbCoreError, Result as UwbCoreResult};
use uwb_core::params::uci_packets::SessionId;
use uwb_core::uci::uci_hal::{UciHal, UciHalPacket};
use uwb_uci_packets::{DeviceState, DeviceStatusNtfBuilder};

use crate::error::{Error, Result};

fn input_uci_hal_packet<T: Into<uwb_uci_packets::UciControlPacket>>(
    builder: T,
) -> Vec<UciHalPacket> {
    let packets: Vec<uwb_uci_packets::UciControlPacketHal> = builder.into().into();
    packets.into_iter().map(|packet| packet.into()).collect()
}

/// Send device status notification with error state.
fn send_device_state_error_notification(
    uci_sender: &mpsc::UnboundedSender<UciHalPacket>,
) -> UwbCoreResult<()> {
    let raw_message_packets = input_uci_hal_packet(DeviceStatusNtfBuilder {
        device_state: DeviceState::DeviceStateError,
    });
    for raw_message_packet in raw_message_packets {
        if let Err(e) = uci_sender.send(raw_message_packet) {
            error!("Error sending device state error notification: {:?}", e);
            return Err(UwbCoreError::BadParameters);
        }
    }
    Ok(())
}

/// Redirects the raw UCI callbacks to UciHalAndroid and manages the HalEvent.
///
/// RawUciCallback Redirects Raw UCI callbacks upstream, and manages HalEvent.
/// RawUciCallback is declared as a seprate struct as a struct with IUwbClientCallbackAsyncServer
/// trait is consumed by BnUwbClientCallback, thus it cannot be implemented for UciHalAndroid.
#[derive(Clone, Debug)]
struct RawUciCallback {
    uci_sender: mpsc::UnboundedSender<UciHalPacket>,
    hal_open_result_sender: mpsc::Sender<Result<()>>,
    hal_close_result_sender: mpsc::Sender<Result<()>>,
}

impl RawUciCallback {
    pub fn new(
        uci_sender: mpsc::UnboundedSender<UciHalPacket>,
        hal_open_result_sender: mpsc::Sender<Result<()>>,
        hal_close_result_sender: mpsc::Sender<Result<()>>,
    ) -> Self {
        Self { uci_sender, hal_open_result_sender, hal_close_result_sender }
    }
}

impl Interface for RawUciCallback {}

#[async_trait]
impl IUwbClientCallbackAsyncServer for RawUciCallback {
    async fn onHalEvent(&self, event: UwbEvent, _event_status: UwbStatus) -> BinderResult<()> {
        match event {
            // UwbEvent::ERROR is processed differently by UciHalAndroid depending on its state.
            //
            // If error occurs before POST_INIT_CPLT received: UciHalAndroid handles the error.
            // If error occurs after POST_INIT_CPLT received: UciHalAndroid redirects the error
            // upstream by converting it to UCI DeviceStatusNtf.
            // Both are attempted as RawUciCallback is not aware of the state for UciHalAndroid.
            // Similarly, error due to close of hal cannot be reported as both the reason of the
            // error and expectation of UciHalAndroid are unknown.
            UwbEvent::ERROR => {
                // Error sending hal_open_result_sender is not meaningful, as RawUciCallback do not
                // know the reason for UwbEvent::ERROR. The receiving end only listens to
                // hal_open_result_sender when it is expecting POST_INIT_CPLT or ERROR.
                let _ = self.hal_open_result_sender.try_send(Err(Error::BinderStatus(
                    BinderStatus::new_exception(ExceptionCode::TRANSACTION_FAILED, None),
                )));

                send_device_state_error_notification(&self.uci_sender)
                    .map_err(|e| BinderStatus::from(Error::from(e)))
            }
            UwbEvent::POST_INIT_CPLT => self.hal_open_result_sender.try_send(Ok(())).map_err(|e| {
                error!("Failed sending POST_INIT_CPLT: {:?}", e);
                BinderStatus::new_exception(ExceptionCode::TRANSACTION_FAILED, None)
            }),
            UwbEvent::CLOSE_CPLT => self.hal_close_result_sender.try_send(Ok(())).map_err(|e| {
                error!("Failed sending CLOSE_CPLT: {:?}", e);
                BinderStatus::new_exception(ExceptionCode::TRANSACTION_FAILED, None)
            }),
            _ => Ok(()),
        }
    }

    async fn onUciMessage(&self, data: &[u8]) -> BinderResult<()> {
        self.uci_sender.send(data.to_owned()).map_err(|e| {
            error!("Failed sending UCI response or notification: {:?}", e);
            BinderStatus::new_exception(ExceptionCode::TRANSACTION_FAILED, None)
        })
    }
}

/// Implentation of UciHal trait for Android.
#[derive(Default)]
pub struct UciHalAndroid {
    chip_id: String,
    hal_close_result_receiver: Option<mpsc::Receiver<Result<()>>>,
    hal_death_recipient: Option<Arc<Mutex<DeathRecipient>>>,
    hal_uci_recipient: Option<Strong<dyn IUwbChipAsync<Tokio>>>,
}

#[allow(dead_code)]
impl UciHalAndroid {
    /// Constructor for empty UciHal.
    pub fn new(chip_id: &str) -> Self {
        Self {
            chip_id: chip_id.to_owned(),
            hal_close_result_receiver: None,
            hal_death_recipient: None,
            hal_uci_recipient: None,
        }
    }
}

#[async_trait]
impl UciHal for UciHalAndroid {
    /// Open the UCI HAL and power on the UWBS.
    async fn open(
        &mut self,
        packet_sender: mpsc::UnboundedSender<UciHalPacket>,
    ) -> UwbCoreResult<()> {
        // Returns error if UciHalAndroid is already open.
        if self.hal_uci_recipient.is_some() {
            return Err(UwbCoreError::BadParameters);
        }

        // Get hal service.
        let service_name = "android.hardware.uwb.IUwb/default";
        let i_uwb: Strong<dyn IUwbAsync<Tokio>> = binder_tokio::wait_for_interface(service_name)
            .await
            .map_err(|e| UwbCoreError::from(Error::from(e)))?;
        let chip_names = i_uwb.getChips().await.map_err(|e| UwbCoreError::from(Error::from(e)))?;
        if chip_names.is_empty() {
            error!("No UWB chip available.");
            return Err(UwbCoreError::BadParameters);
        }
        let chip_name: &str = match &self.chip_id == "default" {
            true => &chip_names[0],
            false => &self.chip_id,
        };
        if !chip_names.contains(&chip_name.to_string()) {
            return Err(UwbCoreError::BadParameters);
        }
        let i_uwb_chip = i_uwb
            .getChip(chip_name)
            .await
            .map_err(|e| UwbCoreError::from(Error::from(e)))?
            .into_async();

        // If the binder object unexpectedly goes away (typically because its hosting process has
        // been killed), then the `DeathRecipient`'s callback will be called.
        let packet_sender_clone = packet_sender.clone();
        let mut bare_death_recipient = DeathRecipient::new(move || {
            send_device_state_error_notification(&packet_sender_clone).unwrap_or_else(|e| {
                error!("Error sending device state error notification: {:?}", e);
            });
        });
        i_uwb_chip
            .as_binder()
            .link_to_death(&mut bare_death_recipient)
            .map_err(|e| UwbCoreError::from(Error::from(e)))?;

        // Connect callback to packet_sender.
        let (hal_open_result_sender, mut hal_open_result_receiver) = mpsc::channel::<Result<()>>(1);
        let (hal_close_result_sender, hal_close_result_receiver) = mpsc::channel::<Result<()>>(1);
        let m_cback = BnUwbClientCallback::new_async_binder(
            RawUciCallback::new(
                packet_sender.clone(),
                hal_open_result_sender,
                hal_close_result_sender,
            ),
            TokioRuntime(Handle::current()),
            BinderFeatures::default(),
        );
        i_uwb_chip.open(&m_cback).await.map_err(|e| UwbCoreError::from(Error::from(e)))?;
        // Initialize core and wait for POST_INIT_CPLT.
        i_uwb_chip.coreInit().await.map_err(|e| UwbCoreError::from(Error::from(e)))?;
        match hal_open_result_receiver.recv().await {
            Some(Ok(())) => {
                // Workaround while http://b/243140882 is not fixed:
                // Send DEVICE_STATE_READY notification as chip is not sending this notification.
                let device_ready_ntfs = input_uci_hal_packet(
                    DeviceStatusNtfBuilder { device_state: DeviceState::DeviceStateReady }.build(),
                );
                for device_ready_ntf in device_ready_ntfs {
                    packet_sender.send(device_ready_ntf).unwrap_or_else(|e| {
                        error!("UCI HAL: failed to send device ready notification: {:?}", e);
                    });
                }
                // End of workaround.
                self.hal_uci_recipient.replace(i_uwb_chip);
                self.hal_death_recipient.replace(Arc::new(Mutex::new(bare_death_recipient)));
                self.hal_close_result_receiver.replace(hal_close_result_receiver);
                Ok(())
            }
            _ => {
                error!("POST_INIT_CPLT event is not received");
                Err(UwbCoreError::Unknown)
            }
        }
    }

    async fn close(&mut self) -> UwbCoreResult<()> {
        // Reset UciHalAndroid regardless of whether hal_close is successful or not.
        let hal_uci_recipient = self.hal_uci_recipient.take();
        let _hal_death_recipient = self.hal_death_recipient.take();
        let hal_close_result_receiver = self.hal_close_result_receiver.take();
        match hal_uci_recipient {
            Some(i_uwb_chip) => {
                i_uwb_chip.close().await.map_err(|e| UwbCoreError::from(Error::from(e)))
            }
            None => Err(UwbCoreError::BadParameters),
        }?;
        match hal_close_result_receiver.unwrap().recv().await {
            // When RawUciCallback received an error due to this close() function, currently none of
            // the error messages will be triggered, and this close() will be pending until timeout,
            // as the reason for the UwbEvent::ERROR cannot be determined.
            Some(result) => result.map_err(|_| UwbCoreError::Unknown),
            None => Err(UwbCoreError::Unknown),
        }
    }

    async fn send_packet(&mut self, packet: UciHalPacket) -> UwbCoreResult<()> {
        match &self.hal_uci_recipient {
            Some(i_uwb_chip) => {
                i_uwb_chip
                    .sendUciMessage(&packet)
                    .await
                    .map_err(|e| UwbCoreError::from(Error::from(e)))?;
                Ok(())
            }
            None => Err(UwbCoreError::BadParameters),
        }
    }

    async fn notify_session_initialized(&mut self, session_id: SessionId) -> UwbCoreResult<()> {
        match &self.hal_uci_recipient {
            Some(i_uwb_chip) => {
                i_uwb_chip
                    // HAL API accepts signed int, so cast received session_id as i32.
                    .sessionInit(session_id as i32)
                    .await
                    .map_err(|e| UwbCoreError::from(Error::from(e)))?;
                Ok(())
            }
            None => Err(UwbCoreError::BadParameters),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_send_device_state_error_notification() {
        let (uci_sender, _) = mpsc::unbounded_channel();
        let res = send_device_state_error_notification(&uci_sender);
        assert_eq!(res, Err(UwbCoreError::BadParameters));
    }

    #[tokio::test]
    async fn test_new() {
        let chip_id = "test_chip_id";
        let hal = UciHalAndroid::new(chip_id);
        assert_eq!(hal.chip_id, chip_id);
        assert!(hal.hal_close_result_receiver.is_none());
        assert!(hal.hal_death_recipient.is_none());
        assert!(hal.hal_uci_recipient.is_none());
    }

    #[tokio::test]
    async fn test_open_error() {
        let chip_id = "test_chip_id";
        let mut hal = UciHalAndroid::new(chip_id);
        let packet_sender = mpsc::unbounded_channel().0;
        let res = hal.open(packet_sender).await;
        assert_eq!(res, Err(UwbCoreError::BadParameters));
    }

    #[tokio::test]
    async fn test_close() {
        let chip_id = "test_chip_id";
        let mut hal = UciHalAndroid::new(chip_id);
        let (_, receiver) = mpsc::channel::<Result<()>>(1);
        let death_recipient = Arc::new(Mutex::new(DeathRecipient::new(|| {})));
        hal.hal_close_result_receiver = Some(receiver);
        hal.hal_death_recipient = Some(death_recipient.clone());
        let res = hal.close().await;
        assert_eq!(res, Err(UwbCoreError::BadParameters));
        assert!(hal.hal_close_result_receiver.is_none());
        assert!(hal.hal_death_recipient.is_none());
        assert!(hal.hal_uci_recipient.is_none());
    }
}
