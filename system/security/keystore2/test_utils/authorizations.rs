// Copyright 2022, The Android Open Source Project
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

//! This module implements test utils to create Autherizations.

use std::ops::Deref;

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Algorithm::Algorithm, BlockMode::BlockMode, Digest::Digest, EcCurve::EcCurve,
    KeyParameter::KeyParameter, KeyParameterValue::KeyParameterValue, KeyPurpose::KeyPurpose,
    PaddingMode::PaddingMode, Tag::Tag,
};

/// Helper struct to create set of Authorizations.
#[derive(Debug, Clone, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct AuthSetBuilder(Vec<KeyParameter>);

impl Default for AuthSetBuilder {
    fn default() -> Self {
        Self::new()
    }
}

impl AuthSetBuilder {
    /// Creates new Authorizations list.
    pub fn new() -> Self {
        Self(Vec::new())
    }

    /// Add Purpose.
    pub fn purpose(mut self, p: KeyPurpose) -> Self {
        self.0.push(KeyParameter { tag: Tag::PURPOSE, value: KeyParameterValue::KeyPurpose(p) });
        self
    }

    /// Add Digest.
    pub fn digest(mut self, d: Digest) -> Self {
        self.0.push(KeyParameter { tag: Tag::DIGEST, value: KeyParameterValue::Digest(d) });
        self
    }

    /// Add Algorithm.
    pub fn algorithm(mut self, a: Algorithm) -> Self {
        self.0.push(KeyParameter { tag: Tag::ALGORITHM, value: KeyParameterValue::Algorithm(a) });
        self
    }

    /// Add EC-Curve.
    pub fn ec_curve(mut self, e: EcCurve) -> Self {
        self.0.push(KeyParameter { tag: Tag::EC_CURVE, value: KeyParameterValue::EcCurve(e) });
        self
    }

