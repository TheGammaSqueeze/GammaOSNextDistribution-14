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
#include "MockHWDisplay.h"

#include <fuzzer/FuzzedDataProvider.h>

#include <sys/time.h>

#include <iostream>

using ::android::hardware::automotive::evs::V1_0::DisplayDesc;
using ::android::hardware::automotive::evs::V1_0::DisplayState;
using IEvsDisplay_1_0 = ::android::hardware::automotive::evs::V1_0::IEvsDisplay;

namespace android::automotive::evs::V1_1::implementation {

namespace {

enum EvsFuzzFuncs {
    EVS_FUZZ_GET_HW_DISPLAY = 0,       // verify getHwDisplay
    EVS_FUZZ_GET_DISPLAY_INFO,         // verify getDisplayInfo
    EVS_FUZZ_SET_DISPLAY_STATE,        // verify setDisplayState
    EVS_FUZZ_GET_DISPLAY_STATE,        // verify getDisplayState
    EVS_FUZZ_GET_TARGET_BUFFER,        // verify getTargetBuffer
    EVS_FUZZ_RTN_TGT_BUF_FOR_DISPLAY,  // verify returnTargetBufferForDisplay
    EVS_FUZZ_GET_DISPLAY_INFO_1_1,     // verify getDisplayInfo_1_1
    EVS_FUZZ_TO_STRING,                // verify toString
    EVS_FUZZ_API_SUM
};

const int kMaxFuzzerConsumedBytes = 12;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    FuzzedDataProvider fdp(data, size);
    sp<IEvsDisplay_1_0> mockHWDisplay = new MockHWDisplay();
    sp<HalDisplay> halDisplay = new HalDisplay(mockHWDisplay);

    while (fdp.remaining_bytes() > kMaxFuzzerConsumedBytes) {
        switch (fdp.ConsumeIntegralInRange<uint32_t>(0, EVS_FUZZ_API_SUM)) {
            case EVS_FUZZ_GET_HW_DISPLAY: {
                LOG(DEBUG) << "EVS_FUZZ_GET_HW_DISPLAY";
                halDisplay->getHwDisplay();
                break;
            }
            case EVS_FUZZ_GET_DISPLAY_INFO: {
                LOG(DEBUG) << "EVS_FUZZ_GET_DISPLAY_INFO";
                halDisplay->getDisplayInfo([](DisplayDesc desc) {});
                break;
            }
            case EVS_FUZZ_SET_DISPLAY_STATE: {
                LOG(DEBUG) << "EVS_FUZZ_SET_DISPLAY_STATE";
                uint32_t state =
                        fdp.ConsumeIntegralInRange<uint32_t>(0,
                                                             static_cast<uint32_t>(
                                                                     DisplayState::NUM_STATES) -
                                                                     1);
                halDisplay->setDisplayState(static_cast<DisplayState>(state));
                break;
            }
            case EVS_FUZZ_GET_DISPLAY_STATE: {
                LOG(DEBUG) << "EVS_FUZZ_GET_DISPLAY_STATE";
                halDisplay->getDisplayState();
                break;
            }
            case EVS_FUZZ_GET_TARGET_BUFFER: {
                LOG(DEBUG) << "EVS_FUZZ_GET_TARGET_BUFFER";
                halDisplay->getTargetBuffer([](const BufferDesc_1_0& buff) {});
                break;
            }
            case EVS_FUZZ_RTN_TGT_BUF_FOR_DISPLAY: {
                LOG(DEBUG) << "EVS_FUZZ_RTN_TGT_BUF_FOR_DISPLAY";
                BufferDesc_1_0 buffer;
                buffer.bufferId = fdp.ConsumeIntegral<int32_t>();
                halDisplay->returnTargetBufferForDisplay(buffer);
                break;
            }
            case EVS_FUZZ_GET_DISPLAY_INFO_1_1: {
                LOG(DEBUG) << "EVS_FUZZ_GET_DISPLAY_INFO_1_1";
                halDisplay->getDisplayInfo_1_1([](const auto& config, const auto& state) {});
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
}  // namespace android::automotive::evs::V1_1::implementation
