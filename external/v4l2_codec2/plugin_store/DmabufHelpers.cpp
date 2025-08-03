// Copyright 2021 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "DmabufHelpers"

#include <v4l2_codec2/plugin_store/DmabufHelpers.h>

#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <log/log.h>

namespace android {

std::optional<unique_id_t> getDmabufId(int dmabufFd) {
    struct stat sb {};
    if (fstat(dmabufFd, &sb) != 0) {
        return std::nullopt;
    }

    if (sb.st_size == 0) {
        ALOGE("Dma-buf size is 0. Please check your kernel is v5.3+");
        return std::nullopt;
    }

    return static_cast<unique_id_t>(sb.st_ino);
}

}  // namespace android
