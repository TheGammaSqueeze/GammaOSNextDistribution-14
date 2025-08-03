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

use std::{
    collections::BTreeMap,
    sync::{Arc, Mutex, MutexGuard},
};

use lazy_static::lazy_static;

use netsim_proto::model::Chip as ProtoChip;
use netsim_proto::stats::NetsimRadioStats as ProtoRadioStats;

use crate::{
    devices::chip::ChipIdentifier,
    echip::{ble_beacon, mocked},
};

#[derive(Clone)]
pub struct SharedEmulatedChip(pub Arc<Mutex<Box<dyn EmulatedChip + Send + Sync>>>);

#[cfg(not(test))]
use crate::echip::{bluetooth, uwb, wifi};

// ECHIPS is a singleton that contains a hash map from
// ChipIdentifier to SharedEmulatedChip
lazy_static! {
    static ref ECHIPS: Arc<Mutex<BTreeMap<ChipIdentifier, SharedEmulatedChip>>> =
        Arc::new(Mutex::new(BTreeMap::new()));
}

impl SharedEmulatedChip {
    pub fn lock(&self) -> MutexGuard<Box<dyn EmulatedChip + Send + Sync>> {
        self.0.lock().expect("Poisoned Shared Emulated Chip lock")
    }
}

/// Parameter for each constructor of Emulated Chips
#[allow(clippy::large_enum_variant, dead_code)]
pub enum CreateParam {
    BleBeacon(ble_beacon::CreateParams),
    #[cfg(not(test))]
    Bluetooth(bluetooth::CreateParams),
    #[cfg(not(test))]
    Wifi(wifi::CreateParams),
    #[cfg(not(test))]
    Uwb(uwb::CreateParams),
    Mock(mocked::CreateParams),
}

// TODO: Factory trait to include start, stop, and add
/// EmulatedChip is a trait that provides interface between the generic Chip
/// and Radio specific library (rootcanal, libslirp, pica).
pub trait EmulatedChip {
    /// This is the main entry for incoming host-to-controller packets
    /// from virtual devices called by the transport module. The format of the
    /// packet depends on the emulated chip kind:
    /// * Bluetooth - packet is H4 HCI format
    /// * Wi-Fi - packet is Radiotap format
    /// * UWB - packet is UCI format
    /// * NFC - packet is NCI format
    fn handle_request(&self, packet: &[u8]);

    /// Reset the internal state of the emulated chip for the virtual device.
    /// The transmitted and received packet count will be set to 0 and the chip
    /// shall be in the enabled state following a call to this function.
    fn reset(&mut self);

    /// Return the Chip model protobuf from the emulated chip. This is part of
    /// the Frontend API.
    fn get(&self) -> ProtoChip;

    /// Patch the state of the emulated chip. For example enable/disable the
    /// chip's host-to-controller packet processing. This is part of the
    /// Frontend API
    fn patch(&mut self, chip: &ProtoChip);

    /// Remove the emulated chip from the emulated chip library. No further calls will
    /// be made on this emulated chip. This is called when the packet stream from
    /// the virtual device closes.
    fn remove(&mut self);

    /// Return the NetsimRadioStats protobuf from the emulated chip. This is
    /// part of NetsimStats protobuf.
    fn get_stats(&self, duration_secs: u64) -> Vec<ProtoRadioStats>;
}

/// Lookup for SharedEmulatedChip with chip_id
/// Returns None if chip_id is non-existent key.
pub fn get(chip_id: ChipIdentifier) -> Option<SharedEmulatedChip> {
    ECHIPS.lock().expect("Failed to acquire lock on ECHIPS").get(&chip_id).cloned()
}

/// Remove and return SharedEmulatedchip from ECHIPS.
/// Returns None if chip_id is non-existent key.
pub fn remove(chip_id: ChipIdentifier) -> Option<SharedEmulatedChip> {
    let echip = ECHIPS.lock().expect("Failed to acquire lock on ECHIPS").remove(&chip_id);
    echip.clone()?.lock().remove();
    echip
}

/// This is called when the transport module receives a new packet stream
/// connection from a virtual device.
pub fn new(create_param: &CreateParam, chip_id: ChipIdentifier) -> SharedEmulatedChip {
    // Based on create_param, construct SharedEmulatedChip.
    let shared_echip = match create_param {
        CreateParam::BleBeacon(params) => ble_beacon::new(params, chip_id),
        #[cfg(not(test))]
        CreateParam::Bluetooth(params) => bluetooth::new(params, chip_id),
        #[cfg(not(test))]
        CreateParam::Wifi(params) => wifi::new(params, chip_id),
        #[cfg(not(test))]
        CreateParam::Uwb(params) => uwb::new(params, chip_id),
        CreateParam::Mock(params) => mocked::new(params, chip_id),
    };

    // Insert into ECHIPS Map
    ECHIPS.lock().expect("Failed to acquire lock on ECHIPS").insert(chip_id, shared_echip.clone());
    shared_echip
}

// TODO(b/309529194):
// 1. Create Mock echip, patch and get
// 2. Create Mock echip, patch and reset
#[cfg(test)]
mod tests {

    use super::*;
    use netsim_proto::common::ChipKind as ProtoChipKind;

    #[test]
    fn test_echip_new() {
        let mock_param =
            CreateParam::Mock(mocked::CreateParams { chip_kind: ProtoChipKind::UNSPECIFIED });
        let mock_chip_id = 0;
        let echip = new(&mock_param, mock_chip_id);
        assert_eq!(echip.lock().get(), ProtoChip::new());
    }
}
