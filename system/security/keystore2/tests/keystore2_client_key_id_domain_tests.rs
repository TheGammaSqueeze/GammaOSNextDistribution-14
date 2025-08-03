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
    Digest::Digest, EcCurve::EcCurve, KeyPurpose::KeyPurpose, SecurityLevel::SecurityLevel,
};
use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, KeyDescriptor::KeyDescriptor, ResponseCode::ResponseCode,
};

use keystore2_test_utils::{
    authorizations, get_keystore_service, key_generations, key_generations::Error,
};

use crate::keystore2_client_test_utils::perform_sample_sign_operation;

/// Try to generate a key with `Domain::KEY_ID`, test should fail with an error code
/// `SYSTEM_ERROR`. `Domain::KEY_ID` is not allowed to use for generating a key. Key id is returned
/// by Keystore2 after a key has been mapped from an alias.
#[test]
fn keystore2_generate_key_with_key_id_domain_expect_sys_error() {
    let alias = "ks_gen_key_id_test_key";
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let result = key_generations::map_ks_error(key_generations::generate_ec_key(
        &sec_level,
        Domain::KEY_ID,
        key_generations::SELINUX_SHELL_NAMESPACE,
        Some(alias.to_string()),
        EcCurve::P_256,
        Digest::SHA_2_256,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::SYSTEM_ERROR), result.unwrap_err());
}

/// Generate a key and try to load the generated key using KEY_ID as domain. Create an
/// operation using key which is loaded with domain as KEY_ID. Test should create an operation
/// successfully.
#[test]
fn keystore2_find_key_with_key_id_as_domain() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = "ks_key_id_test_key";

    let key_metadata = key_generations::generate_ec_key(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        EcCurve::P_256,
        Digest::SHA_2_256,
    )
    .expect("Failed to generate a EC key.");

    // Try to load the above generated key with KEY_ID as domain.
    let key_entry_response = keystore2
        .getKeyEntry(&KeyDescriptor {
            domain: Domain::KEY_ID,
            nspace: key_metadata.key.nspace,
            alias: Some(alias.to_string()),
            blob: None,
        })
        .expect("Error in getKeyEntry to load a key with domain KEY_ID.");

    // Verify above found key is same the one generated.
    assert_eq!(key_metadata.key, key_entry_response.metadata.key);
    assert_eq!(key_metadata.certificate, key_entry_response.metadata.certificate);
    assert_eq!(key_metadata.certificateChain, key_entry_response.metadata.certificateChain);
    assert_eq!(key_metadata.key.nspace, key_entry_response.metadata.key.nspace);

    // Try to create an operation using above loaded key, operation should be created
    // successfully.
    let op_response = sec_level
        .createOperation(
            &key_entry_response.metadata.key,
            &authorizations::AuthSetBuilder::new()
                .purpose(KeyPurpose::SIGN)
                .digest(Digest::SHA_2_256),
            false,
        )
        .expect("Error in creation of operation.");

    assert!(op_response.iOperation.is_some());
    assert_eq!(
        Ok(()),
        key_generations::map_ks_error(perform_sample_sign_operation(
            &op_response.iOperation.unwrap()
        ))
    );
}

