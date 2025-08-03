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

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Algorithm::Algorithm, Digest::Digest, ErrorCode::ErrorCode, KeyPurpose::KeyPurpose,
    SecurityLevel::SecurityLevel,
};
use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, IKeystoreSecurityLevel::IKeystoreSecurityLevel, KeyDescriptor::KeyDescriptor,
};

use keystore2_test_utils::{
    authorizations, get_keystore_service, key_generations, key_generations::Error,
};

use crate::keystore2_client_test_utils::perform_sample_sign_operation;

/// Generate HMAC key with given parameters and perform a sample operation using generated key.
fn create_hmac_key_and_operation(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    alias: &str,
    key_size: i32,
    mac_len: i32,
    min_mac_len: i32,
    digest: Digest,
) -> Result<(), binder::Status> {
    let key_metadata =
        key_generations::generate_hmac_key(sec_level, alias, key_size, min_mac_len, digest)?;

    let op_response = sec_level.createOperation(
        &key_metadata.key,
        &authorizations::AuthSetBuilder::new()
            .purpose(KeyPurpose::SIGN)
            .digest(digest)
            .mac_length(mac_len),
        false,
    )?;

    assert!(op_response.iOperation.is_some());
    assert_eq!(
        Ok(()),
        key_generations::map_ks_error(perform_sample_sign_operation(
            &op_response.iOperation.unwrap()
        ))
    );

    Ok(())
}

/// Generate HMAC keys with various digest modes [SHA1, SHA_2_224, SHA_2_256, SHA_2_384,
/// SHA_2_512]. Create an operation using generated keys. Test should create operations
/// successfully.
#[test]
fn keystore2_hmac_key_op_success() {
    let digests =
        [Digest::SHA1, Digest::SHA_2_224, Digest::SHA_2_256, Digest::SHA_2_384, Digest::SHA_2_512];
    let min_mac_len = 128;
    let mac_len = 128;
    let key_size = 128;

    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    for digest in digests {
        let alias = format!("ks_hmac_test_key_{}", digest.0);

        assert_eq!(
            Ok(()),
            create_hmac_key_and_operation(
                &sec_level,
                &alias,
                key_size,
                mac_len,
                min_mac_len,
                digest,
            )
        );
    }
}

/// Generate HMAC keys with various key lengths. For invalid key sizes, key generation
/// should fail with an error code `UNSUPPORTED_KEY_SIZE`.
#[test]
fn keystore2_hmac_gen_keys_fails_expect_unsupported_key_size() {
    let min_mac_len = 256;
    let digest = Digest::SHA_2_256;

    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    for key_size in 0..513 {
        let alias = format!("ks_hmac_test_key_{}", key_size);
        let result = key_generations::map_ks_error(key_generations::generate_hmac_key(
            &sec_level,
            &alias,
            key_size,
            min_mac_len,
            digest,
        ));

        match result {
            Ok(_) => {
                assert!((key_size >= 64 && key_size % 8 == 0));
            }
            Err(e) => {
                assert_eq!(e, Error::Km(ErrorCode::UNSUPPORTED_KEY_SIZE));
                assert!((key_size < 64 || key_size % 8 != 0), "Unsupported KeySize: {}", key_size);
            }
        }
    }
}

/// Generate HMAC keys with various min-mac-lengths. For invalid min-mac-length, key generation
/// should fail with an error code `UNSUPPORTED_MIN_MAC_LENGTH`.
#[test]
fn keystore2_hmac_gen_keys_fails_expect_unsupported_min_mac_length() {
    let digest = Digest::SHA_2_256;
    let key_size = 128;

    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    for min_mac_len in 0..257 {
        let alias = format!("ks_hmac_test_key_mml_{}", min_mac_len);
        match key_generations::map_ks_error(key_generations::generate_hmac_key(
            &sec_level,
            &alias,
            key_size,
            min_mac_len,
            digest,
        )) {
            Ok(_) => {
                assert!((min_mac_len >= 64 && min_mac_len % 8 == 0));
            }
            Err(e) => {
                assert_eq!(e, Error::Km(ErrorCode::UNSUPPORTED_MIN_MAC_LENGTH));
                assert!(
                    (min_mac_len < 64 || min_mac_len % 8 != 0),
                    "Unsupported MinMacLength: {}",
                    min_mac_len
                );
            }
        }
    }
}

