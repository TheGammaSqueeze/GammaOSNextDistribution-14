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

// Device.rs

use netsim_proto::model::State;
use protobuf::Message;

use crate::devices::chip;
use crate::devices::chip::Chip;
use crate::devices::chip::ChipIdentifier;
use netsim_proto::common::ChipKind as ProtoChipKind;
use netsim_proto::model::Device as ProtoDevice;
use netsim_proto::model::Orientation as ProtoOrientation;
use netsim_proto::model::Position as ProtoPosition;
use netsim_proto::stats::NetsimRadioStats as ProtoRadioStats;
use std::collections::BTreeMap;

pub type DeviceIdentifier = u32;

pub struct Device {
    pub id: DeviceIdentifier,
    pub guid: String,
    pub name: String,
    pub visible: State,
    pub position: ProtoPosition,
    pub orientation: ProtoOrientation,
    pub chips: BTreeMap<ChipIdentifier, Chip>,
    pub builtin: bool,
}
impl Device {
    pub fn new(id: DeviceIdentifier, guid: String, name: String, builtin: bool) -> Self {
        Device {
            id,
            guid,
            name,
            visible: State::ON,
            position: ProtoPosition::new(),
            orientation: ProtoOrientation::new(),
            chips: BTreeMap::new(),
            builtin,
        }
    }
}

#[derive(Debug, Clone)]
pub struct AddChipResult {
    pub device_id: DeviceIdentifier,
    pub chip_id: ChipIdentifier,
}

impl Device {
    pub fn get(&self) -> Result<ProtoDevice, String> {
        let mut device = ProtoDevice::new();
        device.id = self.id;
        device.name = self.name.clone();
        device.visible = self.visible.into();
        device.position = protobuf::MessageField::from(Some(self.position.clone()));
        device.orientation = protobuf::MessageField::from(Some(self.orientation.clone()));
        for chip in self.chips.values() {
            device.chips.push(chip.get()?);
        }
        Ok(device)
    }

    /// Patch a device and its chips.
    pub fn patch(&mut self, patch: &ProtoDevice) -> Result<(), String> {
        let patch_visible = patch.visible.enum_value_or_default();
        if patch_visible != State::UNKNOWN {
            self.visible = patch_visible;
        }
        if patch.position.is_some() {
            self.position.clone_from(&patch.position);
        }
        if patch.orientation.is_some() {
            self.orientation.clone_from(&patch.orientation);
        }
        // iterate over patched ProtoChip entries and patch matching chip
        for patch_chip in patch.chips.iter() {
            let mut patch_chip_kind = patch_chip.kind.enum_value_or_default();
            // Check if chip is given when kind is not given.
            // TODO: Fix patch device request body in CLI to include ChipKind, and remove if block below.
            if patch_chip_kind == ProtoChipKind::UNSPECIFIED {
                if patch_chip.has_bt() {
                    patch_chip_kind = ProtoChipKind::BLUETOOTH;
                } else if patch_chip.has_ble_beacon() {
                    patch_chip_kind = ProtoChipKind::BLUETOOTH_BEACON;
                } else if patch_chip.has_wifi() {
                    patch_chip_kind = ProtoChipKind::WIFI;
                } else if patch_chip.has_uwb() {
                    patch_chip_kind = ProtoChipKind::UWB;
                } else {
                    break;
                }
            }
            let patch_chip_name = &patch_chip.name;
            // Find the matching chip and patch the proto chip
            let target = self.match_target_chip(patch_chip_kind, patch_chip_name)?;
            match target {
                Some(chip) => chip.patch(patch_chip)?,
                None => {
                    return Err(format!(
                        "Chip {} not found in device {}",
                        patch_chip_name, self.name
                    ))
                }
            }
        }
        Ok(())
    }

    fn match_target_chip(
        &mut self,
        patch_chip_kind: ProtoChipKind,
        patch_chip_name: &str,
    ) -> Result<Option<&mut Chip>, String> {
        let mut multiple_matches = false;
        let mut target: Option<&mut Chip> = None;
        for chip in self.chips.values_mut() {
            // Check for specified chip kind and matching chip name
            if chip.kind == patch_chip_kind && chip.name.contains(patch_chip_name) {
                // Check for exact match
                if chip.name == patch_chip_name {
                    multiple_matches = false;
                    target = Some(chip);
                    break;
                }
                // Check for ambiguous match
                if target.is_none() {
                    target = Some(chip);
                } else {
                    // Return if no chip name is supplied but multiple chips of specified kind exist
                    if patch_chip_name.is_empty() {
                        return Err(format!(
                            "No chip name is supplied but multiple chips of chip kind {:?} exist.",
                            chip.kind
                        ));
                    }
                    // Multiple matches were found - continue to look for possible exact match
                    multiple_matches = true;
                }
            }
        }
        if multiple_matches {
            return Err(format!(
                "Multiple ambiguous matches were found with chip name {}",
                patch_chip_name
            ));
        }
        Ok(target)
    }

