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

//! This module implements test utils to generate various types of keys.

use anyhow::Result;
use core::ops::Range;
use nix::unistd::getuid;
use std::collections::HashSet;
use std::fmt::Write;

use binder::ThreadState;

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Algorithm::Algorithm, BlockMode::BlockMode, Digest::Digest, EcCurve::EcCurve,
    ErrorCode::ErrorCode, HardwareAuthenticatorType::HardwareAuthenticatorType,
    KeyOrigin::KeyOrigin, KeyParameter::KeyParameter, KeyParameterValue::KeyParameterValue,
    KeyPurpose::KeyPurpose, PaddingMode::PaddingMode, SecurityLevel::SecurityLevel, Tag::Tag,
};
use android_system_keystore2::aidl::android::system::keystore2::{
    AuthenticatorSpec::AuthenticatorSpec, Authorization::Authorization,
    CreateOperationResponse::CreateOperationResponse, Domain::Domain,
    IKeystoreSecurityLevel::IKeystoreSecurityLevel, KeyDescriptor::KeyDescriptor,
    KeyMetadata::KeyMetadata, ResponseCode::ResponseCode,
};

use crate::authorizations::AuthSetBuilder;
use android_system_keystore2::binder::{ExceptionCode, Result as BinderResult};

use crate::ffi_test_utils::{
    get_os_patchlevel, get_os_version, get_value_from_attest_record, get_vendor_patchlevel,
    validate_certchain_with_strict_issuer_check,
};

/// Shell namespace.
pub const SELINUX_SHELL_NAMESPACE: i64 = 1;
/// Vold namespace.
pub const SELINUX_VOLD_NAMESPACE: i64 = 100;

/// SU context.
pub const TARGET_SU_CTX: &str = "u:r:su:s0";

/// Vold context
pub const TARGET_VOLD_CTX: &str = "u:r:vold:s0";

/// Allowed tags in generated/imported key authorizations.
/// See hardware/interfaces/security/keymint/aidl/android/hardware/security/keymint/Tag.aidl for the
/// list feature tags.
/// Note: This list need to be updated whenever a new Tag is introduced and is expected to be added
/// in key authorizations.
pub const ALLOWED_TAGS_IN_KEY_AUTHS: &[Tag] = &[
    Tag::ACTIVE_DATETIME,
    Tag::ALGORITHM,
    Tag::ALLOW_WHILE_ON_BODY,
    Tag::AUTH_TIMEOUT,
    Tag::BLOCK_MODE,
    Tag::BOOTLOADER_ONLY,
    Tag::BOOT_PATCHLEVEL,
    Tag::CALLER_NONCE,
    Tag::CREATION_DATETIME,
    Tag::DIGEST,
    Tag::EARLY_BOOT_ONLY,
    Tag::EC_CURVE,
    Tag::IDENTITY_CREDENTIAL_KEY,
    Tag::INCLUDE_UNIQUE_ID,
    Tag::KEY_SIZE,
    Tag::MAX_BOOT_LEVEL,
    Tag::MAX_USES_PER_BOOT,
    Tag::MIN_MAC_LENGTH,
    Tag::NO_AUTH_REQUIRED,
    Tag::ORIGIN,
    Tag::ORIGINATION_EXPIRE_DATETIME,
    Tag::OS_PATCHLEVEL,
    Tag::OS_VERSION,
    Tag::PADDING,
    Tag::PURPOSE,
    Tag::ROLLBACK_RESISTANCE,
    Tag::RSA_OAEP_MGF_DIGEST,
    Tag::RSA_PUBLIC_EXPONENT,
    Tag::STORAGE_KEY,
    Tag::TRUSTED_CONFIRMATION_REQUIRED,
    Tag::TRUSTED_USER_PRESENCE_REQUIRED,
    Tag::UNLOCKED_DEVICE_REQUIRED,
    Tag::USAGE_COUNT_LIMIT,
    Tag::USAGE_EXPIRE_DATETIME,
    Tag::USER_AUTH_TYPE,
    Tag::USER_ID,
    Tag::USER_SECURE_ID,
    Tag::VENDOR_PATCHLEVEL,
];

/// Key parameters to generate a key.
pub struct KeyParams {
    /// Key Size.
    pub key_size: i32,
    /// Key Purposes.
    pub purpose: Vec<KeyPurpose>,
    /// Padding Mode.
    pub padding: Option<PaddingMode>,
    /// Digest.
    pub digest: Option<Digest>,
    /// MFG Digest.
    pub mgf_digest: Option<Digest>,
    /// Block Mode.
    pub block_mode: Option<BlockMode>,
    /// Attestation challenge.
    pub att_challenge: Option<Vec<u8>>,
}

