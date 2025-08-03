//! Implementation of the Socket API (IBluetoothSocketManager).

use bt_topshim::btif::{BluetoothInterface, BtStatus, RawAddress, Uuid};
use bt_topshim::profiles::socket;
use log;
use nix::sys::socket::{recvmsg, ControlMessageOwned};
use nix::sys::uio::IoVec;
use std::collections::HashMap;
use std::convert::TryFrom;
use std::fmt;
use std::os::unix;
use std::os::unix::io::{AsRawFd, FromRawFd, IntoRawFd, RawFd};
use std::sync::{Arc, Mutex};
use std::time::Duration;
use tokio::net::UnixStream;
use tokio::runtime::{Builder, Runtime};
use tokio::sync::mpsc::{channel, Receiver, Sender};
use tokio::task::JoinHandle;
use tokio::time;

use crate::bluetooth::BluetoothDevice;
use crate::bluetooth_admin::{BluetoothAdmin, IBluetoothAdmin};
use crate::callbacks::Callbacks;
use crate::uuid::UuidHelper;
use crate::Message;
use crate::RPCProxy;

/// Type for unique identifier for each opened socket.
pub type SocketId = u64;

/// Type for callback identification.
pub type CallbackId = u32;

/// The underlying connection type for a socket.
pub type SocketType = socket::SocketType;

/// Result type for calls in `IBluetoothSocketManager`.
#[derive(Debug)]
pub struct SocketResult {
    pub status: BtStatus,
    pub id: u64,
}

impl SocketResult {
    fn new(status: BtStatus, id: u64) -> Self {
        SocketResult { status, id }
    }
}

/// Use this to select a dynamic PSM when creating socket.
pub const DYNAMIC_PSM_NO_SDP: i32 = -2;

/// Use this to select a dynamic channel when creating socket.
pub const DYNAMIC_CHANNEL: i32 = -1;

/// Socket ids are unsigned so make zero an invalid value.
pub const INVALID_SOCKET_ID: SocketId = 0;

/// Represents a listening socket.
#[derive(Clone, Debug)]
pub struct BluetoothServerSocket {
    pub id: SocketId,
    pub sock_type: SocketType,
    pub flags: i32,
    pub psm: Option<i32>,
    pub channel: Option<i32>,
    pub name: Option<String>,
    pub uuid: Option<Uuid>,
}

impl Default for BluetoothServerSocket {
    fn default() -> Self {
        BluetoothServerSocket::new()
    }
}

impl BluetoothServerSocket {
    fn new() -> Self {
        BluetoothServerSocket {
            id: 0,
            sock_type: SocketType::Unknown,
            flags: 0,
            psm: None,
            channel: None,
            name: None,
            uuid: None,
        }
    }

    fn make_l2cap_channel(flags: i32, is_le: bool) -> Self {
        BluetoothServerSocket {
            id: 0,
            sock_type: match is_le {
                true => SocketType::L2capLe,
                false => SocketType::L2cap,
            },
            flags: flags | socket::SOCK_FLAG_NO_SDP,
            psm: Some(DYNAMIC_PSM_NO_SDP),
            channel: None,
            name: None,
            uuid: None,
        }
    }

    fn make_rfcomm_channel(
        flags: i32,
        name: Option<String>,
        channel: Option<i32>,
        uuid: Option<Uuid>,
    ) -> Self {
        BluetoothServerSocket {
            id: 0,
            sock_type: SocketType::Rfcomm,
            flags,
            psm: None,
            channel: channel,
            name: name,
            uuid: uuid,
        }
    }

    fn make_default_rfcomm_channel(flags: i32, name: String, uuid: Uuid) -> Self {
        BluetoothServerSocket {
            id: 0,
            sock_type: SocketType::Rfcomm,
            flags,
            psm: None,
            channel: Some(DYNAMIC_CHANNEL),
            name: Some(name),
            uuid: Some(uuid),
        }
    }

    /// Creates a new BluetoothSocket using a connection complete event and the incoming file
    /// descriptor. The connected socket inherits the id of the listening socket.
    fn to_connecting_socket(
        &self,
        conn: socket::ConnectionComplete,
        sockfd: Option<RawFd>,
    ) -> BluetoothSocket {
        let mut sock = BluetoothSocket::new();

        // Data from server socket.
        sock.id = self.id;
        sock.sock_type = self.sock_type.clone();
        sock.flags = self.flags;
        sock.uuid = self.uuid.clone();

        // Data from connection.
        sock.remote_device = BluetoothDevice::new(conn.addr.to_string(), "".into());
        sock.port = conn.channel;
        sock.max_rx_size = conn.max_rx_packet_size.into();
        sock.max_tx_size = conn.max_tx_packet_size.into();
        sock.fd = match socket::try_from_fd(sockfd.unwrap_or(-1)) {
            Ok(v) => Some(v),
            Err(_) => None,
        };

        sock
    }
}

impl fmt::Display for BluetoothServerSocket {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(
            f,
            "port={}, type={:?}, name={}, uuid={}",
            match (self.psm, self.channel) {
                (Some(psm), Some(cn)) => format!("psm {} | cn {}", psm, cn),
                (None, Some(cn)) => format!("cn {}", cn),
                (Some(psm), None) => format!("psm {}", psm),
                (None, None) => format!("none"),
            },
            self.sock_type,
            self.name.as_ref().unwrap_or(&String::new()),
            match self.uuid {
                Some(u) => UuidHelper::to_string(&u.uu),
                None => "".to_string(),
            }
        )
    }
}

/// Represents a connected socket.
#[derive(Debug)]
pub struct BluetoothSocket {
    pub id: SocketId,
    pub remote_device: BluetoothDevice,
    pub sock_type: SocketType,
    pub flags: i32,
    pub fd: Option<std::fs::File>,
    pub port: i32,
    pub uuid: Option<Uuid>,
    pub max_rx_size: i32,
    pub max_tx_size: i32,
}

