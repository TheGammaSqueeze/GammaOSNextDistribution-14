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

//! This module defines the UciHal trait, used for the UCI hardware abstration layer.

use std::convert::TryInto;

use async_trait::async_trait;
use pdl_runtime::Packet;
use tokio::sync::mpsc;
use uwb_uci_packets::{UciControlPacket, UciControlPacketHal};

use crate::error::Result;
use crate::params::uci_packets::SessionId;
use crate::uci::command::UciCommand;

/// The byte buffer of a UCI packet that is used to communicate with the UciHal trait.
/// The format of the byte buffer should follow the UCI packet spec.
pub type UciHalPacket = Vec<u8>;

/// The trait for the UCI hardware abstration layer. The client of this library should implement
/// this trait and inject into the library.
/// Note: Each method should be completed in 1000 ms.
#[async_trait]
pub trait UciHal: 'static + Send + Sync {
    /// Open the UCI HAL and power on the UWB Subsystem.
    ///
    /// All the other API should be called after the open() completes successfully. Once the method
    /// completes successfully, the UciHal instance should store |packet_sender| and send the UCI
    /// packets (responses, notifications, data) back to the caller via the |packet_sender|.
    async fn open(&mut self, packet_sender: mpsc::UnboundedSender<UciHalPacket>) -> Result<()>;

    /// Close the UCI HAL.
    ///
    /// After calling this method, the instance would drop |packet_sender| received from open()
    /// method.
    async fn close(&mut self) -> Result<()>;

    /// Write the UCI command to the UWB Subsystem.
    ///
    /// The caller should call this method after the response of the previous send_command() is
    /// received.
    async fn send_command(&mut self, cmd: UciCommand) -> Result<()> {
        // A UCI command message may consist of multiple UCI packets when the payload is over the
        // maximum packet size. We convert the command into list of UciHalPacket, then send the
        // packets via send_packet().
        let packet: UciControlPacket = cmd.try_into()?;
        let fragmented_packets: Vec<UciControlPacketHal> = packet.into();
        for packet in fragmented_packets.into_iter() {
            self.send_packet(packet.to_vec()).await?;
        }
        Ok(())
    }

    /// Write the UCI packet to the UWB Subsystem.
    async fn send_packet(&mut self, packet: UciHalPacket) -> Result<()>;

    /// Notify the HAL that the UWB session is initialized successfully.
    async fn notify_session_initialized(&mut self, _session_id: SessionId) -> Result<()> {
        Ok(())
    }
}

/// A placeholder implementation for UciHal that do nothing.
pub struct NopUciHal {}
#[async_trait]
impl UciHal for NopUciHal {
    async fn open(&mut self, _packet_sender: mpsc::UnboundedSender<UciHalPacket>) -> Result<()> {
        Ok(())
    }
    async fn close(&mut self) -> Result<()> {
        Ok(())
    }
    async fn send_packet(&mut self, _packet: UciHalPacket) -> Result<()> {
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    struct MockUciHal {
        pub packets: Vec<UciHalPacket>,
    }

    #[async_trait]
    impl UciHal for MockUciHal {
        async fn open(&mut self, _: mpsc::UnboundedSender<UciHalPacket>) -> Result<()> {
            Ok(())
        }
        async fn close(&mut self) -> Result<()> {
            Ok(())
        }
        async fn send_packet(&mut self, packet: UciHalPacket) -> Result<()> {
            self.packets.push(packet);
            Ok(())
        }
    }

    // Verify if UciHal::send_command() split the packets correctly.
    #[tokio::test]
    async fn test_send_command() {
        let mut hal = MockUciHal { packets: vec![] };
        let _ = hal.send_command(UciCommand::CoreGetDeviceInfo).await;
        let expected_packets = vec![vec![0x20, 0x02, 0x00, 0x00]];
        assert_eq!(hal.packets, expected_packets);
    }
}
