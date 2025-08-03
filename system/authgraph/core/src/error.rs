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

//! Error Handling

use alloc::string::String;
use authgraph_wire::ErrorCode;
use coset::CoseError;

/// AuthGraph error type.
#[derive(Debug)]
pub struct Error(pub ErrorCode, pub String);

impl core::convert::From<CoseError> for Error {
    fn from(e: CoseError) -> Self {
        crate::ag_err!(InternalError, "CoseError: {}", e)
    }
}

impl From<alloc::collections::TryReserveError> for Error {
    fn from(e: alloc::collections::TryReserveError) -> Self {
        crate::ag_err!(MemoryAllocationFailed, "allocation of Vec failed: {:?}", e)
    }
}

/// Macro to build an [`Error`] instance.
/// E.g. use: `ag_err!(InternalError, "some {} format", arg)`.
#[macro_export]
macro_rules! ag_err {
    { $error_code:ident, $($arg:tt)+ } => {
        Error(ErrorCode::$error_code,
              alloc::format!("{}:{}: {}", file!(), line!(), format_args!($($arg)+))) };
}

/// Macro to build an [`Error`] instance.
/// E.g. use: `ag_verr!(rc, "some {} format", arg)`.
#[macro_export]
macro_rules! ag_verr {
    { $error_code:expr, $($arg:tt)+ } => {
          Error($error_code,
                alloc::format!("{}:{}: {}", file!(), line!(), format_args!($($arg)+))) };
}
