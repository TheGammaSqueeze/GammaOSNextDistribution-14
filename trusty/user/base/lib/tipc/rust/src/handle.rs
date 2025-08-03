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

use crate::serialization::Serializer;
use crate::sys::*;
use crate::{Deserialize, Serialize, TipcError};
use core::convert::TryInto;
use core::ffi::CStr;
use core::mem::MaybeUninit;
use log::{error, warn};
use trusty_std::alloc::{FallibleVec, Vec};
use trusty_sys::{c_int, c_long};

/// An open IPC connection or shared memory reference.
///
/// A `Handle` can either represent an open IPC connection or a shared memory
/// reference. Which one a given handle represents generally must be determined
/// from context, i.e. the handle returned by [`Handle::connect`] will always
/// represent an IPC connection. A given incoming or outgoing message will
/// generally have specific semantics regarding what kind of handles are sent
/// along with it.
///
/// # IPC Connections
///
/// This handle knows how to send and receive messages which implement
/// [`Serialize`] and [`Deserialize`] respectively. Serialization and parsing
/// are handled by the message itself.
///
/// The handle owns its connection, which is closed when this struct is dropped.
/// Do not rely on the connection being closed for protocol correctness, as the
/// drop method may not always be called.
///
/// # Shared Memory References
///
/// An incoming TIPC message may include one or more handles representing a
/// shared memory buffer. These can be mapped into process memory using
/// [`Handle::mmap`]. The returned [`UnsafeSharedBuf`] object provides access to
/// the shared memory buffer and will unmap the buffer automatically on drop.
#[repr(transparent)]
#[derive(Eq, PartialEq, Debug)]
pub struct Handle(handle_t);

/// Maximum number of handles that can be transferred in an IPC message at once.
pub const MAX_MSG_HANDLES: usize = 8;

impl Handle {
    /// Open a client connection to the given service.
    ///
    /// The service `port` can be either a Trusty TA or kernel port name. This
    /// call is synchronous and will block until the specified port exists.
    ///
    /// # Examples
    ///
    /// Open a TIPC connection to `com.android.trusty.test_port`:
    ///
    /// ```
    /// use core::ffi::CStr;
    /// use tipc::Handle;
    ///
    /// let port = CStr::from_bytes_with_nul(b"com.android.trusty.test_port\0")
    ///                  .unwrap();
    ///
    /// if let Ok(handle) = Handle::connect(port) {
    ///     println!("Connection successful");
    /// } else {
    ///     println!("Connection attempt failed");
    /// }
    /// ```
    pub fn connect(port: &CStr) -> crate::Result<Self> {
        // SAFETY: external syscall. port is guaranteed to be a well-formed,
        // null-terminated C string.
        let rc = unsafe { trusty_sys::connect(port.as_ptr(), IPC_CONNECT_WAIT_FOR_PORT as u32) };
        if rc < 0 {
            Err(TipcError::from_uapi(rc))
        } else {
            rc.try_into().map(Handle).or(Err(TipcError::InvalidHandle))
        }
    }

    pub fn try_clone(&self) -> crate::Result<Self> {
        // SAFETY: external syscall, handle descriptor is valid for the lifetime
        // of self. Return value is either an error or a new valid handle
        // descriptor that we can take ownership of.
        let rc = unsafe { trusty_sys::dup(self.0) };
        Self::from_raw(rc.try_into().or(Err(TipcError::InvalidHandle))?)
    }

    /// Construct a Handle from a raw file descriptor
    ///
    /// Conditionally creates a Handle from the return value of a C FFI function.
    /// If the integer value is < 0, then we will refuse to create the Handle.
    pub fn from_raw(fd: i32) -> crate::Result<Self> {
        if fd < 0 {
            Err(TipcError::from_uapi(fd as c_long))
        } else {
            Ok(Self(fd))
        }
    }

    /// Send an IPC message.
    ///
    /// Serializes `msg` using its [`Serialize`] implementation and send it
    /// across this IPC connection. Attempts to serialize the message in-place
    /// without new allocations.
    pub fn send<'s, T: Serialize<'s>>(&self, msg: &'s T) -> crate::Result<()> {
        let mut serializer = BorrowingSerializer::default();
        msg.serialize(&mut serializer)?;
        self.send_vectored(&serializer.buffers[..], &serializer.handles[..])
    }