/// DER-encoded PKCS#8 format RSA key. Generated using:
/// openssl genrsa 2048 | openssl pkcs8 -topk8 -nocrypt -outform der | hexdump -e '30/1  "%02X" "\n"'
pub static RSA_2048_KEY: &[u8] = &[
    0x30, 0x82, 0x04, 0xBD, 0x02, 0x01, 0x00, 0x30, 0x0D, 0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7,
    0x0D, 0x01, 0x01, 0x01, 0x05, 0x00, 0x04, 0x82, 0x04, 0xA7, 0x30, 0x82, 0x04, 0xA3, 0x02, 0x01,
    0x00, 0x02, 0x82, 0x01, 0x01, 0x00, 0xE5, 0x14, 0xE3, 0xC2, 0x43, 0xF3, 0x0F, 0xCC, 0x22, 0x73,
    0x9C, 0x84, 0xCC, 0x1B, 0x6C, 0x97, 0x4B, 0xC9, 0xDF, 0x1F, 0xE2, 0xB8, 0x80, 0x85, 0xF9, 0x27,
    0xAB, 0x97, 0x94, 0x58, 0x4B, 0xC9, 0x40, 0x94, 0x5A, 0xB4, 0xD4, 0xF8, 0xD0, 0x36, 0xC4, 0x86,
    0x17, 0x7D, 0xA2, 0x48, 0x6D, 0x40, 0xF0, 0xB9, 0x61, 0x4F, 0xCE, 0x65, 0x80, 0x88, 0x81, 0x59,
    0x95, 0x11, 0x24, 0xF4, 0x36, 0xB7, 0xB7, 0x37, 0x44, 0xF4, 0x6C, 0x1C, 0xEB, 0x04, 0x19, 0x78,
    0xB2, 0x29, 0x4D, 0x21, 0x44, 0x16, 0x57, 0x58, 0x6D, 0x7D, 0x56, 0xB5, 0x99, 0xDD, 0xD2, 0xAD,
    0x02, 0x9A, 0x72, 0x16, 0x67, 0xD6, 0x00, 0x9F, 0x69, 0xE0, 0x25, 0xEE, 0x7C, 0x86, 0x54, 0x27,
    0x4B, 0x50, 0xEF, 0x60, 0x52, 0x60, 0x82, 0xAA, 0x09, 0x15, 0x72, 0xD2, 0xEB, 0x01, 0x52, 0x04,
    0x39, 0x60, 0xBC, 0x5E, 0x95, 0x07, 0xC8, 0xC2, 0x3A, 0x3A, 0xE2, 0xA4, 0x99, 0x6B, 0x27, 0xE3,
    0xA3, 0x55, 0x69, 0xC4, 0xB3, 0x2D, 0x19, 0xC4, 0x34, 0x76, 0xFC, 0x27, 0xDA, 0x22, 0xB2, 0x62,
    0x69, 0x25, 0xDE, 0x0D, 0xE7, 0x54, 0x3C, 0xBB, 0x61, 0xD2, 0x20, 0xDA, 0x7B, 0x6E, 0x63, 0xBD,
    0x9A, 0x4B, 0xCD, 0x75, 0xC6, 0xA1, 0x5E, 0x1C, 0x3E, 0xD5, 0x63, 0x59, 0x22, 0x7E, 0xE0, 0x6C,
    0x98, 0x25, 0x63, 0x97, 0x56, 0xDF, 0x71, 0xF5, 0x4C, 0x78, 0xE9, 0xE1, 0xD5, 0xFC, 0xF8, 0x5A,
    0x5B, 0xF6, 0x1D, 0xFA, 0x5A, 0x99, 0x4C, 0x99, 0x19, 0x21, 0x1D, 0xF5, 0x24, 0x07, 0xEF, 0x8A,
    0xC9, 0x9F, 0xE7, 0x3F, 0xBB, 0x46, 0x1A, 0x16, 0x96, 0xC6, 0xD6, 0x12, 0x7E, 0xDA, 0xCB, 0xEB,
    0x2F, 0x1D, 0x3B, 0x31, 0xCC, 0x55, 0x63, 0xA2, 0x6F, 0x8A, 0xDE, 0x35, 0x52, 0x40, 0x04, 0xBF,
    0xE0, 0x82, 0x32, 0xE1, 0x6D, 0x8B, 0x02, 0x03, 0x01, 0x00, 0x01, 0x02, 0x82, 0x01, 0x00, 0x2D,
    0x1F, 0x71, 0x41, 0x79, 0xBA, 0xED, 0xD8, 0xAA, 0xCC, 0x94, 0xFE, 0xFF, 0x69, 0x43, 0x79, 0x85,
    0xBF, 0x2C, 0xC9, 0x0E, 0x12, 0x83, 0x96, 0x60, 0x1E, 0x75, 0x49, 0x35, 0x3A, 0x33, 0x2B, 0x60,
    0x22, 0x18, 0xBF, 0xD7, 0xD7, 0x6E, 0xC3, 0xEA, 0xEF, 0xF2, 0xBE, 0x97, 0x71, 0xA6, 0xBB, 0x8C,
    0xEF, 0x27, 0x00, 0xDE, 0x49, 0xD6, 0x08, 0x8D, 0x5A, 0x04, 0xE7, 0xCC, 0x9C, 0xA2, 0x0E, 0x8B,
    0xF3, 0x42, 0x0C, 0xD7, 0x22, 0xD7, 0x14, 0x06, 0xA4, 0x64, 0x8B, 0x88, 0x1A, 0xCE, 0x5B, 0x8C,
    0x36, 0xE9, 0xD2, 0x2F, 0x7B, 0x33, 0xE4, 0xA2, 0xB3, 0xDB, 0x78, 0x6A, 0x92, 0x89, 0x3F, 0x78,
    0xFD, 0xED, 0x8F, 0xEE, 0x48, 0xCC, 0x94, 0x75, 0x0D, 0x0C, 0x63, 0xD3, 0xD2, 0xE8, 0x47, 0x04,
    0x55, 0xD3, 0xD6, 0x3A, 0xB8, 0xDA, 0xFB, 0x76, 0x99, 0x48, 0x68, 0x0A, 0x92, 0xA2, 0xCD, 0xF7,
    0x45, 0x8B, 0x50, 0xFE, 0xF9, 0x1A, 0x33, 0x24, 0x3C, 0x2E, 0xDE, 0x88, 0xAD, 0xB2, 0x5B, 0x9F,
    0x44, 0xEA, 0xD1, 0x9F, 0xC7, 0x9F, 0x02, 0x5E, 0x31, 0x61, 0xB3, 0xD6, 0xE2, 0xE1, 0xBC, 0xFB,
    0x1C, 0xDB, 0xBD, 0xB2, 0x9A, 0xE5, 0xEF, 0xDA, 0xCD, 0x29, 0xA5, 0x45, 0xCC, 0x67, 0x01, 0x8B,
    0x1C, 0x1D, 0x0E, 0x8F, 0x73, 0x69, 0x4D, 0x4D, 0xF6, 0x9D, 0xA6, 0x6C, 0x9A, 0x1C, 0xF4, 0x5C,
    0xE4, 0x83, 0x9A, 0x77, 0x12, 0x01, 0xBD, 0xCE, 0x66, 0x3A, 0x4B, 0x3D, 0x6E, 0xE0, 0x6E, 0x82,
    0x98, 0xDE, 0x74, 0x11, 0x47, 0xEC, 0x7A, 0x3A, 0xA9, 0xD8, 0x48, 0x00, 0x26, 0x64, 0x47, 0x7B,
    0xAE, 0x55, 0x9D, 0x29, 0x22, 0xB4, 0xB3, 0xB9, 0xB1, 0x64, 0xEA, 0x3B, 0x5A, 0xD3, 0x3F, 0x8D,
    0x0F, 0x14, 0x7E, 0x4E, 0xB8, 0x1B, 0x06, 0xFC, 0xB1, 0x7E, 0xCD, 0xB9, 0x1A, 0x4E, 0xA1, 0x02,
    0x81, 0x81, 0x00, 0xF9, 0xDE, 0xEE, 0xED, 0x13, 0x2F, 0xBB, 0xE7, 0xE2, 0xB3, 0x2D, 0x98, 0xD2,
    0xE8, 0x25, 0x07, 0x5A, 0x1E, 0x51, 0x0A, 0xC8, 0xAD, 0x50, 0x4B, 0x80, 0xC6, 0x22, 0xF5, 0x9B,
    0x08, 0xE6, 0x3D, 0x01, 0xC6, 0x3E, 0xC8, 0xD2, 0x54, 0x9F, 0x91, 0x77, 0x95, 0xCD, 0xCA, 0xC7,
    0xE7, 0x47, 0x94, 0xA9, 0x5F, 0x4E, 0xBE, 0x31, 0x3D, 0xB4, 0xAF, 0x43, 0x0F, 0xDC, 0x8D, 0x9C,
    0x1E, 0x52, 0x7B, 0x72, 0x21, 0x34, 0xB3, 0x96, 0x7C, 0x9C, 0xB8, 0x51, 0x65, 0x60, 0xAC, 0x3D,
    0x11, 0x32, 0xB8, 0xD6, 0x34, 0x35, 0x66, 0xD0, 0x30, 0xB9, 0xE9, 0x67, 0x2C, 0x87, 0x73, 0x43,
    0x9C, 0x12, 0x16, 0x7D, 0x4A, 0xD9, 0xA3, 0x4C, 0x24, 0x64, 0x6A, 0x32, 0x8E, 0xC3, 0xD8, 0x00,
    0x90, 0x5C, 0x4D, 0x65, 0x01, 0x53, 0x8A, 0xD0, 0x87, 0xCE, 0x96, 0xEF, 0xFA, 0x73, 0x03, 0xF1,
    0xDC, 0x1B, 0x9B, 0x02, 0x81, 0x81, 0x00, 0xEA, 0xB3, 0x69, 0x00, 0x11, 0x0E, 0x50, 0xAA, 0xD3,
    0x22, 0x51, 0x78, 0x9D, 0xFF, 0x05, 0x62, 0xBC, 0x9A, 0x67, 0x86, 0xE1, 0xC5, 0x02, 0x2D, 0x14,
    0x11, 0x29, 0x30, 0xE7, 0x90, 0x5D, 0x72, 0x6F, 0xC5, 0x62, 0xEB, 0xD4, 0xB0, 0x3F, 0x3D, 0xDC,
    0xB9, 0xFC, 0x2B, 0x5C, 0xBD, 0x9E, 0x71, 0x81, 0x5C, 0xC5, 0xFE, 0xDF, 0x69, 0x73, 0x12, 0x66,
    0x92, 0x06, 0xD4, 0xD5, 0x8F, 0xDF, 0x14, 0x2E, 0x9C, 0xD0, 0x4C, 0xC2, 0x4D, 0x31, 0x2E, 0x47,
    0xA5, 0xDC, 0x8A, 0x83, 0x7B, 0xE8, 0xA5, 0xC3, 0x03, 0x98, 0xD8, 0xBF, 0xF4, 0x7D, 0x6E, 0x87,
    0x55, 0xE4, 0x0F, 0x15, 0x10, 0xC8, 0x76, 0x4F, 0xAD, 0x1D, 0x1C, 0x95, 0x41, 0x9D, 0x88, 0xEC,
    0x8C, 0xDA, 0xBA, 0x90, 0x7F, 0x8D, 0xD9, 0x8B, 0x47, 0x6C, 0x0C, 0xFF, 0xBA, 0x73, 0x00, 0x20,
    0x1F, 0xF7, 0x7E, 0x5F, 0xF4, 0xEC, 0xD1, 0x02, 0x81, 0x80, 0x16, 0xB7, 0x43, 0xB5, 0x5D, 0xD7,
    0x2B, 0x18, 0x0B, 0xAE, 0x0A, 0x69, 0x28, 0x53, 0x5E, 0x7A, 0x6A, 0xA0, 0xF2, 0xF1, 0x2E, 0x09,
    0x43, 0x91, 0x79, 0xA5, 0x89, 0xAC, 0x16, 0x6A, 0x1A, 0xB4, 0x55, 0x22, 0xF6, 0xB6, 0x3F, 0x18,
    0xDE, 0x60, 0xD5, 0x24, 0x53, 0x4F, 0x2A, 0x19, 0x46, 0x92, 0xA7, 0x4B, 0x38, 0xD7, 0x65, 0x96,
    0x9C, 0x84, 0x8A, 0x6E, 0x38, 0xB8, 0xCF, 0x06, 0x9A, 0xAD, 0x0A, 0x55, 0x26, 0x7B, 0x65, 0x24,
    0xF3, 0x02, 0x76, 0xB3, 0xE6, 0xB4, 0x01, 0xE1, 0x3C, 0x61, 0x3D, 0x68, 0x05, 0xAA, 0xD1, 0x26,
    0x7C, 0xE0, 0x51, 0x36, 0xE5, 0x21, 0x7F, 0x76, 0x02, 0xD6, 0xF4, 0x91, 0x07, 0x74, 0x27, 0x09,
    0xEF, 0xEF, 0x0F, 0xA5, 0x96, 0xFC, 0x5E, 0x20, 0xC1, 0xA3, 0x6F, 0x99, 0x4D, 0x45, 0x03, 0x6C,
    0x35, 0x45, 0xD7, 0x8F, 0x47, 0x41, 0x86, 0x8D, 0x62, 0x1D, 0x02, 0x81, 0x81, 0x00, 0xC3, 0x93,
    0x85, 0xA7, 0xFC, 0x8E, 0x85, 0x42, 0x14, 0x76, 0xC0, 0x95, 0x56, 0x73, 0xB0, 0xB5, 0x3A, 0x9D,
    0x20, 0x30, 0x11, 0xEA, 0xED, 0x89, 0x4A, 0xF3, 0x91, 0xF3, 0xA2, 0xC3, 0x76, 0x5B, 0x6A, 0x30,
    0x7D, 0xE2, 0x2F, 0x76, 0x3E, 0xFC, 0xF9, 0xF6, 0x31, 0xE0, 0xA0, 0x83, 0x92, 0x88, 0xDB, 0x57,
    0xC7, 0xD6, 0x3F, 0xAD, 0xCB, 0xAA, 0x45, 0xB6, 0xE1, 0xE2, 0x71, 0xA4, 0x56, 0x2C, 0xA7, 0x3B,
    0x1D, 0x89, 0x19, 0x50, 0xE1, 0xEE, 0xC2, 0xDD, 0xC0, 0x0D, 0xDC, 0xCB, 0x60, 0x6E, 0xE1, 0x37,
    0x1A, 0x23, 0x64, 0xB2, 0x03, 0xE4, 0x1A, 0xFA, 0xC3, 0xF4, 0x9D, 0x85, 0x42, 0xC6, 0xF4, 0x56,
    0x39, 0xB0, 0x1B, 0xE0, 0x75, 0xBA, 0x28, 0x04, 0xA8, 0x30, 0x57, 0x41, 0x33, 0x9F, 0x58, 0xA4,
    0xC7, 0xB1, 0x7D, 0x58, 0x8D, 0x84, 0x49, 0x40, 0xDA, 0x28, 0x81, 0x25, 0xC4, 0x41, 0x02, 0x81,
    0x80, 0x13, 0x20, 0x65, 0xD5, 0x96, 0x98, 0x8D, 0x16, 0x73, 0xA1, 0x31, 0x73, 0x79, 0xBA, 0xEC,
    0xB0, 0xD9, 0x0C, 0xF6, 0xEF, 0x2F, 0xC2, 0xE7, 0x96, 0x9B, 0xA1, 0x2D, 0xE9, 0xFB, 0x45, 0xB9,
    0xD0, 0x30, 0xE2, 0xBD, 0x30, 0x4F, 0xB6, 0xFE, 0x24, 0x02, 0xCF, 0x8D, 0x51, 0x48, 0x45, 0xD9,
    0xF7, 0x20, 0x53, 0x1C, 0x0B, 0xA9, 0x7E, 0xC2, 0xA2, 0x65, 0xCC, 0x3E, 0x0E, 0x0D, 0xF1, 0x62,
    0xDD, 0x5F, 0xBC, 0x55, 0x9B, 0x58, 0x26, 0x40, 0x6A, 0xEE, 0x02, 0x55, 0x36, 0xE9, 0xBA, 0x82,
    0x5A, 0xFD, 0x3C, 0xDF, 0xA6, 0x26, 0x32, 0x81, 0xA9, 0x5E, 0x46, 0xBE, 0xBA, 0xDC, 0xD3, 0x2A,
    0x3A, 0x3B, 0xC1, 0x4E, 0xF7, 0x1A, 0xDC, 0x4B, 0xAF, 0x67, 0x1B, 0x3A, 0x83, 0x0D, 0x04, 0xDE,
    0x27, 0x47, 0xFC, 0xE6, 0x39, 0x89, 0x7B, 0x66, 0xF9, 0x50, 0x4D, 0xF1, 0xAC, 0x20, 0x43, 0x7E,
    0xEE,
];

