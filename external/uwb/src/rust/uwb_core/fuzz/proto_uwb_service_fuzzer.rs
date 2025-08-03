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

#![no_main]

use libfuzzer_sys::{arbitrary::Arbitrary, fuzz_target};
use uwb_core::service::{
    default_runtime, NopUwbServiceCallback, ProtoUwbService, UwbServiceBuilder,
    UwbServiceCallbackSendBuilder,
};
use uwb_core::uci::{NopUciHal, NopUciLoggerFactory};

/// The list of the ProtoUwbService's methods that take the argument.
#[derive(Arbitrary, Debug)]
enum Command {
    SetLoggerMode,
    InitSession,
    DeinitSession,
    StartRanging,
    StopRanging,
    Reconfigure,
    UpdateControllerMulticastList,
    AndroidSetCountryCode,
    SendVendorCmd,
    SessionParams,
}

fuzz_target!(|methods: Vec<(Command, &[u8])>| {
    // Setup the ProtoUwbService.
    let runtime = default_runtime().unwrap();
    let service = UwbServiceBuilder::new()
        .runtime_handle(runtime.handle().to_owned())
        .callback_builder(UwbServiceCallbackSendBuilder::new(NopUwbServiceCallback {}))
        .uci_hal(NopUciHal {})
        .uci_logger_factory(NopUciLoggerFactory {})
        .build()
        .unwrap();
    let mut proto_service = ProtoUwbService::new(service);
    let _ = proto_service.enable();

    // Call the methods of ProtoUwbService that takes the argument.
    for (command, bytes) in methods.into_iter() {
        match command {
            Command::SetLoggerMode => {
                let _ = proto_service.set_logger_mode(bytes);
            }
            Command::InitSession => {
                let _ = proto_service.init_session(bytes);
            }
            Command::DeinitSession => {
                let _ = proto_service.deinit_session(bytes);
            }
            Command::StartRanging => {
                let _ = proto_service.start_ranging(bytes);
            }
            Command::StopRanging => {
                let _ = proto_service.stop_ranging(bytes);
            }
            Command::Reconfigure => {
                let _ = proto_service.reconfigure(bytes);
            }
            Command::UpdateControllerMulticastList => {
                let _ = proto_service.update_controller_multicast_list(bytes);
            }
            Command::AndroidSetCountryCode => {
                let _ = proto_service.android_set_country_code(bytes);
            }
            Command::SendVendorCmd => {
                let _ = proto_service.raw_uci_cmd(bytes);
            }
            Command::SessionParams => {
                let _ = proto_service.session_params(bytes);
            }
        }
    }
});
