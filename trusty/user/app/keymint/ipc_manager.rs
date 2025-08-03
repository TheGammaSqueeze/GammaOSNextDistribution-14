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
//! Trusty handler for IPC connections. It handles both secure and non-secure world ports.
use crate::secure_storage_manager;
use alloc::{rc::Rc, vec::Vec};
use core::{cell::RefCell, mem};
use keymint_access_policy::{
    keymint_check_secure_target_access_policy_provisioning, keymint_check_target_access_policy,
};
use kmr_common::{
    crypto, km_err,
    wire::legacy::{
        self, AppendAttestationCertChainResponse, ClearAttestationCertChainResponse,
        ConfigureBootPatchlevelResponse, GetAuthTokenKeyResponse, GetDeviceInfoResponse,
        GetVersion2Response, GetVersionResponse, SetAttestationIdsKM3Response,
        SetAttestationIdsResponse, SetAttestationKeyResponse, SetBootParamsResponse,
        SetWrappedAttestationKeyResponse, TrustyMessageId, TrustyPerformOpReq, TrustyPerformOpRsp,
        TrustyPerformSecureOpReq, TrustyPerformSecureOpRsp,
    },
    Error,
};
use kmr_ta::{self, device::SigningAlgorithm, split_rsp, HardwareInfo, KeyMintTa, RpcInfo};
use kmr_wire::keymint::{Algorithm, BootInfo};
use log::{debug, error, info};
use system_state::{ProvisioningAllowedFlagValues, SystemState, SystemStateFlag};
use tipc::{
    service_dispatcher, ConnectResult, Deserialize, Handle, Manager, MessageResult, PortCfg,
    Serialize, Serializer, Service, TipcError, Uuid,
};
use trusty_std::alloc::TryAllocFrom;

/// Port that handles new style keymint messages from non-secure world
const KM_NS_TIPC_SRV_PORT: &str = "com.android.trusty.keymint";
/// Port that handles secure world messages
const KM_SEC_TIPC_SRV_PORT: &str = "com.android.trusty.keymaster.secure";
/// Port that handles legacy style keymint/keymaster messages
const KM_NS_LEGACY_TIPC_SRV_PORT: &str = "com.android.trusty.keymaster";
/// Port count for this TA (as above).
const PORT_COUNT: usize = 3;
/// Maximum connection count for this TA:
/// - Gatekeeper
/// - Fingerprint
/// - FaceAuth
/// - Widevine
/// - Non-secure world.
const MAX_CONNECTION_COUNT: usize = 5;

const KEYMINT_MAX_BUFFER_LENGTH: usize = 4096;
const KEYMINT_MAX_MESSAGE_CONTENT_SIZE: usize = 4000;

/// TIPC connection context information.
struct Context {
    uuid: Uuid,
}

/// Newtype wrapper for opaque messages.
struct KMMessage(Vec<u8>);

impl Deserialize for KMMessage {
    type Error = TipcError;
    const MAX_SERIALIZED_SIZE: usize = KEYMINT_MAX_BUFFER_LENGTH;

    fn deserialize(bytes: &[u8], _handles: &mut [Option<Handle>]) -> tipc::Result<Self> {
        Ok(KMMessage(Vec::try_alloc_from(bytes)?))
    }
}

impl<'s> Serialize<'s> for KMMessage {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        serializer.serialize_bytes(&self.0.as_slice())
    }
}

/// Convert KeyMint [`Algorithm`] to [`SigningAlgorithm`].
fn keymaster_algorithm_to_signing_algorithm(
    algorithm: Algorithm,
) -> Result<SigningAlgorithm, Error> {
    match algorithm {
        Algorithm::Rsa => Ok(SigningAlgorithm::Rsa),
        Algorithm::Ec => Ok(SigningAlgorithm::Ec),
        _ => Err(km_err!(
            Unimplemented,
            "only supported algorithms are RSA and EC. Got {}",
            algorithm as u32
        )),
    }
}

/// TIPC service implementation for communication with the HAL service in Android.
struct KMService {
    km_ta: Rc<RefCell<KeyMintTa>>,
}

impl KMService {
    /// Create a service implementation.
    fn new(km_ta: Rc<RefCell<KeyMintTa>>) -> Self {
        KMService { km_ta }
    }

    /// Process an incoming request message, returning the response as a collection of fragments
    /// that are each small enough to send over the channel.
    fn handle_message(&self, req_data: &[u8]) -> Result<Vec<Vec<u8>>, Error> {
        let resp = self.km_ta.borrow_mut().process(req_data);
        split_rsp(resp.as_slice(), KEYMINT_MAX_MESSAGE_CONTENT_SIZE)
    }
}

impl Service for KMService {
    type Connection = Context;
    type Message = KMMessage;

    fn on_connect(
        &self,
        _port: &PortCfg,
        _handle: &Handle,
        peer: &Uuid,
    ) -> tipc::Result<ConnectResult<Self::Connection>> {
        info!("Accepted connection from uuid {:?}.", peer);
        Ok(ConnectResult::Accept(Context { uuid: peer.clone() }))
    }

