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

#ifndef CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLHWCAMERA_H
#define CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLHWCAMERA_H

#include "HidlCameraStream.h"

#include <aidl/android/hardware/automotive/evs/BnEvsCamera.h>
#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/CameraDesc.h>
#include <aidl/android/hardware/automotive/evs/CameraParam.h>
#include <aidl/android/hardware/automotive/evs/EvsEventDesc.h>
#include <aidl/android/hardware/automotive/evs/EvsResult.h>
#include <aidl/android/hardware/automotive/evs/IEvsCameraStream.h>
#include <aidl/android/hardware/automotive/evs/IEvsDisplay.h>
#include <aidl/android/hardware/automotive/evs/ParameterRange.h>
#include <aidl/android/hardware/automotive/evs/Stream.h>
#include <android/hardware/automotive/evs/1.1/IEvsCamera.h>

#include <unordered_map>

namespace aidl::android::automotive::evs::implementation {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;
namespace hidlevs = ::android::hardware::automotive::evs;

class AidlCamera : public ::aidl::android::hardware::automotive::evs::BnEvsCamera {
public:
    // Methods from ::android::hardware::automotive::evs::IEvsCamera follow.
    ::ndk::ScopedAStatus doneWithFrame(const std::vector<aidlevs::BufferDesc>& buffers) override;
    ::ndk::ScopedAStatus forcePrimaryClient(
            const std::shared_ptr<aidlevs::IEvsDisplay>& display) override;
    ::ndk::ScopedAStatus getCameraInfo(aidlevs::CameraDesc* _aidl_return) override;
    ::ndk::ScopedAStatus getExtendedInfo(int32_t opaqueIdentifier,
                                         std::vector<uint8_t>* value) override;
    ::ndk::ScopedAStatus getIntParameter(aidlevs::CameraParam id,
                                         std::vector<int32_t>* value) override;
    ::ndk::ScopedAStatus getIntParameterRange(aidlevs::CameraParam id,
                                              aidlevs::ParameterRange* _aidl_return) override;
    ::ndk::ScopedAStatus getParameterList(std::vector<aidlevs::CameraParam>* _aidl_return) override;
    ::ndk::ScopedAStatus getPhysicalCameraInfo(const std::string& deviceId,
                                               aidlevs::CameraDesc* _aidl_return) override;
    ::ndk::ScopedAStatus importExternalBuffers(const std::vector<aidlevs::BufferDesc>& buffers,
                                               int32_t* _aidl_return) override;
    ::ndk::ScopedAStatus pauseVideoStream() override;
    ::ndk::ScopedAStatus resumeVideoStream() override;
    ::ndk::ScopedAStatus setExtendedInfo(int32_t opaqueIdentifier,
                                         const std::vector<uint8_t>& opaqueValue) override;
    ::ndk::ScopedAStatus setIntParameter(aidlevs::CameraParam id, int32_t value,
                                         std::vector<int32_t>* effectiveValue) override;
    ::ndk::ScopedAStatus setPrimaryClient() override;
    ::ndk::ScopedAStatus setMaxFramesInFlight(int32_t bufferCount) override;
    ::ndk::ScopedAStatus startVideoStream(
            const std::shared_ptr<aidlevs::IEvsCameraStream>& receiver) override;
    ::ndk::ScopedAStatus stopVideoStream() override;
    ::ndk::ScopedAStatus unsetPrimaryClient() override;

    explicit AidlCamera(const ::android::sp<hidlevs::V1_0::IEvsCamera>& camera,
                        bool forceV1_0 = false);
    virtual ~AidlCamera() { mImpl = nullptr; }

