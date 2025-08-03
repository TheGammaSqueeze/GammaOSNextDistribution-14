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

//! NCI Protocol Abstraction Layer
//! Supports sending NCI commands to the HAL and receiving
//! NCI messages back

use bytes::{BufMut, BytesMut};
use log::{debug, error};
use nfc_hal::{Hal, HalEventRegistry};
use nfc_packets::nci::DataPacketChild::Payload;
use nfc_packets::nci::NciPacketChild;
use nfc_packets::nci::NotificationChild::ConnCreditsNotification;
use nfc_packets::nci::{Command, DataPacket, DataPacketBuilder, Notification};
use nfc_packets::nci::{Opcode, PacketBoundaryFlag, Response};
use pdl_runtime::Packet;
use std::collections::HashMap;
use std::collections::VecDeque;
use std::sync::{Arc, Mutex};
use tokio::select;
use tokio::sync::mpsc::{channel, Receiver, Sender, UnboundedSender};
use tokio::sync::{oneshot, RwLock};
use tokio::time::{sleep, Duration, Instant};

pub mod api;

/// Result type
type Result<T> = std::result::Result<T, Box<dyn std::error::Error + Send + Sync>>;

/// Initialize the module and connect the channels
pub async fn init() -> Nci {
    let hc = nfc_hal::init().await;
    // Channel to handle data upstream messages
    //    let (in_data_int, in_data_ext) = channel::<DataPacket>(10);
    // Internal data channels
    //    let ic = InternalChannels { in_data_int };

    let (cmd_tx, cmd_rx) = channel::<QueuedCommand>(10);
    let commands = CommandSender { cmd_tx };
    let hal_events = hc.hal_events.clone();

    let notifications = EventRegistry { handlers: Arc::new(Mutex::new(HashMap::new())) };
    let connections = LogicalConnectionsRegistry {
        conns: Arc::new(RwLock::new(HashMap::new())),
        sender: hc.out_data_tx.clone(),
    };

    tokio::spawn(dispatch(notifications, connections.clone(), hc, cmd_rx));
    Nci { hal_events, commands, connections }
}

/// NCI module external interface
pub struct Nci {
    /// HAL events
    pub hal_events: HalEventRegistry,
    /// NCI command communication interface
    pub commands: CommandSender,
    /// NCI logical connections
    pub connections: LogicalConnectionsRegistry,
}

#[derive(Debug)]
struct PendingCommand {
    cmd: Command,
    response: oneshot::Sender<Response>,
}

#[derive(Debug)]
struct QueuedCommand {
    pending: PendingCommand,
    notification: Option<oneshot::Sender<Notification>>,
}

/// Sends raw commands. Only useful for facades & shims, or wrapped as a CommandSender.
pub struct CommandSender {
    cmd_tx: Sender<QueuedCommand>,
}

/// The data returned by send_notify() method.
pub struct ResponsePendingNotification {
    /// Command response
    pub response: Response,
    /// Pending notification receiver
    pub notification: oneshot::Receiver<Notification>,
}

impl CommandSender {
    /// Send a command, but do not expect notification to be returned
    pub async fn send(&mut self, cmd: Command) -> Result<Response> {
        let (tx, rx) = oneshot::channel::<Response>();
        self.cmd_tx
            .send(QueuedCommand {
                pending: PendingCommand { cmd, response: tx },
                notification: None,
            })
            .await?;
        let event = rx.await?;
        Ok(event)
    }
    /// Send a command which expects notification as a result
    pub async fn send_and_notify(&mut self, cmd: Command) -> Result<ResponsePendingNotification> {
        let (tx, rx) = oneshot::channel::<Response>();
        let (ntx, nrx) = oneshot::channel::<Notification>();
        self.cmd_tx
            .send(QueuedCommand {
                pending: PendingCommand { cmd, response: tx },
                notification: Some(ntx),
            })
            .await?;
        let event = rx.await?;
        Ok(ResponsePendingNotification { response: event, notification: nrx })
    }
}

impl Drop for CommandSender {
    fn drop(&mut self) {
        debug!("CommandSender is dropped");
    }
}

/// Parameters of a logical connection
struct ConnectionParameters {
    callback: Option<fn(u8, u16, &[u8])>,
    max_payload_size: u8,
    nfcc_credits_avail: u8,
    sendq: VecDeque<DataPacket>,
    recvq: VecDeque<DataPacket>,
}

impl ConnectionParameters {
    /// Flush TX queue
    fn flush_tx(&mut self) {
        self.sendq.clear();
    }
}

/// To keep track of currentry open logical connections
#[derive(Clone)]
pub struct LogicalConnectionsRegistry {
    conns: Arc<RwLock<HashMap<u8, Mutex<ConnectionParameters>>>>,
    sender: UnboundedSender<DataPacket>,
}