impl Default for BluetoothSocket {
    fn default() -> Self {
        BluetoothSocket::new()
    }
}

impl BluetoothSocket {
    fn new() -> Self {
        BluetoothSocket {
            id: 0,
            remote_device: BluetoothDevice::new(String::new(), String::new()),
            sock_type: SocketType::Unknown,
            flags: 0,
            fd: None,
            port: 0,
            uuid: None,
            max_rx_size: 0,
            max_tx_size: 0,
        }
    }

    fn make_l2cap_channel(flags: i32, device: BluetoothDevice, psm: i32, is_le: bool) -> Self {
        BluetoothSocket {
            id: 0,
            remote_device: device,
            sock_type: match is_le {
                true => SocketType::L2capLe,
                false => SocketType::L2cap,
            },
            flags,
            fd: None,
            port: psm,
            uuid: None,
            max_rx_size: -1,
            max_tx_size: -1,
        }
    }

    fn make_rfcomm_channel(flags: i32, device: BluetoothDevice, uuid: Uuid) -> Self {
        BluetoothSocket {
            id: 0,
            remote_device: device,
            sock_type: SocketType::Rfcomm,
            flags,
            fd: None,
            port: -1,
            uuid: Some(uuid),
            max_rx_size: -1,
            max_tx_size: -1,
        }
    }
}

impl fmt::Display for BluetoothSocket {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(
            f,
            "[{}]:{} (type: {:?}) (uuid: {})",
            self.remote_device.address,
            self.port,
            self.sock_type,
            match self.uuid {
                Some(u) => UuidHelper::to_string(&u.uu),
                None => "".to_string(),
            }
        )
    }
}

pub trait IBluetoothSocketManagerCallbacks: RPCProxy {
    /// Listening socket is ready to listen. This is sent each time a listening socket
    /// transitions to a non-listening state (i.e. a new listener opened or accept timed-out). The
    /// client must re-run accept each time this event is sent to accept additional connections.
    fn on_incoming_socket_ready(&mut self, socket: BluetoothServerSocket, status: BtStatus);

    /// Listening socket is closed. Reason is given in BtStatus.
    fn on_incoming_socket_closed(&mut self, listener_id: SocketId, reason: BtStatus);

    /// After listening on a socket, a connection is established. The socket is
    /// now owned by the caller and the caller is responsible for closing the
    /// socket.
    fn on_handle_incoming_connection(&mut self, listener_id: SocketId, connection: BluetoothSocket);

    /// Result of an outgoing socket connection. The actual socket is given only
    /// when the connection is successful.
    fn on_outgoing_connection_result(
        &mut self,
        connecting_id: SocketId,
        result: BtStatus,
        socket: Option<BluetoothSocket>,
    );
}

pub trait IBluetoothSocketManager {
    /// Register for socket callbacks. This must be called before calling any of
    /// the apis below or they will always fail (because a socket id will be
    /// associated with a specific callback).
    fn register_callback(
        &mut self,
        callback: Box<dyn IBluetoothSocketManagerCallbacks + Send>,
    ) -> CallbackId;

    /// Unregister for socket callbacks.
    fn unregister_callback(&mut self, callback: CallbackId) -> bool;

    /// Create an insecure listening L2CAP socket. PSM is dynamically assigned.
    fn listen_using_insecure_l2cap_channel(&mut self, callback: CallbackId) -> SocketResult;

    /// Create an insecure listening L2CAP LE socket. PSM is dynamically assigned.
    fn listen_using_insecure_l2cap_le_channel(&mut self, callback: CallbackId) -> SocketResult;

    /// Create an insecure listening RFCOMM socket. Channel is dynamically assigned.
    fn listen_using_insecure_rfcomm_with_service_record(
        &mut self,
        callback: CallbackId,
        name: String,
        uuid: Uuid,
    ) -> SocketResult;

    /// Create a secure listening L2CAP socket. PSM is dynamically assigned.
    fn listen_using_l2cap_channel(&mut self, callback: CallbackId) -> SocketResult;

    /// Create a secure listening L2CAP LE socket. PSM is dynamically assigned.
    fn listen_using_l2cap_le_channel(&mut self, callback: CallbackId) -> SocketResult;

    /// Create a secure listening RFCOMM socket. Channel is dynamically assigned.
    fn listen_using_rfcomm_with_service_record(
        &mut self,
        callback: CallbackId,
        name: String,
        uuid: Uuid,
    ) -> SocketResult;

    /// Generic method for setting up an RFCOMM listening socket.  Prefer to use one of the other
    /// RFCOMM listen methods when possible as they reflect the more preferred RFCOMM flows, but
    /// this method exposes all of the options that the stack supports.
    fn listen_using_rfcomm(
        &mut self,
        callback: CallbackId,
        channel: Option<i32>,
        application_uuid: Option<Uuid>,
        name: Option<String>,
        flags: Option<i32>,
    ) -> SocketResult;

    /// Create an insecure L2CAP connection.
    fn create_insecure_l2cap_channel(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        psm: i32,
    ) -> SocketResult;

    /// Create an insecure L2CAP LE connection.
    fn create_insecure_l2cap_le_channel(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        psm: i32,
    ) -> SocketResult;

    /// Create an insecure RFCOMM connection.
    fn create_insecure_rfcomm_socket_to_service_record(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        uuid: Uuid,
    ) -> SocketResult;

    /// Create a secure L2CAP connection.
    fn create_l2cap_channel(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        psm: i32,
    ) -> SocketResult;

    /// Create a secure L2CAP LE connection.
    fn create_l2cap_le_channel(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        psm: i32,
    ) -> SocketResult;

    /// Create an insecure RFCOMM connection.
    fn create_rfcomm_socket_to_service_record(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        uuid: Uuid,
    ) -> SocketResult;

    /// Start accepting connections on a listening socket.
    fn accept(&mut self, callback: CallbackId, id: SocketId, timeout_ms: Option<u32>) -> BtStatus;