/// DER-encoded PKCS#8 format EC key. Generated using:
/// openssl ecparam -name prime256v1 -genkey | openssl pkcs8 -topk8 -nocrypt -outform der | hexdump -e '30/1  "%02X" "\n"'
pub static EC_P_256_KEY: &[u8] = &[
    0x30, 0x81, 0x87, 0x02, 0x01, 0x00, 0x30, 0x13, 0x06, 0x07, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x02,
    0x01, 0x06, 0x08, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x03, 0x01, 0x07, 0x04, 0x6D, 0x30, 0x6B, 0x02,
    0x01, 0x01, 0x04, 0x20, 0xB9, 0x1D, 0xAF, 0x50, 0xFD, 0xD8, 0x6A, 0x40, 0xAB, 0x2C, 0xCB, 0x54,
    0x4E, 0xED, 0xF1, 0x64, 0xBC, 0x30, 0x25, 0xFB, 0xC4, 0x69, 0x00, 0x34, 0x1A, 0x82, 0xA3, 0x72,
    0x5D, 0xC7, 0xA9, 0x85, 0xA1, 0x44, 0x03, 0x42, 0x00, 0x04, 0xE8, 0x53, 0x0A, 0xF2, 0xD3, 0x68,
    0x40, 0x48, 0x8C, 0xB4, 0x2F, 0x11, 0x34, 0xD7, 0xF4, 0x4A, 0x5C, 0x33, 0xFF, 0xF6, 0x2B, 0xF7,
    0x98, 0x0F, 0x02, 0xA5, 0xD7, 0x4F, 0xF9, 0xDE, 0x60, 0x9C, 0x6E, 0xB0, 0x45, 0xDA, 0x3F, 0xF4,
    0x34, 0x23, 0x9B, 0x4C, 0x3A, 0x09, 0x9C, 0x5E, 0x5D, 0x37, 0x96, 0xAC, 0x4A, 0xE7, 0x65, 0x2B,
    0xD6, 0x84, 0x98, 0xEA, 0x96, 0x91, 0xFB, 0x78, 0xED, 0x86,
];

