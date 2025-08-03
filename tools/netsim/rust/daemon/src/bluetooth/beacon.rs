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

use super::advertise_data::{AdvertiseData, AdvertiseDataBuilder};
use super::advertise_settings::{
    AdvertiseMode, AdvertiseSettings, AdvertiseSettingsBuilder, TxPowerLevel,
};
use super::chip::{rust_bluetooth_add, RustBluetoothChipCallbacks};
use super::packets::link_layer::{
    Address, AddressType, LeLegacyAdvertisingPduBuilder, LeScanResponseBuilder, PacketType,
};
use crate::devices::chip::{ChipIdentifier, FacadeIdentifier};
use crate::devices::device::{AddChipResult, DeviceIdentifier};
use crate::devices::{devices_handler::add_chip, id_factory::IdFactory};
use crate::echip;
use crate::ffi::ffi_bluetooth;
use cxx::{let_cxx_string, UniquePtr};
use lazy_static::lazy_static;
use log::{error, info, warn};
use netsim_proto::common::ChipKind;
use netsim_proto::model::chip::Bluetooth;
use netsim_proto::model::chip::{
    ble_beacon::AdvertiseData as AdvertiseDataProto,
    ble_beacon::AdvertiseSettings as AdvertiseSettingsProto, BleBeacon as BleBeaconProto,
};
use netsim_proto::model::chip_create::{
    BleBeaconCreate as BleBeaconCreateProto, Chip as BuiltinProto,
};
use netsim_proto::model::{ChipCreate as ChipCreateProto, DeviceCreate as DeviceCreateProto};
use pdl_runtime::Packet;
use protobuf::{Message, MessageField};
use std::alloc::System;
use std::sync::{Mutex, RwLock};
use std::time::{Duration, Instant};
use std::{collections::HashMap, ptr::null};

lazy_static! {
    static ref EMPTY_ADDRESS: Address = Address::try_from(0u64).unwrap();
    // A singleton that contains a hash map from chip id to RustBluetoothChip.
    // It's used by `BeaconChip` to access `RustBluetoothChip` to call send_link_layer_packet().
    static ref BT_CHIPS: RwLock<HashMap<ChipIdentifier, Mutex<UniquePtr<ffi_bluetooth::RustBluetoothChip>>>> =
        RwLock::new(HashMap::new());
    // Used to find beacon chip based on it's id from static methods.
    pub(crate) static ref BEACON_CHIPS: RwLock<HashMap<ChipIdentifier, Mutex<BeaconChip>>> =
        RwLock::new(HashMap::new());
}

/// BeaconChip class.
pub struct BeaconChip {
    device_name: String,
    chip_id: ChipIdentifier,
    address: Address,
    advertise_settings: AdvertiseSettings,
    advertise_data: AdvertiseData,
    scan_response_data: AdvertiseData,
    advertise_last: Option<Instant>,
    advertise_start: Option<Instant>,
}

impl BeaconChip {
    pub fn new(
        device_name: String,
        chip_id: ChipIdentifier,
        address: String,
    ) -> Result<Self, String> {
        Ok(BeaconChip {
            chip_id,
            device_name: device_name.clone(),
            address: str_to_addr(&address)?,
            advertise_settings: AdvertiseSettings::builder().build(),
            advertise_data: AdvertiseData::builder(device_name.clone(), TxPowerLevel::default())
                .build()
                .unwrap(),
            scan_response_data: AdvertiseData::builder(device_name, TxPowerLevel::default())
                .build()
                .unwrap(),
            advertise_last: None,
            advertise_start: None,
        })
    }

