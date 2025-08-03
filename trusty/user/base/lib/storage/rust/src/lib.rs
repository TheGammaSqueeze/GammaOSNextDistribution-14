/*
 * Copyright (C) 2022 The Android Open Source Project
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

//! Access to the Trusty storage service.
//!
//! The secure storage service provides encrypted and tamper proof storage to
//! secure apps. All operations that modify the file system state are
//! transactional. Files can be opened, create or deleted by name (where the
//! name is local to the app). Open files support read, write, get-size and
//! set-size operations. There is currently no support for sparse files,
//! permissions, quotas or directory operations.
//!
//! Here's a quick example of how to start a session with the storage service
//! and perform basic filesystem operations:
//!
//!```
//! use storage::{Session, Port, OpenMode};
//!
//! let session = Session::new(Port::TamperDetect)?;
//!
//! // Write file contents to disk, creating a new
//! // file or overwriting an existing one.
//! session.write(
//!     "foo.txt",
//!     "Hello, world!".as_bytes()
//! )?;
//!
//! // Read contents of a file.
//! let contents = session.read("foo.txt")?;
//! println!("Contents of `bar.txt`: {}", contents);
//!
//! // Open a file handle in order to perform more
//! // advanced operations like setting the size.
//! let file = session.open_file(
//!     "foo.txt",
//!     OpenMode::Open,
//! )?;
//!
//! session.set_size(&file, 1024)?;
//! ```
//!
//! # Getting Started
//!
//! To interact with the storage service you first need to start a session. The
//! storage service listens for connections on multiple different ports, and
//! provides different filesystem properties based on which port you connect to.
//! See [`Port`] for the available ports and their properties, though
//! [`TamperDetect`][Port::TamperDetect] should be your default if you do not
//! specifically need the properties provided by other ports.
//!
//! ```
//! use storage::{Session, Port};
//!
//! let session = Session::new(Port::TamperDetect)?;
//! ```
//!
//! All filesystem operations are performed through the [`Session`] object, see
//! the methods on [`Session`] for a list of operations. All filesystem
//! operations return a `Result`. If no specific error conditions are documented
//! for an operation, general errors may still still occur e.g. if there is an
//! issue communicating with the service.
//!
//! # Using Transactions
//!
//! It's possible to group multiple file operations into a transaction in order
//! to commit or discard all changes as a group. To do so, use
//! [`Session::begin_transaction`] to get a [`Transaction`] object.
//! [`Transaction`] exposes the same file manipulation methods as [`Session`],
//! but any changes done through [`Transaction`] will not be applied
//! immediately. Instead, [`Transaction::commit`] and [`Transaction::discard`]
//! are used to either commit the pending changes to disk or discard the pending
//! changes, respectively.
//!
//! If a [`Transaction`] object is dropped without being explicitly committed or
//! discarded, it will panic with a message reminding you to manually finalize
//! the transaction.
//!
//! ```
//! let transaction = session.begin_transaction();
//!
//! let file = transaction.open_file("foo.txt", OpenMode::Create)?;
//! transaction.write_all(&file, "Hello, world!".as_bytes())?;
//!
//! transaction.commit()?;
//! ```
//!
//! The main reason to use transactions instead of the simpler API provided by
//! [`Session`] is that it allows multiple file operations to be committed
//! atomically in a single transaction. This can be important for apps that need
//! to ensure that files in secure storage do not end up in an invalid state. In
//! the future it may also allow for more efficient disk operations by allowing
//! the storage service to write changes in bulk, but that is not currently
//! implemented.
//!
//! For simple use cases that only need to read or write an entire file the
//! simpler [`Session`] file access APIs can be good enough, but for more
//! complex use cases use the transaction API instead.
//!
//! The storage API is also designed such that while a transaction is open all
//! file operations must go through that transaction until it is either
//! committed or discarded. This ensures that there's no way to accidentally
//! perform a file operation through `Session` that circumvents the atomicity
//! guarantees provided by the transaction.
//!
//! # Listing Files
//!
//! You can use [`Session::list_files`] and [`Transaction::list_files`] to list
//! the files in the current session's storage. Note that the storage service
//! does not currently support directories, and so it always lists the files at
//! the root directory. The iterator returned by these methods yields the name
//! of the file and its current state, i.e. if the file is fully committed to
//! disk or if it is being added/removed in the current transaction.
//!
//! ```
//! for entry in session.list_files().unwrap() {
//!     let (name, state) = entry.unwrap();
//!     println!("File {} is in state {:?}", name, state);
//! }
//! ```

#![feature(allocator_api)]

pub use crate::transaction::*;
pub use trusty_sys::Error as ErrorCode;

use core::alloc::AllocError;
use core::ffi::c_void;
use core::mem::MaybeUninit;
use core::ptr;
use trusty_std::alloc::TryAllocFrom;
use trusty_std::ffi::{CString, FallibleCString, TryNewError};
use trusty_std::string::{FromUtf8Error, String};
use trusty_std::vec::Vec;
use trusty_sys::c_long;

#[cfg(test)]
mod test;
mod transaction;

#[allow(bad_style)]
#[allow(unused)]
// See: https://github.com/rust-lang/rust-bindgen/issues/1651
#[allow(deref_nullptr)]
mod sys {
    #[cfg(test)]
    use test::assert_eq;

    include!(env!("BINDGEN_INC_FILE"));
}

/// An active connection to the storage service.
///
/// The `Session` object manages the active connection to the storage service,
/// and is used to communicate with the service. The connection is automatically
/// closed when the `Session` object is dropped.
#[derive(Debug, PartialEq, Eq)]
pub struct Session {
    raw: sys::storage_session_t,
}

impl Session {
    /// Opens a new connection to the storage service.
    ///
    /// If `wait_for_port` is true and the port is not yet open for connections,
    /// then this method will block until the port is open and a connection can be
    /// established. Otherwise it will return an error if the connection cannot be
    /// established immediately.
    ///
    /// # Errors
    ///
    /// * Returns an error code if we fail to connect to the storage service.
    /// * If `wait_for_port` is false, returns an error code if the port is not
    ///   listening for connections.
    pub fn new(port: Port, wait_for_port: bool) -> Result<Self, Error> {
        use Port::*;

        // Convert the `port` enum to the corresponding C string expected by the C API.
        let port = match port {
            TamperDetect => sys::STORAGE_CLIENT_TD_PORT.as_ptr().cast(),
            TamperDetectPersist => sys::STORAGE_CLIENT_TDP_PORT.as_ptr().cast(),
            TamperDetectEarlyAccess => sys::STORAGE_CLIENT_TDEA_PORT.as_ptr().cast(),
            TamperProof => sys::STORAGE_CLIENT_TP_PORT.as_ptr().cast(),

            #[cfg(test)]
            TestPortNonExistent => b"com.android.trusty.storage.client.fake\0".as_ptr().cast(),
        };

        let flags = if wait_for_port { trusty_sys::IPC_CONNECT_WAIT_FOR_PORT } else { 0 };

        // SAFETY: FFI call to underlying C API. Both inputs were constructed in this
        // function and so are guaranteed to be safe for this call.
        let handle = Error::check_non_negative(unsafe { trusty_sys::connect(port, flags) })?;

        Ok(Self { raw: handle.try_into().unwrap() })
    }

    /// Drops the `Session` and closes the connection.
    ///
    /// The connection is closed automatically when the `Session` object is dropped,
    /// but this method can be used if you need to explicitly close a session before
    /// the `Session` object would normally go out of scope.
    pub fn close(self) {
        // NOTE: No logic needed here. We simply take ownership of `self` and then
        // let the `Drop` impl handle closing the connection.
    }

    /// Attempts to open a file at `name` with the options specified by `mode`.
    ///
    /// If `mode` specifies that a new file may be created or an existing file may
    /// be truncated, any resulting file changes are immediately committed as a
    /// transaction.
    ///
    /// # Errors
    ///
    /// This function will return an error in the following situations, but is not
    /// limited to just these cases:
    ///
    /// * If `mode` specifies `Open` or `TruncateExisting` and there is no existing
    ///   file.
    /// * If `mode` specifies `CreateExclusive` and a file already exists.
    pub fn open_file(&mut self, name: &str, mode: OpenMode) -> Result<SecureFile, Error> {
        self.open_file_impl(name, mode, true)
    }

    // NOTE: We split the implementation of `open_file` to a separate method in
    // order to hide the `complete` argument.
    //
    // When client code opens a file directly with `open_file` and a file is
    // created, we need to immediately commit the transaction. But when going
    // through the transaction API the individual write operations shouldn't be
    // committed until the entire transaction is done. We internally provide the
    // `complete` argument to support both of these cases, but want to avoid
    // exposing that arg publicly so that client code can't manually trigger a
    // transaction outside of the full transaction API.
    fn open_file_impl(
        &mut self,
        name: &str,
        mode: OpenMode,
        complete: bool,
    ) -> Result<SecureFile, Error> {
        use OpenMode::*;

        // Convert `name` to a `CString` in order to add a null byte to the end.
        let name = CString::try_new(name)?;

        // Construct the flags bitmask from selected mode.
        let flags = match mode {
            Open => 0,
            Create => sys::STORAGE_FILE_OPEN_CREATE,
            CreateExclusive => {
                sys::STORAGE_FILE_OPEN_CREATE | sys::STORAGE_FILE_OPEN_CREATE_EXCLUSIVE
            }
            TruncateExisting => sys::STORAGE_FILE_OPEN_TRUNCATE,
            TruncateOrCreate => sys::STORAGE_FILE_OPEN_CREATE | sys::STORAGE_FILE_OPEN_TRUNCATE,
        };

        // Convert `complete` into the corresponding flag value.
        let ops_flags = if complete { sys::STORAGE_OP_COMPLETE } else { 0 };

        let mut file = MaybeUninit::uninit();

        // SAFETY: FFI call to underlying C API. Both inputs were constructed in this
        // function and so are guaranteed to be safe for this call.
        Error::check_return_code(unsafe {
            sys::storage_open_file(self.raw, file.as_mut_ptr(), name.as_ptr(), flags, ops_flags)
        })?;

        // SAFETY: We've checked the error code returned by `storage_open_file`, so
        // at this point we know that the file was successfully opened and `file`
        // was initialized.
        let raw = unsafe { file.assume_init() };

        Ok(SecureFile { raw })
    }

    /// Reads the contents of the file at `name`.
    ///
    /// Reads the contents of the file into `buf`, returning the part of `buf` that
    /// contains the contents of the file.
    ///
    /// # Errors
    ///
    /// This function will return an error in the following situations, but is not
    /// limited to just these cases:
    ///
    /// * `name` is not the path to a valid file.
    /// * `buf` isn't large enough to contain the full contents of the file.
    pub fn read<'buf>(&mut self, path: &str, buf: &'buf mut [u8]) -> Result<&'buf [u8], Error> {
        let file = self.open_file_impl(path, OpenMode::Open, false)?;
        self.read_all(&file, buf)
    }

    /// Writes the contents of `buf` to a file at `name`.
    ///
    /// If no file exists at `name` then a new one is created. If a file already
    /// exists the length is truncated to fit the length of `buf` if the previous
    /// size was larger.
    ///
    /// This operation implicitly creates its own transaction, so the file contents
    /// are guaranteed to be written to disk if it completes successfully.
    ///
    /// # Errors
    ///
    /// This function will return an error in the following situations, but is not
    /// limited to just these cases:
    ///
    /// * `name` refers to a file that is already open as a handle and therefor
    ///   cannot be overwritten.
    pub fn write(&mut self, path: &str, buf: &[u8]) -> Result<(), Error> {
        let mut file = self.open_file_impl(path, OpenMode::Create, false)?;
        self.write_all(&mut file, buf)
    }

    /// Reads the entire contents of `file` into `buf`.
    ///
    /// Reads contents starting from the beginning of the file, regardless of the
    /// current cursor position in `file`. Returns a slice of `buf` containing the
    /// read data.
    ///
    /// If you only want to read up to `buf.len()` bytes of the file, regardless of
    /// how large the whole file is, use [`read_at`](Self::read_at) instead.
    ///
    /// # Errors
    ///
    /// This function will return an error in the following situations, but is not
    /// limited to just these cases:
    ///
    /// * [`ErrorCode::NotEnoughBuffer`] if `buf` isn't large enough to contain the
    ///   full contents of the file.
    pub fn read_all<'buf>(
        &mut self,
        file: &SecureFile,
        buf: &'buf mut [u8],
    ) -> Result<&'buf [u8], Error> {
        // Validate that `buf` is large enough to hold the full contents of the file
        // since the underlying `read_at` call does not consider it an error if the
        // buffer is smaller than the remaining file size.
        let size = self.get_size(file)?;
        if buf.len() < size {
            return Err(Error::Code(ErrorCode::NotEnoughBuffer));
        }

        self.read_at(file, 0, buf)
    }

    /// Reads the content of `file` starting at the specified offset.
    ///
    /// Reads contents starting from the given `offset` in bytes from the start of
    /// the file. Reads up to `buf.len()` bytes from the file and writes them into
    /// `buf`. If there isn't enough data after `offset` to fill `buf`, then `buf`
    /// will only be partially filled. Returns a slice of `buf` that contains the
    /// read data.
    ///
    /// # Errors
    ///
    /// This function will return an error in the following situations, but is not
    /// limited to just these cases:
    ///
    /// * `offset` is greater than the length of the file.
    pub fn read_at<'buf>(
        &mut self,
        file: &SecureFile,
        offset: usize,
        buf: &'buf mut [u8],
    ) -> Result<&'buf [u8], Error> {
        // SAFETY: FFI call to underlying C API. The raw file handle is guaranteed to be
        // valid until the `SecureFile` object is dropped, and so is valid at this
        // point.
        let bytes_read = Error::check_size(unsafe {
            sys::storage_read(
                file.raw,
                offset.try_into().unwrap(),
                buf.as_mut_ptr() as *mut c_void,
                buf.len(),
            )
        })?;

        Ok(&buf[..bytes_read])
    }

    /// Overwrites `file` with the contents of `buf`.
    ///
    /// Writes the contents of `buf` to the file starting from the beginning of the
    /// file, regardless of the current cursor position in `file`. The file is then
    /// truncated to fit the length of `buf` if the previous size was larger.
    ///
    /// This operation implicitly creates its own transaction, so the file contents
    /// are guaranteed to be written to disk if it completes successfully.
    pub fn write_all(&mut self, file: &mut SecureFile, buf: &[u8]) -> Result<(), Error> {
        self.write_all_impl(file, buf, true)
    }

    // NOTE: We split the implementation of `write_all` to a separate method in
    // order to hide the `complete` argument. See the comment on `open_file_impl`
    // for additional context.
    fn write_all_impl(
        &mut self,
        file: &mut SecureFile,
        buf: &[u8],
        complete: bool,
    ) -> Result<(), Error> {
        // Set the file size to 0 so that after the write completes the file size will
        // match the length of `buf`.
        //
        // SAFETY: FFI call to underlying C API. The raw file handle is guaranteed to be
        // valid until the `SecureFile` object is dropped, and so is valid at this
        // point.
        Error::check_return_code(unsafe { sys::storage_set_file_size(file.raw, 0, 0) })?;

        self.write_at_impl(file, 0, buf, complete)
    }

    /// Writes to a file starting at the specified offset.
    ///
    /// Writes the contents of `buf` to the file starting at `offset` bytes from
    /// the beginning of the file. If the file is not already long enough to fit
    /// the new data, it will be extended automatically to fit.
    ///
    /// This operation implicitly creates its own transaction, so the file
    /// contents are guaranteed to be written to disk if it completes
    /// successfully.
    pub fn write_at(
        &mut self,
        file: &mut SecureFile,
        offset: usize,
        buf: &[u8],
    ) -> Result<(), Error> {
        self.write_at_impl(file, offset, buf, true)
    }

    // NOTE: We split the implementation of `write_at` to a separate method in
    // order to hide the `complete` argument. See the comment on `open_file_impl`
    // for additional context.
    fn write_at_impl(
        &mut self,
        file: &mut SecureFile,
        offset: usize,
        buf: &[u8],
        complete: bool,
    ) -> Result<(), Error> {
        // Convert `complete` into the corresponding flag value.
        let ops_flags = if complete { sys::STORAGE_OP_COMPLETE } else { 0 };

        // Write to the file at `offset`.
        //
        // SAFETY: FFI call to the underlying C API. Invariants are same as noted above.
        let bytes_written = Error::check_size(unsafe {
            sys::storage_write(
                file.raw,
                offset.try_into().unwrap(),
                buf.as_ptr() as *const c_void,
                buf.len(),
                ops_flags,
            )
        })?;

        // Verify our assumption that the entire write will always succeed.
        assert_eq!(
            bytes_written,
            buf.len(),
            "File data was only partially written, tried to write {} bytes but only {} bytes written",
            buf.len(),
            bytes_written,
        );

        Ok(())
    }

    /// Returns the size of the file in bytes.
    pub fn get_size(&mut self, file: &SecureFile) -> Result<usize, Error> {
        let mut size = 0;

        // SAFETY: FFI call to underlying C API. The raw file handle is guaranteed to be
        // valid until the `SecureFile` object is dropped, and so is valid at this
        // point.
        Error::check_return_code(unsafe { sys::storage_get_file_size(file.raw, &mut size) })?;

        Ok(size as usize)
    }

    /// Truncates or extends the underlying file, updating the size of the file to
    /// become `size.`
    ///
    /// If `size` is less than the current file's size, then the file will be
    /// shrunk. If it is greater than the current file's size, then the file will be
    /// extended to `size` and have all of the intermediate data filled with 0s.
    ///
    /// This operation implicitly creates its own transaction, so the changes are
    /// guaranteed to be written to disk if it completes successfully.
    pub fn set_size(&mut self, file: &mut SecureFile, size: usize) -> Result<(), Error> {
        self.set_size_impl(file, size, true)
    }

    // NOTE: We split the implementation of `set_size` to a separate method in
    // order to hide the `complete` argument. See the comment on `open_file_impl`
    // for additional context.
    fn set_size_impl(
        &mut self,
        file: &mut SecureFile,
        size: usize,
        complete: bool,
    ) -> Result<(), Error> {
        // Convert `complete` into the corresponding flag value.
        let ops_flags = if complete { sys::STORAGE_OP_COMPLETE } else { 0 };

        // SAFETY: FFI call to underlying C API. The raw file handle is guaranteed to be
        // valid until the `SecureFile` object is dropped, and so is valid at this
        // point.
        Error::check_return_code(unsafe {
            sys::storage_set_file_size(file.raw, size as u64, ops_flags)
        })
    }

    /// Renames a file to a new name, replacing the original file if `to` already
    /// exists.
    ///
    /// This operation implicitly creates its own transaction, so the changes are
    /// guaranteed to be written to disk if it completes successfully.
    ///
    /// # Errors
    ///
    /// This function will return an error in the following situations, but is not
    /// limited to just these cases:
    ///
    /// * `from` does not exist.
    /// * `to` exists and cannot be overwritten.
    /// * A handle to `from` is already open.
    pub fn rename(&mut self, from: &str, to: &str) -> Result<(), Error> {
        self.rename_impl(from, to, true)
    }

    // NOTE: We split the implementation of `rename` to a separate method in
    // order to hide the `complete` argument. See the comment on `open_file_impl`
    // for additional context.
    fn rename_impl(&mut self, from: &str, to: &str, complete: bool) -> Result<(), Error> {
        // Convert `name` to a `CString` in order to add a null byte to the end.
        let from = CString::try_new(from)?;
        let to = CString::try_new(to)?;

        // Convert `complete` into the corresponding flag value.
        let ops_flags = if complete { sys::STORAGE_OP_COMPLETE } else { 0 };

        // SAFETY: FFI call to underlying C API. The raw file handle is guaranteed to be
        // valid until the `SecureFile` object is dropped, and so is valid at this
        // point.
        Error::check_return_code(unsafe {
            sys::storage_move_file(
                self.raw,
                0,
                from.as_ptr(),
                to.as_ptr(),
                sys::STORAGE_FILE_MOVE_CREATE,
                ops_flags,
            )
        })
    }

    /// Removes a file from the filesystem.
    ///
    /// This operation implicitly creates its own transaction, so the changes are
    /// guaranteed to be written to disk if it completes successfully.
    ///
    /// # Errors
    ///
    /// This function will return an error in the following situations, but is not
    /// limited to just these cases:
    ///
    /// * `name` doesn't exist.
    /// * `name` cannot be deleted because it is open as a file handle.
    pub fn remove(&mut self, name: &str) -> Result<(), Error> {
        self.remove_impl(name, true)
    }

    // NOTE: We split the implementation of `remove` to a separate method in
    // order to hide the `complete` argument. See the comment on `open_file_impl`
    // for additional context.
    fn remove_impl(&mut self, name: &str, complete: bool) -> Result<(), Error> {
        // Convert `name` to a `CString` in order to add a null byte to the end.
        let name = CString::try_new(name)?;

        // Convert `complete` into the corresponding flag value.
        let ops_flags = if complete { sys::STORAGE_OP_COMPLETE } else { 0 };

        // SAFETY: FFI call to underlying C API. The raw file handle is guaranteed to be
        // valid until the `SecureFile` object is dropped, and so is valid at this
        // point.
        Error::check_return_code(unsafe {
            sys::storage_delete_file(self.raw, name.as_ptr(), ops_flags)
        })
    }

    /// Creates a new [`Transaction`] object.
    ///
    /// See the [crate-level documentation][crate] for information on how any
    /// why to use transactions.
    pub fn begin_transaction(&mut self) -> Transaction<'_> {
        Transaction { session: self }
    }

    /// Returns an iterator that can be used to list the files in storage.
    ///
    /// The iterator will yield instances of [`Result<(String, FileState)>`],
    /// where the contained `String` is the name of a file.
    ///
    /// # Errors
    ///
    /// This function or the returned iterator will yield an error in the
    /// following situations, but is not limited to just these cases:
    ///
    /// * The session is closed (i.e. the `Session` object is dropped) while the
    ///   iterator is still in use.
    pub fn list_files(&mut self) -> Result<DirIter, Error> {
        DirIter::new(self)
    }
}

impl Drop for Session {
    fn drop(&mut self) {
        // SAFETY: The raw handle is guaranteed to be valid at this point because we
        // only ever construct a `Session` with a valid handle, and we only close
        // the session on drop.
        unsafe {
            sys::storage_close_session(self.raw);
        }
    }
}

/// Handle to an open file.
///
/// A file handle can be opened with [`Session::open_file`]. File operations
/// cannot be performed directly with the file handle. Instead, use methods on
/// [`Session`] like [`Session::read_all`] and [`Session::write_all`] to read
/// and write the contents of a file.
///
/// The handle is automatically closed on drop.
#[derive(Debug)]
pub struct SecureFile {
    raw: sys::file_handle_t,
}

impl SecureFile {
    /// Drops the `SecureFile` and closes the handle.
    ///
    /// The connection is closed automatically when the `SecureFile` object is
    /// dropped, but this method can be used if you need to explicitly close a
    /// handle before the `SecureFile` object would normally go out of scope.
    pub fn close(self) {
        // NOTE: No logic needed here. We simply take ownership of `self` and then
        // let the `Drop` impl handle closing the handle.
    }
}

impl Drop for SecureFile {
    fn drop(&mut self) {
        // SAFETY: The raw handle is guaranteed to be valid at this point because we
        // only ever construct a `SecureFile` with a valid handle, and we only close
        // the file on drop.
        unsafe {
            sys::storage_close_file(self.raw);
        }
    }
}

/// Common error type for file operations.
///
/// Errors mostly originate from the storage service, but some error variants
/// are generated locally.
#[derive(Debug, PartialEq, Eq)]
pub enum Error {
    /// An error code returned by the storage service.
    ///
    /// Check the contained [`trusty_sys::Error`] to determine the specific
    /// error code that was returned.
    Code(ErrorCode),

    // An error converting a string or path for FFI.
    TryNew(TryNewError),

    Alloc(AllocError),

    Utf8(FromUtf8Error),
}

impl Error {
    /// Checks a return code and converts it to an `Error` if necessary.
    ///
    /// Returns a `Result` so that this method can be used with `?` in order to
    /// quickly propagate errors returned from the storage service, e.g.:
    ///
    /// ```
    /// Error::try_from_code(unsafe {
    ///     sys::some_ffi_call()
    /// })?;
    /// ```
    ///
    /// Note that only a value of 0 is considered success, and all other values are
    /// treated as an error. If positive values should not be considered errors, use
    /// [`check_non_negative`](Self::check_non_negative). If the return code
    /// represents a length or index that you want to handle as a `usize`, use
    /// [`check_size`](Self::check_size).
    fn check_return_code(code: impl Into<c_long>) -> Result<(), Self> {
        let code = code.into();
        if ErrorCode::is_err(code) {
            return Err(Error::Code(ErrorCode::from(code)));
        }

        Ok(())
    }

    /// Checks a return code where positive values do not represent an error.
    ///
    /// This helper is for use with FFI functions that return a signed value where
    /// negative values indicate an error but positive values have some non-error
    /// meaning, e.g. a function that returns a handle value on success. The
    /// original value is returned in the success case to allow calling code to
    /// easily continue using it:
    ///
    /// ```
    /// let handle = Error::check_non_negative(unsafe {
    ///     sys::some_ffi_call()
    /// })?;
    ///
    /// println!("Handle value: {}", handle);
    /// ```
    ///
    /// For cases where the return value specifically represents a length or index
    /// value that you then want to use in Rust code as a `usize`, use
    /// [`check_size`](Self::check_size) instead.
    fn check_non_negative<T: Into<c_long> + Copy>(code: T) -> Result<T, Self> {
        let long_code = code.into();

        // NOTE: We directly check `code < 0` here instead of using `is_err` because
        // `is_err` will also treat positive values as errors, whereas in this case
        // positive values are the success case.
        if long_code < 0 {
            Err(Error::Code(ErrorCode::from(long_code)))
        } else {
            Ok(code)
        }
    }

    /// Checks a return code that may also represent a length or index value.
    ///
    /// This helper is for use with FFI functions that return a size value that may
    /// be negative to indicate an error, i.e. when reading or writing a file's
    /// contents. If `size` does not encode an error, it is converted to a `usize`
    /// for further use in Rust code.
    ///
    /// ```
    /// let len = Error::check_size(unsafe {
    ///     sys::some_ffi_call()
    /// })?;
    ///
    /// println!("Bytes written: {}", len);
    /// ```
    fn check_size(size: isize) -> Result<usize, Self> {
        Self::check_non_negative(size as c_long)?;

        // We've confirmed that `size` is not negative, so it's safe to cast it to
        // a `usize`.
        Ok(size as usize)
    }
}

impl From<TryNewError> for Error {
    fn from(from: TryNewError) -> Self {
        Error::TryNew(from)
    }
}

impl From<AllocError> for Error {
    fn from(from: AllocError) -> Self {
        Error::Alloc(from)
    }
}

impl From<FromUtf8Error> for Error {
    fn from(from: FromUtf8Error) -> Self {
        Error::Utf8(from)
    }
}

/// The port to use when connecting to the storage service.
///
/// The different ports provide different guarantees for how data is stored, and
/// so an appropriate port to connect to will need to be chosen based on the
/// needs of your client service.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Port {
    /// Provides storage with tamper and rollback protection.
    ///
    /// This port is only available once the non-secure OS has booted. This port
    /// should be used by most apps as it can offer more storage and better
    /// performance than the other choices.
    ///
    /// Note, though, that this storage is not persistent across factory resets.
    /// Data that requires persistence can use `TamperDetectPersist` instead.
    TamperDetect,

    /// Provides storage that will be preserved during a normal device wipe.
    ///
    /// Also provides tamper and rollback protection, same as
    /// [`TamperDetect`][Self::TamperDetect].
    TamperDetectPersist,

    /// Provides access to storage before the non-secure OS has booted.
    ///
    /// Also provides tamper and rollback protection, same as
    /// [`TamperDetect`][Self::TamperDetect]. This storage might also not be wiped
    /// when device user data is wiped (i.e. during a factory reset), but that
    /// property is not guaranteed.
    TamperDetectEarlyAccess,

    /// Provides tamper-proof storage.
    ///
    /// Note that non-secure code can prevent read and write operations from
    /// succeeding, but it cannot modify on-disk data.
    TamperProof,

    // HACK: Fake port used to test how the library handles attempting to connect
    // to a port that doesn't exist. All of the normal ports are active while
    // running the test, so without this we don't have a way to specify an invalid
    // port.
    #[cfg(test)]
    #[doc(hidden)]
    TestPortNonExistent,
}

/// Configuration for how opening a file should be handled.
///
/// When you request to open a file handle, the storage service needs to know
/// how you want to answer the following questions:
///
/// * If no file already exists should one be created?
/// * If a file already exists should it be opened, or should that be treated as
///   an error?
/// * If an existing file may be opened should its contents be preserved, or
///   should it be truncated so that it looks like a new file?
///
/// The variants of this enum represent the valid ways to answer all of these
/// questions. Not all combinations of answers are represented because they
/// would be contradictory.
///
/// The default mode is `Open`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OpenMode {
    /// Open an existing file.
    ///
    /// Generates an error if no file already exists.
    Open,

    /// Create a new file if one does not already exist.
    ///
    /// If a file already exists the file is opened.
    Create,

    /// Create a new file only if no file already exists.
    ///
    /// Generates an error if the file already exists.
    CreateExclusive,

    /// Truncates the file and opens it as a new file.
    ///
    /// Generates an error if no file already exists.
    TruncateExisting,

    /// Truncates the file and opens it as a new file.
    ///
    /// Creates a new file if no file already exists.
    TruncateOrCreate,
}

impl Default for OpenMode {
    fn default() -> Self {
        OpenMode::Open
    }
}

/// Iterator over the entries in a directory.
///
/// This iterator is returned from [`Session::list_files`] and
/// [`Transaction::list_files`] and will yield instances of [`Result<(String,
/// FileState)>`]. If the iterator yields an error at any point during iteration,
/// subsequent calls to [`next`][Iterator::next] will always return `None`.
///
/// # Errors
///
/// Yields an error if the session was closed.
#[derive(Debug)]
pub struct DirIter {
    session: sys::storage_session_t,
    raw: *mut sys::storage_open_dir_state,
    finished: bool,
}

impl DirIter {
    fn new(session: &mut Session) -> Result<Self, Error> {
        let mut dir_ptr = ptr::null_mut();

        // SAFETY: FFI call to underlying C API. The raw session handle is
        // guaranteed to be valid until the `Session` object is dropped.
        Error::check_return_code(unsafe {
            sys::storage_open_dir(session.raw, ptr::null(), &mut dir_ptr)
        })?;

        if dir_ptr.is_null() {
            return Err(Error::Code(ErrorCode::Generic));
        }

        Ok(DirIter { session: session.raw, raw: dir_ptr, finished: false })
    }

    /// Attempts to get the next file item in the directory.
    ///
    /// This method provides the bulk of the logic for the `Iterator` impl for
    /// `DirIter`, but returns a `Result<Option>` instead of an `Option<Result>` the way
    /// the `next` method needs to. This allows us to use `?` within `try_next` to
    /// propagate errors. The `Iterator::next` method can then simply do
    /// `try_next().transpose()` to convert the result to the correct form for the
    /// `Iterator` API.
    fn try_next(&mut self) -> Result<Option<(String, FileState)>, Error> {
        let mut buf = [0; sys::STORAGE_MAX_NAME_LENGTH_BYTES as usize];
        let mut flags = 0;

        // SAFETY: FFI call to underlying C API. The raw session handle is guaranteed to
        // be valid while the `Session` object is alive, and the raw dir iter pointer is
        // likewise guaranteed to be valid while the `DirIter` object is alive.
        let bytes_read = Error::check_size(unsafe {
            let code = sys::storage_read_dir(
                self.session,
                self.raw,
                &mut flags,
                buf.as_mut_ptr().cast(),
                buf.len(),
            );

            code as isize
        })?;

        // Convert the output `flags` value into the corresponding `FileState`, and
        // handle the end of iteration as necessary.
        let state =
            match u32::from(flags) & sys::storage_file_list_flag_STORAGE_FILE_LIST_STATE_MASK {
                sys::storage_file_list_flag_STORAGE_FILE_LIST_COMMITTED => FileState::Committed,
                sys::storage_file_list_flag_STORAGE_FILE_LIST_ADDED => FileState::Added,
                sys::storage_file_list_flag_STORAGE_FILE_LIST_REMOVED => FileState::Removed,

                sys::storage_file_list_flag_STORAGE_FILE_LIST_END => return Ok(None),

                flag => {
                    panic!("Unexpected flag returned from `storage_read_dir`: {:#x}", flag);
                }
            };

        // Convert our temporary stack-allocated buffer into a `String` so that it can
        // be returned to the caller.
        //
        // NOTE: `bytes_read` should only ever be 0 when indicating that we've finished
        // enumerating the directory, in which case we should have already returned
        // `None`. But we perform an extra check here to make sure we don't overflow
        // when doing `bytes_read - 1` if there is a case where `bytes_read` can be 0.
        let name = if bytes_read > 0 {
            // NOTE: `bytes_read` will include the terminating nul byte at the end of the C
            // string, so we subtract 1 when creating the `String` to exclude that
            // terminating byte.
            let buf = Vec::try_alloc_from(&buf[..bytes_read - 1])?;
            String::from_utf8(buf)?
        } else {
            panic!("`storage_read_dir` returned file name length 0");
        };

        Ok(Some((name, state)))
    }
}

impl Iterator for DirIter {
    type Item = Result<(String, FileState), Error>;

    fn next(&mut self) -> Option<Self::Item> {
        // Always return `None` if we should no longer continue enumerating entries.
        if self.finished {
            return None;
        }

        let next = self.try_next().transpose();

        // Once we yield `None` or an error once we want to effectively "fuse" the
        // iterator so that we only return `None` going forward. This avoids the
        // iterator getting stuck in a loop where it continues to yield errors forever
        // if the underlying iterator gets into an invalid state (e.g. if the session is
        // closed).
        self.finished = match &next {
            None | Some(Err(..)) => true,
            _ => false,
        };

        next
    }
}

impl Drop for DirIter {
    fn drop(&mut self) {
        // SAFETY: FFI call to underlying C API. The raw session handle is guaranteed to
        // be valid until the `Session` object is dropped, and the raw dir state pointer
        // is guaranteed to be valid until this point.
        unsafe { sys::storage_close_dir(self.session, self.raw) }
    }
}

/// The state of a file when listing directory entries.
///
/// Part of the element yielded by [`DirIter`].
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum FileState {
    /// The file is committed and has not been removed in a pending transaction.
    Committed,

    /// The file will be added by the current transaction.
    Added,

    /// The file will be removed by the current transaction.
    Removed,
}
