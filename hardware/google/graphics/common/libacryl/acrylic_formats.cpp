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

#include <linux/videodev2.h>
#include <log/log.h>
#include <mali_gralloc_formats.h>
#include <system/graphics.h>
#include <exynos_format.h> // hardware/smasung_slsi/exynos/include
#include "acrylic_internal.h"

#define V4L2_PIX_FMT_NV12N             v4l2_fourcc('N', 'N', '1', '2')
#define V4L2_PIX_FMT_NV12N_10B         v4l2_fourcc('B', 'N', '1', '2')
#define V4L2_PIX_FMT_YUV420N           v4l2_fourcc('Y', 'N', '1', '2')
#define V4L2_PIX_FMT_NV12M_S10B        v4l2_fourcc('B', 'M', '1', '2')
#define V4L2_PIX_FMT_NV21M_S10B        v4l2_fourcc('B', 'M', '2', '1')
#define V4L2_PIX_FMT_NV16M_S10B        v4l2_fourcc('B', 'M', '1', '6')
#define V4L2_PIX_FMT_NV61M_S10B        v4l2_fourcc('B', 'M', '6', '1')
#define V4L2_PIX_FMT_NV12M_P010        v4l2_fourcc('P', 'M', '1', '2')
#define V4L2_PIX_FMT_NV21M_P010        v4l2_fourcc('P', 'M', '2', '1')
#define V4L2_PIX_FMT_NV16M_P210        v4l2_fourcc('P', 'M', '1', '6')
#define V4L2_PIX_FMT_NV61M_P210        v4l2_fourcc('P', 'M', '6', '1')
#define V4L2_PIX_FMT_NV12_P010         v4l2_fourcc('P', 'N', '1', '2')
#define V4L2_PIX_FMT_ARGB2101010       v4l2_fourcc('A', 'R', '3', '0')
#define V4L2_PIX_FMT_ABGR2101010       v4l2_fourcc('A', 'R', '1', '0')
#define V4L2_PIX_FMT_RGBA1010102       v4l2_fourcc('R', 'A', '3', '0')
#define V4L2_PIX_FMT_BGRA1010102       v4l2_fourcc('B', 'A', '1', '0')

/* 12 Y/CbCr 4:2:0 SBWC */
#define V4L2_PIX_FMT_NV12M_SBWC_8B     v4l2_fourcc('M', '1', 'S', '8')
#define V4L2_PIX_FMT_NV12M_SBWC_10B    v4l2_fourcc('M', '1', 'S', '1')
/* 21 Y/CrCb 4:2:0 SBWC */
#define V4L2_PIX_FMT_NV21M_SBWC_8B     v4l2_fourcc('M', '2', 'S', '8')
#define V4L2_PIX_FMT_NV21M_SBWC_10B    v4l2_fourcc('M', '2', 'S', '1')
/* 12 Y/CbCr 4:2:0 SBWC single */
#define V4L2_PIX_FMT_NV12N_SBWC_8B     v4l2_fourcc('N', '1', 'S', '8')
#define V4L2_PIX_FMT_NV12N_SBWC_10B    v4l2_fourcc('N', '1', 'S', '1')
/* 12 Y/CbCr 4:2:0 SBWC Lossy */
#define V4L2_PIX_FMT_NV12M_SBWCL_8B    v4l2_fourcc('M', '1', 'L', '8')
#define V4L2_PIX_FMT_NV12M_SBWCL_10B   v4l2_fourcc('M', '1', 'L', '1')
/* 12 Y/CbCr 4:2:0 SBWC Lossy single */
#define V4L2_PIX_FMT_NV12N_SBWCL_8B    v4l2_fourcc('N', '1', 'L', '8')
#define V4L2_PIX_FMT_NV12N_SBWCL_10B   v4l2_fourcc('N', '1', 'L', '1')


static uint32_t __halfmt_to_v4l2_rgb[][2] = {
    {HAL_PIXEL_FORMAT_RGBA_8888,                    V4L2_PIX_FMT_ABGR32   },
    {HAL_PIXEL_FORMAT_BGRA_8888,                    V4L2_PIX_FMT_ARGB32   },
    {HAL_PIXEL_FORMAT_RGBX_8888,                    V4L2_PIX_FMT_XBGR32   },
    {HAL_PIXEL_FORMAT_RGB_888,                      V4L2_PIX_FMT_RGB24    },
    {HAL_PIXEL_FORMAT_RGB_565,                      V4L2_PIX_FMT_RGB565   },
};

