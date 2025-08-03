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

//! # Generic Boot Loader (gbl) Library
//!
//! TODO: b/312610098 - add documentation.
//!
//! The intended users of this library are firmware, bootloader, and bring-up teams at OEMs and SOC
//! Vendors
//!
//! # Features
//! * `sw_digest` - enables software implementation of digests: [SwDigest], [SwContext]
//! * `alloc` - enables AVB ops related logic that relies on allocation and depends on allocation.

// This code is intended for use in bootloaders that typically will not support
// the Rust standard library
#![cfg_attr(not(any(test, android_dylib)), no_std)]
// TODO: b/312610985 - return warning for unused partitions
#![allow(unused_variables, dead_code)]
// TODO: b/312608163 - Adding ZBI library usage to check dependencies
extern crate avb;
extern crate core;
extern crate cstr;
extern crate spin;
extern crate zbi;

use avb::{HashtreeErrorMode, SlotVerifyData, SlotVerifyError, SlotVerifyFlags, SlotVerifyResult};
use core::ffi::CStr;
use core::fmt::Debug;
use cstr::cstr;
use spin::Mutex;

pub mod boot_mode;
pub mod boot_reason;
pub mod digest;
pub mod error;
pub mod ops;

/// The 'slots' module, containing types and traits for
/// querying and modifying slotted boot behavior.
pub mod slots;

use slots::{BootTarget, BootToken, Cursor, OneShot, SuffixBytes, UnbootableReason};

#[cfg(feature = "sw_digest")]
pub mod sw_digest;

pub use avb::Descriptor;
pub use boot_mode::BootMode;
pub use boot_reason::KnownBootReason;
pub use digest::{Context, Digest};
pub use error::{Error, Result};
pub use ops::{DefaultGblOps, GblOps};
#[cfg(feature = "sw_digest")]
pub use sw_digest::{SwContext, SwDigest};

// TODO: b/312607649 - Replace placeholders with actual structures: https://r.android.com/2721974, etc
/// TODO: b/312607649 - placeholder type
pub struct Partition {}
/// TODO: b/312607649 - placeholder type
pub struct InfoStruct {}
/// TODO: b/312607649 - placeholder type
pub struct AvbVerificationFlags(u32); // AvbVBMetaImageFlags from
                                      // external/avb/libavb/avb_vbmeta_image.h
/// Data structure holding verified slot data.
#[derive(Debug)]
pub struct VerifiedData<'a>(SlotVerifyData<'a>);

/// Structure representing partition and optional address it is required to be loaded.
/// If no address is provided GBL will use default one.
pub struct PartitionRamMap<'b, 'c> {
    /// Partition details
    pub partition: &'b Partition,

    /// Optional memory region to load partitions.
    /// If it's not provided default values will be used.
    pub address: Option<&'c mut [u8]>,

    loaded: bool,
    verified: bool,
}

/// Boot Image in memory
pub struct BootImage<'a>(&'a mut [u8]);

/// Vendor Boot Image in memory
pub struct VendorBootImage<'a>(&'a mut [u8]);

/// Init Boot Image in memory
pub struct InitBootImage<'a>(&'a mut [u8]);

/// Kernel Image in memory
pub struct KernelImage<'a>(&'a mut [u8]);

/// Ramdisk in memory
pub struct Ramdisk<'a>(&'a mut [u8]);
/// Bootconfig in memory
pub struct Bootconfig<'a>(&'a mut [u8]);
/// DTB in memory
pub struct Dtb<'a>(&'a mut [u8]);

/// Create Boot Image from corresponding partition for `partitions_ram_map` and `avb_descriptors`
/// lists
pub fn get_boot_image<'a: 'b, 'b: 'c, 'c, 'd>(
    verified_data: &mut VerifiedData<'d>,
    partitions_ram_map: &'a mut [PartitionRamMap<'b, 'c>],
) -> (Option<BootImage<'c>>, &'a mut [PartitionRamMap<'b, 'c>]) {
    match partitions_ram_map.len() {
        0 => (None, partitions_ram_map),
        _ => {
            let (partition_map, tail) = partitions_ram_map.split_first_mut().unwrap();
            (partition_map.address.take().map(BootImage), tail)
        }
    }
}

