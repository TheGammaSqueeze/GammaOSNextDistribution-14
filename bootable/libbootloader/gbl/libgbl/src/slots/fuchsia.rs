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

extern crate bitflags;
extern crate crc32fast;
extern crate zerocopy;

use super::{
    BootTarget, BootToken, Bootability, Error, Manager, OneShot, RecoveryTarget, Slot,
    SlotIterator, Suffix, UnbootableReason,
};
use bitflags::bitflags;
use core::mem::size_of;
use crc32fast::Hasher;
use zerocopy::byteorder::big_endian::U32 as BigEndianU32;
use zerocopy::{AsBytes, ByteSlice, FromBytes, FromZeroes, Ref};

/// Custom error type
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum AbrSlotError {
    /// The magic number field was corrupted
    BadMagic,
    /// The major version of the structure is unsupported
    BadVersion,
    /// The struct crc check failed
    BadCrc,
    /// The deserialization buffer is too small
    BufferTooSmall,
}

#[derive(Debug, PartialEq, Eq)]
enum CacheStatus {
    Clean,
    Dirty,
}

const DEFAULT_PRIORITY: u8 = 15;
const DEFAULT_RETRIES: u8 = 7;

#[repr(C, packed)]
#[derive(Copy, Clone, Debug, PartialEq, Eq, AsBytes, FromBytes, FromZeroes)]
struct AbrSlotData {
    priority: u8,
    tries: u8,
    successful: u8,
    unbootable_reason: u8,
}

impl Default for AbrSlotData {
    fn default() -> Self {
        Self {
            priority: DEFAULT_PRIORITY,
            tries: DEFAULT_RETRIES,
            successful: 0,
            unbootable_reason: 0,
        }
    }
}

#[repr(C, packed)]
#[derive(Copy, Clone, Debug, PartialEq, Eq, AsBytes, FromBytes, FromZeroes)]
struct OneShotFlags(u8);

bitflags! {
    impl OneShotFlags: u8 {
        /// No oneshot specified
        const NONE = 0;
        /// Oneshot boot to recovery mode
        const RECOVERY = 1 << 0;
        /// Oneshot boot to fastboot
        const BOOTLOADER = 1 << 1;
    }
}

impl From<OneShotFlags> for Option<OneShot> {
    fn from(flags: OneShotFlags) -> Self {
        match flags {
            OneShotFlags::RECOVERY => Some(OneShot::Continue(RecoveryTarget::Dedicated)),
            OneShotFlags::BOOTLOADER => Some(OneShot::Bootloader),
            _ => None,
        }
    }
}

impl From<Option<OneShot>> for OneShotFlags {
    fn from(oneshot: Option<OneShot>) -> Self {
        if let Some(target) = oneshot {
            match target {
                OneShot::Bootloader => Self::BOOTLOADER,
                OneShot::Continue(RecoveryTarget::Dedicated) => Self::RECOVERY,
                _ => Self::NONE,
            }
        } else {
            Self::NONE
        }
    }
}

const ABR_MAGIC: &[u8; 4] = b"\0AB0";
const ABR_VERSION_MAJOR: u8 = 2;
const ABR_VERSION_MINOR: u8 = 3;

#[repr(C, packed)]
#[derive(Copy, Clone, Debug, PartialEq, Eq, AsBytes, FromBytes, FromZeroes)]
struct AbrData {
    magic: [u8; 4],
    version_major: u8,
    version_minor: u8,
    reserved: [u8; 2],
    slot_data: [AbrSlotData; 2],
    oneshot_flag: OneShotFlags,
    reserved2: [u8; 11],
    crc32: BigEndianU32,
}

impl AbrData {
    fn calculate_crc32(&self) -> u32 {
        let mut hasher = Hasher::new();
        // Note: core::offset_of isn't stable yet,
        // and size_of_val isn't permitted on unaligned structs.
        hasher.update(&self.as_bytes()[..(size_of::<Self>() - size_of::<BigEndianU32>())]);
        hasher.finalize()
    }
}

