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

//! This module defines the UCI application config parameters for the FiRa ranging session.

use std::collections::{HashMap, HashSet};
use std::convert::{TryFrom, TryInto};

use log::warn;
use num_derive::{FromPrimitive, ToPrimitive};
use zeroize::Zeroize;

use crate::params::app_config_params::{AppConfigParams, AppConfigTlvMap};
use crate::params::uci_packets::{AppConfigTlvType, SessionState, SubSessionId};
use crate::params::utils::{u16_to_bytes, u32_to_bytes, u8_to_bytes, validate};
use crate::utils::{builder_field, getter_field};

// The default value of each parameters.
const DEFAULT_RANGING_ROUND_USAGE: RangingRoundUsage = RangingRoundUsage::DsTwr;
const DEFAULT_STS_CONFIG: StsConfig = StsConfig::Static;
const DEFAULT_CHANNEL_NUMBER: UwbChannel = UwbChannel::Channel9;
const DEFAULT_SLOT_DURATION_RSTU: u16 = 2400;
const DEFAULT_RANGING_DURATION_MS: u32 = 200;
const DEFAULT_MAC_FCS_TYPE: MacFcsType = MacFcsType::Crc16;
const DEFAULT_RANGING_ROUND_CONTROL: RangingRoundControl = RangingRoundControl {
    ranging_result_report_message: true,
    control_message: true,
    measurement_report_message: false,
};
const DEFAULT_AOA_RESULT_REQUEST: AoaResultRequest = AoaResultRequest::ReqAoaResults;
const DEFAULT_RANGE_DATA_NTF_CONFIG: RangeDataNtfConfig = RangeDataNtfConfig::Enable;
const DEFAULT_RANGE_DATA_NTF_PROXIMITY_NEAR_CM: u16 = 0;
const DEFAULT_RANGE_DATA_NTF_PROXIMITY_FAR_CM: u16 = 20000;
const DEFAULT_RFRAME_CONFIG: RframeConfig = RframeConfig::SP3;
const DEFAULT_PREAMBLE_CODE_INDEX: u8 = 10;
const DEFAULT_SFD_ID: u8 = 2;
const DEFAULT_PSDU_DATA_RATE: PsduDataRate = PsduDataRate::Rate6m81;
const DEFAULT_PREAMBLE_DURATION: PreambleDuration = PreambleDuration::T64Symbols;
const DEFAULT_RANGING_TIME_STRUCT: RangingTimeStruct = RangingTimeStruct::BlockBasedScheduling;
const DEFAULT_SLOTS_PER_RR: u8 = 25;
const DEFAULT_TX_ADAPTIVE_PAYLOAD_POWER: TxAdaptivePayloadPower = TxAdaptivePayloadPower::Disable;
const DEFAULT_RESPONDER_SLOT_INDEX: u8 = 1;
const DEFAULT_PRF_MODE: PrfMode = PrfMode::Bprf;
const DEFAULT_SCHEDULED_MODE: ScheduledMode = ScheduledMode::TimeScheduledRanging;
const DEFAULT_KEY_ROTATION: KeyRotation = KeyRotation::Disable;
const DEFAULT_KEY_ROTATION_RATE: u8 = 0;
const DEFAULT_SESSION_PRIORITY: u8 = 50;
const DEFAULT_MAC_ADDRESS_MODE: MacAddressMode = MacAddressMode::MacAddress2Bytes;
const DEFAULT_NUMBER_OF_STS_SEGMENTS: u8 = 1;
const DEFAULT_MAX_RR_RETRY: u16 = 0;
const DEFAULT_UWB_INITIATION_TIME_MS: u32 = 0;
const DEFAULT_HOPPING_MODE: HoppingMode = HoppingMode::Disable;
const DEFAULT_BLOCK_STRIDE_LENGTH: u8 = 0;
const DEFAULT_RESULT_REPORT_CONFIG: ResultReportConfig =
    ResultReportConfig { tof: true, aoa_azimuth: false, aoa_elevation: false, aoa_fom: false };
const DEFAULT_IN_BAND_TERMINATION_ATTEMPT_COUNT: u8 = 1;
const DEFAULT_SUB_SESSION_ID: u32 = 0;
const DEFAULT_BPRF_PHR_DATA_RATE: BprfPhrDataRate = BprfPhrDataRate::Rate850k;
const DEFAULT_MAX_NUMBER_OF_MEASUREMENTS: u16 = 0;
const DEFAULT_STS_LENGTH: StsLength = StsLength::Length64;
const DEFAULT_NUMBER_OF_RANGE_MEASUREMENTS: u8 = 0;
const DEFAULT_NUMBER_OF_AOA_AZIMUTH_MEASUREMENTS: u8 = 0;
const DEFAULT_NUMBER_OF_AOA_ELEVATION_MEASUREMENTS: u8 = 0;

/// The FiRa's application configuration parameters.
/// Ref: FiRa Consortium UWB Command Interface Generic Techinal Specification Version 1.1.0.
#[derive(Clone, PartialEq, Eq)]
pub struct FiraAppConfigParams {
    // FiRa standard config.
    device_type: DeviceType,
    ranging_round_usage: RangingRoundUsage,
    sts_config: StsConfig,
    multi_node_mode: MultiNodeMode,
    channel_number: UwbChannel,
    device_mac_address: UwbAddress,
    dst_mac_address: Vec<UwbAddress>,
    slot_duration_rstu: u16,
    ranging_duration_ms: u32,
    mac_fcs_type: MacFcsType,
    ranging_round_control: RangingRoundControl,
    aoa_result_request: AoaResultRequest,
    range_data_ntf_config: RangeDataNtfConfig,
    range_data_ntf_proximity_near_cm: u16,
    range_data_ntf_proximity_far_cm: u16,
    device_role: DeviceRole,
    rframe_config: RframeConfig,
    preamble_code_index: u8,
    sfd_id: u8,
    psdu_data_rate: PsduDataRate,
    preamble_duration: PreambleDuration,
    ranging_time_struct: RangingTimeStruct,
    slots_per_rr: u8,
    tx_adaptive_payload_power: TxAdaptivePayloadPower,
    responder_slot_index: u8,
    prf_mode: PrfMode,
    scheduled_mode: ScheduledMode,
    key_rotation: KeyRotation,
    key_rotation_rate: u8,
    session_priority: u8,
    mac_address_mode: MacAddressMode,
    vendor_id: [u8; 2],
    static_sts_iv: [u8; 6],
    number_of_sts_segments: u8,
    max_rr_retry: u16,
    uwb_initiation_time_ms: u32,
    hopping_mode: HoppingMode,
    block_stride_length: u8,
    result_report_config: ResultReportConfig,
    in_band_termination_attempt_count: u8,
    sub_session_id: SubSessionId,
    bprf_phr_data_rate: BprfPhrDataRate,
    max_number_of_measurements: u16,
    sts_length: StsLength,

    // Android-specific app config.
    number_of_range_measurements: u8,
    number_of_aoa_azimuth_measurements: u8,
    number_of_aoa_elevation_measurements: u8,
}

/// Explicitly implement Debug trait to prevent logging PII data.
impl std::fmt::Debug for FiraAppConfigParams {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> Result<(), std::fmt::Error> {
        static REDACTED_STR: &str = "redacted";