    pub fn from_proto(
        device_name: String,
        chip_id: ChipIdentifier,
        beacon_proto: &BleBeaconCreateProto,
    ) -> Result<Self, String> {
        let advertise_settings = AdvertiseSettings::from_proto(&beacon_proto.settings)?;
        let advertise_data = AdvertiseData::from_proto(
            device_name.clone(),
            beacon_proto
                .settings
                .tx_power
                .as_ref()
                .map(TxPowerLevel::try_from)
                .transpose()?
                .unwrap_or_default(),
            &beacon_proto.adv_data,
        )?;
        let scan_response_data = AdvertiseData::from_proto(
            device_name.clone(),
            advertise_settings.tx_power_level,
            &beacon_proto.scan_response,
        )?;

        let address = if beacon_proto.address == String::default() {
            // Safe to unwrap here because chip_id is a u32 which is less than 6 bytes
            u64::from(chip_id).try_into().unwrap()
        } else {
            str_to_addr(&beacon_proto.address)?
        };

        Ok(BeaconChip {
            device_name,
            chip_id,
            address,
            advertise_settings,
            advertise_data,
            scan_response_data,
            advertise_last: None,
            advertise_start: None,
        })
    }

    pub fn send_link_layer_le_packet(&self, packet: &[u8], tx_power: i8) {
        let binding = BT_CHIPS.read().unwrap();
        if let Some(rust_bluetooth_chip) = binding.get(&self.chip_id) {
            rust_bluetooth_chip
                .lock()
                .expect("Failed to acquire lock on RustBluetoothChip")
                .pin_mut()
                .send_link_layer_le_packet(packet, tx_power);
        } else {
            warn!("Failed to get RustBluetoothChip for unknown chip id: {}", self.chip_id);
        };
    }
}

// BEACON_CHIPS has ownership of all the BeaconChips, so we need a separate class to hold the callbacks.
// This class will be owned by rootcanal.
pub struct BeaconChipCallbacks {
    chip_id: ChipIdentifier,
}

impl RustBluetoothChipCallbacks for BeaconChipCallbacks {
    fn tick(&mut self) {
        let guard = BEACON_CHIPS.read().unwrap();
        let mut beacon = guard.get(&self.chip_id);
        if beacon.is_none() {
            error!("could not find bluetooth beacon with chip id {}", self.chip_id);
            return;
        }
        let mut beacon = beacon.unwrap().lock().expect("Failed to acquire lock on BeaconChip");

        if let (Some(start), Some(timeout)) =
            (beacon.advertise_start, beacon.advertise_settings.timeout)
        {
            if start.elapsed() > timeout {
                return;
            }
        }

        if let Some(last) = beacon.advertise_last {
            if last.elapsed() <= beacon.advertise_settings.mode.interval {
                return;
            }
        } else {
            beacon.advertise_start = Some(Instant::now())
        }

        beacon.advertise_last = Some(Instant::now());

        let packet = LeLegacyAdvertisingPduBuilder {
            advertising_type: beacon.advertise_settings.get_packet_type(),
            advertising_data: beacon.advertise_data.to_bytes(),
            advertising_address_type: AddressType::Public,
            target_address_type: AddressType::Public,
            source_address: beacon.address,
            destination_address: *EMPTY_ADDRESS,
        }
        .build()
        .to_vec();

        beacon.send_link_layer_le_packet(&packet, beacon.advertise_settings.tx_power_level.dbm);
    }

    fn receive_link_layer_packet(
        &mut self,
        source_address: String,
        destination_address: String,
        packet_type: u8,
        packet: &[u8],
    ) {
        let guard = BEACON_CHIPS.read().unwrap();
        let beacon = guard.get(&self.chip_id);
        if beacon.is_none() {
            error!("could not find bluetooth beacon with chip id {}", self.chip_id);
            return;
        }
        let beacon = beacon.unwrap().lock().expect("Failed to acquire lock on BeaconChip");

        if beacon.advertise_settings.scannable
            && destination_address == addr_to_str(beacon.address)
            && packet_type == u8::from(PacketType::LeScan)
        {
            let packet = LeScanResponseBuilder {
                advertising_address_type: AddressType::Public,
                source_address: beacon.address,
                destination_address: beacon.address,
                scan_response_data: beacon.scan_response_data.to_bytes(),
            }
            .build()
            .to_vec();

            beacon.send_link_layer_le_packet(&packet, beacon.advertise_settings.tx_power_level.dbm);
        }
    }
}

