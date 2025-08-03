// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! A simple example for the usage of the uwb_core library.

use log::debug;

use uwb_core::error::{Error as UwbError, Result as UwbResult};
use uwb_core::service::{
    default_runtime, NopUwbServiceCallback, UwbServiceBuilder, UwbServiceCallbackSendBuilder,
};
use uwb_core::uci::{NopUciHal, NopUciLoggerFactory};

fn main() {
    env_logger::init();

    // The UwbService needs an outlived Tokio Runtime.
    let runtime = default_runtime().unwrap();
    // Initialize the UWB service.
    let service = UwbServiceBuilder::new()
        .runtime_handle(runtime.handle().to_owned())
        .callback_builder(UwbServiceCallbackSendBuilder::new(NopUwbServiceCallback {}))
        .uci_hal(NopUciHal {})
        .uci_logger_factory(NopUciLoggerFactory {})
        .build()
        .unwrap();

    // Call the public methods of UWB service under tokio runtime.
    let result: UwbResult<()> = service.enable();

    // Enumerate the error code for backward-compatibility.
    // WARNING: Modifying or removing the current fields are prohibited in general,
    // unless we could confirm that there is no client using the modified field.
    if let Err(err) = result {
        match err {
            UwbError::BadParameters => {}
            UwbError::MaxSessionsExceeded => {}
            UwbError::MaxRrRetryReached => {}
            UwbError::ProtocolSpecific => {}
            UwbError::RemoteRequest => {}
            UwbError::Timeout => {}
            UwbError::CommandRetry => {}
            UwbError::DuplicatedSessionId => {}
            UwbError::RegulationUwbOff => {}
            UwbError::Unknown => {}

            // UwbError is non_exhaustive so we need to add a wild branch here.
            // With this wild branch, adding a new enum field doesn't break the build.
            _ => debug!("Received unknown error: {:?}", err),
        }
    }
}
