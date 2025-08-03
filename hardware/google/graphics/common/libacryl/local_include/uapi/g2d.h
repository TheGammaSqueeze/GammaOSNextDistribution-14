/*
 * Copyright Samsung Electronics Co.,LTD.
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef __UAPI_G2D_H__
#define __UAPI_G2D_H__

#ifdef __cplusplus
extern "C" {
#endif

#define G2D_SWZ_MASK           0xFFFF

#define G2D_SWZ_ARGB           0x3210
#define G2D_SWZ_ABGR           0x3012
#define G2D_SWZ_xBGR           0x5012
#define G2D_SWZ_xRGB           0x5210

#define G2D_SWZ_ALPHA_MASK     (0xF << 12)
#define G2D_SWZ_ALPHA_ONE      (0x5 << 12)

#define G2D_YUVORDER_MASK      (0x3 << 24)

#define G2D_YUV_UV             ((1 << 24) | G2D_SWZ_ARGB)
#define G2D_YUV_VU             ((0 << 24) | G2D_SWZ_ARGB)
#define G2D_YUV_YC             (0 << 25)
#define G2D_YUV_CY             (1 << 25)

#define G2D_DATAFORMAT_AFBC    (1 << 20)
#define G2D_DATAFORMAT_UORDER  (1 << 21)
#define G2D_DATAFORMAT_SBWC    (1 << 22)

#define G2D_DATAFMT_SHIFT      16
#define G2D_DATAFMT_MASK       (0xF << G2D_DATAFMT_SHIFT)

#define G2D_DATAFMT_8888       (0 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_565        (1 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_4444       (2 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_888        (3 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_1555       (4 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_5551       (5 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_RGB8_MAX   (5 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_8          (6 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_RESERVED   (7 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_YUV_MIN    (8 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_YUV420SP   (8 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_YUV420P    (9 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_YUV422I    (10 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_YUV422SP   (11 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_P010_LGCY  (12 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_YUV_MAX    (12 << G2D_DATAFMT_SHIFT)
#define G2D_DATAFMT_ABGR2101010 (14 << G2D_DATAFMT_SHIFT)

#define G2D_FMT_YCBCR_BITDEPTH_SHIFT 28
#define G2D_FMT_YCBCR_BITDEPTH_MASK  (0x3 << G2D_FMT_YCBCR_BITDEPTH_SHIFT)
#define G2D_FMT_YCBCR_8BIT     (0 << G2D_FMT_YCBCR_BITDEPTH_SHIFT)
#define G2D_FMT_YCBCR_10BIT    (1 << G2D_FMT_YCBCR_BITDEPTH_SHIFT)

#define G2D_FMT_ARGB8888       (G2D_DATAFMT_8888 | G2D_SWZ_ARGB)
#define G2D_FMT_ABGR8888       (G2D_DATAFMT_8888 | G2D_SWZ_ABGR)
#define G2D_FMT_XBGR8888       (G2D_DATAFMT_8888 | G2D_SWZ_xBGR)
#define G2D_FMT_RGB565         (G2D_DATAFMT_565 | G2D_SWZ_xRGB)
#define G2D_FMT_BGR565         (G2D_DATAFMT_565 | G2D_SWZ_xBGR)
#define G2D_FMT_RGB888         (G2D_DATAFMT_888 | G2D_SWZ_xRGB)
#define G2D_FMT_NV12           (G2D_DATAFMT_YUV420SP | G2D_YUV_UV)
#define G2D_FMT_NV21           (G2D_DATAFMT_YUV420SP | G2D_YUV_VU)
#define G2D_FMT_YUV420P        (G2D_DATAFMT_YUV420P | G2D_YUV_UV)
#define G2D_FMT_YV12           (G2D_DATAFMT_YUV420P | G2D_YUV_VU)
#define G2D_FMT_YUYV           (G2D_DATAFMT_YUV422I | G2D_YUV_YC | G2D_YUV_UV)
#define G2D_FMT_YVYU           (G2D_DATAFMT_YUV422I | G2D_YUV_YC | G2D_YUV_VU)
#define G2D_FMT_UYVY           (G2D_DATAFMT_YUV422I | G2D_YUV_CY | G2D_YUV_UV)
#define G2D_FMT_VYUY           (G2D_DATAFMT_YUV422I | G2D_YUV_CY | G2D_YUV_VU)
#define G2D_FMT_NV16           (G2D_DATAFMT_YUV422SP | G2D_YUV_UV)
#define G2D_FMT_NV61           (G2D_DATAFMT_YUV422SP | G2D_YUV_VU)
#define G2D_FMT_NV12_P010_LGCY (G2D_DATAFMT_P010_LGCY | G2D_YUV_UV)
#define G2D_FMT_NV21_P010_LGCY (G2D_DATAFMT_P010_LGCY | G2D_YUV_VU)
#define G2D_FMT_NV12_P010      (G2D_DATAFMT_YUV420SP | G2D_FMT_YCBCR_10BIT | G2D_YUV_UV)
#define G2D_FMT_NV21_P010      (G2D_DATAFMT_YUV420SP | G2D_FMT_YCBCR_10BIT | G2D_YUV_VU)
#define G2D_FMT_NV16_P210      (G2D_DATAFMT_YUV422SP | G2D_FMT_YCBCR_10BIT | G2D_YUV_UV)
#define G2D_FMT_NV61_P210      (G2D_DATAFMT_YUV422SP | G2D_FMT_YCBCR_10BIT | G2D_YUV_VU)
#define G2D_FMT_ABGR2101010    (G2D_DATAFMT_ABGR2101010 | G2D_SWZ_ABGR)
#define G2D_FMT_NV12_SBWC      (G2D_FMT_NV12 | G2D_DATAFORMAT_SBWC)
#define G2D_FMT_NV21_SBWC      (G2D_FMT_NV21 | G2D_DATAFORMAT_SBWC)
#define G2D_FMT_NV12_SBWC_10B  (G2D_FMT_NV12_P010 | G2D_DATAFORMAT_SBWC)
#define G2D_FMT_NV21_SBWC_10B  (G2D_FMT_NV21_P010 | G2D_DATAFORMAT_SBWC)

#define G2D_YCBCRMODE_DITHER   (1 << 2)

#define IS_YUV(fmt) ((((fmt) & G2D_DATAFMT_MASK) >= G2D_DATAFMT_YUV_MIN) && (((fmt) & G2D_DATAFMT_MASK) <= G2D_DATAFMT_YUV_MAX))
#define IS_RGB(fmt) ((((fmt) & G2D_DATAFMT_MASK) <= G2D_DATAFMT_RGB8_MAX) || (((fmt) & G2D_DATAFMT_MASK) > G2D_DATAFMT_YUV_MAX))

#define G2D_IMGFMT(value) ((value) & \
            (G2D_DATAFMT_MASK | G2D_YUVORDER_MASK | G2D_SWZ_MASK))

#define G2D_MAX_PLANES    4

#define G2D_ROTATEDIR_FLIP_SHIFT    4
#define G2D_ROTATEDIR_ROT90CCW      (1 << 0)

#define G2D_BLEND_NONE    (2 | (2 << 4) | (2 << 8) | (2 << 12) | (1 << 18)) // Ga*Sa*Sc + (1-Sa*Ga)*Dc
#define G2D_BLEND_SRCOVER (6 |            (2 << 8) | (2 << 12) | (1 << 18)) // Ga*Sc + (1-Sa*Ga)*Dc
#define G2D_BLEND_SRCCOPY (6 |            (1 << 8)                        ) // Ga*Sc

#define G2D_LAYERCMD_PREMULT_GLOBALALPHA   (2 << 24)
#define G2D_LAYERCMD_PREMULT_ALPHA         (1 << 24)

#define G2D_LAYERCMD_ALPHABLEND (1 << 20)
#define G2D_LAYERCMD_OPAQUE     (1 << 1)
#define G2D_LAYERCMD_VALID      (1 << 0)

#define G2D_LAYERSEL_COLORFILL   1

#define G2D_LAYER_YCBCRMODE_WIDE (1 << 4)
#define G2D_LAYER_YCBCRMODE_OFFX (2 << 8)
#define G2D_LAYER_YCBCRMODE_OFFY (2 << 12)

#define G2D_SCALECONTROL_BILINEAR 2
#define G2D_SCALECONTROL_POLYPHASE 3
#define G2D_SCALECONTROL_FILTERCOEF_SHIFT 4
#define G2D_SCALEFACTOR_FRACBITS  16

#define G2D_LAYER_HDRMODE_DEMULT_ALPHA (1 << 12)

struct g2d_reg {
    uint32_t offset;
    uint32_t value;
};

enum g2dsfr_img_register {
    G2DSFR_IMG_STRIDE,
    G2DSFR_IMG_COLORMODE,
    G2DSFR_IMG_LEFT,
    G2DSFR_IMG_TOP,
    G2DSFR_IMG_RIGHT,
    G2DSFR_IMG_BOTTOM,
    G2DSFR_IMG_WIDTH,
    G2DSFR_IMG_HEIGHT,

    G2DSFR_IMG_FIELD_COUNT,
};

/*
 * The order of command list should be fixed.
 * The new command item must be added from the bottom.
 */
