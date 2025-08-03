// Copyright 2023 the authors.
// This project is dual-licensed under Apache 2.0 and MIT terms.
// See LICENSE-APACHE and LICENSE-MIT for details.

//! Standard Arm architecture calls.

mod calls;
pub mod error;

pub use calls::{
    arch_workaround_1, arch_workaround_2, arch_workaround_3, features, soc_id, version,
};
use core::fmt::{self, Debug, Display, Formatter};
pub use error::Error;

pub const SMCCC_VERSION: u32 = 0x8000_0000;
pub const SMCCC_ARCH_FEATURES: u32 = 0x8000_0001;
pub const SMCCC_ARCH_SOC_ID: u32 = 0x8000_0002;
pub const SMCCC_ARCH_WORKAROUND_1: u32 = 0x8000_8000;
pub const SMCCC_ARCH_WORKAROUND_2: u32 = 0x8000_7FFF;
pub const SMCCC_ARCH_WORKAROUND_3: u32 = 0x8000_3FFF;

/// A version of the SMC Calling Convention.
#[derive(Copy, Clone, Eq, Ord, PartialEq, PartialOrd)]
pub struct Version {
    pub major: u16,
    pub minor: u16,
}

impl Display for Version {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        write!(f, "{}.{}", self.major, self.minor)
    }
}

impl Debug for Version {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        Display::fmt(self, f)
    }
}

impl TryFrom<i32> for Version {
    type Error = Error;

    fn try_from(value: i32) -> Result<Self, Error> {
        if value < 0 {
            Err(value.into())
        } else {
            Ok(Self {
                major: (value >> 16) as u16,
                minor: value as u16,
            })
        }
    }
}

impl From<Version> for u32 {
    fn from(version: Version) -> Self {
        u32::from(version.major) << 16 | u32::from(version.minor)
    }
}

#[derive(Copy, Clone, Eq, Ord, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum SocIdType {
    /// The SoC version.
    Version,
    /// The SoC revision.
    Revision,
}

impl From<SocIdType> for u32 {
    fn from(id_type: SocIdType) -> Self {
        id_type as Self
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn convert_version() {
        let version = Version { major: 1, minor: 2 };
        assert_eq!(u32::from(version), 0x0001_0002);
        assert_eq!(0x0001_0002.try_into(), Ok(version));
    }
}
