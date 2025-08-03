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

#![allow(clippy::all)]
#![allow(non_upper_case_globals)]
#![allow(non_camel_case_types)]
#![allow(non_snake_case)]
#![allow(unused)]
#![allow(missing_docs)]

use std::cmp;

use log::error;
use num_derive::FromPrimitive;
use num_traits::FromPrimitive;
use zeroize::Zeroize;

include!(concat!(env!("OUT_DIR"), "/uci_packets.rs"));

const MAX_PAYLOAD_LEN: usize = 255;
// TODO: Use a PDL struct to represent the headers and avoid hardcoding
// lengths below.
// Real UCI packet header len.
pub const UCI_PACKET_HAL_HEADER_LEN: usize = 4;
// Unfragmented UCI packet header len.
pub const UCI_PACKET_HEADER_LEN: usize = 7;
// Unfragmented UCI DATA_MESSAGE_SND packet header len.
const UCI_DATA_SND_PACKET_HEADER_LEN: usize = 6;

// Opcode field byte position (within UCI packet header) and mask (of bits to be used).
const UCI_HEADER_MT_BYTE_POSITION: usize = 0;
const UCI_HEADER_MT_BIT_SHIFT: u8 = 5;
const UCI_HEADER_MT_MASK: u8 = 0x7;

const UCI_HEADER_PBF_BYTE_POSITION: usize = 0;
const UCI_HEADER_PBF_BIT_SHIFT: u8 = 4;
const UCI_HEADER_PBF_MASK: u8 = 0x1;

const UCI_CONTROL_HEADER_GID_BYTE_POSITION: usize = 0;
const UCI_CONTROL_HEADER_GID_MASK: u8 = 0xF;

const UCI_CONTROL_HEADER_OID_BYTE_POSITION: usize = 1;
const UCI_CONTROL_HEADER_OID_MASK: u8 = 0x3F;

// Radar field lengths
pub const UCI_RADAR_SEQUENCE_NUMBER_LEN: usize = 4;
pub const UCI_RADAR_TIMESTAMP_LEN: usize = 4;
pub const UCI_RADAR_VENDOR_DATA_LEN_LEN: usize = 1;

#[derive(Debug, Clone, PartialEq, FromPrimitive)]
pub enum TimeStampLength {
    Timestamp40Bit = 0x0,
    Timestamp64Bit = 0x1,
}

#[derive(Debug, Clone, PartialEq, FromPrimitive)]
pub enum DTAnchorLocationType {
    NotIncluded = 0x0,
    Wgs84 = 0x1,
    Relative = 0x2,
}

#[allow(dead_code)]
#[derive(Debug, Clone, PartialEq)]
pub struct DlTdoaRangingMeasurement {
    pub status: u8,
    pub message_type: u8,
    pub message_control: u16,
    pub block_index: u16,
    pub round_index: u8,
    pub nlos: u8,
    pub aoa_azimuth: u16,
    pub aoa_azimuth_fom: u8,
    pub aoa_elevation: u16,
    pub aoa_elevation_fom: u8,
    pub rssi: u8,
    pub tx_timestamp: u64,
    pub rx_timestamp: u64,
    pub anchor_cfo: u16,
    pub cfo: u16,
    pub initiator_reply_time: u32,
    pub responder_reply_time: u32,
    pub initiator_responder_tof: u16,
    pub dt_anchor_location: Vec<u8>,
    pub ranging_rounds: Vec<u8>,
    total_size: usize,
}