    /// Add Attestation-Challenge.
    pub fn attestation_challenge(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::ATTESTATION_CHALLENGE,
            value: KeyParameterValue::Blob(b),
        });
        self
    }

    /// Add No_auth_required.
    pub fn no_auth_required(mut self) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::NO_AUTH_REQUIRED,
            value: KeyParameterValue::BoolValue(true),
        });
        self
    }

    /// Add RSA_public_exponent.
    pub fn rsa_public_exponent(mut self, e: i64) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::RSA_PUBLIC_EXPONENT,
            value: KeyParameterValue::LongInteger(e),
        });
        self
    }

    /// Add key size.
    pub fn key_size(mut self, s: i32) -> Self {
        self.0.push(KeyParameter { tag: Tag::KEY_SIZE, value: KeyParameterValue::Integer(s) });
        self
    }

    /// Add block mode.
    pub fn block_mode(mut self, b: BlockMode) -> Self {
        self.0.push(KeyParameter { tag: Tag::BLOCK_MODE, value: KeyParameterValue::BlockMode(b) });
        self
    }

    /// Add certificate_not_before.
    pub fn cert_not_before(mut self, b: i64) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::CERTIFICATE_NOT_BEFORE,
            value: KeyParameterValue::DateTime(b),
        });
        self
    }

    /// Add certificate_not_after.
    pub fn cert_not_after(mut self, a: i64) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::CERTIFICATE_NOT_AFTER,
            value: KeyParameterValue::DateTime(a),
        });
        self
    }

    /// Add padding mode.
    pub fn padding_mode(mut self, p: PaddingMode) -> Self {
        self.0.push(KeyParameter { tag: Tag::PADDING, value: KeyParameterValue::PaddingMode(p) });
        self
    }

    /// Add mgf_digest.
    pub fn mgf_digest(mut self, d: Digest) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::RSA_OAEP_MGF_DIGEST,
            value: KeyParameterValue::Digest(d),
        });
        self
    }

    /// Add nonce.
    pub fn nonce(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter { tag: Tag::NONCE, value: KeyParameterValue::Blob(b) });
        self
    }

    /// Add CALLER_NONCE.
    pub fn caller_nonce(mut self) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::CALLER_NONCE,
            value: KeyParameterValue::BoolValue(true),
        });
        self
    }

    /// Add MAC length.
    pub fn mac_length(mut self, l: i32) -> Self {
        self.0.push(KeyParameter { tag: Tag::MAC_LENGTH, value: KeyParameterValue::Integer(l) });
        self
    }

    /// Add min MAC length.
    pub fn min_mac_length(mut self, l: i32) -> Self {
        self.0
            .push(KeyParameter { tag: Tag::MIN_MAC_LENGTH, value: KeyParameterValue::Integer(l) });
        self
    }

    /// Add Attestation-Device-Brand.
    pub fn attestation_device_brand(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::ATTESTATION_ID_BRAND,
            value: KeyParameterValue::Blob(b),
        });
        self
    }

    /// Add Attestation-Device-name.
    pub fn attestation_device_name(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::ATTESTATION_ID_DEVICE,
            value: KeyParameterValue::Blob(b),
        });
        self
    }

    /// Add Attestation-Device-Product-Name.
    pub fn attestation_device_product_name(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::ATTESTATION_ID_PRODUCT,
            value: KeyParameterValue::Blob(b),
        });
        self
    }

    /// Add Attestation-Device-Serial.
    pub fn attestation_device_serial(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::ATTESTATION_ID_SERIAL,
            value: KeyParameterValue::Blob(b),
        });
        self
    }

    /// Add Attestation-Device-IMEI.
    pub fn attestation_device_imei(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::ATTESTATION_ID_IMEI,
            value: KeyParameterValue::Blob(b),
        });
        self
    }

    /// Add Attestation-Device-IMEI.
    pub fn attestation_device_second_imei(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::ATTESTATION_ID_SECOND_IMEI,
            value: KeyParameterValue::Blob(b),
        });
        self
    }

    /// Add Attestation-Device-MEID.
    pub fn attestation_device_meid(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::ATTESTATION_ID_MEID,
            value: KeyParameterValue::Blob(b),
        });
        self
    }

    /// Add Attestation-Device-Manufacturer.
    pub fn attestation_device_manufacturer(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::ATTESTATION_ID_MANUFACTURER,
            value: KeyParameterValue::Blob(b),
        });
        self
    }

    /// Add Attestation-Device-Model.
    pub fn attestation_device_model(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::ATTESTATION_ID_MODEL,
            value: KeyParameterValue::Blob(b),
        });
        self
    }

    /// Set active date-time.
    pub fn active_date_time(mut self, date: i64) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::ACTIVE_DATETIME,
            value: KeyParameterValue::DateTime(date),
        });
        self
    }

    /// Set origination expire date-time.
    pub fn origination_expire_date_time(mut self, date: i64) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::ORIGINATION_EXPIRE_DATETIME,
            value: KeyParameterValue::DateTime(date),
        });
        self
    }

    /// Set usage expire date-time.
    pub fn usage_expire_date_time(mut self, date: i64) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::USAGE_EXPIRE_DATETIME,
            value: KeyParameterValue::DateTime(date),
        });
        self
    }

    /// Set boot loader only.
    pub fn boot_loader_only(mut self) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::BOOTLOADER_ONLY,
            value: KeyParameterValue::BoolValue(true),
        });
        self
    }

    /// Set early boot only.
    pub fn early_boot_only(mut self) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::EARLY_BOOT_ONLY,
            value: KeyParameterValue::BoolValue(true),
        });
        self
    }

    /// Set max uses per boot.
    pub fn max_uses_per_boot(mut self, max_uses: i32) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::MAX_USES_PER_BOOT,
            value: KeyParameterValue::Integer(max_uses),
        });
        self
    }

    /// Set max usage count.
    pub fn usage_count_limit(mut self, usage_count: i32) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::USAGE_COUNT_LIMIT,
            value: KeyParameterValue::Integer(usage_count),
        });
        self
    }

    /// Set creation date-time.
    pub fn creation_date_time(mut self, date: i64) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::CREATION_DATETIME,
            value: KeyParameterValue::DateTime(date),
        });
        self
    }

    /// Set include unique id.
    pub fn include_unique_id(mut self) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::INCLUDE_UNIQUE_ID,
            value: KeyParameterValue::BoolValue(true),
        });
        self
    }

    /// Add app-data.
    pub fn app_data(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter { tag: Tag::APPLICATION_DATA, value: KeyParameterValue::Blob(b) });
        self
    }

    /// Add app-id.
    pub fn app_id(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter { tag: Tag::APPLICATION_ID, value: KeyParameterValue::Blob(b) });
        self
    }

    /// Set device-unique-attestation.
    pub fn device_unique_attestation(mut self) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::DEVICE_UNIQUE_ATTESTATION,
            value: KeyParameterValue::BoolValue(true),
        });
        self
    }

    /// Add certificate serial number.
    pub fn cert_serial(mut self, b: Vec<u8>) -> Self {
        self.0
            .push(KeyParameter { tag: Tag::CERTIFICATE_SERIAL, value: KeyParameterValue::Blob(b) });
        self
    }

    /// Add certificate subject name.
    pub fn cert_subject_name(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::CERTIFICATE_SUBJECT,
            value: KeyParameterValue::Blob(b),
        });
        self
    }
}

impl Deref for AuthSetBuilder {
    type Target = Vec<KeyParameter>;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}
