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
    Algorithm::Algorithm, BlockMode::BlockMode, ErrorCode::ErrorCode, KeyPurpose::KeyPurpose,
    PaddingMode::PaddingMode, SecurityLevel::SecurityLevel,
};

use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, IKeystoreSecurityLevel::IKeystoreSecurityLevel, KeyDescriptor::KeyDescriptor,
};

use keystore2_test_utils::{
    authorizations, get_keystore_service, key_generations, key_generations::Error,
};

use crate::keystore2_client_test_utils::{
    perform_sample_sym_key_decrypt_op, perform_sample_sym_key_encrypt_op, SAMPLE_PLAIN_TEXT,
};

/// Generate a AES key. Create encrypt and decrypt operations using the generated key.
fn create_aes_key_and_operation(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    key_size: i32,
    padding_mode: PaddingMode,
    block_mode: BlockMode,
    mac_len: Option<i32>,
    min_mac_len: Option<i32>,
    nonce: &mut Option<Vec<u8>>,
) -> Result<(), binder::Status> {
    let alias = format!("ks_aes_test_key_{}{}{}", key_size, block_mode.0, padding_mode.0);

    let key_metadata = key_generations::generate_sym_key(
        sec_level,
        Algorithm::AES,
        key_size,
        &alias,
        &padding_mode,
        &block_mode,
        min_mac_len,
    )?;

    let cipher_text = perform_sample_sym_key_encrypt_op(
        sec_level,
        padding_mode,
        block_mode,
        nonce,
        mac_len,
        &key_metadata.key,
    )?;

    assert!(cipher_text.is_some());

    let plain_text = perform_sample_sym_key_decrypt_op(
        sec_level,
        &cipher_text.unwrap(),
        padding_mode,
        block_mode,
        nonce,
        mac_len,
        &key_metadata.key,
    )
    .unwrap();
    assert!(plain_text.is_some());
    assert_eq!(plain_text.unwrap(), SAMPLE_PLAIN_TEXT.to_vec());
    Ok(())
}

/// Generate AES keys with various block modes and paddings.
///  - Block Modes: ECB, CBC
///  - Padding Modes: NONE, PKCS7
/// Test should generate keys and perform operation successfully.
#[test]
fn keystore2_aes_ecb_cbc_generate_key() {
    let keystore2 = get_keystore_service();
    let key_sizes = [128, 256];
    let block_modes = [BlockMode::ECB, BlockMode::CBC];
    let padding_modes = [PaddingMode::PKCS7, PaddingMode::NONE];

    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    for key_size in key_sizes {
        for block_mode in block_modes {
            for padding_mode in padding_modes {
                assert_eq!(
                    Ok(()),
                    create_aes_key_and_operation(
                        &sec_level,
                        key_size,
                        padding_mode,
                        block_mode,
                        None,
                        None,
                        &mut None,
                    )
                );
            }
        }
    }
}

/// Generate AES keys with -
///  - Block Modes: `CTR, GCM`
///  - Padding Modes: `NONE`
/// Test should generate keys and perform operation successfully.
#[test]
fn keystore2_aes_ctr_gcm_generate_key_success() {
    let keystore2 = get_keystore_service();
    let key_sizes = [128, 256];
    let key_params = [(BlockMode::CTR, None, None), (BlockMode::GCM, Some(128), Some(128))];

    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    for key_size in key_sizes {
        for (block_mode, mac_len, min_mac_len) in key_params {
            let result = key_generations::map_ks_error(create_aes_key_and_operation(
                &sec_level,
                key_size,
                PaddingMode::NONE,
                block_mode,
                mac_len,
                min_mac_len,
                &mut None,
            ));

            assert_eq!(Ok(()), result);
        } // End of block mode.
    } // End of key size.
}

/// Generate AES keys with -
///  - Block Modes: `CTR, GCM`
///  - Padding Modes: `PKCS7`
/// Try to create an operation using generated keys, test should fail to create an operation
/// with an error code `INCOMPATIBLE_PADDING_MODE`.
#[test]
fn keystore2_aes_ctr_gcm_generate_key_fails_incompatible() {
    let keystore2 = get_keystore_service();
    let key_sizes = [128, 256];
    let key_params = [(BlockMode::CTR, None, None), (BlockMode::GCM, Some(128), Some(128))];

    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    for key_size in key_sizes {
        for (block_mode, mac_len, min_mac_len) in key_params {
            let result = key_generations::map_ks_error(create_aes_key_and_operation(
                &sec_level,
                key_size,
                PaddingMode::PKCS7,
                block_mode,
                mac_len,
                min_mac_len,
                &mut None,
            ));

            assert!(result.is_err());
            assert_eq!(Error::Km(ErrorCode::INCOMPATIBLE_PADDING_MODE), result.unwrap_err());
        } // End of block mode.
    } // End of key size.
}

