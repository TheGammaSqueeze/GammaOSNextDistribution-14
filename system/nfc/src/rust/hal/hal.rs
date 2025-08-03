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

//! NCI Hardware Abstraction Layer
//! Supports sending NCI commands to the HAL and receiving
//! NCI events from the HAL

use nfc_packets::nci::{DataPacket, NciPacket};
use std::collections::HashMap;
use std::sync::Arc;
use thiserror::Error;
use tokio::sync::mpsc::{UnboundedReceiver, UnboundedSender};
use tokio::sync::{oneshot, Mutex};

#[cfg(target_os = "android")]
#[path = "hidl_hal.rs"]
pub mod ihal;

#[cfg(not(target_os = "android"))]
#[path = "rootcanal_hal.rs"]
pub mod ihal;

/// HAL module interface
pub struct Hal {
    /// HAL events
    pub hal_events: HalEventRegistry,
    /// HAL outbound channel for Command messages
    pub out_cmd_tx: UnboundedSender<NciPacket>,
    /// HAL inbound channel for Response and Notification messages
    pub in_cmd_rx: UnboundedReceiver<NciPacket>,
    /// HAL outbound channel for Data messages
    pub out_data_tx: UnboundedSender<DataPacket>,
    /// HAL inbound channel for Data messages
    pub in_data_rx: UnboundedReceiver<DataPacket>,
}

/// Initialize the module and connect the channels
pub async fn init() -> Hal {
    ihal::init().await
}

/// NFC HAL specific events
#[derive(Debug, Hash, Eq, PartialEq, Clone, Copy)]
pub enum HalEvent {
    /// HAL CLOSE_CPLT event
    CloseComplete,
}

/// Status of a NFC HAL event
#[derive(Debug)]
pub enum HalEventStatus {
    /// HAL OK status
    Success,
    /// HAL FAILED status
    Failed,
    /// HAL ERR_TRANSPORT status
    TransportError,
    /// HAL ERR_CMD_TIMEOUT status
    Timeout,
    /// HAL REFUSED status
    Refused,
}

/// Provides ability to register and unregister for HAL event notifications
#[derive(Clone)]
pub struct HalEventRegistry {
    handlers: Arc<Mutex<HashMap<HalEvent, oneshot::Sender<HalEventStatus>>>>,
}

impl HalEventRegistry {
    /// Indicate interest in specific HAL event
    pub async fn register(&mut self, event: HalEvent, sender: oneshot::Sender<HalEventStatus>) {
        assert!(
            self.handlers.lock().await.insert(event, sender).is_none(),
            "A handler for {:?} is already registered",
            event
        );
    }

    /// Remove interest in specific HAL event
    pub async fn unregister(&mut self, event: HalEvent) -> Option<oneshot::Sender<HalEventStatus>> {
        self.handlers.lock().await.remove(&event)
    }
}

mod internal {
    use crate::{Hal, HalEventRegistry};
    use nfc_packets::nci::{DataPacket, NciPacket};
    use std::collections::HashMap;
    use std::sync::Arc;
    use tokio::sync::mpsc::{unbounded_channel, UnboundedReceiver, UnboundedSender};
    use tokio::sync::Mutex;

    pub struct InnerHal {
        pub out_cmd_rx: UnboundedReceiver<NciPacket>,
        pub in_cmd_tx: UnboundedSender<NciPacket>,
        pub out_data_rx: UnboundedReceiver<DataPacket>,
        pub in_data_tx: UnboundedSender<DataPacket>,
    }

    impl InnerHal {
        pub fn new() -> (Hal, Self) {
            let (out_cmd_tx, out_cmd_rx) = unbounded_channel();
            let (in_cmd_tx, in_cmd_rx) = unbounded_channel();
            let (out_data_tx, out_data_rx) = unbounded_channel();
            let (in_data_tx, in_data_rx) = unbounded_channel();
            let handlers = Arc::new(Mutex::new(HashMap::new()));
            let hal_events = HalEventRegistry { handlers };
            (
                Hal { hal_events, out_cmd_tx, in_cmd_rx, out_data_tx, in_data_rx },
                Self { out_cmd_rx, in_cmd_tx, out_data_rx, in_data_tx },
            )
        }
    }
}

/// Is this NCI control stream or data response
pub fn is_control_packet(data: &[u8]) -> bool {
    // Check the MT bits
    (data[0] >> 5) & 0x7 != 0
}

/// Result type
type Result<T> = std::result::Result<T, Box<dyn std::error::Error + Send + Sync>>;

/// Errors that can be encountered while dealing with the HAL
#[derive(Error, Debug)]
pub enum HalError {
    /// Invalid rootcanal host error
    #[error("Invalid rootcanal host")]
    InvalidAddressError,
    /// Error while connecting to rootcanal
    #[error("Connection to rootcanal failed: {0}")]
    RootcanalConnectError(#[from] tokio::io::Error),
}
