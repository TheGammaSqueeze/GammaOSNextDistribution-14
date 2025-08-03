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

#pragma once

#include <string>

#include <cstdint>

#include <drm/drm_fourcc.h>
#include <linux/types.h>

#include "img_gralloc1_public.h"

static inline std::string HAL_FMT(uint32_t format)
{
	switch (format) {
	case HAL_PIXEL_FORMAT_TI_NV12: return "NV12";
	case HAL_PIXEL_FORMAT_TI_NV12_1D: return "NV12";
	case HAL_PIXEL_FORMAT_YV12: return "YV12";
	case HAL_PIXEL_FORMAT_BGRX_8888: return "xRGB32";
	case HAL_PIXEL_FORMAT_RGBX_8888: return "xBGR32";
	case HAL_PIXEL_FORMAT_BGRA_8888: return "ARGB32";
	case HAL_PIXEL_FORMAT_RGBA_8888: return "ABGR32";
	case HAL_PIXEL_FORMAT_RGB_565: return "RGB565";
	default: return "??";
	}
}

static inline std::string DRM_FMT(uint32_t format)
{
	switch (format) {
	case DRM_FORMAT_NV12: return "NV12";
	case DRM_FORMAT_XRGB8888: return "xRGB32";
	case DRM_FORMAT_ARGB8888: return "ARGB32";
	case DRM_FORMAT_RGB565: return "RGB565";
	default: return "??";
	}
}

bool is_valid_format(uint32_t format);
bool is_rgb_format(uint32_t format);
bool is_bgr_format(uint32_t format);
bool is_nv12_format(uint32_t format);
bool is_opaque_format(uint32_t format);
uint32_t get_format_bpp(uint32_t format);
uint32_t convert_hal_to_dss_format(uint32_t format, bool blended);
uint32_t convert_hal_to_drm_format(uint32_t hal_format, bool blended);
uint32_t convert_hal_to_ocd_format(uint32_t hal_format);
uint32_t get_stride_from_format(uint32_t format, uint32_t width);
