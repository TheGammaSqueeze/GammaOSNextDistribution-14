/*
 * Copyright (C) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! DoH server frontend.

use super::client::{ClientMap, ConnectionID, CONN_ID_LEN, DNS_HEADER_SIZE, MAX_UDP_PAYLOAD_SIZE};
use super::config::{Config, QUICHE_IDLE_TIMEOUT_MS};
use super::stats::Stats;
use anyhow::{bail, ensure, Result};
use lazy_static::lazy_static;
use log::{debug, error, warn};
use std::fs::File;
use std::io::Write;
use std::os::unix::io::{AsRawFd, FromRawFd};
use std::sync::{Arc, Mutex};
use std::time::Duration;
use tokio::net::UdpSocket;
use tokio::runtime::{Builder, Runtime};
use tokio::sync::{mpsc, oneshot};
use tokio::task::JoinHandle;

lazy_static! {
    static ref RUNTIME_STATIC: Arc<Runtime> = Arc::new(
        Builder::new_multi_thread()
            .worker_threads(1)
            .enable_all()
            .thread_name("DohFrontend")
            .build()
            .expect("Failed to create tokio runtime")
    );
}

/// Command used by worker_thread itself.
#[derive(Debug)]
enum InternalCommand {
    MaybeWrite { connection_id: ConnectionID },
}

/// Commands that DohFrontend to ask its worker_thread for.
#[derive(Debug)]
enum ControlCommand {
    Stats { resp: oneshot::Sender<Stats> },
    StatsClearQueries,
    CloseConnection,
}

/// Frontend object.
#[derive(Debug)]
pub struct DohFrontend {
    // Socket address the frontend listens to.
    listen_socket_addr: std::net::SocketAddr,

    // Socket address the backend listens to.
    backend_socket_addr: std::net::SocketAddr,

    /// The content of the certificate.
    certificate: String,

    /// The content of the private key.
    private_key: String,

    // The thread listening to frontend socket and backend socket
    // and processing the messages.
    worker_thread: Option<JoinHandle<Result<()>>>,

    // Custom runtime configuration to control the behavior of the worker thread.
    // It's shared with the worker thread.
    // TODO: use channel to update worker_thread configuration.
    config: Arc<Mutex<Config>>,

    // Caches the latest stats so that the stats remains after worker_thread stops.
    latest_stats: Stats,

    // It is wrapped as Option because the channel is not created in DohFrontend construction.
    command_tx: Option<mpsc::UnboundedSender<ControlCommand>>,
}

/// The parameters passed to the worker thread.
struct WorkerParams {
    frontend_socket: std::net::UdpSocket,
    backend_socket: std::net::UdpSocket,
    clients: ClientMap,
    config: Arc<Mutex<Config>>,
    command_rx: mpsc::UnboundedReceiver<ControlCommand>,
}

impl DohFrontend {
    pub fn new(
        listen: std::net::SocketAddr,
        backend: std::net::SocketAddr,
    ) -> Result<Box<DohFrontend>> {
        let doh = Box::new(DohFrontend {
            listen_socket_addr: listen,
            backend_socket_addr: backend,
            certificate: String::new(),
            private_key: String::new(),
            worker_thread: None,
            config: Arc::new(Mutex::new(Config::new())),
            latest_stats: Stats::new(),
            command_tx: None,
        });
        debug!("DohFrontend created: {:?}", doh);
        Ok(doh)
    }

    pub fn start(&mut self) -> Result<()> {
        ensure!(self.worker_thread.is_none(), "Worker thread has been running");
        ensure!(!self.certificate.is_empty(), "certificate is empty");
        ensure!(!self.private_key.is_empty(), "private_key is empty");

        // Doing error handling here is much simpler.
        let params = match self.init_worker_thread_params() {
            Ok(v) => v,
            Err(e) => return Err(e.context("init_worker_thread_params failed")),
        };

        self.worker_thread = Some(RUNTIME_STATIC.spawn(worker_thread(params)));
        Ok(())
    }

    pub fn stop(&mut self) -> Result<()> {
        debug!("DohFrontend: stopping: {:?}", self);
        if let Some(worker_thread) = self.worker_thread.take() {
            // Update latest_stats before stopping worker_thread.
            let _ = self.request_stats();

            self.command_tx.as_ref().unwrap().send(ControlCommand::CloseConnection)?;
            if let Err(e) = self.wait_for_connections_closed() {
                warn!("wait_for_connections_closed failed: {}", e);
            }

            worker_thread.abort();
            RUNTIME_STATIC.block_on(async {
                debug!("worker_thread result: {:?}", worker_thread.await);
            })
        }

        debug!("DohFrontend: stopped: {:?}", self);
        Ok(())
    }

    pub fn set_certificate(&mut self, certificate: &str) -> Result<()> {
        self.certificate = certificate.to_string();
        Ok(())
    }

    pub fn set_private_key(&mut self, private_key: &str) -> Result<()> {
        self.private_key = private_key.to_string();
        Ok(())
    }

    pub fn set_delay_queries(&self, value: i32) -> Result<()> {
        self.config.lock().unwrap().delay_queries = value;
        Ok(())
    }

    pub fn set_max_idle_timeout(&self, value: u64) -> Result<()> {
        self.config.lock().unwrap().max_idle_timeout = value;
        Ok(())
    }

    pub fn set_max_buffer_size(&self, value: u64) -> Result<()> {
        self.config.lock().unwrap().max_buffer_size = value;
        Ok(())
    }

    pub fn set_max_streams_bidi(&self, value: u64) -> Result<()> {
        self.config.lock().unwrap().max_streams_bidi = value;
        Ok(())
    }

    pub fn block_sending(&self, value: bool) -> Result<()> {
        self.config.lock().unwrap().block_sending = value;
        Ok(())
    }

    pub fn set_reset_stream_id(&self, value: u64) -> Result<()> {
        self.config.lock().unwrap().reset_stream_id = Some(value);
        Ok(())
    }

    pub fn request_stats(&mut self) -> Result<Stats> {
        ensure!(
            self.command_tx.is_some(),
            "command_tx is None because worker thread not yet initialized"
        );
        let command_tx = self.command_tx.as_ref().unwrap();

        if command_tx.is_closed() {
            return Ok(self.latest_stats.clone());
        }

        let (resp_tx, resp_rx) = oneshot::channel();
        command_tx.send(ControlCommand::Stats { resp: resp_tx })?;

        match RUNTIME_STATIC
            .block_on(async { tokio::time::timeout(Duration::from_secs(1), resp_rx).await })
        {
            Ok(v) => match v {
                Ok(stats) => {
                    self.latest_stats = stats.clone();
                    Ok(stats)
                }
                Err(e) => bail!(e),
            },
            Err(e) => bail!(e),
        }
    }

    pub fn stats_clear_queries(&self) -> Result<()> {
        ensure!(
            self.command_tx.is_some(),
            "command_tx is None because worker thread not yet initialized"
        );
        return self
            .command_tx
            .as_ref()
            .unwrap()
            .send(ControlCommand::StatsClearQueries)
            .or_else(|e| bail!(e));
    }

    fn init_worker_thread_params(&mut self) -> Result<WorkerParams> {
        let bind_addr =
            if self.backend_socket_addr.ip().is_ipv4() { "0.0.0.0:0" } else { "[::]:0" };
        let backend_socket = std::net::UdpSocket::bind(bind_addr)?;
        backend_socket.connect(self.backend_socket_addr)?;
        backend_socket.set_nonblocking(true)?;

        let frontend_socket = bind_udp_socket_retry(self.listen_socket_addr)?;
        frontend_socket.set_nonblocking(true)?;

        let clients = ClientMap::new(create_quiche_config(
            self.certificate.to_string(),
            self.private_key.to_string(),
            self.config.clone(),
        )?)?;

        let (command_tx, command_rx) = mpsc::unbounded_channel::<ControlCommand>();
        self.command_tx = Some(command_tx);

        Ok(WorkerParams {
            frontend_socket,
            backend_socket,
            clients,
            config: self.config.clone(),
            command_rx,
        })
    }

    fn wait_for_connections_closed(&mut self) -> Result<()> {
        for _ in 0..3 {
            std::thread::sleep(Duration::from_millis(50));
            match self.request_stats() {
                Ok(stats) if stats.alive_connections == 0 => return Ok(()),
                Ok(_) => (),

                // The worker thread is down. No connection is alive.
                Err(_) => return Ok(()),
            }
        }
        bail!("Some connections still alive")
    }
}

async fn worker_thread(params: WorkerParams) -> Result<()> {
    let backend_socket = into_tokio_udp_socket(params.backend_socket)?;
    let frontend_socket = into_tokio_udp_socket(params.frontend_socket)?;
    let config = params.config;
    let (event_tx, mut event_rx) = mpsc::unbounded_channel::<InternalCommand>();
    let mut command_rx = params.command_rx;
    let mut clients = params.clients;
    let mut frontend_buf = [0; 65535];
    let mut backend_buf = [0; 16384];
    let mut delay_queries_buffer: Vec<Vec<u8>> = vec![];
    let mut queries_received = 0;

    debug!("frontend={:?}, backend={:?}", frontend_socket, backend_socket);

    loop {
        let timeout = clients
            .iter_mut()
            .filter_map(|(_, c)| c.timeout())
            .min()
            .unwrap_or_else(|| Duration::from_millis(QUICHE_IDLE_TIMEOUT_MS));

        tokio::select! {
            _ = tokio::time::sleep(timeout) => {
                debug!("timeout");
                for (_, client) in clients.iter_mut() {
                    // If no timeout has occurred it does nothing.
                    client.on_timeout();

                    let connection_id = client.connection_id().clone();
                    event_tx.send(InternalCommand::MaybeWrite{connection_id})?;
                }
            }

            Ok((len, peer)) = frontend_socket.recv_from(&mut frontend_buf) => {
                debug!("Got {} bytes from {}", len, peer);

                // Parse QUIC packet.
                let pkt_buf = &mut frontend_buf[..len];
                let hdr = match quiche::Header::from_slice(pkt_buf, CONN_ID_LEN) {
                    Ok(v) => v,
                    Err(e) => {
                        error!("Failed to parse QUIC header: {:?}", e);
                        continue;
                    }
                };
                debug!("Got QUIC packet: {:?}", hdr);

                let local = frontend_socket.local_addr()?;
                let client = match clients.get_or_create(&hdr, &peer, &local) {
                    Ok(v) => v,
                    Err(e) => {
                        error!("Failed to get the client by the hdr {:?}: {}", hdr, e);
                        continue;
                    }
                };
                debug!("Got client: {:?}", client);

                match client.handle_frontend_message(pkt_buf, &local) {
                    Ok(v) if !v.is_empty() => {
                        delay_queries_buffer.push(v);
                        queries_received += 1;
                    }
                    Err(e) => {
                        error!("Failed to process QUIC packet: {}", e);
                        continue;
                    }
                    _ => {}
                }

                if delay_queries_buffer.len() >= config.lock().unwrap().delay_queries as usize {
                    for query in delay_queries_buffer.drain(..) {
                        debug!("sending {} bytes to backend", query.len());
                        backend_socket.send(&query).await?;
                    }
                }

                let connection_id = client.connection_id().clone();
                event_tx.send(InternalCommand::MaybeWrite{connection_id})?;
            }

            Ok((len, src)) = backend_socket.recv_from(&mut backend_buf) => {
                debug!("Got {} bytes from {}", len, src);
                if len < DNS_HEADER_SIZE {
                    error!("Received insufficient bytes for DNS header");
                    continue;
                }

                let query_id = [backend_buf[0], backend_buf[1]];
                for (_, client) in clients.iter_mut() {
                    if client.is_waiting_for_query(&query_id) {
                        let reset_stream_id = config.lock().unwrap().reset_stream_id;
                        if let Err(e) = client.handle_backend_message(&backend_buf[..len], reset_stream_id) {
                            error!("Failed to handle message from backend: {}", e);
                        }
                        let connection_id = client.connection_id().clone();
                        event_tx.send(InternalCommand::MaybeWrite{connection_id})?;

                        // It's a bug if more than one client is waiting for this query.
                        break;
                    }
                }
            }

            Some(command) = event_rx.recv(), if !config.lock().unwrap().block_sending => {
                match command {
                    InternalCommand::MaybeWrite {connection_id} => {
                        if let Some(client) = clients.get_mut(&connection_id) {
                            while let Ok(v) = client.flush_egress() {
                                let addr = client.addr();
                                debug!("Sending {} bytes to client {}", v.len(), addr);
                                if let Err(e) = frontend_socket.send_to(&v, addr).await {
                                    error!("Failed to send packet to {:?}: {:?}", client, e);
                                }
                            }
                            client.process_pending_answers()?;
                        }
                    }
                }
            }
            Some(command) = command_rx.recv() => {
                debug!("ControlCommand: {:?}", command);
                match command {
                    ControlCommand::Stats {resp} => {
                        let stats = Stats {
                            queries_received,
                            connections_accepted: clients.len() as u32,
                            alive_connections: clients.iter().filter(|(_, client)| client.is_alive()).count() as u32,
                            resumed_connections: clients.iter().filter(|(_, client)| client.is_resumed()).count() as u32,
                            early_data_connections: clients.iter().filter(|(_, client)| client.handled_early_data()).count() as u32,
                        };
                        if let Err(e) = resp.send(stats) {
                            error!("Failed to send ControlCommand::Stats response: {:?}", e);
                        }
                    }
                    ControlCommand::StatsClearQueries => queries_received = 0,
                    ControlCommand::CloseConnection => {
                        for (_, client) in clients.iter_mut() {
                            client.close();
                            event_tx.send(InternalCommand::MaybeWrite { connection_id: client.connection_id().clone() })?;
                        }
                    }
                }
            }
        }
    }
}

fn create_quiche_config(
    certificate: String,
    private_key: String,
    config: Arc<Mutex<Config>>,
) -> Result<quiche::Config> {
    let mut quiche_config = quiche::Config::new(quiche::PROTOCOL_VERSION)?;

    // Use pipe as a file path for Quiche to read the certificate and the private key.
    let (rd, mut wr) = build_pipe()?;
    let handle = std::thread::spawn(move || {
        wr.write_all(certificate.as_bytes()).expect("Failed to write to pipe");
    });
    let filepath = format!("/proc/self/fd/{}", rd.as_raw_fd());
    quiche_config.load_cert_chain_from_pem_file(&filepath)?;
    handle.join().unwrap();

    let (rd, mut wr) = build_pipe()?;
    let handle = std::thread::spawn(move || {
        wr.write_all(private_key.as_bytes()).expect("Failed to write to pipe");
    });
    let filepath = format!("/proc/self/fd/{}", rd.as_raw_fd());
    quiche_config.load_priv_key_from_pem_file(&filepath)?;
    handle.join().unwrap();

    quiche_config.set_application_protos(quiche::h3::APPLICATION_PROTOCOL)?;
    quiche_config.set_max_idle_timeout(config.lock().unwrap().max_idle_timeout);
    quiche_config.set_max_recv_udp_payload_size(MAX_UDP_PAYLOAD_SIZE);

    let max_buffer_size = config.lock().unwrap().max_buffer_size;
    quiche_config.set_initial_max_data(max_buffer_size);
    quiche_config.set_initial_max_stream_data_bidi_local(max_buffer_size);
    quiche_config.set_initial_max_stream_data_bidi_remote(max_buffer_size);
    quiche_config.set_initial_max_stream_data_uni(max_buffer_size);

    quiche_config.set_initial_max_streams_bidi(config.lock().unwrap().max_streams_bidi);
    quiche_config.set_initial_max_streams_uni(100);
    quiche_config.set_disable_active_migration(true);
    quiche_config.enable_early_data();

    Ok(quiche_config)
}

fn into_tokio_udp_socket(socket: std::net::UdpSocket) -> Result<UdpSocket> {
    match UdpSocket::from_std(socket) {
        Ok(v) => Ok(v),
        Err(e) => {
            error!("into_tokio_udp_socket failed: {}", e);
            bail!("into_tokio_udp_socket failed: {}", e)
        }
    }
}

fn build_pipe() -> Result<(File, File)> {
    let mut fds = [0, 0];
    // SAFETY: The pointer we pass to `pipe` must be valid because it comes from a reference. The
    // file descriptors it returns must be valid and open, so they are safe to pass to
    // `File::from_raw_fd`.
    unsafe {
        if libc::pipe(fds.as_mut_ptr()) == 0 {
            return Ok((File::from_raw_fd(fds[0]), File::from_raw_fd(fds[1])));
        }
    }
    Err(anyhow::Error::new(std::io::Error::last_os_error()).context("build_pipe failed"))
}

// Can retry to bind the socket address if it is in use.
fn bind_udp_socket_retry(addr: std::net::SocketAddr) -> Result<std::net::UdpSocket> {
    for _ in 0..3 {
        match std::net::UdpSocket::bind(addr) {
            Ok(socket) => return Ok(socket),
            Err(e) if e.kind() == std::io::ErrorKind::AddrInUse => {
                warn!("Binding socket address {} that is in use. Try again", addr);
                std::thread::sleep(Duration::from_millis(50));
            }
            Err(e) => return Err(anyhow::anyhow!(e)),
        }
    }
    Err(anyhow::anyhow!(std::io::Error::last_os_error()))
}