    fn on_message(
        &self,
        _connection: &Self::Connection,
        handle: &Handle,
        msg: Self::Message,
    ) -> tipc::Result<MessageResult> {
        debug!("Received a message.");
        let resp_vec = self.handle_message(&msg.0).map_err(|e| match e {
            Error::Hal(_, err_msg) => {
                error!("Error: {} in handling the message.", err_msg);
                TipcError::InvalidData
            }
            Error::Alloc(err_msg) => {
                error!("Error: {} in handling the message.", err_msg);
                TipcError::AllocError
            }
            _ => TipcError::UnknownError,
        })?;
        for resp in resp_vec {
            handle.send(&KMMessage(resp))?;
        }
        Ok(MessageResult::MaintainConnection)
    }
}

/// Retrieve the system state flag controlling provisioning.
fn get_system_state_provisioning_flag() -> Result<ProvisioningAllowedFlagValues, Error> {
    let system_state_session = SystemState::try_connect().map_err(|e| {
        km_err!(SecureHwCommunicationFailed, "couldn't connect to system state provider: {:?}", e)
    })?;
    let flag =
        system_state_session.get_flag(SystemStateFlag::ProvisioningAllowed).map_err(|e| {
            km_err!(
                SecureHwCommunicationFailed,
                "couldn't get ProvisioningAllowed system state flag: {:?}",
                e
            )
        })?;
    ProvisioningAllowedFlagValues::try_from(flag).map_err(|e| {
        km_err!(
            SecureHwCommunicationFailed,
            "couldn't parse ProvisioningAllowed system state flag from value {}: {:?}",
            flag,
            e
        )
    })
}

/// Indicate whether provisioning is allowed.
fn provisioning_allowed() -> Result<bool, Error> {
    Ok(match get_system_state_provisioning_flag()? {
        ProvisioningAllowedFlagValues::ProvisioningAllowed => true,
        _ => false,
    })
}

/// Indicate whether provisioning is allowed during boot.
fn provisioning_allowed_at_boot() -> Result<bool, Error> {
    Ok(match get_system_state_provisioning_flag()? {
        ProvisioningAllowedFlagValues::ProvisioningAllowed => true,
        ProvisioningAllowedFlagValues::ProvisioningAllowedAtBoot => true,
        _ => false,
    })
}

/// TIPC service implementation for communication with components outside Trusty (notably the
/// bootloader and provisioning tools), using legacy (C++) message formats.
struct KMLegacyService {
    km_ta: Rc<RefCell<KeyMintTa>>,
    boot_info: RefCell<Option<BootInfo>>,
    boot_patchlevel: RefCell<Option<u32>>,
}

impl KMLegacyService {
    /// Create a service implementation.
    fn new(km_ta: Rc<RefCell<KeyMintTa>>) -> Self {
        KMLegacyService {
            km_ta,
            boot_info: RefCell::new(None),
            boot_patchlevel: RefCell::new(None),
        }
    }

    /// Indicate whether the boot process is complete.
    fn boot_done(&self) -> bool {
        self.km_ta.borrow().is_hal_info_set()
    }

    /// Indicate whether provisioning operations are allowed.
    fn can_provision(&self) -> Result<bool, Error> {
        if self.boot_done() {
            provisioning_allowed()
        } else {
            provisioning_allowed_at_boot()
        }
    }

    /// Set the boot information for the TA if possible (i.e. if all parts of the required
    /// information are available).
    fn maybe_set_boot_info(&self) {
        match (self.boot_info.borrow().as_ref(), self.boot_patchlevel.borrow().as_ref()) {
            (Some(info), Some(boot_patchlevel)) => {
                // All the information is available to set the boot info, so combine it and pass to
                // the TA.
                let boot_info = BootInfo {
                    verified_boot_key: info.verified_boot_key.clone(),
                    device_boot_locked: info.device_boot_locked,
                    verified_boot_state: info.verified_boot_state,
                    verified_boot_hash: info.verified_boot_hash.clone(),
                    boot_patchlevel: *boot_patchlevel,
                };
                if let Err(e) = self.km_ta.borrow_mut().set_boot_info(boot_info) {
                    error!("failed to set boot info: {:?}", e);
                }
            }
            _ => info!("not all boot information available yet"),
        }
    }