/// Create Vendor Boot Image from corresponding partition for `partitions_ram_map` and
/// `avb_descriptors` lists
pub fn get_vendor_boot_image<'a: 'b, 'b: 'c, 'c, 'd>(
    verified_data: &mut VerifiedData<'d>,
    partitions_ram_map: &'a mut [PartitionRamMap<'b, 'c>],
) -> (Option<VendorBootImage<'c>>, &'a mut [PartitionRamMap<'b, 'c>]) {
    match partitions_ram_map.len() {
        0 => (None, partitions_ram_map),
        _ => {
            let (partition_map, tail) = partitions_ram_map.split_first_mut().unwrap();
            (partition_map.address.take().map(VendorBootImage), tail)
        }
    }
}

/// Create Init Boot Image from corresponding partition for `partitions` and `avb_descriptors` lists
pub fn get_init_boot_image<'a: 'b, 'b: 'c, 'c, 'd>(
    verified_data: &mut VerifiedData<'d>,
    partitions_ram_map: &'a mut [PartitionRamMap<'b, 'c>],
) -> (Option<InitBootImage<'c>>, &'a mut [PartitionRamMap<'b, 'c>]) {
    match partitions_ram_map.len() {
        0 => (None, partitions_ram_map),
        _ => {
            let (partition_map, tail) = partitions_ram_map.split_first_mut().unwrap();
            (partition_map.address.take().map(InitBootImage), tail)
        }
    }
}

/// Create separate image types from [avb::Descriptor]
pub fn get_images<'a: 'b, 'b: 'c, 'c, 'd>(
    verified_data: &mut VerifiedData<'d>,
    partitions_ram_map: &'a mut [PartitionRamMap<'b, 'c>],
) -> (
    Option<BootImage<'c>>,
    Option<InitBootImage<'c>>,
    Option<VendorBootImage<'c>>,
    &'a mut [PartitionRamMap<'b, 'c>],
) {
    let (boot_image, partitions_ram_map) = get_boot_image(verified_data, partitions_ram_map);
    let (init_boot_image, partitions_ram_map) =
        get_init_boot_image(verified_data, partitions_ram_map);
    let (vendor_boot_image, partitions_ram_map) =
        get_vendor_boot_image(verified_data, partitions_ram_map);
    (boot_image, init_boot_image, vendor_boot_image, partitions_ram_map)
}

static BOOT_TOKEN: Mutex<Option<BootToken>> = Mutex::new(Some(BootToken(())));

type AvbVerifySlot = for<'b> fn(
    ops: &'b mut dyn avb::Ops,
    requested_partitions: &[&CStr],
    ab_suffix: Option<&CStr>,
    flags: SlotVerifyFlags,
    hashtree_error_mode: HashtreeErrorMode,
) -> SlotVerifyResult<'b, SlotVerifyData<'b>>;

#[derive(Debug)]
/// GBL object that provides implementation of helpers for boot process.
///
/// To create this object use [GblBuilder].
pub struct Gbl<'a, G> {
    ops: &'a mut G,
    image_verification: bool,
    verify_slot: AvbVerifySlot,
}

