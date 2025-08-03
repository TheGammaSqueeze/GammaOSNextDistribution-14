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

/// request packets flow into netsim
/// response packets flow out of netsim
/// packet transports read requests and write response packets over gRPC or Fds.
use super::h4;
use super::h4::PacketError;
use super::uci;
use crate::devices::chip;
use crate::devices::devices_handler::{add_chip, remove_chip};
use crate::echip;
use crate::echip::packet::{register_transport, unregister_transport, Response};
use crate::ffi::ffi_transport;
use lazy_static::lazy_static;
use log::{error, info, warn};
use netsim_proto::common::ChipKind;
use netsim_proto::hci_packet::HCIPacket;
use netsim_proto::packet_streamer::PacketRequest;
use netsim_proto::startup::{Chip as ChipProto, ChipInfo};
use protobuf::{Enum, EnumOrUnknown, Message, MessageField};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs::File;
use std::io::{ErrorKind, Write};
use std::os::fd::FromRawFd;
use std::sync::{Arc, RwLock};
use std::thread::JoinHandle;
use std::{fmt, thread};

#[derive(Serialize, Deserialize, Debug)]
struct StartupInfo {
    devices: Vec<Device>,
}

#[derive(Serialize, Deserialize, Debug)]
struct Device {
    name: String,
    chips: Vec<Chip>,
}

#[derive(Serialize, Deserialize, Debug, Copy, Clone)]
enum ChipKindEnum {
    UNKNOWN = 0,
    BLUETOOTH = 1,
    WIFI = 2,
    UWB = 3,
}

impl fmt::Display for ChipKindEnum {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{:?}", self)
    }
}

// TODO use proto
#[derive(Serialize, Deserialize, Debug)]
struct Chip {
    kind: ChipKindEnum,
    id: Option<String>,
    manufacturer: Option<String>,
    product_name: Option<String>,
    #[serde(rename = "fdIn")]
    fd_in: u32,
    #[serde(rename = "fdOut")]
    fd_out: u32,
    loopback: Option<bool>,
    address: Option<String>,
}

struct FdTransport {
    file: File,
}

impl Response for FdTransport {
    fn response(&mut self, packet: Vec<u8>, packet_type: u8) {
        let mut buffer = Vec::<u8>::with_capacity(packet.len() + 1);
        buffer.push(packet_type);
        buffer.extend(packet);
        if let Err(e) = self.file.write_all(&buffer[..]) {
            error!("netsimd: error writing {}", e);
        }
    }
}

/// read from the raw fd and pass to the packet hub.
///
/// # Safety
///
/// `fd_rx` must be a valid and open file descriptor.
unsafe fn fd_reader(
    fd_rx: i32,
    kind: ChipKindEnum,
    device_id: u32,
    chip_id: u32,
) -> JoinHandle<()> {
    thread::Builder::new()
        .name(format!("fd_reader_{}", fd_rx))
        .spawn(move || {
            // SAFETY: The caller promises that `fd_rx` is valid and open.
            let mut rx = unsafe { File::from_raw_fd(fd_rx) };

            info!("Handling fd={} for kind: {:?} chip_id: {:?}", fd_rx, kind, chip_id);

            loop {
                match kind {
                    ChipKindEnum::UWB => match uci::read_uci_packet(&mut rx) {
                        Err(e) => {
                            error!("End reader connection with fd={}. Failed to reading uci control packet: {:?}", fd_rx, e);
                            break;
                        }
                        Ok(uci::Packet { mut payload }) => {
                            echip::handle_request(chip_id, &mut payload, 0);
                        }
                    },
                    ChipKindEnum::BLUETOOTH => match h4::read_h4_packet(&mut rx) {
                        Ok(h4::Packet { h4_type, mut payload }) => {
                            echip::handle_request(chip_id, &mut payload, h4_type);
                        }
                        Err(PacketError::IoError(e))
                            if e.kind() == ErrorKind::UnexpectedEof =>
                        {
                            info!("End reader connection with fd={}.", fd_rx);
                            break;
                        }
                        Err(e) => {
                            error!("End reader connection with fd={}. Failed to reading hci control packet: {:?}", fd_rx, e);
                            break;
                        }
                    },
                    _ => {
                        error!("unknown control packet chip_kind: {:?}", kind);
                        break;
                    }
                };
            }

            // unregister before remove_chip because facade may re-use facade_id
            // on an intertwining create_chip and the unregister here might remove
            // the recently added chip creating a disconnected transport.
            unregister_transport(chip_id);

            if let Err(err) = remove_chip(device_id, chip_id) {
                warn!("{err}");
            }
        })
        .unwrap()
}

