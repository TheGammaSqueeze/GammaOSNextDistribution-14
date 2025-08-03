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

//! # The Trusty Rust Standard Library
//!
//! Rust for Trusty requires `no_std`, as the Rust standard library has not been
//! (and will likely never be) ported to Trusty. This crate provides a subset of
//! the standard library types and other generally useful APIs for building
//! trusted apps.
//!
//! This library is designed to accommodate fallible memory allocation and
//! provides types which may only be allocated fallibly. When the necessary APIs
//! are available [upstream](https://github.com/rust-lang/rust/issues/86942) or
//! in this crate, we plan to enable `no_global_oom_handling`, so do not write
//! code using this crate that relies on infallible allocation.

#![no_std]
#![feature(allocator_api)]
#![feature(alloc_error_handler)]
#![feature(alloc_layout_extra)]
#![feature(core_intrinsics)]
// min_specialization is only used to optimize CString::try_new(), so we can
// remove it if needed
#![feature(min_specialization)]
#![feature(new_uninit)]
#![feature(panic_info_message)]
#![feature(slice_internals)]
#![feature(slice_ptr_get)]

// Import alloc with a different name to not clash with our local module
extern crate alloc as alloc_crate;

pub mod alloc;
mod clone_ext;
pub mod ffi;

pub use clone_ext::TryClone;

// Re-exports from core and alloc
pub use alloc_crate::{borrow, boxed, fmt, format, rc, slice, str, string, vec};

pub use core::{
    any, arch, array, cell, char, clone, cmp, convert, default, future, hash, hint, i128, i16, i32,
    i64, i8, intrinsics, isize, iter, marker, mem, ops, option, pin, primitive, ptr, result, u128,
    u16, u32, u64, u8, usize,
};

pub use core::{
    assert_eq, assert_ne, debug_assert, debug_assert_eq, debug_assert_ne, matches, todo,
    unimplemented, unreachable, write, writeln,
};

pub use core::{
    assert, cfg, column, compile_error, concat, env, file, format_args, include, include_bytes,
    include_str, line, module_path, option_env, stringify,
};
