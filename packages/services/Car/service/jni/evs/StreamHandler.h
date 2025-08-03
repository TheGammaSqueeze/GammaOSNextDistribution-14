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
 */
#ifndef SERVICE_JNI_EVS_STREAMHANDLER_H_
#define SERVICE_JNI_EVS_STREAMHANDLER_H_

#include "EvsServiceCallback.h"

#include <aidl/android/hardware/automotive/evs/BnEvsCameraStream.h>
#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/EvsEventDesc.h>
#include <aidl/android/hardware/automotive/evs/IEvsCamera.h>
#include <aidl/android/hardware/graphics/common/HardwareBuffer.h>
#include <android-base/thread_annotations.h>
#include <android/binder_auto_utils.h>

#include <list>

namespace android::automotive::evs {

/*
 * StreamHandler:
 * This class can be used to receive camera imagery from an IEvsCamera implementation.  It will
 * hold onto the most recent image buffer, returning older ones.
 * Note that the video frames are delivered on a background thread, while the control interface
 * is actuated from the applications foreground thread.
 */
class StreamHandler final : public ::aidl::android::hardware::automotive::evs::BnEvsCameraStream {
public:
    StreamHandler(
            const std::shared_ptr<::aidl::android::hardware::automotive::evs::IEvsCamera>& camObj,
            EvsServiceCallback* callback, int maxNumFramesInFlight);
    virtual ~StreamHandler();
    void shutdown();
    bool startStream() EXCLUDES(mLock);
    void blockingStopStream() EXCLUDES(mLock);
    bool isRunning() EXCLUDES(mLock);
    void doneWithFrame(const ::aidl::android::hardware::automotive::evs::BufferDesc& buffer)
            EXCLUDES(mLock);
    void doneWithFrame(int bufferId) EXCLUDES(mLock);

private:
    // Implementation for ::aidl::android::hardware::automotive::evs::IEvsCameraStream
    ::ndk::ScopedAStatus deliverFrame(
            const std::vector<::aidl::android::hardware::automotive::evs::BufferDesc>& buffer)
            override EXCLUDES(mLock);
    ::ndk::ScopedAStatus notify(
            const ::aidl::android::hardware::automotive::evs::EvsEventDesc& event) override
            EXCLUDES(mLock);

    // Values initialized as startup
    std::shared_ptr<::aidl::android::hardware::automotive::evs::IEvsCamera> mEvsCamera;

    // Since we get frames delivered to us asnchronously via the ICarCameraStream interface,
    // we need to protect all member variables that may be modified while we're streaming
    // (ie: those below)
    std::mutex mLock;
    std::condition_variable mCondition;
    bool mRunning GUARDED_BY(mLock) = false;

    // Callbacks to forward EVS events and frames
    EvsServiceCallback* mCallback;

    std::list<::aidl::android::hardware::automotive::evs::BufferDesc> mReceivedBuffers
            GUARDED_BY(mLock);
    int mMaxNumFramesInFlightPerClient;

    // Track number of active streaming clients.
    int mNumClients GUARDED_BY(mLock);
};

}  // namespace android::automotive::evs

#endif  // SERVICE_JNI_EVS_STREAMHANDLER_H_
