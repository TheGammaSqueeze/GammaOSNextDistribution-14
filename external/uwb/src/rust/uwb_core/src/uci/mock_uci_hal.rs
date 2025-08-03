// Copyright 2022, The Android Open Source Project
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

use std::collections::VecDeque;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use async_trait::async_trait;
use log::error;
use tokio::sync::{mpsc, Notify};
use tokio::time::timeout;

use crate::error::{Error, Result};
use crate::params::uci_packets::SessionId;
use crate::uci::command::UciCommand;
use crate::uci::uci_hal::{UciHal, UciHalPacket};

/// The mock implementation of UciHal.
#[derive(Default, Clone)]
pub struct MockUciHal {
    // Wrap inside Arc<Mutex<>> so that the MockUciHal.clone(s) refer to the same object.
    packet_sender: Arc<Mutex<Option<mpsc::UnboundedSender<UciHalPacket>>>>,
    expected_calls: Arc<Mutex<VecDeque<ExpectedCall>>>,
    expect_call_consumed: Arc<Notify>,
}

impl MockUciHal {
    pub fn new() -> Self {
        Default::default()
    }

    pub fn expected_open(&mut self, packets: Option<Vec<UciHalPacket>>, out: Result<()>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::Open { packets, out });
    }

    pub fn expected_close(&mut self, out: Result<()>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::Close { out });
    }

    pub fn expected_send_command(
        &mut self,
        expected_cmd: UciCommand,
        packets: Vec<UciHalPacket>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SendCommand {
            expected_cmd,
            packets,
            out,
        });
    }

    pub fn expected_send_packet(
        &mut self,
        expected_packet_tx: UciHalPacket,
        inject_packets_rx: Vec<UciHalPacket>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SendPacket {
            expected_packet_tx,
            inject_packets_rx,
            out,
        });
    }

    pub fn expected_notify_session_initialized(
        &mut self,
        expected_session_id: SessionId,
        out: Result<()>,
    ) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::NotifySessionInitialized { expected_session_id, out });
    }

    pub async fn wait_expected_calls_done(&mut self) -> bool {
        while !self.expected_calls.lock().unwrap().is_empty() {
            if timeout(Duration::from_secs(1), self.expect_call_consumed.notified()).await.is_err()
            {
                return false;
            }
        }
        true
    }

    // Receive a UCI packet (eg: UCI DATA_MESSAGE_RCV), from UWBS to Host.
    pub fn receive_packet(&mut self, packet: UciHalPacket) -> Result<()> {
        if let Some(ref ps) = *self.packet_sender.lock().unwrap() {
            match ps.send(packet) {
                Ok(_) => Ok(()),
                Err(_) => Err(Error::Unknown),
            }
        } else {
            error!("MockUciHal unable to Rx packet from HAL as channel closed");
            Err(Error::MockUndefined)
        }
    }
}

#[async_trait]
impl UciHal for MockUciHal {
    async fn open(&mut self, packet_sender: mpsc::UnboundedSender<UciHalPacket>) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::Open { packets, out }) => {
                self.expect_call_consumed.notify_one();
                if let Some(packets) = packets {
                    for msg in packets.into_iter() {
                        let _ = packet_sender.send(msg);
                    }
                }
                if out.is_ok() {
                    self.packet_sender.lock().unwrap().replace(packet_sender);
                }
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn close(&mut self) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::Close { out }) => {
                self.expect_call_consumed.notify_one();
                if out.is_ok() {
                    *self.packet_sender.lock().unwrap() = None;
                }
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn send_command(&mut self, cmd: UciCommand) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SendCommand { expected_cmd, packets, out })
                if expected_cmd == cmd =>
            {
                self.expect_call_consumed.notify_one();
                let mut packet_sender_opt = self.packet_sender.lock().unwrap();
                let packet_sender = packet_sender_opt.as_mut().unwrap();
                for msg in packets.into_iter() {
                    let _ = packet_sender.send(msg);
                }
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn send_packet(&mut self, packet_tx: UciHalPacket) -> Result<()> {
        // send_packet() will be directly called for sending UCI Data packets.
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SendPacket { expected_packet_tx, inject_packets_rx, out })
                if expected_packet_tx == packet_tx =>
            {
                self.expect_call_consumed.notify_one();
                let mut packet_sender_opt = self.packet_sender.lock().unwrap();
                let packet_sender = packet_sender_opt.as_mut().unwrap();
                for msg in inject_packets_rx.into_iter() {
                    let _ = packet_sender.send(msg);
                }
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn notify_session_initialized(&mut self, session_id: SessionId) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::NotifySessionInitialized { expected_session_id, out })
                if expected_session_id == session_id =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }
}

enum ExpectedCall {
    Open {
        packets: Option<Vec<UciHalPacket>>,
        out: Result<()>,
    },
    Close {
        out: Result<()>,
    },
    SendCommand {
        expected_cmd: UciCommand,
        packets: Vec<UciHalPacket>,
        out: Result<()>,
    },
    SendPacket {
        expected_packet_tx: UciHalPacket,
        inject_packets_rx: Vec<UciHalPacket>,
        out: Result<()>,
    },
    NotifySessionInitialized {
        expected_session_id: SessionId,
        out: Result<()>,
    },
}
