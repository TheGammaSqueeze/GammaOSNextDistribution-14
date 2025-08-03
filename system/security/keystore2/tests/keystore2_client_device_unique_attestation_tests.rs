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
use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Algorithm::Algorithm, Digest::Digest, EcCurve::EcCurve, ErrorCode::ErrorCode,
    KeyPurpose::KeyPurpose, PaddingMode::PaddingMode, SecurityLevel::SecurityLevel, Tag::Tag,
};

use keystore2_test_utils::{
    authorizations, get_keystore_service, key_generations, key_generations::Error,
};

use keystore2_test_utils::ffi_test_utils::get_value_from_attest_record;

use crate::keystore2_client_test_utils::{
    delete_app_key, get_attest_id_value, is_second_imei_id_attestation_required,
    perform_sample_asym_sign_verify_op,
};

use crate::skip_tests_if_keymaster_impl_present;

/// This macro is used for generating device unique attested EC key with device id attestation.
macro_rules! test_ec_key_device_unique_attestation_id {
    ( $test_name:ident, $tag:expr, $prop_name:expr ) => {
        #[test]
        fn $test_name() {
            generate_ec_key_device_unique_attested_with_id_attest($tag, $prop_name);
        }
    };
}

/// This macro is used for generating device unique attested RSA key with device id attestation.
macro_rules! test_rsa_key_device_unique_attestation_id {
    ( $test_name:ident, $tag:expr, $prop_name:expr ) => {
        #[test]
        fn $test_name() {
            generate_rsa_key_device_unique_attested_with_id_attest($tag, $prop_name);
        }
    };
}

fn generate_ec_key_device_unique_attested_with_id_attest(attest_id_tag: Tag, prop_name: &str) {
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .device_unique_attestation();
    generate_device_unique_attested_key_with_device_attest_ids(
        gen_params,
        attest_id_tag,
        prop_name,
    );
}

fn generate_rsa_key_device_unique_attested_with_id_attest(attest_id_tag: Tag, prop_name: &str) {
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::RSA)
        .rsa_public_exponent(65537)
        .key_size(2048)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .padding_mode(PaddingMode::RSA_PKCS1_1_5_SIGN)
        .attestation_challenge(b"foo".to_vec())
        .device_unique_attestation();
    generate_device_unique_attested_key_with_device_attest_ids(
        gen_params,
        attest_id_tag,
        prop_name,
    );
}

fn add_attest_id_auth(
    gen_params: authorizations::AuthSetBuilder,
    attest_id_tag: Tag,
    value: Vec<u8>,
) -> authorizations::AuthSetBuilder {
    match attest_id_tag {
        Tag::ATTESTATION_ID_BRAND => gen_params.attestation_device_brand(value),
        Tag::ATTESTATION_ID_DEVICE => gen_params.attestation_device_name(value),
        Tag::ATTESTATION_ID_PRODUCT => gen_params.attestation_device_product_name(value),
        Tag::ATTESTATION_ID_SERIAL => gen_params.attestation_device_serial(value),
        Tag::ATTESTATION_ID_MANUFACTURER => gen_params.attestation_device_manufacturer(value),
        Tag::ATTESTATION_ID_MODEL => gen_params.attestation_device_model(value),
        Tag::ATTESTATION_ID_IMEI => gen_params.attestation_device_imei(value),
        Tag::ATTESTATION_ID_SECOND_IMEI => gen_params.attestation_device_second_imei(value),
        _ => {
            panic!("Unknown attestation id");
        }
    }
}

/// Generate a device unique attested key with attestation of the device's identifiers. Test should
/// succeed in generating a attested key with attestation of device identifiers. Test might fail on
/// devices which don't support device id attestation with error response code `CANNOT_ATTEST_IDS`.
fn generate_device_unique_attested_key_with_device_attest_ids(
    gen_params: authorizations::AuthSetBuilder,
    attest_id: Tag,
    prop_name: &str,
) {
    let keystore2 = get_keystore_service();
    let result =
        key_generations::map_ks_error(keystore2.getSecurityLevel(SecurityLevel::STRONGBOX));
    if result.is_err() {
        assert_eq!(Error::Km(ErrorCode::HARDWARE_TYPE_UNAVAILABLE), result.unwrap_err());
        return;
    }
    let sec_level = result.unwrap();

    if attest_id == Tag::ATTESTATION_ID_SECOND_IMEI
        && !is_second_imei_id_attestation_required(&keystore2)
    {
        return;
    }

    if let Some(value) = get_attest_id_value(attest_id, prop_name) {
        if value.is_empty() {
            return;
        }
        let gen_params = add_attest_id_auth(gen_params, attest_id, value.clone());
        let alias = "ks_test_device_unique_attest_id_test";
        match key_generations::map_ks_error(key_generations::generate_key(
            &sec_level,
            &gen_params,
            alias,
        )) {
            Ok(key_metadata) => {
                let attest_id_value = get_value_from_attest_record(
                    key_metadata.certificate.as_ref().unwrap(),
                    attest_id,
                    key_metadata.keySecurityLevel,
                )
                .expect("Attest id verification failed.");
                assert_eq!(attest_id_value, value);
                delete_app_key(&keystore2, alias).unwrap();
            }
            Err(e) => {
                assert_eq!(e, Error::Km(ErrorCode::CANNOT_ATTEST_IDS));
            }
        }
    }
}

