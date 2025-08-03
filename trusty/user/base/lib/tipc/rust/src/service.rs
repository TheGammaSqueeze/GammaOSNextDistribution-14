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

use alloc::rc::{Rc, Weak};
use core::fmt;
use core::mem::{ManuallyDrop, MaybeUninit};
use core::num::ParseIntError;
use log::{debug, error, warn};
use trusty_std::alloc::{AllocError, Vec};
use trusty_std::ffi::{CString, FallibleCString};
use trusty_std::TryClone;
use trusty_sys::c_void;

use crate::handle::MAX_MSG_HANDLES;
use crate::sys;
use crate::{ConnectResult, Deserialize, Handle, MessageResult, Result, TipcError};
use handle_set::HandleSet;

mod handle_set;

/// A description of a server-side IPC port.
///
/// A port configuration specifies the service port and various parameters for
/// the service. This configuration struct is a builder to set these parameters.
///
/// # Examples
///
/// ```
/// # impl Service for () {
/// #     type Connection = ();
/// #     type Message = ();
///
/// #     fn on_connect(
/// #         &self,
/// #         _port: &PortCfg,
/// #         _handle: &Handle,
/// #         _peer: &Uuid,
/// #     ) -> Result<ConnectResult<Self::Connection>> {
/// #         Ok(ConnectResult::Accept(()))
/// #     }
/// #
/// #     fn on_message(
/// #         &self,
/// #         _connection: &Self::Connection,
/// #         _handle: &Handle,
/// #         _msg: Self::Message,
/// #     ) -> Result<MessageResult> {
/// #         Ok(MessageResult::MaintainConnection)
/// #     }
/// # }
///
/// let cfg = PortCfg::new("com.android.trusty.rust_port_test")
///     .msg_queue_len(4)
///     .msg_max_size(4096)
///     .allow_ta_connect();
///
/// let service = ();
/// let buffer = [0u8; 4096];
/// let manager = Manager::new(service, cfg, buffer);
/// ```
#[derive(Debug, Eq, PartialEq)]
pub struct PortCfg {
    path: CString,
    msg_queue_len: u32,
    msg_max_size: u32,
    flags: u32,
}

impl PortCfg {
    /// Construct a new port configuration for the given path
    pub fn new<T: AsRef<str>>(path: T) -> Result<Self> {
        Ok(Self {
            path: CString::try_new(path.as_ref())?,
            msg_queue_len: 1,
            msg_max_size: 4096,
            flags: 0,
        })
    }

    /// Construct a new port configuration for the given path
    ///
    /// This version takes ownership of the path and does not allocate.
    pub fn new_raw(path: CString) -> Self {
        Self { path, msg_queue_len: 1, msg_max_size: 4096, flags: 0 }
    }

    /// Set the message queue length for this port configuration
    pub fn msg_queue_len(self, msg_queue_len: u32) -> Self {
        Self { msg_queue_len, ..self }
    }

    /// Set the message maximum length for this port configuration
    pub fn msg_max_size(self, msg_max_size: u32) -> Self {
        Self { msg_max_size, ..self }
    }

    /// Allow connections from non-secure (Android) clients for this port
    /// configuration
    pub fn allow_ns_connect(self) -> Self {
        Self { flags: self.flags | sys::IPC_PORT_ALLOW_NS_CONNECT as u32, ..self }
    }

    /// Allow connections from secure (Trusty) client for this port
    /// configuration
    pub fn allow_ta_connect(self) -> Self {
        Self { flags: self.flags | sys::IPC_PORT_ALLOW_TA_CONNECT as u32, ..self }
    }
}

impl TryClone for PortCfg {
    type Error = AllocError;

    fn try_clone(&self) -> core::result::Result<Self, Self::Error> {
        Ok(Self { path: self.path.try_clone()?, ..*self })
    }
}

pub(crate) struct Channel<D: Dispatcher> {
    handle: Handle,
    ty: ChannelTy<D>,
}

impl<D: Dispatcher> PartialEq for Channel<D> {
    fn eq(&self, other: &Self) -> bool {
        self.handle == other.handle
    }
}

impl<D: Dispatcher> Eq for Channel<D> {}

impl<D: Dispatcher> fmt::Debug for Channel<D> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        writeln!(f, "Channel {{")?;
        writeln!(f, "  handle: {:?},", self.handle)?;
        writeln!(f, "  ty: {:?},", self.ty)?;
        write!(f, "}}")
    }
}

enum ChannelTy<D: Dispatcher> {
    /// Service port with a configuration describing the port
    Port(PortCfg),

    /// Client connection
    Connection(D::Connection),
}

impl<D: Dispatcher> fmt::Debug for ChannelTy<D> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ChannelTy::Port(cfg) => write!(f, "ChannelTy::Port({:?})", cfg),
            ChannelTy::Connection(_) => write!(f, "ChannelTy::Connection"),
        }
    }
}

impl<D: Dispatcher> Channel<D> {
    pub fn handle(&self) -> &Handle {
        &self.handle
    }

    pub fn is_port(&self) -> bool {
        match self.ty {
            ChannelTy::Port(..) => true,
            _ => false,
        }
    }

    pub fn is_connection(&self) -> bool {
        match self.ty {
            ChannelTy::Connection(..) => true,
            _ => false,
        }
    }

    /// Reconstruct a reference to this type from an opaque pointer.
    ///
    /// SAFETY: The opaque pointer must have been constructed using
    /// Weak::into_raw, which happens in HandleSet::do_set_ctrl to create a
    /// connection cookie.
    unsafe fn from_opaque_ptr<'a>(ptr: *const c_void) -> Option<Rc<Self>> {
        if ptr.is_null() {
            None
        } else {
            // We must not drop the weak pointer here, because we are not
            // actually taking ownership of it.
            let weak = ManuallyDrop::new(Weak::from_raw(ptr.cast()));
            weak.upgrade()
        }
    }

    pub(crate) fn try_new_port(cfg: &PortCfg) -> Result<Rc<Self>> {
        // SAFETY: syscall, config path is borrowed and outlives the call.
        // Return value is either a negative error code or a valid handle.
        let rc = unsafe {
            trusty_sys::port_create(
                cfg.path.as_ptr(),
                cfg.msg_queue_len,
                cfg.msg_max_size,
                cfg.flags,
            )
        };
        if rc < 0 {
            Err(TipcError::from_uapi(rc))
        } else {
            Ok(Rc::try_new(Self {
                handle: Handle::from_raw(rc as i32)?,
                ty: ChannelTy::Port(cfg.try_clone()?),
            })?)
        }
    }

    fn try_new_connection(handle: Handle, data: D::Connection) -> Result<Rc<Self>> {
        Ok(Rc::try_new(Self { handle, ty: ChannelTy::Connection(data) })?)
    }
}

/// Trusty APP UUID
#[derive(Clone, Eq, PartialEq)]
pub struct Uuid(trusty_sys::uuid);

