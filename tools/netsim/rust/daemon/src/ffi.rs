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

//! Netsim daemon cxx libraries.

use std::pin::Pin;

use crate::bluetooth::chip::{
    create_add_rust_device_result, AddRustDeviceResult, RustBluetoothChipCallbacks,
};
use crate::http_server::server_response::ServerResponseWritable;
use crate::http_server::server_response::StrHeaders;
use cxx::let_cxx_string;

use crate::echip::{bluetooth::report_invalid_packet_cxx, handle_request_cxx, handle_response};
use crate::transport::grpc::{register_grpc_transport, unregister_grpc_transport};

use crate::captures::captures_handler::handle_capture_cxx;
use crate::devices::devices_handler::{
    add_chip_cxx, get_distance_cxx, handle_device_cxx, remove_chip_cxx, AddChipResultCxx,
};
use crate::ranging::*;
use crate::version::*;

#[allow(unsafe_op_in_unsafe_fn)]
#[cxx::bridge(namespace = "netsim::echip")]
pub mod ffi_echip {
    extern "Rust" {
        #[cxx_name = HandleRequestCxx]
        fn handle_request_cxx(chip_id: u32, packet: &CxxVector<u8>, packet_type: u8);

        #[cxx_name = HandleResponse]
        fn handle_response(chip_id: u32, packet: &CxxVector<u8>, packet_type: u8);
    }
}

#[allow(unsafe_op_in_unsafe_fn)]
#[cxx::bridge(namespace = "netsim::transport")]
pub mod ffi_transport {
    extern "Rust" {
        #[cxx_name = RegisterGrpcTransport]
        fn register_grpc_transport(chip_id: u32);

        #[cxx_name = UnregisterGrpcTransport]
        fn unregister_grpc_transport(chip_id: u32);
    }

    unsafe extern "C++" {
        // Grpc server.
        include!("backend/backend_packet_hub.h");

        #[rust_name = handle_grpc_response]
        #[namespace = "netsim::backend"]
        fn HandleResponseCxx(chip_id: u32, packet: &Vec<u8>, packet_type: u8);

        include!("core/server.h");

        #[namespace = "netsim::server"]
        type GrpcServer;
        #[rust_name = shut_down]
        #[namespace = "netsim::server"]
        fn Shutdown(self: &GrpcServer);

        #[rust_name = get_grpc_port]
        #[namespace = "netsim::server"]
        fn GetGrpcPort(self: &GrpcServer) -> u32;

        #[rust_name = run_grpc_server_cxx]
        #[namespace = "netsim::server"]
        pub fn RunGrpcServerCxx(
            netsim_grpc_port: u32,
            no_cli_ui: bool,
            vsock: u16,
        ) -> UniquePtr<GrpcServer>;

        // Grpc client.
        // Expose functions in Cuttlefish only, because it's only used by CVDs and it's
        // unable to pass function pointers on Windows.
        #[cfg(feature = "cuttlefish")]
        include!("backend/grpc_client.h");

        #[allow(dead_code)]
        #[rust_name = stream_packets]
        #[namespace = "netsim::backend::client"]
        #[cfg(feature = "cuttlefish")]
        fn StreamPackets(server: &String) -> u32;

        #[allow(dead_code)]
        #[rust_name = read_packet_response_loop]
        #[namespace = "netsim::backend::client"]
        #[cfg(feature = "cuttlefish")]
        fn ReadPacketResponseLoop(
            stream_id: u32,
            read_fn: fn(stream_id: u32, proto_bytes: &[u8]),
        ) -> bool;

        #[allow(dead_code)]
        #[rust_name = write_packet_request]
        #[cfg(feature = "cuttlefish")]
        #[namespace = "netsim::backend::client"]
        fn WritePacketRequest(stream_id: u32, proto_bytes: &[u8]) -> bool;

    }
}

