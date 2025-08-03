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

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Digest::Digest, KeyPurpose::KeyPurpose, SecurityLevel::SecurityLevel,
};
use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, IKeystoreSecurityLevel::IKeystoreSecurityLevel,
    IKeystoreService::IKeystoreService, KeyDescriptor::KeyDescriptor, KeyPermission::KeyPermission,
    ResponseCode::ResponseCode,
};

use keystore2_test_utils::{
    authorizations, get_keystore_service, key_generations, key_generations::Error, run_as,
};

use crate::keystore2_client_test_utils::{
    generate_ec_key_and_grant_to_users, perform_sample_sign_operation,
};

/// Generate an EC signing key and grant it to the user with given access vector.
fn generate_ec_key_and_grant_to_user(
    grantee_uid: i32,
    access_vector: i32,
) -> binder::Result<KeyDescriptor> {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = format!("{}{}", "ks_grant_test_key_1", getuid());

    let key_metadata = key_generations::generate_ec_p256_signing_key(
        &sec_level,
        Domain::SELINUX,
        key_generations::SELINUX_SHELL_NAMESPACE,
        Some(alias),
        None,
    )
    .unwrap();

    keystore2.grant(&key_metadata.key, grantee_uid, access_vector)
}

fn load_grant_key_and_perform_sign_operation(
    keystore2: &binder::Strong<dyn IKeystoreService>,
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    grant_key_nspace: i64,
) -> Result<(), binder::Status> {
    let key_entry_response = keystore2.getKeyEntry(&KeyDescriptor {
        domain: Domain::GRANT,
        nspace: grant_key_nspace,
        alias: None,
        blob: None,
    })?;

    // Perform sample crypto operation using granted key.
    let op_response = sec_level.createOperation(
        &key_entry_response.metadata.key,
        &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(Digest::SHA_2_256),
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

/// Try to grant a key with permission that does not map to any of the `KeyPermission` values.
/// An error is expected with values that does not map to set of permissions listed in
/// `KeyPermission`.
#[test]
fn keystore2_grant_key_with_invalid_perm_expecting_syserror() {
    const USER_ID: u32 = 99;
    const APPLICATION_ID: u32 = 10001;
    let grantee_uid = USER_ID * AID_USER_OFFSET + APPLICATION_ID;
    let invalid_access_vector = KeyPermission::CONVERT_STORAGE_KEY_TO_EPHEMERAL.0 << 19;

    let result = key_generations::map_ks_error(generate_ec_key_and_grant_to_user(
        grantee_uid.try_into().unwrap(),
        invalid_access_vector,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::SYSTEM_ERROR), result.unwrap_err());
}

/// Try to grant a key with empty access vector `KeyPermission::NONE`, should be able to grant a
/// key with empty access vector successfully. In grantee context try to use the granted key, it
/// should fail to load the key with permission denied error.
#[test]
fn keystore2_grant_key_with_perm_none() {
    static TARGET_SU_CTX: &str = "u:r:su:s0";

    static GRANTEE_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";
    const USER_ID: u32 = 99;
    const APPLICATION_ID: u32 = 10001;
    static GRANTEE_UID: u32 = USER_ID * AID_USER_OFFSET + APPLICATION_ID;
    static GRANTEE_GID: u32 = GRANTEE_UID;

    // SAFETY: The test is run in a separate process with no other threads.
    let grant_key_nspace = unsafe {
        run_as::run_as(TARGET_SU_CTX, Uid::from_raw(0), Gid::from_raw(0), || {
            let empty_access_vector = KeyPermission::NONE.0;

            let grant_key = key_generations::map_ks_error(generate_ec_key_and_grant_to_user(
                GRANTEE_UID.try_into().unwrap(),
                empty_access_vector,
            ))
            .unwrap();

            assert_eq!(grant_key.domain, Domain::GRANT);

            grant_key.nspace
        })
    };

    // In grantee context try to load the key, it should fail to load the granted key as it is
    // granted with empty access vector.
    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(
            GRANTEE_CTX,
            Uid::from_raw(GRANTEE_UID),
            Gid::from_raw(GRANTEE_GID),
            move || {
                let keystore2 = get_keystore_service();

                let result = key_generations::map_ks_error(keystore2.getKeyEntry(&KeyDescriptor {
                    domain: Domain::GRANT,
                    nspace: grant_key_nspace,
                    alias: None,
                    blob: None,
                }));
                assert!(result.is_err());
                assert_eq!(Error::Rc(ResponseCode::PERMISSION_DENIED), result.unwrap_err());
            },
        )
    };
}

/// Grant a key to the user (grantee) with `GET_INFO|USE` key permissions. Verify whether grantee
/// can succeed in loading the granted key and try to perform simple operation using this granted
/// key. Grantee should be able to load the key and use the key to perform crypto operation
/// successfully. Try to delete the granted key in grantee context where it is expected to fail to
/// delete it as `DELETE` permission is not granted.
#[test]
fn keystore2_grant_get_info_use_key_perm() {
    static TARGET_SU_CTX: &str = "u:r:su:s0";

    static GRANTEE_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";
    const USER_ID: u32 = 99;
    const APPLICATION_ID: u32 = 10001;
    static GRANTEE_UID: u32 = USER_ID * AID_USER_OFFSET + APPLICATION_ID;
    static GRANTEE_GID: u32 = GRANTEE_UID;

    // Generate a key and grant it to a user with GET_INFO|USE key permissions.
    // SAFETY: The test is run in a separate process with no other threads.
    let grant_key_nspace = unsafe {
        run_as::run_as(TARGET_SU_CTX, Uid::from_raw(0), Gid::from_raw(0), || {
            let access_vector = KeyPermission::GET_INFO.0 | KeyPermission::USE.0;
            let grant_key = key_generations::map_ks_error(generate_ec_key_and_grant_to_user(
                GRANTEE_UID.try_into().unwrap(),
                access_vector,
            ))
            .unwrap();

            assert_eq!(grant_key.domain, Domain::GRANT);

            grant_key.nspace
        })
    };

    // In grantee context load the key and try to perform crypto operation.
    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(
            GRANTEE_CTX,
            Uid::from_raw(GRANTEE_UID),
            Gid::from_raw(GRANTEE_GID),
            move || {
                let keystore2 = get_keystore_service();
                let sec_level =
                    keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

                // Load the granted key.
                let key_entry_response = keystore2
                    .getKeyEntry(&KeyDescriptor {
                        domain: Domain::GRANT,
                        nspace: grant_key_nspace,
                        alias: None,
                        blob: None,
                    })
                    .unwrap();

                // Perform sample crypto operation using granted key.
                let op_response = sec_level
                    .createOperation(
                        &key_entry_response.metadata.key,
                        &authorizations::AuthSetBuilder::new()
                            .purpose(KeyPurpose::SIGN)
                            .digest(Digest::SHA_2_256),
                        false,
                    )
                    .unwrap();
                assert!(op_response.iOperation.is_some());
                assert_eq!(
                    Ok(()),
                    key_generations::map_ks_error(perform_sample_sign_operation(
                        &op_response.iOperation.unwrap()
                    ))
                );

                // Try to delete the key, it is expected to be fail with permission denied error.
                let result = key_generations::map_ks_error(keystore2.deleteKey(&KeyDescriptor {
                    domain: Domain::GRANT,
                    nspace: grant_key_nspace,
                    alias: None,
                    blob: None,
                }));
                assert!(result.is_err());
                assert_eq!(Error::Rc(ResponseCode::PERMISSION_DENIED), result.unwrap_err());
            },
        )
    };
}

