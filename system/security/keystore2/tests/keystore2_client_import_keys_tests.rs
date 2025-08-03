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

use nix::unistd::getuid;

use openssl::rand::rand_bytes;
use openssl::x509::X509;

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Algorithm::Algorithm, BlockMode::BlockMode, Digest::Digest, EcCurve::EcCurve,
    ErrorCode::ErrorCode, HardwareAuthenticatorType::HardwareAuthenticatorType,
    KeyPurpose::KeyPurpose, PaddingMode::PaddingMode, SecurityLevel::SecurityLevel,
};
use android_system_keystore2::aidl::android::system::keystore2::{
    AuthenticatorSpec::AuthenticatorSpec, Domain::Domain,
    IKeystoreSecurityLevel::IKeystoreSecurityLevel, KeyDescriptor::KeyDescriptor,
    KeyMetadata::KeyMetadata, ResponseCode::ResponseCode,
};

use keystore2_test_utils::{
    authorizations, get_keystore_service, key_generations, key_generations::Error,
};

use keystore2_test_utils::ffi_test_utils::{
    create_wrapped_key, create_wrapped_key_additional_auth_data,
};

use crate::keystore2_client_test_utils::{
    encrypt_secure_key, encrypt_transport_key, perform_sample_asym_sign_verify_op,
    perform_sample_hmac_sign_verify_op, perform_sample_sym_key_decrypt_op,
    perform_sample_sym_key_encrypt_op, SAMPLE_PLAIN_TEXT,
};

pub fn import_rsa_sign_key_and_perform_sample_operation(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
    import_params: authorizations::AuthSetBuilder,
) {
    let key_metadata =
        key_generations::import_rsa_2048_key(sec_level, domain, nspace, alias, import_params)
            .unwrap();

    perform_sample_asym_sign_verify_op(
        sec_level,
        &key_metadata,
        Some(PaddingMode::RSA_PSS),
        Some(Digest::SHA_2_256),
    );
}

fn perform_sym_key_encrypt_decrypt_op(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    key_metadata: &KeyMetadata,
) {
    let cipher_text = perform_sample_sym_key_encrypt_op(
        sec_level,
        PaddingMode::PKCS7,
        BlockMode::ECB,
        &mut None,
        None,
        &key_metadata.key,
    )
    .unwrap();

    assert!(cipher_text.is_some());

    let plain_text = perform_sample_sym_key_decrypt_op(
        sec_level,
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
}

fn build_secure_key_wrapper(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    secure_key: &[u8],
    transport_key: &[u8],
    nonce: &[u8],
    aad: &[u8],
    wrapping_key_metadata: &KeyMetadata,
) -> Result<Vec<u8>, Error> {
    // Encrypt secure key with transport key.
    let transport_key_alias = format!("ks_transport_key_aes_256_key_test_{}", getuid());
    let transport_key_metadata =
        key_generations::import_transport_key(sec_level, Some(transport_key_alias), transport_key)
            .unwrap();
    let encrypted_secure_key = encrypt_secure_key(
        sec_level,
        secure_key,
        aad,
        nonce.to_vec(),
        128,
        &transport_key_metadata.key,
    )
    .unwrap();

    // Extract GCM-tag and encrypted secure key data.
    let encrypted_secure_key = encrypted_secure_key.unwrap();
    let gcm_tag: Vec<u8> =
        encrypted_secure_key[secure_key.len()..(encrypted_secure_key.len())].to_vec();
    let encrypted_secure_key: Vec<u8> = encrypted_secure_key[0..secure_key.len()].to_vec();

    // Get wrapping key puplic part and encrypt the transport key.
    let cert_bytes = wrapping_key_metadata.certificate.as_ref().unwrap();
    let cert = X509::from_der(cert_bytes.as_ref()).unwrap();
    let public_key = cert.public_key().unwrap();
    let encrypted_transport_key = encrypt_transport_key(transport_key, &public_key).unwrap();

    // Create `SecureKeyWrapper` ASN.1 DER-encoded data.
    create_wrapped_key(&encrypted_secure_key, &encrypted_transport_key, nonce, &gcm_tag)
}

/// Import RSA key and verify imported key parameters. Try to create an operation using the
/// imported key. Test should be able to create an operation successfully.
#[test]
fn keystore2_rsa_import_key_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = format!("ks_rsa_key_test_import_1_{}{}", getuid(), 2048);

    let import_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::RSA)
        .digest(Digest::SHA_2_256)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .padding_mode(PaddingMode::RSA_PSS)
        .key_size(2048)
        .rsa_public_exponent(65537)
        .cert_not_before(0)
        .cert_not_after(253402300799000);

    import_rsa_sign_key_and_perform_sample_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias),
        import_params,
    );
}

