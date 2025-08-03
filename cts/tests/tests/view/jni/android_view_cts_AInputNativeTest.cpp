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

#define LOG_TAG "AInputNativeTest"

#include <jni.h>
#include <jniAssert.h>
#include <math.h>
#include <array>
#include <cinttypes>
#include <string>

#include <android/input.h>
#include <android/log.h>
#include <nativehelper/JNIHelp.h>

namespace {

static struct MotionEventMethodId {
    jmethodID getDownTime;
    jmethodID getEventTime;
    jmethodID getMetaState;
    jmethodID getAction;
    jmethodID getPointerCount;
    jmethodID getRawX;
    jmethodID getRawY;
    jmethodID getActionButton;
    jmethodID getClassification;
} gMotionEventMethodIds;

static struct KeyEventMethodId {
    jmethodID getDownTime;
    jmethodID getEventTime;
    jmethodID getAction;
    jmethodID getKeyCode;
} gKeyEventMethodIds;

static constexpr int64_t NS_PER_MS = 1000000LL;

void nativeMotionEventTest(JNIEnv *env, jclass /* clazz */, jobject obj) {
    const AInputEvent *event = AMotionEvent_fromJava(env, obj);
    jint action = env->CallIntMethod(obj, gMotionEventMethodIds.getAction);
    jlong downTime = env->CallLongMethod(obj, gMotionEventMethodIds.getDownTime) * NS_PER_MS;
    jlong eventTime = env->CallLongMethod(obj, gMotionEventMethodIds.getEventTime) * NS_PER_MS;
    jint metaState = env->CallIntMethod(obj, gMotionEventMethodIds.getMetaState);
    jint pointerCount = env->CallIntMethod(obj, gMotionEventMethodIds.getPointerCount);
    jint actionButton = env->CallIntMethod(obj, gMotionEventMethodIds.getActionButton);
    jint classification = env->CallIntMethod(obj, gMotionEventMethodIds.getClassification);

    ASSERT(AInputEvent_getType(event) == AINPUT_EVENT_TYPE_MOTION, "Wrong event type %d.",
           AInputEvent_getType(event));

    ASSERT(action == AMotionEvent_getAction(event), "Wrong action %d not equal to %d",
           AMotionEvent_getAction(event), action);

    ASSERT(downTime == AMotionEvent_getDownTime(event),
           "Wrong downTime %" PRId64 " not equal to %" PRId64, AMotionEvent_getDownTime(event),
           downTime);

    ASSERT(eventTime == AMotionEvent_getEventTime(event),
           "Wrong eventTime %" PRId64 " not equal to %" PRId64, AMotionEvent_getEventTime(event),
           eventTime);

    ASSERT(metaState == AMotionEvent_getMetaState(event), "Wrong metaState %d not equal to %d",
           AMotionEvent_getMetaState(event), metaState);

    ASSERT(AMotionEvent_getPointerCount(event) == pointerCount,
           "Wrong pointer count %zu not equal to %d", AMotionEvent_getPointerCount(event),
           pointerCount);

    ASSERT(AMotionEvent_getActionButton(event) == actionButton,
           "Wrong action button %d not equal to %d", AMotionEvent_getActionButton(event),
           actionButton);

    ASSERT(AMotionEvent_getClassification(event) == classification,
           "Wrong classification %hhd not equal to %hhd", AMotionEvent_getClassification(event),
           classification);

    for (int i = 0; i < pointerCount; i++) {
        jfloat rawX = env->CallFloatMethod(obj, gMotionEventMethodIds.getRawX, i);
        jfloat rawY = env->CallFloatMethod(obj, gMotionEventMethodIds.getRawY, i);
        ASSERT(fabs(rawX - AMotionEvent_getRawX(event, i)) == 0.0f, "Point X:%f not same as %f",
               AMotionEvent_getRawX(event, i), rawX);

        ASSERT(fabs(rawY - AMotionEvent_getRawY(event, i)) == 0.0f, "Point Y:%f not same as %f",
               AMotionEvent_getRawY(event, i), rawY);
    }
    AInputEvent_release(event);
}

jobject obtainMotionEventCopyFromNative(JNIEnv *env, jclass /* clazz */, jobject obj) {
    const AInputEvent *event = AMotionEvent_fromJava(env, obj);
    jobject motionEvent = AInputEvent_toJava(env, event);
    AInputEvent_release(event);
    return motionEvent;
}

jobject obtainKeyEventCopyFromNative(JNIEnv *env, jclass /* clazz */, jobject obj) {
    const AInputEvent *event = AKeyEvent_fromJava(env, obj);
    jobject keyEvent = AInputEvent_toJava(env, event);
    AInputEvent_release(event);
    return keyEvent;
}

void nativeKeyEventTest(JNIEnv *env, jclass /* clazz */, jobject obj) {
    const AInputEvent *event = AKeyEvent_fromJava(env, obj);
    jint action = env->CallIntMethod(obj, gKeyEventMethodIds.getAction);
    jlong downTime = env->CallLongMethod(obj, gKeyEventMethodIds.getDownTime) * NS_PER_MS;
    jlong eventTime = env->CallLongMethod(obj, gKeyEventMethodIds.getEventTime) * NS_PER_MS;
    jint keyCode = env->CallIntMethod(obj, gKeyEventMethodIds.getKeyCode);

    ASSERT(AInputEvent_getType(event) == AINPUT_EVENT_TYPE_KEY, "Wrong event type %d.",
           AInputEvent_getType(event));

    ASSERT(action == AKeyEvent_getAction(event), "Wrong action %d not equal to %d",
           AKeyEvent_getAction(event), action);

    ASSERT(downTime == AKeyEvent_getDownTime(event),
           "Wrong downTime %" PRId64 " not equal to %" PRId64, AKeyEvent_getDownTime(event),
           downTime);

    ASSERT(eventTime == AKeyEvent_getEventTime(event),
           "Wrong eventTime %" PRId64 " not equal to %" PRId64, AKeyEvent_getEventTime(event),
           eventTime);

    ASSERT(keyCode == AKeyEvent_getKeyCode(event), "Wrong keyCode %d not equal to %d",
           AKeyEvent_getAction(event), action);

    AInputEvent_release(event);
}

const std::array<JNINativeMethod, 2> JNI_METHODS_MOTION = {{
        {"nativeMotionEventTest", "(Landroid/view/MotionEvent;)V", (void *)nativeMotionEventTest},
        {"obtainMotionEventCopyFromNative",
         "(Landroid/view/MotionEvent;)Landroid/view/MotionEvent;",
         (jobject)obtainMotionEventCopyFromNative},
}};

const std::array<JNINativeMethod, 2> JNI_METHODS_KEY = {{
        {"nativeKeyEventTest", "(Landroid/view/KeyEvent;)V", (void *)nativeKeyEventTest},
        {"obtainKeyEventCopyFromNative", "(Landroid/view/KeyEvent;)Landroid/view/KeyEvent;",
         (jobject)obtainKeyEventCopyFromNative},
}};

} // anonymous namespace

