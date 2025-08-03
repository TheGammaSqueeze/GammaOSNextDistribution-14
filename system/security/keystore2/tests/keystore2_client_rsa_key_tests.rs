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
    Digest::Digest, ErrorCode::ErrorCode, KeyPurpose::KeyPurpose, PaddingMode::PaddingMode,
    SecurityLevel::SecurityLevel,
};
use android_system_keystore2::aidl::android::system::keystore2::{
    CreateOperationResponse::CreateOperationResponse, Domain::Domain,
    IKeystoreSecurityLevel::IKeystoreSecurityLevel,
};

use keystore2_test_utils::{
    authorizations, get_keystore_service, key_generations, key_generations::Error,
};

use crate::keystore2_client_test_utils::{delete_app_key, perform_sample_sign_operation, ForcedOp};

/// This macro is used for creating signing key operation tests using digests and paddings
/// for various key sizes.
macro_rules! test_rsa_sign_key_op {
    ( $test_name:ident, $digest:expr, $key_size:expr, $padding:expr ) => {
        #[test]
        fn $test_name() {
            perform_rsa_sign_key_op_success($digest, $key_size, stringify!($test_name), $padding);
        }
    };

    ( $test_name:ident, $digest:expr, $padding:expr ) => {
        #[test]
        fn $test_name() {
            perform_rsa_sign_key_op_failure($digest, stringify!($test_name), $padding);
        }
    };
}

/// This macro is used for creating encrypt/decrypt key operation tests using digests, mgf-digests
/// and paddings for various key sizes.
macro_rules! test_rsa_encrypt_key_op {
    ( $test_name:ident, $digest:expr, $key_size:expr, $padding:expr ) => {
        #[test]
        fn $test_name() {
            create_rsa_encrypt_decrypt_key_op_success(
                $digest,
                $key_size,
                stringify!($test_name),
                $padding,
                None,
            );
        }
    };

    ( $test_name:ident, $digest:expr, $key_size:expr, $padding:expr, $mgf_digest:expr ) => {
        #[test]
        fn $test_name() {
            create_rsa_encrypt_decrypt_key_op_success(
                $digest,
                $key_size,
                stringify!($test_name),
                $padding,
                $mgf_digest,
            );
        }
    };
}

/// Generate a RSA key and create an operation using the generated key.
fn create_rsa_key_and_operation(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
    key_params: &key_generations::KeyParams,
    op_purpose: KeyPurpose,
    forced_op: ForcedOp,
) -> binder::Result<CreateOperationResponse> {
    let key_metadata =
        key_generations::generate_rsa_key(sec_level, domain, nspace, alias, key_params, None)?;

    let mut op_params = authorizations::AuthSetBuilder::new().purpose(op_purpose);

    if let Some(value) = key_params.digest {
        op_params = op_params.digest(value)
    }
    if let Some(value) = key_params.padding {
        op_params = op_params.padding_mode(value);
    }
    if let Some(value) = key_params.mgf_digest {
        op_params = op_params.mgf_digest(value);
    }
    if let Some(value) = key_params.block_mode {
        op_params = op_params.block_mode(value)
    }

    sec_level.createOperation(&key_metadata.key, &op_params, forced_op.0)
}

/// Generate RSA signing key with given parameters and perform signing operation.
fn perform_rsa_sign_key_op_success(
    digest: Digest,
    key_size: i32,
    alias: &str,
    padding: PaddingMode,
) {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let op_response = create_rsa_key_and_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        &key_generations::KeyParams {
            key_size,
            purpose: vec![KeyPurpose::SIGN, KeyPurpose::VERIFY],
            padding: Some(padding),
            digest: Some(digest),
            mgf_digest: None,
            block_mode: None,
            att_challenge: None,
        },
        KeyPurpose::SIGN,
        ForcedOp(false),
    )
    .expect("Failed to create an operation.");

    assert!(op_response.iOperation.is_some());
    assert_eq!(
        Ok(()),
        key_generations::map_ks_error(perform_sample_sign_operation(
            &op_response.iOperation.unwrap()
        ))
    );

    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate RSA signing key with given parameters and try to perform signing operation.
/// Error `INCOMPATIBLE_DIGEST | UNKNOWN_ERROR` is expected while creating an opearation.
fn perform_rsa_sign_key_op_failure(digest: Digest, alias: &str, padding: PaddingMode) {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let result = key_generations::map_ks_error(create_rsa_key_and_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        &key_generations::KeyParams {
            key_size: 2048,
            purpose: vec![KeyPurpose::SIGN, KeyPurpose::VERIFY],
            padding: Some(padding),
            digest: Some(digest),
            mgf_digest: None,
            block_mode: None,
            att_challenge: None,
        },
        KeyPurpose::SIGN,
        ForcedOp(false),
    ));
    assert!(result.is_err());

    let e = result.unwrap_err();
    assert!(
        e == Error::Km(ErrorCode::UNKNOWN_ERROR) || e == Error::Km(ErrorCode::INCOMPATIBLE_DIGEST)
    );

    delete_app_key(&keystore2, alias).unwrap();
}