/// Grant a key to the user with DELETE access. In grantee context load the key and delete it.
/// Verify that grantee should succeed in deleting the granted key and in grantor context test
/// should fail to find the key with error response `KEY_NOT_FOUND`.
#[test]
fn keystore2_grant_delete_key_success() {
    static GRANTOR_SU_CTX: &str = "u:r:su:s0";
    static GRANTEE_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";
    const USER_ID: u32 = 99;
    const APPLICATION_ID: u32 = 10001;
    static GRANTEE_UID: u32 = USER_ID * AID_USER_OFFSET + APPLICATION_ID;
    static GRANTEE_GID: u32 = GRANTEE_UID;
    static ALIAS: &str = "ks_grant_key_delete_success";

    // Generate a key and grant it to a user with DELETE permission.
    // SAFETY: The test is run in a separate process with no other threads.
    let grant_key_nspace = unsafe {
        run_as::run_as(GRANTOR_SU_CTX, Uid::from_raw(0), Gid::from_raw(0), || {
            let keystore2 = get_keystore_service();
            let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
            let access_vector = KeyPermission::DELETE.0;
            let mut grant_keys = generate_ec_key_and_grant_to_users(
                &keystore2,
                &sec_level,
                Some(ALIAS.to_string()),
                vec![GRANTEE_UID.try_into().unwrap()],
                access_vector,
            )
            .unwrap();

            grant_keys.remove(0)
        })
    };

    // Grantee context, delete the key.
    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(
            GRANTEE_CTX,
            Uid::from_raw(GRANTEE_UID),
            Gid::from_raw(GRANTEE_GID),
            move || {
                let keystore2 = get_keystore_service();
                keystore2
                    .deleteKey(&KeyDescriptor {
                        domain: Domain::GRANT,
                        nspace: grant_key_nspace,
                        alias: None,
                        blob: None,
                    })
                    .unwrap();
            },
        )
    };

    // Verify whether key got deleted in grantor's context.
    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(GRANTOR_SU_CTX, Uid::from_raw(0), Gid::from_raw(0), move || {
            let keystore2_inst = get_keystore_service();
            let result =
                key_generations::map_ks_error(keystore2_inst.getKeyEntry(&KeyDescriptor {
                    domain: Domain::APP,
                    nspace: -1,
                    alias: Some(ALIAS.to_string()),
                    blob: None,
                }));
            assert!(result.is_err());
            assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());
        })
    };
}

