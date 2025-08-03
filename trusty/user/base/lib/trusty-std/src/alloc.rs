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

// Some functions extracted from std include unsafe blocks inside an already
// unsafe function. Rather than changing these functions, we allow this
// behavior.
#![allow(unused_unsafe)]

use alloc::collections::TryReserveError;
use core::cmp;
use core::intrinsics;
use core::ptr::{self, NonNull};

#[doc(inline)]
pub use alloc::alloc::*;

#[doc(inline)]
pub use alloc::vec::Vec;

/// A value-to-value conversion that may fallibly allocate. The opposite of
/// [`TryAllocFrom`].
///
/// See [`core::convert::Into`] for details. This trait is equivalent, with the
/// exception that it will attempt to allocate fallibly and return `Err` if it
/// cannot.
///
/// We can't use [`core::convert::TryInto`] here, as that trait is default
/// implemented for any [`core::convert::Into`] implementation and we need to
/// explicitly require fallible allocation.
pub trait TryAllocInto<T: Sized> {
    /// Performs the conversion.
    fn try_alloc_into(self) -> Result<T, AllocError>;
}

impl<T, U> TryAllocInto<U> for T
where
    U: TryAllocFrom<T>,
{
    fn try_alloc_into(self) -> Result<U, AllocError> {
        U::try_alloc_from(self)
    }
}

/// A value-to-value conversion that may fallibly allocate. The opposite of
/// [`TryAllocInto`].
///
/// See [`core::convert::From`] for details. This trait is equivalent, with the
/// exception that it will attempt to allocate fallibly and return `Err` if it
/// cannot.
///
/// We can't use [`core::convert::TryFrom`] here, as that trait is default
/// implemented for any [`core::convert::Into`] implementation and we need to
/// explicitly require fallible allocation.
pub trait TryAllocFrom<T>: Sized {
    /// Performs the conversion.
    fn try_alloc_from(value: T) -> Result<Self, AllocError>;
}

impl TryAllocFrom<&str> for Vec<u8> {
    fn try_alloc_from(s: &str) -> Result<Self, AllocError> {
        let mut vec = Vec::new();
        vec.try_reserve_exact(s.len()).or(Err(AllocError))?;
        vec.extend_from_slice(s.as_bytes());
        Ok(vec)
    }
}

impl TryAllocFrom<&[u8]> for Vec<u8> {
    fn try_alloc_from(s: &[u8]) -> Result<Self, AllocError> {
        let mut vec = Vec::new();
        vec.try_reserve_exact(s.len()).or(Err(AllocError))?;
        vec.extend_from_slice(s);
        Ok(vec)
    }
}

/// Temporary trait to implement the future fallible API for [`Vec`].
// This should be removed when https://github.com/rust-lang/rust/pull/91559 or a
// similar change is available.
pub trait FallibleVec<T>: Sized {
    /// Tries to append `value` to the end of the vector, returning Err if it
    /// cannot allocate space for the expanded vector.
    fn try_push(&mut self, value: T) -> Result<(), TryReserveError>;

    /// Tries to construct a new, empty `Vec<T>` with the specified capacity.
    fn try_with_capacity(capacity: usize) -> Result<Self, TryReserveError>;
}

impl<T> FallibleVec<T> for Vec<T> {
    fn try_push(&mut self, value: T) -> Result<(), TryReserveError> {
        self.try_reserve(self.len() + 1)?;
        self.push(value);
        Ok(())
    }

    fn try_with_capacity(capacity: usize) -> Result<Self, TryReserveError> {
        let mut v = Vec::new();
        v.try_reserve(capacity)?;
        Ok(v)
    }
}

/*
 * We provide the implementation of std::alloc::System here so that we don't
 * need to maintain a separate allocator implementation.
 *
 * The rest of this file is derived from a combination of src/alloc.rs and
 * src/sys/unix/alloc.rs in the Rust standard library, used under the Apache
 * License, Version 2.0. The following is the original copyright information
 * from the Rust project:
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
 */
/// The default memory allocator provided by Trusty.
///
/// This allocator is extracted from the Rust std library for Unix and adapted
/// for use in Trusty. Internally it currently uses `malloc` from the musl libc.
///
/// This type implements the `GlobalAlloc` trait and Rust programs by default
/// work as if they had this definition:
///
/// ```rust
/// use std::alloc::System;
///
/// #[global_allocator]
/// static A: System = System;
///
/// fn main() {
///     let a = Box::new(4); // Allocates from the system allocator.
///     println!("{}", a);
/// }
/// ```
///
/// You can also define your own wrapper around `System` if you'd like, such as
/// keeping track of the number of all bytes allocated:
///
/// ```rust
/// use std::alloc::{System, GlobalAlloc, Layout};
/// use std::sync::atomic::{AtomicUsize, Ordering::SeqCst};
///
/// struct Counter;
///
/// static ALLOCATED: AtomicUsize = AtomicUsize::new(0);
///
/// unsafe impl GlobalAlloc for Counter {
///     unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
///         let ret = System.alloc(layout);
///         if !ret.is_null() {
///             ALLOCATED.fetch_add(layout.size(), SeqCst);
///         }
///         return ret
///     }
///
///     unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
///         System.dealloc(ptr, layout);
///         ALLOCATED.fetch_sub(layout.size(), SeqCst);
///     }
/// }
///
/// #[global_allocator]
/// static A: Counter = Counter;
///
/// fn main() {
///     println!("allocated bytes before main: {}", ALLOCATED.load(SeqCst));
/// }
/// ```
///
/// It can also be used directly to allocate memory independently of whatever
/// global allocator has been selected for a Rust program. For example if a Rust
/// program opts in to using jemalloc as the global allocator, `System` will
/// still allocate memory using `malloc` and `HeapAlloc`.
#[derive(Debug, Default, Copy, Clone)]
pub struct System;

