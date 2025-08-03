//! ACL core dispatch shared between LE and classic

use crate::hal::AclHal;
use crate::hci::{ControllerExports, EventRegistry};
use crate::link::acl::fragment::{fragmenting_stream, Reassembler};
use bt_common::Bluetooth::{self, Classic, Le};
use bt_packets::hci::EventChild::{DisconnectionComplete, NumberOfCompletedPackets};
use bt_packets::hci::{Acl, Event, EventCode};
use bytes::Bytes;
use futures::stream::{SelectAll, StreamExt};
use gddi::{module, provides, Stoppable};
use log::info;
use std::collections::HashMap;
use std::sync::Arc;
use tokio::runtime::Runtime;
use tokio::select;
use tokio::sync::mpsc::{channel, Receiver, Sender};
use tokio::sync::{oneshot, Mutex};
use tokio_stream::wrappers::ReceiverStream;

module! {
    core_module,
    providers {
        AclDispatch => provide_acl_dispatch,
    },
}

/// A basic ACL connection
#[derive(Debug)]
pub struct Connection {
    pub rx: Option<Receiver<Bytes>>,
    pub tx: Option<Sender<Bytes>>,
    #[allow(dead_code)]
    handle: u16,
    #[allow(dead_code)]
    requests: Sender<Request>,
    pub evt_rx: Receiver<Event>,
    pub evt_tx: Sender<Event>,
}

struct ConnectionInternal {
    reassembler: Reassembler,
    bt: Bluetooth,
    close_tx: oneshot::Sender<()>,
    evt_tx: Sender<Event>,
}

/// Manages rx and tx for open ACL connections
#[derive(Clone, Stoppable)]
pub struct AclDispatch {
    requests: Sender<Request>,
}

impl AclDispatch {
    /// Register the provided connection with the ACL dispatch
    #[allow(dead_code)]
    pub async fn register(&mut self, handle: u16, bt: Bluetooth) -> Connection {
        let (tx, rx) = oneshot::channel();
        self.requests.send(Request::Register { handle, bt, fut: tx }).await.unwrap();
        rx.await.unwrap()
    }
}

#[derive(Debug)]
enum Request {
    Register { handle: u16, bt: Bluetooth, fut: oneshot::Sender<Connection> },
}

const QCOM_DEBUG_HANDLE: u16 = 0xedc;

#[provides]
async fn provide_acl_dispatch(
    acl: AclHal,
    controller: Arc<ControllerExports>,
    mut events: EventRegistry,
    rt: Arc<Runtime>,
) -> AclDispatch {
    let (req_tx, mut req_rx) = channel::<Request>(10);
    let req_tx_clone = req_tx.clone();

    rt.spawn(async move {
        let mut connections: HashMap<u16, ConnectionInternal> = HashMap::new();
        let mut classic_outbound = SelectAll::new();
        let mut classic_credits = controller.acl_buffers;
        let mut le_outbound = SelectAll::new();
        let mut le_credits: u16 = controller.le_buffers.into();

        let (evt_tx, mut evt_rx) = channel(3);
        events.register(EventCode::NumberOfCompletedPackets, evt_tx.clone()).await;
        events.register(EventCode::DisconnectionComplete, evt_tx).await;

        loop {
            select! {
                Some(req) = req_rx.recv() => {
                    match req {
                        Request::Register { handle, bt, fut } => {
                            let (out_tx, out_rx) = channel(10);
                            let (in_tx, in_rx) = channel(10);
                            let (evt_tx, evt_rx) = channel(3);
                            let (close_tx, close_rx) = oneshot::channel();

                            assert!(connections.insert(
                                handle,
                                ConnectionInternal {
                                    reassembler: Reassembler::new(out_tx),
                                    bt,
                                    close_tx,
                                    evt_tx: evt_tx.clone(),
                                }).is_none());

                            match bt {
                                Classic => {
                                    classic_outbound.push(fragmenting_stream(
                                        ReceiverStream::new(in_rx), controller.acl_buffer_length.into(), handle, bt, close_rx));
                                },
                                Le => {
                                    le_outbound.push(fragmenting_stream(
                                        ReceiverStream::new(in_rx), controller.le_buffer_length.into(), handle, bt, close_rx));
                                },
                            }

                            fut.send(Connection {
                                rx: Some(out_rx),
                                tx: Some(in_tx),
                                handle,
                                requests: req_tx_clone.clone(),
                                evt_rx,
                                evt_tx,
                            }).unwrap();
                        },
                    }
                },
                Some(p) = consume(&acl.rx) => {
                    match connections.get_mut(&p.get_handle()) {
                        Some(c) => c.reassembler.on_packet(p).await,
                        None if p.get_handle() == QCOM_DEBUG_HANDLE => {},
                        None => info!("no acl for {}", p.get_handle()),
                    }
                },
                Some(p) = classic_outbound.next(), if classic_credits > 0 => {
                    acl.tx.send(p).await.unwrap();
                    classic_credits -= 1;
                },
                Some(p) = le_outbound.next(), if le_credits > 0 => {
                    acl.tx.send(p).await.unwrap();
                    le_credits -= 1;
                },
                Some(evt) = evt_rx.recv() => {
                    match evt.specialize() {
                        NumberOfCompletedPackets(evt) => {
                            for entry in evt.get_completed_packets() {
                                match connections.get(&entry.connection_handle) {
                                    Some(conn) => {
                                        let credits = entry.host_num_of_completed_packets;
                                        match conn.bt {
                                            Classic => classic_credits += credits,
                                            Le => le_credits += credits,
                                        }
                                        assert!(classic_credits <= controller.acl_buffers);
                                        assert!(le_credits <= controller.le_buffers.into());
                                    },
                                    None => info!("dropping credits for unknown connection {}", entry.connection_handle),
                                }
                            }
                        },
                        DisconnectionComplete(evt) => {
                            if let Some(c) = connections.remove(&evt.get_connection_handle()) {
                                c.close_tx.send(()).unwrap();
                                c.evt_tx.send(evt.into()).await.unwrap();
                            }
                        },
                        _ => unimplemented!(),
                    }
                },
            }
        }
    });

    AclDispatch { requests: req_tx }
}

async fn consume(rx: &Arc<Mutex<Receiver<Acl>>>) -> Option<Acl> {
    rx.lock().await.recv().await
}
