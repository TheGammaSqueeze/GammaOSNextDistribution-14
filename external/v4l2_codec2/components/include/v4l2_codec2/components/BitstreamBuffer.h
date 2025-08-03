// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_V4L2_CODEC2_COMPONENTS_BITSTREAMBUFFER_H
#define ANDROID_V4L2_CODEC2_COMPONENTS_BITSTREAMBUFFER_H

#include <stdint.h>

#include <C2Buffer.h>

namespace android {

// The ConstBitstreamBuffer class can be used to store non-modifiable encoded video data.
struct ConstBitstreamBuffer {
    ConstBitstreamBuffer(const int32_t id, C2ConstLinearBlock dmabuf, const size_t offset,
                         const size_t size)
          : id(id), dmabuf(std::move(dmabuf)), offset(offset), size(size) {}
    ~ConstBitstreamBuffer() = default;

    const int32_t id;
    C2ConstLinearBlock dmabuf;
    const size_t offset;
    const size_t size;
};

// The BitstreamBuffer class can be used to store modifiable encoded video data.
struct BitstreamBuffer {
    BitstreamBuffer(std::shared_ptr<C2LinearBlock> dmabuf, const size_t offset, const size_t size)
          : dmabuf(std::move(dmabuf)), offset(offset), size(size) {}
    ~BitstreamBuffer() = default;

    std::shared_ptr<C2LinearBlock> dmabuf;
    const size_t offset;
    const size_t size;
};

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_COMPONENTS_BITSTREAMBUFFER_H
