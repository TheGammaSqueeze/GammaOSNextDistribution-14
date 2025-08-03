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

//! # Interface library for communicating with the hwkey service.

#![no_std]
#![feature(allocator_api)]

#[allow(non_upper_case_globals)]
#[allow(non_camel_case_types)]
#[allow(unused)]
#[allow(deref_nullptr)] // https://github.com/rust-lang/rust-bindgen/issues/1651
mod sys {
    include!(env!("BINDGEN_INC_FILE"));
}

mod err;
#[cfg(test)]
mod test;

use core::ffi::CStr;
use core::mem;
pub use err::HwkeyError;
use sys::*;
use tipc::{Deserialize, Handle, Serialize, Serializer, TipcError};
use trusty_std::alloc::{TryAllocFrom, Vec};

/// A HwkeySession is a Handle.
type HwkeySession = Handle;

/// Connection to the hwkey service.
#[derive(Debug, Eq, PartialEq)]
pub struct Hwkey(HwkeySession);

impl Hwkey {
    /// Attempt to open a hwkey session.
    ///
    /// # Examples
    ///
    /// ```
    /// let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    /// ```
    ///
    pub fn open() -> Result<Self, TipcError> {
        let port =
            CStr::from_bytes_with_nul(HWKEY_PORT).expect("HWKEY_PORT was not null terminated");
        HwkeySession::connect(port).map(Self)
    }

    fn validate_cmd(sent_cmd: &u32, recvd_cmd: &u32) -> bool {
        *recvd_cmd == (sent_cmd | hwkey_cmd_HWKEY_RESP_BIT as u32)
    }

    /// Starts a request to derive a key from a context.
    ///
    /// # Returns
    ///
    /// A [`DerivedKeyRequest`] request builder
    /// functionality.
    ///
    /// # Examples
    ///
    /// ```
    /// let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    /// let request_builder = hwkey_session.derive_key_req();
    /// ```
    ///
    pub fn derive_key_req(&self) -> DerivedKeyRequest {
        DerivedKeyRequest::new(&self)
    }