    /// Receive an IPC message.
    ///
    /// Receives a message into the given temporary `buffer`, and deserializes
    /// the received message into a `T` using `T::Deserialize`. If the received
    /// message does not fit into `buffer` this method will return error value
    /// [`TipcError::NotEnoughBuffer`]. In the case of insufficient buffer
    /// space, the message data will be lost and must be resent to recover.
    ///
    /// TODO: Support a timeout for the wait.
    pub fn recv<T: Deserialize>(&self, buffer: &mut [u8]) -> Result<T, T::Error> {
        let mut handles: [Option<Handle>; MAX_MSG_HANDLES] = Default::default();
        let (byte_count, handle_count) = self.recv_vectored(&mut [buffer], &mut handles)?;

        T::deserialize(&buffer[..byte_count], &mut handles[..handle_count])
    }

    /// Receive raw bytes and handles into slices of buffers and handles.
    ///
    /// Returns a tuple of the number of bytes written into the buffer and the
    /// number of handles received. `handles` should have space for at least
    /// [`MAX_MSG_HANDLES`].
    pub fn recv_vectored(
        &self,
        buffers: &mut [&mut [u8]],
        handles: &mut [Option<Handle>],
    ) -> crate::Result<(usize, usize)> {
        let _ = self.wait(None)?;

        let mut raw_handles = [-1; MAX_MSG_HANDLES];

        let (buf_len, handles_len) = self.get_msg(|msg_info| {
            if msg_info.len > buffers.iter().map(|b| b.len()).sum() {
                return Err(TipcError::NotEnoughBuffer);
            }

            let mut iovs = Vec::new();
            iovs.try_reserve_exact(buffers.len())?;
            iovs.extend(buffers.iter_mut().map(|buf| trusty_sys::iovec {
                iov_base: buf.as_mut_ptr().cast(),
                iov_len: buf.len(),
            }));

            let mut msg = trusty_sys::ipc_msg {
                num_iov: iovs.len().try_into()?,
                iov: iovs.as_mut_ptr(),

                num_handles: raw_handles.len().try_into()?,
                handles: raw_handles.as_mut_ptr() as *mut i32,
            };

            // SAFETY: syscall, pointer is initialized with valid data and
            // mutably borrowed. The buffers that the msg refers to are valid
            // and writable across this call. `Handle` is a transparent wrapper
            // around `handle_t`, i.e. `i32` so we can safely cast the handles
            // slice to an `i32` pointer. Although the syscall requires a
            // mutable handle pointer, it does not mutate these handles, so we
            // can safely cast the immutable slice to mutable pointer.
            let rc = unsafe { trusty_sys::read_msg(self.as_raw_fd(), msg_info.id, 0, &mut msg) };

            if rc < 0 {
                Err(TipcError::from_uapi(rc))
            } else {
                Ok((rc.try_into()?, msg_info.num_handles.try_into()?))
            }
        })?;

        // Convert the raw handles list into a list of `Option<Handle>`.
        for (index, raw_handle) in raw_handles[..handles_len].into_iter().enumerate() {
            handles[index] = Some(Handle(*raw_handle));
        }

        Ok((buf_len, handles_len))
    }

