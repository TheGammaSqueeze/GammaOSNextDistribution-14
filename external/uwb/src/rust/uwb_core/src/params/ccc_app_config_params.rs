// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, item 2.0 (the "License");
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

#![allow(missing_docs)]

use std::collections::HashMap;

use log::error;

use crate::params::app_config_params::{AppConfigParams, AppConfigTlvMap};
use crate::params::fira_app_config_params::{
    DeviceRole, DeviceType, KeyRotation, MultiNodeMode, RangeDataNtfConfig, StsConfig,
};
use crate::params::uci_packets::{AppConfigTlvType, SessionState};
use crate::params::utils::{u16_to_bytes, u32_to_bytes, u8_to_bytes, validate};
use crate::utils::{builder_field, getter_field};
use num_derive::{FromPrimitive, ToPrimitive};

const CHAP_IN_RSTU: u16 = 400; // 1 Chap = 400 RSTU.
pub(super) const MINIMUM_BLOCK_DURATION_MS: u32 = 96;

// The constant AppConfigTlv values for CCC.
const CCC_DEVICE_TYPE: DeviceType = DeviceType::Controlee;
const CCC_STS_CONFIG: StsConfig = StsConfig::Dynamic;
const CCC_MULTI_NODE_MODE: MultiNodeMode = MultiNodeMode::OneToMany;
const CCC_RANGE_DATA_NTF_CONFIG: RangeDataNtfConfig = RangeDataNtfConfig::Disable;
const CCC_DEVICE_ROLE: DeviceRole = DeviceRole::Initiator;
const CCC_KEY_ROTATION: KeyRotation = KeyRotation::Enable;
const CCC_URSK_TTL: u16 = 0x2D0;

const DEFAULT_PROTOCOL_VERSION: CccProtocolVersion = CccProtocolVersion { major: 1, minor: 0 };

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CccAppConfigParams {
    protocol_version: CccProtocolVersion,
    uwb_config: CccUwbConfig,
    pulse_shape_combo: CccPulseShapeCombo,
    ran_multiplier: u32,
    channel_number: CccUwbChannel,
    chaps_per_slot: ChapsPerSlot,
    num_responder_nodes: u8,
    slots_per_rr: u8,
    sync_code_index: u8,
    hopping_mode: CccHoppingMode,
}

#[allow(missing_docs)]
impl CccAppConfigParams {
    // Generate the getter methods for all the fields.
    getter_field!(protocol_version, CccProtocolVersion);
    getter_field!(uwb_config, CccUwbConfig);
    getter_field!(pulse_shape_combo, CccPulseShapeCombo);
    getter_field!(ran_multiplier, u32);
    getter_field!(channel_number, CccUwbChannel);
    getter_field!(chaps_per_slot, ChapsPerSlot);
    getter_field!(num_responder_nodes, u8);
    getter_field!(slots_per_rr, u8);
    getter_field!(sync_code_index, u8);
    getter_field!(hopping_mode, CccHoppingMode);

    pub fn is_config_updatable(config_map: &AppConfigTlvMap, session_state: SessionState) -> bool {
        match session_state {
            SessionState::SessionStateIdle => {
                // Only ran_multiplier can be updated at idle state.
                config_map.keys().all(|key| key == &AppConfigTlvType::RangingDuration)
            }
            _ => false,
        }
    }