        f.debug_struct("FiraAppConfigParams")
            .field("device_type", &self.device_type)
            .field("ranging_round_usage", &self.ranging_round_usage)
            .field("sts_config", &self.sts_config)
            .field("multi_node_mode", &self.multi_node_mode)
            .field("channel_number", &self.channel_number)
            .field("device_mac_address", &self.device_mac_address)
            .field("dst_mac_address", &self.dst_mac_address)
            .field("slot_duration_rstu", &self.slot_duration_rstu)
            .field("ranging_duration_ms", &self.ranging_duration_ms)
            .field("mac_fcs_type", &self.mac_fcs_type)
            .field("ranging_round_control", &self.ranging_round_control)
            .field("aoa_result_request", &self.aoa_result_request)
            .field("range_data_ntf_config", &self.range_data_ntf_config)
            .field("range_data_ntf_proximity_near_cm", &self.range_data_ntf_proximity_near_cm)
            .field("range_data_ntf_proximity_far_cm", &self.range_data_ntf_proximity_far_cm)
            .field("device_role", &self.device_role)
            .field("rframe_config", &self.rframe_config)
            .field("preamble_code_index", &self.preamble_code_index)
            .field("sfd_id", &self.sfd_id)
            .field("psdu_data_rate", &self.psdu_data_rate)
            .field("preamble_duration", &self.preamble_duration)
            .field("ranging_time_struct", &self.ranging_time_struct)
            .field("slots_per_rr", &self.slots_per_rr)
            .field("tx_adaptive_payload_power", &self.tx_adaptive_payload_power)
            .field("responder_slot_index", &self.responder_slot_index)
            .field("prf_mode", &self.prf_mode)
            .field("scheduled_mode", &self.scheduled_mode)
            .field("key_rotation", &self.key_rotation)
            .field("key_rotation_rate", &self.key_rotation_rate)
            .field("session_priority", &self.session_priority)
            .field("mac_address_mode", &self.mac_address_mode)
            .field("vendor_id", &REDACTED_STR) // vendor_id field is PII.
            .field("static_sts_iv", &REDACTED_STR) // static_sts_iv field is PII.
            .field("number_of_sts_segments", &self.number_of_sts_segments)
            .field("max_rr_retry", &self.max_rr_retry)
            .field("uwb_initiation_time_ms", &self.uwb_initiation_time_ms)
            .field("hopping_mode", &self.hopping_mode)
            .field("block_stride_length", &self.block_stride_length)
            .field("result_report_config", &self.result_report_config)
            .field("in_band_termination_attempt_count", &self.in_band_termination_attempt_count)
            .field("sub_session_id", &self.sub_session_id)
            .field("bprf_phr_data_rate", &self.bprf_phr_data_rate)
            .field("max_number_of_measurements", &self.max_number_of_measurements)
            .field("sts_length", &self.sts_length)
            .field("number_of_range_measurements", &self.number_of_range_measurements)
            .field("number_of_aoa_azimuth_measurements", &self.number_of_aoa_azimuth_measurements)
            .field(
                "number_of_aoa_elevation_measurements",
                &self.number_of_aoa_elevation_measurements,
            )
            .finish()
    }
}

impl Drop for FiraAppConfigParams {
    fn drop(&mut self) {
        self.vendor_id.zeroize();
        self.static_sts_iv.zeroize();
        self.sub_session_id.zeroize();
    }
}

#[allow(missing_docs)]
impl FiraAppConfigParams {
    // Generate the getter methods for all the fields.
    getter_field!(device_type, DeviceType);
    getter_field!(ranging_round_usage, RangingRoundUsage);
    getter_field!(sts_config, StsConfig);
    getter_field!(multi_node_mode, MultiNodeMode);
    getter_field!(channel_number, UwbChannel);
    getter_field!(device_mac_address, UwbAddress);
    getter_field!(dst_mac_address, Vec<UwbAddress>);
    getter_field!(slot_duration_rstu, u16);
    getter_field!(ranging_duration_ms, u32);
    getter_field!(mac_fcs_type, MacFcsType);
    getter_field!(ranging_round_control, RangingRoundControl);
    getter_field!(aoa_result_request, AoaResultRequest);
    getter_field!(range_data_ntf_config, RangeDataNtfConfig);
    getter_field!(range_data_ntf_proximity_near_cm, u16);
    getter_field!(range_data_ntf_proximity_far_cm, u16);
    getter_field!(device_role, DeviceRole);
    getter_field!(rframe_config, RframeConfig);
    getter_field!(preamble_code_index, u8);
    getter_field!(sfd_id, u8);
    getter_field!(psdu_data_rate, PsduDataRate);
    getter_field!(preamble_duration, PreambleDuration);
    getter_field!(ranging_time_struct, RangingTimeStruct);
    getter_field!(slots_per_rr, u8);
    getter_field!(tx_adaptive_payload_power, TxAdaptivePayloadPower);
    getter_field!(responder_slot_index, u8);
    getter_field!(prf_mode, PrfMode);
    getter_field!(scheduled_mode, ScheduledMode);
    getter_field!(key_rotation, KeyRotation);
    getter_field!(key_rotation_rate, u8);
    getter_field!(session_priority, u8);
    getter_field!(mac_address_mode, MacAddressMode);
    getter_field!(vendor_id, [u8; 2]);
    getter_field!(static_sts_iv, [u8; 6]);
    getter_field!(number_of_sts_segments, u8);
    getter_field!(max_rr_retry, u16);
    getter_field!(uwb_initiation_time_ms, u32);
    getter_field!(hopping_mode, HoppingMode);
    getter_field!(block_stride_length, u8);
    getter_field!(result_report_config, ResultReportConfig);
    getter_field!(in_band_termination_attempt_count, u8);
    getter_field!(sub_session_id, u32);
    getter_field!(bprf_phr_data_rate, BprfPhrDataRate);
    getter_field!(max_number_of_measurements, u16);
    getter_field!(sts_length, StsLength);
    getter_field!(number_of_range_measurements, u8);
    getter_field!(number_of_aoa_azimuth_measurements, u8);
    getter_field!(number_of_aoa_elevation_measurements, u8);