// The V4L2_PIX_FMT_RGB32, V4L2_PIX_FMT_BGR32 are deprecated in V4L2.
// But the legacy mscl driver and libhwcutils requires them.
static uint32_t __halfmt_to_v4l2_rgb_deprecated[][2] = {
    {HAL_PIXEL_FORMAT_RGBA_8888,                    V4L2_PIX_FMT_RGB32       },
    {HAL_PIXEL_FORMAT_BGRA_8888,                    V4L2_PIX_FMT_BGR32       },
    {HAL_PIXEL_FORMAT_RGBX_8888,                    V4L2_PIX_FMT_RGB32       },
    {HAL_PIXEL_FORMAT_RGB_888,                      V4L2_PIX_FMT_RGB24       },
    {HAL_PIXEL_FORMAT_RGB_565,                      V4L2_PIX_FMT_RGB565      },
    {HAL_PIXEL_FORMAT_RGBA_1010102,                 V4L2_PIX_FMT_ABGR2101010 },
};

static uint32_t __halfmt_to_v4l2_ycbcr[][2] = {
    {HAL_PIXEL_FORMAT_YV12,                           V4L2_PIX_FMT_YVU420         },
    {HAL_PIXEL_FORMAT_EXYNOS_YV12_M,                  V4L2_PIX_FMT_YVU420M        },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P,             V4L2_PIX_FMT_YUV420         },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_PN,            V4L2_PIX_FMT_YUV420N        },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P_M,           V4L2_PIX_FMT_YUV420M        },
    {HAL_PIXEL_FORMAT_YCrCb_420_SP,                   V4L2_PIX_FMT_NV21           },
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M,          V4L2_PIX_FMT_NV21M          },
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL,     V4L2_PIX_FMT_NV21M          },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP,            V4L2_PIX_FMT_NV12           },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN,           V4L2_PIX_FMT_NV12N          },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M,          V4L2_PIX_FMT_NV12M          },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_PRIV,     V4L2_PIX_FMT_NV12M          },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_S10B,      V4L2_PIX_FMT_NV12N_10B      },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B,     V4L2_PIX_FMT_NV12M_S10B     },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_M,            V4L2_PIX_FMT_NV12M_P010     },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_SPN,          V4L2_PIX_FMT_NV12_P010      },
    {HAL_PIXEL_FORMAT_YCBCR_P010,                     V4L2_PIX_FMT_NV12_P010      },
    {HAL_PIXEL_FORMAT_YCbCr_422_I,                    V4L2_PIX_FMT_YUYV           },
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_422_I,             V4L2_PIX_FMT_YVYU           },
    {HAL_PIXEL_FORMAT_YCbCr_422_SP,                   V4L2_PIX_FMT_NV16           },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC,     V4L2_PIX_FMT_NV12M_SBWC_8B  },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC, V4L2_PIX_FMT_NV12M_SBWC_10B },
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_SBWC,     V4L2_PIX_FMT_NV21M_SBWC_8B  },
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_10B_SBWC, V4L2_PIX_FMT_NV21M_SBWC_10B },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC,      V4L2_PIX_FMT_NV12N_SBWC_8B  },
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC,  V4L2_PIX_FMT_NV12N_SBWC_10B },
};

static uint32_t halfmt_to_v4l2_ycbcr(uint32_t halfmt)
{
    for (size_t i = 0 ; i < ARRSIZE(__halfmt_to_v4l2_ycbcr); i++) {
        if (__halfmt_to_v4l2_ycbcr[i][0] == halfmt)
            return __halfmt_to_v4l2_ycbcr[i][1];
    }

    ALOGE("Unable to find the proper v4l2 format for HAL format %#x", halfmt);

    return 0; // it is alright to return 0 for an error because a fmt identifier is 4cc value
}

uint32_t halfmt_to_v4l2(uint32_t halfmt)
{
    for (size_t i = 0 ; i < ARRSIZE(__halfmt_to_v4l2_rgb); i++) {
        if (__halfmt_to_v4l2_rgb[i][0] == halfmt)
            return __halfmt_to_v4l2_rgb[i][1];
    }

    return halfmt_to_v4l2_ycbcr(halfmt);
}

