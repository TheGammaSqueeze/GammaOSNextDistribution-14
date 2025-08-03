/*
 * Copyright 2023 The Android Open Source Project
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

#include "Common.h"
#include "Enumerator.h"
#include "MockEvsHal.h"
#include "VirtualCamera.h"
#include "utils/include/Utils.h"

#include <fuzzer/FuzzedDataProvider.h>

#include <iostream>

namespace {

using aidl::android::automotive::evs::implementation::HalCamera;
using aidl::android::automotive::evs::implementation::MockEvsHal;
using aidl::android::automotive::evs::implementation::NiceMockEvsCamera;
using aidl::android::automotive::evs::implementation::Utils;
using aidl::android::automotive::evs::implementation::VirtualCamera;
using aidl::android::automotive::evs::implementation::initializeMockEvsHal;
using aidl::android::automotive::evs::implementation::openFirstCamera;
using aidl::android::hardware::automotive::evs::BufferDesc;
using aidl::android::hardware::automotive::evs::CameraDesc;
using aidl::android::hardware::automotive::evs::CameraParam;
using aidl::android::hardware::automotive::evs::EvsEventDesc;
using aidl::android::hardware::automotive::evs::EvsEventType;
using aidl::android::hardware::automotive::evs::IEvsCamera;
using aidl::android::hardware::automotive::evs::IEvsEnumerator;
using aidl::android::hardware::automotive::evs::ParameterRange;

enum EvsFuzzFuncs {
    EVS_FUZZ_GET_ALLOWED_BUFFERS,       // verify getAllowedBuffers
    EVS_FUZZ_IS_STREAMING,              // verify isStreaming
    EVS_FUZZ_SET_DESCRIPTOR,            // verify setDescriptor
    EVS_FUZZ_GET_CAMERA_INFO,           // verify getCameraInfo
    EVS_FUZZ_SETMAX_FRAMES_IN_FLIGHT,   // verify setMaxFramesInFlight
    EVS_FUZZ_START_VIDEO_STREAM,        // verify startVideoStream
    EVS_FUZZ_STOP_VIDEO_STREAM,         // verify stopVideoStream
    EVS_FUZZ_GET_EXTENDED_INFO,         // verify getExtendedInfo
    EVS_FUZZ_SET_EXTENDED_INFO,         // verify setExtendedInfo
    EVS_FUZZ_GET_PHYSICAL_CAMERA_INFO,  // verify getPhysicalCameraInfo
    EVS_FUZZ_PAUSE_VIDEO_STREAM,        // verify pauseVideoStream
    EVS_FUZZ_RESUME_VIDEO_STREAM,       // verify resumeVideoStream
    EVS_FUZZ_GET_PARAMETER_LIST,        // verify getParameterList
    EVS_FUZZ_GET_INT_PARAMETER_RANGE,   // verify getIntParameterRange
    EVS_FUZZ_IMPORT_EXTERNAL_BUFFERS,   // verify importExternalBuffers
    EVS_FUZZ_BASE_ENUM                  // verify common functions
};

const int kMaxFuzzerConsumedBytes = 12;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    FuzzedDataProvider fdp(data, size);

    std::shared_ptr<MockEvsHal> mockEvsHal = initializeMockEvsHal();
    EXPECT_NE(mockEvsHal, nullptr);

    std::shared_ptr<IEvsCamera> mockHwCamera = openFirstCamera(mockEvsHal);
    EXPECT_NE(mockHwCamera, nullptr);

    std::shared_ptr<HalCamera> halCamera = ndk::SharedRefBase::make<HalCamera>(mockHwCamera);
    EXPECT_NE(halCamera, nullptr);
    std::shared_ptr<VirtualCamera> virtualCamera = halCamera->makeVirtualCamera();
    EXPECT_NE(virtualCamera, nullptr);
    std::vector<BufferDesc> buffers;

    bool videoStarted = false;

    while (fdp.remaining_bytes() > kMaxFuzzerConsumedBytes) {
        switch (fdp.ConsumeIntegralInRange<uint32_t>(0, EVS_FUZZ_API_SUM)) {
            case EVS_FUZZ_GET_ALLOWED_BUFFERS: {
                LOG(DEBUG) << "EVS_FUZZ_GET_ALLOWED_BUFFERS";
                virtualCamera->getAllowedBuffers();
                break;
            }
            case EVS_FUZZ_IS_STREAMING: {
                LOG(DEBUG) << "EVS_FUZZ_IS_STREAMING";
                virtualCamera->isStreaming();
                break;
            }
            case EVS_FUZZ_GET_HW_CAMERA: {
                LOG(DEBUG) << "EVS_FUZZ_GET_HW_CAMERA";
                virtualCamera->getHalCameras();
                break;
            }
            case EVS_FUZZ_SET_DESCRIPTOR: {
                LOG(DEBUG) << "EVS_FUZZ_SET_DESCRIPTOR";
                CameraDesc* desc = new CameraDesc();
                virtualCamera->setDescriptor(desc);
                break;
            }
            case EVS_FUZZ_NOTIFY: {
                LOG(DEBUG) << "EVS_FUZZ_NOTIFY";
                if (videoStarted) {
                    EvsEventDesc event;
                    uint32_t type = fdp.ConsumeIntegralInRange<
                            uint32_t>(0, static_cast<uint32_t>(EvsEventType::STREAM_ERROR));
                    event.aType = static_cast<EvsEventType>(type);
                    virtualCamera->notify(event);
                }
                break;
            }
            case EVS_FUZZ_DELIVER_FRAME: {
                LOG(DEBUG) << "EVS_FUZZ_DELIVER_FRAME";
                BufferDesc buffer;
                buffer.bufferId = fdp.ConsumeIntegral<int32_t>();
                virtualCamera->deliverFrame(buffer);
                buffers.emplace_back(std::move(buffer));
                break;
            }
            case EVS_FUZZ_GET_CAMERA_INFO: {
                LOG(DEBUG) << "EVS_FUZZ_GET_CAMERA_INFO";
                CameraDesc desc;
                virtualCamera->getCameraInfo(&desc);
                break;
            }
            case EVS_FUZZ_SETMAX_FRAMES_IN_FLIGHT: {
                LOG(DEBUG) << "EVS_FUZZ_SETMAX_FRAMES_IN_FLIGHT";
                uint32_t delta = fdp.ConsumeIntegral<uint32_t>();
                virtualCamera->setMaxFramesInFlight(delta);
                break;
            }
            case EVS_FUZZ_START_VIDEO_STREAM: {
                LOG(DEBUG) << "EVS_FUZZ_START_VIDEO_STREAM";
                if (!videoStarted) {
                    std::shared_ptr<IEvsCamera> anotherMockHwCamera =
                            ndk::SharedRefBase::make<NiceMockEvsCamera>("another");
                    std::shared_ptr<HalCamera> anotherHalCamera =
                            ndk::SharedRefBase::make<HalCamera>(anotherMockHwCamera);
                    virtualCamera->startVideoStream(anotherHalCamera);
                    videoStarted = true;
                }
                break;
            }
            case EVS_FUZZ_DONE_WITH_FRAME: {
                LOG(DEBUG) << "EVS_FUZZ_DONE_WITH_FRAME";
                if (!buffers.empty()) {
                    uint32_t whichBuffer =
                            fdp.ConsumeIntegralInRange<uint32_t>(0, buffers.size() - 1);
                    std::vector<BufferDesc> buffersToReturn(1);
                    buffersToReturn.push_back(
                            Utils::dupBufferDesc(buffers[whichBuffer], /* doDup= */ true));
                    virtualCamera->doneWithFrame(buffersToReturn);
                }
                break;
            }
            case EVS_FUZZ_STOP_VIDEO_STREAM: {
                LOG(DEBUG) << "EVS_FUZZ_STOP_VIDEO_STREAM";
                virtualCamera->stopVideoStream();
                videoStarted = false;
                break;
            }
            case EVS_FUZZ_GET_EXTENDED_INFO: {
                LOG(DEBUG) << "EVS_FUZZ_GET_EXTENDED_INFO";
                uint32_t opaqueIdentifier = fdp.ConsumeIntegral<uint32_t>();
                std::vector<uint8_t> value;
                virtualCamera->getExtendedInfo(opaqueIdentifier, &value);
                break;
            }
            case EVS_FUZZ_SET_EXTENDED_INFO: {
                LOG(DEBUG) << "EVS_FUZZ_SET_EXTENDED_INFO";
                uint32_t opaqueIdentifier = fdp.ConsumeIntegral<uint32_t>();
                std::vector<uint8_t> value(sizeof(int32_t));
                *reinterpret_cast<uint32_t*>(value.data()) = fdp.ConsumeIntegral<int32_t>();
                virtualCamera->setExtendedInfo(opaqueIdentifier, value);
                break;
            }
            case EVS_FUZZ_GET_PHYSICAL_CAMERA_INFO: {
                LOG(DEBUG) << "EVS_FUZZ_GET_PHYSICAL_CAMERA_INFO";
                std::string deviceId("");
                CameraDesc desc;
                virtualCamera->getPhysicalCameraInfo(deviceId, &desc);
                break;
            }
            case EVS_FUZZ_PAUSE_VIDEO_STREAM: {
                LOG(DEBUG) << "EVS_FUZZ_PAUSE_VIDEO_STREAM";
                virtualCamera->pauseVideoStream();
                break;
            }
            case EVS_FUZZ_RESUME_VIDEO_STREAM: {
                LOG(DEBUG) << "EVS_FUZZ_RESUME_VIDEO_STREAM";
                virtualCamera->resumeVideoStream();
                break;
            }
            case EVS_FUZZ_SET_PRIMARY: {
                LOG(DEBUG) << "EVS_FUZZ_SET_PRIMARY";
                virtualCamera->setPrimaryClient();
                break;
            }
            case EVS_FUZZ_FORCE_PRIMARY: {
                LOG(DEBUG) << "EVS_FUZZ_FORCE_PRIMARY";
                // TODO(161388489) skip this until we finished fuzzing evs display
                break;
            }
            case EVS_FUZZ_UNSET_PRIMARY: {
                LOG(DEBUG) << "EVS_FUZZ_UNSET_PRIMARY";
                virtualCamera->unsetPrimaryClient();
                break;
            }
            case EVS_FUZZ_GET_PARAMETER_LIST: {
                LOG(DEBUG) << "EVS_FUZZ_GET_PARAMETER_LIST";
                std::vector<CameraParam> list;
                virtualCamera->getParameterList(&list);
                break;
            }
            case EVS_FUZZ_GET_INT_PARAMETER_RANGE: {
                LOG(DEBUG) << "EVS_FUZZ_GET_INT_PARAMETER_RANGE";
                uint32_t whichParam =
                        fdp.ConsumeIntegralInRange<uint32_t>(0,
                                                             static_cast<uint32_t>(
                                                                     CameraParam::ABSOLUTE_ZOOM));
                ParameterRange range;
                virtualCamera->getIntParameterRange(static_cast<CameraParam>(whichParam), &range);
                break;
            }
            case EVS_FUZZ_SET_PARAMETER: {
                LOG(DEBUG) << "EVS_FUZZ_SET_PARAMETER";
                uint32_t whichParam =
                        fdp.ConsumeIntegralInRange<uint32_t>(0,
                                                             static_cast<uint32_t>(
                                                                     CameraParam::ABSOLUTE_ZOOM));
                int32_t val = fdp.ConsumeIntegral<int32_t>();
                std::vector<int32_t> effectiveValue;
                virtualCamera->setIntParameter(static_cast<CameraParam>(whichParam), val,
                                               &effectiveValue);
                break;
            }
            case EVS_FUZZ_GET_PARAMETER: {
                LOG(DEBUG) << "EVS_FUZZ_GET_PARAMETER";
                uint32_t whichParam =
                        fdp.ConsumeIntegralInRange<uint32_t>(0,
                                                             static_cast<uint32_t>(
                                                                     CameraParam::ABSOLUTE_ZOOM));
                std::vector<int32_t> effectiveValue;
                virtualCamera->getIntParameter(static_cast<CameraParam>(whichParam),
                                               &effectiveValue);
                break;
            }
            case EVS_FUZZ_IMPORT_EXTERNAL_BUFFERS: {
                LOG(DEBUG) << "EVS_FUZZ_IMPORT_EXTERNAL_BUFFERS";
                if (!buffers.empty()) {
                    int32_t delta = 0;
                    virtualCamera->importExternalBuffers(buffers, &delta);
                }
                break;
            }
            default:
                LOG(ERROR) << "Unexpected option, aborting...";
                break;
        }
    }

    if (videoStarted) {
        // TODO(b/161762538) if we do not stop video stream manually here,
        // there will be crash at VirtualCamera.cpp::pHwCamera->unsetMaster(this);
        virtualCamera->stopVideoStream();
    }
    return 0;
}

}  // namespace
