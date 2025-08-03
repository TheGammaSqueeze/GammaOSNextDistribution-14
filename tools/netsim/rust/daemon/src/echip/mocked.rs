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

use crate::devices::chip::ChipIdentifier;
use crate::echip::{EmulatedChip, SharedEmulatedChip};

use netsim_proto::common::ChipKind as ProtoChipKind;
use netsim_proto::model::Chip as ProtoChip;
use netsim_proto::stats::{netsim_radio_stats, NetsimRadioStats as ProtoRadioStats};
use protobuf::EnumOrUnknown;

use std::sync::{Arc, Mutex};

/// Parameters for creating Mocked chips
pub struct CreateParams {
    pub chip_kind: ProtoChipKind,
}

/// Mock struct is remained empty.
pub struct Mock {
    chip_kind: ProtoChipKind,
}

impl EmulatedChip for Mock {
    fn handle_request(&self, _packet: &[u8]) {}

    fn reset(&mut self) {}

    fn get(&self) -> ProtoChip {
        let mut proto_chip = ProtoChip::new();
        proto_chip.kind = EnumOrUnknown::new(self.chip_kind);
        proto_chip
    }

    fn patch(&mut self, _chip: &ProtoChip) {}

    fn remove(&mut self) {}

    fn get_stats(&self, _duration_secs: u64) -> Vec<ProtoRadioStats> {
        let mut stats = ProtoRadioStats::new();
        stats.kind = Some(EnumOrUnknown::new(match self.chip_kind {
            ProtoChipKind::UNSPECIFIED => netsim_radio_stats::Kind::UNSPECIFIED,
            ProtoChipKind::BLUETOOTH => netsim_radio_stats::Kind::BLUETOOTH_LOW_ENERGY,
            ProtoChipKind::WIFI => netsim_radio_stats::Kind::WIFI,
            ProtoChipKind::UWB => netsim_radio_stats::Kind::UWB,
            ProtoChipKind::BLUETOOTH_BEACON => netsim_radio_stats::Kind::BLE_BEACON,
        }));
        vec![stats]
    }
}

/// Create a new MockedChip
pub fn new(create_params: &CreateParams, _chip_id: ChipIdentifier) -> SharedEmulatedChip {
    SharedEmulatedChip(Arc::new(Mutex::new(Box::new(Mock { chip_kind: create_params.chip_kind }))))
}
