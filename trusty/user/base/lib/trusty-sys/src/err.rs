/*
 * Copyright (C) 2021 The Android Open Source Project
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
use crate::types::c_long;

/// Trusty system error
///
/// Equivalent to the error codes from the Trusty kernel in `uapi/err.h`. These
/// error codes should only be used for interfacing with the kernel and non-Rust
/// libraries. Rust APIs should provide their own idiomatic error types.
#[repr(i32)]
#[non_exhaustive]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Error {
    NoError = NO_ERROR as i32,
    Generic = ERR_GENERIC,
    NotFound = ERR_NOT_FOUND,
    NotReady = ERR_NOT_READY,
    NoMsg = ERR_NO_MSG,
    NoMemory = ERR_NO_MEMORY,
    AlreadyStarted = ERR_ALREADY_STARTED,
    NotValid = ERR_NOT_VALID,
    InvalidArgs = ERR_INVALID_ARGS,
    NotEnoughBuffer = ERR_NOT_ENOUGH_BUFFER,
    NotSuspended = ERR_NOT_SUSPENDED,
    ObjectDestroyed = ERR_OBJECT_DESTROYED,
    NotBlocked = ERR_NOT_BLOCKED,
    TimedOut = ERR_TIMED_OUT,
    AlreadyExists = ERR_ALREADY_EXISTS,
    ChannelClosed = ERR_CHANNEL_CLOSED,
    Offline = ERR_OFFLINE,
    NotAllowed = ERR_NOT_ALLOWED,
    BadPath = ERR_BAD_PATH,
    AlreadyMounted = ERR_ALREADY_MOUNTED,
    IO = ERR_IO,
    NotDir = ERR_NOT_DIR,
    NotFile = ERR_NOT_FILE,
    RecurseTooDeep = ERR_RECURSE_TOO_DEEP,
    NotSupported = ERR_NOT_SUPPORTED,
    TooBig = ERR_TOO_BIG,
    Cancelled = ERR_CANCELLED,
    NotImplemented = ERR_NOT_IMPLEMENTED,
    ChecksumFail = ERR_CHECKSUM_FAIL,
    CrcFail = ERR_CRC_FAIL,
    CmdUnknown = ERR_CMD_UNKNOWN,
    BadState = ERR_BAD_STATE,
    BadLen = ERR_BAD_LEN,
    Busy = ERR_BUSY,
    ThreadDetached = ERR_THREAD_DETACHED,
    I2CNack = ERR_I2C_NACK,
    AlreadyExpired = ERR_ALREADY_EXPIRED,
    OutOfRange = ERR_OUT_OF_RANGE,
    NotConfigured = ERR_NOT_CONFIGURED,
    NotMounted = ERR_NOT_MOUNTED,
    Fault = ERR_FAULT,
    NoResources = ERR_NO_RESOURCES,
    BadHandle = ERR_BAD_HANDLE,
    AccessDenied = ERR_ACCESS_DENIED,
    PartialWrite = ERR_PARTIAL_WRITE,
    UserBase = ERR_USER_BASE,
}

impl Error {
    pub fn is_err(rc: c_long) -> bool {
        rc != NO_ERROR as c_long
    }
}

impl From<c_long> for Error {
    fn from(rc: c_long) -> Self {
        use Error::*;

        if rc > i32::MAX as c_long || rc < i32::MIN as c_long {
            return Generic;
        }
        match rc as i32 {
            rc if rc == NO_ERROR as i32 => NoError,
            ERR_GENERIC => Generic,
            ERR_NOT_FOUND => NotFound,
            ERR_NOT_READY => NotReady,
            ERR_NO_MSG => NoMsg,
            ERR_NO_MEMORY => NoMemory,
            ERR_ALREADY_STARTED => AlreadyStarted,
            ERR_NOT_VALID => NotValid,
            ERR_INVALID_ARGS => InvalidArgs,
            ERR_NOT_ENOUGH_BUFFER => NotEnoughBuffer,
            ERR_NOT_SUSPENDED => NotSuspended,
            ERR_OBJECT_DESTROYED => ObjectDestroyed,
            ERR_NOT_BLOCKED => NotBlocked,
            ERR_TIMED_OUT => TimedOut,
            ERR_ALREADY_EXISTS => AlreadyExists,
            ERR_CHANNEL_CLOSED => ChannelClosed,
            ERR_OFFLINE => Offline,
            ERR_NOT_ALLOWED => NotAllowed,
            ERR_BAD_PATH => BadPath,
            ERR_ALREADY_MOUNTED => AlreadyMounted,
            ERR_IO => IO,
            ERR_NOT_DIR => NotDir,
            ERR_NOT_FILE => NotFile,
            ERR_RECURSE_TOO_DEEP => RecurseTooDeep,
            ERR_NOT_SUPPORTED => NotSupported,
            ERR_TOO_BIG => TooBig,
            ERR_CANCELLED => Cancelled,
            ERR_NOT_IMPLEMENTED => NotImplemented,
            ERR_CHECKSUM_FAIL => ChecksumFail,
            ERR_CRC_FAIL => CrcFail,
            ERR_CMD_UNKNOWN => CmdUnknown,
            ERR_BAD_STATE => BadState,
            ERR_BAD_LEN => BadLen,
            ERR_BUSY => Busy,
            ERR_THREAD_DETACHED => ThreadDetached,
            ERR_I2C_NACK => I2CNack,
            ERR_ALREADY_EXPIRED => AlreadyExpired,
            ERR_OUT_OF_RANGE => OutOfRange,
            ERR_NOT_CONFIGURED => NotConfigured,
            ERR_NOT_MOUNTED => NotMounted,
            ERR_FAULT => Fault,
            ERR_NO_RESOURCES => NoResources,
            ERR_BAD_HANDLE => BadHandle,
            ERR_ACCESS_DENIED => AccessDenied,
            ERR_PARTIAL_WRITE => PartialWrite,
            ERR_USER_BASE => UserBase,
            _ => Generic,
        }
    }
}
