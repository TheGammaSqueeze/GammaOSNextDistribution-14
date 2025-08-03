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
    ErrorCode::ErrorCode, SecurityLevel::SecurityLevel,
};
use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, KeyDescriptor::KeyDescriptor, KeyPermission::KeyPermission,
    ResponseCode::ResponseCode,
};

use keystore2_test_utils::{get_keystore_service, key_generations, key_generations::Error, run_as};

/// Generate a key and update its public certificate and certificate chain. Test should be able to
/// load the key and able to verify whether its certificate and cert-chain are updated successfully.
#[test]
fn keystore2_update_subcomponent_success() {
    let alias = "update_subcomponent_success_key";

    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let key_metadata = key_generations::generate_ec_p256_signing_key(
        &sec_level,
        Domain::SELINUX,
        key_generations::SELINUX_SHELL_NAMESPACE,
        Some(alias.to_string()),
        None,
    )
    .unwrap();

    let other_cert: [u8; 32] = [123; 32];
    let other_cert_chain: [u8; 32] = [12; 32];

    keystore2
        .updateSubcomponent(&key_metadata.key, Some(&other_cert), Some(&other_cert_chain))
        .expect("updateSubcomponent should have succeeded.");

    let key_entry_response = keystore2.getKeyEntry(&key_metadata.key).unwrap();
    assert_eq!(Some(other_cert.to_vec()), key_entry_response.metadata.certificate);
    assert_eq!(Some(other_cert_chain.to_vec()), key_entry_response.metadata.certificateChain);
}

/// Try to update non-existing asymmetric key public cert and certificate chain. Test should fail
/// to update with error response code `KEY_NOT_FOUND`.
#[test]
fn keystore2_update_subcomponent_fail() {
    let alias = "update_component_failure_key";

    let keystore2 = get_keystore_service();

    let other_cert: [u8; 32] = [123; 32];
    let other_cert_chain: [u8; 32] = [12; 32];

    let result = key_generations::map_ks_error(keystore2.updateSubcomponent(
        &KeyDescriptor {
            domain: Domain::SELINUX,
            nspace: key_generations::SELINUX_SHELL_NAMESPACE,
            alias: Some(alias.to_string()),
            blob: None,
        },
        Some(&other_cert),
        Some(&other_cert_chain),
    ));
    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());
}

