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

//! User callback APIs.
//!
//! This module is responsible for bridging the user-implemented callbacks so that they can be
//! written in safe Rust but libavb can call them from C.

extern crate alloc;

use crate::{error::result_to_io_enum, IoError, IoResult};
use avb_bindgen::{AvbIOResult, AvbOps};
use core::{
    cmp::min,
    ffi::{c_char, c_void, CStr},
    marker::PhantomData,
    ptr, slice,
};
#[cfg(feature = "uuid")]
use uuid::Uuid;

/// Base implementation-provided callbacks for verification.
///
/// See libavb `AvbOps` for more complete documentation.
pub trait Ops {
    /// Reads data from the requested partition on disk.
    ///
    /// # Arguments
    /// * `partition`: partition name to read from.
    /// * `offset`: offset in bytes within the partition to read from; a positive value indicates an
    ///             offset from the partition start, a negative value indicates a backwards offset
    ///             from the partition end.
    /// * `buffer`: buffer to read data into.
    ///
    /// # Returns
    /// The number of bytes actually read into `buffer` or an `IoError`. Reading less than
    /// `buffer.len()` bytes is only allowed if the end of the partition was reached.
    fn read_from_partition(
        &mut self,
        partition: &CStr,
        offset: i64,
        buffer: &mut [u8],
    ) -> IoResult<usize>;

    /// Returns a reference to preloaded partition contents.
    ///
    /// This is an optional optimization if a partition has already been loaded to provide libavb
    /// with a reference to the data rather than copying it as `read_from_partition()` would.
    ///
    /// May be left unimplemented if preloaded partitions are not used.
    ///
    /// # Arguments
    /// * `partition`: partition name to read from.
    ///
    /// # Returns
    /// * A reference to the entire partition contents if the partition has been preloaded.
    /// * `Err<IoError::NotImplemented>` if the requested partition has not been preloaded;
    ///   verification will next attempt to load the partition via `read_from_partition()`.
    /// * Any other `Err<IoError>` if an error occurred; verification will exit immediately.
    fn get_preloaded_partition(&mut self, _partition: &CStr) -> IoResult<&[u8]> {
        Err(IoError::NotImplemented)
    }

    /// Checks if the given public key is valid for vbmeta image signing.
    ///
    /// # Arguments
    /// * `public_key`: the public key.
    /// * `public_key_metadata`: public key metadata set by the `--public_key_metadata` arg in
    ///                          `avbtool`, or None if no metadata was provided.
    ///
    /// # Returns
    /// True if the given key is valid, false if it is not, `IoError` on error.
    fn validate_vbmeta_public_key(
        &mut self,
        public_key: &[u8],
        public_key_metadata: Option<&[u8]>,
    ) -> IoResult<bool>;

    /// Reads the rollback index at the given location.
    ///
    /// # Arguments
    /// * `rollback_index_location`: the rollback location.
    ///
    /// # Returns
    /// The rollback index at this location or `IoError` on error.
    fn read_rollback_index(&mut self, rollback_index_location: usize) -> IoResult<u64>;

    /// Writes the rollback index at the given location.
    ///
    /// This API is never actually used by libavb; the purpose of having it here is to group it
    /// with `read_rollback_index()` and indicate to the implementation that it is responsible
    /// for providing this functionality. However, it's up to the implementation to call this
    /// function at the proper time after verification, which is a device-specific decision that
    /// depends on things like the A/B strategy. See the libavb documentation for more information.
    ///
    /// # Arguments
    /// * `rollback_index_location`: the rollback location.
    /// * `index`: the rollback index to write.
    ///
    /// # Returns
    /// Unit on success or `IoError` on error.
    fn write_rollback_index(&mut self, rollback_index_location: usize, index: u64) -> IoResult<()>;

    /// Returns the device unlock state.
    ///
    /// # Returns
    /// True if the device is unlocked, false if locked, `IoError` on error.
    fn read_is_device_unlocked(&mut self) -> IoResult<bool>;

    /// Returns the GUID of the requested partition.
    ///
    /// This is only necessary if the kernel commandline requires GUID substitution, and is omitted
    /// from the library by default to avoid unnecessary dependencies. To implement:
    /// 1. Enable the `uuid` feature during compilation
    /// 2. Provide the [`uuid` crate](https://docs.rs/uuid/latest/uuid/) dependency
    ///
    /// # Arguments
    /// * `partition`: partition name.
    ///
    /// # Returns
    /// The partition GUID or `IoError` on error.
    #[cfg(feature = "uuid")]
    fn get_unique_guid_for_partition(&mut self, partition: &CStr) -> IoResult<Uuid>;

    /// Returns the size of the requested partition.
    ///
    /// # Arguments
    /// * `partition`: partition name.
    ///
    /// # Returns
    /// The partition size in bytes or `IoError` on error.
    fn get_size_of_partition(&mut self, partition: &CStr) -> IoResult<u64>;