impl Uuid {
    const UUID_BYTE_LEN: usize = std::mem::size_of::<trusty_sys::uuid>();
    // UUID_STR_SIZE is a u32, conversion to usize is correct on our targeted architectures
    // Subtracting 1 from UUID_STR_SIZE because we don't need the null terminator on the RUST
    // implementation.
    const UUID_STR_LEN: usize = (sys::UUID_STR_SIZE as usize) - 1;
    const HYPHEN_SKIP_POS: [usize; 4] = [8, 4, 4, 4];
    const CLOCK_SEQ_AND_NODE_NUM_BYTES: usize = 8;

    pub const fn new(
        time_low: u32,
        time_mid: u16,
        time_hi_and_version: u16,
        clock_seq_and_node: [u8; 8],
    ) -> Self {
        Uuid(trusty_sys::uuid { time_low, time_mid, time_hi_and_version, clock_seq_and_node })
    }

    pub fn from_bytes(bytes: &[u8; Self::UUID_BYTE_LEN]) -> Self {
        // SAFETY: `bytes` has the exact same size size as `trusty_sys::uuid`, so this transmute
        //         copy is safe.
        let uuid = unsafe { std::mem::transmute_copy(bytes) };
        Uuid(uuid)
    }

    pub fn try_from_bytes(bytes: &[u8]) -> Result<Self> {
        let bytes: &[u8; Self::UUID_BYTE_LEN] = bytes.try_into().or(Err(TipcError::OutOfBounds))?;
        Ok(Self::from_bytes(bytes))
    }

    pub unsafe fn as_ptr(&self) -> *const trusty_sys::uuid {
        &self.0
    }

    pub fn new_from_string(uuid_str: &str) -> Result<Self> {
        // Helper function that first tries to convert the `uuid_element` bytes into a string and
        // then uses the provided `conversion_fn` to try to convert it into an integer, interpreting
        // the string as a hex number
        fn convert_uuid_element<T>(
            uuid_element: Option<&str>,
            conversion_fn: fn(&str, u32) -> core::result::Result<T, ParseIntError>,
        ) -> Result<T> {
            let uuid_element = uuid_element.ok_or(TipcError::InvalidData)?;
            conversion_fn(uuid_element, 16).map_err(|_| TipcError::InvalidData)
        }
        // Splitting a string into chunks using only std Rust facilities is not stable yet, so
        // providing a function for `Uuid` usage until it is stabilized.
        fn split_convert_string_byte_chunks(
            string_to_split: &str,
            result_buffer: &mut [u8],
        ) -> Result<()> {
            // Because our input is in hexadecimal format, to get a byte we need a string of size 2.
            let chunk_size = 2;
            if (string_to_split.len() % chunk_size) != 0 {
                return Err(TipcError::InvalidData);
            }
            let mut chunk;
            let mut remainder = string_to_split;
            for i in 0..(string_to_split.len() / chunk_size) {
                (chunk, remainder) = remainder.split_at(chunk_size);
                let converted_byte = convert_uuid_element(Some(chunk), u8::from_str_radix)?;
                result_buffer[i] = converted_byte;
            }
            Ok(())
        }
        // checking first that provided string is ASCII, so we can later split clock_seq_and_node in
        // byte chunks.
        if !uuid_str.is_ascii() {
            return Err(TipcError::InvalidData);
        }
        // Check that string has the correct length
        if uuid_str.len() != Self::UUID_STR_LEN {
            return Err(TipcError::InvalidData);
        }
        // Check that hyphens are in the correct positions.
        let mut uuid_chr_itr = uuid_str.chars();
        for skip_pos in Self::HYPHEN_SKIP_POS {
            if uuid_chr_itr.nth(skip_pos) != Some('-') {
                return Err(TipcError::InvalidData);
            }
        }
        // Splitting by the hyphens and checking that we do not end up with more elements than
        // expected. This checks that we didn't have some unexpected hyphens in the middle of the
        // string. This, along with the previous 2 checks should also check that all the elements
        // have the correct number of digits.
        let uuid_elements = uuid_str.split(|c| c == '-');
        if uuid_elements.count() != 5 {
            return Err(TipcError::InvalidData);
        }
        // separating uuid at the '-' now that we know that the string is of the correct length and
        // has the expected number of hyphens.
        let mut uuid_elements = uuid_str.split(|c| c == '-');
        let time_low = convert_uuid_element(uuid_elements.next(), u32::from_str_radix)?;
        let time_mid = convert_uuid_element(uuid_elements.next(), u16::from_str_radix)?;
        let time_hi_and_version = convert_uuid_element(uuid_elements.next(), u16::from_str_radix)?;
        // The last 8 bytes are split in 2 elements. RFC 4122 states that it is stored in Big Endian
        // format, so we are just going to concatenate the individual byte chunks of the 2 elements.
        let mut clock_seq_and_node = [0u8; Self::CLOCK_SEQ_AND_NODE_NUM_BYTES];
        let clock_seq = uuid_elements.next().ok_or(TipcError::InvalidData)?;
        // clock_seq contains the first 2 bytes
        split_convert_string_byte_chunks(clock_seq, &mut clock_seq_and_node[..2])?;
        let node = uuid_elements.next().ok_or(TipcError::InvalidData)?;
        // node contains the remaining 6 bytes
        split_convert_string_byte_chunks(node, &mut clock_seq_and_node[2..])?;
        Ok(Self::new(time_low, time_mid, time_hi_and_version, clock_seq_and_node))
    }
}

impl fmt::Debug for Uuid {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "{:08x}-{:04x}-{:04x}-",
            self.0.time_low, self.0.time_mid, self.0.time_hi_and_version
        )?;
        for (idx, b) in self.0.clock_seq_and_node.iter().enumerate() {
            write!(f, "{:02x}", b)?;
            if idx == 1 {
                write!(f, "-")?;
            }
        }
        Ok(())
    }
}

impl alloc::string::ToString for Uuid {
    fn to_string(&self) -> String {
        format!("{:?}", self)
    }
}

/// A service which handles IPC messages for a collection of ports.
///
/// A service which implements this interface can register itself, along with a
/// set of ports it handles, with a [`Manager`] which then dispatches
/// connection and message events to this service.
pub trait Service {
    /// Generic type to association with a connection. `on_connect()` should
    /// create this type for a successful connection.
    type Connection;

    /// Type of message this service can receive.
    type Message: Deserialize;

    /// Called when a client connects
    ///
    /// Returns either `Ok(Accept(Connection))` if the connection should be
    /// accepted or `Ok(CloseConnection)` if the connection should be closed.
    fn on_connect(
        &self,
        port: &PortCfg,
        handle: &Handle,
        peer: &Uuid,
    ) -> Result<ConnectResult<Self::Connection>>;

    /// Called when the service receives a message.
    ///
    /// The service manager handles deserializing the message, which is then
    /// passed to this callback.
    ///
    /// Should return `Ok(MaintainConnection)` if the connection should be kept open. The
    /// connection will be closed if `Ok(CloseConnection)` or `Err(_)` is returned.
    fn on_message(
        &self,
        connection: &Self::Connection,
        handle: &Handle,
        msg: Self::Message,
    ) -> Result<MessageResult>;

    /// Called when the client closes a connection.
    fn on_disconnect(&self, _connection: &Self::Connection) {}
}

pub trait UnbufferedService {
    /// Generic type to association with a connection. `on_connect()` should
    /// create this type for a successful connection.
    type Connection;

