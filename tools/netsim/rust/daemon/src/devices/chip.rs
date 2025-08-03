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

/// A Chip is a generic emulated radio that connects to Chip Facade
/// library.
///
/// The chip facade is a library that implements the controller protocol.
///
use crate::devices::id_factory::IdFactory;
use crate::echip::SharedEmulatedChip;
use lazy_static::lazy_static;
use log::warn;
use netsim_proto::common::ChipKind as ProtoChipKind;
use netsim_proto::configuration::Controller as ProtoController;
use netsim_proto::model::Chip as ProtoChip;
use netsim_proto::stats::NetsimRadioStats as ProtoRadioStats;
use protobuf::EnumOrUnknown;
use std::collections::HashMap;
use std::sync::{Arc, Mutex, MutexGuard, RwLock};
use std::time::Instant;

use super::device::DeviceIdentifier;

pub type ChipIdentifier = u32;
pub type FacadeIdentifier = u32;

const INITIAL_CHIP_ID: ChipIdentifier = 1000;

// ChipMap is a singleton that contains a hash map from
// chip_id to Chip objects.
type ChipMap = HashMap<ChipIdentifier, Chip>;

#[derive(Clone)]
struct SharedChips(Arc<Mutex<ChipMap>>);

impl SharedChips {
    fn lock(&self) -> MutexGuard<ChipMap> {
        self.0.lock().expect("Poisoned Shared Chips lock")
    }
}

// Allocator for chip identifiers.
lazy_static! {
    static ref IDS: RwLock<IdFactory<ChipIdentifier>> =
        RwLock::new(IdFactory::new(INITIAL_CHIP_ID, 1));
    static ref CHIPS: SharedChips = SharedChips(Arc::new(Mutex::new(HashMap::new())));
}

pub struct CreateParams {
    pub kind: ProtoChipKind,
    pub address: String,
    pub name: Option<String>,
    pub manufacturer: String,
    pub product_name: String,
    pub bt_properties: Option<ProtoController>, // TODO: move to echip CreateParams
}

/// Chip contains the common information for each Chip/Controller.
/// Radio-specific information is contained in the emulated_chip.
#[derive(Clone)]
pub struct Chip {
    pub id: ChipIdentifier,
    pub device_id: DeviceIdentifier,
    pub emulated_chip: Option<SharedEmulatedChip>,
    pub kind: ProtoChipKind,
    pub address: String,
    pub name: String,
    // TODO: may not be necessary
    pub device_name: String,
    // These are patchable
    pub manufacturer: String,
    pub product_name: String,
    pub start: Instant,
}

impl Chip {
    // Use an Option here so that the Chip can be created and
    // inserted into the Device prior to creation of the echip.
    // Any Chip with an emulated_chip == None is temporary.
    // Creating the echip first required holding a Chip+Device lock through
    // initialization which caused a deadlock under certain (rare) conditions.
    fn new(
        id: ChipIdentifier,
        device_id: DeviceIdentifier,
        device_name: &str,
        create_params: &CreateParams,
    ) -> Self {
        Self {
            id,
            device_id,
            emulated_chip: None,
            kind: create_params.kind,
            address: create_params.address.clone(),
            name: create_params.name.clone().unwrap_or(format!("chip-{id}")),
            device_name: device_name.to_string(),
            manufacturer: create_params.manufacturer.clone(),
            product_name: create_params.product_name.clone(),
            start: Instant::now(),
        }
    }

    // Get the stats protobuf for a chip controller instance.
    //
    // This currently wraps the chip "get" facade method because the
    // counts are phy level. We need a vec since Bluetooth reports
    // stats for BLE and CLASSIC.
    pub fn get_stats(&self) -> Vec<ProtoRadioStats> {
        match &self.emulated_chip {
            Some(emulated_chip) => emulated_chip.lock().get_stats(self.start.elapsed().as_secs()),
            None => {
                warn!("EmulatedChip hasn't been instantiated yet for chip_id {}", self.id);
                Vec::<ProtoRadioStats>::new()
            }
        }
    }