/// Grant a key to the user. In grantee context load the granted key and try to grant it to second
/// user. Test should fail with a response code `PERMISSION_DENIED` to grant a key to second user
/// from grantee context. Test should make sure second grantee should not have a access to granted
/// key.
#[test]
fn keystore2_grant_key_fails_with_permission_denied() {
    static GRANTOR_SU_CTX: &str = "u:r:su:s0";
    static GRANTEE_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";
    const USER_ID: u32 = 99;
    const APPLICATION_ID: u32 = 10001;
    static GRANTEE_UID: u32 = USER_ID * AID_USER_OFFSET + APPLICATION_ID;
    static GRANTEE_GID: u32 = GRANTEE_UID;

    const SEC_USER_ID: u32 = 98;
    const SEC_APPLICATION_ID: u32 = 10001;
    static SEC_GRANTEE_UID: u32 = SEC_USER_ID * AID_USER_OFFSET + SEC_APPLICATION_ID;
    static SEC_GRANTEE_GID: u32 = SEC_GRANTEE_UID;

    // Generate a key and grant it to a user with GET_INFO permission.
    // SAFETY: The test is run in a separate process with no other threads.
    let grant_key_nspace = unsafe {
        run_as::run_as(GRANTOR_SU_CTX, Uid::from_raw(0), Gid::from_raw(0), || {
            let keystore2 = get_keystore_service();
            let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
            let access_vector = KeyPermission::GET_INFO.0;
            let alias = format!("ks_grant_perm_denied_key_{}", getuid());
            let mut grant_keys = generate_ec_key_and_grant_to_users(
                &keystore2,
                &sec_level,
                Some(alias),
                vec![GRANTEE_UID.try_into().unwrap()],
                access_vector,
            )
            .unwrap();

            grant_keys.remove(0)
        })
    };

    // Grantee context, load the granted key and try to grant it to `SEC_GRANTEE_UID` grantee.
    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(
            GRANTEE_CTX,
            Uid::from_raw(GRANTEE_UID),
            Gid::from_raw(GRANTEE_GID),
            move || {
                let keystore2 = get_keystore_service();
                let access_vector = KeyPermission::GET_INFO.0;

                let key_entry_response = keystore2
                    .getKeyEntry(&KeyDescriptor {
                        domain: Domain::GRANT,
                        nspace: grant_key_nspace,
                        alias: None,
                        blob: None,
                    })
                    .unwrap();

                let result = key_generations::map_ks_error(keystore2.grant(
                    &key_entry_response.metadata.key,
                    SEC_GRANTEE_UID.try_into().unwrap(),
                    access_vector,
                ));
                assert!(result.is_err());
                assert_eq!(Error::Rc(ResponseCode::PERMISSION_DENIED), result.unwrap_err());
            },
        )
    };

    // Make sure second grantee shouldn't have access to the above granted key.
    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(
            GRANTEE_CTX,
            Uid::from_raw(SEC_GRANTEE_UID),
            Gid::from_raw(SEC_GRANTEE_GID),
            move || {
                let keystore2 = get_keystore_service();

                let result = key_generations::map_ks_error(keystore2.getKeyEntry(&KeyDescriptor {
                    domain: Domain::GRANT,
                    nspace: grant_key_nspace,
                    alias: None,
                    blob: None,
                }));

                assert!(result.is_err());
                assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());
            },
        )
    };
}

