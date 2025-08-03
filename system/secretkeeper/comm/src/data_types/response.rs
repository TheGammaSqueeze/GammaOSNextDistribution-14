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

//! Defines the shared behaviour of all response like data structures.

use crate::data_types::error::{Error, ERROR_OK};
use crate::data_types::packet::ResponsePacket;
use alloc::boxed::Box;
use alloc::vec::Vec;
use ciborium::Value;

/// Shared behaviour of all Secretkeeper's response-like data structures,
/// e.g. `GetVersionResponsePacket`. Note - A valid [`Response`] can be error as well, like
/// `SecretkeeperError::RequestMalformed`.
///
/// Keep in sync with SecretManagement.cddl, in particular `ResponsePacket` type.
pub trait Response {
    /// Constructor of the Response object.
    /// # Arguments
    /// * `response_cbor`: A vector of `[ciborium::Value]` such that:
    /// ```
    ///     For success-like responses:
    ///         ResponsePacketSuccess = [
    ///             0,                          ; Indicates successful Response
    ///             result : Result
    ///         ]
    ///     For error responses:
    ///         ResponsePacketError = [
    ///             error_code: ErrorCode,      ; Indicate the error
    ///             error_message: tstr         ; Additional human-readable context
    ///         ]
    /// ```
    /// See `ResponsePacket<Result>` in SecretManagement.cddl alongside ISecretkeeper.aidl
    fn new(response_cbor: Vec<Value>) -> Result<Box<Self>, Error>;

    /// The result in the `Response`. By default this is empty, but [`Response`] structures like
    /// `GetVersionResponse` must overwrite these to return the expected non-empty result.
    fn result(&self) -> Vec<Value> {
        Vec::new()
    }

    /// Error code corresponding to the response. The default value is 0 but that will work only
    /// for successful responses. Error-like response structures must overwrite this method.
    fn error_code(&self) -> u16 {
        ERROR_OK // Indicates success
    }

    /// Serialize the response to a [`ResponsePacket`].
    fn serialize_to_packet(&self) -> ResponsePacket {
        let mut res = self.result();
        res.insert(0, Value::from(self.error_code()));
        ResponsePacket::from(res)
    }

    /// Construct the response struct from given [`ResponsePacket`].
    fn deserialize_from_packet(packet: ResponsePacket) -> Result<Box<Self>, Error> {
        let res = packet.into_inner();
        // Empty response packet is not allowed, all responses in Secretkeeper HAL at least
        // have `error_code` or '0'; so throw an error!
        if res.is_empty() {
            return Err(Error::ResponseMalformed);
        }
        Self::new(res)
    }
}
