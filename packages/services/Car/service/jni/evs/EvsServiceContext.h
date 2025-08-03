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
#ifndef ANDROID_CARSERVICE_EVS_SERVICE_WRAPPER_H
#define ANDROID_CARSERVICE_EVS_SERVICE_WRAPPER_H

#include "EvsCallbackThread.h"
#include "EvsServiceCallback.h"
#include "IEvsServiceFactory.h"
#include "LinkUnlinkToDeathBase.h"
#include "StreamHandler.h"

#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/EvsEventDesc.h>
#include <aidl/android/hardware/automotive/evs/IEvsCamera.h>
#include <aidl/android/hardware/automotive/evs/IEvsDisplay.h>
#include <aidl/android/hardware/automotive/evs/IEvsEnumerator.h>

#include <mutex>
#include <set>

namespace android::automotive::evs {

class ProdServiceFactory final : public IEvsServiceFactory {
public:
    explicit ProdServiceFactory(const char* serviceName) : mServiceName(serviceName) {}
    ~ProdServiceFactory() = default;

    bool init() override;
    aidl::android::hardware::automotive::evs::IEvsEnumerator* getService() override {
        return mService.get();
    }
    void clear() override { mService.reset(); }

private:
    std::string mServiceName;
    std::shared_ptr<aidl::android::hardware::automotive::evs::IEvsEnumerator> mService;
};

class ProdLinkUnlinkToDeath final : public LinkUnlinkToDeathBase {
public:
    binder_status_t linkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                void* cookie) override;
    binder_status_t unlinkToDeath(AIBinder* binder) override;
    void* getCookie() override;
};

/*
 * This class wraps around HIDL transactions to the Extended View System service
 * and the video stream managements.
 */
class EvsServiceContext final : public EvsServiceCallback {
public:
    static EvsServiceContext* create(JavaVM* vm, jclass clazz);
    static EvsServiceContext* create(JavaVM* vm, jclass clazz,
                                     std::unique_ptr<IEvsServiceFactory> serviceFactory,
                                     std::unique_ptr<LinkUnlinkToDeathBase> linkUnlinkImpl);

    virtual ~EvsServiceContext();

    /*
     * Initializes the service context and connects to the native Extended View
     * System service.
     *
     * @param env A pointer to the JNI environment
     * @param env A reference to CarEvsService object
     * @return false if it fails to connect to the native Extended View System
     *         service or to register a death recipient.
     *         true otherwise.
     */
    bool initialize(JNIEnv* env, jobject thiz) EXCLUDES(mLock);

    /*
     * Deinitialize the service context and releases the resources.
     */
    void deinitialize() EXCLUDES(mLock);

    /*
     * Requests to open a target camera device.
     *
     * @param id a string camera device identifier
     * @return bool false if it has not connected to EVS service, fails to open
     *              a camera device, or fails to initialize a stream handler;
     *              true otherwise.
     */
    bool openCamera(const char* id) EXCLUDES(mLock);

    /*
     * Requests to close an active camera device.
     */
    void closeCamera() EXCLUDES(mLock);

    /*
     * Requests to start a video stream from a successfully opened camera device.
     */
    bool startVideoStream() EXCLUDES(mLock);

    /*
     * Requests to stop an active video stream.
     */
    void stopVideoStream() EXCLUDES(mLock);

    /*
     * Notifies that the client finishes with this buffer.
     *
     * @param frame a consumed frame buffer
     */
    void doneWithFrame(int bufferId) EXCLUDES(mLock);

    /*
     * Tells whether or not we're connected to the Extended View System service
     */
    bool isAvailable() EXCLUDES(mLock) {
        std::lock_guard<std::mutex> lock(mLock);
        return isAvailableLocked();
    }

    bool isAvailableLocked() REQUIRES(mLock) {
        return mServiceFactory != nullptr && mServiceFactory->getService() != nullptr;
    }

    /*
     * Tells whether or not a target camera device is opened
     */
    bool isCameraOpenedLocked() REQUIRES(mLock) {
        return mCamera != nullptr;
    }

    /*
     * Implements EvsServiceCallback methods
     */
    void onNewEvent(const ::aidl::android::hardware::automotive::evs::EvsEventDesc&) override;
    bool onNewFrame(const ::aidl::android::hardware::automotive::evs::BufferDesc&) override;

    /*
     * Triggers a binder died callback.
     */
    void triggerBinderDied();

private:
    EvsServiceContext(JavaVM* vm, JNIEnv* env, jclass clazz,
                      std::unique_ptr<IEvsServiceFactory> serviceFactory,
                      std::unique_ptr<LinkUnlinkToDeathBase> linkUnlinkImpl);

    // Death recipient callback that is called when IEvsEnumerator service dies.
    // The cookie is a pointer to a EvsServiceContext object.
    static void onEvsServiceBinderDied(void* cookie);
    void onEvsServiceDiedImpl();

    // Acquires the camera and the display exclusive ownerships.
    void acquireCameraAndDisplayLocked() REQUIRES(mLock);

    // A mutex to protect shared resources
    mutable std::mutex mLock;

    // A proxy to manage the Extended View System service.
    std::unique_ptr<IEvsServiceFactory> mServiceFactory GUARDED_BY(mLock);

    // A proxy to manage the binder death recipient.
    std::unique_ptr<LinkUnlinkToDeathBase> mLinkUnlinkImpl GUARDED_BY(mLock);

    // A camera device opened for the rearview service
    std::shared_ptr<::aidl::android::hardware::automotive::evs::IEvsCamera> mCamera
            GUARDED_BY(mLock);

    // A handler of a video stream from the rearview camera device
    std::shared_ptr<StreamHandler> mStreamHandler GUARDED_BY(mLock);

    // Extended View System display handle.  This would not be used but held by
    // us to prevent other EVS clients from using EvsDisplay.
    std::shared_ptr<::aidl::android::hardware::automotive::evs::IEvsDisplay> mDisplay;

    // Java VM
    JavaVM* mVm;

    // Background thread to handle callbacks from the native Extended View
    // System service
    EvsCallbackThread mCallbackThread;

    // Reference to CarEvsService object
    jobject mCarEvsServiceObj;

    // CarEvsService object's method to handle the accidental death of the
    // native Extended View System service
    jmethodID mDeathHandlerMethodId;

    // CarEvsService object's method to handle a new frame buffer
    jmethodID mFrameHandlerMethodId;

    // CarEvsService object's method to handle a new stream event
    jmethodID mEventHandlerMethodId;

    // Bookkeeps descriptors of received frame buffer IDs.
    std::set<int> mBufferRecords GUARDED_BY(mLock);

    // A name of the camera device currently in use.
    std::string mCameraIdInUse;

    // List of available camera devices
    std::vector<::aidl::android::hardware::automotive::evs::CameraDesc> mCameraList
            GUARDED_BY(mLock);

    // Service name for EVS enumerator
    static const char* kServiceName;

    // Maximum number of frames CarEvsService can hold.  This number has been
    // chosen heuristically.
    static constexpr int kMaxNumFramesInFlight = 10;

    // EVS service reserves a display ID 255 to allow the clients to open the main
    // display exclusively.
    static constexpr uint8_t kExclusiveMainDisplayId = 0xFF;
};

}  // namespace android::automotive::evs

#endif  // ANDROID_CARSERVICE_EVS_SERVICE_WRAPPER_H