enum g2dsfr_src_register {
    G2DSFR_SRC_COMMAND = G2DSFR_IMG_FIELD_COUNT,
    G2DSFR_SRC_SELECT,
    G2DSFR_SRC_ROTATE,
    G2DSFR_SRC_DSTLEFT,
    G2DSFR_SRC_DSTTOP,
    G2DSFR_SRC_DSTRIGHT,
    G2DSFR_SRC_DSTBOTTOM,
    G2DSFR_SRC_SCALECONTROL,
    G2DSFR_SRC_XSCALE,
    G2DSFR_SRC_YSCALE,
    G2DSFR_SRC_XPHASE,
    G2DSFR_SRC_YPHASE,
    G2DSFR_SRC_COLOR,
    G2DSFR_SRC_ALPHA,
    G2DSFR_SRC_BLEND,
    G2DSFR_SRC_YCBCRMODE,
    G2DSFR_SRC_HDRMODE,
    G2DSFR_SRC_Y_HEADER_STRIDE,
    G2DSFR_SRC_Y_PAYLOAD_STRIDE,
    G2DSFR_SRC_C_HEADER_STRIDE,
    G2DSFR_SRC_C_PAYLOAD_STRIDE,
    G2DSFR_SRC_SBWCINFO,

    G2DSFR_SRC_FIELD_COUNT
};