/// Import RSA key without providing key-size and public exponent in import key parameters list.
/// Let Key-size and public-exponent to be determined from the imported key material. Verify
/// imported key parameters. Try to create an operation using the imported key. Test should be
/// able to create an operation successfully.
#[test]
fn keystore2_rsa_import_key_determine_key_size_and_pub_exponent() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = format!("ks_rsa_key_test_import_2_{}{}", getuid(), 2048);

    // key-size and public-exponent shouldn't be specified in import key parameters list.
    let import_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::RSA)
        .digest(Digest::SHA_2_256)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .padding_mode(PaddingMode::RSA_PSS)
        .cert_not_before(0)
        .cert_not_after(253402300799000);

    import_rsa_sign_key_and_perform_sample_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias),
        import_params,
    );
}

/// Try to import RSA key with wrong key size as import-key-parameter. Test should fail to import
/// a key with `IMPORT_PARAMETER_MISMATCH` error code.
#[test]
fn keystore2_rsa_import_key_fails_with_keysize_param_mismatch_error() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = format!("ks_rsa_key_test_import_3_{}{}", getuid(), 2048);

    let import_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::RSA)
        .digest(Digest::SHA_2_256)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .padding_mode(PaddingMode::RSA_PSS)
        .key_size(1024) // Wrong key size is specified, (actual key-size is 2048).
        .rsa_public_exponent(65537)
        .cert_not_before(0)
        .cert_not_after(253402300799000);

    let result = key_generations::map_ks_error(sec_level.importKey(
        &KeyDescriptor { domain: Domain::APP, nspace: -1, alias: Some(alias), blob: None },
        None,
        &import_params,
        0,
        key_generations::RSA_2048_KEY,
    ));

    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::IMPORT_PARAMETER_MISMATCH), result.unwrap_err());
}

/// Try to import RSA key with wrong public-exponent as import-key-parameter.
/// Test should fail to import a key with `IMPORT_PARAMETER_MISMATCH` error code.
#[test]
fn keystore2_rsa_import_key_fails_with_public_exponent_param_mismatch_error() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = format!("ks_rsa_key_test_import_4_{}{}", getuid(), 2048);

    let import_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::RSA)
        .digest(Digest::SHA_2_256)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .padding_mode(PaddingMode::RSA_PSS)
        .key_size(2048)
        .rsa_public_exponent(3) // This doesn't match the key.
        .cert_not_before(0)
        .cert_not_after(253402300799000);

    let result = key_generations::map_ks_error(sec_level.importKey(
        &KeyDescriptor { domain: Domain::APP, nspace: -1, alias: Some(alias), blob: None },
        None,
        &import_params,
        0,
        key_generations::RSA_2048_KEY,
    ));

    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::IMPORT_PARAMETER_MISMATCH), result.unwrap_err());
}

