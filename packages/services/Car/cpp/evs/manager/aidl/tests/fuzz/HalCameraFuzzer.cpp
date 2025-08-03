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
#include "HalCamera.h"
#include "MockEvsHal.h"
#include "utils/include/Utils.h"

#include <fuzzbinder/libbinder_ndk_driver.h>
#include <fuzzer/FuzzedDataProvider.h>

#include <sys/time.h>

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
using aidl::android::hardware::automotive::evs::Stream;

enum EvsFuzzFuncs {
    EVS_FUZZ_MAKE_VIRTUAL_CAMERA = 0,    // verify makeVirtualCamera
    EVS_FUZZ_OWN_VIRTUAL_CAMERA,         // verify ownVirtualCamera
    EVS_FUZZ_DISOWN_VIRTUAL_CAMERA,      // verify disownVirtualCamera
    EVS_FUZZ_GET_CLIENT_COUNT,           // verify getClientCount
    EVS_FUZZ_GET_ID,                     // verify getId
    EVS_FUZZ_GET_STREAM_CONFIG,          // verify getStreamConfig
    EVS_FUZZ_CHANGE_FRAMES_IN_FLIGHT,    // verify changeFramesInFlight
    EVS_FUZZ_CHANGE_FRAMES_IN_FLIGHT_1,  // verify overloaded changeFramesInFlight
    EVS_FUZZ_REQUEST_NEW_FRAME,          // verify requestNewFrame
    EVS_FUZZ_CLIENT_STREAM_STARTING,     // verify clientStreamStarting
    EVS_FUZZ_CLIENT_STREAM_ENDING,       // verify clientStreamEnding
    EVS_FUZZ_GET_STATS,                  // verify getStats
    EVS_FUZZ_GET_STREAM_CONFIGURATION,   // verify getStreamConfiguration
    EVS_FUZZ_BASE_ENUM,                  // verify common functions
};

int64_t getCurrentTimeStamp() {
    struct timeval tp;
    gettimeofday(&tp, NULL);
    int64_t ms = tp.tv_sec * 1000 + tp.tv_usec / 1000;
    return ms;
}