    /// Called when a client connects
    ///
    /// Returns either `Ok(Accept(Connection))` if the connection should be
    /// accepted or `Ok(CloseConnection)` if the connection should be closed.
    fn on_connect(
        &self,
        port: &PortCfg,
        handle: &Handle,
        peer: &Uuid,
    ) -> Result<ConnectResult<Self::Connection>>;

    /// Called when the service receives a message.
    ///
    /// The service is responsible for deserializing the message.
    /// A default implementation is provided that panics, for reasons of backwards
    /// compatibility with existing code. Any unbuffered service should implement this
    /// method and also provide a simple implementation for `on_message` that e.g. logs
    /// or panics.
    ///
    /// Should return `Ok(MaintainConnection)` if the connection should be kept open. The
    /// connection will be closed if `Ok(CloseConnection)` or `Err(_)` is returned.
    fn on_message(
        &self,
        connection: &Self::Connection,
        handle: &Handle,
        buffer: &mut [u8],
    ) -> Result<MessageResult>;

    /// Called when the client closes a connection.
    fn on_disconnect(&self, _connection: &Self::Connection) {}

    /// Get the maximum possible length of any message handled by this service
    fn max_message_length(&self) -> usize {
        0
    }
}

impl<T, U: Deserialize, V: Service<Connection = T, Message = U>> UnbufferedService for V {
    type Connection = <Self as Service>::Connection;

    fn on_connect(
        &self,
        port: &PortCfg,
        handle: &Handle,
        peer: &Uuid,
    ) -> Result<ConnectResult<<Self as UnbufferedService>::Connection>> {
        <Self as Service>::on_connect(self, port, handle, peer)
    }

    fn on_message(
        &self,
        connection: &<Self as UnbufferedService>::Connection,
        handle: &Handle,
        buffer: &mut [u8],
    ) -> Result<MessageResult> {
        let mut handles: [Option<Handle>; MAX_MSG_HANDLES] = Default::default();
        let (byte_count, handle_count) = handle.recv_vectored(&mut [buffer], &mut handles)?;
        let msg = <Self as Service>::Message::deserialize(
            &buffer[..byte_count],
            &mut handles[..handle_count],
        )
        .map_err(|e| {
            error!("Could not parse message: {:?}", e);
            TipcError::InvalidData
        })?;
        <Self as Service>::on_message(self, connection, handle, msg)
    }

    fn on_disconnect(&self, connection: &<Self as UnbufferedService>::Connection) {
        <Self as Service>::on_disconnect(self, connection)
    }

    fn max_message_length(&self) -> usize {
        <Self as Service>::Message::MAX_SERIALIZED_SIZE
    }
}

/// Wrap a service in a newtype.
///
/// This macro wraps an existing service in a newtype
/// that forwards the service trait implementation
/// (either [`Service`] or [`UnbufferedService`]) to
/// the wrapped service. This is useful when passing the
/// same service multiple times to the [`service_dispatcher!`]
/// macro, which requires that all the service types are distinct.
/// The wrapper(s) can be used to serve multiple ports using
/// the same service implementation.
///
/// [`service_dispatcher!`]: crate::service_dispatcher
///
/// # Examples
///
/// ```
/// // Create a new Service2 type that wraps Service1
/// wrap_service!(Service2(Service1: Service));
/// service_dispatcher! {
///     enum ServiceDispatcher {
///         Service1,
///         Service2,
///     }
/// }
/// ```
#[macro_export]
macro_rules! wrap_service {
    ($vis:vis $wrapper:ident ($inner:ty: Service)) => {
        $crate::wrap_service!(@common $vis $wrapper $inner);
        $crate::wrap_service!(@buffered $wrapper $inner);
    };

    ($vis:vis $wrapper:ident ($inner:ty: UnbufferedService)) => {
        $crate::wrap_service!(@common $vis $wrapper $inner);
        $crate::wrap_service!(@unbuffered $wrapper $inner);
    };

    (@common $vis:vis $wrapper:ident $inner:ty) => {
        $vis struct $wrapper($inner);

        #[allow(dead_code)] // These might not be used by anything
        impl $wrapper {
            $vis fn new(inner: $inner) -> Self { Self(inner) }
            $vis fn inner(&self) -> &$inner { &self.0 }
            $vis fn inner_mut(&mut self) -> &mut $inner { &mut self.0 }
        }
    };

    (@buffered $wrapper:ident $inner:ty) => {
        impl $crate::Service for $wrapper {
            type Connection = <$inner as $crate::Service>::Connection;
            type Message = <$inner as $crate::Service>::Message;

            fn on_connect(
                &self,
                port: &$crate::PortCfg,
                handle: &$crate::Handle,
                peer: &$crate::Uuid,
            ) -> $crate::Result<$crate::ConnectResult<Self::Connection>> {
                <$inner as $crate::Service>::on_connect(&self.0, port, handle, peer)
            }

            fn on_message(
                &self,
                connection: &Self::Connection,
                handle: &$crate::Handle,
                msg: Self::Message,
            ) -> $crate::Result<$crate::MessageResult> {
                <$inner as $crate::Service>::on_message(&self.0, connection, handle, msg)
            }

            fn on_disconnect(&self, connection: &Self::Connection) {
                <$inner as $crate::Service>::on_disconnect(&self.0, connection)
            }
        }
    };

    (@unbuffered $wrapper:ident $inner:ty) => {
        impl $crate::UnbufferedService for $wrapper {
            type Connection = <$inner as $crate::UnbufferedService>::Connection;

            fn on_connect(
                &self,
                port: &$crate::PortCfg,
                handle: &$crate::Handle,
                peer: &$crate::Uuid,
            ) -> $crate::Result<$crate::ConnectResult<Self::Connection>> {
                <$inner as $crate::UnbufferedService>::on_connect(&self.0, port, handle, peer)
            }

            fn on_message(
                &self,
                connection: &Self::Connection,
                handle: &$crate::Handle,
                buffer: &mut [u8],
            ) -> $crate::Result<$crate::MessageResult> {
                <$inner as $crate::UnbufferedService>::on_message(&self.0, connection, handle, buffer)
            }

            fn on_disconnect(&self, connection: &Self::Connection) {
                <$inner as $crate::UnbufferedService>::on_disconnect(&self.0, connection)
            }

            fn max_message_length(&self) -> usize {
                <$inner as $crate::UnbufferedService>::max_message_length(&self.0)
            }
        }
    };
}

pub trait Dispatcher {
    /// Generic type to association with a connection. `on_connect()` should
    /// create this type for a successful connection.
    type Connection;

    /// Called when a client connects
    ///
    /// Returns either `Ok(Accept(Connection))` if the connection should be
    /// accepted or `Ok(CloseConnection)` if the connection should be closed.
    fn on_connect(
        &self,
        port: &PortCfg,
        handle: &Handle,
        peer: &Uuid,
    ) -> Result<ConnectResult<Self::Connection>>;

    /// Called when the service receives a message.
    ///
    /// The service manager handles deserializing the message, which is then
    /// passed to this callback.
    ///
    /// Should return `Ok(MaintainConnection)` if the connection should be kept open. The
    /// connection will be closed if `Ok(CloseConnection)` or `Err(_)` is returned.
    fn on_message(
        &self,
        connection: &Self::Connection,
        handle: &Handle,
        buffer: &mut [u8],
    ) -> Result<MessageResult>;