    /// validate if the params are valid.
    fn is_valid(&self) -> Option<()> {
        if self.device_type == DeviceType::Controlee {
            if self.ranging_round_control.ranging_result_report_message {
                warn!("The RRRM bit is ignored by a controlee");
            }
            if self.ranging_round_control.measurement_report_message {
                warn!("The MRM bit is ignored by a controlee");
            }
            if self.hopping_mode != HoppingMode::Disable {
                warn!("hopping_mode is ignored by a controlee");
            }
            if self.block_stride_length != 0 {
                warn!("block_stride_length is ignored by a controlee");
            }
        }
        if self.ranging_time_struct != RangingTimeStruct::BlockBasedScheduling
            && self.block_stride_length != 0
        {
            warn!(
                "block_stride_length is ignored when ranging_time_struct not BlockBasedScheduling"
            );
        }
        if self.prf_mode != PrfMode::Bprf && self.bprf_phr_data_rate != BprfPhrDataRate::Rate850k {
            warn!("BPRF_PHR_DATA_RATE is ignored when prf_mode not BPRF");
        }

        validate(
            (1..=8).contains(&self.dst_mac_address.len()),
            "The length of dst_mac_address should be between 1 to 8",
        )?;
        validate(
            (0..=15).contains(&self.key_rotation_rate),
            "key_rotation_rate should be between 0 to 15",
        )?;
        validate(
            (1..=100).contains(&self.session_priority),
            "session_priority should be between 1 to 100",
        )?;
        validate(
            (0..=10000).contains(&self.uwb_initiation_time_ms),
            "uwb_initiation_time_ms should be between 0 to 10000",
        )?;
        validate(
            (1..=10).contains(&self.in_band_termination_attempt_count),
            "in_band_termination_attempt_count should be between 1 to 10",
        )?;

        match self.mac_address_mode {
            MacAddressMode::MacAddress2Bytes | MacAddressMode::MacAddress8Bytes2BytesHeader => {
                validate(
                    matches!(self.device_mac_address, UwbAddress::Short(_)),
                    "device_mac_address should be short address",
                )?;
                validate(
                    self.dst_mac_address.iter().all(|addr| matches!(addr, UwbAddress::Short(_))),
                    "dst_mac_address should be short address",
                )?;
            }
            MacAddressMode::MacAddress8Bytes => {
                validate(
                    matches!(self.device_mac_address, UwbAddress::Extended(_)),
                    "device_mac_address should be extended address",
                )?;
                validate(
                    self.dst_mac_address.iter().all(|addr| matches!(addr, UwbAddress::Extended(_))),
                    "dst_mac_address should be extended address",
                )?;
            }
        }

        match self.prf_mode {
            PrfMode::Bprf => {
                validate(
                    (9..=12).contains(&self.preamble_code_index),
                    "preamble_code_index should be between 9 to 12 when BPRF",
                )?;
                validate([0, 2].contains(&self.sfd_id), "sfd_id should be 0 or 2 when BPRF")?;
                validate(
                    self.preamble_duration == PreambleDuration::T64Symbols,
                    "preamble_duration should be 64 symbols when BPRF",
                )?;
            }
            _ => {
                validate(
                    (25..=32).contains(&self.preamble_code_index),
                    "preamble_code_index should be between 25 to 32 when HPRF",
                )?;
                validate(
                    (1..=4).contains(&self.sfd_id),
                    "sfd_id should be between 1 to 4 when HPRF",
                )?;
            }
        }

        match self.rframe_config {
            RframeConfig::SP0 => {
                validate(
                    self.number_of_sts_segments == 0,
                    "number_of_sts_segments should be 0 when SP0",
                )?;
            }
            RframeConfig::SP1 | RframeConfig::SP3 => match self.prf_mode {
                PrfMode::Bprf => {
                    validate(
                        self.number_of_sts_segments == 1,
                        "number_of_sts_segments should be 1 when SP1/SP3 and BPRF",
                    )?;
                }
                _ => {
                    validate(
                        [1, 2, 3, 4].contains(&self.number_of_sts_segments),
                        "number_of_sts_segments should be between 1 to 4 when SP1/SP3 and HPRF",
                    )?;
                }
            },
        }

        match self.aoa_result_request {
            AoaResultRequest::ReqAoaResultsInterleaved => {
                validate(
                    self.is_any_number_of_measurement_set(),
                    "At least one of the ratio params should be set for interleaving mode",
                );
            }
            _ => {
                validate(
                    !self.is_any_number_of_measurement_set(),
                    "All of the ratio params should not be set for non-interleaving mode",
                );
            }
        }

        Some(())
    }

    fn is_any_number_of_measurement_set(&self) -> bool {
        self.number_of_range_measurements != DEFAULT_NUMBER_OF_RANGE_MEASUREMENTS
            || self.number_of_aoa_azimuth_measurements != DEFAULT_NUMBER_OF_AOA_AZIMUTH_MEASUREMENTS
            || self.number_of_aoa_elevation_measurements
                != DEFAULT_NUMBER_OF_AOA_ELEVATION_MEASUREMENTS
    }

    /// Determine if the |config_map| is updatable in the state |session_state|.
    pub fn is_config_updatable(config_map: &AppConfigTlvMap, session_state: SessionState) -> bool {
        match session_state {
            SessionState::SessionStateActive => {
                let avalible_list = HashSet::from([
                    AppConfigTlvType::RangingDuration,
                    AppConfigTlvType::RngDataNtf,
                    AppConfigTlvType::RngDataNtfProximityNear,
                    AppConfigTlvType::RngDataNtfProximityFar,
                    AppConfigTlvType::BlockStrideLength,
                ]);
                config_map.keys().all(|key| avalible_list.contains(key))
            }
            SessionState::SessionStateIdle => true,
            _ => false,
        }
    }

    /// Generate the AppConfigTlv HashMap from the FiraAppConfigParams instance.
    pub fn generate_config_map(&self) -> AppConfigTlvMap {
        debug_assert!(self.is_valid().is_some());

        HashMap::from([
            (AppConfigTlvType::DeviceType, u8_to_bytes(self.device_type as u8)),
            (AppConfigTlvType::RangingRoundUsage, u8_to_bytes(self.ranging_round_usage as u8)),
            (AppConfigTlvType::StsConfig, u8_to_bytes(self.sts_config as u8)),
            (AppConfigTlvType::MultiNodeMode, u8_to_bytes(self.multi_node_mode as u8)),
            (AppConfigTlvType::ChannelNumber, u8_to_bytes(self.channel_number as u8)),
            (AppConfigTlvType::NoOfControlee, u8_to_bytes(self.dst_mac_address.len() as u8)),
            (AppConfigTlvType::DeviceMacAddress, self.device_mac_address.clone().into()),
            (AppConfigTlvType::DstMacAddress, addresses_to_bytes(self.dst_mac_address.clone())),
            (AppConfigTlvType::SlotDuration, u16_to_bytes(self.slot_duration_rstu)),
            (AppConfigTlvType::RangingDuration, u32_to_bytes(self.ranging_duration_ms)),
            (AppConfigTlvType::MacFcsType, u8_to_bytes(self.mac_fcs_type as u8)),
            (
                AppConfigTlvType::RangingRoundControl,
                u8_to_bytes(self.ranging_round_control.as_u8()),
            ),
            (AppConfigTlvType::AoaResultReq, u8_to_bytes(self.aoa_result_request as u8)),
            (AppConfigTlvType::RngDataNtf, u8_to_bytes(self.range_data_ntf_config as u8)),
            (
                AppConfigTlvType::RngDataNtfProximityNear,
                u16_to_bytes(self.range_data_ntf_proximity_near_cm),
            ),
            (
                AppConfigTlvType::RngDataNtfProximityFar,
                u16_to_bytes(self.range_data_ntf_proximity_far_cm),
            ),
            (AppConfigTlvType::DeviceRole, u8_to_bytes(self.device_role as u8)),
            (AppConfigTlvType::RframeConfig, u8_to_bytes(self.rframe_config as u8)),
            (AppConfigTlvType::PreambleCodeIndex, u8_to_bytes(self.preamble_code_index)),
            (AppConfigTlvType::SfdId, u8_to_bytes(self.sfd_id)),
            (AppConfigTlvType::PsduDataRate, u8_to_bytes(self.psdu_data_rate as u8)),
            (AppConfigTlvType::PreambleDuration, u8_to_bytes(self.preamble_duration as u8)),
            (AppConfigTlvType::RangingTimeStruct, u8_to_bytes(self.ranging_time_struct as u8)),
            (AppConfigTlvType::SlotsPerRr, u8_to_bytes(self.slots_per_rr)),
            (
                AppConfigTlvType::TxAdaptivePayloadPower,
                u8_to_bytes(self.tx_adaptive_payload_power as u8),
            ),
            (AppConfigTlvType::ResponderSlotIndex, u8_to_bytes(self.responder_slot_index)),
            (AppConfigTlvType::PrfMode, u8_to_bytes(self.prf_mode as u8)),
            (AppConfigTlvType::ScheduledMode, u8_to_bytes(self.scheduled_mode as u8)),
            (AppConfigTlvType::KeyRotation, u8_to_bytes(self.key_rotation as u8)),
            (AppConfigTlvType::KeyRotationRate, u8_to_bytes(self.key_rotation_rate)),
            (AppConfigTlvType::SessionPriority, u8_to_bytes(self.session_priority)),
            (AppConfigTlvType::MacAddressMode, u8_to_bytes(self.mac_address_mode as u8)),
            (AppConfigTlvType::VendorId, self.vendor_id.to_vec()),
            (AppConfigTlvType::StaticStsIv, self.static_sts_iv.to_vec()),
            (AppConfigTlvType::NumberOfStsSegments, u8_to_bytes(self.number_of_sts_segments)),
            (AppConfigTlvType::MaxRrRetry, u16_to_bytes(self.max_rr_retry)),
            (AppConfigTlvType::UwbInitiationTime, u32_to_bytes(self.uwb_initiation_time_ms)),
            (AppConfigTlvType::HoppingMode, u8_to_bytes(self.hopping_mode as u8)),
            (AppConfigTlvType::BlockStrideLength, u8_to_bytes(self.block_stride_length)),
            (AppConfigTlvType::ResultReportConfig, u8_to_bytes(self.result_report_config.as_u8())),
            (
                AppConfigTlvType::InBandTerminationAttemptCount,
                u8_to_bytes(self.in_band_termination_attempt_count),
            ),
            (AppConfigTlvType::SubSessionId, u32_to_bytes(self.sub_session_id)),
            (AppConfigTlvType::BprfPhrDataRate, u8_to_bytes(self.bprf_phr_data_rate as u8)),
            (
                AppConfigTlvType::MaxNumberOfMeasurements,
                u16_to_bytes(self.max_number_of_measurements),
            ),
            (AppConfigTlvType::StsLength, u8_to_bytes(self.sts_length as u8)),
            (
                AppConfigTlvType::NbOfRangeMeasurements,
                u8_to_bytes(self.number_of_range_measurements),
            ),
            (
                AppConfigTlvType::NbOfAzimuthMeasurements,
                u8_to_bytes(self.number_of_aoa_azimuth_measurements),
            ),
            (
                AppConfigTlvType::NbOfElevationMeasurements,
                u8_to_bytes(self.number_of_aoa_elevation_measurements),
            ),
        ])
    }
}

