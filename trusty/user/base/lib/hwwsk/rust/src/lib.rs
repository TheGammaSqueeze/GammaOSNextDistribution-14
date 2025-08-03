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

#![no_std]
#![feature(allocator_api)]

mod err;

#[cfg(test)]
mod test;

#[allow(non_upper_case_globals)]
#[allow(non_camel_case_types)]
#[allow(unused)]
#[allow(deref_nullptr)] // https://github.com/rust-lang/rust-bindgen/issues/1651
mod sys {
    include!(env!("BINDGEN_INC_FILE"));
}

pub use err::HwWskError;
pub use sys::HWWSK_MAX_MSG_SIZE;

use core::mem;
use sys::*;
use tipc::{Deserialize, Handle, Serialize, Serializer};
use trusty_std::alloc::{TryAllocFrom, Vec};

/// The command sent to the hwwsk service.
pub enum HwWskCmd {
    /// Generate (or import) a persistent storage key.
    ///
    /// Result is wrapped using a device-unique key.
    Generate(GenerateKeyReq),
    /// Re-wrap a non-ephemeral wrapped key with ephemeral storage key.
    ///
    /// Result is a key that is only good for the current session.
    Export(ExportKeyReq),
}

/// A request to the hwwsk service.
pub struct HwWskReq {
    hdr: hwwsk_req_hdr,
    /// The command that is requested.
    pub req: HwWskCmd,
}

impl HwWskReq {
    pub fn response_from(&self, status: u32, payload: Vec<u8>) -> HwWskResponse {
        HwWskResponse { status, cmd: self.hdr.cmd | hwwsk_cmd_HWWSK_CMD_RESP as u32, payload }
    }
}

impl<'s> Serialize<'s> for HwWskReq {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        self.hdr.cmd.serialize(serializer)?;
        self.hdr.flags.serialize(serializer)?;

        match &self.req {
            HwWskCmd::Generate(g) => g.serialize(serializer),
            HwWskCmd::Export(e) => e.serialize(serializer),
        }
    }
}

impl Deserialize for HwWskReq {
    type Error = HwWskError;
    const MAX_SERIALIZED_SIZE: usize = HWWSK_MAX_MSG_SIZE as usize;

    fn deserialize(bytes: &[u8], handles: &mut [Option<Handle>]) -> Result<Self, Self::Error> {
        let header_size = mem::size_of::<hwwsk_req_hdr>();
        if bytes.len() < header_size {
            log::error!("response too small");
            return Err(HwWskError::BadLen);
        }
        // SAFETY: We have validated that the buffer contains enough data to
        // represent a hwwsk_req_hdr. The constructed lifetime here does not
        // outlive the function and thus cannot outlive the lifetime of the
        // buffer.
        let hdr = unsafe { &*(bytes.as_ptr() as *const hwwsk_req_hdr) };

        let req = {
            #[allow(non_upper_case_globals)]
            match hdr.cmd as hwwsk_cmd {
                hwwsk_cmd_HWWSK_CMD_GENERATE_KEY => Ok(HwWskCmd::Generate(
                    GenerateKeyReq::deserialize(&bytes[header_size..], handles)?,
                )),
                hwwsk_cmd_HWWSK_CMD_EXPORT_KEY => {
                    Ok(HwWskCmd::Export(ExportKeyReq::deserialize(&bytes[header_size..], handles)?))
                }
                cmd => {
                    log::error!("unrecognized command request: {:?}", cmd);
                    Err(HwWskError::NotValid)
                }
            }
        }?;
        Ok(Self { hdr: hwwsk_req_hdr { cmd: hdr.cmd, flags: hdr.flags }, req })
    }
}

/// Request to generate a persistent storage key. If the supplied key is empty,
/// a new key will be generated. Otherwise, the provided data will be
/// imported as a raw key.
pub struct GenerateKeyReq {
    req: hwwsk_generate_key_req,
    raw_key: Vec<u8>,
}

impl<'s> Serialize<'s> for GenerateKeyReq {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        // SAFETY:
        //  All serialized attributes are trivial types with
        //  corresponding C representations
        unsafe {
            serializer.serialize_as_bytes(&self.req.key_size)?;
            serializer.serialize_as_bytes(&self.req.key_flags)?;
        }

        serializer.serialize_bytes(&self.raw_key)
    }
}

impl Deserialize for GenerateKeyReq {
    type Error = HwWskError;
    const MAX_SERIALIZED_SIZE: usize = HWWSK_MAX_MSG_SIZE as usize;

