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

//! This module defines the error type and the result type for this library.

/// The error type for the uwb_core library.
#[non_exhaustive] // Adding new enum fields doesn't break the downstream build.
#[derive(Clone, Debug, thiserror::Error, PartialEq, Eq)]
pub enum Error {
    /// The provided parameters are invalid, or the method is not allowed to be called in the
    /// current state.
    #[error("Bad parameters")]
    BadParameters,
    /// Error across Foreign Function Interface.
    #[error("Error across Foreign Function Interface")]
    ForeignFunctionInterface,
    /// The maximum number of sessions has been reached.
    #[error("The maximum number of sessions has been reached")]
    MaxSessionsExceeded,
    /// Max ranging round retries reached.
    #[error("Max ranging round retries reached")]
    MaxRrRetryReached,
    /// Fails due to a protocol specific reason.
    #[error("The session fails with a protocol specific reason")]
    ProtocolSpecific,
    /// The remote device has requested to change the session.
    #[error("The remote device has requested to change the session")]
    RemoteRequest,
    /// The response or notification is not received in timeout.
    #[error("The response or notification is not received in timeout")]
    Timeout,
    /// The command should be retried.
    #[error("The command should be retried")]
    CommandRetry,
    /// Duplicated SessionId.
    #[error("Duplicated SessionId")]
    DuplicatedSessionId,
    /// Packet Tx Error
    #[error("The packet send failed with an error")]
    PacketTxError,
    /// Country code regulation UWB Off
    #[error("The country code command failed with a UWB regulatory error")]
    RegulationUwbOff,
    /// The unknown error.
    #[error("The unknown error")]
    Unknown,

    /// The result of the mock method is not assigned
    #[cfg(any(test, feature = "mock-utils"))]
    #[error("The result of the mock method is not assigned")]
    MockUndefined,
}

/// The result type for the uwb_core library.
///
/// This type is broadly used by the methods in this library which may produce an error.
pub type Result<T> = std::result::Result<T, Error>;
