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

use core::ffi::CStr;
use core::fmt::Write;
use core::str::from_utf8;

use bootconfig::{BootConfigBuilder, BootConfigError};
use bootimg::{BootImage, VendorImageHeader};
use efi::{efi_print, efi_println, exit_boot_services, EfiEntry};
use fdt::Fdt;

use crate::error::{EfiAppError, GblEfiError, Result};
use crate::utils::{
    aligned_subslice, cstr_bytes_to_str, find_gpt_devices, get_efi_fdt, usize_add, usize_roundup,
    MultiGptDevices,
};

use crate::avb::GblEfiAvbOps;
use avb::{slot_verify, HashtreeErrorMode, Ops, SlotVerifyFlags};

// Linux kernel requires 2MB alignment.
const KERNEL_ALIGNMENT: usize = 2 * 1024 * 1024;
// libfdt requires FDT buffer to be 8-byte aligned.
const FDT_ALIGNMENT: usize = 8;

/// A helper macro for creating a null-terminated string literal as CStr.
macro_rules! cstr_literal {
    ( $( $x:expr ),* $(,)?) => {
       CStr::from_bytes_until_nul(core::concat!($($x),*, "\0").as_bytes()).unwrap()
    };
}

/// Helper function for performing libavb verification.
fn avb_verify_slot<'a, 'b, 'c>(
    gpt_dev: &'b mut MultiGptDevices<'a>,
    kernel: &'b [u8],
    vendor_boot: &'b [u8],
    init_boot: &'b [u8],
    bootconfig_builder: &'b mut BootConfigBuilder<'c>,
) -> Result<()> {
    let preloaded = [("boot", kernel), ("vendor_boot", vendor_boot), ("init_boot", init_boot)];
    let mut avb_ops = GblEfiAvbOps::new(gpt_dev, Some(&preloaded));
    let avb_state = match avb_ops.read_is_device_unlocked()? {
        true => "orange",
        _ => "green",
    };

    let res = slot_verify(
        &mut avb_ops,
        &[cstr_literal!("boot"), cstr_literal!("vendor_boot"), cstr_literal!("init_boot")],
        Some(cstr_literal!("_a")),
        SlotVerifyFlags::AVB_SLOT_VERIFY_FLAGS_NONE,
        // For demo, we use the same setting as Cuttlefish u-boot.
        HashtreeErrorMode::AVB_HASHTREE_ERROR_MODE_RESTART_AND_INVALIDATE,
    )
    .map_err(|e| Into::<GblEfiError>::into(e.without_verify_data()))?;

    // Append avb generated bootconfig.
    for cmdline_arg in res.cmdline().to_str().unwrap().split(' ') {
        write!(bootconfig_builder, "{}\n", cmdline_arg).map_err(|_| EfiAppError::BufferTooSmall)?;
    }

    // Append "androidboot.verifiedbootstate="
    write!(bootconfig_builder, "androidboot.verifiedbootstate={}\n", avb_state)
        .map_err(|_| EfiAppError::BufferTooSmall)?;
    Ok(())
}

