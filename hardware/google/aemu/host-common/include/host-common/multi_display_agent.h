// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include "aemu/base/c_header.h"

#include <stdint.h>
#include <stdbool.h>

ANDROID_BEGIN_HEADER

typedef struct QAndroidMultiDisplayAgent {
    bool (*notifyDisplayChanges)();
    int (*setMultiDisplay)(uint32_t id,
                           int32_t x,
                           int32_t y,
                           uint32_t w,
                           uint32_t h,
                           uint32_t dpi,
                           uint32_t flag,
                           bool add);
    bool (*getMultiDisplay)(uint32_t id,
                            int32_t* x,
                            int32_t* y,
                            uint32_t* w,
                            uint32_t* h,
                            uint32_t* dpi,
                            uint32_t* flag,
                            bool* enable);
    bool (*getNextMultiDisplay)(int32_t start_id,
                                uint32_t* id,
                                int32_t* x,
                                int32_t* y,
                                uint32_t* w,
                                uint32_t* h,
                                uint32_t* dpi,
                                uint32_t* flag,
                                uint32_t* cb);
    bool (*isMultiDisplayEnabled)(void);
    void (*getCombinedDisplaySize)(uint32_t* width, uint32_t* height);
    bool (*multiDisplayParamValidate)(uint32_t id, uint32_t w, uint32_t h,
                                      uint32_t dpi, uint32_t flag);
    bool (*translateCoordination)(uint32_t* x, uint32_t*y, uint32_t* displayId);
    void (*setGpuMode)(bool isGuestMode, uint32_t w, uint32_t h);
    int (*createDisplay)(uint32_t* displayId);
    int (*destroyDisplay)(uint32_t displayId);
    int (*setDisplayPose)(uint32_t displayId,
                          int32_t x,
                          int32_t y,
                          uint32_t w,
                          uint32_t h,
                          uint32_t dpi);
    int (*getDisplayPose)(uint32_t displayId,
                         int32_t* x,
                         int32_t* y,
                         uint32_t* w,
                         uint32_t* h);
    int (*getDisplayColorBuffer)(uint32_t displayId, uint32_t* colorBuffer);
    int (*getColorBufferDisplay)(uint32_t colorBuffer, uint32_t* displayId);
    int (*setDisplayColorBuffer)(uint32_t displayId, uint32_t colorBuffer);
    bool (*isMultiDisplayWindow)();
    void (*performRotation)(int rot);
    bool (*isPixelFold)();
} QAndroidMultiDisplayAgent;


ANDROID_END_HEADER
