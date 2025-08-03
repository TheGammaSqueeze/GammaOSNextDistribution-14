// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use log::{error, warn};
use std::collections::VecDeque;
use std::io::{Error, Read};

#[derive(Debug)]
pub struct Packet {
    pub h4_type: u8,
    pub payload: Vec<u8>,
}

#[derive(Debug)]
pub enum PacketError {
    IoError(Error),
    #[allow(dead_code)]
    InvalidPacketType,
    #[allow(dead_code)]
    InvalidPacket,
}

/* H4 message type */
const H4_CMD_TYPE: u8 = 1;
const H4_ACL_TYPE: u8 = 2;
const H4_SCO_TYPE: u8 = 3;
const H4_EVT_TYPE: u8 = 4;
const H4_ISO_TYPE: u8 = 5;

/* HCI message preamble size */
const HCI_CMD_PREAMBLE_SIZE: usize = 3;
const HCI_ACL_PREAMBLE_SIZE: usize = 4;
const HCI_SCO_PREAMBLE_SIZE: usize = 3;
const HCI_EVT_PREAMBLE_SIZE: usize = 2;
const HCI_ISO_PREAMBLE_SIZE: usize = 4;

/// Read and return the h4 packet.
pub fn read_h4_packet<R: Read>(reader: &mut R) -> Result<Packet, PacketError> {
    // Read the h4 type and obtain the preamble length
    let mut buffer = [0u8; 1];
    reader.read_exact(&mut buffer).map_err(PacketError::IoError)?;
    let h4_type = buffer[0];
    let preamble_size = match h4_type {
        H4_CMD_TYPE => HCI_CMD_PREAMBLE_SIZE,
        H4_ACL_TYPE => HCI_ACL_PREAMBLE_SIZE,
        H4_SCO_TYPE => HCI_SCO_PREAMBLE_SIZE,
        H4_EVT_TYPE => HCI_EVT_PREAMBLE_SIZE,
        H4_ISO_TYPE => HCI_ISO_PREAMBLE_SIZE,
        _ => return h4_recovery(reader),
    };

    // Read the preamble and obtain the payload length
    let mut packet = vec![0u8; preamble_size];
    reader.read_exact(&mut packet).map_err(PacketError::IoError)?;
    let payload_length: usize = match h4_type {
        H4_CMD_TYPE => {
            // Command: 2 bytes for opcode, 1 byte for parameter length (Volume 2, Part E, 5.4.1)
            packet[2] as usize
        }
        H4_ACL_TYPE => {
            // ACL: 2 bytes for handle, 2 bytes for data length (Volume 2, Part E, 5.4.2)
            u16::from_le_bytes([packet[2], packet[3]]) as usize
        }
        H4_SCO_TYPE => {
            // SCO: 2 bytes for handle, 1 byte for data length (Volume 2, Part E, 5.4.3)
            packet[2] as usize
        }
        H4_EVT_TYPE => {
            // Event: 1 byte for event code, 1 byte for parameter length (Volume 2, Part E, 5.4.4)
            packet[1] as usize
        }
        H4_ISO_TYPE => {
            // 2 bytes for handle and flags, 12 bits for length (Volume 2, Part E, 5.4.5)
            usize::from(packet[3] & 0x0f) << 8 | usize::from(packet[2])
        }
        _ => {
            // This case was handled above resulting in call to h4_recovery
            panic!("Unknown H4 packet type")
        }
    };
    // Read and append the payload and return
    packet.resize(preamble_size + payload_length, 0u8);
    let result = reader.read_exact(&mut packet[preamble_size..]);
    if let Err(e) = result {
        error!(
            "h4 failed to read {payload_length} bytes for type: {h4_type} preamble: {:?} error: {e}",
            &packet[0..preamble_size]
        );
        return Err(PacketError::IoError(e));
    }
    Ok(Packet { h4_type, payload: packet })
}

/// Skip all received bytes until the HCI Reset command is received.
///
/// Cuttlefish sometimes sends invalid bytes in the virtio-console to
/// rootcanal/netsim when the emulator is restarted in the middle of
/// HCI exchanges. This function recovers from this situation: when an
/// invalid IDC is received all incoming bytes are dropped until the
/// HCI RESET command is recognized in the input stream
///
/// Based on packages/modules/Bluetooth/tools/rootcanal/model/hci/h4_parser.cc
///
fn h4_recovery<R: Read>(mut reader: R) -> Result<Packet, PacketError> {
    const RESET_COMMAND: [u8; 4] = [0x01, 0x03, 0x0c, 0x00];
    let reset_pattern = VecDeque::from(RESET_COMMAND.to_vec());
    let mut buffer = VecDeque::with_capacity(reset_pattern.len());

    warn!("Entering h4 recovery state...");

    let mut byte = [0; 1];
    loop {
        reader.read_exact(&mut byte).map_err(PacketError::IoError)?;
        // bufer contains a sliding window of reset_pattern.len()
        // across the input bytes.
        if buffer.len() == reset_pattern.len() {
            buffer.pop_front();
        }

        buffer.push_back(byte[0]);
        if buffer == reset_pattern {
            warn!("Received HCI Reset command, exiting recovery state");
            return Ok(Packet { h4_type: RESET_COMMAND[0], payload: RESET_COMMAND[1..].to_vec() });
        }
    }
}

