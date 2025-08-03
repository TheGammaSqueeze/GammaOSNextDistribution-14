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

use alloc::vec::Vec;
use core::ffi::CStr;

use crate::error::{EfiAppError, Result};
use efi::defs::EfiGuid;
use efi::{
    BlockIoProtocol, DeviceHandle, DevicePathProtocol, DevicePathText, DevicePathToTextProtocol,
    EfiEntry, LoadedImageProtocol, Protocol,
};
use fdt::FdtHeader;
use gbl_storage::{required_scratch_size, AsBlockDevice, BlockIo, GptEntry};

pub const EFI_DTB_TABLE_GUID: EfiGuid =
    EfiGuid::new(0xb1b621d5, 0xf19c, 0x41a5, [0x83, 0x0b, 0xd9, 0x15, 0x2c, 0x69, 0xaa, 0xe0]);

/// Checks and converts an integer into usize
fn to_usize<T: TryInto<usize>>(val: T) -> Result<usize> {
    Ok(val.try_into().map_err(|_| EfiAppError::ArithmeticOverflow)?)
}

/// Rounds up a usize convertible number.
pub fn usize_roundup<L: TryInto<usize>, R: TryInto<usize>>(lhs: L, rhs: R) -> Result<usize> {
    // (lhs + rhs - 1) / rhs * rhs
    let lhs = to_usize(lhs)?;
    let rhs = to_usize(rhs)?;
    let compute = || lhs.checked_add(rhs.checked_sub(1)?)?.checked_div(rhs)?.checked_mul(rhs);
    Ok(compute().ok_or_else(|| EfiAppError::ArithmeticOverflow)?)
}

/// Adds two usize convertible numbers and checks overflow.
pub fn usize_add<L: TryInto<usize>, R: TryInto<usize>>(lhs: L, rhs: R) -> Result<usize> {
    Ok(to_usize(lhs)?.checked_add(to_usize(rhs)?).ok_or_else(|| EfiAppError::ArithmeticOverflow)?)
}

/// Multiply two usize convertible numbers and checks overflow.
pub fn usize_mul<L: TryInto<usize>, R: TryInto<usize>>(lhs: L, rhs: R) -> Result<usize> {
    Ok(to_usize(lhs)?.checked_mul(to_usize(rhs)?).ok_or_else(|| EfiAppError::ArithmeticOverflow)?)
}

/// Gets a subslice of the given slice with aligned address according to `alignment`
pub fn aligned_subslice(bytes: &mut [u8], alignment: usize) -> Result<&mut [u8]> {
    let addr = bytes.as_ptr() as usize;
    Ok(&mut bytes[usize_roundup(addr, alignment)? - addr..])
}

// Implement a block device on top of BlockIoProtocol
pub struct EfiBlockIo<'a>(pub Protocol<'a, BlockIoProtocol>);

impl BlockIo for EfiBlockIo<'_> {
    fn block_size(&mut self) -> u64 {
        self.0.media().unwrap().block_size as u64
    }

    fn num_blocks(&mut self) -> u64 {
        (self.0.media().unwrap().last_block + 1) as u64
    }

    fn alignment(&mut self) -> u64 {
        core::cmp::max(1, self.0.media().unwrap().io_align as u64)
    }

    fn read_blocks(&mut self, blk_offset: u64, out: &mut [u8]) -> bool {
        self.0.read_blocks(blk_offset, out).is_ok()
    }

    fn write_blocks(&mut self, blk_offset: u64, data: &[u8]) -> bool {
        self.0.write_blocks(blk_offset, data).is_ok()
    }
}

/// `EfiGptDevice` wraps a `EfiBlockIo` and implements `AsBlockDevice` interface.
pub struct EfiGptDevice<'a> {
    io: EfiBlockIo<'a>,
    scratch: Vec<u8>,
}

const MAX_GPT_ENTRIES: u64 = 128;

impl<'a> EfiGptDevice<'a> {
    /// Initialize from a `BlockIoProtocol` EFI protocol
    pub fn new(protocol: Protocol<'a, BlockIoProtocol>) -> Result<Self> {
        let mut io = EfiBlockIo(protocol);
        let scratch = vec![0u8; required_scratch_size(&mut io, MAX_GPT_ENTRIES)?];
        Ok(Self { io, scratch })
    }
}

impl AsBlockDevice for EfiGptDevice<'_> {
    fn get(&mut self) -> (&mut dyn BlockIo, &mut [u8], u64) {
        (&mut self.io, &mut self.scratch[..], MAX_GPT_ENTRIES)
    }
}

/// A helper type that searches and reads/writes from multiple GPT devices.
/// Platforms like cuttlefish may have additional block devices for storing device specific data
/// such as bootconfig.
pub struct MultiGptDevices<'a> {
    gpt_devices: Vec<EfiGptDevice<'a>>,
}

impl<'a> MultiGptDevices<'a> {
    pub fn new(gpt_devices: Vec<EfiGptDevice<'a>>) -> Self {
        Self { gpt_devices }
    }

    /// Find a partition on the first match.
    pub fn find_partition(&mut self, part: &str) -> Result<(usize, GptEntry)> {
        for (idx, device) in &mut self.gpt_devices[..].iter_mut().enumerate() {
            match device.gpt()?.find_partition(part)? {
                Some(p) => {
                    return Ok((idx, *p));
                }
                _ => {}
            }
        }
        Err(EfiAppError::NotFound.into())
    }

    /// Finds a partition given by a set of possible aliases on the first match.
    pub fn find_partition_with_aliases(&mut self, aliases: &[&str]) -> Result<(usize, GptEntry)> {
        for alias in aliases {
            match self.find_partition(alias) {
                Ok(v) => return Ok(v),
                _ => {}
            }
        }
        Err(EfiAppError::NotFound.into())
    }