    fn deserialize(bytes: &[u8], _handles: &mut [Option<Handle>]) -> Result<Self, Self::Error> {
        let header_size = mem::size_of::<hwwsk_generate_key_req>();
        if bytes.len() < header_size {
            log::error!("response too small");
            return Err(HwWskError::BadLen);
        }
        // SAFETY: We have validated that the buffer contains enough data to
        // represent a hwwsk_generate_key_req. The constructed lifetime here does not
        // outlive the function and thus cannot outlive the lifetime of the
        // buffer.
        let hwwsk_generate_key_req { key_size, key_flags } =
            unsafe { &*(bytes.as_ptr() as *const hwwsk_generate_key_req) };

        Ok(Self {
            req: hwwsk_generate_key_req { key_size: *key_size, key_flags: *key_flags },
            raw_key: Vec::try_alloc_from(&bytes[header_size..])?,
        })
    }
}

/// A request to re-wrap a non-ephemeral key using an ephemeral storage key.
/// The resulting key is only good for the current session.
pub struct ExportKeyReq {
    key_blob: Vec<u8>,
}

impl<'s> Serialize<'s> for ExportKeyReq {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        serializer.serialize_bytes(&self.key_blob)
    }
}

impl Deserialize for ExportKeyReq {
    type Error = HwWskError;
    const MAX_SERIALIZED_SIZE: usize = HWWSK_MAX_MSG_SIZE as usize;

    fn deserialize(bytes: &[u8], _handles: &mut [Option<Handle>]) -> Result<Self, Self::Error> {
        Ok(Self { key_blob: Vec::try_alloc_from(bytes)? })
    }
}

/// Response from hwwsk service.
pub struct HwWskResponse {
    /// Sent command, acknowledged by service if successful.
    cmd: u32,
    /// Status of command result.
    status: u32,
    /// Response data.
    payload: Vec<u8>,
}

impl<'s> Serialize<'s> for HwWskResponse {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        // SAFETY:
        //  All serialized attributes are trivial types with
        //  corresponding C representations
        unsafe {
            serializer.serialize_as_bytes(&self.cmd)?;
            serializer.serialize_as_bytes(&self.status)?;
        }

        serializer.serialize_bytes(&self.payload)
    }
}

impl Deserialize for HwWskResponse {
    type Error = HwWskError;
    const MAX_SERIALIZED_SIZE: usize = HWWSK_MAX_MSG_SIZE as usize;

    fn deserialize(bytes: &[u8], _handles: &mut [Option<Handle>]) -> Result<Self, Self::Error> {
        // response must at least contain cmd and status
        let header_size = mem::size_of::<u32>() * 2;
        if bytes.len() < header_size {
            log::error!("response too small");
            return Err(HwWskError::BadLen);
        }
        let (cmd_bytes, rest) = bytes.split_at(mem::size_of::<u32>());
        let (status_bytes, payload_bytes) = rest.split_at(mem::size_of::<u32>());

        let status = u32::from_ne_bytes(status_bytes.try_into()?);
        let cmd = u32::from_ne_bytes(cmd_bytes.try_into()?);

        let response_payload = Vec::try_alloc_from(payload_bytes)?;
        Ok(Self { status, cmd, payload: response_payload })
    }
}

// TODO: consider trusty util for this across client implementations
fn validate_cmd(sent_cmd: &u32, recvd_cmd: &u32) -> Result<(), HwWskError> {
    let validated = *recvd_cmd == (sent_cmd | hwwsk_cmd_HWWSK_CMD_RESP as u32);
    if !validated {
        log::error!(
            "unknown response cmd: {:?}/{:?}",
            recvd_cmd,
            (sent_cmd | hwwsk_cmd_HWWSK_CMD_RESP as u32)
        );
        return Err(HwWskError::InvalidCmdResponse);
    }

    Ok(())
}

/// A combination of flags can be passed to [`generate_key`].
pub struct KeyFlags {
    flags: u32,
}

impl KeyFlags {
    pub fn new() -> Self {
        Self { flags: 0 }
    }

    /// Indicates that the resulting key must be rollback resistant.
    pub fn rollback_resistance(mut self) -> Self {
        self.flags |= hwwsk_key_flags_HWWSK_FLAGS_ROLLBACK_RESISTANCE as u32;
        self
    }
}

