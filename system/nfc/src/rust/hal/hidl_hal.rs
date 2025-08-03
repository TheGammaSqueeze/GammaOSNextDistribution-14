// Copyright 2021, The Android Open Source Project
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

//! Implementation of the HAl that talks to NFC controller over Android's HIDL
use crate::internal::InnerHal;
#[allow(unused)]
use crate::{is_control_packet, Hal, HalEvent, HalEventRegistry, HalEventStatus, Result};
use log::{debug, error};
use nfc_packets::nci::{DataPacket, NciPacket};
use pdl_runtime::Packet;
use std::sync::Mutex;
use tokio::select;
use tokio::sync::mpsc::{UnboundedReceiver, UnboundedSender};
use tokio::sync::oneshot;

/// Initialize the module
pub async fn init() -> Hal {
    let (raw_hal, inner_hal) = InnerHal::new();
    let (hal_open_evt_tx, hal_open_evt_rx) = oneshot::channel::<ffi::NfcStatus>();
    let (hal_close_evt_tx, hal_close_evt_rx) = oneshot::channel::<ffi::NfcStatus>();
    *CALLBACKS.lock().unwrap() = Some(Callbacks {
        hal_open_evt_tx: Some(hal_open_evt_tx),
        hal_close_evt_tx: Some(hal_close_evt_tx),
        in_cmd_tx: inner_hal.in_cmd_tx,
        in_data_tx: inner_hal.in_data_tx,
    });
    ffi::start_hal();
    hal_open_evt_rx.await.unwrap();

    tokio::spawn(dispatch_outgoing(
        raw_hal.hal_events.clone(),
        inner_hal.out_cmd_rx,
        inner_hal.out_data_rx,
        hal_close_evt_rx,
    ));

    raw_hal
}

#[cxx::bridge(namespace = nfc::hal)]
// TODO Either use or remove these functions, this shouldn't be the long term state
#[allow(dead_code)]
#[allow(unsafe_op_in_unsafe_fn)]
mod ffi {

    #[repr(u32)]
    #[derive(Debug)]
    enum NfcEvent {
        OPEN_CPLT = 0,
        CLOSE_CPLT = 1,
        POST_INIT_CPLT = 2,
        PRE_DISCOVER_CPLT = 3,
        REQUEST_CONTROL = 4,
        RELEASE_CONTROL = 5,
        ERROR = 6,
        HCI_NETWORK_RESET = 7,
    }

    #[repr(u32)]
    #[derive(Debug)]
    enum NfcStatus {
        OK = 0,
        FAILED = 1,
        ERR_TRANSPORT = 2,
        ERR_CMD_TIMEOUT = 3,
        REFUSED = 4,
    }

    unsafe extern "C++" {
        include!("hal/ffi/hidl.h");
        fn start_hal();
        fn stop_hal();
        fn send_command(data: &[u8]);

        #[namespace = "android::hardware::nfc::V1_1"]
        type NfcEvent;

        #[namespace = "android::hardware::nfc::V1_0"]
        type NfcStatus;
    }

    extern "Rust" {
        fn on_event(evt: NfcEvent, status: NfcStatus);
        fn on_data(data: &[u8]);
    }
}

impl From<ffi::NfcStatus> for HalEventStatus {
    fn from(ffi_nfc_status: ffi::NfcStatus) -> Self {
        match ffi_nfc_status {
            ffi::NfcStatus::OK => HalEventStatus::Success,
            ffi::NfcStatus::FAILED => HalEventStatus::Failed,
            ffi::NfcStatus::ERR_TRANSPORT => HalEventStatus::TransportError,
            ffi::NfcStatus::ERR_CMD_TIMEOUT => HalEventStatus::Timeout,
            ffi::NfcStatus::REFUSED => HalEventStatus::Refused,
            _ => HalEventStatus::Failed,
        }
    }
}

struct Callbacks {
    hal_open_evt_tx: Option<oneshot::Sender<ffi::NfcStatus>>,
    hal_close_evt_tx: Option<oneshot::Sender<ffi::NfcStatus>>,
    in_cmd_tx: UnboundedSender<NciPacket>,
    in_data_tx: UnboundedSender<DataPacket>,
}

static CALLBACKS: Mutex<Option<Callbacks>> = Mutex::new(None);

fn on_event(evt: ffi::NfcEvent, status: ffi::NfcStatus) {
    debug!("got event: {:?} with status {:?}", evt, status);
    let mut callbacks = CALLBACKS.lock().unwrap();
    match evt {
        ffi::NfcEvent::OPEN_CPLT => {
            if let Some(evt_tx) = callbacks.as_mut().unwrap().hal_open_evt_tx.take() {
                evt_tx.send(status).unwrap();
            }
        }
        ffi::NfcEvent::CLOSE_CPLT => {
            if let Some(evt_tx) = callbacks.as_mut().unwrap().hal_close_evt_tx.take() {
                evt_tx.send(status).unwrap();
            }
        }
        _ => error!("Unhandled HAL event {:?}", evt),
    }
}

fn on_data(data: &[u8]) {
    debug!("got packet: {:02x?}", data);
    let callbacks = CALLBACKS.lock().unwrap();
    if is_control_packet(data) {
        match NciPacket::parse(data) {
            Ok(p) => callbacks.as_ref().unwrap().in_cmd_tx.send(p).unwrap(),
            Err(e) => error!("failure to parse response: {:?} data: {:02x?}", e, data),
        }
    } else {
        match DataPacket::parse(data) {
            Ok(p) => callbacks.as_ref().unwrap().in_data_tx.send(p).unwrap(),
            Err(e) => error!("failure to parse response: {:?} data: {:02x?}", e, data),
        }
    }
}

async fn dispatch_outgoing(
    mut hal_events: HalEventRegistry,
    mut out_cmd_rx: UnboundedReceiver<NciPacket>,
    mut out_data_rx: UnboundedReceiver<DataPacket>,
    hal_close_evt_rx: oneshot::Receiver<ffi::NfcStatus>,
) {
    loop {
        select! {
            Some(cmd) = out_cmd_rx.recv() => ffi::send_command(&cmd.to_bytes()),
            Some(data) = out_data_rx.recv() => ffi::send_command(&data.to_bytes()),
            else => break,
        }
    }
    ffi::stop_hal();
    let status = hal_close_evt_rx.await.unwrap();
    if let Some(evt) = hal_events.unregister(HalEvent::CloseComplete).await {
        evt.send(HalEventStatus::from(status)).unwrap();
    }
}
