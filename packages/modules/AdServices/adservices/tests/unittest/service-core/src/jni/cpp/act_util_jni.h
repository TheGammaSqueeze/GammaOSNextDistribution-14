/*
 * Copyright (C) 2023 The Android Open Source Project
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
/* Header for class com_android_adservices_ActJni */

#ifndef ADSERVICES_UNIT_TEST_SERVICE_CORE_JNI_INCLUDE_ACT_UTIL_JNI_H_
#define ADSERVICES_UNIT_TEST_SERVICE_CORE_JNI_INCLUDE_ACT_UTIL_JNI_H_
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_android_adservices_ActJniUtils
 * Method:    checkClientParameters
 * Signature: ([B[B[B[B)Z
 */
JNIEXPORT jboolean JNICALL Java_com_android_adservices_ActJniUtility_checkClientParameters
  (JNIEnv *, jclass, jbyteArray, jbyteArray, jbyteArray, jbyteArray);

/*
 * Class:     com_android_adservices_ActJniUtility
 * Method:    Java_com_android_adservices_ActJniUtility_generateTokensResponse
 * Signature: ([B[B[B[B[B)Z
 */
JNIEXPORT jbyteArray JNICALL Java_com_android_adservices_ActJniUtility_generateTokensResponse
  (JNIEnv *, jclass, jbyteArray, jbyteArray, jbyteArray, jbyteArray, jbyteArray);

#ifdef __cplusplus
}
#endif
#endif
