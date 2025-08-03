/*
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

#ifndef _EXYNOS_FORMAT_H_
#define _EXYNOS_FORMAT_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

enum {
                                                           /* HAL_PIXEL_FORMAT_YCbCr_422_P = 0x100, */
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P_M       = 0x101,   /* HAL_PIXEL_FORMAT_YCbCr_420_P */
                                                           /* HAL_PIXEL_FORMAT_YCbCr_420_I = 0x102, */
    HAL_PIXEL_FORMAT_EXYNOS_CbYCrY_422_I        = 0x103,   /* HAL_PIXEL_FORMAT_CbYCrY_422_I */
                                                           /* HAL_PIXEL_FORMAT_CbYCrY_420_I = 0x104, */
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M      = 0x105,   /* HAL_PIXEL_FORMAT_YCbCr_420_SP */
    HAL_PIXEL_FORMAT_EXYNOS_YCrCb_422_SP        = 0x106,   /* HAL_PIXEL_FORMAT_YCrCb_422_SP */
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_TILED= 0x107,   /* HAL_PIXEL_FORMAT_YCbCr_420_SP_TILED */
    HAL_PIXEL_FORMAT_EXYNOS_ARGB_8888           = 0x108,   /* HAL_PIXEL_FORMAT_CUSTOM_ARGB_8888 */
    // support custom format for zero copy
                                                           /* HAL_PIXEL_FORMAT_CUSTOM_YCbCr_420_SP = 0x110 */
                                                           /* HAL_PIXEL_FORMAT_CUSTOM_YCrCb_420_SP = 0x111, */
                                                           /* HAL_PIXEL_FORMAT_CUSTOM_YCbCr_420_SP_TILED  = 0x112, */
                                                           /* HAL_PIXEL_FORMAT_CUSTOM_YCbCr_422_SP = 0x113, */
                                                           /* HAL_PIXEL_FORMAT_CUSTOM_YCrCb_422_SP = 0x114, */
                                                           /* HAL_PIXEL_FORMAT_CUSTOM_YCbCr_422_I  = 0x115, */
    HAL_PIXEL_FORMAT_EXYNOS_YCrCb_422_I         = 0x116,   /* HAL_PIXEL_FORMAT_CUSTOM_YCrCb_422_I */
                                                           /* HAL_PIXEL_FORMAT_CUSTOM_CbYCrY_422_I = 0x117, */
    HAL_PIXEL_FORMAT_EXYNOS_CrYCbY_422_I        = 0x118,   /* HAL_PIXEL_FORMAT_CUSTOM_CrYCbY_422_I */
                                                           /* HAL_PIXEL_FORMAT_CUSTOM_CbYCr_422_I = 0x11B, */

    HAL_PIXEL_FORMAT_EXYNOS_YV12_M              = 0x11C,   /* HAL_PIXEL_FORMAT_EXYNOS_YV12 */
    HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M      = 0x11D,   /* HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP */
    HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL = 0x11E,   /* HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_FULL */

    /* newly added formats */
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P         = 0x11F,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP        = 0x120,

    /* EXYNOS_YCbCr_420_SP_M w/ private data buffer */
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_PRIV = 0x121,

    /* contiguous(single fd) custom formats */
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_PN        = 0x122,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN       = 0x123,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_TILED = 0x124,

    /* 10-bit format (8bit + separated 2bit) w/ private data buffer */
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B = 0x125,

    /* 10-bit contiguous(single fd, 8bit + separated 2bit) custom formats */
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_S10B  = 0x126,

    /* 10-bit format (2 fd, 10bit, 2x byte) custom formats */
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_M        = 0x127,

    /* YUV 4:2:2 planar, Y/Cb/Cr */
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_422_P         = 0x128,

    /* 10-bit format (single fd, 10bit, 2x byte) custom formats */
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_SPN      = 0x129,

    /* SBWC format */
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC = 0x130,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC  = 0x131,

    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC = 0x132,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC  = 0x133,

    HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_SBWC = 0x134,
    HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_10B_SBWC = 0x135,

	/* SBWC Lossy formats */
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L50 = 0x140,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L75 = 0x141,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L50  = 0x150,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L75  = 0x151,

    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L40 = 0x160,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L60 = 0x161,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L80 = 0x162,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L40  = 0x170,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L60  = 0x171,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L80  = 0x172,

    HAL_PIXEL_FORMAT_EXYNOS_MAX,
};

enum {
    HAL_PIXEL_FORMAT_GOOGLE_NV12_SP = 0x301,
    HAL_PIXEL_FORMAT_GOOGLE_NV12_SP_10B = 0x302,
    HAL_PIXEL_FORMAT_GOOGLE_R_8 = 0x303,
    HAL_PIXEL_FORMAT_GOOGLE_RG_88 = 0x304,
    HAL_PIXEL_FORMAT_GOOGLE_MAX,
};

