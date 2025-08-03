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

/// Generate a 3DES key. Create encryption and decryption operations using the generated key.
fn create_3des_key_and_operation(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    padding_mode: PaddingMode,
    block_mode: BlockMode,
    nonce: &mut Option<Vec<u8>>,
) -> Result<(), binder::Status> {
    let alias = format!("ks_3des_test_key_{}{}", block_mode.0, padding_mode.0);

    let key_metadata = key_generations::generate_sym_key(
        sec_level,
        Algorithm::TRIPLE_DES,
        168,
        &alias,
        &padding_mode,
        &block_mode,
        None,
    )?;

    // Encrypts `SAMPLE_PLAIN_TEXT` whose length is multiple of DES block size.
    let cipher_text = perform_sample_sym_key_encrypt_op(
        sec_level,
        padding_mode,
        block_mode,
        nonce,
        None,
        &key_metadata.key,
    )?;
    assert!(cipher_text.is_some());

    let plain_text = perform_sample_sym_key_decrypt_op(
        sec_level,
        &cipher_text.unwrap(),
        padding_mode,
        block_mode,
        nonce,
        None,
        &key_metadata.key,
    )
    .unwrap();
    assert!(plain_text.is_some());
    assert_eq!(plain_text.unwrap(), SAMPLE_PLAIN_TEXT.to_vec());
    Ok(())
}

/// Generate 3DES keys with various block modes and paddings.
///  - Block Modes: ECB, CBC
///  - Padding Modes: NONE, PKCS7
/// Test should generate keys and perform operation successfully.
#[test]
fn keystore2_3des_ecb_cbc_generate_key_success() {
    let keystore2 = get_keystore_service();
    let block_modes = [BlockMode::ECB, BlockMode::CBC];
    let padding_modes = [PaddingMode::PKCS7, PaddingMode::NONE];

    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    for block_mode in block_modes {
        for padding_mode in padding_modes {
            assert_eq!(
                Ok(()),
                create_3des_key_and_operation(&sec_level, padding_mode, block_mode, &mut None)
            );
        }
    }
}

/// Try to generate 3DES key with invalid key size. Test should fail to generate a key with
/// an error code `UNSUPPORTED_KEY_SIZE`.
#[test]
fn keystore2_3des_key_fails_unsupported_key_size() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = "3des_key_test_invalid_1";
    let invalid_key_size = 128;

    let result = key_generations::map_ks_error(key_generations::generate_sym_key(
        &sec_level,
        Algorithm::TRIPLE_DES,
        invalid_key_size,
        alias,
        &PaddingMode::PKCS7,
        &BlockMode::CBC,
        None,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_KEY_SIZE), result.unwrap_err());
}

/// Generate a 3DES key without providing padding mode and try to use the generated key to create
/// an operation. Test should fail to create an operation with an error code
/// `UNSUPPORTED_PADDING_MODE`.
#[test]
fn keystore2_3des_key_fails_missing_padding() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = "3des_key_test_missing_padding";

    let gen_params = authorizations::AuthSetBuilder::new()
        .no_auth_required()
        .algorithm(Algorithm::TRIPLE_DES)
        .purpose(KeyPurpose::ENCRYPT)
        .purpose(KeyPurpose::DECRYPT)
        .key_size(168)
        .block_mode(BlockMode::ECB);

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
        .block_mode(BlockMode::ECB);

    let result = key_generations::map_ks_error(sec_level.createOperation(
        &key_metadata.key,
        &op_params,
        false,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_PADDING_MODE), result.unwrap_err());
}

/// Generate a 3DES key with padding mode NONE. Try to encrypt a text whose length isn't a
/// multiple of the DES block size.
#[test]
fn keystore2_3des_key_encrypt_fails_invalid_input_length() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let alias = "3des_key_test_invalid_input_len";

    let key_metadata = key_generations::generate_sym_key(
        &sec_level,
        Algorithm::TRIPLE_DES,
        168,
        alias,
        &PaddingMode::NONE,
        &BlockMode::ECB,
        None,
    )
    .unwrap();

    let op_params = authorizations::AuthSetBuilder::new()
        .purpose(KeyPurpose::ENCRYPT)
        .padding_mode(PaddingMode::NONE)
        .block_mode(BlockMode::ECB);

    let op_response = sec_level
        .createOperation(&key_metadata.key, &op_params, false)
        .expect("Error in creation of operation using rebound key.");
    assert!(op_response.iOperation.is_some());

    let op = op_response.iOperation.unwrap();
    // 3DES expects input should be multiple of DES block size (64-bits) length. Try with invalid
    // length of input.
    let invalid_block_size_msg = b"my message 111";
    let result = key_generations::map_ks_error(op.finish(Some(invalid_block_size_msg), None));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INVALID_INPUT_LENGTH), result.unwrap_err());
}

/// Try to generate 3DES key with BlockMode::CTR. Test should fail to create an operation with an
/// error code `UNSUPPORTED_BLOCK_MODE`.
#[test]
fn keystore2_3des_key_fails_unsupported_block_mode() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let result = key_generations::map_ks_error(create_3des_key_and_operation(
        &sec_level,
        PaddingMode::NONE,
        BlockMode::CTR,
        &mut None,
    ));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::UNSUPPORTED_BLOCK_MODE), result.unwrap_err());
}