/// Generate a key with an alias. Generate another key and bind it to the same alias.
/// Try to create an operation using previously generated key. Creation of an operation should
/// fail because previously generated key material is no longer accessible. Test should successfully
/// create an operation using the rebound key.
#[test]
fn keystore2_key_id_alias_rebind_verify_by_alias() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = format!("ks_key_id_test_alias_rebind_1_{}", getuid());

    let key_metadata = key_generations::generate_ec_key(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        EcCurve::P_256,
        Digest::SHA_2_256,
    )
    .expect("Failed to generate a EC key.");

    // Generate a key with same alias as above generated key, so that alias will be rebound
    // to this key.
    let new_key_metadata = key_generations::generate_ec_key(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias),
        EcCurve::P_256,
        Digest::SHA_2_256,
    )
    .expect("Failed to generate a rebound EC key.");

    assert_ne!(key_metadata.key, new_key_metadata.key);
    assert_ne!(key_metadata.certificate, new_key_metadata.certificate);
    assert_ne!(key_metadata.key.nspace, new_key_metadata.key.nspace);

    // Try to create an operation using previously generated key_metadata.
    // It should fail as previously generated key material is no longer remains valid.
    let result = key_generations::map_ks_error(sec_level.createOperation(
        &key_metadata.key,
        &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(Digest::SHA_2_256),
        false,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());

    // Try to create an operation using rebound key, operation should be created
    // successfully.
    let op_response = sec_level
        .createOperation(
            &new_key_metadata.key,
            &authorizations::AuthSetBuilder::new()
                .purpose(KeyPurpose::SIGN)
                .digest(Digest::SHA_2_256),
            false,
        )
        .expect("Error in creation of operation using rebound key.");

    assert!(op_response.iOperation.is_some());
    assert_eq!(
        Ok(()),
        key_generations::map_ks_error(perform_sample_sign_operation(
            &op_response.iOperation.unwrap()
        ))
    );
}

/// Generate a key with an alias. Load the generated key with `Domain::KEY_ID`. Generate another
/// key and bind it to the same alias. Try to create an operation using the key loaded with domain
/// `KEY_ID`. Creation of an operation should fail because originally loaded key no longer exists.
/// Test should successfully create an operation using the rebound key.
#[test]
fn keystore2_key_id_alias_rebind_verify_by_key_id() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = format!("ks_key_id_test_alias_rebind_2_{}", getuid());

    let key_metadata = key_generations::generate_ec_key(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        EcCurve::P_256,
        Digest::SHA_2_256,
    )
    .expect("Failed to generate a EC key.");

    // Load the above generated key with KEY_ID as domain.
    let key_entry_response = keystore2
        .getKeyEntry(&KeyDescriptor {
            domain: Domain::KEY_ID,
            nspace: key_metadata.key.nspace,
            alias: Some(alias.to_string()),
            blob: None,
        })
        .expect("Error in getKeyEntry to load a key with domain KEY_ID.");

    // Verify above found key is same the one generated.
    assert_eq!(key_metadata.key, key_entry_response.metadata.key);
    assert_eq!(key_metadata.certificate, key_entry_response.metadata.certificate);
    assert_eq!(key_metadata.certificateChain, key_entry_response.metadata.certificateChain);
    assert_eq!(key_metadata.key.nspace, key_entry_response.metadata.key.nspace);

    // Generate another key with same alias as above generated key, so that alias will be rebound
    // to this key.
    let new_key_metadata = key_generations::generate_ec_key(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias),
        EcCurve::P_256,
        Digest::SHA_2_256,
    )
    .expect("Failed to generate a rebound EC key.");

    // Verify that an alias is rebound to a new key.
    assert_eq!(key_metadata.key.alias, new_key_metadata.key.alias);
    assert_ne!(key_metadata.key, new_key_metadata.key);
    assert_ne!(key_metadata.certificate, new_key_metadata.certificate);
    assert_ne!(key_metadata.key.nspace, new_key_metadata.key.nspace);

    // Try to create an operation using previously loaded key_entry_response.
    // It should fail as previously generated key material is no longer valid.
    let result = key_generations::map_ks_error(sec_level.createOperation(
        &key_entry_response.metadata.key,
        &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(Digest::SHA_2_256),
        false,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());

    // Try to create an operation using rebound key, operation should be created
    // successfully.
    let op_response = sec_level
        .createOperation(
            &new_key_metadata.key,
            &authorizations::AuthSetBuilder::new()
                .purpose(KeyPurpose::SIGN)
                .digest(Digest::SHA_2_256),
            false,
        )
        .expect("Error in creation of operation using rebound key.");

    assert!(op_response.iOperation.is_some());
    assert_eq!(
        Ok(()),
        key_generations::map_ks_error(perform_sample_sign_operation(
            &op_response.iOperation.unwrap()
        ))
    );
}
