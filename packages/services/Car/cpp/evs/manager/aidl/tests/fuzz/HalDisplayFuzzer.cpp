/*
 * Copyright 2020 The Android Open Source Project
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
#include "HalDisplay.h"
#include "MockEvsDisplay.h"

#include <fuzzbinder/libbinder_ndk_driver.h>
#include <fuzzer/FuzzedDataProvider.h>

#include <sys/time.h>

#include <iostream>

namespace {

using aidl::android::automotive::evs::implementation::HalDisplay;
using aidl::android::automotive::evs::implementation::NiceMockEvsDisplay;
using aidl::android::hardware::automotive::evs::BufferDesc;
using aidl::android::hardware::automotive::evs::DisplayDesc;
using aidl::android::hardware::automotive::evs::DisplayState;
using aidl::android::hardware::automotive::evs::IEvsDisplay;

enum EvsFuzzFuncs {
    EVS_FUZZ_GET_DISPLAY_INFO = 0,     // verify getDisplayInfo
    EVS_FUZZ_SET_DISPLAY_STATE,        // verify setDisplayState
    EVS_FUZZ_GET_DISPLAY_STATE,        // verify getDisplayState
    EVS_FUZZ_GET_TARGET_BUFFER,        // verify getTargetBuffer
    EVS_FUZZ_RTN_TGT_BUF_FOR_DISPLAY,  // verify returnTargetBufferForDisplay
    EVS_FUZZ_TO_STRING,                // verify toString
    EVS_FUZZ_API_SUM
};

const int kMaxFuzzerConsumedBytes = 12;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    FuzzedDataProvider fdp(data, size);
    std::shared_ptr<IEvsDisplay> mockHwDisplay = ndk::SharedRefBase::make<NiceMockEvsDisplay>();
    std::shared_ptr<HalDisplay> halDisplay = ndk::SharedRefBase::make<HalDisplay>(mockHwDisplay);

    while (fdp.remaining_bytes() > kMaxFuzzerConsumedBytes) {
        switch (fdp.ConsumeIntegralInRange<uint32_t>(0, EVS_FUZZ_API_SUM)) {
            case EVS_FUZZ_GET_DISPLAY_INFO: {
                LOG(DEBUG) << "EVS_FUZZ_GET_DISPLAY_INFO";
                DisplayDesc desc;
                halDisplay->getDisplayInfo(&desc);
                break;
            }
            case EVS_FUZZ_SET_DISPLAY_STATE: {
                LOG(DEBUG) << "EVS_FUZZ_SET_DISPLAY_STATE";
                uint32_t state =
                        fdp.ConsumeIntegralInRange<uint32_t>(0,
                                                             static_cast<uint32_t>(
                                                                     DisplayState::DEAD));
                halDisplay->setDisplayState(static_cast<DisplayState>(state));
                break;
            }
            case EVS_FUZZ_GET_DISPLAY_STATE: {
                LOG(DEBUG) << "EVS_FUZZ_GET_DISPLAY_STATE";
                DisplayState state;
                halDisplay->getDisplayState(&state);
                break;
            }
            case EVS_FUZZ_GET_TARGET_BUFFER: {
                LOG(DEBUG) << "EVS_FUZZ_GET_TARGET_BUFFER";
                BufferDesc displayBuffer;
                halDisplay->getTargetBuffer(&displayBuffer);
                break;
            }
            case EVS_FUZZ_RTN_TGT_BUF_FOR_DISPLAY: {
                LOG(DEBUG) << "EVS_FUZZ_RTN_TGT_BUF_FOR_DISPLAY";
                BufferDesc buffer;
                buffer.bufferId = fdp.ConsumeIntegral<int32_t>();
                halDisplay->returnTargetBufferForDisplay(buffer);
                break;
            }
            case EVS_FUZZ_TO_STRING: {
                LOG(DEBUG) << "EVS_FUZZ_TO_STRING";
                std::string indent = fdp.ConsumeRandomLengthString(kMaxFuzzerConsumedBytes);
                halDisplay->toString(indent.c_str());
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
