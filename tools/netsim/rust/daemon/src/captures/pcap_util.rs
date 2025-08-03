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

//! A utility module for writing pcap files
//!
//! This module includes writing appropriate pcap headers with given
//! linktype and appending records with header based on the assigned
//! protocol.

use std::{
    io::{Result, Write},
    time::Duration,
};

macro_rules! be_vec {
    ( $( $x:expr ),* ) => {
         Vec::<u8>::new().iter().copied()
         $( .chain($x.to_be_bytes()) )*
         .collect()
       };
    }

macro_rules! le_vec {
    ( $( $x:expr ),* ) => {
            Vec::<u8>::new().iter().copied()
            $( .chain($x.to_le_bytes()) )*
            .collect()
        };
    }

/// The indication of packet direction for HCI packets.
pub enum PacketDirection {
    /// Host To Controller as u32 value
    HostToController = 0,
    /// Controller to Host as u32 value
    ControllerToHost = 1,
}

/// Supported LinkTypes for packet capture
/// https://www.tcpdump.org/linktypes.html
pub enum LinkType {
    /// Radiotap link-layer information followed by an 802.11
    /// header. Radiotap is used with mac80211_hwsim networking.
    Ieee80211RadioTap = 127,
    /// Bluetooth HCI UART transport layer
    BluetoothHciH4WithPhdr = 201,
    /// Ultra-wideband controller interface protocol
    FiraUci = 299,
}

/// Returns the file size after writing the header of the
/// pcap file.
pub fn write_pcap_header<W: Write>(link_type: LinkType, output: &mut W) -> Result<usize> {
    // https://tools.ietf.org/id/draft-gharris-opsawg-pcap-00.html#name-file-header
    let header: Vec<u8> = be_vec![
        0xa1b2c3d4u32, // magic number
        2u16,          // major version
        4u16,          // minor version
        0u32,          // reserved 1
        0u32,          // reserved 2
        u32::MAX,      // snaplen
        link_type as u32
    ];

    output.write_all(&header)?;
    Ok(header.len())
}

/// Returns the file size after writing header of the
/// pcapng file
pub fn write_pcapng_header<W: Write>(link_type: LinkType, output: &mut W) -> Result<usize> {
    let header: Vec<u8> = le_vec![
        // PCAPng files must start with a Section Header Block
        0x0A0D0D0A_u32,         // Block Type
        28_u32,                 // Block Total Length
        0x1A2B3C4D_u32,         // Byte-Order Magic
        1_u16,                  // Major Version
        0_u16,                  // Minor Version
        0xFFFFFFFFFFFFFFFF_u64, // Section Length (not specified)
        28_u32,                 // Block Total Length
        // Write the Interface Description Block used for all
        // UCI records.
        0x00000001_u32,   // Block Type
        20_u32,           // Block Total Length
        link_type as u16, // LinkType
        0_u16,            // Reserved
        0_u32,            // SnapLen (no limit)
        20_u32            // Block Total Length
    ];

    output.write_all(&header)?;
    Ok(header.len())
}

/// The BluetoothHciH4WithPhdr frame contains a 4-byte direction
/// field, followed by an HCI packet indicator byte, followed by an
/// HCI packet of the specified packet type.
pub fn wrap_bt_packet(
    packet_direction: PacketDirection,
    packet_type: u32,
    packet: &[u8],
) -> Vec<u8> {
    let header: Vec<u8> = be_vec![packet_direction as u32, packet_type as u8];
    let mut bytes = Vec::<u8>::with_capacity(header.len() + packet.len());
    bytes.extend(&header);
    bytes.extend(packet);
    bytes
}

/// Returns the file size after appending a single packet record.
pub fn append_record<W: Write>(
    timestamp: Duration,
    output: &mut W,
    packet: &[u8],
) -> Result<usize> {
    // https://tools.ietf.org/id/draft-gharris-opsawg-pcap-00.html#name-packet-record
    let length = packet.len();
    let header: Vec<u8> = be_vec![
        timestamp.as_secs() as u32, // seconds
        timestamp.subsec_micros(),  // microseconds
        length as u32,              // Captured Packet Length
        length as u32               // Original Packet Length
    ];
    let mut bytes = Vec::<u8>::with_capacity(header.len() + length);
    bytes.extend(&header);
    bytes.extend(packet);
    output.write_all(&bytes)?;
    output.flush()?;
    Ok(header.len() + length)
}

/// Returns the file size after appending a single packet record for pcapng.
pub fn append_record_pcapng<W: Write>(
    timestamp: Duration,
    output: &mut W,
    packet: &[u8],
) -> Result<usize> {
    let packet_data_padding: usize = 4 - packet.len() % 4;
    let block_total_length: u32 = (packet.len() + packet_data_padding + 32) as u32;
    // Wrap the packet inside an Enhanced Packet Block.
    let header: Vec<u8> = le_vec![
        0x00000006_u32,             // Block Type
        block_total_length,         // Block Total Length
        0_u32,                      // Interface ID
        timestamp.as_secs() as u32, // seconds
        timestamp.subsec_micros(),  // microseconds
        packet.len() as u32,        // Captured Packet Length
        packet.len() as u32         // Original Packet Length
    ];
    output.write_all(&header)?;
    output.write_all(packet)?;
    output.write_all(&vec![0; packet_data_padding])?;
    output.write_all(&block_total_length.to_le_bytes())?;
    output.flush()?;
    Ok(block_total_length as usize)
}

#[cfg(test)]
mod tests {
    use std::time::Duration;

    use super::*;

    static EXPECTED_PCAP: &[u8; 76] = include_bytes!("sample.pcap");
    static EXPECTED_PCAPNG: &[u8; 88] = include_bytes!("sample.pcapng");

    #[test]
    /// The test is done with the golden file sample.pcap with following packets:
    /// Packet 1: HCI_EVT from Controller to Host (Sent Command Complete (LE Set Advertise Enable))
    /// Packet 2: HCI_CMD from Host to Controller (Rcvd LE Set Advertise Enable) [250 milisecs later]
    fn test_pcap_file() {
        let mut actual = Vec::<u8>::new();
        write_pcap_header(LinkType::BluetoothHciH4WithPhdr, &mut actual).unwrap();
        let _ = append_record(
            Duration::from_secs(0),
            &mut actual,
            // H4_EVT_TYPE = 4
            &wrap_bt_packet(PacketDirection::HostToController, 4, &[14, 4, 1, 10, 32, 0]),
        )
        .unwrap();
        let _ = append_record(
            Duration::from_millis(250),
            &mut actual,
            // H4_CMD_TYPE = 1
            &wrap_bt_packet(PacketDirection::ControllerToHost, 1, &[10, 32, 1, 0]),
        )
        .unwrap();
        assert_eq!(actual, EXPECTED_PCAP);
    }

    #[test]
    fn test_pcapng_file() {
        let mut actual = Vec::<u8>::new();
        write_pcapng_header(LinkType::FiraUci, &mut actual).unwrap();
        // Appending a UCI packet: Core Get Device Info Cmd
        let _ = append_record_pcapng(Duration::new(0, 0), &mut actual, &[32, 2, 0, 0]).unwrap();
        assert_eq!(actual, EXPECTED_PCAPNG);
    }
}