    /// Send a set of buffers and file/memref handles.
    ///
    /// Sends a set of buffers and set of handles at once. `buf` must fit in the
    /// message queue and `handles` must contain no more than
    /// [`MAX_MSG_HANDLES`].
    ///
    /// If the message fails to fit in the server's message queue, the send will
    /// block and retry when the kernel indicates that the queue is unblocked.
    fn send_vectored(&self, buffers: &[&[u8]], handles: &[Handle]) -> crate::Result<()> {
        let mut iovs = Vec::new();
        iovs.try_reserve_exact(buffers.len())?;
        iovs.extend(
            buffers.iter().map(|buf| trusty_sys::iovec {
                iov_base: buf.as_ptr() as *mut _,
                iov_len: buf.len(),
            }),
        );
        let total_num_bytes = buffers.iter().map(|b| b.len()).sum();

        let mut msg = trusty_sys::ipc_msg {
            num_iov: iovs.len().try_into()?,
            iov: iovs.as_mut_ptr(),

            num_handles: handles.len().try_into()?,
            handles: handles.as_ptr() as *mut i32,
        };
        // SAFETY: syscall, pointer is initialized with valid data and mutably
        // borrowed. The buffers that the msg refers to are valid and writable
        // across this call. `Handle` is a transparent wrapper around
        // `handle_t`, i.e. `i32` so we can safely cast the handles slice to an
        // `i32` pointer. Although the syscall requires a mutable handle
        // pointer, it does not mutate these handles, so we can safely cast the
        // immutable slice to mutable pointer.
        let mut rc = unsafe { trusty_sys::send_msg(self.as_raw_fd(), &mut msg) };

        // If there's not enough space in the buffer to send the message, wait until we
        // get a `SEND_UNBLOCKED` event or another error occurs.
        if rc == trusty_sys::Error::NotEnoughBuffer as c_long {
            loop {
                let event = self.wait(None)?;
                if event.event & IPC_HANDLE_POLL_SEND_UNBLOCKED as u32 != 0 {
                    break;
                } else if event.event & IPC_HANDLE_POLL_MSG as u32 != 0 {
                    warn!("Received a message while waiting for send to be unblocked, abandoning send attempt");
                    return Err(TipcError::Busy);
                } else if event.event & IPC_HANDLE_POLL_HUP as u32 != 0 {
                    return Err(TipcError::ChannelClosed);
                } else {
                    error!(
                        "Unexpected event while waiting for send to be unblocked: {}",
                        event.event,
                    );
                }
            }

            // Retry the send. It should go through this time because sending is now
            // unblocked.
            rc = unsafe { trusty_sys::send_msg(self.as_raw_fd(), &mut msg) };
        }

        if rc < 0 {
            Err(TipcError::from_uapi(rc))
        } else if rc as usize != total_num_bytes {
            Err(TipcError::IncompleteWrite { num_bytes_written: rc as usize })
        } else {
            Ok(())
        }
    }

    /// Get the raw file descriptor of this handle.
    ///
    /// Returns the raw integer OS file descriptor(fd) associated with this handle. This is
    /// primarily useful for interacting with FFI interfaces. The programmer must ensure that any
    /// interactions with the raw fd do not cause it to close or otherwise become invalid. This
    /// handle must outlive all uses of the returned fd. Otherwise, the behavior is undefined.
    pub fn as_raw_fd(&self) -> i32 {
        self.0
    }

    /// Wait for an event on this handle for `timeout` milliseconds, or
    /// indefinitely if `None`.
    pub(crate) fn wait(&self, timeout: Option<u32>) -> crate::Result<trusty_sys::uevent> {
        let timeout = timeout.unwrap_or(INFINITE_TIME);
        let mut uevent = MaybeUninit::zeroed();
        // SAFETY: syscall, uevent is borrowed mutably and outlives the call
        let rc = unsafe { trusty_sys::wait(self.as_raw_fd(), uevent.as_mut_ptr(), timeout) };
        if rc != 0 {
            Err(TipcError::from_uapi(rc))
        } else {
            // SAFETY: If the wait call succeeded, the uevent structure has been
            // fully initialized.
            let uevent = unsafe { uevent.assume_init() };
            Ok(uevent)
        }
    }

    /// Receive an IPC message.
    ///
    /// The `func` callback must call `trusty_sys::read_msg()` with the provided
    /// message id from `ipc_msg_info` to read the message bytes. A message is
    /// only valid for the lifetime of this callback and the message bytes
    /// should be copied into the return value, if needed.
    fn get_msg<F, R>(&self, mut func: F) -> crate::Result<R>
    where
        F: FnMut(&trusty_sys::ipc_msg_info) -> crate::Result<R>,
    {
        let mut msg_info: MaybeUninit<trusty_sys::ipc_msg_info> = MaybeUninit::uninit();

        // SAFETY: syscall, msg_info pointer is mutably borrowed and will be
        // correctly initialized if the syscall returns 0.
        let msg_info = unsafe {
            let rc = trusty_sys::get_msg(self.as_raw_fd(), msg_info.as_mut_ptr());
            if rc != 0 {
                return Err(TipcError::from_uapi(rc));
            }
            msg_info.assume_init()
        };

        let ret = func(&msg_info);

        // SAFETY: syscall with safe arguments
        let put_msg_rc = unsafe { trusty_sys::put_msg(self.as_raw_fd(), msg_info.id) };

        // prefer returning the callback error to the put_msg error, if any
        if put_msg_rc != 0 {
            Err(ret.err().unwrap_or_else(|| TipcError::from_uapi(put_msg_rc)))
        } else {
            ret
        }
    }