    /// Reads the requested persistent value.
    ///
    /// This is only necessary if using persistent digests or the "managed restart and EIO"
    /// hashtree verification mode; if verification is not using these features, this function will
    /// never be called.
    ///
    /// # Arguments
    /// * `name`: persistent value name.
    /// * `value`: buffer to read persistent value into; if too small to hold the persistent value,
    ///            `IoError::InsufficientSpace` should be returned and this function will be called
    ///            again with an appropriately-sized buffer. This may be an empty slice if the
    ///            caller only wants to query the persistent value size.
    ///
    /// # Returns
    /// * The number of bytes written into `value` on success.
    /// * `IoError::NoSuchValue` if `name` is not a known persistent value.
    /// * `IoError::InsufficientSpace` with the required size if the `value` buffer is too small.
    /// * Any other `IoError` on failure.
    fn read_persistent_value(&mut self, name: &CStr, value: &mut [u8]) -> IoResult<usize>;

    /// Writes the requested persistent value.
    ///
    /// This is only necessary if using persistent digests or the "managed restart and EIO"
    /// hashtree verification mode; if verification is not using these features, this function will
    /// never be called.
    ///
    /// # Arguments
    /// * `name`: persistent value name.
    /// * `value`: bytes to write as the new value.
    ///
    /// # Returns
    /// * Unit on success.
    /// * `IoError::NoSuchValue` if `name` is not a supported persistent value.
    /// * `IoError::InvalidValueSize` if `value` is too large to save as a persistent value.
    /// * Any other `IoError` on failure.
    fn write_persistent_value(&mut self, name: &CStr, value: &[u8]) -> IoResult<()>;

    /// Erases the requested persistent value.
    ///
    /// This is only necessary if using persistent digests or the "managed restart and EIO"
    /// hashtree verification mode; if verification is not using these features, this function will
    /// never be called.
    ///
    /// If the requested persistent value is already erased, this function is a no-op and should
    /// return `Ok(())`.
    ///
    /// # Arguments
    /// * `name`: persistent value name.
    ///
    /// # Returns
    /// * Unit on success.
    /// * `IoError::NoSuchValue` if `name` is not a supported persistent value.
    /// * Any other `IoError` on failure.
    fn erase_persistent_value(&mut self, name: &CStr) -> IoResult<()>;

    /// Checks if the given public key is valid for the given partition.
    ///
    /// This is only used if the "no vbmeta" verification flag is passed, meaning the partitions
    /// to verify have an embedded vbmeta image rather than locating it in a separate vbmeta
    /// partition. If this flag is not used, the `validate_vbmeta_public_key()` callback is used
    /// instead, and this function will never be called.
    ///
    /// # Arguments
    /// * `partition`: partition name.
    /// * `public_key`: the public key.
    /// * `public_key_metadata`: public key metadata set by the `--public_key_metadata` arg in
    ///                          `avbtool`, or None if no metadata was provided.
    ///
    /// # Returns
    /// On success, returns a `PublicKeyForPartitionInfo` object indicating whether the given
    /// key is trusted and its rollback index location.
    ///
    /// On failure, returns an error.
    fn validate_public_key_for_partition(
        &mut self,
        partition: &CStr,
        public_key: &[u8],
        public_key_metadata: Option<&[u8]>,
    ) -> IoResult<PublicKeyForPartitionInfo>;
}

/// Info returned from `validare_public_key_for_partition()`.
#[derive(Clone, Copy, Debug)]
pub struct PublicKeyForPartitionInfo {
    /// Whether the key is trusted for the given partition..
    pub trusted: bool,
    /// The rollback index to use for the given partition.
    pub rollback_index_location: u32,
}

/// Helper to pass user-provided `Ops` through libavb via the `user_data` pointer.
///
/// This is a bit tricky in Rust, we can't just cast `Ops` to `void*` and back directly because
/// `Ops` is a trait to be implemented by the user, which means we don't know the concrete type
/// at this point and must use dynamic dispatch.
///
/// However, dynamic dispatch in Rust requires a "fat pointer" (2 pointers) which cannot be cast to
/// a single `void*`. So instead, we wrap the dynamic dispatch inside this struct which _can_ be
/// represented as a single `void*`, and then we can unwrap it again to fetch the original
/// `&dyn Ops`.
///
/// A more typical approach is to use `Box` to heap-allocate the `&dyn` and then pass the `Box`
/// around, but we want to avoid allocation as much as possible.
///
/// Control flow:
/// ```
///         user                             libavb_rs                        libavb
/// -----------------------------------------------------------------------------------------------
/// create `Ops` (Rust) with
/// callback implementations
///                           ---->
///                                  `UserData::new()` wraps:
///                                  `Ops` (Rust/fat) ->
///                                  `UserData` (Rust/thin)
///
///                                  `ScopedAvbOps` makes
///                                  `AvbOps` (C) containing:
///                                  1. `UserData*` (C)
///                                  2. our callbacks (C)
///                                                            ---->
///                                                                   execute `AvbOps` (C)
///                                                                   callbacks as needed
///                                                            <----
///                                  `as_ops()` unwraps:
///                                  `AvbOps` (C) ->
///                                  `UserData` (Rust/thin) ->
///                                  `Ops` (Rust/fat)
///
///                                  Convert callback data to
///                                  safe Rust
///                           <----
/// perform `Ops` (Rust)
/// callback
/// ```
pub(crate) struct UserData<'a>(&'a mut dyn Ops);