/// Add a beacon device in rootcanal.
///
/// Called by `devices/chip.rs`.
///
/// Similar to `bluetooth_add()`.
#[cfg(not(test))]
pub fn ble_beacon_add(
    device_name: String,
    chip_id: ChipIdentifier,
    chip_proto: &ChipCreateProto,
) -> Result<FacadeIdentifier, String> {
    let beacon_proto = match &chip_proto.chip {
        Some(BuiltinProto::BleBeacon(beacon_proto)) => beacon_proto,
        _ => return Err(String::from("failed to create ble beacon: unexpected chip type")),
    };

    let beacon_chip = BeaconChip::from_proto(device_name, chip_id, beacon_proto)?;
    if BEACON_CHIPS.write().unwrap().insert(chip_id, Mutex::new(beacon_chip)).is_some() {
        return Err(format!(
            "failed to create a bluetooth beacon chip with id {chip_id}: chip id already exists.",
        ));
    }

    let callbacks: Box<dyn RustBluetoothChipCallbacks> = Box::new(BeaconChipCallbacks { chip_id });
    let add_rust_device_result = rust_bluetooth_add(
        chip_id,
        callbacks,
        String::from("beacon"),
        beacon_proto.address.clone(),
    );
    let rust_chip = add_rust_device_result.rust_chip;
    let facade_id = add_rust_device_result.facade_id;
    info!("Creating HCI facade_id: {} for chip_id: {}", facade_id, chip_id);
    BT_CHIPS.write().unwrap().insert(chip_id, Mutex::new(rust_chip));

    Ok(facade_id)
}

#[cfg(not(test))]
pub fn ble_beacon_remove(
    chip_id: ChipIdentifier,
    facade_id: FacadeIdentifier,
) -> Result<(), String> {
    let removed_beacon = BEACON_CHIPS.write().unwrap().remove(&chip_id);
    let removed_radio = BT_CHIPS.write().unwrap().remove(&chip_id);
    if removed_beacon.is_none() || removed_radio.is_none() {
        Err(format!("failed to delete ble beacon chip: chip with id {chip_id} does not exist"))
    } else {
        ffi_bluetooth::bluetooth_remove_rust_device(facade_id);
        Ok(())
    }
}

pub fn ble_beacon_patch(
    facade_id: FacadeIdentifier,
    chip_id: ChipIdentifier,
    patch: &BleBeaconProto,
) -> Result<(), String> {
    let mut guard = BEACON_CHIPS.write().unwrap();
    let mut beacon = guard
        .get_mut(&chip_id)
        .ok_or(format!("could not find bluetooth beacon with chip id {chip_id} for patching"))?
        .get_mut()
        .unwrap();

    if patch.address != String::default() {
        beacon.address = str_to_addr(&patch.address)?;
        #[cfg(not(test))]
        ffi_bluetooth::bluetooth_set_rust_device_address(
            facade_id,
            u64::from(beacon.address).to_le_bytes()[..6].try_into().unwrap(),
        );
    }

    if let Some(patch_settings) = patch.settings.as_ref() {
        if let Some(interval) = patch_settings.interval.as_ref() {
            beacon.advertise_settings.mode = interval.into();
        }

        if let Some(tx_power) = patch_settings.tx_power.as_ref() {
            beacon.advertise_settings.tx_power_level = tx_power.try_into()?
        }

        beacon.advertise_settings.scannable =
            patch_settings.scannable || beacon.advertise_settings.scannable;

        if patch_settings.timeout != u64::default() {
            beacon.advertise_settings.timeout = Some(Duration::from_millis(patch_settings.timeout));
        }
    }

    if let Some(patch_adv_data) = patch.adv_data.as_ref() {
        let mut builder = AdvertiseData::builder(
            beacon.device_name.clone(),
            beacon.advertise_settings.tx_power_level,
        );

        if patch_adv_data.include_device_name || beacon.advertise_data.include_device_name {
            builder.include_device_name();
        }

        if patch_adv_data.include_tx_power_level || beacon.advertise_data.include_tx_power_level {
            builder.include_tx_power_level();
        }

        if !patch_adv_data.manufacturer_data.is_empty() {
            builder.manufacturer_data(patch_adv_data.manufacturer_data.clone());
        } else if let Some(manufacturer_data) = beacon.advertise_data.manufacturer_data.as_ref() {
            builder.manufacturer_data(manufacturer_data.clone());
        }

        beacon.advertise_data = builder.build()?;
    }

    Ok(())
}

