// Copyright 2024, The Android Open Source Project
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

use crate::error::{EfiAppError, Result};
use crate::utils::{aligned_subslice, find_gpt_devices, get_efi_fdt, usize_add};
use core::fmt::Write;
use core::mem::size_of;
use efi::{efi_print, efi_println, EfiEntry};
use fdt::Fdt;
use zbi::{ZbiContainer, ZbiFlags, ZbiHeader, ZbiType, ZBI_ALIGNMENT_USIZE};
use zerocopy::{AsBytes, FromBytes, FromZeroes, Ref};

/// A ZBI kernel is a ZBi container where the first ZBI item is a kernel type item.
#[repr(C)]
#[derive(Debug, Default, Copy, Clone, FromBytes, AsBytes, FromZeroes)]
struct ZbiKernelHeader {
    container_header: ZbiHeader,
    zbi_item_header: ZbiHeader,
    pub entry: u64,
    pub reserve_memory_size: u64,
}

// Kernel load address alignment. Value taken from
// https://fuchsia.googlesource.com/fuchsia/+/4f204d8a0243e84a86af4c527a8edcc1ace1615f/zircon/kernel/target/arm64/boot-shim/BUILD.gn#38
const ZIRCON_KERNEL_ALIGN: usize = 64 * 1024;

/// Relocates a ZBI kernel to a different buffer and returns the kernel entry address.
pub fn relocate_kernel(kernel: &[u8], dest: &mut [u8]) -> Result<usize> {
    if (dest.as_ptr() as usize % ZIRCON_KERNEL_ALIGN) != 0 {
        return Err(EfiAppError::BufferAlignment.into());
    }

    let container = ZbiContainer::parse(&kernel[..])?;
    container.is_bootable()?;
    let kernel_header = Ref::<_, ZbiKernelHeader>::new_from_prefix(kernel)
        .ok_or_else(|| EfiAppError::InvalidInput)?
        .0
        .into_ref();
    let kernel_size = usize_add(2 * size_of::<ZbiHeader>(), kernel_header.zbi_item_header.length)?;
    if dest.len() < usize_add(kernel_size, kernel_header.reserve_memory_size)? {
        return Err(EfiAppError::BufferTooSmall.into());
    }

    dest[..kernel_size].clone_from_slice(&kernel[..kernel_size]);
    // Updates destination ZBI container/item header.
    let dest_kernel_header =
        Ref::<_, ZbiKernelHeader>::new_from_prefix(&mut dest[..]).unwrap().0.into_mut();
    dest_kernel_header.container_header.length = (kernel_size - size_of::<ZbiHeader>())
        .try_into()
        .map_err(|_| EfiAppError::ArithmeticOverflow)?;
    Ok(usize_add(dest_kernel_header.entry, dest.as_ptr() as usize)?)
}

/// A helper for getting the total size of a ZBI container, including payload and header.
fn zbi_container_size(zbi: &[u8]) -> Result<usize> {
    let payload_length = ZbiContainer::parse(&zbi[..])?.get_payload_length_usize();
    Ok(usize_add(payload_length, size_of::<ZbiHeader>())?)
}

/// A helper for getting the trailing unused portion of a ZBI container buffer.
///
/// Returns a tuple of used subslice and unused subslice
fn zbi_get_unused_buffer(zbi: &mut [u8]) -> Result<(&mut [u8], &mut [u8])> {
    let container_size = zbi_container_size(zbi)?;
    Ok(zbi.split_at_mut(container_size))
}

/// Relocate a ZBI kernel to the trailing unused buffer.
///
/// Returns the original kernel subslice, relocated kernel subslice, and kernel entry address.
fn relocate_to_tail(kernel: &mut [u8]) -> Result<(&mut [u8], &mut [u8], usize)> {
    let (original, relocated) = zbi_get_unused_buffer(kernel)?;
    let relocated = aligned_subslice(relocated, ZIRCON_KERNEL_ALIGN)?;
    let entry = relocate_kernel(original, relocated)?;
    Ok((original, relocated, entry))
}

