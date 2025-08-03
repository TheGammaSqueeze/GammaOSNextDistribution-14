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

/// Export the default implementation
pub mod fuchsia;

use core::mem::size_of;

/// A type safe container for describing the number of retries a slot has left
/// before it becomes unbootable.
/// Slot tries can only be compared to, assigned to, or assigned from other
/// tries.
#[derive(Copy, Clone, Debug, Default, PartialEq, Eq)]
pub struct Tries(usize);

impl From<usize> for Tries {
    fn from(u: usize) -> Self {
        Self(u)
    }
}
impl From<u8> for Tries {
    fn from(u: u8) -> Self {
        Self(u.into())
    }
}

/// A type safe container for describing the priority of a slot.
/// Slot priorities can only be compared to, assigned to, or assigned from
/// other priorities.
#[derive(Copy, Clone, Debug, Default, PartialEq, Eq, PartialOrd, Ord)]
pub struct Priority(usize);

impl From<usize> for Priority {
    fn from(u: usize) -> Self {
        Self(u)
    }
}
impl From<u8> for Priority {
    fn from(u: u8) -> Self {
        Self(u.into())
    }
}

/// A type safe container for describing a slot's suffix.
#[derive(Copy, Clone, Debug, Default, PartialEq, Eq)]
pub struct Suffix(pub(crate) char);

impl From<char> for Suffix {
    fn from(c: char) -> Self {
        Self(c)
    }
}

impl TryFrom<usize> for Suffix {
    type Error = Error;

    fn try_from(value: usize) -> Result<Self, Self::Error> {
        u32::try_from(value).ok().and_then(char::from_u32).ok_or(Error::Other).map(Self)
    }
}

// Includes a null terminator
const SUFFIX_CSTR_MAX_BYTES: usize = size_of::<Suffix>() + 1;

/// A buffer large enough to contain the serialized representation of a Suffix.
/// Can be turned into a &Cstr like so:
///
/// let suffix: Suffix = 'a'.into();
/// let buffer: SuffixBytes = suffix.into();
/// let cstr = CStr::from_bytes_until_nul(&buffer)?;
pub type SuffixBytes = [u8; SUFFIX_CSTR_MAX_BYTES];

impl From<Suffix> for SuffixBytes {
    fn from(val: Suffix) -> Self {
        let mut buffer: Self = Default::default();
        let _ = val.0.encode_utf8(&mut buffer);
        buffer
    }
}

/// Slot metadata describing why that slot is unbootable.
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum UnbootableReason {
    /// No information is given about why this slot is not bootable.
    Unknown,
    /// This slot has exhausted its retry budget and cannot be booted.
    NoMoreTries,
    /// As part of a system update, the update agent downloads
    /// an updated image and stores it into a slot other than the current
    /// active slot.
    SystemUpdate,
    /// This slot has been marked unbootable by user request,
    /// usually as part of a system test.
    UserRequested,
    /// This slot has failed a verification check as part of
    /// Android Verified Boot.
    VerificationFailure,
}

impl Default for UnbootableReason {
    fn default() -> Self {
        Self::Unknown
    }
}

impl From<u8> for UnbootableReason {
    fn from(val: u8) -> Self {
        match val {
            1 => Self::NoMoreTries,
            2 => Self::SystemUpdate,
            3 => Self::UserRequested,
            4 => Self::VerificationFailure,
            _ => Self::Unknown,
        }
    }
}

impl From<UnbootableReason> for u8 {
    fn from(reason: UnbootableReason) -> Self {
        match reason {
            UnbootableReason::Unknown => 0,
            UnbootableReason::NoMoreTries => 1,
            UnbootableReason::SystemUpdate => 2,
            UnbootableReason::UserRequested => 3,
            UnbootableReason::VerificationFailure => 4,
        }
    }
}

/// Describes whether a slot has successfully booted and, if not,
/// why it is not a valid boot target OR the number of attempts it has left.
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum Bootability {
    /// This slot has successfully booted.
    Successful,
    /// This slot cannot be booted.
    Unbootable(UnbootableReason),
    /// This slot has not successfully booted yet but has
    /// one or more attempts left before either successfully booting,
    /// and being marked successful, or failing, and being marked
    /// unbootable due to having no more tries.
    Retriable(Tries),
}

impl Default for Bootability {
    fn default() -> Self {
        Self::Retriable(7u8.into())
    }
}

