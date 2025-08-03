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

#ifndef MEGA_PLAYER_JAVASOURCEPROXY_H
#define MEGA_PLAYER_JAVASOURCEPROXY_H

#include <jni.h>

#include "AudioSource.h"

class JavaSourceProxy: public AudioSource {
public:
    static void initJni(JNIEnv* env);

    JavaSourceProxy(jobject sourceObj);
    virtual ~JavaSourceProxy();

    jobject getJavaSourceObject() { return mSourceObj; }

    // AudioSource virtuals
    virtual void init(int numFrames, int numChans);

    virtual void start();
    virtual void stop();

    virtual void reset();

    virtual int pull(float* buffer, int numFrames, int numChans);

private:
    jobject mSourceObj;

    static jmethodID    sMidInit;
    static jmethodID    sMidStart;
    static jmethodID    sMidStop;
    static jmethodID    sMidReset;
    static jmethodID    sMidPull;

    jfloatArray mJavaBuffer;

    bool mIsJVMAttached;
    JNIEnv * attachToJVM();
    void detachFromJVM();
};

#endif // MEGA_PLAYER_JAVASOURCEPROXY_H