impl Default for AbrData {
    fn default() -> Self {
        let mut data = Self {
            magic: *ABR_MAGIC,
            version_major: ABR_VERSION_MAJOR,
            version_minor: ABR_VERSION_MINOR,
            reserved: Default::default(),
            slot_data: Default::default(),
            oneshot_flag: OneShotFlags::NONE,
            reserved2: Default::default(),
            crc32: BigEndianU32::ZERO,
        };
        data.crc32.set(data.calculate_crc32());
        data
    }
}

#[derive(Debug, PartialEq, Eq)]
/// Default implementation for Manager.
/// Represents a partition-backed slot control block with two slots, A and B,
/// a special recovery partition, R, and support for oneshot boot.
/// Includes an Option<BootToken> to support `mark_boot_attempt`
/// and a cache status to support lazy write-back on destruction.
pub struct SlotBlock {
    cache_status: CacheStatus,
    boot_token: Option<BootToken>,
    abr_data: AbrData,
}

impl SlotBlock {
    fn get_mut_data(&mut self) -> &mut AbrData {
        self.cache_status = CacheStatus::Dirty;
        &mut self.abr_data
    }

    fn get_data(&self) -> &AbrData {
        &self.abr_data
    }

    /// Attempt to deserialize a slot control block
    ///
    /// # Returns
    /// * `Ok(SlotBlock)` - on success returns a SlotBlock that wraps a copy of the serialized data
    /// * `Err(AbrSlotError)` - on failure
    pub fn deserialize<B: ByteSlice>(
        buffer: B,
        boot_token: BootToken,
    ) -> Result<Self, AbrSlotError> {
        let abr_data =
            Ref::<B, AbrData>::new_from_prefix(buffer).ok_or(AbrSlotError::BufferTooSmall)?.0;

        if abr_data.magic != *ABR_MAGIC {
            return Err(AbrSlotError::BadMagic);
        }
        if abr_data.version_major > ABR_VERSION_MAJOR {
            return Err(AbrSlotError::BadVersion);
        }

        if abr_data.crc32.get() != abr_data.calculate_crc32() {
            return Err(AbrSlotError::BadCrc);
        }

        Ok(SlotBlock {
            cache_status: CacheStatus::Clean,
            boot_token: Some(boot_token),
            abr_data: *abr_data,
        })
    }

    pub(crate) fn new(boot_token: BootToken) -> Self {
        Self { boot_token: Some(boot_token), ..Default::default() }
    }
}

impl Default for SlotBlock {
    /// Returns a default valued SlotBlock.
    /// Only used in tests because BootToken cannot be constructed out of crate.
    fn default() -> Self {
        Self {
            cache_status: CacheStatus::Clean,
            boot_token: Some(BootToken(())),
            abr_data: Default::default(),
        }
    }
}

impl super::private::SlotGet for SlotBlock {
    fn get_slot_by_number(&self, number: usize) -> Result<Slot, Error> {
        let lower_ascii = 'a'..='z';
        let (suffix, &abr_slot) = core::iter::zip(lower_ascii, self.get_data().slot_data.iter())
            .nth(number)
            .map(|(c, s)| (Suffix(c), s))
            .ok_or_else(|| Suffix::try_from(number).map_or(Error::Other, Error::NoSuchSlot))?;

        let bootability = match (abr_slot.successful, abr_slot.tries) {
            (s, _) if s != 0 => Bootability::Successful,
            (0, t) if t > 0 => Bootability::Retriable(t.into()),
            (_, _) => Bootability::Unbootable(abr_slot.unbootable_reason.into()),
        };

        Ok(Slot { suffix, priority: abr_slot.priority.into(), bootability })
    }
}

// We want lexigraphically lower suffixes
// to have higher priority.
// A cheater way to do this is to compare
// their negative values.
// A char is 4 bytes, and a signed 64 bit int
// can comfortably contain the negative of a
// number represented by an unsigned 32 bit int.
fn suffix_rank(s: Suffix) -> i64 {
    -i64::from(u32::from(s.0))
}