impl<'a> UserData<'a> {
    pub(crate) fn new(ops: &'a mut dyn Ops) -> Self {
        Self(ops)
    }
}

/// Wraps the C `AvbOps` struct with lifetime information for the compiler.
pub(crate) struct ScopedAvbOps<'a> {
    /// `AvbOps` holds a raw pointer to `UserData` with no lifetime information.
    avb_ops: AvbOps,
    /// This provides the necessary lifetime information so the compiler can make sure that
    /// the `UserData` stays alive at least as long as we do.
    _user_data: PhantomData<UserData<'a>>,
}

impl<'a> ScopedAvbOps<'a> {
    pub(crate) fn new(user_data: &'a mut UserData<'a>) -> Self {
        Self {
            avb_ops: AvbOps {
                // Rust won't transitively cast so we need to cast twice manually, but the compiler
                // is smart enough to deduce the types we need.
                user_data: user_data as *mut _ as *mut _,
                ab_ops: ptr::null_mut(),  // Deprecated, no need to support.
                atx_ops: ptr::null_mut(), // TODO: support optional ATX.
                read_from_partition: Some(read_from_partition),
                get_preloaded_partition: Some(get_preloaded_partition),
                write_to_partition: None, // Not needed, only used for deprecated A/B.
                validate_vbmeta_public_key: Some(validate_vbmeta_public_key),
                read_rollback_index: Some(read_rollback_index),
                write_rollback_index: Some(write_rollback_index),
                read_is_device_unlocked: Some(read_is_device_unlocked),
                get_unique_guid_for_partition: Some(get_unique_guid_for_partition),
                get_size_of_partition: Some(get_size_of_partition),
                read_persistent_value: Some(read_persistent_value),
                write_persistent_value: Some(write_persistent_value),
                validate_public_key_for_partition: Some(validate_public_key_for_partition),
            },
            _user_data: PhantomData,
        }
    }
}

impl<'a> AsMut<AvbOps> for ScopedAvbOps<'a> {
    fn as_mut(&mut self) -> &mut AvbOps {
        &mut self.avb_ops
    }
}

/// Extracts the user-provided `Ops` from a raw `AvbOps`.
///
/// # Arguments
/// * `avb_ops`: The raw `AvbOps` pointer used by libavb.
///
/// # Returns
/// The Rust `Ops` extracted from `avb_ops.user_data`.
///
/// # Safety
/// Only call this function on an `AvbOps` created via `ScopedAvbOps`.
///
/// Additionally, this should be considered a mutable borrow of the contained `Ops`:
/// * do not return back to libavb while still holding the returned reference, or it will result
///   in a dangling reference
/// * do not call this again until the previous `Ops` goes out of scope, or it will violate Rust's
///   mutable borrowing rules
///
/// In practice, these conditions are met since we call this exactly once in each callback
/// to extract the `Ops`, and drop it at callback completion.
unsafe fn as_ops<'a>(avb_ops: *mut AvbOps) -> IoResult<&'a mut dyn Ops> {
    // SAFETY: we created this AvbOps object and passed it to libavb so we know it meets all
    // the criteria for `as_mut()`.
    let avb_ops = unsafe { avb_ops.as_mut() }.ok_or(IoError::Io)?;
    // Cast the void* `user_data` back to a UserData*.
    let user_data = avb_ops.user_data as *mut UserData;
    // SAFETY: we created this UserData object and passed it to libavb so we know it meets all
    // the criteria for `as_mut()`.
    Ok(unsafe { user_data.as_mut() }.ok_or(IoError::Io)?.0)
}

/// Converts a non-NULL `ptr` to `()`, NULL to `Err(IoError::Io)`.
fn check_nonnull<T>(ptr: *const T) -> IoResult<()> {
    match ptr.is_null() {
        true => Err(IoError::Io),
        false => Ok(()),
    }
}

/// Wraps a callback to convert the given `IoResult<>` to raw `AvbIOResult` for libavb.
///
/// See corresponding `try_*` function docs.
unsafe extern "C" fn read_from_partition(
    ops: *mut AvbOps,
    partition: *const c_char,
    offset: i64,
    num_bytes: usize,
    buffer: *mut c_void,
    out_num_read: *mut usize,
) -> AvbIOResult {
    // SAFETY: see corresponding `try_*` function safety documentation.
    unsafe {
        result_to_io_enum(try_read_from_partition(
            ops,
            partition,
            offset,
            num_bytes,
            buffer,
            out_num_read,
        ))
    }
}

