// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_V4L2_CODEC2_V4L2_V4L2_COMPONENT_STORE_H
#define ANDROID_V4L2_CODEC2_V4L2_V4L2_COMPONENT_STORE_H

#include <C2Component.h>

namespace android {

struct V4L2ComponentStore {
    static std::shared_ptr<C2ComponentStore> Create();
};

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_V4L2_V4L2_COMPONENT_STORE_H
