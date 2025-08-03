/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "hpke_jni.h"
#include <openssl/curve25519.h>
#include <openssl/hpke.h>
#include <openssl/span.h>
#include <vector>

// Hybrid Public Key Encryption (HPKE) encryption operation
// RFC: https://datatracker.ietf.org/doc/rfc9180
//
// Based from chromium's boringSSL implementation
// https://source.chromium.org/chromium/chromium/src/+/main:content/browser/aggregation_service/aggregatable_report.cc;l=211
JNIEXPORT jbyteArray JNICALL Java_com_android_adservices_HpkeJni_encrypt
  (JNIEnv* env, jobject object,
   jbyteArray publicKey, jbyteArray plainText, jbyteArray associatedData) {

  if (publicKey == NULL || plainText == NULL || associatedData == NULL) {
    return {};
  }

  if (env->GetArrayLength(publicKey) != X25519_PUBLIC_VALUE_LEN) {
    return {};
  }

  bssl::ScopedEVP_HPKE_CTX sender_context;

  std::vector<uint8_t> payload(EVP_HPKE_MAX_ENC_LENGTH);
  size_t encapsulated_shared_secret_len;

  jbyte* peer_public_key = env->GetByteArrayElements(publicKey, 0);
  jbyte* info = env->GetByteArrayElements(associatedData, 0);

  if (!EVP_HPKE_CTX_setup_sender(
            /* ctx= */ sender_context.get(),
            /* out_enc= */ payload.data(),
            /* out_enc_len= */ &encapsulated_shared_secret_len,
            /* max_enc= */ payload.size(),
            /* kem= */ EVP_hpke_x25519_hkdf_sha256(),
            /* kdf= */ EVP_hpke_hkdf_sha256(),
            /* aead= */ EVP_hpke_chacha20_poly1305(),
            /* peer_public_key= */ reinterpret_cast<const uint8_t*>(peer_public_key),
            /* peer_public_key_len= */ env->GetArrayLength(publicKey),
            /* info= */ reinterpret_cast<const uint8_t*>(info),
            /* info_len= */ env->GetArrayLength(associatedData))) {
      env->ReleaseByteArrayElements(publicKey, peer_public_key, JNI_ABORT);
      env->ReleaseByteArrayElements(associatedData, info, JNI_ABORT);
      return {};
    }

  env->ReleaseByteArrayElements(publicKey, peer_public_key, JNI_ABORT);
  env->ReleaseByteArrayElements(associatedData, info, JNI_ABORT);

  payload.resize(encapsulated_shared_secret_len + env->GetArrayLength(plainText) +
                 EVP_HPKE_CTX_max_overhead(sender_context.get()));

  bssl::Span<uint8_t> ciphertext = bssl::MakeSpan(payload).subspan(encapsulated_shared_secret_len);
  size_t ciphertext_len;

  jbyte* plain_text = env->GetByteArrayElements(plainText, 0);

  if (!EVP_HPKE_CTX_seal(
          /* ctx= */ sender_context.get(),
          /* out= */ ciphertext.data(),
          /* out_len= */ &ciphertext_len,
          /* max_out_len= */ ciphertext.size(),
          /* in= */ reinterpret_cast<const uint8_t*>(plain_text),
          /* in_len=*/ env->GetArrayLength(plainText),
          /* ad= */ nullptr,
          /* ad_len= */ 0)) {
    env->ReleaseByteArrayElements(plainText, plain_text, JNI_ABORT);
    return {};
  }

  env->ReleaseByteArrayElements(plainText, plain_text, JNI_ABORT);

  payload.resize(encapsulated_shared_secret_len + ciphertext_len);

  jbyteArray payload_byte_array = env->NewByteArray(payload.size());
  env->SetByteArrayRegion(payload_byte_array, 0, payload.size(),
                          reinterpret_cast<const jbyte*>(payload.data()));
  return payload_byte_array;
}

// Hybrid Public Key Encryption (HPKE) decryption operation
// RFC: https://datatracker.ietf.org/doc/rfc9180
//
// Based from chromium's boringSSL implementation
// https://source.chromium.org/chromium/chromium/src/+/main:content/browser/aggregation_service/aggregation_service_test_utils.cc;l=305
JNIEXPORT jbyteArray JNICALL Java_com_android_adservices_HpkeJni_decrypt
  (JNIEnv* env, jobject object,
   jbyteArray privateKey, jbyteArray ciphertext, jbyteArray associatedData) {

  if (privateKey == NULL || ciphertext == NULL || associatedData == NULL) {
      return {};
  }

  if (env->GetArrayLength(privateKey) != X25519_PRIVATE_KEY_LEN) {
      return {};
  }

  bssl::ScopedEVP_HPKE_KEY hpke_key;
  jbyte* private_key = env->GetByteArrayElements(privateKey, 0);
  if (!EVP_HPKE_KEY_init(hpke_key.get(),
                         EVP_hpke_x25519_hkdf_sha256(),
                         reinterpret_cast<const uint8_t*>(private_key),
                         env->GetArrayLength(privateKey))) {
    env->ReleaseByteArrayElements(privateKey, private_key, JNI_ABORT);
    return {};
  }

  env->ReleaseByteArrayElements(privateKey, private_key, JNI_ABORT);

  std::vector<uint8_t> payload(env->GetArrayLength(ciphertext));
  env->GetByteArrayRegion(ciphertext,
                          0,
                          env->GetArrayLength(ciphertext),
                          reinterpret_cast<jbyte*>(payload.data()));

  bssl::Span<uint8_t> payload_span = bssl::MakeSpan(payload);
  bssl::Span<uint8_t> enc = payload_span.subspan(0, X25519_PUBLIC_VALUE_LEN);
  bssl::ScopedEVP_HPKE_CTX recipient_context;
  jbyte* associated_data = env->GetByteArrayElements(associatedData, 0);
  if (!EVP_HPKE_CTX_setup_recipient(
          /*ctx=*/ recipient_context.get(),
          /*key=*/ hpke_key.get(),
          /*kdf=*/ EVP_hpke_hkdf_sha256(),
          /*aead=*/ EVP_hpke_chacha20_poly1305(),
          /*enc=*/ enc.data(),
          /*enc_len=*/ enc.size(),
          /*info=*/ reinterpret_cast<const uint8_t*>(associated_data),
          /*info_len=*/ env->GetArrayLength(associatedData))) {
    env->ReleaseByteArrayElements(associatedData, associated_data, JNI_ABORT);
    return {};
  }

  env->ReleaseByteArrayElements(associatedData, associated_data, JNI_ABORT);

  bssl::Span<const uint8_t> ciphertext_span = payload_span.subspan(X25519_PUBLIC_VALUE_LEN);
  std::vector<uint8_t> plaintext(ciphertext_span.size());
  size_t plaintext_len;
  if (!EVP_HPKE_CTX_open(
          /*ctx=*/ recipient_context.get(),
          /*out=*/ plaintext.data(),
          /*out_len*/ &plaintext_len,
          /*max_out_len=*/ plaintext.size(),
          /*in=*/ ciphertext_span.data(),
          /*in_len=*/ ciphertext_span.size(),
          /*ad=*/ nullptr,
          /*ad_len=*/ 0)) {
      return {};
  }

  plaintext.resize(plaintext_len);

  jbyteArray payload_byte_array = env->NewByteArray(plaintext.size());
  env->SetByteArrayRegion(payload_byte_array,
                          0,
                          plaintext.size(),
                          reinterpret_cast<const jbyte*>(plaintext.data()));
  return payload_byte_array;
}