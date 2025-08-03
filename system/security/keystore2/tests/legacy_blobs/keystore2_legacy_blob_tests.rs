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

use nix::unistd::{getuid, Gid, Uid};
use rustutils::users::AID_USER_OFFSET;
use serde::{Deserialize, Serialize};

use std::ops::Deref;
use std::path::PathBuf;

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::SecurityLevel;

use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, KeyDescriptor::KeyDescriptor,
};

use android_security_maintenance::aidl::android::security::maintenance::IKeystoreMaintenance::IKeystoreMaintenance;

use android_security_authorization::aidl::android::security::authorization::{
    IKeystoreAuthorization::IKeystoreAuthorization,
};

use keystore2::key_parameter::KeyParameter as KsKeyparameter;
use keystore2::legacy_blob::test_utils::legacy_blob_test_vectors::*;
use keystore2::legacy_blob::test_utils::*;
use keystore2::legacy_blob::LegacyKeyCharacteristics;
use keystore2::utils::AesGcm;
use keystore2_crypto::{Password, ZVec};

use keystore2_test_utils::get_keystore_service;
use keystore2_test_utils::key_generations;
use keystore2_test_utils::run_as;

static USER_MANAGER_SERVICE_NAME: &str = "android.security.maintenance";
static AUTH_SERVICE_NAME: &str = "android.security.authorization";
const SELINUX_SHELL_NAMESPACE: i64 = 1;

fn get_maintenance() -> binder::Strong<dyn IKeystoreMaintenance> {
    binder::get_interface(USER_MANAGER_SERVICE_NAME).unwrap()
}