impl<'a, G> Gbl<'a, G>
where
    G: GblOps,
{
    /// Verify + Load Image Into memory
    ///
    /// Load from disk, validate with AVB
    ///
    /// # Arguments
    ///   * `avb_ops` - implementation for `avb::Ops` that would be borrowed in result to prevent
    ///   changes to partitions until it is out of scope.
    ///   * `partitions_ram_map` - Partitions to verify with optional address to load image to.
    ///   * `avb_verification_flags` - AVB verification flags/options
    ///   * `boot_target` - [Optional] Boot Target
    ///
    /// # Returns
    ///
    /// * `Ok(&[avb_descriptor])` - Array of AVB Descriptors - AVB return codes, partition name,
    /// image load address, image size, AVB Footer contents (version details, etc.)
    /// * `Err(Error)` - on failure
    pub fn load_and_verify_image<'b>(
        &mut self,
        avb_ops: &'b mut impl avb::Ops,
        partitions_ram_map: &mut [PartitionRamMap],
        avb_verification_flags: AvbVerificationFlags,
        boot_target: Option<BootTarget>,
        slot_cursor: &mut Cursor,
    ) -> Result<VerifiedData<'b>>
    where
        'a: 'b,
    {
        let bytes: SuffixBytes =
            if let Some(tgt) = boot_target { tgt.suffix().into() } else { Default::default() };

        let requested_partitions = [cstr!("")];
        let avb_suffix = CStr::from_bytes_until_nul(&bytes)?;

        let verified_data = VerifiedData((self.verify_slot)(
            avb_ops,
            &requested_partitions,
            Some(avb_suffix),
            SlotVerifyFlags::AVB_SLOT_VERIFY_FLAGS_NONE,
            HashtreeErrorMode::AVB_HASHTREE_ERROR_MODE_EIO,
        )?);

        Ok(verified_data)
    }

    /// Load Slot Manager Interface
    ///
    /// The default implementation loads from the `durable_boot` partition
    /// and writes changes back on the destruction of the cursor.
    ///
    /// # Returns
    ///
    /// * `Ok(Cursor)` - Cursor object that manages a Manager
    /// * `Err(Error)` - on failure
    pub fn load_slot_interface(&mut self) -> Result<Cursor> {
        let boot_token = BOOT_TOKEN.lock().take().ok_or(Error::OperationProhibited)?;
        self.ops.load_slot_interface(boot_token).map_err(|_| Error::OperationProhibited)
    }

    /// Info Load
    ///
    /// Unpack boot image in RAM
    ///
    /// # Arguments
    ///   * `boot_image_buffer` - Buffer that contains (Optionally Verified) Boot Image
    ///   * `boot_mode` - Boot Mode
    ///   * `boot_target` - [Optional] Boot Target
    ///
    /// # Returns
    ///
    /// * `Ok(InfoStruct)` - Info Struct (Concatenated kernel commandline - includes slot,
    /// bootconfig selection, normal_mode, Concatenated bootconfig) on success
    /// * `Err(Error)` - on failure
    pub fn unpack_boot_image(
        &self,
        boot_image_buffer: &BootImage,
        boot_target: Option<BootTarget>,
    ) -> Result<InfoStruct> {
        unimplemented!();
    }

    /// Kernel Load
    ///
    /// Prepare kernel in RAM for booting
    ///
    /// # Arguments
    ///   * `info` - Info Struct from Info Load
    ///   * `image_buffer` - Buffer that contains (Verified) Boot Image
    ///   * `load_buffer` - Kernel Load buffer
    ///
    /// # Returns
    ///
    /// * `Ok(())` - on success
    /// * `Err(Error)` - on failure
    pub fn kernel_load<'b>(
        &self,
        info: &InfoStruct,
        image_buffer: BootImage,
        load_buffer: &'b mut [u8],
    ) -> Result<KernelImage<'b>> {
        unimplemented!();
    }

    /// Ramdisk + Bootconfig Load
    ///
    /// Kernel Load
    /// (Could break this into a RD and Bootconfig specific function each, TBD)
    /// Prepare ramdisk/bootconfig in RAM for booting
    ///
    /// # Arguments
    ///   * `info` - Info Struct from Info Load
    ///   * `vendor_boot_image` - Buffer that contains (Verified) Vendor Boot Image
    ///   * `init_boot_image` - Buffer that contains (Verified) Init Boot Image
    ///   * `ramdisk_load_buffer` - Ramdisk Load buffer (not compressed). It will be filled with
    ///     a concatenation of `vendor_boot_image`, `init_boot_image` and bootconfig at the end.
    ///
    /// # Returns
    ///
    /// * `Ok(&str)` - on success returns Kernel command line
    /// * `Err(Error)` - on failure
    pub fn ramdisk_bootconfig_load(
        &self,
        info: &InfoStruct,
        vendor_boot_image: &VendorBootImage,
        init_boot_image: &InitBootImage,
        ramdisk: &mut Ramdisk,
    ) -> Result<&'static str> {
        unimplemented!();
    }

    /// DTB Update And Load
    ///
    /// Prepare DTB in RAM for booting
    ///
    /// # Arguments
    ///   * `info` - Info Struct from Info Load
    ///   * `vendor_boot_image_buffer` - Buffer that contains (Verified) Vendor Boot Image
    ///
    /// # Returns
    ///
    /// * `Ok()` - on success
    /// * `Err(Error)` - on failure
    pub fn dtb_update_and_load(
        &self,
        info: &InfoStruct,
        vendor_boot_image_buffer: VendorBootImage,
    ) -> Result<Dtb> {
        unimplemented!();
    }

    /// Kernel Jump
    ///
    ///
    /// # Arguments
    ///   * `kernel_load_buffer` - Kernel Load buffer
    ///   * `ramdisk_bootconfi_load_buffer` - Concatenated Ramdisk, (Bootconfig if present) Load
    ///   buffer
    ///   * `dtb_load_buffer` - DTB Load buffer
    ///   * `boot_token` - Consumable boot token
    ///
    /// # Returns
    ///
    /// * doesn't return on success
    /// * `Err(Error)` - on failure
    // Nevertype could be used here when it is stable https://github.com/serde-rs/serde/issues/812
    pub fn kernel_jump(
        &self,
        kernel_load_buffer: KernelImage,
        ramdisk_load_buffer: Ramdisk,
        dtb_load_buffer: Dtb,
        boot_token: BootToken,
    ) -> Result<()> {
        unimplemented!();
    }

    /// Load, verify, and boot
    ///
    /// Wrapper around the above functions for devices that don't need custom behavior between each
    /// step
    ///
    /// Warning: If the call to load_verify_boot fails, the device MUST
    ///          be restarted in order to make forward boot progress.
    ///          Callers MAY log the error, enter an interactive mode,
    ///          or take other actions before rebooting.
    ///
    ///
    /// # Arguments
    ///   * `avb_ops` - implementation for `avb::Ops` that would be borrowed in result to prevent
    ///   changes to partitions until it is out of scope.
    ///   * `partitions_ram_map` - Partitions to verify and optional address for them to be loaded.
    ///   * `avb_verification_flags` - AVB verification flags/options
    ///   * `slot_cursor` - Cursor object that manages interactions with boot slot management
    ///   * `kernel_load_buffer` - Buffer for loading the kernel.
    ///   * `ramdisk_load_buffer` - Buffer for loading the ramdisk.
    ///   * `fdt` - Buffer containing a flattened device tree blob.
    ///
    /// # Returns
    ///
    /// * doesn't return on success
    /// * `Err(Error)` - on failure
    // Nevertype could be used here when it is stable https://github.com/serde-rs/serde/issues/812
    #[allow(clippy::too_many_arguments)]
    pub fn load_verify_boot<'b: 'c, 'c, 'd: 'b>(
        &mut self,
        avb_ops: &'b mut impl avb::Ops,
        partitions_ram_map: &'d mut [PartitionRamMap<'b, 'c>],
        avb_verification_flags: AvbVerificationFlags,
        slot_cursor: Cursor,
        kernel_load_buffer: &mut [u8],
        ramdisk_load_buffer: &mut [u8],
        fdt: &mut [u8],
    ) -> Result<()> {
        // TODO(dovs):
        // * Change the receiver of ops.load_slot_interface to be &mut self
        // * Add partition write capabilites to slot manager
        let dtb = Dtb(&mut fdt[..]);
        let mut ramdisk = Ramdisk(ramdisk_load_buffer);

        // Call the inner method which consumes the cursor
        // in order to properly manager cursor lifetime
        // and cleanup.
        let (kernel_image, token) = self.lvb_inner(
            avb_ops,
            &mut ramdisk,
            kernel_load_buffer,
            partitions_ram_map,
            avb_verification_flags,
            slot_cursor,
        )?;

        self.kernel_jump(kernel_image, ramdisk, dtb, token)
    }

    fn is_unrecoverable_error(error: &Error) -> bool {
        // Note: these ifs are nested instead of chained because multiple
        //       expressions in an if-let is an unstable features
        if let Error::AvbSlotVerifyError(ref avb_error) = error {
            // These are the AVB errors that are not recoverable on a subsequent attempt.
            // If necessary in the future, this helper function can be moved to the GblOps trait
            // and customized for platform specific behavior.
            if matches!(
                avb_error,
                SlotVerifyError::Verification(_)
                    | SlotVerifyError::PublicKeyRejected
                    | SlotVerifyError::RollbackIndex
            ) {
                return true;
            }
        }
        false
    }

    fn lvb_inner<'b: 'c, 'c, 'd: 'b, 'e>(
        &mut self,
        avb_ops: &'b mut impl avb::Ops,
        ramdisk: &mut Ramdisk,
        kernel_load_buffer: &'e mut [u8],
        partitions_ram_map: &'d mut [PartitionRamMap<'b, 'c>],
        avb_verification_flags: AvbVerificationFlags,
        mut slot_cursor: Cursor,
    ) -> Result<(KernelImage<'e>, BootToken)> {
        let mut oneshot_status = slot_cursor.ctx.get_oneshot_status();
        slot_cursor.ctx.clear_oneshot_status();

        if oneshot_status == Some(OneShot::Bootloader) {
            match self.ops.do_fastboot(&slot_cursor) {
                Ok(_) => oneshot_status = slot_cursor.ctx.get_oneshot_status(),
                Err(Error::NotImplemented) => (),
                Err(e) => return Err(e),
            }
        }

        let boot_target = match oneshot_status {
            None | Some(OneShot::Bootloader) => slot_cursor.ctx.get_boot_target(),
            Some(OneShot::Continue(recovery)) => BootTarget::Recovery(recovery),
        };

        let mut verify_data = self
            .load_and_verify_image(
                avb_ops,
                partitions_ram_map,
                AvbVerificationFlags(0),
                Some(boot_target),
                &mut slot_cursor,
            )
            .map_err(|e: Error| {
                if let BootTarget::NormalBoot(slot) = boot_target {
                    if Self::is_unrecoverable_error(&e) {
                        let _ = slot_cursor.ctx.set_slot_unbootable(
                            slot.suffix,
                            UnbootableReason::VerificationFailure,
                        );
                    } else {
                        // Note: the call to mark_boot_attempt will fail if any of the following occur:
                        // * the target was already Unbootable before the call to load_and_verify_image
                        // * policy, I/O, or other errors in mark_boot_attempt
                        //
                        // We don't really care about those circumstances.
                        // The call here is a best effort attempt to decrement tries remaining.
                        let _ = slot_cursor.ctx.mark_boot_attempt(boot_target);
                    }
                }
                e
            })?;

        let (boot_image, init_boot_image, vendor_boot_image, _) =
            get_images(&mut verify_data, partitions_ram_map);
        let boot_image = boot_image.ok_or(Error::MissingImage)?;
        let vendor_boot_image = vendor_boot_image.ok_or(Error::MissingImage)?;
        let init_boot_image = init_boot_image.ok_or(Error::MissingImage)?;

        let info_struct = self.unpack_boot_image(&boot_image, Some(boot_target))?;

        let kernel_image = self.kernel_load(&info_struct, boot_image, kernel_load_buffer)?;

        let cmd_line = self.ramdisk_bootconfig_load(
            &info_struct,
            &vendor_boot_image,
            &init_boot_image,
            ramdisk,
        )?;

        self.dtb_update_and_load(&info_struct, vendor_boot_image)?;

        let token = slot_cursor
            .ctx
            .mark_boot_attempt(boot_target)
            .map_err(|_| Error::OperationProhibited)?;

        Ok((kernel_image, token))
    }
}