/// Try to import a key with multiple purposes. Test should fail to import a key with
/// `INCOMPATIBLE_PURPOSE` error code. If the backend is `keymaster` then `importKey` shall be
/// successful.
#[test]
fn keystore2_rsa_import_key_with_multipurpose_fails_incompt_purpose_error() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = format!("ks_rsa_key_test_import_5_{}{}", getuid(), 2048);

    let import_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::RSA)
        .digest(Digest::SHA_2_256)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::ATTEST_KEY)
        .padding_mode(PaddingMode::RSA_PSS)
        .key_size(2048)
        .rsa_public_exponent(65537)
        .cert_not_before(0)
        .cert_not_after(253402300799000);

    let result = key_generations::map_ks_error(sec_level.importKey(
        &KeyDescriptor { domain: Domain::APP, nspace: -1, alias: Some(alias), blob: None },
        None,
        &import_params,
        0,
        key_generations::RSA_2048_KEY,
    ));

    if key_generations::has_default_keymint() {
        assert!(result.is_err());
        assert_eq!(Error::Km(ErrorCode::INCOMPATIBLE_PURPOSE), result.unwrap_err());
    } else {
        assert!(result.is_ok());
    }
}

/// Import EC key and verify imported key parameters. Let ec-curve to be determined from the
/// imported key material. Try to create an operation using the imported key. Test should be
/// able to create an operation successfully.
#[test]
fn keystore2_import_ec_key_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = format!("ks_ec_key_test_import_1_{}{}", getuid(), 256);

    // Don't specify ec-curve.
    let import_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .digest(Digest::SHA_2_256)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .cert_not_before(0)
        .cert_not_after(253402300799000);

    let key_metadata = key_generations::import_ec_p_256_key(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias),
        import_params,
    )
    .expect("Failed to import EC key.");

    perform_sample_asym_sign_verify_op(&sec_level, &key_metadata, None, Some(Digest::SHA_2_256));
}

/// Try to import EC key with wrong ec-curve as import-key-parameter. Test should fail to import a
/// key with `IMPORT_PARAMETER_MISMATCH` error code.
#[test]
fn keystore2_ec_import_key_fails_with_mismatch_curve_error() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = format!("ks_ec_key_test_import_1_{}{}", getuid(), 256);

    let import_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_224) // It doesn't match with key material.
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .cert_not_before(0)
        .cert_not_after(253402300799000);

    let result = key_generations::map_ks_error(sec_level.importKey(
        &KeyDescriptor { domain: Domain::APP, nspace: -1, alias: Some(alias), blob: None },
        None,
        &import_params,
        0,
        key_generations::EC_P_256_KEY,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::IMPORT_PARAMETER_MISMATCH), result.unwrap_err());
}

/// Import AES key and verify key parameters. Try to create an operation using the imported key.
/// Test should be able to create an operation successfully.
#[test]
fn keystore2_import_aes_key_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = format!("ks_aes_key_test_import_1_{}{}", getuid(), 256);
    let key_metadata = key_generations::import_aes_key(&sec_level, Domain::APP, -1, Some(alias))
        .expect("Failed to import AES key.");

    perform_sym_key_encrypt_decrypt_op(&sec_level, &key_metadata);
}

/// Import 3DES key and verify key parameters. Try to create an operation using the imported key.
/// Test should be able to create an operation successfully.
#[test]
fn keystore2_import_3des_key_success() {
    let keystore2 = get_keystore_service();
    let sec_level = key_generations::map_ks_error(
        keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT),
    )
    .unwrap();

    let alias = format!("ks_3des_key_test_import_1_{}{}", getuid(), 168);

    let key_metadata = key_generations::import_3des_key(&sec_level, Domain::APP, -1, Some(alias))
        .expect("Failed to import 3DES key.");

    perform_sym_key_encrypt_decrypt_op(&sec_level, &key_metadata);
}

/// Import HMAC key and verify key parameters. Try to create an operation using the imported key.
/// Test should be able to create an operation successfully.
#[test]
fn keystore2_import_hmac_key_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = format!("ks_hmac_key_test_import_1_{}", getuid());

    let key_metadata = key_generations::import_hmac_key(&sec_level, Domain::APP, -1, Some(alias))
        .expect("Failed to import HMAC key.");

    perform_sample_hmac_sign_verify_op(&sec_level, &key_metadata.key);
}