enum g2dsfr_dst_register {
    G2DSFR_DST_YCBCRMODE = G2DSFR_IMG_FIELD_COUNT,

    G2DSFR_DST_COMPAT_FIELD_COUNT,

    G2DSFR_DST_Y_HEADER_STRIDE = G2DSFR_DST_COMPAT_FIELD_COUNT,
    G2DSFR_DST_Y_PAYLOAD_STRIDE,
    G2DSFR_DST_C_HEADER_STRIDE,
    G2DSFR_DST_C_PAYLOAD_STRIDE,
    G2DSFR_DST_SBWCINFO,

    G2DSFR_DST_FIELD_COUNT,
};

#define G2D_MAX_PLANES         4
#define G2D_MAX_SFR_COUNT      1024
#define G2D_MAX_BUFFERS        4
#define G2D_MAX_IMAGES         16
#define G2D_MAX_PRIORITY       3
#define G2D_MAX_RELEASE_FENCES (G2D_MAX_IMAGES + 1)

struct g2d_compat_commands {
    uint32_t       target[G2DSFR_DST_COMPAT_FIELD_COUNT];
    uint32_t       *source[G2D_MAX_IMAGES];
    struct g2d_reg *extra;
    uint32_t       num_extra_regs;
};

#define G2D_BUFTYPE_NONE    0
#define G2D_BUFTYPE_EMPTY   1
#define G2D_BUFTYPE_USERPTR 2
#define G2D_BUFTYPE_DMABUF  3

#define G2D_BUFTYPE_VALID(type) !(((type) & G2D_BUFTYPE_DMABUF) == 0)

struct g2d_buffer {
    union {
        void *userptr;
        struct {
            int32_t  fd;
            uint32_t offset;
        } dmabuf;
    };
    uint32_t      length;
};

