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

//! Builders for PCAPNG blocks.

use std::convert::TryInto;
use std::time::SystemTime;

use log::debug;
use num_derive::{FromPrimitive, ToPrimitive};
use num_traits::cast::ToPrimitive;

/// Supported PCAPNG block types.
#[derive(FromPrimitive, ToPrimitive, Debug, PartialEq, Eq, Clone)]
#[repr(u32)]
pub enum BlockType {
    SectionHeader = 0x0A0D_0D0A,
    InterfaceDescription = 0x1,
    EnhancedPacket = 0x6,
}

/// BlockOption is an option to be attached to a block.
#[derive(Clone, PartialEq, Eq)]
pub struct BlockOption {
    option_code: u16,
    option_content: Vec<u8>,
}

impl BlockOption {
    /// Constructor.
    pub fn new(option_code: u16, option_content: Vec<u8>) -> Self {
        Self { option_code, option_content }
    }

    /// Constructor for end_of_opt option.
    fn end_of_opt() -> Self {
        Self { option_code: 0, option_content: vec![] }
    }

    /// To little endian bytes.
    fn into_le_bytes(mut self) -> Option<Vec<u8>> {
        let option_length = self.option_content.len();
        // padded to multiple of 4.
        let padded_option_length = integer_ceil(option_length, 4);
        let pad_length = padded_option_length - option_length;
        let mut bytes = Vec::<u8>::new();
        bytes.extend_from_slice(&u16::to_le_bytes(self.option_code));
        bytes.extend_from_slice(&u16::to_le_bytes(option_length.try_into().ok()?));
        bytes.append(&mut self.option_content);
        bytes.extend_from_slice(&vec![0; pad_length]);
        Some(bytes)
    }

    /// Size of option in bytes.
    fn byte_size(&self) -> usize {
        let option_length = self.option_content.len();
        // padded to multiple of 4.
        let padded_option_length = integer_ceil(option_length, 4);
        padded_option_length + 4 // 4 bytes for option_code and option_length
    }
}

/// Builds the little endian block with BlockType and BlockOptions added, and block size counted.
fn wrap_little_endian_block(
    block_type: BlockType,
    mut block_core: Vec<u8>,
    block_options: Vec<BlockOption>,
) -> Option<Vec<u8>> {
    static BLOCK_SIZE_OFFSET: usize = 12;
    let mut option_bytes = Vec::<u8>::new();
    if !block_options.is_empty() {
        for block_option in block_options {
            option_bytes.append(block_option.into_le_bytes()?.as_mut());
        }
        // When option is present, opt_endofopt (option_code 0, length 0) MUST present at end.
        option_bytes.append(BlockOption::end_of_opt().into_le_bytes()?.as_mut());
    };
    let block_size = BLOCK_SIZE_OFFSET + block_core.len() + option_bytes.len();
    let mut bytes = Vec::<u8>::new();
    bytes.extend_from_slice(&u32::to_le_bytes(block_type.to_u32().unwrap()));
    bytes.extend_from_slice(&u32::to_le_bytes(block_size.try_into().ok()?));
    bytes.append(&mut block_core);
    bytes.append(&mut option_bytes);
    bytes.extend_from_slice(&u32::to_le_bytes(block_size.try_into().ok()?));
    Some(bytes)
}

/// Generic Block Builder.
pub trait BlockBuilder {
    /// Builds the block into little endian bytes.
    ///
    /// Returns a PCAPNG block on success.
    /// Fails if the content cannot be converted to a valid PCAPNG block.
    fn into_le_bytes(self) -> Option<Vec<u8>>;
}

/// Builds HeaderBlock.
pub struct HeaderBlockBuilder {}

impl HeaderBlockBuilder {
    /// Constructor.
    #[allow(unused)]
    pub fn new() -> Self {
        Self {}
    }
}

impl BlockBuilder for HeaderBlockBuilder {
    fn into_le_bytes(self) -> Option<Vec<u8>> {
        let mut block_core = Vec::<u8>::new();
        // Byte-Order Magic
        block_core.extend_from_slice(&u32::to_le_bytes(0x1A2B3C4D));
        // Major Version 1, Minor Version 0
        block_core.extend_from_slice(&u16::to_le_bytes(1));
        block_core.extend_from_slice(&u16::to_le_bytes(0));
        // Section Length (not specified)
        block_core.extend_from_slice(&u64::to_le_bytes(0xFFFF_FFFF_FFFF_FFFF));
        wrap_little_endian_block(BlockType::SectionHeader, block_core, vec![])
    }
}