/// The builder pattern for the FiraAppConfigParams.
pub struct FiraAppConfigParamsBuilder {
    device_type: Option<DeviceType>,
    ranging_round_usage: RangingRoundUsage,
    sts_config: StsConfig,
    multi_node_mode: Option<MultiNodeMode>,
    channel_number: UwbChannel,
    device_mac_address: Option<UwbAddress>,
    dst_mac_address: Vec<UwbAddress>,
    slot_duration_rstu: u16,
    ranging_duration_ms: u32,
    mac_fcs_type: MacFcsType,
    ranging_round_control: RangingRoundControl,
    aoa_result_request: AoaResultRequest,
    range_data_ntf_config: RangeDataNtfConfig,
    range_data_ntf_proximity_near_cm: u16,
    range_data_ntf_proximity_far_cm: u16,
    device_role: Option<DeviceRole>,
    rframe_config: RframeConfig,
    preamble_code_index: u8,
    sfd_id: u8,
    psdu_data_rate: PsduDataRate,
    preamble_duration: PreambleDuration,
    ranging_time_struct: RangingTimeStruct,
    slots_per_rr: u8,
    tx_adaptive_payload_power: TxAdaptivePayloadPower,
    responder_slot_index: u8,
    prf_mode: PrfMode,
    scheduled_mode: ScheduledMode,
    key_rotation: KeyRotation,
    key_rotation_rate: u8,
    session_priority: u8,
    mac_address_mode: MacAddressMode,
    vendor_id: Option<[u8; 2]>,
    static_sts_iv: Option<[u8; 6]>,
    number_of_sts_segments: u8,
    max_rr_retry: u16,
    uwb_initiation_time_ms: u32,
    hopping_mode: HoppingMode,
    block_stride_length: u8,
    result_report_config: ResultReportConfig,
    in_band_termination_attempt_count: u8,
    sub_session_id: u32,
    bprf_phr_data_rate: BprfPhrDataRate,
    max_number_of_measurements: u16,
    sts_length: StsLength,
    number_of_range_measurements: u8,
    number_of_aoa_azimuth_measurements: u8,
    number_of_aoa_elevation_measurements: u8,
}

#[allow(clippy::new_without_default)]
#[allow(missing_docs)]
impl FiraAppConfigParamsBuilder {
    /// Fill the default value of each field if exists, otherwise put None.
    pub fn new() -> Self {
        Self {
            device_type: None,
            ranging_round_usage: DEFAULT_RANGING_ROUND_USAGE,
            sts_config: DEFAULT_STS_CONFIG,
            multi_node_mode: None,
            channel_number: DEFAULT_CHANNEL_NUMBER,
            device_mac_address: None,
            dst_mac_address: vec![],
            slot_duration_rstu: DEFAULT_SLOT_DURATION_RSTU,
            ranging_duration_ms: DEFAULT_RANGING_DURATION_MS,
            mac_fcs_type: DEFAULT_MAC_FCS_TYPE,
            ranging_round_control: DEFAULT_RANGING_ROUND_CONTROL,
            aoa_result_request: DEFAULT_AOA_RESULT_REQUEST,
            range_data_ntf_config: DEFAULT_RANGE_DATA_NTF_CONFIG,
            range_data_ntf_proximity_near_cm: DEFAULT_RANGE_DATA_NTF_PROXIMITY_NEAR_CM,
            range_data_ntf_proximity_far_cm: DEFAULT_RANGE_DATA_NTF_PROXIMITY_FAR_CM,
            device_role: None,
            rframe_config: DEFAULT_RFRAME_CONFIG,
            preamble_code_index: DEFAULT_PREAMBLE_CODE_INDEX,
            sfd_id: DEFAULT_SFD_ID,
            psdu_data_rate: DEFAULT_PSDU_DATA_RATE,
            preamble_duration: DEFAULT_PREAMBLE_DURATION,
            ranging_time_struct: DEFAULT_RANGING_TIME_STRUCT,
            slots_per_rr: DEFAULT_SLOTS_PER_RR,
            tx_adaptive_payload_power: DEFAULT_TX_ADAPTIVE_PAYLOAD_POWER,
            responder_slot_index: DEFAULT_RESPONDER_SLOT_INDEX,
            prf_mode: DEFAULT_PRF_MODE,
            scheduled_mode: DEFAULT_SCHEDULED_MODE,
            key_rotation: DEFAULT_KEY_ROTATION,
            key_rotation_rate: DEFAULT_KEY_ROTATION_RATE,
            session_priority: DEFAULT_SESSION_PRIORITY,
            mac_address_mode: DEFAULT_MAC_ADDRESS_MODE,
            vendor_id: None,
            static_sts_iv: None,
            number_of_sts_segments: DEFAULT_NUMBER_OF_STS_SEGMENTS,
            max_rr_retry: DEFAULT_MAX_RR_RETRY,
            uwb_initiation_time_ms: DEFAULT_UWB_INITIATION_TIME_MS,
            hopping_mode: DEFAULT_HOPPING_MODE,
            block_stride_length: DEFAULT_BLOCK_STRIDE_LENGTH,
            result_report_config: DEFAULT_RESULT_REPORT_CONFIG,
            in_band_termination_attempt_count: DEFAULT_IN_BAND_TERMINATION_ATTEMPT_COUNT,
            sub_session_id: DEFAULT_SUB_SESSION_ID,
            bprf_phr_data_rate: DEFAULT_BPRF_PHR_DATA_RATE,
            max_number_of_measurements: DEFAULT_MAX_NUMBER_OF_MEASUREMENTS,
            sts_length: DEFAULT_STS_LENGTH,
            number_of_range_measurements: DEFAULT_NUMBER_OF_RANGE_MEASUREMENTS,
            number_of_aoa_azimuth_measurements: DEFAULT_NUMBER_OF_AOA_AZIMUTH_MEASUREMENTS,
            number_of_aoa_elevation_measurements: DEFAULT_NUMBER_OF_AOA_ELEVATION_MEASUREMENTS,
        }
    }

