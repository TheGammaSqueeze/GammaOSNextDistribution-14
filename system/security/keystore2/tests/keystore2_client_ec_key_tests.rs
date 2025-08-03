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
    Algorithm::Algorithm, Digest::Digest, EcCurve::EcCurve, ErrorCode::ErrorCode,
    KeyPurpose::KeyPurpose, SecurityLevel::SecurityLevel,
};
use android_system_keystore2::aidl::android::system::keystore2::{
    CreateOperationResponse::CreateOperationResponse, Domain::Domain,
    IKeystoreSecurityLevel::IKeystoreSecurityLevel, KeyDescriptor::KeyDescriptor,
    ResponseCode::ResponseCode,
};

use keystore2_test_utils::{
    authorizations, get_keystore_service, key_generations, key_generations::Error, run_as,
};

use crate::keystore2_client_test_utils::{
    delete_app_key, execute_op_run_as_child, perform_sample_sign_operation, BarrierReached,
    ForcedOp, TestOutcome,
};

macro_rules! test_ec_sign_key_op_success {
    ( $test_name:ident, $digest:expr, $ec_curve:expr ) => {
        #[test]
        fn $test_name() {
            perform_ec_sign_key_op_success(stringify!($test_name), $digest, $ec_curve);
        }
    };
}

macro_rules! test_ec_sign_key_op_with_none_or_md5_digest {
    ( $test_name:ident, $digest:expr, $ec_curve:expr ) => {
        #[test]
        fn $test_name() {
            perform_ec_sign_key_op_with_none_or_md5_digest(
                stringify!($test_name),
                $digest,
                $ec_curve,
            );
        }
    };
}

fn create_ec_key_and_operation(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
    digest: Digest,
    ec_curve: EcCurve,
) -> binder::Result<CreateOperationResponse> {
    let key_metadata =
        key_generations::generate_ec_key(sec_level, domain, nspace, alias, ec_curve, digest)?;

    sec_level.createOperation(
        &key_metadata.key,
        &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(digest),
        false,
    )
}

fn perform_ec_sign_key_op_success(alias: &str, digest: Digest, ec_curve: EcCurve) {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let op_response = create_ec_key_and_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        digest,
        ec_curve,
    )
    .unwrap();

    assert!(op_response.iOperation.is_some());
    assert_eq!(
        Ok(()),
        key_generations::map_ks_error(perform_sample_sign_operation(
            &op_response.iOperation.unwrap()
        ))
    );

    delete_app_key(&keystore2, alias).unwrap();
}

fn perform_ec_sign_key_op_with_none_or_md5_digest(alias: &str, digest: Digest, ec_curve: EcCurve) {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    match key_generations::map_ks_error(create_ec_key_and_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        digest,
        ec_curve,
    )) {
        Ok(op_response) => {
            assert!(op_response.iOperation.is_some());
            assert_eq!(
                Ok(()),
                key_generations::map_ks_error(perform_sample_sign_operation(
                    &op_response.iOperation.unwrap()
                ))
            );
        }
        Err(e) => {
            assert_eq!(e, Error::Km(ErrorCode::UNSUPPORTED_DIGEST));
            assert!(digest == Digest::NONE || digest == Digest::MD5);
        }
    }

    delete_app_key(&keystore2, alias).unwrap();
}

