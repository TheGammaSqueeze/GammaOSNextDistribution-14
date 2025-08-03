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

#ifndef HISTOGRAM_MEDIATOR_H_
#define HISTOGRAM_MEDIATOR_H_

#include <aidl/android/hardware/graphics/common/Rect.h>
#include <aidl/com/google/hardware/pixel/display/BnDisplay.h>
#include <aidl/com/google/hardware/pixel/display/HistogramErrorCode.h>
#include <aidl/com/google/hardware/pixel/display/HistogramPos.h>
#include <aidl/com/google/hardware/pixel/display/Priority.h>
#include <aidl/com/google/hardware/pixel/display/Weight.h>
#include <xf86drm.h>
#include <xf86drmMode.h>

#include <condition_variable>
#include <mutex>
#include <vector>

#define HWC2_INCLUDE_STRINGIFICATION
#define HWC2_USE_CPP11
#include <hardware/hwcomposer2.h>
#undef HWC2_INCLUDE_STRINGIFICATION
#undef HWC2_USE_CPP11
#include "ExynosDevice.h"
#include "ExynosDisplay.h"
#include "ExynosDisplayDrmInterfaceModule.h"
#include "ExynosDisplayInterface.h"
#include "ExynosLayer.h"

namespace histogram {
using RoiRect = ::aidl::android::hardware::graphics::common::Rect;
using Weight = ::aidl::com::google::hardware::pixel::display::Weight;
using HistogramPos = ::aidl::com::google::hardware::pixel::display::HistogramPos;
using Priority = ::aidl::com::google::hardware::pixel::display::Priority;
using HistogramErrorCode = ::aidl::com::google::hardware::pixel::display::HistogramErrorCode;

constexpr size_t HISTOGRAM_BINS_SIZE = 256;
constexpr size_t WEIGHT_SUM = 1024;

class HistogramMediator {
public:
    HistogramMediator() = default;
    HistogramMediator(ExynosDisplay *display);
    ~HistogramMediator() {}

    HistogramErrorCode requestHist();
    HistogramErrorCode cancelHistRequest();
    HistogramErrorCode collectRoiLuma(std::vector<char16_t> *buf);
    HistogramErrorCode setRoiWeightThreshold(const RoiRect &roi, const Weight &weight,
                                             const HistogramPos &pos);
    RoiRect calRoi(const RoiRect &roi);
    struct HistogramReceiver : public IDLHistogram {
        HistogramReceiver() : mHistData(){};
        void callbackHistogram(char16_t *bin) override;
        uint16_t mHistData[HISTOGRAM_BINS_SIZE]; // luma buffer
        std::condition_variable mHistData_cv;    // for pullback data sync ctrl
        bool mHistReq_pending = false;
        std::mutex mDataCollectingMutex; // for data collecting operations
    };

    struct HistogramConfig {
        RoiRect mRoi;
        Weight mWeights;
        HistogramPos mPos;

        HistogramConfig() {}

        HistogramConfig(const RoiRect &roi, const Weight &weights, const HistogramPos &pos) {
            mRoi = roi;
            mWeights = weights;
            mPos = pos;
        }

        bool operator!=(const HistogramConfig &rhs) {
            return mRoi != rhs.mRoi || mWeights != rhs.mWeights || mPos != rhs.mPos;
        }

        HistogramConfig &operator=(const HistogramConfig &rhs) {
            mRoi = rhs.mRoi;
            mWeights = rhs.mWeights;
            mPos = rhs.mPos;
            return *this;
        }
    };

    uint32_t getFrameCount();
    void setSampleFrameCounter(int32_t id) { mSampledFrameCounter = id; }
    uint32_t getSampleFrameCounter() { return mSampledFrameCounter; }
    bool histRequested() { return mIDLHistogram->mHistReq_pending; }
    std::mutex mConfigMutex;
    HistogramConfig mConfig GUARDED_BY(mConfigMutex);

private:
    int calculateThreshold(const RoiRect &roi);
    std::shared_ptr<HistogramReceiver> mIDLHistogram;
    ExynosDisplay *mDisplay = nullptr;
    uint32_t mSampledFrameCounter = 0;
};

} // namespace histogram

#endif // HISTOGRAM_MEDIATOR_H_