    /// Close a listening socket.
    fn close(&mut self, callback: CallbackId, id: SocketId) -> BtStatus;
}

/// Internal listening socket data.
struct InternalListeningSocket {
    _callback_id: CallbackId,
    socket_id: SocketId,

    /// Channel to future that listens for `accept` and `close` signals.
    tx: Sender<SocketRunnerActions>,

    /// Used by admin
    uuid: Option<Uuid>,

    /// Used for tracing task status
    joinhandle: JoinHandle<()>,
}

impl InternalListeningSocket {
    fn new(
        _callback_id: CallbackId,
        socket_id: SocketId,
        tx: Sender<SocketRunnerActions>,
        uuid: Option<Uuid>,
        joinhandle: JoinHandle<()>,
    ) -> Self {
        InternalListeningSocket { _callback_id, socket_id, tx, uuid, joinhandle }
    }
}

/// Internal connecting socket data.
struct InternalConnectingSocket {
    _callback_id: CallbackId,
    socket_id: SocketId,

    /// Used for cleaning up
    joinhandle: JoinHandle<()>,
}

impl InternalConnectingSocket {
    fn new(_callback_id: CallbackId, socket_id: SocketId, joinhandle: JoinHandle<()>) -> Self {
        InternalConnectingSocket { _callback_id, socket_id, joinhandle }
    }
}

// This is a safe operation in an unsafe wrapper. Since a unix stream must have
// an open and valid file to operate on, converting to file via RawFd is just
// boilerplate.
fn unixstream_to_file(stream: UnixStream) -> std::fs::File {
    unsafe {
        std::fs::File::from_raw_fd(
            stream.into_std().expect("Failed to convert tokio unixstream").into_raw_fd(),
        )
    }
}

// This is a safe operation in an unsafe wrapper. A file is already open and owned
// so the only way this should fail is via a safe `from_std` call in tokio's
// UnixStream.
fn file_to_unixstream(fd: std::fs::File) -> Option<UnixStream> {
    let raw_stream = unsafe { unix::net::UnixStream::from_raw_fd(fd.into_raw_fd()) };
    match UnixStream::from_std(raw_stream) {
        Ok(v) => Some(v),
        Err(e) => {
            log::error!("Failed to convert file to unixstream: {}", e);
            None
        }
    }
}

/// Time to wait for a socket to connect before timing out.
/// TODO(abps) - Should this be configurable?
const CONNECT_COMPLETE_TIMEOUT_MS: u64 = 10000;

/// Actions to take on the socket in the socket runner.
pub(crate) enum SocketRunnerActions {
    /// Accept connections on a listening socket with an optional timeout.
    AcceptTimeout(SocketId, Option<Duration>),

    /// Close a listening socket.
    Close(SocketId),
}

/// Actions to take in message handler runner (RPC context). Many of these match
/// `IBluetoothSocketManagerCallbacks` so check there for documentation as well.
pub enum SocketActions {
    // First 3 events are for listening sockets.
    OnIncomingSocketReady(CallbackId, BluetoothServerSocket, BtStatus),
    OnIncomingSocketClosed(CallbackId, SocketId, BtStatus),
    OnHandleIncomingConnection(CallbackId, SocketId, BluetoothSocket),

    // This event is for connecting socket.
    OnOutgoingConnectionResult(CallbackId, SocketId, BtStatus, Option<BluetoothSocket>),

    // Request to disconnect all sockets, e.g. when user disconnects the peer device.
    DisconnectAll(RawAddress),
}

/// Implementation of the `IBluetoothSocketManager` api.
pub struct BluetoothSocketManager {
    /// Callbacks registered against the socket manager.
    callbacks: Callbacks<dyn IBluetoothSocketManagerCallbacks + Send>,

    /// List of listening sockets.
    listening: HashMap<CallbackId, Vec<InternalListeningSocket>>,

    /// List of connecting sockets with futures (so we can drop if callback disconnects).
    connecting: HashMap<CallbackId, Vec<InternalConnectingSocket>>,

    /// Separate runtime for socket listeners (so they're not dependent on the
    /// same runtime as RPC).
    runtime: Arc<Runtime>,

    /// Topshim interface for socket. Must call initialize for this to be valid.
    sock: Option<socket::BtSocket>,

    /// Monotonically increasing counter for socket id. Always access using
    /// `next_socket_id`.
    socket_counter: SocketId,

    /// Channel TX for the mainloop for topstack.
    tx: Sender<Message>,

    /// Admin
    admin: Arc<Mutex<Box<BluetoothAdmin>>>,
}

impl BluetoothSocketManager {
    /// Constructs the IBluetooth implementation.
    pub fn new(tx: Sender<Message>, admin: Arc<Mutex<Box<BluetoothAdmin>>>) -> Self {
        let callbacks = Callbacks::new(tx.clone(), Message::SocketManagerCallbackDisconnected);
        let socket_counter: u64 = 1000;
        let connecting = HashMap::new();
        let listening = HashMap::new();
        let runtime = Arc::new(
            Builder::new_multi_thread()
                .worker_threads(1)
                .max_blocking_threads(1)
                .enable_all()
                .build()
                .expect("Failed to make socket runtime."),
        );

        BluetoothSocketManager {
            callbacks,
            connecting,
            listening,
            runtime,
            sock: None,
            socket_counter,
            tx,
            admin,
        }
    }

    /// In order to access the underlying socket apis, we must initialize after
    /// the btif layer has initialized. Thus, this must be called after intf is
    /// init.
    pub fn initialize(&mut self, intf: Arc<Mutex<BluetoothInterface>>) {
        self.sock = Some(socket::BtSocket::new(&intf.lock().unwrap()));
    }

    // TODO(abps) - We need to save information about who the caller is so that
    //              we can pipe it down to the lower levels. This needs to be
    //              provided by the projection layer and is currently missing.
    fn get_caller_uid(&self) -> i32 {
        0
    }