    /// Called when the client closes a connection.
    fn on_disconnect(&self, _connection: &Self::Connection) {}

    /// Get the list of ports this dispatcher handles.
    fn port_configurations(&self) -> &[PortCfg];

    /// Get the maximum possible length of any message handled by this
    /// dispatcher.
    fn max_message_length(&self) -> usize;
}

// Implementation of a static dispatcher for services with only a single message
// type.
pub struct SingleDispatcher<S: Service> {
    service: S,
    ports: [PortCfg; 1],
}

impl<S: Service> SingleDispatcher<S> {
    fn new(service: S, port: PortCfg) -> Self {
        Self { service, ports: [port] }
    }
}

impl<S: Service> Dispatcher for SingleDispatcher<S> {
    type Connection = S::Connection;

    fn on_connect(
        &self,
        port: &PortCfg,
        handle: &Handle,
        peer: &Uuid,
    ) -> Result<ConnectResult<Self::Connection>> {
        self.service.on_connect(port, handle, peer)
    }

    fn on_message(
        &self,
        connection: &Self::Connection,
        handle: &Handle,
        buffer: &mut [u8],
    ) -> Result<MessageResult> {
        let mut handles: [Option<Handle>; MAX_MSG_HANDLES] = Default::default();
        let (byte_count, handle_count) = handle.recv_vectored(&mut [buffer], &mut handles)?;
        let msg = S::Message::deserialize(&buffer[..byte_count], &mut handles[..handle_count])
            .map_err(|e| {
                error!("Could not parse message: {:?}", e);
                TipcError::InvalidData
            })?;
        self.service.on_message(connection, handle, msg)
    }

    fn on_disconnect(&self, connection: &Self::Connection) {
        self.service.on_disconnect(connection)
    }

    fn max_message_length(&self) -> usize {
        S::Message::MAX_SERIALIZED_SIZE
    }

    fn port_configurations(&self) -> &[PortCfg] {
        &self.ports
    }
}

// Implementation of a static dispatcher for unbuffered services.
pub struct SingleUnbufferedDispatcher<S: UnbufferedService> {
    service: S,
    ports: [PortCfg; 1],
}

impl<S: UnbufferedService> SingleUnbufferedDispatcher<S> {
    fn new(service: S, port: PortCfg) -> Self {
        Self { service, ports: [port] }
    }
}

impl<S: UnbufferedService> Dispatcher for SingleUnbufferedDispatcher<S> {
    type Connection = S::Connection;

    fn on_connect(
        &self,
        port: &PortCfg,
        handle: &Handle,
        peer: &Uuid,
    ) -> Result<ConnectResult<Self::Connection>> {
        self.service.on_connect(port, handle, peer)
    }

    fn on_message(
        &self,
        connection: &Self::Connection,
        handle: &Handle,
        buffer: &mut [u8],
    ) -> Result<MessageResult> {
        self.service.on_message(connection, handle, buffer)
    }

    fn on_disconnect(&self, connection: &Self::Connection) {
        self.service.on_disconnect(connection)
    }

    fn max_message_length(&self) -> usize {
        self.service.max_message_length()
    }

    fn port_configurations(&self) -> &[PortCfg] {
        &self.ports
    }
}

/// Create a new service dispatcher that can handle a specified set of service
/// types.
///
/// This macro creates a multi-service dispatcher that holds different types of
/// services that should share the same event loop and dispatches to the
/// relevant service based on the connection port. Services must implement the
/// [`Service`] trait. An instance of this dispatcher must have a single const
/// usize parameter specifying how many ports the dispatcher will handle.
/// This macro has limited lifetime support. A single lifetime can be
/// used for the ServiceDispatcher enum and the included services (see the
/// supported definition in the Examples section).
///
/// # Examples
/// ```
/// service_dispatcher! {
///     enum ServiceDispatcher {
///         Service1,
///         Service2,
///     }
/// }
///
/// // Create a new dispatcher that handles two ports
/// let dispatcher = ServiceDispatcher::<2>::new()
///     .expect("Could not allocate service dispatcher");
///
/// let cfg = PortCfg::new(&"com.android.trusty.test_port1).unwrap();
/// dispatcher.add_service(Rc::new(Service1), cfg).expect("Could not add service 1");
///
/// let cfg = PortCfg::new(&"com.android.trusty.test_port2).unwrap();
/// dispatcher.add_service(Rc::new(Service2), cfg).expect("Could not add service 2");
/// ```
///
/// ## defining a dispatcher with multiple lifetimes
/// ```
/// service_dispatcher! {
///     enum ServiceDispatcher<'a> {
///         Service1<'a>,
///         Service2<'a>,
///     }
/// }
/// ```
#[macro_export]
macro_rules! service_dispatcher {
    (enum $name:ident $(<$elt: lifetime>)? {$($service:ident $(<$slt: lifetime>)? ),+ $(,)*}) => {
        /// Dispatcher that routes incoming messages to the correct server based on what
        /// port the message was sent to.
        ///
        /// This dispatcher adapts multiple different server types that expect different
        /// message formats for the same [`Manager`]. By using this dispatcher,
        /// different servers can be bound to different ports using the same event loop
        /// in the manager.
        struct $name<$($elt,)? const PORT_COUNT: usize> {
            ports: arrayvec::ArrayVec::<$crate::PortCfg, PORT_COUNT>,
            services: arrayvec::ArrayVec::<ServiceKind$(<$elt>)?, PORT_COUNT>,
        }

        impl<$($elt,)? const PORT_COUNT: usize> $name<$($elt,)? PORT_COUNT> {
            fn new() -> $crate::Result<Self> {
                Ok(Self {
                    ports: arrayvec::ArrayVec::<_, PORT_COUNT>::new(),
                    services: arrayvec::ArrayVec::<_, PORT_COUNT>::new(),
                })
            }

            fn add_service<T>(&mut self, service: alloc::rc::Rc<T>, port: $crate::PortCfg) -> $crate::Result<()>
            where ServiceKind$(<$elt>)? : From<alloc::rc::Rc<T>> {
                if self.ports.is_full() || self.services.is_full() {
                    return Err($crate::TipcError::OutOfBounds);
                }
                // SAFETY: We check the size above
                unsafe {
                    self.ports.push_unchecked(port);
                    self.services.push_unchecked(service.into());
                }
                Ok(())
            }
        }

        enum ServiceKind$(<$elt>)? {
            $($service(alloc::rc::Rc<$service$(<$slt>)?>)),+
        }

        $(
            impl$(<$slt>)? From<alloc::rc::Rc<$service$(<$slt>)?>> for ServiceKind$(<$slt>)? {
                fn from(service: alloc::rc::Rc<$service$(<$slt>)?>) -> Self {
                    ServiceKind::$service(service)
                }
            }
        )+

        enum ConnectionKind$(<$elt>)?  {
            $($service(<$service$(<$slt>)? as $crate::UnbufferedService>::Connection)),+
        }

        impl<$($elt,)? const PORT_COUNT: usize> $crate::Dispatcher for $name<$($elt,)? PORT_COUNT> {
            type Connection = (usize, ConnectionKind$(<$elt>)?);

            fn on_connect(
                &self,
                port: &$crate::PortCfg,
                handle: &$crate::Handle,
                peer: &$crate::Uuid,
            ) -> $crate::Result<$crate::ConnectResult<Self::Connection>> {
                let port_idx = self.ports.iter()
                                         .position(|cfg| cfg == port)
                                         .ok_or($crate::TipcError::InvalidPort)?;

                match &self.services[port_idx] {
                    $(ServiceKind::$service(s) => {
                        $crate::UnbufferedService::on_connect(&**s, port, handle, peer)
                            .map(|c| c.map(|c| (port_idx, ConnectionKind::$service(c))))
                    })+
                }
            }

            fn on_message(
                &self,
                connection: &Self::Connection,
                handle: &$crate::Handle,
                buffer: &mut [u8],
            ) -> $crate::Result<$crate::MessageResult> {
                match &self.services[connection.0] {
                    $(ServiceKind::$service(s) => {
                        if let ConnectionKind::$service(conn) = &connection.1 {
                            $crate::UnbufferedService::on_message(&**s, conn, handle, buffer)
                        } else {
                            Err($crate::TipcError::InvalidData)
                        }
                    })*
                }
            }

            fn on_disconnect(&self, connection: &Self::Connection) {
                match &self.services[connection.0] {
                    $(ServiceKind::$service(s) => {
                        if let ConnectionKind::$service(conn) = &connection.1 {
                            $crate::UnbufferedService::on_disconnect(&**s, conn)
                        }
                    })*
                }
            }

            fn port_configurations(&self) -> &[$crate::PortCfg] {
                self.ports.as_slice()
            }

            fn max_message_length(&self) -> usize {
                self.services.iter().map(|s| {
                    match s {
                        $(ServiceKind::$service(service) => {
                            <$service as $crate::UnbufferedService>::max_message_length(&**service)
                        })+
                    }
                }).max().unwrap_or(0usize)
            }
        }
    };

    (@make_none $service:ident) => { None };
}

