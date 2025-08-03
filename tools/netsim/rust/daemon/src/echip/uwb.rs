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

use netsim_proto::model::chip::Radio;
use netsim_proto::model::Chip as ProtoChip;
use netsim_proto::stats::{netsim_radio_stats, NetsimRadioStats as ProtoRadioStats};

use std::sync::{Arc, Mutex};

use crate::devices::chip::ChipIdentifier;

use super::{EmulatedChip, SharedEmulatedChip};

#[cfg(feature = "cuttlefish")]
#[allow(unused_imports)]
use pica::Pica;

type PicaIdentifier = u32;

/// Parameters for creating UWB chips
pub struct CreateParams {
    pub address: String,
}

/// UWB struct will keep track of pica_id
pub struct Uwb {
    pica_id: PicaIdentifier,
}

impl EmulatedChip for Uwb {
    fn handle_request(&self, packet: &[u8]) {
        log::info!("{packet:?}");
    }

    fn reset(&mut self) {
        // TODO(b/321790942): Reset chip state in pica
        log::info!("Reset Uwb Chip for pica_id: {}", self.pica_id)
    }

    fn get(&self) -> ProtoChip {
        let mut chip_proto = ProtoChip::new();
        // TODO(b/321790942): Get the chip state from pica
        let uwb_proto = Radio::new();
        chip_proto.mut_uwb().clone_from(&uwb_proto);
        chip_proto
    }

    fn patch(&mut self, chip: &ProtoChip) {
        // TODO(b/321790942): Patch the chip state through pica
        log::info!("Patch Uwb Chip for pica_id: {}", self.pica_id);
    }

    fn remove(&mut self) {
        // TODO(b/321790942): Remove the chip information from pica
        log::info!("Remove Uwb Chip for pica_id: {}", self.pica_id);
    }

    fn get_stats(&self, duration_secs: u64) -> Vec<ProtoRadioStats> {
        let mut stats_proto = ProtoRadioStats::new();
        stats_proto.set_duration_secs(duration_secs);
        stats_proto.set_kind(netsim_radio_stats::Kind::UWB);
        vec![stats_proto]
    }
}

pub fn new(create_params: &CreateParams, chip_id: ChipIdentifier) -> SharedEmulatedChip {
    // TODO(b/321790942): Add the device into pica and obtain the pica identifier
    let echip = Uwb { pica_id: chip_id };
    SharedEmulatedChip(Arc::new(Mutex::new(Box::new(echip))))
}

#[cfg(test)]
mod tests {

    use super::*;

    fn new_uwb_shared_echip() -> SharedEmulatedChip {
        new(&CreateParams { address: "test".to_string() }, 0)
    }

    #[test]
    fn test_uwb_get() {
        let shared_echip = new_uwb_shared_echip();
        assert!(shared_echip.lock().get().has_uwb());
    }

    #[ignore = "TODO: Check if reset properly sets the state to true"]
    #[test]
    fn test_uwb_reset() {
        todo!()
    }

    #[test]
    fn test_get_stats() {
        let shared_echip = new_uwb_shared_echip();
        let radio_stat_vec = shared_echip.lock().get_stats(0);
        let radio_stat = radio_stat_vec.first().unwrap();
        assert_eq!(radio_stat.kind(), netsim_radio_stats::Kind::UWB);
        assert_eq!(radio_stat.duration_secs(), 0);
    }
}
