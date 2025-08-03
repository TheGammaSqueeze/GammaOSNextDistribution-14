/*
 * Copyright (C) 2022 The Android Open Source Project
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

// Some targets may not have any tests enabled
#![allow(unused)]

use super::*;
use ::test::assert;
use core::ffi::CStr;
use tipc::{Handle, TipcError};

::test::init!();

fn open_hwwsk_session() -> Result<Handle, TipcError> {
    let port = CStr::from_bytes_with_nul(HWWSK_PORT).expect("HWKEY_PORT was not null terminated");
    Handle::connect(port)
}

const KEY_SIZE: usize = 32;

#[cfg(feature = "generic-arm-unittest")]
#[test]
fn test_hwwsk_generate_key() {
    let session = open_hwwsk_session().expect("could not open hwkey session");

    let buf = &mut [0u8; HWWSK_MAX_MSG_SIZE as usize];

    let key_res = generate_key(&session, buf, KEY_SIZE, KeyFlags::new().rollback_resistance());

    assert!(key_res.is_ok());
}

#[cfg(feature = "generic-arm-unittest")]
#[test]
fn test_hwwsk_import_key() {
    let session = open_hwwsk_session().expect("could not open hwkey session");

    let buf = &mut [0u8; HWWSK_MAX_MSG_SIZE as usize];

    let key_res =
        import_key(&session, buf, KEY_SIZE, KeyFlags::new().rollback_resistance(), &[0; KEY_SIZE]);

    assert!(key_res.is_ok());
}
