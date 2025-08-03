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

use crate::defs::{EFI_MEMORY_TYPE_LOADER_DATA, EFI_STATUS_ALREADY_STARTED};
use crate::{EfiEntry, EfiResult};

use core::alloc::{GlobalAlloc, Layout};
use core::ptr::null_mut;

/// Implement a global allocator using `EFI_BOOT_SERVICES.AllocatePool()/FreePool()`
pub enum EfiAllocator {
    Uninitialized,
    Initialized(EfiEntry),
    Exited,
}

#[global_allocator]
static mut EFI_GLOBAL_ALLOCATOR: EfiAllocator = EfiAllocator::Uninitialized;

/// An internal API to obtain library internal global EfiEntry.
pub(crate) fn internal_efi_entry() -> Option<&'static EfiEntry> {
    // SAFETY:
    // For now, `EfiAllocator` is only modified in `init_efi_global_alloc()` when `EfiAllocator` is
    // being initialized or in `exit_efi_global_alloc` after `EFI_BOOT_SERVICES.
    // ExitBootServices()` is called, where there should be no event/notification function that can
    // be triggered. Therefore, it should be safe from race condition.
    unsafe { EFI_GLOBAL_ALLOCATOR.get_efi_entry() }
}

/// Initializes global allocator.
///
/// # Safety
///
/// This function modifies global variable `EFI_GLOBAL_ALLOCATOR`. It should only be called when
/// there is no event/notification function that can be triggered or modify it. Otherwise there
/// is a risk of race condition.
pub(crate) unsafe fn init_efi_global_alloc(efi_entry: EfiEntry) -> EfiResult<()> {
    // SAFETY: See SAFETY of `internal_efi_entry()`
    unsafe {
        match EFI_GLOBAL_ALLOCATOR {
            EfiAllocator::Uninitialized => {
                EFI_GLOBAL_ALLOCATOR = EfiAllocator::Initialized(efi_entry);
                Ok(())
            }
            _ => Err(EFI_STATUS_ALREADY_STARTED.into()),
        }
    }
}

/// Internal API to invalidate global allocator after ExitBootService().
///
/// # Safety
///
/// This function modifies global variable `EFI_GLOBAL_ALLOCATOR`. It should only be called when
/// there is no event/notification function that can be triggered or modify it. Otherwise there
/// is a risk of race condition.
pub(crate) unsafe fn exit_efi_global_alloc() {
    // SAFETY: See SAFETY of `internal_efi_entry()`
    unsafe {
        EFI_GLOBAL_ALLOCATOR = EfiAllocator::Exited;
    }
}

impl EfiAllocator {
    /// Returns a reference to the EfiEntry.
    fn get_efi_entry(&self) -> Option<&EfiEntry> {
        match self {
            EfiAllocator::Initialized(ref entry) => Some(entry),
            _ => None,
        }
    }

    /// Allocate memory via EFI_BOOT_SERVICES.
    fn allocate(&self, size: usize) -> *mut u8 {
        match self
            .get_efi_entry()
            .unwrap()
            .system_table()
            .boot_services()
            .allocate_pool(EFI_MEMORY_TYPE_LOADER_DATA, size)
        {
            Ok(p) => p as *mut _,
            _ => null_mut(),
        }
    }

    /// Deallocate memory previously allocated by `Self::allocate()`. Passing invalid pointer will
    /// cause the method to panic.
    fn deallocate(&self, ptr: *mut u8) {
        match self.get_efi_entry() {
            Some(ref entry) => {
                entry.system_table().boot_services().free_pool(ptr as *mut _).unwrap();
            }
            // After EFI_BOOT_SERVICES.ExitBootServices(), all allocated memory is considered
            // leaked and under full ownership of subsequent OS loader code.
            _ => {}
        }
    }
}

unsafe impl GlobalAlloc for EfiAllocator {
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        let size = layout.size();
        let align = layout.align();
        // TODO(300168989): `EFI_BOOT_SERVICES.AllocatePool()` is only 8-byte aligned. Add support
        // for arbitrary alignment.
        // `AllocatePool()` can be slow for allocating large buffers. In this case,
        // `AllocatePages()` is recommended.
        assert_eq!(8usize.checked_rem(align).unwrap(), 0);
        self.allocate(size)
    }

    unsafe fn dealloc(&self, ptr: *mut u8, _layout: Layout) {
        self.deallocate(ptr);
    }
}

/// API for allocating raw memory via EFI_BOOT_SERVICES
pub fn efi_malloc(size: usize) -> *mut u8 {
    // SAFETY: See SAFETY of `internal_efi_entry()`.
    unsafe { EFI_GLOBAL_ALLOCATOR.allocate(size) }
}

/// API for deallocating raw memory previously allocated by `efi_malloc()`. Passing invalid
/// pointer will cause the function to panic.
pub fn efi_free(ptr: *mut u8) {
    // SAFETY: See SAFETY of `internal_efi_entry()`.
    unsafe { EFI_GLOBAL_ALLOCATOR.deallocate(ptr) }
}