    /// Process an incoming request message, returning the corresponding response.
    fn handle_message(&self, req_msg: TrustyPerformOpReq) -> Result<TrustyPerformOpRsp, Error> {
        let cmd_code = req_msg.code();
        // Checking that if we received a bootloader message we are at a stage when we can process
        // it
        if self.boot_done() && legacy::is_trusty_bootloader_req(&req_msg) {
            return Err(km_err!(
                Unimplemented,
                "bootloader command {:?} not allowed after configure command",
                cmd_code
            ));
        }

        if legacy::is_trusty_provisioning_req(&req_msg) && !(self.can_provision()?) {
            return Err(km_err!(Unimplemented, "provisioning command {:?} not allowed", cmd_code));
        }

        // Handling received message
        match req_msg {
            TrustyPerformOpReq::GetVersion(_req) => {
                // Match the values returned by C++ KeyMint (from `AndroidKeymaster::GetVersion`).
                Ok(TrustyPerformOpRsp::GetVersion(GetVersionResponse {
                    major_ver: 2,
                    minor_ver: 0,
                    subminor_ver: 0,
                }))
            }
            TrustyPerformOpReq::GetVersion2(req) => {
                // Match the values returned by C++ KeyMint (from `AndroidKeymaster::GetVersion2`).
                let km_version = legacy::KmVersion::KeyMint3;
                let message_version = km_version.message_version();
                let max_message_version = core::cmp::min(req.max_message_version, message_version);
                Ok(TrustyPerformOpRsp::GetVersion2(GetVersion2Response {
                    max_message_version,
                    km_version,
                    km_date: legacy::KM_DATE,
                }))
            }
            TrustyPerformOpReq::SetBootParams(req) => {
                // Check if this is the first time we receive a SetBootParams cmd
                if self.boot_info.borrow().is_some() {
                    return Err(km_err!(Unimplemented, "command SetBootParams only allowed once"));
                }

                // Saving boot_info so command won't be accepted a second time
                let boot_info = BootInfo {
                    verified_boot_key: req.verified_boot_key,
                    device_boot_locked: req.device_locked,
                    verified_boot_state: req.verified_boot_state,
                    verified_boot_hash: req.verified_boot_hash,
                    boot_patchlevel: 0, // boot_patchlevel is received on ConfigureBootPatchlevel
                };
                self.boot_info.borrow_mut().replace(boot_info);

                // Checking if we can send set boot info with the info we currently have
                self.maybe_set_boot_info();

                Ok(TrustyPerformOpRsp::SetBootParams(SetBootParamsResponse {}))
            }
            TrustyPerformOpReq::ConfigureBootPatchlevel(req) => {
                // Check if this is the first time we receive a ConfigureBootPatchlevel cmd
                if self.boot_patchlevel.borrow().is_some() {
                    return Err(km_err!(
                        Unimplemented,
                        "command ConfigureBootPatchlevel only allowed once"
                    ));
                }

                // Saving boot_patchlevel so command won't be accepted a second time
                self.boot_patchlevel.borrow_mut().replace(req.boot_patchlevel);

                // Checking if we can send set boot info with the info we currently have
                self.maybe_set_boot_info();

                Ok(TrustyPerformOpRsp::ConfigureBootPatchlevel(ConfigureBootPatchlevelResponse {}))
            }
            TrustyPerformOpReq::SetAttestationKey(req) => {
                let algorithm = keymaster_algorithm_to_signing_algorithm(req.algorithm)?;
                secure_storage_manager::provision_attestation_key_file(algorithm, &req.key_data)?;
                Ok(TrustyPerformOpRsp::SetAttestationKey(SetAttestationKeyResponse {}))
            }
            TrustyPerformOpReq::AppendAttestationCertChain(req) => {
                let algorithm = keymaster_algorithm_to_signing_algorithm(req.algorithm)?;
                secure_storage_manager::append_attestation_cert_chain(algorithm, &req.cert_data)?;
                Ok(TrustyPerformOpRsp::AppendAttestationCertChain(
                    AppendAttestationCertChainResponse {},
                ))
            }
            TrustyPerformOpReq::ClearAttestationCertChain(req) => {
                let algorithm = keymaster_algorithm_to_signing_algorithm(req.algorithm)?;
                secure_storage_manager::clear_attestation_cert_chain(algorithm)?;
                Ok(TrustyPerformOpRsp::ClearAttestationCertChain(
                    ClearAttestationCertChainResponse {},
                ))
            }
            TrustyPerformOpReq::SetWrappedAttestationKey(req) => {
                let algorithm = keymaster_algorithm_to_signing_algorithm(req.algorithm)?;
                secure_storage_manager::set_wrapped_attestation_key(algorithm, &req.key_data)?;
                Ok(TrustyPerformOpRsp::SetWrappedAttestationKey(
                    SetWrappedAttestationKeyResponse {},
                ))
            }
            TrustyPerformOpReq::SetAttestationIds(req) => {
                secure_storage_manager::provision_attestation_id_file(
                    &req.brand,
                    &req.product,
                    &req.device,
                    &req.serial,
                    &req.imei,
                    &req.meid,
                    &req.manufacturer,
                    &req.model,
                    None,
                )?;
                Ok(TrustyPerformOpRsp::SetAttestationIds(SetAttestationIdsResponse {}))
            }
            TrustyPerformOpReq::SetAttestationIdsKM3(req) => {
                secure_storage_manager::provision_attestation_id_file(
                    &req.base.brand,
                    &req.base.product,
                    &req.base.device,
                    &req.base.serial,
                    &req.base.imei,
                    &req.base.meid,
                    &req.base.manufacturer,
                    &req.base.model,
                    Some(&req.second_imei),
                )?;
                Ok(TrustyPerformOpRsp::SetAttestationIdsKM3(SetAttestationIdsKM3Response {}))
            }
        }
    }
}

impl Service for KMLegacyService {
    type Connection = Context;
    type Message = KMMessage;

