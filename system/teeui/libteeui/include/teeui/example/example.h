/*
 *
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stddef.h>
#include <string>

#pragma once

namespace teeui {
namespace example {

struct DeviceInfo {
    uint32_t width_;
    uint32_t height_;
    double dp2px_;
    double mm2px_;
    double powerButtonTopMm_;
    double powerButtonBottomMm_;
    double volUpButtonTopMm_;
    double volUpButtonBottomMm_;
};

enum class EventResult : uint32_t {
    NONE,
    CONFIRM,
    CANCEL,
};

class ITeeuiExample {
  public:
    virtual void selectLanguage(const char*) = 0;
    virtual void setConfirmationMessage(std::string) = 0;
    virtual uint32_t setDeviceInfo(DeviceInfo, bool, bool) = 0;
    virtual uint32_t renderUIIntoBuffer(uint32_t, uint32_t, uint32_t, uint32_t, uint32_t, uint32_t*,
                                        size_t) = 0;
    virtual EventResult onEvent(uint32_t x, uint32_t y, uint32_t) = 0;

    virtual ~ITeeuiExample() {}
};

enum class Examples : uint32_t {
    PhysButton,
    TouchButton,
};

static constexpr const int8_t kFrameBufferError = -1;
static constexpr const int8_t kLayoutExampleError = -2;
static constexpr const char* kPhysButtonLayout = "Physical button";
static constexpr const char* kTouchButtonLayout = "Touch button";

static constexpr const char* kAvailableLayouts[] = {
    kPhysButtonLayout,
    kTouchButtonLayout,
};

#define NUM_LAYOUTS ((sizeof(kAvailableLayouts) / sizeof(kAvailableLayouts[0])))

std::unique_ptr<ITeeuiExample> createExample(Examples example);

}  // namespace example
}  // namespace teeui
