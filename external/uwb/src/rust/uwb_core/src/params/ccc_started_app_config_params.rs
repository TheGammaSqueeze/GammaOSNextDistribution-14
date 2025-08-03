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

use crate::params::app_config_params::AppConfigTlvMap;
use crate::params::ccc_app_config_params::MINIMUM_BLOCK_DURATION_MS;
use crate::params::uci_packets::AppConfigTlvType;
use crate::params::utils::{bytes_to_u32, bytes_to_u64, bytes_to_u8};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CccStartedAppConfigParams {
    pub sts_index: u32,
    pub hop_mode_key: u32,
    pub uwb_time0: u64,
    pub ran_multiplier: u32,
    pub sync_code_index: u8,
}

impl CccStartedAppConfigParams {
    pub fn from_config_map(mut config_map: AppConfigTlvMap) -> Option<Self> {
        Some(Self {
            sts_index: bytes_to_u32(config_map.remove(&AppConfigTlvType::StsIndex)?)?,
            hop_mode_key: bytes_to_u32(config_map.remove(&AppConfigTlvType::CccHopModeKey)?)?,
            uwb_time0: bytes_to_u64(config_map.remove(&AppConfigTlvType::CccUwbTime0)?)?,
            ran_multiplier: bytes_to_u32(config_map.remove(&AppConfigTlvType::RangingDuration)?)?
                / MINIMUM_BLOCK_DURATION_MS,
            sync_code_index: bytes_to_u8(config_map.remove(&AppConfigTlvType::PreambleCodeIndex)?)?,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    use std::collections::HashMap;

    use crate::params::utils::{u32_to_bytes, u64_to_bytes, u8_to_bytes};

    #[test]
    fn test_from_config_map() {
        let sts_index = 3;
        let hop_mode_key = 5;
        let uwb_time0 = 7;
        let ran_multiplier = 4;
        let sync_code_index = 9;

        let config_map = HashMap::from([
            (AppConfigTlvType::StsIndex, u32_to_bytes(sts_index)),
            (AppConfigTlvType::CccHopModeKey, u32_to_bytes(hop_mode_key)),
            (AppConfigTlvType::CccUwbTime0, u64_to_bytes(uwb_time0)),
            (
                AppConfigTlvType::RangingDuration,
                u32_to_bytes(ran_multiplier * MINIMUM_BLOCK_DURATION_MS),
            ),
            (AppConfigTlvType::PreambleCodeIndex, u8_to_bytes(sync_code_index)),
        ]);
        let params = CccStartedAppConfigParams::from_config_map(config_map).unwrap();

        assert_eq!(params.sts_index, sts_index);
        assert_eq!(params.hop_mode_key, hop_mode_key);
        assert_eq!(params.uwb_time0, uwb_time0);
        assert_eq!(params.ran_multiplier, ran_multiplier);
        assert_eq!(params.sync_code_index, sync_code_index);
    }
}
