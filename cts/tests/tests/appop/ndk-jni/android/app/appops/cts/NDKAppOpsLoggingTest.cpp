/*
 * Copyright (C) 2019 The Android Open Source Project
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
#include <aaudio/AAudio.h>

// Start and stop audio stream from native code
extern "C" JNIEXPORT void JNICALL
Java_android_app_appops_cts_AppOpsLoggingTestKt_nativeStartStopAudioRecord(JNIEnv* env, jobject obj,
        jboolean jIsShared, jboolean jIsLowLatency, jstring jPackageName, jstring jAttributionTag) {
    jclass exception = env->FindClass("java/lang/RuntimeException");

    const char *nativePackageName = env->GetStringUTFChars(jPackageName, 0);

    const char *nativeAttributionTag;
    if (jAttributionTag != nullptr) {
        nativeAttributionTag = env->GetStringUTFChars(jAttributionTag, 0);
    } else {
        nativeAttributionTag = nullptr;
    }

    AAudioStreamBuilder *builder;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        env->ThrowNew(exception, "Failed to create audio stream builder");
        goto release_jni_strings_then_return;
    }

    // Just some valid parameters
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);

    if (jIsLowLatency) {
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    } else {
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_NONE);
    }

    if (jIsShared) {
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    } else {
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    }
    // Set app-ops logging related parameters
    AAudioStreamBuilder_setPackageName(builder, nativePackageName);
    AAudioStreamBuilder_setAttributionTag(builder, nativeAttributionTag);

    AAudioStream *stream;
    result = AAudioStreamBuilder_openStream(builder, &stream);
    if (result != AAUDIO_OK) {
        env->ThrowNew(exception, "Failed to create audio stream");
        goto delete_builder_then_return;
    }

    result = AAudioStream_requestStart(stream);
    if (result != AAUDIO_OK) {
        env->ThrowNew(exception, "Failed to start audio stream");
        goto close_stream_then_return;
    }

close_stream_then_return:
    AAudioStream_requestStop(stream);
    AAudioStream_close(stream);

delete_builder_then_return:
    AAudioStreamBuilder_delete(builder);

release_jni_strings_then_return:
    env->ReleaseStringUTFChars(jPackageName, nativePackageName);

    if (jAttributionTag != nullptr) {
        env->ReleaseStringUTFChars(jAttributionTag, nativeAttributionTag);
    }
}