/// Generate RSA encrypt/decrypt key with given parameters and perform decrypt operation.
fn create_rsa_encrypt_decrypt_key_op_success(
    digest: Option<Digest>,
    key_size: i32,
    alias: &str,
    padding: PaddingMode,
    mgf_digest: Option<Digest>,
) {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let result = create_rsa_key_and_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        &key_generations::KeyParams {
            key_size,
            purpose: vec![KeyPurpose::ENCRYPT, KeyPurpose::DECRYPT],
            padding: Some(padding),
            digest,
            mgf_digest,
            block_mode: None,
            att_challenge: None,
        },
        KeyPurpose::DECRYPT,
        ForcedOp(false),
    );

    assert!(result.is_ok());

    delete_app_key(&keystore2, alias).unwrap();
}

// Below macros generate tests for generating RSA signing keys with -
//     Padding mode: RSA_PKCS1_1_5_SIGN
//     Digest modes: `NONE, MD5, SHA1, SHA-2 224, SHA-2 256, SHA-2 384 and SHA-2 512`
// and create operations with generated keys. Tests should create operations successfully.
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_none_2048,
    Digest::NONE,
    2048,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_md5_2048,
    Digest::MD5,
    2048,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha1_2048,
    Digest::SHA1,
    2048,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha224_2048,
    Digest::SHA_2_224,
    2048,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha256_2048,
    Digest::SHA_2_256,
    2048,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha384_2048,
    Digest::SHA_2_384,
    2048,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha512_2048,
    Digest::SHA_2_512,
    2048,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_none_3072,
    Digest::NONE,
    3072,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_md5_3072,
    Digest::MD5,
    3072,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha1_3072,
    Digest::SHA1,
    3072,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha224_3072,
    Digest::SHA_2_224,
    3072,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha256_3072,
    Digest::SHA_2_256,
    3072,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha384_3072,
    Digest::SHA_2_384,
    3072,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha512_3072,
    Digest::SHA_2_512,
    3072,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_none_4096,
    Digest::NONE,
    4096,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_md5_4096,
    Digest::MD5,
    4096,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha1_4096,
    Digest::SHA1,
    4096,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha224_4096,
    Digest::SHA_2_224,
    4096,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha256_4096,
    Digest::SHA_2_256,
    4096,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha384_4096,
    Digest::SHA_2_384,
    4096,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);
test_rsa_sign_key_op!(
    sign_key_pkcs1_1_5_sha512_4096,
    Digest::SHA_2_512,
    4096,
    PaddingMode::RSA_PKCS1_1_5_SIGN
);

// Below macros generate tests for generating RSA signing keys with -
//     Padding mode: RSA_PSS
//     Digest modes: `MD5, SHA1, SHA-2 224, SHA-2 256, SHA-2 384 and SHA-2 512`
// and create operations with generated keys. Tests should create operations
// successfully.
test_rsa_sign_key_op!(sign_key_pss_md5_2048, Digest::MD5, 2048, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha1_2048, Digest::SHA1, 2048, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha224_2048, Digest::SHA_2_224, 2048, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha256_2048, Digest::SHA_2_256, 2048, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha384_2048, Digest::SHA_2_384, 2048, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha512_2048, Digest::SHA_2_512, 2048, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_md5_3072, Digest::MD5, 3072, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha1_3072, Digest::SHA1, 3072, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha224_3072, Digest::SHA_2_224, 3072, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha256_3072, Digest::SHA_2_256, 3072, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha384_3072, Digest::SHA_2_384, 3072, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha512_3072, Digest::SHA_2_512, 3072, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_md5_4096, Digest::MD5, 4096, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha1_4096, Digest::SHA1, 4096, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha224_4096, Digest::SHA_2_224, 4096, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha256_4096, Digest::SHA_2_256, 4096, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha384_4096, Digest::SHA_2_384, 4096, PaddingMode::RSA_PSS);
test_rsa_sign_key_op!(sign_key_pss_sha512_4096, Digest::SHA_2_512, 4096, PaddingMode::RSA_PSS);

