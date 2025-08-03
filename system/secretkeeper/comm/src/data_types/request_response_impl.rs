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

//! Implementation of request & response like data structures.

// derive(N) generates a method that is missing a docstring.
#![allow(missing_docs)]

use crate::cbor_convert::value_to_integer;
use crate::data_types::error::Error;
use crate::data_types::error::ERROR_OK;
use crate::data_types::request::Request;
use crate::data_types::response::Response;
use crate::data_types::{Id, Secret};
use alloc::boxed::Box;
use alloc::vec;
use alloc::vec::Vec;
use ciborium::Value;
use coset::AsCborValue;
use enumn::N;

/// Set of all possible `Opcode` supported by SecretManagement API of the HAL.
/// See `Opcode` in SecretManagement.cddl
#[derive(Clone, Copy, Debug, N, PartialEq)]
#[non_exhaustive]
pub enum Opcode {
    /// Get version of the SecretManagement API.
    GetVersion = 1,
    /// Store a secret
    StoreSecret = 2,
    /// Get the secret
    GetSecret = 3,
}

/// Corresponds to `GetVersionRequestPacket` defined in SecretManagement.cddl
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GetVersionRequest;

impl Request for GetVersionRequest {
    const OPCODE: Opcode = Opcode::GetVersion;

    fn new(args: Vec<Value>) -> Result<Box<Self>, Error> {
        if !args.is_empty() {
            return Err(Error::RequestMalformed);
        }
        Ok(Box::new(Self))
    }

    fn args(&self) -> Vec<Value> {
        Vec::new()
    }
}

/// Success response corresponding to `GetVersionResponsePacket`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GetVersionResponse {
    /// Version of SecretManagement API
    pub version: u64,
}

impl Response for GetVersionResponse {
    fn new(res: Vec<Value>) -> Result<Box<Self>, Error> {
        if res.len() != 2 || value_to_integer(&res[0])? != ERROR_OK.into() {
            return Err(Error::ResponseMalformed);
        }
        let version: u64 = value_to_integer(&res[1])?.try_into()?;
        Ok(Box::new(Self { version }))
    }

    fn result(&self) -> Vec<Value> {
        vec![self.version.into()]
    }
}

/// Corresponds to `StoreSecretRequestPacket` in SecretManagement.cddl
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct StoreSecretRequest {
    // Unique identifier of the secret
    pub id: Id,
    // The secret to be stored :)
    pub secret: Secret,
    // The DICE policy to control access to secret
    pub sealing_policy: Vec<u8>,
}

impl Request for StoreSecretRequest {
    const OPCODE: Opcode = Opcode::StoreSecret;

    fn new(args: Vec<Value>) -> Result<Box<Self>, Error> {
        let [id, secret, sealing_policy] = args.try_into().map_err(|_| Error::RequestMalformed)?;
        let id = Id::from_cbor_value(id)?;
        let secret = Secret::from_cbor_value(secret)?;
        let sealing_policy = sealing_policy.into_bytes()?;
        Ok(Box::new(Self { id, secret, sealing_policy }))
    }

    fn args(&self) -> Vec<Value> {
        vec![
            Value::from(self.id.0.as_slice()),
            Value::from(self.secret.0.as_slice()),
            Value::from(self.sealing_policy.clone()),
        ]
    }
}

/// Success response corresponding to `StoreSecretResponsePacket`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct StoreSecretResponse {}

impl Response for StoreSecretResponse {
    fn new(response_cbor: Vec<Value>) -> Result<Box<Self>, Error> {
        if response_cbor.len() != 1 || (value_to_integer(&response_cbor[0])? != ERROR_OK.into()) {
            return Err(Error::ResponseMalformed);
        }
        Ok(Box::new(Self {}))
    }
}

/// Corresponds to `GetSecretRequestPacket` in SecretManagement.cddl
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GetSecretRequest {
    // Unique identifier of the secret.
    pub id: Id,
    // The new dice_policy that will replace the existing sealing_policy of the secret.
    pub updated_sealing_policy: Option<Vec<u8>>,
}

impl Request for GetSecretRequest {
    const OPCODE: Opcode = Opcode::GetSecret;

    fn new(args: Vec<Value>) -> Result<Box<Self>, Error> {
        let [id, sealing_policy] = args.try_into().map_err(|_| Error::RequestMalformed)?;
        let id = Id::from_cbor_value(id).map_err(|_| Error::RequestMalformed)?;
        let updated_sealing_policy =
            if sealing_policy.is_null() { None } else { Some(sealing_policy.into_bytes()?) };
        Ok(Box::new(Self { id, updated_sealing_policy }))
    }

    fn args(&self) -> Vec<Value> {
        let id = Value::from(self.id.0.as_slice());
        let policy: Value =
            self.updated_sealing_policy.as_ref().map_or(Value::Null, |x| x.as_slice().into());
        vec![id, policy]
    }
}

/// Success response corresponding to `GetSecretResponsePacket`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GetSecretResponse {
    pub secret: Secret,
}

impl Response for GetSecretResponse {
    fn new(res: Vec<Value>) -> Result<Box<Self>, Error> {
        let [error_code, secret] = res.try_into().map_err(|_| Error::ResponseMalformed)?;
        if error_code != ERROR_OK.into() {
            return Err(Error::ResponseMalformed);
        }
        let secret = Secret::from_cbor_value(secret).map_err(|_| Error::ResponseMalformed)?;
        Ok(Box::new(Self { secret }))
    }

    fn result(&self) -> Vec<Value> {
        vec![self.secret.0.as_slice().into()]
    }
}
