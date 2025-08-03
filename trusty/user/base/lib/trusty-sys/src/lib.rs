/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! Trusty syscall wrappers.
//!
//! Provides an unsafe Rust interface to the Trusty OS syscalls without
//! requiring libc.

#![no_std]
#![allow(non_camel_case_types)]

mod err;
mod syscalls;
mod types;

mod sys {
    use crate as trusty_sys;

    include!(env!("BINDGEN_INC_FILE"));

    impl uevent {
        pub const ALL_EVENTS: u32 = u32::MAX;
    }
}

pub use err::*;
pub use syscalls::*;
pub use types::*;

pub const STDOUT_FILENO: u32 = 1;
pub const STDERR_FILENO: u32 = 2;

pub use sys::{
    dma_pmem, handle_t, iovec, ipc_msg, ipc_msg_info, uevent, uuid, IPC_CONNECT_ASYNC,
    IPC_CONNECT_WAIT_FOR_PORT, MMAP_FLAG_IO_HANDLE, MMAP_FLAG_PROT_READ, MMAP_FLAG_PROT_WRITE,
};