/// Bounces the C callback into the user-provided Rust implementation.
///
/// # Safety
/// * `ops` must have been created via `ScopedAvbOps`.
/// * `partition` must adhere to the requirements of `CStr::from_ptr()`.
/// * `buffer` must adhere to the requirements of `slice::from_raw_parts_mut()`.
/// * `out_num_read` must adhere to the requirements of `ptr::write()`.
unsafe fn try_read_from_partition(
    ops: *mut AvbOps,
    partition: *const c_char,
    offset: i64,
    num_bytes: usize,
    buffer: *mut c_void,
    out_num_read: *mut usize,
) -> IoResult<()> {
    check_nonnull(partition)?;
    check_nonnull(buffer)?;
    check_nonnull(out_num_read)?;

    // Initialize the output variables first in case something fails.
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `out_num_read`.
    unsafe { ptr::write(out_num_read, 0) };

    // SAFETY:
    // * we only use `ops` objects created via `ScopedAvbOps` as required.
    // * `ops` is only extracted once and is dropped at the end of the callback.
    let ops = unsafe { as_ops(ops) }?;
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated and nul-terminated `partition`.
    // * the string contents are not modified while the returned `&CStr` exists.
    // * the returned `&CStr` is not held past the scope of this callback.
    let partition = unsafe { CStr::from_ptr(partition) };
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `buffer` with size `num_bytes`.
    // * we only access the contents via the returned slice.
    // * the returned slice is not held past the scope of this callback.
    let buffer = unsafe { slice::from_raw_parts_mut(buffer as *mut u8, num_bytes) };

    let bytes_read = ops.read_from_partition(partition, offset, buffer)?;
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `out_num_read`.
    unsafe { ptr::write(out_num_read, bytes_read) };
    Ok(())
}

/// Wraps a callback to convert the given `IoResult<>` to raw `AvbIOResult` for libavb.
///
/// See corresponding `try_*` function docs.
unsafe extern "C" fn get_preloaded_partition(
    ops: *mut AvbOps,
    partition: *const c_char,
    num_bytes: usize,
    out_pointer: *mut *mut u8,
    out_num_bytes_preloaded: *mut usize,
) -> AvbIOResult {
    // SAFETY: see corresponding `try_*` function safety documentation.
    unsafe {
        result_to_io_enum(try_get_preloaded_partition(
            ops,
            partition,
            num_bytes,
            out_pointer,
            out_num_bytes_preloaded,
        ))
    }
}

/// Bounces the C callback into the user-provided Rust implementation.
///
/// # Safety
/// * `ops` must have been created via `ScopedAvbOps`.
/// * `partition` must adhere to the requirements of `CStr::from_ptr()`.
/// * `out_pointer` and `out_num_bytes_preloaded` must adhere to the requirements of `ptr::write()`.
/// * `out_pointer` will become an alias to the `ops` preloaded partition data, so the preloaded
///   data must remain valid and unmodified while `out_pointer` exists.
unsafe fn try_get_preloaded_partition(
    ops: *mut AvbOps,
    partition: *const c_char,
    num_bytes: usize,
    out_pointer: *mut *mut u8,
    out_num_bytes_preloaded: *mut usize,
) -> IoResult<()> {
    check_nonnull(partition)?;
    check_nonnull(out_pointer)?;
    check_nonnull(out_num_bytes_preloaded)?;

    // Initialize the output variables first in case something fails.
    // SAFETY:
    // * we've checked that the pointers are non-NULL.
    // * libavb gives us properly-aligned and sized `out` vars.
    unsafe {
        ptr::write(out_pointer, ptr::null_mut());
        ptr::write(out_num_bytes_preloaded, 0);
    }

    // SAFETY:
    // * we only use `ops` objects created via `ScopedAvbOps` as required.
    // * `ops` is only extracted once and is dropped at the end of the callback.
    let ops = unsafe { as_ops(ops) }?;
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated and nul-terminated `partition`.
    // * the string contents are not modified while the returned `&CStr` exists.
    // * the returned `&CStr` is not held past the scope of this callback.
    let partition = unsafe { CStr::from_ptr(partition) };

    match ops.get_preloaded_partition(partition) {
        // SAFETY:
        // * we've checked that the pointers are non-NULL.
        // * libavb gives us properly-aligned and sized `out` vars.
        Ok(contents) => unsafe {
            ptr::write(
                out_pointer,
                // Warning: we are casting an immutable &[u8] to a mutable *u8. If libavb actually
                // modified these contents this could cause undefined behavior, but it just reads.
                // TODO: can we change the libavb API to take a const*?
                contents.as_ptr() as *mut u8,
            );
            ptr::write(
                out_num_bytes_preloaded,
                // Truncate here if necessary, we may have more preloaded data than libavb needs.
                min(contents.len(), num_bytes),
            );
        },
        // No-op if this partition is not preloaded, we've already reset the out variables to
        // indicate preloaded data is not available.
        Err(IoError::NotImplemented) => (),
        Err(e) => return Err(e),
    };
    Ok(())
}

/// Wraps a callback to convert the given `IoResult<>` to raw `AvbIOResult` for libavb.
///
/// See corresponding `try_*` function docs.
unsafe extern "C" fn validate_vbmeta_public_key(
    ops: *mut AvbOps,
    public_key_data: *const u8,
    public_key_length: usize,
    public_key_metadata: *const u8,
    public_key_metadata_length: usize,
    out_is_trusted: *mut bool,
) -> AvbIOResult {
    // SAFETY: see corresponding `try_*` function safety documentation.
    unsafe {
        result_to_io_enum(try_validate_vbmeta_public_key(
            ops,
            public_key_data,
            public_key_length,
            public_key_metadata,
            public_key_metadata_length,
            out_is_trusted,
        ))
    }
}