    const ::android::sp<hidlevs::V1_0::IEvsCamera> getHidlCamera() const;

private:
    class IHidlCamera;
    class ImplV0;
    class ImplV1;
    std::shared_ptr<IHidlCamera> mImpl;
};

class AidlCamera::IHidlCamera {
public:
    virtual ::ndk::ScopedAStatus doneWithFrame(const std::vector<aidlevs::BufferDesc>& buffers) = 0;
    virtual ::ndk::ScopedAStatus forcePrimaryClient(
            const std::shared_ptr<aidlevs::IEvsDisplay>& display) = 0;
    virtual ::ndk::ScopedAStatus getCameraInfo(aidlevs::CameraDesc* _aidl_return) = 0;
    virtual ::ndk::ScopedAStatus getExtendedInfo(int32_t opaqueIdentifier,
                                                 std::vector<uint8_t>* value) = 0;
    virtual ::ndk::ScopedAStatus getIntParameter(aidlevs::CameraParam id,
                                                 std::vector<int32_t>* value) = 0;
    virtual ::ndk::ScopedAStatus getIntParameterRange(aidlevs::CameraParam id,
                                                      aidlevs::ParameterRange* _aidl_return) = 0;
    virtual ::ndk::ScopedAStatus getParameterList(
            std::vector<aidlevs::CameraParam>* _aidl_return) = 0;
    virtual ::ndk::ScopedAStatus getPhysicalCameraInfo(const std::string& deviceId,
                                                       aidlevs::CameraDesc* _aidl_return) = 0;
    virtual ::ndk::ScopedAStatus importExternalBuffers(
            const std::vector<aidlevs::BufferDesc>& buffers, int32_t* _aidl_return) = 0;
    virtual ::ndk::ScopedAStatus pauseVideoStream() = 0;
    virtual ::ndk::ScopedAStatus resumeVideoStream() = 0;
    virtual ::ndk::ScopedAStatus setExtendedInfo(int32_t opaqueIdentifier,
                                                 const std::vector<uint8_t>& opaqueValue) = 0;
    virtual ::ndk::ScopedAStatus setIntParameter(aidlevs::CameraParam id, int32_t value,
                                                 std::vector<int32_t>* effectiveValue) = 0;
    virtual ::ndk::ScopedAStatus setPrimaryClient() = 0;
    virtual ::ndk::ScopedAStatus setMaxFramesInFlight(int32_t bufferCount) = 0;
    virtual ::ndk::ScopedAStatus startVideoStream(
            const std::shared_ptr<aidlevs::IEvsCameraStream>& receiver) = 0;
    virtual ::ndk::ScopedAStatus stopVideoStream() = 0;
    virtual ::ndk::ScopedAStatus unsetPrimaryClient() = 0;
    virtual const ::android::sp<hidlevs::V1_0::IEvsCamera> getHidlCamera() const = 0;

    explicit IHidlCamera(const ::android::sp<hidlevs::V1_0::IEvsCamera>& camera) :
          mHidlCamera(camera) {}
    virtual ~IHidlCamera() {
        mHidlCamera = nullptr;
        mHidlStream = nullptr;
    }

protected:
    // The low level camera interface that backs this proxy
    ::android::sp<hidlevs::V1_0::IEvsCamera> mHidlCamera;
    ::android::sp<HidlCameraStream> mHidlStream;
    std::string mId;
};

class AidlCamera::ImplV0 final : public IHidlCamera {
public:
    ::ndk::ScopedAStatus doneWithFrame(const std::vector<aidlevs::BufferDesc>& buffers) override;
    ::ndk::ScopedAStatus forcePrimaryClient(
            const std::shared_ptr<aidlevs::IEvsDisplay>& display) override;
    ::ndk::ScopedAStatus getCameraInfo(aidlevs::CameraDesc* _aidl_return) override;
    ::ndk::ScopedAStatus getExtendedInfo(int32_t opaqueIdentifier,
                                         std::vector<uint8_t>* value) override;
    ::ndk::ScopedAStatus getIntParameter(aidlevs::CameraParam id,
                                         std::vector<int32_t>* value) override;
    ::ndk::ScopedAStatus getIntParameterRange(aidlevs::CameraParam id,
                                              aidlevs::ParameterRange* _aidl_return) override;
    ::ndk::ScopedAStatus getParameterList(std::vector<aidlevs::CameraParam>* _aidl_return) override;
    ::ndk::ScopedAStatus getPhysicalCameraInfo(const std::string& deviceId,
                                               aidlevs::CameraDesc* _aidl_return) override;
    ::ndk::ScopedAStatus importExternalBuffers(const std::vector<aidlevs::BufferDesc>& buffers,
                                               int32_t* _aidl_return) override;
    ::ndk::ScopedAStatus pauseVideoStream() override;
    ::ndk::ScopedAStatus resumeVideoStream() override;
    ::ndk::ScopedAStatus setExtendedInfo(int32_t opaqueIdentifier,
                                         const std::vector<uint8_t>& opaqueValue) override;
    ::ndk::ScopedAStatus setIntParameter(aidlevs::CameraParam id, int32_t value,
                                         std::vector<int32_t>* effectiveValue) override;
    ::ndk::ScopedAStatus setPrimaryClient() override;
    ::ndk::ScopedAStatus setMaxFramesInFlight(int32_t bufferCount) override;
    ::ndk::ScopedAStatus startVideoStream(
            const std::shared_ptr<aidlevs::IEvsCameraStream>& receiver) override;
    ::ndk::ScopedAStatus stopVideoStream() override;
    ::ndk::ScopedAStatus unsetPrimaryClient() override;

