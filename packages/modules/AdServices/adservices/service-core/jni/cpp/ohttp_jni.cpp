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
#include "ohttp_jni.h"
#include "jni_util.h"

#include <android/log.h>
#include <openssl/digest.h>
#include <openssl/hkdf.h>
#include <openssl/hpke.h>

#include <iostream>
#include <string_view>
#include <vector>

constexpr char const *LOG_TAG = "OhttpJniWrapper";
constexpr const char *IllegalArgumentExceptionClass = "java/lang/IllegalArgumentException";
constexpr const char* IllegalStateExceptionClass = "java/lang/IllegalStateException";

// TODO(b/274425716) : Use macros similar to Conscrypt's JNI_TRACE for cleaner
// logging
// TODO(b/274598556) : Add error throwing convenience methods

JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeKemDhkemX25519HkdfSha256(
    JNIEnv *env, jclass) {
  __android_log_write(ANDROID_LOG_INFO, LOG_TAG,
                      "hpkeKemDhkemX25519HkdfSha256");

  const EVP_HPKE_KEM *ctx = EVP_hpke_x25519_hkdf_sha256();
  return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeKdfHkdfSha256(JNIEnv *env,
                                                                    jclass) {
  __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "hpkeKdfHkdfSha256");

  const EVP_HPKE_KDF *ctx = EVP_hpke_hkdf_sha256();
  return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeAeadAes256Gcm(JNIEnv *env,
                                                                    jclass) {
  __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "hpkeAeadAes256Gcm");

  const EVP_HPKE_AEAD *ctx = EVP_hpke_aes_256_gcm();
  return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hkdfSha256MessageDigest(
    JNIEnv *env, jclass) {
  __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "hkdfSha256MessageDigest");

  const EVP_MD *evp_md = EVP_sha256();
  return reinterpret_cast<jlong>(evp_md);
}

JNIEXPORT void JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeCtxFree(
    JNIEnv *env, jclass, jlong hpkeCtxRef) {
  __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "hpkeCtxFree");

  EVP_HPKE_CTX *ctx = reinterpret_cast<EVP_HPKE_CTX *>(hpkeCtxRef);
  if (ctx != nullptr) {
    EVP_HPKE_CTX_free(ctx);
  }
}

JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeCtxNew(JNIEnv *env,
                                                             jclass) {
  __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "hpkeCtxNew");

  const EVP_HPKE_CTX *ctx = EVP_HPKE_CTX_new();
  return reinterpret_cast<jlong>(ctx);
}

// Defining EVP_HPKE_KEM struct with only the field needed to call the
// function "EVP_HPKE_CTX_setup_sender_with_seed_for_testing" using
// "kem->seed_len"
struct evp_hpke_kem_st {
  size_t seed_len;
};

JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeCtxSetupSenderWithSeed(
    JNIEnv *env, jclass, jlong senderHpkeCtxRef, jlong evpKemRef,
    jlong evpKdfRef, jlong evpAeadRef, jbyteArray publicKeyArray,
    jbyteArray infoArray, jbyteArray seedArray) {
  __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "hpkeCtxSetupSenderWithSeed");

  EVP_HPKE_CTX *ctx = reinterpret_cast<EVP_HPKE_CTX *>(senderHpkeCtxRef);
  if (ctx == nullptr) {
    // TODO(b/274598556) : throw NullPointerException
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "hpke context is null");
    return {};
  }

  const EVP_HPKE_KEM *kem = reinterpret_cast<const EVP_HPKE_KEM *>(evpKemRef);
  const EVP_HPKE_KDF *kdf = reinterpret_cast<const EVP_HPKE_KDF *>(evpKdfRef);
  const EVP_HPKE_AEAD *aead =
      reinterpret_cast<const EVP_HPKE_AEAD *>(evpAeadRef);

  __android_log_print(
      ANDROID_LOG_INFO, LOG_TAG,
      "EVP_HPKE_CTX_setup_sender_with_seed(%p, %ld, %ld, %ld, %p, %p, %p)", ctx,
      (long)evpKemRef, (long)evpKdfRef, (long)evpAeadRef, publicKeyArray,
      infoArray, seedArray);

  if (kem == nullptr || kdf == nullptr || aead == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                        "kem or kdf or aead is null");
    return {};
  }

  if (publicKeyArray == nullptr || seedArray == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                        "public key array or seed array is null");
    return {};
  }

  jbyte *peer_public_key = env->GetByteArrayElements(publicKeyArray, 0);
  jbyte *seed = env->GetByteArrayElements(seedArray, 0);

  jbyte *infoArrayBytes = nullptr;
  const uint8_t *info = nullptr;
  size_t infoLen = 0;
  if (infoArray != nullptr) {
    infoArrayBytes = env->GetByteArrayElements(infoArray, 0);
    info = reinterpret_cast<const uint8_t *>(infoArrayBytes);
    infoLen = env->GetArrayLength(infoArray);
  }

  size_t encapsulatedSharedSecretLen;
  std::vector<uint8_t> encapsulatedSharedSecret(EVP_HPKE_MAX_ENC_LENGTH);
  if (!EVP_HPKE_CTX_setup_sender_with_seed_for_testing(
          /* ctx= */ ctx,
          /* out_enc= */ encapsulatedSharedSecret.data(),
          /* out_enc_len= */ &encapsulatedSharedSecretLen,
          /* max_enc= */ encapsulatedSharedSecret.size(),
          /* kem= */ kem,
          /* kdf= */ kdf,
          /* aead= */ aead,
          /* peer_public_key= */
          reinterpret_cast<const uint8_t *>(peer_public_key),
          /* peer_public_key_len= */ env->GetArrayLength(publicKeyArray),
          /* info= */ info,
          /* info_len= */ infoLen,
          /* seed= */ reinterpret_cast<const uint8_t *>(seed),
          /* seed_len= */ kem->seed_len)) {
    env->ReleaseByteArrayElements(publicKeyArray, peer_public_key, JNI_ABORT);
    env->ReleaseByteArrayElements(seedArray, seed, JNI_ABORT);

    if (infoArrayBytes != nullptr) {
      env->ReleaseByteArrayElements(infoArray, infoArrayBytes, JNI_ABORT);
    }

    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "setup sender returned 0");
    return {};
  }

  env->ReleaseByteArrayElements(publicKeyArray, peer_public_key, JNI_ABORT);
  env->ReleaseByteArrayElements(seedArray, seed, JNI_ABORT);

  if (infoArrayBytes != nullptr) {
    env->ReleaseByteArrayElements(infoArray, infoArrayBytes, JNI_ABORT);
  }

  jbyteArray encArray = env->NewByteArray(encapsulatedSharedSecretLen);
  env->SetByteArrayRegion(
      encArray, 0, encapsulatedSharedSecretLen,
      reinterpret_cast<const jbyte *>(encapsulatedSharedSecret.data()));

  return encArray;
}

JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_gatewayDecrypt(
    JNIEnv *env,
    jclass javaClass,
    jlong hpkeCtxRef,
    jlong evpKemRef,
    jlong evpKdfRef,
    jlong evpAeadRef,
    jbyteArray encryptedDataArray)
    {
  __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "gatewayDecrypt");

  EVP_HPKE_CTX *gatewayCtx = reinterpret_cast<EVP_HPKE_CTX *>(hpkeCtxRef);

  jbyte* encryptedDataPtr = env->GetByteArrayElements(encryptedDataArray, 0);
  size_t encryptedDataLen = env->GetArrayLength(encryptedDataArray);

  std::string decrypted(encryptedDataLen, '\0');
  size_t decryptedLen;
  if (!EVP_HPKE_CTX_open(
          gatewayCtx, reinterpret_cast<uint8_t*>(decrypted.data()),
          &decryptedLen, decrypted.size(),
          reinterpret_cast<const uint8_t*>(encryptedDataPtr),
          encryptedDataLen, nullptr, 0)) {
    env->ReleaseByteArrayElements(encryptedDataArray, encryptedDataPtr, JNI_ABORT);
    jni_util::JniUtil::ThrowJavaException(
            env,
            IllegalStateExceptionClass,
            "Could't decrypt ciphertext");
    return {};
  }

  decrypted.resize(decryptedLen);
  env->ReleaseByteArrayElements(encryptedDataArray, encryptedDataPtr, JNI_ABORT);

  jbyteArray decryptedArray = env->NewByteArray(decryptedLen);
  env->SetByteArrayRegion(decryptedArray, 0, decryptedLen,
                          reinterpret_cast<const jbyte *>(decrypted.data()));
  return decryptedArray;
}

JNIEXPORT jboolean JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeSetupRecipient(
    JNIEnv *env, jclass,
    jlong hpkeCtxRef,
    jlong evpKemRef,
    jlong evpKdfRef,
    jlong evpAeadRef,
    jbyteArray privKeyArray,
    jbyteArray encArray,
    jbyteArray infoArray)
    {
  __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "hpkeSetupRecipient");

  if (infoArray == nullptr ||
        encArray == nullptr ||
        privKeyArray == nullptr) {
    jni_util::JniUtil::ThrowJavaException(
            env,
            IllegalArgumentExceptionClass,
            "One of required input parameters is null");
    return {};
  }

  const EVP_HPKE_KEM *kem = reinterpret_cast<const EVP_HPKE_KEM *>(evpKemRef);
  const EVP_HPKE_KDF *kdf = reinterpret_cast<const EVP_HPKE_KDF *>(evpKdfRef);
  const EVP_HPKE_AEAD *aead =
      reinterpret_cast<const EVP_HPKE_AEAD *>(evpAeadRef);

  if (kem == nullptr || kdf == nullptr || aead == nullptr) {
    jni_util::JniUtil::ThrowJavaException(
            env,
            IllegalArgumentExceptionClass,
            "One of HPKE Algorithms is null");
    return (jboolean) 0;
  }

  jbyte* privKeyPtr = env->GetByteArrayElements(privKeyArray, 0);
  size_t keyLength = env->GetArrayLength(privKeyArray);

  EVP_HPKE_KEY *recipientKey = EVP_HPKE_KEY_new();
  if (recipientKey == nullptr) {
    env->ReleaseByteArrayElements(privKeyArray, privKeyPtr, JNI_ABORT);
    jni_util::JniUtil::ThrowJavaException(
            env,
            IllegalStateExceptionClass,
            "Could't create new ENV_HPKE_KEY");
    return (jboolean) 0;
  }

  if (!EVP_HPKE_KEY_init(
          recipientKey, kem,
          reinterpret_cast<const uint8_t*>(privKeyPtr),
          keyLength)) {
    env->ReleaseByteArrayElements(privKeyArray, privKeyPtr, JNI_ABORT);
    jni_util::JniUtil::ThrowJavaException(
            env,
            IllegalStateExceptionClass,
            "Could't initialize ENV_HPKE_KEY with gateway private key");
      return (jboolean) 0;
  }

  jbyte* encPtr = env->GetByteArrayElements(encArray, 0);
  size_t encLength = env->GetArrayLength(encArray);

  jbyte* infoPtr = env->GetByteArrayElements(infoArray, 0);
  size_t infoLen = env->GetArrayLength(infoArray);

  EVP_HPKE_CTX *gatewayCtx = reinterpret_cast<EVP_HPKE_CTX *>(hpkeCtxRef);
  if (gatewayCtx == nullptr) {
    env->ReleaseByteArrayElements(privKeyArray, privKeyPtr, JNI_ABORT);
    env->ReleaseByteArrayElements(encArray, encPtr, JNI_ABORT);
    env->ReleaseByteArrayElements(infoArray, infoPtr, JNI_ABORT);
    jni_util::JniUtil::ThrowJavaException(
            env,
            IllegalStateExceptionClass,
            "Could't get HPKE context");
    return (jboolean) 0;
  }

  if (!EVP_HPKE_CTX_setup_recipient(
          gatewayCtx, recipientKey, kdf,
          aead,
          reinterpret_cast<const uint8_t*>(encPtr),
          encLength,
          reinterpret_cast<const uint8_t*>(infoPtr), infoLen)) {
    env->ReleaseByteArrayElements(privKeyArray, privKeyPtr, JNI_ABORT);
    env->ReleaseByteArrayElements(encArray, encPtr, JNI_ABORT);
    env->ReleaseByteArrayElements(infoArray, infoPtr, JNI_ABORT);
    jni_util::JniUtil::ThrowJavaException(
            env,
            IllegalStateExceptionClass,
            "Could't setup receiver context");
    return (jboolean) 0;
  }

  // Release resources
  env->ReleaseByteArrayElements(privKeyArray, privKeyPtr, JNI_ABORT);
  env->ReleaseByteArrayElements(encArray, encPtr, JNI_ABORT);
  env->ReleaseByteArrayElements(infoArray, infoPtr, JNI_ABORT);

  return (jboolean) 1;
}

JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeCtxSeal(
    JNIEnv *env, jclass, jlong senderHpkeCtxRef, jbyteArray plaintextArray,
    jbyteArray aadArray) {
  __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                      "EVP_HPKE_CTX_seal(%ld, %p, %p)", (long)senderHpkeCtxRef,
                      plaintextArray, aadArray);

  EVP_HPKE_CTX *ctx = reinterpret_cast<EVP_HPKE_CTX *>(senderHpkeCtxRef);
  if (ctx == nullptr) {
    // TODO(b/274598556) : throw NullPointerException
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "hpke context is null");
    return {};
  }

  if (plaintextArray == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "plaintext array is null");
    return {};
  }

  jbyte *plaintext = env->GetByteArrayElements(plaintextArray, 0);

  jbyte *aadArrayElement = nullptr;
  const uint8_t *aad = nullptr;
  size_t aadLen = 0;
  if (aadArray != nullptr) {
    aadArrayElement = env->GetByteArrayElements(aadArray, 0);
    aad = reinterpret_cast<const uint8_t *>(aadArrayElement);
    aadLen = env->GetArrayLength(aadArray);
  }


  size_t encryptedLen;
  std::vector<uint8_t> encrypted(env->GetArrayLength(plaintextArray) +
                                 EVP_HPKE_CTX_max_overhead(ctx));

  if (!EVP_HPKE_CTX_seal(/* ctx= */ ctx,
                         /* out= */ encrypted.data(),
                         /* out_len= */ &encryptedLen,
                         /* max_out_len= */ encrypted.size(),
                         /* in= */ reinterpret_cast<const uint8_t *>(plaintext),
                         /* in_len= */ env->GetArrayLength(plaintextArray),
                         /* aad= */ aad,
                         /* aad_len= */ aadLen)) {
    env->ReleaseByteArrayElements(plaintextArray, plaintext, JNI_ABORT);
    if (aadArrayElement != nullptr) {
      env->ReleaseByteArrayElements(aadArray, aadArrayElement, JNI_ABORT);
    }

    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "EVP_HPKE_CTX_seal failed");
    return {};
  }

  env->ReleaseByteArrayElements(plaintextArray, plaintext, JNI_ABORT);
  if (aadArrayElement != nullptr) {
    env->ReleaseByteArrayElements(aadArray, aadArrayElement, JNI_ABORT);
  }

  jbyteArray ciphertextArray = env->NewByteArray(encryptedLen);
  env->SetByteArrayRegion(ciphertextArray, 0, encryptedLen,
                          reinterpret_cast<const jbyte *>(encrypted.data()));
  return ciphertextArray;
}

JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeExport(
    JNIEnv *env, jclass, jlong hpkeCtxRef, jbyteArray exporterCtxArray,
    jint length) {
  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "HPKE_Export(%ld, %p, %d)",
                      (long)hpkeCtxRef, exporterCtxArray, (int)length);
  EVP_HPKE_CTX *ctx = reinterpret_cast<EVP_HPKE_CTX *>(hpkeCtxRef);

  jbyte *exporterCtxArrayElement = nullptr;
  const uint8_t *exporterCtx = nullptr;
  size_t exporterCtxLen = 0;
  if (exporterCtxArray != nullptr) {
    exporterCtxArrayElement = env->GetByteArrayElements(exporterCtxArray, 0);
    exporterCtx = reinterpret_cast<const uint8_t *>(exporterCtxArrayElement);
    exporterCtxLen = env->GetArrayLength(exporterCtxArray);
  }

  size_t exportedLen = length;
  std::vector<uint8_t> exported(exportedLen);

  if (!EVP_HPKE_CTX_export(/* ctx= */ ctx,
                           /* out= */ exported.data(),
                           /* secret_len= */ exportedLen,
                           /* context= */ exporterCtx,
                           /* context_len= */ exporterCtxLen)) {
    if (exporterCtxArrayElement != nullptr) {
      env->ReleaseByteArrayElements(exporterCtxArray, exporterCtxArrayElement,
                                    JNI_ABORT);
    }
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "HPKE_Export failed");
    return {};
  }

  if (exporterCtxArrayElement != nullptr) {
    env->ReleaseByteArrayElements(exporterCtxArray, exporterCtxArrayElement,
                                  JNI_ABORT);
  }

  jbyteArray exportedArray = env->NewByteArray(exportedLen);
  env->SetByteArrayRegion(exportedArray, 0, exportedLen,
                          reinterpret_cast<const jbyte *>(exported.data()));
  return exportedArray;
}

JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hkdfExtract(
    JNIEnv *env, jclass, jlong hkdfMd, jbyteArray secretArray,
    jbyteArray saltArray) {
  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "HKDF_extract(%ld, %p, %p)",
                      (long)hkdfMd, secretArray, saltArray);

  const EVP_MD *evp_md = reinterpret_cast<const EVP_MD *>(hkdfMd);

  jbyte *secret = env->GetByteArrayElements(secretArray, 0);
  size_t secretLen = env->GetArrayLength(secretArray);

  jbyte *salt = env->GetByteArrayElements(saltArray, 0);
  size_t saltLen = env->GetArrayLength(saltArray);

  std::vector<uint8_t> pseudorandom_key(EVP_MAX_MD_SIZE);
  size_t prk_len;

  if (!HKDF_extract(reinterpret_cast<uint8_t *>(pseudorandom_key.data()),
                    &prk_len, evp_md, reinterpret_cast<const uint8_t *>(secret),
                    secretLen, reinterpret_cast<const uint8_t *>(salt),
                    saltLen)) {
    env->ReleaseByteArrayElements(secretArray, secret, JNI_ABORT);
    env->ReleaseByteArrayElements(saltArray, salt, JNI_ABORT);
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "HKDF_Extract failed");
    return {};
  }

  env->ReleaseByteArrayElements(secretArray, secret, JNI_ABORT);
  env->ReleaseByteArrayElements(saltArray, salt, JNI_ABORT);

  pseudorandom_key.resize(prk_len);

  jbyteArray prkArray = env->NewByteArray(prk_len);
  env->SetByteArrayRegion(
      prkArray, 0, prk_len,
      reinterpret_cast<const jbyte *>(pseudorandom_key.data()));
  return prkArray;
}

JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hkdfExpand(
    JNIEnv *env, jclass, jlong hkdfMd, jbyteArray prkArray,
    jbyteArray infoArray, jint key_len) {
  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "HKDF_expand(%ld, %p, %p)",
                      (long)hkdfMd, prkArray, infoArray);

  const EVP_MD *evp_md = reinterpret_cast<const EVP_MD *>(hkdfMd);

  jbyte *prk = env->GetByteArrayElements(prkArray, 0);
  size_t prkLen = env->GetArrayLength(prkArray);

  jbyte *info = env->GetByteArrayElements(infoArray, 0);
  size_t infoLen = env->GetArrayLength(infoArray);

  std::vector<uint8_t> out_key(key_len);

  if (!HKDF_expand(reinterpret_cast<uint8_t *>(out_key.data()), key_len, evp_md,
                   reinterpret_cast<const uint8_t *>(prk), prkLen,
                   reinterpret_cast<const uint8_t *>(info), infoLen)) {
    env->ReleaseByteArrayElements(prkArray, prk, JNI_ABORT);
    env->ReleaseByteArrayElements(infoArray, info, JNI_ABORT);
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "HKDF_Expand failed");
    return {};
  }

  env->ReleaseByteArrayElements(prkArray, prk, JNI_ABORT);
  env->ReleaseByteArrayElements(infoArray, info, JNI_ABORT);

  jbyteArray responseArray = env->NewByteArray(key_len);
  env->SetByteArrayRegion(responseArray, 0, key_len,
                          reinterpret_cast<const jbyte *>(out_key.data()));
  return responseArray;
}

JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_aeadOpen(
    JNIEnv *env, jclass, jlong evpAeadRef, jbyteArray keyArray,
    jbyteArray nonceArray, jbyteArray cipherTextArray) {
  __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                      "EVP_HPKE_AEAD_CTX_open(%p, %p, %p)", keyArray,
                      nonceArray, cipherTextArray);

  const EVP_HPKE_AEAD *hpkeAead = reinterpret_cast<const EVP_HPKE_AEAD *>(evpAeadRef);
  const EVP_AEAD *aead = EVP_HPKE_AEAD_aead(hpkeAead);

  if (aead == nullptr) {
      __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "aead == null");
      return {};
  }

  jbyte *key = env->GetByteArrayElements(keyArray, 0);
  size_t keyLen = env->GetArrayLength(keyArray);

  EVP_AEAD_CTX *aead_ctx =
      EVP_AEAD_CTX_new(aead, reinterpret_cast<const uint8_t *>(key), keyLen, 0);

  if (aead_ctx == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "aead ctx == null");
    return {};
  }

  jbyte *nonce = env->GetByteArrayElements(nonceArray, 0);
  size_t nonceLen = env->GetArrayLength(nonceArray);

  jbyte *ciphertext = env->GetByteArrayElements(cipherTextArray, 0);
  size_t ciphertextLen = env->GetArrayLength(cipherTextArray);

  std::vector<uint8_t> plaintext(ciphertextLen);
  size_t plaintextLen;

  if (!EVP_AEAD_CTX_open(aead_ctx,
                         reinterpret_cast<uint8_t *>(plaintext.data()),
                         &plaintextLen, plaintext.size(),
                         reinterpret_cast<const uint8_t *>(nonce), nonceLen,
                         reinterpret_cast<const uint8_t *>(ciphertext),
                         ciphertextLen, nullptr, 0)) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "EVP_AEAD_CTX_open failed");
    env->ReleaseByteArrayElements(keyArray, key, JNI_ABORT);
    env->ReleaseByteArrayElements(nonceArray, nonce, JNI_ABORT);
    env->ReleaseByteArrayElements(cipherTextArray, ciphertext, JNI_ABORT);
    return {};
  }

  env->ReleaseByteArrayElements(keyArray, key, JNI_ABORT);
  env->ReleaseByteArrayElements(nonceArray, nonce, JNI_ABORT);
  env->ReleaseByteArrayElements(cipherTextArray, ciphertext, JNI_ABORT);
  plaintext.resize(plaintextLen);

  jbyteArray plaintextArray = env->NewByteArray(plaintextLen);
  env->SetByteArrayRegion(plaintextArray, 0, plaintextLen,
                          reinterpret_cast<const jbyte *>(plaintext.data()));
  return plaintextArray;
}


JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_aeadSeal(
    JNIEnv *env,
    jclass,
    jlong evpAeadRef,
    jbyteArray keyArray,
    jbyteArray nonceArray,
    jbyteArray plainTextArray) {
  __android_log_print(ANDROID_LOG_INFO,
                        LOG_TAG,
                       "aead_Seal(%p, %p, %p)",
                        keyArray,
                        nonceArray,
                        plainTextArray);

  const EVP_HPKE_AEAD *hpkeAead = reinterpret_cast<const EVP_HPKE_AEAD *>(evpAeadRef);
  const EVP_AEAD *aead = EVP_HPKE_AEAD_aead(hpkeAead);

  if (aead == nullptr) {
      jni_util::JniUtil::ThrowJavaException(
              env,
              IllegalArgumentExceptionClass,
              "Unable to initialize AEAD object");
      return {};
  }

  jbyte *keyPtr = env->GetByteArrayElements(keyArray, 0);
  size_t keyLen = env->GetArrayLength(keyArray);

  EVP_AEAD_CTX *aeadCtx =
      EVP_AEAD_CTX_new(aead, reinterpret_cast<const uint8_t *>(keyPtr), keyLen, 0);

  if (aeadCtx == nullptr) {
    env->ReleaseByteArrayElements(keyArray, keyPtr, JNI_ABORT);
    jni_util::JniUtil::ThrowJavaException(
            env,
            IllegalArgumentExceptionClass,
            "Unable to initialize AEAD ctx object");
    return {};
  }

  jbyte *noncePtr = env->GetByteArrayElements(nonceArray, 0);
  size_t nonceLen = env->GetArrayLength(nonceArray);

  jbyte *plainTextPtr = env->GetByteArrayElements(plainTextArray, 0);
  size_t plainTextLen = env->GetArrayLength(plainTextArray);

  const size_t maxEncryptedDataSize =
      nonceLen + plainTextLen + EVP_AEAD_max_overhead(aead);
  std::string encryptedData(maxEncryptedDataSize, '\0');

  size_t ciphertextLen;
  if (!EVP_AEAD_CTX_seal(aeadCtx,
                         reinterpret_cast<uint8_t *>(encryptedData.data()),
                         &ciphertextLen,
                         encryptedData.size() - nonceLen,
                         reinterpret_cast<const uint8_t *>(noncePtr),
                         nonceLen,
                         reinterpret_cast<const uint8_t *>(plainTextPtr),
                         plainTextLen,
                         nullptr,
                         0)) {
    env->ReleaseByteArrayElements(keyArray, keyPtr, JNI_ABORT);
    env->ReleaseByteArrayElements(nonceArray, noncePtr, JNI_ABORT);
    env->ReleaseByteArrayElements(plainTextArray, plainTextPtr, JNI_ABORT);
    jni_util::JniUtil::ThrowJavaException(
            env,
            IllegalStateExceptionClass,
            "EVP_AEAD_CTX_seal failed");
    return {};
  }

  env->ReleaseByteArrayElements(keyArray, keyPtr, JNI_ABORT);
  env->ReleaseByteArrayElements(nonceArray, noncePtr, JNI_ABORT);
  env->ReleaseByteArrayElements(plainTextArray, plainTextPtr, JNI_ABORT);
  encryptedData.resize(ciphertextLen + nonceLen);

  jbyteArray encryptedDataArray = env->NewByteArray(ciphertextLen);
  env->SetByteArrayRegion(encryptedDataArray, 0, ciphertextLen,
                          reinterpret_cast<const jbyte *>(encryptedData.data()));
  return encryptedDataArray;
}