    /// Get the next available socket id.
    fn next_socket_id(&mut self) -> SocketId {
        let next = self.socket_counter;
        self.socket_counter = next + 1;

        next
    }

    /// Common handler for |sock->listen| call.
    fn socket_listen(
        &mut self,
        mut socket_info: BluetoothServerSocket,
        cbid: CallbackId,
    ) -> SocketResult {
        if let Some(uuid) = socket_info.uuid {
            if !self.admin.lock().unwrap().is_service_allowed(uuid.into()) {
                log::debug!("service {} is blocked by admin policy", uuid);
                return SocketResult::new(BtStatus::AuthRejected, INVALID_SOCKET_ID);
            }
            if self
                .listening
                .iter()
                .any(|(_, v)| v.iter().any(|s| s.uuid.map_or(false, |u| u == uuid)))
            {
                log::warn!("Service {} already exists", uuid);
                return SocketResult::new(BtStatus::Fail, INVALID_SOCKET_ID);
            }
        }

        // Create listener socket pair
        let (mut status, result) =
            self.sock.as_ref().expect("Socket Manager not initialized").listen(
                socket_info.sock_type.clone(),
                socket_info.name.as_ref().unwrap_or(&String::new()).clone(),
                match socket_info.uuid {
                    Some(u) => Some(u.uu.clone()),
                    None => None,
                },
                match socket_info.sock_type {
                    SocketType::Rfcomm => socket_info.channel.unwrap_or(DYNAMIC_CHANNEL),
                    SocketType::L2cap | SocketType::L2capLe => {
                        socket_info.psm.unwrap_or(DYNAMIC_PSM_NO_SDP)
                    }
                    _ => 0,
                },
                socket_info.flags,
                self.get_caller_uid(),
            );

        // Put socket into listening list and return result.
        match result {
            Ok(file) => {
                // Push new socket into listeners.
                let id = self.next_socket_id();
                socket_info.id = id;
                let (runner_tx, runner_rx) = channel::<SocketRunnerActions>(10);
                let uuid = socket_info.uuid.clone();

                // Push a listening task to local runtime to wait for device to
                // start accepting or get closed.
                let rpc_tx = self.tx.clone();

                // If the stream can't be converted to filestream, fail early.
                let stream = match file_to_unixstream(file) {
                    Some(v) => v,
                    None => {
                        log::debug!("Converting from file to unixstream failed");
                        return SocketResult::new(BtStatus::Fail, INVALID_SOCKET_ID);
                    }
                };

                // We only send socket ready after we've read the channel out.
                let listen_status = status.clone();
                let joinhandle = self.runtime.spawn(async move {
                    BluetoothSocketManager::listening_task(
                        cbid,
                        listen_status,
                        runner_rx,
                        socket_info,
                        stream,
                        rpc_tx,
                    )
                    .await;
                });

                // Keep track of active listener sockets.
                self.listening
                    .entry(cbid)
                    .or_default()
                    .push(InternalListeningSocket::new(cbid, id, runner_tx, uuid, joinhandle));

                SocketResult::new(status, id)
            }
            Err(_) => {
                // Bad file descriptor but underlying api says success.
                if status == BtStatus::Success {
                    log::error!("Invalid socketpair but listen api succeeded.");
                    status = BtStatus::Fail;
                }

                log::error!("Failed to listen on {}. Status={:?}", socket_info, status);

                SocketResult::new(status, INVALID_SOCKET_ID)
            }
        }
    }

    /// Common handler for |sock->connect| call.
    fn socket_connect(
        &mut self,
        mut socket_info: BluetoothSocket,
        cbid: CallbackId,
    ) -> SocketResult {
        if let Some(uuid) = socket_info.uuid {
            if !self.admin.lock().unwrap().is_service_allowed(uuid.into()) {
                log::debug!("service {} is blocked by admin policy", uuid);
                return SocketResult::new(BtStatus::AuthRejected, INVALID_SOCKET_ID);
            }
        }

        let addr = match RawAddress::from_string(socket_info.remote_device.address.clone()) {
            Some(v) => v,
            None => {
                log::warn!(
                    "Invalid address during socket connection: {}",
                    socket_info.remote_device.address.clone()
                );
                return SocketResult::new(BtStatus::InvalidParam, INVALID_SOCKET_ID);
            }
        };

        // Create connecting socket pair.
        let (mut status, result) =
            self.sock.as_ref().expect("Socket manager not initialized").connect(
                addr,
                socket_info.sock_type.clone(),
                match socket_info.uuid {
                    Some(u) => Some(u.uu.clone()),
                    None => None,
                },
                socket_info.port,
                socket_info.flags,
                self.get_caller_uid(),
            );

        // Put socket into connecting list and return result. Connecting sockets
        // need to be listening for a completion event at which point they will
        // send the ready signal.
        match result {
            Ok(file) => {
                // Push new socket into connectors. These will wait until the
                // connection complete event is seen and then emit an event for
                // callbacks.
                let id = self.next_socket_id();
                socket_info.id = id;

                // Push a connecting task to local runtime to wait for connection
                // completion.
                let tx = self.tx.clone();
                let joinhandle = self.runtime.spawn(async move {
                    BluetoothSocketManager::connecting_task(
                        cbid,
                        id,
                        tx,
                        socket_info,
                        file_to_unixstream(file),
                        Duration::from_millis(CONNECT_COMPLETE_TIMEOUT_MS),
                    )
                    .await;
                });

                // Keep track of these futures in case they need to be cancelled due to callback
                // disconnecting.
                self.connecting
                    .entry(cbid)
                    .or_default()
                    .push(InternalConnectingSocket::new(cbid, id, joinhandle));

                SocketResult::new(status, id)
            }
            Err(_) => {
                if status == BtStatus::Success {
                    log::error!("Invalid socketpair but connect api succeeded.");
                    status = BtStatus::Fail;
                }

                log::error!("Failed to connect to {}. Status={:?}", socket_info, status);

                SocketResult::new(status, INVALID_SOCKET_ID)
            }
        }
    }