impl Manager for SlotBlock {
    fn get_boot_target(&self) -> BootTarget {
        self.slots_iter()
            .filter(Slot::is_bootable)
            .max_by_key(|slot| (slot.priority, suffix_rank(slot.suffix)))
            .map_or(BootTarget::Recovery(RecoveryTarget::Dedicated), BootTarget::NormalBoot)
    }

    fn get_slot_last_set_active(&self) -> Slot {
        // We can safely assume that we have at least one slot.
        self.slots_iter().max_by_key(|slot| (slot.priority, suffix_rank(slot.suffix))).unwrap()
    }

    fn slots_iter(&self) -> SlotIterator {
        SlotIterator::new(self)
    }

    fn set_slot_unbootable(
        &mut self,
        slot_suffix: Suffix,
        reason: UnbootableReason,
    ) -> Result<(), Error> {
        let (idx, slot) = self.get_index_and_slot_with_suffix(slot_suffix)?;
        if slot.bootability == Bootability::Unbootable(reason) {
            return Ok(());
        }

        let abr_data = self.get_mut_data();
        let slot_data = &mut abr_data.slot_data[idx];
        slot_data.unbootable_reason = reason.into();
        slot_data.tries = 0;
        slot_data.successful = 0;

        Ok(())
    }

    fn mark_boot_attempt(&mut self, boot_target: BootTarget) -> Result<BootToken, Error> {
        let target_slot = match boot_target {
            BootTarget::NormalBoot(slot) => slot,
            BootTarget::Recovery(RecoveryTarget::Slotted(_)) => Err(Error::OperationProhibited)?,
            BootTarget::Recovery(RecoveryTarget::Dedicated) => {
                // Even though boot to recovery does not cause a metadata update,
                // we still need to gate access to the boot token.
                return self.boot_token.take().ok_or(Error::OperationProhibited);
            }
        };

        let (idx, slot) = self.get_index_and_slot_with_suffix(target_slot.suffix)?;

        match slot.bootability {
            Bootability::Unbootable(_) => Err(Error::OperationProhibited),
            Bootability::Retriable(_) => {
                let abr_slot = &mut self.get_mut_data().slot_data[idx];
                abr_slot.tries -= 1;
                if abr_slot.tries == 0 {
                    abr_slot.unbootable_reason = UnbootableReason::NoMoreTries.into();
                }
                let token = self.boot_token.take().ok_or(Error::OperationProhibited)?;
                Ok(token)
            }
            Bootability::Successful => {
                let token = self.boot_token.take().ok_or(Error::OperationProhibited)?;
                Ok(token)
            }
        }
    }

    fn set_active_slot(&mut self, slot_suffix: Suffix) -> Result<(), Error> {
        let (idx, _) = self.get_index_and_slot_with_suffix(slot_suffix)?;

        let abr_data = self.get_mut_data();
        for (i, slot) in abr_data.slot_data.iter_mut().enumerate() {
            if i == idx {
                *slot = Default::default();
            } else {
                slot.priority = DEFAULT_PRIORITY - 1;
            }
        }
        Ok(())
    }

    fn get_oneshot_status(&self) -> Option<OneShot> {
        self.get_data().oneshot_flag.into()
    }

    fn set_oneshot_status(&mut self, oneshot: OneShot) -> Result<(), Error> {
        if Some(oneshot) == self.get_oneshot_status() {
            return Ok(());
        }

        let oneshot_flag = OneShotFlags::from(Some(oneshot));
        if oneshot_flag == OneShotFlags::NONE {
            Err(match oneshot {
                OneShot::Continue(RecoveryTarget::Slotted(_)) => Error::OperationProhibited,
                _ => Error::Other,
            })
        } else {
            self.get_mut_data().oneshot_flag = oneshot_flag;
            Ok(())
        }
    }