/// Try to update non-existing asymmetric key public cert only. Test should fail
/// to update with error response code `KEY_NOT_FOUND`.
#[test]
fn keystore2_update_subcomponent_no_key_entry_cert_fail() {
    let alias = "update_no_key_entry_cert_only_component_fail_key";
    let keystore2 = get_keystore_service();
    let other_cert: [u8; 32] = [123; 32];

    let result = key_generations::map_ks_error(keystore2.updateSubcomponent(
        &KeyDescriptor {
            domain: Domain::APP,
            nspace: -1,
            alias: Some(alias.to_string()),
            blob: None,
        },
        Some(&other_cert),
        None,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());
}

/// Try to update non existing key with the only given certificate-chain, test should succeed
/// in creating a new keystore entry with the given certificate-chain.
#[test]
fn keystore2_update_subcomponent_no_key_entry_cert_chain_success() {
    let alias = "update_no_key_entry_cert_chain_only_component_success";
    let keystore2 = get_keystore_service();
    let cert_entries =
        vec![(Domain::SELINUX, key_generations::SELINUX_SHELL_NAMESPACE), (Domain::APP, -1)];
    let other_cert_chain: [u8; 32] = [12; 32];

    for (domain, nspace) in cert_entries {
        keystore2
            .updateSubcomponent(
                &KeyDescriptor { domain, nspace, alias: Some(alias.to_string()), blob: None },
                None,
                Some(&other_cert_chain),
            )
            .expect("updateSubcomponent should have succeeded.");

        let key_entry_response = keystore2
            .getKeyEntry(&KeyDescriptor {
                domain,
                nspace,
                alias: Some(alias.to_string()),
                blob: None,
            })
            .unwrap();
        assert_eq!(Some(other_cert_chain.to_vec()), key_entry_response.metadata.certificateChain);
        assert!(key_entry_response.metadata.certificate.is_none(), "Unexpected certificate entry");
        assert!(key_entry_response.metadata.authorizations.is_empty(), "Unexpected authorizations");
        assert_eq!(key_entry_response.metadata.keySecurityLevel, SecurityLevel::SOFTWARE);

        keystore2
            .deleteKey(&KeyDescriptor {
                domain,
                nspace,
                alias: Some(alias.to_string()),
                blob: None,
            })
            .unwrap();
    }
}

/// Generate a key and grant it to two users. For one user grant it with only `GET_INFO` access
/// permission and for another user grant it with GET_INFO and UPDATE access permissions. In a
/// grantee context where key is granted with only GET_INFO access permission, try to update
/// key's public certificate and certificate chain. Test should fail to update with error response
/// code `PERMISSION_DENIED` because grantee does not possess UPDATE access permission for the
/// specified key. In a grantee context where key is granted with UPDATE and GET_INFO access
/// permissions, test should be able to update public certificate and cert-chain successfully.
#[test]
fn keystore2_update_subcomponent_fails_permission_denied() {
    static GRANTOR_SU_CTX: &str = "u:r:su:s0";
    static GRANTEE_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";

    const USER_ID_1: u32 = 99;
    const APPLICATION_ID: u32 = 10001;
    static GRANTEE_1_UID: u32 = USER_ID_1 * AID_USER_OFFSET + APPLICATION_ID;
    static GRANTEE_1_GID: u32 = GRANTEE_1_UID;

    const USER_ID_2: u32 = 98;
    static GRANTEE_2_UID: u32 = USER_ID_2 * AID_USER_OFFSET + APPLICATION_ID;
    static GRANTEE_2_GID: u32 = GRANTEE_2_UID;

    // Generate a key and grant it to multiple users with different access permissions.
    // SAFETY: The test is run in a separate process with no other threads.
    let mut granted_keys = unsafe {
        run_as::run_as(GRANTOR_SU_CTX, Uid::from_raw(0), Gid::from_raw(0), || {
            let keystore2 = get_keystore_service();
            let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
            let alias = format!("ks_update_subcompo_test_1_{}", getuid());
            let mut granted_keys = Vec::new();

            let key_metadata = key_generations::generate_ec_p256_signing_key(
                &sec_level,
                Domain::APP,
                -1,
                Some(alias),
                None,
            )
            .unwrap();

            // Grant a key without update permission.
            let access_vector = KeyPermission::GET_INFO.0;
            let granted_key = keystore2
                .grant(&key_metadata.key, GRANTEE_1_UID.try_into().unwrap(), access_vector)
                .unwrap();
            assert_eq!(granted_key.domain, Domain::GRANT);
            granted_keys.push(granted_key.nspace);

            // Grant a key with update permission.
            let access_vector = KeyPermission::GET_INFO.0 | KeyPermission::UPDATE.0;
            let granted_key = keystore2
                .grant(&key_metadata.key, GRANTEE_2_UID.try_into().unwrap(), access_vector)
                .unwrap();
            assert_eq!(granted_key.domain, Domain::GRANT);
            granted_keys.push(granted_key.nspace);

            granted_keys
        })
    };

    // Grantee context, try to update the key public certs, permission denied error is expected.
    let granted_key1_nspace = granted_keys.remove(0);
    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(
            GRANTEE_CTX,
            Uid::from_raw(GRANTEE_1_UID),
            Gid::from_raw(GRANTEE_1_GID),
            move || {
                let keystore2 = get_keystore_service();

                let other_cert: [u8; 32] = [123; 32];
                let other_cert_chain: [u8; 32] = [12; 32];

                let result = key_generations::map_ks_error(keystore2.updateSubcomponent(
                    &KeyDescriptor {
                        domain: Domain::GRANT,
                        nspace: granted_key1_nspace,
                        alias: None,
                        blob: None,
                    },
                    Some(&other_cert),
                    Some(&other_cert_chain),
                ));
                assert!(result.is_err());
                assert_eq!(Error::Rc(ResponseCode::PERMISSION_DENIED), result.unwrap_err());
            },
        )
    };

    // Grantee context, update granted key public certs. Update should happen successfully.
    let granted_key2_nspace = granted_keys.remove(0);
    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(
            GRANTEE_CTX,
            Uid::from_raw(GRANTEE_2_UID),
            Gid::from_raw(GRANTEE_2_GID),
            move || {
                let keystore2 = get_keystore_service();

                let other_cert: [u8; 32] = [124; 32];
                let other_cert_chain: [u8; 32] = [13; 32];

                keystore2
                    .updateSubcomponent(
                        &KeyDescriptor {
                            domain: Domain::GRANT,
                            nspace: granted_key2_nspace,
                            alias: None,
                            blob: None,
                        },
                        Some(&other_cert),
                        Some(&other_cert_chain),
                    )
                    .expect("updateSubcomponent should have succeeded.");

                let key_entry_response = keystore2
                    .getKeyEntry(&KeyDescriptor {
                        domain: Domain::GRANT,
                        nspace: granted_key2_nspace,
                        alias: None,
                        blob: None,
                    })
                    .unwrap();
                assert_eq!(Some(other_cert.to_vec()), key_entry_response.metadata.certificate);
                assert_eq!(
                    Some(other_cert_chain.to_vec()),
                    key_entry_response.metadata.certificateChain
                );
            },
        )
    };
}

#[test]
fn keystore2_get_security_level_success() {
    let keystore2 = get_keystore_service();
    assert!(
        keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).is_ok(),
        "getSecurityLevel with SecurityLevel::TRUSTED_ENVIRONMENT should have succeeded."
    );
}

#[test]
fn keystore2_get_security_level_failure() {
    let keystore2 = get_keystore_service();
    let result = key_generations::map_ks_error(keystore2.getSecurityLevel(SecurityLevel::SOFTWARE));

    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::HARDWARE_TYPE_UNAVAILABLE), result.unwrap_err());
}