    /// Gets the keyslot data referenced by slot_id.
    ///
    /// # Arguments
    ///
    /// * `slot_id` - The name of the keyslot, must be null-terminated.
    /// * `keyslot_data` - The buffer into which the keyslot data will be populated.
    ///
    /// # Returns
    ///
    /// A truncated prefix of the input keyslot_data buffer that contains
    /// the key bytes.
    ///
    /// # Examples
    ///
    /// ```
    /// let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    /// let buf = &mut [0u8; 2048 as usize];
    /// let keyslot = CStr::from_bytes_with_nul(b"keyslot_name\0").unwrap();
    /// let keyslot_data =
    ///     hwkey_session.get_keyslot_data(keyslot, buf).expect("could not retrieve keyslot data");
    /// ```
    ///
    pub fn get_keyslot_data<'a>(
        &self,
        slot_id: &CStr,
        keyslot_data: &'a mut [u8],
    ) -> Result<&'a [u8], HwkeyError> {
        let slot_id = slot_id.to_bytes();

        // slot_id is at least one byte because of null byte;
        // check for empty slot_id string or empty keyslot_data
        if slot_id.len() <= 0 {
            log::error!("slot_id cannot be an empty string");
            return Err(HwkeyError::NotValid);
        }

        if keyslot_data.is_empty() {
            log::error!("keyslot_data cannot be empty");
            return Err(HwkeyError::NotValid);
        }

        let cmd = hwkey_cmd_HWKEY_GET_KEYSLOT as u32;

        let req_msg = hwkey_msg {
            header: hwkey_msg_header { cmd, op_id: 0u32, status: 0 },
            arg1: 0u32,
            arg2: 0u32,
            payload: __IncompleteArrayField::new(),
        };

        self.0.send(&HwkeyMsg { msg: req_msg, request: slot_id })?;
        let buf = &mut [0; HWKEY_MAX_MSG_SIZE as usize];
        let response: HwkeyResponse = self.0.recv(buf)?;

        if !Self::validate_cmd(&cmd, &response.cmd) {
            log::error!("unknown response cmd: {:?}", response.cmd);
            return Err(HwkeyError::InvalidCmdResponse);
        }

        HwkeyError::from_hwkey_rc(response.status)?;

        if keyslot_data.len() < response.payload.len() {
            log::error!(
                "keyslot data len ({:?}) < response payload len ({:?})",
                keyslot_data.len(),
                response.payload.len()
            );
            return Err(HwkeyError::BadLen);
        }

        keyslot_data[..response.payload.len()].copy_from_slice(&response.payload[..]);
        Ok(&keyslot_data[..response.payload.len()])
    }

    /// Derive a versioned, device-specific key from provided context.
    ///
    /// # Arguments
    ///
    /// * `src` - The context from which the key will be derived. If
    /// empty, `key_buf` must be empty as well.
    /// * `key_buf` - The buffer into which the key will be written. If
    /// empty, no key will be generated and only the current versions
    /// may be queried.
    /// * `args` - Key derivation options.
    ///
    /// # Returns
    ///
    /// The DeriveResult containing information used in derivation.
    ///
    fn derive(
        &self,
        src: &[u8],
        key_buf: &mut [u8],
        args: DerivedKeyRequest,
    ) -> Result<DeriveResult, HwkeyError> {
        if src.len() == 0 && key_buf.len() != 0 {
            log::error!("if key context is empty, key buffer must also be empty");
            return Err(HwkeyError::NotValid);
        }

        const HEADER_SIZE: usize = mem::size_of::<hwkey_derive_versioned_msg>();
        const MAX_PAYLOAD_LEN: usize = HWKEY_MAX_MSG_SIZE as usize - HEADER_SIZE;

        if src.len() > MAX_PAYLOAD_LEN {
            log::error!("src context length ({:?}) > ({:?})", src.len(), MAX_PAYLOAD_LEN);
            return Err(HwkeyError::BadLen);
        }

        if key_buf.len() > MAX_PAYLOAD_LEN {
            log::error!("key buffer length ({:?}) > ({:?})", key_buf.len(), MAX_PAYLOAD_LEN);
            return Err(HwkeyError::BadLen);
        }

        let key_options = if args.shared_key {
            hwkey_derived_key_options_HWKEY_SHARED_KEY_TYPE
        } else {
            hwkey_derived_key_options_HWKEY_DEVICE_UNIQUE_KEY_TYPE
        };

        let os_rollback_version: i32 = args.os_rollback_version.try_into()?;
        let mut rollback_versions =
            [0; hwkey_rollback_version_indices_HWKEY_ROLLBACK_VERSION_INDEX_COUNT as usize];
        rollback_versions
            [hwkey_rollback_version_indices_HWKEY_ROLLBACK_VERSION_OS_INDEX as usize] =
            os_rollback_version;

        let cmd = hwkey_cmd_HWKEY_DERIVE_VERSIONED as u32;

        let req_msg = hwkey_derive_versioned_msg {
            header: hwkey_msg_header { cmd, op_id: 0u32, status: 0u32 },
            kdf_version: args.kdf_version.into(),
            rollback_version_source: args.rollback_version_source.into(),
            rollback_versions,
            key_options: key_options as u32,
            key_len: key_buf.len() as u32,
        };

        let msg = HwkeyDeriveVersionedMsg { msg: req_msg, context: src };

        self.0.send(&msg)?;

        let buf = &mut [0; HWKEY_MAX_MSG_SIZE as usize];
        let response: HwkeyDeriveVersionedResponse = self.0.recv(buf)?;

        if !Hwkey::validate_cmd(&cmd, &response.cmd) {
            log::error!("unknown response cmd: {:?}", response.cmd);
            return Err(HwkeyError::InvalidCmdResponse);
        }

        HwkeyError::from_hwkey_rc(response.status)?;

        if key_buf.len() != response.payload.len() {
            log::error!(
                "key buffer size ({:?}) != payload size ({:?})",
                key_buf.len(),
                response.payload.len()
            );
            return Err(HwkeyError::BadLen);
        }

        key_buf.copy_from_slice(&response.payload[..]);

        Ok(DeriveResult {
            kdf_version: KdfVersion::from(response.kdf_version),
            os_rollback_version: OsRollbackVersion::try_from(response.os_rollback_version)?,
        })
    }

    /// Queries the current OS version.
    ///
    /// # Returns
    ///
    /// The current [`OsRollbackVersion`] to be incorporated
    /// into key derivation.
    ///
    /// # Examples
    ///
    /// ```
    /// let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    /// let os_rollback_version = hwkey_session
    ///     .query_current_os_version(RollbackVersionSource::RunningVersion)
    ///     .expect("could not query version");
    /// ```
    pub fn query_current_os_version(
        &self,
        rollback_source: RollbackVersionSource,
    ) -> Result<OsRollbackVersion, HwkeyError> {
        let derive_request = self
            .derive_key_req()
            .rollback_version_source(rollback_source)
            .os_rollback_version(OsRollbackVersion::Current);
        self.derive(&[], &mut [], derive_request).map(|res| res.os_rollback_version)
    }
}

