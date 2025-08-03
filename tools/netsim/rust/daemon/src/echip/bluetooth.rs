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
use crate::ffi::ffi_bluetooth;

use cxx::{let_cxx_string, CxxString, CxxVector};
use lazy_static::lazy_static;
use log::{error, info};
use netsim_proto::config::Bluetooth as BluetoothConfig;
use netsim_proto::configuration::Controller as RootcanalController;
use netsim_proto::model::chip::Bluetooth as ProtoBluetooth;
use netsim_proto::model::chip::Radio as ProtoRadio;
use netsim_proto::model::Chip as ProtoChip;
use netsim_proto::model::State as ProtoState;
use netsim_proto::stats::invalid_packet::Reason as InvalidPacketReason;
use netsim_proto::stats::{netsim_radio_stats, InvalidPacket, NetsimRadioStats as ProtoRadioStats};

use protobuf::{Enum, EnumOrUnknown, Message, MessageField};

use std::collections::BTreeMap;
use std::sync::{Arc, Mutex};

static ECHIP_BT_MUTEX: Mutex<()> = Mutex::new(());

pub type RootcanalIdentifier = u32;

// BLUETOOTH_INVALID_PACKETS is a singleton that contains a hash map from
// RootcanalIdentifier to Vec<InvalidPacket>
// This singleton is only used when Rootcanal reports invalid
// packets by rootcanal_id and we add those to Bluetooth struct.
lazy_static! {
    static ref BLUETOOTH_INVALID_PACKETS: Arc<Mutex<BTreeMap<RootcanalIdentifier, Vec<InvalidPacket>>>> =
        Arc::new(Mutex::new(BTreeMap::new()));
}

/// Parameters for creating Bluetooth chips
/// allow(dead_code) due to not being used in unit tests
#[allow(dead_code)]
pub struct CreateParams {
    pub address: String,
    pub bt_properties: Option<MessageField<RootcanalController>>,
}

/// Bluetooth struct will keep track of rootcanal_id
pub struct Bluetooth {
    rootcanal_id: RootcanalIdentifier,
    low_energy_enabled: ProtoState,
    classic_enabled: ProtoState,
}

fn patch_state(
    enabled: &mut ProtoState,
    state: EnumOrUnknown<ProtoState>,
    id: RootcanalIdentifier,
    is_low_energy: bool,
) {
    match (state.enum_value().ok(), &enabled) {
        (Some(ProtoState::ON), ProtoState::OFF) => {
            let _unused = ECHIP_BT_MUTEX.lock().expect("Failed to lock ECHIP_BT_MUTEX");
            *enabled = ProtoState::ON;
            ffi_bluetooth::add_device_to_phy(id, is_low_energy);
        }
        (Some(ProtoState::OFF), ProtoState::ON) => {
            let _unused = ECHIP_BT_MUTEX.lock().expect("Failed to lock ECHIP_BT_MUTEX");
            *enabled = ProtoState::OFF;
            ffi_bluetooth::remove_device_from_phy(id, is_low_energy)
        }
        _ => {}
    }
}

impl EmulatedChip for Bluetooth {
    fn handle_request(&self, packet: &[u8]) {
        // Lock to protect device_to_transport_ table in C++
        let _unused = ECHIP_BT_MUTEX.lock().expect("Failed to acquire lock on ECHIP_BT_MUTEX");
        ffi_bluetooth::handle_bt_request(self.rootcanal_id, packet[0], &packet[1..].to_vec())
    }

    fn reset(&mut self) {
        ffi_bluetooth::bluetooth_reset(self.rootcanal_id);
        self.low_energy_enabled = ProtoState::ON;
        self.classic_enabled = ProtoState::ON;
    }

    fn get(&self) -> ProtoChip {
        let bluetooth_bytes = ffi_bluetooth::bluetooth_get_cxx(self.rootcanal_id);
        let bt_proto = ProtoBluetooth::parse_from_bytes(&bluetooth_bytes).unwrap();
        let mut chip_proto = ProtoChip::new();
        chip_proto.set_bt(ProtoBluetooth {
            low_energy: Some(ProtoRadio {
                state: EnumOrUnknown::new(self.low_energy_enabled),
                tx_count: bt_proto.low_energy.tx_count,
                rx_count: bt_proto.low_energy.rx_count,
                ..Default::default()
            })
            .into(),
            classic: Some(ProtoRadio {
                state: EnumOrUnknown::new(self.classic_enabled),
                tx_count: bt_proto.classic.tx_count,
                rx_count: bt_proto.classic.rx_count,
                ..Default::default()
            })
            .into(),
            address: bt_proto.address,
            bt_properties: bt_proto.bt_properties,
            ..Default::default()
        });
        chip_proto
    }

    fn patch(&mut self, chip: &ProtoChip) {
        if !chip.has_bt() {
            return;
        }
        let id = self.rootcanal_id;
        patch_state(&mut self.low_energy_enabled, chip.bt().low_energy.state, id, true);
        patch_state(&mut self.classic_enabled, chip.bt().classic.state, id, false);
    }