/// Loads Android images from disk and fixes up bootconfig, commandline, and FDT.
///
/// A number of simplifications are made:
///
///   * No A/B slot switching is performed. It always boot from *_a slot.
///   * No dynamic partitions.
///   * Only support V3/V4 image and Android 13+ (generic ramdisk from the "init_boot" partition)
///
/// # Returns
///
/// Returns a tuple of 4 slices corresponding to:
///   (ramdisk load buffer, FDT load buffer, kernel load buffer, unused buffer).
pub fn load_android_simple<'a>(
    efi_entry: &EfiEntry,
    load: &'a mut [u8],
) -> Result<(&'a mut [u8], &'a mut [u8], &'a mut [u8], &'a mut [u8])> {
    let mut gpt_devices = find_gpt_devices(efi_entry)?;

    const PAGE_SIZE: usize = 4096; // V3/V4 image has fixed page size 4096;

    // Parse boot header.
    let (boot_header_buffer, load) = load.split_at_mut(PAGE_SIZE);
    gpt_devices.read_gpt_partition("boot_a", 0, boot_header_buffer)?;
    let boot_header = BootImage::parse(boot_header_buffer)?;
    let (kernel_size, cmdline, kernel_hdr_size) = match boot_header {
        BootImage::V3(ref hdr) => (hdr.kernel_size as usize, &hdr.cmdline[..], PAGE_SIZE),
        BootImage::V4(ref hdr) => {
            (hdr._base.kernel_size as usize, &hdr._base.cmdline[..], PAGE_SIZE)
        }
        _ => {
            efi_println!(efi_entry, "V0/V1/V2 images are not supported");
            return Err(GblEfiError::EfiAppError(EfiAppError::Unsupported));
        }
    };
    efi_println!(efi_entry, "boot image size: {}", kernel_size);
    efi_println!(efi_entry, "boot image cmdline: \"{}\"", from_utf8(cmdline).unwrap());

    // Parse vendor boot header.
    let (vendor_boot_header_buffer, load) = load.split_at_mut(PAGE_SIZE);
    gpt_devices.read_gpt_partition("vendor_boot_a", 0, vendor_boot_header_buffer)?;
    let vendor_boot_header = VendorImageHeader::parse(vendor_boot_header_buffer)?;
    let (vendor_ramdisk_size, vendor_hdr_size, vendor_cmdline) = match vendor_boot_header {
        VendorImageHeader::V3(ref hdr) => (
            hdr.vendor_ramdisk_size as usize,
            usize_roundup(hdr.bytes().len(), hdr.page_size)?,
            &hdr.cmdline[..],
        ),
        VendorImageHeader::V4(ref hdr) => (
            hdr._base.vendor_ramdisk_size as usize,
            usize_roundup(hdr.bytes().len(), hdr._base.page_size)?,
            &hdr._base.cmdline[..],
        ),
    };
    efi_println!(efi_entry, "vendor ramdisk size: {}", vendor_ramdisk_size);
    efi_println!(efi_entry, "vendor cmdline: \"{}\"", from_utf8(vendor_cmdline).unwrap());

    // Parse init_boot header
    let init_boot_header_buffer = &mut load[..PAGE_SIZE];
    gpt_devices.read_gpt_partition("init_boot_a", 0, init_boot_header_buffer)?;
    let init_boot_header = BootImage::parse(init_boot_header_buffer)?;
    let (generic_ramdisk_size, init_boot_hdr_size) = match init_boot_header {
        BootImage::V3(ref hdr) => (hdr.ramdisk_size as usize, PAGE_SIZE),
        BootImage::V4(ref hdr) => (hdr._base.ramdisk_size as usize, PAGE_SIZE),
        _ => {
            efi_println!(efi_entry, "V0/V1/V2 images are not supported");
            return Err(GblEfiError::EfiAppError(EfiAppError::Unsupported));
        }
    };
    efi_println!(efi_entry, "init_boot image size: {}", generic_ramdisk_size);

    // Load and prepare various images.
    let images_buffer = aligned_subslice(load, KERNEL_ALIGNMENT)?;
    let load = &mut images_buffer[..];

    // Load kernel
    // Kernel may need to reserve additional memory after itself. To avoid the risk of this
    // memory overlapping with ramdisk. We place kernel after ramdisk. We first load it to the tail
    // of the buffer and move it forward as much as possible after ramdisk and fdt are loaded,
    // fixed-up and finalized.
    let kernel_load_offset = {
        let off = load.len().checked_sub(kernel_size).ok_or_else(|| EfiAppError::BufferTooSmall)?;
        off.checked_sub(load[off..].as_ptr() as usize % KERNEL_ALIGNMENT)
            .ok_or_else(|| EfiAppError::BufferTooSmall)?
    };
    let (load, kernel_tail_buffer) = load.split_at_mut(kernel_load_offset);
    gpt_devices.read_gpt_partition(
        "boot_a",
        kernel_hdr_size.try_into().unwrap(),
        &mut kernel_tail_buffer[..kernel_size],
    )?;

    // Load vendor ramdisk
    let mut ramdisk_load_curr = 0;
    gpt_devices.read_gpt_partition(
        "vendor_boot_a",
        vendor_hdr_size.try_into().unwrap(),
        &mut load[ramdisk_load_curr..][..vendor_ramdisk_size],
    )?;
    ramdisk_load_curr = usize_add(ramdisk_load_curr, vendor_ramdisk_size)?;

    // Load generic ramdisk
    gpt_devices.read_gpt_partition(
        "init_boot_a",
        init_boot_hdr_size.try_into().unwrap(),
        &mut load[ramdisk_load_curr..][..generic_ramdisk_size],
    )?;
    ramdisk_load_curr = usize_add(ramdisk_load_curr, generic_ramdisk_size)?;

    // Prepare partition data for avb verification
    let (vendor_boot_load_buffer, remains) = load.split_at_mut(vendor_ramdisk_size);
    let (init_boot_load_buffer, remains) = remains.split_at_mut(generic_ramdisk_size);
    // Prepare a BootConfigBuilder to add avb generated bootconfig.
    let mut bootconfig_builder = BootConfigBuilder::new(remains)?;
    // Perform avb verification.
    avb_verify_slot(
        &mut gpt_devices,
        kernel_tail_buffer,
        vendor_boot_load_buffer,
        init_boot_load_buffer,
        &mut bootconfig_builder,
    )?;

    // Add slot index
    bootconfig_builder.add("androidboot.slot_suffix=_a\n")?;

    // Boot into Android
    bootconfig_builder.add("androidboot.force_normal_boot=1\n")?;

    // V4 image has vendor bootconfig.
    if let VendorImageHeader::V4(ref hdr) = vendor_boot_header {
        let mut bootconfig_offset: usize = vendor_hdr_size;
        for image_size in
            [hdr._base.vendor_ramdisk_size, hdr._base.dtb_size, hdr.vendor_ramdisk_table_size]
        {
            bootconfig_offset =
                usize_add(bootconfig_offset, usize_roundup(image_size, hdr._base.page_size)?)?;
        }
        bootconfig_builder.add_with(|out| {
            gpt_devices
                .read_gpt_partition(
                    "vendor_boot_a",
                    bootconfig_offset.try_into().unwrap(),
                    &mut out[..hdr.bootconfig_size as usize],
                )
                .map_err(|_| BootConfigError::GenericReaderError(-1))?;
            Ok(hdr.bootconfig_size as usize)
        })?;
    }
    // Check if there is a device specific bootconfig partition.
    match gpt_devices.partition_size("bootconfig") {
        Ok(sz) => {
            bootconfig_builder.add_with(|out| {
                // For proof-of-concept only, we just load as much as possible and figure out the
                // actual bootconfig string length after. This however, can introduce large amount
                // of unnecessary disk access. In real implementation, we might want to either read
                // page by page or find way to know the actual length first.
                let max_size = core::cmp::min(sz, out.len());
                gpt_devices
                    .read_gpt_partition("bootconfig", 0, &mut out[..max_size])
                    .map_err(|_| BootConfigError::GenericReaderError(-1))?;
                // Compute the actual config string size. The config is a null-terminated string.
                Ok(CStr::from_bytes_until_nul(&out[..])
                    .map_err(|_| BootConfigError::GenericReaderError(-1))?
                    .to_bytes()
                    .len())
            })?;
        }
        _ => {}
    }
    efi_println!(efi_entry, "final bootconfig: \"{}\"", bootconfig_builder);
    ramdisk_load_curr = usize_add(ramdisk_load_curr, bootconfig_builder.config_bytes().len())?;

    // Prepare FDT.

    // For cuttlefish, FDT comes from EFI vendor configuration table installed by u-boot. In real
    // product, it may come from vendor boot image.
    let (_, fdt_bytes) = get_efi_fdt(&efi_entry).ok_or_else(|| EfiAppError::NoFdt)?;
    let fdt_origin = Fdt::new(fdt_bytes)?;

    // Use the remaining load buffer for updating FDT.
    let (ramdisk_load_buffer, load) = load.split_at_mut(ramdisk_load_curr);
    let load = aligned_subslice(load, FDT_ALIGNMENT)?;
    let mut fdt = Fdt::new_from_init(&mut load[..], fdt_bytes)?;

    // Add ramdisk range to FDT
    let ramdisk_addr: u64 = (ramdisk_load_buffer.as_ptr() as usize).try_into().unwrap();
    let ramdisk_end: u64 = ramdisk_addr + u64::try_from(ramdisk_load_buffer.len()).unwrap();
    fdt.set_property(
        "chosen",
        CStr::from_bytes_with_nul(b"linux,initrd-start\0").unwrap(),
        &ramdisk_addr.to_be_bytes(),
    )?;
    fdt.set_property(
        "chosen",
        CStr::from_bytes_with_nul(b"linux,initrd-end\0").unwrap(),
        &ramdisk_end.to_be_bytes(),
    )?;
    efi_println!(&efi_entry, "linux,initrd-start: {:#x}", ramdisk_addr);
    efi_println!(&efi_entry, "linux,initrd-end: {:#x}", ramdisk_end);

    // Concatenate kernel commandline and add it to FDT.
    let bootargs_prop = CStr::from_bytes_with_nul(b"bootargs\0").unwrap();
    let all_cmdline = [
        cstr_bytes_to_str(fdt_origin.get_property("chosen", bootargs_prop).unwrap_or(&[0]))?,
        " ",
        cstr_bytes_to_str(cmdline)?,
        " ",
        cstr_bytes_to_str(vendor_cmdline)?,
        "\0",
    ];
    let mut all_cmdline_len = 0;
    all_cmdline.iter().for_each(|v| all_cmdline_len += v.len());
    let cmdline_payload = fdt.set_property_placeholder("chosen", bootargs_prop, all_cmdline_len)?;
    let mut cmdline_payload_off: usize = 0;
    for ele in all_cmdline {
        cmdline_payload[cmdline_payload_off..][..ele.len()].clone_from_slice(ele.as_bytes());
        cmdline_payload_off += ele.len();
    }
    efi_println!(&efi_entry, "final cmdline: \"{}\"", from_utf8(cmdline_payload).unwrap());

    // Finalize FDT to actual used size.
    fdt.shrink_to_fit()?;

    // Move the kernel backward as much as possible to preserve more space after it. This is
    // necessary in case the input buffer is at the end of address space.
    let kernel_tail_buffer_size = kernel_tail_buffer.len();
    let ramdisk_load_buffer_size = ramdisk_load_buffer.len();
    let fdt_len = fdt.header_ref()?.actual_size();
    // Split out the ramdisk.
    let (ramdisk, remains) = images_buffer.split_at_mut(ramdisk_load_buffer_size);
    // Split out the fdt.
    let (fdt, kernel) = aligned_subslice(remains, FDT_ALIGNMENT)?.split_at_mut(fdt_len);
    // Move the kernel backward as much as possible.
    let kernel = aligned_subslice(kernel, KERNEL_ALIGNMENT)?;
    let kernel_start = kernel.len().checked_sub(kernel_tail_buffer_size).unwrap();
    kernel.copy_within(kernel_start..kernel_start.checked_add(kernel_size).unwrap(), 0);
    // Split out the remaining buffer.
    let (kernel, remains) = kernel.split_at_mut(kernel_size);

    Ok((ramdisk, fdt, kernel, remains))
}