    pub fn generate_config_map(&self) -> AppConfigTlvMap {
        debug_assert!(self.is_valid().is_some());

        HashMap::from([
            (AppConfigTlvType::DeviceType, u8_to_bytes(CCC_DEVICE_TYPE as u8)),
            (AppConfigTlvType::StsConfig, u8_to_bytes(CCC_STS_CONFIG as u8)),
            (AppConfigTlvType::MultiNodeMode, u8_to_bytes(CCC_MULTI_NODE_MODE as u8)),
            (AppConfigTlvType::ChannelNumber, u8_to_bytes(self.channel_number as u8)),
            (AppConfigTlvType::NoOfControlee, u8_to_bytes(self.num_responder_nodes)),
            (
                AppConfigTlvType::SlotDuration,
                u16_to_bytes((self.chaps_per_slot as u16) * CHAP_IN_RSTU),
            ),
            (
                AppConfigTlvType::RangingDuration,
                u32_to_bytes(self.ran_multiplier * MINIMUM_BLOCK_DURATION_MS),
            ),
            (AppConfigTlvType::RngDataNtf, u8_to_bytes(CCC_RANGE_DATA_NTF_CONFIG as u8)),
            (AppConfigTlvType::DeviceRole, u8_to_bytes(CCC_DEVICE_ROLE as u8)),
            (AppConfigTlvType::PreambleCodeIndex, u8_to_bytes(self.sync_code_index)),
            (AppConfigTlvType::SlotsPerRr, u8_to_bytes(self.slots_per_rr)),
            (AppConfigTlvType::KeyRotation, u8_to_bytes(CCC_KEY_ROTATION as u8)),
            (AppConfigTlvType::HoppingMode, u8_to_bytes(self.hopping_mode as u8)),
            (AppConfigTlvType::CccRangingProtocolVer, self.protocol_version.clone().into()),
            (AppConfigTlvType::CccUwbConfigId, u16_to_bytes(self.uwb_config as u16)),
            (AppConfigTlvType::CccPulseshapeCombo, self.pulse_shape_combo.clone().into()),
            (AppConfigTlvType::CccUrskTtl, u16_to_bytes(CCC_URSK_TTL)),
        ])
    }

    fn is_valid(&self) -> Option<()> {
        validate(
            (1..=32).contains(&self.sync_code_index),
            "sync_code_index should be between 1 to 32",
        )?;

        self.ran_multiplier.checked_mul(MINIMUM_BLOCK_DURATION_MS).or_else(|| {
            error!("ran_multiplier * MINIMUM_BLOCK_DURATION_MS overflows");
            None
        })?;

        Some(())
    }
}

pub struct CccAppConfigParamsBuilder {
    protocol_version: CccProtocolVersion,
    uwb_config: Option<CccUwbConfig>,
    pulse_shape_combo: Option<CccPulseShapeCombo>,
    ran_multiplier: Option<u32>,
    channel_number: Option<CccUwbChannel>,
    chaps_per_slot: Option<ChapsPerSlot>,
    num_responder_nodes: Option<u8>,
    slots_per_rr: Option<u8>,
    sync_code_index: Option<u8>,
    hopping_mode: Option<CccHoppingMode>,
}

#[allow(clippy::new_without_default)]
impl CccAppConfigParamsBuilder {
    pub fn new() -> Self {
        Self {
            protocol_version: DEFAULT_PROTOCOL_VERSION,
            uwb_config: None,
            pulse_shape_combo: None,
            ran_multiplier: None,
            channel_number: None,
            chaps_per_slot: None,
            num_responder_nodes: None,
            slots_per_rr: None,
            sync_code_index: None,
            hopping_mode: None,
        }
    }

    pub fn build(&self) -> Option<AppConfigParams> {
        let params = CccAppConfigParams {
            protocol_version: self.protocol_version.clone(),
            uwb_config: self.uwb_config?,
            pulse_shape_combo: self.pulse_shape_combo.clone()?,
            ran_multiplier: self.ran_multiplier?,
            channel_number: self.channel_number?,
            chaps_per_slot: self.chaps_per_slot?,
            num_responder_nodes: self.num_responder_nodes?,
            slots_per_rr: self.slots_per_rr?,
            sync_code_index: self.sync_code_index?,
            hopping_mode: self.hopping_mode?,
        };
        params.is_valid()?;
        Some(AppConfigParams::Ccc(params))
    }

