/*
 * Copyright (C) 2023 The Android Open Source Project
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

//! Messages used for communication with the bootloader.
//!
//! The messages exchanged with the bootloader are encoded in a simple manual format rather than
//! CBOR, so that the corresponding code in the bootloader has an easy job (and doesn't need
//! additional dependencies on CBOR libraries).

use alloc::format;
use alloc::string::{String, ToString};
use alloc::vec::Vec;

const U32_LEN: usize = core::mem::size_of::<u32>();

type Error = String;

/// Op code values corresponding to Request variants.
#[derive(Debug, PartialEq, Eq, Clone, Copy)]
#[repr(u32)]
pub enum OpCode {
    /// Unknown op code, for example when an incoming request cannot be identified.
    Unknown = 0,
    /// Retrieve the Secretkeeper public key used as an identity.
    GetIdentityKey = 1,
}

/// Requests that Secretkeeper can receive from the bootloader.  Distinct requests
/// are identified by the corresponding [`OpCode`] value.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Request {
    /// Request to retrieve the Secretkeeper identity public key, as a COSE_Key.
    GetIdentityKey, // `OpCode::GetIdentityKey`
}

impl Request {
    /// Build a request message structure from received serialized data, expected to be in the form:
    /// - 4-byte big-endian opcode.
    /// - N bytes of content, dependent on the opcode.
    pub fn from_slice(data: &[u8]) -> Result<Self, Error> {
        if data.len() < U32_LEN {
            return Err(format!("data too short (len={})", data.len()));
        }
        let (opcode, remainder) = data.split_at(U32_LEN);
        let opcode = u32::from_be_bytes(opcode.try_into().unwrap());
        match opcode {
            x if x == OpCode::GetIdentityKey as u32 => {
                if !remainder.is_empty() {
                    Err("extra data after request".to_string())
                } else {
                    Ok(Self::GetIdentityKey)
                }
            }
            _ => Err(format!("unrecognized opcode {opcode}")),
        }
    }
}

/// Top bit is set in response message op codes.
const RESPONSE_MARKER: u32 = 0x8000_0000;

/// Responses that Secretkeeper can return to the bootloader.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Response {
    /// Request failed
    Error(OpCode, ErrorCode),
    /// Identity key, as a CBOR-encoded COSE_Key.
    IdentityKey(Vec<u8>),
}

impl Response {
    /// Convert a response message to a serialized message in the following form:
    /// - 4-byte big-endian opcode, with top bit set to indicate a response.
    /// - 4-byte error code, where 0 indicates success.
    /// - N bytes of content, dependent on the opcode. Only present on success.
    pub fn into_vec(self) -> Vec<u8> {
        let content = self.content();
        let size = 2 * U32_LEN + content.len();
        let mut result = Vec::with_capacity(size);
        let rsp_op_code = self.op_code() | RESPONSE_MARKER;
        result.extend_from_slice(&rsp_op_code.to_be_bytes());
        result.extend_from_slice(&self.error_code().to_be_bytes());
        result.extend_from_slice(content);
        result
    }

    fn op_code(&self) -> u32 {
        match self {
            Self::Error(op, _) => *op as u32,
            Self::IdentityKey(_) => OpCode::GetIdentityKey as u32,
        }
    }

    fn error_code(&self) -> u32 {
        match self {
            Self::Error(_, rc) => *rc as u32,
            Self::IdentityKey(_) => ErrorCode::Ok as u32,
        }
    }

    fn content(&self) -> &[u8] {
        match self {
            Self::Error(_, _) => &[],
            Self::IdentityKey(k) => k,
        }
    }

    /// Build a response message structure from serialized data.
    /// (Only used for testing.)
    pub fn from_slice(data: &[u8]) -> Result<Self, Error> {
        if data.len() < 2 * U32_LEN {
            return Err(format!("data too short (len={})", data.len()));
        }
        let (opcode, remainder) = data.split_at(U32_LEN);
        let opcode = u32::from_be_bytes(opcode.try_into().unwrap());
        if (opcode & RESPONSE_MARKER) != RESPONSE_MARKER {
            return Err("response missing marker bit".to_string());
        }
        let opcode = match opcode & !RESPONSE_MARKER {
            x if x == OpCode::GetIdentityKey as u32 => OpCode::GetIdentityKey,
            op => return Err(format!("Unrecognized opcode {op}")),
        };
        let (errcode, remainder) = remainder.split_at(U32_LEN);
        match u32::from_be_bytes(errcode.try_into().unwrap()) {
            0 => match opcode {
                OpCode::GetIdentityKey => Ok(Self::IdentityKey(remainder.to_vec())),
                _ => Err(format!("unexpected opcode {opcode:?}")),
            },
            rc => match rc {
                x if x == ErrorCode::CborFailure as u32 => {
                    Ok(Self::Error(opcode, ErrorCode::CborFailure))
                }
                _ => Err(format!("Unrecognized error code {rc}")),
            },
        }
    }
}

/// Error code values.
#[derive(Debug, PartialEq, Eq, Clone, Copy)]
#[repr(u32)]
pub enum ErrorCode {
    /// Success
    Ok = 0,
    /// Failed to CBOR-serialize data.
    CborFailure = 1,
}

#[cfg(test)]
mod tests {
    use super::*;
    use alloc::vec;

    #[test]
    fn test_deserialize_req() {
        let tests = [
            ("00000001", Ok(Request::GetIdentityKey)),
            ("00000002", Err("unrecognized opcode 2".to_string())),
            ("000000", Err("data too short (len=3)".to_string())),
        ];
        for (hexdata, want) in tests {
            let data = hex::decode(hexdata).unwrap();
            let got = Request::from_slice(&data);
            assert_eq!(got, want, "Failed for input {hexdata}");
        }
    }

    #[test]
    fn test_serialize_rsp() {
        let tests = [
            (
                Response::IdentityKey(vec![1, 2, 3]),
                concat!(
                    "80000001", // opcode
                    "00000000", // errcode
                    "010203"
                ),
            ),
            (
                Response::Error(OpCode::GetIdentityKey, ErrorCode::CborFailure),
                concat!(
                    "80000001", // opcode
                    "00000001"  // errcode
                ),
            ),
        ];
        for (rsp, want) in tests {
            let got_data = rsp.clone().into_vec();
            let got = hex::encode(&got_data);
            assert_eq!(got, want, "Failed for input {rsp:?}");

            let recovered = Response::from_slice(&got_data)
                .unwrap_or_else(|e| panic!("failed to deserialize rsp from {got}: {e:?}"));
            assert_eq!(rsp, recovered, "Failed to rebuild response from {got}");
        }
    }
}