/// Try to generate AES key with invalid key size. Test should fail to generate a key with
/// an error code `UNSUPPORTED_KEY_SIZE`.
#[test]
fn keystore2_aes_key_fails_unsupported_key_size() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = "aes_key_test_invalid_1";

    let result = key_generations::map_ks_error(key_generations::generate_sym_key(
        &sec_level,
        Algorithm::AES,
        1024,
        alias,
        &PaddingMode::NONE,
        &BlockMode::ECB,
        None,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_KEY_SIZE), result.unwrap_err());
}

/// Try to generate AES key with GCM block mode without providing `MIN_MAC_LENGTH`.
/// Test should fail to generate a key with an error code `MISSING_MIN_MAC_LENGTH`.
#[test]
fn keystore2_aes_gcm_key_fails_missing_min_mac_len() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = "aes_key_test_invalid_1";

    let result = key_generations::map_ks_error(key_generations::generate_sym_key(
        &sec_level,
        Algorithm::AES,
        128,
        alias,
        &PaddingMode::NONE,
        &BlockMode::GCM,
        None,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::MISSING_MIN_MAC_LENGTH), result.unwrap_err());
}

/// Try to create an operation using AES key with multiple block modes. Test should fail to create
/// an operation with `UNSUPPORTED_BLOCK_MODE` error code.
#[test]
fn keystore2_aes_key_op_fails_multi_block_modes() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = "aes_key_test_invalid_1";

    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::AES)
        .purpose(KeyPurpose::ENCRYPT)
        .purpose(KeyPurpose::DECRYPT)
        .key_size(128)
        .block_mode(BlockMode::ECB)
        .block_mode(BlockMode::CBC)
        .padding_mode(PaddingMode::NONE);

    let key_metadata = sec_level
        .generateKey(
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
        )
        .unwrap();

    let op_params = authorizations::AuthSetBuilder::new()
        .purpose(KeyPurpose::ENCRYPT)
        .block_mode(BlockMode::ECB)
        .block_mode(BlockMode::CBC)
        .padding_mode(PaddingMode::NONE);

    let result = key_generations::map_ks_error(sec_level.createOperation(
        &key_metadata.key,
        &op_params,
        false,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_BLOCK_MODE), result.unwrap_err());
}

/// Try to create an operation using AES key with multiple padding modes. Test should fail to create
/// an operation with `UNSUPPORTED_PADDING_MODE` error code.
#[test]
fn keystore2_aes_key_op_fails_multi_padding_modes() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = "aes_key_test_invalid_1";

    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::AES)
        .purpose(KeyPurpose::ENCRYPT)
        .purpose(KeyPurpose::DECRYPT)
        .key_size(128)
        .block_mode(BlockMode::ECB)
        .padding_mode(PaddingMode::PKCS7)
        .padding_mode(PaddingMode::NONE);

    let key_metadata = sec_level
        .generateKey(
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
        )
        .unwrap();

    let op_params = authorizations::AuthSetBuilder::new()
        .purpose(KeyPurpose::ENCRYPT)
        .block_mode(BlockMode::ECB)
        .padding_mode(PaddingMode::PKCS7)
        .padding_mode(PaddingMode::NONE);

    let result = key_generations::map_ks_error(sec_level.createOperation(
        &key_metadata.key,
        &op_params,
        false,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_PADDING_MODE), result.unwrap_err());
}

/// Generate a AES-ECB key with unpadded mode. Try to create an operation using generated key
/// with PKCS7 padding mode. Test should fail to create an Operation with
/// `INCOMPATIBLE_PADDING_MODE` error code.
#[test]
fn keystore2_aes_key_op_fails_incompatible_padding() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = "aes_key_test_invalid_1";

    let key_metadata = key_generations::generate_sym_key(
        &sec_level,
        Algorithm::AES,
        128,
        alias,
        &PaddingMode::NONE,
        &BlockMode::ECB,
        None,
    )
    .unwrap();

    let result = key_generations::map_ks_error(perform_sample_sym_key_encrypt_op(
        &sec_level,
        PaddingMode::PKCS7,
        BlockMode::ECB,
        &mut None,
        None,
        &key_metadata.key,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INCOMPATIBLE_PADDING_MODE), result.unwrap_err());
}

