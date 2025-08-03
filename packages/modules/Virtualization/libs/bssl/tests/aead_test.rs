// Copyright 2023, The Android Open Source Project
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

use bssl_avf::{Aead, AeadContext, ApiName, CipherError, Error, ReasonCode, Result};

/// The following vectors are generated randomly with:
/// `hexdump -vn32 -e'32/1 "0x%02x, " 1 "\n"' /dev/urandom`
const KEY1: [u8; 32] = [
    0xdb, 0x16, 0xcc, 0xbf, 0xf0, 0xc4, 0xbc, 0x93, 0xc3, 0x5f, 0x11, 0xc5, 0xfa, 0xae, 0x03, 0x6c,
    0x75, 0x40, 0x1f, 0x60, 0xb6, 0x3e, 0xb9, 0x2a, 0x6c, 0x84, 0x06, 0x4b, 0x36, 0x7f, 0xed, 0xdb,
];
const KEY2: [u8; 32] = [
    0xaa, 0x57, 0x7a, 0x1a, 0x8b, 0xa2, 0x59, 0x3b, 0xad, 0x5f, 0x4d, 0x29, 0xe1, 0x0c, 0xaa, 0x85,
    0xde, 0xf9, 0xad, 0xad, 0x8c, 0x11, 0x0c, 0x2e, 0x13, 0x43, 0xd7, 0xdf, 0x2a, 0x43, 0xb9, 0xdd,
];
/// The following vectors are generated randomly with:
/// Generated with `hexdump -vn12 -e'12/1 "0x%02x, " 1 "\n"' /dev/urandom`
const AES_256_GCM_NONCE1: [u8; 12] =
    [0x56, 0x96, 0x73, 0xe1, 0xc6, 0x3d, 0xca, 0x9a, 0x2f, 0xad, 0x3b, 0xeb];
const AES_256_GCM_NONCE2: [u8; 12] =
    [0xa0, 0x27, 0xea, 0x3a, 0x29, 0xfa, 0x8a, 0x49, 0x35, 0x07, 0x32, 0xec];
const MESSAGE: &[u8] = b"aead_aes_256_gcm test message";

#[test]
fn aes_256_gcm_encrypts_and_decrypts_successfully() -> Result<()> {
    let ciphertext = aes_256_gcm_encrypt(MESSAGE)?;
    let tag_len = None;

    let ad = &[];
    let aead_ctx = AeadContext::new(Aead::aes_256_gcm(), &KEY1, tag_len)?;
    let mut out = vec![0u8; ciphertext.len()];

    let plaintext = aead_ctx.open(&ciphertext, &AES_256_GCM_NONCE1, ad, &mut out)?;

    assert_eq!(MESSAGE, plaintext);
    Ok(())
}

#[test]
fn aes_256_gcm_fails_to_encrypt_with_invalid_nonce() -> Result<()> {
    let tag_len = None;
    let aead_ctx = AeadContext::new(Aead::aes_256_gcm(), &KEY1, tag_len)?;
    let nonce = &[];
    let ad = &[];
    let mut out = vec![0u8; MESSAGE.len() + aead_ctx.aead().max_overhead()];

    let err = aead_ctx.seal(MESSAGE, nonce, ad, &mut out).unwrap_err();

    let expected_err = Error::CallFailed(
        ApiName::EVP_AEAD_CTX_seal,
        ReasonCode::Cipher(CipherError::InvalidNonceSize),
    );
    assert_eq!(expected_err, err);
    Ok(())
}

#[test]
fn aes_256_gcm_fails_to_decrypt_with_wrong_key() -> Result<()> {
    let ciphertext = aes_256_gcm_encrypt(MESSAGE)?;
    let tag_len = None;

    let ad = &[];
    let aead_ctx2 = AeadContext::new(Aead::aes_256_gcm(), &KEY2, tag_len)?;
    let mut plaintext = vec![0u8; ciphertext.len()];

    let err = aead_ctx2.open(&ciphertext, &AES_256_GCM_NONCE1, ad, &mut plaintext).unwrap_err();

    let expected_err =
        Error::CallFailed(ApiName::EVP_AEAD_CTX_open, ReasonCode::Cipher(CipherError::BadDecrypt));
    assert_eq!(expected_err, err);
    Ok(())
}

#[test]
fn aes_256_gcm_fails_to_decrypt_with_different_ad() -> Result<()> {
    let ciphertext = aes_256_gcm_encrypt(MESSAGE)?;
    let tag_len = None;

    let ad2 = &[1];
    let aead_ctx = AeadContext::new(Aead::aes_256_gcm(), &KEY1, tag_len)?;
    let mut plaintext = vec![0u8; ciphertext.len()];

    let err = aead_ctx.open(&ciphertext, &AES_256_GCM_NONCE1, ad2, &mut plaintext).unwrap_err();

    let expected_err =
        Error::CallFailed(ApiName::EVP_AEAD_CTX_open, ReasonCode::Cipher(CipherError::BadDecrypt));
    assert_eq!(expected_err, err);
    Ok(())
}

#[test]
fn aes_256_gcm_fails_to_decrypt_with_different_nonce() -> Result<()> {
    let ciphertext = aes_256_gcm_encrypt(MESSAGE)?;
    let tag_len = None;

    let ad = &[];
    let aead_ctx = AeadContext::new(Aead::aes_256_gcm(), &KEY1, tag_len)?;
    let mut plaintext = vec![0u8; ciphertext.len()];

    let err = aead_ctx.open(&ciphertext, &AES_256_GCM_NONCE2, ad, &mut plaintext).unwrap_err();

    let expected_err =
        Error::CallFailed(ApiName::EVP_AEAD_CTX_open, ReasonCode::Cipher(CipherError::BadDecrypt));
    assert_eq!(expected_err, err);
    Ok(())
}

#[test]
fn aes_256_gcm_fails_to_decrypt_corrupted_ciphertext() -> Result<()> {
    let mut ciphertext = aes_256_gcm_encrypt(MESSAGE)?;
    ciphertext[1] = !ciphertext[1];
    let tag_len = None;

    let ad = &[];
    let aead_ctx = AeadContext::new(Aead::aes_256_gcm(), &KEY1, tag_len)?;
    let mut plaintext = vec![0u8; ciphertext.len()];

    let err = aead_ctx.open(&ciphertext, &AES_256_GCM_NONCE1, ad, &mut plaintext).unwrap_err();

    let expected_err =
        Error::CallFailed(ApiName::EVP_AEAD_CTX_open, ReasonCode::Cipher(CipherError::BadDecrypt));
    assert_eq!(expected_err, err);
    Ok(())
}

fn aes_256_gcm_encrypt(message: &[u8]) -> Result<Vec<u8>> {
    let tag_len = None;
    let aead_ctx = AeadContext::new(Aead::aes_256_gcm(), &KEY1, tag_len)?;
    let mut out = vec![0u8; message.len() + aead_ctx.aead().max_overhead()];

    assert_eq!(aead_ctx.aead().nonce_length(), AES_256_GCM_NONCE1.len());
    let ad = &[];

    let ciphertext = aead_ctx.seal(message, &AES_256_GCM_NONCE1, ad, &mut out)?;
    assert_ne!(message, ciphertext);
    Ok(ciphertext.to_vec())
}