impl LogicalConnectionsRegistry {
    /// Create a logical connection
    pub async fn open(
        &mut self,
        conn_id: u8,
        cb: Option<fn(u8, u16, &[u8])>,
        max_payload_size: u8,
        nfcc_credits_avail: u8,
    ) {
        let conn_params = ConnectionParameters {
            callback: cb,
            max_payload_size,
            nfcc_credits_avail,
            sendq: VecDeque::<DataPacket>::new(),
            recvq: VecDeque::<DataPacket>::new(),
        };
        assert!(
            self.conns.write().await.insert(conn_id, Mutex::new(conn_params)).is_none(),
            "A logical connection with id {:?} already exists",
            conn_id
        );
    }
    /// Set static callback
    pub async fn set_static_callback(&mut self, conn_id: u8, cb: Option<fn(u8, u16, &[u8])>) {
        if conn_id < 2 && cb.is_some() {
            // Static connections
            if let Some(conn_params) = self.conns.read().await.get(&conn_id) {
                let mut conn_params = conn_params.lock().unwrap();
                conn_params.callback = cb;
            }
        }
    }
    /// Close a logical connection
    pub async fn close(&mut self, conn_id: u8) -> Option<fn(u8, u16, &[u8])> {
        if let Some(conn_params) = self.conns.write().await.remove(&conn_id) {
            conn_params.lock().unwrap().callback
        } else {
            None
        }
    }
    /// Add credits to a logical connection
    pub async fn add_credits(&self, conn_id: u8, ncreds: u8) {
        if let Some(conn_params) = self.conns.read().await.get(&conn_id) {
            let mut conn_params = conn_params.lock().unwrap();
            conn_params.nfcc_credits_avail += ncreds;
            while !conn_params.sendq.is_empty() && conn_params.nfcc_credits_avail > 0 {
                self.sender.send(conn_params.sendq.pop_front().unwrap()).unwrap();
                conn_params.nfcc_credits_avail -= 1;
            }
        }
    }

    /// Send a packet to a logical channel, splitting it if needed
    pub async fn send_packet(&mut self, conn_id: u8, pkt: DataPacket) {
        if let Some(conn_params) = self.conns.read().await.get(&conn_id) {
            let mut conn_params = conn_params.lock().unwrap();
            if let Payload(mut p) = pkt.specialize() {
                if p.len() > conn_params.max_payload_size.into() {
                    let conn_id = pkt.get_conn_id();
                    while p.len() > conn_params.max_payload_size.into() {
                        let part = DataPacketBuilder {
                            conn_id,
                            pbf: PacketBoundaryFlag::Incomplete,
                            cr: 0,
                            payload: Some(p.split_to(conn_params.max_payload_size.into())),
                        }
                        .build();
                        conn_params.sendq.push_back(part);
                    }
                    if !p.is_empty() {
                        let end = DataPacketBuilder {
                            conn_id,
                            pbf: PacketBoundaryFlag::CompleteOrFinal,
                            cr: 0,
                            payload: Some(p),
                        }
                        .build();
                        conn_params.sendq.push_back(end);
                    }
                } else {
                    conn_params.sendq.push_back(pkt);
                }
            }
            while conn_params.nfcc_credits_avail > 0 && !conn_params.sendq.is_empty() {
                self.sender.send(conn_params.sendq.pop_front().unwrap()).unwrap();
                conn_params.nfcc_credits_avail -= 1;
            }
        }
    }

    /// Send data packet callback to the upper layers
    pub async fn send_callback(&self, pkt: DataPacket) {
        let conn_id = pkt.get_conn_id();
        let ncreds = pkt.get_cr();
        if ncreds > 0 {
            self.add_credits(conn_id, ncreds).await;
        }
        let done = pkt.get_pbf() == PacketBoundaryFlag::CompleteOrFinal;
        if let Some(conn_params) = self.conns.read().await.get(&conn_id) {
            let mut conn_params = conn_params.lock().unwrap();
            if !done && conn_params.recvq.is_empty() {
                const NFC_DATA_START_CEVT: u16 = 5;
                let cb = conn_params.callback.unwrap();
                cb(conn_id, NFC_DATA_START_CEVT, &[]);
            }
            conn_params.recvq.push_back(pkt);
            if done {
                const NFC_DATA_CEVT_SIZE: usize = 4; // 3 for header and 1 for status
                let cap = conn_params.recvq.len() * conn_params.max_payload_size as usize
                    + NFC_DATA_CEVT_SIZE;
                let mut buffer = BytesMut::with_capacity(cap);
                buffer.put_u8(0u8); // status
                let pkt = conn_params.recvq.pop_front().unwrap();
                buffer.put(pkt.to_bytes());
                while !conn_params.recvq.is_empty() {
                    let pkt = conn_params.recvq.pop_front().unwrap();
                    if let Payload(p) = pkt.specialize() {
                        buffer.put(p);
                    }
                }
                let data_cevt = buffer.freeze();
                let cb = conn_params.callback.unwrap();
                const NFC_DATA_CEVT: u16 = 3;
                cb(conn_id, NFC_DATA_CEVT, data_cevt.as_ref());
            }
        }
    }