// Below macros generate tests for generating RSA signing keys with -
//     Padding mode: `NONE`
//     Digest mode `NONE`
// and try to create operations with generated keys. Tests should create operations
// successfully.
test_rsa_sign_key_op!(sign_key_none_none_2048, Digest::NONE, 2048, PaddingMode::NONE);
test_rsa_sign_key_op!(sign_key_none_none_3072, Digest::NONE, 3072, PaddingMode::NONE);
test_rsa_sign_key_op!(sign_key_none_none_4096, Digest::NONE, 4096, PaddingMode::NONE);

// Below macros generate tests for generating RSA signing keys with -
//     Padding mode: `NONE`
//     Digest modes: `MD5, SHA1, SHA-2 224, SHA-2 256, SHA-2 384 and SHA-2 512`
// and create operations with generated keys. Tests should fail to create operations with
// an error code `UNKNOWN_ERROR | INCOMPATIBLE_DIGEST`.
test_rsa_sign_key_op!(sign_key_none_md5_2048, Digest::MD5, PaddingMode::NONE);
test_rsa_sign_key_op!(sign_key_none_sha1_2048, Digest::SHA1, PaddingMode::NONE);
test_rsa_sign_key_op!(sign_key_none_sha224_2048, Digest::SHA_2_224, PaddingMode::NONE);
test_rsa_sign_key_op!(sign_key_none_sha256_2048, Digest::SHA_2_256, PaddingMode::NONE);
test_rsa_sign_key_op!(sign_key_none_sha384_2048, Digest::SHA_2_384, PaddingMode::NONE);
test_rsa_sign_key_op!(sign_key_none_sha512_2048, Digest::SHA_2_512, PaddingMode::NONE);

// Below macros generate tests for generating RSA encryption keys with various digest mode
// and padding mode combinations.
//     Digest modes: `MD5, SHA1, SHA-2 224, SHA-2 256, SHA-2 384 and SHA-2 512`
//     Padding modes: `NONE, RSA_PKCS1_1_5_ENCRYPT`
// and try to create operations using generated keys, tests should create operations successfully.
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_none_2048,
    Some(Digest::NONE),
    2048,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_md5_2048,
    Some(Digest::MD5),
    2048,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha1_2048,
    Some(Digest::SHA1),
    2048,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha224_2048,
    Some(Digest::SHA_2_224),
    2048,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha256_2048,
    Some(Digest::SHA_2_256),
    2048,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha384_2048,
    Some(Digest::SHA_2_384),
    2048,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha512_2048,
    Some(Digest::SHA_2_512),
    2048,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_none_3072,
    Some(Digest::NONE),
    3072,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_md5_3072,
    Some(Digest::MD5),
    3072,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha1_3072,
    Some(Digest::SHA1),
    3072,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha224_3072,
    Some(Digest::SHA_2_224),
    3072,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha256_3072,
    Some(Digest::SHA_2_256),
    3072,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha384_3072,
    Some(Digest::SHA_2_384),
    3072,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha512_3072,
    Some(Digest::SHA_2_512),
    3072,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_none_4096,
    Some(Digest::NONE),
    4096,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_md5_4096,
    Some(Digest::MD5),
    4096,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha1_4096,
    Some(Digest::SHA1),
    4096,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha224_4096,
    Some(Digest::SHA_2_224),
    4096,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha256_4096,
    Some(Digest::SHA_2_256),
    4096,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha384_4096,
    Some(Digest::SHA_2_384),
    4096,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_sha512_4096,
    Some(Digest::SHA_2_512),
    4096,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT
);
test_rsa_encrypt_key_op!(encrypt_key_none_none_2048, Some(Digest::NONE), 2048, PaddingMode::NONE);
test_rsa_encrypt_key_op!(encrypt_key_none_md5_2048, Some(Digest::MD5), 2048, PaddingMode::NONE);
test_rsa_encrypt_key_op!(encrypt_key_none_sha1_2048, Some(Digest::SHA1), 2048, PaddingMode::NONE);
test_rsa_encrypt_key_op!(
    encrypt_key_none_sha224_2048,
    Some(Digest::SHA_2_224),
    2048,
    PaddingMode::NONE
);
test_rsa_encrypt_key_op!(
    encrypt_key_none_sha256_2048,
    Some(Digest::SHA_2_256),
    2048,
    PaddingMode::NONE
);
test_rsa_encrypt_key_op!(
    encrypt_key_none_sha384_2048,
    Some(Digest::SHA_2_384),
    2048,
    PaddingMode::NONE
);
test_rsa_encrypt_key_op!(
    encrypt_key_none_sha512_2048,
    Some(Digest::SHA_2_512),
    2048,
    PaddingMode::NONE
);
test_rsa_encrypt_key_op!(encrypt_key_none_none_3072, Some(Digest::NONE), 3072, PaddingMode::NONE);
test_rsa_encrypt_key_op!(encrypt_key_none_md5_3072, Some(Digest::MD5), 3072, PaddingMode::NONE);
test_rsa_encrypt_key_op!(encrypt_key_none_sha1_3072, Some(Digest::SHA1), 3072, PaddingMode::NONE);
test_rsa_encrypt_key_op!(
    encrypt_key_none_sha224_3072,
    Some(Digest::SHA_2_224),
    3072,
    PaddingMode::NONE
);
test_rsa_encrypt_key_op!(
    encrypt_key_none_sha256_3072,
    Some(Digest::SHA_2_256),
    3072,
    PaddingMode::NONE
);
test_rsa_encrypt_key_op!(
    encrypt_key_none_sha384_3072,
    Some(Digest::SHA_2_384),
    3072,
    PaddingMode::NONE
);
test_rsa_encrypt_key_op!(
    encrypt_key_none_sha512_3072,
    Some(Digest::SHA_2_512),
    3072,
    PaddingMode::NONE
);
test_rsa_encrypt_key_op!(encrypt_key_none_none_4096, Some(Digest::NONE), 4096, PaddingMode::NONE);
test_rsa_encrypt_key_op!(encrypt_key_none_md5_4096, Some(Digest::MD5), 4096, PaddingMode::NONE);
test_rsa_encrypt_key_op!(encrypt_key_none_sha1_4096, Some(Digest::SHA1), 4096, PaddingMode::NONE);
test_rsa_encrypt_key_op!(
    encrypt_key_none_sha224_4096,
    Some(Digest::SHA_2_224),
    4096,
    PaddingMode::NONE
);
test_rsa_encrypt_key_op!(
    encrypt_key_none_sha256_4096,
    Some(Digest::SHA_2_256),
    4096,
    PaddingMode::NONE
);
test_rsa_encrypt_key_op!(
    encrypt_key_none_sha384_4096,
    Some(Digest::SHA_2_384),
    4096,
    PaddingMode::NONE
);
test_rsa_encrypt_key_op!(
    encrypt_key_none_sha512_4096,
    Some(Digest::SHA_2_512),
    4096,
    PaddingMode::NONE
);

