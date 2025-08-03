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
use crate::echip::{packet::hwsim_cmd_response, EmulatedChip, SharedEmulatedChip};
use crate::ffi::ffi_wifi;
use crate::wifi::medium::Medium;
use lazy_static::lazy_static;
use log::info;
use netsim_proto::config::WiFi as WiFiConfig;
use netsim_proto::model::chip::Radio;
use netsim_proto::model::Chip as ProtoChip;
use netsim_proto::stats::{netsim_radio_stats, NetsimRadioStats as ProtoRadioStats};
use protobuf::{Message, MessageField};

use std::sync::{Arc, Mutex};

/// Parameters for creating Wifi chips
/// allow(dead_code) due to not being used in unit tests
#[allow(dead_code)]
pub struct CreateParams {}

/// Wifi struct will keep track of chip_id
pub struct Wifi {
    chip_id: ChipIdentifier,
}

// Allocator for chip identifiers.
lazy_static! {
    static ref MEDIUM: Mutex<Medium> = Mutex::new(Medium::new(hwsim_cmd_response));
}

impl EmulatedChip for Wifi {
    fn handle_request(&self, packet: &[u8]) {
        if !MEDIUM.lock().expect("Lock failed").process(self.chip_id, packet) {
            ffi_wifi::handle_wifi_request(self.chip_id, &packet.to_vec());
        }
    }

    fn reset(&mut self) {
        ffi_wifi::wifi_reset(self.chip_id);
    }

    fn get(&self) -> ProtoChip {
        let radio_bytes = ffi_wifi::wifi_get_cxx(self.chip_id);
        let wifi_proto = Radio::parse_from_bytes(&radio_bytes).unwrap();
        let mut chip_proto = ProtoChip::new();
        chip_proto.mut_wifi().clone_from(&wifi_proto);
        chip_proto
    }

    fn patch(&mut self, chip: &ProtoChip) {
        let radio_bytes = chip.wifi().write_to_bytes().unwrap();
        ffi_wifi::wifi_patch_cxx(self.chip_id, &radio_bytes);
    }

    fn remove(&mut self) {
        ffi_wifi::wifi_remove(self.chip_id);
    }

    fn get_stats(&self, duration_secs: u64) -> Vec<ProtoRadioStats> {
        let mut stats_proto = ProtoRadioStats::new();
        stats_proto.set_duration_secs(duration_secs);
        stats_proto.set_kind(netsim_radio_stats::Kind::WIFI);
        let chip_proto = self.get();
        if chip_proto.has_wifi() {
            stats_proto.set_tx_count(chip_proto.wifi().tx_count);
            stats_proto.set_rx_count(chip_proto.wifi().rx_count);
        }
        vec![stats_proto]
    }
}

/// Create a new Emulated Wifi Chip
/// allow(dead_code) due to not being used in unit tests
#[allow(dead_code)]
pub fn new(_params: &CreateParams, chip_id: ChipIdentifier) -> SharedEmulatedChip {
    ffi_wifi::wifi_add(chip_id);
    info!("WiFi EmulatedChip created chip_id: {chip_id}");
    let echip = Wifi { chip_id };
    SharedEmulatedChip(Arc::new(Mutex::new(Box::new(echip))))
}

/// Starts the WiFi service.
pub fn wifi_start(config: &MessageField<WiFiConfig>) {
    let proto_bytes = config.as_ref().unwrap_or_default().write_to_bytes().unwrap();
    ffi_wifi::wifi_start(&proto_bytes);
}

/// Stops the WiFi service.
pub fn wifi_stop() {
    ffi_wifi::wifi_stop();
}