/// start_fd_transport
///
/// Create threads to read and write to file descriptors
///
/// # Safety
///
/// The file descriptors in the JSON must be valid and open.
pub unsafe fn run_fd_transport(startup_json: &String) {
    info!("Running fd transport with {startup_json}");
    let startup_info = match serde_json::from_str::<StartupInfo>(startup_json.as_str()) {
        Err(e) => {
            error!("Error parsing startup info: {:?}", e);
            return;
        }
        Ok(startup_info) => startup_info,
    };
    // See https://tokio.rs/tokio/topics/bridging
    // This code is synchronous hosting asynchronous until main is converted to rust.
    thread::Builder::new()
        .name("fd_transport".to_string())
        .spawn(move || {
            let chip_count = startup_info.devices.iter().map(|d| d.chips.len()).sum();
            let mut handles = Vec::with_capacity(chip_count);
            for (device_guid, device) in startup_info.devices.iter().enumerate() {
                for chip in &device.chips {
                    let chip_kind = match chip.kind {
                        ChipKindEnum::BLUETOOTH => ChipKind::BLUETOOTH,
                        ChipKindEnum::WIFI => ChipKind::WIFI,
                        ChipKindEnum::UWB => ChipKind::UWB,
                        _ => ChipKind::UNSPECIFIED,
                    };
                    // TODO(b/323899010): Avoid having cfg(test) in mainline code
                    #[cfg(not(test))]
                    let echip_create_param = match chip_kind {
                        ChipKind::BLUETOOTH => {
                            echip::CreateParam::Bluetooth(echip::bluetooth::CreateParams {
                                address: chip.address.clone().unwrap_or_default(),
                                bt_properties: None,
                            })
                        }
                        ChipKind::WIFI => echip::CreateParam::Wifi(echip::wifi::CreateParams {}),
                        ChipKind::UWB => echip::CreateParam::Uwb(echip::uwb::CreateParams {
                            address: chip.address.clone().unwrap_or_default(),
                        }),
                        _ => {
                            warn!("The provided chip kind is unsupported: {:?}", chip.kind);
                            return;
                        }
                    };
                    #[cfg(test)]
                    let echip_create_param =
                        echip::CreateParam::Mock(echip::mocked::CreateParams { chip_kind });
                    let chip_create_params = chip::CreateParams {
                        kind: chip_kind,
                        address: chip.address.clone().unwrap_or_default(),
                        name: Some(chip.id.clone().unwrap_or_default()),
                        manufacturer: chip.manufacturer.clone().unwrap_or_default(),
                        product_name: chip.product_name.clone().unwrap_or_default(),
                        bt_properties: None,
                    };
                    let result = match add_chip(
                        &format!("fd-device-{}", device_guid),
                        &device.name.clone(),
                        &chip_create_params,
                        &echip_create_param,
                    ) {
                        Ok(chip_result) => chip_result,
                        Err(err) => {
                            warn!("{err}");
                            return;
                        }
                    };

                    // Cf writes to fd_out and reads from fd_in
                    // SAFETY: Our caller promises that the file descriptors in the JSON are valid
                    // and open.
                    let file_in = unsafe { File::from_raw_fd(chip.fd_in as i32) };

                    register_transport(result.chip_id, Box::new(FdTransport { file: file_in }));
                    // TODO: switch to runtime.spawn once FIFOs are available in Tokio
                    // SAFETY: Our caller promises that the file descriptors in the JSON are valid
                    // and open.
                    handles.push(unsafe {
                        fd_reader(chip.fd_out as i32, chip.kind, result.device_id, result.chip_id)
                    });
                }
            }
            // Wait for all of them to complete.
            for handle in handles {
                // The `spawn` method returns a `JoinHandle`. A `JoinHandle` is
                // a future, so we can wait for it using `block_on`.
                // runtime.block_on(handle).unwrap();
                // TODO: use runtime.block_on once FIFOs are available in Tokio
                handle.join().unwrap();
            }
            info!("done with all fd handlers");
        })
        .unwrap();
}