/// Bounces the C callback into the user-provided Rust implementation.
///
/// # Safety
/// * `ops` must have been created via `ScopedAvbOps`.
/// * `public_key_*` args must adhere to the requirements of `slice::from_raw_parts()`.
/// * `out_is_trusted` must adhere to the requirements of `ptr::write()`.
unsafe fn try_validate_vbmeta_public_key(
    ops: *mut AvbOps,
    public_key_data: *const u8,
    public_key_length: usize,
    public_key_metadata: *const u8,
    public_key_metadata_length: usize,
    out_is_trusted: *mut bool,
) -> IoResult<()> {
    check_nonnull(public_key_data)?;
    check_nonnull(out_is_trusted)?;

    // Initialize the output variables first in case something fails.
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `out_is_trusted`.
    unsafe { ptr::write(out_is_trusted, false) };

    // SAFETY:
    // * we only use `ops` objects created via `ScopedAvbOps` as required.
    // * `ops` is only extracted once and is dropped at the end of the callback.
    let ops = unsafe { as_ops(ops) }?;
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `public_key_data` with size `public_key_length`.
    // * we only access the contents via the returned slice.
    // * the returned slice is not held past the scope of this callback.
    let public_key = unsafe { slice::from_raw_parts(public_key_data, public_key_length) };
    let metadata = check_nonnull(public_key_metadata).ok().map(
        // SAFETY:
        // * we've checked that the pointer is non-NULL.
        // * libavb gives us a properly-allocated `public_key_metadata` with size
        //   `public_key_metadata_length`.
        // * we only access the contents via the returned slice.
        // * the returned slice is not held past the scope of this callback.
        |_| unsafe { slice::from_raw_parts(public_key_metadata, public_key_metadata_length) },
    );

    let trusted = ops.validate_vbmeta_public_key(public_key, metadata)?;

    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `out_is_trusted`.
    unsafe { ptr::write(out_is_trusted, trusted) };
    Ok(())
}

/// Wraps a callback to convert the given `IoResult<>` to raw `AvbIOResult` for libavb.
///
/// See corresponding `try_*` function docs.
unsafe extern "C" fn read_rollback_index(
    ops: *mut AvbOps,
    rollback_index_location: usize,
    out_rollback_index: *mut u64,
) -> AvbIOResult {
    // SAFETY: see corresponding `try_*` function safety documentation.
    unsafe {
        result_to_io_enum(try_read_rollback_index(
            ops,
            rollback_index_location,
            out_rollback_index,
        ))
    }
}

/// Bounces the C callback into the user-provided Rust implementation.
///
/// # Safety
/// * `ops` must have been created via `ScopedAvbOps`.
/// * `out_rollback_index` must adhere to the requirements of `ptr::write()`.
unsafe fn try_read_rollback_index(
    ops: *mut AvbOps,
    rollback_index_location: usize,
    out_rollback_index: *mut u64,
) -> IoResult<()> {
    check_nonnull(out_rollback_index)?;

    // Initialize the output variables first in case something fails.
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `out_rollback_index`.
    unsafe { ptr::write(out_rollback_index, 0) };

    // SAFETY:
    // * we only use `ops` objects created via `ScopedAvbOps` as required.
    // * `ops` is only extracted once and is dropped at the end of the callback.
    let ops = unsafe { as_ops(ops) }?;
    let index = ops.read_rollback_index(rollback_index_location)?;

    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `out_rollback_index`.
    unsafe { ptr::write(out_rollback_index, index) };
    Ok(())
}

/// Wraps a callback to convert the given `IoResult<>` to raw `AvbIOResult` for libavb.
///
/// See corresponding `try_*` function docs.
unsafe extern "C" fn write_rollback_index(
    ops: *mut AvbOps,
    rollback_index_location: usize,
    rollback_index: u64,
) -> AvbIOResult {
    // SAFETY: see corresponding `try_*` function safety documentation.
    unsafe {
        result_to_io_enum(try_write_rollback_index(
            ops,
            rollback_index_location,
            rollback_index,
        ))
    }
}

/// Bounces the C callback into the user-provided Rust implementation.
///
/// # Safety
/// * `ops` must have been created via `ScopedAvbOps`.
unsafe fn try_write_rollback_index(
    ops: *mut AvbOps,
    rollback_index_location: usize,
    rollback_index: u64,
) -> IoResult<()> {
    // SAFETY:
    // * we only use `ops` objects created via `ScopedAvbOps` as required.
    // * `ops` is only extracted once and is dropped at the end of the callback.
    let ops = unsafe { as_ops(ops) }?;
    ops.write_rollback_index(rollback_index_location, rollback_index)
}

/// Wraps a callback to convert the given `IoResult<>` to raw `AvbIOResult` for libavb.
///
/// See corresponding `try_*` function docs.
unsafe extern "C" fn read_is_device_unlocked(
    ops: *mut AvbOps,
    out_is_unlocked: *mut bool,
) -> AvbIOResult {
    // SAFETY: see corresponding `try_*` function safety documentation.
    unsafe { result_to_io_enum(try_read_is_device_unlocked(ops, out_is_unlocked)) }
}

