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

use crate::bluetooth::advertise_settings as ble_advertise_settings;
use crate::captures::captures_handler::clear_pcap_files;
use crate::config::{set_dev, set_pcap};
use crate::echip;
use crate::ffi::ffi_transport::{run_grpc_server_cxx, GrpcServer};
use crate::http_server::server::run_http_server;
use crate::transport::socket::run_socket_transport;
use cxx::UniquePtr;
use log::{error, info, warn};
use netsim_common::util::ini_file::IniFile;
use netsim_common::util::os_utils::get_netsim_ini_filepath;
use netsim_common::util::zip_artifact::remove_zip_files;
use std::env;
use std::time::Duration;

/// Module to control startup, run, and cleanup netsimd services.

pub struct ServiceParams {
    fd_startup_str: String,
    no_cli_ui: bool,
    no_web_ui: bool,
    pcap: bool,
    hci_port: u16,
    instance_num: u16,
    dev: bool,
    vsock: u16,
}

impl ServiceParams {
    #[allow(clippy::too_many_arguments)]
    pub fn new(
        fd_startup_str: String,
        no_cli_ui: bool,
        no_web_ui: bool,
        pcap: bool,
        hci_port: u16,
        instance_num: u16,
        dev: bool,
        vsock: u16,
    ) -> Self {
        ServiceParams {
            fd_startup_str,
            no_cli_ui,
            no_web_ui,
            pcap,
            hci_port,
            instance_num,
            dev,
            vsock,
        }
    }
}

pub struct Service {
    // netsimd states, like device resource.
    service_params: ServiceParams,
    // grpc server
    grpc_server: UniquePtr<GrpcServer>,
}

impl Service {
    /// # Safety
    ///
    /// The file descriptors in `service_params.fd_startup_str` must be valid and open, and must
    /// remain so for as long as the `Service` exists.
    pub unsafe fn new(service_params: ServiceParams) -> Service {
        Service { service_params, grpc_server: UniquePtr::null() }
    }

    /// Sets up the states for netsimd.
    pub fn set_up(&self) {
        // Clear all zip files
        match remove_zip_files() {
            Ok(()) => info!("netsim generated zip files in temp directory has been removed."),
            Err(err) => error!("{err:?}"),
        }

        // Clear all pcap files
        if clear_pcap_files() {
            info!("netsim generated pcap files in temp directory has been removed.");
        }

        set_pcap(self.service_params.pcap);
        set_dev(self.service_params.dev);
    }

    /// Runs netsim gRPC server
    fn run_grpc_server(&self) -> Option<UniquePtr<GrpcServer>> {
        // Environment variable "NETSIM_GRPC_PORT" is set in google3 forge jobs.
        // If set, use the fixed port for grpc server.
        let netsim_grpc_port =
            env::var("NETSIM_GRPC_PORT").map(|val| val.parse::<u32>().unwrap_or(0)).unwrap_or(0);
        let grpc_server = run_grpc_server_cxx(
            netsim_grpc_port,
            self.service_params.no_cli_ui,
            self.service_params.vsock,
        );
        match grpc_server.is_null() {
            true => None,
            false => Some(grpc_server),
        }
    }

    /// Runs netsim web server
    fn run_web_server(&self) -> Option<u16> {
        // Environment variable "NETSIM_GRPC_PORT" is set in google3 forge jobs.
        // If set, don't start http server.
        let forge_job = env::var("NETSIM_GRPC_PORT").is_ok();
        match !forge_job && !self.service_params.no_web_ui {
            true => Some(run_http_server(self.service_params.instance_num)),
            false => None,
        }
    }

    /// Write ports to netsim.ini file
    fn write_ports_to_ini(&self, grpc_port: u32, web_port: Option<u16>) {
        let filepath = get_netsim_ini_filepath(self.service_params.instance_num);
        let mut ini_file = IniFile::new(filepath);
        if let Some(num) = web_port {
            ini_file.insert("web.port", &num.to_string());
        }
        ini_file.insert("grpc.port", &grpc_port.to_string());
        if let Err(err) = ini_file.write() {
            error!("{err:?}");
        }
    }

    /// Runs the netsimd services.
    #[allow(unused_unsafe)]
    pub fn run(&mut self) {
        if !self.service_params.fd_startup_str.is_empty() {
            // SAFETY: When the `Service` was constructed by `Service::new` the caller guaranteed
            // that the file descriptors in `service_params.fd_startup_str` would remain valid and
            // open.
            unsafe {
                use crate::transport::fd::run_fd_transport;
                run_fd_transport(&self.service_params.fd_startup_str);
            }
        }

        // Run netsim gRPC server
        self.grpc_server = match self.run_grpc_server() {
            Some(server) => server,
            None => {
                error!("Failed to run netsimd because unable to start grpc server");
                return;
            }
        };

        // Run frontend web server
        let web_port = self.run_web_server();

        // Write the port numbers to ini file
        self.write_ports_to_ini(self.grpc_server.get_grpc_port(), web_port);

        // Run the socket server.
        run_socket_transport(self.service_params.hci_port);
    }

    /// Shut down the netsimd services
    pub fn shut_down(&self) {
        // TODO: shutdown other services in Rust
        if !self.grpc_server.is_null() {
            self.grpc_server.shut_down();
        }
        echip::bluetooth::bluetooth_stop();
        echip::wifi::wifi_stop();
    }
}

/// Constructing test beacons for dev mode
pub fn new_test_beacon(idx: u32, interval: u64) {
    use crate::devices::devices_handler::create_device;
    use netsim_proto::common::ChipKind;
    use netsim_proto::frontend::CreateDeviceRequest;
    use netsim_proto::model::chip::ble_beacon::{
        AdvertiseData as AdvertiseDataProto, AdvertiseSettings as AdvertiseSettingsProto,
    };
    use netsim_proto::model::chip_create::{
        BleBeaconCreate as BleBeaconCreateProto, Chip as ChipProto,
    };
    use netsim_proto::model::ChipCreate as ChipCreateProto;
    use netsim_proto::model::DeviceCreate as DeviceCreateProto;
    use protobuf::MessageField;
    use protobuf_json_mapping::print_to_string;

    let beacon_proto = BleBeaconCreateProto {
        address: format!("be:ac:01:be:ef:{:02x}", idx),
        settings: MessageField::some(AdvertiseSettingsProto {
            interval: Some(
                ble_advertise_settings::AdvertiseMode::new(Duration::from_millis(interval))
                    .try_into()
                    .unwrap(),
            ),
            scannable: true,
            ..Default::default()
        }),
        adv_data: MessageField::some(AdvertiseDataProto {
            include_device_name: true,
            ..Default::default()
        }),
        scan_response: MessageField::some(AdvertiseDataProto {
            manufacturer_data: vec![1u8, 2, 3, 4],
            ..Default::default()
        }),
        ..Default::default()
    };

    let chip_proto = ChipCreateProto {
        name: format!("gDevice-bt-beacon-chip-{idx}"),
        kind: ChipKind::BLUETOOTH_BEACON.into(),
        chip: Some(ChipProto::BleBeacon(beacon_proto)),
        ..Default::default()
    };

    let device_proto = DeviceCreateProto {
        name: format!("gDevice-beacon-{idx}"),
        chips: vec![chip_proto],
        ..Default::default()
    };

    let request =
        CreateDeviceRequest { device: MessageField::some(device_proto), ..Default::default() };

    if let Err(err) = create_device(&print_to_string(&request).unwrap()) {
        warn!("Failed to create beacon device {idx}: {err}");
    }
}
