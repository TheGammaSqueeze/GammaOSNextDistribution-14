// Copyright 2023, The Android Open Source Project
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

use std::time::SystemTime;

use openssl::bn::{BigNum, MsbOption};
use openssl::x509::X509NameBuilder;

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Algorithm::Algorithm, BlockMode::BlockMode, Digest::Digest, EcCurve::EcCurve,
    ErrorCode::ErrorCode, KeyPurpose::KeyPurpose, PaddingMode::PaddingMode,
    SecurityLevel::SecurityLevel, Tag::Tag,
};

use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, IKeystoreSecurityLevel::IKeystoreSecurityLevel, KeyDescriptor::KeyDescriptor,
    KeyMetadata::KeyMetadata, ResponseCode::ResponseCode,
};

use aconfig_android_hardware_biometrics_rust;
use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    HardwareAuthToken::HardwareAuthToken,
    HardwareAuthenticatorType::HardwareAuthenticatorType
};
use android_hardware_security_secureclock::aidl::android::hardware::security::secureclock::Timestamp::Timestamp;

use keystore2_test_utils::{
    authorizations, get_keystore_auth_service, get_keystore_service, key_generations,
    key_generations::Error,
};

use crate::keystore2_client_test_utils::{
    app_attest_key_feature_exists, delete_app_key, perform_sample_asym_sign_verify_op,
    perform_sample_hmac_sign_verify_op, perform_sample_sym_key_decrypt_op,
    perform_sample_sym_key_encrypt_op, verify_certificate_serial_num,
    verify_certificate_subject_name, SAMPLE_PLAIN_TEXT,
};

use crate::{skip_test_if_no_app_attest_key_feature, skip_tests_if_keymaster_impl_present};

use keystore2_test_utils::ffi_test_utils::get_value_from_attest_record;

fn gen_key_including_unique_id(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    alias: &str,
) -> Vec<u8> {
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .include_unique_id();

    let key_metadata = key_generations::generate_key(sec_level, &gen_params, alias).unwrap();

    let unique_id = get_value_from_attest_record(
        key_metadata.certificate.as_ref().unwrap(),
        Tag::UNIQUE_ID,
        key_metadata.keySecurityLevel,
    )
    .expect("Unique id not found.");
    assert!(!unique_id.is_empty());
    unique_id
}

fn generate_key_and_perform_sign_verify_op_max_times(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    gen_params: &authorizations::AuthSetBuilder,
    alias: &str,
    max_usage_count: i32,
) -> binder::Result<KeyMetadata> {
    let key_metadata = key_generations::generate_key(sec_level, gen_params, alias)?;

    // Use above generated key `max_usage_count` times.
    for _ in 0..max_usage_count {
        perform_sample_asym_sign_verify_op(sec_level, &key_metadata, None, Some(Digest::SHA_2_256));
    }

    Ok(key_metadata)
}

