// Copyright 2021 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <v4l2_codec2/components/VideoEncoder.h>

namespace android {

VideoEncoder::InputFrame::InputFrame(std::vector<int>&& fds,
                                     const std::vector<VideoFramePlane>& planes,
                                     VideoPixelFormat pixelFormat, uint64_t index,
                                     int64_t timestamp)
      : mFds(std::move(fds)),
        mPlanes(planes),
        mPixelFormat(pixelFormat),
        mIndex(index),
        mTimestamp(timestamp) {}

}  // namespace android
