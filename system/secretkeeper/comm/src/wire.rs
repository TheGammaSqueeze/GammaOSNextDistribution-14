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

//! Rust types used for CBOR-encoded communication between HAL and TA,
//! corresponding to the schema in `comm/InternalHalTaMessages.cddl`.

#![allow(missing_docs)] // needed for `enumn::N`, sadly

use alloc::{string::String, vec, vec::Vec};
use ciborium::value::Value;
use coset::{AsCborValue, CborSerializable, CoseError};
use enumn::N;

/// Wrapper type for communicating requests between the HAL service and the TA.
/// This is an internal implementation detail, and is not visible on the API.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PerformOpReq {
    /// A secret management request holds a CBOR-encoded `COSE_Encrypt0`.
    SecretManagement(Vec<u8>),

    /// A (plaintext) request to delete some `SecretId`s.
    DeleteIds(Vec<SecretId>),

    /// A (plaintext) request to delete all data.
    DeleteAll,
}

impl PerformOpReq {
    pub fn code(&self) -> OpCode {
        match self {
            Self::SecretManagement(_) => OpCode::SecretManagement,
            Self::DeleteIds(_) => OpCode::DeleteIds,
            Self::DeleteAll => OpCode::DeleteAll,
        }
    }
}

impl AsCborValue for PerformOpReq {
    fn to_cbor_value(self) -> Result<Value, CoseError> {
        Ok(Value::Array(match self {
            Self::SecretManagement(encrypt0) => {
                vec![OpCode::SecretManagement.to_cbor_value()?, Value::Bytes(encrypt0)]
            }
            Self::DeleteIds(ids) => {
                vec![
                    OpCode::DeleteIds.to_cbor_value()?,
                    Value::Array(
                        ids.into_iter().map(|id| Value::Bytes(id.to_vec())).collect::<Vec<Value>>(),
                    ),
                ]
            }
            Self::DeleteAll => vec![OpCode::DeleteAll.to_cbor_value()?, Value::Null],
        }))
    }

    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        let mut a = match value {
            Value::Array(a) if a.len() == 2 => a,
            _ => return cbor_type_error(&value, "arr len 2"),
        };
        let val = a.remove(1);
        let code = OpCode::from_cbor_value(a.remove(0))?;
        Ok(match code {
            OpCode::SecretManagement => Self::SecretManagement(match val {
                Value::Bytes(b) => b,
                _ => return cbor_type_error(&val, "bstr"),
            }),
            OpCode::DeleteIds => {
                let ids = match &val {
                    Value::Array(a) => a,
                    _ => return cbor_type_error(&val, "arr"),
                };
                let ids = ids
                    .iter()
                    .map(|id| match &id {
                        Value::Bytes(b) => SecretId::try_from(b.as_slice())
                            .map_err(|_e| CoseError::OutOfRangeIntegerValue),
                        _ => cbor_type_error(&val, "bstr"),
                    })
                    .collect::<Result<Vec<_>, _>>()?;
                Self::DeleteIds(ids)
            }
            OpCode::DeleteAll => {
                if !val.is_null() {
                    return cbor_type_error(&val, "nil");
                }
                Self::DeleteAll
            }
        })
    }
}

impl CborSerializable for PerformOpReq {}

/// Op code value to distinguish requests.
#[derive(Debug, Clone, Copy, PartialOrd, Ord, PartialEq, Eq, Hash, N)]
pub enum OpCode {
    SecretManagement = 0x10,
    DeleteIds = 0x11,
    DeleteAll = 0x12,
}

impl AsCborValue for OpCode {
    fn to_cbor_value(self) -> Result<Value, CoseError> {
        Ok(Value::Integer((self as i32).into()))
    }

    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        let i = match value {
            Value::Integer(i) => i,
            _ => return cbor_type_error(&value, "int"),
        };
        let code: i32 = i.try_into().map_err(|_| CoseError::OutOfRangeIntegerValue)?;
        OpCode::n(code).ok_or(CoseError::OutOfRangeIntegerValue)
    }
}