/// Generate a key with `USAGE_COUNT_LIMIT` and verify the key characteristics. Test should be able
/// to use the key successfully `max_usage_count` times. After exceeding key usage `max_usage_count`
/// times subsequent attempts to use the key in test should fail with response code `KEY_NOT_FOUND`.
/// Test should also verify that the attest record includes `USAGE_COUNT_LIMIT` for attested keys.
fn generate_key_and_perform_op_with_max_usage_limit(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    gen_params: &authorizations::AuthSetBuilder,
    alias: &str,
    max_usage_count: i32,
    check_attestation: bool,
) {
    // Generate a key and use the key for `max_usage_count` times.
    let key_metadata = generate_key_and_perform_sign_verify_op_max_times(
        sec_level,
        gen_params,
        alias,
        max_usage_count,
    )
    .unwrap();

    let auth = key_generations::get_key_auth(&key_metadata.authorizations, Tag::USAGE_COUNT_LIMIT)
        .unwrap();
    if check_attestation && key_generations::has_default_keymint() {
        // Check usage-count-limit is included in attest-record.
        // `USAGE_COUNT_LIMIT` is supported from KeyMint1.0
        assert_ne!(
            gen_params.iter().filter(|kp| kp.tag == Tag::ATTESTATION_CHALLENGE).count(),
            0,
            "Attestation challenge is missing in generated key parameters."
        );
        let result = get_value_from_attest_record(
            key_metadata.certificate.as_ref().unwrap(),
            Tag::USAGE_COUNT_LIMIT,
            auth.securityLevel,
        )
        .expect("Attest id verification failed.");
        let usage_count: i32 = std::str::from_utf8(&result).unwrap().parse().unwrap();
        assert_eq!(usage_count, max_usage_count);
    }
    if max_usage_count == 1 {
        assert!(matches!(
            auth.securityLevel,
            SecurityLevel::KEYSTORE | SecurityLevel::TRUSTED_ENVIRONMENT
        ));
    } else {
        assert_eq!(auth.securityLevel, SecurityLevel::KEYSTORE);
    }

    // Try to use the key one more time.
    let result = key_generations::map_ks_error(sec_level.createOperation(
        &key_metadata.key,
        &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(Digest::SHA_2_256),
        false,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());
}

/// Generate a key with `ACTIVE_DATETIME` set to current time. Test should successfully generate
/// a key and verify the key characteristics. Test should be able to create a sign operation using
/// the generated key successfully.
#[test]
fn keystore2_gen_key_auth_active_datetime_test_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let duration_since_epoch = SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap();
    let active_datetime = duration_since_epoch.as_millis();
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .active_date_time(active_datetime.try_into().unwrap());

    let alias = "ks_test_auth_tags_test";
    let result = key_generations::create_key_and_operation(
        &sec_level,
        &gen_params,
        &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(Digest::SHA_2_256),
        alias,
    );
    assert!(result.is_ok());
    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate a key with `ACTIVE_DATETIME` set to future date and time. Test should successfully
/// generate a key and verify the key characteristics. Try to create a sign operation
/// using the generated key, test should fail to create an operation with error code
/// `KEY_NOT_YET_VALID`.
#[test]
fn keystore2_gen_key_auth_future_active_datetime_test_op_fail() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let duration_since_epoch = SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap();
    let future_active_datetime = duration_since_epoch.as_millis() + (24 * 60 * 60 * 1000);
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .active_date_time(future_active_datetime.try_into().unwrap());

    let alias = "ks_test_auth_tags_test";
    let result = key_generations::map_ks_error(key_generations::create_key_and_operation(
        &sec_level,
        &gen_params,
        &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(Digest::SHA_2_256),
        alias,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::KEY_NOT_YET_VALID), result.unwrap_err());
    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate a key with `ORIGINATION_EXPIRE_DATETIME` set to future date and time. Test should
/// successfully generate a key and verify the key characteristics. Test should be able to create
/// sign operation using the generated key successfully.
#[test]
fn keystore2_gen_key_auth_future_origination_expire_datetime_test_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let duration_since_epoch = SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap();
    let origination_expire_datetime = duration_since_epoch.as_millis() + (24 * 60 * 60 * 1000);
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .origination_expire_date_time(origination_expire_datetime.try_into().unwrap());

    let alias = "ks_test_auth_tags_test";
    let result = key_generations::create_key_and_operation(
        &sec_level,
        &gen_params,
        &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(Digest::SHA_2_256),
        alias,
    );
    assert!(result.is_ok());
    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate a key with `ORIGINATION_EXPIRE_DATETIME` set to current date and time. Test should
/// successfully generate a key and verify the key characteristics. Try to create a sign operation
/// using the generated key, test should fail to create an operation with error code
/// `KEY_EXPIRED`.
#[test]
fn keystore2_gen_key_auth_origination_expire_datetime_test_op_fail() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let duration_since_epoch = SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap();
    let origination_expire_datetime = duration_since_epoch.as_millis();
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .origination_expire_date_time(origination_expire_datetime.try_into().unwrap());

    let alias = "ks_test_auth_tags_test";
    let result = key_generations::map_ks_error(key_generations::create_key_and_operation(
        &sec_level,
        &gen_params,
        &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(Digest::SHA_2_256),
        alias,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::KEY_EXPIRED), result.unwrap_err());
    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate a HMAC key with `USAGE_EXPIRE_DATETIME` set to future date and time. Test should
/// successfully generate a key and verify the key characteristics. Test should be able to create
/// sign and verify operations using the generated key successfully.
#[test]
fn keystore2_gen_key_auth_future_usage_expire_datetime_hmac_verify_op_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let duration_since_epoch = SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap();
    let usage_expire_datetime = duration_since_epoch.as_millis() + (24 * 60 * 60 * 1000);
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::HMAC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .key_size(128)
        .min_mac_length(256)
        .digest(Digest::SHA_2_256)
        .usage_expire_date_time(usage_expire_datetime.try_into().unwrap());

    let alias = "ks_test_auth_tags_hmac_verify_success";
    let key_metadata = key_generations::generate_key(&sec_level, &gen_params, alias).unwrap();

    perform_sample_hmac_sign_verify_op(&sec_level, &key_metadata.key);
    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate a key with `USAGE_EXPIRE_DATETIME` set to current date and time. Test should
/// successfully generate a key and verify the key characteristics. Test should be able to create
/// sign operation successfully and fail while performing verify operation with error code
/// `KEY_EXPIRED`.
#[test]
fn keystore2_gen_key_auth_usage_expire_datetime_hmac_verify_op_fail() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let duration_since_epoch = SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap();
    let usage_expire_datetime = duration_since_epoch.as_millis();
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::HMAC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .key_size(128)
        .min_mac_length(256)
        .digest(Digest::SHA_2_256)
        .usage_expire_date_time(usage_expire_datetime.try_into().unwrap());

    let alias = "ks_test_auth_tags_hamc_verify_fail";
    let key_metadata = key_generations::generate_key(&sec_level, &gen_params, alias).unwrap();

    let result = key_generations::map_ks_error(
        sec_level.createOperation(
            &key_metadata.key,
            &authorizations::AuthSetBuilder::new()
                .purpose(KeyPurpose::VERIFY)
                .digest(Digest::SHA_2_256),
            false,
        ),
    );
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::KEY_EXPIRED), result.unwrap_err());
    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate AES key with `USAGE_EXPIRE_DATETIME` set to future date and time. Test should
