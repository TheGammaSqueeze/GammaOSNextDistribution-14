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

//! Stable API definition copied from uapi/linux/fsverity.h

use nix::{ioctl_readwrite, ioctl_write_ptr};

const FS_IOCTL_MAGIC: u8 = b'f';
const FS_IOC_ENABLE_VERITY: u8 = 133;
const FS_IOCTL_READ_VERITY_METADATA: u8 = 135;

pub const FS_VERITY_HASH_ALG_SHA256: u32 = 1;
pub const FS_VERITY_METADATA_TYPE_MERKLE_TREE: u64 = 1;
pub const FS_VERITY_METADATA_TYPE_SIGNATURE: u64 = 3;

#[repr(C)]
pub struct fsverity_read_metadata_arg {
    pub metadata_type: u64,
    pub offset: u64,
    pub length: u64,
    pub buf_ptr: u64,
    pub __reserved: u64,
}

ioctl_readwrite!(
    read_verity_metadata,
    FS_IOCTL_MAGIC,
    FS_IOCTL_READ_VERITY_METADATA,
    fsverity_read_metadata_arg
);

#[repr(C)]
pub struct fsverity_enable_arg {
    pub version: u32,
    pub hash_algorithm: u32,
    pub block_size: u32,
    pub salt_size: u32,
    pub salt_ptr: u64,
    pub sig_size: u32,
    pub __reserved1: u32,
    pub sig_ptr: u64,
    pub __reserved2: [u64; 11],
}

ioctl_write_ptr!(enable_verity, FS_IOCTL_MAGIC, FS_IOC_ENABLE_VERITY, fsverity_enable_arg);