#[global_allocator]
static A: System = System;

impl System {
    #[inline]
    fn alloc_impl(&self, layout: Layout, zeroed: bool) -> Result<NonNull<[u8]>, AllocError> {
        match layout.size() {
            0 => Ok(NonNull::slice_from_raw_parts(layout.dangling(), 0)),
            // SAFETY: `layout` is non-zero in size,
            size => unsafe {
                let raw_ptr = if zeroed {
                    GlobalAlloc::alloc_zeroed(self, layout)
                } else {
                    GlobalAlloc::alloc(self, layout)
                };
                let ptr = NonNull::new(raw_ptr).ok_or(AllocError)?;
                Ok(NonNull::slice_from_raw_parts(ptr, size))
            },
        }
    }

    // SAFETY: Same as `Allocator::grow`
    #[inline]
    unsafe fn grow_impl(
        &self,
        ptr: NonNull<u8>,
        old_layout: Layout,
        new_layout: Layout,
        zeroed: bool,
    ) -> Result<NonNull<[u8]>, AllocError> {
        debug_assert!(
            new_layout.size() >= old_layout.size(),
            "`new_layout.size()` must be greater than or equal to `old_layout.size()`"
        );

        match old_layout.size() {
            0 => self.alloc_impl(new_layout, zeroed),

            // SAFETY: `new_size` is non-zero as `new_size` is greater than or equal to `old_size`
            // as required by safety conditions and the `old_size == 0` case was handled in the
            // previous match arm. Other conditions must be upheld by the caller
            old_size if old_layout.align() == new_layout.align() => unsafe {
                let new_size = new_layout.size();

                // `realloc` probably checks for `new_size >= old_layout.size()` or something similar.
                intrinsics::assume(new_size >= old_layout.size());

                let raw_ptr = GlobalAlloc::realloc(self, ptr.as_ptr(), old_layout, new_size);
                let ptr = NonNull::new(raw_ptr).ok_or(AllocError)?;
                if zeroed {
                    raw_ptr.add(old_size).write_bytes(0, new_size - old_size);
                }
                Ok(NonNull::slice_from_raw_parts(ptr, new_size))
            },

            // SAFETY: because `new_layout.size()` must be greater than or equal to `old_size`,
            // both the old and new memory allocation are valid for reads and writes for `old_size`
            // bytes. Also, because the old allocation wasn't yet deallocated, it cannot overlap
            // `new_ptr`. Thus, the call to `copy_nonoverlapping` is safe. The safety contract
            // for `dealloc` must be upheld by the caller.
            old_size => unsafe {
                let new_ptr = self.alloc_impl(new_layout, zeroed)?;
                ptr::copy_nonoverlapping(ptr.as_ptr(), new_ptr.as_mut_ptr(), old_size);
                Allocator::deallocate(&self, ptr, old_layout);
                Ok(new_ptr)
            },
        }
    }
}

// The Allocator impl checks the layout size to be non-zero and forwards to the GlobalAlloc impl,
// which is in `std::sys::*::alloc`.
unsafe impl Allocator for System {
    #[inline]
    fn allocate(&self, layout: Layout) -> Result<NonNull<[u8]>, AllocError> {
        self.alloc_impl(layout, false)
    }

    #[inline]
    fn allocate_zeroed(&self, layout: Layout) -> Result<NonNull<[u8]>, AllocError> {
        self.alloc_impl(layout, true)
    }

    #[inline]
    unsafe fn deallocate(&self, ptr: NonNull<u8>, layout: Layout) {
        if layout.size() != 0 {
            // SAFETY: `layout` is non-zero in size,
            // other conditions must be upheld by the caller
            unsafe { GlobalAlloc::dealloc(self, ptr.as_ptr(), layout) }
        }
    }

    #[inline]
    unsafe fn grow(
        &self,
        ptr: NonNull<u8>,
        old_layout: Layout,
        new_layout: Layout,
    ) -> Result<NonNull<[u8]>, AllocError> {
        // SAFETY: all conditions must be upheld by the caller
        unsafe { self.grow_impl(ptr, old_layout, new_layout, false) }
    }