pub fn ble_beacon_get(
    chip_id: ChipIdentifier,
    _facade_id: FacadeIdentifier,
) -> Result<BleBeaconProto, String> {
    let guard = BEACON_CHIPS.read().unwrap();
    let beacon = guard
        .get(&chip_id)
        .ok_or(format!("could not get bluetooth beacon with chip id {chip_id}"))?
        .lock()
        .expect("Failed to acquire lock on BeaconChip");
    #[cfg(not(test))]
    let bt = {
        let bluetooth_bytes = ffi_bluetooth::bluetooth_get_cxx(_facade_id);
        Some(Bluetooth::parse_from_bytes(&bluetooth_bytes).unwrap())
    };
    #[cfg(test)]
    let bt = Some(netsim_proto::model::chip::Bluetooth::new());
    Ok(BleBeaconProto {
        bt: bt.into(),
        address: addr_to_str(beacon.address),
        settings: MessageField::some((&beacon.advertise_settings).try_into()?),
        adv_data: MessageField::some((&beacon.advertise_data).into()),
        ..Default::default()
    })
}

fn addr_to_str(addr: Address) -> String {
    let bytes = u64::from(addr).to_le_bytes();
    bytes[..5]
        .iter()
        .rfold(format!("{:02x}", bytes[5]), |addr, byte| addr + &format!(":{:02x}", byte))
}

fn str_to_addr(addr: &str) -> Result<Address, String> {
    if addr == String::default() {
        Ok(*EMPTY_ADDRESS)
    } else {
        if addr.len() != 17 {
            return Err(String::from("failed to parse address: address was not the right length"));
        }
        let addr = addr.replace(':', "");
        u64::from_str_radix(&addr, 16)
            .map_err(|_| String::from("failed to parse address: invalid hex"))?
            .try_into()
            .map_err(|_| {
                String::from("failed to parse address: address must be smaller than 6 bytes")
            })
    }
}

#[cfg(test)]
pub mod tests {
    use std::thread;

    use netsim_proto::model::chip::ble_beacon::{
        advertise_settings::{AdvertiseTxPower as AdvertiseTxPowerProto, Tx_power as TxPowerProto},
        AdvertiseData as AdvertiseDataProto,
    };

    use super::*;
    // using ble_beacon_add from mocked.rs
    use crate::bluetooth::ble_beacon_add;

    lazy_static! {
        static ref TEST_GUID_GENERATOR: Mutex<IdFactory<u32>> = Mutex::new(IdFactory::new(0, 1));
    }

    fn new_test_beacon_with_settings(settings: AdvertiseSettingsProto) -> DeviceIdentifier {
        let id = TEST_GUID_GENERATOR.lock().unwrap().next_id();

        let add_result = ble_beacon_add(
            format!("test-device-{:?}", thread::current().id()),
            id,
            &ChipCreateProto {
                name: format!("test-beacon-chip-{:?}", thread::current().id()),
                chip: Some(BuiltinProto::BleBeacon(BleBeaconCreateProto {
                    address: String::from("00:00:00:00:00:00"),
                    settings: MessageField::some(settings),
                    ..Default::default()
                })),
                ..Default::default()
            },
        );
        assert!(add_result.is_ok(), "{}", add_result.unwrap_err());

        id
    }

    fn cleanup_beacon(chip_id: ChipIdentifier) {
        BEACON_CHIPS.write().unwrap().remove(&chip_id);
    }

    #[test]
    fn test_beacon_get() {
        let interval = Duration::from_millis(9999);
        let settings = AdvertiseSettingsProto {
            interval: Some(AdvertiseMode::new(interval).try_into().unwrap()),
            ..Default::default()
        };

        let id = new_test_beacon_with_settings(settings);

        let beacon = ble_beacon_get(id, 0);
        assert!(beacon.is_ok(), "{}", beacon.unwrap_err());
        let beacon = beacon.unwrap();

        let interval_after_get =
            beacon.settings.interval.as_ref().map(AdvertiseMode::from).unwrap().interval;

        assert_eq!(interval, interval_after_get);
        cleanup_beacon(id);
    }

