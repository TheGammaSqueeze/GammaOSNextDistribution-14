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

use core::arch::asm;

/// Boots a Linux kernel with the given boot hart ID and FDT blob.
///
/// # Safety
///
/// Caller must ensure that `kernel` contains a valid Linux kernel.
pub unsafe fn jump_linux(kernel: &[u8], boot_hart_id: usize, fdt: &[u8]) -> ! {
    // No official documentation exists yet. This is equivalent to a C function call taking
    // the hart ID and FDT address as parameters.
    // SAFETY: By safety requirement of this function, `kernel` contains a valid linux kernel.
    unsafe {
        asm!(
            "csrw satp, zero",
            "jr {ep}",
            in("a0") boot_hart_id,
            in("a1") fdt.as_ptr() as usize,
            ep = in(reg) kernel.as_ptr() as usize,
        );
    }

    unreachable!();
}