/// DER-encoded PKCS#8 format RSA key -
///     Size: 2048
///     Public Exponent: 65537
///     Purpose: WRAP_KEY, ENCRYPT, DECRYPT
///     Encryption scheme: RSAES-PKCS1-v1_5
///         Digest: SHA_2_256
///         Padding: RSA_OAEP
/// This sample wrapping_key is taken from KeyMint tests
/// (see hardware/interfaces/security/keymint/aidl/vts/functional/KeyMintTest.cpp).
/// Similarly more test keys can be generated with below command -
/// openssl genrsa 2048 | openssl pkcs8 -topk8 -nocrypt -outform der | hexdump -e '30/1  "%02X" "\n"'
pub static WRAPPING_KEY: &[u8] = &[
    0x30, 0x82, 0x04, 0xbe, 0x02, 0x01, 0x00, 0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86, 0x48, 0x86, 0xf7,
    0x0d, 0x01, 0x01, 0x01, 0x05, 0x00, 0x04, 0x82, 0x04, 0xa8, 0x30, 0x82, 0x04, 0xa4, 0x02, 0x01,
    0x00, 0x02, 0x82, 0x01, 0x01, 0x00, 0xae, 0xc3, 0x67, 0x93, 0x1d, 0x89, 0x00, 0xce, 0x56, 0xb0,
    0x06, 0x7f, 0x7d, 0x70, 0xe1, 0xfc, 0x65, 0x3f, 0x3f, 0x34, 0xd1, 0x94, 0xc1, 0xfe, 0xd5, 0x00,
    0x18, 0xfb, 0x43, 0xdb, 0x93, 0x7b, 0x06, 0xe6, 0x73, 0xa8, 0x37, 0x31, 0x3d, 0x56, 0xb1, 0xc7,
    0x25, 0x15, 0x0a, 0x3f, 0xef, 0x86, 0xac, 0xbd, 0xdc, 0x41, 0xbb, 0x75, 0x9c, 0x28, 0x54, 0xea,
    0xe3, 0x2d, 0x35, 0x84, 0x1e, 0xfb, 0x5c, 0x18, 0xd8, 0x2b, 0xc9, 0x0a, 0x1c, 0xb5, 0xc1, 0xd5,
    0x5a, 0xdf, 0x24, 0x5b, 0x02, 0x91, 0x1f, 0x0b, 0x7c, 0xda, 0x88, 0xc4, 0x21, 0xff, 0x0e, 0xba,
    0xfe, 0x7c, 0x0d, 0x23, 0xbe, 0x31, 0x2d, 0x7b, 0xd5, 0x92, 0x1f, 0xfa, 0xea, 0x13, 0x47, 0xc1,
    0x57, 0x40, 0x6f, 0xef, 0x71, 0x8f, 0x68, 0x26, 0x43, 0xe4, 0xe5, 0xd3, 0x3c, 0x67, 0x03, 0xd6,
    0x1c, 0x0c, 0xf7, 0xac, 0x0b, 0xf4, 0x64, 0x5c, 0x11, 0xf5, 0xc1, 0x37, 0x4c, 0x38, 0x86, 0x42,
    0x74, 0x11, 0xc4, 0x49, 0x79, 0x67, 0x92, 0xe0, 0xbe, 0xf7, 0x5d, 0xec, 0x85, 0x8a, 0x21, 0x23,
    0xc3, 0x67, 0x53, 0xe0, 0x2a, 0x95, 0xa9, 0x6d, 0x7c, 0x45, 0x4b, 0x50, 0x4d, 0xe3, 0x85, 0xa6,
    0x42, 0xe0, 0xdf, 0xc3, 0xe6, 0x0a, 0xc3, 0xa7, 0xee, 0x49, 0x91, 0xd0, 0xd4, 0x8b, 0x01, 0x72,
    0xa9, 0x5f, 0x95, 0x36, 0xf0, 0x2b, 0xa1, 0x3c, 0xec, 0xcc, 0xb9, 0x2b, 0x72, 0x7d, 0xb5, 0xc2,
    0x7e, 0x5b, 0x2f, 0x5c, 0xec, 0x09, 0x60, 0x0b, 0x28, 0x6a, 0xf5, 0xcf, 0x14, 0xc4, 0x20, 0x24,
    0xc6, 0x1d, 0xdf, 0xe7, 0x1c, 0x2a, 0x8d, 0x74, 0x58, 0xf1, 0x85, 0x23, 0x4c, 0xb0, 0x0e, 0x01,
    0xd2, 0x82, 0xf1, 0x0f, 0x8f, 0xc6, 0x72, 0x1d, 0x2a, 0xed, 0x3f, 0x48, 0x33, 0xcc, 0xa2, 0xbd,
    0x8f, 0xa6, 0x28, 0x21, 0xdd, 0x55, 0x02, 0x03, 0x01, 0x00, 0x01, 0x02, 0x82, 0x01, 0x00, 0x43,
    0x14, 0x47, 0xb6, 0x25, 0x19, 0x08, 0x11, 0x2b, 0x1e, 0xe7, 0x6f, 0x99, 0xf3, 0x71, 0x1a, 0x52,
    0xb6, 0x63, 0x09, 0x60, 0x04, 0x6c, 0x2d, 0xe7, 0x0d, 0xe1, 0x88, 0xd8, 0x33, 0xf8, 0xb8, 0xb9,
    0x1e, 0x4d, 0x78, 0x5c, 0xae, 0xee, 0xaf, 0x4f, 0x0f, 0x74, 0x41, 0x4e, 0x2c, 0xda, 0x40, 0x64,
    0x1f, 0x7f, 0xe2, 0x4f, 0x14, 0xc6, 0x7a, 0x88, 0x95, 0x9b, 0xdb, 0x27, 0x76, 0x6d, 0xf9, 0xe7,
    0x10, 0xb6, 0x30, 0xa0, 0x3a, 0xdc, 0x68, 0x3b, 0x5d, 0x2c, 0x43, 0x08, 0x0e, 0x52, 0xbe, 0xe7,
    0x1e, 0x9e, 0xae, 0xb6, 0xde, 0x29, 0x7a, 0x5f, 0xea, 0x10, 0x72, 0x07, 0x0d, 0x18, 0x1c, 0x82,
    0x2b, 0xcc, 0xff, 0x08, 0x7d, 0x63, 0xc9, 0x40, 0xba, 0x8a, 0x45, 0xf6, 0x70, 0xfe, 0xb2, 0x9f,
    0xb4, 0x48, 0x4d, 0x1c, 0x95, 0xe6, 0xd2, 0x57, 0x9b, 0xa0, 0x2a, 0xae, 0x0a, 0x00, 0x90, 0x0c,
    0x3e, 0xbf, 0x49, 0x0e, 0x3d, 0x2c, 0xd7, 0xee, 0x8d, 0x0e, 0x20, 0xc5, 0x36, 0xe4, 0xdc, 0x5a,
    0x50, 0x97, 0x27, 0x28, 0x88, 0xcd, 0xdd, 0x7e, 0x91, 0xf2, 0x28, 0xb1, 0xc4, 0xd7, 0x47, 0x4c,
    0x55, 0xb8, 0xfc, 0xd6, 0x18, 0xc4, 0xa9, 0x57, 0xbb, 0xdd, 0xd5, 0xad, 0x74, 0x07, 0xcc, 0x31,
    0x2d, 0x8d, 0x98, 0xa5, 0xca, 0xf7, 0xe0, 0x8f, 0x4a, 0x0d, 0x6b, 0x45, 0xbb, 0x41, 0xc6, 0x52,
    0x65, 0x9d, 0x5a, 0x5b, 0xa0, 0x5b, 0x66, 0x37, 0x37, 0xa8, 0x69, 0x62, 0x81, 0x86, 0x5b, 0xa2,
    0x0f, 0xbd, 0xd7, 0xf8, 0x51, 0xe6, 0xc5, 0x6e, 0x8c, 0xbe, 0x0d, 0xdb, 0xbf, 0x24, 0xdc, 0x03,
    0xb2, 0xd2, 0xcb, 0x4c, 0x3d, 0x54, 0x0f, 0xb0, 0xaf, 0x52, 0xe0, 0x34, 0xa2, 0xd0, 0x66, 0x98,
    0xb1, 0x28, 0xe5, 0xf1, 0x01, 0xe3, 0xb5, 0x1a, 0x34, 0xf8, 0xd8, 0xb4, 0xf8, 0x61, 0x81, 0x02,
    0x81, 0x81, 0x00, 0xde, 0x39, 0x2e, 0x18, 0xd6, 0x82, 0xc8, 0x29, 0x26, 0x6c, 0xc3, 0x45, 0x4e,
    0x1d, 0x61, 0x66, 0x24, 0x2f, 0x32, 0xd9, 0xa1, 0xd1, 0x05, 0x77, 0x75, 0x3e, 0x90, 0x4e, 0xa7,
    0xd0, 0x8b, 0xff, 0x84, 0x1b, 0xe5, 0xba, 0xc8, 0x2a, 0x16, 0x4c, 0x59, 0x70, 0x00, 0x70, 0x47,
    0xb8, 0xc5, 0x17, 0xdb, 0x8f, 0x8f, 0x84, 0xe3, 0x7b, 0xd5, 0x98, 0x85, 0x61, 0xbd, 0xf5, 0x03,
    0xd4, 0xdc, 0x2b, 0xdb, 0x38, 0xf8, 0x85, 0x43, 0x4a, 0xe4, 0x2c, 0x35, 0x5f, 0x72, 0x5c, 0x9a,
    0x60, 0xf9, 0x1f, 0x07, 0x88, 0xe1, 0xf1, 0xa9, 0x72, 0x23, 0xb5, 0x24, 0xb5, 0x35, 0x7f, 0xdf,
    0x72, 0xe2, 0xf6, 0x96, 0xba, 0xb7, 0xd7, 0x8e, 0x32, 0xbf, 0x92, 0xba, 0x8e, 0x18, 0x64, 0xea,
    0xb1, 0x22, 0x9e, 0x91, 0x34, 0x61, 0x30, 0x74, 0x8a, 0x6e, 0x3c, 0x12, 0x4f, 0x91, 0x49, 0xd7,
    0x1c, 0x74, 0x35, 0x02, 0x81, 0x81, 0x00, 0xc9, 0x53, 0x87, 0xc0, 0xf9, 0xd3, 0x5f, 0x13, 0x7b,
    0x57, 0xd0, 0xd6, 0x5c, 0x39, 0x7c, 0x5e, 0x21, 0xcc, 0x25, 0x1e, 0x47, 0x00, 0x8e, 0xd6, 0x2a,
    0x54, 0x24, 0x09, 0xc8, 0xb6, 0xb6, 0xac, 0x7f, 0x89, 0x67, 0xb3, 0x86, 0x3c, 0xa6, 0x45, 0xfc,
    0xce, 0x49, 0x58, 0x2a, 0x9a, 0xa1, 0x73, 0x49, 0xdb, 0x6c, 0x4a, 0x95, 0xaf, 0xfd, 0xae, 0x0d,
    0xae, 0x61, 0x2e, 0x1a, 0xfa, 0xc9, 0x9e, 0xd3, 0x9a, 0x2d, 0x93, 0x4c, 0x88, 0x04, 0x40, 0xae,
    0xd8, 0x83, 0x2f, 0x98, 0x43, 0x16, 0x3a, 0x47, 0xf2, 0x7f, 0x39, 0x21, 0x99, 0xdc, 0x12, 0x02,
    0xf9, 0xa0, 0xf9, 0xbd, 0x08, 0x30, 0x80, 0x07, 0xcb, 0x1e, 0x4e, 0x7f, 0x58, 0x30, 0x93, 0x66,
    0xa7, 0xde, 0x25, 0xf7, 0xc3, 0xc9, 0xb8, 0x80, 0x67, 0x7c, 0x06, 0x8e, 0x1b, 0xe9, 0x36, 0xe8,
    0x12, 0x88, 0x81, 0x52, 0x52, 0xa8, 0xa1, 0x02, 0x81, 0x80, 0x57, 0xff, 0x8c, 0xa1, 0x89, 0x50,
    0x80, 0xb2, 0xca, 0xe4, 0x86, 0xef, 0x0a, 0xdf, 0xd7, 0x91, 0xfb, 0x02, 0x35, 0xc0, 0xb8, 0xb3,
    0x6c, 0xd6, 0xc1, 0x36, 0xe5, 0x2e, 0x40, 0x85, 0xf4, 0xea, 0x5a, 0x06, 0x32, 0x12, 0xa4, 0xf1,
    0x05, 0xa3, 0x76, 0x47, 0x43, 0xe5, 0x32, 0x81, 0x98, 0x8a, 0xba, 0x07, 0x3f, 0x6e, 0x00, 0x27,
    0x29, 0x8e, 0x1c, 0x43, 0x78, 0x55, 0x6e, 0x0e, 0xfc, 0xa0, 0xe1, 0x4e, 0xce, 0x1a, 0xf7, 0x6a,
    0xd0, 0xb0, 0x30, 0xf2, 0x7a, 0xf6, 0xf0, 0xab, 0x35, 0xfb, 0x73, 0xa0, 0x60, 0xd8, 0xb1, 0xa0,
    0xe1, 0x42, 0xfa, 0x26, 0x47, 0xe9, 0x3b, 0x32, 0xe3, 0x6d, 0x82, 0x82, 0xae, 0x0a, 0x4d, 0xe5,
    0x0a, 0xb7, 0xaf, 0xe8, 0x55, 0x00, 0xa1, 0x6f, 0x43, 0xa6, 0x47, 0x19, 0xd6, 0xe2, 0xb9, 0x43,
    0x98, 0x23, 0x71, 0x9c, 0xd0, 0x8b, 0xcd, 0x03, 0x17, 0x81, 0x02, 0x81, 0x81, 0x00, 0xba, 0x73,
    0xb0, 0xbb, 0x28, 0xe3, 0xf8, 0x1e, 0x9b, 0xd1, 0xc5, 0x68, 0x71, 0x3b, 0x10, 0x12, 0x41, 0xac,
    0xc6, 0x07, 0x97, 0x6c, 0x4d, 0xdc, 0xcc, 0x90, 0xe6, 0x5b, 0x65, 0x56, 0xca, 0x31, 0x51, 0x60,
    0x58, 0xf9, 0x2b, 0x6e, 0x09, 0xf3, 0xb1, 0x60, 0xff, 0x0e, 0x37, 0x4e, 0xc4, 0x0d, 0x78, 0xae,
    0x4d, 0x49, 0x79, 0xfd, 0xe6, 0xac, 0x06, 0xa1, 0xa4, 0x00, 0xc6, 0x1d, 0xd3, 0x12, 0x54, 0x18,
    0x6a, 0xf3, 0x0b, 0x22, 0xc1, 0x05, 0x82, 0xa8, 0xa4, 0x3e, 0x34, 0xfe, 0x94, 0x9c, 0x5f, 0x3b,
    0x97, 0x55, 0xba, 0xe7, 0xba, 0xa7, 0xb7, 0xb7, 0xa6, 0xbd, 0x03, 0xb3, 0x8c, 0xef, 0x55, 0xc8,
    0x68, 0x85, 0xfc, 0x6c, 0x19, 0x78, 0xb9, 0xce, 0xe7, 0xef, 0x33, 0xda, 0x50, 0x7c, 0x9d, 0xf6,
    0xb9, 0x27, 0x7c, 0xff, 0x1e, 0x6a, 0xaa, 0x5d, 0x57, 0xac, 0xa5, 0x28, 0x46, 0x61, 0x02, 0x81,
    0x81, 0x00, 0xc9, 0x31, 0x61, 0x7c, 0x77, 0x82, 0x9d, 0xfb, 0x12, 0x70, 0x50, 0x2b, 0xe9, 0x19,
    0x5c, 0x8f, 0x28, 0x30, 0x88, 0x5f, 0x57, 0xdb, 0xa8, 0x69, 0x53, 0x68, 0x11, 0xe6, 0x86, 0x42,
    0x36, 0xd0, 0xc4, 0x73, 0x6a, 0x00, 0x08, 0xa1, 0x45, 0xaf, 0x36, 0xb8, 0x35, 0x7a, 0x7c, 0x3d,
    0x13, 0x99, 0x66, 0xd0, 0x4c, 0x4e, 0x00, 0x93, 0x4e, 0xa1, 0xae, 0xde, 0x3b, 0xb6, 0xb8, 0xec,
    0x84, 0x1d, 0xc9, 0x5e, 0x3f, 0x57, 0x97, 0x51, 0xe2, 0xbf, 0xdf, 0xe2, 0x7a, 0xe7, 0x78, 0x98,
    0x3f, 0x95, 0x93, 0x56, 0x21, 0x07, 0x23, 0x28, 0x7b, 0x0a, 0xff, 0xcc, 0x9f, 0x72, 0x70, 0x44,
    0xd4, 0x8c, 0x37, 0x3f, 0x1b, 0xab, 0xde, 0x07, 0x24, 0xfa, 0x17, 0xa4, 0xfd, 0x4d, 0xa0, 0x90,
    0x2c, 0x7c, 0x9b, 0x9b, 0xf2, 0x7b, 0xa6, 0x1b, 0xe6, 0xad, 0x02, 0xdf, 0xdd, 0xda, 0x8f, 0x4e,
    0x68, 0x22,
];