    async fn listening_task(
        cbid: CallbackId,
        listen_status: BtStatus,
        mut runner_rx: Receiver<SocketRunnerActions>,
        mut socket_info: BluetoothServerSocket,
        stream: UnixStream,
        rpc_tx: Sender<Message>,
    ) {
        let mut accepting: Option<JoinHandle<()>> = None;
        let stream = Arc::new(stream);

        let connection_timeout = Duration::from_millis(CONNECT_COMPLETE_TIMEOUT_MS);
        // Wait for stream to be readable, then read channel. This is the first thing that must
        // happen in the listening channel. If this fails, close the channel.
        let mut channel_bytes = [0 as u8; 4];
        let mut status =
            Self::wait_and_read_stream(connection_timeout, &stream, &mut channel_bytes).await;
        let channel = i32::from_ne_bytes(channel_bytes);
        if channel <= 0 {
            status = BtStatus::Fail;
        }

        // If we don't get a valid channel, consider the socket as closed.
        if status != BtStatus::Success {
            // First send the incoming socket ready signal and then closed. If we
            // are unable to read the channel, the client needs to consider the
            // socket as closed.
            let _ = rpc_tx
                .send(Message::SocketManagerActions(SocketActions::OnIncomingSocketReady(
                    cbid,
                    socket_info.clone(),
                    status,
                )))
                .await;
            let _ = rpc_tx
                .send(Message::SocketManagerActions(SocketActions::OnIncomingSocketClosed(
                    cbid,
                    socket_info.id,
                    BtStatus::Success,
                )))
                .await;

            return;
        }

        match socket_info.sock_type {
            SocketType::Rfcomm => socket_info.channel = Some(channel),
            SocketType::L2cap | SocketType::L2capLe => socket_info.psm = Some(channel),

            // Don't care about other types. We don't support them in this path.
            _ => (),
        };
        // Notify via callbacks that this socket is ready to be listened to since we have the
        // channel available now.
        let (forwarded_socket, forwarded_status) = (socket_info.clone(), listen_status.clone());
        let _ = rpc_tx
            .send(Message::SocketManagerActions(SocketActions::OnIncomingSocketReady(
                cbid,
                forwarded_socket,
                forwarded_status,
            )))
            .await;

        loop {
            let m = match runner_rx.recv().await {
                Some(v) => v,
                None => {
                    break;
                }
            };

            match m {
                SocketRunnerActions::AcceptTimeout(socket_id, may_timeout) => {
                    // If the given socket id doesn't match, ignore the call.
                    if &socket_id != &socket_info.id {
                        continue;
                    }

                    // Cancel the previous future before continuing.
                    if let Some(ref handle) = accepting {
                        handle.abort();
                    }

                    let tx = rpc_tx.clone();
                    let cloned_socket_info = socket_info.clone();
                    let cstream = stream.clone();

                    // Replace the previous joinhandle.
                    accepting = Some(tokio::spawn(async move {
                        loop {
                            let readable = if let Some(timeout) = may_timeout {
                                match time::timeout(timeout, cstream.readable()).await {
                                    // Result ok means ready to read.
                                    Ok(r) => r.is_ok(),
                                    // Timeout means we exit this future after sending.
                                    Err(_) => false,
                                }
                            } else {
                                cstream.readable().await.is_ok()
                            };

                            // Anytime the readable future completes but isn't readable,
                            // we send a socket ready with a failed status message (you
                            // can try accepting again).
                            if !readable {
                                let _ = tx
                                    .send(Message::SocketManagerActions(
                                        SocketActions::OnIncomingSocketReady(
                                            cbid,
                                            cloned_socket_info,
                                            BtStatus::Timeout,
                                        ),
                                    ))
                                    .await;
                                break;
                            }

                            // Read the accepted socket information and use
                            // CMSG to grab the sockets also transferred over
                            // this socket.
                            let rawfd = cstream.as_raw_fd();
                            let socket_info_inner = cloned_socket_info.clone();
                            let sock: std::io::Result<Option<BluetoothSocket>> =
                                cstream.try_io(tokio::io::Interest::READABLE, || {
                                    let mut data = [0; socket::CONNECT_COMPLETE_SIZE];
                                    let iov = [IoVec::from_mut_slice(&mut data)];
                                    let mut cspace = nix::cmsg_space!(RawFd);
                                    let maybe_sock = match recvmsg(
                                        rawfd,
                                        &iov,
                                        Some(&mut cspace),
                                        nix::sys::socket::MsgFlags::MSG_DONTWAIT,
                                    ) {
                                        Ok(recv) => {
                                            let fd = match recv.cmsgs().next() {
                                                Some(ControlMessageOwned::ScmRights(fds)) => {
                                                    if fds.len() == 1 {
                                                        Some(fds[0])
                                                    } else {
                                                        log::error!(
                                                            "Unexpected number of fds given: {}",
                                                            fds.len()
                                                        );
                                                        None
                                                    }
                                                }
                                                _ => {
                                                    log::error!(
                                                        "Ancillary fds not found in connection."
                                                    );
                                                    None
                                                }
                                            };

                                            return match socket::ConnectionComplete::try_from(
                                                &data[0..socket::CONNECT_COMPLETE_SIZE],
                                            ) {
                                                Ok(cc) => {
                                                    let status = BtStatus::from(cc.status as u32);
                                                    let sock = socket_info_inner
                                                        .to_connecting_socket(cc, fd);

                                                    if status == BtStatus::Success
                                                        && sock.fd.is_some()
                                                    {
                                                        Ok(Some(sock))
                                                    } else {
                                                        Ok(None)
                                                    }
                                                }
                                                Err(_) => Ok(None),
                                            };
                                        }

                                        Err(e) => {
                                            if e == nix::errno::Errno::EAGAIN {
                                                Err(std::io::Error::new(
                                                    std::io::ErrorKind::WouldBlock,
                                                    "Recvfrom is readable but would block on read",
                                                ))
                                            } else {
                                                Ok(None)
                                            }
                                        }
                                    };

                                    maybe_sock
                                });

                            // If we returned an error for the above socket, then the recv failed.
                            // Just continue this loop.
                            if !sock.is_ok() {
                                continue;
                            }

                            match sock.unwrap_or(None) {
                                Some(s) => {
                                    let _ = tx
                                        .send(Message::SocketManagerActions(
                                            SocketActions::OnHandleIncomingConnection(
                                                cbid, s.id, s,
                                            ),
                                        ))
                                        .await;
                                }
                                // Exit out of the accepting state here.
                                None => {
                                    log::error!(
                                        "Incoming connection failed to recv: {}",
                                        cloned_socket_info
                                    );

                                    let _ = tx
                                        .send(Message::SocketManagerActions(
                                            SocketActions::OnIncomingSocketReady(
                                                cbid,
                                                cloned_socket_info,
                                                BtStatus::Fail,
                                            ),
                                        ))
                                        .await;

                                    break;
                                }
                            }
                        }
                    }));
                }
                SocketRunnerActions::Close(socket_id) => {
                    // Ignore requests where socket id doesn't match.
                    if &socket_id != &socket_info.id {
                        continue;
                    }

                    // First close any active accepting handle.
                    if let Some(ref handle) = accepting {
                        handle.abort();
                    }

                    // Notify RPC that we're closing.
                    let _ = rpc_tx
                        .send(Message::SocketManagerActions(SocketActions::OnIncomingSocketClosed(
                            cbid,
                            socket_info.id,
                            BtStatus::Success,
                        )))
                        .await;

                    // Now exit this task.
                    break;
                }
            }
        }
    }

