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

//! CBOR serialization code.

use alloc::{vec, vec::Vec};
use ciborium::value::Value;

/// Marker structure indicating that the EOF was encountered when reading CBOR data.
#[derive(Debug)]
pub struct EndOfFile;

/// Error type for failures in encoding or decoding CBOR types.
pub enum CborError {
    /// CBOR decoding failure.
    DecodeFailed(ciborium::de::Error<EndOfFile>),
    /// CBOR encoding failure.
    EncodeFailed,
    /// CBOR input had extra data.
    ExtraneousData,
    /// Integer value outside expected range.
    OutOfRangeIntegerValue,
    /// Integer value that doesn't match expected set of allowed enum values.
    NonEnumValue,
    /// Unexpected CBOR item encountered (got, want).
    UnexpectedItem(&'static str, &'static str),
    /// Value conversion failure.
    InvalidValue,
    /// Allocation failure.
    AllocationFailed,
}

impl<T> From<ciborium::de::Error<T>> for CborError {
    fn from(e: ciborium::de::Error<T>) -> Self {
        // Make sure we use our [`EndOfFile`] marker.
        use ciborium::de::Error::{Io, RecursionLimitExceeded, Semantic, Syntax};
        let e = match e {
            Io(_) => Io(EndOfFile),
            Syntax(x) => Syntax(x),
            Semantic(a, b) => Semantic(a, b),
            RecursionLimitExceeded => RecursionLimitExceeded,
        };
        CborError::DecodeFailed(e)
    }
}

impl<T> From<ciborium::ser::Error<T>> for CborError {
    fn from(_e: ciborium::ser::Error<T>) -> Self {
        CborError::EncodeFailed
    }
}

impl core::fmt::Debug for CborError {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        match self {
            CborError::DecodeFailed(de) => write!(f, "decode CBOR failure: {:?}", de),
            CborError::EncodeFailed => write!(f, "encode CBOR failure"),
            CborError::ExtraneousData => write!(f, "extraneous data in CBOR input"),
            CborError::OutOfRangeIntegerValue => write!(f, "out of range integer value"),
            CborError::NonEnumValue => write!(f, "integer not a valid enum value"),
            CborError::UnexpectedItem(got, want) => write!(f, "got {}, expected {}", got, want),
            CborError::InvalidValue => write!(f, "invalid CBOR value"),
            CborError::AllocationFailed => write!(f, "allocation failed"),
        }
    }
}

/// Return an error indicating that an unexpected CBOR type was encountered.
pub fn cbor_type_error<T>(value: &Value, want: &'static str) -> Result<T, CborError> {
    let got = match value {
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
    Err(CborError::UnexpectedItem(got, want))
}

/// Read a [`Value`] from a byte slice, failing if any extra data remains after the `Value` has been
/// read.
pub fn read_to_value(mut slice: &[u8]) -> Result<Value, CborError> {
    let value = ciborium::de::from_reader_with_recursion_limit(&mut slice, 16)?;
    if slice.is_empty() {
        Ok(value)
    } else {
        Err(CborError::ExtraneousData)
    }
}

/// Trait for types that can be converted to/from a [`Value`]. This is essentially the same as the
/// `coset::AsCborValue` trait, but defining a copy locally allows us to implement it for primitive
/// types without falling foul of the orphan trait rules.
pub trait AsCborValue: Sized {
    /// Convert a [`Value`] into an instance of the type.
    fn from_cbor_value(value: Value) -> Result<Self, CborError>;

    /// Convert the object into a [`Value`], consuming it along the way.
    fn to_cbor_value(self) -> Result<Value, CborError>;

    /// Create an object instance from serialized CBOR data in a slice.
    fn from_slice(slice: &[u8]) -> Result<Self, CborError> {
        Self::from_cbor_value(read_to_value(slice)?)
    }

    /// Serialize this object to a vector, consuming it along the way.
    fn into_vec(self) -> Result<Vec<u8>, CborError> {
        let mut data = Vec::new();
        ciborium::ser::into_writer(&self.to_cbor_value()?, &mut data)?;
        Ok(data)
    }
}

/// An `Option<T>` encodes as `( ? t )`, where `t` is whatever `T` encodes as in CDDL.
impl<T: AsCborValue> AsCborValue for Option<T> {
    fn from_cbor_value(value: Value) -> Result<Self, CborError> {
        let mut arr = match value {
            Value::Array(a) => a,
            _ => return Err(CborError::UnexpectedItem("non-arr", "arr")),
        };
        match arr.len() {
            0 => Ok(None),
            1 => Ok(Some(<T>::from_cbor_value(arr.remove(0))?)),
            _ => Err(CborError::UnexpectedItem("arr len >1", "arr len 0/1")),
        }
    }
    fn to_cbor_value(self) -> Result<Value, CborError> {
        match self {
            Some(t) => Ok(Value::Array(vec![t.to_cbor_value()?])),
            None => Ok(Value::Array(Vec::new())),
        }
    }
}

impl<T: AsCborValue, const N: usize> AsCborValue for [T; N] {
    fn from_cbor_value(value: Value) -> Result<Self, CborError> {
        let arr = match value {
            Value::Array(a) => a,
            _ => return cbor_type_error(&value, "arr"),
        };
        let results: Result<Vec<_>, _> = arr.into_iter().map(<T>::from_cbor_value).collect();
        let results: Vec<_> = results?;
        results.try_into().map_err(|_e| CborError::UnexpectedItem("arr other len", "arr fixed len"))
    }
    fn to_cbor_value(self) -> Result<Value, CborError> {
        let values: Result<Vec<_>, _> = self.into_iter().map(|v| v.to_cbor_value()).collect();
        Ok(Value::Array(values?))
    }
}

impl AsCborValue for Vec<u8> {
    fn from_cbor_value(value: Value) -> Result<Self, CborError> {
        match value {
            Value::Bytes(bstr) => Ok(bstr),
            _ => cbor_type_error(&value, "bstr"),
        }
    }
    fn to_cbor_value(self) -> Result<Value, CborError> {
        Ok(Value::Bytes(self))
    }
}

impl AsCborValue for i32 {
    fn from_cbor_value(value: Value) -> Result<Self, CborError> {
        match value {
            Value::Integer(i) => i.try_into().map_err(|_| CborError::OutOfRangeIntegerValue),
            _ => crate::cbor_type_error(&value, "i32"),
        }
    }
    fn to_cbor_value(self) -> Result<Value, CborError> {
        Ok(Value::Integer(self.into()))
    }
}