/// WrappedKeyData as ASN.1 DER-encoded data corresponding to the `SecureKeyWrapper` schema
/// specified in IKeyMintDevice.aidl. Wrapped key parameters are -
///     Algorithm: AES
///     Key size: 256
///     Block mode: ECB
///     Padding mode: PKCS7
/// This sample wrapped_key is taken from KeyMint tests (see KeyMintTest.cpp).
pub static WRAPPED_KEY: &[u8] = &[
    0x30, 0x82, 0x01, 0x79, 0x02, 0x01, 0x00, 0x04, 0x82, 0x01, 0x00, 0x93, 0x4b, 0xf9, 0x4e, 0x2a,
    0xa2, 0x8a, 0x3f, 0x83, 0xc9, 0xf7, 0x92, 0x97, 0x25, 0x02, 0x62, 0xfb, 0xe3, 0x27, 0x6b, 0x5a,
    0x1c, 0x91, 0x15, 0x9b, 0xbf, 0xa3, 0xef, 0x89, 0x57, 0xaa, 0xc8, 0x4b, 0x59, 0xb3, 0x0b, 0x45,
    0x5a, 0x79, 0xc2, 0x97, 0x34, 0x80, 0x82, 0x3d, 0x8b, 0x38, 0x63, 0xc3, 0xde, 0xef, 0x4a, 0x8e,
    0x24, 0x35, 0x90, 0x26, 0x8d, 0x80, 0xe1, 0x87, 0x51, 0xa0, 0xe1, 0x30, 0xf6, 0x7c, 0xe6, 0xa1,
    0xac, 0xe9, 0xf7, 0x9b, 0x95, 0xe0, 0x97, 0x47, 0x4f, 0xeb, 0xc9, 0x81, 0x19, 0x5b, 0x1d, 0x13,
    0xa6, 0x90, 0x86, 0xc0, 0x86, 0x3f, 0x66, 0xa7, 0xb7, 0xfd, 0xb4, 0x87, 0x92, 0x22, 0x7b, 0x1a,
    0xc5, 0xe2, 0x48, 0x9f, 0xeb, 0xdf, 0x08, 0x7a, 0xb5, 0x48, 0x64, 0x83, 0x03, 0x3a, 0x6f, 0x00,
    0x1c, 0xa5, 0xd1, 0xec, 0x1e, 0x27, 0xf5, 0xc3, 0x0f, 0x4c, 0xec, 0x26, 0x42, 0x07, 0x4a, 0x39,
    0xae, 0x68, 0xae, 0xe5, 0x52, 0xe1, 0x96, 0x62, 0x7a, 0x8e, 0x3d, 0x86, 0x7e, 0x67, 0xa8, 0xc0,
    0x1b, 0x11, 0xe7, 0x5f, 0x13, 0xcc, 0xa0, 0xa9, 0x7a, 0xb6, 0x68, 0xb5, 0x0c, 0xda, 0x07, 0xa8,
    0xec, 0xb7, 0xcd, 0x8e, 0x3d, 0xd7, 0x00, 0x9c, 0x96, 0x36, 0x53, 0x4f, 0x6f, 0x23, 0x9c, 0xff,
    0xe1, 0xfc, 0x8d, 0xaa, 0x46, 0x6f, 0x78, 0xb6, 0x76, 0xc7, 0x11, 0x9e, 0xfb, 0x96, 0xbc, 0xe4,
    0xe6, 0x9c, 0xa2, 0xa2, 0x5d, 0x0b, 0x34, 0xed, 0x9c, 0x3f, 0xf9, 0x99, 0xb8, 0x01, 0x59, 0x7d,
    0x52, 0x20, 0xe3, 0x07, 0xea, 0xa5, 0xbe, 0xe5, 0x07, 0xfb, 0x94, 0xd1, 0xfa, 0x69, 0xf9, 0xe5,
    0x19, 0xb2, 0xde, 0x31, 0x5b, 0xac, 0x92, 0xc3, 0x6f, 0x2e, 0xa1, 0xfa, 0x1d, 0xf4, 0x47, 0x8c,
    0x0d, 0xde, 0xde, 0xae, 0x8c, 0x70, 0xe0, 0x23, 0x3c, 0xd0, 0x98, 0x04, 0x0c, 0xd7, 0x96, 0xb0,
    0x2c, 0x37, 0x0f, 0x1f, 0xa4, 0xcc, 0x01, 0x24, 0xf1, 0x30, 0x2e, 0x02, 0x01, 0x03, 0x30, 0x29,
    0xa1, 0x08, 0x31, 0x06, 0x02, 0x01, 0x00, 0x02, 0x01, 0x01, 0xa2, 0x03, 0x02, 0x01, 0x20, 0xa3,
    0x04, 0x02, 0x02, 0x01, 0x00, 0xa4, 0x05, 0x31, 0x03, 0x02, 0x01, 0x01, 0xa6, 0x05, 0x31, 0x03,
    0x02, 0x01, 0x40, 0xbf, 0x83, 0x77, 0x02, 0x05, 0x00, 0x04, 0x20, 0xcc, 0xd5, 0x40, 0x85, 0x5f,
    0x83, 0x3a, 0x5e, 0x14, 0x80, 0xbf, 0xd2, 0xd3, 0x6f, 0xaf, 0x3a, 0xee, 0xe1, 0x5d, 0xf5, 0xbe,
    0xab, 0xe2, 0x69, 0x1b, 0xc8, 0x2d, 0xde, 0x2a, 0x7a, 0xa9, 0x10, 0x04, 0x10, 0x64, 0xc9, 0xf6,
    0x89, 0xc6, 0x0f, 0xf6, 0x22, 0x3a, 0xb6, 0xe6, 0x99, 0x9e, 0x0e, 0xb6, 0xe5,
];

/// To map Keystore errors.
#[derive(thiserror::Error, Debug, Eq, PartialEq)]
pub enum Error {
    /// Keystore2 error code
    #[error("ResponseCode {0:?}")]
    Rc(ResponseCode),
    /// Keymint error code
    #[error("ErrorCode {0:?}")]
    Km(ErrorCode),
    /// Exception
    #[error("Binder exception {0:?}")]
    Binder(ExceptionCode),
    /// This is returned if the C implementation of extractSubjectFromCertificate failed.
    #[error("Failed to validate certificate chain.")]
    ValidateCertChainFailed,
    /// Error code to indicate error in ASN.1 DER-encoded data creation.
    #[error("Failed to create and encode ASN.1 data.")]
    DerEncodeFailed,
    /// Error code to indicate error while using keystore-engine API.
    #[error("Failed to perform crypto op using keystore-engine APIs.")]
    Keystore2EngineOpFailed,
    /// Error code to indicate error in attestation-id validation.
    #[error("Failed to validate attestation-id.")]
    ValidateAttestIdFailed,
    /// Error code to indicate error in getting value from attest record.
    #[error("Failed to get value from attest record.")]
    AttestRecordGetValueFailed,
}

/// Keystore2 error mapping.
pub fn map_ks_error<T>(r: BinderResult<T>) -> Result<T, Error> {
    r.map_err(|s| {
        match s.exception_code() {
            ExceptionCode::SERVICE_SPECIFIC => {
                match s.service_specific_error() {
                    se if se < 0 => {
                        // Negative service specific errors are KM error codes.
                        Error::Km(ErrorCode(se))
                    }
                    se => {
                        // Positive service specific errors are KS response codes.
                        Error::Rc(ResponseCode(se))
                    }
                }
            }
            // We create `Error::Binder` to preserve the exception code
            // for logging.
            e_code => Error::Binder(e_code),
        }
    })
}

/// Indicate whether the default device is KeyMint (rather than Keymaster).
pub fn has_default_keymint() -> bool {
    binder::is_declared("android.hardware.security.keymint.IKeyMintDevice/default")
        .expect("Could not check for declared keymint interface")
}

/// Verify that given key param is listed in given authorizations list.
pub fn check_key_param(authorizations: &[Authorization], key_param: &KeyParameter) -> bool {
    authorizations.iter().any(|auth| &auth.keyParameter == key_param)
}

/// Verify the given key authorizations with the expected authorizations.
pub fn check_key_authorizations(
    authorizations: &[Authorization],
    expected_params: &[KeyParameter],
    expected_key_origin: KeyOrigin,
) {
    // Make sure key authorizations contains only `ALLOWED_TAGS_IN_KEY_AUTHS`
    authorizations.iter().all(|auth| {
        // Ignore `INVALID` tag if the backend is Keymaster and not KeyMint.
        // Keymaster allows INVALID tag for unsupported key parameters.
        if !has_default_keymint() && auth.keyParameter.tag == Tag::INVALID {
            return true;
        }
        assert!(
            ALLOWED_TAGS_IN_KEY_AUTHS.contains(&auth.keyParameter.tag),
            "key authorization is not allowed: {:#?}",
            auth.keyParameter
        );
        true
    });

    //Check allowed-expected-key-parameters are present in given key authorizations list.
    expected_params.iter().all(|key_param| {
        // `INCLUDE_UNIQUE_ID` is not strictly expected to be in key authorizations but has been
        // put there by some implementations so cope with that.
        if key_param.tag == Tag::INCLUDE_UNIQUE_ID
            && !authorizations.iter().any(|auth| auth.keyParameter.tag == key_param.tag)
        {
            return true;
        }

        // Ignore below parameters if the backend is Keymaster and not KeyMint.
        // Keymaster does not support these parameters. These key parameters are introduced in
        // KeyMint1.0.
        if !has_default_keymint() {
            if matches!(key_param.tag, Tag::RSA_OAEP_MGF_DIGEST | Tag::USAGE_COUNT_LIMIT) {
                return true;
            }
            if key_param.tag == Tag::PURPOSE
                && key_param.value == KeyParameterValue::KeyPurpose(KeyPurpose::ATTEST_KEY)
            {
                return true;
            }
        }

        if ALLOWED_TAGS_IN_KEY_AUTHS.contains(&key_param.tag) {
            assert!(
                check_key_param(authorizations, key_param),
                "Key parameter not found: {:#?}",
                key_param
            );
        }
        true
    });

    check_common_auths(authorizations, expected_key_origin);
}