    fn on_connect(
        &self,
        _port: &PortCfg,
        _handle: &Handle,
        peer: &Uuid,
    ) -> tipc::Result<ConnectResult<Self::Connection>> {
        info!("Accepted connection from uuid {:?}.", peer);
        Ok(ConnectResult::Accept(Context { uuid: peer.clone() }))
    }

    fn on_message(
        &self,
        _connection: &Self::Connection,
        handle: &Handle,
        msg: Self::Message,
    ) -> tipc::Result<MessageResult> {
        debug!("Received legacy message.");
        let req_msg = legacy::deserialize_trusty_req(&msg.0).map_err(|e| {
            error!("Received error when parsing legacy message: {:?}", e);
            TipcError::InvalidData
        })?;
        let op = req_msg.code();

        let resp = match self.handle_message(req_msg) {
            Ok(resp_msg) => legacy::serialize_trusty_rsp(resp_msg).map_err(|e| {
                error!("failed to serialize legacy response message: {:?}", e);
                TipcError::InvalidData
            })?,
            Err(Error::Hal(rc, msg)) => {
                error!("operation {:?} failed: {:?} {}", op, rc, msg);
                legacy::serialize_trusty_error_rsp(op, rc).map_err(|e| {
                    error!("failed to serialize legacy error {:?} response message: {:?}", rc, e);
                    TipcError::InvalidData
                })?
            }
            Err(e) => {
                error!("error handling legacy message: {:?}", e);
                return Err(TipcError::UnknownError);
            }
        };
        handle.send(&KMMessage(resp))?;
        Ok(MessageResult::MaintainConnection)
    }
}

/// TIPC service implementation for secure communication with other components in Trusty
/// (e.g. Gatekeeper, ConfirmationUI), using legacy (C++) message formats.
struct KMSecureService {
    km_ta: Rc<RefCell<KeyMintTa>>,
}

impl KMSecureService {
    /// Create a service implementation.
    fn new(km_ta: Rc<RefCell<KeyMintTa>>) -> Self {
        KMSecureService { km_ta }
    }
    fn handle_message(
        &self,
        req_msg: TrustyPerformSecureOpReq,
    ) -> Result<TrustyPerformSecureOpRsp, Error> {
        match req_msg {
            TrustyPerformSecureOpReq::GetAuthTokenKey(_) => {
                match self.km_ta.borrow().get_hmac_key() {
                    Some(mut payload) => {
                        Ok(TrustyPerformSecureOpRsp::GetAuthTokenKey(GetAuthTokenKeyResponse {
                            key_material: mem::take(&mut payload.0),
                        }))
                    }
                    None => Err(km_err!(UnknownError, "hmac_key is not available")),
                }
            }
            TrustyPerformSecureOpReq::GetDeviceInfo(_) => {
                Ok(TrustyPerformSecureOpRsp::GetDeviceInfo(GetDeviceInfoResponse {
                    device_ids: self.km_ta.borrow().rpc_device_info()?,
                }))
            }
            TrustyPerformSecureOpReq::SetAttestationIds(req) => {
                secure_storage_manager::provision_attestation_id_file(
                    &req.brand,
                    &req.product,
                    &req.device,
                    &req.serial,
                    &req.imei,
                    &req.meid,
                    &req.manufacturer,
                    &req.model,
                    None,
                )?;
                Ok(TrustyPerformSecureOpRsp::SetAttestationIds(SetAttestationIdsResponse {}))
            }
        }
    }
}

impl Service for KMSecureService {
    type Connection = Context;
    type Message = KMMessage;

    fn on_connect(
        &self,
        _port: &PortCfg,
        _handle: &Handle,
        peer: &Uuid,
    ) -> tipc::Result<ConnectResult<Self::Connection>> {
        if !keymint_check_target_access_policy(peer) {
            error!("access policy rejected the uuid: {:?}", peer);
            return Ok(ConnectResult::CloseConnection);
        }
        info!("Accepted connection from uuid {:?}.", peer);
        Ok(ConnectResult::Accept(Context { uuid: peer.clone() }))
    }

    fn on_message(
        &self,
        connection: &Self::Connection,
        handle: &Handle,
        msg: Self::Message,
    ) -> tipc::Result<MessageResult> {
        debug!("Received secure message.");

        let req_msg = legacy::deserialize_trusty_secure_req(&msg.0).map_err(|e| {
            error!("Received error when parsing message: {:?}", e);
            TipcError::InvalidData
        })?;
        let op = req_msg.code();
        if matches!(&req_msg, TrustyPerformSecureOpReq::SetAttestationIds(_))
            && !keymint_check_secure_target_access_policy_provisioning(&connection.uuid)
        {
            error!("access policy rejected the uuid: {:?}", &connection.uuid);
            return Ok(MessageResult::CloseConnection);
        }

        let resp = match self.handle_message(req_msg) {
            Ok(resp_msg) => legacy::serialize_trusty_secure_rsp(resp_msg).map_err(|e| {
                error!("failed to serialize legacy response secure message: {:?}", e);
                TipcError::InvalidData
            })?,
            Err(Error::Hal(rc, msg)) => {
                error!("operation {:?} failed: {:?} {}", op, rc, msg);
                legacy::serialize_trusty_secure_error_rsp(op, rc).map_err(|e| {
                    error!(
                        "failed to serialize legacy error {:?} response secure message: {:?}",
                        rc, e
                    );
                    TipcError::InvalidData
                })?
            }
            Err(e) => {
                error!("error handling secure legacy message: {:?}", e);
                return Err(TipcError::UnknownError);
            }
        };
        handle.send(&KMMessage(resp))?;
        Ok(MessageResult::MaintainConnection)
    }
}

