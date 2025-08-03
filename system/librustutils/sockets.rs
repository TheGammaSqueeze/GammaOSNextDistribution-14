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

//! Provides utilities for sockets.

use nix::errno::Errno;
use nix::fcntl::{fcntl, FdFlag, F_SETFD};
use std::ffi::CString;
use std::os::unix::io::RawFd;
use thiserror::Error;

/// Errors this crate can generate
#[derive(Error, Debug)]
pub enum SocketError {
    /// invalid name parameter
    #[error("socket name {0} contains NUL byte")]
    NulError(String),

    /// android_get_control_socket failed to get a fd
    #[error("android_get_control_socket({0}) failed")]
    GetControlSocketFailed(String),

    /// Failed to execute fcntl
    #[error("Failed to execute fcntl {0}")]
    FcntlFailed(Errno),
}

/// android_get_control_socket - simple helper function to get the file
/// descriptor of our init-managed Unix domain socket. `name' is the name of the
/// socket, as given in init.rc. Returns -1 on error.
/// The returned file descriptor has the flag CLOEXEC set.
pub fn android_get_control_socket(name: &str) -> Result<RawFd, SocketError> {
    let cstr = CString::new(name).map_err(|_| SocketError::NulError(name.to_owned()))?;
    // SAFETY: android_get_control_socket doesn't take ownership of name
    let fd = unsafe { cutils_bindgen::android_get_control_socket(cstr.as_ptr()) };
    if fd < 0 {
        return Err(SocketError::GetControlSocketFailed(name.to_owned()));
    }
    // The file descriptor had CLOEXEC disabled to be inherited from the parent.
    fcntl(fd, F_SETFD(FdFlag::FD_CLOEXEC)).map_err(SocketError::FcntlFailed)?;
    Ok(fd)
}
