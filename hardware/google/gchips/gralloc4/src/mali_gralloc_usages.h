/*
 * Copyright (C) 2017-2020 ARM Limited. All rights reserved.
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

#ifndef MALI_GRALLOC_USAGES_H_
#define MALI_GRALLOC_USAGES_H_

#define GRALLOC_USAGE_PRIVATE_MASK (0xffff0000f0000000U)
#define GRALLOC_USAGE_PUBLIC_MASK  ((uint64_t) ~GRALLOC_USAGE_PRIVATE_MASK)

/*
 * MALI_GRALLOC_USAGE_AFBC_PADDING is only valid if MALI_GRALLOC_USAGE_NO_AFBC
 * is not present.
 */

#include <aidl/android/hardware/graphics/common/BufferUsage.h>
#include <android/hardware/graphics/common/1.2/types.h>
#include <pixel-gralloc/usage.h>

/* BufferUsage is not defined in 1.2/types.h as there are no changes from previous version */
namespace hidl_common = android::hardware::graphics::common::V1_1;
namespace aidl_common = aidl::android::hardware::graphics::common;

/* Local macro definitions to emulate Gralloc 1.0 usage interface */
#define GRALLOC_USAGE_PRIVATE_0 1ULL << 28
#define GRALLOC_USAGE_PRIVATE_1 1ULL << 29
#define GRALLOC_USAGE_PRIVATE_2 1ULL << 30
#define GRALLOC_USAGE_PRIVATE_3 1ULL << 31
#define GRALLOC_USAGE_PRIVATE_4 1ULL << 63
#define GRALLOC_USAGE_PRIVATE_5 1ULL << 62
#define GRALLOC_USAGE_PRIVATE_6 1ULL << 61
#define GRALLOC_USAGE_PRIVATE_7 1ULL << 60
#define GRALLOC_USAGE_PRIVATE_8 1ULL << 59
#define GRALLOC_USAGE_PRIVATE_9 1ULL << 58
#define GRALLOC_USAGE_PRIVATE_10 1ULL << 57
#define GRALLOC_USAGE_PRIVATE_11 1ULL << 56
#define GRALLOC_USAGE_PRIVATE_12 1ULL << 55
#define GRALLOC_USAGE_PRIVATE_13 1ULL << 54
#define GRALLOC_USAGE_PRIVATE_14 1ULL << 53
#define GRALLOC_USAGE_PRIVATE_15 1ULL << 52
#define GRALLOC_USAGE_PRIVATE_16 1ULL << 51
#define GRALLOC_USAGE_PRIVATE_17 1ULL << 50
#define GRALLOC_USAGE_PRIVATE_18 1ULL << 49
#define GRALLOC_USAGE_PRIVATE_19 1ULL << 48

// TODO(b/183478446): Cleanup usage flags redefinition
typedef enum
{
	DEPRECATED_MALI_GRALLOC_USAGE_FRONTBUFFER = GRALLOC_USAGE_PRIVATE_12,
	MALI_GRALLOC_USAGE_FORCE_BACKBUFFER = GRALLOC_USAGE_PRIVATE_13,
	MALI_GRALLOC_USAGE_NO_AFBC = GRALLOC_USAGE_PRIVATE_1,
	MALI_GRALLOC_USAGE_AFBC_PADDING = GRALLOC_USAGE_PRIVATE_14,

	/* SLSI specific usages */
	GRALLOC_USAGE_PRIVATE_NONSECURE                        = GRALLOC_USAGE_PRIVATE_8,
	GRALLOC_USAGE_NOZEROED                                 = GRALLOC_USAGE_PRIVATE_9,

	GRALLOC_USAGE_VIDEO_PRIVATE_DATA                       = GRALLOC_USAGE_PRIVATE_7,

	GRALLOC_USAGE_ROIINFO                                  = GRALLOC_USAGE_PRIVATE_15,

	/* Google specific usages */
	GRALLOC_USAGE_GOOGLE_IP_BO                             = GRALLOC_USAGE_PRIVATE_16,
	GRALLOC_USAGE_GOOGLE_IP_BW                             = GRALLOC_USAGE_PRIVATE_16, /* Alias to BO */
	GRALLOC_USAGE_GOOGLE_IP_BIG                            = GRALLOC_USAGE_PRIVATE_16, /* Alias to BO/BW */
	GRALLOC_USAGE_GOOGLE_IP_MFC                            = GRALLOC_USAGE_PRIVATE_17,
	GRALLOC_USAGE_PLACEHOLDER_BUFFER                       = ::pixel::graphics::Usage::PLACEHOLDER_BUFFER,

	/* FaceAuth specific usages. */
	GS101_GRALLOC_USAGE_TPU_INPUT                          = GRALLOC_USAGE_PRIVATE_5,
	GS101_GRALLOC_USAGE_TPU_OUTPUT                         = GRALLOC_USAGE_PRIVATE_3,
	GS101_GRALLOC_USAGE_CAMERA_STATS                       = GRALLOC_USAGE_PRIVATE_2,

	GS101_GRALLOC_USAGE_FACEAUTH_RAW_EVAL                  = ::pixel::graphics::Usage::FACEAUTH_RAW_EVAL,
} mali_gralloc_usage_type;

