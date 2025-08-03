/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <android/hardware/graphics/common/1.0/types.h>

using android::hardware::graphics::common::V1_0::PixelFormat;
using android::hardware::graphics::common::V1_0::toString;

std::string toStringNotInlined(PixelFormat format) asm(
        "_ZN7android8hardware8graphics6common4V1_08toStringENS3_11PixelFormatE");
std::string toStringNotInlined(PixelFormat format) {
    return toString(format);
}