#define G2D_LAYERFLAG_ACQUIRE_FENCE (1 << 1)
#define G2D_LAYERFLAG_SECURE (1 << 2)
#define G2D_LAYERFLAG_COLORFILL (1 << 3)
#define G2D_LAYERFLAG_MFC_STRIDE (1 << 4)
#define G2D_LAYERFLAG_AFBC_WIDEBLK (1 << 5)
#define G2D_LAYERFLAG_NO_CACHECLEAN (1 << 16)
#define G2D_LAYERFLAG_NO_CACHEINV (1 << 17)

struct g2d_layer {
    uint32_t          flags;
    int32_t           fence;
    uint32_t          buffer_type;
    uint32_t          num_buffers;
    struct g2d_buffer buffer[G2D_MAX_BUFFERS];
};

#define G2D_FLAG_DITHER   (1 << 1)
#define G2D_FLAG_NONBLOCK (1 << 2)
#define G2D_FLAG_HWFC     (1 << 3)
#define G2D_FLAG_APB      (1 << 4)
#define G2D_FLAG_ERROR    (1 << 5)

struct g2d_compat_task {
    uint32_t            version;
    uint32_t            flags;
    uint32_t            laptime_in_usec;
    uint32_t            priority;
    uint32_t            num_source;
    uint32_t            num_release_fences;
    int32_t             *release_fence;
    struct g2d_layer    target;
    struct g2d_layer    *source;
    struct g2d_compat_commands commands;
};

/*
 * Commands must be flexible because it may change according to H/W changes.
 * The commands must be written in the promised order as version.
 */
struct g2d_commands {
    uint32_t       *target;
    uint32_t       *source[G2D_MAX_IMAGES];
    struct g2d_reg *extra;
    uint32_t       num_extra_regs;
};

struct g2d_task {
    uint32_t            version;
    uint32_t            flags;
    uint32_t            laptime_in_usec;
    uint32_t            priority;
    uint32_t            num_source;
    uint32_t            num_release_fences;
    int32_t             *release_fence;
    struct g2d_layer    target;
    struct g2d_layer    *source;
    struct g2d_commands commands;
};

#define G2D_PERF_LAYER_ROTATE     (1 << 0)
#define G2D_PERF_LAYER_SCALING    (1 << 1)
#define G2D_PERF_LAYER_YUV2P      (1 << 4)
#define G2D_PERF_LAYER_SBWC       (1 << 5)
#define G2D_PERF_LAYER_RGB_AFBC   (1 << 6)
#define G2D_PERF_LAYER_YUV_AFBC   (1 << 7)

struct g2d_performance_layer {
    uint16_t crop_width;
    uint16_t crop_height;
    uint16_t window_width;
    uint16_t window_height;
    uint32_t layer_attr;
};

#define G2D_PERF_FRAME_SOLIDCOLORFILL    (1 << 0)

struct g2d_performance_frame {
    struct g2d_performance_layer layer[G2D_MAX_IMAGES];
    uint32_t bandwidth_read;
    uint32_t bandwidth_write;
    uint32_t target_pixelcount;
    uint32_t frame_rate;
    uint32_t frame_attr;
    uint32_t num_layers;
};

#define G2D_PERF_MAX_FRAMES 4

struct g2d_performance {
    struct g2d_performance_frame frame[G2D_PERF_MAX_FRAMES];
    uint32_t num_frame;
    uint32_t reserved;
};

enum g2d_priority {
    G2D_LOW_PRIORITY,
    G2D_MEDIUM_PRIORITY,
    G2D_DEFAULT_PRIORITY = G2D_MEDIUM_PRIORITY,
    G2D_HIGH_PRIORITY,
    G2D_HIGHEST_PRIORITY,
    G2D_PRIORITY_END
};

#define G2D_IOC_PROCESS        _IOWR('M', 3, struct g2d_task)
#define G2D_IOC_COMPAT_PROCESS _IOWR('M', 4, struct g2d_compat_task)
#define G2D_IOC_PRIORITY       _IOR('M', 5, int32_t)
#define G2D_IOC_PERFORMANCE    _IOR('M', 6, struct g2d_performance)
#define G2D_IOC_VERSION		_IOR('M', 7, uint32_t)

#ifdef __cplusplus
}
#endif

#endif /* __UAPI_G2D_H__ */
