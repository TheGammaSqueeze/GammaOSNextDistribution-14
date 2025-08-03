// Copyright (C) 2022 The Android Open Source Project
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

use std::ffi::CString;
use std::os::unix::{net::UnixListener, prelude::FromRawFd};

use anyhow::{ensure, Result};

pub fn android_get_control_socket(name: &str) -> Result<UnixListener> {
    let name = CString::new(name)?;
    // SAFETY: name is a valid C string, and android_get_control_socket doesn't retain it after it
    // returns.
    let fd = unsafe { cutils_socket_bindgen::android_get_control_socket(name.as_ptr()) };
    ensure!(fd >= 0, "android_get_control_socket failed");
    // SAFETY: android_get_control_socket either returns a valid and open FD or -1, and we checked
    // that it's not -1.
    Ok(unsafe { UnixListener::from_raw_fd(fd) })
}
