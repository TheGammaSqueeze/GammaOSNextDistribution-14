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

#include "EvsServiceContext.h"
#ifdef __TEST__
#include "MockEvsServiceFactory.h"
#endif
#include <android-base/logging.h>
#include <nativehelper/JNIHelp.h>

#include <jni.h>

namespace {

using ::android::automotive::evs::EvsServiceContext;
#ifdef __TEST__
using ::android::automotive::evs::MockEvsServiceFactory;
using ::android::automotive::evs::MockLinkUnlinkToDeath;
#endif

// EvsHalWrapperImpl class
constexpr const char kCarEvsServiceClassName[] = "com/android/car/evs/EvsHalWrapperImpl";

/*
 * Connects to the Extended View System service
 */
jboolean connectToHalServiceIfNecessary(JNIEnv* env, jobject thiz, jlong handle) {
    EvsServiceContext* ctxt = reinterpret_cast<EvsServiceContext*>(handle);
    if (!ctxt) {
        LOG(ERROR) << "The service context is invalid.";
        return JNI_FALSE;
    }

    if (ctxt->isAvailable()) {
        LOG(DEBUG) << "Service is connected already.";
        return JNI_TRUE;
    }

    LOG(DEBUG) << "Connecting to EVS service";

    // Initializes a new service context with a death handler
    if (!ctxt->initialize(env, thiz)) {
        LOG(ERROR) << "Failed to initialize a service context";
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/*
 * Disconnects from the Extended View System service
 */
void disconnectFromHalService(JNIEnv*, jobject, jlong handle) {
    EvsServiceContext* ctxt = reinterpret_cast<EvsServiceContext*>(handle);
    if (ctxt == nullptr || !ctxt->isAvailable()) {
        LOG(DEBUG) << "Ignores a disconnecting service request with an invalid handle.";
        return;
    }

    // We simply delete a service handle.
    ctxt->deinitialize();
}

/*
 * Returns a consumed frame buffer to EVS service
 */
void returnFrameBuffer(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint bufferId) {
    EvsServiceContext* ctxt = reinterpret_cast<EvsServiceContext*>(handle);
    if (!ctxt) {
        LOG(ERROR) << __FUNCTION__ << ": EVS service context is not available.";
        return;
    }

    ctxt->doneWithFrame(bufferId);
}

/*
 * Open the target camera device for the service
 */
jboolean openCamera(JNIEnv* env, jobject /*thiz*/, jlong handle, jstring cameraId) {
    EvsServiceContext* ctxt = reinterpret_cast<EvsServiceContext*>(handle);
    if (!ctxt) {
        LOG(ERROR) << __FUNCTION__ << ": EVS service context is not available.";
        return JNI_FALSE;
    }

    // Attempts to open the target camera device
    const char* id = env->GetStringUTFChars(cameraId, NULL);
    if (!id || !ctxt->openCamera(id)) {
        LOG(ERROR) << "Failed to open a camera device";
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(cameraId, id);
    return JNI_TRUE;
}

/*
 * Close the target camera device
 */
void closeCamera(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    EvsServiceContext* ctxt = reinterpret_cast<EvsServiceContext*>(handle);
    if (!ctxt) {
        LOG(WARNING) << __FUNCTION__ << ": EVS service context is not available.";
    }

    ctxt->closeCamera();
}

/*
 * Request to start a video stream
 */
jboolean startVideoStream(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    EvsServiceContext* ctxt = reinterpret_cast<EvsServiceContext*>(handle);
    if (!ctxt) {
        LOG(ERROR) << __FUNCTION__ << ": EVS service context is not available.";
        return JNI_FALSE;
    }

    return ctxt->startVideoStream() ? JNI_TRUE : JNI_FALSE;
}

/*
 * Request to stop a video stream
 */
void stopVideoStream(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    EvsServiceContext* ctxt = reinterpret_cast<EvsServiceContext*>(handle);
    if (!ctxt) {
        LOG(WARNING) << __FUNCTION__ << ": EVS service context is not available.";
        return;
    }

    ctxt->stopVideoStream();
}

/*
 * Static method to create the service context
 */
jlong createServiceHandle(JNIEnv* env, jclass clazz) {
    JavaVM* vm = nullptr;
    env->GetJavaVM(&vm);
    if (vm == nullptr) {
        jniThrowException(env, "java/lang/IllegalStateException",
                          "Can't initialize the EvsServiceContext because the JavaVM is invalid");
    }

    return reinterpret_cast<jlong>(EvsServiceContext::create(vm, clazz));
}

jlong createServiceHandleForTest([[maybe_unused]] JNIEnv* env, [[maybe_unused]] jclass clazz) {
#ifdef __TEST__
    JavaVM* vm = nullptr;
    env->GetJavaVM(&vm);
    if (vm == nullptr) {
        jniThrowException(env, "java/lang/IllegalStateException",
                          "Can't initialize the EvsServiceContext because the JavaVM is invalid");
    }

    return reinterpret_cast<jlong>(
            EvsServiceContext::create(vm, clazz, std::make_unique<MockEvsServiceFactory>(),
                                      std::make_unique<MockLinkUnlinkToDeath>()));
#else
    return 0L;
#endif
}

void triggerBinderDied([[maybe_unused]] JNIEnv* env, [[maybe_unused]] jobject thiz,
                       [[maybe_unused]] jlong handle) {
#ifdef __TEST__
    EvsServiceContext* ctxt = reinterpret_cast<EvsServiceContext*>(handle);
    if (!ctxt) {
        LOG(WARNING) << __FUNCTION__ << ": EVS service context is not available.";
        return;
    }

    ctxt->triggerBinderDied();
#endif
}

/*
 * Static method to destroy the service context
 */
void destroyServiceHandle(JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    delete reinterpret_cast<EvsServiceContext*>(handle);
}

}  // namespace

namespace android {

jint initializeCarEvsService(JavaVM* vm) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOG(ERROR) << __FUNCTION__ << ": Failed to get the environment.";
        return JNI_ERR;
    }

    // Registers native methods
    static const JNINativeMethod methods[] = {
            {"nativeConnectToHalServiceIfNecessary", "(J)Z",
             reinterpret_cast<void*>(connectToHalServiceIfNecessary)},
            {"nativeDisconnectFromHalService", "(J)V",
             reinterpret_cast<void*>(disconnectFromHalService)},
            {"nativeOpenCamera", "(JLjava/lang/String;)Z", reinterpret_cast<void*>(openCamera)},
            {"nativeCloseCamera", "(J)V", reinterpret_cast<void*>(closeCamera)},
            {"nativeRequestToStartVideoStream", "(J)Z", reinterpret_cast<void*>(startVideoStream)},
            {"nativeRequestToStopVideoStream", "(J)V", reinterpret_cast<void*>(stopVideoStream)},
            {"nativeDoneWithFrame", "(JI)V", reinterpret_cast<void*>(returnFrameBuffer)},
            {"nativeTriggerBinderDied", "(J)V", reinterpret_cast<void*>(triggerBinderDied)},
            {"nativeCreateServiceHandle", "()J", reinterpret_cast<void*>(createServiceHandle)},
            {"nativeCreateServiceHandleForTest", "()J",
             reinterpret_cast<void*>(createServiceHandleForTest)},
            {"nativeDestroyServiceHandle", "(J)V", reinterpret_cast<void*>(destroyServiceHandle)},
    };
    jniRegisterNativeMethods(env, kCarEvsServiceClassName, methods, NELEM(methods));

    return JNI_VERSION_1_6;
}

}  // namespace android