#[cfg(feature = "sw_digest")]
impl<'a> Default for Gbl<'a, DefaultGblOps> {
    fn default() -> Self {
        GblBuilder::new(DefaultGblOps::new()).build()
    }
}

/// Builder for GBL object
#[derive(Debug)]
pub struct GblBuilder<'a, G> {
    ops: &'a mut G,
    image_verification: bool,
    verify_slot: AvbVerifySlot,
}

impl<'a, G> GblBuilder<'a, G>
where
    G: GblOps,
{
    /// Start Gbl object creation, with default GblOps implementation
    pub fn new(ops: &'a mut G) -> Self {
        GblBuilder { ops, image_verification: true, verify_slot: avb::slot_verify }
    }

    /// Disable image verification
    pub fn no_image_verification(mut self) -> Self {
        self.image_verification = false;
        self
    }

    // Override [avb::slot_verify] for testing only
    #[cfg(test)]
    fn verify_slot(mut self, verify_slot: AvbVerifySlot) -> Self {
        self.verify_slot = verify_slot;
        self
    }

    /// Finish Gbl object construction and return it as the result
    pub fn build(self) -> Gbl<'a, G> {
        Gbl {
            ops: self.ops,
            image_verification: self.image_verification,
            verify_slot: self.verify_slot,
        }
    }
}

