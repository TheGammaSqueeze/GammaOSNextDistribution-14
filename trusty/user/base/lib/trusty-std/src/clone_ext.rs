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

use alloc::alloc::{AllocError, Allocator};
use alloc::boxed::Box;
use alloc::vec::Vec;

/// Trait for fallible duplication of types that can be cloned.
///
/// See the [`Clone`] trait for more details. This trait is identical except
/// that duplication may fail, e.g. if the allocator could not allocate more
/// space for the result.
pub trait TryClone: Sized {
    /// Error type when the clone fails
    type Error;

    /// Attempt to duplicate the value.
    ///
    /// See [`Clone::clone()`]. This method may fail with `Self::Error`, which
    /// for a heap allocation, generally indicates that space for a duplicate
    /// value could not be allocated from the heap.
    ///
    /// # Examples
    ///
    /// ```
    /// let value = Box::new("Hello");
    ///
    /// let cloned_value = value.try_clone().expect("Box could not be cloned");
    /// ```
    fn try_clone(&self) -> Result<Self, Self::Error>;
}

impl<T: Clone, A: Allocator + Clone> TryClone for Box<T, A> {
    type Error = AllocError;

    #[inline]
    fn try_clone(&self) -> Result<Self, Self::Error> {
        let mut boxed = Self::try_new_uninit_in(Box::allocator(self).clone())?;
        unsafe {
            boxed.as_mut_ptr().write((**self).clone());
            Ok(boxed.assume_init())
        }
    }
}

#[inline]
fn try_to_vec<T: TryConvertVec, A: Allocator>(s: &[T], alloc: A) -> Result<Vec<T, A>, AllocError> {
    T::try_to_vec(s, alloc)
}

trait TryConvertVec {
    fn try_to_vec<A: Allocator>(s: &[Self], alloc: A) -> Result<Vec<Self, A>, AllocError>
    where
        Self: Sized;
}

impl<T: Clone> TryConvertVec for T {
    #[inline]
    default fn try_to_vec<A: Allocator>(s: &[Self], alloc: A) -> Result<Vec<Self, A>, AllocError> {
        struct DropGuard<'a, T, A: Allocator> {
            vec: &'a mut Vec<T, A>,
            num_init: usize,
        }
        impl<'a, T, A: Allocator> Drop for DropGuard<'a, T, A> {
            #[inline]
            fn drop(&mut self) {
                // SAFETY:
                // items were marked initialized in the loop below
                unsafe {
                    self.vec.set_len(self.num_init);
                }
            }
        }
        let mut vec = Vec::new_in(alloc);
        // TODO: replace with try_with_capacity_in when
        // https://github.com/rust-lang/rust/pull/86938 lands
        vec.try_reserve_exact(s.len()).or(Err(AllocError))?;
        let mut guard = DropGuard { vec: &mut vec, num_init: 0 };
        let slots = guard.vec.spare_capacity_mut();
        // .take(slots.len()) is necessary for LLVM to remove bounds checks
        // and has better codegen than zip.
        for (i, b) in s.iter().enumerate().take(slots.len()) {
            guard.num_init = i;
            slots[i].write(b.clone());
        }
        core::mem::forget(guard);
        // SAFETY:
        // the vec was allocated and initialized above to at least this length.
        unsafe {
            vec.set_len(s.len());
        }
        Ok(vec)
    }
}

#[cfg(not(no_global_oom_handling))]
impl<T: Copy> TryConvertVec for T {
    #[inline]
    fn try_to_vec<A: Allocator>(s: &[Self], alloc: A) -> Result<Vec<Self, A>, AllocError> {
        let mut v = Vec::new_in(alloc);
        // TODO: replace with try_with_capacity_in when
        // https://github.com/rust-lang/rust/pull/86938 lands
        v.try_reserve_exact(s.len()).or(Err(AllocError))?;
        // SAFETY:
        // allocated above with the capacity of `s`, and initialize to `s.len()` in
        // ptr::copy_to_non_overlapping below.
        unsafe {
            s.as_ptr().copy_to_nonoverlapping(v.as_mut_ptr(), s.len());
            v.set_len(s.len());
        }
        Ok(v)
    }
}

impl<T: Clone, A: Allocator + Clone> TryClone for Box<[T], A> {
    type Error = AllocError;

    #[inline]
    fn try_clone(&self) -> Result<Self, Self::Error> {
        let alloc = Box::allocator(self).clone();
        try_to_vec(&*self, alloc).map(Vec::into_boxed_slice)
    }
}
