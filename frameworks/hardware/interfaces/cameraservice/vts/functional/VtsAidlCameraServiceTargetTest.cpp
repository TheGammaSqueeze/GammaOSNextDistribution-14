/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define LOG_TAG "VtsAidlCameraServiceTargetTest"
// #define LOG_NDEBUG 0

#include <CameraMetadata.h>
#include <aidl/android/frameworks/cameraservice/device/BnCameraDeviceCallback.h>
#include <aidl/android/frameworks/cameraservice/device/CaptureRequest.h>
#include <aidl/android/frameworks/cameraservice/device/ICameraDeviceUser.h>
#include <aidl/android/frameworks/cameraservice/device/OutputConfiguration.h>
#include <aidl/android/frameworks/cameraservice/device/StreamConfigurationMode.h>
#include <aidl/android/frameworks/cameraservice/device/SubmitInfo.h>
#include <aidl/android/frameworks/cameraservice/device/TemplateId.h>
#include <aidl/android/frameworks/cameraservice/service/BnCameraServiceListener.h>
#include <aidl/android/frameworks/cameraservice/service/CameraStatusAndId.h>
#include <aidl/android/frameworks/cameraservice/service/ICameraService.h>
#include <aidlcommonsupport/NativeHandle.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android/log.h>
#include <fmq/AidlMessageQueue.h>
#include <gtest/gtest.h>
#include <hidl/GtestPrinter.h>
#include <media/NdkImageReader.h>
#include <stdint.h>
#include <system/camera_metadata.h>
#include <system/graphics.h>
#include <utils/Condition.h>
#include <utils/Mutex.h>
#include <utils/StrongPointer.h>

#include <algorithm>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace android {

using ::aidl::android::frameworks::cameraservice::device::BnCameraDeviceCallback;
using ::aidl::android::frameworks::cameraservice::device::CaptureMetadataInfo;
using ::aidl::android::frameworks::cameraservice::device::CaptureRequest;
using ::aidl::android::frameworks::cameraservice::device::CaptureResultExtras;
using ::aidl::android::frameworks::cameraservice::device::ErrorCode;
using ::aidl::android::frameworks::cameraservice::device::ICameraDeviceUser;
using ::aidl::android::frameworks::cameraservice::device::OutputConfiguration;
using ::aidl::android::frameworks::cameraservice::device::PhysicalCaptureResultInfo;
using ::aidl::android::frameworks::cameraservice::device::StreamConfigurationMode;
using ::aidl::android::frameworks::cameraservice::device::SubmitInfo;
using ::aidl::android::frameworks::cameraservice::device::TemplateId;
using ::aidl::android::frameworks::cameraservice::service::BnCameraServiceListener;
using ::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus;
using ::aidl::android::frameworks::cameraservice::service::CameraStatusAndId;
using ::aidl::android::frameworks::cameraservice::service::ICameraService;
using ::aidl::android::hardware::common::fmq::MQDescriptor;
using ::android::hardware::camera::common::helper::CameraMetadata;
using ::ndk::SpAIBinder;

using AidlCameraMetadata = ::aidl::android::frameworks::cameraservice::device::CameraMetadata;
using RequestMetadataQueue = AidlMessageQueue<int8_t, SynchronizedReadWrite>;

static constexpr int kCaptureRequestCount = 10;
static constexpr int kVGAImageWidth = 640;
static constexpr int kVGAImageHeight = 480;
static constexpr int kNumRequests = 4;

#define IDLE_TIMEOUT 2000000000  // ns

class CameraServiceListener : public BnCameraServiceListener {
    std::map<std::string, CameraDeviceStatus> mCameraStatuses;
    // map: logical camera id -> set of unavailable physical camera ids
    std::map<std::string, std::set<std::string>> mUnavailablePhysicalCameras;
    mutable Mutex mLock;

   public:
    ~CameraServiceListener() override = default;