/// User-visible representation of a boot slot.
/// Describes the slot's moniker (i.e. the suffix),
/// its priority,
/// and information about its bootability.
///
/// Note: structures that implement Manager will probably have a different
/// internal representation for slots and will convert and return Slot structures
/// on the fly as part of iteration.
#[derive(Copy, Clone, Debug, Default, PartialEq, Eq)]
pub struct Slot {
    /// The partition suffix for the slot.
    pub suffix: Suffix,
    /// The slot's priority for booting.
    pub priority: Priority,
    /// Information about a slot's boot eligibility and history.
    pub bootability: Bootability,
}

impl Slot {
    /// Returns whether a slot is a valid boot target,
    /// i.e. return true if its bootability is not Unbootable.
    pub fn is_bootable(&self) -> bool {
        !matches!(self.bootability, Bootability::Unbootable(_))
    }
}

/// Describes the platform recovery mode boot target.
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum RecoveryTarget {
    /// The platform uses a dedicated recovery slot with special semantics.
    /// It can't be marked unbootable, has unlimited retries,
    /// and often doesn't have an explicit metadata entry.
    Dedicated,
    /// The platform enters recovery mode by booting to a regular slot
    /// but with a special commandline and ramdisk.
    Slotted(Slot),
}

/// Describes a system's boot target, which can be a regular boot to a slot
/// or a recovery boot.
/// Whether the recovery boot target is a dedicated slot or a regular slot
/// with a special command line is platform specific.
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum BootTarget {
    /// The system will attempt a normal boot to the given slot.
    NormalBoot(Slot),
    /// The system will attempt a recovery boot.
    ///
    /// Some platforms, such as Fuchsia, have dedicated recovery partitions with
    /// special semantics. On these platforms, Recovery contains None.
    ///
    /// Other platforms, such as Android, do not have dedicated recovery partitions.
    /// They enter recovery mode by attempting to boot a regular slot with a special
    /// kernel command line and ramdisk.
    /// Under these circomstances, Recovery contains the slot that will be used for recovery.
    Recovery(RecoveryTarget),
}

impl BootTarget {
    /// Gets the suffix for a particular boot target.
    /// Implemented for BootTarget instead of slot in order to handle
    /// Fuchsia's recovery partition.
    pub fn suffix(&self) -> Suffix {
        match self {
            Self::NormalBoot(slot) | Self::Recovery(RecoveryTarget::Slotted(slot)) => slot.suffix,
            Self::Recovery(RecoveryTarget::Dedicated) => 'r'.into(),
        }
    }
}

#[doc(hidden)]
pub mod private {
    use super::*;

    #[doc(hidden)]
    pub trait SlotGet {
        /// Given an index, returns the Slot that corresponds to that index,
        /// or Error if the index is out of bounds.
        /// This is intended to abstract storage details for structs that impl Manager.
        /// Most implementors will use some other, internal representation for slots,
        /// and will dynamically create and return Slots on the fly.
        ///
        /// This method is a helper, implementation detail for SlotIterator.
        /// It is not intended to be called by other parts of GBL or other users.
        fn get_slot_by_number(&self, number: usize) -> Result<Slot, Error>;
    }
}

/// Custom error type.
#[derive(Debug, PartialEq, Eq)]
pub enum Error {
    /// An API method has attempted an operation on a slot that does not exist.
    NoSuchSlot(Suffix),
    /// The backend policy has denied permission for the given operation.
    OperationProhibited,
    /// Unspecified error.
    Other,
}

/// A helper structure for iterating over slots.
pub struct SlotIterator<'a> {
    count: usize,
    slot_getter: &'a dyn private::SlotGet,
}

impl<'a> SlotIterator<'a> {
    /// Constructor for SlotIterator
    pub fn new(intf: &'a dyn private::SlotGet) -> Self {
        Self { count: 0, slot_getter: intf }
    }
}

impl<'a> Iterator for SlotIterator<'a> {
    type Item = Slot;

    fn next(&mut self) -> Option<Self::Item> {
        let maybe_slot = self.slot_getter.get_slot_by_number(self.count).ok();
        if maybe_slot.is_some() {
            self.count += 1;
        }
        maybe_slot
    }
}

/// Describe a oneshot boot target.
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum OneShot {
    /// The bootloader will stop in some kind of interactive mode.
    /// This can be Fastboot, a TUI boot menu, or something similar.
    Bootloader,
    /// The system will continue to the specified recovery target.
    Continue(RecoveryTarget),
}

/// Opaque boot token generated by `mark_boot_attempt` and consumed by `kernel_jump`.
/// Used to mandate that `mark_boot_attempt` is called **exactly** once continuing boot.
///
/// Custom structs that implement Manager should take a BootToken as an injected parameter
/// on construction and return it on the first successful call to mark_boot_attempt.
#[derive(Debug, PartialEq, Eq)]
pub struct BootToken(pub(crate) ());