/// This test creates a wrapped key data and imports it. Validates the imported wrapped key.
///     1. Create a wrapped key material to import, as ASN.1 DER-encoded data corresponding to the
///        `SecureKeyWrapper` schema defined in IKeyMintDevice.aidl.
///     2. Import wrapped key and use it for crypto operations.
/// Test should successfully import the wrapped key and perform crypto operations.
#[test]
fn keystore2_create_wrapped_key_and_import_wrapped_key_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let mut secure_key = [0; 32];
    rand_bytes(&mut secure_key).unwrap();

    let mut transport_key = [0; 32];
    rand_bytes(&mut transport_key).unwrap();

    let mut nonce = [0; 12];
    rand_bytes(&mut nonce).unwrap();

    // Import wrapping key.
    let wrapping_key_alias = format!("ks_wrapping_key_test_import_2_{}_2048", getuid());
    let wrapping_key_metadata = key_generations::import_wrapping_key(
        &sec_level,
        key_generations::RSA_2048_KEY,
        Some(wrapping_key_alias),
    )
    .unwrap();

    // Create the DER-encoded representation of `KeyDescription` schema defined in
    // `IKeyMintDevice.aidl` and use it as additional authenticated data.
    let aad = create_wrapped_key_additional_auth_data().unwrap();

    // Build ASN.1 DER-encoded wrapped key material as described in `SecureKeyWrapper` schema.
    let wrapped_key_data = build_secure_key_wrapper(
        &sec_level,
        &secure_key,
        &transport_key,
        &nonce,
        &aad,
        &wrapping_key_metadata,
    )
    .unwrap();

    // Unwrap the key. Import wrapped key.
    let secured_key_alias = format!("ks_wrapped_aes_key_{}", getuid());
    let secured_key_metadata = key_generations::import_wrapped_key(
        &sec_level,
        Some(secured_key_alias),
        &wrapping_key_metadata,
        Some(wrapped_key_data.to_vec()),
    )
    .unwrap();

    perform_sym_key_encrypt_decrypt_op(&sec_level, &secured_key_metadata);
}

/// Create a wrapped key data with invalid Additional Authenticated Data (AAD) and
/// try to import wrapped key.
///     1. Create a wrapped key material with invalid AAD to import, as ASN.1 DER-encoded
///        data corresponding to the `SecureKeyWrapper` schema defined in IKeyMintDevice.aidl.
///     2. Import wrapped key and use it for crypto operations.
/// Test should fail to import the wrapped key with error code `VERIFICATION_FAILED`.
#[test]
fn keystore2_create_wrapped_key_with_invalid_aad_and_import_wrapped_key_fail() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let mut secure_key = [0; 32];
    rand_bytes(&mut secure_key).unwrap();

    let mut transport_key = [0; 32];
    rand_bytes(&mut transport_key).unwrap();

    let mut nonce = [0; 12];
    rand_bytes(&mut nonce).unwrap();

    // Import wrapping key.
    let wrapping_key_alias = format!("ks_wrapping_key_test_import_2_{}_2048", getuid());
    let wrapping_key_metadata = key_generations::import_wrapping_key(
        &sec_level,
        key_generations::RSA_2048_KEY,
        Some(wrapping_key_alias),
    )
    .unwrap();

    // Use invalid value as the additional authenticated data.
    let aad = b"foo";

    // Build ASN.1 DER-encoded wrapped key material as described in `SecureKeyWrapper` schema.
    let wrapped_key_data = build_secure_key_wrapper(
        &sec_level,
        &secure_key,
        &transport_key,
        &nonce,
        aad,
        &wrapping_key_metadata,
    )
    .unwrap();

    // Unwrap the key. Import wrapped key.
    let secured_key_alias = format!("ks_wrapped_aes_key_{}", getuid());
    let result = key_generations::map_ks_error(key_generations::import_wrapped_key(
        &sec_level,
        Some(secured_key_alias),
        &wrapping_key_metadata,
        Some(wrapped_key_data.to_vec()),
    ));

    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::VERIFICATION_FAILED), result.unwrap_err());
}

