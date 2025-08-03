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
#include <jni.h>

/* Header for class HpkeJni */
#ifndef ADSERVICES_SERVICE_CORE_JNI_INCLUDE_OHTTP_JNI_H_
#define ADSERVICES_SERVICE_CORE_JNI_INCLUDE_OHTTP_JNI_H_
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     OhttpJniWrapper
 * Method:    hpkeKemDhkemX25519HkdfSha256
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeKemDhkemX25519HkdfSha256(
    JNIEnv *, jclass);
/*
 * Class:     OhttpJniWrapper
 * Method:    hpkeKdfHkdfSha256
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeKdfHkdfSha256(JNIEnv *,
                                                                    jclass);

/*
 * Class:     OhttpJniWrapper
 * Method:    hpkeAeadAes128Gcm
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeAeadAes128Gcm(JNIEnv *,
                                                                    jclass);

/*
 * Class:     OhttpJniWrapper
 * Method:    hpkeAeadAes256Gcm
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeAeadAes256Gcm(JNIEnv *,
                                                                    jclass);

/*
 * Class:     OhttpJniWrapper
 * Method:    hkdfSha256MessageDigest
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hkdfSha256MessageDigest(
    JNIEnv *env, jclass);

/*
 * Class:     OhttpJniWrapper
 * Method:    hpkeCtxFree
 * Signature: (J)
 */
JNIEXPORT void JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeCtxFree(JNIEnv *env,
                                                              jclass,
                                                              jlong hpkeCtxRef);

/*
 * Class:     OhttpJniWrapper
 * Method:    hpkeCtxNew
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeCtxNew(JNIEnv *env,
                                                             jclass);

/*
 * Class:     OhttpJniWrapper
 * Method:    hpkeCtxSetupSenderWithSeed
 * Signature: (JJJJ[B[B[B)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeCtxSetupSenderWithSeed(
    JNIEnv *env, jclass, jlong senderHpkeCtxRef, jlong evpKemRef,
    jlong evpKdfRef, jlong evpAeadRef, jbyteArray publicKeyArray,
    jbyteArray infoArray, jbyteArray seedArray);

/*
 * Class:     OhttpJniWrapper
 * Method:    hpkeSetupRecipient
 * Signature: (JJJJ[B[B[B)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeSetupRecipient(
    JNIEnv *env, jclass,
    jlong hpkeCtxRef,
    jlong evpKemRef,
    jlong evpKdfRef,
    jlong evpAeadRef,
    jbyteArray privKeyArray,
    jbyteArray encArray,
    jbyteArray infoArray);

/*
 * Class:     OhttpJniWrapper
 * Method:    gatewayDecrypt
 * Signature: (JJJJ[B)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_gatewayDecrypt(
    JNIEnv *env, jclass,
    jlong hpkeCtxRef,
    jlong evpKemRef,
    jlong evpKdfRef,
    jlong evpAeadRef,
    jbyteArray encryptedDataArray);

/*
 * Class:     OhttpJniWrapper
 * Method:    hpkeCtxSeal
 * Signature: (J[B[B)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeCtxSeal(
    JNIEnv *env, jclass, jlong senderHpkeCtxRef, jbyteArray plaintextArray,
    jbyteArray aadArray);

/*
 * Class:     OhttpJniWrapper
 * Method:    hpkeExport
 * Signature: (J[BI)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeExport(JNIEnv* env, jclass, jlong hpkeCtxRef,
                                                       jbyteArray exporterCtxArray, jint length);
/*
 * Class:     OhttpJniWrapper
 * Method:    hkdfExtract
 * Signature: (J[B[B)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hkdfExtract(
    JNIEnv *env, jclass, jlong hkdfMd, jbyteArray secret, jbyteArray salt);

/*
 * Class:     OhttpJniWrapper
 * Method:    hkdfExpand
 * Signature: (J[B[BI)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hkdfExpand(
    JNIEnv *env, jclass, jlong hkdfMd, jbyteArray prkArray,
    jbyteArray infoArray, jint key_len);

/*
 * Class:     OhttpJniWrapper
 * Method:    aeadOpen
 * Signature: (J[B[B[B)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_aeadOpen(
    JNIEnv *env, jclass, jlong evpAeadRef, jbyteArray keyArray,
    jbyteArray nonceArray, jbyteArray cipherTextArray);


/*
 * Class:     OhttpJniWrapper
 * Method:    aeadOpen
 * Signature: (J[B[B[B)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_aeadSeal(
    JNIEnv *env,
    jclass,
    jlong evpAeadRef,
    jbyteArray keyArray,
    jbyteArray nonceArray,
    jbyteArray plainTextArray);

#ifdef __cplusplus
}
#endif
#endif  // ADSERVICES_SERVICE_CORE_JNI_INCLUDE_OHTTP_JNI_H_
