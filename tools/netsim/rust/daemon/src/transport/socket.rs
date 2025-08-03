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

use super::h4::PacketError;
use crate::devices::chip::{self, ChipIdentifier};
use crate::devices::devices_handler::{add_chip, remove_chip};
use crate::echip;
use crate::echip::packet::{register_transport, unregister_transport, Response};
use crate::transport::h4;
use log::{error, info, warn};
use netsim_proto::common::ChipKind;
use std::io::{ErrorKind, Write};
use std::net::{Ipv4Addr, SocketAddrV4, TcpListener, TcpStream};
use std::thread;

// The HCI server implements the Bluetooth UART transport protocol
// (a.k.a. H4) over TCP. Each new connection on the HCI port spawns a
// new virtual controller associated with a new device.

/// Start the socket-based transport.
///
/// The socket transport reads/writes host-controller messages
/// for bluetooth (h4 hci) over a [TcpStream] transport.
///

struct SocketTransport {
    stream: TcpStream,
}

impl Response for SocketTransport {
    fn response(&mut self, packet: Vec<u8>, packet_type: u8) {
        let mut buffer = Vec::new();
        buffer.push(packet_type);
        buffer.extend(packet);
        if let Err(e) = self.stream.write_all(&buffer[..]) {
            error!("error writing {}", e);
        };
    }
}

pub fn run_socket_transport(hci_port: u16) {
    thread::Builder::new()
        .name("hci_transport".to_string())
        .spawn(move || {
            accept_incoming(hci_port)
                .unwrap_or_else(|e| error!("Failed to accept incoming stream: {:?}", e));
        })
        .unwrap();
}

fn accept_incoming(hci_port: u16) -> std::io::Result<()> {
    let hci_socket = SocketAddrV4::new(Ipv4Addr::UNSPECIFIED, hci_port);
    let listener = TcpListener::bind(hci_socket)?;
    info!("Hci socket server is listening on: {}", hci_port);

    for stream in listener.incoming() {
        let stream = stream?;
        // the socket address of the remote peer of this TCP connection
        info!("Hci client address: {}", stream.peer_addr().unwrap());
        thread::Builder::new()
            .name("hci_transport client".to_string())
            .spawn(move || {
                handle_hci_client(stream);
            })
            .unwrap();
    }
    Ok(())
}

fn handle_hci_client(stream: TcpStream) {
    // ...
    let chip_create_params = chip::CreateParams {
        kind: ChipKind::BLUETOOTH,
        address: String::new(),
        name: Some(format!("socket-{}", stream.peer_addr().unwrap())),
        manufacturer: "Google".to_string(),
        product_name: "Google".to_string(),
        bt_properties: None,
    };
    #[cfg(not(test))]
    let echip_create_params = echip::CreateParam::Bluetooth(echip::bluetooth::CreateParams {
        address: String::new(),
        bt_properties: None,
    });
    #[cfg(test)]
    let echip_create_params =
        echip::CreateParam::Mock(echip::mocked::CreateParams { chip_kind: ChipKind::BLUETOOTH });
    let result = match add_chip(
        &stream.peer_addr().unwrap().port().to_string(),
        &format!("socket-{}", stream.peer_addr().unwrap()),
        &chip_create_params,
        &echip_create_params,
    ) {
        Ok(chip_result) => chip_result,
        Err(err) => {
            warn!("{err}");
            return;
        }
    };
    let tcp_rx = stream.try_clone().unwrap();
    register_transport(result.chip_id, Box::new(SocketTransport { stream }));

    let _ = reader(tcp_rx, ChipKind::BLUETOOTH, result.chip_id);

    // unregister before remove_chip because facade may re-use facade_id
    // on an intertwining create_chip and the unregister here might remove
    // the recently added chip creating a disconnected transport.
    unregister_transport(result.chip_id);

    if let Err(err) = remove_chip(result.device_id, result.chip_id) {
        warn!("{err}");
    };
    info!("Removed chip: device_id: {} chip_id: {}.", result.device_id, result.chip_id);
}

/// read from the socket and pass to the packet hub.
///
fn reader(mut tcp_rx: TcpStream, kind: ChipKind, chip_id: ChipIdentifier) -> std::io::Result<()> {
    loop {
        if let ChipKind::BLUETOOTH = kind {
            match h4::read_h4_packet(&mut tcp_rx) {
                Ok(mut packet) => {
                    echip::handle_request(chip_id, &mut packet.payload, packet.h4_type);
                }
                Err(PacketError::IoError(e)) if e.kind() == ErrorKind::UnexpectedEof => {
                    info!("End socket reader connection with {}.", &tcp_rx.peer_addr().unwrap());
                    return Ok(());
                }
                Err(e) => {
                    error!("End socket reader connection with {}. Failed to reading hci control packet: {:?}",  &tcp_rx.peer_addr().unwrap(), e);
                    return Ok(());
                }
            }
        }
    }
}