fn get_authorization() -> binder::Strong<dyn IKeystoreAuthorization> {
    binder::get_interface(AUTH_SERVICE_NAME).unwrap()
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
struct KeygenResult {
    cert: Vec<u8>,
    cert_chain: Vec<u8>,
    key_parameters: Vec<KsKeyparameter>,
}

struct TestKey(ZVec);

impl keystore2::utils::AesGcmKey for TestKey {
    fn key(&self) -> &[u8] {
        &self.0
    }
}

impl Deref for TestKey {
    type Target = [u8];
    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

fn keystore2_restart_service() {
    let output = std::process::Command::new("pidof")
        .arg("keystore2")
        .output()
        .expect("failed to execute pidof keystore2");

    let id = String::from_utf8(output.stdout).unwrap();
    let id: String = id.chars().filter(|c| c.is_ascii_digit()).collect();

    let _status = std::process::Command::new("kill").arg("-9").arg(id).status().unwrap();

    // Loop till we find keystore2 service up and running.
    loop {
        let output = std::process::Command::new("pidof")
            .arg("keystore2")
            .output()
            .expect("failed to execute pidof keystore2");

        if output.status.code() == Some(0) {
            break;
        }
    }
}

/// Create legacy blobs file layout for a user with user-id 99 and app-id 10001 with
/// user-cert, ca-certs and encrypted key-characteristics files and tries to import
/// these legacy blobs under user context.
///
/// Expected File layout for user with user-id "98" and app-id "10001" and key-alias
/// "authbound":
///     /data/misc/keystore/user_99/.masterkey
///     /data/misc/keystore/user_99/9910001_USRPKEY_authbound
///     /data/misc/keystore/user_99/.9910001_chr_USRPKEY_authbound
///     /data/misc/keystore/user_99/9910001_USRCERT_authbound
///     /data/misc/keystore/user_99/9910001_CACERT_authbound
///
/// Test performs below tasks -
/// With su context it performs following tasks -
///     1. Remove this user if already exist.
///     2. Generate a key-blob, user cert-blob and ca-cert-blob to store it in legacy blobs file
///        layout.
///     3. Prepare file layout using generated key-blob, user cert and ca certs.
///     4. Restart the keystore2 service to make it detect the populated legacy blobs.
///     5. Inform the keystore2 service about the user and unlock the user.
/// With user-99 context it performs following tasks -
///     6. To load and import the legacy key using its alias.
///     7. After successful key import validate the user cert and cert-chain with initially
///        generated blobs.
///     8. Validate imported key perameters. Imported key parameters list should be the combination
///        of the key-parameters in characteristics file and the characteristics according to
///        the augmentation rules. There might be duplicate entries with different values for the
///        parameters like OS_VERSION, OS_VERSION, BOOT_PATCHLEVEL, VENDOR_PATCHLEVEL etc.
///     9. Confirm keystore2 service cleanup the legacy blobs after successful import.
#[test]
fn keystore2_encrypted_characteristics() -> anyhow::Result<()> {
    let auid = 99 * AID_USER_OFFSET + 10001;
    let agid = 99 * AID_USER_OFFSET + 10001;
    static TARGET_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";
    static TARGET_SU_CTX: &str = "u:r:su:s0";

    // Cleanup user directory if it exists
    let path_buf = PathBuf::from("/data/misc/keystore/user_99");
    if path_buf.as_path().is_dir() {
        std::fs::remove_dir_all(path_buf.as_path()).unwrap();
    }

    // Safety: run_as must be called from a single threaded process.
    // This device test is run as a separate single threaded process.
    let mut gen_key_result = unsafe {
        run_as::run_as(TARGET_SU_CTX, Uid::from_raw(0), Gid::from_raw(0), || {
            // Remove user if already exist.
            let maint_service = get_maintenance();
            match maint_service.onUserRemoved(99) {
                Ok(_) => {
                    println!("User was existed, deleted successfully");
                }
                Err(e) => {
                    println!("onUserRemoved error: {:#?}", e);
                }
            }

            let keystore2 = get_keystore_service();
            let sec_level = keystore2
                .getSecurityLevel(SecurityLevel::SecurityLevel::TRUSTED_ENVIRONMENT)
                .unwrap();
            // Generate Key BLOB and prepare legacy keystore blob files.
            let att_challenge: &[u8] = b"foo";
            let key_metadata = key_generations::generate_ec_p256_signing_key(
                &sec_level,
                Domain::BLOB,
                SELINUX_SHELL_NAMESPACE,
                None,
                Some(att_challenge),
            )
            .expect("Failed to generate key blob");

            // Create keystore file layout for user_99.
            let pw: Password = PASSWORD.into();
            let pw_key = TestKey(pw.derive_key_pbkdf2(SUPERKEY_SALT, 32).unwrap());
            let super_key =
                TestKey(pw_key.decrypt(SUPERKEY_PAYLOAD, SUPERKEY_IV, SUPERKEY_TAG).unwrap());

            let mut path_buf = PathBuf::from("/data/misc/keystore/user_99");
            if !path_buf.as_path().is_dir() {
                std::fs::create_dir(path_buf.as_path()).unwrap();
            }
            path_buf.push(".masterkey");
            if !path_buf.as_path().is_file() {
                std::fs::write(path_buf.as_path(), SUPERKEY).unwrap();
            }

            let mut path_buf = PathBuf::from("/data/misc/keystore/user_99");
            path_buf.push("9910001_USRPKEY_authbound");
            if !path_buf.as_path().is_file() {
                make_encrypted_key_file(
                    path_buf.as_path(),
                    &super_key,
                    &key_metadata.key.blob.unwrap(),
                )
                .unwrap();
            }

            let mut path_buf = PathBuf::from("/data/misc/keystore/user_99");
            path_buf.push(".9910001_chr_USRPKEY_authbound");
            if !path_buf.as_path().is_file() {
                make_encrypted_characteristics_file(path_buf.as_path(), &super_key, KEY_PARAMETERS)
                    .unwrap();
            }

            let mut path_buf = PathBuf::from("/data/misc/keystore/user_99");
            path_buf.push("9910001_USRCERT_authbound");
            if !path_buf.as_path().is_file() {
                make_cert_blob_file(path_buf.as_path(), key_metadata.certificate.as_ref().unwrap())
                    .unwrap();
            }

            let mut path_buf = PathBuf::from("/data/misc/keystore/user_99");
            path_buf.push("9910001_CACERT_authbound");
            if !path_buf.as_path().is_file() {
                make_cert_blob_file(
                    path_buf.as_path(),
                    key_metadata.certificateChain.as_ref().unwrap(),
                )
                .unwrap();
            }

            // Keystore2 disables the legacy importer when it finds the legacy database empty.
            // However, if the device boots with an empty legacy database, the optimization kicks in
            // and keystore2 never checks the legacy file system layout.
            // So, restart keystore2 service to detect populated legacy database.
            keystore2_restart_service();

            let auth_service = get_authorization();
            match auth_service.onDeviceUnlocked(99, Some(PASSWORD)) {
                Ok(result) => {
                    println!("Unlock Result: {:?}", result);
                }
                Err(e) => {
                    panic!("Unlock should have succeeded: {:?}", e);
                }
            }

            let mut key_params: Vec<KsKeyparameter> = Vec::new();
            for param in key_metadata.authorizations {
                let key_param = KsKeyparameter::new(param.keyParameter.into(), param.securityLevel);
                key_params.push(key_param);
            }

            KeygenResult {
                cert: key_metadata.certificate.unwrap(),
                cert_chain: key_metadata.certificateChain.unwrap(),
                key_parameters: key_params,
            }
        })
    };

    // Safety: run_as must be called from a single threaded process.
    // This device test is run as a separate single threaded process.
    unsafe {
        run_as::run_as(TARGET_CTX, Uid::from_raw(auid), Gid::from_raw(agid), move || {
            println!("UID: {}", getuid());
            println!("Android User ID: {}", rustutils::users::multiuser_get_user_id(9910001));
            println!("Android app ID: {}", rustutils::users::multiuser_get_app_id(9910001));

            let test_alias = "authbound";
            let keystore2 = get_keystore_service();

            match keystore2.getKeyEntry(&KeyDescriptor {
                domain: Domain::APP,
                nspace: SELINUX_SHELL_NAMESPACE,
                alias: Some(test_alias.to_string()),
                blob: None,
            }) {
                Ok(key_entry_response) => {
                    assert_eq!(
                        key_entry_response.metadata.certificate.unwrap(),
                        gen_key_result.cert
                    );
                    assert_eq!(
                        key_entry_response.metadata.certificateChain.unwrap(),
                        gen_key_result.cert_chain
                    );
                    assert_eq!(key_entry_response.metadata.key.domain, Domain::KEY_ID);
                    assert_ne!(key_entry_response.metadata.key.nspace, 0);
                    assert_eq!(
                        key_entry_response.metadata.keySecurityLevel,
                        SecurityLevel::SecurityLevel::TRUSTED_ENVIRONMENT
                    );

                    // Preapare KsKeyParameter list from getKeEntry response Authorizations.
                    let mut key_params: Vec<KsKeyparameter> = Vec::new();
                    for param in key_entry_response.metadata.authorizations {
                        let key_param =
                            KsKeyparameter::new(param.keyParameter.into(), param.securityLevel);
                        key_params.push(key_param);
                    }

                    // Combine keyparameters from gen_key_result and keyparameters
                    // from legacy key-char file.
                    let mut legacy_file_key_params: Vec<KsKeyparameter> = Vec::new();
                    match structured_test_params() {
                        LegacyKeyCharacteristics::File(legacy_key_params) => {
                            for param in &legacy_key_params {
                                let mut present_in_gen_params = false;
                                for gen_param in &gen_key_result.key_parameters {
                                    if param.get_tag() == gen_param.get_tag() {
                                        present_in_gen_params = true;
                                    }
                                }
                                if !present_in_gen_params {
                                    legacy_file_key_params.push(param.clone());
                                }
                            }
                        }
                        _ => {
                            panic!("Expecting file characteristics");
                        }
                    }

                    // Remove Key-Params which have security levels other than TRUSTED_ENVIRONMENT
                    gen_key_result.key_parameters.retain(|in_element| {
                        *in_element.security_level()
                            == SecurityLevel::SecurityLevel::TRUSTED_ENVIRONMENT
                    });

                    println!("GetKeyEntry response key params: {:#?}", key_params);
                    println!("Generated key params: {:#?}", gen_key_result.key_parameters);

                    gen_key_result.key_parameters.append(&mut legacy_file_key_params);

                    println!("Combined key params: {:#?}", gen_key_result.key_parameters);

                    // Validate all keyparameters present in getKeyEntry response.
                    for param in &key_params {
                        gen_key_result.key_parameters.retain(|in_element| *in_element != *param);
                    }

                    println!(
                        "GetKeyEntry response unmatched key params: {:#?}",
                        gen_key_result.key_parameters
                    );
                    assert_eq!(gen_key_result.key_parameters.len(), 0);
                }
                Err(s) => {
                    panic!("getKeyEntry should have succeeded. {:?}", s);
                }
            };
        })
    };

    // Make sure keystore2 clean up imported legacy db.
    let path_buf = PathBuf::from("/data/misc/keystore/user_99");
    if path_buf.as_path().is_dir() {
        panic!("Keystore service should have deleted this dir {:?}", path_buf);
    }
    Ok(())
}

/// Create legacy blobs file layout for a user with user-id 98 and app-id 10001 with encrypted
/// user-cert and ca-certs files and tries to import these legacy blobs under user context.
///
/// Expected File layout for user with user-id "98" and app-id "10001" and key-alias
/// "authboundcertenc":
///     /data/misc/keystore/user_98/.masterkey
///     /data/misc/keystore/user_98/9810001_USRPKEY_authboundcertenc
///     /data/misc/keystore/user_98/.9810001_chr_USRPKEY_authboundcertenc
///     /data/misc/keystore/user_98/9810001_USRCERT_authboundcertenc
///     /data/misc/keystore/user_98/9810001_CACERT_authboundcertenc
///
/// Test performs below tasks -
/// With su context it performs following tasks -
///     1. Remove this user if already exist.
///     2. Generate a key-blob, user cert-blob and ca-cert-blob to store it in legacy blobs file
///        layout.
///     3. Prepare file layout using generated key-blob, user cert and ca certs.
///     4. Restart the keystore2 service to make it detect the populated legacy blobs.
///     5. Inform the keystore2 service about the user and unlock the user.
/// With user-98 context it performs following tasks -
///     6. To load and import the legacy key using its alias.
///     7. After successful key import validate the user cert and cert-chain with initially
///        generated blobs.
///     8. Validate imported key perameters. Imported key parameters list should be the combination
///        of the key-parameters in characteristics file and the characteristics according to
///        the augmentation rules. There might be duplicate entries with different values for the
///        parameters like OS_VERSION, OS_VERSION, BOOT_PATCHLEVEL, VENDOR_PATCHLEVEL etc.
///     9. Confirm keystore2 service cleanup the legacy blobs after successful import.
#[test]
fn keystore2_encrypted_certificates() -> anyhow::Result<()> {
    let auid = 98 * AID_USER_OFFSET + 10001;
    let agid = 98 * AID_USER_OFFSET + 10001;
    static TARGET_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";
    static TARGET_SU_CTX: &str = "u:r:su:s0";

    // Cleanup user directory if it exists
    let path_buf = PathBuf::from("/data/misc/keystore/user_98");
    if path_buf.as_path().is_dir() {
        std::fs::remove_dir_all(path_buf.as_path()).unwrap();
    }

    // Safety: run_as must be called from a single threaded process.
    // This device test is run as a separate single threaded process.
    let gen_key_result = unsafe {
        run_as::run_as(TARGET_SU_CTX, Uid::from_raw(0), Gid::from_raw(0), || {
            // Remove user if already exist.
            let maint_service = get_maintenance();
            match maint_service.onUserRemoved(98) {
                Ok(_) => {
                    println!("User was existed, deleted successfully");
                }
                Err(e) => {
                    println!("onUserRemoved error: {:#?}", e);
                }
            }

            let keystore2 = get_keystore_service();
            let sec_level = keystore2
                .getSecurityLevel(SecurityLevel::SecurityLevel::TRUSTED_ENVIRONMENT)
                .unwrap();
            // Generate Key BLOB and prepare legacy keystore blob files.
            let att_challenge: &[u8] = b"foo";
            let key_metadata = key_generations::generate_ec_p256_signing_key(
                &sec_level,
                Domain::BLOB,
                SELINUX_SHELL_NAMESPACE,
                None,
                Some(att_challenge),
            )
            .expect("Failed to generate key blob");

            // Create keystore file layout for user_98.
            let pw: Password = PASSWORD.into();
            let pw_key = TestKey(pw.derive_key_pbkdf2(SUPERKEY_SALT, 32).unwrap());
            let super_key =
                TestKey(pw_key.decrypt(SUPERKEY_PAYLOAD, SUPERKEY_IV, SUPERKEY_TAG).unwrap());

            let mut path_buf = PathBuf::from("/data/misc/keystore/user_98");
            if !path_buf.as_path().is_dir() {
                std::fs::create_dir(path_buf.as_path()).unwrap();
            }
            path_buf.push(".masterkey");
            if !path_buf.as_path().is_file() {
                std::fs::write(path_buf.as_path(), SUPERKEY).unwrap();
            }

            let mut path_buf = PathBuf::from("/data/misc/keystore/user_98");
            path_buf.push("9810001_USRPKEY_authboundcertenc");
            if !path_buf.as_path().is_file() {
                make_encrypted_key_file(
                    path_buf.as_path(),
                    &super_key,
                    &key_metadata.key.blob.unwrap(),
                )
                .unwrap();
            }

            let mut path_buf = PathBuf::from("/data/misc/keystore/user_98");
            path_buf.push(".9810001_chr_USRPKEY_authboundcertenc");
            if !path_buf.as_path().is_file() {
                std::fs::write(path_buf.as_path(), USRPKEY_AUTHBOUND_CHR).unwrap();
            }

            let mut path_buf = PathBuf::from("/data/misc/keystore/user_98");
            path_buf.push("9810001_USRCERT_authboundcertenc");
            if !path_buf.as_path().is_file() {
                make_encrypted_usr_cert_file(
                    path_buf.as_path(),
                    &super_key,
                    key_metadata.certificate.as_ref().unwrap(),
                )
                .unwrap();
            }

            let mut path_buf = PathBuf::from("/data/misc/keystore/user_98");
            path_buf.push("9810001_CACERT_authboundcertenc");
            if !path_buf.as_path().is_file() {
                make_encrypted_ca_cert_file(
                    path_buf.as_path(),
                    &super_key,
                    key_metadata.certificateChain.as_ref().unwrap(),
                )
                .unwrap();
            }

            // Keystore2 disables the legacy importer when it finds the legacy database empty.
            // However, if the device boots with an empty legacy database, the optimization kicks in
            // and keystore2 never checks the legacy file system layout.
            // So, restart keystore2 service to detect populated legacy database.
            keystore2_restart_service();

            let auth_service = get_authorization();
            match auth_service.onDeviceUnlocked(98, Some(PASSWORD)) {
                Ok(result) => {
                    println!("Unlock Result: {:?}", result);
                }
                Err(e) => {
                    panic!("Unlock should have succeeded: {:?}", e);
                }
            }

            let mut key_params: Vec<KsKeyparameter> = Vec::new();
            for param in key_metadata.authorizations {
                let key_param = KsKeyparameter::new(param.keyParameter.into(), param.securityLevel);
                key_params.push(key_param);
            }

            KeygenResult {
                cert: key_metadata.certificate.unwrap(),
                cert_chain: key_metadata.certificateChain.unwrap(),
                key_parameters: key_params,
            }
        })
    };

    // Safety: run_as must be called from a single threaded process.
    // This device test is run as a separate single threaded process.
    unsafe {
        run_as::run_as(TARGET_CTX, Uid::from_raw(auid), Gid::from_raw(agid), move || {
            println!("UID: {}", getuid());
            println!("Android User ID: {}", rustutils::users::multiuser_get_user_id(9810001));
            println!("Android app ID: {}", rustutils::users::multiuser_get_app_id(9810001));

            let test_alias = "authboundcertenc";
            let keystore2 = get_keystore_service();

            match keystore2.getKeyEntry(&KeyDescriptor {
                domain: Domain::APP,
                nspace: SELINUX_SHELL_NAMESPACE,
                alias: Some(test_alias.to_string()),
                blob: None,
            }) {
                Ok(key_entry_response) => {
                    assert_eq!(
                        key_entry_response.metadata.certificate.unwrap(),
                        gen_key_result.cert
                    );
                    assert_eq!(
                        key_entry_response.metadata.certificateChain.unwrap(),
                        gen_key_result.cert_chain
                    );

                    // Preapare KsKeyParameter list from getKeEntry response Authorizations.
                    let mut key_params: Vec<KsKeyparameter> = Vec::new();
                    for param in key_entry_response.metadata.authorizations {
                        let key_param =
                            KsKeyparameter::new(param.keyParameter.into(), param.securityLevel);
                        key_params.push(key_param);
                    }

                    println!("GetKeyEntry response key params: {:#?}", key_params);
                    println!("Generated key params: {:#?}", gen_key_result.key_parameters);
                    match structured_test_params_cache() {
                        LegacyKeyCharacteristics::Cache(legacy_key_params) => {
                            println!("Legacy key-char cache: {:#?}", legacy_key_params);
                            // Validate all keyparameters present in getKeyEntry response.
                            for param in &legacy_key_params {
                                key_params.retain(|in_element| *in_element != *param);
                            }

                            println!(
                                "GetKeyEntry response unmatched key params: {:#?}",
                                key_params
                            );
                            assert_eq!(key_params.len(), 0);
                        }
                        _ => {
                            panic!("Expecting file characteristics");
                        }
                    }
                }
                Err(s) => {
                    panic!("getKeyEntry should have succeeded. {:?}", s);
                }
            };
        })
    };

    // Make sure keystore2 clean up imported legacy db.
    let path_buf = PathBuf::from("/data/misc/keystore/user_98");
    if path_buf.as_path().is_dir() {
        panic!("Keystore service should have deleted this dir {:?}", path_buf);
    }
    Ok(())
}