/// Verify common key authorizations.
fn check_common_auths(authorizations: &[Authorization], expected_key_origin: KeyOrigin) {
    assert!(check_key_param(
        authorizations,
        &KeyParameter {
            tag: Tag::OS_VERSION,
            value: KeyParameterValue::Integer(get_os_version().try_into().unwrap())
        }
    ));
    assert!(check_key_param(
        authorizations,
        &KeyParameter {
            tag: Tag::OS_PATCHLEVEL,
            value: KeyParameterValue::Integer(get_os_patchlevel().try_into().unwrap())
        }
    ));

    // Access denied for finding vendor-patch-level ("ro.vendor.build.security_patch") property
    // in a test running with `untrusted_app` context. Keeping this check to verify
    // vendor-patch-level in tests running with `su` context.
    if getuid().is_root() {
        assert!(check_key_param(
            authorizations,
            &KeyParameter {
                tag: Tag::VENDOR_PATCHLEVEL,
                value: KeyParameterValue::Integer(get_vendor_patchlevel().try_into().unwrap())
            }
        ));
    }
    assert!(check_key_param(
        authorizations,
        &KeyParameter { tag: Tag::ORIGIN, value: KeyParameterValue::Origin(expected_key_origin) }
    ));
    assert!(check_key_param(
        authorizations,
        &KeyParameter {
            tag: Tag::USER_ID,
            value: KeyParameterValue::Integer(
                rustutils::users::multiuser_get_user_id(ThreadState::get_calling_uid())
                    .try_into()
                    .unwrap()
            )
        }
    ));

    if has_default_keymint() {
        assert!(authorizations
            .iter()
            .map(|auth| &auth.keyParameter)
            .any(|key_param| key_param.tag == Tag::CREATION_DATETIME));
    }
}

/// Get the key `Authorization` for the given auth `Tag`.
pub fn get_key_auth(authorizations: &[Authorization], tag: Tag) -> Option<&Authorization> {
    let auths: Vec<&Authorization> =
        authorizations.iter().filter(|auth| auth.keyParameter.tag == tag).collect();

    if !auths.is_empty() {
        Some(auths[0])
    } else {
        None
    }
}

/// Generate EC Key using given security level and domain with below key parameters and
/// optionally allow the generated key to be attested with factory provisioned attest key using
/// given challenge and application id -
///     Purposes: SIGN and VERIFY
///     Digest: SHA_2_256
///     Curve: P_256
pub fn generate_ec_p256_signing_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
    att_challenge: Option<&[u8]>,
) -> binder::Result<KeyMetadata> {
    let mut key_attest = false;
    let mut gen_params = AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256);

    if let Some(challenge) = att_challenge {
        key_attest = true;
        gen_params = gen_params.clone().attestation_challenge(challenge.to_vec());
    }

    match sec_level.generateKey(
        &KeyDescriptor { domain, nspace, alias, blob: None },
        None,
        &gen_params,
        0,
        b"entropy",
    ) {
        Ok(key_metadata) => {
            assert!(key_metadata.certificate.is_some());
            if key_attest {
                assert!(key_metadata.certificateChain.is_some());
            }
            if domain == Domain::BLOB {
                assert!(key_metadata.key.blob.is_some());
            }

            check_key_authorizations(
                &key_metadata.authorizations,
                &gen_params,
                KeyOrigin::GENERATED,
            );
            Ok(key_metadata)
        }
        Err(e) => Err(e),
    }
}

/// Generate EC signing key.
pub fn generate_ec_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
    ec_curve: EcCurve,
    digest: Digest,
) -> binder::Result<KeyMetadata> {
    let gen_params = AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(digest)
        .ec_curve(ec_curve);

    let key_metadata = sec_level.generateKey(
        &KeyDescriptor { domain, nspace, alias, blob: None },
        None,
        &gen_params,
        0,
        b"entropy",
    )?;

    // Must have a public key.
    assert!(key_metadata.certificate.is_some());

    // Should not have an attestation record.
    assert!(key_metadata.certificateChain.is_none());

    if domain == Domain::BLOB {
        assert!(key_metadata.key.blob.is_some());
    } else {
        assert!(key_metadata.key.blob.is_none());
    }
    check_key_authorizations(&key_metadata.authorizations, &gen_params, KeyOrigin::GENERATED);
    Ok(key_metadata)
}

/// Generate a RSA key with the given key parameters, alias, domain and namespace.
pub fn generate_rsa_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
    key_params: &KeyParams,
    attest_key: Option<&KeyDescriptor>,
) -> binder::Result<KeyMetadata> {
    let mut gen_params = AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::RSA)
        .rsa_public_exponent(65537)
        .key_size(key_params.key_size);

    for purpose in &key_params.purpose {
        gen_params = gen_params.purpose(*purpose);
    }
    if let Some(value) = key_params.digest {
        gen_params = gen_params.digest(value)
    }
    if let Some(value) = key_params.padding {
        gen_params = gen_params.padding_mode(value);
    }
    if let Some(value) = key_params.mgf_digest {
        gen_params = gen_params.mgf_digest(value);
    }
    if let Some(value) = key_params.block_mode {
        gen_params = gen_params.block_mode(value)
    }
    if let Some(value) = &key_params.att_challenge {
        gen_params = gen_params.attestation_challenge(value.to_vec())
    }

    let key_metadata = sec_level.generateKey(
        &KeyDescriptor { domain, nspace, alias, blob: None },
        attest_key,
        &gen_params,
        0,
        b"entropy",
    )?;

    // Must have a public key.
    assert!(key_metadata.certificate.is_some());

    if attest_key.is_none() && key_params.att_challenge.is_some() {
        // Should have an attestation record.
        assert!(key_metadata.certificateChain.is_some());
    } else {
        // Should not have an attestation record.
        assert!(key_metadata.certificateChain.is_none());
    }

    assert!(
        (domain == Domain::BLOB && key_metadata.key.blob.is_some())
            || key_metadata.key.blob.is_none()
    );

    check_key_authorizations(&key_metadata.authorizations, &gen_params, KeyOrigin::GENERATED);
    // If `RSA_OAEP_MGF_DIGEST` tag is not mentioned explicitly while generating/importing a key,
    // then make sure `RSA_OAEP_MGF_DIGEST` tag with default value (SHA1) must not be included in
    // key authorization list.
    if key_params.mgf_digest.is_none() {
        assert!(!check_key_param(
            &key_metadata.authorizations,
            &KeyParameter {
                tag: Tag::RSA_OAEP_MGF_DIGEST,
                value: KeyParameterValue::Digest(Digest::SHA1)
            }
        ));
    }
    Ok(key_metadata)
}

/// Generate AES/3DES key.
pub fn generate_sym_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    algorithm: Algorithm,
    size: i32,
    alias: &str,
    padding_mode: &PaddingMode,
    block_mode: &BlockMode,
    min_mac_len: Option<i32>,
) -> binder::Result<KeyMetadata> {
    let mut gen_params = AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(algorithm)
        .purpose(KeyPurpose::ENCRYPT)
        .purpose(KeyPurpose::DECRYPT)
        .key_size(size)
        .padding_mode(*padding_mode)
        .block_mode(*block_mode);

    if let Some(val) = min_mac_len {
        gen_params = gen_params.min_mac_length(val);
    }

    let key_metadata = sec_level.generateKey(
        &KeyDescriptor {
            domain: Domain::APP,
            nspace: -1,
            alias: Some(alias.to_string()),
            blob: None,
        },
        None,
        &gen_params,
        0,
        b"entropy",
    )?;

    // Should not have public certificate.
    assert!(key_metadata.certificate.is_none());

    // Should not have an attestation record.
    assert!(key_metadata.certificateChain.is_none());
    check_key_authorizations(&key_metadata.authorizations, &gen_params, KeyOrigin::GENERATED);
    Ok(key_metadata)
}

/// Generate HMAC key.
pub fn generate_hmac_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    alias: &str,
    key_size: i32,
    min_mac_len: i32,
    digest: Digest,
) -> binder::Result<KeyMetadata> {
    let gen_params = AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::HMAC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .key_size(key_size)
        .min_mac_length(min_mac_len)
        .digest(digest);

    let key_metadata = sec_level.generateKey(
        &KeyDescriptor {
            domain: Domain::APP,
            nspace: -1,
            alias: Some(alias.to_string()),
            blob: None,
        },
        None,
        &gen_params,
        0,
        b"entropy",
    )?;

    // Should not have public certificate.
    assert!(key_metadata.certificate.is_none());

    // Should not have an attestation record.
    assert!(key_metadata.certificateChain.is_none());

    check_key_authorizations(&key_metadata.authorizations, &gen_params, KeyOrigin::GENERATED);
    Ok(key_metadata)
}

