// Copyright 2020, The Android Open Source Project
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

//! This is the implementation for the remote provisioning AIDL interface between
//! the network providers for remote provisioning and the system. This interface
//! allows the caller to prompt the Remote Provisioning HAL to generate keys and
//! CBOR blobs that can be ferried to a provisioning server that will return
//! certificate chains signed by some root authority and stored in a keystore SQLite
//! DB.

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Algorithm::Algorithm, AttestationKey::AttestationKey, Certificate::Certificate,
    KeyParameter::KeyParameter, KeyParameterValue::KeyParameterValue, SecurityLevel::SecurityLevel,
    Tag::Tag,
};
use android_security_rkp_aidl::aidl::android::security::rkp::RemotelyProvisionedKey::RemotelyProvisionedKey;
use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, KeyDescriptor::KeyDescriptor,
};
use anyhow::{Context, Result};
use keystore2_crypto::parse_subject_from_certificate;

use crate::database::Uuid;
use crate::error::wrapped_rkpd_error_to_ks_error;
use crate::globals::get_remotely_provisioned_component_name;
use crate::ks_err;
use crate::metrics_store::log_rkp_error_stats;
use crate::watchdog_helper::watchdog as wd;
use android_security_metrics::aidl::android::security::metrics::RkpError::RkpError as MetricsRkpError;

/// Contains helper functions to check if remote provisioning is enabled on the system and, if so,
/// to assign and retrieve attestation keys and certificate chains.
#[derive(Default)]
pub struct RemProvState {
    security_level: SecurityLevel,
    km_uuid: Uuid,
}

impl RemProvState {
    /// Creates a RemProvState struct.
    pub fn new(security_level: SecurityLevel, km_uuid: Uuid) -> Self {
        Self { security_level, km_uuid }
    }

    /// Returns the uuid for the KM instance attached to this RemProvState struct.
    pub fn get_uuid(&self) -> Uuid {
        self.km_uuid
    }

    fn is_rkp_only(&self) -> bool {
        let default_value = false;

        let property_name = match self.security_level {
            SecurityLevel::STRONGBOX => "remote_provisioning.strongbox.rkp_only",
            SecurityLevel::TRUSTED_ENVIRONMENT => "remote_provisioning.tee.rkp_only",
            _ => return default_value,
        };

        rustutils::system_properties::read_bool(property_name, default_value)
            .unwrap_or(default_value)
    }

    fn is_asymmetric_key(&self, params: &[KeyParameter]) -> bool {
        params.iter().any(|kp| {
            matches!(
                kp,
                KeyParameter {
                    tag: Tag::ALGORITHM,
                    value: KeyParameterValue::Algorithm(Algorithm::RSA)
                } | KeyParameter {
                    tag: Tag::ALGORITHM,
                    value: KeyParameterValue::Algorithm(Algorithm::EC)
                }
            )
        })
    }

    /// Fetches attestation key and corresponding certificates from RKPD.
    pub fn get_rkpd_attestation_key_and_certs(
        &self,
        key: &KeyDescriptor,
        caller_uid: u32,
        params: &[KeyParameter],
    ) -> Result<Option<(AttestationKey, Certificate)>> {
        if !self.is_asymmetric_key(params) || key.domain != Domain::APP {
            Ok(None)
        } else {
            match get_rkpd_attestation_key(&self.security_level, caller_uid) {
                Err(e) => {
                    if self.is_rkp_only() {
                        log::error!("Error occurred: {:?}", e);
                        return Err(wrapped_rkpd_error_to_ks_error(&e)).context(format!("{e:?}"));
                    }
                    log::warn!("Error occurred: {:?}", e);
                    log_rkp_error_stats(
                        MetricsRkpError::FALL_BACK_DURING_HYBRID,
                        &self.security_level,
                    );
                    Ok(None)
                }
                Ok(rkpd_key) => Ok(Some((
                    AttestationKey {
                        keyBlob: rkpd_key.keyBlob,
                        attestKeyParams: vec![],
                        // Batch certificate is at the beginning of the certificate chain.
                        issuerSubjectName: parse_subject_from_certificate(
                            &rkpd_key.encodedCertChain,
                        )
                        .context(ks_err!("Failed to parse subject."))?,
                    },
                    Certificate { encodedCertificate: rkpd_key.encodedCertChain },
                ))),
            }
        }
    }
}

fn get_rkpd_attestation_key(
    security_level: &SecurityLevel,
    caller_uid: u32,
) -> Result<RemotelyProvisionedKey> {
    // Depending on the Android release, RKP may not have been mandatory for the
    // TEE or StrongBox KM instances. In such cases, lookup failure for the IRPC
    // HAL service is WAI and should not cause a failure. The error should be caught
    // by the calling function and allow for natural fallback to the factory key.
    let rpc_name = get_remotely_provisioned_component_name(security_level)
        .context(ks_err!("Trying to get IRPC name."))?;
    let _wd = wd::watch_millis("Calling get_rkpd_attestation_key()", 500);
    rkpd_client::get_rkpd_attestation_key(&rpc_name, caller_uid)
}