/// Try to generate HMAC key with multiple digests in key authorizations list.
/// Test fails to generate a key with multiple digests with an error code `UNSUPPORTED_DIGEST`.
#[test]
fn keystore2_hmac_gen_key_multi_digests_fails_expect_unsupported_digest() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = "ks_hmac_test_key_multi_dig";
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::HMAC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .key_size(128)
        .min_mac_length(128)
        .digest(Digest::SHA1)
        .digest(Digest::SHA_2_256);

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
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_DIGEST), result.unwrap_err());
}

/// Try to generate HMAC key without providing digest mode. HMAC key generation with
/// no digest should fail with an error code `UNSUPPORTED_DIGEST`.
#[test]
fn keystore2_hmac_gen_key_no_digests_fails_expect_unsupported_digest() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = "ks_hmac_test_key_no_dig";
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::HMAC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .key_size(128)
        .min_mac_length(128);

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
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_DIGEST), result.unwrap_err());
}

/// Try to generate a HMAC key with NONE digest mode, it should fail with `UNSUPPORTED_DIGEST`
/// error code.
#[test]
fn keystore2_hmac_gen_key_with_none_digest_fails_expect_unsupported_digest() {
    let min_mac_len = 128;
    let key_size = 128;
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = "ks_hmac_test_key_fail";
    let result = key_generations::map_ks_error(key_generations::generate_hmac_key(
        &sec_level,
        alias,
        key_size,
        min_mac_len,
        Digest::NONE,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_DIGEST), result.unwrap_err());
}

/// Generate HMAC key with min-mac-len of 128 bits for the digest modes Digest::SHA1 and
/// Digest::SHA_2_224. Try to create an operation with generated key and mac-length greater than
/// digest length. Test should fail to create an operation with an error code
/// `UNSUPPORTED_MAC_LENGTH`.
#[test]
fn keystore2_hmac_key_op_with_mac_len_greater_than_digest_len_fail() {
    let digests = [Digest::SHA1, Digest::SHA_2_224];
    let min_mac_len = 128;
    let mac_len = 256;
    let key_size = 128;

    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    for digest in digests {
        let alias = format!("ks_hmac_test_key_{}", digest.0);

        let result = key_generations::map_ks_error(create_hmac_key_and_operation(
            &sec_level,
            &alias,
            key_size,
            mac_len,
            min_mac_len,
            digest,
        ));

        assert!(result.is_err());
        assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_MAC_LENGTH), result.unwrap_err());
    }
}

/// Generate HMAC key with min-mac-len of 128 bits for the digest modes Digest::SHA1 and
/// Digest::SHA_2_224. Try to create an operation with generated key and mac-length less than
/// min-mac-length. Test should fail to create an operation with an error code
/// `INVALID_MAC_LENGTH`.
#[test]
fn keystore2_hmac_key_op_with_mac_len_less_than_min_mac_len_fail() {
    let digests = [Digest::SHA1, Digest::SHA_2_224];
    let min_mac_len = 128;
    let mac_len = 64;
    let key_size = 128;

    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    for digest in digests {
        let alias = format!("ks_hmac_test_key_{}", digest.0);

        let result = key_generations::map_ks_error(create_hmac_key_and_operation(
            &sec_level,
            &alias,
            key_size,
            mac_len,
            min_mac_len,
            digest,
        ));

        assert!(result.is_err());
        assert_eq!(Error::Km(ErrorCode::INVALID_MAC_LENGTH), result.unwrap_err());
    }
}
