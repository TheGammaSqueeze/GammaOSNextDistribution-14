//! Rootcanal HAL
//! This connects to "rootcanal" which provides a simulated
//! Bluetooth chip as well as a simulated environment.

use crate::hal::internal::{InnerHal, RawHal};
use crate::hal::{Result, H4_HEADER_SIZE};
use bt_packets::hci::{Acl, Command, Event, Iso, Sco};
use bytes::{BufMut, Bytes, BytesMut};
use gddi::{module, provides, Stoppable};
use num_derive::{FromPrimitive, ToPrimitive};
use pdl_runtime::Packet;
use std::net::{IpAddr, SocketAddr};
use std::str::FromStr;
use std::sync::Arc;
use tokio::io::{AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpStream;
use tokio::runtime::Runtime;
use tokio::select;
use tokio::sync::mpsc::{UnboundedReceiver, UnboundedSender};

#[derive(FromPrimitive, ToPrimitive)]
enum HciPacketType {
    Command = 0x01,
    Acl = 0x02,
    Sco = 0x03,
    Event = 0x04,
    Iso = 0x05,
}

const SIZE_OF_EVENT_HEADER: usize = 2;
const SIZE_OF_SCO_HEADER: usize = 3;
const SIZE_OF_ACL_HEADER: usize = 4;
const SIZE_OF_ISO_HEADER: usize = 4;

module! {
    rootcanal_hal_module,
    providers {
        RawHal => provide_rootcanal_hal,
    }
}

#[provides]
async fn provide_rootcanal_hal(config: RootcanalConfig, rt: Arc<Runtime>) -> RawHal {
    let (raw_hal, inner_hal) = InnerHal::new();
    let (reader, writer) = TcpStream::connect(&config.to_socket_addr().unwrap())
        .await
        .expect("unable to create stream to rootcanal")
        .into_split();

    rt.spawn(dispatch_incoming(
        inner_hal.evt_tx,
        inner_hal.acl_tx,
        inner_hal.iso_tx,
        inner_hal.sco_tx,
        reader,
    ));
    rt.spawn(dispatch_outgoing(
        inner_hal.cmd_rx,
        inner_hal.acl_rx,
        inner_hal.iso_rx,
        inner_hal.sco_rx,
        writer,
    ));

    raw_hal
}

/// Rootcanal configuration
#[derive(Clone, Debug, Default, Stoppable)]
pub struct RootcanalConfig {
    address: String,
    port: u16,
}

impl RootcanalConfig {
    /// Create a rootcanal config
    pub fn new(address: &str, port: u16) -> Self {
        Self { address: String::from(address), port }
    }

    fn to_socket_addr(&self) -> Result<SocketAddr> {
        Ok(SocketAddr::new(IpAddr::from_str(&self.address)?, self.port))
    }
}

/// Send HCI events received from the HAL to the HCI layer
async fn dispatch_incoming<R>(
    evt_tx: UnboundedSender<Event>,
    acl_tx: UnboundedSender<Acl>,
    iso_tx: UnboundedSender<Iso>,
    sco_tx: UnboundedSender<Sco>,
    reader: R,
) -> Result<()>
where
    R: AsyncReadExt + Unpin,
{
    let mut reader = BufReader::new(reader);
    loop {
        let mut buffer = BytesMut::with_capacity(1024);
        buffer.resize(H4_HEADER_SIZE, 0);
        reader.read_exact(&mut buffer).await?;
        if buffer[0] == HciPacketType::Event as u8 {
            buffer.resize(SIZE_OF_EVENT_HEADER, 0);
            reader.read_exact(&mut buffer).await?;
            let len: usize = buffer[1].into();
            let mut payload = buffer.split_off(SIZE_OF_EVENT_HEADER);
            payload.resize(len, 0);
            reader.read_exact(&mut payload).await?;
            buffer.unsplit(payload);
            let frozen = buffer.freeze();
            match Event::parse(&frozen) {
                Ok(p) => evt_tx.send(p).unwrap(),
                Err(e) => log::error!("dropping invalid event packet: {}: {:02x}", e, frozen),
            }
        } else if buffer[0] == HciPacketType::Acl as u8 {
            buffer.resize(SIZE_OF_ACL_HEADER, 0);
            reader.read_exact(&mut buffer).await?;
            let len: usize = (buffer[2] as u16 + ((buffer[3] as u16) << 8)).into();
            let mut payload = buffer.split_off(SIZE_OF_ACL_HEADER);
            payload.resize(len, 0);
            reader.read_exact(&mut payload).await?;
            buffer.unsplit(payload);
            let frozen = buffer.freeze();
            match Acl::parse(&frozen) {
                Ok(p) => acl_tx.send(p).unwrap(),
                Err(e) => log::error!("dropping invalid ACL packet: {}: {:02x}", e, frozen),
            }
        } else if buffer[0] == HciPacketType::Iso as u8 {
            buffer.resize(SIZE_OF_ISO_HEADER, 0);
            reader.read_exact(&mut buffer).await?;
            let len: usize = (buffer[2] as u16 + (((buffer[3] & 0x3f) as u16) << 8)).into();
            let mut payload = buffer.split_off(SIZE_OF_ISO_HEADER);
            payload.resize(len, 0);
            reader.read_exact(&mut payload).await?;
            buffer.unsplit(payload);
            let frozen = buffer.freeze();
            match Iso::parse(&frozen) {
                Ok(p) => iso_tx.send(p).unwrap(),
                Err(e) => log::error!("dropping invalid ISO packet: {}: {:02x}", e, frozen),
            }
        } else if buffer[0] == HciPacketType::Sco as u8 {
            buffer.resize(SIZE_OF_SCO_HEADER, 0);
            reader.read_exact(&mut buffer).await?;
            let len: usize = buffer[2].into();
            let mut payload = buffer.split_off(SIZE_OF_SCO_HEADER);
            payload.resize(len, 0);
            reader.read_exact(&mut payload).await?;
            buffer.unsplit(payload);
            let frozen = buffer.freeze();
            match Sco::parse(&frozen) {
                Ok(p) => sco_tx.send(p).unwrap(),
                Err(e) => log::error!("dropping invalid SCO packet: {}: {:02x}", e, frozen),
            }
        }
    }
}

/// Send commands received from the HCI later to rootcanal
async fn dispatch_outgoing<W>(
    mut cmd_rx: UnboundedReceiver<Command>,
    mut acl_rx: UnboundedReceiver<Acl>,
    mut iso_rx: UnboundedReceiver<Iso>,
    mut sco_rx: UnboundedReceiver<Sco>,
    mut writer: W,
) -> Result<()>
where
    W: AsyncWriteExt + Unpin,
{
    loop {
        select! {
            Some(cmd) = cmd_rx.recv() => write_with_type(&mut writer, HciPacketType::Command, cmd.to_bytes()).await?,
            Some(acl) = acl_rx.recv() => write_with_type(&mut writer, HciPacketType::Acl, acl.to_bytes()).await?,
            Some(iso) = iso_rx.recv() => write_with_type(&mut writer, HciPacketType::Iso, iso.to_bytes()).await?,
            Some(sco) = sco_rx.recv() => write_with_type(&mut writer, HciPacketType::Sco, sco.to_bytes()).await?,
            else => break,
        }
    }

    Ok(())
}

async fn write_with_type<W>(writer: &mut W, t: HciPacketType, b: Bytes) -> Result<()>
where
    W: AsyncWriteExt + Unpin,
{
    let mut data = BytesMut::with_capacity(b.len() + 1);
    data.put_u8(t as u8);
    data.extend(b);
    writer.write_all(&data[..]).await?;

    Ok(())
}
