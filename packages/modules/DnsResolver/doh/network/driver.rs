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

//! Provides a backing task to implement a network

use crate::boot_time::{timeout, BootTime, Duration};
use crate::config::Config;
use crate::connection::driver::Cause;
use crate::connection::Connection;
use crate::dispatcher::{QueryError, Response};
use crate::encoding;
use anyhow::{anyhow, bail, Result};
use log::{debug, info};
use std::sync::Arc;
use tokio::sync::{mpsc, watch};
use tokio::task;

use super::{Query, ServerInfo, SocketTagger, ValidationReporter};

pub struct Driver {
    info: ServerInfo,
    config: Config,
    connection: Connection,
    command_rx: mpsc::Receiver<Command>,
    status_tx: watch::Sender<Status>,
    validation: ValidationReporter,
    tag_socket: SocketTagger,
}

#[derive(Debug)]
/// Requests the network can handle
pub enum Command {
    /// Send a DNS query to the network
    Query(Query),
    /// Run a probe to check the health of the network. Argument is timeout.
    Probe(Duration),
}

#[derive(Clone, Debug)]
/// Current Network Status
///
/// (Unprobed or Failed) can go to (Live or Failed) via Probe.
/// Currently, there is no way to go from Live to Failed - probing a live network will short-circuit to returning valid, and query failures do not declare the network failed.
pub enum Status {
    /// Network has not been probed, it may or may not work
    Unprobed,
    /// Network is believed to be working
    Live,
    /// Network is broken, reason as argument
    Failed(Arc<anyhow::Error>),
}

impl Status {
    pub fn is_live(&self) -> bool {
        matches!(self, Self::Live)
    }
    pub fn is_failed(&self) -> bool {
        matches!(self, Self::Failed(_))
    }
}

async fn build_connection(
    info: &ServerInfo,
    tag_socket: &SocketTagger,
    config: &mut Config,
    session: Option<Vec<u8>>,
    cause: Cause,
) -> Result<Connection> {
    use std::ops::DerefMut;
    Ok(Connection::new(info, tag_socket, config.take().await.deref_mut(), session, cause).await?)
}

impl Driver {
    const MAX_BUFFERED_COMMANDS: usize = 50;

    pub async fn new(
        info: ServerInfo,
        mut config: Config,
        validation: ValidationReporter,
        tag_socket: SocketTagger,
    ) -> Result<(Self, mpsc::Sender<Command>, watch::Receiver<Status>)> {
        let (command_tx, command_rx) = mpsc::channel(Self::MAX_BUFFERED_COMMANDS);
        let (status_tx, status_rx) = watch::channel(Status::Unprobed);
        let connection =
            build_connection(&info, &tag_socket, &mut config, None, Cause::Probe).await?;
        Ok((
            Self { info, config, connection, status_tx, command_rx, validation, tag_socket },
            command_tx,
            status_rx,
        ))
    }

    pub async fn drive(mut self) -> Result<()> {
        while let Some(cmd) = self.command_rx.recv().await {
            match cmd {
                Command::Probe(duration) => {
                    if let Err(e) = self.probe(duration).await {
                        self.status_tx.send(Status::Failed(Arc::new(e)))?
                    }
                }
                Command::Query(query) => {
                    if let Err(e) = self.send_query(query).await {
                        info!("Unable to send query: {:?}", e)
                    }
                }
            };
        }
        Ok(())
    }

    async fn probe(&mut self, probe_timeout: Duration) -> Result<()> {
        if self.status_tx.borrow().is_failed() {
            debug!("Network is currently failed, reconnecting");
            // If our network is currently failed, it may be due to issues with the connection.
            // Re-establish before re-probing
            self.connection = build_connection(
                &self.info,
                &self.tag_socket,
                &mut self.config,
                None,
                Cause::Retry,
            )
            .await?;
            self.status_tx.send(Status::Unprobed)?;
        }
        if self.status_tx.borrow().is_live() {
            // If we're already validated, short circuit
            (self.validation)(&self.info, true).await;
            return Ok(());
        }
        self.force_probe(probe_timeout).await
    }

    async fn force_probe(&mut self, probe_timeout: Duration) -> Result<()> {
        info!("Sending probe to server {} on Network {}", self.info.peer_addr, self.info.net_id);
        let probe = encoding::probe_query()?;
        let dns_request = encoding::dns_request(&probe, &self.info.url)?;
        let expiry = BootTime::now().checked_add(probe_timeout);
        let request = async {
            match self.connection.query(dns_request, expiry).await {
                Err(e) => self.status_tx.send(Status::Failed(Arc::new(anyhow!(e)))),
                Ok(rsp) => {
                    if let Some(_stream) = rsp.await {
                        // TODO verify stream contents
                        self.status_tx.send(Status::Live)
                    } else {
                        self.status_tx.send(Status::Failed(Arc::new(anyhow!("Empty response"))))
                    }
                }
            }
        };
        match timeout(probe_timeout, request).await {
            // Timed out
            Err(time) => self.status_tx.send(Status::Failed(Arc::new(anyhow!(
                "Probe timed out after {:?} (timeout={:?})",
                time,
                probe_timeout
            )))),
            // Query completed
            Ok(r) => r,
        }?;
        let valid = self.status_tx.borrow().is_live();
        (self.validation)(&self.info, valid).await;
        Ok(())
    }

    async fn send_query(&mut self, query: Query) -> Result<()> {
        // If the associated receiver has been closed, meaning that the request has already
        // timed out, just drop it. This check helps drain the channel quickly in the case
        // where the network is stalled.
        if query.response.is_closed() {
            bail!("Abandoning expired DNS request")
        }

        if !self.connection.wait_for_live().await {
            let session =
                if self.info.use_session_resumption { self.connection.session() } else { None };
            // Try reconnecting
            self.connection = build_connection(
                &self.info,
                &self.tag_socket,
                &mut self.config,
                session,
                Cause::Reconnect,
            )
            .await?;
        }
        let request = encoding::dns_request(&query.query, &self.info.url)?;
        let stream_fut = self.connection.query(request, Some(query.expiry)).await?;
        task::spawn(async move {
            let stream = match stream_fut.await {
                Some(stream) => stream,
                None => {
                    info!("Connection died while processing request");
                    // We don't care if the response is gone
                    let _ =
                        query.response.send(Response::Error { error: QueryError::ConnectionError });
                    return;
                }
            };
            // We don't care if the response is gone.
            let _ = if let Some(err) = stream.error {
                query.response.send(Response::Error { error: QueryError::Reset(err) })
            } else {
                query.response.send(Response::Success { answer: stream.data })
            };
        });
        Ok(())
    }
}