    pub fn from_params(params: &AppConfigParams) -> Option<Self> {
        match params {
            AppConfigParams::Fira(params) => Some(Self {
                device_type: Some(params.device_type),
                ranging_round_usage: params.ranging_round_usage,
                sts_config: params.sts_config,
                multi_node_mode: Some(params.multi_node_mode),
                channel_number: params.channel_number,
                device_mac_address: Some(params.device_mac_address.clone()),
                dst_mac_address: params.dst_mac_address.clone(),
                slot_duration_rstu: params.slot_duration_rstu,
                ranging_duration_ms: params.ranging_duration_ms,
                mac_fcs_type: params.mac_fcs_type,
                ranging_round_control: params.ranging_round_control.clone(),
                aoa_result_request: params.aoa_result_request,
                range_data_ntf_config: params.range_data_ntf_config,
                range_data_ntf_proximity_near_cm: params.range_data_ntf_proximity_near_cm,
                range_data_ntf_proximity_far_cm: params.range_data_ntf_proximity_far_cm,
                device_role: Some(params.device_role),
                rframe_config: params.rframe_config,
                preamble_code_index: params.preamble_code_index,
                sfd_id: params.sfd_id,
                psdu_data_rate: params.psdu_data_rate,
                preamble_duration: params.preamble_duration,
                ranging_time_struct: params.ranging_time_struct,
                slots_per_rr: params.slots_per_rr,
                tx_adaptive_payload_power: params.tx_adaptive_payload_power,
                responder_slot_index: params.responder_slot_index,
                prf_mode: params.prf_mode,
                scheduled_mode: params.scheduled_mode,
                key_rotation: params.key_rotation,
                key_rotation_rate: params.key_rotation_rate,
                session_priority: params.session_priority,
                mac_address_mode: params.mac_address_mode,
                vendor_id: Some(params.vendor_id),
                static_sts_iv: Some(params.static_sts_iv),
                number_of_sts_segments: params.number_of_sts_segments,
                max_rr_retry: params.max_rr_retry,
                uwb_initiation_time_ms: params.uwb_initiation_time_ms,
                hopping_mode: params.hopping_mode,
                block_stride_length: params.block_stride_length,
                result_report_config: params.result_report_config.clone(),
                in_band_termination_attempt_count: params.in_band_termination_attempt_count,
                sub_session_id: params.sub_session_id,
                bprf_phr_data_rate: params.bprf_phr_data_rate,
                max_number_of_measurements: params.max_number_of_measurements,
                sts_length: params.sts_length,
                number_of_range_measurements: params.number_of_range_measurements,
                number_of_aoa_azimuth_measurements: params.number_of_aoa_azimuth_measurements,
                number_of_aoa_elevation_measurements: params.number_of_aoa_elevation_measurements,
            }),
            _ => None,
        }
    }

    pub fn build(&self) -> Option<AppConfigParams> {
        let params = FiraAppConfigParams {
            device_type: self.device_type?,
            ranging_round_usage: self.ranging_round_usage,
            sts_config: self.sts_config,
            multi_node_mode: self.multi_node_mode?,
            channel_number: self.channel_number,
            device_mac_address: self.device_mac_address.clone()?,
            dst_mac_address: self.dst_mac_address.clone(),
            slot_duration_rstu: self.slot_duration_rstu,
            ranging_duration_ms: self.ranging_duration_ms,
            mac_fcs_type: self.mac_fcs_type,
            ranging_round_control: self.ranging_round_control.clone(),
            aoa_result_request: self.aoa_result_request,
            range_data_ntf_config: self.range_data_ntf_config,
            range_data_ntf_proximity_near_cm: self.range_data_ntf_proximity_near_cm,
            range_data_ntf_proximity_far_cm: self.range_data_ntf_proximity_far_cm,
            device_role: self.device_role?,
            rframe_config: self.rframe_config,
            preamble_code_index: self.preamble_code_index,
            sfd_id: self.sfd_id,
            psdu_data_rate: self.psdu_data_rate,
            preamble_duration: self.preamble_duration,
            ranging_time_struct: self.ranging_time_struct,
            slots_per_rr: self.slots_per_rr,
            tx_adaptive_payload_power: self.tx_adaptive_payload_power,
            responder_slot_index: self.responder_slot_index,
            prf_mode: self.prf_mode,
            scheduled_mode: self.scheduled_mode,
            key_rotation: self.key_rotation,
            key_rotation_rate: self.key_rotation_rate,
            session_priority: self.session_priority,
            mac_address_mode: self.mac_address_mode,
            vendor_id: self.vendor_id?,
            static_sts_iv: self.static_sts_iv?,
            number_of_sts_segments: self.number_of_sts_segments,
            max_rr_retry: self.max_rr_retry,
            uwb_initiation_time_ms: self.uwb_initiation_time_ms,
            hopping_mode: self.hopping_mode,
            block_stride_length: self.block_stride_length,
            result_report_config: self.result_report_config.clone(),
            in_band_termination_attempt_count: self.in_band_termination_attempt_count,
            sub_session_id: self.sub_session_id,
            bprf_phr_data_rate: self.bprf_phr_data_rate,
            max_number_of_measurements: self.max_number_of_measurements,
            sts_length: self.sts_length,
            number_of_range_measurements: self.number_of_range_measurements,
            number_of_aoa_azimuth_measurements: self.number_of_aoa_azimuth_measurements,
            number_of_aoa_elevation_measurements: self.number_of_aoa_elevation_measurements,
        };

        params.is_valid()?;
        Some(AppConfigParams::Fira(params))
    }

    // Generate the setter methods for all the fields.
    builder_field!(device_type, DeviceType, Some);
    builder_field!(ranging_round_usage, RangingRoundUsage);
    builder_field!(sts_config, StsConfig);
    builder_field!(multi_node_mode, MultiNodeMode, Some);
    builder_field!(channel_number, UwbChannel);
    builder_field!(device_mac_address, UwbAddress, Some);
    builder_field!(dst_mac_address, Vec<UwbAddress>);
    builder_field!(slot_duration_rstu, u16);
    builder_field!(ranging_duration_ms, u32);
    builder_field!(mac_fcs_type, MacFcsType);
    builder_field!(ranging_round_control, RangingRoundControl);
    builder_field!(aoa_result_request, AoaResultRequest);
    builder_field!(range_data_ntf_config, RangeDataNtfConfig);
    builder_field!(range_data_ntf_proximity_near_cm, u16);
    builder_field!(range_data_ntf_proximity_far_cm, u16);
    builder_field!(device_role, DeviceRole, Some);
    builder_field!(rframe_config, RframeConfig);
    builder_field!(preamble_code_index, u8);
    builder_field!(sfd_id, u8);
    builder_field!(psdu_data_rate, PsduDataRate);
    builder_field!(preamble_duration, PreambleDuration);
    builder_field!(ranging_time_struct, RangingTimeStruct);
    builder_field!(slots_per_rr, u8);
    builder_field!(tx_adaptive_payload_power, TxAdaptivePayloadPower);
    builder_field!(responder_slot_index, u8);
    builder_field!(prf_mode, PrfMode);
    builder_field!(scheduled_mode, ScheduledMode);
    builder_field!(key_rotation, KeyRotation);
    builder_field!(key_rotation_rate, u8);
    builder_field!(session_priority, u8);
    builder_field!(mac_address_mode, MacAddressMode);
    builder_field!(vendor_id, [u8; 2], Some);
    builder_field!(static_sts_iv, [u8; 6], Some);
    builder_field!(number_of_sts_segments, u8);
    builder_field!(max_rr_retry, u16);
    builder_field!(uwb_initiation_time_ms, u32);
    builder_field!(hopping_mode, HoppingMode);
    builder_field!(block_stride_length, u8);
    builder_field!(result_report_config, ResultReportConfig);
    builder_field!(in_band_termination_attempt_count, u8);
    builder_field!(sub_session_id, u32);
    builder_field!(bprf_phr_data_rate, BprfPhrDataRate);
    builder_field!(max_number_of_measurements, u16);
    builder_field!(sts_length, StsLength);
    builder_field!(number_of_range_measurements, u8);
    builder_field!(number_of_aoa_azimuth_measurements, u8);
    builder_field!(number_of_aoa_elevation_measurements, u8);
}

