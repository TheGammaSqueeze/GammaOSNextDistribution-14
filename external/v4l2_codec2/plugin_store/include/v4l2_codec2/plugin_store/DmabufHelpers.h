// Copyright 2021 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_V4L2_CODEC2_PLUGIN_STORE_DMABUF_HELPERS_H
#define ANDROID_V4L2_CODEC2_PLUGIN_STORE_DMABUF_HELPERS_H

#include <inttypes.h>

#include <optional>

namespace android {

// We use the value of dma-buf inode as the unique ID of the graphic buffers.
using unique_id_t = uint32_t;
std::optional<unique_id_t> getDmabufId(int dmabufFd);

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_PLUGIN_STORE_DMABUF_HELPERS_H