// Below macros generate tests for generating RSA keys with -
//     Padding Mode: `RSA_OAEP`
//     Digest modes: `MD5, SHA1, SHA-2 224, SHA-2 256, SHA-2 384 and SHA-2 512`
//     mgf-digests: `MD5, SHA1, SHA-2 224, SHA-2 256, SHA-2 384 and SHA-2 512`
// and create a decrypt operations using generated keys. Tests should create operations
// successfully.
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_md5_2048,
    Some(Digest::MD5),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha1_2048,
    Some(Digest::MD5),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha224_2048,
    Some(Digest::MD5),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha256_2048,
    Some(Digest::MD5),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha384_2048,
    Some(Digest::MD5),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha512_2048,
    Some(Digest::MD5),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_md5_2048,
    Some(Digest::SHA1),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha1_2048,
    Some(Digest::SHA1),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha224_2048,
    Some(Digest::SHA1),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha256_2048,
    Some(Digest::SHA1),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha384_2048,
    Some(Digest::SHA1),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha512_2048,
    Some(Digest::SHA1),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_md5_2048,
    Some(Digest::SHA_2_224),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha1_2048,
    Some(Digest::SHA_2_224),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha224_2048,
    Some(Digest::SHA_2_224),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha256_2048,
    Some(Digest::SHA_2_224),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha384_2048,
    Some(Digest::SHA_2_224),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha512_2048,
    Some(Digest::SHA_2_224),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_md5_2048,
    Some(Digest::SHA_2_256),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha1_2048,
    Some(Digest::SHA_2_256),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha224_2048,
    Some(Digest::SHA_2_256),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha256_2048,
    Some(Digest::SHA_2_256),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha384_2048,
    Some(Digest::SHA_2_256),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha512_2048,
    Some(Digest::SHA_2_256),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_md5_2048,
    Some(Digest::SHA_2_384),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha1_2048,
    Some(Digest::SHA_2_384),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha224_2048,
    Some(Digest::SHA_2_384),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha256_2048,
    Some(Digest::SHA_2_384),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha384_2048,
    Some(Digest::SHA_2_384),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha512_2048,
    Some(Digest::SHA_2_384),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_md5_2048,
    Some(Digest::SHA_2_512),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha1_2048,
    Some(Digest::SHA_2_512),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha224_2048,
    Some(Digest::SHA_2_512),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha256_2048,
    Some(Digest::SHA_2_512),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha384_2048,
    Some(Digest::SHA_2_512),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha512_2048,
    Some(Digest::SHA_2_512),
    2048,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_md5_3072,
    Some(Digest::MD5),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha1_3072,
    Some(Digest::MD5),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha224_3072,
    Some(Digest::MD5),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha256_3072,
    Some(Digest::MD5),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha384_3072,
    Some(Digest::MD5),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha512_3072,
    Some(Digest::MD5),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_md5_3072,
    Some(Digest::SHA1),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha1_3072,
    Some(Digest::SHA1),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha224_3072,
    Some(Digest::SHA1),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha256_3072,
    Some(Digest::SHA1),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha384_3072,
    Some(Digest::SHA1),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha512_3072,
    Some(Digest::SHA1),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_md5_3072,
    Some(Digest::SHA_2_224),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha1_3072,
    Some(Digest::SHA_2_224),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha224_3072,
    Some(Digest::SHA_2_224),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha256_3072,
    Some(Digest::SHA_2_224),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha384_3072,
    Some(Digest::SHA_2_224),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha512_3072,
    Some(Digest::SHA_2_224),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_md5_3072,
    Some(Digest::SHA_2_256),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha1_3072,
    Some(Digest::SHA_2_256),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha224_3072,
    Some(Digest::SHA_2_256),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha256_3072,
    Some(Digest::SHA_2_256),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha384_3072,
    Some(Digest::SHA_2_256),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha512_3072,
    Some(Digest::SHA_2_256),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_md5_3072,
    Some(Digest::SHA_2_384),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha1_3072,
    Some(Digest::SHA_2_384),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha224_3072,
    Some(Digest::SHA_2_384),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha256_3072,
    Some(Digest::SHA_2_384),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha384_3072,
    Some(Digest::SHA_2_384),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha512_3072,
    Some(Digest::SHA_2_384),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_md5_3072,
    Some(Digest::SHA_2_512),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha1_3072,
    Some(Digest::SHA_2_512),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha224_3072,
    Some(Digest::SHA_2_512),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha256_3072,
    Some(Digest::SHA_2_512),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha384_3072,
    Some(Digest::SHA_2_512),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha512_3072,
    Some(Digest::SHA_2_512),
    3072,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_md5_4096,
    Some(Digest::MD5),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha1_4096,
    Some(Digest::MD5),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha224_4096,
    Some(Digest::MD5),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha256_4096,
    Some(Digest::MD5),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha384_4096,
    Some(Digest::MD5),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_sha512_4096,
    Some(Digest::MD5),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_md5_4096,
    Some(Digest::SHA1),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha1_4096,
    Some(Digest::SHA1),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha224_4096,
    Some(Digest::SHA1),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha256_4096,
    Some(Digest::SHA1),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha384_4096,
    Some(Digest::SHA1),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_sha512_4096,
    Some(Digest::SHA1),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_md5_4096,
    Some(Digest::SHA_2_224),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha1_4096,
    Some(Digest::SHA_2_224),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha224_4096,
    Some(Digest::SHA_2_224),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha256_4096,
    Some(Digest::SHA_2_224),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha384_4096,
    Some(Digest::SHA_2_224),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_sha512_4096,
    Some(Digest::SHA_2_224),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_md5_4096,
    Some(Digest::SHA_2_256),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha1_4096,
    Some(Digest::SHA_2_256),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha224_4096,
    Some(Digest::SHA_2_256),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha256_4096,
    Some(Digest::SHA_2_256),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha384_4096,
    Some(Digest::SHA_2_256),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_sha512_4096,
    Some(Digest::SHA_2_256),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_md5_4096,
    Some(Digest::SHA_2_384),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha1_4096,
    Some(Digest::SHA_2_384),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha224_4096,
    Some(Digest::SHA_2_384),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha256_4096,
    Some(Digest::SHA_2_384),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha384_4096,
    Some(Digest::SHA_2_384),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_sha512_4096,
    Some(Digest::SHA_2_384),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_md5_4096,
    Some(Digest::SHA_2_512),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::MD5)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha1_4096,
    Some(Digest::SHA_2_512),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA1)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha224_4096,
    Some(Digest::SHA_2_512),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_224)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha256_4096,
    Some(Digest::SHA_2_512),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_256)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha384_4096,
    Some(Digest::SHA_2_512),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_384)
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_sha512_4096,
    Some(Digest::SHA_2_512),
    4096,
    PaddingMode::RSA_OAEP,
    Some(Digest::SHA_2_512)
);