    /// Maps the shared memory buffer represented by this handle.
    ///
    /// If `size` is not already a multiple of the page size it will be rounded up
    /// to the nearest multiple of the page size. Use the
    /// [`len`][UnsafeSharedBuf::len] method on the returned [`UnsafeSharedBuf`] to
    /// determine the final size of the mapped buffer.
    pub fn mmap(&self, size: usize, flags: MMapFlags) -> crate::Result<UnsafeSharedBuf> {
        let prot = match flags {
            MMapFlags::Read => trusty_sys::MMAP_FLAG_PROT_READ as c_int,
            MMapFlags::Write => trusty_sys::MMAP_FLAG_PROT_WRITE as c_int,
            MMapFlags::ReadWrite => {
                (trusty_sys::MMAP_FLAG_PROT_READ | trusty_sys::MMAP_FLAG_PROT_WRITE) as c_int
            }
        };

        // SAFETY: FFI call with all safe arguments.
        let page_size = unsafe { libc::getauxval(libc::AT_PAGESZ) };

        // Round `size` up to the nearest multiple of the page size.
        let page_size: usize = page_size.try_into().unwrap();
        let size = (size + (page_size - 1)) & !(page_size - 1);

        // SAFETY: FFI call with all safe arguments.
        let buf_ptr =
            unsafe { libc::mmap(core::ptr::null_mut(), size, prot, 0, self.as_raw_fd(), 0) };

        if buf_ptr == libc::MAP_FAILED {
            Err(TipcError::InvalidHandle)
        } else {
            Ok(UnsafeSharedBuf { buf: buf_ptr as *mut u8, len: size })
        }
    }
}

impl Drop for Handle {
    fn drop(&mut self) {
        // SAFETY syscall with safe arguments
        unsafe {
            let _ = trusty_sys::close(self.as_raw_fd());
        }
    }
}

/// A serializer that borrows its input bytes and does not allocate.
#[derive(Default)]
struct BorrowingSerializer<'a> {
    buffers: Vec<&'a [u8]>,
    handles: Vec<Handle>,
}

impl<'a> Serializer<'a> for BorrowingSerializer<'a> {
    type Ok = ();
    type Error = TipcError;

    fn serialize_bytes(&mut self, bytes: &'a [u8]) -> Result<Self::Ok, Self::Error> {
        self.buffers.try_push(bytes).or(Err(TipcError::AllocError))
    }

    fn serialize_handle(&mut self, handle: &'a Handle) -> Result<Self::Ok, Self::Error> {
        self.handles.try_push(Handle(handle.as_raw_fd())).or(Err(TipcError::AllocError))
    }
}

/// Memory protection flags for [`Handle::mmap`].
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MMapFlags {
    /// The shared buffer can be read from.
    Read,

    /// The shared buffer can be written to.
    Write,

    /// The shared buffer can be read from and written to.
    ReadWrite,
}

/// A shared buffer that has been mapped into memory
///
/// # Safety
///
/// Note that all operations performed on the shared buffer must be performed
/// through a raw pointer, accessible via the [`ptr`][Self::ptr] method. Rust's
/// ownership semantics do not align with how shared buffers work, and so it
/// cannot be represented as a normal Rust slice or reference. Extra care must
/// be taken on the part of the user to ensure that all reads and writes
/// performed on the buffer are done safely.
///
/// Most notably, it is **never** safe to take a reference to data in the shared
/// buffer. All read operations must copy data from the buffer via the raw
/// pointer APIs in order to safely read shared memory.
///
/// # Unmapping
///
/// Call [`unmap`][Self::unmap] once the shared memory is no longer needed to
/// unmap the buffer. Doing this invalidates any existing pointers to the
/// buffer, so care must be taken to ensure that any such pointers are not used
/// after unmapping.
///
/// Note that the buffer is not automatically unmapped on drop. Failing to unmap
/// the buffer will leak memory until the process exits.
#[derive(Debug)]
pub struct UnsafeSharedBuf {
    buf: *mut u8,
    len: usize,
}