/// Generate a AES-ECB key with unpadded mode. Try to create an operation using generated key
/// with CBC block mode. Test should fail to create an Operation with
/// `INCOMPATIBLE_BLOCK_MODE` error code.
#[test]
fn keystore2_aes_key_op_fails_incompatible_blockmode() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = "aes_key_test_invalid_1";

    let key_metadata = key_generations::generate_sym_key(
        &sec_level,
        Algorithm::AES,
        128,
        alias,
        &PaddingMode::NONE,
        &BlockMode::ECB,
        None,
    )
    .unwrap();

    let result = key_generations::map_ks_error(perform_sample_sym_key_encrypt_op(
        &sec_level,
        PaddingMode::NONE,
        BlockMode::CBC,
        &mut None,
        None,
        &key_metadata.key,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INCOMPATIBLE_BLOCK_MODE), result.unwrap_err());
}

/// Generate a AES-GCM key with `MIN_MAC_LENGTH`. Try to create an operation using this
/// generated key without providing `MAC_LENGTH`. Test should fail to create an operation with
/// `MISSING_MAC_LENGTH` error code.
#[test]
fn keystore2_aes_gcm_op_fails_missing_mac_len() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let mac_len = None;
    let min_mac_len = Some(128);

    let result = key_generations::map_ks_error(create_aes_key_and_operation(
        &sec_level,
        128,
        PaddingMode::NONE,
        BlockMode::GCM,
        mac_len,
        min_mac_len,
        &mut None,
    ));
    assert!(result.is_err());

    let e = result.unwrap_err();
    assert!(
        e == Error::Km(ErrorCode::MISSING_MAC_LENGTH)
            || e == Error::Km(ErrorCode::UNSUPPORTED_MAC_LENGTH)
    );
}

/// Generate a AES-GCM key with `MIN_MAC_LENGTH`. Try to create an operation using this
/// generated key and  provide `MAC_LENGTH` < key's `MIN_MAC_LENGTH`. Test should fail to create
/// an operation with `INVALID_MAC_LENGTH` error code.
#[test]
fn keystore2_aes_gcm_op_fails_invalid_mac_len() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let mac_len = Some(96);
    let min_mac_len = Some(104);

    let result = key_generations::map_ks_error(create_aes_key_and_operation(
        &sec_level,
        128,
        PaddingMode::NONE,
        BlockMode::GCM,
        mac_len,
        min_mac_len,
        &mut None,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INVALID_MAC_LENGTH), result.unwrap_err());
}

/// Generate a AES-GCM key with `MIN_MAC_LENGTH`. Try to create an operation using this
/// generated key and  provide `MAC_LENGTH` > 128. Test should fail to create an operation with
/// `UNSUPPORTED_MAC_LENGTH` error code.
#[test]
fn keystore2_aes_gcm_op_fails_unsupported_mac_len() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let result = key_generations::map_ks_error(create_aes_key_and_operation(
        &sec_level,
        128,
        PaddingMode::NONE,
        BlockMode::GCM,
        Some(256),
        Some(128),
        &mut None,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_MAC_LENGTH), result.unwrap_err());
}

/// Generate a AES-CBC-PKCS7 key without `CALLER_NONCE` authorization. Try to set nonce while
/// creating an operation using this generated key. Test should fail to create an operation with
/// `CALLER_NONCE_PROHIBITED` error code.
#[test]
fn keystore2_aes_key_op_fails_nonce_prohibited() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = "aes_key_test_nonce_1";
    let mut nonce = Some(vec![0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]);

    let key_metadata = key_generations::generate_sym_key(
        &sec_level,
        Algorithm::AES,
        128,
        alias,
        &PaddingMode::PKCS7,
        &BlockMode::CBC,
        None,
    )
    .unwrap();

    let result = key_generations::map_ks_error(perform_sample_sym_key_encrypt_op(
        &sec_level,
        PaddingMode::NONE,
        BlockMode::CBC,
        &mut nonce,
        None,
        &key_metadata.key,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::CALLER_NONCE_PROHIBITED), result.unwrap_err());
}