    pub fn from_params(params: &AppConfigParams) -> Option<Self> {
        match params {
            AppConfigParams::Ccc(params) => Some(Self {
                protocol_version: params.protocol_version.clone(),
                uwb_config: Some(params.uwb_config),
                pulse_shape_combo: Some(params.pulse_shape_combo.clone()),
                ran_multiplier: Some(params.ran_multiplier),
                channel_number: Some(params.channel_number),
                chaps_per_slot: Some(params.chaps_per_slot),
                num_responder_nodes: Some(params.num_responder_nodes),
                slots_per_rr: Some(params.slots_per_rr),
                sync_code_index: Some(params.sync_code_index),
                hopping_mode: Some(params.hopping_mode),
            }),
            _ => None,
        }
    }

    // Generate the setter methods for all the fields.
    builder_field!(protocol_version, CccProtocolVersion);
    builder_field!(uwb_config, CccUwbConfig, Some);
    builder_field!(pulse_shape_combo, CccPulseShapeCombo, Some);
    builder_field!(ran_multiplier, u32, Some);
    builder_field!(channel_number, CccUwbChannel, Some);
    builder_field!(chaps_per_slot, ChapsPerSlot, Some);
    builder_field!(num_responder_nodes, u8, Some);
    builder_field!(slots_per_rr, u8, Some);
    builder_field!(sync_code_index, u8, Some);
    builder_field!(hopping_mode, CccHoppingMode, Some);
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CccProtocolVersion {
    pub major: u8,
    pub minor: u8,
}

impl From<CccProtocolVersion> for Vec<u8> {
    fn from(item: CccProtocolVersion) -> Self {
        vec![item.major, item.minor]
    }
}

#[repr(u16)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum CccUwbConfig {
    Config0 = 0,
    Config1 = 1,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CccPulseShapeCombo {
    pub initiator_tx: PulseShape,
    pub responder_tx: PulseShape,
}

impl From<CccPulseShapeCombo> for Vec<u8> {
    fn from(item: CccPulseShapeCombo) -> Self {
        vec![((item.initiator_tx as u8) << 4) | (item.responder_tx as u8)]
    }
}

#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum PulseShape {
    SymmetricalRootRaisedCosine = 0x0,
    PrecursorFree = 0x1,
    PrecursorFreeSpecial = 0x2,
}

#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum CccUwbChannel {
    Channel5 = 5,
    Channel9 = 9,
}

#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum HoppingConfigMode {
    None = 0,
    Continuous = 1,
    Adaptive = 2,
}

#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum HoppingSequence {
    Default = 0,
    Aes = 1,
}

#[repr(u16)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum ChapsPerSlot {
    Value3 = 3,
    Value4 = 4,
    Value6 = 6,
    Value8 = 8,
    Value9 = 9,
    Value12 = 12,
    Value24 = 24,
}