/// The KDF algorithm version the hwkey service will use.
#[derive(Debug, PartialEq, Eq, Copy, Clone)]
pub enum KdfVersion {
    /// Tell the hwkey service to choose the best KDF algorithm version.
    Best,
    /// Specify KDF version hwkey service should use.
    Version(u32),
}

impl Into<u32> for KdfVersion {
    /// Converts a [`KdfVersion`] into an [`i32`].
    fn into(self) -> u32 {
        match self {
            Self::Best => 0,
            Self::Version(v) => v,
        }
    }
}

impl From<u32> for KdfVersion {
    /// Converts an [`i32`] into a [`KdfVersion`].
    fn from(v: u32) -> KdfVersion {
        if v == 0 {
            KdfVersion::Best
        } else {
            KdfVersion::Version(v)
        }
    }
}

/// the OS rollback version to be incorporated
/// into the key derivation.
#[derive(Debug, PartialEq, Eq, Copy, Clone)]
pub enum OsRollbackVersion {
    /// The latest available version will be used.
    Current,
    /// A specific version will be used.
    Version(u32),
}

impl TryInto<i32> for OsRollbackVersion {
    type Error = HwkeyError;
    /// Tries to convert a [`OsRollbackVersion`] into an [`i32`].
    fn try_into(self) -> Result<i32, HwkeyError> {
        match self {
            OsRollbackVersion::Current => Ok(HWKEY_ROLLBACK_VERSION_CURRENT),
            OsRollbackVersion::Version(version) => Ok(version.try_into()?),
        }
    }
}

impl TryFrom<i32> for OsRollbackVersion {
    type Error = HwkeyError;
    /// Tries to convert an [`i32`] into an [`OsRollbackVersion`].
    fn try_from(v: i32) -> Result<OsRollbackVersion, HwkeyError> {
        match v {
            HWKEY_ROLLBACK_VERSION_CURRENT => Ok(OsRollbackVersion::Current),
            n => Ok(OsRollbackVersion::Version(n.try_into()?)),
        }
    }
}

/// Specifies whether the rollback version must
/// have been committed. If
/// [`RollbackVersionSource::CommittedVersion`]
/// is specified, the system must guarantee that software
/// with a lower rollback version cannot ever run on a future
/// boot.
#[derive(Copy, Clone, Debug)]
pub enum RollbackVersionSource {
    /// Gate the derived key based on the anti-rollback counter that has been
    /// committed to fuses or stored. A version of Trusty with a version smaller
    /// than this value should never run on the device again. The latest key may
    /// not be available the first few times a new version of Trusty runs on the
    /// device, because the counter may not be committed immediately. This
    /// version source may not allow versions > 0 on some devices (i.e. rollback
    /// versions cannot be committed).
    CommittedVersion,
    /// Gate the derived key based on the anti-rollback version in the signed
    /// image of Trusty that is currently running. The latest key should be
    /// available immediately, but the Trusty image may be rolled back on a
    /// future boot. Care should be taken that Trusty still works if the image is
    /// rolled back and access to this key is lost. Care should also be taken
    /// that Trusty cannot infer this key if it rolls back to a previous version.
    /// For example, storing the latest version of this key in Trusty’s storage
    /// would allow it to be retrieved after rollback.
    RunningVersion,
}

impl Into<u32> for RollbackVersionSource {
    /// Converts a [`RollbackVersionSource`] into a [`u32`].
    fn into(self) -> u32 {
        match self {
            Self::CommittedVersion => {
                hwkey_rollback_version_source_HWKEY_ROLLBACK_COMMITTED_VERSION as u32
            }
            Self::RunningVersion => {
                hwkey_rollback_version_source_HWKEY_ROLLBACK_RUNNING_VERSION as u32
            }
        }
    }
}

/// The result of deriving a key.
#[derive(Debug, Eq, PartialEq)]
pub struct DeriveResult {
    /// The KDF algorithm version used in key derivation.
    pub kdf_version: KdfVersion,
    /// The OS rollback version used in key derivation.
    pub os_rollback_version: OsRollbackVersion,
}