#[cfg(test)]
mod tests {
    use super::PacketError;
    use super::{h4_recovery, read_h4_packet};
    use super::{H4_ACL_TYPE, H4_CMD_TYPE, H4_EVT_TYPE, H4_ISO_TYPE, H4_SCO_TYPE};
    use std::io::{BufReader, Cursor};

    // Validate packet types

    #[test]
    fn test_read_h4_cmd_packet() {
        let hci_cmd = vec![0x01, 0x03, 0x0c, 0x00];
        let mut reader = Cursor::new(&hci_cmd);
        let packet = read_h4_packet(&mut reader).unwrap();
        assert_eq!(packet.h4_type, H4_CMD_TYPE);
        assert_eq!(packet.payload, hci_cmd[1..]);
    }

    #[test]
    fn test_read_h4_acl_packet() {
        let hci_acl = vec![
            0x02, 0x2a, 0x20, 0x11, 0x00, 0x15, 0x00, 0x40, 0x00, 0x06, 0x00, 0x01, 0x00, 0x10,
            0x36, 0x00, 0x03, 0x19, 0x11, 0x08, 0x02, 0xa0,
        ];
        let mut reader = Cursor::new(&hci_acl);
        let packet = read_h4_packet(&mut reader).unwrap();
        assert_eq!(packet.h4_type, H4_ACL_TYPE);
        assert_eq!(packet.payload, hci_acl[1..]);
    }

    #[test]
    fn test_read_h4_sco_packet() {
        let hci_sco = vec![0x03, 0x03, 0x0c, 0x02, 0x01, 0x02];
        let mut reader = Cursor::new(&hci_sco);
        let packet = read_h4_packet(&mut reader).unwrap();
        assert_eq!(packet.h4_type, H4_SCO_TYPE);
        assert_eq!(packet.payload, hci_sco[1..]);
    }

    #[test]
    fn test_read_h4_evt_packet() {
        let hci_evt = vec![0x04, 0x0C, 0x01, 0x10];
        let mut reader = Cursor::new(&hci_evt);
        let packet = read_h4_packet(&mut reader).unwrap();
        assert_eq!(packet.h4_type, H4_EVT_TYPE);
        assert_eq!(packet.payload, hci_evt[1..]);
    }

    #[test]
    fn test_read_h4_iso_packet() {
        let hci_iso = vec![0x05, 0x02, 0x00, 0x06, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06];
        let mut reader = Cursor::new(&hci_iso);
        let packet = read_h4_packet(&mut reader).unwrap();
        assert_eq!(packet.h4_type, H4_ISO_TYPE);
        assert_eq!(packet.payload, hci_iso[1..]);
    }

    // Test invalid packet length

    #[test]
    fn test_read_h4_packet_with_eof_in_type() {
        let hci_acl: Vec<u8> = vec![];
        let mut file = Cursor::new(hci_acl);
        let result = read_h4_packet(&mut file);
        assert!(result.is_err());
        matches!(result.unwrap_err(), PacketError::InvalidPacketType);
    }

    #[test]
    fn test_read_h4_packet_with_eof_in_preamble() {
        let mut reader = Cursor::new(&[0x01, 0x03, 0x0c]);
        let result = read_h4_packet(&mut reader);
        assert!(result.is_err());
        matches!(result.unwrap_err(), PacketError::InvalidPacketType);
    }

    #[test]
    fn test_read_h4_packet_with_eof_in_payload() {
        let mut reader = Cursor::new(&[0x01, 0x03, 0x0c, 0x01]);
        let result = read_h4_packet(&mut reader);
        assert!(result.is_err());
        matches!(result.unwrap_err(), PacketError::InvalidPacketType);
    }

    // Test h4_recovery

    #[test]
    fn test_h4_recovery_with_reset_first() {
        let input = b"\0x08\x01\x03\x0c\x00";
        let mut reader = BufReader::new(&input[..]);
        let packet = h4_recovery(&mut reader).unwrap();
        assert_eq!(packet.h4_type, 0x01);
        assert_eq!(packet.payload, vec![0x03, 0x0c, 0x00]);
    }

    #[test]
    fn test_h4_recovery_with_reset_after_many() {
        let input = b"randombytes\x01\x03\x0c\x00";
        let mut reader = BufReader::new(&input[..]);
        let packet = h4_recovery(&mut reader).unwrap();
        assert_eq!(packet.h4_type, 0x01);
        assert_eq!(packet.payload, vec![0x03, 0x0c, 0x00]);
    }

    #[test]
    fn test_h4_recovery_with_eof() {
        let input = b"\0x08\x01\x03\x0c";
        let reader = BufReader::new(&input[..]);
        let result = h4_recovery(reader);
        assert!(result.is_err());
        matches!(result.unwrap_err(), PacketError::IoError(_));
    }
}