impl UnsafeSharedBuf {
    /// Gets the pointer to the start of the buffer.
    ///
    /// Any pointers returned by this method are invalidated once
    /// [`unmap`][Self::unmap] is called.
    pub fn ptr(&self) -> *mut u8 {
        self.buf
    }

    /// Gets the length of the buffer.
    ///
    /// Guaranteed to always be a multiple of the page size.
    pub fn len(&self) -> usize {
        self.len
    }

    /// Unmaps the shared memory buffer.
    ///
    /// Invalidates any pointers to the shared memory that were previously returned
    /// by calls to [`ptr`][Self::ptr].
    pub fn unmap(self) {
        let rc = unsafe { libc::munmap(self.buf as *mut _, self.len) };
        if rc != 0 {
            panic!("Failed to unmap shared buf");
        }
    }
}

#[cfg(test)]
pub(crate) mod test {
    use super::Handle;
    use crate::sys;
    use crate::TipcError;
    use std::sync::Once;
    use test::expect_eq;
    use trusty_sys::Error;

    // Expected limits: should be in sync with kernel settings

    /// First user handle ID
    pub const USER_BASE_HANDLE: i32 = sys::USER_BASE_HANDLE as i32;

    /// Maximum number of user handles
    pub const MAX_USER_HANDLES: i32 = sys::MAX_USER_HANDLES as i32;

    const INVALID_IPC_HANDLE: Handle = Handle(-1);

    static mut FIRST_FREE_HANDLE_INDEX: i32 = -1;
    static FIRST_FREE_HANDLE_INDEX_INIT: Once = Once::new();

    // We don't know ahead of time what the first free handle will be, so we have to
    // check and save the result the first time we need it.
    pub fn first_free_handle_index() -> i32 {
        type Channel = crate::service::Channel<crate::service::SingleDispatcher<()>>;

        FIRST_FREE_HANDLE_INDEX_INIT.call_once(|| {
            let chan = Channel::try_new_port(
                &crate::PortCfg::new("com.android.tipc.handle_probe").unwrap(),
            )
            .unwrap();

            // SAFETY: Write access is guarded by Once
            unsafe {
                FIRST_FREE_HANDLE_INDEX = chan.handle().0 - USER_BASE_HANDLE;
            }
        });

        // SAFETY: Once call above gates write access, so we know that the
        // static has been initialized at this point and will be read-only from
        // now on. Read-only access to a static i32 is safe.
        unsafe { FIRST_FREE_HANDLE_INDEX }
    }

    #[test]
    fn wait_negative() {
        let timeout = Some(1000); // 1 sec

        expect_eq!(
            INVALID_IPC_HANDLE.wait(timeout).err(),
            Some(TipcError::InvalidHandle),
            "wait on invalid handle"
        );

        //   call wait on an invalid (out of range) handle
        //
        //   check handling of the following cases:
        //     - handle is on the upper boundary of valid handle range
        //     - handle is above of the upper boundary of valid handle range
        //     - handle is below of valid handle range
        //
        //   in all cases, the expected result is ERR_BAD_HANDLE error.
        expect_eq!(
            Handle(USER_BASE_HANDLE + MAX_USER_HANDLES).wait(timeout).err(),
            Some(TipcError::InvalidHandle),
            "wait on invalid handle"
        );

        expect_eq!(
            Handle(USER_BASE_HANDLE + MAX_USER_HANDLES + 1).wait(timeout).err(),
            Some(TipcError::InvalidHandle),
            "wait on invalid handle"
        );

        expect_eq!(
            Handle(USER_BASE_HANDLE - 1).wait(timeout).err(),
            Some(TipcError::InvalidHandle),
            "wait on invalid handle"
        );

        // wait on non-existent handle in valid range
        for i in first_free_handle_index()..MAX_USER_HANDLES {
            expect_eq!(
                Handle(USER_BASE_HANDLE + i).wait(timeout).err(),
                Some(TipcError::SystemError(Error::NotFound)),
                "wait on invalid handle"
            );
        }
    }
}