/// Generate RSA or EC attestation keys using below parameters -
///     Purpose: ATTEST_KEY
///     Digest: Digest::SHA_2_256
///     Padding: PaddingMode::RSA_PKCS1_1_5_SIGN
///     RSA-Key-Size: 2048
///     EC-Curve: EcCurve::P_256
pub fn generate_attestation_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    algorithm: Algorithm,
    att_challenge: &[u8],
) -> binder::Result<KeyMetadata> {
    assert!(algorithm == Algorithm::RSA || algorithm == Algorithm::EC);

    if algorithm == Algorithm::RSA {
        let alias = "ks_rsa_attest_test_key";
        let metadata = generate_rsa_key(
            sec_level,
            Domain::APP,
            -1,
            Some(alias.to_string()),
            &KeyParams {
                key_size: 2048,
                purpose: vec![KeyPurpose::ATTEST_KEY],
                padding: Some(PaddingMode::RSA_PKCS1_1_5_SIGN),
                digest: Some(Digest::SHA_2_256),
                mgf_digest: None,
                block_mode: None,
                att_challenge: Some(att_challenge.to_vec()),
            },
            None,
        )
        .unwrap();
        Ok(metadata)
    } else {
        let metadata = generate_ec_attestation_key(
            sec_level,
            att_challenge,
            Digest::SHA_2_256,
            EcCurve::P_256,
        )
        .unwrap();

        Ok(metadata)
    }
}

/// Generate EC attestation key with the given
///    curve, attestation-challenge and attestation-app-id.
pub fn generate_ec_attestation_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    att_challenge: &[u8],
    digest: Digest,
    ec_curve: EcCurve,
) -> binder::Result<KeyMetadata> {
    let alias = "ks_attest_ec_test_key";
    let gen_params = AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::ATTEST_KEY)
        .ec_curve(ec_curve)
        .digest(digest)
        .attestation_challenge(att_challenge.to_vec());

    let attestation_key_metadata = sec_level.generateKey(
        &KeyDescriptor {
            domain: Domain::APP,
            nspace: -1,
            alias: Some(alias.to_string()),
            blob: None,
        },
        None,
        &gen_params,
        0,
        b"entropy",
    )?;

    // Should have public certificate.
    assert!(attestation_key_metadata.certificate.is_some());
    // Should have an attestation record.
    assert!(attestation_key_metadata.certificateChain.is_some());

    check_key_authorizations(
        &attestation_key_metadata.authorizations,
        &gen_params,
        KeyOrigin::GENERATED,
    );
    Ok(attestation_key_metadata)
}

/// Generate EC-P-256 key and attest it with given attestation key.
pub fn generate_ec_256_attested_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    alias: Option<String>,
    att_challenge: &[u8],
    attest_key: &KeyDescriptor,
) -> binder::Result<KeyMetadata> {
    let ec_gen_params = AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(att_challenge.to_vec());

    let ec_key_metadata = sec_level
        .generateKey(
            &KeyDescriptor { domain: Domain::APP, nspace: -1, alias, blob: None },
            Some(attest_key),
            &ec_gen_params,
            0,
            b"entropy",
        )
        .unwrap();

    // Should have public certificate.
    assert!(ec_key_metadata.certificate.is_some());
    // Shouldn't have an attestation record.
    assert!(ec_key_metadata.certificateChain.is_none());

    check_key_authorizations(&ec_key_metadata.authorizations, &ec_gen_params, KeyOrigin::GENERATED);
    Ok(ec_key_metadata)
}

/// Imports above defined RSA key - `RSA_2048_KEY` and validates imported key parameters.
pub fn import_rsa_2048_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
    import_params: AuthSetBuilder,
) -> binder::Result<KeyMetadata> {
    let key_metadata = sec_level
        .importKey(
            &KeyDescriptor { domain, nspace, alias, blob: None },
            None,
            &import_params,
            0,
            RSA_2048_KEY,
        )
        .unwrap();

    assert!(key_metadata.certificate.is_some());
    assert!(key_metadata.certificateChain.is_none());

    check_key_authorizations(&key_metadata.authorizations, &import_params, KeyOrigin::IMPORTED);

    // Check below auths explicitly, they might not be addd in import parameters.
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::ALGORITHM, value: KeyParameterValue::Algorithm(Algorithm::RSA) }
    ));

    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::KEY_SIZE, value: KeyParameterValue::Integer(2048) }
    ));

    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::DIGEST, value: KeyParameterValue::Digest(Digest::SHA_2_256) }
    ));

    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter {
            tag: Tag::RSA_PUBLIC_EXPONENT,
            value: KeyParameterValue::LongInteger(65537)
        }
    ));

    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter {
            tag: Tag::PADDING,
            value: KeyParameterValue::PaddingMode(PaddingMode::RSA_PSS)
        }
    ));

    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::ORIGIN, value: KeyParameterValue::Origin(KeyOrigin::IMPORTED) }
    ));

    Ok(key_metadata)
}

/// Imports above defined EC key - `EC_P_256_KEY` and validates imported key parameters.
pub fn import_ec_p_256_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
    import_params: AuthSetBuilder,
) -> binder::Result<KeyMetadata> {
    let key_metadata = sec_level
        .importKey(
            &KeyDescriptor { domain, nspace, alias, blob: None },
            None,
            &import_params,
            0,
            EC_P_256_KEY,
        )
        .unwrap();

    assert!(key_metadata.certificate.is_some());
    assert!(key_metadata.certificateChain.is_none());

    check_key_authorizations(&key_metadata.authorizations, &import_params, KeyOrigin::IMPORTED);

    // Check below auths explicitly, they might not be addd in import parameters.
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::ALGORITHM, value: KeyParameterValue::Algorithm(Algorithm::EC) }
    ));

    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::EC_CURVE, value: KeyParameterValue::EcCurve(EcCurve::P_256) }
    ));

    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::DIGEST, value: KeyParameterValue::Digest(Digest::SHA_2_256) }
    ));
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::ORIGIN, value: KeyParameterValue::Origin(KeyOrigin::IMPORTED) }
    ));

    Ok(key_metadata)
}

/// Import sample AES key and validate its key parameters.
pub fn import_aes_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
) -> binder::Result<KeyMetadata> {
    static AES_KEY: &[u8] = &[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
    let key_size = AES_KEY.len() * 8;

    let import_params = AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::AES)
        .block_mode(BlockMode::ECB)
        .key_size(key_size.try_into().unwrap())
        .purpose(KeyPurpose::ENCRYPT)
        .purpose(KeyPurpose::DECRYPT)
        .padding_mode(PaddingMode::PKCS7);

    let key_metadata = sec_level.importKey(
        &KeyDescriptor { domain, nspace, alias, blob: None },
        None,
        &import_params,
        0,
        AES_KEY,
    )?;

    check_key_authorizations(&key_metadata.authorizations, &import_params, KeyOrigin::IMPORTED);

    // Check below auths explicitly, they might not be addd in import parameters.
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::ALGORITHM, value: KeyParameterValue::Algorithm(Algorithm::AES) }
    ));
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::KEY_SIZE, value: KeyParameterValue::Integer(128) }
    ));
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter {
            tag: Tag::PADDING,
            value: KeyParameterValue::PaddingMode(PaddingMode::PKCS7)
        }
    ));
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::BLOCK_MODE, value: KeyParameterValue::BlockMode(BlockMode::ECB) }
    ));
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::ORIGIN, value: KeyParameterValue::Origin(KeyOrigin::IMPORTED) }
    ));

    Ok(key_metadata)
}

/// Import sample 3DES key and validate its key parameters.
pub fn import_3des_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
) -> binder::Result<KeyMetadata> {
    static TRIPLE_DES_KEY: &[u8] = &[
        0xa4, 0x9d, 0x75, 0x64, 0x19, 0x9e, 0x97, 0xcb, 0x52, 0x9d, 0x2c, 0x9d, 0x97, 0xbf, 0x2f,
        0x98, 0xd3, 0x5e, 0xdf, 0x57, 0xba, 0x1f, 0x73, 0x58,
    ];

    let import_params = AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::TRIPLE_DES)
        .block_mode(BlockMode::ECB)
        .key_size(168)
        .purpose(KeyPurpose::ENCRYPT)
        .purpose(KeyPurpose::DECRYPT)
        .padding_mode(PaddingMode::PKCS7);

    let key_metadata = sec_level.importKey(
        &KeyDescriptor { domain, nspace, alias, blob: None },
        None,
        &import_params,
        0,
        TRIPLE_DES_KEY,
    )?;

    check_key_authorizations(&key_metadata.authorizations, &import_params, KeyOrigin::IMPORTED);

    // Check below auths explicitly, they might not be addd in import parameters.
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter {
            tag: Tag::ALGORITHM,
            value: KeyParameterValue::Algorithm(Algorithm::TRIPLE_DES)
        }
    ));
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::KEY_SIZE, value: KeyParameterValue::Integer(168) }
    ));
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter {
            tag: Tag::PADDING,
            value: KeyParameterValue::PaddingMode(PaddingMode::PKCS7)
        }
    ));
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::BLOCK_MODE, value: KeyParameterValue::BlockMode(BlockMode::ECB) }
    ));
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::ORIGIN, value: KeyParameterValue::Origin(KeyOrigin::IMPORTED) }
    ));

    Ok(key_metadata)
}

/// Import sample HMAC key and validate its key parameters.
pub fn import_hmac_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
) -> binder::Result<KeyMetadata> {
    static HMAC_KEY: &[u8] = &[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
    let key_size = HMAC_KEY.len() * 8;

    let import_params = AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::HMAC)
        .key_size(key_size.try_into().unwrap())
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .min_mac_length(256);

    let key_metadata = sec_level.importKey(
        &KeyDescriptor { domain, nspace, alias, blob: None },
        None,
        &import_params,
        0,
        HMAC_KEY,
    )?;

    check_key_authorizations(&key_metadata.authorizations, &import_params, KeyOrigin::IMPORTED);

    // Check below auths explicitly, they might not be addd in import parameters.
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::ALGORITHM, value: KeyParameterValue::Algorithm(Algorithm::HMAC) }
    ));
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::KEY_SIZE, value: KeyParameterValue::Integer(128) }
    ));
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::DIGEST, value: KeyParameterValue::Digest(Digest::SHA_2_256) }
    ));
    assert!(check_key_param(
        &key_metadata.authorizations,
        &KeyParameter { tag: Tag::ORIGIN, value: KeyParameterValue::Origin(KeyOrigin::IMPORTED) }
    ));

    Ok(key_metadata)
}

