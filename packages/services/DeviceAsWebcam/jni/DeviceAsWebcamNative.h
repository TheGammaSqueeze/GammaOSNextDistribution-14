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

#pragma once

#include <jni.h>
#include <thread>

namespace android {
namespace webcam {

typedef struct {
    jmethodID setStreamConfig;
    jmethodID startStreaming;
    jmethodID stopStreaming;
    jmethodID returnImage;
    jmethodID stopService;
} JavaMethods;

/**
 * Container class for java interactions. All interaction to/from java must happen from this class.
 */
class DeviceAsWebcamNative {
  public:
    static const JNINativeMethod sMethods[];
    static int registerJNIMethods(JNIEnv* e, JavaVM* jvm);

    static JavaVM* kJVM;

    // Native implementations of Java Methods.
    static jint com_android_DeviceAsWebcam_encodeImage(JNIEnv* env, jobject thiz,
                                                       jobject hardwareBuffer, jlong timestamp,
                                                       jint rotation);
    static jint com_android_DeviceAsWebcam_setupServicesAndStartListening(JNIEnv*, jobject,
                                                                          jobjectArray);
    static jboolean com_android_DeviceAsWebcam_shouldStartService(JNIEnv*, jclass, jobjectArray);
    static void com_android_DeviceAsWebcam_onDestroy(JNIEnv*, jobject);

    // Methods that call back into java code. The method signatures match their java counterparts
    // All threads calling these functions must be bound to kJVM and pass their JNIEnv to
    // these functions
    static void setStreamConfig(jobject thiz, bool mjpeg, uint32_t width, uint32_t height,
                                uint32_t fps);
    static void startStreaming(jobject thiz);
    static void stopStreaming(jobject thiz);
    static void returnImage(jobject thiz, long timestamp);
    static void stopService(jobject thiz);

    // Utility method to get JNIEnv associated with the current thread or abort the program. Care
    // must be taken to only call this from a thread that is attached to kJVM as it will
    // terminate the program otherwise.
    static JNIEnv* getJNIEnvOrAbort();

    /**
     * Returns a std::thread can call Java methods in the current JVM.
     * There is a non-trivial cost to attaching and detaching threads to the JVM, these
     * threads must not be spammed. Better to have a long running thread that is attached instead.
     *
     * Usage:
     *  std::thread myThread = createJniAttachedThread([] () -> {
     *      // Thread logic that might call into java
     *      ...
     *  });
     */
    template <class F, typename... Args>
    static std::thread createJniAttachedThread(F f, Args&&... args) {
        return std::thread([f, args...]() mutable {
            // Attach Thread to JVM for java calls
            JNIEnv* env;
            ScopedAttach _jniScope(&env);
            // Call the passed function
            std::invoke(f, std::forward<Args...>(args...));
            // Thread detaches from the JVM automatically when _jniScope goes out of scope
        });
    }

    // Methods that call back into java code
    // must only be called via their corresponding public methods.
     static JavaMethods kJavaMethods;

    /**
     * Class to Provide a RAII style means for attaching the current thread to the JVM. The thread
     * is attached to the JVM when this object is created, and detached when the object goes out of
     * scope.
     */
    class ScopedAttach {
      public:
        explicit ScopedAttach(JNIEnv** env) { kJVM->AttachCurrentThread(env, nullptr); }
        ~ScopedAttach() { kJVM->DetachCurrentThread(); }
    };
};

}  // namespace webcam
}  // namespace android