    #[inline]
    unsafe fn grow_zeroed(
        &self,
        ptr: NonNull<u8>,
        old_layout: Layout,
        new_layout: Layout,
    ) -> Result<NonNull<[u8]>, AllocError> {
        // SAFETY: all conditions must be upheld by the caller
        unsafe { self.grow_impl(ptr, old_layout, new_layout, true) }
    }

    #[inline]
    unsafe fn shrink(
        &self,
        ptr: NonNull<u8>,
        old_layout: Layout,
        new_layout: Layout,
    ) -> Result<NonNull<[u8]>, AllocError> {
        debug_assert!(
            new_layout.size() <= old_layout.size(),
            "`new_layout.size()` must be smaller than or equal to `old_layout.size()`"
        );

        match new_layout.size() {
            // SAFETY: conditions must be upheld by the caller
            0 => unsafe {
                Allocator::deallocate(&self, ptr, old_layout);
                Ok(NonNull::slice_from_raw_parts(new_layout.dangling(), 0))
            },

            // SAFETY: `new_size` is non-zero. Other conditions must be upheld by the caller
            new_size if old_layout.align() == new_layout.align() => unsafe {
                // `realloc` probably checks for `new_size <= old_layout.size()` or something similar.
                intrinsics::assume(new_size <= old_layout.size());

                let raw_ptr = GlobalAlloc::realloc(self, ptr.as_ptr(), old_layout, new_size);
                let ptr = NonNull::new(raw_ptr).ok_or(AllocError)?;
                Ok(NonNull::slice_from_raw_parts(ptr, new_size))
            },

            // SAFETY: because `new_size` must be smaller than or equal to `old_layout.size()`,
            // both the old and new memory allocation are valid for reads and writes for `new_size`
            // bytes. Also, because the old allocation wasn't yet deallocated, it cannot overlap
            // `new_ptr`. Thus, the call to `copy_nonoverlapping` is safe. The safety contract
            // for `dealloc` must be upheld by the caller.
            new_size => unsafe {
                let new_ptr = Allocator::allocate(&self, new_layout)?;
                ptr::copy_nonoverlapping(ptr.as_ptr(), new_ptr.as_mut_ptr(), new_size);
                Allocator::deallocate(&self, ptr, old_layout);
                Ok(new_ptr)
            },
        }
    }
}

#[cfg(any(target_arch = "x86", target_arch = "arm"))]
const MIN_ALIGN: usize = 8;
#[cfg(any(target_arch = "x86_64", target_arch = "aarch64"))]
const MIN_ALIGN: usize = 16;

unsafe fn realloc_fallback(
    alloc: &System,
    ptr: *mut u8,
    old_layout: Layout,
    new_size: usize,
) -> *mut u8 {
    // Docs for GlobalAlloc::realloc require this to be valid:
    let new_layout = Layout::from_size_align_unchecked(new_size, old_layout.align());

    let new_ptr = GlobalAlloc::alloc(alloc, new_layout);
    if !new_ptr.is_null() {
        let size = cmp::min(old_layout.size(), new_size);
        ptr::copy_nonoverlapping(ptr, new_ptr, size);
        GlobalAlloc::dealloc(alloc, ptr, old_layout);
    }
    new_ptr
}

unsafe impl GlobalAlloc for System {
    #[inline]
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        // jemalloc provides alignment less than MIN_ALIGN for small allocations.
        // So only rely on MIN_ALIGN if size >= align.
        // Also see <https://github.com/rust-lang/rust/issues/45955> and
        // <https://github.com/rust-lang/rust/issues/62251#issuecomment-507580914>.
        if layout.align() <= MIN_ALIGN && layout.align() <= layout.size() {
            libc::malloc(layout.size()) as *mut u8
        } else {
            #[cfg(target_os = "macos")]
            {
                if layout.align() > (1 << 31) {
                    return ptr::null_mut();
                }
            }
            libc::memalign(layout.align(), layout.size()) as *mut u8
        }
    }

    #[inline]
    unsafe fn alloc_zeroed(&self, layout: Layout) -> *mut u8 {
        // See the comment above in `alloc` for why this check looks the way it does.
        if layout.align() <= MIN_ALIGN && layout.align() <= layout.size() {
            libc::calloc(layout.size(), 1) as *mut u8
        } else {
            let ptr = self.alloc(layout);
            if !ptr.is_null() {
                ptr::write_bytes(ptr, 0, layout.size());
            }
            ptr
        }
    }

    #[inline]
    unsafe fn dealloc(&self, ptr: *mut u8, _layout: Layout) {
        libc::free(ptr as *mut libc::c_void)
    }

    #[inline]
    unsafe fn realloc(&self, ptr: *mut u8, layout: Layout, new_size: usize) -> *mut u8 {
        if layout.align() <= MIN_ALIGN && layout.align() <= new_size {
            libc::realloc(ptr as *mut libc::c_void, new_size) as *mut u8
        } else {
            realloc_fallback(self, ptr, layout, new_size)
        }
    }
}
