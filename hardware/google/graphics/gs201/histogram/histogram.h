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

#ifndef HISTOGRAM_GS201_H_
#define HISTOGRAM_GS201_H_

#include <../gs101/include/histogram/HistogramInfo.h>
#include <../gs101/include/histogram/histogram_control.h>
#include <aidl/com/google/hardware/pixel/display/HistogramPos.h>

using HistogramPos =
    ::aidl::com::google::hardware::pixel::display::HistogramPos;

class IDLHistogram : public HistogramInfo {
 public:
  IDLHistogram() : HistogramInfo(HistogramType::HISTOGRAM_HIDL) {}
  virtual ~IDLHistogram() {}
  virtual void setHistogramPos(const HistogramPos& pos) {
      std::unique_lock<std::mutex> lk(mSetHistInfoMutex);
      mHistogramPos = pos;
  }
  HistogramPos getHistogramPos() {
      std::unique_lock<std::mutex> lk(mSetHistInfoMutex);
      return mHistogramPos;
  }

 private:
  HistogramPos mHistogramPos = HistogramPos::POST;
};

#endif  // HISTOGRAM_GS201_H_