    /// Helper function that waits for given stream to be readable and then reads the stream into
    /// the provided buffer.
    async fn wait_and_read_stream(
        timeout: Duration,
        stream: &UnixStream,
        buf: &mut [u8],
    ) -> BtStatus {
        // Wait on the stream to be readable.
        match time::timeout(timeout, stream.readable()).await {
            Ok(inner) => match inner {
                Ok(()) => {}
                Err(_e) => {
                    // Stream was not readable. This is usually due to some polling error.
                    return BtStatus::Fail;
                }
            },
            Err(_) => {
                // Timed out waiting for stream to be readable.
                return BtStatus::NotReady;
            }
        };

        match stream.try_read(buf) {
            Ok(n) => {
                if n != buf.len() {
                    return BtStatus::Fail;
                }
                return BtStatus::Success;
            }
            _ => {
                return BtStatus::Fail;
            }
        }
    }

    /// Task spawned on socket runtime to handle socket connections.
    ///
    /// This task will always result in a |SocketActions::OnOutgoingConnectionResult| message being
    /// sent and the result will depend on whether the connection is successful.
    async fn connecting_task(
        cbid: CallbackId,
        socket_id: SocketId,
        tx: Sender<Message>,
        socket_info: BluetoothSocket,
        stream: Option<UnixStream>,
        connection_timeout: Duration,
    ) {
        // If the unixstream isn't available for this connection, immediately return
        // a failure.
        let stream = match stream {
            Some(s) => s,
            None => {
                let _ = tx
                    .send(Message::SocketManagerActions(SocketActions::OnOutgoingConnectionResult(
                        cbid,
                        socket_id,
                        BtStatus::Fail,
                        None,
                    )))
                    .await;
                return;
            }
        };

        // Wait for stream to be readable, then read channel
        let mut channel_bytes = [0 as u8; 4];
        let mut status =
            Self::wait_and_read_stream(connection_timeout, &stream, &mut channel_bytes).await;
        if i32::from_ne_bytes(channel_bytes) <= 0 {
            status = BtStatus::Fail;
        }
        if status != BtStatus::Success {
            log::info!(
                "Connecting socket to {} failed while trying to read channel from stream",
                socket_info
            );
            let _ = tx
                .send(Message::SocketManagerActions(SocketActions::OnOutgoingConnectionResult(
                    cbid, socket_id, status, None,
                )))
                .await;
            return;
        }

        // Wait for stream to be readable, then read connect complete data
        let mut data = [0; socket::CONNECT_COMPLETE_SIZE];
        let status = Self::wait_and_read_stream(connection_timeout, &stream, &mut data).await;
        if status != BtStatus::Success {
            log::info!(
                "Connecting socket to {} failed while trying to read connect complete from stream",
                socket_info
            );
            let _ = tx
                .send(Message::SocketManagerActions(SocketActions::OnOutgoingConnectionResult(
                    cbid, socket_id, status, None,
                )))
                .await;
            return;
        }
        match socket::ConnectionComplete::try_from(&data[0..socket::CONNECT_COMPLETE_SIZE]) {
            Ok(cc) => {
                let status = BtStatus::from(cc.status as u32);
                if status != BtStatus::Success {
                    let _ = tx
                        .send(Message::SocketManagerActions(
                            SocketActions::OnOutgoingConnectionResult(
                                cbid,
                                socket_id,
                                status.clone(),
                                None,
                            ),
                        ))
                        .await;
                } else {
                    let mut sock = socket_info;
                    sock.fd = Some(unixstream_to_file(stream));
                    sock.port = cc.channel;
                    sock.max_rx_size = cc.max_rx_packet_size.into();
                    sock.max_tx_size = cc.max_tx_packet_size.into();

                    let _ = tx
                        .send(Message::SocketManagerActions(
                            SocketActions::OnOutgoingConnectionResult(
                                cbid,
                                socket_id,
                                status.clone(),
                                Some(sock),
                            ),
                        ))
                        .await;
                }
            }
            Err(err) => {
                log::info!("Unable to parse ConnectionComplete: {}", err);
                let _ = tx
                    .send(Message::SocketManagerActions(SocketActions::OnOutgoingConnectionResult(
                        cbid,
                        socket_id,
                        BtStatus::Fail,
                        None,
                    )))
                    .await;
            }
        }
    }