/// Read from the raw fd and pass to the grpc server.
///
/// # Safety
///
/// `fd_rx` must be a valid and open file descriptor.
unsafe fn connector_fd_reader(fd_rx: i32, kind: ChipKindEnum, stream_id: u32) -> JoinHandle<()> {
    info!("Connecting fd reader for stream_id: {}, fd_rx: {}", stream_id, fd_rx);
    thread::Builder::new()
        .name(format!("fd_connector_{}_{}", stream_id, fd_rx))
        .spawn(move || {
            // SAFETY: The caller promises that `fd_rx` is valid and open.
            let mut rx = unsafe { File::from_raw_fd(fd_rx) };
            info!("Handling fd={} for kind: {:?} stream_id: {:?}", fd_rx, kind, stream_id);

            loop {
                match kind {
                    ChipKindEnum::UWB => match uci::read_uci_packet(&mut rx) {
                        Err(e) => {
                            error!(
                                "End reader connection with fd={}. Failed to read \
                                     uci control packet: {:?}",
                                fd_rx, e
                            );
                            break;
                        }
                        Ok(uci::Packet { payload: _ }) => {
                            // TODO: Compose PacketRequest.
                        }
                    },
                    ChipKindEnum::BLUETOOTH => match h4::read_h4_packet(&mut rx) {
                        Ok(h4::Packet { h4_type, payload }) => {
                            let mut request = PacketRequest::new();
                            let hci_packet = HCIPacket {
                                packet_type: EnumOrUnknown::from_i32(h4_type as i32),
                                packet: payload,
                                ..Default::default()
                            };
                            request.set_hci_packet(hci_packet);
                            let proto_bytes = request.write_to_bytes().unwrap();
                            ffi_transport::write_packet_request(stream_id, &proto_bytes);
                        }
                        Err(PacketError::IoError(e)) if e.kind() == ErrorKind::UnexpectedEof => {
                            info!("End reader connection with fd={}.", fd_rx);
                            break;
                        }
                        Err(e) => {
                            error!(
                                "End reader connection with fd={}. Failed to read \
                                     hci control packet: {:?}",
                                fd_rx, e
                            );
                            break;
                        }
                    },
                    _ => {
                        error!("unknown control packet chip_kind: {:?}", kind);
                        break;
                    }
                };
            }
        })
        .unwrap()
}

// For connector.
lazy_static! {
    static ref CONNECTOR_FILES: Arc<RwLock<HashMap<u32, File>>> =
        Arc::new(RwLock::new(HashMap::new()));
}

/// This function is called when a packet is received from the gRPC server.
fn connector_grpc_read_callback(stream_id: u32, proto_bytes: &[u8]) {
    let request = PacketRequest::parse_from_bytes(proto_bytes).unwrap();

    let mut buffer = Vec::<u8>::with_capacity(request.hci_packet().packet.len() + 1);
    buffer.push(request.hci_packet().packet_type.enum_value_or_default().value() as u8);
    buffer.extend(&request.hci_packet().packet);

    if let Some(mut file_in) = CONNECTOR_FILES.read().unwrap().get(&stream_id) {
        if let Err(e) = file_in.write_all(&buffer[..]) {
            error!("Failed to write: {}", e);
        }
    } else {
        warn!("Unable to find file with stream_id {}", stream_id);
    }
}