// Below macros generate tests for generating EC keys with curves EcCurve::P_224, EcCurve::P_256,
// EcCurve::P_384, EcCurve::P_521 and various digest modes. Tests tries to create operations using
// the generated keys. Operations with digest modes `SHA1, SHA-2 224, SHA-2 256, SHA-2 384 and
// SHA-2 512` should be created  successfully. Creation of operations with digest modes NONE and
// MD5 should fail with an error code `UNSUPPORTED_DIGEST`.
test_ec_sign_key_op_with_none_or_md5_digest!(
    sign_ec_key_op_none_ec_p224,
    Digest::NONE,
    EcCurve::P_224
);
test_ec_sign_key_op_with_none_or_md5_digest!(
    sign_ec_key_op_md5_ec_p224,
    Digest::MD5,
    EcCurve::P_224
);
test_ec_sign_key_op_success!(sign_ec_key_op_sha1_ec_p224, Digest::SHA1, EcCurve::P_224);
test_ec_sign_key_op_success!(sign_ec_key_op_sha224_ec_p224, Digest::SHA_2_224, EcCurve::P_224);
test_ec_sign_key_op_success!(sign_ec_key_op_sha256_ec_p224, Digest::SHA_2_256, EcCurve::P_224);
test_ec_sign_key_op_success!(sign_ec_key_op_sha384_ec_p224, Digest::SHA_2_384, EcCurve::P_224);
test_ec_sign_key_op_success!(sign_ec_key_op_sha512_ec_p224, Digest::SHA_2_512, EcCurve::P_224);
test_ec_sign_key_op_with_none_or_md5_digest!(
    sign_ec_key_op_none_ec_p256,
    Digest::NONE,
    EcCurve::P_256
);
test_ec_sign_key_op_with_none_or_md5_digest!(
    sign_ec_key_op_md5_ec_p256,
    Digest::MD5,
    EcCurve::P_256
);
test_ec_sign_key_op_success!(sign_ec_key_op_sha1_ec_p256, Digest::SHA1, EcCurve::P_256);
test_ec_sign_key_op_success!(sign_ec_key_op_sha224_ec_p256, Digest::SHA_2_224, EcCurve::P_256);
test_ec_sign_key_op_success!(sign_ec_key_op_sha256_ec_p256, Digest::SHA_2_256, EcCurve::P_256);
test_ec_sign_key_op_success!(sign_ec_key_op_sha384_ec_p256, Digest::SHA_2_384, EcCurve::P_256);
test_ec_sign_key_op_success!(sign_ec_key_op_sha512_ec_p256, Digest::SHA_2_512, EcCurve::P_256);
test_ec_sign_key_op_with_none_or_md5_digest!(
    sign_ec_key_op_none_ec_p384,
    Digest::NONE,
    EcCurve::P_384
);
test_ec_sign_key_op_with_none_or_md5_digest!(
    sign_ec_key_op_md5_ec_p384,
    Digest::MD5,
    EcCurve::P_384
);
test_ec_sign_key_op_success!(sign_ec_key_op_sha1_ec_p384, Digest::SHA1, EcCurve::P_384);
test_ec_sign_key_op_success!(sign_ec_key_op_sha224_ec_p384, Digest::SHA_2_224, EcCurve::P_384);
test_ec_sign_key_op_success!(sign_ec_key_op_sha256_ec_p384, Digest::SHA_2_256, EcCurve::P_384);
test_ec_sign_key_op_success!(sign_ec_key_op_sha384_ec_p384, Digest::SHA_2_384, EcCurve::P_384);
test_ec_sign_key_op_success!(sign_ec_key_op_sha512_ec_p384, Digest::SHA_2_512, EcCurve::P_384);
test_ec_sign_key_op_with_none_or_md5_digest!(
    sign_ec_key_op_none_ec_p521,
    Digest::NONE,
    EcCurve::P_521
);
test_ec_sign_key_op_with_none_or_md5_digest!(
    sign_ec_key_op_md5_ec_p521,
    Digest::MD5,
    EcCurve::P_521
);
test_ec_sign_key_op_success!(sign_ec_key_op_sha1_ec_p521, Digest::SHA1, EcCurve::P_521);
test_ec_sign_key_op_success!(sign_ec_key_op_sha224_ec_p521, Digest::SHA_2_224, EcCurve::P_521);
test_ec_sign_key_op_success!(sign_ec_key_op_sha256_ec_p521, Digest::SHA_2_256, EcCurve::P_521);
test_ec_sign_key_op_success!(sign_ec_key_op_sha384_ec_p521, Digest::SHA_2_384, EcCurve::P_521);
test_ec_sign_key_op_success!(sign_ec_key_op_sha512_ec_p521, Digest::SHA_2_512, EcCurve::P_521);

/// This test will try to load the key with Domain::BLOB.
/// INVALID_ARGUMENT error is expected.
#[test]
fn keystore2_get_key_entry_blob_fail() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    // Generate a key with domain as BLOB.
    let key_metadata = key_generations::generate_ec_p256_signing_key(
        &sec_level,
        Domain::BLOB,
        key_generations::SELINUX_SHELL_NAMESPACE,
        None,
        None,
    )
    .unwrap();

    // Try to load the key using above generated KeyDescriptor.
    let result = key_generations::map_ks_error(keystore2.getKeyEntry(&key_metadata.key));
    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::INVALID_ARGUMENT), result.unwrap_err());

    // Delete the generated key blob.
    sec_level.deleteKey(&key_metadata.key).unwrap();
}