    /// Create the model protobuf
    pub fn get(&self) -> Result<ProtoChip, String> {
        let mut proto_chip = self
            .emulated_chip
            .as_ref()
            .map(|c| c.lock().get())
            .ok_or(format!("EmulatedChip hasn't been instantiated yet for chip_id {}", self.id))?;
        proto_chip.kind = EnumOrUnknown::new(self.kind);
        proto_chip.id = self.id;
        proto_chip.name = self.name.clone();
        proto_chip.manufacturer = self.manufacturer.clone();
        proto_chip.product_name = self.product_name.clone();
        Ok(proto_chip)
    }

    /// Patch processing for the chip. Validate and move state from the patch
    /// into the chip changing the ChipFacade as needed.
    pub fn patch(&mut self, patch: &ProtoChip) -> Result<(), String> {
        if !patch.manufacturer.is_empty() {
            self.manufacturer = patch.manufacturer.clone();
        }
        if !patch.product_name.is_empty() {
            self.product_name = patch.product_name.clone();
        }
        self.emulated_chip
            .as_ref()
            .map(|c| c.lock().patch(patch))
            .ok_or(format!("EmulatedChip hasn't been instantiated yet for chip_id {}", self.id))
    }

    pub fn reset(&mut self) -> Result<(), String> {
        self.emulated_chip
            .as_ref()
            .map(|c| c.lock().reset())
            .ok_or(format!("EmulatedChip hasn't been instantiated yet for chip_id {}", self.id))
    }
}

/// Obtains a Chip with given chip_id
pub fn get(chip_id: ChipIdentifier) -> Result<Chip, String> {
    get_with_singleton(chip_id, CHIPS.clone())
}

/// Testable function for get()
fn get_with_singleton(chip_id: ChipIdentifier, shared_chips: SharedChips) -> Result<Chip, String> {
    Ok(shared_chips
        .lock()
        .get(&chip_id)
        .ok_or(format!("CHIPS does not contains key {chip_id}"))?
        .clone())
}

/// Allocates a new chip.
pub fn new(
    device_id: DeviceIdentifier,
    device_name: &str,
    create_params: &CreateParams,
) -> Result<Chip, String> {
    new_with_singleton(device_id, device_name, create_params, CHIPS.clone(), &IDS)
}

/// Testable function for new()
fn new_with_singleton(
    device_id: DeviceIdentifier,
    device_name: &str,
    create_params: &CreateParams,
    shared_chips: SharedChips,
    id_factory: &RwLock<IdFactory<ChipIdentifier>>,
) -> Result<Chip, String> {
    let id = id_factory.write().unwrap().next_id();
    let chip = Chip::new(id, device_id, device_name, create_params);
    shared_chips.lock().insert(id, chip.clone());
    Ok(chip)
}

#[cfg(test)]
mod tests {
    use netsim_proto::stats::netsim_radio_stats;

    use crate::echip::mocked;

    use super::*;

    const DEVICE_ID: u32 = 0;
    const DEVICE_NAME: &str = "device";
    const CHIP_KIND: ProtoChipKind = ProtoChipKind::UNSPECIFIED;
    const ADDRESS: &str = "address";
    const MANUFACTURER: &str = "manufacturer";
    const PRODUCT_NAME: &str = "product_name";

    fn new_test_chip(
        emulated_chip: Option<SharedEmulatedChip>,
        shared_chips: SharedChips,
        id_factory: &RwLock<IdFactory<ChipIdentifier>>,
    ) -> Chip {
        let create_params = CreateParams {
            kind: CHIP_KIND,
            address: ADDRESS.to_string(),
            name: None,
            manufacturer: MANUFACTURER.to_string(),
            product_name: PRODUCT_NAME.to_string(),
            bt_properties: None,
        };
        match new_with_singleton(
            DEVICE_ID,
            DEVICE_NAME,
            &create_params,
            shared_chips.clone(),
            id_factory,
        ) {
            Ok(mut chip) => {
                chip.emulated_chip = emulated_chip;
                chip
            }
            Err(err) => {
                unreachable!("{err:?}");
            }
        }
    }