/// Try generate a key with `DEVICE_UNIQUE_ATTESTATION` using `TRUSTED_ENVIRONMENT` security level.
/// Test should fail to generate a key with error code `INVALID_ARGUMENT`
#[test]
fn keystore2_gen_key_device_unique_attest_with_default_sec_level_unimplemented() {
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
        .device_unique_attestation();

    let alias = "ks_test_auth_tags_test";
    let result = key_generations::map_ks_error(key_generations::generate_key(
        &sec_level,
        &gen_params,
        alias,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INVALID_ARGUMENT), result.unwrap_err());
}

/// Generate a EC key with `DEVICE_UNIQUE_ATTESTATION` using `STRONGBOX` security level.
/// Test should create a key successfully, verify key characteristics, cert-chain signatures and
/// use it for performing an operation.
#[test]
fn keystore2_gen_ec_key_device_unique_attest_with_strongbox_sec_level_test_success() {
    let keystore2 = get_keystore_service();
    let result =
        key_generations::map_ks_error(keystore2.getSecurityLevel(SecurityLevel::STRONGBOX));
    if result.is_err() {
        assert_eq!(Error::Km(ErrorCode::HARDWARE_TYPE_UNAVAILABLE), result.unwrap_err());
        return;
    }

    let sec_level = result.unwrap();
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(b"foo".to_vec())
        .device_unique_attestation();

    let alias = "ks_device_unique_ec_key_attest_test";
    match key_generations::map_ks_error(key_generations::generate_key(
        &sec_level,
        &gen_params,
        alias,
    )) {
        Ok(key_metadata) => {
            perform_sample_asym_sign_verify_op(
                &sec_level,
                &key_metadata,
                None,
                Some(Digest::SHA_2_256),
            );
            delete_app_key(&keystore2, alias).unwrap();
        }
        Err(e) => {
            assert_eq!(e, Error::Km(ErrorCode::CANNOT_ATTEST_IDS));
        }
    }
}

/// Generate a RSA key with `DEVICE_UNIQUE_ATTESTATION` using `STRONGBOX` security level.
/// Test should create a key successfully, verify key characteristics, cert-chain signatures and
/// use it for performing an operation.
#[test]
fn keystore2_gen_rsa_key_device_unique_attest_with_strongbox_sec_level_test_success() {
    let keystore2 = get_keystore_service();
    let result =
        key_generations::map_ks_error(keystore2.getSecurityLevel(SecurityLevel::STRONGBOX));
    if result.is_err() {
        assert_eq!(Error::Km(ErrorCode::HARDWARE_TYPE_UNAVAILABLE), result.unwrap_err());
        return;
    }

    let sec_level = result.unwrap();
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::RSA)
        .rsa_public_exponent(65537)
        .key_size(2048)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .padding_mode(PaddingMode::RSA_PKCS1_1_5_SIGN)
        .attestation_challenge(b"foo".to_vec())
        .device_unique_attestation();

    let alias = "ks_device_unique_rsa_key_attest_test";
    match key_generations::map_ks_error(key_generations::generate_key(
        &sec_level,
        &gen_params,
        alias,
    )) {
        Ok(key_metadata) => {
            perform_sample_asym_sign_verify_op(
                &sec_level,
                &key_metadata,
                Some(PaddingMode::RSA_PKCS1_1_5_SIGN),
                Some(Digest::SHA_2_256),
            );
            delete_app_key(&keystore2, alias).unwrap();
        }
        Err(e) => {
            assert_eq!(e, Error::Km(ErrorCode::CANNOT_ATTEST_IDS));
        }
    }
}

