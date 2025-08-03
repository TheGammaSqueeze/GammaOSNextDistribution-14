/*
 * Copyright (C) 2017 ARM Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
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

#ifndef GRALLOC3_PRIV_H_
#define GRALLOC3_PRIV_H_

#include <linux/fb.h>
#include <hardware/gralloc1.h>
#include <inttypes.h>
#include <log/log.h>

#define AFBC_INFO_SIZE                              (sizeof(int))
#define AFBC_ENABLE                                 (0xafbc)

/* Exynos specific usages */
#define GRALLOC1_PRODUCER_USAGE_PRIVATE_NONSECURE               GRALLOC1_PRODUCER_USAGE_PRIVATE_8
#define GRALLOC1_PRODUCER_USAGE_NOZEROED                        GRALLOC1_PRODUCER_USAGE_PRIVATE_9

#define GRALLOC1_CONSUMER_USAGE_VIDEO_PRIVATE_DATA              GRALLOC1_CONSUMER_USAGE_PRIVATE_7

#define GRALLOC_USAGE_GOOGLE_IP_BO                              GRALLOC1_CONSUMER_USAGE_PRIVATE_16
#define GRALLOC_USAGE_GOOGLE_IP_BW                              GRALLOC1_CONSUMER_USAGE_PRIVATE_16 /* Alias to BO */
#define GRALLOC_USAGE_GOOGLE_IP_MFC                             GRALLOC1_CONSUMER_USAGE_PRIVATE_17

#define GS101_GRALLOC_USAGE_TPU_INPUT                           GRALLOC1_CONSUMER_USAGE_PRIVATE_5
#define GS101_GRALLOC_USAGE_TPU_OUTPUT                          GRALLOC1_PRODUCER_USAGE_PRIVATE_3
#define GS101_GRALLOC_USAGE_CAMERA_STATS                        GRALLOC1_PRODUCER_USAGE_PRIVATE_2

/* for legacy */
#define GRALLOC_USAGE_PRIVATE_NONSECURE                         GRALLOC1_PRODUCER_USAGE_PRIVATE_NONSECURE

typedef int ion_user_handle_t;

typedef enum
{
	/*
	 * Allocation will be used as a front-buffer, which
	 * supports concurrent producer-consumer access.
	 *
	 * NOTE: Must not be used with MALI_GRALLOC_USAGE_FORCE_BACKBUFFER
	 */
	MALI_GRALLOC_USAGE_FRONTBUFFER = GRALLOC1_PRODUCER_USAGE_PRIVATE_12,

	/*
	 * Allocation will be used as a back-buffer.
	 * Use when switching from front-buffer as a workaround for Android
	 * buffer queue, which does not re-allocate for a sub-set of
	 * existing usage.
	 *
	 * NOTE: Must not be used with MALI_GRALLOC_USAGE_FRONTBUFFER.
	 */
	MALI_GRALLOC_USAGE_FORCE_BACKBUFFER = GRALLOC1_PRODUCER_USAGE_PRIVATE_13,

	/*
	 * Buffer will not be allocated with AFBC.
	 *
	 * NOTE: Not compatible with MALI_GRALLOC_USAGE_FORCE_BACKBUFFER so cannot be
	 * used when switching from front-buffer to back-buffer.
	 */
	MALI_GRALLOC_USAGE_NO_AFBC = GRALLOC1_PRODUCER_USAGE_PRIVATE_1,

	/* Custom alignment for AFBC headers.
	 *
	 * NOTE: due to usage flag overlap, AFBC_PADDING cannot be used with FORCE_BACKBUFFER.
	 */
	MALI_GRALLOC_USAGE_AFBC_PADDING = GRALLOC1_PRODUCER_USAGE_PRIVATE_14,
	/* Private format usage.
	 * 'format' argument to allocation function will be interpreted in a
	 * private manner and must be constructed via GRALLOC_PRIVATE_FORMAT_WRAPPER_*
	 * macros which pack base format and AFBC format modifiers into 32-bit value.
	 */
	MALI_GRALLOC_USAGE_PRIVATE_FORMAT = GRALLOC1_PRODUCER_USAGE_PRIVATE_15,

	/* YUV only. */
	MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_DEFAULT = 0,
	MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT601 = GRALLOC1_PRODUCER_USAGE_PRIVATE_18,
	MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT709 = GRALLOC1_PRODUCER_USAGE_PRIVATE_19,
	MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT2020 = (GRALLOC1_PRODUCER_USAGE_PRIVATE_18 | GRALLOC1_PRODUCER_USAGE_PRIVATE_19),
	MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_MASK = MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT2020,

	MALI_GRALLOC_USAGE_RANGE_DEFAULT = 0,
	MALI_GRALLOC_USAGE_RANGE_NARROW = GRALLOC1_PRODUCER_USAGE_PRIVATE_16,
	MALI_GRALLOC_USAGE_RANGE_WIDE = GRALLOC1_PRODUCER_USAGE_PRIVATE_17,
	MALI_GRALLOC_USAGE_RANGE_MASK = (GRALLOC1_PRODUCER_USAGE_PRIVATE_16 | GRALLOC1_PRODUCER_USAGE_PRIVATE_17),
} mali_gralloc_usage_type;

typedef struct
{
	struct hw_module_t common;
} gralloc1_module_t;

typedef enum
{
	MALI_DPY_TYPE_UNKNOWN = 0,
	MALI_DPY_TYPE_CLCD,
	MALI_DPY_TYPE_HDLCD
} mali_dpy_type;

typedef enum
{
	MALI_YUV_NO_INFO,
	MALI_YUV_BT601_NARROW,
	MALI_YUV_BT601_WIDE,
	MALI_YUV_BT709_NARROW,
	MALI_YUV_BT709_WIDE,
	MALI_YUV_BT2020_NARROW,
	MALI_YUV_BT2020_WIDE
} mali_gralloc_yuv_info;

#endif /* GRALLOC3_PRIV_H_ */
