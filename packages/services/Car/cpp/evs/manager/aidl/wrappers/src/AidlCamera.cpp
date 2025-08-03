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

#include "AidlCamera.h"

#include "HidlDisplay.h"
#include "utils/include/Utils.h"

#include <android-base/logging.h>

namespace aidl::android::automotive::evs::implementation {

namespace hidlevs = ::android::hardware::automotive::evs;

using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::aidl::android::hardware::automotive::evs::CameraDesc;
using ::aidl::android::hardware::automotive::evs::CameraParam;
using ::aidl::android::hardware::automotive::evs::DisplayState;
using ::aidl::android::hardware::automotive::evs::EvsEventDesc;
using ::aidl::android::hardware::automotive::evs::EvsEventType;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::aidl::android::hardware::automotive::evs::IEvsCameraStream;
using ::aidl::android::hardware::automotive::evs::IEvsDisplay;
using ::aidl::android::hardware::automotive::evs::ParameterRange;
using ::android::hardware::hidl_vec;
using ::ndk::ScopedAStatus;

AidlCamera::AidlCamera(const ::android::sp<hidlevs::V1_0::IEvsCamera>& hidlCamera, bool forceV1_0) {
    auto hidlCameraV1 = hidlevs::V1_1::IEvsCamera::castFrom(hidlCamera).withDefault(nullptr);
    if (forceV1_0 || !hidlCameraV1) {
        // AidlCamera is initialized in V1_0::IEvsCamera support mode in below
        // three conditions:
        // 1. A given camera object is an implementation of V1_0::IEvsCamera
        //    (fails to upcast as V1_1::IEvsCamera).
        // 2. A caller explicitly creates AidlCamera object in V1_0::IEvsCamera
        //    mode by setting forceV1_0 as true.
        // 3. Or, A given camera object is invalid (nullptr).
        mImpl = std::make_shared<ImplV0>(hidlCamera);
    } else {
        mImpl = std::make_shared<ImplV1>(hidlCameraV1);
    }
}

const ::android::sp<hidlevs::V1_0::IEvsCamera> AidlCamera::getHidlCamera() const {
    return mImpl->getHidlCamera();
}

ScopedAStatus AidlCamera::doneWithFrame(const std::vector<BufferDesc>& buffers) {
    return mImpl->doneWithFrame(buffers);
}

ScopedAStatus AidlCamera::forcePrimaryClient(const std::shared_ptr<IEvsDisplay>& display) {
    return mImpl->forcePrimaryClient(display);
}

ScopedAStatus AidlCamera::getCameraInfo(CameraDesc* _aidl_return) {
    return mImpl->getCameraInfo(_aidl_return);
}

ScopedAStatus AidlCamera::getExtendedInfo(int32_t opaqueIdentifier, std::vector<uint8_t>* value) {
    return mImpl->getExtendedInfo(opaqueIdentifier, value);
}

ScopedAStatus AidlCamera::getIntParameter(CameraParam id, std::vector<int32_t>* value) {
    return mImpl->getIntParameter(id, value);
}

ScopedAStatus AidlCamera::getIntParameterRange(CameraParam id, ParameterRange* _aidl_return) {
    return mImpl->getIntParameterRange(id, _aidl_return);
}

ScopedAStatus AidlCamera::getParameterList(std::vector<CameraParam>* _aidl_return) {
    return mImpl->getParameterList(_aidl_return);
}

ScopedAStatus AidlCamera::getPhysicalCameraInfo(const std::string& deviceId,
                                                CameraDesc* _aidl_return) {
    return mImpl->getPhysicalCameraInfo(deviceId, _aidl_return);
}

ScopedAStatus AidlCamera::importExternalBuffers(const std::vector<BufferDesc>& buffers,
                                                int32_t* _aidl_return) {
    return mImpl->importExternalBuffers(buffers, _aidl_return);
}

ScopedAStatus AidlCamera::pauseVideoStream() {
    return mImpl->pauseVideoStream();
}

ScopedAStatus AidlCamera::resumeVideoStream() {
    return mImpl->resumeVideoStream();
}

ScopedAStatus AidlCamera::setExtendedInfo(int32_t opaqueIdentifier,
                                          const std::vector<uint8_t>& opaqueValue) {
    return mImpl->setExtendedInfo(opaqueIdentifier, opaqueValue);
}

ScopedAStatus AidlCamera::setIntParameter(CameraParam id, int32_t value,
                                          std::vector<int32_t>* effectiveValue) {
    return mImpl->setIntParameter(id, value, effectiveValue);
}

ScopedAStatus AidlCamera::setPrimaryClient() {
    return mImpl->setPrimaryClient();
}

ScopedAStatus AidlCamera::setMaxFramesInFlight(int32_t bufferCount) {
    return mImpl->setMaxFramesInFlight(bufferCount);
}

ScopedAStatus AidlCamera::startVideoStream(const std::shared_ptr<IEvsCameraStream>& listener) {
    return mImpl->startVideoStream(listener);
}

ScopedAStatus AidlCamera::stopVideoStream() {
    return mImpl->stopVideoStream();
}

ScopedAStatus AidlCamera::unsetPrimaryClient() {
    return mImpl->unsetPrimaryClient();
}

AidlCamera::ImplV0::ImplV0(const ::android::sp<hidlevs::V1_0::IEvsCamera>& camera) :
      IHidlCamera(camera) {
    if (!camera) {
        LOG(WARNING) << "AidlCamera object is instantiated with an invalid IEvsCamera object.";
        return;
    }

    // Because android::hardware::automotive::evs::V1_0::BufferDesc does not
    // contain a device id while it is required to handle received frames
    // properly, we are retrieving it from the camera descriptor and store
    // locally.
    camera->getCameraInfo([this](const auto& read) { mId = read.cameraId; });
}

ScopedAStatus AidlCamera::ImplV0::doneWithFrame(const std::vector<BufferDesc>& buffers) {
    if (!mHidlStream) {
        LOG(WARNING) << "Ignores a request to return a buffer of an invalid HIDL camera stream";
        return ScopedAStatus::ok();
    }

    hidlevs::V1_0::BufferDesc hidlBuffer;
    if (mHidlStream->getHidlBuffer(buffers[0].bufferId, &hidlBuffer)) {
        mHidlCamera->doneWithFrame(hidlBuffer);
    }

    return ScopedAStatus::ok();
}

ScopedAStatus AidlCamera::ImplV0::forcePrimaryClient(
        [[maybe_unused]] const std::shared_ptr<IEvsDisplay>& display) {
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
}

ScopedAStatus AidlCamera::ImplV0::getCameraInfo(CameraDesc* _aidl_return) {
    if (!mHidlCamera) {
        LOG(ERROR) << "HIDL camera is not valid";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    (void)mHidlCamera->getCameraInfo(
            [_aidl_return](auto& desc) { *_aidl_return = std::move(Utils::makeFromHidl(desc)); });

    return ScopedAStatus::ok();
}

ScopedAStatus AidlCamera::ImplV0::getExtendedInfo(int32_t opaqueIdentifier,
                                                  std::vector<uint8_t>* value) {
    if (!mHidlCamera) {
        LOG(ERROR) << "HIDL camera is not valid";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    int32_t hidlValue = mHidlCamera->getExtendedInfo(opaqueIdentifier);
    value->resize(sizeof(hidlValue));
    int* p = reinterpret_cast<int*>(value->data());
    *p = hidlValue;
    return ScopedAStatus::ok();
}

ScopedAStatus AidlCamera::ImplV0::getIntParameter(CameraParam /*id*/,
                                                  std::vector<int32_t>* /*value*/) {
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
}

ScopedAStatus AidlCamera::ImplV0::getIntParameterRange(CameraParam /*id*/,
                                                       ParameterRange* /*_aidl_return*/) {
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
}

ScopedAStatus AidlCamera::ImplV0::getParameterList(std::vector<CameraParam>* /*_aidl_return*/) {
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
}

ScopedAStatus AidlCamera::ImplV0::getPhysicalCameraInfo(const std::string& /*deviceId*/,
                                                        CameraDesc* /*_aidl_return*/) {
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
}

ScopedAStatus AidlCamera::ImplV0::importExternalBuffers(const std::vector<BufferDesc>& /*buffers*/,
                                                        int32_t* /*_aidl_return*/) {
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
}

ScopedAStatus AidlCamera::ImplV0::pauseVideoStream() {
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
}

ScopedAStatus AidlCamera::ImplV0::resumeVideoStream() {
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
}

ScopedAStatus AidlCamera::ImplV0::setExtendedInfo(int32_t opaqueIdentifier,
                                                  const std::vector<uint8_t>& opaqueValue) {
    if (opaqueValue.size() < sizeof(int32_t)) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::INVALID_ARG);
    }

    int32_t v = *(reinterpret_cast<const int32_t*>(opaqueValue.data()));
    return Utils::buildScopedAStatusFromEvsResult(
            mHidlCamera->setExtendedInfo(opaqueIdentifier, v));
}

ScopedAStatus AidlCamera::ImplV0::setIntParameter(CameraParam /*id*/, int32_t /*value*/,
                                                  std::vector<int32_t>* /*effectiveValue*/) {
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
}

ScopedAStatus AidlCamera::ImplV0::setPrimaryClient() {
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
}

ScopedAStatus AidlCamera::ImplV0::setMaxFramesInFlight(int32_t bufferCount) {
    if (!mHidlCamera) {
        LOG(ERROR) << "HIDL camera is not valid";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return Utils::buildScopedAStatusFromEvsResult(mHidlCamera->setMaxFramesInFlight(bufferCount));
}

ScopedAStatus AidlCamera::ImplV0::startVideoStream(
        const std::shared_ptr<IEvsCameraStream>& listener) {
    if (!listener) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::INVALID_ARG);
    } else if (mHidlStream) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::STREAM_ALREADY_RUNNING);
    }

    // Creates a wrapper object and requests a video stream
    mHidlStream = new (std::nothrow) HidlCameraStream(mId, listener);
    return Utils::buildScopedAStatusFromEvsResult(mHidlCamera->startVideoStream(mHidlStream));
}

