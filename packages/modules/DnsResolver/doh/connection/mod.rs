/*
* Copyright (C) 2021 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

//! Module providing an async abstraction around a quiche HTTP/3 connection

use crate::boot_time::BootTime;
use crate::connection::driver::Cause;
use crate::connection::driver::HandshakeInfo;
use crate::network::ServerInfo;
use crate::network::SocketTagger;
use log::{debug, error, warn};
use quiche::h3;
use std::future::Future;
use std::io;
use std::net::SocketAddr;
use thiserror::Error;
use tokio::net::UdpSocket;
use tokio::sync::{mpsc, oneshot, watch};
use tokio::task;

pub mod driver;

pub use driver::Stream;
use driver::{drive, Request};

#[derive(Debug, Clone)]
pub enum Status {
    QUIC,
    H3,
    Dead {
        /// The session of the closed connection.
        session: Option<Vec<u8>>,
    },
}

/// Quiche HTTP/3 connection
pub struct Connection {
    request_tx: mpsc::Sender<Request>,
    status_rx: watch::Receiver<Status>,
}

fn new_scid() -> [u8; quiche::MAX_CONN_ID_LEN] {
    use ring::rand::{SecureRandom, SystemRandom};
    let mut scid = [0; quiche::MAX_CONN_ID_LEN];
    SystemRandom::new().fill(&mut scid).unwrap();
    scid
}

fn mark_socket(socket: &std::net::UdpSocket, socket_mark: u32) -> io::Result<()> {
    use std::os::unix::io::AsRawFd;
    let fd = socket.as_raw_fd();
    // SAFETY: libc::setsockopt is a wrapper function calling into bionic setsockopt.
    // The only pointer being passed in is &socket_mark, which is valid by virtue of being a
    // reference, and the foreign function doesn't take ownership or a reference to that memory
    // after completion.
    if unsafe {
        libc::setsockopt(
            fd,
            libc::SOL_SOCKET,
            libc::SO_MARK,
            &socket_mark as *const _ as *const libc::c_void,
            std::mem::size_of::<u32>() as libc::socklen_t,
        )
    } == 0
    {
        Ok(())
    } else {
        Err(io::Error::last_os_error())
    }
}

async fn build_socket(
    peer_addr: SocketAddr,
    socket_mark: u32,
    tag_socket: &SocketTagger,
) -> io::Result<UdpSocket> {
    let bind_addr = match peer_addr {
        SocketAddr::V4(_) => "0.0.0.0:0",
        SocketAddr::V6(_) => "[::]:0",
    };

    let socket = UdpSocket::bind(bind_addr).await?;
    let std_socket = socket.into_std()?;
    mark_socket(&std_socket, socket_mark)
        .unwrap_or_else(|e| error!("Unable to mark socket : {:?}", e));
    tag_socket(&std_socket).await;
    let socket = UdpSocket::from_std(std_socket)?;
    socket.connect(peer_addr).await?;
    Ok(socket)
}

/// Error type for HTTP/3 connection
#[derive(Debug, Error)]
pub enum Error {
    /// QUIC protocol error
    #[error("QUIC error: {0}")]
    Quic(#[from] quiche::Error),
    /// HTTP/3 protocol error
    #[error("HTTP/3 error: {0}")]
    H3(#[from] h3::Error),
    /// Unable to send the request to the driver. This likely means the
    /// backing task has died.
    #[error("Unable to send request")]
    SendRequest(#[from] mpsc::error::SendError<Request>),
    /// IO failed. This is most likely to occur while trying to set up the
    /// UDP socket for use by the connection.
    #[error("IO error: {0}")]
    Io(#[from] io::Error),
    /// The request is no longer being serviced. This could mean that the
    /// request was dropped for an unspecified reason, or that the connection
    /// was closed prematurely and it can no longer be serviced.
    #[error("Driver dropped request")]
    RecvResponse(#[from] oneshot::error::RecvError),
}

/// Common result type for working with a HTTP/3 connection
pub type Result<T> = std::result::Result<T, Error>;

impl Connection {
    const MAX_PENDING_REQUESTS: usize = 10;
    /// Create a new connection with a background task handling IO.
    pub async fn new(
        info: &ServerInfo,
        tag_socket: &SocketTagger,
        config: &mut quiche::Config,
        session: Option<Vec<u8>>,
        cause: Cause,
    ) -> Result<Self> {
        let server_name = info.domain.as_deref();
        let to = info.peer_addr;
        let socket_mark = info.sk_mark;
        let net_id = info.net_id;
        let (request_tx, request_rx) = mpsc::channel(Self::MAX_PENDING_REQUESTS);
        let (status_tx, status_rx) = watch::channel(Status::QUIC);
        let scid = new_scid();
        let socket = build_socket(to, socket_mark, tag_socket).await?;
        let from = socket.local_addr()?;

        let mut quiche_conn =
            quiche::connect(server_name, &quiche::ConnectionId::from_ref(&scid), from, to, config)?;

        // We will fall back to a full handshake if the session is expired.
        if let Some(session) = session {
            debug!("Setting session");
            quiche_conn.set_session(&session)?;
        }

        let handshake_info = HandshakeInfo {
            cause,
            sent_bytes: 0,
            recv_bytes: 0,
            elapsed: 0,
            quic_version: 0,
            network_type: info.network_type,
            private_dns_mode: info.private_dns_mode,
            session_hit_checker: quiche_conn.session().is_some(),
        };

        let driver = async move {
            let result =
                drive(request_rx, status_tx, quiche_conn, socket, net_id, handshake_info).await;
            if let Err(ref e) = result {
                warn!("Connection driver returns some Err: {:?}", e);
            }
            result
        };
        task::spawn(driver);
        Ok(Self { request_tx, status_rx })
    }

    /// Waits until we're either fully alive or dead
    pub async fn wait_for_live(&mut self) -> bool {
        // Once sc-mainline-prod updates to modern tokio, use
        // borrow_and_update here.
        match &*self.status_rx.borrow() {
            Status::H3 => return true,
            Status::Dead { .. } => return false,
            Status::QUIC => (),
        }
        if self.status_rx.changed().await.is_err() {
            // status_tx is gone, we're dead
            return false;
        }
        if matches!(*self.status_rx.borrow(), Status::H3) {
            return true;
        }
        // Since we're stuck on legacy tokio due to mainline, we need to try one more time in case there was an outstanding change notification. Using borrow_and_update avoids this.
        match self.status_rx.changed().await {
            // status_tx is gone, we're dead
            Err(_) => false,
            // If there's an HTTP/3 connection now we're alive, otherwise we're stuck/dead
            _ => matches!(*self.status_rx.borrow(), Status::H3),
        }
    }

    pub fn session(&self) -> Option<Vec<u8>> {
        match &*self.status_rx.borrow() {
            Status::Dead { session } => session.clone(),
            _ => None,
        }
    }

    /// Send a query, produce a future which will provide a response.
    /// The future is separately returned rather than awaited to allow it to be waited on without
    /// keeping the `Connection` itself borrowed.
    pub async fn query(
        &self,
        headers: Vec<h3::Header>,
        expiry: Option<BootTime>,
    ) -> Result<impl Future<Output = Option<Stream>>> {
        let (response_tx, response_rx) = oneshot::channel();
        self.request_tx.send(Request { headers, response_tx, expiry }).await?;
        Ok(async move { response_rx.await.ok() })
    }
}