#define GRALLOC_USAGE_SW_WRITE_RARELY static_cast<uint64_t>(hidl_common::BufferUsage::CPU_WRITE_RARELY)
#define GRALLOC_USAGE_SW_WRITE_OFTEN static_cast<uint64_t>(hidl_common::BufferUsage::CPU_WRITE_OFTEN)
#define GRALLOC_USAGE_SW_READ_RARELY static_cast<uint64_t>(hidl_common::BufferUsage::CPU_READ_RARELY)
#define GRALLOC_USAGE_SW_READ_OFTEN static_cast<uint64_t>(hidl_common::BufferUsage::CPU_READ_OFTEN)
#define GRALLOC_USAGE_RENDERSCRIPT static_cast<uint64_t>(hidl_common::BufferUsage::RENDERSCRIPT)
#define GRALLOC_USAGE_HW_FB static_cast<uint64_t>(hidl_common::BufferUsage::COMPOSER_CLIENT_TARGET)

/* Bit 10 must be zero as per Gralloc 2.x interface specification. Used, however, for backward compatibility */
#define GRALLOC_USAGE_HW_2D  static_cast<uint64_t>(0x00000400)

#define GRALLOC_USAGE_SW_WRITE_MASK static_cast<uint64_t>(hidl_common::BufferUsage::CPU_WRITE_MASK)
#define GRALLOC_USAGE_SW_READ_MASK static_cast<uint64_t>(hidl_common::BufferUsage::CPU_READ_MASK)
#define GRALLOC_USAGE_PROTECTED static_cast<uint64_t>(hidl_common::BufferUsage::PROTECTED)
#define GRALLOC_USAGE_CURSOR static_cast<uint64_t>(hidl_common::BufferUsage::COMPOSER_CURSOR)
#define GRALLOC_USAGE_HW_RENDER static_cast<uint64_t>(hidl_common::BufferUsage::GPU_RENDER_TARGET)
#define GRALLOC_USAGE_HW_CAMERA_WRITE static_cast<uint64_t>(hidl_common::BufferUsage::CAMERA_OUTPUT)
#define GRALLOC_USAGE_HW_CAMERA_READ static_cast<uint64_t>(hidl_common::BufferUsage::CAMERA_INPUT)
#define GRALLOC_USAGE_HW_TEXTURE static_cast<uint64_t>(hidl_common::BufferUsage::GPU_TEXTURE)
#define GRALLOC_USAGE_HW_VIDEO_ENCODER static_cast<uint64_t>(hidl_common::BufferUsage::VIDEO_ENCODER)
#define GRALLOC_USAGE_HW_VIDEO_DECODER static_cast<uint64_t>(hidl_common::BufferUsage::VIDEO_DECODER)
#define GRALLOC_USAGE_HW_COMPOSER static_cast<uint64_t>(hidl_common::BufferUsage::COMPOSER_OVERLAY)
#define GRALLOC_USAGE_EXTERNAL_DISP  static_cast<uint64_t>(0x00002000)

