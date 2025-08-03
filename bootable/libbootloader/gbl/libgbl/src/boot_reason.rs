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

//! Possible boot reasons.

use core::fmt::{Debug, Display, Formatter};

#[derive(Debug, PartialEq, Clone)]
/// Boot reasons that could be used in [BootMode]
pub enum KnownBootReason {
    // kernel
    /// Watchdog
    Watchdog,
    /// Kerner panic
    KernelPanic,
    // strong
    /// Recovery
    Recovery,
    /// Bootloader
    Bootloader,
    // blunt
    /// Generally indicates a full reset of all devices, including memory
    Cold,
    /// Generally indicates the hardware has its state reset and ramoops should retain persistent
    /// content
    Hard,
    /// Generally indicates the memory and the devices retain some state, and the ramoops (see
    /// pstore driver in kernel) backing store contains persistent content
    Warm,
    // super blunt
    /// Shutdown
    Shutdown,
    /// Generally means the ramoops state is unknown and the hardware state is unknown. This value
    /// is a catchall as the cold, hard, and warm values provide clues as to the depth of the reset
    /// for the device
    Reboot,
}

impl Display for KnownBootReason {
    fn fmt(&self, f: &mut Formatter<'_>) -> core::fmt::Result {
        let str = match self {
            // kernel
            KnownBootReason::Watchdog => "watchdog",
            KnownBootReason::KernelPanic => "kernel_panic",
            // strong
            KnownBootReason::Recovery => "recovery",
            KnownBootReason::Bootloader => "bootloader",
            // blunt
            KnownBootReason::Cold => "cold",
            KnownBootReason::Hard => "hard",
            KnownBootReason::Warm => "warm",
            // super blunt
            KnownBootReason::Shutdown => "shutdown",
            KnownBootReason::Reboot => "reboot",
        };
        write!(f, "{str}")
    }
}
