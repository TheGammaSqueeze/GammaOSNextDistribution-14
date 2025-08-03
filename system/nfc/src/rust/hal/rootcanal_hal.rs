// Copyright 2021, The Android Open Source Project
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

//! Rootcanal HAL
//! This connects to "rootcanal" which provides a simulated
//! Nfc chip as well as a simulated environment.

use crate::internal::InnerHal;
use crate::{is_control_packet, Hal, HalEvent, HalEventRegistry, HalEventStatus, Result};
use bytes::{BufMut, BytesMut};
use log::{debug, error};
use nfc_packets::nci::{DataPacket, NciPacket};
use pdl_runtime::Packet;
use std::convert::TryInto;
use tokio::io::{AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpStream;
use tokio::select;
use tokio::sync::mpsc::{UnboundedReceiver, UnboundedSender};

/// Initialize the module
pub async fn init() -> Hal {
    let (raw_hal, inner_hal) = InnerHal::new();
    let (reader, writer) = TcpStream::connect("127.0.0.1:7000")
        .await
        .expect("unable to create stream to rootcanal")
        .into_split();

    let reader = BufReader::new(reader);
    tokio::spawn(dispatch_incoming(inner_hal.in_cmd_tx, inner_hal.in_data_tx, reader));
    tokio::spawn(dispatch_outgoing(
        raw_hal.hal_events.clone(),
        inner_hal.out_cmd_rx,
        inner_hal.out_data_rx,
        writer,
    ));

    raw_hal
}

/// Send NCI events received from the HAL to the NCI layer
async fn dispatch_incoming<R>(
    in_cmd_tx: UnboundedSender<NciPacket>,
    in_data_tx: UnboundedSender<DataPacket>,
    mut reader: R,
) -> Result<()>
where
    R: AsyncReadExt + Unpin,
{
    loop {
        let mut buffer = BytesMut::with_capacity(1024);
        let len: usize = reader.read_u16().await?.into();
        buffer.resize(len, 0);
        reader.read_exact(&mut buffer).await?;
        let frozen = buffer.freeze();
        debug!("{:?}", &frozen);
        if is_control_packet(&frozen[..]) {
            match NciPacket::parse(&frozen) {
                Ok(p) => {
                    if in_cmd_tx.send(p).is_err() {
                        break;
                    }
                }
                Err(e) => error!("dropping invalid cmd event packet: {}: {:02x}", e, frozen),
            }
        } else {
            match DataPacket::parse(&frozen) {
                Ok(p) => {
                    if in_data_tx.send(p).is_err() {
                        break;
                    }
                }
                Err(e) => error!("dropping invalid data event packet: {}: {:02x}", e, frozen),
            }
        }
    }
    debug!("Dispatch incoming finished.");
    Ok(())
}

/// Send commands received from the NCI later to rootcanal
async fn dispatch_outgoing<W>(
    mut hal_events: HalEventRegistry,
    mut out_cmd_rx: UnboundedReceiver<NciPacket>,
    mut out_data_rx: UnboundedReceiver<DataPacket>,
    mut writer: W,
) -> Result<()>
where
    W: AsyncWriteExt + Unpin,
{
    loop {
        select! {
            Some(cmd) = out_cmd_rx.recv() => write_nci(&mut writer, cmd).await?,
            Some(data) = out_data_rx.recv() => write_nci(&mut writer, data).await?,
            else => break,
        }
    }

    writer.shutdown().await?;
    if let Some(evt) = hal_events.unregister(HalEvent::CloseComplete).await {
        evt.send(HalEventStatus::Success).unwrap();
    }
    debug!("Dispatch outgoing finished.");
    Ok(())
}

async fn write_nci<W, P>(writer: &mut W, cmd: P) -> Result<()>
where
    W: AsyncWriteExt + Unpin,
    P: Packet,
{
    let b = cmd.to_bytes();
    let mut data = BytesMut::with_capacity(b.len() + 2);
    data.put_u16(b.len().try_into().unwrap());
    data.extend(b);
    writer.write_all(&data[..]).await?;
    debug!("Sent {:?}", data);
    Ok(())
}
