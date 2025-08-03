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

#ifndef HISTOGRAMINFO_H_
#define HISTOGRAMINFO_H_
#include <aidl/com/google/hardware/pixel/display/HistogramPos.h>
#include <drm/samsung_drm.h>

#include <mutex>

using HistogramPos = ::aidl::com::google::hardware::pixel::display::HistogramPos;

class HistogramInfo {
public:
    enum class HistogramType { HISTOGRAM_SAMPLING = 0, HISTOGRAM_HIDL, HISTOGRAM_TYPE_NUM };
    void setHistogramROI(uint16_t x, uint16_t y, uint16_t h, uint16_t v) {
        std::unique_lock<std::mutex> lk(mSetHistInfoMutex);
        mHistogramROI.start_x = x;
        mHistogramROI.start_y = y;
        mHistogramROI.hsize = h;
        mHistogramROI.vsize = v;
    };
    const struct histogram_roi& getHistogramROI() { return mHistogramROI; }

    void setHistogramWeights(uint16_t r, uint16_t g, uint16_t b) {
        std::unique_lock<std::mutex> lk(mSetHistInfoMutex);
        mHistogramWeights.weight_r = r;
        mHistogramWeights.weight_g = g;
        mHistogramWeights.weight_b = b;
    };
    const struct histogram_weights& getHistogramWeights() { return mHistogramWeights; }

    void setHistogramThreshold(uint32_t t) {
        std::unique_lock<std::mutex> lk(mSetHistInfoMutex);
        mHistogramThreshold = t;
    }

    uint32_t getHistogramThreshold() {
        std::unique_lock<std::mutex> lk(mSetHistInfoMutex);
        return mHistogramThreshold;
    }

    HistogramType getHistogramType() { return mHistogramType; }

    HistogramInfo(HistogramType type) { mHistogramType = type; }
    virtual ~HistogramInfo() {}
    virtual void setHistogramPos(const HistogramPos& pos) = 0;
    virtual void callbackHistogram(char16_t* bin) = 0;
    std::mutex mSetHistInfoMutex;

private:
    HistogramType mHistogramType = HistogramType::HISTOGRAM_TYPE_NUM;
    struct histogram_roi mHistogramROI;
    struct histogram_weights mHistogramWeights;
    uint32_t mHistogramThreshold = 0;
};

#endif // HISTOGRAM_H_
