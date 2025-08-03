/*
 * Copyright (C) 2020 Samsung Electronics Co. Ltd.
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

#include <linux/videodev2_exynos_media.h>
#include <gralloc_priv.h>

#define PLANE_SIZE(w, h)      ((w) * (h))
#define S2B_PLANE_SIZE(w, h)  (GRALLOC_ALIGN((w) / 4, 16) * (GRALLOC_ALIGN(h, 16)))

/*
 * Compute SBWC buffer geometry for a buffer containing packed SBWC YUV data
 * with bits per pixel bpp, width w, and height h.
 * Returns a pair of { luma size, chroma size }.
 */
template <int bpp>
static std::pair<size_t, size_t> sbwc_sizes(int w, int h) {
	static_assert(bpp == 8 || bpp == 10, "Unexpected bit width");

	const size_t luma_body_size = (bpp == 8) ?
		SBWC_8B_Y_SIZE(w, h) : SBWC_10B_Y_SIZE(w, h);
	const size_t luma_header_size = (bpp == 8) ?
		SBWC_8B_Y_HEADER_SIZE(w, h) : SBWC_10B_Y_HEADER_SIZE(w, h);

	const size_t chroma_body_size = (bpp == 8) ?
		SBWC_8B_CBCR_SIZE(w, h) : SBWC_10B_CBCR_SIZE(w, h);
	const size_t chroma_header_size = (bpp == 8) ?
		SBWC_8B_CBCR_HEADER_SIZE(w, h) : SBWC_10B_CBCR_HEADER_SIZE(w, h);

	MALI_GRALLOC_LOGV("SBWC luma body size 0x%zx, header size 0x%zx", luma_body_size, luma_header_size);
	MALI_GRALLOC_LOGV("SBWC chroma body size 0x%zx, header size 0x%zx", chroma_body_size, chroma_header_size);

	return { luma_body_size + luma_header_size,
	         chroma_body_size + chroma_header_size };
}

/*
 * All setup_<format> function will returns the plane_count
 */

/* Sets up 8-bit SBWC semi planar and returns the plane count */
int setup_sbwc_420_sp(int w, int h, int fd_count, plane_info_t *plane)
{
	std::tie(plane[0].size, plane[1].size) = sbwc_sizes<8>(w, h);

	plane[0].alloc_width = GRALLOC_ALIGN(w, 32);
	plane[0].alloc_height = __ALIGN_UP(h, 16);
	plane[0].byte_stride = SBWC_8B_STRIDE(w);
	plane[0].fd_idx = 0;

	plane[1].alloc_width = GRALLOC_ALIGN(w, 32);
	plane[1].alloc_height = __ALIGN_UP(h, 16) / 2;
	plane[1].byte_stride = SBWC_8B_STRIDE(w);

	if (fd_count > 1)
	{
		plane[1].fd_idx = 1;
	}
	else
	{
		plane[1].fd_idx = 0;
		plane[1].offset = plane[0].size;
	}

	return 2;
}

/* Sets up 10-bit SBWC semi planar and returns the plane count */
int setup_sbwc_420_sp_10bit(int w, int h, int fd_count, plane_info_t *plane)
{
	std::tie(plane[0].size, plane[1].size) = sbwc_sizes<10>(w, h);

	plane[0].alloc_width = GRALLOC_ALIGN(w, 32);
	plane[0].alloc_height = __ALIGN_UP(h, 16);
	plane[0].byte_stride = SBWC_10B_STRIDE(w);
	plane[0].fd_idx = 0;

	plane[1].alloc_width = GRALLOC_ALIGN(w, 32);
	plane[1].alloc_height = __ALIGN_UP(h, 16) / 2;
	plane[1].byte_stride = SBWC_10B_STRIDE(w);

	if (fd_count > 1)
	{
		plane[1].fd_idx = 1;
	}
	else
	{
		plane[1].fd_idx = 0;
		plane[1].offset = plane[0].size;
	}

	return 2;
}

