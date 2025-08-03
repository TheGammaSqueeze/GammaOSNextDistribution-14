/*
 * Copyright 2020 The Android Open Source Project
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

#include "AppCallbackAudioSink.h"

static const char * const TAG = "AppCallbackAudioSink";

AppCallbackAudioSink::AppCallbackAudioSink(JNIEnv *env, jobject callbackObj) {
    jint rs = env->GetJavaVM(&mJVM);
    mCallbackObj = env->NewGlobalRef(callbackObj);

    jclass callbackClass = env->GetObjectClass(mCallbackObj);
    mMIDonDataReady = env->GetMethodID(callbackClass, "onDataReady", "([FI)V");
}

void AppCallbackAudioSink::init(int numFrames, int numChannels) {
    JNIEnv * env;
    int getEnvStat = mJVM->GetEnv((void **)&env, JNI_VERSION_1_6);

    if (getEnvStat == JNI_EDETACHED) {
        int rs = mJVM->AttachCurrentThread(&env, NULL);
    }

    mAudioDataArrayLength = numChannels * numFrames;
    mAudioDataArray = env->NewFloatArray(mAudioDataArrayLength);
    mAudioDataArray = (jfloatArray)env->NewGlobalRef(mAudioDataArray);

    if (getEnvStat == JNI_EDETACHED) {
        mJVM->DetachCurrentThread();
    }
}

void AppCallbackAudioSink::start() {
}

void AppCallbackAudioSink::stop() {
    JNIEnv * env;
    int getEnvStat = mJVM->GetEnv((void **)&env, JNI_VERSION_1_6);

    if (getEnvStat == JNI_EDETACHED) {
        int rs = mJVM->AttachCurrentThread(&env, NULL);
    }

    releaseJNIResources(env);

    if (getEnvStat == JNI_EDETACHED) {
        mJVM->DetachCurrentThread();
    }
}

void AppCallbackAudioSink::push(float* audioData, int numFrames, int numChannels) {
    // Get the local JNI env
    JNIEnv * env;
    int getEnvStat = mJVM->GetEnv((void **)&env, JNI_VERSION_1_6);

    if (getEnvStat == JNI_EDETACHED) {
        int rs = mJVM->AttachCurrentThread(&env, NULL);
    }

    // put the float* into a jfloatarray
    env->SetFloatArrayRegion(mAudioDataArray, 0, numFrames * numChannels, audioData);
    env->CallVoidMethod(mCallbackObj, mMIDonDataReady, mAudioDataArray, numFrames);

    if (getEnvStat == JNI_EDETACHED) {
        mJVM->DetachCurrentThread();
    }
}

void AppCallbackAudioSink::releaseJNIResources(JNIEnv *env) {
    env->DeleteGlobalRef(mCallbackObj);
}

extern "C" {
JNIEXPORT jlong JNICALL
Java_org_hyphonate_megaaudio_recorder_sinks_AppCallbackAudioSinkProvider_allocOboeSinkN(
        JNIEnv *env, jobject thiz, jobject callback_obj) {
    AppCallbackAudioSink* sink = new AppCallbackAudioSink(env, callback_obj);
    return (jlong)sink;
}

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_recorder_sinks_AppCallbackAudioSinkProvider_releaseJNIResourcesN(
        JNIEnv *env, jobject thiz, jlong oboe_sink) {
    AppCallbackAudioSink* sink = (AppCallbackAudioSink*)oboe_sink;
    sink->releaseJNIResources(env);
}

} // extern "C"
