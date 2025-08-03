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

use std::convert::TryFrom;

use tokio::sync::mpsc;
use uwb_uci_packets::{UciControlPacket, UciDataPacket};

use crate::error::{Error, Result};
use crate::uci::uci_logger::UciLogger;

/// Mock implementation of UciLogger
#[derive(Debug, PartialEq, Eq, Clone)]
pub(crate) enum UciLogEvent {
    Packet(Vec<u8>),
    HalOpen(Result<()>),
    HalClose(Result<()>),
}

impl TryFrom<UciLogEvent> for Vec<u8> {
    type Error = Error;
    fn try_from(value: UciLogEvent) -> Result<Self> {
        match value {
            UciLogEvent::Packet(packet) => Ok(packet),
            _ => Err(Error::BadParameters),
        }
    }
}

pub(crate) struct MockUciLogger {
    log_sender: mpsc::UnboundedSender<UciLogEvent>,
}

impl MockUciLogger {
    pub(crate) fn new(log_sender: mpsc::UnboundedSender<UciLogEvent>) -> Self {
        Self { log_sender }
    }
}

impl UciLogger for MockUciLogger {
    fn log_hal_close(&mut self, result: Result<()>) {
        let _ = self.log_sender.send(UciLogEvent::HalClose(result));
    }

    fn log_hal_open(&mut self, result: Result<()>) {
        let _ = self.log_sender.send(UciLogEvent::HalOpen(result));
    }

    fn log_uci_control_packet(&mut self, packet: UciControlPacket) {
        let _ = self.log_sender.send(UciLogEvent::Packet(packet.into()));
    }

    fn log_uci_data_packet(&mut self, packet: &UciDataPacket) {
        let _ = self.log_sender.send(UciLogEvent::Packet(packet.clone().into()));
    }
}
