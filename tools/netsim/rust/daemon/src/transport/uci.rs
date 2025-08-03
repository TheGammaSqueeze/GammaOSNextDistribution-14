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

use std::io::{Error, Read};

/// This module implements control packet parsing for UWB.
///
/// UWB Command Interface Specification, UCI Generic Specification
/// Version 1.1
///
/// 2.3.2 Format of Control Packets

const UCI_HEADER_SIZE: usize = 4;
const UCI_PAYLOAD_LENGTH_FIELD: usize = 3;

#[derive(Debug)]
pub struct Packet {
    pub payload: Vec<u8>,
}

#[derive(Debug)]
pub enum PacketError {
    IoError(Error),
}

pub fn read_uci_packet<R: Read>(reader: &mut R) -> Result<Packet, PacketError> {
    // Read the UCI header
    let mut buffer = vec![0; UCI_HEADER_SIZE];
    reader.read_exact(&mut buffer[0..]).map_err(PacketError::IoError)?;
    // Extract the control packet payload length and read
    let length = buffer[UCI_PAYLOAD_LENGTH_FIELD] as usize + UCI_HEADER_SIZE;
    buffer.resize(length, 0);
    reader.read_exact(&mut buffer[UCI_HEADER_SIZE..]).map_err(PacketError::IoError)?;
    Ok(Packet { payload: buffer })
}