/// Builds Interface Description Block that is unique to chip.
pub struct InterfaceDescriptionBlockBuilder {
    /// LinkType.
    link_type: u16,
    /// SnapLen.
    snap_len: u32,
    /// Options for block.
    block_options: Vec<BlockOption>,
}

impl Default for InterfaceDescriptionBlockBuilder {
    fn default() -> Self {
        Self {
            link_type: 299, // FiRa UCI
            snap_len: 0,    // unlimited
            block_options: vec![],
        }
    }
}

impl InterfaceDescriptionBlockBuilder {
    /// Constructor.
    #[allow(unused)]
    pub fn new() -> Self {
        InterfaceDescriptionBlockBuilder::default()
    }

    /// Set LinkType.
    #[allow(unused)]
    pub fn link_type(mut self, link_type: u16) -> Self {
        self.link_type = link_type;
        self
    }

    /// Set SnapLen.
    #[allow(unused)]
    pub fn snap_len(mut self, snap_length: u32) -> Self {
        self.snap_len = snap_length;
        self
    }

    /// Append an option.
    #[allow(unused)]
    pub fn append_option(mut self, block_option: BlockOption) -> Self {
        self.block_options.push(block_option);
        self
    }
}

impl BlockBuilder for InterfaceDescriptionBlockBuilder {
    fn into_le_bytes(self) -> Option<Vec<u8>> {
        let mut block_core = Vec::<u8>::new();
        block_core.extend_from_slice(&u16::to_le_bytes(self.link_type)); // LinkType
        block_core.extend_from_slice(&u16::to_le_bytes(0)); // Reserved
        block_core.extend_from_slice(&u32::to_le_bytes(self.snap_len)); // SnapLen
        wrap_little_endian_block(BlockType::InterfaceDescription, block_core, self.block_options)
    }
}

pub struct EnhancedPacketBlockBuilder {
    interface_id: u32,
    timestamp: u64,
    packet: Vec<u8>,
    block_options: Vec<BlockOption>,
    max_block_size: Option<usize>,
}

impl Default for EnhancedPacketBlockBuilder {
    fn default() -> Self {
        let timestamp = match SystemTime::now().duration_since(SystemTime::UNIX_EPOCH) {
            // as_micros return u128. However, u64 will not overflow until year 586524.
            Ok(duration) => duration.as_micros() as u64,
            Err(e) => {
                debug!("UCI log: system time is before Unix Epoch: {:?}", e);
                0u64
            }
        };
        Self {
            interface_id: 0,
            timestamp,
            packet: vec![],
            block_options: vec![],
            max_block_size: None,
        }
    }
}

impl EnhancedPacketBlockBuilder {
    /// Constructor.
    #[allow(unused)]
    pub fn new() -> Self {
        EnhancedPacketBlockBuilder::default()
    }

    /// Set interface ID.
    #[allow(unused)]
    pub fn interface_id(mut self, interface_id: u32) -> Self {
        self.interface_id = interface_id;
        self
    }

    /// Set timestamp.
    #[allow(unused)]
    pub fn timestamp(mut self, timestamp: u64) -> Self {
        self.timestamp = timestamp;
        self
    }

    /// Set packet.
    #[allow(unused)]
    pub fn packet(mut self, packet: Vec<u8>) -> Self {
        self.packet = packet;
        self
    }

    /// Sets maximum block size permitted (optional). Truncated down to nearest multiple of 4.
    #[allow(unused)]
    pub fn max_block_size(mut self, max_block_size: usize) -> Self {
        self.max_block_size = Some(integer_floor(max_block_size, 4));
        self
    }

    /// Append an option.
    #[allow(unused)]
    pub fn append_option(mut self, block_option: BlockOption) -> Self {
        self.block_options.push(block_option);
        self
    }

