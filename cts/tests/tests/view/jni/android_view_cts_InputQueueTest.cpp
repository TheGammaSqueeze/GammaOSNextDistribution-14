/*
 * Copyright (C) 2021 The Android Open Source Project
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
 *
 */

#include <android/input.h>
#include <jni.h>
#include <jniAssert.h>

#include <array>
#include <thread>

#define LOG_TAG "InputQueueTest"

bool waitForEvent(JNIEnv *env, jclass /* clazz */, jobject inputQueue) {
    constexpr size_t NUM_TRIES = 5;
    for (size_t i = 0; i < NUM_TRIES; i++) {
        AInputQueue *nativeQueue = AInputQueue_fromJava(env, inputQueue);
        if (nativeQueue != nullptr) {
            int32_t numEvents = AInputQueue_hasEvents(nativeQueue);
            if (numEvents > 0) {
                return true;
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    return false;
}

void inputQueueTest(JNIEnv *env, jclass /* clazz */, jobject inputQueue) {
    AInputQueue *nativeQueue = AInputQueue_fromJava(env, inputQueue);
    ASSERT(nativeQueue != nullptr, "Native input queue not returned");
    AInputEvent *event = nullptr;
    ASSERT(AInputQueue_getEvent(nativeQueue, &event) >= 0, "getEvent did not succeed");
    ASSERT(AInputEvent_getType(event) == AINPUT_EVENT_TYPE_MOTION, "Wrong event type");
    ASSERT(AKeyEvent_getAction(event) == AKEY_EVENT_ACTION_DOWN, "Wrong action");
    AInputQueue_finishEvent(nativeQueue, event, true);
}

const std::array<JNINativeMethod, 2> JNI_METHODS = {{
        {"waitForEvent", "(Landroid/view/InputQueue;)Z", (void *)waitForEvent},
        {"inputQueueTest", "(Landroid/view/InputQueue;)V", (void *)inputQueueTest},
}};

jint register_android_view_cts_InputQueueTest(JNIEnv *env) {
    jclass clazzTest = env->FindClass("android/view/cts/InputQueueTest");
    return env->RegisterNatives(clazzTest, JNI_METHODS.data(), JNI_METHODS.size());
}
