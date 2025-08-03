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

use crate::sys::*;
use alloc::alloc::AllocError;
use core::ffi::FromBytesWithNulError;
use core::num::TryFromIntError;
use tipc::TipcError;
use trusty_sys::Error;

#[derive(Debug, Eq, PartialEq)]
pub enum HwkeyError {
    Generic,
    NotValid,
    BadLen,
    NotImplemented,
    NotFound,
    AlreadyExists,
    AllocError,
    InvalidCmdResponse,
    OutOfBounds,
    System(Error),
    Tipc(TipcError),
}

impl HwkeyError {
    pub(crate) fn from_hwkey_rc(rc: u32) -> Result<(), Self> {
        #[allow(non_upper_case_globals)]
        match rc.into() {
            hwkey_err_HWKEY_NO_ERROR => Ok(()),
            hwkey_err_HWKEY_ERR_GENERIC => Err(Self::Generic),
            hwkey_err_HWKEY_ERR_NOT_VALID => Err(Self::NotValid),
            hwkey_err_HWKEY_ERR_BAD_LEN => Err(Self::BadLen),
            hwkey_err_HWKEY_ERR_NOT_IMPLEMENTED => Err(Self::NotImplemented),
            hwkey_err_HWKEY_ERR_NOT_FOUND => Err(Self::NotFound),
            hwkey_err_HWKEY_ERR_ALREADY_EXISTS => Err(Self::AlreadyExists),
            _ => Err(Self::Generic),
        }
    }
}

impl From<TipcError> for HwkeyError {
    fn from(e: TipcError) -> Self {
        Self::Tipc(e)
    }
}

impl From<Error> for HwkeyError {
    fn from(e: Error) -> Self {
        Self::System(e)
    }
}

impl From<TryFromIntError> for HwkeyError {
    fn from(_err: TryFromIntError) -> Self {
        HwkeyError::OutOfBounds
    }
}

impl From<AllocError> for HwkeyError {
    fn from(_err: AllocError) -> Self {
        Self::AllocError
    }
}

impl From<FromBytesWithNulError> for HwkeyError {
    fn from(_err: FromBytesWithNulError) -> Self {
        Self::NotValid
    }
}
