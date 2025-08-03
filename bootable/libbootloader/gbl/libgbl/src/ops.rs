// Copyright 2023, The Android Open Source Project
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

//! GblOps trait that defines GBL callbacks.
//!
#[cfg(feature = "alloc")]
extern crate alloc;
#[cfg(test)]
extern crate static_assertions;

use crate::digest::{Algorithm, Context};
use crate::error::{Error, Result};
#[cfg(feature = "sw_digest")]
use crate::sw_digest::SwContext;
#[cfg(feature = "alloc")]
use alloc::ffi::CString;
use core::{fmt::Debug, ptr::NonNull};

use super::slots;

// https://stackoverflow.com/questions/41081240/idiomatic-callbacks-in-rust
// should we use traits for this? or optional/box FnMut?
//
/* TODO: b/312612203 - needed callbacks:
missing:
- validate_public_key_for_partition: None,
- key management => atx extension in callback =>  atx_ops: ptr::null_mut(), // support optional ATX.
*/
/// Trait that defines callbacks that can be provided to Gbl.
pub trait GblOps: Debug {
    /// Digest context type
    type Context: Context;

    /// Create digest object to use for hash computations.
    ///
    /// Context interface allows to update value adding more data to process.
    /// # Arguments
    ///
    /// * algorithm - algorithm to use for hash computation.
    fn new_digest(&self, algorithm: Algorithm) -> Self::Context {
        Context::new(algorithm)
    }

    /// Calculate digest of provided data with requested algorithm. Single use unlike [new_digest]
    /// flow.
    fn digest(&self, algorithm: Algorithm, data: &[u8]) -> <Self::Context as Context>::Digest {
        let mut ctx = self.new_digest(algorithm);
        ctx.update(data);
        ctx.finish()
    }

    /// Callback for when fastboot mode is requested.
    // Nevertype could be used here when it is stable https://github.com/serde-rs/serde/issues/812
    fn do_fastboot(&self, cursor: &slots::Cursor) -> Result<()> {
        Err(Error::NotImplemented)
    }

    /// TODO: b/312607649 - placeholder interface for Gbl specific callbacks that uses alloc.
    #[cfg(feature = "alloc")]
    fn gbl_alloc_extra_action(&mut self, s: &str) -> Result<()> {
        let _c_string = CString::new(s);
        Err(Error::Error)
    }

    /// Load and initialize a slot manager and return a cursor over the manager on success.
    fn load_slot_interface(&mut self, boot_token: slots::BootToken) -> Result<slots::Cursor> {
        Err(Error::OperationProhibited)
    }
}

/// Default [GblOps] implementation that returns errors and does nothing.
#[derive(Debug)]
pub struct DefaultGblOps {}

#[cfg(feature = "sw_digest")]
impl GblOps for DefaultGblOps {
    type Context = SwContext;
}

#[cfg(test)]
static_assertions::const_assert_eq!(core::mem::size_of::<DefaultGblOps>(), 0);
impl DefaultGblOps {
    /// Create new DefaultGblOps object
    pub fn new() -> &'static mut Self {
        let mut ptr: NonNull<Self> = NonNull::dangling();
        // SAFETY: Self is a ZST, asserted above, and ptr is appropriately aligned and nonzero by
        // NonNull::dangling()
        unsafe { ptr.as_mut() }
    }
}
