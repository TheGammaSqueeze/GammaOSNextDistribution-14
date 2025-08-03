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

use crate::{devices::chip::ChipIdentifier, ffi::ffi_bluetooth::RustBluetoothChip};
use cxx::{let_cxx_string, UniquePtr};

/// Rust bluetooth chip trait.
pub trait RustBluetoothChipCallbacks {
    fn tick(&mut self);

    // TODO: include the pdl library in Rust for reading the packet contents.
    fn receive_link_layer_packet(
        &mut self,
        source_address: String,
        destination_address: String,
        packet_type: u8,
        packet: &[u8],
    );
}

/// AddRustDeviceResult for the returned object of AddRustDevice() in C++
pub struct AddRustDeviceResult {
    pub rust_chip: UniquePtr<RustBluetoothChip>,
    pub facade_id: u32,
}

pub fn create_add_rust_device_result(
    facade_id: u32,
    rust_chip: UniquePtr<RustBluetoothChip>,
) -> Box<AddRustDeviceResult> {
    Box::new(AddRustDeviceResult { facade_id, rust_chip })
}

/// Add a bluetooth chip by an object implements RustBluetoothChipCallbacks trait.
pub fn rust_bluetooth_add(
    chip_id: ChipIdentifier,
    callbacks: Box<dyn RustBluetoothChipCallbacks>,
    string_type: String,
    address: String,
) -> Box<AddRustDeviceResult> {
    let_cxx_string!(cxx_string_type = string_type);
    let_cxx_string!(cxx_address = address);
    crate::ffi::ffi_bluetooth::bluetooth_add_rust_device(
        chip_id,
        Box::new(callbacks),
        &cxx_string_type,
        &cxx_address,
    )
}
