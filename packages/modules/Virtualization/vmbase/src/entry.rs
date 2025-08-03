// Copyright 2022, The Android Open Source Project
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

//! Rust entry point.

use crate::{
    bionic, console, heap, logger,
    power::{reboot, shutdown},
    rand,
};
use core::mem::size_of;
use hyp::{self, get_mmio_guard};

fn try_console_init() -> Result<(), hyp::Error> {
    console::init();

    if let Some(mmio_guard) = get_mmio_guard() {
        mmio_guard.enroll()?;
        mmio_guard.validate_granule()?;
        mmio_guard.map(console::BASE_ADDRESS)?;
    }

    Ok(())
}

/// This is the entry point to the Rust code, called from the binary entry point in `entry.S`.
#[no_mangle]
extern "C" fn rust_entry(x0: u64, x1: u64, x2: u64, x3: u64) -> ! {
    // SAFETY: Only called once, from here, and inaccessible to client code.
    unsafe { heap::init() };

    if try_console_init().is_err() {
        // Don't panic (or log) here to avoid accessing the console.
        reboot()
    }

    logger::init().expect("Failed to initialize the logger");
    // We initialize the logger to Off (like the log crate) and clients should log::set_max_level.

    const SIZE_OF_STACK_GUARD: usize = size_of::<u64>();
    let mut stack_guard = [0u8; SIZE_OF_STACK_GUARD];
    // We keep a null byte at the top of the stack guard to act as a string terminator.
    let random_guard = &mut stack_guard[..(SIZE_OF_STACK_GUARD - 1)];

    if let Err(e) = rand::init() {
        panic!("Failed to initialize a source of entropy: {e}");
    }

    if let Err(e) = rand::fill_with_entropy(random_guard) {
        panic!("Failed to get stack canary entropy: {e}");
    }

    bionic::__get_tls().stack_guard = u64::from_ne_bytes(stack_guard);

    // Note: If rust_entry ever returned (which it shouldn't by being -> !), the compiler-injected
    // stack guard comparison would detect a mismatch and call __stack_chk_fail.

    // SAFETY: `main` is provided by the application using the `main!` macro, and we make sure it
    // has the right type.
    unsafe {
        main(x0, x1, x2, x3);
    }
    shutdown();
}

extern "Rust" {
    /// Main function provided by the application using the `main!` macro.
    fn main(arg0: u64, arg1: u64, arg2: u64, arg3: u64);
}

/// Marks the main function of the binary.
///
/// Once main is entered, it can assume that:
/// - The panic_handler has been configured and panic!() and friends are available;
/// - The global_allocator has been configured and heap memory is available;
/// - The logger has been configured and the log::{info, warn, error, ...} macros are available.
///
/// Example:
///
/// ```rust
/// use vmbase::main;
/// use log::{info, LevelFilter};
///
/// main!(my_main);
///
/// fn my_main() {
///     log::set_max_level(LevelFilter::Info);
///     info!("Hello world");
/// }
/// ```
#[macro_export]
macro_rules! main {
    ($name:path) => {
        // Export a symbol with a name matching the extern declaration above.
        #[export_name = "main"]
        fn __main(arg0: u64, arg1: u64, arg2: u64, arg3: u64) {
            // Ensure that the main function provided by the application has the correct type.
            $name(arg0, arg1, arg2, arg3)
        }
    };
}
