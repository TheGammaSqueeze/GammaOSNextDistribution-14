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

use std::sync::{Arc, Mutex};
use std::{collections::HashMap, io::Cursor, net::TcpStream};

use http::Request;
use log::{error, info, warn};
use netsim_proto::common::ChipKind;
use tungstenite::{protocol::Role, Message, WebSocket};

use crate::devices::chip;
use crate::devices::devices_handler::{add_chip, remove_chip};
use crate::echip;
use crate::echip::packet::{register_transport, unregister_transport, Response};
use crate::http_server::server_response::ResponseWritable;

use super::h4;

// This feature is enabled only for CMake builds
#[cfg(feature = "local_ssl")]
use crate::openssl;

/// Generate Sec-Websocket-Accept value from given Sec-Websocket-Key value
fn generate_websocket_accept(websocket_key: String) -> String {
    let concat = websocket_key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    let hashed = openssl::sha::sha1(concat.as_bytes());
    data_encoding::BASE64.encode(&hashed)
}

/// Handler for websocket server connection
pub fn handle_websocket(request: &Request<Vec<u8>>, param: &str, writer: ResponseWritable) {
    if param != "bt" {
        writer.put_error(404, "netsim websocket currently supports bt, uri=/v1/websocket/bt");
    }
    let websocket_accept = match request.headers().get("Sec-Websocket-Key") {
        Some(key) => match key.to_str() {
            Ok(key_str) => generate_websocket_accept(key_str.to_string()),
            Err(_) => {
                writer.put_error(
                    404,
                    "The HeaderValue of Sec-Websocket-Key cannot be converted to str",
                );
                return;
            }
        },
        None => {
            writer.put_error(404, "Missing Sec-Websocket-Key in header");
            return;
        }
    };
    writer.put_ok_switch_protocol(
        "websocket",
        vec![("Sec-WebSocket-Accept".to_string(), websocket_accept)],
    )
}

struct WebSocketTransport {
    websocket_writer: Arc<Mutex<WebSocket<TcpStream>>>,
}

impl Response for WebSocketTransport {
    fn response(&mut self, packet: Vec<u8>, packet_type: u8) {
        let mut buffer = Vec::new();
        buffer.push(packet_type);
        buffer.extend(packet);
        if let Err(err) = self
            .websocket_writer
            .lock()
            .expect("Failed to acquire lock on WebSocket")
            .send(Message::Binary(buffer))
        {
            error!("{err}");
        };
    }
}

/// Run websocket transport for packet flow in netsim
pub fn run_websocket_transport(stream: TcpStream, queries: HashMap<&str, &str>) {
    let chip_create_params = chip::CreateParams {
        kind: ChipKind::BLUETOOTH,
        address: queries.get("address").unwrap_or(&"").to_string(),
        name: Some(format!("websocket-{}", stream.peer_addr().unwrap())),
        manufacturer: "Google".to_string(),
        product_name: "Google".to_string(),
        bt_properties: None,
    };
    #[cfg(not(test))]
    let echip_create_params = echip::CreateParam::Bluetooth(echip::bluetooth::CreateParams {
        address: chip_create_params.address.clone(),
        bt_properties: None,
    });
    #[cfg(test)]
    let echip_create_params =
        echip::CreateParam::Mock(echip::mocked::CreateParams { chip_kind: ChipKind::BLUETOOTH });
    // Add Chip
    let result = match add_chip(
        &stream.peer_addr().unwrap().port().to_string(),
        queries
            .get("name")
            .unwrap_or(&format!("websocket-device-{}", stream.peer_addr().unwrap()).as_str()),
        &chip_create_params,
        &echip_create_params,
    ) {
        Ok(chip_result) => chip_result,
        Err(err) => {
            warn!("{err}");
            return;
        }
    };

    // Create websocket_writer to handle packet responses, write pong or close messages
    let websocket_writer = Arc::new(Mutex::new(WebSocket::from_raw_socket(
        stream.try_clone().unwrap(),
        Role::Server,
        None,
    )));
    // Websocket reader
    let mut websocket_reader = WebSocket::from_raw_socket(stream, Role::Server, None);

    // Sending cloned websocket into packet dispatcher
    register_transport(
        result.chip_id,
        Box::new(WebSocketTransport { websocket_writer: websocket_writer.clone() }),
    );

    // Running Websocket server
    loop {
        let packet_msg =
            match websocket_reader.read().map_err(|_| "Failed to read Websocket message") {
                Ok(message) => message,
                Err(err) => {
                    error!("{err}");
                    break;
                }
            };
        if packet_msg.is_binary() {
            let mut cursor = Cursor::new(packet_msg.into_data());
            match h4::read_h4_packet(&mut cursor) {
                Ok(mut packet) => {
                    echip::handle_request(result.chip_id, &mut packet.payload, packet.h4_type);
                }
                Err(error) => {
                    error!(
                        "netsimd: end websocket reader {}: {:?}",
                        websocket_reader.get_ref().peer_addr().unwrap(),
                        error
                    );
                    break;
                }
            }
        } else if packet_msg.is_ping() {
            if let Err(err) = websocket_writer
                .lock()
                .expect("Failed to acquire lock on WebSocket")
                .send(Message::Pong(packet_msg.into_data()))
            {
                error!("{err}");
            }
        } else if packet_msg.is_close() {
            if let Message::Close(close_frame) = packet_msg {
                if let Err(err) = websocket_writer
                    .lock()
                    .expect("Failed to acquire lock on WebSocket")
                    .close(close_frame)
                    .map_err(|_| "Failed to close Websocket")
                {
                    error!("{err}");
                }
            }
            break;
        }
    }

    // unregister before remove_chip because facade may re-use facade_id
    // on an intertwining create_chip and the unregister here might remove
    // the recently added chip creating a disconnected transport.
    unregister_transport(result.chip_id);

    if let Err(err) = remove_chip(result.device_id, result.chip_id) {
        warn!("{err}");
    };
    info!("Removed chip: device_id: {}, chip_id: {}", result.device_id, result.chip_id);
}
