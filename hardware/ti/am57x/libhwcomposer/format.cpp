/*
 * Copyright (C) Texas Instruments Incorporated - http://www.ti.com/
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

#include <cstdint>

#include <log/log.h>

#include <linux/types.h>

#include "format.h"
#include "img_gralloc1_public.h"

bool is_valid_format(uint32_t format)
{
    switch (format) {
    case HAL_PIXEL_FORMAT_RGBA_8888:
    case HAL_PIXEL_FORMAT_BGRA_8888:
        return true;

    case HAL_PIXEL_FORMAT_RGB_565:
        return true;

    case HAL_PIXEL_FORMAT_NV12:
        return true;

    case HAL_PIXEL_FORMAT_RGBX_8888:
    case HAL_PIXEL_FORMAT_BGRX_8888:
        return true;

    case HAL_PIXEL_FORMAT_TI_NV12:
    case HAL_PIXEL_FORMAT_TI_NV12_1D:
        /* legacy formats not supported anymore */
        return false;

    default:
        return false;
    }
}

bool is_rgb_format(uint32_t format)
{
    switch (format) {
    case HAL_PIXEL_FORMAT_BGRA_8888:
    case HAL_PIXEL_FORMAT_BGRX_8888:
    case HAL_PIXEL_FORMAT_RGB_565:
        return true;
    default:
        return false;
    }
}

bool is_bgr_format(uint32_t format)
{
    switch (format) {
    case HAL_PIXEL_FORMAT_RGBX_8888:
    case HAL_PIXEL_FORMAT_RGBA_8888:
        return true;
    default:
        return false;
    }
}

bool is_nv12_format(uint32_t format)
{
    switch (format) {
    case HAL_PIXEL_FORMAT_TI_NV12:
    case HAL_PIXEL_FORMAT_TI_NV12_1D:
    case HAL_PIXEL_FORMAT_NV12:
        return true;
    default:
        return false;
    }
}

bool is_opaque_format(uint32_t format)
{
    switch (format) {
    case HAL_PIXEL_FORMAT_RGB_565:
    case HAL_PIXEL_FORMAT_RGBX_8888:
    case HAL_PIXEL_FORMAT_BGRX_8888:
        return true;
    default:
        return false;
    }
}

uint32_t get_format_bpp(uint32_t format)
{
    switch (format) {
    case HAL_PIXEL_FORMAT_BGRA_8888:
    case HAL_PIXEL_FORMAT_BGRX_8888:
    case HAL_PIXEL_FORMAT_RGBX_8888:
    case HAL_PIXEL_FORMAT_RGBA_8888:
        return 32;
    case HAL_PIXEL_FORMAT_RGB_565:
        return 16;
    case HAL_PIXEL_FORMAT_TI_NV12:
    case HAL_PIXEL_FORMAT_TI_NV12_1D:
    case HAL_PIXEL_FORMAT_NV12:
        return 8;
    default:
        return 0;
    }
}

uint32_t convert_hal_to_drm_format(uint32_t hal_format, bool blended)
{
    uint32_t dss_format = HAL_PIXEL_FORMAT_RGBA_8888;

    /* convert color format */
    switch (hal_format) {
    case HAL_PIXEL_FORMAT_RGBA_8888:
    case HAL_PIXEL_FORMAT_BGRA_8888:
        dss_format = DRM_FORMAT_ARGB8888;
        if (blended)
            break;

    case HAL_PIXEL_FORMAT_RGBX_8888:
    case HAL_PIXEL_FORMAT_BGRX_8888:
        dss_format = DRM_FORMAT_XRGB8888;
        break;

    case HAL_PIXEL_FORMAT_RGB_565:
        dss_format = DRM_FORMAT_RGB565;
        break;

    case HAL_PIXEL_FORMAT_TI_NV12:
    case HAL_PIXEL_FORMAT_TI_NV12_1D:
    case HAL_PIXEL_FORMAT_NV12:
        dss_format = DRM_FORMAT_NV12;
        break;

    default:
        /* Should have been filtered out */
        ALOGV("Unsupported pixel format");
    }

    return dss_format;
}