ScopedAStatus AidlCamera::ImplV0::stopVideoStream() {
    if (!mHidlStream) {
        return ScopedAStatus::ok();
    }

    mHidlCamera->stopVideoStream();
    mHidlStream = nullptr;
    return ScopedAStatus::ok();
}

ScopedAStatus AidlCamera::ImplV0::unsetPrimaryClient() {
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
}

AidlCamera::ImplV1::ImplV1(const ::android::sp<hidlevs::V1_1::IEvsCamera>& camera) :
      IHidlCamera(camera), mHidlCamera(camera) {
    if (!camera) {
        LOG(WARNING) << "AidlCamera object is instantiated with an invalid IEvsCamera object.";
        return;
    }

    camera->getCameraInfo_1_1([this](const auto& read) { mId = read.v1.cameraId; });
}

ScopedAStatus AidlCamera::ImplV1::doneWithFrame(const std::vector<BufferDesc>& buffers) {
    if (!mHidlStream) {
        LOG(WARNING) << "Ignores a request to return a buffer of an invalid HIDL camera stream";
        return ScopedAStatus::ok();
    }

    const auto n = buffers.size();
    hidl_vec<hidlevs::V1_1::BufferDesc> hidlBuffers(n);
    for (auto i = 0; i < n; ++i) {
        hidlevs::V1_1::BufferDesc buffer;
        if (mHidlStream->getHidlBuffer(buffers[i].bufferId, &buffer)) {
            hidlBuffers[i] = std::move(buffer);
        }
    }

    mHidlCamera->doneWithFrame_1_1(hidlBuffers);
    return ScopedAStatus::ok();
}