/// successfully generate a key and verify the key characteristics. Test should be able to create
/// Encrypt and Decrypt operations successfully.
#[test]
fn keystore2_gen_key_auth_usage_future_expire_datetime_decrypt_op_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let duration_since_epoch = SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap();
    let usage_expire_datetime = duration_since_epoch.as_millis() + (24 * 60 * 60 * 1000);
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::AES)
        .purpose(KeyPurpose::ENCRYPT)
        .purpose(KeyPurpose::DECRYPT)
        .key_size(128)
        .padding_mode(PaddingMode::PKCS7)
        .block_mode(BlockMode::ECB)
        .usage_expire_date_time(usage_expire_datetime.try_into().unwrap());

    let alias = "ks_test_auth_tags_test";
    let key_metadata = key_generations::generate_key(&sec_level, &gen_params, alias).unwrap();
    let cipher_text = perform_sample_sym_key_encrypt_op(
        &sec_level,
        PaddingMode::PKCS7,
        BlockMode::ECB,
        &mut None,
        None,
        &key_metadata.key,
    )
    .unwrap();

    assert!(cipher_text.is_some());

    let plain_text = perform_sample_sym_key_decrypt_op(
        &sec_level,
        &cipher_text.unwrap(),
        PaddingMode::PKCS7,
        BlockMode::ECB,
        &mut None,
        None,
        &key_metadata.key,
    )
    .unwrap();
    assert!(plain_text.is_some());
    assert_eq!(plain_text.unwrap(), SAMPLE_PLAIN_TEXT.to_vec());
    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate AES key with `USAGE_EXPIRE_DATETIME` set to current date and time. Test should
