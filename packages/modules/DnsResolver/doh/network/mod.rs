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

//! Provides the ability to query DNS for a specific network configuration

use crate::boot_time::{BootTime, Duration};
use crate::config::Config;
use crate::dispatcher::{QueryError, Response};
use anyhow::Result;
use futures::future::BoxFuture;
use log::warn;
use std::net::SocketAddr;
use std::sync::Arc;
use tokio::sync::{mpsc, oneshot, watch};
use tokio::task;
use url::Url;

mod driver;

use driver::{Command, Driver};

pub use driver::Status;

/// Closure to signal validation status to outside world
pub type ValidationReporter = Arc<dyn Fn(&ServerInfo, bool) -> BoxFuture<()> + Send + Sync>;
/// Closure to tag socket during connection construction
pub type SocketTagger = Arc<dyn Fn(&std::net::UdpSocket) -> BoxFuture<()> + Send + Sync>;

#[derive(Eq, PartialEq, Debug, Clone)]
pub struct ServerInfo {
    pub net_id: u32,
    pub url: Url,
    pub peer_addr: SocketAddr,
    pub domain: Option<String>,
    pub sk_mark: u32,
    pub cert_path: Option<String>,
    pub idle_timeout_ms: u64,
    pub use_session_resumption: bool,
    pub enable_early_data: bool,
    pub network_type: u32,
    pub private_dns_mode: u32,
}

#[derive(Debug)]
/// DNS resolution query
pub struct Query {
    /// Raw DNS query, base64 encoded
    pub query: String,
    /// Place to send the answer
    pub response: oneshot::Sender<Response>,
    /// When this request is considered stale (will be ignored if not serviced by that point)
    pub expiry: BootTime,
}

/// Handle to a particular network's DNS resolution
pub struct Network {
    info: ServerInfo,
    status_rx: watch::Receiver<Status>,
    command_tx: mpsc::Sender<Command>,
}

impl Network {
    pub async fn new(
        info: ServerInfo,
        config: Config,
        validation: ValidationReporter,
        tagger: SocketTagger,
    ) -> Result<Network> {
        let (driver, command_tx, status_rx) =
            Driver::new(info.clone(), config, validation, tagger).await?;
        task::spawn(driver.drive());
        Ok(Network { info, command_tx, status_rx })
    }

    pub async fn probe(&mut self, timeout: Duration) -> Result<()> {
        self.command_tx.send(Command::Probe(timeout)).await?;
        Ok(())
    }

    pub async fn query(&mut self, query: Query) -> Result<()> {
        // The clone is used to prevent status_rx from being held across an await
        let status: Status = self.status_rx.borrow().clone();
        match status {
            Status::Failed(_) => query
                .response
                .send(Response::Error { error: QueryError::BrokenServer })
                .unwrap_or_else(|_| {
                    warn!("Query result listener went away before receiving a response")
                }),
            Status::Unprobed => query
                .response
                .send(Response::Error { error: QueryError::ServerNotReady })
                .unwrap_or_else(|_| {
                    warn!("Query result listener went away before receiving a response")
                }),
            Status::Live => self.command_tx.try_send(Command::Query(query))?,
        }
        Ok(())
    }

    pub fn get_info(&self) -> &ServerInfo {
        &self.info
    }
}