service_dispatcher! {
    enum KMServiceDispatcher {
        KMService,
        KMSecureService,
        KMLegacyService,
    }
}

/// Main loop handler for the KeyMint TA.
pub fn handle_port_connections(
    hw_info: HardwareInfo,
    rpc_info: RpcInfo,
    imp: crypto::Implementation,
    dev: kmr_ta::device::Implementation,
) -> Result<(), Error> {
    let km_ta = Rc::new(RefCell::new(KeyMintTa::new(hw_info, rpc_info, imp, dev)));
    let ns_service = KMService::new(Rc::clone(&km_ta));
    let legacy_service = KMLegacyService::new(Rc::clone(&km_ta));
    let sec_service = KMSecureService::new(km_ta);

    let mut dispatcher = KMServiceDispatcher::<3>::new()
        .map_err(|e| km_err!(UnknownError, "could not create multi-service dispatcher: {:?}", e))?;
    let cfg = PortCfg::new(KM_NS_TIPC_SRV_PORT)
        .map_err(|e| {
            km_err!(
                UnknownError,
                "could not create port config for {}: {:?}",
                KM_NS_TIPC_SRV_PORT,
                e
            )
        })?
        .allow_ta_connect()
        .allow_ns_connect();
    dispatcher.add_service(Rc::new(ns_service), cfg).map_err(|e| {
        km_err!(UnknownError, "could not add non-secure service to dispatcher: {:?}", e)
    })?;
    let cfg = PortCfg::new(KM_SEC_TIPC_SRV_PORT)
        .map_err(|e| {
            km_err!(
                UnknownError,
                "could not create port config for {}: {:?}",
                KM_SEC_TIPC_SRV_PORT,
                e
            )
        })?
        .allow_ta_connect();
    dispatcher.add_service(Rc::new(sec_service), cfg).map_err(|e| {
        km_err!(UnknownError, "could not add secure service to dispatcher: {:?}", e)
    })?;
    let cfg = PortCfg::new(KM_NS_LEGACY_TIPC_SRV_PORT)
        .map_err(|e| {
            km_err!(
                UnknownError,
                "could not create port config for {}: {:?}",
                KM_NS_LEGACY_TIPC_SRV_PORT,
                e
            )
        })?
        .allow_ta_connect()
        .allow_ns_connect();
    dispatcher.add_service(Rc::new(legacy_service), cfg).map_err(|e| {
        km_err!(UnknownError, "could not add secure service to dispatcher: {:?}", e)
    })?;
    let buffer = [0u8; 4096];
    let manager =
        Manager::<_, _, PORT_COUNT, MAX_CONNECTION_COUNT>::new_with_dispatcher(dispatcher, buffer)
            .map_err(|e| km_err!(UnknownError, "could not create service manager: {:?}", e))?;
    manager
        .run_event_loop()
        .map_err(|e| km_err!(UnknownError, "service manager received error: {:?}", e))?;
    Err(km_err!(SecureHwCommunicationFailed, "KeyMint TA handler terminated unexpectedly."))
}

// TODO: remove when tests reinstated
#[allow(dead_code)]
#[cfg(test)]
mod tests {
    use super::*;
    use kmr_wire::{
        keymint::{ErrorCode, VerifiedBootState},
        legacy::{self, InnerSerialize},
    };
    use test::{expect, skip};
    use trusty_std::ffi::{CString, FallibleCString};

    const CONFIGURE_BOOT_PATCHLEVEL_CMD: u32 =
        legacy::TrustyKeymasterOperation::ConfigureBootPatchlevel as u32;
    const SET_BOOT_PARAMS_CMD: u32 = legacy::TrustyKeymasterOperation::SetBootParams as u32;
    const SET_ATTESTATION_IDS_CMD: u32 = legacy::TrustyKeymasterOperation::SetAttestationIds as u32;
    const SET_ATTESTATION_KEY_CMD: u32 = legacy::TrustyKeymasterOperation::SetAttestationKey as u32;

    #[test]
    fn connection_test() {
        if !cfg!(kmr_enabled) {
            skip!("KeyMint Rust TA not configured");
        }

        // Only doing a connection test because the auth token key is not available for unittests.
        let port1 = CString::try_new(KM_NS_TIPC_SRV_PORT).unwrap();
        let _session1 = Handle::connect(port1.as_c_str()).unwrap();
        let port2 = CString::try_new(KM_SEC_TIPC_SRV_PORT).unwrap();
        let _session2 = Handle::connect(port2.as_c_str()).unwrap();
        let port3 = CString::try_new(KM_NS_LEGACY_TIPC_SRV_PORT).unwrap();
        let _session3 = Handle::connect(port3.as_c_str()).unwrap();
    }

