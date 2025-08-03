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

//! Error-like data structures. See `ResponsePacketError` in the CDDL

// derive(N) generates a method that is missing a docstring.
#![allow(missing_docs)]

use crate::cbor_convert::value_to_integer;
use crate::data_types::response::Response;
use alloc::boxed::Box;
use alloc::vec::Vec;
use ciborium::Value;
use coset::CoseError;
use enumn::N;

/// 'Error code' corresponding to successful response.
pub const ERROR_OK: u16 = 0; // All real errors must have non-zero error_codes

/// Errors from Secretkeeper API. Keep in sync with `ErrorCode` defined for Secretkeeper HAL
/// at SecretManagement.cddl
#[derive(Clone, Copy, Debug, Eq, N, PartialEq)]
pub enum SecretkeeperError {
    // This is the Error code used if no other error codes explains the issue.
    UnexpectedServerError = 1,
    // Indicates the Request was malformed & hence couldn't be served.
    RequestMalformed = 2,
    /// Requested Entry not found.
    EntryNotFound = 3,
    /// Errors originating during serialization or deserialization
    SerializationError = 4,
    /// Policy matching did not succeed & hence access not granted.
    DicePolicyError = 5,
}

// [`SecretkeeperError`] is a valid [`Response`] type.
// For more information see `ErrorCode` in SecretManagement.cddl alongside ISecretkeeper.aidl
impl Response for SecretkeeperError {
    fn new(response_cbor: Vec<Value>) -> Result<Box<Self>, Error> {
        // TODO(b/291228655): This method currently discards the second value in response_cbor,
        // which contains additional human-readable context in error. Include it!
        if response_cbor.is_empty() || response_cbor.len() > 2 {
            return Err(Error::ResponseMalformed);
        }
        let error_code: u16 = value_to_integer(&response_cbor[0])?.try_into()?;
        SecretkeeperError::n(error_code)
            .map_or_else(|| Err(Error::ResponseMalformed), |sk_err| Ok(Box::new(sk_err)))
    }

    fn error_code(&self) -> u16 {
        *self as u16
    }
}

/// Errors thrown internally by the library.
#[derive(Debug, PartialEq)]
pub enum Error {
    /// Request was malformed.
    RequestMalformed,
    /// Response received from the server was malformed.
    ResponseMalformed,
    /// An error happened when serializing to/from a [`Value`].
    CborValueError,
    /// An error happened while casting a type to different type,
    /// including one [`Value`] type to another.
    ConversionError,
    /// These are unexpected errors, which should never really happen.
    UnexpectedError,
    /// Sequence number has reached the upper limit.
    SequenceNumberExhausted,
}

impl From<ciborium::value::Error> for Error {
    fn from(_e: ciborium::value::Error) -> Self {
        Self::CborValueError
    }
}

impl From<ciborium::Value> for Error {
    fn from(_e: ciborium::Value) -> Self {
        Self::ConversionError
    }
}

impl From<core::num::TryFromIntError> for Error {
    fn from(_e: core::num::TryFromIntError) -> Self {
        Self::ConversionError
    }
}

impl From<coset::CoseError> for Error {
    fn from(e: coset::CoseError) -> Self {
        match e {
            CoseError::DecodeFailed(_)
            | CoseError::EncodeFailed
            | CoseError::UnexpectedItem(_, _) => Self::ConversionError,
            _ => {
                // TODO: Map other COSE errors to precise errors when we use coset more heavily.
                Self::UnexpectedError
            }
        }
    }
}