/* Sets up 8-bit Lossy SBWC semi planar and returns the plane count */
int setup_sbwc_420_sp_lossy(int width, int height, int rate, int fd_count, plane_info_t *plane)
{
	plane[0].size = SBWCL_8B_Y_SIZE(width, height, rate);
	plane[0].alloc_width = GRALLOC_ALIGN(width, 32);
	plane[0].alloc_height = __ALIGN_UP(height, 8);
	plane[0].byte_stride = SBWCL_8B_STRIDE(width, rate);
	plane[0].fd_idx = 0;

	plane[1].size = SBWCL_8B_CBCR_SIZE(width, height, rate);
	plane[1].alloc_width = GRALLOC_ALIGN(width, 32);
	plane[1].alloc_height = __ALIGN_UP(height, 8) / 2;
	plane[1].byte_stride = SBWCL_8B_STRIDE(width, rate);

	if (fd_count > 1)
	{
		plane[1].fd_idx = 1;
	}
	else
	{
		plane[1].fd_idx = 0;
		plane[1].offset = plane[0].size;
	}

	return 2;
}


/* Sets up 10-bit Lossy SBWC semi planar and returns the plane count */
int setup_sbwc_420_sp_10bit_lossy(int width, int height, int rate, int fd_count, plane_info_t *plane)
{
	plane[0].size = SBWCL_10B_Y_SIZE(width, height, rate);
	plane[0].alloc_width = GRALLOC_ALIGN(width, 32);
	plane[0].alloc_height = __ALIGN_UP(height, 8);
	plane[0].byte_stride = SBWCL_10B_STRIDE(width, rate);
	plane[0].fd_idx = 0;

	plane[1].size = SBWCL_10B_CBCR_SIZE(width, height, rate);
	plane[1].alloc_width = GRALLOC_ALIGN(width, 32);
	plane[1].alloc_height = __ALIGN_UP(height, 8) / 2;
	plane[1].byte_stride = SBWCL_10B_STRIDE(width, rate);

	if (fd_count > 1)
	{
		plane[1].fd_idx = 1;
	}
	else
	{
		plane[1].fd_idx = 0;
		plane[1].offset = plane[0].size;
	}

	return 2;
}

int setup_420_sp(int width, int height, int fd_count, plane_info_t *plane)
{
	/* TODO: make this into an assert instead ? */
	height = GRALLOC_ALIGN(height, 2);

	plane[0].size = PLANE_SIZE(width, height);
	plane[0].alloc_width = width;
	plane[0].alloc_height = height;
	plane[0].byte_stride = width;
	plane[0].fd_idx = 0;

	int chroma_width = width;
	int chroma_height = height / 2;

	plane[1].size = PLANE_SIZE(chroma_width, chroma_height);
	plane[1].alloc_width = chroma_width;
	plane[1].alloc_height = chroma_height;
	plane[1].byte_stride = chroma_width;

	if (fd_count > 1)
	{
		plane[1].fd_idx = 1;
	}
	else
	{
		plane[1].fd_idx = 0;
		plane[1].offset = plane[0].size;
	}

	return 2;
}

int setup_420_sp_s10b(int width, int height, int fd_count, plane_info_t *plane)
{
	/* TODO: make this into an assert instead ? */
	/* TODO: assert height aligned to 16 ? */
	height = GRALLOC_ALIGN(height, 2);

	int mscl_ext = 256;

	/* HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_S10B have special padding requirement */
	if (fd_count == 1)
	{
		mscl_ext = 64;
	}

	plane[0].size = PLANE_SIZE(width, height) + S2B_PLANE_SIZE(width, height) + mscl_ext;
	plane[0].alloc_width = width;
	plane[0].alloc_height = height;
	plane[0].byte_stride = width;
	plane[0].fd_idx = 0;

	int chroma_width = width;
	int chroma_height = height / 2;

	plane[1].size = PLANE_SIZE(chroma_width, chroma_height)
		+ S2B_PLANE_SIZE(chroma_width, chroma_height) + mscl_ext;
	plane[1].alloc_width = chroma_width;
	plane[1].alloc_height = chroma_height;
	plane[1].byte_stride = chroma_width;

	if (fd_count > 1)
	{
		plane[1].fd_idx = 1;
	}
	else
	{
		plane[1].fd_idx = 0;
		plane[1].offset = plane[0].size;
	}

	return 2;
}

