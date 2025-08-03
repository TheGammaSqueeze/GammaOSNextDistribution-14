//! Handles fragmentation & reassembly of ACL packets into whole L2CAP payloads

use bt_common::Bluetooth;
use bt_packets::hci::PacketBoundaryFlag::{
    ContinuingFragment, FirstAutomaticallyFlushable, FirstNonAutomaticallyFlushable,
};
use bt_packets::hci::{Acl, AclBuilder, AclChild, BroadcastFlag};
use bytes::{Buf, Bytes, BytesMut};
use futures::stream::{self, StreamExt};
use log::{error, info, warn};
use tokio::sync::mpsc::Sender;
use tokio::sync::oneshot;
use tokio_stream::wrappers::ReceiverStream;

const L2CAP_BASIC_FRAME_HEADER_LEN: usize = 4;

pub struct Reassembler {
    buffer: Option<BytesMut>,
    remaining: usize,
    out: Sender<Bytes>,
}

impl Reassembler {
    /// Create a new reassembler
    pub fn new(out: Sender<Bytes>) -> Self {
        Self { buffer: None, remaining: 0, out }
    }

    /// Injest the packet and send out if fully reassembled
    pub async fn on_packet(&mut self, packet: Acl) {
        let payload = match packet.specialize() {
            AclChild::Payload(payload) => payload,
            AclChild::None => {
                info!("dropping ACL packet with empty payload");
                return;
            }
        };

        if let BroadcastFlag::ActivePeripheralBroadcast = packet.get_broadcast_flag() {
            // we do not accept broadcast packets
            return;
        }

        match packet.get_packet_boundary_flag() {
            FirstNonAutomaticallyFlushable => error!("not allowed to send FIRST_NON_AUTOMATICALLY_FLUSHABLE to host except loopback mode"),
            FirstAutomaticallyFlushable => {
                if self.buffer.take().is_some() {
                    error!("got a start packet without finishing previous reassembly - dropping previous");
                }

                let full_size = get_l2cap_pdu_size(&payload);
                self.remaining = full_size - (payload.len() - L2CAP_BASIC_FRAME_HEADER_LEN);
                if self.remaining > 0 {
                    let mut buffer = BytesMut::with_capacity(full_size);
                    buffer.extend_from_slice(&payload[..]);
                    self.buffer = Some(buffer);
                } else {
                    self.out.send(payload).await.unwrap();
                }
            },
            ContinuingFragment => {
                match self.buffer.take() {
                    None => warn!("got continuation packet without pending reassembly"),
                    Some(_) if self.remaining < payload.len() => warn!("remote sent unexpected L2CAP PDU - dropping entire packet"),
                    Some(mut buffer) => {
                        self.remaining -= payload.len();
                        buffer.extend_from_slice(&payload[..]);
                        if self.remaining == 0 {
                            self.out.send(buffer.freeze()).await.unwrap();
                        } else {
                            self.buffer = Some(buffer);
                        }
                    }
                }
            },
        }
    }
}

fn get_l2cap_pdu_size(first_packet: &Bytes) -> usize {
    if first_packet.len() <= L2CAP_BASIC_FRAME_HEADER_LEN {
        error!("invalid l2cap starting packet");

        0
    } else {
        (&first_packet[..]).get_u16_le() as usize
    }
}

pub fn fragmenting_stream(
    rx: ReceiverStream<Bytes>,
    mtu: usize,
    handle: u16,
    bt: Bluetooth,
    close_rx: oneshot::Receiver<()>,
) -> std::pin::Pin<
    std::boxed::Box<dyn futures::Stream<Item = bt_packets::hci::Acl> + std::marker::Send>,
> {
    rx.flat_map(move |data| {
        stream::iter(
            data.chunks(mtu)
                .enumerate()
                .map(move |(i, chunk)| {
                    AclBuilder {
                        handle,
                        packet_boundary_flag: match bt {
                            Bluetooth::Classic if i == 0 => FirstAutomaticallyFlushable,
                            Bluetooth::Le if i == 0 => FirstNonAutomaticallyFlushable,
                            _ => ContinuingFragment,
                        },
                        broadcast_flag: BroadcastFlag::PointToPoint,
                        payload: Some(Bytes::copy_from_slice(chunk)),
                    }
                    .build()
                })
                .collect::<Vec<Acl>>(),
        )
    })
    .take_until(close_rx)
    .boxed()
}
