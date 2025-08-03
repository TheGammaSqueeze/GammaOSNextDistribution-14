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

use crate::bluetooth::{BeaconChip, BEACON_CHIPS};
use crate::devices::chip::{ChipIdentifier, FacadeIdentifier};
use crate::devices::device::{AddChipResult, DeviceIdentifier};
use ::protobuf::MessageField;
use lazy_static::lazy_static;
use log::info;
use netsim_proto::config::Bluetooth as BluetoothConfig;
use netsim_proto::configuration::Controller as RootcanalController;
use netsim_proto::model::chip::{BleBeacon, Bluetooth};
use netsim_proto::model::chip_create::Chip as Builtin;
use netsim_proto::model::{ChipCreate, DeviceCreate};
use std::sync::Mutex;
use std::sync::RwLock;
use std::{collections::HashMap, ptr::null};

lazy_static! {
    static ref IDS: RwLock<FacadeIds> = RwLock::new(FacadeIds::new());
}

struct FacadeIds {
    pub current_id: u32,
}

impl FacadeIds {
    fn new() -> Self {
        FacadeIds { current_id: 0 }
    }
}

// Avoid crossing cxx boundary in tests
pub fn ble_beacon_add(
    device_name: String,
    chip_id: ChipIdentifier,
    chip_proto: &ChipCreate,
) -> Result<FacadeIdentifier, String> {
    let beacon_proto = match &chip_proto.chip {
        Some(Builtin::BleBeacon(beacon_proto)) => beacon_proto,
        _ => return Err(String::from("failed to create ble beacon: unexpected chip type")),
    };

    let beacon_chip = BeaconChip::from_proto(device_name, chip_id, beacon_proto)?;
    if BEACON_CHIPS.write().unwrap().insert(chip_id, Mutex::new(beacon_chip)).is_some() {
        return Err(format!(
            "failed to create a bluetooth beacon chip with id {chip_id}: chip id already exists.",
        ));
    }

    let mut resource = crate::bluetooth::mocked::IDS.write().unwrap();
    let facade_id = resource.current_id;
    resource.current_id += 1;

    info!("ble_beacon_add successful with chip_id: {chip_id}");
    Ok(facade_id)
}

pub fn ble_beacon_remove(
    chip_id: ChipIdentifier,
    facade_id: FacadeIdentifier,
) -> Result<(), String> {
    info!("{:?}", BEACON_CHIPS.read().unwrap().keys());
    if BEACON_CHIPS.write().unwrap().remove(&chip_id).is_none() {
        Err(format!("failed to delete ble beacon chip: chip with id {chip_id} does not exist"))
    } else {
        Ok(())
    }
}