    ndk::ScopedAStatus onStatusChanged(CameraDeviceStatus in_status,
                                       const std::string& in_cameraId) override {
        Mutex::Autolock l(mLock);
        mCameraStatuses[in_cameraId] = in_status;
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus onPhysicalCameraStatusChanged(
        CameraDeviceStatus in_status, const std::string& in_cameraId,
        const std::string& in_physicalCameraId) override {
        Mutex::Autolock l(mLock);
        ALOGI("%s: Physical camera %s : %s status changed to %d", __FUNCTION__, in_cameraId.c_str(),
              in_physicalCameraId.c_str(), in_status);

        EXPECT_NE(mCameraStatuses.find(in_cameraId), mCameraStatuses.end());
        EXPECT_EQ(mCameraStatuses[in_cameraId], CameraDeviceStatus::STATUS_PRESENT);

        if (in_status == CameraDeviceStatus::STATUS_PRESENT) {
            auto res = mUnavailablePhysicalCameras[in_cameraId].erase(in_physicalCameraId);
            EXPECT_EQ(res, 1);
        } else {
            auto res = mUnavailablePhysicalCameras[in_cameraId].emplace(in_physicalCameraId);
            EXPECT_TRUE(res.second);
        }
        return ndk::ScopedAStatus::ok();
    }

    void initializeStatuses(const std::vector<CameraStatusAndId>& statuses) {
        Mutex::Autolock l(mLock);

        for (auto& status : statuses) {
            mCameraStatuses[status.cameraId] = status.deviceStatus;
            for (auto& physicalId : status.unavailPhysicalCameraIds) {
                mUnavailablePhysicalCameras[status.cameraId].emplace(physicalId);
            }
        }
    }
};

// ICameraDeviceCallback implementation
class CameraDeviceCallback : public BnCameraDeviceCallback {
   public:
    enum LocalCameraDeviceStatus {
        IDLE,
        ERROR,
        RUNNING,
        RESULT_RECEIVED,
        UNINITIALIZED,
        REPEATING_REQUEST_ERROR,
    };

   protected:
    bool mError = false;
    LocalCameraDeviceStatus mLastStatus = UNINITIALIZED;
    mutable std::vector<LocalCameraDeviceStatus> mStatusesHit;
    // stream id -> prepared count;
    mutable std::unordered_map<int, int> mStreamsPreparedCount;
    mutable Mutex mLock;
    mutable Condition mStatusCondition;
    mutable Condition mPreparedCondition;

   public:
    CameraDeviceCallback() {}