    pub fn handle_actions(&mut self, action: SocketActions) {
        match action {
            SocketActions::OnIncomingSocketReady(cbid, server_socket, status) => {
                if let Some(callback) = self.callbacks.get_by_id_mut(cbid) {
                    callback.on_incoming_socket_ready(server_socket, status);
                }
            }

            SocketActions::OnIncomingSocketClosed(cbid, socket_id, status) => {
                if let Some(callback) = self.callbacks.get_by_id_mut(cbid) {
                    callback.on_incoming_socket_closed(socket_id, status);

                    // Also make sure to remove the socket from listening list.
                    self.listening
                        .entry(cbid)
                        .and_modify(|v| v.retain(|s| s.socket_id != socket_id));
                }
            }

            SocketActions::OnHandleIncomingConnection(cbid, socket_id, socket) => {
                if let Some(callback) = self.callbacks.get_by_id_mut(cbid) {
                    callback.on_handle_incoming_connection(socket_id, socket);
                }
            }

            SocketActions::OnOutgoingConnectionResult(cbid, socket_id, status, socket) => {
                if let Some(callback) = self.callbacks.get_by_id_mut(cbid) {
                    callback.on_outgoing_connection_result(socket_id, status, socket);

                    // Also make sure to remove the socket from connecting list.
                    self.connecting
                        .entry(cbid)
                        .and_modify(|v| v.retain(|s| s.socket_id != socket_id));
                }
            }

            SocketActions::DisconnectAll(addr) => {
                self.sock.as_ref().expect("Socket Manager not initialized").disconnect_all(addr);
            }
        }
    }

    /// Close Rfcomm sockets whose UUID is not allowed by policy
    pub fn handle_admin_policy_changed(&mut self) {
        let forbidden_sockets = self
            .listening
            .values()
            .into_iter()
            .flatten()
            .filter(|sock| {
                sock.uuid
                    // Don't need to close L2cap socket (indicated by no uuid).
                    .map_or(false, |uuid| {
                        !self.admin.lock().unwrap().is_service_allowed(uuid.into())
                    })
            })
            .map(|sock| (sock.socket_id, sock.tx.clone(), sock.uuid.unwrap()))
            .collect::<Vec<(u64, Sender<SocketRunnerActions>, Uuid)>>();

        self.runtime.spawn(async move {
            for (id, tx, uuid) in forbidden_sockets {
                log::debug!(
                    "socket id {} is not allowed by admin policy due to uuid {}, closing",
                    id,
                    uuid
                );
                let _ = tx.send(SocketRunnerActions::Close(id)).await;
            }
        });
    }

    pub fn remove_callback(&mut self, callback: CallbackId) {
        // Remove any associated futures and sockets waiting to accept.
        self.connecting.remove(&callback).map(|sockets| {
            for s in sockets {
                s.joinhandle.abort();
            }
        });
        self.listening.remove(&callback).map(|sockets| {
            for s in sockets {
                if s.joinhandle.is_finished() {
                    continue;
                }
                let tx = s.tx.clone();
                let id = s.socket_id;
                self.runtime.spawn(async move {
                    let _ = tx.send(SocketRunnerActions::Close(id)).await;
                });
            }
        });
        self.callbacks.remove_callback(callback);
    }

    // Send MSC command to the peer. ONLY FOR QUALIFICATION USE.
    // libbluetooth auto starts the control request only when it is the client.
    // This function allows the host to start the control request while as a server.
    pub fn rfcomm_send_msc(&mut self, dlci: u8, addr: String) {
        match (|| -> Result<(), &str> {
            let addr = RawAddress::from_string(addr)
                .ok_or("Invalid address for starting control request")?;
            let sock = self
                .sock
                .as_ref()
                .ok_or("Socket Manager not initialized when starting control request")?;
            if sock.send_msc(dlci, addr) != BtStatus::Success {
                return Err("Failed to start control request");
            }
            Ok(())
        })() {
            Ok(_) => {}
            Err(msg) => log::warn!("{}", msg),
        };
    }
}

impl IBluetoothSocketManager for BluetoothSocketManager {
    fn register_callback(
        &mut self,
        callback: Box<dyn IBluetoothSocketManagerCallbacks + Send>,
    ) -> CallbackId {
        self.callbacks.add_callback(callback)
    }

    fn unregister_callback(&mut self, callback: CallbackId) -> bool {
        self.callbacks.remove_callback(callback)
    }

    fn listen_using_insecure_l2cap_channel(&mut self, callback: CallbackId) -> SocketResult {
        if self.callbacks.get_by_id(callback).is_none() {
            return SocketResult::new(BtStatus::NotReady, INVALID_SOCKET_ID);
        }

        let socket_info = BluetoothServerSocket::make_l2cap_channel(socket::SOCK_FLAG_NONE, false);
        self.socket_listen(socket_info, callback)
    }

    fn listen_using_insecure_l2cap_le_channel(&mut self, callback: CallbackId) -> SocketResult {
        if self.callbacks.get_by_id(callback).is_none() {
            return SocketResult::new(BtStatus::NotReady, INVALID_SOCKET_ID);
        }

        let socket_info = BluetoothServerSocket::make_l2cap_channel(socket::SOCK_FLAG_NONE, true);
        self.socket_listen(socket_info, callback)
    }

    fn listen_using_l2cap_channel(&mut self, callback: CallbackId) -> SocketResult {
        if self.callbacks.get_by_id(callback).is_none() {
            return SocketResult::new(BtStatus::NotReady, INVALID_SOCKET_ID);
        }

        let socket_info =
            BluetoothServerSocket::make_l2cap_channel(socket::SOCK_META_FLAG_SECURE, false);
        self.socket_listen(socket_info, callback)
    }