// The following implements a demo for booting Android from disk. It can be run from
// Cuttlefish by adding `--android_efi_loader=<path of this EFI binary>` to the command line.
//
// A number of simplifications are made (see `android_load::load_android_simple()`):
//
//   * No A/B slot switching is performed. It always boot from *_a slot.
//   * No AVB is performed.
//   * No dynamic partitions.
//   * Only support V3/V4 image and Android 13+ (generic ramdisk from the "init_boot" partition)
//
// The missing pieces above are currently under development as part of the full end-to-end boot
// flow in libgbl, which will eventually replace this demo. The demo is currently used as an
// end-to-end test for libraries developed so far.
pub fn android_boot_demo(entry: EfiEntry) -> Result<()> {
    efi_println!(entry, "Try booting as Android");

    // Allocate buffer for load.
    let mut load_buffer = vec![0u8; 128 * 1024 * 1024]; // 128MB

    let (ramdisk, fdt, kernel, remains) = load_android_simple(&entry, &mut load_buffer[..])?;

    efi_println!(&entry, "");
    efi_println!(
        &entry,
        "Booting kernel @ {:#x}, ramdisk @ {:#x}, fdt @ {:#x}",
        kernel.as_ptr() as usize,
        ramdisk.as_ptr() as usize,
        fdt.as_ptr() as usize
    );
    efi_println!(&entry, "");

    #[cfg(target_arch = "aarch64")]
    {
        let _ = exit_boot_services(entry, remains)?;
        // SAFETY: We currently targets at Cuttlefish emulator where images are provided valid.
        unsafe { boot::aarch64::jump_linux_el2_or_lower(kernel, fdt) };
    }

    #[cfg(any(target_arch = "x86_64", target_arch = "x86"))]
    {
        let fdt = fdt::Fdt::new(&fdt[..])?;
        let efi_mmap = exit_boot_services(entry, remains)?;
        // SAFETY: We currently target at Cuttlefish emulator where images are provided valid.
        unsafe {
            boot::x86::boot_linux_bzimage(
                kernel,
                ramdisk,
                fdt.get_property(
                    "chosen",
                    core::ffi::CStr::from_bytes_with_nul(b"bootargs\0").unwrap(),
                )
                .unwrap(),
                |e820_entries| {
                    // Convert EFI memory type to e820 memory type.
                    if efi_mmap.len() > e820_entries.len() {
                        return Err(boot::BootError::E820MemoryMapCallbackError(-1));
                    }
                    for (idx, mem) in efi_mmap.into_iter().enumerate() {
                        e820_entries[idx] = boot::x86::e820entry {
                            addr: mem.physical_start,
                            size: mem.number_of_pages * 4096,
                            type_: crate::utils::efi_to_e820_mem_type(mem.memory_type),
                        };
                    }
                    Ok(efi_mmap.len().try_into().unwrap())
                },
                0x9_0000,
            )?;
        }
        unreachable!();
    }

    #[cfg(target_arch = "riscv64")]
    {
        let boot_hart_id = entry
            .system_table()
            .boot_services()
            .find_first_and_open::<efi::RiscvBootProtocol>()?
            .get_boot_hartid()?;
        efi_println!(entry, "riscv boot_hart_id: {}", boot_hart_id);
        let _ = exit_boot_services(entry, remains)?;
        // SAFETY: We currently target at Cuttlefish emulator where images are provided valid.
        unsafe { boot::riscv64::jump_linux(kernel, boot_hart_id, fdt) };
    }
}
