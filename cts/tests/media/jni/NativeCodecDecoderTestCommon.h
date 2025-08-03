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

#ifndef MEDIACTSNATIVE_NATIVE_CODEC_DECODER_TEST_COMMON_H
#define MEDIACTSNATIVE_NATIVE_CODEC_DECODER_TEST_COMMON_H

#include <jni.h>

extern jboolean nativeTestSimpleDecode(JNIEnv* env, jobject, jstring jDecoder, jobject surface,
                                       jstring jMediaType, jstring jtestFile, jstring jrefFile,
                                       jint jColorFormat, jfloat jrmsError, jlong jChecksum,
                                       jobject jRetMsg);

extern jboolean nativeTestOnlyEos(JNIEnv* env, jobject, jstring jDecoder, jstring jMediaType,
                                  jstring jtestFile, jint jColorFormat, jobject jRetMsg);

extern jboolean nativeTestFlush(JNIEnv* env, jobject, jstring jDecoder, jobject surface,
                                jstring jMediaType, jstring jtestFile, jint jColorFormat,
                                jobject jRetMsg);

extern jboolean nativeTestSimpleDecodeQueueCSD(JNIEnv* env, jobject, jstring jDecoder,
                                               jstring jMediaType, jstring jtestFile,
                                               jint jColorFormat, jobject jRetMsg);

#endif // MEDIACTSNATIVE_NATIVE_CODEC_DECODER_TEST_COMMON_H
