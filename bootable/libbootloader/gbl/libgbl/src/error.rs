// Copyright 2023, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Error types used in libgbl.

use avb::{DescriptorError, SlotVerifyError};
use core::ffi::{FromBytesUntilNulError, FromBytesWithNulError};
use core::fmt::{Debug, Display, Formatter};

/// Helper type GBL functions will return.
pub type Result<T> = core::result::Result<T, Error>;

#[derive(Debug, PartialEq)]
/// Error values that can be returned by function in this library
pub enum Error {
    /// Generic error
    Error,
    /// Missing all images required to boot system
    MissingImage,
    /// Functionality is not implemented
    NotImplemented,
    /// Some combination of parameters and global state prohibits the operation
    OperationProhibited,
    /// Internal error
    Internal,
    /// AvbOps were already borrowed. This would happen on second `load_and_verify_image()` call
    /// unless `reuse()` is called before.
    AvbOpsBusy,
    /// Failed to get descriptor from AvbMeta
    AvbDescriptorError(DescriptorError),
    /// Avb slot verification failed.
    /// SlotVerifyError is used without verify data.
    AvbSlotVerifyError(SlotVerifyError<'static>),
}

// Unfortunately thiserror is not available in `no_std` world.
// Thus `Display` implementation is required.
impl Display for Error {
    fn fmt(&self, f: &mut Formatter<'_>) -> core::fmt::Result {
        match self {
            Error::Error => write!(f, "Generic error"),
            Error::MissingImage => write!(f, "Missing image required to boot system"),
            Error::NotImplemented => write!(f, "Functionality is not implemented"),
            Error::OperationProhibited => write!(f, "Operation is prohibited"),
            Error::Internal => write!(f, "Internal error"),
            Error::AvbOpsBusy => write!(f, "AvbOps were already borrowed"),
            Error::AvbDescriptorError(error) => {
                write!(f, "Failed to get descriptor from AvbMeta: {:?}", error)
            }
            Error::AvbSlotVerifyError(error) => {
                write!(f, "Avb slot verification failed: {}", error)
            }
        }
    }
}

impl From<DescriptorError> for Error {
    fn from(value: DescriptorError) -> Self {
        Error::AvbDescriptorError(value)
    }
}

impl<'a> From<SlotVerifyError<'a>> for Error {
    fn from(value: SlotVerifyError<'a>) -> Self {
        Error::AvbSlotVerifyError(value.without_verify_data())
    }
}

impl From<FromBytesUntilNulError> for Error {
    fn from(e: FromBytesUntilNulError) -> Self {
        Error::Internal
    }
}

impl From<FromBytesWithNulError> for Error {
    fn from(e: FromBytesWithNulError) -> Self {
        Error::Internal
    }
}

#[cfg(test)]
mod tests {
    use crate::*;
    use avb::{DescriptorError, SlotVerifyError};

    #[test]
    fn test_error_output_formats() {
        assert_eq!("Generic error", format!("{}", Error::Error));
        assert_eq!(
            format!("Avb slot verification failed: {}", SlotVerifyError::Io),
            format!("{}", Error::AvbSlotVerifyError(SlotVerifyError::Io))
        );
        assert_eq!(
            format!("Failed to get descriptor from AvbMeta: {:?}", DescriptorError::InvalidValue),
            format!("{}", Error::AvbDescriptorError(DescriptorError::InvalidValue))
        );
    }
}