/// successfully generate a key and verify the key characteristics. Test should be able to create
/// Encrypt operation successfully and fail while performing decrypt operation with error code
/// `KEY_EXPIRED`.
#[test]
fn keystore2_gen_key_auth_usage_expire_datetime_decrypt_op_fail() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let duration_since_epoch = SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap();
    let usage_expire_datetime = duration_since_epoch.as_millis();
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::AES)
        .purpose(KeyPurpose::ENCRYPT)
        .purpose(KeyPurpose::DECRYPT)
        .key_size(128)
        .padding_mode(PaddingMode::PKCS7)
        .block_mode(BlockMode::ECB)
        .usage_expire_date_time(usage_expire_datetime.try_into().unwrap());

    let alias = "ks_test_auth_tags_test";
    let key_metadata = key_generations::generate_key(&sec_level, &gen_params, alias).unwrap();
    let cipher_text = perform_sample_sym_key_encrypt_op(
        &sec_level,
        PaddingMode::PKCS7,
        BlockMode::ECB,
        &mut None,
        None,
        &key_metadata.key,
    )
    .unwrap();

    assert!(cipher_text.is_some());

    let result = key_generations::map_ks_error(perform_sample_sym_key_decrypt_op(
        &sec_level,
        &cipher_text.unwrap(),
        PaddingMode::PKCS7,
        BlockMode::ECB,
        &mut None,
        None,
        &key_metadata.key,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::KEY_EXPIRED), result.unwrap_err());
    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate a key with `BOOTLOADER_ONLY`. Test should successfully generate
/// a key and verify the key characteristics. Test should fail with error code `INVALID_KEY_BLOB`
/// during creation of an operation using this key.
#[test]
fn keystore2_gen_key_auth_boot_loader_only_op_fail() {
    skip_tests_if_keymaster_impl_present!();
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .boot_loader_only();

    let alias = "ks_test_auth_tags_test";
    let result = key_generations::map_ks_error(key_generations::create_key_and_operation(
        &sec_level,
        &gen_params,
        &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(Digest::SHA_2_256),
        alias,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INVALID_KEY_BLOB), result.unwrap_err());
}

/// Generate a key with `EARLY_BOOT_ONLY`. Test should successfully generate
/// a key and verify the key characteristics. Test should fail with error code `EARLY_BOOT_ENDED`
/// during creation of an operation using this key.
#[test]
fn keystore2_gen_key_auth_early_boot_only_op_fail() {
    skip_tests_if_keymaster_impl_present!();
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .early_boot_only();

    let alias = "ks_test_auth_tags_test";
    let result = key_generations::map_ks_error(key_generations::create_key_and_operation(
        &sec_level,
        &gen_params,
        &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(Digest::SHA_2_256),
        alias,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::EARLY_BOOT_ENDED), result.unwrap_err());
    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate a key with `MAX_USES_PER_BOOT`. Test should successfully generate
/// a key and verify the key characteristics. Test should be able to use the key successfully
/// `MAX_USES_COUNT` times. After exceeding key usage `MAX_USES_COUNT` times
/// subsequent attempts to use the key in test should fail with error code MAX_OPS_EXCEEDED.
#[test]
fn keystore2_gen_key_auth_max_uses_per_boot() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    const MAX_USES_COUNT: i32 = 3;

    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .max_uses_per_boot(MAX_USES_COUNT);

    let alias = "ks_test_auth_tags_test";
    // Generate a key and use the key for `MAX_USES_COUNT` times.
    let key_metadata = generate_key_and_perform_sign_verify_op_max_times(
        &sec_level,
        &gen_params,
        alias,
        MAX_USES_COUNT,
    )
    .unwrap();

    // Try to use the key one more time.
    let result = key_generations::map_ks_error(sec_level.createOperation(
        &key_metadata.key,
        &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(Digest::SHA_2_256),
        false,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::KEY_MAX_OPS_EXCEEDED), result.unwrap_err());
    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate a key with `USAGE_COUNT_LIMIT`. Test should successfully generate
/// a key and verify the key characteristics. Test should be able to use the key successfully
/// `MAX_USES_COUNT` times. After exceeding key usage `MAX_USES_COUNT` times
/// subsequent attempts to use the key in test should fail with response code `KEY_NOT_FOUND`.
/// Test should also verify that the attest record includes `USAGE_COUNT_LIMIT`.
#[test]
fn keystore2_gen_key_auth_usage_count_limit() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    const MAX_USES_COUNT: i32 = 3;

    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .usage_count_limit(MAX_USES_COUNT);

    let alias = "ks_test_auth_tags_test";
    generate_key_and_perform_op_with_max_usage_limit(
        &sec_level,
        &gen_params,
        alias,
        MAX_USES_COUNT,
        true,
    );
}