    #[test]
    fn test_beacon_patch() {
        let settings = AdvertiseSettingsProto {
            interval: Some(AdvertiseMode::new(Duration::from_millis(0)).try_into().unwrap()),
            ..Default::default()
        };

        let id = new_test_beacon_with_settings(settings);

        let interval = Duration::from_millis(33);
        let tx_power = TxPowerProto::TxPowerLevel(AdvertiseTxPowerProto::MEDIUM.into());
        let scannable = true;
        let patch_result = ble_beacon_patch(
            0,
            id,
            &BleBeaconProto {
                settings: MessageField::some(AdvertiseSettingsProto {
                    interval: Some(
                        AdvertiseMode::new(Duration::from_millis(33)).try_into().unwrap(),
                    ),
                    scannable,
                    tx_power: Some(tx_power.clone()),
                    ..Default::default()
                }),
                ..Default::default()
            },
        );
        assert!(patch_result.is_ok(), "{}", patch_result.unwrap_err());

        let beacon_proto = ble_beacon_get(id, 0);
        assert!(beacon_proto.is_ok(), "{}", beacon_proto.unwrap_err());
        let beacon_proto = beacon_proto.unwrap();
        let interval_after_patch =
            beacon_proto.settings.interval.as_ref().map(AdvertiseMode::from).unwrap().interval;

        assert_eq!(interval, interval_after_patch);
        assert_eq!(tx_power, *beacon_proto.settings.tx_power.as_ref().unwrap());
        assert_eq!(scannable, beacon_proto.settings.scannable);
        cleanup_beacon(id);
    }

    #[test]
    fn test_beacon_patch_default() {
        let settings =
            AdvertiseSettingsProto { timeout: 1234, scannable: true, ..Default::default() };

        let id = new_test_beacon_with_settings(settings.clone());

        let patch_result = ble_beacon_patch(0, id, &BleBeaconProto::default());
        assert!(patch_result.is_ok(), "{}", patch_result.unwrap_err());

        let beacon_proto = ble_beacon_get(id, 0);
        assert!(beacon_proto.is_ok(), "{}", beacon_proto.unwrap_err());
        let beacon_proto = beacon_proto.unwrap();

        let settings_after_patch = beacon_proto.settings.unwrap();
        assert_eq!(settings.timeout, settings_after_patch.timeout);
        assert_eq!(settings.scannable, settings_after_patch.scannable);
    }

    #[test]
    fn test_str_to_addr_succeeds() {
        let addr = str_to_addr("be:ac:12:34:00:0f");
        assert_eq!(Address::try_from(0xbe_ac_12_34_00_0f).unwrap(), addr.unwrap());
    }

    #[test]
    fn test_empty_str_to_addr_succeeds() {
        let addr = str_to_addr("00:00:00:00:00:00");
        assert_eq!(Address::try_from(0).unwrap(), addr.unwrap());
    }

    #[test]
    fn test_str_to_addr_fails() {
        let addr = str_to_addr("hi mom!");
        assert!(addr.is_err());
    }

    #[test]
    fn test_invalid_str_to_addr_fails() {
        let addr = str_to_addr("56:78:9a:bc:de:fg");
        assert!(addr.is_err());
    }

    #[test]
    fn test_long_str_to_addr_fails() {
        let addr = str_to_addr("55:55:55:55:55:55:55:55");
        assert!(addr.is_err());
    }

    #[test]
    fn test_short_str_to_addr_fails() {
        let addr = str_to_addr("ab:cd");
        assert!(addr.is_err());
    }

    #[test]
    fn test_addr_to_str_succeeds() {
        let addr: u64 = 0xbe_ac_12_34_00_0f;
        assert_eq!("be:ac:12:34:00:0f", addr_to_str(addr.try_into().unwrap()))
    }

    #[test]
    fn test_empty_addr_to_str_succeeds() {
        let addr: u64 = 0;
        assert_eq!("00:00:00:00:00:00", addr_to_str(addr.try_into().unwrap()))
    }

    #[test]
    fn test_small_addr_to_str_succeeds() {
        let addr: u64 = 123;
        assert_eq!("00:00:00:00:00:7b", addr_to_str(addr.try_into().unwrap()))
    }
}