    fn listen_using_l2cap_le_channel(&mut self, callback: CallbackId) -> SocketResult {
        if self.callbacks.get_by_id(callback).is_none() {
            return SocketResult::new(BtStatus::NotReady, INVALID_SOCKET_ID);
        }

        let socket_info =
            BluetoothServerSocket::make_l2cap_channel(socket::SOCK_META_FLAG_SECURE, true);
        self.socket_listen(socket_info, callback)
    }

    fn listen_using_insecure_rfcomm_with_service_record(
        &mut self,
        callback: CallbackId,
        name: String,
        uuid: Uuid,
    ) -> SocketResult {
        if self.callbacks.get_by_id(callback).is_none() {
            return SocketResult::new(BtStatus::NotReady, INVALID_SOCKET_ID);
        }

        let socket_info =
            BluetoothServerSocket::make_default_rfcomm_channel(socket::SOCK_FLAG_NONE, name, uuid);
        self.socket_listen(socket_info, callback)
    }

    fn listen_using_rfcomm_with_service_record(
        &mut self,
        callback: CallbackId,
        name: String,
        uuid: Uuid,
    ) -> SocketResult {
        if self.callbacks.get_by_id(callback).is_none() {
            return SocketResult::new(BtStatus::NotReady, INVALID_SOCKET_ID);
        }

        let socket_info = BluetoothServerSocket::make_default_rfcomm_channel(
            socket::SOCK_META_FLAG_SECURE,
            name,
            uuid,
        );

        self.socket_listen(socket_info, callback)
    }

    fn listen_using_rfcomm(
        &mut self,
        callback: CallbackId,
        channel: Option<i32>,
        application_uuid: Option<Uuid>,
        name: Option<String>,
        flags: Option<i32>,
    ) -> SocketResult {
        if self.callbacks.get_by_id(callback).is_none() {
            return SocketResult::new(BtStatus::NotReady, INVALID_SOCKET_ID);
        }

        let flags = match flags {
            Some(flags) => flags,
            None => socket::SOCK_FLAG_NONE,
        };

        self.socket_listen(
            BluetoothServerSocket::make_rfcomm_channel(flags, name, channel, application_uuid),
            callback,
        )
    }

    fn create_insecure_l2cap_channel(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        psm: i32,
    ) -> SocketResult {
        if self.callbacks.get_by_id(callback).is_none() {
            return SocketResult::new(BtStatus::NotReady, INVALID_SOCKET_ID);
        }

        let socket_info =
            BluetoothSocket::make_l2cap_channel(socket::SOCK_FLAG_NONE, device, psm, false);
        self.socket_connect(socket_info, callback)
    }

    fn create_insecure_l2cap_le_channel(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        psm: i32,
    ) -> SocketResult {
        if self.callbacks.get_by_id(callback).is_none() {
            return SocketResult::new(BtStatus::NotReady, INVALID_SOCKET_ID);
        }

        let socket_info =
            BluetoothSocket::make_l2cap_channel(socket::SOCK_FLAG_NONE, device, psm, true);
        self.socket_connect(socket_info, callback)
    }

    fn create_l2cap_channel(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        psm: i32,
    ) -> SocketResult {
        if self.callbacks.get_by_id(callback).is_none() {
            return SocketResult::new(BtStatus::NotReady, INVALID_SOCKET_ID);
        }

        let socket_info =
            BluetoothSocket::make_l2cap_channel(socket::SOCK_META_FLAG_SECURE, device, psm, false);
        self.socket_connect(socket_info, callback)
    }

    fn create_l2cap_le_channel(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        psm: i32,
    ) -> SocketResult {
        if self.callbacks.get_by_id(callback).is_none() {
            return SocketResult::new(BtStatus::NotReady, INVALID_SOCKET_ID);
        }

        let socket_info =
            BluetoothSocket::make_l2cap_channel(socket::SOCK_META_FLAG_SECURE, device, psm, true);
        self.socket_connect(socket_info, callback)
    }

    fn create_insecure_rfcomm_socket_to_service_record(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        uuid: Uuid,
    ) -> SocketResult {
        if self.callbacks.get_by_id(callback).is_none() {
            return SocketResult::new(BtStatus::NotReady, INVALID_SOCKET_ID);
        }

        let socket_info =
            BluetoothSocket::make_rfcomm_channel(socket::SOCK_FLAG_NONE, device, uuid);
        self.socket_connect(socket_info, callback)
    }

    fn create_rfcomm_socket_to_service_record(
        &mut self,
        callback: CallbackId,
        device: BluetoothDevice,
        uuid: Uuid,
    ) -> SocketResult {
        if self.callbacks.get_by_id(callback).is_none() {
            return SocketResult::new(BtStatus::NotReady, INVALID_SOCKET_ID);
        }

        let socket_info =
            BluetoothSocket::make_rfcomm_channel(socket::SOCK_META_FLAG_SECURE, device, uuid);
        self.socket_connect(socket_info, callback)
    }

    fn accept(&mut self, callback: CallbackId, id: SocketId, timeout_ms: Option<u32>) -> BtStatus {
        match self.listening.get(&callback) {
            Some(v) => {
                if let Some(found) = v.iter().find(|item| item.socket_id == id) {
                    let tx = found.tx.clone();
                    let timeout_duration = match timeout_ms {
                        Some(t) => Some(Duration::from_millis(t.into())),
                        None => None,
                    };
                    self.runtime.spawn(async move {
                        let _ =
                            tx.send(SocketRunnerActions::AcceptTimeout(id, timeout_duration)).await;
                    });

                    return BtStatus::Success;
                }
            }
            None => (),
        }

        BtStatus::InvalidParam
    }

    fn close(&mut self, callback: CallbackId, id: SocketId) -> BtStatus {
        match self.listening.get(&callback) {
            Some(v) => {
                if let Some(found) = v.iter().find(|item| item.socket_id == id) {
                    let tx = found.tx.clone();
                    self.runtime.spawn(async move {
                        let _ = tx.send(SocketRunnerActions::Close(id)).await;
                    });

                    return BtStatus::Success;
                }
            }
            None => (),
        }

        BtStatus::InvalidParam
    }
}
