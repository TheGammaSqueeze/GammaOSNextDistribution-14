/*
 * Copyright (C) 2023 The Android Open Source Project
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

//! A wrapper library to use fs-verity

mod sys;

use crate::sys::*;
use std::io;
use std::os::fd::AsRawFd;
use std::os::unix::io::BorrowedFd;

fn read_metadata(fd: i32, metadata_type: u64, offset: u64, buf: &mut [u8]) -> io::Result<usize> {
    let mut arg = fsverity_read_metadata_arg {
        metadata_type,
        offset,
        length: buf.len() as u64,
        buf_ptr: buf.as_mut_ptr() as u64,
        __reserved: 0,
    };
    // SAFETY: the ioctl doesn't change the sematics in the current process
    Ok(unsafe { read_verity_metadata(fd, &mut arg) }? as usize)
}

/// Read the raw Merkle tree from the fd, if it exists. The API semantics is similar to a regular
/// pread(2), and may not return full requested buffer.
pub fn read_merkle_tree(fd: i32, offset: u64, buf: &mut [u8]) -> io::Result<usize> {
    read_metadata(fd, FS_VERITY_METADATA_TYPE_MERKLE_TREE, offset, buf)
}

/// Read the fs-verity signature from the fd (if exists). The returned signature should be complete.
pub fn read_signature(fd: i32, buf: &mut [u8]) -> io::Result<usize> {
    read_metadata(fd, FS_VERITY_METADATA_TYPE_SIGNATURE, 0 /* offset */, buf)
}

/// Enable fs-verity to the `fd`, with sha256 hash algorithm and 4KB block size.
pub fn enable(fd: BorrowedFd) -> io::Result<()> {
    let arg = fsverity_enable_arg {
        version: 1,
        hash_algorithm: FS_VERITY_HASH_ALG_SHA256,
        block_size: 4096,
        salt_size: 0,
        salt_ptr: 0,
        sig_size: 0,
        __reserved1: 0,
        sig_ptr: 0,
        __reserved2: [0; 11],
    };
    // SAFETY: the ioctl doesn't change the sematics in the current process
    if unsafe { enable_verity(fd.as_raw_fd(), &arg) } == Ok(0) {
        Ok(())
    } else {
        Err(io::Error::last_os_error())
    }
}
