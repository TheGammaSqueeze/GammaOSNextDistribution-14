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

//! Interface library for communicating with the hwbcc service.
//!
//! Where these interfaces require user-supplied buffers, it is
//! important for the buffers supplied to be large enough to
//! contain the entirety of the hwbcc service response. All services
//! provided are subject to tipc failures; the corresponding error
//! codes will be returned in these cases.

#![feature(allocator_api)]

#[cfg(test)]
mod test;

mod err;

#[allow(non_upper_case_globals)]
#[allow(non_camel_case_types)]
#[allow(non_snake_case)]
#[allow(unused)]
#[allow(deref_nullptr)] // https://github.com/rust-lang/rust-bindgen/issues/1651
mod sys {
    include!(env!("BINDGEN_INC_FILE"));
}

pub use err::HwBccError;

use core::ffi::CStr;
use core::mem;
use sys::*;
use tipc::Serializer;
use tipc::{Deserialize, Handle, Serialize};
use trusty_std::alloc::{TryAllocFrom, Vec};
use trusty_sys::{c_long, Error};

// Constant defined in trusty/user/base/interface/hwbcc/include/interface/hwbcc
pub const HWBCC_MAX_RESP_PAYLOAD_LENGTH: usize = HWBCC_MAX_RESP_PAYLOAD_SIZE as usize;

#[derive(Copy, Clone)]
#[repr(u32)]
enum BccCmd {
    RespBit = hwbcc_cmd_HWBCC_CMD_RESP_BIT,
    SignData = hwbcc_cmd_HWBCC_CMD_SIGN_DATA,
    GetBcc = hwbcc_cmd_HWBCC_CMD_GET_BCC,
    GetDiceArtifacts = hwbcc_cmd_HWBCC_CMD_GET_DICE_ARTIFACTS,
    NsDeprivilege = hwbcc_cmd_HWBCC_CMD_NS_DEPRIVILEGE,
}

impl BccCmd {
    fn validate_response(self, resp: u32) -> Result<(), HwBccError> {
        if resp != self as u32 | BccCmd::RespBit as u32 {
            log::error!("unknown response cmd: {:?}", resp);
            return Err(HwBccError::InvalidCmdResponse);
        }

        Ok(())
    }
}
/// Generic header for all hwbcc requests.
struct BccMsgHeader {
    cmd: BccCmd,
    test_mode: HwBccMode,
    context: u64,
}

impl<'s> Serialize<'s> for BccMsgHeader {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        // SAFETY:
        //  All serialized attributes are trivial types with
        //  corresponding C representations
        unsafe {
            serializer.serialize_as_bytes(&self.cmd)?;
            serializer.serialize_as_bytes(&self.test_mode)?;
            serializer.serialize_as_bytes(&self.context)
        }
    }
}

/// Request to sign data.
struct SignDataMsg<'a> {
    header: BccMsgHeader,
    /// Contains signing algorithm, data size, aad size
    algorithm: SigningAlgorithm,
    data: &'a [u8],
    // size is needed for reference in serialization
    data_size: u16,
    aad: &'a [u8],
    // size is needed for reference in serialization
    aad_size: u32,
}

impl<'a> SignDataMsg<'a> {
    fn new(
        header: BccMsgHeader,
        algorithm: SigningAlgorithm,
        data: &'a [u8],
        aad: &'a [u8],
    ) -> Self {
        Self {
            header,
            algorithm,
            data,
            data_size: data.len() as u16,
            aad,
            aad_size: aad.len() as u32,
        }
    }
}

impl<'s> Serialize<'s> for SignDataMsg<'s> {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        self.header.serialize(serializer)?;
        // SAFETY:
        //  All serialized attributes are trivial types with
        //  corresponding C representations
        unsafe {
            serializer.serialize_as_bytes(&self.algorithm)?;
            serializer.serialize_as_bytes(&self.data_size)?;
            serializer.serialize_as_bytes(&self.aad_size)?;
        }
        serializer.serialize_bytes(self.data)?;
        serializer.serialize_bytes(self.aad)
    }
}

/// Response type for all hwbcc services.
struct HwBccResponse {
    /// Status of command result.
    status: i32,
    /// Sent command, acknowledged by service if successful.
    cmd: u32,
    /// Response data.
    payload: Vec<u8>,
}

