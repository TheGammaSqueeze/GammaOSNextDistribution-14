#![cfg(test)]

use crate::sys::{lender_msg, lender_region};
use core::ffi::CStr;
use tipc::{Deserialize, Handle, MMapFlags, Serialize, Serializer, TipcError, UnsafeSharedBuf};

#[allow(bad_style)]
#[allow(dead_code)] // Needed because not all variants of the `lender_command` enum are used.
#[allow(deref_nullptr)] // https://github.com/rust-lang/rust-bindgen/issues/1651
mod sys {
    include!(env!("BINDGEN_INC_FILE"));
}

test::init!();

const LENDER_PORT: &[u8] = b"com.android.memref.lender\0";

#[test]
fn recv_ref() {
    // Connect to the lender service.
    let port = CStr::from_bytes_with_nul(LENDER_PORT).unwrap();
    let lender = Handle::connect(port).unwrap();

    // Request the shared buffer from the lender service.
    let remote_handle = request_remote_buf(&lender);

    // Try to mmap the shared buffer into process memory.
    //
    // NOTE: Try to map with size 2 in order to test the logic for rounding up to a
    // multiple of the page size. The lender service will always allocate a buffer
    // of exactly one page, but we only ever read/write the first two bytes, so this
    // should still map correctly.
    let remote_buf =
        remote_handle.mmap(2, MMapFlags::ReadWrite).expect("Failed to map the shared buffer");

    // Run the main test logic.
    test_read_write(&lender, remote_buf);
}

#[test]
fn drop_shared_buf_handle() {
    // Connect to the lender service.
    let port = CStr::from_bytes_with_nul(LENDER_PORT).unwrap();
    let lender = Handle::connect(port).unwrap();

    // Request the shared buffer from the lender service.
    let remote_handle = request_remote_buf(&lender);

    // Map the buffer into memory and then drop the `Handle` associated with it.
    // This should not invalidate the buffer.
    let remote_buf =
        remote_handle.mmap(2, MMapFlags::ReadWrite).expect("Failed to map the shared buffer");
    std::mem::drop(remote_handle);

    // Run the main test logic to verify that the shared buffer is still valid after
    // closing the associated handle.
    test_read_write(&lender, remote_buf);
}

/// Makes the initial request to the lender service for the remote buffer.
fn request_remote_buf(lender: &Handle) -> Handle {
    // Send a command to the lender service telling it we want to receive a shared
    // memory buffer.
    lender
        .send(&lender_msg { cmd: sys::lender_command_LENDER_LEND_BSS, region: Default::default() })
        .unwrap();

    // Receive the memref from the lender service.
    let recv_buf = &mut [0; 0][..];
    let resp = lender.recv::<MemrefResponse>(recv_buf).unwrap();

    resp.handle
}

/// Runs the logic to test writing to and reading from the shared buffer.
fn test_read_write(lender: &Handle, remote_buf: UnsafeSharedBuf) {
    // Check the initial state of the remote buffer after mapping.
    //
    // SAFETY: Reading a single `u8` from the remote buffer.
    assert_eq!(4096, remote_buf.len());
    assert_eq!(0, unsafe { remote_buf.ptr().read() });

    // Write to the shared buffer and then ask the lender service to read from the
    // buffer and send back to us the value it sees.
    //
    // SAFETY: Writing a single `u8` to the shared buffer.
    unsafe { remote_buf.ptr().write(7) };

    lender
        .send(&lender_msg {
            cmd: sys::lender_command_LENDER_READ_BSS,
            region: lender_region { offset: 0, size: 1 },
        })
        .unwrap();

    let recv_buf = &mut [0; 1][..];
    let resp = lender.recv::<ReadResponse>(recv_buf).unwrap();

    // Verify that the lender service read the same value that we wrote.
    assert_eq!(7, resp.value);

    // Send the lender service a value to write into the buffer. We tell it to write
    // into the second byte of the buffer so that we can also verify that the first
    // byte is not modified.
    lender
        .send(&WriteRequest {
            msg: lender_msg {
                cmd: sys::lender_command_LENDER_WRITE_BSS,
                region: lender_region { offset: 1, size: 1 },
            },
            value: 123,
        })
        .unwrap();

    let recv_buf = &mut [0; 0][..];
    lender.recv::<WriteResponse>(recv_buf).unwrap();

    // Verify that the value we sent was written to the specified offset in the
    // shared buffer.
    //
    // SAFETY: Reading the first two bytes of the shared buffer.
    assert_eq!(7, unsafe { remote_buf.ptr().read() });
    assert_eq!(123, unsafe { remote_buf.ptr().offset(1).read() });

    // Reset the buffer since it's shared between test runs.
    //
    // SAFETY: Writing to the first two bytes of the shared buffer.
    unsafe {
        remote_buf.ptr().write(0);
        remote_buf.ptr().offset(1).write(0);
    }

    remote_buf.unmap();
}

impl<'s> Serialize<'s> for lender_msg {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        // SAFETY: `lender_msg` is generated from the C header and already matches the
        // expected layout.
        unsafe { serializer.serialize_as_bytes(self) }
    }
}

impl Default for lender_region {
    fn default() -> Self {
        lender_region { offset: 0, size: 0 }
    }
}

/// Response type for the `LENDER_LEND_BSS` command.
struct MemrefResponse {
    handle: Handle,
}

impl Deserialize for MemrefResponse {
    type Error = TipcError;
    const MAX_SERIALIZED_SIZE: usize = 0;

    fn deserialize(_bytes: &[u8], handles: &mut [Option<Handle>]) -> Result<Self, Self::Error> {
        assert_eq!(1, handles.len());
        let handle = handles[0].take().unwrap();
        Ok(MemrefResponse { handle })
    }
}

/// Response type for the `LENDER_READ_BSS` command.
struct ReadResponse {
    value: u8,
}

impl Deserialize for ReadResponse {
    type Error = TipcError;
    const MAX_SERIALIZED_SIZE: usize = 1;

    fn deserialize(bytes: &[u8], _handles: &mut [Option<Handle>]) -> Result<Self, Self::Error> {
        Ok(ReadResponse { value: bytes[0] })
    }
}

/// Request type for the `LENDER_WRITE_BSS` command, which includes an additional
/// byte value to write.
struct WriteRequest {
    msg: lender_msg,
    value: u8,
}

impl<'s> Serialize<'s> for WriteRequest {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        self.msg.serialize(serializer)?;

        // SAFETY: Serializing a single `u8` value is always safe.
        unsafe { serializer.serialize_as_bytes(&self.value) }
    }
}

/// Empty response type for the `LENDER_WRITE_BSS` command.
struct WriteResponse;

impl Deserialize for WriteResponse {
    type Error = TipcError;
    const MAX_SERIALIZED_SIZE: usize = 0;

    fn deserialize(_bytes: &[u8], _handles: &mut [Option<Handle>]) -> Result<Self, Self::Error> {
        Ok(WriteResponse)
    }
}