    /// Flush outgoing data queue
    pub async fn flush_data(&mut self, conn_id: u8) -> bool {
        if let Some(conn_params) = self.conns.read().await.get(&conn_id) {
            conn_params.lock().unwrap().flush_tx();
            true
        } else {
            false
        }
    }
}

/// Provides ability to register and unregister for NCI notifications
#[derive(Clone)]
pub struct EventRegistry {
    handlers: Arc<Mutex<HashMap<Opcode, oneshot::Sender<Notification>>>>,
}

impl EventRegistry {
    /// Indicate interest in specific NCI notification
    pub async fn register(&mut self, code: Opcode, sender: oneshot::Sender<Notification>) {
        assert!(
            self.handlers.lock().unwrap().insert(code, sender).is_none(),
            "A handler for {:?} is already registered",
            code
        );
    }

    /// Remove interest in specific NCI notification
    pub async fn unregister(&mut self, code: Opcode) -> Option<oneshot::Sender<Notification>> {
        self.handlers.lock().unwrap().remove(&code)
    }
}

async fn dispatch(
    mut ntfs: EventRegistry,
    lcons: LogicalConnectionsRegistry,
    mut hc: Hal,
    //    ic: InternalChannels,
    mut cmd_rx: Receiver<QueuedCommand>,
) -> Result<()> {
    let mut pending: Option<PendingCommand> = None;
    let timeout = sleep(Duration::MAX);
    // The max_deadline is used to set  the sleep() deadline to a very distant moment in
    // the future, when the notification from the timer is not required.
    let max_deadline = timeout.deadline();
    tokio::pin!(timeout);
    loop {
        select! {
            Some(cmd) = hc.in_cmd_rx.recv() => {
                match cmd.specialize() {
                    NciPacketChild::Response(rsp) => {
                        timeout.as_mut().reset(max_deadline);
                        let this_opcode = rsp.get_cmd_op();
                        match pending.take() {
                            Some(PendingCommand{cmd, response}) if cmd.get_op() == this_opcode => {
                                if let Err(e) = response.send(rsp) {
                                    error!("failure dispatching command status {:?}", e);
                                }
                            },
                            Some(PendingCommand{cmd, ..}) => panic!("Waiting for {:?}, got {:?}", cmd.get_op(), this_opcode),
                            None => panic!("Unexpected status event with opcode {:?}", this_opcode),
                        }
                    },
                    NciPacketChild::Notification(ntfy) => {
                        match ntfy.specialize() {
                            ConnCreditsNotification(ccnp) => {
                                let conns = ccnp.get_conns();
                                for conn in conns {
                                    lcons.add_credits(conn.conn_id, conn.ncredits).await;
                                }
                            },
                            _ => {
                                let code = ntfy.get_cmd_op();
                                match ntfs.unregister(code).await {
                                    Some(sender) => {
                                        if let Err(e) = sender.send(ntfy) {
                                            error!("notification channel closed {:?}", e);
                                        }
                                    },
                                    None => panic!("Unhandled notification {:?}", code),
                                }
                            },
                        }
                    },
                    _ => error!("Unexpected NCI data received {:?}", cmd),
                }
            },
            qc = cmd_rx.recv(), if pending.is_none() => if let Some(queued) = qc {
                debug!("cmd_rx got a q");
                if let Some(nsender) = queued.notification {
                    ntfs.register(queued.pending.cmd.get_op(), nsender).await;
                }
                if let Err(e) = hc.out_cmd_tx.send(queued.pending.cmd.clone().into()) {
                    error!("command queue closed: {:?}", e);
                }
                timeout.as_mut().reset(Instant::now() + Duration::from_millis(20));
                pending = Some(queued.pending);
            } else {
                break;
            },
            () = &mut timeout => {
                error!("Command processing timeout");
                timeout.as_mut().reset(max_deadline);
                pending = None;
            },
            Some(data) = hc.in_data_rx.recv() => lcons.send_callback(data).await,
            else => {
                debug!("Select is done");
                break;
            },
        }
    }
    debug!("NCI dispatch is terminated.");
    Ok(())
}