jint register_android_view_cts_AMotionEventNativeTest(JNIEnv *env) {
    jclass clazz = env->FindClass("android/view/MotionEvent");
    gMotionEventMethodIds.getAction = env->GetMethodID(clazz, "getAction", "()I");
    gMotionEventMethodIds.getMetaState = env->GetMethodID(clazz, "getMetaState", "()I");
    gMotionEventMethodIds.getDownTime = env->GetMethodID(clazz, "getDownTime", "()J");
    gMotionEventMethodIds.getEventTime = env->GetMethodID(clazz, "getEventTime", "()J");
    gMotionEventMethodIds.getPointerCount = env->GetMethodID(clazz, "getPointerCount", "()I");
    gMotionEventMethodIds.getRawX = env->GetMethodID(clazz, "getRawX", "(I)F");
    gMotionEventMethodIds.getRawY = env->GetMethodID(clazz, "getRawY", "(I)F");
    gMotionEventMethodIds.getActionButton = env->GetMethodID(clazz, "getActionButton", "()I");
    gMotionEventMethodIds.getClassification = env->GetMethodID(clazz, "getClassification", "()I");
    jclass clazzTest = env->FindClass("android/view/cts/MotionEventTest");
    return env->RegisterNatives(clazzTest, JNI_METHODS_MOTION.data(), JNI_METHODS_MOTION.size());
}

jint register_android_view_cts_AKeyEventNativeTest(JNIEnv *env) {
    jclass clazz = env->FindClass("android/view/KeyEvent");
    gKeyEventMethodIds.getAction = env->GetMethodID(clazz, "getAction", "()I");
    gKeyEventMethodIds.getKeyCode = env->GetMethodID(clazz, "getKeyCode", "()I");
    gKeyEventMethodIds.getDownTime = env->GetMethodID(clazz, "getDownTime", "()J");
    gKeyEventMethodIds.getEventTime = env->GetMethodID(clazz, "getEventTime", "()J");
    jclass clazzTest = env->FindClass("android/view/cts/KeyEventTest");
    return env->RegisterNatives(clazzTest, JNI_METHODS_KEY.data(), JNI_METHODS_KEY.size());
}
