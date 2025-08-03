//! Defines the context type for a session handling hwtrust data structures.

use crate::dice::ProfileVersion;
use std::ops::RangeInclusive;

/// The context for a session handling hwtrust data structures.
#[derive(Default, Debug)]
pub struct Session {
    /// Options that control the behaviour during this session.
    pub options: Options,
}

/// Options that control the behaviour of a session.
#[derive(Default, Debug)]
pub struct Options {
    /// The range of supported Android Profile for DICE versions.
    pub dice_profile_range: DiceProfileRange,
}

/// An inclusive range of Android Profile for DICE versions.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DiceProfileRange(RangeInclusive<ProfileVersion>);

impl DiceProfileRange {
    /// Creates a new inclusive range of Android Profile for DICE versions.
    pub fn new(start: ProfileVersion, end: ProfileVersion) -> Self {
        Self(RangeInclusive::new(start, end))
    }

    /// Returns `true` if `version` is contained in the range.
    pub fn contains(&self, version: ProfileVersion) -> bool {
        self.0.contains(&version)
    }

    /// Returns the lower bound of the range.
    pub fn start(&self) -> ProfileVersion {
        *self.0.start()
    }

    /// Returns the upper bound of the range.
    pub fn end(&self) -> ProfileVersion {
        *self.0.end()
    }
}

impl Default for DiceProfileRange {
    fn default() -> Self {
        Self::new(ProfileVersion::Android14, ProfileVersion::Android16)
    }
}

impl Options {
    /// The options use by VSR 13.
    pub fn vsr13() -> Self {
        Self {
            dice_profile_range: DiceProfileRange::new(
                ProfileVersion::Android13,
                ProfileVersion::Android13,
            ),
        }
    }

    /// The options use by VSR 14.
    pub fn vsr14() -> Self {
        Self {
            dice_profile_range: DiceProfileRange::new(
                ProfileVersion::Android14,
                ProfileVersion::Android14,
            ),
        }
    }

    /// The options use by VSR 15.
    pub fn vsr15() -> Self {
        Self {
            dice_profile_range: DiceProfileRange::new(
                ProfileVersion::Android14,
                ProfileVersion::Android15,
            ),
        }
    }

    /// The options use by VSR 16.
    pub fn vsr16() -> Self {
        Self {
            dice_profile_range: DiceProfileRange::new(
                ProfileVersion::Android14,
                ProfileVersion::Android16,
            ),
        }
    }
}
