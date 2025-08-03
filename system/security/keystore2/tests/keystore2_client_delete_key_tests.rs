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

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    ErrorCode::ErrorCode, SecurityLevel::SecurityLevel,
};
use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, KeyDescriptor::KeyDescriptor, ResponseCode::ResponseCode,
};

use keystore2_test_utils::{get_keystore_service, key_generations, key_generations::Error};

/// Generate a key and delete it using keystore2 service `deleteKey` API. Test should successfully
/// delete the generated key.
#[test]
fn keystore2_delete_key_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = "delete_key_success_key";

    let key_metadata = key_generations::generate_ec_p256_signing_key(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        None,
    )
    .unwrap();

    keystore2.deleteKey(&key_metadata.key).expect("Failed to delete a key.");

    // Check wehther deleted key is removed from keystore.
    let result = key_generations::map_ks_error(keystore2.getKeyEntry(&key_metadata.key));
    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());
}

/// Try to delete non-existing key with domain other than BLOB using keystore2 service `deleteKey`
/// API. Test should fail with an error code `KEY_NOT_FOUND`.
#[test]
fn keystore2_delete_key_fail() {
    let test_alias = "delete_key_failure_key";
    let keystore2 = get_keystore_service();

    let result = key_generations::map_ks_error(keystore2.deleteKey(&KeyDescriptor {
        domain: Domain::SELINUX,
        nspace: key_generations::SELINUX_SHELL_NAMESPACE,
        alias: Some(test_alias.to_string()),
        blob: None,
    }));
    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());
}

/// Generate a key with `Domain::BLOB`. Try to delete a key with `Domain::BLOB` using keystore2
/// service `deleteKey` API. Test should fail to delete a key with domain BLOB with an error code
/// `INVALID_ARGUMENT`.
#[test]
fn keystore2_delete_key_with_blob_domain_fail() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = "delete_key_blob_fail_key";

    let key_metadata = key_generations::generate_ec_p256_signing_key(
        &sec_level,
        Domain::BLOB,
        key_generations::SELINUX_SHELL_NAMESPACE,
        Some(alias.to_string()),
        None,
    )
    .unwrap();

    let result = key_generations::map_ks_error(keystore2.deleteKey(&key_metadata.key));
    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::INVALID_ARGUMENT), result.unwrap_err());
}

/// Generate a key with `Domain::BLOB`. Delete generated key with `Domain::BLOB` using underlying
/// security level `deleteKey` API. Test should delete the key successfully.
#[test]
fn keystore2_delete_key_blob_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = "delete_key_blob_success_key";

    let key_metadata = key_generations::generate_ec_p256_signing_key(
        &sec_level,
        Domain::BLOB,
        key_generations::SELINUX_SHELL_NAMESPACE,
        Some(alias.to_string()),
        None,
    )
    .unwrap();

    let result = sec_level.deleteKey(&key_metadata.key);
    assert!(result.is_ok());
}

/// Try to delete a key with `Domain::BLOB` without providing key-blob. Test should fail to delete a
/// key with error code `INVALID_ARGUMENT`.
#[test]
fn keystore2_delete_key_fails_with_missing_key_blob() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let result = key_generations::map_ks_error(sec_level.deleteKey(&KeyDescriptor {
        domain: Domain::BLOB,
        nspace: key_generations::SELINUX_SHELL_NAMESPACE,
        alias: None,
        blob: None,
    }));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INVALID_ARGUMENT), result.unwrap_err());
}

/// Try to delete a key with domain other than `Domain::BLOB` using underlying security-level
/// `deleteKey` API. Test should fail to delete a key-blob from underlying security-level backend
/// with error code `INVALID_ARGUMENT`.
#[test]
fn keystore2_delete_key_blob_fail() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = format!("ks_delete_keyblob_test_key_{}", getuid());

    let key_metadata = key_generations::generate_ec_p256_signing_key(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias),
        None,
    )
    .unwrap();

    let result = key_generations::map_ks_error(sec_level.deleteKey(&key_metadata.key));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INVALID_ARGUMENT), result.unwrap_err());
}