// Below macros generate tests for generating RSA keys with -
//     Padding mode: `RSA_OAEP`
//     Digest modes: `MD5, SHA1, SHA-2 224, SHA-2 256, SHA-2 384 and SHA-2 512`
// and create a decrypt operations using generated keys. Tests should create operations
// successfully.
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_no_mgf_2048,
    Some(Digest::MD5),
    2048,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_no_mgf_2048,
    Some(Digest::SHA1),
    2048,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_no_mgf_2048,
    Some(Digest::SHA_2_224),
    2048,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_no_mgf_2048,
    Some(Digest::SHA_2_256),
    2048,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_no_mgf_2048,
    Some(Digest::SHA_2_384),
    2048,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_no_mgf_2048,
    Some(Digest::SHA_2_512),
    2048,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_no_mgf_3072,
    Some(Digest::MD5),
    3072,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_no_mgf_3072,
    Some(Digest::SHA1),
    3072,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_no_mgf_3072,
    Some(Digest::SHA_2_224),
    3072,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_no_mgf_3072,
    Some(Digest::SHA_2_256),
    3072,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_no_mgf_3072,
    Some(Digest::SHA_2_384),
    3072,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_no_mgf_3072,
    Some(Digest::SHA_2_512),
    3072,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_md5_no_mgf_4096,
    Some(Digest::MD5),
    4096,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha1_no_mgf_4096,
    Some(Digest::SHA1),
    4096,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha224_no_mgf_4096,
    Some(Digest::SHA_2_224),
    4096,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha256_no_mgf_4096,
    Some(Digest::SHA_2_256),
    4096,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha384_no_mgf_4096,
    Some(Digest::SHA_2_384),
    4096,
    PaddingMode::RSA_OAEP,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_oaep_sha512_no_mgf_4096,
    Some(Digest::SHA_2_512),
    4096,
    PaddingMode::RSA_OAEP,
    None
);

