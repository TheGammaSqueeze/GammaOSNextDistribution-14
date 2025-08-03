/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! This library implements the functionality used by the KeyMint Trusty
//! application.

mod ffi_bindings;
mod ipc_manager;
mod key_wrapper;
mod keybox;
mod keymaster_attributes;
mod keys;
mod monotonic_clock;
mod rng;
mod rpc;
mod secure_deletion_secret_manager;
mod secure_storage_manager;

pub use ipc_manager::handle_port_connections;
pub use key_wrapper::{TrustyAes, TrustyStorageKeyWrapper};
pub use keys::legacy::TrustyLegacyKeyBlobHandler;
pub use keys::TrustyKeys;
pub use monotonic_clock::TrustyMonotonicClock;
pub use rng::TrustyRng;
pub use rpc::TrustyRpc;
pub use secure_deletion_secret_manager::{SharedSddManager, TrustySecureDeletionSecretManager};
pub use secure_storage_manager::{AttestationIds, CertSignInfo};

/// Implementation of bootloader status indication for Trusty.
// TODO: maintain the bootloader status and update it as the bootloader informs
// Trusty when it is done.
pub struct TrustyBootLoaderStatus;
impl kmr_ta::device::BootloaderStatus for TrustyBootLoaderStatus {}

#[cfg(test)]
mod tests {
    test::init!();
}