/// Imports RSA encryption key with WRAP_KEY purpose.
pub fn import_wrapping_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    wrapping_key_data: &[u8],
    wrapping_key_alias: Option<String>,
) -> binder::Result<KeyMetadata> {
    let wrapping_key_params = AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::RSA)
        .digest(Digest::SHA_2_256)
        .purpose(KeyPurpose::ENCRYPT)
        .purpose(KeyPurpose::DECRYPT)
        .purpose(KeyPurpose::WRAP_KEY)
        .padding_mode(PaddingMode::RSA_OAEP)
        .key_size(2048)
        .rsa_public_exponent(65537)
        .cert_not_before(0)
        .cert_not_after(253402300799000);

    sec_level.importKey(
        &KeyDescriptor { domain: Domain::APP, nspace: -1, alias: wrapping_key_alias, blob: None },
        None,
        &wrapping_key_params,
        0,
        wrapping_key_data,
    )
}

/// Import wrapped key using given wrapping key.
pub fn import_wrapped_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    alias: Option<String>,
    wrapping_key_metadata: &KeyMetadata,
    wrapped_key: Option<Vec<u8>>,
) -> binder::Result<KeyMetadata> {
    let unwrap_params =
        AuthSetBuilder::new().digest(Digest::SHA_2_256).padding_mode(PaddingMode::RSA_OAEP);

    let authenticator_spec: &[AuthenticatorSpec] = &[AuthenticatorSpec {
        authenticatorType: HardwareAuthenticatorType::NONE,
        authenticatorId: 0,
    }];

    let key_metadata = sec_level.importWrappedKey(
        &KeyDescriptor { domain: Domain::APP, nspace: -1, alias, blob: wrapped_key },
        &wrapping_key_metadata.key,
        None,
        &unwrap_params,
        authenticator_spec,
    )?;

    Ok(key_metadata)
}

/// Import wrapping key and then import wrapped key using wrapping key.
pub fn import_wrapping_key_and_wrapped_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
    wrapping_key_alias: Option<String>,
    wrapping_key_params: AuthSetBuilder,
) -> binder::Result<KeyMetadata> {
    let wrapping_key_metadata = sec_level.importKey(
        &KeyDescriptor { domain, nspace, alias: wrapping_key_alias, blob: None },
        None,
        &wrapping_key_params,
        0,
        WRAPPING_KEY,
    )?;

    import_wrapped_key(sec_level, alias, &wrapping_key_metadata, Some(WRAPPED_KEY.to_vec()))
}

/// Import given key material as AES-256-GCM-NONE transport key.
pub fn import_transport_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    transport_key_alias: Option<String>,
    transport_key: &[u8],
) -> binder::Result<KeyMetadata> {
    let transport_key_params = AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::AES)
        .block_mode(BlockMode::GCM)
        .padding_mode(PaddingMode::NONE)
        .key_size(256)
        .caller_nonce()
        .min_mac_length(128)
        .purpose(KeyPurpose::ENCRYPT)
        .purpose(KeyPurpose::DECRYPT);

    sec_level.importKey(
        &KeyDescriptor { domain: Domain::APP, nspace: -1, alias: transport_key_alias, blob: None },
        None,
        &transport_key_params,
        0,
        transport_key,
    )
}

/// Generate EC key with purpose AGREE_KEY.
pub fn generate_ec_agree_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    ec_curve: EcCurve,
    digest: Digest,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
) -> binder::Result<KeyMetadata> {
    let gen_params = AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::AGREE_KEY)
        .digest(digest)
        .ec_curve(ec_curve);

    match sec_level.generateKey(
        &KeyDescriptor { domain, nspace, alias, blob: None },
        None,
        &gen_params,
        0,
        b"entropy",
    ) {
        Ok(key_metadata) => {
            assert!(key_metadata.certificate.is_some());
            if domain == Domain::BLOB {
                assert!(key_metadata.key.blob.is_some());
            }

            check_key_authorizations(
                &key_metadata.authorizations,
                &gen_params,
                KeyOrigin::GENERATED,
            );
            Ok(key_metadata)
        }
        Err(e) => Err(e),
    }
}

/// Helper method to import AES keys `total_count` of times.
pub fn import_aes_keys(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    alias_prefix: String,
    total_count: Range<i32>,
) -> binder::Result<HashSet<String>> {
    let mut imported_key_aliases = HashSet::new();

    // Import Total number of keys with given alias prefix.
    for count in total_count {
        let mut alias = String::new();
        write!(alias, "{}_{}", alias_prefix, count).unwrap();
        imported_key_aliases.insert(alias.clone());

        import_aes_key(sec_level, Domain::APP, -1, Some(alias))?;
    }

    Ok(imported_key_aliases)
}

/// Generate attested EC-P_256 key with device id attestation.
pub fn generate_key_with_attest_id(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    algorithm: Algorithm,
    alias: Option<String>,
    att_challenge: &[u8],
    attest_key: &KeyDescriptor,
    attest_id: Tag,
    value: Vec<u8>,
) -> binder::Result<KeyMetadata> {
    assert!(algorithm == Algorithm::RSA || algorithm == Algorithm::EC);

    let mut ec_gen_params;
    if algorithm == Algorithm::EC {
        ec_gen_params = AuthSetBuilder::new()
            .no_auth_required()
            .algorithm(Algorithm::EC)
            .purpose(KeyPurpose::SIGN)
            .purpose(KeyPurpose::VERIFY)
            .digest(Digest::SHA_2_256)
            .ec_curve(EcCurve::P_256)
            .attestation_challenge(att_challenge.to_vec());
    } else {
        ec_gen_params = AuthSetBuilder::new()
            .no_auth_required()
            .algorithm(Algorithm::RSA)
            .rsa_public_exponent(65537)
            .key_size(2048)
            .purpose(KeyPurpose::SIGN)
            .purpose(KeyPurpose::VERIFY)
            .digest(Digest::SHA_2_256)
            .padding_mode(PaddingMode::RSA_PKCS1_1_5_SIGN)
            .attestation_challenge(att_challenge.to_vec());
    }

    match attest_id {
        Tag::ATTESTATION_ID_BRAND => {
            ec_gen_params = ec_gen_params.attestation_device_brand(value);
        }
        Tag::ATTESTATION_ID_DEVICE => {
            ec_gen_params = ec_gen_params.attestation_device_name(value);
        }
        Tag::ATTESTATION_ID_PRODUCT => {
            ec_gen_params = ec_gen_params.attestation_device_product_name(value);
        }
        Tag::ATTESTATION_ID_SERIAL => {
            ec_gen_params = ec_gen_params.attestation_device_serial(value);
        }
        Tag::ATTESTATION_ID_MANUFACTURER => {
            ec_gen_params = ec_gen_params.attestation_device_manufacturer(value);
        }
        Tag::ATTESTATION_ID_MODEL => {
            ec_gen_params = ec_gen_params.attestation_device_model(value);
        }
        Tag::ATTESTATION_ID_IMEI => {
            ec_gen_params = ec_gen_params.attestation_device_imei(value);
        }
        Tag::ATTESTATION_ID_SECOND_IMEI => {
            ec_gen_params = ec_gen_params.attestation_device_second_imei(value);
        }
        _ => {
            panic!("Unknown attestation id");
        }
    }

    sec_level.generateKey(
        &KeyDescriptor { domain: Domain::APP, nspace: -1, alias, blob: None },
        Some(attest_key),
        &ec_gen_params,
        0,
        b"entropy",
    )
}

/// Generate Key and validate key characteristics.
pub fn generate_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    gen_params: &AuthSetBuilder,
    alias: &str,
) -> binder::Result<KeyMetadata> {
    let key_metadata = sec_level.generateKey(
        &KeyDescriptor {
            domain: Domain::APP,
            nspace: -1,
            alias: Some(alias.to_string()),
            blob: None,
        },
        None,
        gen_params,
        0,
        b"entropy",
    )?;

    if gen_params.iter().any(|kp| {
        matches!(
            kp.value,
            KeyParameterValue::Algorithm(Algorithm::RSA)
                | KeyParameterValue::Algorithm(Algorithm::EC)
        )
    }) {
        assert!(key_metadata.certificate.is_some());
        if gen_params.iter().any(|kp| kp.tag == Tag::ATTESTATION_CHALLENGE) {
            assert!(key_metadata.certificateChain.is_some());
            let mut cert_chain: Vec<u8> = Vec::new();
            cert_chain.extend(key_metadata.certificate.as_ref().unwrap());
            cert_chain.extend(key_metadata.certificateChain.as_ref().unwrap());
            let strict_issuer_check =
                !(gen_params.iter().any(|kp| kp.tag == Tag::DEVICE_UNIQUE_ATTESTATION));
            validate_certchain_with_strict_issuer_check(&cert_chain, strict_issuer_check)
                .expect("Error while validating cert chain");
        }

        if let Some(challenge_param) =
            gen_params.iter().find(|kp| kp.tag == Tag::ATTESTATION_CHALLENGE)
        {
            if let KeyParameterValue::Blob(val) = &challenge_param.value {
                let att_challenge = get_value_from_attest_record(
                    key_metadata.certificate.as_ref().unwrap(),
                    challenge_param.tag,
                    key_metadata.keySecurityLevel,
                )
                .expect("Attestation challenge verification failed.");
                assert_eq!(&att_challenge, val);
            }

            let att_app_id = get_value_from_attest_record(
                key_metadata.certificate.as_ref().unwrap(),
                Tag::ATTESTATION_APPLICATION_ID,
                SecurityLevel::KEYSTORE,
            )
            .expect("Attestation application id verification failed.");
            assert!(!att_app_id.is_empty());
        }
    }
    check_key_authorizations(&key_metadata.authorizations, gen_params, KeyOrigin::GENERATED);

    Ok(key_metadata)
}

/// Generate a key using given authorizations and create an operation using the generated key.
pub fn create_key_and_operation(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    gen_params: &AuthSetBuilder,
    op_params: &AuthSetBuilder,
    alias: &str,
) -> binder::Result<CreateOperationResponse> {
    let key_metadata = generate_key(sec_level, gen_params, alias)?;

    sec_level.createOperation(&key_metadata.key, op_params, false)
}