/// Bounces the C callback into the user-provided Rust implementation.
///
/// # Safety
/// * `ops` must have been created via `ScopedAvbOps`.
/// * `out_is_unlocked` must adhere to the requirements of `ptr::write()`.
unsafe fn try_read_is_device_unlocked(
    ops: *mut AvbOps,
    out_is_unlocked: *mut bool,
) -> IoResult<()> {
    check_nonnull(out_is_unlocked)?;

    // Initialize the output variables first in case something fails.
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `out_is_unlocked`.
    unsafe { ptr::write(out_is_unlocked, false) };

    // SAFETY:
    // * we only use `ops` objects created via `ScopedAvbOps` as required.
    // * `ops` is only extracted once and is dropped at the end of the callback.
    let ops = unsafe { as_ops(ops) }?;
    let unlocked = ops.read_is_device_unlocked()?;

    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `out_is_unlocked`.
    unsafe { ptr::write(out_is_unlocked, unlocked) };
    Ok(())
}

/// Wraps a callback to convert the given `IoResult<>` to raw `AvbIOResult` for libavb.
///
/// See corresponding `try_*` function docs.
unsafe extern "C" fn get_unique_guid_for_partition(
    ops: *mut AvbOps,
    partition: *const c_char,
    guid_buf: *mut c_char,
    guid_buf_size: usize,
) -> AvbIOResult {
    // SAFETY: see corresponding `try_*` function safety documentation.
    unsafe {
        result_to_io_enum(try_get_unique_guid_for_partition(
            ops,
            partition,
            guid_buf,
            guid_buf_size,
        ))
    }
}

/// Bounces the C callback into the user-provided Rust implementation.
///
/// When the `uuid` feature is not enabled, this doesn't call into the user ops at all and instead
/// gives the empty string for all partitions.
///
/// # Safety
/// * `ops` must have been created via `ScopedAvbOps`.
/// * `partition` must adhere to the requirements of `CStr::from_ptr()`.
/// * `guid_buf` must adhere to the requirements of `slice::from_raw_parts_mut()`.
unsafe fn try_get_unique_guid_for_partition(
    #[allow(unused_variables)] ops: *mut AvbOps,
    #[allow(unused_variables)] partition: *const c_char,
    guid_buf: *mut c_char,
    guid_buf_size: usize,
) -> IoResult<()> {
    check_nonnull(guid_buf)?;

    // On some architectures `c_char` is `u8`, and on others `i8`. We make sure it's `u8` here
    // since that's what `CStr::to_bytes_with_nul()` always provides.
    #[allow(clippy::unnecessary_cast)]
    let guid_buf = guid_buf as *mut u8;

    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `guid_buf` with size `guid_buf_size`.
    // * we only access the contents via the returned slice.
    // * the returned slice is not held past the scope of this callback.
    let buffer = unsafe { slice::from_raw_parts_mut(guid_buf, guid_buf_size) };

    // Initialize the output buffer to the empty string.
    //
    // When the `uuid` feature is not selected, the user doesn't need commandline GUIDs but libavb
    // may still attempt to inject the `vmbeta` or `boot` partition GUIDs into the commandline,
    // depending on the verification settings. In order to satisfy libavb's requirements we must:
    // * write a nul-terminated string to avoid undefined behavior (empty string is sufficient)
    // * return `Ok(())` or verification will fail
    if buffer.is_empty() {
        return Err(IoError::Oom);
    }
    buffer[0] = b'\0';

    #[cfg(feature = "uuid")]
    {
        check_nonnull(partition)?;

        // SAFETY:
        // * we've checked that the pointer is non-NULL.
        // * libavb gives us a properly-allocated and nul-terminated `partition`.
        // * the string contents are not modified while the returned `&CStr` exists.
        // * the returned `&CStr` is not held past the scope of this callback.
        let partition = unsafe { CStr::from_ptr(partition) };

        // SAFETY:
        // * we only use `ops` objects created via `ScopedAvbOps` as required.
        // * `ops` is only extracted once and is dropped at the end of the callback.
        let ops = unsafe { as_ops(ops) }?;
        let guid = ops.get_unique_guid_for_partition(partition)?;

        // Write the UUID string to a uuid buffer which is guaranteed to be large enough, then use
        // `CString` to apply nul-termination.
        // This does allocate memory, but it's short-lived and discarded as soon as we copy the
        // properly-terminated string back to the buffer.
        let mut encode_buffer = Uuid::encode_buffer();
        let guid_str = guid.as_hyphenated().encode_lower(&mut encode_buffer);
        let guid_cstring = alloc::ffi::CString::new(guid_str.as_bytes()).or(Err(IoError::Io))?;
        let guid_bytes = guid_cstring.to_bytes_with_nul();

        if buffer.len() < guid_bytes.len() {
            // This would indicate some internal error - the uuid library needs more
            // space to print the UUID string than libavb provided.
            return Err(IoError::Oom);
        }
        buffer[..guid_bytes.len()].copy_from_slice(guid_bytes);
    }

    Ok(())
}

/// Wraps a callback to convert the given `IoResult<>` to raw `AvbIOResult` for libavb.
///
/// See corresponding `try_*` function docs.
unsafe extern "C" fn get_size_of_partition(
    ops: *mut AvbOps,
    partition: *const c_char,
    out_size_num_bytes: *mut u64,
) -> AvbIOResult {
    // SAFETY: see corresponding `try_*` function safety documentation.
    unsafe {
        result_to_io_enum(try_get_size_of_partition(
            ops,
            partition,
            out_size_num_bytes,
        ))
    }
}