/// Read from grpc server and write back to file descriptor.
fn connector_grpc_reader(chip_kind: u32, stream_id: u32, file_in: File) -> JoinHandle<()> {
    info!("Connecting grpc reader for stream_id: {}", stream_id);
    thread::Builder::new()
        .name(format!("grpc_reader_{}", stream_id))
        .spawn(move || {
            {
                let mut binding = CONNECTOR_FILES.write().unwrap();
                if binding.contains_key(&stream_id) {
                    error!(
                        "register_connector: key already present for \
                                 stream_id: {stream_id}"
                    );
                }
                binding.insert(stream_id, file_in);
            }
            if chip_kind != ChipKindEnum::BLUETOOTH as u32 {
                warn!("Unable to register connector for chip type {}", chip_kind);
            }
            // Read packet from grpc and send to file_in.
            ffi_transport::read_packet_response_loop(stream_id, connector_grpc_read_callback);

            CONNECTOR_FILES.write().unwrap().remove(&stream_id);
        })
        .unwrap()
}

/// Create threads to forward file descriptors to another netsim daemon.
pub fn run_fd_connector(startup_json: &String, server: &str) -> Result<(), String> {
    info!("Running fd connector with {startup_json}");
    let startup_info = match serde_json::from_str::<StartupInfo>(startup_json.as_str()) {
        Ok(startup_info) => startup_info,
        Err(e) => {
            return Err(format!("Error parsing startup info: {:?}", e.to_string()));
        }
    };
    let server = server.to_owned();

    let chip_count = startup_info.devices.iter().map(|d| d.chips.len()).sum();
    let mut handles = Vec::with_capacity(chip_count);

    for device in startup_info.devices {
        for chip in device.chips {
            // Cf writes to fd_out and reads from fd_in
            // SAFETY: Our caller promises that the file descriptors in the JSON are valid
            // and open.
            let file_in = unsafe { File::from_raw_fd(chip.fd_in as i32) };

            let stream_id = ffi_transport::stream_packets(&server);
            // Send out initial info of PacketRequest to grpc server.
            let mut initial_request = PacketRequest::new();
            initial_request.set_initial_info(ChipInfo {
                name: device.name.clone(),
                chip: MessageField::some(ChipProto {
                    kind: EnumOrUnknown::from_i32(chip.kind as i32),
                    id: chip.id.unwrap_or_default(),
                    manufacturer: chip.manufacturer.unwrap_or_default(),
                    product_name: chip.product_name.unwrap_or_default(),
                    address: chip.address.unwrap_or_default(),
                    ..Default::default()
                }),
                ..Default::default()
            });
            ffi_transport::write_packet_request(
                stream_id,
                &initial_request.write_to_bytes().unwrap(),
            );
            info!("Sent initial request to grpc for stream_id: {}", stream_id);

            handles.push(connector_grpc_reader(chip.kind as u32, stream_id, file_in));

            // TODO: switch to runtime.spawn once FIFOs are available in Tokio
            // SAFETY: Our caller promises that the file descriptors in the JSON are valid
            // and open.
            handles.push(unsafe { connector_fd_reader(chip.fd_out as i32, chip.kind, stream_id) });
        }
    }
    // Wait for all of them to complete.
    for handle in handles {
        // The `spawn` method returns a `JoinHandle`. A `JoinHandle` is
        // a future, so we can wait for it using `block_on`.
        // runtime.block_on(handle).unwrap();
        // TODO: use runtime.block_on once FIFOs are available in Tokio
        handle.join().unwrap();
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::StartupInfo;
    use log::info;

    #[test]
    fn test_serde() {
        let s = r#"
    {"devices": [
       {"name": "emulator-5554",
        "chips": [{"kind": "WIFI", "fdIn": 1, "fdOut": 2},
                  {"kind": "BLUETOOTH", "fdIn": 20, "fdOut":21}]
       },
       {"name": "emulator-5555",
        "chips": [{"kind": "BLUETOOTH", "fdIn": 3, "fdOut": 4},
                {"kind": "UWB", "fdIn": 5, "fdOut": 6, "model": "DW300"}]
       }
     ]
    }"#;
        let startup_info = serde_json::from_str::<StartupInfo>(s).unwrap();
        for device in startup_info.devices {
            info!("device: {:?}", device);
        }
    }
}
