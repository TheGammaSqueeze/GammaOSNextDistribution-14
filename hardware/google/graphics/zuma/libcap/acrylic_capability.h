/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <cstring>

#include <log/log.h>

#include <exynos_format.h> // hardware/smasung_slsi/exynos/include
#include <mali_gralloc_formats.h>

static uint32_t all_fimg2d_zuma_formats[] = {
    HAL_PIXEL_FORMAT_RGBA_8888,
    HAL_PIXEL_FORMAT_BGRA_8888,
    HAL_PIXEL_FORMAT_RGBA_1010102,
    HAL_PIXEL_FORMAT_RGBX_8888,
    HAL_PIXEL_FORMAT_RGB_888,
    HAL_PIXEL_FORMAT_RGB_565,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P,
    HAL_PIXEL_FORMAT_YCrCb_420_SP,                  // NV21 (YVU420 semi-planar)
    HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M,         // NV21 on multi-buffer
    HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL,    // NV21 on multi-buffer
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP,           // NV12 (YUV420 semi-planar)
    HAL_PIXEL_FORMAT_GOOGLE_NV12_SP,                // NV12 (YUV420 semi-planar)
    MALI_GRALLOC_FORMAT_INTERNAL_YUV420_8BIT_I,     // NV12 AFBC
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN,          // NV12 with MFC alignment constraints
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M,
    // NV12M with MFC alignment constraints on multi-buffer
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_PRIV,
    // NV12M with MFC alignment constraints on multi-buffer
    HAL_PIXEL_FORMAT_YCbCr_422_SP,                  // YUV422 2P (YUV422 semi-planar)
    HAL_PIXEL_FORMAT_YCBCR_P010,
    HAL_PIXEL_FORMAT_GOOGLE_NV12_SP_10B,
    MALI_GRALLOC_FORMAT_INTERNAL_YUV420_10BIT_I,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_SPN,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_M,
    MALI_GRALLOC_FORMAT_INTERNAL_P010,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC,
    HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_SBWC,
    HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_10B_SBWC,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L50,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L75,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L50,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L75,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L40,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L60,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L80,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L40,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L60,
    HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L80,
};

static int all_hwc_zuma_dataspaces[] = {
    HAL_DATASPACE_STANDARD_BT709,
    HAL_DATASPACE_STANDARD_BT709 | HAL_DATASPACE_RANGE_FULL,
    HAL_DATASPACE_STANDARD_BT709 | HAL_DATASPACE_RANGE_LIMITED,
    HAL_DATASPACE_STANDARD_BT2020,
    HAL_DATASPACE_STANDARD_BT2020 | HAL_DATASPACE_RANGE_FULL,
    HAL_DATASPACE_STANDARD_BT2020 | HAL_DATASPACE_RANGE_LIMITED,
    HAL_DATASPACE_STANDARD_BT601_625,
    HAL_DATASPACE_STANDARD_BT601_625 | HAL_DATASPACE_RANGE_FULL,
    HAL_DATASPACE_STANDARD_BT601_625 | HAL_DATASPACE_RANGE_LIMITED,
    HAL_DATASPACE_STANDARD_BT601_525,
    HAL_DATASPACE_STANDARD_BT601_525 | HAL_DATASPACE_RANGE_FULL,
    HAL_DATASPACE_STANDARD_BT601_525 | HAL_DATASPACE_RANGE_LIMITED,
    HAL_DATASPACE_STANDARD_BT601_625_UNADJUSTED,
    HAL_DATASPACE_STANDARD_BT601_625_UNADJUSTED | HAL_DATASPACE_RANGE_FULL,
    HAL_DATASPACE_STANDARD_BT601_625_UNADJUSTED | HAL_DATASPACE_RANGE_LIMITED,
    HAL_DATASPACE_STANDARD_BT601_525_UNADJUSTED,
    HAL_DATASPACE_STANDARD_BT601_525_UNADJUSTED | HAL_DATASPACE_RANGE_FULL,
    HAL_DATASPACE_STANDARD_BT601_525_UNADJUSTED | HAL_DATASPACE_RANGE_LIMITED,
    HAL_DATASPACE_STANDARD_DCI_P3,
    HAL_DATASPACE_STANDARD_DCI_P3 | HAL_DATASPACE_RANGE_FULL,
    HAL_DATASPACE_STANDARD_DCI_P3 | HAL_DATASPACE_RANGE_LIMITED,
    HAL_DATASPACE_STANDARD_FILM,
    HAL_DATASPACE_STANDARD_FILM | HAL_DATASPACE_RANGE_FULL,
    HAL_DATASPACE_STANDARD_FILM | HAL_DATASPACE_RANGE_LIMITED,
    // 0 should be treated as BT709 Limited range
    0,
    HAL_DATASPACE_RANGE_FULL,
    HAL_DATASPACE_RANGE_LIMITED,
    // Deprecated legacy dataspace definitions
    HAL_DATASPACE_SRGB,
    HAL_DATASPACE_JFIF,
    HAL_DATASPACE_BT601_525,
    HAL_DATASPACE_BT601_625,
    HAL_DATASPACE_BT709,
};

const static stHW2DCapability __capability_g2d_zuma = {
    .max_upsampling_num = {8, 8},
    .max_downsampling_factor = {4, 4},
    .max_upsizing_num = {8, 8},
    .max_downsizing_factor = {4, 4},
    .min_src_dimension = {1, 1},
    .max_src_dimension = {8192, 8192},
    .min_dst_dimension = {1, 1},
    .max_dst_dimension = {8192, 8192},
    .min_pix_align = {1, 1},
    .rescaling_count = 0,
    .compositing_mode = HW2DCapability::BLEND_NONE | HW2DCapability::BLEND_SRC_COPY
	                 | HW2DCapability::BLEND_SRC_OVER,
    .transform_type = HW2DCapability::TRANSFORM_ALL,
    .auxiliary_feature = HW2DCapability::FEATURE_PLANE_ALPHA | HW2DCapability::FEATURE_UORDER_WRITE
                         | HW2DCapability::FEATURE_AFBC_ENCODE | HW2DCapability::FEATURE_AFBC_DECODE
                         | HW2DCapability::FEATURE_SOLIDCOLOR,
    .num_formats = ARRSIZE(all_fimg2d_zuma_formats),
    .num_dataspaces = ARRSIZE(all_hwc_zuma_dataspaces),
    .max_layers = 4,
    .pixformats = all_fimg2d_zuma_formats,
    .dataspaces = all_hwc_zuma_dataspaces,
    .base_align = 1,
};

static const HW2DCapability capability_zuma(__capability_g2d_zuma);

inline Acrylic *createAcrylicCompositorG2D(const char *spec)
{
    Acrylic *compositor = nullptr;

    if (strcmp(spec, "fimg2d_zuma") == 0) {
        compositor = new AcrylicCompositorG2D(capability_zuma, /*halfmt_to_g2dfmt*/ true);
    }
    return compositor;
}