/// Bounces the C callback into the user-provided Rust implementation.
///
/// # Safety
/// * `ops` must have been created via `ScopedAvbOps`.
/// * `partition` must adhere to the requirements of `CStr::from_ptr()`.
/// * `out_size_num_bytes` must adhere to the requirements of `ptr::write()`.
unsafe fn try_get_size_of_partition(
    ops: *mut AvbOps,
    partition: *const c_char,
    out_size_num_bytes: *mut u64,
) -> IoResult<()> {
    check_nonnull(partition)?;
    check_nonnull(out_size_num_bytes)?;

    // Initialize the output variables first in case something fails.
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `out_size_num_bytes`.
    unsafe { ptr::write(out_size_num_bytes, 0) };

    // SAFETY:
    // * we only use `ops` objects created via `ScopedAvbOps` as required.
    // * `ops` is only extracted once and is dropped at the end of the callback.
    let ops = unsafe { as_ops(ops) }?;
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated and nul-terminated `partition`.
    // * the string contents are not modified while the returned `&CStr` exists.
    // * the returned `&CStr` is not held past the scope of this callback.
    let partition = unsafe { CStr::from_ptr(partition) };
    let size = ops.get_size_of_partition(partition)?;

    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `out_size_num_bytes`.
    unsafe { ptr::write(out_size_num_bytes, size) };
    Ok(())
}

/// Wraps a callback to convert the given `IoResult<>` to raw `AvbIOResult` for libavb.
///
/// See corresponding `try_*` function docs.
unsafe extern "C" fn read_persistent_value(
    ops: *mut AvbOps,
    name: *const c_char,
    buffer_size: usize,
    out_buffer: *mut u8,
    out_num_bytes_read: *mut usize,
) -> AvbIOResult {
    // SAFETY: see corresponding `try_*` function safety documentation.
    unsafe {
        result_to_io_enum(try_read_persistent_value(
            ops,
            name,
            buffer_size,
            out_buffer,
            out_num_bytes_read,
        ))
    }
}

/// Bounces the C callback into the user-provided Rust implementation.
///
/// # Safety
/// * `ops` must have been created via `ScopedAvbOps`.
/// * `name` must adhere to the requirements of `CStr::from_ptr()`.
/// * `out_buffer` must adhere to the requirements of `slice::from_raw_parts_mut()`.
/// * `out_num_bytes_read` must adhere to the requirements of `ptr::write()`.
unsafe fn try_read_persistent_value(
    ops: *mut AvbOps,
    name: *const c_char,
    buffer_size: usize,
    out_buffer: *mut u8,
    out_num_bytes_read: *mut usize,
) -> IoResult<()> {
    check_nonnull(name)?;
    check_nonnull(out_num_bytes_read)?;

    // Initialize the output variables first in case something fails.
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `out_num_bytes_read`.
    unsafe { ptr::write(out_num_bytes_read, 0) };

    // SAFETY:
    // * we only use `ops` objects created via `ScopedAvbOps` as required.
    // * `ops` is only extracted once and is dropped at the end of the callback.
    let ops = unsafe { as_ops(ops) }?;
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated and nul-terminated `name`.
    // * the string contents are not modified while the returned `&CStr` exists.
    // * the returned `&CStr` is not held past the scope of this callback.
    let name = unsafe { CStr::from_ptr(name) };
    let mut empty: [u8; 0] = [];
    let value = match out_buffer.is_null() {
        // NULL buffer => empty slice, used to just query the value size.
        true => &mut empty,
        false => {
            // SAFETY:
            // * we've checked that the pointer is non-NULL.
            // * libavb gives us a properly-allocated `out_buffer` with size `buffer_size`.
            // * we only access the contents via the returned slice.
            // * the returned slice is not held past the scope of this callback.
            unsafe { slice::from_raw_parts_mut(out_buffer, buffer_size) }
        }
    };

    let result = ops.read_persistent_value(name, value);
    // On success or insufficient space we need to write the property size back.
    if let Ok(size) | Err(IoError::InsufficientSpace(size)) = result {
        // SAFETY:
        // * we've checked that the pointer is non-NULL.
        // * libavb gives us a properly-allocated `out_num_bytes_read`.
        unsafe { ptr::write(out_num_bytes_read, size) };
    };
    // We've written the size back and can drop it now.
    result.map(|_| ())
}

/// Wraps a callback to convert the given `IoResult<>` to raw `AvbIOResult` for libavb.
///
/// See corresponding `try_*` function docs.
unsafe extern "C" fn write_persistent_value(
    ops: *mut AvbOps,
    name: *const c_char,
    value_size: usize,
    value: *const u8,
) -> AvbIOResult {
    // SAFETY: see corresponding `try_*` function safety documentation.
    unsafe { result_to_io_enum(try_write_persistent_value(ops, name, value_size, value)) }
}

