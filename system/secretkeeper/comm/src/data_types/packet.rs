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

//! Defines the packet structures passed between functional layer & the layer below.

use ciborium::Value;

use crate::cbor_convert::value_to_integer;
use crate::data_types::error::Error;
use crate::data_types::error::ERROR_OK;
use crate::data_types::request_response_impl::Opcode;
use alloc::vec::Vec;
use coset::{AsCborValue, CborSerializable, CoseError};

/// Encapsulate Request-like data that functional layer operates on. All structures
/// that implements `data_types::request::Request` can be serialized to [`ResponsePacket`].
/// Similarly all [`RequestPacket`] can be deserialized to concrete Requests.
/// Keep in sync with HAL spec (in particular RequestPacket):
///     security/secretkeeper/aidl/android/hardware/security/secretkeeper/SecretManagement.cddl
#[derive(Clone, PartialEq)]
pub struct RequestPacket(Vec<Value>);

impl RequestPacket {
    /// Construct a [`RequestPacket`] from array of `ciborium::Value`
    pub fn from(request_cbor: Vec<Value>) -> Self {
        Self(request_cbor)
    }

    /// Get the containing CBOR. This can be used for getting concrete response objects.
    /// Keep in sync with [`crate::data_types::request::Request::serialize_to_packet()`]
    pub fn into_inner(self) -> Vec<Value> {
        self.0
    }

    /// Extract [`Opcode`] corresponding to this packet. As defined in by the spec, this is
    /// the first value in the CBOR array.
    pub fn opcode(&self) -> Result<Opcode, Error> {
        if self.0.is_empty() {
            return Err(Error::RequestMalformed);
        }
        let num: u16 = value_to_integer(&self.0[0])?.try_into()?;

        Opcode::n(num).ok_or(Error::RequestMalformed)
    }
}

impl AsCborValue for RequestPacket {
    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        Ok(Self(value.into_array().map_err(|_| CoseError::UnexpectedItem("-", "Array"))?))
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        Ok(Value::Array(self.0))
    }
}

// Serialize/Deserialize the [`RequestPacket`] to bytes
impl CborSerializable for RequestPacket {}

/// Encapsulate Response like data that the functional layer operates on. All structures
/// that implements `data_types::response::Response` can be serialized to [`ResponsePacket`].
/// Similarly all [`ResponsePacket`] can be deserialized to concrete Response.
#[derive(Clone, PartialEq)]
pub struct ResponsePacket(Vec<Value>);

impl ResponsePacket {
    /// Construct a [`ResponsePacket`] from array of `ciborium::Value`
    pub fn from(response_cbor: Vec<Value>) -> Self {
        Self(response_cbor)
    }

    /// Get raw content. This can be used for getting concrete response objects.
    /// Keep in sync with `crate::data_types::response::Response::serialize_to_packet`
    pub fn into_inner(self) -> Vec<Value> {
        self.0
    }

    /// A [`ResponsePacket`] encapsulates different types of responses, find which one!
    pub fn response_type(&self) -> Result<ResponseType, Error> {
        if self.0.is_empty() {
            return Err(Error::ResponseMalformed);
        }
        let error_code: u16 = value_to_integer(&self.0[0])?.try_into()?;
        if error_code == ERROR_OK {
            Ok(ResponseType::Success)
        } else {
            Ok(ResponseType::Error)
        }
    }
}

impl AsCborValue for ResponsePacket {
    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        Ok(Self(value.into_array().map_err(|_| CoseError::UnexpectedItem("-", "Array"))?))
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        Ok(Value::Array(self.0))
    }
}

// Serialize/Deserialize the [`ResponsePacket`] to bytes
impl CborSerializable for ResponsePacket {}

/// Responses can be different type - `Success`-like or `Error`-like.
#[derive(Debug, Eq, PartialEq)]
pub enum ResponseType {
    /// Indicates successful operation. See `ResponsePacketSuccess` in SecretManagement.cddl
    Success,
    /// Indicate failed operation. See `ResponsePacketError` in SecretManagement.cddl
    Error,
}
