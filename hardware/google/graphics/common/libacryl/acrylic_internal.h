/*
 * Copyright Samsung Electronics Co.,LTD.
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef __HARDWARE_EXYNOS_ACRYLIC_INTERNAL_H__
#define __HARDWARE_EXYNOS_ACRYLIC_INTERNAL_H__

#include <cerrno>
#include <cstring>

#include <hardware/exynos/acryl.h>

#ifndef ALOGERR
#define ALOGERR(fmt, args...) ((void)ALOG(LOG_ERROR, LOG_TAG, fmt " [%s]", ##args, strerror(errno)))
#endif

#ifdef LOG_NDEBUG
#define LOGASSERT ALOG_ASSERT
#else
#define LOGASSERT(cond, ...)  if (!(cond)) ALOGE(__VA_ARGS__)
#endif

#ifdef LIBACRYL_DEBUG
#define ALOGD_TEST(args...) ALOGD("TEST: " args)
#else
#define ALOGD_TEST(...)
#endif

#define ARRSIZE(arr) (sizeof(arr) / sizeof(arr[0]))

static inline int32_t get_width(hwc_rect_t &rect)
{
    return rect.right - rect.left;
}

static inline int32_t get_height(hwc_rect_t &rect)
{
    return rect.bottom - rect.top;
}

static inline bool operator==(hw2d_coord_t xy1, hw2d_coord_t xy2)
{
    return (xy1.hori == xy2.hori) && (xy1.vert == xy2.vert);
}

static inline bool operator!=(hw2d_coord_t xy1, hw2d_coord_t xy2)
{
    return !(xy1 == xy2);
}

static inline bool operator==(hw2d_rect_t r1, hw2d_rect_t r2)
{
    return (r1.pos == r2.pos) && (r1.size == r2.size);
}

static inline bool operator!=(hw2d_rect_t r1, hw2d_rect_t r2)
{
    return !(r1 == r2);
}

static inline bool operator==(hw2d_rect_t r1, hwc_rect_t r2)
{
    return (r1.pos.hori == r2.left) && (r1.pos.vert == r2.top) &&
           ((r1.pos.hori + r1.size.hori) == r2.right) &&
           ((r1.pos.vert + r1.size.vert) == r2.bottom);

}

static inline bool operator==(hwc_rect_t r1, hw2d_rect_t r2)
{
    return operator==(r2, r1);
}

static inline bool operator!=(hw2d_rect_t r1, hwc_rect_t r2)
{
    return !(r1 == r2);
}

static inline bool operator!=(hwc_rect_t r1, hw2d_rect_t r2)
{
    return !(r2 == r1);
}

static inline bool operator>(hw2d_rect_t rect, hw2d_coord_t xy)
{
    int32_t len = static_cast<int32_t>(rect.pos.hori) + rect.size.hori;
    if (len > xy.hori)
        return true;

    len = static_cast<int32_t>(rect.pos.vert) + rect.size.vert;
    if (len > xy.vert)
        return true;

    return false;
}

static inline bool area_is_zero(hw2d_rect_t rect)
{
    return (rect.size.hori == 0) && (rect.size.vert == 0);
}

uint32_t halfmt_to_v4l2(uint32_t halfmt);
uint32_t halfmt_to_v4l2_deprecated(uint32_t halfmt);
unsigned int halfmt_buf_count(uint32_t fmt);
size_t halfmt_plane_length(uint32_t fmt, unsigned int plane, uint32_t width, uint32_t height);
uint32_t haldataspace_to_v4l2(int dataspace, uint32_t width, uint32_t height);
uint32_t find_format_equivalent(uint32_t fmt);
uint8_t halfmt_chroma_subsampling(uint32_t fmt);
unsigned int halfmt_bpp(uint32_t fmt);
uint8_t halfmt_plane_count(uint32_t fmt);

#endif /* __HARDWARE_EXYNOS_ACRYLIC_INTERNAL_H__ */