    /// Returns maximum packet byte size if value is valid.
    fn max_truncated_packet_length(&self) -> Option<u32> {
        static EPB_SIZE_OFFSET: usize = 32;
        if self.max_block_size.is_none() {
            return Some(u32::MAX);
        }
        let max_block_size = self.max_block_size.unwrap();
        let options_byte_length: usize = if !self.block_options.is_empty() {
            self.block_options
                .iter()
                .map(|block_option| -> usize { block_option.byte_size() })
                .sum::<usize>()
                + 4 // opt_endofopt of size 4 is compulsory for nonempty options.
        } else {
            0
        };
        match max_block_size > EPB_SIZE_OFFSET + options_byte_length {
            true => Some((max_block_size - EPB_SIZE_OFFSET - options_byte_length).try_into().ok()?),
            false => None,
        }
    }
}

impl BlockBuilder for EnhancedPacketBlockBuilder {
    /// Builds the block into little endian bytes.
    ///
    /// Fails if packet is larger than u32::MAX, or max_packet_length is too small such that the
    /// package is does not fit with all content truncated.
    fn into_le_bytes(mut self) -> Option<Vec<u8>> {
        let max_packet_length = self.max_truncated_packet_length()?;
        let original_packet_length: u32 = self.packet.len().try_into().ok()?;
        let captured_packet_length = if max_packet_length >= original_packet_length {
            // padding:
            let pad_length = integer_ceil(self.packet.len(), 4) - self.packet.len();
            self.packet.append(vec![0; pad_length].as_mut());
            original_packet_length
        } else {
            // truncating:
            self.packet.truncate(max_packet_length.try_into().ok()?);
            self.packet.len().try_into().ok()?
        };
        let mut block_core = Vec::<u8>::new();
        // interface ID
        block_core.extend_from_slice(&u32::to_le_bytes(self.interface_id));
        // High timestamp
        block_core.extend_from_slice(&u32::to_le_bytes((self.timestamp >> 32) as u32));
        // Low timestamp
        block_core.extend_from_slice(&u32::to_le_bytes(self.timestamp as u32));
        // Captured Packet Length
        block_core.extend_from_slice(&u32::to_le_bytes(captured_packet_length));
        // Original Packet Length
        block_core.extend_from_slice(&u32::to_le_bytes(original_packet_length));
        block_core.append(&mut self.packet);
        wrap_little_endian_block(BlockType::EnhancedPacket, block_core, self.block_options)
    }
}

fn integer_ceil<T: num_traits::PrimInt>(value: T, step: T) -> T {
    (value + (step - T::one())) / step * step
}