/// A manager that handles the IPC event loop and dispatches connections and
/// messages to a generic service.
pub struct Manager<
    D: Dispatcher,
    B: AsMut<[u8]> + AsRef<[u8]>,
    const PORT_COUNT: usize,
    const MAX_CONNECTION_COUNT: usize,
> {
    dispatcher: D,
    handle_set: HandleSet<D, PORT_COUNT, MAX_CONNECTION_COUNT>,
    buffer: B,
}

impl<
        S: Service,
        B: AsMut<[u8]> + AsRef<[u8]>,
        const PORT_COUNT: usize,
        const MAX_CONNECTION_COUNT: usize,
    > Manager<SingleDispatcher<S>, B, PORT_COUNT, MAX_CONNECTION_COUNT>
{
    /// Create a new service manager for the given service and port.
    ///
    /// The manager will receive data into the buffer `B`, so this buffer needs
    /// to be at least as large as the largest message this service can handle.
    ///
    /// # Examples
    ///
    /// ```
    /// struct MyService;
    ///
    /// impl Service for MyService {
    ///     type Connection = ();
    ///     type Message = ();
    ///
    ///     fn on_connect(
    ///         &self,
    ///         _port: &PortCfg,
    ///         _handle: &Handle,
    ///         _peer: &Uuid,
    ///     ) -> Result<ConnectResult<Self::Connection>> {
    ///         Ok(ConnectResult::Accept(()))
    ///     }
    ///
    ///     fn on_message(
    ///         &self,
    ///         _connection: &Self::Connection,
    ///         _handle: &Handle,
    ///         _msg: Self::Message,
    ///     ) -> Result<MessageResult> {
    ///         Ok(MessageResult::MaintainConnection)
    ///     }
    /// }
    ///
    /// let service = MyService;
    /// let cfg = PortCfg::new("com.android.trusty.rust_port_test");
    /// let buffer = [0u8; 4096];
    /// let mut manager = Manager::<_, _, 1, 1>::new(service, &[cfg], buffer);
    ///
    /// manager.run_event_loop()
    ///     .expect("Service manager encountered an error");
    /// ```
    pub fn new(service: S, port_cfg: PortCfg, buffer: B) -> Result<Self> {
        let dispatcher = SingleDispatcher::new(service, port_cfg);
        Self::new_with_dispatcher(dispatcher, buffer)
    }
}

impl<S: UnbufferedService, const PORT_COUNT: usize, const MAX_CONNECTION_COUNT: usize>
    Manager<SingleUnbufferedDispatcher<S>, [u8; 0], PORT_COUNT, MAX_CONNECTION_COUNT>
{
    /// Create a new unbuffered service manager for the given service and port.
    ///
    /// The newly created manager will not have an internal buffer.
    /// The service is responsible for reading messages explicitly from the handler.
    pub fn new_unbuffered(service: S, port_cfg: PortCfg) -> Result<Self> {
        let dispatcher = SingleUnbufferedDispatcher::new(service, port_cfg);
        Self::new_with_dispatcher(dispatcher, [])
    }
}

