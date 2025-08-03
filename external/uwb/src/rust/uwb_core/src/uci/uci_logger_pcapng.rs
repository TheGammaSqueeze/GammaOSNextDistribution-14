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

//! Implements UciLoggerPcapng, a UciLogger with PCAPNG format log.

use log::warn;
use tokio::sync::mpsc;
use uwb_uci_packets::{UciControlPacket, UciDataPacket};

use crate::uci::pcapng_block::{BlockBuilder, BlockOption, EnhancedPacketBlockBuilder};
use crate::uci::pcapng_uci_logger_factory::LogWriter;
use crate::uci::uci_logger::UciLogger;
/// A UCI logger that saves UCI packets and HAL events as PCAPNG file.
///
/// UciLoggerPcapng is built by PcapngUciLoggerFactory.
pub struct UciLoggerPcapng {
    log_writer: LogWriter,
    interface_id: u32, // Unique to each UWB chip per log session.
}

impl UciLoggerPcapng {
    /// Constructor.
    pub(crate) fn new(log_writer: LogWriter, interface_id: u32) -> Self {
        Self { log_writer, interface_id }
    }

    fn send_block_bytes(&mut self, bytes: Vec<u8>) {
        if self.log_writer.send_bytes(bytes).is_none() {
            warn!("UCI log: Logging to LogWritter failed.")
        }
    }

    /// Flush the logs.
    pub fn flush(&mut self) -> Option<mpsc::UnboundedReceiver<bool>> {
        self.log_writer.flush()
    }
}

impl UciLogger for UciLoggerPcapng {
    fn log_uci_control_packet(&mut self, packet: UciControlPacket) {
        let block_bytes = match EnhancedPacketBlockBuilder::new()
            .interface_id(self.interface_id)
            .packet(packet.into())
            .into_le_bytes()
        {
            Some(b) => b,
            None => return,
        };
        self.send_block_bytes(block_bytes);
    }

    fn log_uci_data_packet(&mut self, packet: &UciDataPacket) {
        let packet_header_bytes = match EnhancedPacketBlockBuilder::new()
            .interface_id(self.interface_id)
            .packet(packet.clone().into())
            .into_le_bytes()
        {
            Some(b) => b,
            None => return,
        };
        self.send_block_bytes(packet_header_bytes);
    }

    fn log_hal_open(&mut self, result: crate::error::Result<()>) {
        let block_option = match result {
            Ok(_) => BlockOption::new(0x1, "HAL OPEN: OKAY".to_owned().into_bytes()),
            Err(_) => BlockOption::new(0x1, "HAL OPEN: FAIL".to_owned().into_bytes()),
        };
        let block_bytes = EnhancedPacketBlockBuilder::new()
            .interface_id(self.interface_id)
            .append_option(block_option)
            .into_le_bytes()
            .unwrap(); // Constant Block except for timestamp, which do not throw error.
        self.send_block_bytes(block_bytes);
    }

    fn log_hal_close(&mut self, result: crate::error::Result<()>) {
        let block_option = match result {
            Ok(_) => BlockOption::new(0x1, "HAL CLOSE: OKAY".to_owned().into_bytes()),
            Err(_) => BlockOption::new(0x1, "HAL CLOSE: FAIL".to_owned().into_bytes()),
        };
        let block_bytes = EnhancedPacketBlockBuilder::new()
            .interface_id(self.interface_id)
            .append_option(block_option)
            .into_le_bytes()
            .unwrap();
        // packet and max_packet_length field are not assigned, into_le_bytes() must return
        // a valid result.
        self.send_block_bytes(block_bytes);
    }
}