// Below macros generate tests for generating RSA encryption keys with only padding modes.
//     Padding modes: `NONE, RSA_PKCS1_1_5_ENCRYPT`
// and try to create operations using generated keys, tests should create operations
// successfully.
test_rsa_encrypt_key_op!(encrypt_key_none_pad_2048, None, 2048, PaddingMode::NONE, None);
test_rsa_encrypt_key_op!(encrypt_key_none_pad_3072, None, 3072, PaddingMode::NONE, None);
test_rsa_encrypt_key_op!(encrypt_key_none_pad_4096, None, 4096, PaddingMode::NONE, None);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_pad_2048,
    None,
    2048,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_pad_3072,
    None,
    3072,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT,
    None
);
test_rsa_encrypt_key_op!(
    encrypt_key_pkcs1_1_5_pad_4096,
    None,
    4096,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT,
    None
);

/// Generate RSA signing key with -
///     Padding mode: RSA_PSS
///     Digest mode: `NONE`.
/// Try to create an operation with this generated key. Test should fail to create an operation with
/// `INCOMPATIBLE_DIGEST` error code.
#[test]
fn keystore2_rsa_generate_signing_key_padding_pss_fail() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = "ks_rsa_pss_none_key_op_test";
    let result = key_generations::map_ks_error(create_rsa_key_and_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        &key_generations::KeyParams {
            key_size: 2048,
            purpose: vec![KeyPurpose::SIGN, KeyPurpose::VERIFY],
            padding: Some(PaddingMode::RSA_PSS),
            digest: Some(Digest::NONE),
            mgf_digest: None,
            block_mode: None,
            att_challenge: None,
        },
        KeyPurpose::SIGN,
        ForcedOp(false),
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INCOMPATIBLE_DIGEST), result.unwrap_err());
}

/// Generate RSA encryption key with -
///     Digest mode: `NONE`
///     Padding mode: `RSA_OAEP`
/// Try to create an operation using generated key. Test should fail to create an operation
/// with an error code `INCOMPATIBLE_DIGEST`.
#[test]
fn keystore2_rsa_generate_key_with_oaep_padding_fail() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = "ks_rsa_key_oaep_padding_fail_test";
    let result = key_generations::map_ks_error(create_rsa_key_and_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        &key_generations::KeyParams {
            key_size: 2048,
            purpose: vec![KeyPurpose::ENCRYPT, KeyPurpose::DECRYPT],
            padding: Some(PaddingMode::RSA_OAEP),
            digest: Some(Digest::NONE),
            mgf_digest: None,
            block_mode: None,
            att_challenge: None,
        },
        KeyPurpose::DECRYPT,
        ForcedOp(false),
    ));

    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INCOMPATIBLE_DIGEST), result.unwrap_err());
}