    #[test]
    fn test_access_policy() {
        if !cfg!(kmr_enabled) {
            skip!("KeyMint Rust TA not configured");
        }

        // Test whether the access policy is in action.
        // Keymint unit test app should be able to connect to the KM secure service.
        let port = CString::try_new(KM_SEC_TIPC_SRV_PORT).unwrap();
        Handle::connect(port.as_c_str())
            .expect("Keymint unit test app should be able to connect to the KM secure service");

        // Keymint unit test app should not be able to call the attestation id provisioning API
        // in the KM secure service.
        let err = set_attestation_ids_secure().expect_err(
            "An error is expected. Keymint unit test app shouldn't be able to provision",
        );
        assert_eq!(err, TipcError::SystemError(trusty_sys::Error::NoMsg));
    }

    fn check_response_status(rsp: &KMMessage) -> Result<(), ErrorCode> {
        let error_code = legacy::deserialize_trusty_rsp_error_code(&rsp.0)
            .expect("Couldn't retrieve error code");
        if error_code == ErrorCode::Ok {
            Ok(())
        } else {
            Err(error_code)
        }
    }

    fn get_message_request(cmd: u32) -> Vec<u8> {
        (cmd << legacy::TRUSTY_CMD_SHIFT).to_ne_bytes().to_vec()
    }

    fn get_response_status(session: &Handle) -> Result<(), ErrorCode> {
        let mut buf = [0; KEYMINT_MAX_BUFFER_LENGTH as usize];
        let response: KMMessage =
            session.recv(&mut buf).map_err(|_| ErrorCode::SecureHwCommunicationFailed)?;
        check_response_status(&response)
    }

    fn get_configure_boot_patchlevel_message(
        boot_patchlevel: u32,
    ) -> Result<Vec<u8>, legacy::Error> {
        let mut req = get_message_request(CONFIGURE_BOOT_PATCHLEVEL_CMD);
        boot_patchlevel.serialize_into(&mut req)?;
        Ok(req)
    }

    fn get_set_boot_params_message(
        os_version: u32,
        os_patchlevel: u32,
        device_locked: bool,
        verified_boot_state: VerifiedBootState,
        verified_boot_key: Vec<u8>,
        verified_boot_hash: Vec<u8>,
    ) -> Result<Vec<u8>, legacy::Error> {
        let mut req = get_message_request(SET_BOOT_PARAMS_CMD);
        os_version.serialize_into(&mut req)?;
        os_patchlevel.serialize_into(&mut req)?;
        device_locked.serialize_into(&mut req)?;
        verified_boot_state.serialize_into(&mut req)?;
        verified_boot_key.serialize_into(&mut req)?;
        verified_boot_hash.serialize_into(&mut req)?;
        Ok(req)
    }

    fn get_set_attestation_ids_message(
        brand: &Vec<u8>,
        product: &Vec<u8>,
        device: &Vec<u8>,
        serial: &Vec<u8>,
        imei: &Vec<u8>,
        meid: &Vec<u8>,
        manufacturer: &Vec<u8>,
        model: &Vec<u8>,
    ) -> Result<Vec<u8>, legacy::Error> {
        let mut req = get_message_request(SET_ATTESTATION_IDS_CMD);
        brand.serialize_into(&mut req)?;
        product.serialize_into(&mut req)?;
        device.serialize_into(&mut req)?;
        serial.serialize_into(&mut req)?;
        imei.serialize_into(&mut req)?;
        meid.serialize_into(&mut req)?;
        manufacturer.serialize_into(&mut req)?;
        model.serialize_into(&mut req)?;
        Ok(req)
    }

    fn get_set_attestation_key_message(
        algorithm: Algorithm,
        content: &[u8],
    ) -> Result<Vec<u8>, legacy::Error> {
        let mut req = get_message_request(SET_ATTESTATION_KEY_CMD);
        (algorithm as u32).serialize_into(&mut req)?;
        (content.len() as u32).serialize_into(&mut req)?;
        req.extend_from_slice(content);
        Ok(req)
    }

    #[test]
    fn set_attestation_keys_certs() {
        if !cfg!(kmr_enabled) {
            skip!("KeyMint Rust TA not configured");
        }

        let port = CString::try_new(KM_NS_LEGACY_TIPC_SRV_PORT).unwrap();
        let session = Handle::connect(port.as_c_str()).unwrap();

        let req = get_set_attestation_key_message(Algorithm::Ec, &[0; 1024])
            .expect("couldn't construct SetAttestatonKey request");
        let set_attestation_key_req = KMMessage(req);
        // Sending `SetAttestationKey` request and processing response
        session.send(&set_attestation_key_req).unwrap();
        let buf = &mut [0; KEYMINT_MAX_BUFFER_LENGTH as usize];
        let response: KMMessage = session.recv(buf).expect("Didn't get response");
        let km_error_code = check_response_status(&response);
        expect!(km_error_code.is_ok(), "Should be able to call SetAttestatonKeys");
    }