/// Creates a new persistent key from provided raw key material.
///
/// This routine creates a new hardware wrapped storage key by
/// either importing raw key material that's specified by the caller.
/// The resulting key is persistent and reusable across device reset.
///
/// # Arguments
///
/// * `session` - IPC channel to HWWSK service
/// * `buf` - buffer to store resulting key blob
/// * `key_size` - key size in bits
/// * `key_flags` - a combination of [`KeyFlags`] to specify any additional
///     properties of the generated key
/// * `raw_key` - the buffer containing raw key data for import operation
///
/// # Returns
///
/// A truncated view into `buf` where the wrapped key data was populated.
///
pub fn import_key<'a>(
    session: &Handle,
    buf: &'a mut [u8],
    key_size: usize,
    key_flags: KeyFlags,
    raw_key: &[u8],
) -> Result<&'a [u8], HwWskError> {
    if raw_key.is_empty() {
        return Err(HwWskError::BadLen);
    }
    create_key(session, buf, key_size, key_flags, raw_key)
}

/// Creates a new persistent key.
///
/// This routine creates a new hardware wrapped storage key by
/// generating a new random key The resulting key is persistent and
/// reusable across device reset.
///
/// # Arguments
///
/// * `session` - IPC channel to HWWSK service
/// * `buf` - buffer to store resulting key blob
/// * `key_size` - key size in bits
/// * `key_flags` - a combination of [`KeyFlags`] to specify any additional
///     properties of the generated key
///
/// # Returns
///
/// A truncated view into `buf` where the wrapped key data was populated.
///
pub fn generate_key<'a>(
    session: &Handle,
    buf: &'a mut [u8],
    key_size: usize,
    key_flags: KeyFlags,
) -> Result<&'a [u8], HwWskError> {
    create_key(session, buf, key_size, key_flags, &[])
}

fn create_key<'a>(
    session: &Handle,
    buf: &'a mut [u8],
    key_size: usize,
    key_flags: KeyFlags,
    raw_key: &[u8],
) -> Result<&'a [u8], HwWskError> {
    let cmd = hwwsk_cmd_HWWSK_CMD_GENERATE_KEY as u32;
    let req = HwWskReq {
        hdr: hwwsk_req_hdr { cmd, flags: 0 },
        req: HwWskCmd::Generate(GenerateKeyReq {
            req: hwwsk_generate_key_req { key_size: key_size as u32, key_flags: key_flags.flags },
            raw_key: Vec::try_alloc_from(raw_key)?,
        }),
    };

    session.send(&req)?;

    let resp_buf = &mut [0; HWWSK_MAX_MSG_SIZE as usize];
    let response: HwWskResponse = session.recv(resp_buf)?;

    validate_cmd(&cmd, &response.cmd)?;
    HwWskError::from_status(response.status)?;

    if buf.len() < response.payload.len() {
        log::error!("response payload is too large to fit into the buffer");
        return Err(HwWskError::BadLen);
    }

    let res_buffer = &mut buf[..response.payload.len()];
    res_buffer.copy_from_slice(&response.payload);

    Ok(res_buffer)
}

/// Rewrap specified SK key with ESK.
///
/// This routine rewraps a specified persistent SK key with an ephemeral
/// storage key (ESK). The resulting key is only good for the current
/// session.
///
/// # Arguments
///
/// * `session` - IPC channel to HWWSK service
/// * `buf` - buffer to store the resulting key blob
/// * `key_blob` - the key blob to unwrap
///
/// # Returns
///
/// A truncated view into `buf` where the rewrrapped key data was populated.
///
pub fn export_key<'a>(
    session: &Handle,
    buf: &'a mut [u8],
    key_blob: &[u8],
) -> Result<&'a [u8], HwWskError> {
    let cmd = hwwsk_cmd_HWWSK_CMD_EXPORT_KEY as u32;
    let req = HwWskReq {
        hdr: hwwsk_req_hdr { cmd, flags: 0 },
        req: HwWskCmd::Export(ExportKeyReq { key_blob: Vec::try_alloc_from(key_blob)? }),
    };

    session.send(&req)?;

    let resp_buf = &mut [0; HWWSK_MAX_MSG_SIZE as usize];
    let response: HwWskResponse = session.recv(resp_buf)?;

    validate_cmd(&cmd, &response.cmd)?;
    HwWskError::from_status(response.status)?;

    if buf.len() < response.payload.len() {
        log::error!("response payload is too large to fit into the buffer");
        return Err(HwWskError::BadLen);
    }

    let res_buffer = &mut buf[..response.payload.len()];
    res_buffer.copy_from_slice(&response.payload);

    Ok(res_buffer)
}