impl<
        D: Dispatcher,
        B: AsMut<[u8]> + AsRef<[u8]>,
        const PORT_COUNT: usize,
        const MAX_CONNECTION_COUNT: usize,
    > Manager<D, B, PORT_COUNT, MAX_CONNECTION_COUNT>
{
    /// Create a manager that can handle multiple services and ports
    ///
    /// A dispatcher handles mapping connections to services and parsing
    /// messages for the relevant service depending on which port the connection
    /// was made to. This allows multiple distinct services, each with their own
    /// message format and port to share the same event loop in the manager.
    ///
    /// See [`service_dispatcher!`] for details on how to create a dispatcher
    /// for use with this API.
    ///
    /// [`service_dispatcher!`]: crate::service_dispatcher
    ///
    /// # Examples
    /// ```
    /// service_dispatcher! {
    ///     enum ServiceDispatcher {
    ///         Service1,
    ///         Service2,
    ///     }
    /// }
    ///
    /// // Create a new dispatcher that handles two ports
    /// let dispatcher = ServiceDispatcher::<2>::new()
    ///     .expect("Could not allocate service dispatcher");
    ///
    /// let cfg = PortCfg::new(&"com.android.trusty.test_port1).unwrap();
    /// dispatcher.add_service(Rc::new(Service1), cfg).expect("Could not add service 1");
    ///
    /// let cfg = PortCfg::new(&"com.android.trusty.test_port2).unwrap();
    /// dispatcher.add_service(Rc::new(Service2), cfg).expect("Could not add service 2");
    ///
    /// Manager::<_, _, 2, 4>::new_with_dispatcher(dispatcher, [0u8; 4096])
    ///     .expect("Could not create service manager")
    ///     .run_event_loop()
    ///     .expect("Service manager exited unexpectedly");
    /// ```
    pub fn new_with_dispatcher(dispatcher: D, buffer: B) -> Result<Self> {
        if buffer.as_ref().len() < dispatcher.max_message_length() {
            return Err(TipcError::NotEnoughBuffer);
        }

        let ports: Vec<Rc<Channel<D>>> = dispatcher
            .port_configurations()
            .iter()
            .map(Channel::try_new_port)
            .collect::<Result<_>>()?;
        let ports: [Rc<Channel<D>>; PORT_COUNT] = ports
            .try_into()
            .expect("This is impossible. Array size must match expected PORT_COUNT");
        let handle_set = HandleSet::try_new(ports)?;

        Ok(Self { dispatcher, handle_set, buffer })
    }

    /// Run the service event loop.
    ///
    /// Only returns if an error occurs.
    pub fn run_event_loop(mut self) -> Result<()> {
        use trusty_sys::Error;

        loop {
            // Process the next incoming event, extracting any returned error for further
            // checking.
            let err = match self.event_loop_inner() {
                Ok(()) => continue,
                Err(err) => err,
            };

            // Check if the error is recoverable or not. If the error is not one of a
            // limited set of recoverable errors, we break from the event loop.
            match err {
                // Recoverable errors that are always ignored.
                | TipcError::SystemError(Error::TimedOut)
                | TipcError::SystemError(Error::ChannelClosed)

                // These are always caused by the client and so shouldn't be treated as an
                // internal error or cause the event loop to exit.
                | TipcError::ChannelClosed
                => {
                    debug!("Recoverable error ignored: {:?}", err)
                }

                // These are legitimate errors and we should be handling them, but they would be
                // better handled lower in the event loop closer to where they originate. If
                // they get propagated up here then we can't meaningfully handle them anymore,
                // so just log them and continue the loop.
                | TipcError::IncompleteWrite { .. }
                | TipcError::NotEnoughBuffer
                | TipcError::Busy
                => {
                    warn!(
                        "Received error {:?} in main event loop. This should have been handled closer to where it originated",
                        err,
                    )
                }

                _ => {
                    error!("Error occurred while handling incoming event: {:?}", err);
                    return Err(err);
                }
            }
        }
    }

    fn event_loop_inner(&mut self) -> Result<()> {
        let event = self.handle_set.wait(None)?;
        // SAFETY: This cookie was previously initialized by the handle set.
        // Its lifetime is managed by the handle set, so we are sure that
        // the cookie is still valid if the channel is still in this handle
        // set.
        let channel: Rc<Channel<D>> = unsafe { Channel::from_opaque_ptr(event.cookie) }
            .ok_or_else(|| {
                // The opaque pointer associated with this handle could not
                // be converted back into a `Channel`. This should never
                // happen, but throw an internal error if it does.
                error!("Connection cookie was invalid");
                TipcError::InvalidData
            })?;
        self.handler(channel, &event)
    }

    fn handler(&mut self, channel: Rc<Channel<D>>, event: &trusty_sys::uevent) -> Result<()> {
        // TODO: Abort on port errors?
        match &channel.ty {
            ChannelTy::Port(cfg) if event.event & (sys::IPC_HANDLE_POLL_READY as u32) != 0 => {
                self.handle_connect(&channel.handle, cfg)
            }
            ChannelTy::Connection(data) if event.event & (sys::IPC_HANDLE_POLL_MSG as u32) != 0 => {
                match self.handle_message(&channel.handle, &data) {
                    Ok(MessageResult::MaintainConnection) => Ok(()),
                    Ok(MessageResult::CloseConnection) => {
                        self.handle_set.close(channel);
                        Ok(())
                    }
                    Err(e) => {
                        error!("Could not handle message, closing connection: {:?}", e);
                        self.handle_set.close(channel);
                        Ok(())
                    }
                }
            }
            ChannelTy::Connection(data) if event.event & (sys::IPC_HANDLE_POLL_HUP as u32) != 0 => {
                self.handle_disconnect(&channel.handle, &data);
                self.handle_set.close(channel);
                Ok(())
            }

            // `SEND_UNBLOCKED` means that some previous attempt to send a message was
            // blocked and has now become unblocked. This should normally be handled by
            // the code trying to send the message, but if the sending code doesn't do so
            // then we can end up getting it here.
            _ if event.event & (sys::IPC_HANDLE_POLL_SEND_UNBLOCKED as u32) != 0 => {
                warn!("Received `SEND_UNBLOCKED` event received in main event loop. This likely means that a sent message was lost somewhere");
                Ok(())
            }

            // `NONE` is not an event we should get in practice, but if it does then it
            // shouldn't trigger an error.
            _ if event.event == (sys::IPC_HANDLE_POLL_NONE as u32) => Ok(()),

            // Treat any unrecognized events as errors by default.
            _ => {
                error!("Could not handle event {}", event.event);
                Err(TipcError::UnknownError)
            }
        }
    }

    fn handle_connect(&mut self, handle: &Handle, cfg: &PortCfg) -> Result<()> {
        let mut peer = MaybeUninit::zeroed();
        // SAFETY: syscall. The port owns its handle, so it is still valid as
        // a raw fd. The peer structure outlives this call and is mutably
        // borrowed by the call to initialize the structure's data.
        let rc = unsafe { trusty_sys::accept(handle.as_raw_fd(), peer.as_mut_ptr()) as i32 };
        let connection_handle = Handle::from_raw(rc)?;
        // SAFETY: accept did not return an error, so it has successfully
        // initialized the peer structure.
        let peer = unsafe { Uuid(peer.assume_init()) };

        // TODO: Implement access control

        let connection_data = self.dispatcher.on_connect(&cfg, &connection_handle, &peer)?;
        if let ConnectResult::Accept(data) = connection_data {
            let connection_channel = Channel::try_new_connection(connection_handle, data)?;
            self.handle_set.add_connection(connection_channel)?;
        }

        Ok(())
    }

    fn handle_message(&mut self, handle: &Handle, data: &D::Connection) -> Result<MessageResult> {
        self.dispatcher.on_message(data, handle, self.buffer.as_mut())
    }

    fn handle_disconnect(&mut self, _handle: &Handle, data: &D::Connection) {
        self.dispatcher.on_disconnect(data);
    }
}

#[cfg(test)]
mod test {
    use super::{PortCfg, Service};
    use crate::handle::test::{first_free_handle_index, MAX_USER_HANDLES};
    use crate::{
        ConnectResult, Deserialize, Handle, Manager, MessageResult, Result, Serialize, Serializer,
        TipcError, UnbufferedService, Uuid,
    };
    use test::{expect, expect_eq};
    use trusty_std::alloc::FallibleVec;
    use trusty_std::ffi::{CString, FallibleCString};
    use trusty_std::format;
    use trusty_std::rc::Rc;
    use trusty_std::vec::Vec;
    use trusty_sys::Error;

    /// Maximum length of port path name
    const MAX_PORT_PATH_LEN: usize = 64;

    /// Maximum number of buffers per port
    const MAX_PORT_BUF_NUM: u32 = 64;

    /// Maximum size of port buffer
    const MAX_PORT_BUF_SIZE: u32 = 4096;

    const SRV_PATH_BASE: &str = "com.android.ipc-unittest";

    impl Service for () {
        type Connection = ();
        type Message = ();

        fn on_connect(
            &self,
            _port: &PortCfg,
            _handle: &Handle,
            _peer: &Uuid,
        ) -> Result<ConnectResult<Self::Connection>> {
            Ok(ConnectResult::Accept(()))
        }

