/*
 * Copyright (C) 2020 ARM Limited. All rights reserved.
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

#include "gralloc_capabilities.h"

#include <string.h>
#include <dlfcn.h>
#include <inttypes.h>
#include <assert.h>
#include <pthread.h>

#include "core/format_info.h"

/* Writing to runtime_caps_read is guarded by mutex caps_init_mutex. */
static pthread_mutex_t caps_init_mutex = PTHREAD_MUTEX_INITIALIZER;
static bool runtime_caps_read = false;

mali_gralloc_format_caps cpu_runtime_caps;
mali_gralloc_format_caps dpu_runtime_caps;
mali_gralloc_format_caps vpu_runtime_caps;
mali_gralloc_format_caps gpu_runtime_caps;
mali_gralloc_format_caps cam_runtime_caps;
mali_gralloc_format_caps bo_runtime_caps;
mali_gralloc_format_caps bw_runtime_caps;
mali_gralloc_format_caps mfc_runtime_caps;

void get_ip_capabilities(void)
{
	/* Ensure capability setting is not interrupted by other
	 * allocations during start-up.
	 */
	pthread_mutex_lock(&caps_init_mutex);

	if (runtime_caps_read)
	{
		goto already_init;
	}

	sanitize_formats();

	/* Determine CPU IP capabilities */
	cpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT;
	cpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_PIXFMT_RGBA1010102;
	cpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_PIXFMT_RGBA16161616;

	/* Determine DPU IP capabilities */
	dpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT;
	dpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC;
	dpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_READ;
#ifdef SOC_ZUMA
	dpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK;
#endif
#if defined(GRALLOC_DPU_SUPPORT_1010102_AFBC) && (GRALLOC_DPU_SUPPORT_1010102_AFBC == 1)
	dpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_PIXFMT_RGBA1010102;
#endif

	gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT;
#if defined(MALI_GPU_SUPPORT_AFBC_BASIC) && (MALI_GPU_SUPPORT_AFBC_BASIC == 1)
	gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC;
	gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_READ;
	gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_PIXFMT_RGBA1010102;
	gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_PIXFMT_RGBA16161616;

#if defined(MALI_GPU_SUPPORT_AFBC_YUV_WRITE) && (MALI_GPU_SUPPORT_AFBC_YUV_WRITE == 1)
	gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_WRITE;
#endif

#if defined(MALI_GPU_SUPPORT_AFBC_SPLITBLK) && (MALI_GPU_SUPPORT_AFBC_SPLITBLK == 1)
	gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK;
#endif

#if defined(MALI_GPU_SUPPORT_AFBC_WIDEBLK) && (MALI_GPU_SUPPORT_AFBC_WIDEBLK == 1)
	gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK;
	gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK;
#endif

#if defined(MALI_GPU_SUPPORT_AFBC_TILED_HEADERS) && (MALI_GPU_SUPPORT_AFBC_TILED_HEADERS == 1)
	gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK;
	gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK;
	gpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS;
#endif
#endif /* defined(MALI_GPU_SUPPORT_AFBC_BASIC) && (MALI_GPU_SUPPORT_AFBC_BASIC == 1) */

	/* Determine BIGOCEAN IP capabilities */
	bo_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT;
	bo_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC;
	bo_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_WRITE;

	/* Determine BIGWAVE IP capabilities */
	bw_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT;
	bw_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC;
	bw_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_READ;
	bw_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_WRITE;
	bw_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK;

	/* Determine MFC IP capabilities */
	mfc_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT;

	/* Determine VPU IP capabilities */
	vpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT;
	vpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC;
	vpu_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_WRITE;

	/* Determine CAM IP capabilities */
	cam_runtime_caps.caps_mask |= MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT;

/* Build specific capability changes */
#if defined(GRALLOC_ARM_NO_EXTERNAL_AFBC) && (GRALLOC_ARM_NO_EXTERNAL_AFBC == 1)
	cpu_runtime_caps.caps_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
	dpu_runtime_caps.caps_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
	vpu_runtime_caps.caps_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
	gpu_runtime_caps.caps_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
	cam_runtime_caps.caps_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
	bo_runtime_caps.caps_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
	bw_runtime_caps.caps_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
	mfc_runtime_caps.caps_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
#endif

	runtime_caps_read = true;

already_init:
	pthread_mutex_unlock(&caps_init_mutex);

	MALI_GRALLOC_LOGV("GPU format capabilities 0x%" PRIx64, gpu_runtime_caps.caps_mask);
	MALI_GRALLOC_LOGV("DPU format capabilities 0x%" PRIx64, dpu_runtime_caps.caps_mask);
	MALI_GRALLOC_LOGV("VPU format capabilities 0x%" PRIx64, vpu_runtime_caps.caps_mask);
	MALI_GRALLOC_LOGV("CAM format capabilities 0x%" PRIx64, cam_runtime_caps.caps_mask);
	MALI_GRALLOC_LOGV("BO format capabilities 0x%" PRIx64, bo_runtime_caps.caps_mask);
	MALI_GRALLOC_LOGV("MFC format capabilities 0x%" PRIx64, mfc_runtime_caps.caps_mask);
}


/* This is used by the unit tests to get the capabilities for each IP. */
extern "C" {
	void mali_gralloc_get_caps(struct mali_gralloc_format_caps *gpu_caps,
	                           struct mali_gralloc_format_caps *vpu_caps,
	                           struct mali_gralloc_format_caps *dpu_caps,
	                           struct mali_gralloc_format_caps *cam_caps)
	{
		get_ip_capabilities();

		memcpy(gpu_caps, (void *)&gpu_runtime_caps, sizeof(*gpu_caps));
		memcpy(vpu_caps, (void *)&vpu_runtime_caps, sizeof(*vpu_caps));
		memcpy(dpu_caps, (void *)&dpu_runtime_caps, sizeof(*dpu_caps));
		memcpy(cam_caps, (void *)&cam_runtime_caps, sizeof(*cam_caps));
	}
}