/// Generate RSA keys without padding and digest modes. Try to create decrypt operation without
/// digest and padding. Creation of an operation should fail with an error code
/// `UNSUPPORTED_PADDING_MODE`.
#[test]
fn keystore2_rsa_generate_keys() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = "ks_rsa_key_unsupport_padding_test";
    let result = key_generations::map_ks_error(create_rsa_key_and_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        &key_generations::KeyParams {
            key_size: 2048,
            purpose: vec![KeyPurpose::ENCRYPT, KeyPurpose::DECRYPT],
            padding: None,
            digest: None,
            mgf_digest: None,
            block_mode: None,
            att_challenge: None,
        },
        KeyPurpose::DECRYPT,
        ForcedOp(false),
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_PADDING_MODE), result.unwrap_err());
}

/// Generate a RSA encryption key. Try to create a signing operation with it, an error
/// `INCOMPATIBLE_PURPOSE` is expected as the generated key doesn't support sign operation.
#[test]
fn keystore2_rsa_encrypt_key_op_invalid_purpose() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = "ks_rsa_test_key_1";
    let result = key_generations::map_ks_error(create_rsa_key_and_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        &key_generations::KeyParams {
            key_size: 2048,
            purpose: vec![KeyPurpose::ENCRYPT, KeyPurpose::DECRYPT],
            padding: Some(PaddingMode::RSA_PKCS1_1_5_ENCRYPT),
            digest: Some(Digest::SHA_2_256),
            mgf_digest: None,
            block_mode: None,
            att_challenge: None,
        },
        KeyPurpose::SIGN,
        ForcedOp(false),
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INCOMPATIBLE_PURPOSE), result.unwrap_err());
}

/// Generate a RSA signing key. Try to create a decrypt operation with it, an error
/// `INCOMPATIBLE_PURPOSE` is expected as the generated key doesn't support decrypt operation.
#[test]
fn keystore2_rsa_sign_key_op_invalid_purpose() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = "ks_rsa_test_key_2";
    let result = key_generations::map_ks_error(create_rsa_key_and_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        &key_generations::KeyParams {
            key_size: 2048,
            purpose: vec![KeyPurpose::SIGN, KeyPurpose::VERIFY],
            padding: Some(PaddingMode::RSA_PKCS1_1_5_SIGN),
            digest: Some(Digest::SHA_2_256),
            mgf_digest: None,
            block_mode: None,
            att_challenge: None,
        },
        KeyPurpose::DECRYPT,
        ForcedOp(false),
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INCOMPATIBLE_PURPOSE), result.unwrap_err());
}

/// Generate a RSA key with SIGN and AGREE_KEY purposes. Try to perform an operation using the
/// generated key, an error `UNSUPPORTED_PURPOSE` is expected as RSA doesn't support AGREE_KEY.
#[test]
fn keystore2_rsa_key_unsupported_purpose() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = "ks_rsa_key_test_3";
    let result = key_generations::map_ks_error(create_rsa_key_and_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        &key_generations::KeyParams {
            key_size: 2048,
            purpose: vec![KeyPurpose::AGREE_KEY],
            padding: Some(PaddingMode::RSA_PKCS1_1_5_SIGN),
            digest: Some(Digest::SHA_2_256),
            mgf_digest: None,
            block_mode: None,
            att_challenge: None,
        },
        KeyPurpose::AGREE_KEY,
        ForcedOp(false),
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_PURPOSE), result.unwrap_err());
}

/// Generate a RSA encrypt key with padding mode supported for signing. Try to create an operation
/// using generated key, an error `UNSUPPORTED_PADDING_MODE` is expected with unsupported padding
/// mode.
#[test]
fn keystore2_rsa_encrypt_key_unsupported_padding() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let paddings = [PaddingMode::RSA_PKCS1_1_5_SIGN, PaddingMode::RSA_PSS];

    for padding in paddings {
        let alias = format!("ks_rsa_encrypt_key_unsupported_pad_test{}", padding.0);
        let result = key_generations::map_ks_error(create_rsa_key_and_operation(
            &sec_level,
            Domain::APP,
            -1,
            Some(alias.to_string()),
            &key_generations::KeyParams {
                key_size: 2048,
                purpose: vec![KeyPurpose::ENCRYPT, KeyPurpose::DECRYPT],
                padding: Some(padding),
                digest: Some(Digest::SHA_2_256),
                mgf_digest: None,
                block_mode: None,
                att_challenge: None,
            },
            KeyPurpose::DECRYPT,
            ForcedOp(false),
        ));
        assert!(result.is_err());
        assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_PADDING_MODE), result.unwrap_err());
    }
}