#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, FromPrimitive, ToPrimitive)]
pub enum CccHoppingMode {
    Disable = 0,
    AdaptiveDefault = 2,
    ContinuousDefault = 3,
    AdaptiveAes = 4,
    ContinuousAes = 5,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ok() {
        let protocol_version = CccProtocolVersion { major: 2, minor: 1 };
        let uwb_config = CccUwbConfig::Config0;
        let pulse_shape_combo = CccPulseShapeCombo {
            initiator_tx: PulseShape::PrecursorFree,
            responder_tx: PulseShape::PrecursorFreeSpecial,
        };
        let ran_multiplier = 3;
        let channel_number = CccUwbChannel::Channel9;
        let chaps_per_slot = ChapsPerSlot::Value9;
        let num_responder_nodes = 1;
        let slots_per_rr = 3;
        let sync_code_index = 12;
        let hopping_mode = CccHoppingMode::ContinuousAes;

        let params = CccAppConfigParamsBuilder::new()
            .protocol_version(protocol_version)
            .uwb_config(uwb_config)
            .pulse_shape_combo(pulse_shape_combo.clone())
            .ran_multiplier(ran_multiplier)
            .channel_number(channel_number)
            .chaps_per_slot(chaps_per_slot)
            .num_responder_nodes(num_responder_nodes)
            .slots_per_rr(slots_per_rr)
            .sync_code_index(sync_code_index)
            .hopping_mode(hopping_mode)
            .build()
            .unwrap();

        // Verify the generated TLV.
        let config_map = params.generate_config_map();
        let expected_config_map = HashMap::from([
            (AppConfigTlvType::DeviceType, u8_to_bytes(CCC_DEVICE_TYPE as u8)),
            (AppConfigTlvType::StsConfig, u8_to_bytes(CCC_STS_CONFIG as u8)),
            (AppConfigTlvType::MultiNodeMode, u8_to_bytes(CCC_MULTI_NODE_MODE as u8)),
            (AppConfigTlvType::ChannelNumber, u8_to_bytes(channel_number as u8)),
            (AppConfigTlvType::NoOfControlee, u8_to_bytes(num_responder_nodes)),
            (AppConfigTlvType::SlotDuration, u16_to_bytes((chaps_per_slot as u16) * CHAP_IN_RSTU)),
            (
                AppConfigTlvType::RangingDuration,
                u32_to_bytes(ran_multiplier * MINIMUM_BLOCK_DURATION_MS),
            ),
            (AppConfigTlvType::RngDataNtf, u8_to_bytes(CCC_RANGE_DATA_NTF_CONFIG as u8)),
            (AppConfigTlvType::DeviceRole, u8_to_bytes(CCC_DEVICE_ROLE as u8)),
            (AppConfigTlvType::PreambleCodeIndex, u8_to_bytes(sync_code_index)),
            (AppConfigTlvType::SlotsPerRr, u8_to_bytes(slots_per_rr)),
            (AppConfigTlvType::KeyRotation, u8_to_bytes(CCC_KEY_ROTATION as u8)),
            (AppConfigTlvType::HoppingMode, u8_to_bytes(hopping_mode as u8)),
            (AppConfigTlvType::CccRangingProtocolVer, vec![2, 1]),
            (AppConfigTlvType::CccUwbConfigId, u16_to_bytes(uwb_config as u16)),
            (AppConfigTlvType::CccPulseshapeCombo, pulse_shape_combo.into()),
            (AppConfigTlvType::CccUrskTtl, u16_to_bytes(CCC_URSK_TTL)),
        ]);
        assert_eq!(config_map, expected_config_map);

        // Update the value from the params.
        let updated_ran_multiplier = 5;
        assert_ne!(ran_multiplier, updated_ran_multiplier);
        let expected_updated_config_map = HashMap::from([(
            AppConfigTlvType::RangingDuration,
            u32_to_bytes(updated_ran_multiplier * MINIMUM_BLOCK_DURATION_MS),
        )]);

        let updated_params1 = CccAppConfigParamsBuilder::from_params(&params)
            .unwrap()
            .ran_multiplier(updated_ran_multiplier)
            .build()
            .unwrap();
        let updated_config_map1 = updated_params1
            .generate_updated_config_map(&params, SessionState::SessionStateIdle)
            .unwrap();
        assert_eq!(updated_config_map1, expected_updated_config_map);
    }

    #[test]
    fn test_update_config() {
        let mut builder = CccAppConfigParamsBuilder::new();
        builder
            .protocol_version(CccProtocolVersion { major: 2, minor: 1 })
            .uwb_config(CccUwbConfig::Config0)
            .pulse_shape_combo(CccPulseShapeCombo {
                initiator_tx: PulseShape::PrecursorFree,
                responder_tx: PulseShape::PrecursorFreeSpecial,
            })
            .ran_multiplier(3)
            .channel_number(CccUwbChannel::Channel9)
            .chaps_per_slot(ChapsPerSlot::Value9)
            .num_responder_nodes(1)
            .slots_per_rr(3)
            .sync_code_index(12)
            .hopping_mode(CccHoppingMode::ContinuousAes);
        let params = builder.build().unwrap();

        builder.ran_multiplier(5);
        let updated_params = builder.build().unwrap();
        // ran_multiplier can be updated at idle state.
        assert!(updated_params
            .generate_updated_config_map(&params, SessionState::SessionStateIdle)
            .is_some());
        // ran_multiplier cannot be updated at active state.
        assert!(updated_params
            .generate_updated_config_map(&params, SessionState::SessionStateActive)
            .is_none());
    }
}