#define GRALLOC_USAGE_SENSOR_DIRECT_DATA static_cast<uint64_t>(hidl_common::BufferUsage::SENSOR_DIRECT_DATA)
#define GRALLOC_USAGE_GPU_DATA_BUFFER static_cast<uint64_t>(hidl_common::BufferUsage::GPU_DATA_BUFFER)
#define GRALLOC_USAGE_FRONT_BUFFER static_cast<uint64_t>(aidl_common::BufferUsage::FRONT_BUFFER)

#define UNSUPPORTED_MALI_GRALLOC_USAGE_CUBE_MAP static_cast<uint64_t>(aidl_common::BufferUsage::GPU_CUBE_MAP)
#define UNSUPPORTED_MALI_GRALLOC_USAGE_MIPMAP_COMPLETE static_cast<uint64_t>(aidl_common::BufferUsage::GPU_MIPMAP_COMPLETE)


/* Originally (Gralloc 0.x), Android did not provide an explicit DECODER usage. This was rectified in Android N-MR1/7.1
 * when Gralloc 1.0 defined GRALLOC1_PRODUCER_USAGE_VIDEO_DECODER. However, libstagefright continues
 * (currently, Android P/9.0) to use this old combination of flags to indicate decoder producer
 */
#define GRALLOC_USAGE_DECODER (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_COMPOSER | GRALLOC_USAGE_EXTERNAL_DISP)

static const uint64_t VALID_USAGE =
    GRALLOC_USAGE_SW_READ_MASK |            /* 0x0FU */
    GRALLOC_USAGE_SW_WRITE_MASK |           /* 0xF0U */
    GRALLOC_USAGE_HW_TEXTURE |              /* 1U << 8 */
    GRALLOC_USAGE_HW_RENDER |               /* 1U << 9 */
    GRALLOC_USAGE_HW_2D |                   /* 1U << 10 */
    GRALLOC_USAGE_HW_COMPOSER |             /* 1U << 11 */
    GRALLOC_USAGE_HW_FB |                   /* 1U << 12 */
    GRALLOC_USAGE_EXTERNAL_DISP |           /* 1U << 13 */
    GRALLOC_USAGE_PROTECTED |               /* 1U << 14 */
    GRALLOC_USAGE_CURSOR |                  /* 1U << 15 */
    GRALLOC_USAGE_HW_VIDEO_ENCODER |        /* 1U << 16 */
    GRALLOC_USAGE_HW_CAMERA_WRITE |         /* 1U << 17 */
    GRALLOC_USAGE_HW_CAMERA_READ |          /* 1U << 18 */
    GRALLOC_USAGE_RENDERSCRIPT |            /* 1U << 20 */
    GRALLOC_USAGE_HW_VIDEO_DECODER |        /* 1U << 22 */
    /* Producer and consumer usage are combined, but on Gralloc version 1 there is no way to differentiate these as they
     * are mapped to the same value (1U << 23). */
    GRALLOC_USAGE_SENSOR_DIRECT_DATA |      /* 1U << 23 */
    GRALLOC_USAGE_GPU_DATA_BUFFER |         /* 1U << 24 */
    GRALLOC_USAGE_FRONT_BUFFER |            /* 1U << 32 */

    /* Google specific usages */
    GRALLOC_USAGE_GOOGLE_IP_BIG |           /* 1U << 51 */
    GRALLOC_USAGE_GOOGLE_IP_MFC |           /* 1U << 50 */

    GS101_GRALLOC_USAGE_TPU_INPUT |         /* 1U << 62 */
    GS101_GRALLOC_USAGE_TPU_OUTPUT |        /* 1U << 31 */
    GS101_GRALLOC_USAGE_FACEAUTH_RAW_EVAL | /* 1U << 63 */
    GS101_GRALLOC_USAGE_CAMERA_STATS |      /* 1U << 30 */

    GRALLOC_USAGE_ROIINFO |                 /* 1U << 52 */
    MALI_GRALLOC_USAGE_AFBC_PADDING |       /* 1U << 53 */
    MALI_GRALLOC_USAGE_FORCE_BACKBUFFER |   /* 1U << 54 */
    MALI_GRALLOC_USAGE_NO_AFBC |            /* 1U << 29 */
    0;

#endif /*MALI_GRALLOC_USAGES_H_*/