/// A builder for a derived key request. May
/// only be created via Hwkey::derive_key_req,
/// which will default to values backwards-compatible
/// with the unversioned key derivation functionality
/// provided by the hwkey service.
pub struct DerivedKeyRequest<'a> {
    /// The version of the KDF to use.
    /// [`KdfVersion::Best`] will be assumed, and
    /// the latest version will be used.
    kdf_version: KdfVersion,
    /// If true, the derived key will be consistent and shared across the entire
    /// family of devices, given the same input. If false, the derived key will
    /// be unique to the particular device it was derived on.
    shared_key: bool,
    /// Specifies whether the @rollback_version must have been committed. If
    /// [`RollbackVersionSource::CommittedVersion`] is specified, the system
    /// must guarantee that software with a lower rollback version cannot
    /// ever run on a future boot.
    rollback_version_source: RollbackVersionSource,
    /// The OS rollback version to be incorporated into the key
    /// derivation. Must be less than or equal to the current Trusty OS rollback
    /// version from [`RollbackVersionSource`]. If set to
    /// [`OsRollbackVersion::Current`] the latest available version will be used
    /// and will be written back to the struct.
    os_rollback_version: OsRollbackVersion,
    /// Hwkey session
    session: &'a Hwkey,
}

impl<'a> DerivedKeyRequest<'a> {
    /// Returns default options; backwards-compatible,
    /// with unversioned derived key service.
    fn new(hwkey_sess: &'a Hwkey) -> Self {
        Self {
            kdf_version: KdfVersion::Best,
            shared_key: false,
            rollback_version_source: RollbackVersionSource::CommittedVersion,
            os_rollback_version: OsRollbackVersion::Version(0),
            session: hwkey_sess,
        }
    }

    /// Sets the KDF algorithm version used in key derivation.
    pub fn kdf(mut self, kdf_version: KdfVersion) -> Self {
        self.kdf_version = kdf_version;
        self
    }

    /// Tells derivation service to generate a shared key,
    /// which will be consistent and shared across the entire
    /// family of devices, given the same input.
    pub fn shared_key(mut self) -> Self {
        self.shared_key = true;
        self
    }

    /// Tells derivation service to generate a key which will
    /// be unique to the particular device it was derived on.
    /// This key should never be available outside of
    /// this device.
    pub fn unique_key(mut self) -> Self {
        self.shared_key = false;
        self
    }

    /// Sets the rollback version source used in key derivation.
    pub fn rollback_version_source(mut self, src: RollbackVersionSource) -> Self {
        self.rollback_version_source = src;
        self
    }

    /// Sets the OS rollback version used in key derivation.
    /// Must be less than or equal to the current Trusty rollback version
    /// from [`RollbackVersionSource`].
    pub fn os_rollback_version(mut self, v: OsRollbackVersion) -> Self {
        self.os_rollback_version = v;
        self
    }

    /// Derive a versioned, device-specific key from the provided context.
    ///
    /// # Arguments
    ///
    /// * `src` - The context from which the key will be derived. If
    /// empty, `key_buf` must be empty as well.
    /// * `key_buf` - The buffer into which the key will be written. If
    /// empty, no key will be generated and only the current versions
    /// may be queried.
    ///
    /// # Returns
    ///
    /// The DeriveResult containing information used in derivation.
    ///
    /// # Examples
    ///
    /// ```
    /// let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    /// let buf = &mut [0u8; 32 as usize];
    /// let DeriveResult { kdf_version, os_rollback_version } = hwkey_session
    ///     .derive_key_req()
    ///     .derive(b"thirtytwo-bytes-of-nonsense-data", buf)
    ///     .expect("could not derive key");
    /// ```
    ///
    /// ```
    /// let hwkey_session = Hwkey::open().expect("could not open hwkey session");
    /// let buf = &mut [0u8; 128 as usize];
    /// let DeriveResult { kdf_version, os_rollback_version } = hwkey_session
    ///     .derive_key_req()
    ///     .unique_key()
    ///     .kdf(KdfVersion::Best)
    ///     .os_rollback_version(OsRollbackVersion::Current)
    ///     .rollback_version_source(RollbackVersionSource::RunningVersion)
    ///     .derive(b"thirtytwo-bytes-of-nonsense-data", buf)
    ///     .expect("could not derive key");
    /// ```
    ///
    pub fn derive(self, src: &[u8], key_buf: &mut [u8]) -> Result<DeriveResult, HwkeyError> {
        self.session.derive(src, key_buf, self)
    }
}

struct HwkeyMsg<'a> {
    msg: hwkey_msg,
    request: &'a [u8],
}

