/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use crate::boot_time::{BootTime, Duration};
use anyhow::Result;
use log::error;
use tokio::runtime::{Builder, Runtime};
use tokio::sync::{mpsc, oneshot};
use tokio::task;

pub use crate::network::{ServerInfo, SocketTagger, ValidationReporter};

const MAX_BUFFERED_CMD_COUNT: usize = 400;

mod driver;
use driver::Driver;

#[derive(Eq, PartialEq, Debug)]
/// Error response to a query
pub enum QueryError {
    /// Network failed probing
    BrokenServer,
    /// HTTP/3 connection died
    ConnectionError,
    /// Network not probed yet
    ServerNotReady,
    /// Server reset HTTP/3 stream
    Reset(u64),
    /// Tried to query non-existent network
    Unexpected,
}

#[derive(Eq, PartialEq, Debug)]
pub enum Response {
    Error { error: QueryError },
    Success { answer: Vec<u8> },
}

#[derive(Debug)]
pub enum Command {
    Probe {
        info: ServerInfo,
        timeout: Duration,
    },
    Query {
        net_id: u32,
        base64_query: String,
        expired_time: BootTime,
        resp: oneshot::Sender<Response>,
    },
    Clear {
        net_id: u32,
    },
    Exit,
}

/// Context for a running DoH engine.
pub struct Dispatcher {
    /// Used to submit cmds to the I/O task.
    cmd_sender: mpsc::Sender<Command>,
    join_handle: task::JoinHandle<Result<()>>,
    runtime: Runtime,
}

impl Dispatcher {
    const DOH_THREADS: usize = 1;

    pub fn new(validation: ValidationReporter, tagger: SocketTagger) -> Result<Dispatcher> {
        let (cmd_sender, cmd_receiver) = mpsc::channel::<Command>(MAX_BUFFERED_CMD_COUNT);
        let runtime = Builder::new_multi_thread()
            .worker_threads(Self::DOH_THREADS)
            .enable_all()
            .thread_name("doh-handler")
            .build()?;
        let join_handle = runtime.spawn(async {
            let result = Driver::new(cmd_receiver, validation, tagger).drive().await;
            if let Err(ref e) = result {
                error!("Dispatcher driver exited due to {:?}", e)
            }
            result
        });
        Ok(Dispatcher { cmd_sender, join_handle, runtime })
    }

    pub fn send_cmd(&self, cmd: Command) -> Result<()> {
        self.cmd_sender.blocking_send(cmd)?;
        Ok(())
    }

    pub fn exit_handler(&mut self) {
        if self.cmd_sender.blocking_send(Command::Exit).is_err() {
            return;
        }
        let _ = self.runtime.block_on(&mut self.join_handle);
    }
}