    ndk::ScopedAStatus onDeviceError(ErrorCode in_errorCode,
                                     const CaptureResultExtras& /*in_resultExtras*/) override {
        ALOGE("%s: onDeviceError occurred with: %d", __FUNCTION__, static_cast<int>(in_errorCode));
        Mutex::Autolock l(mLock);
        mError = true;
        mLastStatus = ERROR;
        mStatusesHit.push_back(mLastStatus);
        mStatusCondition.broadcast();
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus onDeviceIdle() override {
        Mutex::Autolock l(mLock);
        mLastStatus = IDLE;
        mStatusesHit.push_back(mLastStatus);
        mStatusCondition.broadcast();
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus onCaptureStarted(const CaptureResultExtras& /*in_resultExtras*/,
                                        int64_t /*in_timestamp*/) override {
        Mutex::Autolock l(mLock);
        mLastStatus = RUNNING;
        mStatusesHit.push_back(mLastStatus);
        mStatusCondition.broadcast();
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus onResultReceived(
        const CaptureMetadataInfo& /*in_result*/, const CaptureResultExtras& /*in_resultExtras*/,
        const std::vector<PhysicalCaptureResultInfo>& /*in_physicalCaptureResultInfos*/) override {
        Mutex::Autolock l(mLock);
        mLastStatus = RESULT_RECEIVED;
        mStatusesHit.push_back(mLastStatus);
        mStatusCondition.broadcast();
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus onRepeatingRequestError(int64_t /*in_lastFrameNumber*/,
                                               int32_t /*in_repeatingRequestId*/) override {
        Mutex::Autolock l(mLock);
        mLastStatus = REPEATING_REQUEST_ERROR;
        mStatusesHit.push_back(mLastStatus);
        mStatusCondition.broadcast();
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus onPrepared(int32_t streamId) override {
        Mutex::Autolock l(mLock);
        if (mStreamsPreparedCount.find(streamId) == mStreamsPreparedCount.end()) {
            mStreamsPreparedCount[streamId] = 0;
        }
        mStreamsPreparedCount[streamId]++;
        mPreparedCondition.broadcast();
        return ndk::ScopedAStatus::ok();
    }

    bool waitForPreparedCount(int streamId, int count) const {
        Mutex::Autolock l(mLock);
        if ((mStreamsPreparedCount.find(streamId) != mStreamsPreparedCount.end()) &&
            (mStreamsPreparedCount[streamId] == count)) {
            return true;
        }

        while ((mStreamsPreparedCount.find(streamId) == mStreamsPreparedCount.end()) ||
               (mStreamsPreparedCount[streamId] < count)) {
            if (mPreparedCondition.waitRelative(mLock, IDLE_TIMEOUT) != android::OK) {
                return false;
            }
        }
        return (mStreamsPreparedCount[streamId] == count);
    }

    // Test helper functions:
    bool waitForStatus(LocalCameraDeviceStatus status) const {
        Mutex::Autolock l(mLock);
        if (mLastStatus == status) {
            return true;
        }

        while (std::find(mStatusesHit.begin(), mStatusesHit.end(), status) == mStatusesHit.end()) {
            if (mStatusCondition.waitRelative(mLock, IDLE_TIMEOUT) != android::OK) {
                mStatusesHit.clear();
                return false;
            }
        }
        mStatusesHit.clear();

        return true;
    }

    bool waitForIdle() const { return waitForStatus(IDLE); }
};

static bool convertFromAidlCloned(const AidlCameraMetadata& metadata, CameraMetadata* rawMetadata) {
    const camera_metadata* buffer = (camera_metadata_t*)(metadata.metadata.data());
    size_t expectedSize = metadata.metadata.size();
    int ret = validate_camera_metadata_structure(buffer, &expectedSize);
    if (ret == OK || ret == CAMERA_METADATA_VALIDATION_SHIFTED) {
        *rawMetadata = buffer;  // assignment operator clones
    } else {
        ALOGE("%s: Malformed camera metadata received from caller", __FUNCTION__);
        return false;
    }
    return true;
}

struct StreamConfiguration {
    int32_t width = -1;
    int32_t height = -1;
};

class VtsAidlCameraServiceTargetTest : public ::testing::TestWithParam<std::string> {
   public:
    void SetUp() override {
        bool success = ABinderProcess_setThreadPoolMaxThreadCount(5);
        ASSERT_TRUE(success);
        ABinderProcess_startThreadPool();

        SpAIBinder cameraServiceBinder =
            SpAIBinder(AServiceManager_checkService(GetParam().c_str()));
        ASSERT_NE(cameraServiceBinder.get(), nullptr);

        std::shared_ptr<ICameraService> cameraService =
            ICameraService::fromBinder(cameraServiceBinder);
        ASSERT_NE(cameraService.get(), nullptr);
        mCameraService = cameraService;
    }

    void TearDown() override {}

    // creates an outputConfiguration with no deferred streams
    static OutputConfiguration createOutputConfiguration(const std::vector<native_handle_t*>& nhs) {
        OutputConfiguration output;
        output.rotation = OutputConfiguration::Rotation::R0;
        output.windowGroupId = -1;
        output.width = 0;
        output.height = 0;
        output.isDeferred = false;
        output.windowHandles.reserve(nhs.size());
        for (auto nh : nhs) {
            output.windowHandles.push_back(::android::makeToAidl(nh));
        }
        return output;
    }

    static void initializeCaptureRequestPartial(CaptureRequest* captureRequest, int32_t streamId,
                                                const std::string& cameraId, size_t settingsSize) {
        captureRequest->physicalCameraSettings.resize(1);
        captureRequest->physicalCameraSettings[0].id = cameraId;
        captureRequest->streamAndWindowIds.resize(1);
        captureRequest->streamAndWindowIds[0].streamId = streamId;
        captureRequest->streamAndWindowIds[0].windowId = 0;
        // Write the settings metadata into the fmq.
        captureRequest->physicalCameraSettings[0]
            .settings.set<CaptureMetadataInfo::fmqMetadataSize>(settingsSize);
    }

    static bool doesCapabilityExist(const CameraMetadata& characteristics, int capability) {
        camera_metadata_ro_entry rawEntry =
            characteristics.find(ANDROID_REQUEST_AVAILABLE_CAPABILITIES);
        EXPECT_TRUE(rawEntry.count > 0);
        for (size_t i = 0; i < rawEntry.count; i++) {
            if (rawEntry.data.u8[i] == capability) {
                return true;
            }
        }
        return false;
    }

    static bool isSecureOnlyDevice(const CameraMetadata& characteristics) {
        camera_metadata_ro_entry rawEntry =
            characteristics.find(ANDROID_REQUEST_AVAILABLE_CAPABILITIES);
        EXPECT_TRUE(rawEntry.count > 0);
        if (rawEntry.count == 1 &&
            rawEntry.data.u8[0] == ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA) {
            return true;
        }
        return false;
    }

    // Return the first advertised available stream sizes for the given format
    // and use-case.
    static StreamConfiguration getStreamConfiguration(const CameraMetadata& characteristics,
                                                      uint32_t tag, int32_t chosenUse,
                                                      int32_t chosenFormat) {
        camera_metadata_ro_entry rawEntry = characteristics.find(tag);
        StreamConfiguration streamConfig;
        const size_t STREAM_FORMAT_OFFSET = 0;
        const size_t STREAM_WIDTH_OFFSET = 1;
        const size_t STREAM_HEIGHT_OFFSET = 2;
        const size_t STREAM_INOUT_OFFSET = 3;
        const size_t STREAM_CONFIG_SIZE = 4;
        if (rawEntry.count < STREAM_CONFIG_SIZE) {
            return streamConfig;
        }
        EXPECT_TRUE((rawEntry.count % STREAM_CONFIG_SIZE) == 0);
        for (size_t i = 0; i < rawEntry.count; i += STREAM_CONFIG_SIZE) {
            int32_t format = rawEntry.data.i32[i + STREAM_FORMAT_OFFSET];
            int32_t use = rawEntry.data.i32[i + STREAM_INOUT_OFFSET];
            if (format == chosenFormat && use == chosenUse) {
                streamConfig.width = rawEntry.data.i32[i + STREAM_WIDTH_OFFSET];
                streamConfig.height = rawEntry.data.i32[i + STREAM_HEIGHT_OFFSET];
                return streamConfig;
            }
        }
        return streamConfig;
    }
    void BasicCameraTests(bool prepareWindows) {
        std::shared_ptr<CameraServiceListener> listener =
            ::ndk::SharedRefBase::make<CameraServiceListener>();
        std::vector<CameraStatusAndId> cameraStatuses;

        ndk::ScopedAStatus ret = mCameraService->addListener(listener, &cameraStatuses);
        EXPECT_TRUE(ret.isOk());
        listener->initializeStatuses(cameraStatuses);

        for (const auto& it : cameraStatuses) {
            CameraMetadata rawMetadata;
            if (it.deviceStatus != CameraDeviceStatus::STATUS_PRESENT) {
                continue;
            }
            AidlCameraMetadata aidlMetadata;
            ret = mCameraService->getCameraCharacteristics(it.cameraId, &aidlMetadata);
            EXPECT_TRUE(ret.isOk());
            bool cStatus = convertFromAidlCloned(aidlMetadata, &rawMetadata);
            EXPECT_TRUE(cStatus);
            EXPECT_FALSE(rawMetadata.isEmpty());

            std::shared_ptr<CameraDeviceCallback> callbacks =
                ndk::SharedRefBase::make<CameraDeviceCallback>();
            std::shared_ptr<ICameraDeviceUser> deviceRemote = nullptr;
            ret = mCameraService->connectDevice(callbacks, it.cameraId, &deviceRemote);
            EXPECT_TRUE(ret.isOk());
            EXPECT_TRUE(deviceRemote != nullptr);

            MQDescriptor<int8_t, SynchronizedReadWrite> mqDesc;
            ret = deviceRemote->getCaptureRequestMetadataQueue(&mqDesc);
            EXPECT_TRUE(ret.isOk());
            std::shared_ptr<RequestMetadataQueue> requestMQ =
                std::make_shared<RequestMetadataQueue>(mqDesc);
            EXPECT_TRUE(requestMQ->isValid());
            EXPECT_TRUE((requestMQ->availableToWrite() >= 0));

            AImageReader* reader = nullptr;
            bool isDepthOnlyDevice =
                !doesCapabilityExist(rawMetadata,
                                     ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) &&
                doesCapabilityExist(rawMetadata,
                                    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT);
            int chosenImageFormat = AIMAGE_FORMAT_YUV_420_888;
            int chosenImageWidth = kVGAImageWidth;
            int chosenImageHeight = kVGAImageHeight;
            bool isSecureOnlyCamera = isSecureOnlyDevice(rawMetadata);
            status_t mStatus = OK;
            if (isSecureOnlyCamera) {
                StreamConfiguration secureStreamConfig = getStreamConfiguration(
                    rawMetadata, ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS,
                    ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_OUTPUT,
                    HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED);
                EXPECT_TRUE(secureStreamConfig.width != -1);
                EXPECT_TRUE(secureStreamConfig.height != -1);
                chosenImageFormat = AIMAGE_FORMAT_PRIVATE;
                chosenImageWidth = secureStreamConfig.width;
                chosenImageHeight = secureStreamConfig.height;
                mStatus = AImageReader_newWithUsage(
                    chosenImageWidth, chosenImageHeight, chosenImageFormat,
                    AHARDWAREBUFFER_USAGE_PROTECTED_CONTENT, kCaptureRequestCount, &reader);

            } else {
                if (isDepthOnlyDevice) {
                    StreamConfiguration depthStreamConfig = getStreamConfiguration(
                        rawMetadata, ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS,
                        ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_OUTPUT,
                        HAL_PIXEL_FORMAT_Y16);
                    EXPECT_TRUE(depthStreamConfig.width != -1);
                    EXPECT_TRUE(depthStreamConfig.height != -1);
                    chosenImageFormat = AIMAGE_FORMAT_DEPTH16;
                    chosenImageWidth = depthStreamConfig.width;
                    chosenImageHeight = depthStreamConfig.height;
                }
                mStatus = AImageReader_new(chosenImageWidth, chosenImageHeight, chosenImageFormat,
                                           kCaptureRequestCount, &reader);
            }

            EXPECT_EQ(mStatus, AMEDIA_OK);
            native_handle_t* wh = nullptr;
            mStatus = AImageReader_getWindowNativeHandle(reader, &wh);
            EXPECT_TRUE(mStatus == AMEDIA_OK && wh != nullptr);

            ret = deviceRemote->beginConfigure();
            EXPECT_TRUE(ret.isOk());

            OutputConfiguration output = createOutputConfiguration({wh});
            int32_t streamId = -1;
            ret = deviceRemote->createStream(output, &streamId);
            EXPECT_TRUE(ret.isOk());
            EXPECT_TRUE(streamId >= 0);

            AidlCameraMetadata sessionParams;
            ret = deviceRemote->endConfigure(StreamConfigurationMode::NORMAL_MODE, sessionParams,
                                             systemTime());
            EXPECT_TRUE(ret.isOk());

            if (prepareWindows) {
                ret = deviceRemote->prepare(streamId);
                EXPECT_TRUE(ret.isOk());
                EXPECT_TRUE(callbacks->waitForPreparedCount(streamId, 1));

                ret = deviceRemote->prepare(streamId);
                // We should get another callback;
                EXPECT_TRUE(ret.isOk());
                EXPECT_TRUE(callbacks->waitForPreparedCount(streamId, 2));
            }
            AidlCameraMetadata aidlSettingsMetadata;
            ret = deviceRemote->createDefaultRequest(TemplateId::PREVIEW, &aidlSettingsMetadata);
            EXPECT_TRUE(ret.isOk());
            EXPECT_GE(aidlSettingsMetadata.metadata.size(), 0);
            std::vector<CaptureRequest> captureRequests;
            captureRequests.resize(kNumRequests);
            for (int i = 0; i < kNumRequests; i++) {
                CaptureRequest& captureRequest = captureRequests[i];
                initializeCaptureRequestPartial(&captureRequest, streamId, it.cameraId,
                                                aidlSettingsMetadata.metadata.size());
                // Write the settings metadata into the fmq.
                bool written = requestMQ->write(
                    reinterpret_cast<int8_t*>(aidlSettingsMetadata.metadata.data()),
                    aidlSettingsMetadata.metadata.size());
                EXPECT_TRUE(written);
            }

            SubmitInfo info;
            // Test a single capture
            ret = deviceRemote->submitRequestList(captureRequests, false, &info);
            EXPECT_TRUE(ret.isOk());
            EXPECT_GE(info.requestId, 0);
            EXPECT_TRUE(callbacks->waitForStatus(
                CameraDeviceCallback::LocalCameraDeviceStatus::RESULT_RECEIVED));
            EXPECT_TRUE(callbacks->waitForIdle());

            // Test repeating requests
            CaptureRequest captureRequest;
            initializeCaptureRequestPartial(&captureRequest, streamId, it.cameraId,
                                            aidlSettingsMetadata.metadata.size());

            bool written =
                requestMQ->write(reinterpret_cast<int8_t*>(aidlSettingsMetadata.metadata.data()),
                                 aidlSettingsMetadata.metadata.size());
            EXPECT_TRUE(written);

            ret = deviceRemote->submitRequestList({captureRequest}, true, &info);
            EXPECT_TRUE(ret.isOk());
            EXPECT_TRUE(callbacks->waitForStatus(
                CameraDeviceCallback::LocalCameraDeviceStatus::RESULT_RECEIVED));

            int64_t lastFrameNumber = -1;
            ret = deviceRemote->cancelRepeatingRequest(&lastFrameNumber);
            EXPECT_TRUE(ret.isOk());
            EXPECT_GE(lastFrameNumber, 0);

            // Test waitUntilIdle()
            auto statusRet = deviceRemote->waitUntilIdle();
            EXPECT_TRUE(statusRet.isOk());

            // Test deleteStream()
            statusRet = deviceRemote->deleteStream(streamId);
            EXPECT_TRUE(statusRet.isOk());

            ret = deviceRemote->disconnect();
            EXPECT_TRUE(ret.isOk());
        }
        ret = mCameraService->removeListener(listener);
        EXPECT_TRUE(ret.isOk());
    }

    std::shared_ptr<ICameraService> mCameraService = nullptr;
};

// Basic AIDL calls for ICameraService
TEST_P(VtsAidlCameraServiceTargetTest, BasicCameraLifeCycleTest) {
    BasicCameraTests(/*prepareWindows*/ false);
    BasicCameraTests(/*prepareWindows*/ true);
}

TEST_P(VtsAidlCameraServiceTargetTest, CameraServiceListenerTest) {
    std::shared_ptr<CameraServiceListener> listener =
        ndk::SharedRefBase::make<CameraServiceListener>();
    if (mCameraService == nullptr) return;

    std::vector<CameraStatusAndId> cameraStatuses;
    ndk::ScopedAStatus ret = mCameraService->addListener(listener, &cameraStatuses);
    EXPECT_TRUE(ret.isOk());
    listener->initializeStatuses(cameraStatuses);

    for (const auto& it : cameraStatuses) {
        CameraMetadata rawMetadata;
        AidlCameraMetadata aidlCameraMetadata;
        ret = mCameraService->getCameraCharacteristics(it.cameraId, &aidlCameraMetadata);
        EXPECT_TRUE(ret.isOk());
        bool cStatus = convertFromAidlCloned(aidlCameraMetadata, &rawMetadata);
        EXPECT_TRUE(cStatus);
        EXPECT_FALSE(rawMetadata.isEmpty());

        bool isLogicalCamera = doesCapabilityExist(
            rawMetadata, ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA);
        if (!isLogicalCamera) {
            EXPECT_TRUE(it.unavailPhysicalCameraIds.empty());
            continue;
        }
        camera_metadata_entry entry = rawMetadata.find(ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS);
        EXPECT_GT(entry.count, 0);

        std::unordered_set<std::string> validPhysicalIds;
        const uint8_t* ids = entry.data.u8;
        size_t start = 0;
        for (size_t i = 0; i < entry.count; i++) {
            if (ids[i] == '\0') {
                if (start != i) {
                    std::string currentId(reinterpret_cast<const char*>(ids + start));
                    validPhysicalIds.emplace(currentId);
                }
                start = i + 1;
            }
        }

        std::unordered_set<std::string> unavailablePhysicalIds(it.unavailPhysicalCameraIds.begin(),
                                                               it.unavailPhysicalCameraIds.end());
        EXPECT_EQ(unavailablePhysicalIds.size(), it.unavailPhysicalCameraIds.size());
        for (auto& unavailablePhysicalId : unavailablePhysicalIds) {
            EXPECT_NE(validPhysicalIds.find(unavailablePhysicalId), validPhysicalIds.end());
        }
    }

    ret = mCameraService->removeListener(listener);
    EXPECT_TRUE(ret.isOk());
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(VtsAidlCameraServiceTargetTest);
INSTANTIATE_TEST_SUITE_P(PerInstance, VtsAidlCameraServiceTargetTest,
                         testing::ValuesIn({std::string(ICameraService::descriptor) + "/default"}),
                         android::hardware::PrintInstanceNameToString);

}  // namespace android