/// Generate a RSA signing key with padding mode supported for encryption. Try to create an
/// operation using generated key, an error `UNSUPPORTED_PADDING_MODE` is expected with
/// unsupported padding mode.
#[test]
fn keystore2_rsa_signing_key_unsupported_padding() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let paddings = [PaddingMode::RSA_PKCS1_1_5_ENCRYPT, PaddingMode::RSA_OAEP];

    for padding in paddings {
        let alias = format!("ks_rsa_sign_key_unsupported_pad_test_4_{}", padding.0);
        let result = key_generations::map_ks_error(create_rsa_key_and_operation(
            &sec_level,
            Domain::APP,
            -1,
            Some(alias.to_string()),
            &key_generations::KeyParams {
                key_size: 2048,
                purpose: vec![KeyPurpose::SIGN, KeyPurpose::VERIFY],
                padding: Some(padding),
                digest: Some(Digest::SHA_2_256),
                mgf_digest: None,
                block_mode: None,
                att_challenge: None,
            },
            KeyPurpose::SIGN,
            ForcedOp(false),
        ));
        assert!(result.is_err());
        assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_PADDING_MODE), result.unwrap_err());
    }
}

/// Generate a RSA encryption key. Try to perform encrypt operation using the generated
/// key, an error `UNSUPPORTED_PURPOSE` is expected as encrypt operation is not supported
/// with RSA key.
#[test]
fn keystore2_rsa_key_unsupported_op() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = "ks_rsa_key_test_5";
    let result = key_generations::map_ks_error(create_rsa_key_and_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        &key_generations::KeyParams {
            key_size: 2048,
            purpose: vec![KeyPurpose::ENCRYPT, KeyPurpose::DECRYPT],
            padding: Some(PaddingMode::RSA_PKCS1_1_5_ENCRYPT),
            digest: Some(Digest::SHA_2_256),
            mgf_digest: None,
            block_mode: None,
            att_challenge: None,
        },
        KeyPurpose::ENCRYPT,
        ForcedOp(false),
    ));

    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_PURPOSE), result.unwrap_err());
}

/// Generate a RSA key with encrypt, sign and verify purpose. Try to perform decrypt operation
/// using the generated key, an error `INCOMPATIBLE_PURPOSE` is expected as the key is not
/// generated with decrypt purpose.
#[test]
fn keystore2_rsa_key_missing_purpose() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = "ks_rsa_key_test_6";
    let result = key_generations::map_ks_error(create_rsa_key_and_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        &key_generations::KeyParams {
            key_size: 2048,
            purpose: vec![KeyPurpose::ENCRYPT, KeyPurpose::SIGN, KeyPurpose::VERIFY],
            padding: Some(PaddingMode::RSA_PKCS1_1_5_ENCRYPT),
            digest: Some(Digest::SHA_2_256),
            mgf_digest: None,
            block_mode: None,
            att_challenge: None,
        },
        KeyPurpose::DECRYPT,
        ForcedOp(false),
    ));

    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INCOMPATIBLE_PURPOSE), result.unwrap_err());
}

/// Generate RSA encryption keys with OAEP padding mode and without digest mode. Try to create an
/// operation with generated key, unsupported digest error is expected.
#[test]
fn keystore2_rsa_gen_keys_with_oaep_paddings_without_digest() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = "ks_rsa_key_padding_fail";
    let result = key_generations::map_ks_error(create_rsa_key_and_operation(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        &key_generations::KeyParams {
            key_size: 2048,
            purpose: vec![KeyPurpose::ENCRYPT, KeyPurpose::DECRYPT],
            padding: Some(PaddingMode::RSA_OAEP),
            digest: None,
            mgf_digest: None,
            block_mode: None,
            att_challenge: None,
        },
        KeyPurpose::DECRYPT,
        ForcedOp(false),
    ));

    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_DIGEST), result.unwrap_err());
}

/// Generate RSA keys with unsupported key size, an error `UNSUPPORTED_KEY_SIZE` is expected.
#[test]
fn keystore2_rsa_gen_keys_unsupported_size() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = "ks_rsa_key_padding_fail";
    let result = key_generations::map_ks_error(key_generations::generate_rsa_key(
        &sec_level,
        Domain::APP,
        -1,
        Some(alias.to_string()),
        &key_generations::KeyParams {
            key_size: 5120,
            purpose: vec![KeyPurpose::ENCRYPT, KeyPurpose::SIGN, KeyPurpose::VERIFY],
            padding: Some(PaddingMode::RSA_PKCS1_1_5_ENCRYPT),
            digest: Some(Digest::SHA_2_256),
            mgf_digest: None,
            block_mode: None,
            att_challenge: None,
        },
        None,
    ));

    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_KEY_SIZE), result.unwrap_err());
}
