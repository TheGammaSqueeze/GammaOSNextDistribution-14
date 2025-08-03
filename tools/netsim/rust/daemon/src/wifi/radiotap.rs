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

/// Produce PCAP Radiotap buffers from Hwsim Frames
///
/// This module produces the Radiotap buffers used by PCAP and PcapNG
/// for logging 802.11 frames.
///
/// See https://www.radiotap.org/
use crate::wifi::frame::Frame;
use crate::wifi::medium;
use crate::wifi::medium::HwsimCmdEnum;
use anyhow::{anyhow, Context};
use log::info;
use std::mem;

#[repr(C, packed)]
struct RadiotapHeader {
    version: u8,
    pad: u8,
    len: u16,
    present: u32,
    channel: ChannelInfo,
    signal: u8,
}

#[repr(C)]
struct ChannelInfo {
    freq: u16,
    flags: u16,
}

pub fn into_pcap(packet: &[u8]) -> Option<Vec<u8>> {
    match medium::parse_hwsim_cmd(packet) {
        Ok(HwsimCmdEnum::Frame(frame)) => frame_into_pcap(*frame).ok(),
        Ok(_) => None,
        Err(e) => {
            info!("Failed to convert packet to pcap format. Err: {}. Packet: {:?}", e, &packet);
            None
        }
    }
}

pub fn frame_into_pcap(frame: Frame) -> anyhow::Result<Vec<u8>> {
    // Create an instance of the RadiotapHeader with fields for
    // Channel and Signal.  In the future add more fields from the
    // Frame.

    let mut radiotap_hdr: RadiotapHeader = RadiotapHeader {
        version: 0,
        pad: 0,
        len: (std::mem::size_of::<RadiotapHeader>() as u16),
        present: (1 << 3 /* channel */ | 1 << 5/* signal dBm */),
        channel: ChannelInfo { freq: frame.freq.unwrap_or(0) as u16, flags: 0 },
        signal: frame.signal.unwrap_or(0) as u8,
    };

    // Add the struct fields to the buffer manually in little-endian.
    let mut buffer = Vec::<u8>::new();
    buffer.push(radiotap_hdr.version);
    buffer.push(radiotap_hdr.pad);
    buffer.extend_from_slice(&radiotap_hdr.len.to_le_bytes());
    buffer.extend_from_slice(&radiotap_hdr.present.to_le_bytes());
    buffer.extend_from_slice(&radiotap_hdr.channel.freq.to_le_bytes());
    buffer.extend_from_slice(&radiotap_hdr.channel.flags.to_le_bytes());
    buffer.push(radiotap_hdr.signal);
    buffer.extend_from_slice(&frame.data);

    Ok(buffer)
}