/// Try to grant a key with `GRANT` access. Keystore2 system shouldn't allow to grant a key with
/// `GRANT` access. Test should fail to grant a key with `PERMISSION_DENIED` error response code.
#[test]
fn keystore2_grant_key_fails_with_grant_perm_expect_perm_denied() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let access_vector = KeyPermission::GRANT.0;
    let alias = format!("ks_grant_access_vec_key_{}", getuid());
    let user_id = 98;
    let application_id = 10001;
    let grantee_uid = user_id * AID_USER_OFFSET + application_id;

    let result = key_generations::map_ks_error(generate_ec_key_and_grant_to_users(
        &keystore2,
        &sec_level,
        Some(alias),
        vec![grantee_uid.try_into().unwrap()],
        access_vector,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::PERMISSION_DENIED), result.unwrap_err());
}

/// Try to grant a non-existing key to the user. Test should fail with `KEY_NOT_FOUND` error
/// response.
#[test]
fn keystore2_grant_fails_with_non_existing_key_expect_key_not_found_err() {
    let keystore2 = get_keystore_service();
    let alias = format!("ks_grant_test_non_existing_key_5_{}", getuid());
    let user_id = 98;
    let application_id = 10001;
    let grantee_uid = user_id * AID_USER_OFFSET + application_id;
    let access_vector = KeyPermission::GET_INFO.0;

    let result = key_generations::map_ks_error(keystore2.grant(
        &KeyDescriptor {
            domain: Domain::SELINUX,
            nspace: key_generations::SELINUX_SHELL_NAMESPACE,
            alias: Some(alias),
            blob: None,
        },
        grantee_uid.try_into().unwrap(),
        access_vector,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());
}

/// Grant a key to the user and immediately ungrant the granted key. In grantee context try to load
/// the key. Grantee should fail to load the ungranted key with `KEY_NOT_FOUND` error response.
#[test]
fn keystore2_ungrant_key_success() {
    static GRANTOR_SU_CTX: &str = "u:r:su:s0";
    static GRANTEE_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";
    const USER_ID: u32 = 99;
    const APPLICATION_ID: u32 = 10001;
    static GRANTEE_UID: u32 = USER_ID * AID_USER_OFFSET + APPLICATION_ID;
    static GRANTEE_GID: u32 = GRANTEE_UID;

    // Generate a key and grant it to a user with GET_INFO permission.
    // SAFETY: The test is run in a separate process with no other threads.
    let grant_key_nspace = unsafe {
        run_as::run_as(GRANTOR_SU_CTX, Uid::from_raw(0), Gid::from_raw(0), || {
            let keystore2 = get_keystore_service();
            let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
            let alias = format!("ks_ungrant_test_key_1{}", getuid());
            let access_vector = KeyPermission::GET_INFO.0;
            let mut grant_keys = generate_ec_key_and_grant_to_users(
                &keystore2,
                &sec_level,
                Some(alias.to_string()),
                vec![GRANTEE_UID.try_into().unwrap()],
                access_vector,
            )
            .unwrap();

            let grant_key_nspace = grant_keys.remove(0);

            //Ungrant above granted key.
            keystore2
                .ungrant(
                    &KeyDescriptor {
                        domain: Domain::APP,
                        nspace: -1,
                        alias: Some(alias),
                        blob: None,
                    },
                    GRANTEE_UID.try_into().unwrap(),
                )
                .unwrap();

            grant_key_nspace
        })
    };

    // Grantee context, try to load the ungranted key.
    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(
            GRANTEE_CTX,
            Uid::from_raw(GRANTEE_UID),
            Gid::from_raw(GRANTEE_GID),
            move || {
                let keystore2 = get_keystore_service();
                let result = key_generations::map_ks_error(keystore2.getKeyEntry(&KeyDescriptor {
                    domain: Domain::GRANT,
                    nspace: grant_key_nspace,
                    alias: None,
                    blob: None,
                }));
                assert!(result.is_err());
                assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());
            },
        )
    };
}