#[cfg(test)]
mod tests {
    extern crate avb_test;

    use super::*;
    use avb::IoError;
    use avb::IoResult as AvbIoResult;
    use avb::PublicKeyForPartitionInfo;
    use avb_test::TestOps;
    use slots::fuchsia::SlotBlock;
    use std::fs;

    struct AvbOpsUnimplemented {}
    impl avb::Ops for AvbOpsUnimplemented {
        fn validate_vbmeta_public_key(&mut self, _: &[u8], _: Option<&[u8]>) -> AvbIoResult<bool> {
            Err(IoError::NotImplemented)
        }
        fn read_from_partition(&mut self, _: &CStr, _: i64, _: &mut [u8]) -> AvbIoResult<usize> {
            Err(IoError::NotImplemented)
        }
        fn read_rollback_index(&mut self, _: usize) -> AvbIoResult<u64> {
            Err(IoError::NotImplemented)
        }
        fn write_rollback_index(&mut self, _: usize, _: u64) -> AvbIoResult<()> {
            Err(IoError::NotImplemented)
        }
        fn read_is_device_unlocked(&mut self) -> AvbIoResult<bool> {
            Err(IoError::NotImplemented)
        }
        #[cfg(feature = "uuid")]
        fn get_unique_guid_for_partition(&mut self, partition: &CStr) -> AvbIoResult<uuid::Uuid> {
            Err(IoError::NotImplemented)
        }
        fn get_size_of_partition(&mut self, partition: &CStr) -> AvbIoResult<u64> {
            Err(IoError::NotImplemented)
        }
        fn read_persistent_value(&mut self, name: &CStr, value: &mut [u8]) -> AvbIoResult<usize> {
            Err(IoError::NotImplemented)
        }
        fn write_persistent_value(&mut self, name: &CStr, value: &[u8]) -> AvbIoResult<()> {
            Err(IoError::NotImplemented)
        }
        fn erase_persistent_value(&mut self, name: &CStr) -> AvbIoResult<()> {
            Err(IoError::NotImplemented)
        }
        fn validate_public_key_for_partition(
            &mut self,
            partition: &CStr,
            public_key: &[u8],
            public_key_metadata: Option<&[u8]>,
        ) -> AvbIoResult<PublicKeyForPartitionInfo> {
            Err(IoError::NotImplemented)
        }
    }