    fn set_attestation_ids_secure() -> tipc::Result<()> {
        let port = CString::try_new(KM_SEC_TIPC_SRV_PORT).unwrap();
        let session = Handle::connect(port.as_c_str()).unwrap();

        // Creating a SetAttestationIds message
        let brand = b"no brand".to_vec();
        let device = b"a new device".to_vec();
        let product = b"p1".to_vec();
        let serial = vec![b'5'; 64];
        let imei = b"7654321".to_vec();
        let meid = b"1234567".to_vec();
        let manufacturer = b"a manufacturer".to_vec();
        let model = b"the new one".to_vec();
        let req = get_set_attestation_ids_message(
            &brand,
            &device,
            &product,
            &serial,
            &imei,
            &meid,
            &manufacturer,
            &model,
        )
        .expect("couldn't construct SetAttestatonIds request");
        let set_attestation_ids_req = KMMessage(req);

        // Sending SetAttestationIds
        session.send(&set_attestation_ids_req).unwrap();
        let buf = &mut [0; KEYMINT_MAX_BUFFER_LENGTH as usize];
        session.recv(buf)
    }

    #[test]
    fn set_attestation_ids() {
        if !cfg!(kmr_enabled) {
            skip!("KeyMint Rust TA not configured");
        }

        let port = CString::try_new(KM_NS_LEGACY_TIPC_SRV_PORT).unwrap();
        let session = Handle::connect(port.as_c_str()).unwrap();

        // Creating a SetAttestationIds message
        let brand = b"no brand".to_vec();
        let device = b"a new device".to_vec();
        let product = b"p1".to_vec();
        let serial = vec![b'5'; 64];
        let imei = b"7654321".to_vec();
        let meid = b"1234567".to_vec();
        let manufacturer = b"a manufacturer".to_vec();
        let model = b"the new one".to_vec();
        let req = get_set_attestation_ids_message(
            &brand,
            &device,
            &product,
            &serial,
            &imei,
            &meid,
            &manufacturer,
            &model,
        )
        .expect("couldn't construct SetAttestatonIds request");
        let set_attestation_ids_req = KMMessage(req);

        // Sending SetAttestationIds
        session.send(&set_attestation_ids_req).unwrap();
        let buf = &mut [0; KEYMINT_MAX_BUFFER_LENGTH as usize];
        let response: KMMessage = session.recv(buf).expect("Didn't get response");
        let km_error_code = check_response_status(&response);
        expect!(km_error_code.is_ok(), "Should be able to call SetAttestationIds");
    }

    // The following tests are all skipped because they try to SetBootParams more than once
    // which isn't allowed.  However, the code is preserved to allow for future manual testing.

