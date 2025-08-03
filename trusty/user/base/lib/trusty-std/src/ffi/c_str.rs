/*
 * This file is derived from src/ffi/c_str.rs in the Rust standard library, used
 * under the Apache License, Version 2.0. The following is the original
 * copyright information from the Rust project:
 *
 * Copyrights in the Rust project are retained by their contributors. No
 * copyright assignment is required to contribute to the Rust project.
 *
 * Some files include explicit copyright notices and/or license notices.
 * For full authorship information, see the version control history or
 * https://thanks.rust-lang.org
 *
 * Except as otherwise noted (below and/or in individual files), Rust is
 * licensed under the Apache License, Version 2.0 <LICENSE-APACHE> or
 * <http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
 * <LICENSE-MIT> or <http://opensource.org/licenses/MIT>, at your option.
 *
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

//! Implementation of CString and CStr for use in Trusty.
//!
//! This module is a lightly modified version of `ffi/c_str.rs` from the Rust
//! std crate. `CString::new()` is replaced by the fallible allocating
//! [`CString::try_new()`] and other APIs which can allocate infallibly are
//! removed.

use crate::alloc::{AllocError, TryAllocInto};
use crate::TryClone;
use alloc::ffi::CString;
use alloc::vec::Vec;
use core::slice::memchr;

#[derive(PartialEq, Eq, Debug)]
pub enum TryNewError {
    /// An error indicating that an interior nul byte was found.
    ///
    /// While Rust strings may contain nul bytes in the middle, C strings
    /// can't, as that byte would effectively truncate the string.
    ///
    /// This error is created by the [`CString::try_new`] method.
    /// See its documentation for more.
    ///
    /// # Examples
    ///
    /// ```
    /// use std::ffi::{CString, NulError};
    ///
    /// let _: NulError = CString::new(b"f\0oo".to_vec()).unwrap_err();
    /// ```
    NulError(usize, Vec<u8>),

    AllocError,
}

impl From<AllocError> for TryNewError {
    fn from(_err: AllocError) -> Self {
        TryNewError::AllocError
    }
}

pub trait FallibleCString {
    /// Creates a new C-compatible string from a container of bytes.
    ///
    /// This function will consume the provided data and use the
    /// underlying bytes to construct a new string, ensuring that
    /// there is a trailing 0 byte. This trailing 0 byte will be
    /// appended by this function; the provided data should *not*
    /// contain any 0 bytes in it.
    ///
    /// # Examples
    ///
    /// ```ignore (extern-declaration)
    /// use std::ffi::CString;
    /// use std::os::raw::c_char;
    ///
    /// extern "C" { fn puts(s: *const c_char); }
    ///
    /// let to_print = CString::new("Hello!").expect("CString::new failed");
    /// unsafe {
    ///     puts(to_print.as_ptr());
    /// }
    /// ```
    ///
    /// # Errors
    ///
    /// This function will return an error if the supplied bytes contain an
    /// internal 0 byte. The [`TryNewError::NulError`] returned will contain the bytes as well as
    /// the position of the nul byte.
    fn try_new<T: TryAllocInto<Vec<u8>>>(t: T) -> Result<CString, TryNewError>;

    /// Creates a C-compatible string by consuming a byte vector,
    /// without checking for interior 0 bytes.
    ///
    /// This method is equivalent to [`CString::try_new`] except that no runtime
    /// assertion is made that `v` contains no 0 bytes, and it requires an
    /// actual byte vector, not anything that can be converted to one with Into.
    ///
    /// # Examples
    ///
    /// ```
    /// use std::ffi::CString;
    ///
    /// let raw = b"foo".to_vec();
    /// unsafe {
    ///     let c_string = CString::from_vec_unchecked(raw);
    /// }
    /// ```
    unsafe fn try_from_vec_unchecked(v: Vec<u8>) -> Result<CString, AllocError>;
}

impl FallibleCString for CString {
    fn try_new<T: TryAllocInto<Vec<u8>>>(t: T) -> Result<CString, TryNewError> {
        trait SpecIntoVec {
            fn into_vec(self) -> Result<Vec<u8>, AllocError>;
        }
        impl<T: TryAllocInto<Vec<u8>>> SpecIntoVec for T {
            default fn into_vec(self) -> Result<Vec<u8>, AllocError> {
                self.try_alloc_into()
            }
        }
        // Specialization for avoiding reallocation.
        impl SpecIntoVec for &'_ [u8] {
            fn into_vec(self) -> Result<Vec<u8>, AllocError> {
                let mut v = Vec::new();
                v.try_reserve_exact(self.len() + 1).or(Err(AllocError))?;
                v.extend_from_slice(self);
                Ok(v)
            }
        }
        impl SpecIntoVec for &'_ str {
            fn into_vec(self) -> Result<Vec<u8>, AllocError> {
                let mut v = Vec::new();
                v.try_reserve_exact(self.len() + 1).or(Err(AllocError))?;
                v.extend_from_slice(self.as_bytes());
                Ok(v)
            }
        }

        let bytes = SpecIntoVec::into_vec(t)?;
        match memchr::memchr(0, &bytes) {
            Some(i) => Err(TryNewError::NulError(i, bytes)),
            None => Ok(unsafe { CString::try_from_vec_unchecked(bytes)? }),
        }
    }

    unsafe fn try_from_vec_unchecked(mut v: Vec<u8>) -> Result<CString, AllocError> {
        v.try_reserve_exact(1).or(Err(AllocError))?;
        v.push(0);
        Ok(CString::from_vec_with_nul_unchecked(v))
    }
}

impl TryClone for CString {
    type Error = AllocError;

    fn try_clone(&self) -> Result<Self, Self::Error> {
        let inner = self.as_bytes_with_nul().try_alloc_into()?;

        // SAFETY: The `Vec` used here was cloned directly from an existing `CString`,
        // and so upholds the invariants required.
        Ok(unsafe { CString::from_vec_with_nul_unchecked(inner) })
    }
}