/// Import wrapped AES key and use it for crypto operations. Test should import wrapped key and
/// perform crypto operations successfully.
#[test]
fn keystore2_import_wrapped_key_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = format!("ks_wrapped_key_test_import_1_{}_256", getuid());
    let wrapping_key_alias = format!("ks_wrapping_key_test_import_1_{}_2048", getuid());

    let wrapping_key_params = authorizations::AuthSetBuilder::new()
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

    let key_metadata = key_generations::import_wrapping_key_and_wrapped_key(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias),
        Some(wrapping_key_alias),
        wrapping_key_params,
    )
    .expect("Failed to import wrapped key.");

    // Try to perform operations using wrapped key.
    perform_sym_key_encrypt_decrypt_op(&sec_level, &key_metadata);
}

/// Import wrapping-key without specifying KeyPurpose::WRAP_KEY in import key parameters. Try to
/// use this as wrapping-key for importing wrapped-key. Test should fail with an error code
/// `INCOMPATIBLE_PURPOSE` to import wrapped-key using a wrapping-key which doesn't possess
/// `WRAP_KEY` purpose.
#[test]
fn keystore2_import_wrapped_key_fails_with_wrong_purpose() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let wrapping_key_alias = format!("ks_wrapping_key_test_import_2_{}_2048", getuid());
    let alias = format!("ks_wrapped_key_test_import_2_{}_256", getuid());

    // In this KeyPurpose::WRAP_KEY is missing.
    let wrapping_key_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::RSA)
        .digest(Digest::SHA_2_256)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .padding_mode(PaddingMode::RSA_OAEP)
        .key_size(2048)
        .rsa_public_exponent(65537)
        .cert_not_before(0)
        .cert_not_after(253402300799000);

    let result =
        key_generations::map_ks_error(key_generations::import_wrapping_key_and_wrapped_key(
            &sec_level,
            Domain::APP,
            -1,
            Some(alias),
            Some(wrapping_key_alias),
            wrapping_key_params,
        ));

    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INCOMPATIBLE_PURPOSE), result.unwrap_err());
}

/// Try to import wrapped key whose wrapping key is missing in Android Keystore.
/// Test should fail to import wrapped key with `ResponseCode::KEY_NOT_FOUND`.
#[test]
fn keystore2_import_wrapped_key_fails_with_missing_wrapping_key() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let unwrap_params = authorizations::AuthSetBuilder::new()
        .digest(Digest::SHA_2_256)
        .padding_mode(PaddingMode::RSA_OAEP);

    let authenticator_spec: &[AuthenticatorSpec] = &[AuthenticatorSpec {
        authenticatorType: HardwareAuthenticatorType::NONE,
        authenticatorId: 0,
    }];

    let alias = format!("ks_wrapped_key_test_import_3_{}_256", getuid());

    // Wrapping key with this alias doesn't exist.
    let wrapping_key_alias = format!("ks_wrapping_key_not_exist_{}_2048", getuid());

    let result = key_generations::map_ks_error(sec_level.importWrappedKey(
        &KeyDescriptor {
            domain: Domain::APP,
            nspace: -1,
            alias: Some(alias),
            blob: Some(key_generations::WRAPPED_KEY.to_vec()),
        },
        &KeyDescriptor {
            domain: Domain::APP,
            nspace: -1,
            alias: Some(wrapping_key_alias),
            blob: None,
        },
        None,
        &unwrap_params,
        authenticator_spec,
    ));

    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());
}