/// Generate a key with `USAGE_COUNT_LIMIT`. Test should successfully generate
/// a key and verify the key characteristics. Test should be able to use the key successfully
/// `MAX_USES_COUNT` times. After exceeding key usage `MAX_USES_COUNT` times
/// subsequent attempts to use the key in test should fail with response code `KEY_NOT_FOUND`.
/// Test should also verify that the attest record includes `USAGE_COUNT_LIMIT`.
#[test]
fn keystore2_gen_key_auth_usage_count_limit_one() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    const MAX_USES_COUNT: i32 = 1;

    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .usage_count_limit(MAX_USES_COUNT);

    let alias = "ks_test_auth_tags_test";
    generate_key_and_perform_op_with_max_usage_limit(
        &sec_level,
        &gen_params,
        alias,
        MAX_USES_COUNT,
        true,
    );
}

/// Generate a non-attested key with `USAGE_COUNT_LIMIT`. Test should successfully generate
/// a key and verify the key characteristics. Test should be able to use the key successfully
/// `MAX_USES_COUNT` times. After exceeding key usage `MAX_USES_COUNT` times
/// subsequent attempts to use the key in test should fail with response code `KEY_NOT_FOUND`.
#[test]
fn keystore2_gen_non_attested_key_auth_usage_count_limit() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    const MAX_USES_COUNT: i32 = 2;

    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .usage_count_limit(MAX_USES_COUNT);

    let alias = "ks_test_auth_tags_test";
    generate_key_and_perform_op_with_max_usage_limit(
        &sec_level,
        &gen_params,
        alias,
        MAX_USES_COUNT,
        false,
    );
}

/// Try to generate a key with `Tag::CREATION_DATETIME` set to valid value. Test should fail
/// to generate a key with `INVALID_ARGUMENT` error as Keystore2 backend doesn't allow user to
/// specify `CREATION_DATETIME`.
#[test]
fn keystore2_gen_key_auth_creation_date_time_test_fail_with_invalid_arg_error() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let duration_since_epoch = SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap();
    let creation_datetime = duration_since_epoch.as_millis();
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .creation_date_time(creation_datetime.try_into().unwrap());

    let alias = "ks_test_auth_tags_test";
    let result = key_generations::map_ks_error(sec_level.generateKey(
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
    ));

    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::INVALID_ARGUMENT), result.unwrap_err());
}

/// Generate a key with `Tag::INCLUDE_UNIQUE_ID` set. Test should verify that `Tag::UNIQUE_ID` is
/// included in attest record and it remains the same for new keys generated.
#[test]
fn keystore2_gen_key_auth_include_unique_id_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias_first = "ks_test_auth_tags_test_1";
    let unique_id_first = gen_key_including_unique_id(&sec_level, alias_first);

    let alias_second = "ks_test_auth_tags_test_2";
    let unique_id_second = gen_key_including_unique_id(&sec_level, alias_second);

    assert_eq!(unique_id_first, unique_id_second);

    delete_app_key(&keystore2, alias_first).unwrap();
    delete_app_key(&keystore2, alias_second).unwrap();
}

/// Generate a key with `APPLICATION_DATA`. Test should create an operation using the
/// same `APPLICATION_DATA` successfully.
#[test]
fn keystore2_gen_key_auth_app_data_test_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .app_data(b"app-data".to_vec());

    let alias = "ks_test_auth_tags_test";
    let result = key_generations::create_key_and_operation(
        &sec_level,
        &gen_params,
        &authorizations::AuthSetBuilder::new()
            .purpose(KeyPurpose::SIGN)
            .digest(Digest::SHA_2_256)
            .app_data(b"app-data".to_vec()),
        alias,
    );
    assert!(result.is_ok());
    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate a key with `APPLICATION_DATA`. Try to create an operation using the
