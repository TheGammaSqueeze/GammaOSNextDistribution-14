/*
 * Copyright (C) 2022 The Android Open Source Project
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

use super::*;

use core::{array::TryFromSliceError, num::TryFromIntError};
use tipc::TipcError;
use trusty_std::alloc::AllocError;
use trusty_sys::Error;

/// Errors that the HwWsk client and service may encounter.
#[derive(Debug, Eq, PartialEq)]
pub enum HwWskError {
    /// The requested command or specified parameter is not supported.
    NotSupported,
    /// A generic error received by the client as a response from the service.
    Generic,
    /// An invalid command or command parameter specified.
    NotValid,
    /// An unexpected or unaccepted buffer or data length.
    BadLen,
    /// An integer overflow error or bad cast.
    OutOfBounds,
    /// An allocation failure that may be due to resource exhaustion.
    AllocError,
    /// The client receives a response from the service that is invalid.
    InvalidCmdResponse,
    /// A conversion from a slice to an array fails.
    ConversionError,
    /// Some tipc error.
    Tipc(TipcError),
    /// Some other system error.
    System(Error),
}

impl HwWskError {
    pub(crate) fn from_status(rc: u32) -> Result<(), Self> {
        #[allow(non_upper_case_globals)]
        match rc.into() {
            hwwsk_err_HWWSK_NO_ERROR => Ok(()),
            hwwsk_err_HWWSK_ERR_INVALID_ARGS => Err(HwWskError::NotValid),
            hwwsk_err_HWWSK_ERR_NOT_SUPPORTED => Err(HwWskError::NotSupported),
            hwwsk_err_HWWSK_ERR_BAD_LEN => Err(HwWskError::BadLen),
            _ => Err(HwWskError::Generic),
        }
    }
}

impl From<TipcError> for HwWskError {
    fn from(err: TipcError) -> Self {
        HwWskError::Tipc(err)
    }
}

impl From<Error> for HwWskError {
    fn from(err: Error) -> Self {
        HwWskError::System(err)
    }
}

impl From<TryFromIntError> for HwWskError {
    fn from(_err: TryFromIntError) -> Self {
        HwWskError::OutOfBounds
    }
}

impl From<AllocError> for HwWskError {
    fn from(_err: AllocError) -> Self {
        HwWskError::AllocError
    }
}

impl From<TryFromSliceError> for HwWskError {
    fn from(_err: TryFromSliceError) -> Self {
        HwWskError::ConversionError
    }
}