    fn clear_oneshot_status(&mut self) {
        if self.get_oneshot_status().is_some() {
            self.get_mut_data().oneshot_flag = OneShotFlags::NONE;
        }
    }

    fn write_back(&mut self) {
        if self.cache_status == CacheStatus::Clean {
            return;
        }

        let data = self.get_mut_data();
        data.version_minor = ABR_VERSION_MINOR;
        data.crc32 = data.calculate_crc32().into();

        // TODO(dovs): write data back to partition
    }
}

impl SlotBlock {
    fn get_index_and_slot_with_suffix(&self, slot_suffix: Suffix) -> Result<(usize, Slot), Error> {
        self.slots_iter()
            .enumerate()
            .find(|(_, s)| s.suffix == slot_suffix)
            .ok_or(Error::NoSuchSlot(slot_suffix))
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn test_slot_block_defaults() {
        let sb: SlotBlock = Default::default();
        let expected: Vec<Slot> = vec![
            Slot {
                suffix: 'a'.into(),
                priority: DEFAULT_PRIORITY.into(),
                bootability: Bootability::Retriable(sb.get_max_retries()),
            },
            Slot {
                suffix: 'b'.into(),
                priority: DEFAULT_PRIORITY.into(),
                bootability: Bootability::Retriable(sb.get_max_retries()),
            },
        ];
        let actual: Vec<Slot> = sb.slots_iter().collect();
        assert_eq!(actual, expected);
        assert_eq!(sb.get_oneshot_status(), None);
    }

    #[test]
    fn test_slot_block_new() {
        let new = SlotBlock::new(BootToken(()));
        let default: SlotBlock = Default::default();
        assert_eq!(new, default);
    }

    #[test]
    fn test_suffix() {
        let slot = Slot { suffix: 'a'.into(), ..Default::default() };
        assert_eq!(BootTarget::Recovery(RecoveryTarget::Dedicated).suffix(), 'r'.into());
        assert_eq!(BootTarget::Recovery(RecoveryTarget::Slotted(slot)).suffix(), slot.suffix);
        assert_eq!(BootTarget::NormalBoot(slot).suffix(), slot.suffix);
    }

    #[test]
    fn test_slot_block_parse() {
        let sb: SlotBlock = Default::default();
        assert_eq!(SlotBlock::deserialize(sb.get_data().as_bytes(), BootToken(())), Ok(sb));
    }

    #[test]
    fn test_slot_block_parse_buffer_too_small() {
        let buffer: [u8; 0] = Default::default();
        assert_eq!(
            SlotBlock::deserialize::<&[u8]>(&buffer, BootToken(())),
            Err(AbrSlotError::BufferTooSmall),
        );
    }

    #[test]
    fn test_slot_block_parse_bad_magic() {
        let mut sb: SlotBlock = Default::default();
        sb.get_mut_data().magic[0] += 1;
        assert_eq!(
            SlotBlock::deserialize(sb.get_data().as_bytes(), BootToken(())),
            Err(AbrSlotError::BadMagic)
        );
    }

    #[test]
    fn test_slot_block_parse_bad_version_major() {
        let mut sb: SlotBlock = Default::default();
        sb.get_mut_data().version_major = 15;
        assert_eq!(
            SlotBlock::deserialize(sb.get_data().as_bytes(), BootToken(())),
            Err(AbrSlotError::BadVersion)
        );
    }

    #[test]
    fn test_slot_block_parse_bad_crc() {
        let mut sb: SlotBlock = Default::default();
        let bad_crc = sb.get_data().crc32.get() ^ BigEndianU32::MAX_VALUE.get();
        sb.get_mut_data().crc32 = bad_crc.into();
        assert_eq!(
            SlotBlock::deserialize(sb.get_data().as_bytes(), BootToken(())),
            Err(AbrSlotError::BadCrc)
        );
    }

    #[test]
    fn test_slot_mark_boot_attempt() {
        let mut sb: SlotBlock = Default::default();
        let slot = Slot { suffix: 'a'.into(), ..Default::default() };
        assert_eq!(sb.mark_boot_attempt(BootTarget::NormalBoot(slot)), Ok(BootToken(())));
        assert_eq!(
            sb.slots_iter().next().unwrap(),
            Slot {
                suffix: slot.suffix,
                priority: DEFAULT_PRIORITY.into(),
                bootability: Bootability::Retriable((DEFAULT_RETRIES - 1).into())
            }
        );

        // Make sure we can call exactly once
        assert_eq!(
            sb.mark_boot_attempt(BootTarget::NormalBoot(slot)),
            Err(Error::OperationProhibited)
        );
    }

    #[test]
    fn test_slot_mark_boot_attempt_no_more_tries() {
        let mut sb: SlotBlock = Default::default();
        sb.get_mut_data().slot_data[0].tries = 1;
        let slot = Slot { suffix: 'a'.into(), ..Default::default() };
        assert_eq!(sb.mark_boot_attempt(BootTarget::NormalBoot(slot)), Ok(BootToken(())));
        assert_eq!(
            sb.slots_iter().next().unwrap(),
            Slot {
                suffix: slot.suffix,
                priority: DEFAULT_PRIORITY.into(),
                bootability: Bootability::Unbootable(UnbootableReason::NoMoreTries)
            }
        );
    }

    #[test]
    fn test_slot_mark_boot_attempt_successful() {
        let mut sb: SlotBlock = Default::default();
        sb.get_mut_data().slot_data[0].successful = 1;
        let target = BootTarget::NormalBoot(Slot {
            suffix: 'a'.into(),
            priority: DEFAULT_PRIORITY.into(),
            bootability: Bootability::Successful,
        });
        assert_eq!(sb.mark_boot_attempt(target), Ok(BootToken(())));
        assert_eq!(sb.get_boot_target(), target);
    }

    #[test]
    fn test_slot_mark_tried_no_such_slot() {
        let mut sb: SlotBlock = Default::default();
        let slot = Slot { suffix: '$'.into(), ..Default::default() };
        assert_eq!(
            sb.mark_boot_attempt(BootTarget::NormalBoot(slot)),
            Err(Error::NoSuchSlot(slot.suffix))
        );
    }

    #[test]
    fn test_slot_mark_tried_recovery() {
        let mut sb: SlotBlock = Default::default();
        let recovery_tgt = BootTarget::Recovery(RecoveryTarget::Dedicated);
        assert_eq!(sb.mark_boot_attempt(recovery_tgt), Ok(BootToken(())));

        // Make sure a second attempt fails due to the moved boot token
        assert_eq!(sb.mark_boot_attempt(recovery_tgt), Err(Error::OperationProhibited));
    }

    #[test]
    fn test_mark_slot_tried_slotted_recovery() {
        let mut sb: SlotBlock = Default::default();
        let slot: Slot = Default::default();
        assert_eq!(
            sb.mark_boot_attempt(BootTarget::Recovery(RecoveryTarget::Slotted(slot))),
            Err(Error::OperationProhibited)
        );
    }

    #[test]
    fn test_slot_mark_tried_unbootable() {
        let mut sb: SlotBlock = Default::default();
        let slot = Slot { suffix: 'b'.into(), ..Default::default() };
        assert_eq!(sb.set_slot_unbootable(slot.suffix, UnbootableReason::UserRequested), Ok(()));
        assert_eq!(
            sb.mark_boot_attempt(BootTarget::NormalBoot(slot)),
            Err(Error::OperationProhibited)
        );
    }

    macro_rules! set_unbootable_tests {
                ($($name:ident: $value:expr,)*) => {
                    $(
                        #[test]
                        fn $name() {
                            let mut sb: SlotBlock = Default::default();
                            let suffix: Suffix = 'a'.into();
                            assert_eq!(sb.set_slot_unbootable(suffix, $value), Ok(()));
                            assert_eq!(sb.slots_iter()
                                       .find(|s| s.suffix == suffix)
                                       .unwrap()
                                       .bootability,
                                       Bootability::Unbootable($value)
                            );
                        }
                    )*
                }
            }

    use UnbootableReason::*;
    set_unbootable_tests! {
        test_set_unbootable_no_more_tries: NoMoreTries,
        test_set_unbootable_system_update: SystemUpdate,
        test_set_unbootable_user_requested: UserRequested,
        test_set_unbootable_verification_failure: VerificationFailure,
        test_set_unbootable_unknown: Unknown,
    }

    #[test]
    fn test_no_bootable_slots_boot_recovery() {
        let mut sb: SlotBlock = Default::default();
        let v: Vec<Slot> = sb.slots_iter().collect();
        for slot in v {
            assert_eq!(
                sb.set_slot_unbootable(slot.suffix, UnbootableReason::UserRequested),
                Ok(())
            );
        }
        assert_eq!(sb.get_boot_target(), BootTarget::Recovery(RecoveryTarget::Dedicated));
    }

    #[test]
    fn test_set_active_slot() {
        let mut sb: SlotBlock = Default::default();
        let v: Vec<Slot> = sb.slots_iter().collect();

        assert_eq!(sb.get_boot_target(), BootTarget::NormalBoot(v[0]));
        for slot in v.iter() {
            assert_eq!(sb.set_active_slot(slot.suffix), Ok(()));
            assert_eq!(sb.get_boot_target(), BootTarget::NormalBoot(*slot));
        }
    }

    #[test]
    fn test_set_active_slot_no_such_slot() {
        let mut sb: SlotBlock = Default::default();
        let bad_suffix: Suffix = '$'.into();
        assert_eq!(sb.set_active_slot(bad_suffix), Err(Error::NoSuchSlot(bad_suffix)));
    }

    #[test]
    fn test_get_slot_last_set_active() {
        let mut sb: SlotBlock = Default::default();
        let v: Vec<Slot> = sb.slots_iter().collect();
        assert_eq!(sb.set_active_slot(v[0].suffix), Ok(()));
        assert_eq!(sb.get_slot_last_set_active(), v[0]);
        for slot in v {
            assert_eq!(sb.set_slot_unbootable(slot.suffix, NoMoreTries), Ok(()));
        }

        assert_eq!(sb.get_slot_last_set_active(), sb.slots_iter().next().unwrap());
    }

    macro_rules! set_oneshot_tests {
                ($($name:ident: $value:expr,)*) => {
                    $(
                        #[test]
                        fn $name(){
                            let mut sb: SlotBlock = Default::default();
                            assert_eq!(sb.set_oneshot_status($value), Ok(()));
                            assert_eq!(sb.get_oneshot_status(), Some($value));

                            assert_eq!(sb.get_boot_target(),
                                       BootTarget::NormalBoot(
                                           Slot{
                                               suffix: 'a'.into(),
                                               priority: DEFAULT_PRIORITY.into(),
                                               bootability: Bootability::Retriable(sb.get_max_retries()),
                                           },
                                       ));
                        }
                    )*
                }
            }

    set_oneshot_tests! {
        test_set_oneshot_bootloader: OneShot::Bootloader,
        test_set_oneshot_recovery: OneShot::Continue(RecoveryTarget::Dedicated),
    }

    #[test]
    fn test_clear_oneshot_status() {
        let mut sb: SlotBlock = Default::default();
        assert_eq!(sb.set_oneshot_status(OneShot::Bootloader), Ok(()));
        sb.clear_oneshot_status();
        assert_eq!(sb.get_oneshot_status(), None);
    }

    #[test]
    fn test_set_oneshot_mistaken_recovery_slotted() {
        let mut sb: SlotBlock = Default::default();
        let slot = sb.slots_iter().next().unwrap();
        assert_eq!(
            sb.set_oneshot_status(OneShot::Continue(RecoveryTarget::Slotted(slot))),
            Err(Error::OperationProhibited)
        );
    }
}