/// different `APPLICATION_DATA`, test should fail to create an operation with error code
/// `INVALID_KEY_BLOB`.
#[test]
fn keystore2_gen_key_auth_app_data_test_fail() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .app_data(b"app-data".to_vec());

    let alias = "ks_test_auth_tags_test";
    let result = key_generations::map_ks_error(key_generations::create_key_and_operation(
        &sec_level,
        &gen_params,
        &authorizations::AuthSetBuilder::new()
            .purpose(KeyPurpose::SIGN)
            .digest(Digest::SHA_2_256)
            .app_data(b"invalid-app-data".to_vec()),
        alias,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INVALID_KEY_BLOB), result.unwrap_err());
    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate a key with `APPLICATION_ID`. Test should create an operation using the
/// same `APPLICATION_ID` successfully.
#[test]
fn keystore2_gen_key_auth_app_id_test_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .app_id(b"app-id".to_vec());

    let alias = "ks_test_auth_tags_test";
    let result = key_generations::create_key_and_operation(
        &sec_level,
        &gen_params,
        &authorizations::AuthSetBuilder::new()
            .purpose(KeyPurpose::SIGN)
            .digest(Digest::SHA_2_256)
            .app_id(b"app-id".to_vec()),
        alias,
    );
    assert!(result.is_ok());
    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate a key with `APPLICATION_ID`. Try to create an operation using the
/// different `APPLICATION_ID`, test should fail to create an operation with error code
/// `INVALID_KEY_BLOB`.
#[test]
fn keystore2_gen_key_auth_app_id_test_fail() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .app_id(b"app-id".to_vec());

    let alias = "ks_test_auth_tags_test";
    let result = key_generations::map_ks_error(key_generations::create_key_and_operation(
        &sec_level,
        &gen_params,
        &authorizations::AuthSetBuilder::new()
            .purpose(KeyPurpose::SIGN)
            .digest(Digest::SHA_2_256)
            .app_id(b"invalid-app-id".to_vec()),
        alias,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INVALID_KEY_BLOB), result.unwrap_err());
    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate an attestation-key without specifying `APPLICATION_ID` and `APPLICATION_DATA`.
/// Test should be able to generate a new key with specifying app-id and app-data using previously
/// generated attestation-key.
#[test]
fn keystore2_gen_attested_key_auth_app_id_app_data_test_success() {
    skip_test_if_no_app_attest_key_feature!();
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    // Generate attestation key.
    let attest_gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::ATTEST_KEY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec());
    let attest_alias = "ks_test_auth_tags_attest_key";
    let attest_key_metadata =
        key_generations::generate_key(&sec_level, &attest_gen_params, attest_alias).unwrap();

    // Generate attested key.
    let alias = "ks_test_auth_tags_attested_key";
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"bar".to_vec())
        .app_id(b"app-id".to_vec())
        .app_data(b"app-data".to_vec());

    let result = sec_level.generateKey(
        &KeyDescriptor {
            domain: Domain::APP,
            nspace: -1,
            alias: Some(alias.to_string()),
            blob: None,
        },
        Some(&attest_key_metadata.key),
        &gen_params,
        0,
        b"entropy",
    );

    assert!(result.is_ok());
    delete_app_key(&keystore2, alias).unwrap();
    delete_app_key(&keystore2, attest_alias).unwrap();
}