/// Try to generate a device unique attested key with attestation of invalid device's identifiers.
/// Test should fail with error response code `CANNOT_ATTEST_IDS`.
#[test]
fn keystore2_device_unique_attest_key_fails_with_invalid_attestation_id() {
    let keystore2 = get_keystore_service();
    let result =
        key_generations::map_ks_error(keystore2.getSecurityLevel(SecurityLevel::STRONGBOX));
    if result.is_err() {
        assert_eq!(Error::Km(ErrorCode::HARDWARE_TYPE_UNAVAILABLE), result.unwrap_err());
        return;
    }

    let sec_level = result.unwrap();
    let attest_id_params = vec![
        (Tag::ATTESTATION_ID_BRAND, b"invalid-brand".to_vec()),
        (Tag::ATTESTATION_ID_DEVICE, b"invalid-device-name".to_vec()),
        (Tag::ATTESTATION_ID_PRODUCT, b"invalid-product-name".to_vec()),
        (Tag::ATTESTATION_ID_SERIAL, b"invalid-ro-serial".to_vec()),
        (Tag::ATTESTATION_ID_MANUFACTURER, b"invalid-ro-product-manufacturer".to_vec()),
        (Tag::ATTESTATION_ID_MODEL, b"invalid-ro-product-model".to_vec()),
        (Tag::ATTESTATION_ID_IMEI, b"invalid-imei".to_vec()),
    ];

    for (attest_id, value) in attest_id_params {
        let gen_params = authorizations::AuthSetBuilder::new()
            .no_auth_required()
            .algorithm(Algorithm::EC)
            .purpose(KeyPurpose::SIGN)
            .purpose(KeyPurpose::VERIFY)
            .digest(Digest::SHA_2_256)
            .ec_curve(EcCurve::P_256)
            .attestation_challenge(b"foo".to_vec())
            .device_unique_attestation();
        let alias = "ks_ec_device_unique_attested_test_key_fail";
        let gen_params = add_attest_id_auth(gen_params, attest_id, value.clone());

        let result = key_generations::map_ks_error(key_generations::generate_key(
            &sec_level,
            &gen_params,
            alias,
        ));
        assert!(result.is_err());
        assert!(matches!(result.unwrap_err(), Error::Km(ErrorCode::CANNOT_ATTEST_IDS)));
    }
}

// Below macros generate tests for generating device unique attested EC keys with attestation
// of the device's identifiers.
test_ec_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_ecdsa_attest_id_brand,
    Tag::ATTESTATION_ID_BRAND,
    "brand"
);
test_ec_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_ecdsa_attest_id_device,
    Tag::ATTESTATION_ID_DEVICE,
    "device"
);
test_ec_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_ecdsa_attest_id_product,
    Tag::ATTESTATION_ID_PRODUCT,
    "name"
);
test_ec_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_ecdsa_attest_id_serial,
    Tag::ATTESTATION_ID_SERIAL,
    "serialno"
);
test_ec_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_ecdsa_attest_id_manufacturer,
    Tag::ATTESTATION_ID_MANUFACTURER,
    "manufacturer"
);
test_ec_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_ecdsa_attest_id_model,
    Tag::ATTESTATION_ID_MODEL,
    "model"
);
test_ec_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_ecdsa_attest_id_imei,
    Tag::ATTESTATION_ID_IMEI,
    ""
);
test_ec_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_ecdsa_attest_id_second_imei,
    Tag::ATTESTATION_ID_SECOND_IMEI,
    ""
);

// Below macros generate tests for generating device unique attested RSA keys with attestation
// of the device's identifiers.
test_rsa_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_rsa_attest_id_brand,
    Tag::ATTESTATION_ID_BRAND,
    "brand"
);
test_rsa_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_rsa_attest_id_device,
    Tag::ATTESTATION_ID_DEVICE,
    "device"
);
test_rsa_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_rsa_attest_id_product,
    Tag::ATTESTATION_ID_PRODUCT,
    "name"
);
test_rsa_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_rsa_attest_id_serial,
    Tag::ATTESTATION_ID_SERIAL,
    "serialno"
);
test_rsa_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_rsa_attest_id_manufacturer,
    Tag::ATTESTATION_ID_MANUFACTURER,
    "manufacturer"
);
test_rsa_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_rsa_attest_id_model,
    Tag::ATTESTATION_ID_MODEL,
    "model"
);
test_rsa_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_rsa_attest_id_imei,
    Tag::ATTESTATION_ID_IMEI,
    ""
);
test_rsa_key_device_unique_attestation_id!(
    keystore2_device_unique_attest_rsa_attest_id_second_imei,
    Tag::ATTESTATION_ID_SECOND_IMEI,
    ""
);