impl<'s> Serialize<'s> for HwkeyMsg<'s> {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        // SAFETY:
        //  hwkey_msg.header is a fully-initialized, repr(C)
        //  struct that outlives the Serializer lifetime.
        //  arg1 and arg2 each a u32
        unsafe {
            serializer.serialize_as_bytes(&self.msg.header)?;
            serializer.serialize_as_bytes(&self.msg.arg1)?;
            serializer.serialize_as_bytes(&self.msg.arg2)?;
        }
        serializer.serialize_bytes(self.request)
    }
}

struct HwkeyDeriveVersionedMsg<'a> {
    msg: hwkey_derive_versioned_msg,
    context: &'a [u8],
}

impl<'s> Serialize<'s> for HwkeyDeriveVersionedMsg<'s> {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        // SAFETY:
        //  hwkey_derive_versioned_msg.header is a fully-initialized,
        //  repr(C) struct that outlives the Serializer lifetime.
        //  All other serialized attributes are trivial types with
        //  a corresponding C repr.
        unsafe {
            serializer.serialize_as_bytes(&self.msg.header)?;
            serializer.serialize_as_bytes(&self.msg.kdf_version)?;
            serializer.serialize_as_bytes(&self.msg.rollback_version_source)?;
        }

        for rv in &self.msg.rollback_versions {
            unsafe {
                serializer.serialize_as_bytes(rv)?;
            }
        }

        unsafe {
            serializer.serialize_as_bytes(&self.msg.key_options)?;
            serializer.serialize_as_bytes(&self.msg.key_len)?;
        }

        serializer.serialize_bytes(self.context)
    }
}

// TODO: replace owned payload with references when
// GATs are available for use in Deserialize trait
#[derive(Eq, PartialEq, Debug)]
struct HwkeyResponse {
    kdf_version: u32,
    status: u32,
    cmd: u32,
    payload: Vec<u8>,
}

impl Deserialize for HwkeyResponse {
    type Error = HwkeyError;
    const MAX_SERIALIZED_SIZE: usize = HWKEY_MAX_MSG_SIZE as usize;
    fn deserialize(bytes: &[u8], _handles: &mut [Option<Handle>]) -> Result<Self, Self::Error> {
        let header_size = mem::size_of::<hwkey_msg>();
        if bytes.len() < header_size {
            log::error!("response too small to be valid");
            return Err(HwkeyError::BadLen);
        }
        // SAFETY: We have validated that the buffer contains enough data to
        // represent a hwkey_msg. The constructed lifetime here does not
        // outlive the function and thus cannot outlive the lifetime of the
        // buffer.
        let msg = unsafe { &*(bytes.as_ptr() as *const hwkey_msg) };

        let response_payload = Vec::try_alloc_from(&bytes[header_size..])?;
        Ok(Self {
            status: msg.header.status,
            cmd: msg.header.cmd,
            kdf_version: msg.arg1,
            payload: response_payload,
        })
    }
}

// TODO: replace owned payload with references when
// GATs are available for use in Deserialize trait
struct HwkeyDeriveVersionedResponse {
    cmd: u32,
    status: u32,
    os_rollback_version: i32,
    kdf_version: u32,
    payload: Vec<u8>,
}

impl Deserialize for HwkeyDeriveVersionedResponse {
    type Error = HwkeyError;

    const MAX_SERIALIZED_SIZE: usize = HWKEY_MAX_MSG_SIZE as usize;

    fn deserialize(bytes: &[u8], _handles: &mut [Option<Handle>]) -> Result<Self, Self::Error> {
        let header_size = mem::size_of::<hwkey_derive_versioned_msg>();
        if bytes.len() < header_size {
            log::error!("response too small to be valid");
            return Err(HwkeyError::BadLen);
        }

        // SAFETY: We have validated that the buffer contains enough data to
        // represent a hwkey_derive_versioned_msg.
        let msg = unsafe { &*(bytes.as_ptr() as *const hwkey_derive_versioned_msg) };

        // the rest of the buffer should be the key data
        if bytes.len() - header_size != msg.key_len as usize {
            log::error!(
                "response payload size ({:?}) != key length ({:?})",
                bytes.len() - header_size,
                msg.key_len
            );
            return Err(HwkeyError::BadLen);
        }

        let response_payload = Vec::try_alloc_from(&bytes[header_size..])?;
        Ok(Self {
            cmd: msg.header.cmd,
            status: msg.header.status,
            os_rollback_version: msg.rollback_versions
                [hwkey_rollback_version_indices_HWKEY_ROLLBACK_VERSION_OS_INDEX as usize],
            kdf_version: msg.kdf_version,
            payload: response_payload,
        })
    }
}