#[allow(unsafe_op_in_unsafe_fn)]
#[cxx::bridge(namespace = "netsim")]
pub mod ffi_bluetooth {
    extern "Rust" {
        // Rust Bluetooth device.
        #[namespace = "netsim::hci::facade"]
        type DynRustBluetoothChipCallbacks;

        #[cxx_name = Tick]
        #[namespace = "netsim::hci::facade"]
        fn tick(dyn_callbacks: &mut DynRustBluetoothChipCallbacks);

        #[cxx_name = ReceiveLinkLayerPacket]
        #[namespace = "netsim::hci::facade"]
        fn receive_link_layer_packet(
            dyn_callbacks: &mut DynRustBluetoothChipCallbacks,
            source_address: String,
            destination_address: String,
            packet_type: u8,
            packet: &[u8],
        );

        // Bluetooth facade.
        #[namespace = "netsim::hci::facade"]
        type AddRustDeviceResult;
        #[cxx_name = "CreateAddRustDeviceResult"]
        #[namespace = "netsim::hci"]
        fn create_add_rust_device_result(
            facade_id: u32,
            rust_chip: UniquePtr<RustBluetoothChip>,
        ) -> Box<AddRustDeviceResult>;

        // Rust Invalid Packet Report
        #[cxx_name = "ReportInvalidPacket"]
        #[namespace = "netsim::hci::facade"]
        fn report_invalid_packet_cxx(
            rootcanal_id: u32,
            reason: i32,
            description: &CxxString,
            packet: &CxxVector<u8>,
        );
    }

    #[allow(dead_code)]
    unsafe extern "C++" {
        // Bluetooth facade.
        include!("hci/hci_packet_hub.h");

        #[rust_name = handle_bt_request]
        #[namespace = "netsim::hci"]
        fn HandleBtRequestCxx(rootcanal_id: u32, packet_type: u8, packet: &Vec<u8>);

        // Rust Bluetooth device.
        include!("hci/rust_device.h");

        #[namespace = "netsim::hci::facade"]
        type RustBluetoothChip;
        #[rust_name = send_link_layer_le_packet]
        #[namespace = "netsim::hci::facade"]
        fn SendLinkLayerLePacket(self: &RustBluetoothChip, packet: &[u8], tx_power: i8);

        include!("hci/bluetooth_facade.h");

        #[rust_name = bluetooth_get_cxx]
        #[namespace = "netsim::hci::facade"]
        pub fn GetCxx(rootcanal_id: u32) -> Vec<u8>;

        #[rust_name = bluetooth_reset]
        #[namespace = "netsim::hci::facade"]
        pub fn Reset(rootcanal_id: u32);

        #[rust_name = bluetooth_remove]
        #[namespace = "netsim::hci::facade"]
        pub fn Remove(rootcanal_id: u32);

        #[rust_name = bluetooth_add]
        #[namespace = "netsim::hci::facade"]
        pub fn Add(chip_id: u32, address: &CxxString, controller_proto_bytes: &[u8]) -> u32;

        /*
        From https://cxx.rs/binding/box.html#restrictions,
        ```
        If T is an opaque Rust type, the Rust type is required to be Sized i.e. size known at compile time. In the future we may introduce support for dynamically sized opaque Rust types.
        ```

        The workaround is using Box<dyn MyData> (fat pointer) as the opaque type.
        Reference:
        - Passing trait objects to C++. https://github.com/dtolnay/cxx/issues/665.
        - Exposing trait methods to C++. https://github.com/dtolnay/cxx/issues/667
                */
        #[rust_name = bluetooth_add_rust_device]
        #[namespace = "netsim::hci::facade"]
        pub fn AddRustDevice(
            chip_id: u32,
            callbacks: Box<DynRustBluetoothChipCallbacks>,
            string_type: &CxxString,
            address: &CxxString,
        ) -> Box<AddRustDeviceResult>;

        /// The provided address must be 6 bytes in length
        #[rust_name = bluetooth_set_rust_device_address]
        #[namespace = "netsim::hci::facade"]
        pub fn SetRustDeviceAddress(rootcanal_id: u32, address: [u8; 6]);

        #[rust_name = bluetooth_remove_rust_device]
        #[namespace = "netsim::hci::facade"]
        pub fn RemoveRustDevice(rootcanal_id: u32);

        #[rust_name = bluetooth_start]
        #[namespace = "netsim::hci::facade"]
        pub fn Start(proto_bytes: &[u8], instance_num: u16);

        #[rust_name = bluetooth_stop]
        #[namespace = "netsim::hci::facade"]
        pub fn Stop();

        #[rust_name = add_device_to_phy]
        #[namespace = "netsim::hci::facade"]
        pub fn AddDeviceToPhy(rootcanal_id: u32, is_low_energy: bool);

        #[rust_name = remove_device_from_phy]
        #[namespace = "netsim::hci::facade"]
        pub fn RemoveDeviceFromPhy(rootcanal_id: u32, is_low_energy: bool);
    }
}

