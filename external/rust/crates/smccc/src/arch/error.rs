// Copyright 2023 the authors.
// This project is dual-licensed under Apache 2.0 and MIT terms.
// See LICENSE-APACHE and LICENSE-MIT for details.

//! Error codes for standard Arm Architecture SMCCC calls.

pub use crate::error::SUCCESS;
use core::fmt::{self, Display, Formatter};

pub const NOT_SUPPORTED: i32 = -1;
pub const NOT_REQUIRED: i32 = -2;
pub const INVALID_PARAMETER: i32 = -3;

/// Errors for standard Arm Architecture calls.
#[derive(Copy, Clone, Debug, Eq, PartialEq)]
pub enum Error {
    /// The call is not supported by the implementation.
    NotSupported,
    /// The call is deemed not required by the implementation.
    NotRequired,
    /// One of the call parameters has a non-supported value.
    InvalidParameter,
    /// There was an unexpected return value.
    Unknown(i32),
}

impl From<Error> for i32 {
    fn from(error: Error) -> i32 {
        match error {
            Error::NotSupported => NOT_SUPPORTED,
            Error::NotRequired => NOT_REQUIRED,
            Error::InvalidParameter => INVALID_PARAMETER,
            Error::Unknown(value) => value,
        }
    }
}

impl From<i32> for Error {
    fn from(value: i32) -> Self {
        match value {
            NOT_SUPPORTED => Error::NotSupported,
            NOT_REQUIRED => Error::NotRequired,
            INVALID_PARAMETER => Error::InvalidParameter,
            _ => Error::Unknown(value),
        }
    }
}

impl Display for Error {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        match self {
            Self::NotSupported => write!(f, "SMCCC call not supported"),
            Self::NotRequired => write!(f, "SMCCC call not required"),
            Self::InvalidParameter => write!(f, "SMCCC call received non-supported value"),
            Self::Unknown(e) => write!(f, "Unknown SMCCC return value {} ({0:#x})", e),
        }
    }
}
