// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

//! Channel-related functionality and helpers.

use log::error;
use std::ffi::CString;
use std::io::{Read, Write};

/// Abstraction of a channel to a secure world TA implementation.
/// Required to be [`Sync`] to support multi-threaded use by the HAL service.
pub trait SerializedChannel: Send + Sync {
    /// Maximum supported size for the channel in bytes.
    const MAX_SIZE: usize;

    /// Accepts serialized request messages and returns serialized response messages
    /// (or an error if communication via the channel is lost).
    fn execute(&self, serialized_req: &[u8]) -> binder::Result<Vec<u8>>;
}

/// Write a message to a stream-oriented [`Write`] item, with length framing.
pub fn write_msg<W: Write>(w: &mut W, data: &[u8]) -> binder::Result<()> {
    // The underlying `Write` item does not guarantee delivery of complete messages.
    // Make this possible by adding framing in the form of a big-endian `u32` holding
    // the message length.
    let data_len: u32 = data.len().try_into().map_err(|_e| {
        binder::Status::new_exception(
            binder::ExceptionCode::BAD_PARCELABLE,
            Some(&CString::new("encoded request message too large").unwrap()),
        )
    })?;
    let data_len_data = data_len.to_be_bytes();
    w.write_all(&data_len_data[..]).map_err(|e| {
        error!("Failed to write length to stream: {}", e);
        binder::Status::new_exception(
            binder::ExceptionCode::BAD_PARCELABLE,
            Some(&CString::new("failed to write framing length").unwrap()),
        )
    })?;
    w.write_all(data).map_err(|e| {
        error!("Failed to write data to stream: {}", e);
        binder::Status::new_exception(
            binder::ExceptionCode::BAD_PARCELABLE,
            Some(&CString::new("failed to write data").unwrap()),
        )
    })?;
    Ok(())
}

/// Read a message from a stream-oriented [`Read`] item, with length framing.
pub fn read_msg<R: Read>(r: &mut R) -> binder::Result<Vec<u8>> {
    // The data read from the `Read` item has a 4-byte big-endian length prefix.
    let mut len_data = [0u8; 4];
    r.read_exact(&mut len_data).map_err(|e| {
        error!("Failed to read length from stream: {}", e);
        binder::Status::new_exception(binder::ExceptionCode::TRANSACTION_FAILED, None)
    })?;
    let len = u32::from_be_bytes(len_data);
    let mut data = vec![0; len as usize];
    r.read_exact(&mut data).map_err(|e| {
        error!("Failed to read data from stream: {}", e);
        binder::Status::new_exception(binder::ExceptionCode::TRANSACTION_FAILED, None)
    })?;
    Ok(data)
}
