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

//! Provide the conversion between the elements of uwb_core and protobuf.

use log::error;
use protobuf::Message;

use crate::error::{Error, Result};

/// Convert the protobuf message to a byte buffers. Return dbus::MethodErr when conversion fails.
pub fn write_to_bytes<M: Message>(msg: &M) -> Result<Vec<u8>> {
    msg.write_to_bytes().map_err(|e| {
        error!("Failed to write protobuf {} to bytes: {:?}", M::NAME, e);
        Error::Unknown
    })
}

/// Parse the byte buffer to the protobuf message. Return dbus::MethodErr when failed to parse.
pub fn parse_from_bytes<M: Message>(bytes: &[u8]) -> Result<M> {
    M::parse_from_bytes(bytes).map_err(|e| {
        error!("Failed to parse {:?}: {:?}", M::NAME, e);
        Error::BadParameters
    })
}
