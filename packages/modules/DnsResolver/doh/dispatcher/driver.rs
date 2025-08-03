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

//! Provides a backing task to implement a Dispatcher

use crate::boot_time::{BootTime, Duration};
use anyhow::{bail, Result};
use log::{debug, trace, warn};
use std::collections::HashMap;
use tokio::sync::{mpsc, oneshot};

use super::{Command, QueryError, Response};
use crate::network::{Network, ServerInfo, SocketTagger, ValidationReporter};
use crate::{config, network};

pub struct Driver {
    command_rx: mpsc::Receiver<Command>,
    networks: HashMap<u32, Network>,
    validation: ValidationReporter,
    tagger: SocketTagger,
    config_cache: config::Cache,
}

fn debug_err(r: Result<()>) {
    if let Err(e) = r {
        debug!("Dispatcher loop got {:?}", e);
    }
}

impl Driver {
    pub fn new(
        command_rx: mpsc::Receiver<Command>,
        validation: ValidationReporter,
        tagger: SocketTagger,
    ) -> Self {
        Self {
            command_rx,
            networks: HashMap::new(),
            validation,
            tagger,
            config_cache: config::Cache::new(),
        }
    }

    pub async fn drive(mut self) -> Result<()> {
        loop {
            self.drive_once().await?
        }
    }

    async fn drive_once(&mut self) -> Result<()> {
        if let Some(command) = self.command_rx.recv().await {
            trace!("dispatch command: {:?}", command);
            match command {
                Command::Probe { info, timeout } => debug_err(self.probe(info, timeout).await),
                Command::Query { net_id, base64_query, expired_time, resp } => {
                    debug_err(self.query(net_id, base64_query, expired_time, resp).await)
                }
                Command::Clear { net_id } => {
                    self.networks.remove(&net_id);
                    self.config_cache.garbage_collect();
                }
                Command::Exit => {
                    bail!("Death due to Exit")
                }
            }
            Ok(())
        } else {
            bail!("Death due to command_tx dying")
        }
    }

    async fn query(
        &mut self,
        net_id: u32,
        query: String,
        expiry: BootTime,
        response: oneshot::Sender<Response>,
    ) -> Result<()> {
        if let Some(network) = self.networks.get_mut(&net_id) {
            network.query(network::Query { query, response, expiry }).await?;
        } else {
            warn!("Tried to send a query to non-existent network net_id={}", net_id);
            response.send(Response::Error { error: QueryError::Unexpected }).unwrap_or_else(|_| {
                warn!("Unable to send reply for non-existent network net_id={}", net_id);
            })
        }
        Ok(())
    }

    async fn probe(&mut self, info: ServerInfo, timeout: Duration) -> Result<()> {
        use std::collections::hash_map::Entry;
        if !self.networks.get(&info.net_id).map_or(true, |net| net.get_info() == &info) {
            // If we have a network registered to the provided net_id, but the server info doesn't
            // match, our API has been used incorrectly. Attempt to recover by deleting the old
            // network and recreating it according to the probe request.
            warn!("Probing net_id={} with mismatched server info {:?}", info.net_id, info);
            self.networks.remove(&info.net_id);
        }
        // Can't use or_insert_with because creating a network may fail
        let net = match self.networks.entry(info.net_id) {
            Entry::Occupied(network) => network.into_mut(),
            Entry::Vacant(vacant) => {
                let key = config::Key {
                    cert_path: info.cert_path.clone(),
                    max_idle_timeout: info.idle_timeout_ms,
                    enable_early_data: info.enable_early_data,
                };
                let config = self.config_cache.get(&key)?;
                vacant.insert(
                    Network::new(info, config, self.validation.clone(), self.tagger.clone())
                        .await?,
                )
            }
        };
        net.probe(timeout).await?;
        Ok(())
    }
}