int setup_p010_sp(int width, int height, int fd_count, plane_info_t *plane)
{
	/* TODO: make this into an assert instead ? */
	height = GRALLOC_ALIGN(height, 2);

	plane[0].size = PLANE_SIZE(width * 2, height);
	plane[0].alloc_width = width;
	plane[0].alloc_height = height;
	plane[0].byte_stride = width * 2;
	plane[0].fd_idx = 0;

	int chroma_width = width;
	int chroma_height = height / 2;

	plane[1].size = PLANE_SIZE(chroma_width * 2, chroma_height);
	plane[1].alloc_width = chroma_width;
	plane[1].alloc_height = chroma_height;
	plane[1].byte_stride = chroma_width * 2;

	if (fd_count > 1)
	{
		plane[1].fd_idx = 1;
	}
	else
	{
		plane[1].fd_idx = 0;
		plane[1].offset = plane[0].size;
	}

	return 2;
}

int setup_420_p(int width, int height, int fd_count, plane_info_t *plane)
{
	/* TODO: make this into an assert instead ? */
	width = GRALLOC_ALIGN(width, 2);
	height = GRALLOC_ALIGN(height, 2);

	plane[0].size = PLANE_SIZE(width, height);
	plane[0].alloc_width = width;
	plane[0].alloc_height = height;
	plane[0].byte_stride = width;
	plane[0].fd_idx = 0;

	int chroma_width = GRALLOC_ALIGN(width / 2, 16);
	int chroma_height = height / 2;

	plane[1].size = PLANE_SIZE(chroma_width, chroma_height);
	plane[1].alloc_width = chroma_width;
	plane[1].alloc_height = chroma_height;
	plane[1].byte_stride = chroma_width;

	plane[2].size = PLANE_SIZE(chroma_width, chroma_height);
	plane[2].alloc_width = chroma_width;
	plane[2].alloc_height = chroma_height;
	plane[2].byte_stride = chroma_width;

	if (fd_count > 1)
	{
		plane[1].fd_idx = 1;
		plane[2].fd_idx = 2;
	}
	else
	{
		plane[1].fd_idx = 0;
		plane[2].fd_idx = 0;
		plane[1].offset = plane[0].size;
		plane[2].offset = plane[0].size + plane[1].size;
	}

	return 3;
}

int setup_420_sp_tiled(int width, int height, int fd_count, plane_info_t *plane)
{
	/* TODO: make this into an assert instead ? */
	width = GRALLOC_ALIGN(width, 2);
	height = GRALLOC_ALIGN(height, 2);

	plane[0].size = PLANE_SIZE(width, height);
	plane[0].alloc_width = width;
	plane[0].alloc_height = height;
	plane[0].byte_stride = width * 16;
	plane[0].fd_idx = 0;

	int chroma_width = width;
	int chroma_height = GRALLOC_ALIGN(height / 2, 32);

	plane[1].size = PLANE_SIZE(chroma_width, chroma_height);
	plane[1].alloc_width = chroma_width;
	plane[1].alloc_height = chroma_height;
	plane[1].byte_stride = chroma_width * 16;

	if (fd_count > 1)
	{
		plane[1].fd_idx = 1;
	}
	else
	{
		plane[1].fd_idx = 0;
		plane[1].offset = plane[0].size;
	}

	return 3;
}
