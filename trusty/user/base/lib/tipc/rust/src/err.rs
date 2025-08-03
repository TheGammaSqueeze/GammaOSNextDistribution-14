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

use alloc::alloc::AllocError;
use alloc::collections::TryReserveError;
use core::num::TryFromIntError;
use trusty_std::ffi::TryNewError;
use trusty_sys::{c_long, Error};

/// A specialized [`Result`] type for IPC operations.
///
/// This type is used throughout the [`tipc`](crate) crate as a shorthand for result
/// return values. Users outside the current crate should generally use this
/// type as `tipc::Result` rather than shadowing the standard `Result` type.
pub type Result<T> = core::result::Result<T, TipcError>;

/// Errors that an IPC connection may encounter.
#[derive(Clone, Debug, Eq, PartialEq)]
pub enum TipcError {
    /// The handle ID provided or returned by the kernel was invalid.
    InvalidHandle,

    /// The provided port was invalid.
    InvalidPort,

    /// Failed to allocate, probably out of memory.
    AllocError,

    /// An integer did not fit into the required size.
    OutOfBounds,

    /// Could not write the entire message. Contains the number of bytes that
    /// were successfully written.
    IncompleteWrite { num_bytes_written: usize },

    /// The provided buffer was not large enough to receive the entire message.
    NotEnoughBuffer,

    /// Some other error occurred.
    UnknownError,

    /// Internal data was not valid
    InvalidData,

    /// Message could not be sent because connection was busy.
    Busy,

    /// The channel was closed before the operation could be completed.
    ChannelClosed,

    /// Some other system error
    SystemError(Error),
}

/// The result type for an `on_connect` operation.
///
/// If the connection is accepted, contains created connection object for this connection.
#[derive(Clone, Copy, Debug)]
pub enum ConnectResult<C> {
    Accept(C),
    CloseConnection,
}

/// The result type for an `on_message` operation.
///
/// Its value tells the `ServiceManager` what to do after handling the message.
#[derive(Clone, Copy, Debug)]
pub enum MessageResult {
    MaintainConnection,
    CloseConnection,
}

impl TipcError {
    pub fn from_uapi(rc: c_long) -> Self {
        let sys_err: Error = rc.into();
        match sys_err {
            Error::BadHandle => TipcError::InvalidHandle,
            e => TipcError::SystemError(e),
        }
    }
}

impl<T> ConnectResult<T> {
    /// Maps a `ConnectResult<T>` to a `ConnectResult<U>` by applying a function to the contained connection.
    pub fn map<U, F: FnOnce(T) -> U>(self, f: F) -> ConnectResult<U> {
        match self {
            Self::Accept(c) => ConnectResult::Accept(f(c)),
            Self::CloseConnection => ConnectResult::CloseConnection,
        }
    }
}

impl From<TryNewError> for TipcError {
    fn from(err: TryNewError) -> Self {
        match err {
            TryNewError::NulError(..) => Self::InvalidPort,
            TryNewError::AllocError => Self::AllocError,
        }
    }
}

impl From<TryReserveError> for TipcError {
    fn from(_err: TryReserveError) -> Self {
        TipcError::AllocError
    }
}

impl From<TryFromIntError> for TipcError {
    fn from(_err: TryFromIntError) -> Self {
        TipcError::OutOfBounds
    }
}

impl From<AllocError> for TipcError {
    fn from(_err: AllocError) -> Self {
        TipcError::AllocError
    }
}