impl DlTdoaRangingMeasurement {
    pub fn parse_one(bytes: &[u8]) -> Option<Self> {
        let mut ptr = 0;
        let status = extract_u8(bytes, &mut ptr, 1)?;
        let message_type = extract_u8(bytes, &mut ptr, 1)?;
        let message_control = extract_u16(bytes, &mut ptr, 2)?;
        let block_index = extract_u16(bytes, &mut ptr, 2)?;
        let round_index = extract_u8(bytes, &mut ptr, 1)?;
        let nlos = extract_u8(bytes, &mut ptr, 1)?;
        let aoa_azimuth = extract_u16(bytes, &mut ptr, 2)?;
        let aoa_azimuth_fom = extract_u8(bytes, &mut ptr, 1)?;
        let aoa_elevation = extract_u16(bytes, &mut ptr, 2)?;
        let aoa_elevation_fom = extract_u8(bytes, &mut ptr, 1)?;
        let rssi = extract_u8(bytes, &mut ptr, 1)?;
        let tx_timestamp_length = (message_control >> 1) & 0x1;
        let tx_timestamp = match TimeStampLength::from_u16(tx_timestamp_length)? {
            TimeStampLength::Timestamp40Bit => extract_u64(bytes, &mut ptr, 5)?,
            TimeStampLength::Timestamp64Bit => extract_u64(bytes, &mut ptr, 8)?,
        };
        let rx_timestamp_length = (message_control >> 3) & 0x1;
        let rx_timestamp = match TimeStampLength::from_u16(rx_timestamp_length)? {
            TimeStampLength::Timestamp40Bit => extract_u64(bytes, &mut ptr, 5)?,
            TimeStampLength::Timestamp64Bit => extract_u64(bytes, &mut ptr, 8)?,
        };
        let anchor_cfo = extract_u16(bytes, &mut ptr, 2)?;
        let cfo = extract_u16(bytes, &mut ptr, 2)?;
        let initiator_reply_time = extract_u32(bytes, &mut ptr, 4)?;
        let responder_reply_time = extract_u32(bytes, &mut ptr, 4)?;
        let initiator_responder_tof = extract_u16(bytes, &mut ptr, 2)?;
        let dt_location_type = (message_control >> 5) & 0x3;
        let dt_anchor_location = match DTAnchorLocationType::from_u16(dt_location_type)? {
            DTAnchorLocationType::Wgs84 => extract_vec(bytes, &mut ptr, 12)?,
            DTAnchorLocationType::Relative => extract_vec(bytes, &mut ptr, 10)?,
            _ => vec![],
        };
        let active_ranging_rounds = ((message_control >> 7) & 0xf) as u8;
        let ranging_round = extract_vec(bytes, &mut ptr, active_ranging_rounds as usize)?;

        Some(DlTdoaRangingMeasurement {
            status,
            message_type,
            message_control,
            block_index,
            round_index,
            nlos,
            aoa_azimuth,
            aoa_azimuth_fom,
            aoa_elevation,
            aoa_elevation_fom,
            rssi,
            tx_timestamp,
            rx_timestamp,
            anchor_cfo,
            cfo,
            initiator_reply_time,
            responder_reply_time,
            initiator_responder_tof,
            dt_anchor_location: dt_anchor_location.to_vec(),
            ranging_rounds: ranging_round.to_vec(),
            total_size: ptr,
        })
    }
    pub fn get_total_size(&self) -> usize {
        self.total_size
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct ShortAddressDlTdoaRangingMeasurement {
    pub mac_address: u16,
    pub measurement: DlTdoaRangingMeasurement,
}

impl ShortAddressDlTdoaRangingMeasurement {
    /// Parse the `payload` byte buffer from PDL to the vector of measurement.
    pub fn parse(bytes: &[u8], no_of_ranging_measurement: u8) -> Option<Vec<Self>> {
        let mut ptr = 0;
        let mut measurements = vec![];
        let mut count = 0;
        while (count < no_of_ranging_measurement) {
            let mac_address = extract_u16(bytes, &mut ptr, 2)?;
            let rem = &bytes[ptr..];
            let measurement = DlTdoaRangingMeasurement::parse_one(rem);
            match measurement {
                Some(measurement) => {
                    ptr += measurement.get_total_size();
                    measurements
                        .push(ShortAddressDlTdoaRangingMeasurement { mac_address, measurement });
                    count = count + 1;
                }
                None => return None,
            }
        }
        Some(measurements)
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct ExtendedAddressDlTdoaRangingMeasurement {
    pub mac_address: u64,
    pub measurement: DlTdoaRangingMeasurement,
}

impl ExtendedAddressDlTdoaRangingMeasurement {
    /// Parse the `payload` byte buffer from PDL to the vector of measurement.
    pub fn parse(bytes: &[u8], no_of_ranging_measurement: u8) -> Option<Vec<Self>> {
        let mut ptr = 0;
        let mut measurements = vec![];
        let mut count = 0;
        while (count < no_of_ranging_measurement) {
            let mac_address = extract_u64(bytes, &mut ptr, 8)?;
            let rem = &bytes[ptr..];
            let measurement = DlTdoaRangingMeasurement::parse_one(rem);
            match measurement {
                Some(measurement) => {
                    ptr += measurement.get_total_size();
                    measurements
                        .push(ExtendedAddressDlTdoaRangingMeasurement { mac_address, measurement });
                    count = count + 1;
                }
                None => return None,
            }
        }
        Some(measurements)
    }
}

pub fn extract_vec(bytes: &[u8], ptr: &mut usize, consumed_size: usize) -> Option<Vec<u8>> {
    if bytes.len() < *ptr + consumed_size {
        return None;
    }

    let res = bytes[*ptr..*ptr + consumed_size].to_vec();
    *ptr += consumed_size;
    Some(res)
}

/// Generate the function that extracts the value from byte buffers.
macro_rules! generate_extract_func {
    ($func_name:ident, $type:ty) => {
        /// Extract the value from |byte[ptr..ptr + consumed_size]| in little endian.
        fn $func_name(bytes: &[u8], ptr: &mut usize, consumed_size: usize) -> Option<$type> {
            const type_size: usize = std::mem::size_of::<$type>();
            if consumed_size > type_size {
                return None;
            }

            let extracted_bytes = extract_vec(bytes, ptr, consumed_size)?;
            let mut le_bytes = [0; type_size];
            le_bytes[0..consumed_size].copy_from_slice(&extracted_bytes);
            Some(<$type>::from_le_bytes(le_bytes))
        }
    };
}

generate_extract_func!(extract_u8, u8);
generate_extract_func!(extract_u16, u16);
generate_extract_func!(extract_u32, u32);
generate_extract_func!(extract_u64, u64);

// The GroupIdOrDataPacketFormat enum has all the values defined in both the GroupId and
// DataPacketFormat enums. It represents the same bits in UCI packet header - the GID field in
// a UCI control packet, and the DataPacketFormat field in a UCI data packet. Hence the unwrap()
// calls in the conversions below should always succeed (as long as care is taken in future, to
// keep the two enums in sync, for any additional values defined in the UCI spec).
impl From<GroupId> for GroupIdOrDataPacketFormat {
    fn from(gid: GroupId) -> Self {
        GroupIdOrDataPacketFormat::try_from(u8::from(gid)).unwrap()
    }
}

impl From<GroupIdOrDataPacketFormat> for GroupId {
    fn from(gid_or_dpf: GroupIdOrDataPacketFormat) -> Self {
        GroupId::try_from(u8::from(gid_or_dpf)).unwrap()
    }
}

impl From<DataPacketFormat> for GroupIdOrDataPacketFormat {
    fn from(dpf: DataPacketFormat) -> Self {
        GroupIdOrDataPacketFormat::try_from(u8::from(dpf)).unwrap()
    }
}

// The GroupIdOrDataPacketFormat enum has more values defined (for the GroupId bits) than the
// DataPacketFormat enum. Hence this is implemented as TryFrom() instead of From().
impl TryFrom<GroupIdOrDataPacketFormat> for DataPacketFormat {
    type Error = Error;

    fn try_from(gid_or_dpf: GroupIdOrDataPacketFormat) -> Result<Self> {
        DataPacketFormat::try_from(u8::from(gid_or_dpf)).or(Err(Error::InvalidPacketError))
    }
}

// Container for UCI packet header fields.
struct UciControlPacketHeader {
    message_type: MessageType,
    group_id: GroupId,
    opcode: u8,
}

impl UciControlPacketHeader {
    fn new(message_type: MessageType, group_id: GroupId, opcode: u8) -> Result<Self> {
        if !is_uci_control_packet(message_type) {
            return Err(Error::InvalidPacketError);
        }

        Ok(UciControlPacketHeader {
            message_type: message_type,
            group_id: group_id,
            opcode: opcode,
        })
    }
}

// Helper methods to extract the UCI Packet header fields.
fn get_mt_from_uci_packet(packet: &[u8]) -> u8 {
    (packet[UCI_HEADER_MT_BYTE_POSITION] >> UCI_HEADER_MT_BIT_SHIFT) & UCI_HEADER_MT_MASK
}

fn get_pbf_from_uci_packet(packet: &[u8]) -> u8 {
    (packet[UCI_HEADER_PBF_BYTE_POSITION] >> UCI_HEADER_PBF_BIT_SHIFT) & UCI_HEADER_PBF_MASK
}

fn get_gid_from_uci_control_packet(packet: &[u8]) -> u8 {
    packet[UCI_CONTROL_HEADER_GID_BYTE_POSITION] & UCI_CONTROL_HEADER_GID_MASK
}

fn get_oid_from_uci_control_packet(packet: &[u8]) -> u8 {
    packet[UCI_CONTROL_HEADER_OID_BYTE_POSITION] & UCI_CONTROL_HEADER_OID_MASK
}

// This function parses the packet bytes to return the Control Packet Opcode (OID) field. The
// caller should check that the packet bytes represent a UCI control packet. The code will not
// panic because UciPacketHal::to_bytes() should always be larger then the place we access.
fn get_opcode_from_uci_control_packet(packet: &UciPacketHal) -> u8 {
    get_oid_from_uci_control_packet(&packet.clone().to_bytes())
}

fn is_uci_control_packet(message_type: MessageType) -> bool {
    match message_type {
        MessageType::Command
        | MessageType::Response
        | MessageType::Notification
        | MessageType::ReservedForTesting1
        | MessageType::ReservedForTesting2 => true,
        _ => false,
    }
}

pub fn build_uci_control_packet(
    message_type: MessageType,
    group_id: GroupId,
    opcode: u8,
    payload: Option<Bytes>,
) -> Option<UciControlPacket> {
    if !is_uci_control_packet(message_type) {
        error!("Only control packets are allowed, MessageType: {message_type:?}");
        return None;
    }
    Some(UciControlPacketBuilder { group_id, message_type, opcode, payload }.build())
}

// Ensure that the new packet fragment belong to the same packet.
fn is_same_control_packet(header: &UciControlPacketHeader, packet: &UciPacketHal) -> bool {
    is_uci_control_packet(header.message_type)
        && header.message_type == packet.get_message_type()
        && header.group_id == packet.get_group_id_or_data_packet_format().into()
        && header.opcode == get_opcode_from_uci_control_packet(packet)
}

fn is_device_state_err_control_packet(packet: &UciPacketHal) -> bool {
    packet.get_message_type() ==  MessageType::Notification.into()
    && packet.get_group_id_or_data_packet_format() == GroupIdOrDataPacketFormat::Core.into()
    && get_opcode_from_uci_control_packet(packet) == CoreOpCode::CoreDeviceStatusNtf.into()
    && packet.clone().to_vec()[UCI_PACKET_HAL_HEADER_LEN] == DeviceState::DeviceStateError.into()
}

impl UciControlPacket {
    // For some usage, we need to get the raw payload.
    pub fn to_raw_payload(self) -> Vec<u8> {
        self.to_bytes().slice(UCI_PACKET_HEADER_LEN..).to_vec()
    }
}

// Helper to convert from vector of |UciPacketHal| to |UciControlPacket|. An example
// usage is to convert a list UciPacketHAL fragments to one UciPacket, during de-fragmentation.
impl TryFrom<Vec<UciPacketHal>> for UciControlPacket {
    type Error = Error;

    fn try_from(packets: Vec<UciPacketHal>) -> Result<Self> {
        if packets.is_empty() {
            return Err(Error::InvalidPacketError);
        }

        // Store header info from the first packet.
        let header = UciControlPacketHeader::new(
            packets[0].get_message_type(),
            packets[0].get_group_id_or_data_packet_format().into(),
            get_opcode_from_uci_control_packet(&packets[0]),
        )?;

        // Create the reassembled payload.
        let mut payload_buf = BytesMut::new();
        for packet in packets {
            // Ensure that the new fragment is part of the same packet.
            if !is_same_control_packet(&header, &packet) {
                // if DEVICE_STATE_ERROR notification is received while waiting for remaining fragments,
                // process it and send to upper layer for device recovery
                if is_device_state_err_control_packet(&packet) {
                   error!("Received device reset error: {:?}", packet);
                   return UciControlPacket::parse(
                        &UciControlPacketBuilder {
                            message_type: packet.get_message_type(),
                            group_id: packet.get_group_id_or_data_packet_format().into(),
                            opcode: get_opcode_from_uci_control_packet(&packet),
                            payload: Some(packet.to_bytes().slice(UCI_PACKET_HAL_HEADER_LEN..)),
                        }
                        .build()
                        .to_bytes(),
                   );
                }
                error!("Received unexpected fragment: {:?}", packet);
                return Err(Error::InvalidPacketError);
            }
            // get payload by stripping the header.
            payload_buf.extend_from_slice(&packet.to_bytes().slice(UCI_PACKET_HAL_HEADER_LEN..))
        }

        // Create assembled |UciControlPacket| and convert to bytes again since we need to
        // reparse the packet after defragmentation to get the appropriate message.
        UciControlPacket::parse(
            &UciControlPacketBuilder {
                message_type: header.message_type,
                group_id: header.group_id,
                opcode: header.opcode,
                payload: Some(payload_buf.into()),
            }
            .build()
            .to_bytes(),
        )
    }
}

#[derive(Debug, Clone)]
pub struct RawUciControlPacket {
    pub mt: u8,
    pub gid: u8,
    pub oid: u8,
    pub payload: Vec<u8>,
}

impl RawUciControlPacket {
    // Match the GID and OID to confirm the UCI packet (represented by header) is
    // the same as the stored signature. We don't match the MT because they can be
    // different (eg: CMD/RSP pair).
    pub fn is_same_signature_bytes(&self, header: &[u8]) -> bool {
        let gid = get_gid_from_uci_control_packet(header);
        let oid = get_oid_from_uci_control_packet(header);
        gid == self.gid && oid == self.oid
    }
}

fn is_uci_data_packet(message_type: MessageType) -> bool {
    message_type == MessageType::Data
}

fn is_data_rcv_or_radar_format(data_packet_format: DataPacketFormat) -> bool {
    data_packet_format == DataPacketFormat::DataRcv
        || data_packet_format == DataPacketFormat::RadarDataMessage
}

fn try_into_data_payload(
    packet: UciPacketHal,
    expected_data_packet_format: DataPacketFormat,
) -> Result<Bytes> {
    let dpf: DataPacketFormat = packet.get_group_id_or_data_packet_format().try_into()?;
    if is_uci_data_packet(packet.get_message_type()) && dpf == expected_data_packet_format {
        Ok(packet.to_bytes().slice(UCI_PACKET_HAL_HEADER_LEN..))
    } else {
        error!("Received unexpected data packet fragment: {:?}", packet);
        Err(Error::InvalidPacketError)
    }
}

// Helper to convert from vector of |UciPacketHal| to |UciDataPacket|. An example
// usage is to convert a list UciPacketHAL fragments to one UciPacket, during de-fragmentation.
impl TryFrom<Vec<UciPacketHal>> for UciDataPacket {
    type Error = Error;

    fn try_from(packets: Vec<UciPacketHal>) -> Result<Self> {
        if packets.is_empty() {
            return Err(Error::InvalidPacketError);
        }

        let dpf: DataPacketFormat = packets[0].get_group_id_or_data_packet_format().try_into()?;
        if !is_data_rcv_or_radar_format(dpf) {
            error!("Unexpected data packet format {:?}", dpf);
        }

        // Create the reassembled payload.
        let mut payload_buf = Bytes::new();
        for packet in packets {
            // Ensure that the fragment is a Data Rcv packet.
            // Get payload by stripping the header.
            payload_buf = [payload_buf, try_into_data_payload(packet, dpf)?].concat().into();
        }

        // Create assembled |UciDataPacket| and convert to bytes again since we need to
        // reparse the packet after defragmentation to get the appropriate message.
        UciDataPacket::parse(
            &UciDataPacketBuilder {
                message_type: MessageType::Data,
                data_packet_format: dpf,
                payload: Some(payload_buf.into()),
            }
            .build()
            .to_bytes(),
        )
    }
}

// Helper to convert from |UciControlPacket| to vector of |UciControlPacketHal|s. An
// example usage is to do this conversion for fragmentation (from Host to UWBS).
impl From<UciControlPacket> for Vec<UciControlPacketHal> {
    fn from(packet: UciControlPacket) -> Self {
        // Store header info.
        let header = match UciControlPacketHeader::new(
            packet.get_message_type(),
            packet.get_group_id(),
            packet.get_opcode(),
        ) {
            Ok(hdr) => hdr,
            _ => {
                error!(
                    "Unable to parse UciControlPacketHeader from UciControlPacket: {:?}",
                    packet
                );
                return Vec::new();
            }
        };

        let mut fragments = Vec::new();
        // get payload by stripping the header.
        let payload = packet.to_bytes().slice(UCI_PACKET_HEADER_LEN..);
        if payload.is_empty() {
            fragments.push(
                UciControlPacketHalBuilder {
                    message_type: header.message_type,
                    group_id_or_data_packet_format: header.group_id.into(),
                    opcode: header.opcode,
                    packet_boundary_flag: PacketBoundaryFlag::Complete,
                    payload: None,
                }
                .build(),
            );
        } else {
            let mut fragments_iter = payload.chunks(MAX_PAYLOAD_LEN).peekable();
            while let Some(fragment) = fragments_iter.next() {
                // Set the last fragment complete if this is last fragment.
                let pbf = if let Some(nxt_fragment) = fragments_iter.peek() {
                    PacketBoundaryFlag::NotComplete
                } else {
                    PacketBoundaryFlag::Complete
                };
                fragments.push(
                    UciControlPacketHalBuilder {
                        message_type: header.message_type,
                        group_id_or_data_packet_format: header.group_id.into(),
                        opcode: header.opcode,
                        packet_boundary_flag: pbf,
                        payload: Some(Bytes::from(fragment.to_owned())),
                    }
                    .build(),
                );
            }
        }
        fragments
    }
}

// Helper to convert From<UciDataSnd> into Vec<UciDataPacketHal>. An
// example usage is for fragmentation in the Data Packet Tx flow.
pub fn fragment_data_msg_send(packet: UciDataSnd, max_payload_len: usize) -> Vec<UciDataPacketHal> {
    let mut fragments = Vec::new();
    let dpf = packet.get_data_packet_format().into();

    // get payload by stripping the header.
    let payload = packet.to_bytes().slice(UCI_DATA_SND_PACKET_HEADER_LEN..);
    if payload.is_empty() {
        fragments.push(
            UciDataPacketHalBuilder {
                group_id_or_data_packet_format: dpf,
                packet_boundary_flag: PacketBoundaryFlag::Complete,
                payload: None,
            }
            .build(),
        );
    } else {
        let mut fragments_iter = payload.chunks(max_payload_len).peekable();
        while let Some(fragment) = fragments_iter.next() {
            // Set the last fragment complete if this is last fragment.
            let pbf = if let Some(nxt_fragment) = fragments_iter.peek() {
                PacketBoundaryFlag::NotComplete
            } else {
                PacketBoundaryFlag::Complete
            };
            fragments.push(
                UciDataPacketHalBuilder {
                    group_id_or_data_packet_format: dpf,
                    packet_boundary_flag: pbf,
                    payload: Some(Bytes::from(fragment.to_owned())),
                }
                .build(),
            );
        }
    }
    fragments
}

#[derive(Default, Debug)]
pub struct PacketDefrager {
    // Cache to store incoming fragmented packets in the middle of reassembly.
    // Will be empty if there is no reassembly in progress.
    // TODO(b/261762781): Prefer this to be UciControlPacketHal
    control_fragment_cache: Vec<UciPacketHal>,
    // TODO(b/261762781): Prefer this to be UciDataPacketHal
    data_fragment_cache: Vec<UciPacketHal>,
    // Raw packet payload bytes cache
    raw_fragment_cache: Vec<u8>,
}

pub enum UciDefragPacket {
    Control(UciControlPacket),
    Data(UciDataPacket),
    Raw(Result<()>, RawUciControlPacket),
}

impl PacketDefrager {
    pub fn defragment_packet(
        &mut self,
        msg: &[u8],
        last_raw_cmd: Option<RawUciControlPacket>,
    ) -> Option<UciDefragPacket> {
        if let Some(raw_cmd) = last_raw_cmd {
            let mt_u8 = get_mt_from_uci_packet(msg);
            match MessageType::try_from(u8::from(mt_u8)) {
                Ok(mt) => match mt {
                    // Parse only a UCI response packet as a Raw packet.
                    MessageType::Response => {
                        return self.defragment_raw_uci_response_packet(msg, raw_cmd);
                    }
                    _ => { /* Fallthrough to de-frag as a normal UCI packet below */ }
                },
                Err(_) => {
                    error!("Rx packet from HAL has unrecognized MT={}", mt_u8);
                    return Some(UciDefragPacket::Raw(
                        Err(Error::InvalidPacketError),
                        RawUciControlPacket { mt: mt_u8, gid: 0, oid: 0, payload: Vec::new() },
                    ));
                }
            };
        }

        let packet = UciPacketHal::parse(msg)
            .or_else(|e| {
                error!("Failed to parse packet: {:?}", e);
                Err(e)
            })
            .ok()?;

        let pbf = packet.get_packet_boundary_flag();

        // TODO(b/261762781): The current implementation allows for the possibility that we receive
        // interleaved Control/Data HAL packets, and so uses separate caches for them. In the
        // future, if we determine that interleaving is not possible, this can be simplified.
        if is_uci_control_packet(packet.get_message_type()) {
            // Add the incoming fragment to the control packet cache.
            self.control_fragment_cache.push(packet);
            if pbf == PacketBoundaryFlag::NotComplete {
                // Wait for remaining fragments.
                return None;
            }

            // All fragments received, defragment the control packet.
            match self.control_fragment_cache.drain(..).collect::<Vec<_>>().try_into() {
                Ok(packet) => Some(UciDefragPacket::Control(packet)),
                Err(e) => {
                    error!("Failed to defragment control packet: {:?}", e);
                    None
                }
            }
        } else {
            // Add the incoming fragment to the data packet cache.
            self.data_fragment_cache.push(packet);
            if pbf == PacketBoundaryFlag::NotComplete {
                // Wait for remaining fragments.
                return None;
            }

            // All fragments received, defragment the data packet.
            match self.data_fragment_cache.drain(..).collect::<Vec<_>>().try_into() {
                Ok(packet) => Some(UciDefragPacket::Data(packet)),
                Err(e) => {
                    error!("Failed to defragment data packet: {:?}", e);
                    None
                }
            }
        }
    }

    fn defragment_raw_uci_response_packet(
        &mut self,
        msg: &[u8],
        raw_cmd: RawUciControlPacket,
    ) -> Option<UciDefragPacket> {
        let mt_u8 = get_mt_from_uci_packet(msg);
        let pbf = get_pbf_from_uci_packet(msg);
        let gid = get_gid_from_uci_control_packet(msg);
        let oid = get_oid_from_uci_control_packet(msg);
        if raw_cmd.is_same_signature_bytes(msg) {
            // Store only the packet payload bytes (UCI header should not be stored).
            self.raw_fragment_cache.extend_from_slice(&msg[UCI_PACKET_HAL_HEADER_LEN..]);

            if pbf == u8::from(PacketBoundaryFlag::NotComplete) {
                return None;
            }

            // All fragments received, defragment and return the Raw packet's payload bytes.
            return Some(UciDefragPacket::Raw(
                Ok(()),
                RawUciControlPacket {
                    mt: mt_u8,
                    gid,
                    oid,
                    payload: self.raw_fragment_cache.drain(..).collect(),
                },
            ));
        } else {
            error!(
                "Rx packet from HAL (MT={}, PBF={}, GID={}, OID={}) has non-matching\
                   RawCmd signature",
                mt_u8, pbf, gid, oid
            );
            return Some(UciDefragPacket::Raw(
                Err(Error::InvalidPacketError),
                RawUciControlPacket { mt: mt_u8, gid, oid, payload: Vec::new() },
            ));
        }
    }
}

#[allow(dead_code)]
#[derive(Debug, Clone)]
pub struct ParsedDiagnosticNtfPacket {
    session_token: u32,
    sequence_number: u32,
    frame_reports: Vec<ParsedFrameReport>,
}

#[allow(dead_code)]
#[derive(Debug, Clone)]
pub struct ParsedFrameReport {
    uwb_msg_id: u8,
    action: u8,
    antenna_set: u8,
    rssi: Vec<u8>,
    aoa: Vec<AoaMeasurement>,
    cir: Vec<CirValue>,
}

pub fn parse_diagnostics_ntf(evt: AndroidRangeDiagnosticsNtf) -> Result<ParsedDiagnosticNtfPacket> {
    let session_token = evt.get_session_token();
    let sequence_number = evt.get_sequence_number();
    let mut parsed_frame_reports = Vec::new();
    for report in evt.get_frame_reports() {
        let mut rssi_vec = Vec::new();
        let mut aoa_vec = Vec::new();
        let mut cir_vec = Vec::new();
        for tlv in &report.frame_report_tlvs {
            match FrameReportTlvPacket::parse(
                &[vec![tlv.t as u8, tlv.v.len() as u8, (tlv.v.len() >> 8) as u8], tlv.v.clone()]
                    .concat(),
            ) {
                Ok(pkt) => match pkt.specialize() {
                    FrameReportTlvPacketChild::Rssi(rssi) => {
                        rssi_vec.append(&mut rssi.get_rssi().clone())
                    }
                    FrameReportTlvPacketChild::Aoa(aoa) => {
                        aoa_vec.append(&mut aoa.get_aoa().clone())
                    }
                    FrameReportTlvPacketChild::Cir(cir) => {
                        cir_vec.append(&mut cir.get_cir_value().clone())
                    }
                    _ => return Err(Error::InvalidPacketError),
                },
                Err(e) => {
                    error!("Failed to parse the packet {:?}", e);
                    return Err(Error::InvalidPacketError);
                }
            }
        }
        parsed_frame_reports.push(ParsedFrameReport {
            uwb_msg_id: report.uwb_msg_id,
            action: report.action,
            antenna_set: report.antenna_set,
            rssi: rssi_vec,
            aoa: aoa_vec,
            cir: cir_vec,
        });
    }
    Ok(ParsedDiagnosticNtfPacket {
        session_token,
        sequence_number,
        frame_reports: parsed_frame_reports,
    })
}

#[derive(Debug, Clone, PartialEq)]
pub enum Controlees {
    NoSessionKey(Vec<Controlee>),
    ShortSessionKey(Vec<Controlee_V2_0_16_Byte_Version>),
    LongSessionKey(Vec<Controlee_V2_0_32_Byte_Version>),
}

// TODO(ziyiw): Replace these functions after making uwb_uci_packets::Controlee::write_to() public.
pub fn write_controlee(controlee: &Controlee) -> BytesMut {
    let mut buffer = BytesMut::new();
    buffer.extend_from_slice(&controlee.short_address);
    let subsession_id = controlee.subsession_id;
    buffer.extend_from_slice(&subsession_id.to_le_bytes()[0..4]);
    buffer
}

pub fn write_controlee_2_0_16byte(controlee: &Controlee_V2_0_16_Byte_Version) -> BytesMut {
    let mut buffer = BytesMut::new();
    buffer.extend_from_slice(&controlee.short_address);
    let subsession_id = controlee.subsession_id;
    buffer.extend_from_slice(&subsession_id.to_le_bytes()[0..4]);
    buffer.extend_from_slice(&controlee.subsession_key);
    buffer
}

pub fn write_controlee_2_0_32byte(controlee: &Controlee_V2_0_32_Byte_Version) -> BytesMut {
    let mut buffer = BytesMut::new();
    buffer.extend_from_slice(&controlee.short_address);
    let subsession_id = controlee.subsession_id;
    buffer.extend_from_slice(&subsession_id.to_le_bytes()[0..4]);
    buffer.extend_from_slice(&controlee.subsession_key);
    buffer
}

/// Generate the SessionUpdateControllerMulticastListCmd packet.
///
/// This function can build the packet with/without message control, which
/// is indicated by action parameter.
pub fn build_session_update_controller_multicast_list_cmd(
    session_token: u32,
    action: UpdateMulticastListAction,
    controlees: Controlees,
) -> Result<SessionUpdateControllerMulticastListCmd> {
    let mut controlees_buf = BytesMut::new();
    match controlees {
        Controlees::NoSessionKey(controlee_v1) => {
            controlees_buf.extend_from_slice(&(controlee_v1.len() as u8).to_le_bytes());
            for controlee in controlee_v1 {
                controlees_buf.extend_from_slice(&write_controlee(&controlee));
            }
        }
        Controlees::ShortSessionKey(controlee_v2)
            if action == UpdateMulticastListAction::AddControleeWithShortSubSessionKey =>
        {
            controlees_buf.extend_from_slice(&(controlee_v2.len() as u8).to_le_bytes());
            for controlee in controlee_v2 {
                controlees_buf.extend_from_slice(&write_controlee_2_0_16byte(&controlee));
            }
        }
        Controlees::LongSessionKey(controlee_v2)
            if action == UpdateMulticastListAction::AddControleeWithLongSubSessionKey =>
        {
            controlees_buf.extend_from_slice(&(controlee_v2.len() as u8).to_le_bytes());
            for controlee in controlee_v2 {
                controlees_buf.extend_from_slice(&write_controlee_2_0_32byte(&controlee));
            }
        }
        _ => return Err(Error::InvalidPacketError),
    }
    Ok(SessionUpdateControllerMulticastListCmdBuilder {
        session_token,
        action,
        payload: Some(controlees_buf.freeze()),
    }
    .build())
}

/// building Data transfer phase config command
pub fn build_data_transfer_phase_config_cmd(
    session_token: u32,
    dtpcm_repetition: u8,
    data_transfer_control: u8,
    dtpml_size: u8,
    mac_address: Vec<u8>,
    slot_bitmap: Vec<u8>,
) -> Result<SessionDataTransferPhaseConfigCmd> {
    let mut dtpml_buffer = BytesMut::new();

    //calculate mac address mode from data transfer control
    let mac_address_mode = data_transfer_control & 0x01;

    // Calculate mac address size based on address mode
    let mac_address_size = match mac_address_mode {
        SHORT_ADDRESS => 2,
        EXTENDED_ADDRESS => 8,
        _ => return Err(Error::InvalidPacketError),
    };

    // Calculate slot bitmap size from data transfer control
    let slot_bitmap_size = 1 << ((data_transfer_control & 0x0F) >> 1);

    // Prepare segmented vectors for mac_address
    let mac_address_vec: Vec<_> =
        mac_address.chunks(mac_address_size).map(|chunk| chunk.to_owned()).collect();

    // Prepare segmented vectors for slot_bitmap
    let slot_bitmap_vec: Vec<_> =
        slot_bitmap.chunks(slot_bitmap_size).map(|chunk| chunk.to_owned()).collect();

    // Validate sizes of mac_address and slot_bitmap
    if slot_bitmap_vec.len() != dtpml_size.into() || mac_address_vec.len() != dtpml_size.into() {
        return Err(Error::InvalidPacketError);
    }

    // Combine segmented vectors into dtpml_buffer
    for (elem1, elem2) in mac_address_vec.into_iter().zip(slot_bitmap_vec.into_iter()) {
        dtpml_buffer.extend_from_slice(&elem1);
        dtpml_buffer.extend_from_slice(&elem2);
    }

    Ok(SessionDataTransferPhaseConfigCmdBuilder {
        session_token,
        dtpcm_repetition,
        data_transfer_control,
        dtpml_size,
        payload: Some(dtpml_buffer.freeze()),
    }
    .build())
}

impl Drop for AppConfigTlv {
    fn drop(&mut self) {
        if self.cfg_id == AppConfigTlvType::VendorId || self.cfg_id == AppConfigTlvType::StaticStsIv
        {
            self.v.zeroize();
        }
    }
}

// Radar data 'bits per sample' field isn't a raw value, instead it's an enum
// that maps to the raw value. We need this mapping to get the max sample size
// length.
pub fn radar_bytes_per_sample_value(bps: BitsPerSample) -> u8 {
    match bps {
        BitsPerSample::Value32 => 4,
        BitsPerSample::Value48 => 6,
        BitsPerSample::Value64 => 8,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_diagnostics_ntf() {
        let rssi_vec = vec![0x01, 0x02, 0x03];
        let rssi = RssiBuilder { rssi: rssi_vec.clone() }.build();
        let aoa_1 = AoaMeasurement { tdoa: 1, pdoa: 2, aoa: 3, fom: 4, t: 1 };
        let aoa_2 = AoaMeasurement { tdoa: 5, pdoa: 6, aoa: 7, fom: 8, t: 2 };
        let aoa = AoaBuilder { aoa: vec![aoa_1.clone(), aoa_2.clone()] }.build();
        let cir_vec = vec![CirValue {
            first_path_index: 1,
            first_path_snr: 2,
            first_path_ns: 3,
            peak_path_index: 4,
            peak_path_snr: 5,
            peak_path_ns: 6,
            first_path_sample_offset: 7,
            samples_number: 2,
            sample_window: vec![0, 1, 2, 3],
        }];
        let cir = CirBuilder { cir_value: cir_vec.clone() }.build();
        let mut frame_reports = Vec::new();
        let tlvs = vec![
            FrameReportTlv { t: rssi.get_t(), v: rssi.get_rssi().to_vec() },
            FrameReportTlv { t: aoa.get_t(), v: aoa.to_vec()[3..].to_vec() },
            FrameReportTlv { t: cir.get_t(), v: cir.to_vec()[3..].to_vec() },
        ];
        let frame_report =
            FrameReport { uwb_msg_id: 1, action: 1, antenna_set: 1, frame_report_tlvs: tlvs };
        frame_reports.push(frame_report);
        let packet = AndroidRangeDiagnosticsNtfBuilder {
            session_token: 1,
            sequence_number: 1,
            frame_reports,
        }
        .build();
        let mut parsed_packet = parse_diagnostics_ntf(packet).unwrap();
        let parsed_frame_report = parsed_packet.frame_reports.pop().unwrap();
        assert_eq!(rssi_vec, parsed_frame_report.rssi);
        assert_eq!(aoa_1, parsed_frame_report.aoa[0]);
        assert_eq!(aoa_2, parsed_frame_report.aoa[1]);
        assert_eq!(cir_vec, parsed_frame_report.cir);
    }

    #[test]
    fn test_write_controlee() {
        let short_address: [u8; 2] = [2, 3];
        let controlee: Controlee = Controlee { short_address, subsession_id: 3 };
        let bytes = write_controlee(&controlee);
        let parsed_controlee = Controlee::parse(&bytes).unwrap();
        assert_eq!(controlee, parsed_controlee);
    }

    #[test]
    fn test_build_multicast_update_packet() {
        let short_address: [u8; 2] = [0x12, 0x34];
        let controlee = Controlee { short_address, subsession_id: 0x1324_3546 };
        let packet: UciControlPacket = build_session_update_controller_multicast_list_cmd(
            0x1425_3647,
            UpdateMulticastListAction::AddControlee,
            Controlees::NoSessionKey(vec![controlee; 1]),
        )
        .unwrap()
        .into();
        let packet_fragments: Vec<UciControlPacketHal> = packet.into();
        let uci_packet: Vec<u8> = packet_fragments[0].clone().into();
        assert_eq!(
            uci_packet,
            vec![
                0x21, 0x07, 0x00, 0x0c, // 2(packet info), RFU, payload length(12)
                0x47, 0x36, 0x25, 0x14, // 4(session id (LE))
                0x00, 0x01, 0x12, 0x34, // action, # controlee, 2(short address (LE))
                0x46, 0x35, 0x24, 0x13, // 4(subsession id (LE))
            ]
        );
    }

    #[test]
    fn test_to_raw_payload() {
        let payload = vec![0x11, 0x22, 0x33];
        let payload_clone = payload.clone();
        let packet = UciControlPacketBuilder {
            group_id: GroupId::Test,
            message_type: MessageType::Response,
            opcode: 0x5,
            payload: Some(payload_clone.into()),
        }
        .build();

        assert_eq!(payload, packet.to_raw_payload());
    }

    #[test]
    fn test_to_raw_payload_empty() {
        let payload: Vec<u8> = vec![];
        let packet = UciControlPacketBuilder {
            group_id: GroupId::Test,
            message_type: MessageType::Response,
            opcode: 0x5,
            payload: None,
        }
        .build();

        assert_eq!(payload, packet.to_raw_payload());
    }

    #[cfg(test)]
    mod tests {
        use crate::{extract_u16, extract_u32, extract_u64, extract_u8, extract_vec};
        #[test]
        fn test_extract_func() {
            let bytes = [0x1, 0x3, 0x5, 0x7, 0x9, 0x2, 0x4, 0x05, 0x07, 0x09, 0x0a];
            let mut ptr = 0;

            let u8_val = extract_u8(&bytes, &mut ptr, 1);
            assert_eq!(u8_val, Some(0x1));
            assert_eq!(ptr, 1);

            let u16_val = extract_u16(&bytes, &mut ptr, 2);
            assert_eq!(u16_val, Some(0x0503));
            assert_eq!(ptr, 3);

            let u32_val = extract_u32(&bytes, &mut ptr, 3);
            assert_eq!(u32_val, Some(0x020907));
            assert_eq!(ptr, 6);

            let u64_val = extract_u64(&bytes, &mut ptr, 5);
            assert_eq!(u64_val, Some(0x0a09070504));
            assert_eq!(ptr, 11);

            let vec = extract_vec(&bytes, &mut ptr, 3);
            assert_eq!(vec, None);
            assert_eq!(ptr, 11);
        }
    }

    #[test]
    fn test_short_dltdoa_ranging_measurement() {
        let bytes = [
            // All Fields in Little Endian (LE)
            // First measurement
            0x0a, 0x01, 0x33, 0x05, // 2(Mac address), Status, Message Type
            0x53, 0x05, 0x02, 0x05, // 2(Message control), 2(Block Index)
            0x07, 0x09, 0x0a, 0x01, // Round Index, NLoS, 2(AoA Azimuth)
            0x02, 0x05, 0x07, 0x09, // AoA Azimuth FOM, 2(AoA Elevation), AoA Elevation FOM
            0x0a, 0x01, 0x02, 0x05, // RSSI, 3(Tx Timestamp..)
            0x07, 0x09, 0x0a, 0x01, // 4(Tx Timestamp..)
            0x02, 0x05, 0x07, 0x09, // Tx Timestamp, 3(Rx Timestamp..)
            0x05, 0x07, 0x09, 0x0a, // 2(Rx Timestamp), 2(Anchor Cfo)
            0x01, 0x02, 0x05, 0x07, // 2(Cfo), 2(Initiator Reply Time..)
            0x09, 0x05, 0x07, 0x09, // 2(Initiator Reply Time), 2(Responder Reply Time..)
            0x0a, 0x01, 0x02, 0x05, // 2(Responder Reply Time), 2(Initiator-Responder ToF)
            0x07, 0x09, 0x07, 0x09, // 4(Anchor Location..)
            0x05, 0x07, 0x09, 0x0a, // 4(Anchor Location..)
            0x01, 0x02, 0x05, 0x07, // 2(Anchor Location..), 2(Active Ranging Rounds..)
            0x09, 0x0a, 0x01, 0x02, // 4(Active Ranging Rounds..)
            0x05, 0x07, 0x09, 0x05, // 4(Active Ranging Rounds)
            // Second measurement
            0x0a, 0x01, 0x33, 0x05, // 2(Mac address), Status, Message Type
            0x33, 0x05, 0x02, 0x05, // 2(Message control), 2(Block Index)
            0x07, 0x09, 0x0a, 0x01, // Round Index, NLoS, 2(AoA Azimuth)
            0x02, 0x05, 0x07, 0x09, // AoA Azimuth FOM, 2(AoA Elevation), AoA Elevation FOM
            0x0a, 0x01, 0x02, 0x05, // RSSI, 3(Tx Timestamp..)
            0x07, 0x09, 0x0a, 0x01, // 4(Tx Timestamp..)
            0x02, 0x05, 0x07, 0x09, // Tx Timestamp, 3(Rx Timestamp..)
            0x05, 0x07, 0x09, 0x0a, // 2(Rx Timestamp), 2(Anchor Cfo)
            0x01, 0x02, 0x05, 0x07, // 2(Cfo), 2(Initiator Reply Time..)
            0x09, 0x05, 0x07, 0x09, // 2(Initiator Reply Time), 2(Responder Reply Time..)
            0x0a, 0x01, 0x02, 0x05, // 2(Responder Reply Time), 2(Initiator-Responder ToF)
            0x07, 0x09, 0x07, 0x09, // 4(Anchor Location..)
            0x05, 0x07, 0x09, 0x0a, // 4(Anchor Location..)
            0x01, 0x02, 0x01, 0x02, // 4(Anchor Location)
            0x05, 0x07, 0x09, 0x0a, // 4(Active Ranging Rounds..)
            0x01, 0x02, 0x05, 0x07, // 4(Active Ranging Rounds..)
            0x09, 0x05, // 2(Active Ranging Rounds)
        ];

        let measurements = ShortAddressDlTdoaRangingMeasurement::parse(&bytes, 2).unwrap();
        assert_eq!(measurements.len(), 2);
        let measurement_1 = &measurements[0].measurement;
        let mac_address_1 = &measurements[0].mac_address;
        assert_eq!(*mac_address_1, 0x010a);
        assert_eq!(measurement_1.status, 0x33);
        assert_eq!(measurement_1.message_type, 0x05);
        assert_eq!(measurement_1.message_control, 0x0553);
        assert_eq!(measurement_1.block_index, 0x0502);
        assert_eq!(measurement_1.round_index, 0x07);
        assert_eq!(measurement_1.nlos, 0x09);
        assert_eq!(measurement_1.aoa_azimuth, 0x010a);
        assert_eq!(measurement_1.aoa_azimuth_fom, 0x02);
        assert_eq!(measurement_1.aoa_elevation, 0x0705);
        assert_eq!(measurement_1.aoa_elevation_fom, 0x09);
        assert_eq!(measurement_1.rssi, 0x0a);
        assert_eq!(measurement_1.tx_timestamp, 0x02010a0907050201);
        assert_eq!(measurement_1.rx_timestamp, 0x0705090705);
        assert_eq!(measurement_1.anchor_cfo, 0x0a09);
        assert_eq!(measurement_1.cfo, 0x0201);
        assert_eq!(measurement_1.initiator_reply_time, 0x05090705);
        assert_eq!(measurement_1.responder_reply_time, 0x010a0907);
        assert_eq!(measurement_1.initiator_responder_tof, 0x0502);
        assert_eq!(
            measurement_1.dt_anchor_location,
            vec![0x07, 0x09, 0x07, 0x09, 0x05, 0x07, 0x09, 0x0a, 0x01, 0x02]
        );
        assert_eq!(
            measurement_1.ranging_rounds,
            vec![0x05, 0x07, 0x09, 0x0a, 0x01, 0x02, 0x05, 0x07, 0x09, 0x05,]
        );

        let measurement_2 = &measurements[1].measurement;
        let mac_address_2 = &measurements[1].mac_address;
        assert_eq!(*mac_address_2, 0x010a);
        assert_eq!(measurement_2.status, 0x33);
        assert_eq!(measurement_2.message_type, 0x05);
        assert_eq!(measurement_2.message_control, 0x0533);
        assert_eq!(measurement_2.block_index, 0x0502);
        assert_eq!(measurement_2.round_index, 0x07);
        assert_eq!(measurement_2.nlos, 0x09);
        assert_eq!(measurement_2.aoa_azimuth, 0x010a);
        assert_eq!(measurement_2.aoa_azimuth_fom, 0x02);
        assert_eq!(measurement_2.aoa_elevation, 0x0705);
        assert_eq!(measurement_2.aoa_elevation_fom, 0x09);
        assert_eq!(measurement_2.rssi, 0x0a);
        assert_eq!(measurement_2.tx_timestamp, 0x02010a0907050201);
        assert_eq!(measurement_2.rx_timestamp, 0x0705090705);
        assert_eq!(measurement_2.anchor_cfo, 0x0a09);
        assert_eq!(measurement_2.cfo, 0x0201);
        assert_eq!(measurement_2.initiator_reply_time, 0x05090705);
        assert_eq!(measurement_2.responder_reply_time, 0x010a0907);
        assert_eq!(measurement_2.initiator_responder_tof, 0x0502);
        assert_eq!(
            measurement_2.dt_anchor_location,
            vec![0x07, 0x09, 0x07, 0x09, 0x05, 0x07, 0x09, 0x0a, 0x01, 0x02, 0x01, 0x02]
        );
        assert_eq!(
            measurement_2.ranging_rounds,
            vec![0x05, 0x07, 0x09, 0x0a, 0x01, 0x02, 0x05, 0x07, 0x09, 0x05,]
        );
    }

    #[test]
    fn test_extended_dltdoa_ranging_measurement() {
        let bytes = [
            // All Fields in Little Endian (LE)
            /* First measurement  */
            0x0a, 0x01, 0x33, 0x05, // 4(Mac address..)
            0x33, 0x05, 0x02, 0x05, // 4(Mac address)
            0x07, 0x09, 0x0a, 0x01, // Status, Message Type, 2(Message control),
            0x02, 0x05, 0x07, 0x09, // 2(Block Index), Round Index, NLoS,
            0x0a, 0x01, 0x02, 0x05, // 2(AoA Azimuth), AoA Azimuth FOM, 1(AoA Elevation..)
            0x07, 0x09, 0x0a, // 1(AoA Elevation), AoA Elevation FOM, RSSI,
            0x01, 0x02, 0x05, 0x07, // 4(Tx Timestamp..)
            0x09, 0x05, 0x07, 0x09, // 4(Tx Timestamp),
            0x0a, 0x01, 0x02, 0x05, // 4(Rx Timestamp..)
            0x07, 0x09, 0x05, 0x07, // 4(Rx Timestamp)
            0x09, 0x0a, 0x01, 0x02, // 2(Anchor Cfo), 2(Cfo),
            0x05, 0x07, 0x09, 0x05, // 4(Initiator Reply Time)
            0x07, 0x09, 0x0a, 0x01, // 4(Responder Reply Time),
            0x02, 0x05, 0x02, 0x05, // 2(Initiator-Responder ToF), 2(Active Ranging Rounds)
        ];

        let measurements = ExtendedAddressDlTdoaRangingMeasurement::parse(&bytes, 1).unwrap();
        assert_eq!(measurements.len(), 1);
        let measurement = &measurements[0].measurement;
        let mac_address = &measurements[0].mac_address;
        assert_eq!(*mac_address, 0x050205330533010a);
        assert_eq!(measurement.message_control, 0x010a);
        assert_eq!(measurement.block_index, 0x0502);
        assert_eq!(measurement.round_index, 0x07);
        assert_eq!(measurement.nlos, 0x09);
        assert_eq!(measurement.aoa_azimuth, 0x010a);
        assert_eq!(measurement.aoa_azimuth_fom, 0x02);
        assert_eq!(measurement.aoa_elevation, 0x0705);
        assert_eq!(measurement.aoa_elevation_fom, 0x09);
        assert_eq!(measurement.rssi, 0x0a);
        assert_eq!(measurement.tx_timestamp, 0x0907050907050201);
        assert_eq!(measurement.rx_timestamp, 0x070509070502010a);
        assert_eq!(measurement.anchor_cfo, 0x0a09);
        assert_eq!(measurement.cfo, 0x0201);
        assert_eq!(measurement.initiator_reply_time, 0x05090705);
        assert_eq!(measurement.responder_reply_time, 0x010a0907);
        assert_eq!(measurement.initiator_responder_tof, 0x0502);
        assert_eq!(measurement.dt_anchor_location, vec![]);
        assert_eq!(measurement.ranging_rounds, vec![0x02, 0x05]);
    }
}
