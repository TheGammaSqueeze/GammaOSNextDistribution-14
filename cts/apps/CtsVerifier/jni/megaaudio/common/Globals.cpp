/*
 * Copyright 2021 The Android Open Source Project
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

#include <android/log.h>

#include <oboe/Oboe.h>
#include <aaudio/AAudioExtensions.h>

static const char * const TAG = "Globals(native)";

using namespace oboe;

extern "C" {

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_common_Globals_setOboeWorkaroundsEnabled(
        JNIEnv *env, jclass clazz, jboolean enabled) {
    oboe::OboeGlobals::setWorkaroundsEnabled(enabled);
}

JNIEXPORT jboolean JNICALL
Java_org_hyphonate_megaaudio_common_Globals_isMMapSupported(JNIEnv *env, jclass clazz) {
    return AAudioExtensions::getInstance().isMMapSupported();
}

JNIEXPORT jboolean JNICALL
Java_org_hyphonate_megaaudio_common_Globals_isMMapExclusiveSupported(JNIEnv *env, jclass clazz) {
    return AAudioExtensions::getInstance().isMMapExclusiveSupported();
}

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_common_Globals_setMMapEnabled(
        JNIEnv *env, jclass clazz, jboolean enabled) {
    AAudioExtensions::getInstance().setMMapEnabled(enabled);
}

JNIEXPORT jboolean JNICALL
Java_org_hyphonate_megaaudio_common_Globals_isMMapEnabled(
        JNIEnv *env, jclass clazz) {
    return AAudioExtensions::getInstance().isMMapEnabled();
}

}
