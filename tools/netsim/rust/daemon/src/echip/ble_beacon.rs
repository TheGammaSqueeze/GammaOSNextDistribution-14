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

use crate::bluetooth::{ble_beacon_add, ble_beacon_get, ble_beacon_patch, ble_beacon_remove};
use crate::devices::chip::{ChipIdentifier, FacadeIdentifier};
use crate::echip::{EmulatedChip, SharedEmulatedChip};

use log::{error, info};
use netsim_proto::model::Chip as ProtoChip;
use netsim_proto::model::ChipCreate as ChipCreateProto;
use netsim_proto::stats::{netsim_radio_stats, NetsimRadioStats as ProtoRadioStats};

use std::sync::{Arc, Mutex};

#[cfg(not(test))]
use crate::ffi::ffi_bluetooth;

/// Parameters for creating BleBeacon chips
pub struct CreateParams {
    pub device_name: String,
    pub chip_proto: ChipCreateProto,
}

/// BleBeacon struct will keep track of facade_id
pub struct BleBeacon {
    facade_id: FacadeIdentifier,
    chip_id: ChipIdentifier,
}

impl EmulatedChip for BleBeacon {
    fn handle_request(&self, packet: &[u8]) {
        #[cfg(not(test))]
        ffi_bluetooth::handle_bt_request(self.facade_id, packet[0], &packet[1..].to_vec());
        #[cfg(test)]
        log::info!("BleBeacon::handle_request({packet:?})");
    }

    fn reset(&mut self) {
        #[cfg(not(test))]
        ffi_bluetooth::bluetooth_reset(self.facade_id);
        #[cfg(test)]
        log::info!("BleBeacon::reset()");
    }

    fn get(&self) -> ProtoChip {
        let mut chip_proto = ProtoChip::new();
        match ble_beacon_get(self.chip_id, self.facade_id) {
            Ok(beacon_proto) => chip_proto.mut_ble_beacon().clone_from(&beacon_proto),
            Err(err) => error!("{err:?}"),
        }
        chip_proto
    }

    fn patch(&mut self, chip: &ProtoChip) {
        if let Err(err) = ble_beacon_patch(self.facade_id, self.chip_id, chip.ble_beacon()) {
            error!("{err:?}");
        }
    }

    fn remove(&mut self) {
        if let Err(err) = ble_beacon_remove(self.chip_id, self.facade_id) {
            error!("{err:?}");
        }
    }

    fn get_stats(&self, duration_secs: u64) -> Vec<ProtoRadioStats> {
        let mut stats_proto = ProtoRadioStats::new();
        stats_proto.set_duration_secs(duration_secs);
        stats_proto.set_kind(netsim_radio_stats::Kind::BLE_BEACON);
        let chip_proto = self.get();
        if chip_proto.has_ble_beacon() {
            stats_proto.set_tx_count(chip_proto.ble_beacon().bt.low_energy.tx_count);
            stats_proto.set_rx_count(chip_proto.ble_beacon().bt.low_energy.rx_count);
        }
        vec![stats_proto]
    }
}

/// Create a new Emulated BleBeacon Chip
pub fn new(params: &CreateParams, chip_id: ChipIdentifier) -> SharedEmulatedChip {
    match ble_beacon_add(params.device_name.clone(), chip_id, &params.chip_proto) {
        Ok(facade_id) => {
            info!("BleBeacon EmulatedChip created with facade_id: {facade_id} chip_id: {chip_id}");
            SharedEmulatedChip(Arc::new(Mutex::new(Box::new(BleBeacon { facade_id, chip_id }))))
        }
        Err(err) => {
            error!("{err:?}");
            SharedEmulatedChip(Arc::new(Mutex::new(Box::new(BleBeacon {
                facade_id: u32::MAX,
                chip_id: u32::MAX,
            }))))
        }
    }
}