    #[test]
    fn test_new_and_get_with_singleton() {
        // Construct a new Chip by calling new_test_chip, which calls new_with_singleton
        let shared_chips = SharedChips(Arc::new(Mutex::new(HashMap::new())));
        let id_factory = RwLock::new(IdFactory::new(INITIAL_CHIP_ID, 1));
        let chip = new_test_chip(None, shared_chips.clone(), &id_factory);

        // Check if the Chip has been successfully inserted in SharedChips
        let chip_id = chip.id;
        assert!(shared_chips.lock().contains_key(&chip_id));

        // Check if the get_with_singleton can successfully fetch the chip
        let chip_result = get_with_singleton(chip_id, shared_chips.clone());
        assert!(chip_result.is_ok());

        // Check if the fields are correctly populated
        let chip = chip_result.unwrap();
        assert_eq!(chip.device_id, DEVICE_ID);
        assert_eq!(chip.device_name, DEVICE_NAME);
        assert!(chip.emulated_chip.is_none());
        assert_eq!(chip.kind, CHIP_KIND);
        assert_eq!(chip.address, ADDRESS);
        assert_eq!(chip.name, format!("chip-{chip_id}"));
        assert_eq!(chip.manufacturer, MANUFACTURER);
        assert_eq!(chip.product_name, PRODUCT_NAME);
    }

    #[test]
    fn test_chip_get_stats() {
        // When emulated_chip is constructed
        let mocked_echip =
            mocked::new(&mocked::CreateParams { chip_kind: ProtoChipKind::UNSPECIFIED }, 0);
        let shared_chips = SharedChips(Arc::new(Mutex::new(HashMap::new())));
        let id_factory = RwLock::new(IdFactory::new(INITIAL_CHIP_ID, 1));
        let chip = new_test_chip(Some(mocked_echip), shared_chips.clone(), &id_factory);
        assert_eq!(netsim_radio_stats::Kind::UNSPECIFIED, chip.get_stats().first().unwrap().kind());

        // When emulated_chip is not constructed
        let chip = new_test_chip(None, shared_chips.clone(), &id_factory);
        assert_eq!(Vec::<ProtoRadioStats>::new(), chip.get_stats());
    }

    #[test]
    fn test_chip_get() {
        let mocked_echip =
            mocked::new(&mocked::CreateParams { chip_kind: ProtoChipKind::UNSPECIFIED }, 0);
        let shared_chips = SharedChips(Arc::new(Mutex::new(HashMap::new())));
        let id_factory = RwLock::new(IdFactory::new(INITIAL_CHIP_ID, 1));
        let chip = new_test_chip(Some(mocked_echip.clone()), shared_chips.clone(), &id_factory);

        // Obtain actual chip.get()
        let actual = chip.get().unwrap();

        // Construct expected ProtoChip
        let mut expected = mocked_echip.lock().get();
        expected.kind = EnumOrUnknown::new(chip.kind);
        expected.id = chip.id;
        expected.name = chip.name.clone();
        expected.manufacturer = chip.manufacturer.clone();
        expected.product_name = chip.product_name.clone();

        // Compare
        assert_eq!(expected, actual);
    }

    #[test]
    fn test_chip_patch() {
        let mocked_echip =
            mocked::new(&mocked::CreateParams { chip_kind: ProtoChipKind::UNSPECIFIED }, 0);
        let shared_chips = SharedChips(Arc::new(Mutex::new(HashMap::new())));
        let id_factory = RwLock::new(IdFactory::new(INITIAL_CHIP_ID, 1));
        let mut chip = new_test_chip(Some(mocked_echip.clone()), shared_chips.clone(), &id_factory);

        // Construct the patch body for modifying manufacturer and product_name
        let mut patch_body = ProtoChip::new();
        patch_body.manufacturer = "patched_manufacturer".to_string();
        patch_body.product_name = "patched_product_name".to_string();

        // Perform Patch
        assert!(chip.patch(&patch_body).is_ok());

        // Check if fields of chip has been patched
        assert_eq!(patch_body.manufacturer, chip.manufacturer);
        assert_eq!(patch_body.product_name, chip.product_name)
    }

    // TODO (b/309529194)
    // Implement echip/mocked.rs to test emulated_chip level of patch and resets.
}