    /// Remove a chip from a device.
    pub fn remove_chip(&mut self, chip_id: ChipIdentifier) -> Result<Vec<ProtoRadioStats>, String> {
        let radio_stats = self
            .chips
            .get_mut(&chip_id)
            .ok_or(format!("RemoveChip chip id {chip_id} not found"))
            .map(|c| c.get_stats())?;
        match self.chips.remove(&chip_id) {
            Some(_) => Ok(radio_stats),
            None => Err(format!("Key {chip_id} not found in Hashmap")),
        }
    }

    pub fn add_chip(
        &mut self,
        chip_create_params: &chip::CreateParams,
    ) -> Result<(DeviceIdentifier, ChipIdentifier), String> {
        for chip in self.chips.values() {
            if chip.kind == chip_create_params.kind
                && chip_create_params.name.clone().is_some_and(|name| name == chip.name)
            {
                return Err(format!("Device::AddChip - duplicate at id {}, skipping.", chip.id));
            }
        }

        let chip = chip::new(self.id, &self.name, chip_create_params)?;
        let chip_id = chip.id;
        self.chips.insert(chip_id, chip);

        Ok((self.id, chip_id))
    }

    /// Reset a device to its default state.
    pub fn reset(&mut self) -> Result<(), String> {
        self.visible = State::ON;
        self.position.clear();
        self.orientation.clear();
        for chip in self.chips.values_mut() {
            chip.reset()?;
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    static PATCH_CHIP_KIND: ProtoChipKind = ProtoChipKind::BLUETOOTH;
    static TEST_DEVICE_NAME: &str = "test_device";
    static TEST_CHIP_NAME_1: &str = "test-bt-chip-1";
    static TEST_CHIP_NAME_2: &str = "test-bt-chip-2";

    fn create_test_device() -> Result<Device, String> {
        let mut device = Device::new(0, "0".to_string(), TEST_DEVICE_NAME.to_string(), false);
        device.add_chip(&chip::CreateParams {
            kind: ProtoChipKind::BLUETOOTH,
            address: "".to_string(),
            name: Some(TEST_CHIP_NAME_1.to_string()),
            manufacturer: "test_manufacturer".to_string(),
            product_name: "test_product_name".to_string(),
            bt_properties: None,
        })?;
        device.add_chip(&chip::CreateParams {
            kind: ProtoChipKind::BLUETOOTH,
            address: "".to_string(),
            name: Some(TEST_CHIP_NAME_2.to_string()),
            manufacturer: "test_manufacturer".to_string(),
            product_name: "test_product_name".to_string(),
            bt_properties: None,
        })?;
        Ok(device)
    }

    #[ignore = "TODO: include thread_id in names and ids"]
    #[test]
    fn test_exact_target_match() {
        let mut device = create_test_device().unwrap();
        let result = device.match_target_chip(PATCH_CHIP_KIND, TEST_CHIP_NAME_1);
        assert!(result.is_ok());
        let target = result.unwrap();
        assert!(target.is_some());
        assert_eq!(target.unwrap().name, TEST_CHIP_NAME_1);
        assert_eq!(device.name, TEST_DEVICE_NAME);
    }

    #[ignore = "TODO: include thread_id in names and ids"]
    #[test]
    fn test_substring_target_match() {
        let mut device = create_test_device().unwrap();
        let result = device.match_target_chip(PATCH_CHIP_KIND, "chip-1");
        assert!(result.is_ok());
        let target = result.unwrap();
        assert!(target.is_some());
        assert_eq!(target.unwrap().name, TEST_CHIP_NAME_1);
        assert_eq!(device.name, TEST_DEVICE_NAME);
    }

    #[ignore = "TODO: include thread_id in names and ids"]
    #[test]
    fn test_ambiguous_target_match() {
        let mut device = create_test_device().unwrap();
        let result = device.match_target_chip(PATCH_CHIP_KIND, "chip");
        assert!(result.is_err());
        assert_eq!(
            result.err(),
            Some("Multiple ambiguous matches were found with chip name chip".to_string())
        );
    }

    #[ignore = "TODO: include thread_id in names and ids"]
    #[test]
    fn test_ambiguous_empty_target_match() {
        let mut device = create_test_device().unwrap();
        let result = device.match_target_chip(PATCH_CHIP_KIND, "");
        assert!(result.is_err());
        assert_eq!(
            result.err(),
            Some(format!(
                "No chip name is supplied but multiple chips of chip kind {:?} exist.",
                PATCH_CHIP_KIND
            ))
        );
    }

    #[ignore = "TODO: include thread_id in names and ids"]
    #[test]
    fn test_no_target_match() {
        let mut device = create_test_device().unwrap();
        let invalid_chip_name = "invalid-chip";
        let result = device.match_target_chip(PATCH_CHIP_KIND, invalid_chip_name);
        assert!(result.is_ok());
        let target = result.unwrap();
        assert!(target.is_none());
    }
}
