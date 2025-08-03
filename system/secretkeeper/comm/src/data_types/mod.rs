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

//! Implements the data structures specified by SecretManagement.cddl in Secretkeeper HAL.
//!  Data structures specified by SecretManagement.cddl in Secretkeeper HAL.
//!  Note this library must stay in sync with:
//!      platform/hardware/interfaces/security/\
//!      secretkeeper/aidl/android/hardware/security/secretkeeper/SecretManagement.cddl

pub mod error;
pub mod packet;
pub mod request;
pub mod request_response_impl;
pub mod response;
use crate::data_types::error::Error;
use alloc::vec::Vec;
use ciborium::Value;
use coset::{AsCborValue, CborSerializable, CoseError};
use zeroize::ZeroizeOnDrop;
use CoseError::UnexpectedItem;

/// Size of the `id` bstr in SecretManagement.cddl
pub const ID_SIZE: usize = 64;
/// Size of the `secret` bstr in SecretManagement.cddl
pub const SECRET_SIZE: usize = 32;

/// Identifier of Secret. See `id` in SecretManagement.cddl
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct Id(pub [u8; ID_SIZE]);

impl AsCborValue for Id {
    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        Ok(Self(
            value
                .into_bytes()
                .map_err(|_| UnexpectedItem("-", "Bytes"))?
                .try_into()
                .map_err(|_| UnexpectedItem("Bytes", "64 Bytes"))?,
        ))
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        Ok(Value::Bytes(self.0.to_vec()))
    }
}

impl CborSerializable for Id {}

/// Secret - corresponds to `secret` in SecretManagement.cddl
#[derive(Clone, Eq, PartialEq, ZeroizeOnDrop)]
pub struct Secret(pub [u8; SECRET_SIZE]);
impl AsCborValue for Secret {
    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        Ok(Self(
            value
                .into_bytes()
                .map_err(|_| UnexpectedItem("-", "Bytes"))?
                .try_into()
                .map_err(|_| UnexpectedItem("Bytes", "32 Bytes"))?,
        ))
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        Ok(Value::Bytes(self.0.to_vec()))
    }
}

impl CborSerializable for Secret {}

impl core::fmt::Debug for Secret {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        write!(f, "Sensitive information omitted")
    }
}

/// Used to represent `RequestSeqNum` & `ResponseSeqNum` in SecretManagement.cddl
#[derive(Default)]
pub struct SeqNum(u64);

impl SeqNum {
    /// Construct a new SeqNum. All new instances start with 0
    pub fn new() -> Self {
        Self(0)
    }

    /// Get the encoded SeqNum & increment the underlying integer by +1. This method uses
    /// `u64::checked_add`, thereby preventing wrapping around of SeqNum.
    pub fn get_then_increment(&mut self) -> Result<Vec<u8>, Error> {
        let enc = Value::Integer(self.0.into()).to_vec().map_err(|_| Error::ConversionError)?;
        self.0 = self.0.checked_add(1).ok_or(Error::SequenceNumberExhausted)?;
        Ok(enc)
    }
}