/// The device type.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum DeviceType {
    /// Controlee
    Controlee = 0,
    /// Controller
    Controller = 1,
}

/// The mode of ranging round usage.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum RangingRoundUsage {
    /// SS-TWR with Deferred Mode
    SsTwr = 1,
    /// DS-TWR with Deferred Mode (default)
    DsTwr = 2,
    /// SS-TWR with Non-deferred Mode
    SsTwrNon = 3,
    /// DS-TWR with Non-deferred Mode
    DsTwrNon = 4,
}

/// This parameter indicates how the system shall generate the STS.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum StsConfig {
    /// Static STS (default)
    Static = 0,
    /// Dynamic STS
    Dynamic = 1,
    /// Dynamic STS for Responder specific Sub-session Key
    DynamicForControleeIndividualKey = 2,
}

/// The mode of multi node.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum MultiNodeMode {
    /// Single device to Single device (Unicast)
    Unicast = 0,
    /// One to Many
    OneToMany = 1,
    /// Many to Many
    ManyToMany = 2,
}

/// The UWB channel number. (default = 9)
#[allow(missing_docs)]
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum UwbChannel {
    Channel5 = 5,
    Channel6 = 6,
    Channel8 = 8,
    Channel9 = 9,
    Channel10 = 10,
    Channel12 = 12,
    Channel13 = 13,
    Channel14 = 14,
}

/// The UWB address.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum UwbAddress {
    /// The short MAC address (2 bytes)
    Short([u8; 2]),
    /// The extended MAC address (8 bytes)
    Extended([u8; 8]),
}

impl From<UwbAddress> for Vec<u8> {
    fn from(item: UwbAddress) -> Self {
        match item {
            UwbAddress::Short(addr) => addr.to_vec(),
            UwbAddress::Extended(addr) => addr.to_vec(),
        }
    }
}

impl TryFrom<Vec<u8>> for UwbAddress {
    type Error = &'static str;
    fn try_from(value: Vec<u8>) -> Result<Self, Self::Error> {
        match value.len() {
            2 => Ok(UwbAddress::Short(value.try_into().unwrap())),
            8 => Ok(UwbAddress::Extended(value.try_into().unwrap())),
            _ => Err("Invalid address length"),
        }
    }
}

fn addresses_to_bytes(addresses: Vec<UwbAddress>) -> Vec<u8> {
    addresses.into_iter().flat_map(Into::<Vec<u8>>::into).collect()
}

/// CRC type in MAC footer.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum MacFcsType {
    /// CRC 16 (default)
    Crc16 = 0,
    /// CRC 32
    Crc32 = 1,
}

/// This parameter is used to tell the UWBS which messages will be included in a Ranging Round.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RangingRoundControl {
    /// Ranging Result Report Message (RRRM)
    ///
    /// If set to true (default), a Controller shall schedule an RRRM in the Ranging Device
    /// Management List (RDML).
    /// If set to false, a Controller shall not schedule an RRRM in the RDML.
    /// This field shall be ignored by a Controlee; Controlees shall follow the message sequence
    /// provided in the RDML.
    pub ranging_result_report_message: bool,
    /// Control Message (CM)
    ///
    /// If set to true (default), a Controller shall send a separate CM and a Controlee shall expect
    /// a separate CM.
    /// If set to false, a Controller shall not send a separate CM and a Controlee shall not expect
    /// a separate CM.
    pub control_message: bool,
    /// Measurement Report Message (MRM)
    ///
    /// If set to false (default), the controller shall schedule the MRM to be sent from the
    /// initiator to the Responder(s) in the RDML.
    /// If set to true, the controller shall schedule the MRM to be sent from the responder(s) to
    /// the initiator in the RDML.
    /// This field shall be ignored by a controlee. The controlees shall follow the message sequence
    /// provided in the RDML
    pub measurement_report_message: bool,
}

impl RangingRoundControl {
    const RANGING_RESULT_REPORT_MESSAGE_BIT_OFFSET: u8 = 0;
    const CONTROL_MESSAGE_BIT_OFFSET: u8 = 1;
    const MEASUREMENT_REPORT_MESSAGE_BIT_OFFSET: u8 = 7;

    fn as_u8(&self) -> u8 {
        let mut value = 0_u8;
        if self.ranging_result_report_message {
            value |= 1 << Self::RANGING_RESULT_REPORT_MESSAGE_BIT_OFFSET;
        }
        if self.control_message {
            value |= 1 << Self::CONTROL_MESSAGE_BIT_OFFSET;
        }
        if self.measurement_report_message {
            value |= 1 << Self::MEASUREMENT_REPORT_MESSAGE_BIT_OFFSET;
        }
        value
    }
}

/// This parameter is used to configure AOA results in the range data notification.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum AoaResultRequest {
    /// Disable AOA
    NoAoaReport = 0,
    /// Enable AOA (default)
    ReqAoaResults = 1,
    /// Enable only AOA Azimuth
    ReqAoaResultsAzimuthOnly = 2,
    /// Enable only AOA Elevation
    ReqAoaResultsElevationOnly = 3,
    /// Enable AOA interleaved
    ReqAoaResultsInterleaved = 0xF0,
}

/// This config is used to enable/disable the range data notification.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum RangeDataNtfConfig {
    /// Disable range data notification
    Disable = 0,
    /// Enable range data notification (default)
    Enable = 1,
    /// Enable range data notification while in proximity range
    EnableProximity = 2,
}

/// The device role.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum DeviceRole {
    /// Responder of the session
    Responder = 0,
    /// Initiator of the session
    Initiator = 1,
}

/// Rframe config.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum RframeConfig {
    /// SP0
    SP0 = 0,
    /// SP1
    SP1 = 1,
    /// SP3 (default)
    SP3 = 3,
}

/// This value configures the data rate.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum PsduDataRate {
    /// 6.81 Mbps (default)
    Rate6m81 = 0,
    /// 7.80 Mbps
    Rate7m80 = 1,
    /// 27.2 Mbps
    Rate27m2 = 2,
    /// 31.2 Mbps
    Rate31m2 = 3,
    /// 850Kbps
    Rate850k = 4,
}

/// Preamble duration is same as Preamble Symbol Repetitions (PSR).
///
/// Two configurations are possible. BPRF uses only 64 symbols. HPRF can use both.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum PreambleDuration {
    /// 32 symbols
    T32Symbols = 0,
    /// 64 symbols (default)
    T64Symbols = 1,
}

/// The type of ranging time scheduling.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum RangingTimeStruct {
    /// Interval Based Scheduling
    IntervalBasedScheduling = 0,
    /// Block Based Scheduling (default)
    BlockBasedScheduling = 1,
}

/// This configuration is used to enable/disable adaptive payload power for TX.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum TxAdaptivePayloadPower {
    /// Disable (default)
    Disable = 0,
    /// Enable
    Enable = 1,
}

/// This parameter is used to configure the mean PRF.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum PrfMode {
    /// 62.4 MHz PRF. BPRF mode (default)
    Bprf = 0,
    /// 124.8 MHz PRF. HPRF mode
    HprfWith124_8MHz = 1,
    /// 249.6 MHz PRF. HPRF mode with data rate 27.2 and 31.2 Mbps
    HprfWith249_6MHz = 2,
}

/// This parameter is used to set the Multinode Ranging Type.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum ScheduledMode {
    /// Time scheduled ranging (default)
    TimeScheduledRanging = 1,
}

/// This configuration is used to enable/disable the key rotation feature during Dynamic STS
/// ranging.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum KeyRotation {
    /// Disable (default)
    Disable = 0,
    /// Enable
    Enable = 1,
}