    /// Finds size of a partition given by a set of possible aliases.
    pub fn partition_size_with_aliases(&mut self, aliases: &[&str]) -> Result<usize> {
        let (idx, part) = self.find_partition_with_aliases(aliases)?;
        Ok(usize_mul(part.blocks()?, self.gpt_devices[idx].block_io().block_size())?)
    }

    /// Returns the size of the target partition on the first match.
    pub fn partition_size(&mut self, part: &str) -> Result<usize> {
        self.partition_size_with_aliases(&[part])
    }

    /// Traverse all gpt devices and read the given partition on the first match.
    pub fn read_gpt_partition(
        &mut self,
        part_name: &str,
        offset: u64,
        out: &mut [u8],
    ) -> Result<()> {
        let (idx, _) = self.find_partition(part_name)?;
        Ok(self.gpt_devices[idx].read_gpt_partition(part_name, offset, out)?)
    }
}

/// Helper function to get the `DevicePathText` from a `DeviceHandle`.
pub fn get_device_path<'a>(
    entry: &'a EfiEntry,
    handle: DeviceHandle,
) -> Result<DevicePathText<'a>> {
    let bs = entry.system_table().boot_services();
    let path = bs.open_protocol::<DevicePathProtocol>(handle)?;
    let path_to_text = bs.find_first_and_open::<DevicePathToTextProtocol>()?;
    Ok(path_to_text.convert_device_path_to_text(&path, false, false)?)
}

/// Helper function to get the loaded image path.
pub fn loaded_image_path(entry: &EfiEntry) -> Result<DevicePathText> {
    get_device_path(
        entry,
        entry
            .system_table()
            .boot_services()
            .open_protocol::<LoadedImageProtocol>(entry.image_handle())?
            .device_handle()?,
    )
}

/// Find FDT from EFI configuration table.
pub fn get_efi_fdt<'a>(entry: &'a EfiEntry) -> Option<(&FdtHeader, &[u8])> {
    if let Some(config_tables) = entry.system_table().configuration_table() {
        for table in config_tables {
            if table.vendor_guid == EFI_DTB_TABLE_GUID {
                // SAFETY: Buffer provided by EFI configuration table.
                return unsafe { FdtHeader::from_raw(table.vendor_table as *const _).ok() };
            }
        }
    }
    None
}

/// Find all block devices that have a valid GPT and returns them as a `MultiGptDevices`.
pub fn find_gpt_devices(efi_entry: &EfiEntry) -> Result<MultiGptDevices> {
    let bs = efi_entry.system_table().boot_services();
    let block_dev_handles = bs.locate_handle_buffer_by_protocol::<BlockIoProtocol>()?;
    let mut gpt_devices = Vec::<EfiGptDevice>::new();
    for handle in block_dev_handles.handles() {
        let mut gpt_dev = EfiGptDevice::new(bs.open_protocol::<BlockIoProtocol>(*handle)?)?;
        match gpt_dev.sync_gpt() {
            Ok(_) => {
                gpt_devices.push(gpt_dev);
            }
            _ => {}
        };
    }
    Ok(MultiGptDevices::new(gpt_devices))
}

#[cfg(any(target_arch = "x86_64", target_arch = "x86"))]
pub fn efi_to_e820_mem_type(efi_mem_type: u32) -> u32 {
    match efi_mem_type {
        efi::defs::EFI_MEMORY_TYPE_LOADER_CODE
        | efi::defs::EFI_MEMORY_TYPE_LOADER_DATA
        | efi::defs::EFI_MEMORY_TYPE_BOOT_SERVICES_CODE
        | efi::defs::EFI_MEMORY_TYPE_BOOT_SERVICES_DATA
        | efi::defs::EFI_MEMORY_TYPE_CONVENTIONAL_MEMORY => boot::x86::E820_ADDRESS_TYPE_RAM,
        efi::defs::EFI_MEMORY_TYPE_RUNTIME_SERVICES_CODE
        | efi::defs::EFI_MEMORY_TYPE_RUNTIME_SERVICES_DATA
        | efi::defs::EFI_MEMORY_TYPE_MEMORY_MAPPED_IO
        | efi::defs::EFI_MEMORY_TYPE_MEMORY_MAPPED_IOPORT_SPACE
        | efi::defs::EFI_MEMORY_TYPE_PAL_CODE
        | efi::defs::EFI_MEMORY_TYPE_RESERVED_MEMORY_TYPE => boot::x86::E820_ADDRESS_TYPE_RESERVED,
        efi::defs::EFI_MEMORY_TYPE_UNUSABLE_MEMORY => boot::x86::E820_ADDRESS_TYPE_UNUSABLE,
        efi::defs::EFI_MEMORY_TYPE_ACPIRECLAIM_MEMORY => boot::x86::E820_ADDRESS_TYPE_ACPI,
        efi::defs::EFI_MEMORY_TYPE_ACPIMEMORY_NVS => boot::x86::E820_ADDRESS_TYPE_NVS,
        efi::defs::EFI_MEMORY_TYPE_PERSISTENT_MEMORY => boot::x86::E820_ADDRESS_TYPE_PMEM,
        v => panic!("Unmapped EFI memory type {v}"),
    }
}

/// A helper to convert a bytes slice containing a null-terminated string to `str`
pub fn cstr_bytes_to_str(data: &[u8]) -> Result<&str> {
    Ok(CStr::from_bytes_until_nul(data)
        .map_err(|_| EfiAppError::InvalidString)?
        .to_str()
        .map_err(|_| EfiAppError::InvalidString)?)
}
