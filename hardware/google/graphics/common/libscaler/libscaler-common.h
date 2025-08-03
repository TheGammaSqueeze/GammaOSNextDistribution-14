/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright@ Samsung Electronics Co. LTD
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

/*!
 * \file      libscaler-common.h
 * \brief     source file for Scaler HAL
 * \author    Cho KyongHo <pullip.cho@samsung.com>
 * \date      2014/05/08
 *
 * <b>Revision History: </b>
 * - 2014.05.08 : Cho KyongHo (pullip.cho@samsung.com) \n
 *   Create
 */
#ifndef _LIBSCALER_COMMON_H_
#define _LIBSCALER_COMMON_H_

#define LOG_TAG "libexynosscaler"
#include <log/log.h>
#include <cerrno>
#include <cstring>

//#define LOG_NDEBUG 0

#ifdef __GNUC__
#  define __UNUSED__ __attribute__((__unused__))
#else
#  define __UNUSED__
#endif

#define SC_LOGERR(fmt, args...) ((void)ALOG(LOG_ERROR, LOG_TAG, "%s: " fmt " [%s]", __func__, ##args, strerror(errno)))
#define SC_LOGE(fmt, args...)     ((void)ALOG(LOG_ERROR, LOG_TAG, "%s: " fmt, __func__, ##args))
#define SC_LOGI(fmt, args...)     ((void)ALOG(LOG_INFO, LOG_TAG, "%s: " fmt, __func__, ##args))
#define SC_LOGI_IF(cond, fmt, args...)  do { \
    if (cond)                                \
        SC_LOGI(fmt, ##args);                \
    } while (0)
#define SC_LOGE_IF(cond, fmt, args...)  do { \
    if (cond)                                \
        SC_LOGE(fmt, ##args);                   \
    } while (0)
#define SC_LOG_ASSERT(cont, fmt, args...) ((void)ALOG_ASSERT(cond, "%s: " fmt, __func__, ##args))

#ifdef SC_DEBUG
#define SC_LOGD(args...) ((void)ALOG(LOG_INFO, LOG_TAG, ##args))
#define SC_LOGD_IF(cond, fmt, args...)  do { \
    if (cond)                                \
        SC_LOGD(fmt, ##args);                \
    } while (0)
#else
#define SC_LOGD(args...) do { } while (0)
#define SC_LOGD_IF(cond, fmt, args...)  do { } while (0)
#endif

#define ARRSIZE(arr) (sizeof(arr)/sizeof(arr[0]))



namespace LibScaler {
template <typename T>
static inline T min (T a, T b) {
    return (a > b) ? b : a;
}

template <typename T>
static inline void swap(T &a, T &b) {
    T t = a;
    a = b;
    b = t;
}

static inline bool UnderOne16thScaling(unsigned int srcw, unsigned int srch,
        unsigned int dstw, unsigned int dsth, unsigned int rot) {
    if ((rot == 90) || (rot == 270))
        swap(srcw, srch);

    return ((srcw > (dstw * 16)) || (srch > (dsth * 16)));
}

};
// marker for output parameters
#define __out

#define V4L2_CID_2D_SRC_BLEND_SET_FMT (V4L2_CID_EXYNOS_BASE + 116)
#define V4L2_CID_2D_SRC_BLEND_SET_H_POS (V4L2_CID_EXYNOS_BASE + 117)
#define V4L2_CID_2D_SRC_BLEND_SET_V_POS (V4L2_CID_EXYNOS_BASE + 118)
#define V4L2_CID_2D_SRC_BLEND_FMT_PREMULTI (V4L2_CID_EXYNOS_BASE + 119)
#define V4L2_CID_2D_SRC_BLEND_SET_STRIDE (V4L2_CID_EXYNOS_BASE + 120)
#define V4L2_CID_2D_SRC_BLEND_SET_WIDTH (V4L2_CID_EXYNOS_BASE + 121)
#define V4L2_CID_2D_SRC_BLEND_SET_HEIGHT (V4L2_CID_EXYNOS_BASE + 122)

#ifndef V4L2_CID_GLOBAL_ALPHA
#define V4L2_CID_GLOBAL_ALPHA          (V4L2_CID_EXYNOS_BASE + 1)
#endif
#ifndef V4L2_CID_2D_BLEND_OP
#define V4L2_CID_2D_BLEND_OP           (V4L2_CID_EXYNOS_BASE + 103)
#endif
#ifndef V4L2_CID_2D_COLOR_FILL
#define V4L2_CID_2D_COLOR_FILL         (V4L2_CID_EXYNOS_BASE + 104)
#endif
#ifndef V4L2_CID_2D_DITH
#define V4L2_CID_2D_DITH               (V4L2_CID_EXYNOS_BASE + 105)
#endif
#ifndef V4L2_CID_2D_FMT_PREMULTI
#define V4L2_CID_2D_FMT_PREMULTI       (V4L2_CID_EXYNOS_BASE + 106)
#endif

#define SC_CID_FRAMERATE               (V4L2_CID_EXYNOS_BASE + 110)

#define LIBSC_V4L2_CID_DNOISE_FT        (V4L2_CID_EXYNOS_BASE + 150)
#define LIBSC_M2M1SHOT_OP_FILTER_SHIFT  (28)
#define LIBSC_M2M1SHOT_OP_FILTER_MASK   (0xf << 28)

#endif //_LIBSCALER_COMMON_H_
