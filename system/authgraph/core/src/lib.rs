// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

//! Core Authgraph operations

#![no_std]
extern crate alloc;
use alloc::vec::Vec;

pub mod arc;
pub mod error;
pub mod key;
pub mod keyexchange;
pub mod ta;
pub mod traits;

/// Extension trait to provide fallible-allocation variants of `Vec` methods.
pub trait FallibleAllocExt<T> {
    /// Try to add the `value` to the collection, failing on memory exhaustion.
    fn try_push(&mut self, value: T) -> Result<(), alloc::collections::TryReserveError>;
    /// Try to extend the collection with the contents of `other`, failing on memory exhaustion.
    fn try_extend_from_slice(
        &mut self,
        other: &[T],
    ) -> Result<(), alloc::collections::TryReserveError>
    where
        T: Clone;
}

impl<T> FallibleAllocExt<T> for Vec<T> {
    fn try_push(&mut self, value: T) -> Result<(), alloc::collections::TryReserveError> {
        self.try_reserve(1)?;
        self.push(value);
        Ok(())
    }
    fn try_extend_from_slice(
        &mut self,
        other: &[T],
    ) -> Result<(), alloc::collections::TryReserveError>
    where
        T: Clone,
    {
        self.try_reserve(other.len())?;
        self.extend_from_slice(other);
        Ok(())
    }
}

/// Function that mimics `vec![<val>; <len>]` but which detects allocation failure with the given
/// error.
pub fn vec_try_fill_with_alloc_err<T: Clone>(
    elem: T,
    len: usize,
) -> Result<Vec<T>, alloc::collections::TryReserveError> {
    let mut v = alloc::vec::Vec::new();
    v.try_reserve(len)?;
    v.resize(len, elem);
    Ok(v)
}

/// Macro to allocate a `Vec<T>` with the given length reserved, detecting allocation failure.
#[macro_export]
macro_rules! vec_try_with_capacity {
    { $len:expr } => {
        {
            let mut v = alloc::vec::Vec::new();
            v.try_reserve($len).map(|_| v)
        }
    }
}

/// Macro that mimics `vec![value; size]` but which detects allocation failure.
#[macro_export]
macro_rules! vec_try {
    { $elem:expr ; $len:expr } => {
        $crate::vec_try_fill_with_alloc_err($elem, $len)
    };
}

/// Function that mimics `slice.to_vec()` but which detects allocation failures.
#[inline]
pub fn try_to_vec<T: Clone>(s: &[T]) -> Result<Vec<T>, alloc::collections::TryReserveError> {
    let mut v = vec_try_with_capacity!(s.len())?;
    v.extend_from_slice(s);
    Ok(v)
}
