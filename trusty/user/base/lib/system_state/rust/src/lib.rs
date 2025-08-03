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

//! # Interface library for communicating with the system state service.

#![no_std]

#[allow(non_upper_case_globals)]
#[allow(non_camel_case_types)]
#[allow(unused)]
mod sys {
    include!(env!("BINDGEN_INC_FILE"));
}

use core::convert::{TryFrom, TryInto};
use core::ffi::CStr;
use core::mem;
use sys::*;
use tipc::{Deserialize, Handle, Serialize, Serializer, TipcError};

const SYSTEM_STATE_PORT: &'static [u8] = b"com.android.trusty.system-state\0";

/// System state flags.
///
/// Supported queries that the system state service provides.
#[repr(u32)]
#[derive(Copy, Clone, Debug, Eq, PartialEq)]
pub enum SystemStateFlag {
    /// Flag used to restrict when provisioning is allowed.
    ProvisioningAllowed = system_state_flag_SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED as u32,

    /// Flag used to indicate that loading apps signed with insecure dev keys is
    /// allowed.
    AppLoadingUnlocked = system_state_flag_SYSTEM_STATE_FLAG_APP_LOADING_UNLOCKED as u32,

    /// Flag used to permit skipping of app version checks or rollback version
    /// updates.
    AppLoadingVersionCheck = system_state_flag_SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK as u32,
}

impl TryFrom<u32> for SystemStateFlag {
    type Error = TipcError;

    fn try_from(value: u32) -> Result<SystemStateFlag, Self::Error> {
        match value as system_state_flag {
            sys::system_state_flag_SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED => {
                Ok(SystemStateFlag::ProvisioningAllowed)
            }
            sys::system_state_flag_SYSTEM_STATE_FLAG_APP_LOADING_UNLOCKED => {
                Ok(SystemStateFlag::AppLoadingUnlocked)
            }
            sys::system_state_flag_SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK => {
                Ok(SystemStateFlag::AppLoadingVersionCheck)
            }
            _ => Err(TipcError::InvalidData),
        }
    }
}

/// Possible values for SystemStateFlag::ProvisioningAllowed flag.
#[repr(u64)]
#[derive(Copy, Clone, Debug, Eq, PartialEq)]
pub enum ProvisioningAllowedFlagValues {
    /// Indicates that provisoning is not currently allowed.
    ProvisioningNotAllowed = system_state_flag_provisioning_allowed_SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_NOT_ALLOWED as u64,

    /// Indicates that provisoning is currently allowed.
    ProvisioningAllowed = system_state_flag_provisioning_allowed_SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_ALLOWED as u64,

    /// Indicates that provisoning is currently allowed if the client is in a boot stage.
    ///
    /// For backward compatibility. Not recommended for new systems.
    ProvisioningAllowedAtBoot = system_state_flag_provisioning_allowed_SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_ALLOWED_AT_BOOT as u64,
}

impl TryFrom<u64> for ProvisioningAllowedFlagValues {
    type Error = TipcError;

    fn try_from(value: u64) -> Result<ProvisioningAllowedFlagValues, Self::Error> {
        match value as system_state_flag {
            sys::system_state_flag_provisioning_allowed_SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_NOT_ALLOWED => {
                Ok(ProvisioningAllowedFlagValues::ProvisioningNotAllowed)
            }
            sys::system_state_flag_provisioning_allowed_SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_ALLOWED => {
                Ok(ProvisioningAllowedFlagValues::ProvisioningAllowed)
            }
            sys::system_state_flag_provisioning_allowed_SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_ALLOWED_AT_BOOT => {
                Ok(ProvisioningAllowedFlagValues::ProvisioningAllowedAtBoot)
            }
            _ => Err(TipcError::InvalidData),
        }
    }
}

/// Connection to the system state service
pub struct SystemState(Handle);

impl SystemState {
    /// Attempt to connect to the system state service.
    pub fn try_connect() -> Result<Self, TipcError> {
        let port = CStr::from_bytes_with_nul(SYSTEM_STATE_PORT)
            .expect("SYSTEM_STATE_PORT was not null terminated");
        Handle::connect(port).map(Self)
    }

    /// Retrieve a state value from the system state service.
    pub fn get_flag(&self, flag: SystemStateFlag) -> Result<u64, TipcError> {
        self.0.send(&Request::get_flag(flag))?;

        let mut buf = [0; Response::MAX_SERIALIZED_SIZE];
        let response: Response = self.0.recv(&mut buf)?;
        assert_eq!(response.flag, flag);
        Ok(response.value)
    }
}

struct Request(system_state_req, RequestPayload);

enum RequestPayload {
    GetFlag(system_state_get_flag_req),
}

const GET_FLAG_REQ_CMD: u32 = system_state_cmd_SYSTEM_STATE_CMD_GET_FLAG as u32;
const GET_FLAG_RESP_CMD: u32 = (system_state_cmd_SYSTEM_STATE_CMD_GET_FLAG
    | system_state_cmd_SYSTEM_STATE_CMD_RESP_BIT) as u32;

impl Request {
    fn get_flag(flag: SystemStateFlag) -> Self {
        let header = system_state_req {
            cmd: GET_FLAG_REQ_CMD,
            reserved: 0,
            payload: __IncompleteArrayField::new(),
        };
        let payload = RequestPayload::GetFlag(system_state_get_flag_req { flag: flag as u32 });
        Self(header, payload)
    }
}

impl<'s> Serialize<'s> for Request {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        // SAFETY: system_state_req is a fully-initialized, repr(C) struct that
        // outlives the Serializer lifetime.
        unsafe { serializer.serialize_as_bytes(&self.0)? };

        match &self.1 {
            RequestPayload::GetFlag(req) => {
                // SAFETY: system_state_get_flag_req is a fully-initialized,
                // repr(C) struct that outlives the Serializer lifetime.
                unsafe { serializer.serialize_as_bytes(req) }
            }
        }
    }
}

struct Response {
    flag: SystemStateFlag,
    value: u64,
}

impl Deserialize for Response {
    type Error = TipcError;

    const MAX_SERIALIZED_SIZE: usize =
        mem::size_of::<system_state_resp>() + mem::size_of::<system_state_get_flag_resp>();

    fn deserialize(bytes: &[u8], _handles: &mut [Option<Handle>]) -> Result<Self, Self::Error> {
        if bytes.len() < mem::size_of::<system_state_resp>() {
            return Err(TipcError::NotEnoughBuffer);
        }
        // SAFETY: We have validated that the buffer contains enough data to
        // represent a system_state_resp. The constructed lifetime here does not
        // outlive the function and thus cannot outlive the lifetime of the
        // buffer.
        let header = unsafe { &*(bytes.as_ptr() as *const system_state_resp) };
        match header.cmd {
            GET_FLAG_RESP_CMD => {
                if bytes.len()
                    < mem::size_of::<system_state_resp>()
                        + mem::size_of::<system_state_get_flag_resp>()
                {
                    return Err(TipcError::NotEnoughBuffer);
                }
                // SAFETY: We have validated that the buffer is large enough for
                // both the header and the get_flag payload.
                let payload =
                    unsafe { &*(header.payload.as_ptr() as *const system_state_get_flag_resp) };
                Ok(Self { flag: payload.flag.try_into()?, value: payload.value })
            }
            _ => Err(TipcError::InvalidData),
        }
    }
}
