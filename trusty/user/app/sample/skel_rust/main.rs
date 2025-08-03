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

#![allow(unused)]

extern crate libc;
extern crate tipc;
use log::info;
use std::io::{stdout, Write};
use std::write;
use system_state::{SystemState, SystemStateFlag};
use trusty_sys::iovec;

fn main() {
    trusty_log::init();

    let message = b"Hello from Rust!\n";
    unsafe {
        libc::write(2, message.as_ptr().cast(), message.len());
    }

    let message2 = b"Hello from a Rust syscall!\n";
    let iov = iovec { iov_base: message2.as_ptr() as *mut _, iov_len: message2.len() };
    unsafe {
        let _ = trusty_sys::writev(2, &iov, 1);
    }

    write!(stdout(), "Hello from the Trusty Rust std!\n");

    info!("Hello from the log crate!");

    let state = SystemState::try_connect().expect("Could not connect to system state service");

    let provisioning_allowed = state
        .get_flag(SystemStateFlag::ProvisioningAllowed)
        .expect("Could not get provisioning flag");
    info!("Provisioning allowed: {}", provisioning_allowed);
}
