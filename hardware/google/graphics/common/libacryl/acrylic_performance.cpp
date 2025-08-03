/*
 * Copyright Samsung Electronics Co.,LTD.  * Copyright (C) 2016 The Android Open Source Project
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

#include <log/log.h>

#include <hardware/exynos/acryl.h>

#include "acrylic_internal.h"

AcrylicPerformanceRequest::AcrylicPerformanceRequest()
    : mNumFrames(0), mNumAllocFrames(0), mFrames(NULL)
{
}

AcrylicPerformanceRequest::~AcrylicPerformanceRequest()
{
    delete [] mFrames;
}

bool AcrylicPerformanceRequest::reset(int num_frames)
{
    if (num_frames == 0) {
        delete [] mFrames;
        mFrames = NULL;
    } else if (num_frames > mNumAllocFrames) {
        delete [] mFrames;
        mFrames = new AcrylicPerformanceRequestFrame[num_frames];
        if (mFrames == NULL) {
            ALOGE("Failed to allocate PerformanceRequestFrame[%d]", num_frames);
            return false;
        }

        mNumAllocFrames = num_frames;
    }

    mNumFrames = num_frames;

    return true;
}

AcrylicPerformanceRequestFrame::AcrylicPerformanceRequestFrame()
    : mNumLayers(0), mNumAllocLayers(0), mFrameRate(60),
      mHasBackgroundLayer(false), mLayers(NULL)
{
}


AcrylicPerformanceRequestFrame::~AcrylicPerformanceRequestFrame()
{
    delete [] mLayers;
}

bool AcrylicPerformanceRequestFrame::reset(int num_layers)
{
    if (num_layers == 0) {
        delete [] mLayers;
        mLayers = NULL;
    } else if (num_layers > mNumAllocLayers) {
        mLayers = new AcrylicPerformanceRequestLayer[num_layers];
        if (mLayers == NULL) {
            ALOGE("Failed to allocate PerformanceRequestLayer[%d]", num_layers);
            return false;
        }

        mNumAllocLayers = num_layers;
    }

    mNumLayers = num_layers;

    return true;
}