uint32_t halfmt_to_v4l2_deprecated(uint32_t halfmt)
{
    for (size_t i = 0 ; i < ARRSIZE(__halfmt_to_v4l2_rgb_deprecated); i++) {
        if (__halfmt_to_v4l2_rgb_deprecated[i][0] == halfmt)
            return __halfmt_to_v4l2_rgb_deprecated[i][1];
    }

    return halfmt_to_v4l2_ycbcr(halfmt);
}


static struct {
    uint32_t fmt;                   // HAL_PIXEL_FORMAT that describe how pixels are stored in memory
    uint8_t  bufcnt;                // the number of buffer to describe @fmt
    uint8_t  subfactor;             // Horizontal (upper 4 bits)and vertical (lower 4 bits) chroma subsampling factor
    uint8_t  bpp[MAX_HW2D_PLANES];  // bits in a buffer per pixel
    uint32_t equivalent;            // The equivalent format on a single buffer without H/W constraints
    uint8_t  planecnt;              // the number of planes to describe @fmt
} __halfmt_plane_bpp[] = {
    {HAL_PIXEL_FORMAT_RGBA_8888,                        1, 0x11, {32, 0, 0, 0}, HAL_PIXEL_FORMAT_RGBA_8888,                     1},
    {HAL_PIXEL_FORMAT_BGRA_8888,                        1, 0x11, {32, 0, 0, 0}, HAL_PIXEL_FORMAT_BGRA_8888,                     1},
    {HAL_PIXEL_FORMAT_RGBA_1010102,                     1, 0x11, {32, 0, 0, 0}, HAL_PIXEL_FORMAT_RGBA_1010102,                  1},
    {HAL_PIXEL_FORMAT_RGBX_8888,                        1, 0x11, {32, 0, 0, 0}, HAL_PIXEL_FORMAT_RGBX_8888,                     1},
    {HAL_PIXEL_FORMAT_RGB_888,                          1, 0x11, {24, 0, 0, 0}, HAL_PIXEL_FORMAT_RGB_888,                       1},
    {HAL_PIXEL_FORMAT_RGB_565,                          1, 0x11, {16, 0, 0, 0}, HAL_PIXEL_FORMAT_RGB_565,                       1},
    {HAL_PIXEL_FORMAT_YCbCr_422_I,                      1, 0x21, {16, 0, 0, 0}, HAL_PIXEL_FORMAT_YCbCr_422_I,                   1},
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_422_I,               1, 0x21, {16, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCrCb_422_I,            1},
    {HAL_PIXEL_FORMAT_YCbCr_422_SP,                     1, 0x21, {16, 0, 0, 0}, HAL_PIXEL_FORMAT_YCbCr_422_SP,                  2},
    {HAL_PIXEL_FORMAT_YV12,                             1, 0x22, {12, 0, 0, 0}, HAL_PIXEL_FORMAT_YV12,                          2},
    {HAL_PIXEL_FORMAT_EXYNOS_YV12_M,                    3, 0x22, { 8, 2, 2, 0}, HAL_PIXEL_FORMAT_YV12,                          2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P,               1, 0x22, {12, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P,            2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_PN,              1, 0x22, {12, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P,            2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P_M,             3, 0x22, { 8, 2, 2, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P,            2},
    {HAL_PIXEL_FORMAT_YCrCb_420_SP,                     1, 0x22, {12, 0, 0, 0}, HAL_PIXEL_FORMAT_YCrCb_420_SP,                  2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M,            2, 0x22, { 8, 4, 0, 0}, HAL_PIXEL_FORMAT_YCrCb_420_SP,                  2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL,       2, 0x22, { 8, 4, 0, 0}, HAL_PIXEL_FORMAT_YCrCb_420_SP,                  2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP,              1, 0x22, {12, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP,           2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN,             1, 0x22, {12, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP,           2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_TILED,       1, 0x22, {12, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP,           2},
    {HAL_PIXEL_FORMAT_GOOGLE_NV12_SP,                   1, 0x22, {12, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP,           2},
    {MALI_GRALLOC_FORMAT_INTERNAL_YUV420_8BIT_I,        1, 0x22, {12, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP,           2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M,            2, 0x22, { 8, 4, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP,           2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_PRIV,       2, 0x22, { 8, 4, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP,           2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_TILED,      2, 0x22, { 8, 4, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP,           2},
    {HAL_PIXEL_FORMAT_YCBCR_P010,                       1, 0x22, {24, 0, 0, 0}, HAL_PIXEL_FORMAT_YCBCR_P010,                    2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_M,              2, 0x22, {16, 8, 0, 0}, HAL_PIXEL_FORMAT_YCBCR_P010,                    2},
    {HAL_PIXEL_FORMAT_GOOGLE_NV12_SP_10B,               1, 0x22, {24, 0, 0, 0}, HAL_PIXEL_FORMAT_YCBCR_P010,                    2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_SPN,            1, 0x22, {24, 0, 0, 0}, HAL_PIXEL_FORMAT_YCBCR_P010,                    2},
    {MALI_GRALLOC_FORMAT_INTERNAL_YUV420_10BIT_I,       1, 0x22, {15, 0, 0, 0}, HAL_PIXEL_FORMAT_YCBCR_P010,                    2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC,       2, 0x22, { 8, 4, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC,     2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC,        1, 0x22, {12, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC,     2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC,   2, 0x22, {16, 8, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC, 2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC,    1, 0x22, {24, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC, 2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L50,   2, 0x22, {8, 4, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L50, 2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L50,     1, 0x22, {12, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L50, 2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L75,    2, 0x22, {8, 4, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L75, 2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L75,     1, 0x22, {12, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L75, 2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L40, 2, 0x22, {16, 8, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L40, 2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L40,  1, 0x22, {24, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L40, 2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L60, 2,0x22, {16, 8, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L60, 2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L60, 1, 0x22, {24, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L60, 2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L80, 2, 0x22, {16, 8, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L80, 2},
    {HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L80, 1, 0x22, {24, 0, 0, 0}, HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L80, 2},
};

#define MFC_PAD_SIZE                256
#define MFC_2B_PAD_SIZE             (MFC_PAD_SIZE / 4)
#define MFC_ALIGN(v)                (((v) + 15) & ~15)

#define NV12_MFC_Y_PAYLOAD(w, h)    (MFC_ALIGN(w) * MFC_ALIGN(h))
#define NV12_MFC_C_PAYLOAD(w, h)    (MFC_ALIGN(w) * MFC_ALIGN(h) / 2)
#define NV12_MFC_PAYLOAD(w, h)      (NV12_MFC_Y_PAYLOAD(w, h) + MFC_PAD_SIZE + (MFC_ALIGN(w) * (h) / 2))

size_t halfmt_plane_length(uint32_t fmt, unsigned int plane, uint32_t width, uint32_t height)
{
    for (size_t i = 0; i < ARRSIZE(__halfmt_plane_bpp); i++) {
        if (__halfmt_plane_bpp[i].fmt == fmt) {
            LOGASSERT(plane < __halfmt_plane_bpp[i].bufcnt,
                      "Plane count of HAL format %#x is %u but %d plane is requested", fmt,
                      __halfmt_plane_bpp[i].bufcnt, plane);
            if (plane < __halfmt_plane_bpp[i].bufcnt)
                return (__halfmt_plane_bpp[i].bpp[plane] * width * height) / 8;
        }
    }

    LOGASSERT(1, "Unable to find HAL format %#x with plane %d", fmt, plane);

    return 0;
}

unsigned int halfmt_bpp(uint32_t fmt)
{
    for (size_t i = 0 ; i < ARRSIZE(__halfmt_plane_bpp); i++) {
        if (__halfmt_plane_bpp[i].fmt == fmt)
                return __halfmt_plane_bpp[i].bpp[0] + __halfmt_plane_bpp[i].bpp[1] + __halfmt_plane_bpp[i].bpp[2];
    }

    LOGASSERT(1, "Unable to find HAL format %#x", fmt);

    return 0;
}

#define DEFINE_HALFMT_PROPERTY_GETTER(rettype, funcname, member)    \
    rettype funcname(uint32_t fmt)                                  \
    {                                                               \
        for (size_t i = 0 ; i < ARRSIZE(__halfmt_plane_bpp); i++) { \
            if (__halfmt_plane_bpp[i].fmt == fmt)                   \
                return __halfmt_plane_bpp[i].member;                \
        }                                                           \
        LOGASSERT(1, "Unable to find HAL format %#x", fmt);         \
        return 0;                                                   \
    }

DEFINE_HALFMT_PROPERTY_GETTER(unsigned int, halfmt_buf_count, bufcnt)
DEFINE_HALFMT_PROPERTY_GETTER(uint8_t, halfmt_chroma_subsampling, subfactor)
DEFINE_HALFMT_PROPERTY_GETTER(uint32_t, find_format_equivalent, equivalent)
DEFINE_HALFMT_PROPERTY_GETTER(uint8_t, halfmt_plane_count, planecnt)

static struct {
    int32_t  hal;
    uint32_t v4l2;
} __haldataspace_to_v4l2[] = {
    {HAL_DATASPACE_STANDARD_BT709 | HAL_DATASPACE_RANGE_FULL,        V4L2_COLORSPACE_SRGB},
    {HAL_DATASPACE_STANDARD_BT709 | HAL_DATASPACE_RANGE_LIMITED,     V4L2_COLORSPACE_REC709},
    {HAL_DATASPACE_STANDARD_BT601_625 | HAL_DATASPACE_RANGE_FULL,    V4L2_COLORSPACE_JPEG},
    {HAL_DATASPACE_STANDARD_BT601_525 | HAL_DATASPACE_RANGE_FULL,    V4L2_COLORSPACE_JPEG},
    {HAL_DATASPACE_STANDARD_BT601_625 | HAL_DATASPACE_RANGE_LIMITED, V4L2_COLORSPACE_SMPTE170M},
    {HAL_DATASPACE_STANDARD_BT601_525 | HAL_DATASPACE_RANGE_LIMITED, V4L2_COLORSPACE_SMPTE170M},
    {HAL_DATASPACE_STANDARD_BT2020 | HAL_DATASPACE_RANGE_LIMITED,    V4L2_COLORSPACE_BT2020},
    {HAL_DATASPACE_STANDARD_FILM | HAL_DATASPACE_RANGE_FULL,         V4L2_COLORSPACE_SRGB},
    {HAL_DATASPACE_STANDARD_FILM | HAL_DATASPACE_RANGE_LIMITED,      V4L2_COLORSPACE_REC709},
    {HAL_DATASPACE_SRGB,                                             V4L2_COLORSPACE_SRGB},
    {HAL_DATASPACE_JFIF,                                             V4L2_COLORSPACE_JPEG},
    {HAL_DATASPACE_BT601_525,                                        V4L2_COLORSPACE_SMPTE170M},
    {HAL_DATASPACE_BT601_625,                                        V4L2_COLORSPACE_SMPTE170M},
    {HAL_DATASPACE_BT709,                                            V4L2_COLORSPACE_REC709},
};

#define HAL_DATASPACE_LEGACY_TYPE_MASK  ((1 << HAL_DATASPACE_STANDARD_SHIFT) - 1)

uint32_t haldataspace_to_v4l2(int dataspace, uint32_t width, uint32_t height)
{
    // if legacy type, discard upper bits above 15th
    if ((dataspace & HAL_DATASPACE_LEGACY_TYPE_MASK) != 0) {
        dataspace &= HAL_DATASPACE_LEGACY_TYPE_MASK;
    } else {
        dataspace &= ~HAL_DATASPACE_LEGACY_TYPE_MASK;
        // if Y value range is not specified, force it to the limited.
        if ((dataspace & HAL_DATASPACE_RANGE_MASK) == 0)
            dataspace |= HAL_DATASPACE_RANGE_LIMITED;
        // if colorspace is not specified, force it to 709 or 601 according to the resolution
        if ((dataspace & HAL_DATASPACE_STANDARD_MASK) == 0)
            dataspace |= ((width * height) < (1280 * 720))
                         ? HAL_DATASPACE_STANDARD_BT601_625
                         : HAL_DATASPACE_STANDARD_BT709;
        // discard transfer function type values because it is not required during color space conviersion
        dataspace &= ~HAL_DATASPACE_TRANSFER_MASK;
    }
    for (size_t i = 0 ; i < ARRSIZE(__haldataspace_to_v4l2); i++) {
        if (__haldataspace_to_v4l2[i].hal == dataspace)
            return __haldataspace_to_v4l2[i].v4l2;
    }

    LOGASSERT(1, "Unable to find HAL dataspace value %#x", dataspace);

    return V4L2_COLORSPACE_DEFAULT;
}