/// The boot slot manager trait.
/// Responsible for setting boot slot policy and abstracting over on-disk/in-memory
/// representation of slot metadata.
pub trait Manager: private::SlotGet {
    /// Returns an iterator over all regular slots on the system.
    fn slots_iter(&self) -> SlotIterator;

    /// Returns the current active slot,
    /// or Recovery if the system will try to boot to recovery.
    fn get_boot_target(&self) -> BootTarget;

    /// Returns the slot last set active.
    /// Note that this is different from get_boot_target in that
    /// the slot last set active cannot be Recovery.
    fn get_slot_last_set_active(&self) -> Slot;

    /// Given a boot target, updates internal metadata (usually the retry count)
    /// indicating that the system will have tried to boot the slot.
    /// Returns Ok(BootToken) on success to verify that boot attempt metadata has been updated.
    /// The token must be consumed by `kernel_jump`.
    ///
    /// Can return Err if the designated slot does not exist,
    /// if it is ineligible to try,
    /// or for other, backend reasons.
    ///
    /// Note: mark_boot_attempt is NOT idempotent.
    /// It is intended to be called EXACTLY once,
    /// right before jumping into the kernel.
    ///
    /// Note: mark_boot_attempt takes a BootTarget to facilitate generating
    /// the boot token when booting to recovery. If the boot target is recovery,
    /// then implementations SHOULD NOT update internal metadata.
    fn mark_boot_attempt(&mut self, boot_target: BootTarget) -> Result<BootToken, Error>;

    /// Attempts to set the active slot.
    ///
    /// Can return Err if the designated slot does not exist,
    /// if the bootloader does not have permission to set slots active,
    /// or for other, backend policy reasons.
    fn set_active_slot(&mut self, slot_suffix: Suffix) -> Result<(), Error>;

    /// Attempts to mark a slot as unbootable.
    fn set_slot_unbootable(
        &mut self,
        slot_suffix: Suffix,
        reason: UnbootableReason,
    ) -> Result<(), Error>;

    /// Default for initial tries
    fn get_max_retries(&self) -> Tries {
        7u8.into()
    }

    /// Optional oneshot boot support

    /// Gets the current oneshot boot status,
    /// or None if the system will try to boot normally.
    ///
    /// Oneshots are a special feature for temporarily bypassing
    /// normal boot flow logic.
    /// This can be used as part of device flashing, for tests, or interactive development.
    fn get_oneshot_status(&self) -> Option<OneShot> {
        None
    }

    /// Attempts to set the oneshot boot status.
    ///
    /// Returns Err if the system does not support oneshot boot,
    /// if the designated slot does not exist,
    /// or for other, backend reasons.
    fn set_oneshot_status(&mut self, _: OneShot) -> Result<(), Error> {
        Err(Error::OperationProhibited)
    }

    /// Clears the oneshot status.
    fn clear_oneshot_status(&mut self);

    /// If the slot manager caches changes before writing to a backing store,
    /// writes back and sets the cache status to clean.
    /// The implementation is responsible for handling any errors,
    /// e.g. ignoring, logging, or aborting.
    ///
    /// This is useful for partition based slot setups,
    /// where we do not write back every interaction in order to coalesce writes
    /// and preserve disk lifetime.
    fn write_back(&mut self) {}
}

/// RAII helper object for coalescing changes.
pub struct Cursor<'a> {
    /// The backing manager for slot metadata.
    pub ctx: &'a mut dyn Manager,
}

impl<'a> Drop for Cursor<'a> {
    fn drop(&mut self) {
        self.ctx.write_back();
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use core::ffi::CStr;

    #[test]
    fn test_suffix_to_cstr() {
        let normal: Suffix = 'a'.into();
        let normal_buffer: SuffixBytes = normal.into();
        let normal_cstr = CStr::from_bytes_until_nul(&normal_buffer);
        assert!(normal_cstr.is_ok());

        // All UTF-8 characters are at most 4 bytes.
        // The in-memory representation as a chr or Suffix
        // uses all 4 bytes regardless of the length of the serialized
        // representation, but we need to make sure that buffer for
        // the serialized suffix can handle that too.
        // All emoji are 4 bytes when encoded as UTF-8,
        // so they're a reasonable test.
        let squid: Suffix = '🦑'.into();
        let squid_buffer: SuffixBytes = squid.into();
        let squid_cstr = CStr::from_bytes_until_nul(&squid_buffer);
        assert!(squid_cstr.is_ok());
    }
}