/// Try to generate a key with invalid Domain. `INVALID_ARGUMENT` error response is expected.
#[test]
fn keystore2_generate_key_invalid_domain() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = format!("ks_invalid_test_key_{}", getuid());

    let result = key_generations::map_ks_error(key_generations::generate_ec_key(
        &sec_level,
        Domain(99), // Invalid domain.
        key_generations::SELINUX_SHELL_NAMESPACE,
        Some(alias),
        EcCurve::P_256,
        Digest::SHA_2_256,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Rc(ResponseCode::INVALID_ARGUMENT), result.unwrap_err());
}

/// Try to generate a EC key without providing the curve.
/// `UNSUPPORTED_EC_CURVE or UNSUPPORTED_KEY_SIZE` error response is expected.
#[test]
fn keystore2_generate_ec_key_missing_curve() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = format!("ks_ec_no_curve_test_key_{}", getuid());

    // Don't provide EC curve.
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256);

    let result = key_generations::map_ks_error(sec_level.generateKey(
        &KeyDescriptor {
            domain: Domain::SELINUX,
            nspace: key_generations::SELINUX_SHELL_NAMESPACE,
            alias: Some(alias),
            blob: None,
        },
        None,
        &gen_params,
        0,
        b"entropy",
    ));
    assert!(result.is_err());
    let err = result.unwrap_err();
    assert!(matches!(
        err,
        Error::Km(ErrorCode::UNSUPPORTED_EC_CURVE) | Error::Km(ErrorCode::UNSUPPORTED_KEY_SIZE)
    ));
}

/// Try to generate a EC key with curve `CURVE_25519` having `SIGN and AGREE_KEY` purposes.
/// `INCOMPATIBLE_PURPOSE` error response is expected.
#[test]
fn keystore2_generate_ec_key_25519_multi_purpose() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = format!("ks_ec_no_curve_test_key_{}", getuid());

    // Specify `SIGN and AGREE_KEY` purposes.
    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::EC)
        .ec_curve(EcCurve::CURVE_25519)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::AGREE_KEY)
        .digest(Digest::SHA_2_256);

    let result = key_generations::map_ks_error(sec_level.generateKey(
        &KeyDescriptor {
            domain: Domain::SELINUX,
            nspace: key_generations::SELINUX_SHELL_NAMESPACE,
            alias: Some(alias),
            blob: None,
        },
        None,
        &gen_params,
        0,
        b"entropy",
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INCOMPATIBLE_PURPOSE), result.unwrap_err());
}

/// Generate EC key with curve `CURVE_25519` and digest mode NONE. Try to create an operation using
/// generated key. `CURVE_25519` key should support `Digest::NONE` digest mode and test should be
/// able to create an operation successfully.
#[test]
fn keystore2_ec_25519_generate_key_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = format!("ks_ec_25519_none_test_key_gen_{}", getuid());
    let key_metadata = key_generations::generate_ec_key(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias),
        EcCurve::CURVE_25519,
        Digest::NONE,
    )
    .unwrap();

    let op_response = sec_level
        .createOperation(
            &key_metadata.key,
            &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(Digest::NONE),
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
}

/// Generate EC keys with curve `CURVE_25519` and digest modes `MD5, SHA1, SHA-2 224, SHA-2 256,
/// SHA-2 384 and SHA-2 512`. Try to create operations using generated keys. `CURVE_25519` keys
/// shouldn't support these digest modes. Test should fail to create operations with an error
/// `UNSUPPORTED_DIGEST`.
#[test]
fn keystore2_ec_25519_generate_key_fail() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let digests = [
        Digest::MD5,
        Digest::SHA1,
        Digest::SHA_2_224,
        Digest::SHA_2_256,
        Digest::SHA_2_384,
        Digest::SHA_2_512,
    ];

    for digest in digests {
        let alias = format!("ks_ec_25519_test_key_gen_{}{}", getuid(), digest.0);
        let key_metadata = key_generations::generate_ec_key(
            &sec_level,
            Domain::APP,
            -1,
            Some(alias.to_string()),
            EcCurve::CURVE_25519,
            digest,
        )
        .unwrap();

        let result = key_generations::map_ks_error(sec_level.createOperation(
            &key_metadata.key,
            &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(digest),
            false,
        ));
        assert!(result.is_err());
        assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_DIGEST), result.unwrap_err());
    }
}