    #[cfg(feature = "sw_digest")]
    #[test]
    fn test_load_and_verify_image_avb_io_error() {
        let mut gbl = GblBuilder::new(DefaultGblOps::new()).build();
        let mut avb_ops = AvbOpsUnimplemented {};
        let mut partitions_ram_map: [PartitionRamMap; 0] = [];
        let avb_verification_flags = AvbVerificationFlags(0);
        let mut slot_cursor = Cursor { ctx: &mut SlotBlock::default() };
        let res = gbl.load_and_verify_image(
            &mut avb_ops,
            &mut partitions_ram_map,
            avb_verification_flags,
            None,
            &mut slot_cursor,
        );
        assert_eq!(res.unwrap_err(), Error::AvbSlotVerifyError(SlotVerifyError::Io));
    }

    const TEST_PARTITION_NAME: &str = "test_part";
    const TEST_IMAGE_PATH: &str = "testdata/test_image.img";
    const TEST_VBMETA_PATH: &str = "testdata/test_vbmeta.img";
    const TEST_PUBLIC_KEY_PATH: &str = "testdata/testkey_rsa4096_pub.bin";
    const TEST_VBMETA_ROLLBACK_LOCATION: usize = 0; // Default value, we don't explicitly set this.

    #[cfg(feature = "sw_digest")]
    #[test]
    fn test_load_and_verify_image_stub() {
        let mut gbl = GblBuilder::new(DefaultGblOps::new()).build();
        let mut avb_ops = TestOps::default();
        let mut slot_cursor = Cursor { ctx: &mut SlotBlock::default() };

        avb_ops.add_partition(TEST_PARTITION_NAME, fs::read(TEST_IMAGE_PATH).unwrap());
        avb_ops.add_partition("vbmeta", fs::read(TEST_VBMETA_PATH).unwrap());
        avb_ops.add_vbmeta_key(fs::read(TEST_PUBLIC_KEY_PATH).unwrap(), None, true);
        avb_ops.rollbacks.insert(TEST_VBMETA_ROLLBACK_LOCATION, 0);
        avb_ops.unlock_state = Ok(false);

        let mut partitions_ram_map: [PartitionRamMap; 0] = [];
        let avb_verification_flags = AvbVerificationFlags(0);
        let res = gbl.load_and_verify_image(
            &mut avb_ops,
            &mut partitions_ram_map,
            avb_verification_flags,
            None,
            &mut slot_cursor,
        );
        assert!(res.is_ok());
    }

    #[cfg(feature = "sw_digest")]
    #[test]
    fn test_load_and_verify_image_avb_error() {
        const TEST_ERROR: SlotVerifyError<'static> = SlotVerifyError::Verification(None);
        let expected_error = SlotVerifyError::Verification(None);
        let mut gbl = GblBuilder::new(DefaultGblOps::new())
            .verify_slot(|_, _, _, _, _| Err(TEST_ERROR))
            .build();
        let mut avb_ops = AvbOpsUnimplemented {};
        let mut partitions_ram_map: [PartitionRamMap; 0] = [];
        let avb_verification_flags = AvbVerificationFlags(0);
        let mut slot_cursor = Cursor { ctx: &mut SlotBlock::default() };
        let res = gbl.load_and_verify_image(
            &mut avb_ops,
            &mut partitions_ram_map,
            avb_verification_flags,
            None,
            &mut slot_cursor,
        );
        assert_eq!(res.unwrap_err(), Error::AvbSlotVerifyError(TEST_ERROR));
    }
}
