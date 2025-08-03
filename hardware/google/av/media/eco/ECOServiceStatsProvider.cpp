/*
 * Copyright (C) 2020 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "ECOServiceStatsProvider"
#include <android/binder_manager.h>
#include <eco/ECOServiceStatsProvider.h>
#include <utils/Timers.h>

namespace android {
namespace media {
namespace eco {

ECOServiceStatsProvider::ECOServiceStatsProvider(int32_t width, int32_t height,
                                                 bool isCameraRecording,
                                                 std::shared_ptr<IECOSession>& session,
                                                 const char* name)
      : BnECOServiceStatsProvider(),
        mWidth(width),
        mHeight(height),
        mIsCameraRecording(isCameraRecording),
        mECOSession(session),
        mProviderName(name) {
    ALOGD("%s, construct with w: %d, h: %d, isCameraRecording: %d, ProviderName:%s",
            __func__, mWidth, mHeight, isCameraRecording, name);
}

ScopedAStatus ECOServiceStatsProvider::getType(int32_t* _aidl_return) {
    *_aidl_return = STATS_PROVIDER_TYPE_VIDEO_ENCODER;
    return ScopedAStatus::ok();
}

ScopedAStatus ECOServiceStatsProvider::getName(std::string* _aidl_return) {
    *_aidl_return = std::string(mProviderName);
    return ScopedAStatus::ok();
}

ScopedAStatus ECOServiceStatsProvider::getECOSession(::ndk::SpAIBinder* _aidl_return) {
    *_aidl_return = mECOSession->asBinder();
    return ScopedAStatus::ok();
}

ScopedAStatus ECOServiceStatsProvider::isCameraRecording(bool* _aidl_return) {
    *_aidl_return = mIsCameraRecording;
    return ScopedAStatus::ok();
}

void ECOServiceStatsProvider::binderDied(const std::weak_ptr<AIBinder>& /* who */) {}

bool ECOServiceStatsProvider::updateStats(const ECOData& data) {
    bool ret = false;
    if (mECOSession) {
        ScopedAStatus status = mECOSession->pushNewStats(data, &ret);
        return ret;
    }
    return ret;
}

bool ECOServiceStatsProvider::addProvider() {
    bool ret = false;
    if (mECOSession) {
        ECOData providerConfig(ECOData::DATA_TYPE_STATS_PROVIDER_CONFIG, systemTime() / 1000);
        mECOSession->addStatsProvider(fromBinder(asBinder()), providerConfig, &ret);
        return ret;
    }
    return ret;
}

bool ECOServiceStatsProvider::removeProvider() {
    bool ret = false;
    if (mECOSession) {
        mECOSession->removeStatsProvider(fromBinder(asBinder()), &ret);
        return ret;
    }
    return ret;
}

std::shared_ptr<ECOServiceStatsProvider> ECOServiceStatsProvider::create(int32_t width,
                                                                         int32_t height,
                                                                         bool isCameraRecording,
                                                                         const char* name) {
    std::shared_ptr<IECOService> service = IECOService::fromBinder(
            ndk::SpAIBinder(AServiceManager_waitForService("media.ecoservice")));
    if (service == nullptr) {
        ALOGE("Failed to connect to ecoservice");
        return nullptr;
    }

    ALOGI("Connected to ecoservice");

    // Obtain the ECOSession and add the listener to the service.
    std::shared_ptr<IECOSession> session = nullptr;

    service->obtainSession(width, height, isCameraRecording, &session);

    if (session == nullptr) {
        ALOGE("Failed to obtain an ECO session");
        return nullptr;
    }
    ALOGI("Obtained an ECO session");

    return ndk::SharedRefBase::make<ECOServiceStatsProvider>(width, height, isCameraRecording,
                                                             session, name);
}

float ECOServiceStatsProvider::getFramerate(int64_t currTimestamp) {
    float framerate;
    int64_t timeInterval = currTimestamp - mLastFrameTimestamp;
    if (timeInterval == 0) {
        framerate = 0.0;
    } else {
        framerate = 1E6 / (currTimestamp - mLastFrameTimestamp);
    }
    mLastFrameTimestamp = currTimestamp;
    return framerate;
}

}  // namespace eco
}  // namespace media
}  // namespace android
