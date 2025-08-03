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

//#define LOG_NDEBUG 0
#define LOG_TAG "NativeCodecDecoderTest"
#include <log/log.h>

#include "NativeCodecDecoderTestCommon.h"

int registerAndroidMediaV2CtsDecoderTest(JNIEnv* env) {
    const JNINativeMethod methodTable[] = {
            {"nativeTestSimpleDecode",
             "(Ljava/lang/String;Landroid/view/Surface;Ljava/lang/String;Ljava/lang/String;Ljava/"
             "lang/String;IFJLjava/lang/StringBuilder;)Z",
             (void*)nativeTestSimpleDecode},
            {"nativeTestOnlyEos",
             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/StringBuilder;)Z",
             (void*)nativeTestOnlyEos},
            {"nativeTestFlush",
             "(Ljava/lang/String;Landroid/view/Surface;Ljava/lang/String;Ljava/lang/String;ILjava/"
             "lang/StringBuilder;)Z",
             (void*)nativeTestFlush},
            {"nativeTestSimpleDecodeQueueCSD",
             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/StringBuilder;)Z",
             (void*)nativeTestSimpleDecodeQueueCSD},
    };
    jclass c = env->FindClass("android/mediav2/cts/CodecDecoderTest");
    return env->RegisterNatives(c, methodTable, sizeof(methodTable) / sizeof(JNINativeMethod));
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    if (registerAndroidMediaV2CtsDecoderTest(env) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