#[cxx::bridge(namespace = "netsim::wifi::facade")]
pub mod ffi_wifi {
    #[allow(dead_code)]
    unsafe extern "C++" {
        // WiFi facade.
        include!("wifi/wifi_packet_hub.h");

        #[rust_name = handle_wifi_request]
        #[namespace = "netsim::wifi"]
        fn HandleWifiRequestCxx(chip_id: u32, packet: &Vec<u8>);

        include!("wifi/wifi_facade.h");

        #[rust_name = wifi_patch_cxx]
        pub fn PatchCxx(chip_id: u32, proto_bytes: &[u8]);

        #[rust_name = wifi_get_cxx]
        pub fn GetCxx(chip_id: u32) -> Vec<u8>;

        #[rust_name = wifi_reset]
        pub fn Reset(chip_id: u32);

        #[rust_name = wifi_remove]
        pub fn Remove(chip_id: u32);

        #[rust_name = wifi_add]
        pub fn Add(chip_id: u32);

        #[rust_name = wifi_start]
        pub fn Start(proto_bytes: &[u8]);

        #[rust_name = wifi_stop]
        pub fn Stop();

    }
}

#[allow(unsafe_op_in_unsafe_fn)]
#[cxx::bridge(namespace = "netsim::device")]
pub mod ffi_devices {
    extern "Rust" {

        // Device Resource
        type AddChipResultCxx;
        #[cxx_name = "GetDeviceId"]
        fn get_device_id(self: &AddChipResultCxx) -> u32;
        #[cxx_name = "GetChipId"]
        fn get_chip_id(self: &AddChipResultCxx) -> u32;
        #[cxx_name = "IsError"]
        fn is_error(self: &AddChipResultCxx) -> bool;

        #[allow(clippy::too_many_arguments)]
        #[cxx_name = AddChipCxx]
        fn add_chip_cxx(
            device_guid: &str,
            device_name: &str,
            chip_kind: &CxxString,
            chip_address: &str,
            chip_name: &str,
            chip_manufacturer: &str,
            chip_product_name: &str,
            bt_properties: &CxxVector<u8>,
        ) -> Box<AddChipResultCxx>;

        #[cxx_name = RemoveChipCxx]
        fn remove_chip_cxx(device_id: u32, chip_id: u32);

        #[cxx_name = GetDistanceCxx]
        fn get_distance_cxx(a: u32, b: u32) -> f32;
    }
}

#[allow(unsafe_op_in_unsafe_fn)]
#[cxx::bridge(namespace = "netsim")]
pub mod ffi_response_writable {
    extern "Rust" {
        // handlers for gRPC server's invocation of API calls

        #[cxx_name = "HandleCaptureCxx"]
        fn handle_capture_cxx(
            responder: Pin<&mut CxxServerResponseWriter>,
            method: String,
            param: String,
            body: String,
        );

        #[cxx_name = "HandleDeviceCxx"]
        fn handle_device_cxx(
            responder: Pin<&mut CxxServerResponseWriter>,
            method: String,
            param: String,
            body: String,
        );
    }
    unsafe extern "C++" {
        /// A C++ class which can be used to respond to a request.
        include!("frontend/server_response_writable.h");

        #[namespace = "netsim::frontend"]
        type CxxServerResponseWriter;

        #[namespace = "netsim::frontend"]
        fn put_ok_with_length(self: &CxxServerResponseWriter, mime_type: &CxxString, length: usize);

        #[namespace = "netsim::frontend"]
        fn put_chunk(self: &CxxServerResponseWriter, chunk: &[u8]);

        #[namespace = "netsim::frontend"]
        fn put_ok(self: &CxxServerResponseWriter, mime_type: &CxxString, body: &CxxString);

        #[namespace = "netsim::frontend"]
        fn put_error(self: &CxxServerResponseWriter, error_code: u32, error_message: &CxxString);

    }
}