/// Bounces the C callback into the user-provided Rust implementation.
///
/// # Safety
/// * `ops` must have been created via `ScopedAvbOps`.
/// * `name` must adhere to the requirements of `CStr::from_ptr()`.
/// * `out_buffer` must adhere to the requirements of `slice::from_raw_parts()`.
unsafe fn try_write_persistent_value(
    ops: *mut AvbOps,
    name: *const c_char,
    value_size: usize,
    value: *const u8,
) -> IoResult<()> {
    check_nonnull(name)?;

    // SAFETY:
    // * we only use `ops` objects created via `ScopedAvbOps` as required.
    // * `ops` is only extracted once and is dropped at the end of the callback.
    let ops = unsafe { as_ops(ops) }?;
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated and nul-terminated `name`.
    // * the string contents are not modified while the returned `&CStr` exists.
    // * the returned `&CStr` is not held past the scope of this callback.
    let name = unsafe { CStr::from_ptr(name) };

    if value_size == 0 {
        ops.erase_persistent_value(name)
    } else {
        check_nonnull(value)?;
        // SAFETY:
        // * we've checked that the pointer is non-NULL.
        // * libavb gives us a properly-allocated `value` with size `value_size`.
        // * we only access the contents via the returned slice.
        // * the returned slice is not held past the scope of this callback.
        let value = unsafe { slice::from_raw_parts(value, value_size) };
        ops.write_persistent_value(name, value)
    }
}

/// Wraps a callback to convert the given `IoResult<>` to raw `AvbIOResult` for libavb.
///
/// See corresponding `try_*` function docs.
unsafe extern "C" fn validate_public_key_for_partition(
    ops: *mut AvbOps,
    partition: *const c_char,
    public_key_data: *const u8,
    public_key_length: usize,
    public_key_metadata: *const u8,
    public_key_metadata_length: usize,
    out_is_trusted: *mut bool,
    out_rollback_index_location: *mut u32,
) -> AvbIOResult {
    // SAFETY: see corresponding `try_*` function safety documentation.
    unsafe {
        result_to_io_enum(try_validate_public_key_for_partition(
            ops,
            partition,
            public_key_data,
            public_key_length,
            public_key_metadata,
            public_key_metadata_length,
            out_is_trusted,
            out_rollback_index_location,
        ))
    }
}

/// Bounces the C callback into the user-provided Rust implementation.
///
/// # Safety
/// * `ops` must have been created via `ScopedAvbOps`.
/// * `partition` must adhere to the requirements of `CStr::from_ptr()`.
/// * `public_key_*` args must adhere to the requirements of `slice::from_raw_parts()`.
/// * `out_*` must adhere to the requirements of `ptr::write()`.
#[allow(clippy::too_many_arguments)] // Mirroring libavb C API.
unsafe fn try_validate_public_key_for_partition(
    ops: *mut AvbOps,
    partition: *const c_char,
    public_key_data: *const u8,
    public_key_length: usize,
    public_key_metadata: *const u8,
    public_key_metadata_length: usize,
    out_is_trusted: *mut bool,
    out_rollback_index_location: *mut u32,
) -> IoResult<()> {
    check_nonnull(partition)?;
    check_nonnull(public_key_data)?;
    check_nonnull(out_is_trusted)?;
    check_nonnull(out_rollback_index_location)?;

    // Initialize the output variables first in case something fails.
    // SAFETY:
    // * we've checked that the pointers are non-NULL.
    // * libavb gives us a properly-allocated `out_*`.
    unsafe {
        ptr::write(out_is_trusted, false);
        ptr::write(out_rollback_index_location, 0);
    }

    // SAFETY:
    // * we only use `ops` objects created via `ScopedAvbOps` as required.
    // * `ops` is only extracted once and is dropped at the end of the callback.
    let ops = unsafe { as_ops(ops) }?;
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated and nul-terminated `partition`.
    // * the string contents are not modified while the returned `&CStr` exists.
    // * the returned `&CStr` is not held past the scope of this callback.
    let partition = unsafe { CStr::from_ptr(partition) };
    // SAFETY:
    // * we've checked that the pointer is non-NULL.
    // * libavb gives us a properly-allocated `public_key_data` with size `public_key_length`.
    // * we only access the contents via the returned slice.
    // * the returned slice is not held past the scope of this callback.
    let public_key = unsafe { slice::from_raw_parts(public_key_data, public_key_length) };
    let metadata = check_nonnull(public_key_metadata).ok().map(
        // SAFETY:
        // * we've checked that the pointer is non-NULL.
        // * libavb gives us a properly-allocated `public_key_metadata` with size
        //   `public_key_metadata_length`.
        // * we only access the contents via the returned slice.
        // * the returned slice is not held past the scope of this callback.
        |_| unsafe { slice::from_raw_parts(public_key_metadata, public_key_metadata_length) },
    );

    let key_info = ops.validate_public_key_for_partition(partition, public_key, metadata)?;

    // SAFETY:
    // * we've checked that the pointers are non-NULL.
    // * libavb gives us a properly-allocated `out_*`.
    unsafe {
        ptr::write(out_is_trusted, key_info.trusted);
        ptr::write(
            out_rollback_index_location,
            key_info.rollback_index_location,
        );
    }
    Ok(())
}