impl Deserialize for HwBccResponse {
    type Error = HwBccError;
    const MAX_SERIALIZED_SIZE: usize = HWBCC_MAX_RESP_PAYLOAD_LENGTH;

    fn deserialize(bytes: &[u8], handles: &mut [Option<Handle>]) -> Result<Self, Self::Error> {
        if handles.len() != 0 {
            for handle in handles {
                log::error!("unexpected handle: {:?}", handle);
            }
            return Err(HwBccError::InvalidCmdResponse);
        }

        let header_size = mem::size_of::<hwbcc_resp_hdr>();
        if bytes.len() < header_size {
            log::error!("response too small");
            return Err(HwBccError::BadLen);
        }
        // SAFETY: We have validated that the buffer contains enough data to
        // represent a hwbcc_resp_hdr. The constructed lifetime here does not
        // outlive the function and thus cannot outlive the lifetime of the
        // buffer.
        let (header, payload) = bytes.split_at(header_size);
        let (prefix, header_body, _) = unsafe { header.align_to::<hwbcc_resp_hdr>() };
        if !prefix.is_empty() {
            log::error!("buffer too short or misaligned");
            return Err(HwBccError::BadLen);
        }
        let msg: &hwbcc_resp_hdr = &header_body[0];
        let response_payload = Vec::try_alloc_from(payload)?;

        if msg.payload_size as usize != response_payload.len() {
            log::error!("response payload size is not as advertised");
            return Err(HwBccError::BadLen);
        }

        Ok(Self { status: msg.status, cmd: msg.cmd, payload: response_payload })
    }
}

/// Specifies test or release request types.
///
/// `Test` mode derives key seed bytes with a secure RNG,
/// and should differ with each invocation, intra-test.
/// `Release` mode relies on the hwkey service to derive
/// its key seed.
#[derive(Copy, Clone, Debug)]
#[repr(u32)]
pub enum HwBccMode {
    Release = 0,
    Test = 1,
}

/// Signing algorithm options.
///
/// Project uses CBOR Object Signing and Encryption (COSE) encodings.
#[non_exhaustive]
#[derive(Copy, Clone, Debug)]
#[repr(i16)]
pub enum SigningAlgorithm {
    ED25519 = hwbcc_algorithm_HWBCC_ALGORITHM_ED25519 as i16,
}

fn recv_resp(session: &Handle, cmd: BccCmd, buf: &mut [u8]) -> Result<HwBccResponse, HwBccError> {
    let response: HwBccResponse = session.recv(buf)?;

    cmd.validate_response(response.cmd)?;

    if response.status != 0 {
        log::error!("Status is not SUCCESS. Actual: {:?}", response.status);
        return Err(HwBccError::System(Error::from(response.status as c_long)));
    }

    Ok(response)
}

fn read_payload(resp: HwBccResponse, buf: &mut [u8]) -> Result<&[u8], HwBccError> {
    let payload_size = resp.payload.len();
    if payload_size > buf.len() {
        log::error!("response payload is too large to fit into buffer");
        return Err(HwBccError::BadLen);
    }

    buf[..payload_size].copy_from_slice(&resp.payload);
    Ok(&buf[..payload_size])
}

/// DICE artifacts for a child node in the DICE chain/tree.
pub struct DiceArtifacts<'a> {
    pub artifacts: &'a [u8],
}

/// Retrieves DICE artifacts for a child node in the DICE chain/tree.
///
/// The user supplies device-specific `context` as well as an
/// `artifacts` buffer, in which the service will write a portion
/// of its response payload.
///
/// # Returns
///
/// A [`DiceArtifacts`] result containing truncated prefixes of the populated
/// `artifacts` buffer. A [`HwBccError`] will be
/// returned if the user supplies an empty `artifacts` buffer.
///
/// # Examples
///
/// ```
/// let dice_artifacts_buf = &mut [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];
/// let DiceArtifacts { artifacts } =
///     get_dice_artifacts(0, dice_artifacts_buf).expect("could not get protected data");
/// ```
///
pub fn get_dice_artifacts<'a>(
    context: u64,
    artifacts: &'a mut [u8],
) -> Result<DiceArtifacts<'a>, HwBccError> {
    if artifacts.is_empty() {
        log::error!("DICE artifacts buffer must not be empty");
        return Err(HwBccError::BadLen);
    }

    let port = CStr::from_bytes_with_nul(HWBCC_PORT).expect("HWBCC_PORT was not null terminated");
    let session = Handle::connect(port)?;

    let cmd = BccCmd::GetDiceArtifacts;
    session.send(&BccMsgHeader { cmd, test_mode: HwBccMode::Release, context })?;

    let res_buf = &mut [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];
    let response = recv_resp(&session, cmd, res_buf)?;
    let artifacts = read_payload(response, artifacts)?;

    Ok(DiceArtifacts { artifacts })
}