/// Generate a key, grant it to the user and then delete the granted key. Try to ungrant
/// a deleted key. Test should fail to ungrant a non-existing key with `KEY_NOT_FOUND` error
/// response. Generate a new key with the same alias and try to access the previously granted
/// key in grantee context. Test should fail to load the granted key in grantee context as the
/// associated key is deleted from grantor context.
#[test]
fn keystore2_ungrant_fails_with_non_existing_key_expect_key_not_found_error() {
    static GRANTOR_SU_CTX: &str = "u:r:su:s0";
    static GRANTEE_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";

    const APPLICATION_ID: u32 = 10001;
    const USER_ID: u32 = 99;
    static GRANTEE_UID: u32 = USER_ID * AID_USER_OFFSET + APPLICATION_ID;
    static GRANTEE_GID: u32 = GRANTEE_UID;

    // SAFETY: The test is run in a separate process with no other threads.
    let grant_key_nspace = unsafe {
        run_as::run_as(GRANTOR_SU_CTX, Uid::from_raw(0), Gid::from_raw(0), || {
            let keystore2 = get_keystore_service();
            let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
            let alias = format!("{}{}", "ks_grant_delete_ungrant_test_key_1", getuid());

            let key_metadata = key_generations::generate_ec_p256_signing_key(
                &sec_level,
                Domain::SELINUX,
                key_generations::SELINUX_SHELL_NAMESPACE,
                Some(alias.to_string()),
                None,
            )
            .unwrap();

            let access_vector = KeyPermission::GET_INFO.0;
            let grant_key = keystore2
                .grant(&key_metadata.key, GRANTEE_UID.try_into().unwrap(), access_vector)
                .unwrap();
            assert_eq!(grant_key.domain, Domain::GRANT);

            // Delete above granted key.
            keystore2.deleteKey(&key_metadata.key).unwrap();

            // Try to ungrant above granted key.
            let result = key_generations::map_ks_error(
                keystore2.ungrant(&key_metadata.key, GRANTEE_UID.try_into().unwrap()),
            );
            assert!(result.is_err());
            assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());

            // Generate a new key with the same alias and try to access the earlier granted key
            // in grantee context.
            let result = key_generations::generate_ec_p256_signing_key(
                &sec_level,
                Domain::SELINUX,
                key_generations::SELINUX_SHELL_NAMESPACE,
                Some(alias),
                None,
            );
            assert!(result.is_ok());

            grant_key.nspace
        })
    };

    // Make sure grant did not persist, try to access the earlier granted key in grantee context.
    // Grantee context should fail to load the granted key as its associated key is deleted in
    // grantor context.
    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(
            GRANTEE_CTX,
            Uid::from_raw(GRANTEE_UID),
            Gid::from_raw(GRANTEE_GID),
            move || {
                let keystore2 = get_keystore_service();

                let result = key_generations::map_ks_error(keystore2.getKeyEntry(&KeyDescriptor {
                    domain: Domain::GRANT,
                    nspace: grant_key_nspace,
                    alias: None,
                    blob: None,
                }));
                assert!(result.is_err());
                assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());
            },
        )
    };
}

/// Grant a key to multiple users. Verify that all grantees should succeed in loading the key and
/// use it for performing an operation successfully.
#[test]
fn keystore2_grant_key_to_multi_users_success() {
    static GRANTOR_SU_CTX: &str = "u:r:su:s0";
    static GRANTEE_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";

    const APPLICATION_ID: u32 = 10001;
    const USER_ID_1: u32 = 99;
    static GRANTEE_1_UID: u32 = USER_ID_1 * AID_USER_OFFSET + APPLICATION_ID;
    static GRANTEE_1_GID: u32 = GRANTEE_1_UID;

    const USER_ID_2: u32 = 98;
    static GRANTEE_2_UID: u32 = USER_ID_2 * AID_USER_OFFSET + APPLICATION_ID;
    static GRANTEE_2_GID: u32 = GRANTEE_2_UID;

    // Generate a key and grant it to multiple users with GET_INFO|USE permissions.
    // SAFETY: The test is run in a separate process with no other threads.
    let mut grant_keys = unsafe {
        run_as::run_as(GRANTOR_SU_CTX, Uid::from_raw(0), Gid::from_raw(0), || {
            let keystore2 = get_keystore_service();
            let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
            let alias = format!("ks_grant_test_key_2{}", getuid());
            let access_vector = KeyPermission::GET_INFO.0 | KeyPermission::USE.0;

            generate_ec_key_and_grant_to_users(
                &keystore2,
                &sec_level,
                Some(alias),
                vec![GRANTEE_1_UID.try_into().unwrap(), GRANTEE_2_UID.try_into().unwrap()],
                access_vector,
            )
            .unwrap()
        })
    };

    for (grantee_uid, grantee_gid) in
        &[(GRANTEE_1_UID, GRANTEE_1_GID), (GRANTEE_2_UID, GRANTEE_2_GID)]
    {
        let grant_key_nspace = grant_keys.remove(0);
        // SAFETY: The test is run in a separate process with no other threads.
        unsafe {
            run_as::run_as(
                GRANTEE_CTX,
                Uid::from_raw(*grantee_uid),
                Gid::from_raw(*grantee_gid),
                move || {
                    let keystore2 = get_keystore_service();
                    let sec_level =
                        keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

                    assert_eq!(
                        Ok(()),
                        key_generations::map_ks_error(load_grant_key_and_perform_sign_operation(
                            &keystore2,
                            &sec_level,
                            grant_key_nspace
                        ))
                    );
                },
            )
        };
    }
}