ScopedAStatus AidlCamera::ImplV1::forcePrimaryClient(const std::shared_ptr<IEvsDisplay>& display) {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
    }

    return Utils::buildScopedAStatusFromEvsResult(
            mHidlCamera->forceMaster(new HidlDisplay(display)));
}

ScopedAStatus AidlCamera::ImplV1::getCameraInfo(CameraDesc* _aidl_return) {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    (void)mHidlCamera->getCameraInfo_1_1(
            [_aidl_return](auto& desc) { *_aidl_return = std::move(Utils::makeFromHidl(desc)); });

    return ScopedAStatus::ok();
}

ScopedAStatus AidlCamera::ImplV1::getExtendedInfo(int32_t opaqueIdentifier,
                                                  std::vector<uint8_t>* value) {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    hidlevs::V1_0::EvsResult hidlStatus = hidlevs::V1_0::EvsResult::OK;
    (void)mHidlCamera->getExtendedInfo_1_1(opaqueIdentifier,
                                           [&hidlStatus, value](auto status,
                                                                const hidl_vec<uint8_t>& hwValue) {
                                               hidlStatus = status;
                                               *value = hwValue;
                                           });
    return Utils::buildScopedAStatusFromEvsResult(hidlStatus);
}

ScopedAStatus AidlCamera::ImplV1::getIntParameter(CameraParam id, std::vector<int32_t>* value) {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    hidlevs::V1_0::EvsResult hidlStatus = hidlevs::V1_0::EvsResult::OK;
    (void)mHidlCamera->getIntParameter(Utils::makeToHidl(id),
                                       [&hidlStatus, value](auto status,
                                                            const hidl_vec<int32_t>& hidlValues) {
                                           hidlStatus = status;
                                           *value = hidlValues;
                                       });
    return Utils::buildScopedAStatusFromEvsResult(hidlStatus);
}

ScopedAStatus AidlCamera::ImplV1::getIntParameterRange(CameraParam id,
                                                       ParameterRange* _aidl_return) {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    (void)mHidlCamera->getIntParameterRange(Utils::makeToHidl(id),
                                            [_aidl_return](auto min, auto max, auto step) {
                                                _aidl_return->min = min;
                                                _aidl_return->max = max;
                                                _aidl_return->step = step;
                                            });
    return ScopedAStatus::ok();
}