/// MAC Addressing mode to be used in UWBS.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum MacAddressMode {
    /// MAC address is 2 bytes and 2 bytes to be used in MAC header (default)
    MacAddress2Bytes = 0,
    /// MAC address is 8 bytes and 2 bytes to be used in MAC header
    MacAddress8Bytes2BytesHeader = 1,
    /// MAC address is 8 bytes and 8 bytes to be used in MAC header
    MacAddress8Bytes = 2,
}

/// This parameter is used to enable/disable the hopping.
///
/// Note: This config is applicable only for controller and ignored in case of controlee.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum HoppingMode {
    /// Hopping Diable (default)
    Disable = 0,
    /// FiRa Hopping Enable
    FiraHoppingEnable = 1,
}

/// This config is used to enable/disable the result reports to be included in the RRRM.
///
/// The ToF Report, AoA Azimuth Report and AoA Elevation Report parameters from the FiRa UWB MAC are
/// negotiated OOB.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ResultReportConfig {
    /// TOF report (false: Disable, true: Enable)
    pub tof: bool,
    /// AOA azimuth report (false: Disable, true: Enable)
    pub aoa_azimuth: bool,
    /// AOA elevation report (false: Disable, true: Enable)
    pub aoa_elevation: bool,
    /// AOA FOM report (false: Disable, true: Enable)
    pub aoa_fom: bool,
}

impl ResultReportConfig {
    const TOF_BIT_OFFSET: u8 = 0;
    const AOA_AZIMUTH_BIT_OFFSET: u8 = 1;
    const AOA_ELEVATION_BIT_OFFSET: u8 = 2;
    const AOA_FOM_BIT_OFFSET: u8 = 3;

    fn as_u8(&self) -> u8 {
        let mut value = 0_u8;
        if self.tof {
            value |= 1 << Self::TOF_BIT_OFFSET;
        }
        if self.aoa_azimuth {
            value |= 1 << Self::AOA_AZIMUTH_BIT_OFFSET;
        }
        if self.aoa_elevation {
            value |= 1 << Self::AOA_ELEVATION_BIT_OFFSET;
        }
        if self.aoa_fom {
            value |= 1 << Self::AOA_FOM_BIT_OFFSET;
        }

        value
    }
}

/// The data rate for BPRF mode.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum BprfPhrDataRate {
    /// 850 kbps (default)
    Rate850k = 0,
    /// 6.81 Mbps
    Rate6m81 = 1,
}

/// The number of symbols in an STS segment.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum StsLength {
    /// 32 symbols
    Length32 = 0,
    /// 64 symbols (default)
    Length64 = 1,
    /// 128 symbols
    Length128 = 2,
}

#[cfg(test)]
mod tests {
    use super::*;

    use crate::utils::init_test_logging;