/// Wrapper type for communicating responses between the HAL service and the TA.
/// This is an internal implementation detail, and is not visible on the API.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PerformOpResponse {
    Success(PerformOpSuccessRsp),
    Failure(ApiError),
}

impl PerformOpResponse {
    pub fn err_code(&self) -> AidlErrorCode {
        match self {
            Self::Success(_) => AidlErrorCode::Ok,
            Self::Failure(err) => err.err_code,
        }
    }
}

impl AsCborValue for PerformOpResponse {
    fn to_cbor_value(self) -> Result<Value, CoseError> {
        Ok(match self {
            Self::Success(rsp) => {
                Value::Array(vec![Value::Integer(0.into()), rsp.to_cbor_value()?])
            }
            Self::Failure(err) => Value::Array(vec![
                Value::Integer((err.err_code as i32).into()),
                Value::Text(err.msg),
            ]),
        })
    }

    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        let mut a = match value {
            Value::Array(a) if a.len() == 2 => a,
            _ => return cbor_type_error(&value, "arr len 2"),
        };
        let val = a.remove(1);
        let err_code =
            a.remove(0).as_integer().ok_or(CoseError::UnexpectedItem("non-int", "int"))?;
        let err_code: i32 = err_code.try_into().map_err(|_| CoseError::OutOfRangeIntegerValue)?;
        Ok(match err_code {
            0 => Self::Success(PerformOpSuccessRsp::from_cbor_value(val)?),
            err_code => {
                let msg = match val {
                    Value::Text(t) => t,
                    _ => return cbor_type_error(&val, "tstr"),
                };
                let err_code = AidlErrorCode::n(err_code).unwrap_or(AidlErrorCode::InternalError);
                Self::Failure(ApiError { err_code, msg })
            }
        })
    }
}

impl CborSerializable for PerformOpResponse {}

/// Inner response type holding the result of a successful request.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PerformOpSuccessRsp {
    ProtectedResponse(Vec<u8>),
    Empty,
}

impl AsCborValue for PerformOpSuccessRsp {
    fn to_cbor_value(self) -> Result<Value, CoseError> {
        Ok(match self {
            Self::ProtectedResponse(data) => Value::Bytes(data),
            Self::Empty => Value::Null,
        })
    }

    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        match value {
            Value::Bytes(data) => Ok(Self::ProtectedResponse(data)),
            Value::Null => Ok(Self::Empty),
            _ => cbor_type_error(&value, "bstr/nil"),
        }
    }
}

impl CborSerializable for PerformOpSuccessRsp {}

/// Return an error indicating that an unexpected CBOR type was encountered.
pub fn cbor_type_error<T>(got: &Value, want: &'static str) -> Result<T, CoseError> {
    let got = match got {
        Value::Integer(_) => "int",
        Value::Bytes(_) => "bstr",
        Value::Text(_) => "tstr",
        Value::Array(_) => "array",
        Value::Map(_) => "map",
        Value::Tag(_, _) => "tag",
        Value::Float(_) => "float",
        Value::Bool(_) => "bool",
        Value::Null => "null",
        _ => "unknown",
    };
    Err(CoseError::UnexpectedItem(got, want))
}

/// Identifier for a secret
pub type SecretId = [u8; 64];

/// Error information reported visibly on the external API.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ApiError {
    pub err_code: AidlErrorCode,
    pub msg: String,
}

/// Error codes emitted as service specific errors at the HAL.
/// Keep in sync with the ERROR_ codes in:
/// hardware/interfaces/security/secretkeeper/aidl/
///   android/hardware/security/secretkeeper/ISecretkeeper.aidl
#[derive(Debug, Clone, Copy, PartialEq, Eq, N)]
pub enum AidlErrorCode {
    Ok = 0,
    UnknownKeyId = 1,
    InternalError = 2,
    RequestMalformed = 3,
}