/// Generate an attestation-key with specifying `APPLICATION_ID` and `APPLICATION_DATA`.
/// Test should try to generate an attested key using previously generated attestation-key without
/// specifying app-id and app-data. Test should fail to generate a new key with error code
/// `INVALID_KEY_BLOB`.
/// It is an oversight of the Keystore API that `APPLICATION_ID` and `APPLICATION_DATA` tags cannot
/// be provided to generateKey for an attestation key that was generated with them.
#[test]
fn keystore2_gen_attestation_key_with_auth_app_id_app_data_test_fail() {
    skip_test_if_no_app_attest_key_feature!();
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    // Generate attestation key.
    let attest_gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::ATTEST_KEY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .app_id(b"app-id".to_vec())
        .app_data(b"app-data".to_vec());
    let attest_alias = "ks_test_auth_tags_attest_key";
    let attest_key_metadata =
        key_generations::generate_key(&sec_level, &attest_gen_params, attest_alias).unwrap();

    // Generate new key using above generated attestation key without providing app-id and app-data.
    let alias = "ks_test_auth_tags_attested_key";
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec());

    let result = key_generations::map_ks_error(sec_level.generateKey(
        &KeyDescriptor {
            domain: Domain::APP,
            nspace: -1,
            alias: Some(alias.to_string()),
            blob: None,
        },
        Some(&attest_key_metadata.key),
        &gen_params,
        0,
        b"entropy",
    ));

    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INVALID_KEY_BLOB), result.unwrap_err());
    delete_app_key(&keystore2, attest_alias).unwrap();
}

fn add_hardware_token(auth_type: HardwareAuthenticatorType) {
    let keystore_auth = get_keystore_auth_service();

    let token = HardwareAuthToken {
        challenge: 0,
        userId: 0,
        authenticatorId: 0,
        authenticatorType: auth_type,
        timestamp: Timestamp { milliSeconds: 500 },
        mac: vec![],
    };
    keystore_auth.addAuthToken(&token).unwrap();
}

#[test]
fn keystore2_flagged_off_get_last_auth_password_permission_denied() {
    if aconfig_android_hardware_biometrics_rust::last_authentication_time() {
        return;
    }

    let keystore_auth = get_keystore_auth_service();

    let result = keystore_auth.getLastAuthTime(0, &[HardwareAuthenticatorType::PASSWORD]);

    assert!(result.is_err());
    assert_eq!(result.unwrap_err().service_specific_error(), ResponseCode::PERMISSION_DENIED.0);
}

#[test]
fn keystore2_flagged_on_get_last_auth_password_success() {
    if !aconfig_android_hardware_biometrics_rust::last_authentication_time() {
        return;
    }

    let keystore_auth = get_keystore_auth_service();

    add_hardware_token(HardwareAuthenticatorType::PASSWORD);
    assert!(keystore_auth.getLastAuthTime(0, &[HardwareAuthenticatorType::PASSWORD]).unwrap() > 0);
}

#[test]
fn keystore2_flagged_on_get_last_auth_fingerprint_success() {
    if !aconfig_android_hardware_biometrics_rust::last_authentication_time() {
        return;
    }

    let keystore_auth = get_keystore_auth_service();

    add_hardware_token(HardwareAuthenticatorType::FINGERPRINT);
    assert!(
        keystore_auth.getLastAuthTime(0, &[HardwareAuthenticatorType::FINGERPRINT]).unwrap() > 0
    );
}

/// Generate a key with specifying `CERTIFICATE_SUBJECT and CERTIFICATE_SERIAL`. Test should
/// generate a key successfully and verify the specified key parameters.
#[test]
fn keystore2_gen_key_auth_serial_number_subject_test_success() {
    skip_tests_if_keymaster_impl_present!();
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let cert_subject = "test cert subject";
    let mut x509_name = X509NameBuilder::new().unwrap();
    x509_name.append_entry_by_text("CN", cert_subject).unwrap();
    let x509_name = x509_name.build().to_der().unwrap();

    let mut serial = BigNum::new().unwrap();
    serial.rand(159, MsbOption::MAYBE_ZERO, false).unwrap();

    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .cert_subject_name(x509_name)
        .cert_serial(serial.to_vec());

    let alias = "ks_test_auth_tags_test";
    let key_metadata = key_generations::generate_key(&sec_level, &gen_params, alias).unwrap();
    verify_certificate_subject_name(
        key_metadata.certificate.as_ref().unwrap(),
        cert_subject.as_bytes(),
    );
    verify_certificate_serial_num(key_metadata.certificate.as_ref().unwrap(), &serial);
    delete_app_key(&keystore2, alias).unwrap();
}
