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
#include <cstring>

#include <android/log.h>
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#include "JavaSourceProxy.h"

static const char*  TAG = "JavaSourceProxy";
static const char*  sClassNameAudioSource = "org/hyphonate/megaaudio/player/AudioSource";

JavaVM* sJVM;

jmethodID JavaSourceProxy::sMidInit;
jmethodID JavaSourceProxy::sMidStart;
jmethodID JavaSourceProxy::sMidStop;
jmethodID JavaSourceProxy::sMidReset;
jmethodID JavaSourceProxy::sMidPull;

void JavaSourceProxy::initJni(JNIEnv *env) {
    env->GetJavaVM(&sJVM);

    jclass clsAudioSource = env->FindClass(sClassNameAudioSource);

    sMidInit = env->GetMethodID(clsAudioSource, "init", "(II)V");
    sMidStart = env->GetMethodID(clsAudioSource, "start", "()V");
    sMidStop = env->GetMethodID(clsAudioSource, "stop", "()V");
    sMidReset = env->GetMethodID(clsAudioSource, "reset", "()V");
    sMidPull = env->GetMethodID(clsAudioSource, "pull", "([FII)I");
}

JavaSourceProxy::JavaSourceProxy(jobject sourceObj)
    : mSourceObj(nullptr),
      mJavaBuffer(nullptr),
       mIsJVMAttached(false) {
    // Get the local JNI env
    JNIEnv * env = attachToJVM();
    mSourceObj = env->NewGlobalRef(sourceObj);
    detachFromJVM();
}

JavaSourceProxy::~JavaSourceProxy() {
    // Get the local JNI env
    JNIEnv * env = attachToJVM();
    env->DeleteGlobalRef(mSourceObj);
    env->DeleteGlobalRef(mJavaBuffer);
    detachFromJVM();
}

JNIEnv * JavaSourceProxy::attachToJVM() {
   // Get the local JNI env
    JNIEnv * env;
    int getEnvStat = sJVM->GetEnv((void **)&env, JNI_VERSION_1_6);

    if (getEnvStat == JNI_EDETACHED) {
        /*int rs = */sJVM->AttachCurrentThread(&env, NULL);
        mIsJVMAttached = true;
    }

    return env;
}

void JavaSourceProxy::detachFromJVM() {
    if (mIsJVMAttached) {
        sJVM->DetachCurrentThread();
    }
}

void JavaSourceProxy::init(int numFrames, int numChans) {
    JNIEnv * env = attachToJVM();

    mJavaBuffer = env->NewFloatArray(numFrames * numChans);
    mJavaBuffer = (jfloatArray)env->NewGlobalRef(mJavaBuffer);

    detachFromJVM();
}

void JavaSourceProxy::start() {
    JNIEnv * env = attachToJVM();
    env->CallVoidMethod(mSourceObj, sMidStart);
    detachFromJVM();
}

void JavaSourceProxy::stop() {
    JNIEnv * env = attachToJVM();
    env->CallVoidMethod(mSourceObj, sMidStop);
    detachFromJVM();
}

void JavaSourceProxy::reset() {
    JNIEnv * env = attachToJVM();
    env->CallVoidMethod(mSourceObj, sMidReset);
    detachFromJVM();
}

int JavaSourceProxy::pull(float* buffer, int numFrames, int numChans) {
    JNIEnv * env = attachToJVM();

    int numFilledFrames = env->CallIntMethod(mSourceObj, sMidPull, mJavaBuffer, numFrames, numChans);
    memcpy(buffer,
        env->GetFloatArrayElements(mJavaBuffer, 0),
        (numFilledFrames * numChans) * sizeof(float));

    detachFromJVM();

    return numFilledFrames;
}

extern "C" {
JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_player_JavaSourceProxy_initN(JNIEnv *env, jclass clazz) {
    JavaSourceProxy::initJni(env);
}

JNIEXPORT jlong JNICALL
Java_org_hyphonate_megaaudio_player_JavaSourceProxy_allocNativeSource(JNIEnv *env, jclass clazz, jobject javaSource) {
    ALOGI("allocNativeSource(%p)", javaSource);
    return (jlong)(new JavaSourceProxy(javaSource));
}

} // extern "C"