fn integer_floor<T: num_traits::PrimInt>(value: T, step: T) -> T {
    value / step * step
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_option_length_count() {
        let aligned_option = BlockOption::new(0x1, "ABCDEFGH".to_owned().into_bytes());
        assert_eq!(aligned_option.byte_size(), aligned_option.into_le_bytes().unwrap().len());
        let unaligned_option = BlockOption::new(0x1, "ABCDEF".to_owned().into_bytes());
        assert_eq!(unaligned_option.byte_size(), unaligned_option.into_le_bytes().unwrap().len());
    }

    #[test]
    fn test_padded_enhanced_packet_build() {
        let uci_packet: Vec<u8> = vec![0x41, 0x03, 0x00, 0x02, 0x00, 0x00];
        let timestamp: u64 = 0x0102_0304_0506_0708;
        let interface_id: u32 = 0;
        let enhanced_packet_block = EnhancedPacketBlockBuilder::new()
            .interface_id(interface_id)
            .timestamp(timestamp)
            .packet(uci_packet)
            .into_le_bytes()
            .unwrap();

        let expected_block: Vec<u8> = vec![
            0x06, 0x00, 0x00, 0x00, // block type
            // packet is of length 6, padded to 8, with total length 40=0x28
            0x28, 0x00, 0x00, 0x00, // block length
            0x00, 0x00, 0x00, 0x00, // interface id
            0x04, 0x03, 0x02, 0x01, // timestamp high
            0x08, 0x07, 0x06, 0x05, // timestemp low
            0x06, 0x00, 0x00, 0x00, // captured length
            0x06, 0x00, 0x00, 0x00, // original length
            0x41, 0x03, 0x00, 0x02, // packet (padded)
            0x00, 0x00, 0x00, 0x00, // packet (padded)
            0x28, 0x00, 0x00, 0x00, // block length
        ];
        assert_eq!(&enhanced_packet_block, &expected_block);
    }

    #[test]
    fn test_aligned_enhanced_packet_build() {
        let uci_packet: Vec<u8> = vec![0x41, 0x03, 0x00, 0x04, 0x01, 0x01, 0x01, 0x00];
        let timestamp: u64 = 0x0102_0304_0506_0708;
        let interface_id: u32 = 0;
        let enhanced_packet_block = EnhancedPacketBlockBuilder::new()
            .interface_id(interface_id)
            .timestamp(timestamp)
            .packet(uci_packet)
            .into_le_bytes()
            .unwrap();

        let expected_block: Vec<u8> = vec![
            0x06, 0x00, 0x00, 0x00, // block type
            // packet is of length 6, padded to 8, with total length 40=0x28
            0x28, 0x00, 0x00, 0x00, // block length
            0x00, 0x00, 0x00, 0x00, // interface id
            0x04, 0x03, 0x02, 0x01, // timestamp high
            0x08, 0x07, 0x06, 0x05, // timestemp low
            0x08, 0x00, 0x00, 0x00, // captured length
            0x08, 0x00, 0x00, 0x00, // original length
            0x41, 0x03, 0x00, 0x04, // packet (aligned)
            0x01, 0x01, 0x01, 0x00, // packet (aligned)
            0x28, 0x00, 0x00, 0x00, // block length
        ];
        assert_eq!(&enhanced_packet_block, &expected_block);
    }
    #[test]
    fn test_truncated_enhanced_packet_build() {
        let uci_packet: Vec<u8> = vec![0x41, 0x03, 0x00, 0x02, 0x00, 0x00];
        let timestamp: u64 = 0x0102_0304_0506_0708;
        let interface_id: u32 = 0;
        let enhanced_packet_block = EnhancedPacketBlockBuilder::new()
            .interface_id(interface_id)
            .timestamp(timestamp)
            .packet(uci_packet)
            .max_block_size(0x24)
            .into_le_bytes()
            .unwrap();

        let expected_block: Vec<u8> = vec![
            0x06, 0x00, 0x00, 0x00, // block type
            // packet is of length 6, truncated to 4, with total length 36=0x24
            0x24, 0x00, 0x00, 0x00, // block length
            0x00, 0x00, 0x00, 0x00, // interface id
            0x04, 0x03, 0x02, 0x01, // timestamp high
            0x08, 0x07, 0x06, 0x05, // timestemp low
            0x04, 0x00, 0x00, 0x00, // captured length
            0x06, 0x00, 0x00, 0x00, // original length
            0x41, 0x03, 0x00, 0x02, // packet (truncated)
            0x24, 0x00, 0x00, 0x00, // block length
        ];
        assert_eq!(&enhanced_packet_block, &expected_block);
    }

    #[test]
    fn test_interface_description_block_with_options_build() {
        let comment_opt = BlockOption::new(0x1, "ABCDEF".to_owned().into_bytes());
        let link_type: u16 = 299; // 0x12b
        let snap_len: u32 = 0;
        let interface_description_block = InterfaceDescriptionBlockBuilder::new()
            .link_type(link_type)
            .snap_len(snap_len)
            .append_option(comment_opt)
            .into_le_bytes()
            .unwrap();
        let expected_block: Vec<u8> = vec![
            0x01, 0x00, 0x00, 0x00, // block type
            0x24, 0x00, 0x00, 0x00, // block length
            0x2b, 0x01, 0x00, 0x00, // link type, reserved
            0x00, 0x00, 0x00, 0x00, // SnapLen
            0x01, 0x00, 0x06, 0x00, // option code, padded length
            0x41, 0x42, 0x43, 0x44, // option (ABCD)
            0x45, 0x46, 0x00, 0x00, // option (EF)
            0x00, 0x00, 0x00, 0x00, // option code, padded length (opt_endofopt)
            0x24, 0x00, 0x00, 0x00, // block length
        ];
        assert_eq!(&interface_description_block, &expected_block);
    }
}