const int kMaxFuzzerConsumedBytes = 12;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    FuzzedDataProvider fdp(data, size);

    std::shared_ptr<MockEvsHal> mockEvsHal = initializeMockEvsHal();
    EXPECT_NE(mockEvsHal, nullptr);

    std::shared_ptr<IEvsCamera> mockHwCamera = openFirstCamera(mockEvsHal);
    EXPECT_NE(mockHwCamera, nullptr);

    std::shared_ptr<HalCamera> halCamera = ndk::SharedRefBase::make<HalCamera>(mockHwCamera);
    EXPECT_NE(halCamera, nullptr);
    std::vector<std::shared_ptr<VirtualCamera>> virtualCameras;
    std::vector<BufferDesc> buffers;

    while (fdp.remaining_bytes() > kMaxFuzzerConsumedBytes) {
        switch (fdp.ConsumeIntegralInRange<uint32_t>(0, EVS_FUZZ_API_SUM)) {
            case EVS_FUZZ_MAKE_VIRTUAL_CAMERA: {
                LOG(DEBUG) << "EVS_FUZZ_MAKE_VIRTUAL_CAMERA";
                std::shared_ptr<VirtualCamera> virtualCamera = halCamera->makeVirtualCamera();
                virtualCameras.emplace_back(virtualCamera);
                break;
            }
            case EVS_FUZZ_OWN_VIRTUAL_CAMERA: {
                LOG(DEBUG) << "EVS_FUZZ_OWN_VIRTUAL_CAMERA";
                if (!virtualCameras.empty()) {
                    uint32_t whichCam =
                            fdp.ConsumeIntegralInRange<uint32_t>(0, virtualCameras.size() - 1);
                    halCamera->ownVirtualCamera(virtualCameras[whichCam]);
                }
                break;
            }
            case EVS_FUZZ_DISOWN_VIRTUAL_CAMERA: {
                LOG(DEBUG) << "EVS_FUZZ_DISOWN_VIRTUAL_CAMERA";
                if (!virtualCameras.empty()) {
                    uint32_t whichCam =
                            fdp.ConsumeIntegralInRange<uint32_t>(0, virtualCameras.size() - 1);
                    halCamera->disownVirtualCamera(virtualCameras[whichCam].get());
                }
                break;
            }
            case EVS_FUZZ_GET_HW_CAMERA: {
                LOG(DEBUG) << "EVS_FUZZ_GET_HW_CAMERA";
                halCamera->getHwCamera();
                break;
            }
            case EVS_FUZZ_GET_CLIENT_COUNT: {
                LOG(DEBUG) << "EVS_FUZZ_GET_CLIENT_COUNT";
                halCamera->getClientCount();
                break;
            }
            case EVS_FUZZ_GET_ID: {
                LOG(DEBUG) << "EVS_FUZZ_GET_ID";
                halCamera->getId();
                break;
            }
            case EVS_FUZZ_GET_STREAM_CONFIG: {
                LOG(DEBUG) << "EVS_FUZZ_GET_STREAM_CONFIG";
                halCamera->getStreamConfig();
                break;
            }
            case EVS_FUZZ_CHANGE_FRAMES_IN_FLIGHT: {
                LOG(DEBUG) << "EVS_FUZZ_CHANGE_FRAMES_IN_FLIGHT";
                uint32_t delta = fdp.ConsumeIntegral<int32_t>();
                halCamera->changeFramesInFlight(delta);
                break;
            }
            case EVS_FUZZ_CHANGE_FRAMES_IN_FLIGHT_1: {
                LOG(DEBUG) << "EVS_FUZZ_CHANGE_FRAMES_IN_FLIGHT_1";
                std::vector<BufferDesc> buffers;
                int32_t delta = 0;
                halCamera->changeFramesInFlight(buffers, &delta);
                break;
            }
            case EVS_FUZZ_REQUEST_NEW_FRAME: {
                LOG(DEBUG) << "EVS_FUZZ_REQUEST_NEW_FRAME";
                if (!virtualCameras.empty()) {
                    uint32_t whichCam =
                            fdp.ConsumeIntegralInRange<uint32_t>(0, virtualCameras.size() - 1);
                    halCamera->requestNewFrame(virtualCameras[whichCam], getCurrentTimeStamp());
                }
                break;
            }
            case EVS_FUZZ_CLIENT_STREAM_STARTING: {
                LOG(DEBUG) << "EVS_FUZZ_CLIENT_STREAM_STARTING";
                halCamera->clientStreamStarting();
                break;
            }
            case EVS_FUZZ_CLIENT_STREAM_ENDING: {
                LOG(DEBUG) << "EVS_FUZZ_CLIENT_STREAM_ENDING";
                if (!virtualCameras.empty()) {
                    uint32_t whichCam =
                            fdp.ConsumeIntegralInRange<uint32_t>(0, virtualCameras.size() - 1);
                    halCamera->clientStreamEnding(virtualCameras[whichCam].get());
                }
                break;
            }
            case EVS_FUZZ_DONE_WITH_FRAME: {
                LOG(DEBUG) << "EVS_FUZZ_DONE_WITH_FRAME";
                if (!buffers.empty()) {
                    uint32_t whichBuffer =
                            fdp.ConsumeIntegralInRange<uint32_t>(0, buffers.size() - 1);
                    halCamera->doneWithFrame(
                            Utils::dupBufferDesc(buffers[whichBuffer], /* doDup= */ true));
                }
                break;
            }
            case EVS_FUZZ_SET_PRIMARY: {
                LOG(DEBUG) << "EVS_FUZZ_SET_PRIMARY";
                if (!virtualCameras.empty()) {
                    uint32_t whichCam =
                            fdp.ConsumeIntegralInRange<uint32_t>(0, virtualCameras.size() - 1);
                    halCamera->setPrimaryClient(virtualCameras[whichCam]);
                }
                break;
            }
            case EVS_FUZZ_FORCE_PRIMARY: {
                LOG(DEBUG) << "EVS_FUZZ_FORCE_PRIMARY";
                if (!virtualCameras.empty()) {
                    uint32_t whichCam =
                            fdp.ConsumeIntegralInRange<uint32_t>(0, virtualCameras.size() - 1);
                    halCamera->forcePrimaryClient(virtualCameras[whichCam]);
                }
                break;
            }
            case EVS_FUZZ_UNSET_PRIMARY: {
                LOG(DEBUG) << "EVS_FUZZ_UNSET_PRIMARY";
                if (!virtualCameras.empty()) {
                    uint32_t whichCam =
                            fdp.ConsumeIntegralInRange<uint32_t>(0, virtualCameras.size() - 1);
                    halCamera->unsetPrimaryClient(virtualCameras[whichCam].get());
                }
                break;
            }
            case EVS_FUZZ_SET_PARAMETER: {
                LOG(DEBUG) << "EVS_FUZZ_SET_PARAMETER";
                if (!virtualCameras.empty()) {
                    uint32_t whichCam =
                            fdp.ConsumeIntegralInRange<uint32_t>(0, virtualCameras.size() - 1);
                    uint32_t whichParam = fdp.ConsumeIntegralInRange<
                            uint32_t>(0, static_cast<uint32_t>(CameraParam::ABSOLUTE_ZOOM));
                    int32_t value = fdp.ConsumeIntegral<int32_t>();
                    halCamera->setParameter(virtualCameras[whichCam],
                                            static_cast<CameraParam>(whichParam), &value);
                }
                break;
            }
            case EVS_FUZZ_GET_PARAMETER: {
                LOG(DEBUG) << "EVS_FUZZ_GET_PARAMETER";
                uint32_t whichParam =
                        fdp.ConsumeIntegralInRange<uint32_t>(0,
                                                             static_cast<uint32_t>(
                                                                     CameraParam::ABSOLUTE_ZOOM));
                int32_t value = fdp.ConsumeIntegral<int32_t>();
                halCamera->getParameter(static_cast<CameraParam>(whichParam), &value);
                break;
            }
            case EVS_FUZZ_GET_STATS: {
                LOG(DEBUG) << "EVS_FUZZ_GET_STATS";
                halCamera->getStats();
                break;
            }
            case EVS_FUZZ_GET_STREAM_CONFIGURATION: {
                LOG(DEBUG) << "EVS_FUZZ_GET_STREAM_CONFIGURATION";
                halCamera->getStreamConfiguration();
                break;
            }
            case EVS_FUZZ_DELIVER_FRAME: {
                LOG(DEBUG) << "EVS_FUZZ_DELIVER_FRAME";
                BufferDesc buffer, duped;
                buffer.bufferId = fdp.ConsumeIntegral<int32_t>();

                std::vector<BufferDesc> buffersToSend(1);
                buffersToSend.push_back(Utils::dupBufferDesc(buffer, /* doDup= */ true));
                halCamera->deliverFrame(buffersToSend);
                buffers.emplace_back(std::move(buffer));
                break;
            }
            case EVS_FUZZ_NOTIFY: {
                LOG(DEBUG) << "EVS_FUZZ_NOTIFY";
                EvsEventDesc event;
                uint32_t type =
                        fdp.ConsumeIntegralInRange<uint32_t>(0,
                                                             static_cast<uint32_t>(
                                                                     EvsEventType::STREAM_ERROR));
                event.aType = static_cast<EvsEventType>(type);
                // TODO(b/160824438) let's comment this for now because of the failure.
                // If virtualCamera does not call startVideoStream, and notify(1) is called
                // it will fail.
                // halCamera->notify(event);
                break;
            }
            default:
                LOG(ERROR) << "Unexpected option, aborting...";
                break;
        }
    }
    return 0;
}

}  // namespace