/// Load a ZBI kernel from the disk.
///
/// Returns the subslice the kernel is loaded to.
fn load_fuchsia_simple<'a>(efi_entry: &EfiEntry, load: &'a mut [u8]) -> Result<&'a mut [u8]> {
    let load = aligned_subslice(load, ZBI_ALIGNMENT_USIZE)?;

    let mut gpt_devices = find_gpt_devices(&efi_entry)?;

    // Gets FDT from EFI configuration table.
    let (_, fdt_bytes) = get_efi_fdt(&efi_entry).ok_or_else(|| EfiAppError::NoFdt).unwrap();
    let fdt = Fdt::new(fdt_bytes)?;

    // Checks if UEFI loader passes any custom ZBI blob.
    let (custom_zbi, load) = match fdt
        .get_property("zircon", core::ffi::CStr::from_bytes_with_nul(b"zbi-blob\0").unwrap())
    {
        Ok(blob) => {
            // Make to copy to make sure container is in an aligned buffer
            load[..blob.len()].clone_from_slice(blob);
            let (custom_zbi_buffer, remains) = zbi_get_unused_buffer(load)?;
            (Some(ZbiContainer::parse(custom_zbi_buffer).unwrap()), remains)
        }
        _ => (None, load),
    };
    let load = aligned_subslice(load, ZIRCON_KERNEL_ALIGN)?;

    // Reads ZBI header to compute image length.
    gpt_devices.read_gpt_partition("zircon_a", 0, &mut load[..size_of::<ZbiHeader>()]).unwrap();
    let image_length = Ref::<_, ZbiHeader>::new_from_prefix(&mut load[..])
        .ok_or_else(|| EfiAppError::NoZbiImage)?
        .0
        .into_ref()
        .length;

    // Reads the entire image.
    gpt_devices
        .read_gpt_partition(
            "zircon_a",
            0,
            &mut load[..usize_add(size_of::<ZbiHeader>(), image_length)?],
        )
        .unwrap();

    let mut zbi = ZbiContainer::parse(&mut load[..])?;
    // Appends current slot zbi item. The demo always boots from A slot.
    zbi.create_entry_with_payload(
        ZbiType::CmdLine,
        0,
        ZbiFlags::default(),
        b"zvb.current_slot=_a",
    )?;
    // Appends device custom ZBI blob.
    if let Some(val) = custom_zbi {
        zbi.extend(&val)?;
    }

    Ok(load)
}

/// Check if the disk GPT layout is a Fuchsia device layout.
pub fn is_fuchsia_gpt(efi_entry: &EfiEntry) -> Result<()> {
    let mut gpt_devices = find_gpt_devices(&efi_entry)?;
    let partitions: [&[&str]; 8] = [
        &["zircon_a"],
        &["zircon_b"],
        &["zircon_r"],
        &["vbmeta_a"],
        &["vbmeta_b"],
        &["vbmeta_r"],
        &["misc", "durable_boot"],
        &["fvm"],
    ];
    for partition in partitions {
        gpt_devices.partition_size_with_aliases(partition)?;
    }
    Ok(())
}

// The following implements a demo for booting Fuchsia ZBI kernel from disk. It currently targets
// at the Vim3 development board
// (https://fuchsia.dev/fuchsia-src/development/hardware/khadas-vim3?hl=en).
//
// To run the demo:
//   1. Complete all steps in the link above to setup Vim3 as a Fuchsia device.
//   2. Reboot the device into fastboot mode.
//   3. Run "fastboot stage <path to the EFI binary> && fastboot oem run-staged-efi"
//
// The demo has a number of simplifications:
//
//   * No A/B/R slot switching is performed. It always boot from zircon_a slot.
//   * No AVB is performed.
//
// The missing pieces above are currently under development as part of the full end-to-end boot
// flow in libgbl, which will eventually replace this demo. The demo is currently used as an
// end-to-end test for libraries developed so far.
pub fn fuchsia_boot_demo(efi_entry: EfiEntry) -> Result<()> {
    efi_println!(efi_entry, "Try booting as Fuchsia/Zircon");

    // Allocate buffer for load.
    let mut load_buffer = vec![0u8; 128 * 1024 * 1024]; // 128MB
    let zbi_kernel = load_fuchsia_simple(&efi_entry, &mut load_buffer[..])?;
    #[allow(unused_variables)]
    let (original, relocated, kernel_entry) = relocate_to_tail(&mut zbi_kernel[..])?;

    #[cfg(target_arch = "aarch64")]
    {
        // Uses the unused buffer for `exit_boot_services` to store output memory map.
        // The map is not used for now. We currently rely on UEFI firmware to pass memory map via
        // an raw zbi blob in device tree. Long term we want to support adding from EFI memory maps
        // if none is provided.
        let (_, remains) = zbi_get_unused_buffer(relocated)?;
        let _ = efi::exit_boot_services(efi_entry, remains).unwrap();
        // SAFETY: For demo, we assume images are provided valid.
        unsafe { boot::aarch64::jump_zircon_el2_or_lower(kernel_entry, original) };
    }

    #[cfg(any(target_arch = "x86_64", target_arch = "x86"))]
    {
        unimplemented!();
    }

    #[cfg(target_arch = "riscv64")]
    {
        unimplemented!();
    }
}