#[allow(unsafe_op_in_unsafe_fn)]
#[cxx::bridge(namespace = "netsim")]
pub mod ffi_util {
    extern "Rust" {
        // Ranging

        #[cxx_name = "DistanceToRssi"]
        fn distance_to_rssi(tx_power: i8, distance: f32) -> i8;

        // Version

        #[cxx_name = "GetVersion"]
        fn get_version() -> String;
    }

    #[allow(dead_code)]
    unsafe extern "C++" {

        // OS utilities.
        include!("util/os_utils.h");

        #[rust_name = redirect_std_stream]
        #[namespace = "netsim::osutils"]
        pub fn RedirectStdStream(netsim_temp_dir: &CxxString, instance_name: &CxxString);

        // Crash report.
        include!("util/crash_report.h");

        #[rust_name = set_up_crash_report]
        #[namespace = "netsim"]
        pub fn SetUpCrashReport();

        // Frontend client.
        include!("frontend/frontend_client_stub.h");

        #[rust_name = is_netsimd_alive]
        #[namespace = "netsim::frontend"]
        pub fn IsNetsimdAlive(instance_num: u16) -> bool;

    }
}

// It's required so `RustBluetoothChip` can be sent between threads safely.
// Ref: How to use opaque types in threads? https://github.com/dtolnay/cxx/issues/1175
// SAFETY: Nothing in `RustBluetoothChip` depends on being run on a particular thread.
unsafe impl Send for ffi_bluetooth::RustBluetoothChip {}

type DynRustBluetoothChipCallbacks = Box<dyn RustBluetoothChipCallbacks>;

fn tick(dyn_callbacks: &mut DynRustBluetoothChipCallbacks) {
    (**dyn_callbacks).tick();
}

fn receive_link_layer_packet(
    dyn_callbacks: &mut DynRustBluetoothChipCallbacks,
    source_address: String,
    destination_address: String,
    packet_type: u8,
    packet: &[u8],
) {
    (**dyn_callbacks).receive_link_layer_packet(
        source_address,
        destination_address,
        packet_type,
        packet,
    );
}

/// CxxServerResponseWriter is defined in server_response_writable.h
/// Wrapper struct allows the impl to discover the respective C++ methods
pub struct CxxServerResponseWriterWrapper<'a> {
    pub writer: Pin<&'a mut ffi_response_writable::CxxServerResponseWriter>,
}

impl ServerResponseWritable for CxxServerResponseWriterWrapper<'_> {
    fn put_ok_with_length(&mut self, mime_type: &str, length: usize, _headers: StrHeaders) {
        let_cxx_string!(mime_type = mime_type);
        self.writer.put_ok_with_length(&mime_type, length);
    }
    fn put_chunk(&mut self, chunk: &[u8]) {
        self.writer.put_chunk(chunk);
    }
    fn put_ok(&mut self, mime_type: &str, body: &str, _headers: StrHeaders) {
        let_cxx_string!(mime_type = mime_type);
        let_cxx_string!(body = body);
        self.writer.put_ok(&mime_type, &body);
    }
    fn put_error(&mut self, error_code: u16, error_message: &str) {
        let_cxx_string!(error_message = error_message);
        self.writer.put_error(error_code.into(), &error_message);
    }

    fn put_ok_with_vec(&mut self, _mime_type: &str, _body: Vec<u8>, _headers: StrHeaders) {
        todo!()
    }
    fn put_ok_switch_protocol(&mut self, _connection: &str, _headers: StrHeaders) {
        todo!()
    }
}