ScopedAStatus AidlCamera::ImplV1::getParameterList(std::vector<CameraParam>* _aidl_return) {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    (void)mHidlCamera->getParameterList(
            [_aidl_return](const hidl_vec<hidlevs::V1_1::CameraParam>& list) {
                _aidl_return->reserve(list.size());
                for (auto i = 0; i < list.size(); ++i) {
                    _aidl_return->push_back(std::move(Utils::makeFromHidl(list[i])));
                }
            });
    return ScopedAStatus::ok();
}

ScopedAStatus AidlCamera::ImplV1::getPhysicalCameraInfo(const std::string& deviceId,
                                                        CameraDesc* _aidl_return) {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    (void)mHidlCamera->getPhysicalCameraInfo(deviceId, [_aidl_return](const auto& hidlDesc) {
        *_aidl_return = std::move(Utils::makeFromHidl(hidlDesc));
    });
    return ScopedAStatus::ok();
}

ScopedAStatus AidlCamera::ImplV1::importExternalBuffers(const std::vector<BufferDesc>& buffers,
                                                        int32_t* _aidl_return) {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    hidl_vec<hidlevs::V1_1::BufferDesc> hidlBuffers;
    hidlBuffers.resize(buffers.size());
    for (auto i = 0; i < buffers.size(); ++i) {
        hidlBuffers[i] = std::move(Utils::makeToHidlV1_1(buffers[i]));
    }
    hidlevs::V1_0::EvsResult hidlStatus = hidlevs::V1_0::EvsResult::OK;
    (void)mHidlCamera->importExternalBuffers(hidlBuffers,
                                             [&hidlStatus, _aidl_return](auto status, auto delta) {
                                                 hidlStatus = status;
                                                 *_aidl_return = delta;
                                             });
    return Utils::buildScopedAStatusFromEvsResult(hidlStatus);
}

ScopedAStatus AidlCamera::ImplV1::pauseVideoStream() {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }
    return Utils::buildScopedAStatusFromEvsResult(mHidlCamera->pauseVideoStream());
}

ScopedAStatus AidlCamera::ImplV1::resumeVideoStream() {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }
    return Utils::buildScopedAStatusFromEvsResult(mHidlCamera->resumeVideoStream());
}

ScopedAStatus AidlCamera::ImplV1::setExtendedInfo(int32_t opaqueIdentifier,
                                                  const std::vector<uint8_t>& opaqueValue) {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    hidl_vec<uint8_t> value = opaqueValue;
    hidlevs::V1_0::EvsResult hidlStatus = mHidlCamera->setExtendedInfo_1_1(opaqueIdentifier, value);

    return Utils::buildScopedAStatusFromEvsResult(hidlStatus);
}

ScopedAStatus AidlCamera::ImplV1::setIntParameter(CameraParam id, int32_t value,
                                                  std::vector<int32_t>* effectiveValue) {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    hidlevs::V1_0::EvsResult hidlStatus = hidlevs::V1_0::EvsResult::OK;
    (void)mHidlCamera->setIntParameter(Utils::makeToHidl(id), value,
                                       [&hidlStatus,
                                        &effectiveValue](auto status,
                                                         const hidl_vec<int32_t>& values) {
                                           hidlStatus = status;
                                           *effectiveValue = values;
                                       });
    return Utils::buildScopedAStatusFromEvsResult(hidlStatus);
}

ScopedAStatus AidlCamera::ImplV1::setPrimaryClient() {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return Utils::buildScopedAStatusFromEvsResult(mHidlCamera->setMaster());
}

ScopedAStatus AidlCamera::ImplV1::setMaxFramesInFlight(int32_t bufferCount) {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return Utils::buildScopedAStatusFromEvsResult(mHidlCamera->setMaxFramesInFlight(bufferCount));
}

ScopedAStatus AidlCamera::ImplV1::startVideoStream(
        const std::shared_ptr<IEvsCameraStream>& listener) {
    if (!listener) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::INVALID_ARG);
    } else if (mHidlStream) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::STREAM_ALREADY_RUNNING);
    }

    // Creates a wrapper object and requests a video stream
    mHidlStream = new (std::nothrow) HidlCameraStream(mId, listener);
    return Utils::buildScopedAStatusFromEvsResult(mHidlCamera->startVideoStream(mHidlStream));
}

ScopedAStatus AidlCamera::ImplV1::stopVideoStream() {
    if (!mHidlStream) {
        return ScopedAStatus::ok();
    }

    mHidlCamera->stopVideoStream();
    mHidlStream = nullptr;
    return ScopedAStatus::ok();
}

ScopedAStatus AidlCamera::ImplV1::unsetPrimaryClient() {
    if (!mHidlCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return Utils::buildScopedAStatusFromEvsResult(mHidlCamera->unsetMaster());
}

}  // namespace aidl::android::automotive::evs::implementation