        fn on_message(
            &self,
            _connection: &Self::Connection,
            _handle: &Handle,
            _msg: Self::Message,
        ) -> Result<MessageResult> {
            Ok(MessageResult::MaintainConnection)
        }
    }

    type Channel = super::Channel<super::SingleDispatcher<()>>;

    #[test]
    fn port_create_negative() {
        let path = [0u8; 0];

        expect_eq!(
            Channel::try_new_port(&PortCfg::new_raw(CString::try_new(&path[..]).unwrap())).err(),
            Some(TipcError::SystemError(Error::InvalidArgs)),
            "empty server path",
        );

        let mut path = format!("{}.port", SRV_PATH_BASE);

        let cfg = PortCfg::new(&path).unwrap().msg_queue_len(0);
        expect_eq!(
            Channel::try_new_port(&cfg).err(),
            Some(TipcError::SystemError(Error::InvalidArgs)),
            "no buffers",
        );

        let cfg = PortCfg::new(&path).unwrap().msg_max_size(0);
        expect_eq!(
            Channel::try_new_port(&cfg).err(),
            Some(TipcError::SystemError(Error::InvalidArgs)),
            "zero buffer size",
        );

        let cfg = PortCfg::new(&path).unwrap().msg_queue_len(MAX_PORT_BUF_NUM * 100);
        expect_eq!(
            Channel::try_new_port(&cfg).err(),
            Some(TipcError::SystemError(Error::InvalidArgs)),
            "large number of buffers",
        );

        let cfg = PortCfg::new(&path).unwrap().msg_max_size(MAX_PORT_BUF_SIZE * 100);
        expect_eq!(
            Channel::try_new_port(&cfg).err(),
            Some(TipcError::SystemError(Error::InvalidArgs)),
            "large buffers size",
        );

        while path.len() < MAX_PORT_PATH_LEN + 16 {
            path.push('a');
        }

        let cfg = PortCfg::new(&path).unwrap();
        expect_eq!(
            Channel::try_new_port(&cfg).err(),
            Some(TipcError::SystemError(Error::InvalidArgs)),
            "path is too long",
        );
    }

    #[test]
    fn port_create() {
        let mut channels: Vec<Rc<Channel>> = Vec::new();

        for i in first_free_handle_index()..MAX_USER_HANDLES - 1 {
            let path = format!("{}.port.{}{}", SRV_PATH_BASE, "test", i);
            let cfg = PortCfg::new(path).unwrap();
            let channel = Channel::try_new_port(&cfg);
            expect!(channel.is_ok(), "create ports");
            channels.try_push(channel.unwrap()).unwrap();

            expect_eq!(
                Channel::try_new_port(&cfg).err(),
                Some(TipcError::SystemError(Error::AlreadyExists)),
                "collide with existing port",
            );
        }

        // Creating one more port should succeed
        let path = format!("{}.port.{}{}", SRV_PATH_BASE, "test", MAX_USER_HANDLES - 1);
        let cfg = PortCfg::new(path).unwrap();
        let channel = Channel::try_new_port(&cfg);
        expect!(channel.is_ok(), "create ports");
        channels.try_push(channel.unwrap()).unwrap();

        // but creating colliding port should fail with different error code
        // because we actually exceeded max number of handles instead of
        // colliding with an existing path
        expect_eq!(
            Channel::try_new_port(&cfg).err(),
            Some(TipcError::SystemError(Error::NoResources)),
            "collide with existing port",
        );

        let path = format!("{}.port.{}{}", SRV_PATH_BASE, "test", MAX_USER_HANDLES);
        let cfg = PortCfg::new(path).unwrap();
        expect_eq!(
            Channel::try_new_port(&cfg).err(),
            Some(TipcError::SystemError(Error::NoResources)),
            "max number of ports reached",
        );
    }

    #[test]
    fn wait_on_port() {
        let mut channels: Vec<Rc<Channel>> = Vec::new();

        for i in first_free_handle_index()..MAX_USER_HANDLES {
            let path = format!("{}.port.{}{}", SRV_PATH_BASE, "test", i);
            let cfg = PortCfg::new(path).unwrap();
            let channel = Channel::try_new_port(&cfg);
            expect!(channel.is_ok(), "create ports");
            channels.try_push(channel.unwrap()).unwrap();
        }

        for chan in &channels {
            expect_eq!(
                chan.handle.wait(Some(0)).err(),
                Some(TipcError::SystemError(Error::TimedOut)),
                "zero timeout",
            );

            expect_eq!(
                chan.handle.wait(Some(100)).err(),
                Some(TipcError::SystemError(Error::TimedOut)),
                "non-zero timeout",
            );
        }
    }

    impl<'s> Serialize<'s> for i32 {
        fn serialize<'a: 's, S: Serializer<'s>>(
            &'a self,
            serializer: &mut S,
        ) -> core::result::Result<S::Ok, S::Error> {
            unsafe { serializer.serialize_as_bytes(self) }
        }
    }

    impl Deserialize for i32 {
        type Error = TipcError;

        const MAX_SERIALIZED_SIZE: usize = 4;

        fn deserialize(
            bytes: &[u8],
            _handles: &mut [Option<Handle>],
        ) -> core::result::Result<Self, Self::Error> {
            Ok(i32::from_ne_bytes(bytes[0..4].try_into().map_err(|_| TipcError::OutOfBounds)?))
        }
    }

    struct Service1;

    impl Service for Service1 {
        type Connection = ();
        type Message = ();

        fn on_connect(
            &self,
            _port: &PortCfg,
            _handle: &Handle,
            _peer: &Uuid,
        ) -> Result<ConnectResult<Self::Connection>> {
            Ok(ConnectResult::Accept(()))
        }

        fn on_message(
            &self,
            _connection: &Self::Connection,
            handle: &Handle,
            _msg: Self::Message,
        ) -> Result<MessageResult> {
            handle.send(&1i32)?;
            Ok(MessageResult::MaintainConnection)
        }
    }

    struct Service2;

    impl Service for Service2 {
        type Connection = ();
        type Message = ();

        fn on_connect(
            &self,
            _port: &PortCfg,
            _handle: &Handle,
            _peer: &Uuid,
        ) -> Result<ConnectResult<Self::Connection>> {
            Ok(ConnectResult::Accept(()))
        }

        fn on_message(
            &self,
            _connection: &Self::Connection,
            handle: &Handle,
            _msg: Self::Message,
        ) -> Result<MessageResult> {
            handle.send(&2i32)?;
            Ok(MessageResult::MaintainConnection)
        }
    }

    struct Service3;

    impl UnbufferedService for Service3 {
        type Connection = ();

        fn on_connect(
            &self,
            _port: &PortCfg,
            _handle: &Handle,
            _peer: &Uuid,
        ) -> Result<ConnectResult<Self::Connection>> {
            Ok(ConnectResult::Accept(()))
        }

        fn on_message(
            &self,
            _connection: &Self::Connection,
            handle: &Handle,
            _buffer: &mut [u8],
        ) -> Result<MessageResult> {
            handle.send(&3i32)?;
            Ok(MessageResult::MaintainConnection)
        }
    }

    wrap_service!(WrappedService2(Service2: Service));
    wrap_service!(WrappedService3(Service3: UnbufferedService));

    service_dispatcher! {
        enum TestServiceDispatcher {
            Service1,
            Service2,
            Service3,
            WrappedService2,
            WrappedService3,
        }
    }

    #[test]
    fn multiple_services() {
        let mut dispatcher = TestServiceDispatcher::<5>::new().unwrap();

        let path1 = format!("{}.port.{}", SRV_PATH_BASE, "testService1");
        let cfg = PortCfg::new(&path1).unwrap();
        dispatcher.add_service(Rc::new(Service1), cfg).expect("Could not add service 1");

        let path2 = format!("{}.port.{}", SRV_PATH_BASE, "testService2");
        let cfg = PortCfg::new(&path2).unwrap();
        dispatcher.add_service(Rc::new(Service2), cfg).expect("Could not add service 2");

        let path = format!("{}.port.{}", SRV_PATH_BASE, "testService3");
        let cfg = PortCfg::new(&path).unwrap();
        dispatcher.add_service(Rc::new(Service3), cfg).expect("Could not add service 3");

        let path = format!("{}.port.{}", SRV_PATH_BASE, "testWrappedService2");
        let cfg = PortCfg::new(&path).unwrap();
        dispatcher
            .add_service(Rc::new(WrappedService2(Service2)), cfg)
            .expect("Could not add wrapped service 2");

        let path = format!("{}.port.{}", SRV_PATH_BASE, "testWrappedService3");
        let cfg = PortCfg::new(&path).unwrap();
        dispatcher
            .add_service(Rc::new(WrappedService3(Service3)), cfg)
            .expect("Could not add wrapped service 3");

        let buffer = [0u8; 4096];
        Manager::<_, _, 5, 4>::new_with_dispatcher(dispatcher, buffer)
            .expect("Could not create service manager");
    }

    #[test]
    fn unbuffered_service() {
        let path = format!("{}.port.{}", SRV_PATH_BASE, "unbufferedService");
        let cfg = PortCfg::new(&path).unwrap();
        Manager::<_, _, 1, 4>::new_unbuffered(Service3, cfg)
            .expect("Could not create service manager");
    }
}