static inline int is_sbwc_format(uint32_t format)
{
    switch (format) {
    default:
        return 0;
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC:
    case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_SBWC:
    case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_10B_SBWC:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L50:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L75:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L50:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L75:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L40:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L60:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L80:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L40:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L60:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L80:
        return 1;
    }
}

/* for backward compatibility */
#define HAL_PIXEL_FORMAT_CUSTOM_YCbCr_420_SP    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M
#define HAL_PIXEL_FORMAT_CUSTOM_YCrCb_420_SP    HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M

// Gamut (colorspace range)
enum {
    HAL_PIXEL_GAMUT_DEFAULT = 0,
    // Values range 0-255
    HAL_PIXEL_GAMUT_WIDE_8,
    // Values range 16-235
    HAL_PIXEL_GAMUT_NARROW_8
};

// Chromaticities (colorspace parameters)
enum {
    HAL_PIXEL_CHROMA_DEFAULT = 0,
    // BT.601 "Standard Definition" color space
    HAL_PIXEL_CHROMA_BT601_8,
    // BT.709 "High Definition" color space
    HAL_PIXEL_CHROMA_BT709_8
};

struct ADDRS {
    unsigned int addr_y;
    unsigned int addr_cbcr;
    unsigned int buf_idx;
    unsigned int reserved;
};

/* 12  Y/CbCr 4:2:0 64x32 macroblocks */
#define V4L2_PIX_FMT_NV12T    v4l2_fourcc('T', 'V', '1', '2')

#define ALIGN_UP(x, a)    (((x) + (a) - 1) & ~((a) - 1))
#define ALIGN_DOWN(x, a)  ((x) - (x % a))
#ifndef ALIGN
#define ALIGN(x, a)       ALIGN_UP(x, a)
#endif
#ifndef ALIGN_TO_32B
#define ALIGN_TO_32B(x)   ((((x) + (1 <<  5) - 1) >>  5) <<  5)
#endif
#ifndef ALIGN_TO_128B
#define ALIGN_TO_128B(x)  ((((x) + (1 <<  7) - 1) >>  7) <<  7)
#endif
#ifndef ALIGN_TO_8KB
#define ALIGN_TO_8KB(x)   ((((x) + (1 << 13) - 1) >> 13) << 13)
#endif

#define GET_32BPP_FRAME_SIZE(w, h)  (((w) * (h)) << 2)
#define GET_24BPP_FRAME_SIZE(w, h)  (((w) * (h)) * 3)
#define GET_16BPP_FRAME_SIZE(w, h)  (((w) * (h)) << 1)

/*
 * Convert hal_pixel_format to v4l2_pixel_format.
 *
 * @param hal_pixel_format
 *   hal_pixel_format[in]
 *
 * @return
 *   v4l2_pixel_format
 */
int HAL_PIXEL_FORMAT_2_V4L2_PIX(
    int hal_pixel_format);

/*
 * Convert v4l2_pixel_format to hal_pixel_format.
 *
 * @param v4l2_pixel_format
 *   v4l2_pixel_format[in]
 *
 * @return
 *   hal_pixel_format
 */
int V4L2_PIX_2_HAL_PIXEL_FORMAT(
    int v4l2_pixel_format);

/*
 * Get frame_size of hal_pixel_format.
 *
 * @param hal_pixel_format
 *   hal_pixel_format[in]
 *
 * @param width
 *   width[in]
 *
 * @param height
 *   height[in]
 *
 * @return
 *   frame_size
 */
unsigned int FRAME_SIZE(
    int hal_pixel_format,
    int width,
    int height);

int PLANAR_FRAME_SIZE(
    int hal_pixel_format,
    int width,
    int height, 
    unsigned int *luma_size,
    unsigned int *chroma_size);

int NUM_PLANES(int hal_pixel_format);


/*
 * Get bpp and plane of v4l2_pixel_format.
 *
 * @param v4l2_pixel_format
 *   v4l2_pixel_format[in]
 *
 * @param bpp
 *   address of bpp[out]
 *
 * @param planes
 *   address of planes[out]
 *
 * @return
 *   error code
 */
int V4L2_PIX_2_YUV_INFO(
    unsigned int  v4l2_pixel_format,
    unsigned int *bpp,
    unsigned int *planes);

/*
 * Get bpp of v4l2_pixel_format.
 *
 * @param v4l2_pixel_format
 *   v4l2_pixel_format[in]
 *
 * @return
 *   bpp
 */
int get_yuv_bpp(
    unsigned int v4l2_pixel_format);

/*
 * Get plane of v4l2_pixel_format.
 *
 * @param v4l2_pixel_format
 *   v4l2_pixel_format[in]
 *
 * @return
 *   num of plane
 */
int get_yuv_planes(
    unsigned int v4l2_pixel_format);

#ifdef __cplusplus
}
#endif

#endif