    #[test]
    fn send_setbootparams_configure_setbootparams_configure() {
        if true {
            skip!("SetBootParams cannot be performed more than once");
        }

        let port = CString::try_new(KM_NS_LEGACY_TIPC_SRV_PORT).unwrap();
        let session = Handle::connect(port.as_c_str()).unwrap();

        // Creating a SetBootParams message
        let os_version = 1;
        let os_patchlevel = 0x202010;
        let device_locked = true;
        let verified_boot_state = VerifiedBootState::Unverified;
        let verified_boot_key = [0u8; 32];
        let verified_boot_hash = [0u8; 32];
        let req = get_set_boot_params_message(
            os_version,
            os_patchlevel,
            device_locked,
            verified_boot_state,
            verified_boot_key.to_vec(),
            verified_boot_hash.to_vec(),
        )
        .expect("couldn't construct SetBootParams request");
        let set_boot_param_req = KMMessage(req);

        // Sending SetBootParamsRequest
        session.send(&set_boot_param_req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(km_error_code.is_ok(), "Should be able to call SetBootParams");

        // Creating a ConfigureBootPatchlevelRequest message
        let boot_patchlevel = 0x20201010;
        let req =
            get_configure_boot_patchlevel_message(boot_patchlevel).expect("Couldn't construct msg");
        let configure_bootpatchlevel_req = KMMessage(req);

        // Sending ConfigureBootPatchlevelRequest
        session.send(&configure_bootpatchlevel_req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(km_error_code.is_ok(), "Should be able to call ConfigureBootPatchlevel");

        // Checking that sending the message a second time fails
        session.send(&set_boot_param_req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(km_error_code.is_err(), "Shouldn't be able to call SetBootParams a second time");

        // Checking that sending the message a second time fails
        session.send(&configure_bootpatchlevel_req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(
            km_error_code.is_err(),
            "Shouldn't be able to call ConfigureBootPatchlevel a second time"
        );
    }

    #[test]
    fn send_configure_configure_setbootparams_setbootparams() {
        if true {
            skip!("SetBootParams cannot be performed more than once");
        }

        let port = CString::try_new(KM_NS_LEGACY_TIPC_SRV_PORT).unwrap();
        let session = Handle::connect(port.as_c_str()).unwrap();

        // Creating a ConfigureBootPatchlevelRequest message
        let boot_patchlevel = 0x20201010;
        let req =
            get_configure_boot_patchlevel_message(boot_patchlevel).expect("Couldn't construct msg");
        let req = KMMessage(req);

        // Sending ConfigureBootPatchlevelRequest
        session.send(&req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(km_error_code.is_ok(), "Should be able to call ConfigureBootPatchlevel");

        // Checking that sending the message a second time fails
        session.send(&req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(
            km_error_code.is_err(),
            "Shouldn't be able to call ConfigureBootPatchlevel a second time"
        );

        // Creating a SetBootParams message
        let os_version = 1;
        let os_patchlevel = 0x202010;
        let device_locked = true;
        let verified_boot_state = VerifiedBootState::Unverified;
        let verified_boot_key = [0u8; 32];
        let verified_boot_hash = [0u8; 32];
        let req = get_set_boot_params_message(
            os_version,
            os_patchlevel,
            device_locked,
            verified_boot_state,
            verified_boot_key.to_vec(),
            verified_boot_hash.to_vec(),
        )
        .expect("couldn't construct SetBootParams request");
        let req = KMMessage(req);

        // Sending SetBootParamsRequest
        session.send(&req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(km_error_code.is_ok(), "Should be able to call SetBootParams");

        // Checking that sending the message a second time fails
        session.send(&req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(km_error_code.is_err(), "Shouldn't be able to call SetBootParams a second time");
    }

    #[test]
    fn send_setbootparams_setbootparams_configure_configure() {
        if true {
            skip!("SetBootParams cannot be performed more than once");
        }

        let port = CString::try_new(KM_NS_LEGACY_TIPC_SRV_PORT).unwrap();
        let session = Handle::connect(port.as_c_str()).unwrap();

        // Creating a SetBootParams message
        let os_version = 1;
        let os_patchlevel = 0x202010;
        let device_locked = true;
        let verified_boot_state = VerifiedBootState::Unverified;
        let verified_boot_key = [0u8; 32];
        let verified_boot_hash = [0u8; 32];
        let req = get_set_boot_params_message(
            os_version,
            os_patchlevel,
            device_locked,
            verified_boot_state,
            verified_boot_key.to_vec(),
            verified_boot_hash.to_vec(),
        )
        .expect("couldn't construct SetBootParams request");
        let req = KMMessage(req);

        // Sending SetBootParamsRequest
        session.send(&req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(km_error_code.is_ok(), "Should be able to call SetBootParams");

        // Checking that sending the message a second time fails
        session.send(&req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(km_error_code.is_err(), "Shouldn't be able to call SetBootParams a second time");

        // Creating a ConfigureBootPatchlevelRequest message
        let boot_patchlevel = 0x20201010;
        let req =
            get_configure_boot_patchlevel_message(boot_patchlevel).expect("Couldn't construct msg");
        let req = KMMessage(req);

        // Sending ConfigureBootPatchlevelRequest
        session.send(&req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(km_error_code.is_ok(), "Should be able to call ConfigureBootPatchlevel");

        // Checking that sending the message a second time fails
        session.send(&req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(
            km_error_code.is_err(),
            "Shouldn't be able to call ConfigureBootPatchlevel a second time"
        );
    }

    #[test]
    fn send_configure_setbootparams_setbootparams_configure() {
        if true {
            skip!("SetBootParams cannot be performed more than once");
        }

        let port = CString::try_new(KM_NS_LEGACY_TIPC_SRV_PORT).unwrap();
        let session = Handle::connect(port.as_c_str()).unwrap();

        // Creating a ConfigureBootPatchlevelRequest message
        let boot_patchlevel = 0x20201010;
        let req =
            get_configure_boot_patchlevel_message(boot_patchlevel).expect("Couldn't construct msg");
        let configure_bootpatchlevel_req = KMMessage(req);

        // Sending ConfigureBootPatchlevelRequest
        session.send(&configure_bootpatchlevel_req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(km_error_code.is_ok(), "Should be able to call ConfigureBootPatchlevel");

        // Creating a SetBootParams message
        let os_version = 1;
        let os_patchlevel = 0x202010;
        let device_locked = true;
        let verified_boot_state = VerifiedBootState::Unverified;
        let verified_boot_key = [0u8; 32];
        let verified_boot_hash = [0u8; 32];
        let req = get_set_boot_params_message(
            os_version,
            os_patchlevel,
            device_locked,
            verified_boot_state,
            verified_boot_key.to_vec(),
            verified_boot_hash.to_vec(),
        )
        .expect("couldn't construct SetBootParams request");
        let set_boot_param_req = KMMessage(req);

        // Sending SetBootParamsRequest
        session.send(&set_boot_param_req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(km_error_code.is_ok(), "Should be able to call SetBootParams");

        // Checking that sending the message a second time fails
        session.send(&set_boot_param_req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(km_error_code.is_err(), "Shouldn't be able to call SetBootParams a second time");

        // Checking that sending the message a second time fails
        session.send(&configure_bootpatchlevel_req).unwrap();
        let km_error_code = get_response_status(&session);
        expect!(
            km_error_code.is_err(),
            "Shouldn't be able to call ConfigureBootPatchlevel a second time"
        );
    }
}