#[cfg(test)]
mod multiservice_with_lifetimes_tests {
    use super::*;
    use core::marker::PhantomData;
    #[allow(unused)]
    use trusty_std::alloc::FallibleVec;

    const SRV_PATH_BASE: &str = "com.android.ipc-unittest-lifetimes";

    struct Service1<'a> {
        phantom: PhantomData<&'a u32>,
    }

    impl<'a> Service for Service1<'a> {
        type Connection = ();
        type Message = ();

        fn on_connect(
            &self,
            _port: &PortCfg,
            _handle: &Handle,
            _peer: &Uuid,
        ) -> Result<ConnectResult<Self::Connection>> {
            Ok(ConnectResult::Accept(()))
        }

        fn on_message(
            &self,
            _connection: &Self::Connection,
            handle: &Handle,
            _msg: Self::Message,
        ) -> Result<MessageResult> {
            handle.send(&2i32)?;
            Ok(MessageResult::MaintainConnection)
        }
    }

    struct Service2<'a> {
        phantom: PhantomData<&'a u32>,
    }

    impl<'a> Service for Service2<'a> {
        type Connection = ();
        type Message = ();

        fn on_connect(
            &self,
            _port: &PortCfg,
            _handle: &Handle,
            _peer: &Uuid,
        ) -> Result<ConnectResult<Self::Connection>> {
            Ok(ConnectResult::Accept(()))
        }

        fn on_message(
            &self,
            _connection: &Self::Connection,
            handle: &Handle,
            _msg: Self::Message,
        ) -> Result<MessageResult> {
            handle.send(&2i32)?;
            Ok(MessageResult::MaintainConnection)
        }
    }

    service_dispatcher! {
        enum TestServiceLifetimeDispatcher<'a> {
            Service1<'a>,
            Service2<'a>,
        }
    }

    #[test]
    fn manager_creation() {
        let mut dispatcher = TestServiceLifetimeDispatcher::<2>::new().unwrap();

        let path1 = format!("{}.port.{}", SRV_PATH_BASE, "testService1");
        let cfg = PortCfg::new(&path1).unwrap();
        dispatcher
            .add_service(Rc::new(Service1 { phantom: PhantomData }), cfg)
            .expect("Could not add service 1");

        let path2 = format!("{}.port.{}", SRV_PATH_BASE, "testService2");
        let cfg = PortCfg::new(&path2).unwrap();
        dispatcher
            .add_service(Rc::new(Service2 { phantom: PhantomData }), cfg)
            .expect("Could not add service 2");

        let buffer = [0u8; 4096];
        Manager::<_, _, 2, 4>::new_with_dispatcher(dispatcher, buffer)
            .expect("Could not create service manager");
    }
}

#[cfg(test)]
mod uuid_tests {
    use super::Uuid;
    use test::{expect, expect_eq};

    #[test]
    fn uuid_parsing() {
        let uuid = Uuid::new(0, 0, 0, [0; 8]);
        let uuid_string = "00000000-0000-0000-0000-000000000000".to_string();
        expect_eq!(uuid.to_string(), uuid_string);
        let uuid_from_str = Uuid::new_from_string(&uuid_string);
        expect!(uuid_from_str.is_ok(), "couldn't parse uuid string");
        let uuid_from_str = uuid_from_str.unwrap();
        expect_eq!(uuid, uuid_from_str, "uuid should match");
        let uuid2 = Uuid::new(1262561249, 51804, 17255, [189, 181, 5, 22, 64, 5, 183, 196]);
        let uuid_string_2 = "4b4127e1-ca5c-4367-bdb5-05164005b7c4".to_string();
        let uuid2_from_str = Uuid::new_from_string(&uuid_string_2);
        expect!(uuid2_from_str.is_ok(), "couldn't parse uuid string");
        let uuid2_from_str = uuid2_from_str.unwrap();
        expect_eq!(uuid2, uuid2_from_str, "uuid should match");
        let bad_uuid_from_str = Uuid::new_from_string("4b4127e1-ca5c-4367-bdb5-05164005b7c45");
        expect!(bad_uuid_from_str.is_err(), "shouldn't be able to parse string");
        let bad_uuid_from_str = Uuid::new_from_string("4b4127e1-ca5c-4367-bdbq-05164005b7c4");
        expect!(bad_uuid_from_str.is_err(), "shouldn't be able to parse string");
        let bad_uuid_from_str = Uuid::new_from_string("4b4127e1-ca5c-4367-bdb5005164005b7c4");
        expect!(bad_uuid_from_str.is_err(), "shouldn't be able to parse string");
        let bad_uuid_from_str = Uuid::new_from_string("4b41-7e1-ca5c-4367-bdb5-05164005b7c4");
        expect!(bad_uuid_from_str.is_err(), "shouldn't be able to parse string");
        let bad_uuid_from_str = Uuid::new_from_string("4b4127e1-ca5c-4367-bd-b505164005b7c4");
        expect!(bad_uuid_from_str.is_err(), "shouldn't be able to parse string");
    }
}