    #[test]
    fn test_ok() {
        init_test_logging();

        let device_type = DeviceType::Controlee;
        let ranging_round_usage = RangingRoundUsage::SsTwr;
        let sts_config = StsConfig::DynamicForControleeIndividualKey;
        let multi_node_mode = MultiNodeMode::ManyToMany;
        let channel_number = UwbChannel::Channel10;
        let device_mac_address = [1, 2, 3, 4, 5, 6, 7, 8];
        let dst_mac_address1 = [2, 2, 3, 4, 5, 6, 7, 8];
        let dst_mac_address2 = [3, 2, 3, 4, 5, 6, 7, 8];
        let slot_duration_rstu = 0x0A28;
        let ranging_duration_ms = 100;
        let mac_fcs_type = MacFcsType::Crc32;
        let ranging_round_control = RangingRoundControl {
            ranging_result_report_message: false,
            control_message: true,
            measurement_report_message: false,
        };
        let aoa_result_request = AoaResultRequest::ReqAoaResultsInterleaved;
        let range_data_ntf_config = RangeDataNtfConfig::EnableProximity;
        let range_data_ntf_proximity_near_cm = 50;
        let range_data_ntf_proximity_far_cm = 200;
        let device_role = DeviceRole::Initiator;
        let rframe_config = RframeConfig::SP1;
        let preamble_code_index = 25;
        let sfd_id = 3;
        let psdu_data_rate = PsduDataRate::Rate7m80;
        let preamble_duration = PreambleDuration::T32Symbols;
        let slots_per_rr = 10;
        let tx_adaptive_payload_power = TxAdaptivePayloadPower::Enable;
        let prf_mode = PrfMode::HprfWith124_8MHz;
        let key_rotation = KeyRotation::Enable;
        let key_rotation_rate = 15;
        let session_priority = 100;
        let mac_address_mode = MacAddressMode::MacAddress8Bytes;
        let vendor_id = [0xFE, 0xDC];
        let static_sts_iv = [0xDF, 0xCE, 0xAB, 0x12, 0x34, 0x56];
        let number_of_sts_segments = 2;
        let max_rr_retry = 3;
        let uwb_initiation_time_ms = 100;
        let result_report_config =
            ResultReportConfig { tof: true, aoa_azimuth: true, aoa_elevation: true, aoa_fom: true };
        let in_band_termination_attempt_count = 8;
        let sub_session_id = 24;
        let sts_length = StsLength::Length128;
        let number_of_range_measurements = 1;
        let number_of_aoa_azimuth_measurements = 2;
        let number_of_aoa_elevation_measurements = 3;

        let mut builder = FiraAppConfigParamsBuilder::new();
        builder
            .device_type(device_type)
            .ranging_round_usage(ranging_round_usage)
            .sts_config(sts_config)
            .multi_node_mode(multi_node_mode)
            .channel_number(channel_number)
            .device_mac_address(UwbAddress::Extended(device_mac_address))
            .dst_mac_address(vec![
                UwbAddress::Extended(dst_mac_address1),
                UwbAddress::Extended(dst_mac_address2),
            ])
            .slot_duration_rstu(slot_duration_rstu)
            .ranging_duration_ms(ranging_duration_ms)
            .mac_fcs_type(mac_fcs_type)
            .ranging_round_control(ranging_round_control.clone())
            .aoa_result_request(aoa_result_request)
            .range_data_ntf_config(range_data_ntf_config)
            .range_data_ntf_proximity_near_cm(range_data_ntf_proximity_near_cm)
            .range_data_ntf_proximity_far_cm(range_data_ntf_proximity_far_cm)
            .device_role(device_role)
            .rframe_config(rframe_config)
            .preamble_code_index(preamble_code_index)
            .sfd_id(sfd_id)
            .psdu_data_rate(psdu_data_rate)
            .preamble_duration(preamble_duration)
            .slots_per_rr(slots_per_rr)
            .tx_adaptive_payload_power(tx_adaptive_payload_power)
            .prf_mode(prf_mode)
            .key_rotation(key_rotation)
            .key_rotation_rate(key_rotation_rate)
            .session_priority(session_priority)
            .mac_address_mode(mac_address_mode)
            .vendor_id(vendor_id)
            .static_sts_iv(static_sts_iv)
            .number_of_sts_segments(number_of_sts_segments)
            .max_rr_retry(max_rr_retry)
            .uwb_initiation_time_ms(uwb_initiation_time_ms)
            .result_report_config(result_report_config.clone())
            .in_band_termination_attempt_count(in_band_termination_attempt_count)
            .sub_session_id(sub_session_id)
            .sts_length(sts_length)
            .number_of_range_measurements(number_of_range_measurements)
            .number_of_aoa_azimuth_measurements(number_of_aoa_azimuth_measurements)
            .number_of_aoa_elevation_measurements(number_of_aoa_elevation_measurements);
        let params = builder.build().unwrap();

        // Verify the generated TLV.
        let config_map = params.generate_config_map();
        let expected_config_map = HashMap::from([
            (AppConfigTlvType::DeviceType, vec![device_type as u8]),
            (AppConfigTlvType::RangingRoundUsage, vec![ranging_round_usage as u8]),
            (AppConfigTlvType::StsConfig, vec![sts_config as u8]),
            (AppConfigTlvType::MultiNodeMode, vec![multi_node_mode as u8]),
            (AppConfigTlvType::ChannelNumber, vec![channel_number as u8]),
            (AppConfigTlvType::NoOfControlee, vec![2]),
            (AppConfigTlvType::DeviceMacAddress, device_mac_address.to_vec()),
            (
                AppConfigTlvType::DstMacAddress,
                [dst_mac_address1, dst_mac_address2].concat().to_vec(),
            ),
            (AppConfigTlvType::SlotDuration, slot_duration_rstu.to_le_bytes().to_vec()),
            (AppConfigTlvType::RangingDuration, ranging_duration_ms.to_le_bytes().to_vec()),
            (AppConfigTlvType::MacFcsType, vec![mac_fcs_type as u8]),
            (AppConfigTlvType::RangingRoundControl, vec![ranging_round_control.as_u8()]),
            (AppConfigTlvType::AoaResultReq, vec![aoa_result_request as u8]),
            (AppConfigTlvType::RngDataNtf, vec![range_data_ntf_config as u8]),
            (
                AppConfigTlvType::RngDataNtfProximityNear,
                range_data_ntf_proximity_near_cm.to_le_bytes().to_vec(),
            ),
            (
                AppConfigTlvType::RngDataNtfProximityFar,
                range_data_ntf_proximity_far_cm.to_le_bytes().to_vec(),
            ),
            (AppConfigTlvType::DeviceRole, vec![device_role as u8]),
            (AppConfigTlvType::RframeConfig, vec![rframe_config as u8]),
            (AppConfigTlvType::PreambleCodeIndex, vec![preamble_code_index]),
            (AppConfigTlvType::SfdId, vec![sfd_id]),
            (AppConfigTlvType::PsduDataRate, vec![psdu_data_rate as u8]),
            (AppConfigTlvType::PreambleDuration, vec![preamble_duration as u8]),
            (AppConfigTlvType::RangingTimeStruct, vec![DEFAULT_RANGING_TIME_STRUCT as u8]),
            (AppConfigTlvType::SlotsPerRr, vec![slots_per_rr]),
            (AppConfigTlvType::TxAdaptivePayloadPower, vec![tx_adaptive_payload_power as u8]),
            (AppConfigTlvType::ResponderSlotIndex, vec![DEFAULT_RESPONDER_SLOT_INDEX]),
            (AppConfigTlvType::PrfMode, vec![prf_mode as u8]),
            (AppConfigTlvType::ScheduledMode, vec![DEFAULT_SCHEDULED_MODE as u8]),
            (AppConfigTlvType::KeyRotation, vec![key_rotation as u8]),
            (AppConfigTlvType::KeyRotationRate, vec![key_rotation_rate]),
            (AppConfigTlvType::SessionPriority, vec![session_priority]),
            (AppConfigTlvType::MacAddressMode, vec![mac_address_mode as u8]),
            (AppConfigTlvType::VendorId, vendor_id.to_vec()),
            (AppConfigTlvType::StaticStsIv, static_sts_iv.to_vec()),
            (AppConfigTlvType::NumberOfStsSegments, vec![number_of_sts_segments]),
            (AppConfigTlvType::MaxRrRetry, max_rr_retry.to_le_bytes().to_vec()),
            (AppConfigTlvType::UwbInitiationTime, uwb_initiation_time_ms.to_le_bytes().to_vec()),
            (AppConfigTlvType::HoppingMode, vec![DEFAULT_HOPPING_MODE as u8]),
            (AppConfigTlvType::BlockStrideLength, vec![DEFAULT_BLOCK_STRIDE_LENGTH]),
            (AppConfigTlvType::ResultReportConfig, vec![result_report_config.as_u8()]),
            (
                AppConfigTlvType::InBandTerminationAttemptCount,
                vec![in_band_termination_attempt_count],
            ),
            (AppConfigTlvType::BprfPhrDataRate, vec![DEFAULT_BPRF_PHR_DATA_RATE as u8]),
            (
                AppConfigTlvType::MaxNumberOfMeasurements,
                DEFAULT_MAX_NUMBER_OF_MEASUREMENTS.to_le_bytes().to_vec(),
            ),
            (AppConfigTlvType::StsLength, vec![sts_length as u8]),
            (AppConfigTlvType::SubSessionId, sub_session_id.to_le_bytes().to_vec()),
            (AppConfigTlvType::NbOfRangeMeasurements, vec![number_of_range_measurements]),
            (AppConfigTlvType::NbOfAzimuthMeasurements, vec![number_of_aoa_azimuth_measurements]),
            (
                AppConfigTlvType::NbOfElevationMeasurements,
                vec![number_of_aoa_elevation_measurements],
            ),
        ]);
        assert_eq!(config_map, expected_config_map);

        // Update the value from the original builder.
        let updated_key_rotation_rate = 10;
        assert_ne!(key_rotation_rate, updated_key_rotation_rate);
        let expected_updated_config_map =
            HashMap::from([(AppConfigTlvType::KeyRotationRate, vec![updated_key_rotation_rate])]);

        let updated_params1 = builder.key_rotation_rate(updated_key_rotation_rate).build().unwrap();
        let updated_config_map1 = updated_params1
            .generate_updated_config_map(&params, SessionState::SessionStateIdle)
            .unwrap();
        assert_eq!(updated_config_map1, expected_updated_config_map);

        // Update the value from the params.
        let updated_params2 = FiraAppConfigParamsBuilder::from_params(&params)
            .unwrap()
            .key_rotation_rate(updated_key_rotation_rate)
            .build()
            .unwrap();
        let updated_config_map2 = updated_params2
            .generate_updated_config_map(&params, SessionState::SessionStateIdle)
            .unwrap();
        assert_eq!(updated_config_map2, expected_updated_config_map);
    }

    #[test]
    fn test_update_config() {
        let mut builder = FiraAppConfigParamsBuilder::new();
        builder
            .device_type(DeviceType::Controller)
            .multi_node_mode(MultiNodeMode::Unicast)
            .device_mac_address(UwbAddress::Short([1, 2]))
            .dst_mac_address(vec![UwbAddress::Short([3, 4])])
            .device_role(DeviceRole::Initiator)
            .vendor_id([0xFE, 0xDC])
            .static_sts_iv([0xDF, 0xCE, 0xAB, 0x12, 0x34, 0x56]);
        let params = builder.build().unwrap();

        builder.multi_node_mode(MultiNodeMode::OneToMany);
        let updated_params = builder.build().unwrap();
        // MultiNodeMode can be updated at idle state.
        assert!(updated_params
            .generate_updated_config_map(&params, SessionState::SessionStateIdle)
            .is_some());
        // MultiNodeMode cannot be updated at active state.
        assert!(updated_params
            .generate_updated_config_map(&params, SessionState::SessionStateActive)
            .is_none());
    }

    #[test]
    fn test_redacted_pii_fields() {
        let mut builder = FiraAppConfigParamsBuilder::new();
        builder
            .device_type(DeviceType::Controller)
            .multi_node_mode(MultiNodeMode::Unicast)
            .device_mac_address(UwbAddress::Short([1, 2]))
            .dst_mac_address(vec![UwbAddress::Short([3, 4])])
            .device_role(DeviceRole::Initiator)
            .vendor_id([0xFE, 0xDC])
            .static_sts_iv([0xDF, 0xCE, 0xAB, 0x12, 0x34, 0x56]);
        let params = builder.build().unwrap();

        let format_str = format!("{params:?}");
        assert!(format_str.contains("vendor_id: \"redacted\""));
        assert!(format_str.contains("static_sts_iv: \"redacted\""));
    }
}