/// Grant a key to multiple users with GET_INFO|DELETE permissions. In one of the grantee context
/// use the key and delete it. Try to load the granted key in another grantee context. Test should
/// fail to load the granted key with `KEY_NOT_FOUND` error response.
#[test]
fn keystore2_grant_key_to_multi_users_delete_fails_with_key_not_found_error() {
    static GRANTOR_SU_CTX: &str = "u:r:su:s0";
    static GRANTEE_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";

    const USER_ID_1: u32 = 99;
    const APPLICATION_ID: u32 = 10001;
    static GRANTEE_1_UID: u32 = USER_ID_1 * AID_USER_OFFSET + APPLICATION_ID;
    static GRANTEE_1_GID: u32 = GRANTEE_1_UID;

    const USER_ID_2: u32 = 98;
    static GRANTEE_2_UID: u32 = USER_ID_2 * AID_USER_OFFSET + APPLICATION_ID;
    static GRANTEE_2_GID: u32 = GRANTEE_2_UID;

    // Generate a key and grant it to multiple users with GET_INFO permission.
    // SAFETY: The test is run in a separate process with no other threads.
    let mut grant_keys = unsafe {
        run_as::run_as(GRANTOR_SU_CTX, Uid::from_raw(0), Gid::from_raw(0), || {
            let keystore2 = get_keystore_service();
            let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
            let alias = format!("ks_grant_test_key_2{}", getuid());
            let access_vector =
                KeyPermission::GET_INFO.0 | KeyPermission::USE.0 | KeyPermission::DELETE.0;

            generate_ec_key_and_grant_to_users(
                &keystore2,
                &sec_level,
                Some(alias),
                vec![GRANTEE_1_UID.try_into().unwrap(), GRANTEE_2_UID.try_into().unwrap()],
                access_vector,
            )
            .unwrap()
        })
    };

    // Grantee #1 context
    let grant_key1_nspace = grant_keys.remove(0);
    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(
            GRANTEE_CTX,
            Uid::from_raw(GRANTEE_1_UID),
            Gid::from_raw(GRANTEE_1_GID),
            move || {
                let keystore2 = get_keystore_service();
                let sec_level =
                    keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

                assert_eq!(
                    Ok(()),
                    key_generations::map_ks_error(load_grant_key_and_perform_sign_operation(
                        &keystore2,
                        &sec_level,
                        grant_key1_nspace
                    ))
                );

                // Delete the granted key.
                keystore2
                    .deleteKey(&KeyDescriptor {
                        domain: Domain::GRANT,
                        nspace: grant_key1_nspace,
                        alias: None,
                        blob: None,
                    })
                    .unwrap();
            },
        )
    };

    // Grantee #2 context
    let grant_key2_nspace = grant_keys.remove(0);
    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(
            GRANTEE_CTX,
            Uid::from_raw(GRANTEE_2_UID),
            Gid::from_raw(GRANTEE_2_GID),
            move || {
                let keystore2 = get_keystore_service();

                let result = key_generations::map_ks_error(keystore2.getKeyEntry(&KeyDescriptor {
                    domain: Domain::GRANT,
                    nspace: grant_key2_nspace,
                    alias: None,
                    blob: None,
                }));
                assert!(result.is_err());
                assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());
            },
        )
    };
}