    fn remove(&mut self) {
        // Lock to protect id_to_chip_info_ table in C++
        let _unused = ECHIP_BT_MUTEX.lock().expect("Failed to acquire lock on ECHIP_BT_MUTEX");
        ffi_bluetooth::bluetooth_remove(self.rootcanal_id);
        BLUETOOTH_INVALID_PACKETS.lock().expect("invalid packets").remove(&self.rootcanal_id);
    }

    fn get_stats(&self, duration_secs: u64) -> Vec<ProtoRadioStats> {
        // Construct NetsimRadioStats for BLE and Classic.
        let mut ble_stats_proto = ProtoRadioStats::new();
        ble_stats_proto.set_duration_secs(duration_secs);
        if let Some(v) = BLUETOOTH_INVALID_PACKETS
            .lock()
            .expect("Failed to acquire lock on BLUETOOTH_INVALID_PACKETS")
            .get(&self.rootcanal_id)
        {
            for invalid_packet in v {
                ble_stats_proto.invalid_packets.push(invalid_packet.clone());
            }
        }
        let mut classic_stats_proto = ble_stats_proto.clone();

        // Obtain the Chip Information with get()
        let chip_proto = self.get();
        if chip_proto.has_bt() {
            // Setting values for BLE Radio Stats
            ble_stats_proto.set_kind(netsim_radio_stats::Kind::BLUETOOTH_LOW_ENERGY);
            ble_stats_proto.set_tx_count(chip_proto.bt().low_energy.tx_count);
            ble_stats_proto.set_rx_count(chip_proto.bt().low_energy.rx_count);
            // Setting values for Classic Radio Stats
            classic_stats_proto.set_kind(netsim_radio_stats::Kind::BLUETOOTH_CLASSIC);
            classic_stats_proto.set_tx_count(chip_proto.bt().classic.tx_count);
            classic_stats_proto.set_rx_count(chip_proto.bt().classic.rx_count);
        }
        vec![ble_stats_proto, classic_stats_proto]
    }
}

/// Create a new Emulated Bluetooth Chip
/// allow(dead_code) due to not being used in unit tests
#[allow(dead_code)]
pub fn new(create_params: &CreateParams, chip_id: ChipIdentifier) -> SharedEmulatedChip {
    // Lock to protect id_to_chip_info_ table in C++
    let _unused = ECHIP_BT_MUTEX.lock().expect("Failed to acquire lock on ECHIP_BT_MUTEX");
    let_cxx_string!(cxx_address = create_params.address.clone());
    let proto_bytes = match &create_params.bt_properties {
        Some(properties) => properties.write_to_bytes().unwrap(),
        None => Vec::new(),
    };
    let rootcanal_id = ffi_bluetooth::bluetooth_add(chip_id, &cxx_address, &proto_bytes);
    info!("Bluetooth EmulatedChip created with rootcanal_id: {rootcanal_id} chip_id: {chip_id}");
    let echip = Bluetooth {
        rootcanal_id,
        low_energy_enabled: ProtoState::ON,
        classic_enabled: ProtoState::ON,
    };
    BLUETOOTH_INVALID_PACKETS.lock().expect("invalid packets").insert(rootcanal_id, Vec::new());
    SharedEmulatedChip(Arc::new(Mutex::new(Box::new(echip))))
}

/// Starts the Bluetooth service.
pub fn bluetooth_start(config: &MessageField<BluetoothConfig>, instance_num: u16) {
    let proto_bytes = config.as_ref().unwrap_or_default().write_to_bytes().unwrap();
    ffi_bluetooth::bluetooth_start(&proto_bytes, instance_num);
}

/// Stops the Bluetooth service.
pub fn bluetooth_stop() {
    ffi_bluetooth::bluetooth_stop();
}

/// (Called by C++) Report Invalid Packet
pub fn report_invalid_packet_cxx(
    rootcanal_id: RootcanalIdentifier,
    reason: i32,
    description: &CxxString,
    packet: &CxxVector<u8>,
) {
    match BLUETOOTH_INVALID_PACKETS.lock().unwrap().get_mut(&rootcanal_id) {
        Some(v) => {
            // Remove the earliest reported packet if length greater than 5
            if v.len() >= 5 {
                v.remove(0);
            }
            // append error packet
            let mut invalid_packet = InvalidPacket::new();
            invalid_packet.set_reason(
                InvalidPacketReason::from_i32(reason).unwrap_or(InvalidPacketReason::UNKNOWN),
            );
            invalid_packet.set_description(description.to_string());
            invalid_packet.set_packet(packet.as_slice().to_vec());
            v.push(invalid_packet);
            // Log the report
            info!("Reported Invalid Packet for Bluetooth EmulatedChip with rootcanal_id: {rootcanal_id}, reason:{reason}, description: {description:?}, packet: {packet:?}");
        }
        None => error!("Bluetooth EmulatedChip not created for rootcanal_id: {rootcanal_id}"),
    }
}