/// Generate a EC key with `SHA_2_256` digest mode. Try to create an operation with digest mode
/// other than `SHA_2_256`. Creation of an operation with generated key should fail with
/// `INCOMPATIBLE_DIGEST` error as there is a mismatch of digest mode in key authorizations.
#[test]
fn keystore2_create_op_with_incompatible_key_digest() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = "ks_ec_test_incomp_key_digest";
    let key_metadata = key_generations::generate_ec_key(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        EcCurve::P_256,
        Digest::SHA_2_256,
    )
    .unwrap();

    let digests =
        [Digest::NONE, Digest::SHA1, Digest::SHA_2_224, Digest::SHA_2_384, Digest::SHA_2_512];

    for digest in digests {
        let result = key_generations::map_ks_error(sec_level.createOperation(
            &key_metadata.key,
            &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(digest),
            false,
        ));
        assert!(result.is_err());
        assert_eq!(Error::Km(ErrorCode::INCOMPATIBLE_DIGEST), result.unwrap_err());
    }
}

/// Generate a key in client#1 and try to use it in other client#2.
/// Client#2 should fail to load the key as the it doesn't own the client#1 generated key.
#[test]
fn keystore2_key_owner_validation() {
    static TARGET_CTX: &str = "u:r:untrusted_app:s0:c91,c256,c10,c20";
    const USER_ID: u32 = 99;
    const APPLICATION_ID_1: u32 = 10601;

    let uid1 = USER_ID * AID_USER_OFFSET + APPLICATION_ID_1;
    let gid1 = USER_ID * AID_USER_OFFSET + APPLICATION_ID_1;
    let alias = "ks_owner_check_test_key";

    // Client#1: Generate a key and create an operation using generated key.
    // Wait until the parent notifies to continue. Once the parent notifies, this operation
    // is expected to be completed successfully.
    // SAFETY: The test is run in a separate process with no other threads.
    let mut child_handle = unsafe {
        execute_op_run_as_child(
            TARGET_CTX,
            Domain::APP,
            -1,
            Some(alias.to_string()),
            Uid::from_raw(uid1),
            Gid::from_raw(gid1),
            ForcedOp(false),
        )
    };

    // Wait until (client#1) child process notifies us to continue, so that there will be a key
    // generated by client#1.
    child_handle.recv();

    // Client#2: This child will try to load the key generated by client#1.
    const APPLICATION_ID_2: u32 = 10602;
    let uid2 = USER_ID * AID_USER_OFFSET + APPLICATION_ID_2;
    let gid2 = USER_ID * AID_USER_OFFSET + APPLICATION_ID_2;
    // SAFETY: The test is run in a separate process with no other threads.
    unsafe {
        run_as::run_as(TARGET_CTX, Uid::from_raw(uid2), Gid::from_raw(gid2), move || {
            let keystore2_inst = get_keystore_service();
            let result =
                key_generations::map_ks_error(keystore2_inst.getKeyEntry(&KeyDescriptor {
                    domain: Domain::APP,
                    nspace: -1,
                    alias: Some(alias.to_string()),
                    blob: None,
                }));
            assert!(result.is_err());
            assert_eq!(Error::Rc(ResponseCode::KEY_NOT_FOUND), result.unwrap_err());
        });
    };

    // Notify the child process (client#1) to resume and finish.
    child_handle.send(&BarrierReached {});
    assert!(
        (child_handle.get_result() == TestOutcome::Ok),
        "Client#1 failed to complete the operation."
    );
}

/// Generate EC key with BLOB as domain. Generated key should be returned to caller as key blob.
/// Verify that `blob` field in the `KeyDescriptor` is not empty and should have the key blob.
/// Try to use this key for performing a sample operation and the operation should complete
/// successfully.
#[test]
fn keystore2_generate_key_with_blob_domain() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let key_metadata = key_generations::generate_ec_key(
        &sec_level,
        Domain::BLOB,
        key_generations::SELINUX_SHELL_NAMESPACE,
        None,
        EcCurve::P_256,
        Digest::SHA_2_256,
    )
    .unwrap();

    assert!(key_metadata.certificate.is_some());
    assert!(key_metadata.certificateChain.is_none());

    // Must have the key blob.
    assert!(key_metadata.key.blob.is_some());

    let op_response = key_generations::map_ks_error(sec_level.createOperation(
        &key_metadata.key,
        &authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN).digest(Digest::SHA_2_256),
        false,
    ))
    .unwrap();
    assert!(op_response.iOperation.is_some());
    assert_eq!(
        Ok(()),
        key_generations::map_ks_error(perform_sample_sign_operation(
            &op_response.iOperation.unwrap()
        ))
    );

    // Delete the generated key blob.
    sec_level.deleteKey(&key_metadata.key).unwrap();
}
