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

#ifndef HISTOGRAM_H_
#define HISTOGRAM_H_

#include <aidl/com/google/hardware/pixel/display/HistogramPos.h>

#include "histogram/HistogramInfo.h"
#include "histogram/histogram_control.h"
using HistogramPos = ::aidl::com::google::hardware::pixel::display::HistogramPos;

class IDLHistogram : public HistogramInfo {
public:
    IDLHistogram() : HistogramInfo(HistogramType::HISTOGRAM_HIDL) {}
    virtual ~IDLHistogram() {}
    virtual void setHistogramPos(const HistogramPos& pos) {}
};

#endif // HISTOGRAM_H_