    explicit ImplV0(const ::android::sp<hidlevs::V1_0::IEvsCamera>& camera);
    virtual ~ImplV0() { mHidlCamera = nullptr; };

    const ::android::sp<hidlevs::V1_0::IEvsCamera> getHidlCamera() const override {
        return mHidlCamera;
    }
};

class AidlCamera::ImplV1 final : public IHidlCamera {
public:
    ::ndk::ScopedAStatus doneWithFrame(const std::vector<aidlevs::BufferDesc>& buffers) override;
    ::ndk::ScopedAStatus forcePrimaryClient(
            const std::shared_ptr<aidlevs::IEvsDisplay>& display) override;
    ::ndk::ScopedAStatus getCameraInfo(aidlevs::CameraDesc* _aidl_return) override;
    ::ndk::ScopedAStatus getExtendedInfo(int32_t opaqueIdentifier,
                                         std::vector<uint8_t>* value) override;
    ::ndk::ScopedAStatus getIntParameter(aidlevs::CameraParam id,
                                         std::vector<int32_t>* value) override;
    ::ndk::ScopedAStatus getIntParameterRange(aidlevs::CameraParam id,
                                              aidlevs::ParameterRange* _aidl_return) override;
    ::ndk::ScopedAStatus getParameterList(std::vector<aidlevs::CameraParam>* _aidl_return) override;
    ::ndk::ScopedAStatus getPhysicalCameraInfo(const std::string& deviceId,
                                               aidlevs::CameraDesc* _aidl_return) override;
    ::ndk::ScopedAStatus importExternalBuffers(const std::vector<aidlevs::BufferDesc>& buffers,
                                               int32_t* _aidl_return) override;
    ::ndk::ScopedAStatus pauseVideoStream() override;
    ::ndk::ScopedAStatus resumeVideoStream() override;
    ::ndk::ScopedAStatus setExtendedInfo(int32_t opaqueIdentifier,
                                         const std::vector<uint8_t>& opaqueValue) override;
    ::ndk::ScopedAStatus setIntParameter(aidlevs::CameraParam id, int32_t value,
                                         std::vector<int32_t>* effectiveValue) override;
    ::ndk::ScopedAStatus setPrimaryClient() override;
    ::ndk::ScopedAStatus setMaxFramesInFlight(int32_t bufferCount) override;
    ::ndk::ScopedAStatus startVideoStream(
            const std::shared_ptr<aidlevs::IEvsCameraStream>& receiver) override;
    ::ndk::ScopedAStatus stopVideoStream() override;
    ::ndk::ScopedAStatus unsetPrimaryClient() override;

    explicit ImplV1(const ::android::sp<hidlevs::V1_1::IEvsCamera>& camera);
    virtual ~ImplV1() { mHidlCamera = nullptr; }

    const ::android::sp<hidlevs::V1_0::IEvsCamera> getHidlCamera() const override {
        return mHidlCamera;
    }

private:
    ::android::sp<hidlevs::V1_1::IEvsCamera> mHidlCamera;
};

}  // namespace aidl::android::automotive::evs::implementation

#endif  // CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLHWCAMERA_H