/// Deprivileges hwbcc from serving calls to non-secure clients.
///
/// # Returns
///
/// Err(HwBccError) on failure.
///
/// # Examples
///
/// ```
/// ns_deprivilege().expect("could not execute ns deprivilege");
///
/// // assuming non-secure client
/// let dice_artifacts_buf = &mut [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];
/// let err =
///     get_dice_artifacts(0, dice_artifacts_buf).expect_err("non-secure client has access to hwbcc services");
/// ```
///
pub fn ns_deprivilege() -> Result<(), HwBccError> {
    let port = CStr::from_bytes_with_nul(HWBCC_PORT).expect("HWBCC_PORT was not null terminated");
    let session = Handle::connect(port)?;

    let cmd = BccCmd::NsDeprivilege;
    session.send(&BccMsgHeader { cmd, test_mode: HwBccMode::Release, context: 0 })?;

    let res_buf = &mut [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];
    recv_resp(&session, cmd, res_buf)?;

    Ok(())
}

/// Retrieves Boot certificate chain (BCC).
/// Clients may request test values using `test_mode`.
///
/// # Examples
///
/// ```
/// let mut cose_sign1_buf = [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];
///
/// let bcc = get_bcc (
///     HwBccMode::Test,
///     &mut bcc_buf,
/// )
/// .expect("could not get bcc");
/// ```
pub fn get_bcc<'a>(test_mode: HwBccMode, bcc: &'a mut [u8]) -> Result<&'a [u8], HwBccError> {
    if bcc.is_empty() {
        log::error!("bcc buffer must not be empty");
        return Err(HwBccError::BadLen);
    }

    let port = CStr::from_bytes_with_nul(HWBCC_PORT).expect("HWBCC_PORT was not null terminated");
    let session = Handle::connect(port)?;

    let cmd = BccCmd::GetBcc;
    session.send(&BccMsgHeader { cmd, test_mode, context: 0 })?;

    let res_buf = &mut [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];
    let response = recv_resp(&session, cmd, res_buf)?;
    let bcc = read_payload(response, bcc)?;

    Ok(bcc)
}

/// Retrieves the signed data in a COSE-Sign1 message. Data signed using the CDI leaf private key.
/// Clients may request to sign using a test key via test_mode.
///
/// # Examples
///
/// ```
/// let mut cose_sign1_buf = [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];
///
/// let cose_sign1 = sign_data(
///     HwBccMode::Test,
///     SigningAlgorithm::ED25519,
///     TEST_MAC_KEY,
///     TEST_AAD,
///     &mut cose_sign1_buf,
/// )
/// .expect("could not get signed data");
/// ```
pub fn sign_data<'a>(
    test_mode: HwBccMode,
    cose_algorithm: SigningAlgorithm,
    data: &[u8],
    aad: &[u8],
    cose_sign1: &'a mut [u8],
) -> Result<&'a [u8], HwBccError> {
    if cose_sign1.is_empty() {
        log::error!("cose_sign1 buffer must not be empty");
        return Err(HwBccError::BadLen);
    }

    if aad.len() > HWBCC_MAX_AAD_SIZE as usize {
        log::error!("AAD exceeds HWCC_MAX_AAD_SIZE limit");
        return Err(HwBccError::BadLen);
    }

    let port = CStr::from_bytes_with_nul(HWBCC_PORT).expect("HWBCC_PORT was not null terminated");
    let session = Handle::connect(port)?;

    let cmd = BccCmd::SignData;
    let req =
        SignDataMsg::new(BccMsgHeader { cmd, test_mode, context: 0 }, cose_algorithm, data, aad);
    session.send(&req)?;

    let res_buf = &mut [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];
    let response = recv_resp(&session, cmd, res_buf)?;
    let cose_sign1 = read_payload(response, cose_sign1)?;

    Ok(cose_sign1)
}
