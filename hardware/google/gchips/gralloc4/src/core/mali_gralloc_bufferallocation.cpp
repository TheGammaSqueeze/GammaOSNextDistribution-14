/*
 * Copyright (C) 2016-2020 ARM Limited. All rights reserved.
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

#define ATRACE_TAG ATRACE_TAG_GRAPHICS

#include <inttypes.h>
#include <assert.h>
#include <atomic>
#include <algorithm>
#include <set>
#include <utils/Trace.h>

#include <hardware/hardware.h>
#include <hardware/gralloc1.h>

#include "mali_gralloc_bufferallocation.h"
#include "allocator/mali_gralloc_ion.h"
#include "mali_gralloc_buffer.h"
#include "mali_gralloc_bufferdescriptor.h"
#include "mali_gralloc_log.h"
#include "format_info.h"
#include <exynos_format.h>
#include "exynos_format_allocation.h"

#define EXT_SIZE       256
#define INVALID_USAGE  (((1LL * 0xFFFE) << 32))

/* HW needs extra padding bytes for its prefetcher does not check the picture boundary */
#define BW_EXT_SIZE (16 * 1024)

/* Default align values for Exynos */
#define YUV_BYTE_ALIGN_DEFAULT 16
#define RGB_BYTE_ALIGN_DEFAULT 64

/* IP-specific align values */
#define GPU_BYTE_ALIGN_DEFAULT 64
#define BIG_BYTE_ALIGN_DEFAULT 64
#ifdef SOC_ZUMA
#define CAMERA_RAW_BUFFER_BYTE_ALIGN 32
#endif

/* Realign YV12 format so that chroma stride is half of luma stride */
#define REALIGN_YV12 1

/* TODO: set S10B format align in BoardConfig.mk */
#define BOARD_EXYNOS_S10B_FORMAT_ALIGN 64
#if 0
ifeq ($(BOARD_EXYNOS_S10B_FORMAT_ALIGN), 64)
LOCAL_CFLAGS += -DBOARD_EXYNOS_S10B_FORMAT_ALIGN=$(BOARD_EXYNOS_S10B_FORMAT_ALIGN)
else
LOCAL_CFLAGS += -DBOARD_EXYNOS_S10B_FORMAT_ALIGN=16
endif
#endif

#define AFBC_PIXELS_PER_BLOCK 256
#define AFBC_HEADER_BUFFER_BYTES_PER_BLOCKENTRY 16

bool afbc_format_fallback(uint32_t * const format_idx, const uint64_t usage, bool force);


/*
 * Get a global unique ID
 */
static uint64_t getUniqueId()
{
	static std::atomic<uint32_t> counter(0);
	uint64_t id = static_cast<uint64_t>(getpid()) << 32;
	return id | counter++;
}

template<typename T>
static void afbc_buffer_align(const bool is_tiled, T *size)
{
	constexpr T AFBC_BODY_BUFFER_BYTE_ALIGNMENT = 1024;
	T buffer_byte_alignment = AFBC_BODY_BUFFER_BYTE_ALIGNMENT;

	if (is_tiled)
	{
		buffer_byte_alignment = 4 * AFBC_BODY_BUFFER_BYTE_ALIGNMENT;
	}

	*size = GRALLOC_ALIGN(*size, buffer_byte_alignment);
}

/*
 * Obtain AFBC superblock dimensions from type.
 */
static rect_t get_afbc_sb_size(AllocBaseType alloc_base_type)
{
	const uint16_t AFBC_BASIC_BLOCK_WIDTH = 16;
	const uint16_t AFBC_BASIC_BLOCK_HEIGHT = 16;
	const uint16_t AFBC_WIDE_BLOCK_WIDTH = 32;
	const uint16_t AFBC_WIDE_BLOCK_HEIGHT = 8;
	const uint16_t AFBC_EXTRAWIDE_BLOCK_WIDTH = 64;
	const uint16_t AFBC_EXTRAWIDE_BLOCK_HEIGHT = 4;

	rect_t sb = {0, 0};

	switch(alloc_base_type)
	{
		case AllocBaseType::AFBC:
			sb.width = AFBC_BASIC_BLOCK_WIDTH;
			sb.height = AFBC_BASIC_BLOCK_HEIGHT;
			break;
		case AllocBaseType::AFBC_WIDEBLK:
			sb.width = AFBC_WIDE_BLOCK_WIDTH;
			sb.height = AFBC_WIDE_BLOCK_HEIGHT;
			break;
		case AllocBaseType::AFBC_EXTRAWIDEBLK:
			sb.width = AFBC_EXTRAWIDE_BLOCK_WIDTH;
			sb.height = AFBC_EXTRAWIDE_BLOCK_HEIGHT;
			break;
		default:
			break;
	}
	return sb;
}

/*
 * Obtain AFBC superblock dimensions for specific plane.
 *
 * See alloc_type_t for more information.
 */
static rect_t get_afbc_sb_size(alloc_type_t alloc_type, const uint8_t plane)
{
	if (plane > 0 && alloc_type.is_afbc() && alloc_type.is_multi_plane)
	{
		return get_afbc_sb_size(AllocBaseType::AFBC_EXTRAWIDEBLK);
	}
	else
	{
		return get_afbc_sb_size(alloc_type.primary_type);
	}
}

bool get_alloc_type(const uint64_t format_ext,
                    const uint32_t format_idx,
                    const uint64_t usage,
                    alloc_type_t * const alloc_type)
{
	alloc_type->primary_type = AllocBaseType::UNCOMPRESSED;
	alloc_type->is_multi_plane = formats[format_idx].npln > 1;
	alloc_type->is_tiled = false;
	alloc_type->is_padded = false;
	alloc_type->is_frontbuffer_safe = false;

	/* Determine AFBC type for this format. This is used to decide alignment.
	   Split block does not affect alignment, and therefore doesn't affect the allocation type. */
	if (format_ext & MALI_GRALLOC_INTFMT_AFBCENABLE_MASK)
	{
		/* YUV transform shall not be enabled for a YUV format */
		if ((formats[format_idx].is_yuv == true) && (format_ext & MALI_GRALLOC_INTFMT_AFBC_YUV_TRANSFORM))
		{
			MALI_GRALLOC_LOGW("YUV Transform is incorrectly enabled for format = (%s 0x%x). Extended internal format = (%s 0x%" PRIx64 ")\n",
				format_name(formats[format_idx].id), formats[format_idx].id, format_name(format_ext), format_ext);
		}

		/* Determine primary AFBC (superblock) type. */
		alloc_type->primary_type = AllocBaseType::AFBC;
		if (format_ext & MALI_GRALLOC_INTFMT_AFBC_WIDEBLK)
		{
			alloc_type->primary_type = AllocBaseType::AFBC_WIDEBLK;
		}
		else if (format_ext & MALI_GRALLOC_INTFMT_AFBC_EXTRAWIDEBLK)
		{
			alloc_type->primary_type = AllocBaseType::AFBC_EXTRAWIDEBLK;
		}

		if (format_ext & MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS)
		{
			alloc_type->is_tiled = true;

			if (formats[format_idx].npln > 1 &&
				(format_ext & MALI_GRALLOC_INTFMT_AFBC_EXTRAWIDEBLK) == 0)
			{
				MALI_GRALLOC_LOGW("Extra-wide AFBC must be signalled for multi-plane formats. "
				      "Falling back to single plane AFBC.");
				alloc_type->is_multi_plane = false;
			}

			if (format_ext & MALI_GRALLOC_INTFMT_AFBC_DOUBLE_BODY)
			{
				alloc_type->is_frontbuffer_safe = true;
			}
		}
		else
		{
			if (formats[format_idx].npln > 1)
			{
				MALI_GRALLOC_LOGW("Multi-plane AFBC is not supported without tiling. "
				      "Falling back to single plane AFBC.");
			}
			alloc_type->is_multi_plane = false;
		}

		if (format_ext & MALI_GRALLOC_INTFMT_AFBC_EXTRAWIDEBLK &&
			!alloc_type->is_tiled)
		{
			/* Headers must be tiled for extra-wide. */
			MALI_GRALLOC_LOGE("ERROR: Invalid to specify extra-wide block without tiled headers.");
			return false;
		}

		if (alloc_type->is_frontbuffer_safe &&
		    (format_ext & (MALI_GRALLOC_INTFMT_AFBC_WIDEBLK | MALI_GRALLOC_INTFMT_AFBC_EXTRAWIDEBLK)))
		{
			MALI_GRALLOC_LOGE("ERROR: Front-buffer safe not supported with wide/extra-wide block.");
		}

		if (formats[format_idx].npln == 1 &&
		    format_ext & MALI_GRALLOC_INTFMT_AFBC_WIDEBLK &&
		    format_ext & MALI_GRALLOC_INTFMT_AFBC_EXTRAWIDEBLK)
		{
			/* "Wide + Extra-wide" implicitly means "multi-plane". */
			MALI_GRALLOC_LOGE("ERROR: Invalid to specify multiplane AFBC with single plane format.");
			return false;
		}

		if (usage & MALI_GRALLOC_USAGE_AFBC_PADDING)
		{
			alloc_type->is_padded = true;
		}
	}
	return true;
}

/*
 * Initialise AFBC header based on superblock layout.
 * Width and height should already be AFBC aligned.
 */
void init_afbc(uint8_t *buf, const uint64_t alloc_format,
               const bool is_multi_plane,
               const uint64_t w, const uint64_t h)
{
	ATRACE_CALL();
	const bool is_tiled = ((alloc_format & MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS)
	                         == MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS);
	const uint32_t n_headers = (w * h) / AFBC_PIXELS_PER_BLOCK;
	uint32_t body_offset = n_headers * AFBC_HEADER_BUFFER_BYTES_PER_BLOCKENTRY;

	afbc_buffer_align(is_tiled, &body_offset);

	/*
	 * Declare the AFBC header initialisation values for each superblock layout.
	 * Tiled headers (AFBC 1.2) can be initialised to zero for non-subsampled formats
	 * (SB layouts: 0, 3, 4, 7).
	 */
	uint32_t headers[][4] = {
		{ (uint32_t)body_offset, 0x1, 0x10000, 0x0 }, /* Layouts 0, 3, 4, 7 */
		{ ((uint32_t)body_offset + (1 << 28)), 0x80200040, 0x1004000, 0x20080 } /* Layouts 1, 5 */
	};
	if ((alloc_format & MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS))
	{
		/* Zero out body_offset for non-subsampled formats. */
		memset(headers[0], 0, sizeof(size_t) * 4);
	}

	/* Map base format to AFBC header layout */
	const uint32_t base_format = alloc_format & MALI_GRALLOC_INTFMT_FMT_MASK;

	/* Sub-sampled formats use layouts 1 and 5 which is index 1 in the headers array.
	 * 1 = 4:2:0 16x16, 5 = 4:2:0 32x8.
	 *
	 * Non-subsampled use layouts 0, 3, 4 and 7, which is index 0.
	 * 0 = 16x16, 3 = 32x8 + split, 4 = 32x8, 7 = 64x4.
	 *
	 * When using separated planes for YUV formats, the header layout is the non-subsampled one
	 * as there is a header per-plane and there is no sub-sampling within the plane.
	 * Separated plane only supports 32x8 or 64x4 for the luma plane, so the first plane must be 4 or 7.
	 * Seperated plane only supports 64x4 for subsequent planes, so these must be header layout 7.
	 */
	const uint32_t layout = is_subsampled_yuv(base_format) && !is_multi_plane ? 1 : 0;

	MALI_GRALLOC_LOGV("Writing AFBC header layout %d for format (%s %" PRIx32 ")",
		layout, format_name(base_format), base_format);

	for (uint32_t i = 0; i < n_headers; i++)
	{
		memcpy(buf, headers[layout], sizeof(headers[layout]));
		buf += sizeof(headers[layout]);
	}
}

/*
 * Obtain plane allocation dimensions (in pixels).
 *
 * NOTE: pixel stride, where defined for format, is
 * incorporated into allocation dimensions.
 */
static void get_pixel_w_h(uint64_t * const width,
                          uint64_t * const height,
                          const format_info_t format,
                          const alloc_type_t alloc_type,
                          const uint8_t plane,
                          bool has_cpu_usage)
{
	const rect_t sb = get_afbc_sb_size(alloc_type, plane);
	const bool is_primary_plane = (plane == 0 || !format.planes_contiguous);

	/*
	 * Round-up plane dimensions, to multiple of:
	 * - Samples for all channels (sub-sampled formats)
	 * - Memory bytes/words (some packed formats)
	 */
	if (is_primary_plane)
	{
		*width = GRALLOC_ALIGN(*width, format.align_w);
		*height = GRALLOC_ALIGN(*height, format.align_h);
	}

	/*
	 * Sub-sample (sub-sampled) planes.
	 */
	if (plane > 0)
	{
		*width /= format.hsub;
		*height /= format.vsub;
	}

	/*
	 * Pixel alignment (width),
	 * where format stride is stated in pixels.
	 */
	uint32_t pixel_align_w = 1, pixel_align_h = 1;
	if (has_cpu_usage && is_primary_plane)
	{
		pixel_align_w = format.align_w_cpu;
	}
	else if (alloc_type.is_afbc())
	{
		constexpr uint32_t HEADER_STRIDE_ALIGN_IN_SUPER_BLOCKS = 0;
		uint32_t num_sb_align = 0;
		if (alloc_type.is_padded && !format.is_yuv)
		{
			/* Align to 4 superblocks in width --> 64-byte,
			 * assuming 16-byte header per superblock.
			 */
			num_sb_align = 4;
		}
		pixel_align_w = std::max(HEADER_STRIDE_ALIGN_IN_SUPER_BLOCKS, num_sb_align) * sb.width;

		/*
		 * Determine AFBC tile size when allocating tiled headers.
		 */
		rect_t afbc_tile = sb;
		if (alloc_type.is_tiled)
		{
			afbc_tile.width = format.bpp_afbc[plane] > 32 ? 4 * afbc_tile.width : 8 * afbc_tile.width;
			afbc_tile.height = format.bpp_afbc[plane] > 32 ? 4 * afbc_tile.height : 8 * afbc_tile.height;
		}

		MALI_GRALLOC_LOGV("Plane[%hhu]: [SUB-SAMPLE] w:%" PRIu64 ", h:%" PRIu64 "\n", plane, *width, *height);
		MALI_GRALLOC_LOGV("Plane[%hhu]: [PIXEL_ALIGN] w:%d\n", plane, pixel_align_w);
		MALI_GRALLOC_LOGV("Plane[%hhu]: [LINEAR_TILE] w:%" PRIu16 "\n", plane, format.tile_size);
		MALI_GRALLOC_LOGV("Plane[%hhu]: [AFBC_TILE] w:%" PRIu64 ", h:%" PRIu64 "\n", plane, afbc_tile.width, afbc_tile.height);

		pixel_align_w = std::max(static_cast<uint64_t>(pixel_align_w), afbc_tile.width);
		pixel_align_h = std::max(static_cast<uint64_t>(pixel_align_h), afbc_tile.height);

		if (AllocBaseType::AFBC_WIDEBLK == alloc_type.primary_type && !alloc_type.is_tiled)
		{
			/*
			 * Special case for wide block (32x8) AFBC with linear (non-tiled)
			 * headers: hardware reads and writes 32x16 blocks so we need to
			 * pad the body buffer accordingly.
			 *
			 * Note that this branch will not be taken for multi-plane AFBC
			 * since that requires tiled headers.
			 */
			pixel_align_h = std::max(pixel_align_h, 16u);
		}
	}
	*width = GRALLOC_ALIGN(*width, std::max({1u, pixel_align_w, static_cast<uint32_t>(format.tile_size)}));
	*height = GRALLOC_ALIGN(*height, std::max({1u, pixel_align_h, static_cast<uint32_t>(format.tile_size)}));
}

template<typename T>
static T gcd(T a, T b)
{
	T r, t;

	if (a == b)
	{
		return a;
	}
	else if (a < b)
	{
		t = a;
		a = b;
		b = t;
	}

	while (b != 0)
	{
		r = a % b;
		a = b;
		b = r;
	}

	return a;
}

template<typename T>
T lcm(T a, T b)
{
	if (a != 0 && b != 0)
	{
		return (a * b) / gcd(a, b);
	}

	return std::max(a, b);
}


#if REALIGN_YV12 == 1
/*
 * YV12 stride has additional complexity since chroma stride
 * must conform to the following:
 *
 * c_stride = ALIGN(stride/2, 16)
 *
 * Since the stride alignment must satisfy both CPU and HW
 * constraints, the luma stride must be doubled.
 */
static void update_yv12_stride(int8_t plane,
                               size_t luma_stride,
                               uint32_t stride_align,
                               uint64_t * byte_stride)
{
	// https://developer.android.com/reference/android/graphics/ImageFormat#YV12
	if (plane == 0) {
		// stride_align has to be honored as GPU alignment still requires the format to be
		// 64 bytes aligned. Though that does not break the contract as long as the
		// horizontal stride for chroma is half the luma stride and aligned to 16.
		*byte_stride = GRALLOC_ALIGN(luma_stride, GRALLOC_ALIGN(stride_align, 16));
	} else {
		*byte_stride = GRALLOC_ALIGN(luma_stride / 2, 16);
	}
}
#endif

/*
 * Logs and returns false if deprecated usage bits are found
 *
 * At times, framework introduces new usage flags which are identical to what
 * vendor has been using internally. This method logs those bits and returns
 * true if there is any deprecated usage bit.
 */
static bool log_obsolete_usage_flags(uint64_t usage) {
	if (usage & DEPRECATED_MALI_GRALLOC_USAGE_FRONTBUFFER) {
		MALI_GRALLOC_LOGW("Using deprecated FRONTBUFFER usage bit, please upgrade to BufferUsage::FRONT_BUFFER");
		return false;
	}
	if (usage & UNSUPPORTED_MALI_GRALLOC_USAGE_CUBE_MAP) {
		MALI_GRALLOC_LOGW("BufferUsage::GPU_CUBE_MAP is unsupported");
		return false;
	}
	if (usage & UNSUPPORTED_MALI_GRALLOC_USAGE_MIPMAP_COMPLETE) {
		MALI_GRALLOC_LOGW("BufferUsage::GPU_MIPMAP_COMPLETE is unsupported");
		return false;
	}

	return true;
}

static bool validate_size(uint32_t layer_count, uint32_t width, uint32_t height) {
	// The max size of an image can be from camera (50 Megapixels) and considering the max
	// depth of 4 bytes per pixel, we get an image of size 200MB.
	// We can keep twice the margin for a max size of 400MB.
	uint64_t overflow_limit = 400 * (1 << 20);

	// Maximum 4 bytes per pixel buffers are supported (RGBA). This does not take care of
	// alignment, but 400MB is already very generous, so there should not be an issue.
	overflow_limit /= 4;
	overflow_limit /= layer_count;

	if (width > overflow_limit) {
		MALI_GRALLOC_LOGE("Parameters layer: %" PRIu32 ", width: %" PRIu32 ", height: %" PRIu32 " are too big", layer_count, width, height);
		return false;
	}
	overflow_limit /= width;

	if (height > overflow_limit) {
		MALI_GRALLOC_LOGE("Parameters layer: %" PRIu32 ", width: %" PRIu32 ", height: %" PRIu32 " are too big", layer_count, width, height);
		return false;
	}

	return true;
}

static bool validate_descriptor(buffer_descriptor_t * const bufDescriptor) {
	uint64_t usage = bufDescriptor->producer_usage | bufDescriptor->consumer_usage;
	if (!log_obsolete_usage_flags(bufDescriptor->producer_usage | bufDescriptor->consumer_usage)) {
		return false;
	}

	if (usage & INVALID_USAGE) {
		return -EINVAL;
	}

	// BLOB formats are used for some ML models whose size can be really large (up to 2GB)
	if (bufDescriptor->hal_format != MALI_GRALLOC_FORMAT_INTERNAL_BLOB &&
	    !validate_size(bufDescriptor->layer_count, bufDescriptor->width, bufDescriptor->height)) {
		return false;
	}

	return true;
}

/*
 * Modify usage flag when BO/BW is the producer (decoder) or the consumer (encoder)
 *
 * BO/BW cannot use the flags CPU_READ_RARELY as Codec layer redefines those flags
 * for some internal usage. So, when BO/BW is sending CPU_READ_OFTEN, it still
 * expects to allocate an uncached buffer and this procedure convers the OFTEN
 * flag to RARELY.
 */
static uint64_t update_usage_for_BIG(uint64_t usage) {
	MALI_GRALLOC_LOGV("Hacking CPU RW flags for BO/BW");
	if (usage & hidl_common::BufferUsage::CPU_READ_OFTEN) {
		usage &= ~(static_cast<uint64_t>(hidl_common::BufferUsage::CPU_READ_OFTEN));
		usage |= hidl_common::BufferUsage::CPU_READ_RARELY;
	}

	if (usage & hidl_common::BufferUsage::CPU_WRITE_OFTEN) {
		usage &= ~(static_cast<uint64_t>(hidl_common::BufferUsage::CPU_WRITE_OFTEN));
		usage |= hidl_common::BufferUsage::CPU_WRITE_RARELY;
	}
	return usage;
}

static void align_plane_stride(plane_info_t *plane_info, uint8_t plane, const format_info_t format, uint32_t stride_align)
{
	plane_info[plane].byte_stride = GRALLOC_ALIGN(plane_info[plane].byte_stride * format.tile_size, stride_align) / format.tile_size;
	plane_info[plane].alloc_width = plane_info[plane].byte_stride * 8 / format.bpp[plane];
}

/*
 * Calculate allocation size.
 *
 * Determine the width and height of each plane based on pixel alignment for
 * both uncompressed and AFBC allocations.
 *
 * @param width           [in]    Buffer width.
 * @param height          [in]    Buffer height.
 * @param alloc_type      [in]    Allocation type inc. whether tiled and/or multi-plane.
 * @param format          [in]    Pixel format.
 * @param has_cpu_usage   [in]    CPU usage requested (in addition to any other).
 * @param has_hw_usage    [in]    HW usage requested.
 * @param has_gpu_usage   [in]    GPU usage requested.
 * @param has_video_usage [in]    Video usage requested.
 * @param has_camera_usage[in]    Camera usage requested.
 * @param pixel_stride    [out]   Calculated pixel stride.
 * @param size            [out]   Total calculated buffer size including all planes.
 * @param plane_info      [out]   Array of calculated information for each plane. Includes
 *                                offset, byte stride and allocation width and height.
 */
static void calc_allocation_size(const int width,
                                 const int height,
                                 const alloc_type_t alloc_type,
                                 const format_info_t format,
                                 const bool has_cpu_usage,
                                 const bool has_hw_usage,
                                 const bool has_gpu_usage,
                                 const bool has_BIG_usage,
                                 const bool has_camera_usage,
                                 uint64_t * const pixel_stride,
                                 uint64_t  * const size,
                                 plane_info_t plane_info[MAX_PLANES])
{
	/* pixel_stride is set outside this function after this function is called */
	GRALLOC_UNUSED(pixel_stride);
#ifndef SOC_ZUMA
	GRALLOC_UNUSED(has_camera_usage);
#endif

	plane_info[0].offset = 0;

	*size = 0;
	for (uint8_t plane = 0; plane < format.npln; plane++)
	{
		plane_info[plane].alloc_width = width;
		plane_info[plane].alloc_height = height;
		get_pixel_w_h(&plane_info[plane].alloc_width,
		              &plane_info[plane].alloc_height,
		              format,
		              alloc_type,
		              plane,
		              has_cpu_usage);
		MALI_GRALLOC_LOGV("Aligned w=%" PRIu64 ", h=%" PRIu64 " (in pixels)",
		      plane_info[plane].alloc_width, plane_info[plane].alloc_height);

		/*
		 * Calculate byte stride (per plane).
		 */
		if (alloc_type.is_afbc())
		{
			assert((plane_info[plane].alloc_width * format.bpp_afbc[plane]) % 8 == 0);
			plane_info[plane].byte_stride = (plane_info[plane].alloc_width * format.bpp_afbc[plane]) / 8;
		}
		else
		{
			assert((plane_info[plane].alloc_width * format.bpp[plane]) % 8 == 0);
			plane_info[plane].byte_stride = (plane_info[plane].alloc_width * format.bpp[plane]) / 8;

			/*
			 * Align byte stride (uncompressed allocations only).
			 *
			 * Find the lowest-common-multiple of:
			 * 1. hw_align: Minimum byte stride alignment for HW IP (has_hw_usage == true)
			 * 2. cpu_align: Byte equivalent of 'align_w_cpu' (has_cpu_usage == true)
			 *
			 * NOTE: Pixel stride is defined as multiple of 'align_w_cpu'.
			 */
			uint32_t hw_align = 0;
			if (has_hw_usage)
			{
				static_assert(is_power2(YUV_BYTE_ALIGN_DEFAULT),
							  "YUV_BYTE_ALIGN_DEFAULT is not a power of 2");
				static_assert(is_power2(RGB_BYTE_ALIGN_DEFAULT),
							  "RGB_BYTE_ALIGN_DEFAULT is not a power of 2");

				hw_align = format.is_yuv ?
					YUV_BYTE_ALIGN_DEFAULT :
					(format.is_rgb ? RGB_BYTE_ALIGN_DEFAULT : 0);
			}

			if (has_gpu_usage)
			{
				static_assert(is_power2(GPU_BYTE_ALIGN_DEFAULT),
							  "RGB_BYTE_ALIGN_DEFAULT is not a power of 2");

				/*
				 * The GPU requires stricter alignment on YUV and raw formats.
				 */
				hw_align = std::max(hw_align, static_cast<uint32_t>(GPU_BYTE_ALIGN_DEFAULT));
			}

#ifdef SOC_ZUMA
			if (has_camera_usage && (format.id == MALI_GRALLOC_FORMAT_INTERNAL_RAW10 ||
						format.id == MALI_GRALLOC_FORMAT_INTERNAL_RAW12 ||
						format.id == MALI_GRALLOC_FORMAT_INTERNAL_RAW16)) {
				/*
				 * Camera ISP requires RAW buffers to have 32-byte aligned stride
				 */
				hw_align = std::max(hw_align, static_cast<uint32_t>(CAMERA_RAW_BUFFER_BYTE_ALIGN));
			}
#endif

			if (has_BIG_usage) {
				assert(has_hw_usage);
				hw_align = lcm(hw_align, static_cast<uint32_t>(BIG_BYTE_ALIGN_DEFAULT));
			}

			uint32_t cpu_align = 0;
			if (has_cpu_usage && format.id != MALI_GRALLOC_FORMAT_INTERNAL_RAW10) {
				assert((format.bpp[plane] * format.align_w_cpu) % 8 == 0);
				const bool is_primary_plane = (plane == 0 || !format.planes_contiguous);
				if (is_primary_plane) {
					cpu_align = (format.bpp[plane] * format.align_w_cpu) / 8;
				}
			}

			uint32_t stride_align = lcm(hw_align, cpu_align);
			if (stride_align)
			{
				align_plane_stride(plane_info, plane, format, stride_align);
			}

#if REALIGN_YV12 == 1
			/*
			 * Update YV12 stride with both CPU & HW usage due to constraint of chroma stride.
			 * Width is anyway aligned to 16px for luma and chroma (has_cpu_usage).
			 *
			 * Note: To prevent luma stride misalignment with GPU stride alignment.
			 * The luma plane will maintain the same `stride` size, and the chroma plane
			 * will align to `stride/2`.
			 */
			if (format.id == MALI_GRALLOC_FORMAT_INTERNAL_YV12 && has_hw_usage && has_cpu_usage)
			{
				update_yv12_stride(plane,
				                   plane_info[0].byte_stride,
				                   stride_align,
				                   &plane_info[plane].byte_stride);
			}
#endif
		}
		MALI_GRALLOC_LOGV("Byte stride: %" PRIu64, plane_info[plane].byte_stride);

		const uint32_t sb_num = (plane_info[plane].alloc_width * plane_info[plane].alloc_height)
		                      / AFBC_PIXELS_PER_BLOCK;

		/*
		 * Calculate body size (per plane).
		 */
		size_t body_size = 0;
		if (alloc_type.is_afbc())
		{
			const rect_t sb = get_afbc_sb_size(alloc_type, plane);
			const size_t sb_bytes = GRALLOC_ALIGN((format.bpp_afbc[plane] * sb.width * sb.height) / 8, 128);
			body_size = sb_num * sb_bytes;

			/* When AFBC planes are stored in separate buffers and this is not the last plane,
			   also align the body buffer to make the subsequent header aligned. */
			if (format.npln > 1 && plane < 2)
			{
				afbc_buffer_align(alloc_type.is_tiled, &body_size);
			}

			if (alloc_type.is_frontbuffer_safe)
			{
				size_t back_buffer_size = body_size;
				afbc_buffer_align(alloc_type.is_tiled, &back_buffer_size);
				body_size += back_buffer_size;
			}
		}
		else
		{
			if (has_BIG_usage && plane &&
			    (format.id == HAL_PIXEL_FORMAT_GOOGLE_NV12_SP ||
			     format.id == HAL_PIXEL_FORMAT_GOOGLE_NV12_SP_10B))
			{
				/* Make luma and chroma planes have the same stride. */
				plane_info[plane].byte_stride = plane_info[0].byte_stride;
			}
			body_size = plane_info[plane].byte_stride * plane_info[plane].alloc_height;
		}
		MALI_GRALLOC_LOGV("Body size: %zu", body_size);


		/*
		 * Calculate header size (per plane).
		 */
		size_t header_size = 0;
		if (alloc_type.is_afbc())
		{
			/* As this is AFBC, calculate header size for this plane.
			 * Always align the header, which will make the body buffer aligned.
			 */
			header_size = sb_num * AFBC_HEADER_BUFFER_BYTES_PER_BLOCKENTRY;
			afbc_buffer_align(alloc_type.is_tiled, &header_size);
		}
		MALI_GRALLOC_LOGV("AFBC Header size: %zu", header_size);

		/*
		 * Set offset for separate chroma planes.
		 */
		if (plane > 0)
		{
			plane_info[plane].offset = *size;
		}

		/*
		 * Set overall size.
		 * Size must be updated after offset.
		 */
		*size += body_size + header_size;
		MALI_GRALLOC_LOGV("size=%" PRIu64, *size);
	}
}



/*
 * Validate selected format against requested.
 * Return true if valid, false otherwise.
 */
static bool validate_format(const format_info_t * const format,
                            const alloc_type_t alloc_type,
                            const buffer_descriptor_t * const bufDescriptor)
{
	if (alloc_type.is_afbc())
	{
		/*
		 * Validate format is supported by AFBC specification and gralloc.
		 */
		if (format->afbc == false)
		{
			MALI_GRALLOC_LOGE("ERROR: AFBC selected but not supported for base format: (%s 0x%" PRIx32")",
				format_name(format->id), format->id);
			return false;
		}

		/*
		 * Enforce consistency between number of format planes and
		 * request for single/multi-plane AFBC.
		 */
		if (((format->npln == 1 && alloc_type.is_multi_plane) ||
		    (format->npln > 1 && !alloc_type.is_multi_plane)))
		{
			MALI_GRALLOC_LOGE("ERROR: Format ((%s %" PRIx32 "), num planes: %u) is incompatible with %s-plane AFBC request",
				format_name(format->id), format->id, format->npln, (alloc_type.is_multi_plane) ? "multi" : "single");
			return false;
		}
	}
	else
	{
		if (format->linear == false)
		{
			MALI_GRALLOC_LOGE("ERROR: Uncompressed format requested but not supported for base format: (%s %" PRIx32 ")",
				format_name(format->id), format->id);
			return false;
		}
	}

	if (format->id == MALI_GRALLOC_FORMAT_INTERNAL_BLOB &&
	    bufDescriptor->height != 1)
	{
		MALI_GRALLOC_LOGE("ERROR: Height for format BLOB must be 1.");
		return false;
	}

	return true;
}

static int prepare_descriptor_exynos_formats(
		buffer_descriptor_t *bufDescriptor,
		format_info_t format_info)
{
	int w = bufDescriptor->width;
	int h = bufDescriptor->height;
	uint64_t usage = bufDescriptor->producer_usage | bufDescriptor->consumer_usage;
	int plane_count = 2;
	int format = MALI_GRALLOC_INTFMT_FMT_MASK & bufDescriptor->alloc_format;
	int fd_count = get_exynos_fd_count(format);

	if (usage & (GRALLOC_USAGE_HW_VIDEO_ENCODER | GRALLOC_USAGE_HW_VIDEO_DECODER))
	{
		usage |= GRALLOC_USAGE_VIDEO_PRIVATE_DATA;
		bufDescriptor->producer_usage |= GRALLOC_USAGE_VIDEO_PRIVATE_DATA;
		bufDescriptor->consumer_usage |= GRALLOC_USAGE_VIDEO_PRIVATE_DATA;
	}

	/* SWBC Formats have special size requirements */
	switch (format)
	{
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC:
		case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_SBWC:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC:
			plane_count = setup_sbwc_420_sp(w, h, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC:
		case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_10B_SBWC:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC:
			plane_count = setup_sbwc_420_sp_10bit(w, h, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L50:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L50:
			plane_count = setup_sbwc_420_sp_lossy(w, h, 50, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L75:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L75:
			plane_count = setup_sbwc_420_sp_lossy(w, h, 75, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L40:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L40:
			plane_count = setup_sbwc_420_sp_10bit_lossy(w, h, 40, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L60:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L60:
			plane_count = setup_sbwc_420_sp_10bit_lossy(w, h, 60, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L80:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L80:
			plane_count = setup_sbwc_420_sp_10bit_lossy(w, h, 80, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_YCrCb_420_SP:
			h = GRALLOC_ALIGN(h, 2);
			plane_count = setup_420_sp(w, h, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YV12_M:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P_M:
			w = GRALLOC_ALIGN(w, 32);
			h = GRALLOC_ALIGN(h, 16);
			plane_count = setup_420_p(w, h, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_TILED:
			w = GRALLOC_ALIGN(w, 16);
			h = GRALLOC_ALIGN(h, 32);
			plane_count = setup_420_sp_tiled(w, h, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P:
			w = GRALLOC_ALIGN(w, 16);
			plane_count = setup_420_p(w, h, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M:
		case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M:
			w = GRALLOC_ALIGN(w, 16);
			h = GRALLOC_ALIGN(h, 32);
			plane_count = setup_420_sp(w, h, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN:
			w = GRALLOC_ALIGN(w, 64);
			h = GRALLOC_ALIGN(h, 16);
			plane_count = setup_420_sp(w, h, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B:
			/* This is 64 pixel align for now */
			w = GRALLOC_ALIGN(w, BOARD_EXYNOS_S10B_FORMAT_ALIGN);
			h = GRALLOC_ALIGN(h, 16);
			plane_count = setup_420_sp_s10b(w, h, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_S10B:
			w = GRALLOC_ALIGN(w, BOARD_EXYNOS_S10B_FORMAT_ALIGN);
			h = GRALLOC_ALIGN(h, 16);
			plane_count = setup_420_sp_s10b(w, h, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_M:
			w = GRALLOC_ALIGN(w, 16);
			h = GRALLOC_ALIGN(h, 16);
			plane_count = setup_p010_sp(w, h, fd_count, bufDescriptor->plane_info);
			break;

		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_SPN:
			w = GRALLOC_ALIGN(w, 64);
			h = GRALLOC_ALIGN(h, 16);
			plane_count = setup_p010_sp(w, h, fd_count, bufDescriptor->plane_info);
			break;

		default:
			MALI_GRALLOC_LOGE("invalid yuv format (%s %" PRIx64 ")", format_name(bufDescriptor->alloc_format),
				bufDescriptor->alloc_format);
			return -1;
	}

	plane_info_t *plane = bufDescriptor->plane_info;

	if (usage & (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_RENDER | GRALLOC_USAGE_GPU_DATA_BUFFER))
	{
		if (is_sbwc_format(format))
		{
			MALI_GRALLOC_LOGE("using SBWC format (%s %" PRIx64 ") with GPU is invalid",
							  format_name(bufDescriptor->alloc_format),
							  bufDescriptor->alloc_format);
			return -1;
		}
		else
		{
			/*
			 * The GPU requires stricter alignment on YUV formats.
			 */
			for (int pidx = 0; pidx < plane_count; ++pidx)
			{
				if (plane[pidx].size == plane[pidx].byte_stride * plane[pidx].alloc_height)
				{
					align_plane_stride(plane, pidx, format_info, GPU_BYTE_ALIGN_DEFAULT);
					plane[pidx].size = plane[pidx].byte_stride * plane[pidx].alloc_height;
				}
				else
				{
					MALI_GRALLOC_LOGE("buffer with format (%s %" PRIx64
									  ") has size %" PRIu64
									  " != byte_stride %" PRIu64 " * alloc_height %" PRIu64,
									  format_name(bufDescriptor->alloc_format),
									  bufDescriptor->alloc_format,
									  plane[pidx].size, plane[pidx].byte_stride, plane[pidx].alloc_height);
				}
			}
		}
	}

	for (int fidx = 0; fidx < fd_count; fidx++)
	{
		uint64_t size = 0;

		for (int pidx = 0; pidx < plane_count; pidx++)
		{
			if (plane[pidx].fd_idx == fidx)
			{
				size += plane[pidx].size;
			}
		}

		/* TODO(b/183073089): Removing the following size hacks make video playback
		 * fail. Need to investigate more for the root cause. Copying the original
		 * comment from upstream below */
		/* is there a need to check the condition for padding like in older gralloc? */
		/* Add MSCL_EXT_SIZE */
		/* MSCL_EXT_SIZE + MSCL_EXT_SIZE/2 + ext_size */
		size += 1024;

		size = size < SZ_4K ? SZ_4K : size;

		bufDescriptor->alloc_sizes[fidx] = size;
	}

	bufDescriptor->fd_count = fd_count;
	bufDescriptor->plane_count = plane_count;

	return 0;
}

int mali_gralloc_derive_format_and_size(buffer_descriptor_t * const bufDescriptor)
{
	ATRACE_CALL();
	alloc_type_t alloc_type{};

	uint64_t alloc_width = bufDescriptor->width;
	uint64_t alloc_height = bufDescriptor->height;
	uint64_t usage = bufDescriptor->producer_usage | bufDescriptor->consumer_usage;

	if (!validate_descriptor(bufDescriptor)) {
		return -EINVAL;
	}
	/*
	* Select optimal internal pixel format based upon
	* usage and requested format.
	*/
	bufDescriptor->alloc_format = mali_gralloc_select_format(bufDescriptor->hal_format,
	                                                         bufDescriptor->format_type,
	                                                         usage);

	int base_format = bufDescriptor->alloc_format & MALI_GRALLOC_INTFMT_FMT_MASK;

	// TODO(b/182885532): Delete all multi-fd related dead code from gralloc
	if (is_exynos_format(base_format) && get_exynos_fd_count(base_format) != 1)
	{
		static std::set<uint32_t> seen_formats;
		if (seen_formats.find(base_format) == seen_formats.end()) {
			MALI_GRALLOC_LOGW("Multi-fd format (%s 0x%" PRIx64 ") have been deprecated. Requested format: %s 0x%" PRIx64
					". Consider changing the format to one of the single-fd options.",
					format_name(base_format), static_cast<uint64_t>(base_format),
					format_name(bufDescriptor->hal_format), bufDescriptor->hal_format);
			seen_formats.insert(base_format);
		}
	}

	if (bufDescriptor->alloc_format == MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED)
	{
		MALI_GRALLOC_LOGE("ERROR: Unrecognized and/or unsupported format (%s 0x%" PRIx64 ") and usage (%s 0x%" PRIx64 ")",
				format_name(bufDescriptor->hal_format), bufDescriptor->hal_format,
				describe_usage(usage).c_str(), usage);
		return -EINVAL;
	}

	int32_t format_idx = get_format_index(base_format);
	if (format_idx == -1)
	{
		return -EINVAL;
	}
	MALI_GRALLOC_LOGV("alloc_format: (%s 0x%" PRIx64 ") format_idx: %d",
		format_name(bufDescriptor->alloc_format), bufDescriptor->alloc_format, format_idx);

	/*
	 * Obtain allocation type (uncompressed, AFBC basic, etc...)
	 */
	if (!get_alloc_type(bufDescriptor->alloc_format & MALI_GRALLOC_INTFMT_EXT_MASK,
	    format_idx, usage, &alloc_type))
	{
		return -EINVAL;
	}

	if (!validate_format(&formats[format_idx], alloc_type, bufDescriptor))
	{
		return -EINVAL;
	}

	if (is_exynos_format(base_format))
	{
		prepare_descriptor_exynos_formats(bufDescriptor, formats[format_idx]);
	}
	else
	{
		/*
		 * Resolution of frame (allocation width and height) might require adjustment.
		 * This adjustment is only based upon specific usage and pixel format.
		 * If using AFBC, further adjustments to the allocation width and height will be made later
		 * based on AFBC alignment requirements and, for YUV, the plane properties.
		 */
		mali_gralloc_adjust_dimensions(bufDescriptor->alloc_format,
		                               usage,
		                               &alloc_width,
		                               &alloc_height);

		/* Obtain buffer size and plane information. */
		calc_allocation_size(alloc_width,
		                     alloc_height,
		                     alloc_type,
		                     formats[format_idx],
		                     usage & (GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK),
		                     usage & ~(GRALLOC_USAGE_PRIVATE_MASK | GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK),
		                     usage & (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_RENDER | GRALLOC_USAGE_GPU_DATA_BUFFER),
		                     (usage & (GRALLOC_USAGE_HW_VIDEO_ENCODER | GRALLOC_USAGE_HW_VIDEO_DECODER)) && (usage & GRALLOC_USAGE_GOOGLE_IP_BIG),
		                     usage & (GRALLOC_USAGE_HW_CAMERA_WRITE | GRALLOC_USAGE_HW_CAMERA_READ),
		                     &bufDescriptor->pixel_stride,
		                     &bufDescriptor->alloc_sizes[0],
		                     bufDescriptor->plane_info);
	}

	/* Set pixel stride differently for RAW formats */
	switch (base_format)
	{
		case MALI_GRALLOC_FORMAT_INTERNAL_RAW12:
		case MALI_GRALLOC_FORMAT_INTERNAL_RAW10:
			bufDescriptor->pixel_stride = bufDescriptor->plane_info[0].byte_stride;
			break;
		default:
			bufDescriptor->pixel_stride = bufDescriptor->plane_info[0].alloc_width;
	}

	/*
	 * Each layer of a multi-layer buffer must be aligned so that
	 * it is accessible by both producer and consumer. In most cases,
	 * the stride alignment is also sufficient for each layer, however
	 * for AFBC the header buffer alignment is more constrained (see
	 * AFBC specification v3.4, section 2.15: "Alignment requirements").
	 * Also update the buffer size to accommodate all layers.
	 */
	if (bufDescriptor->layer_count > 1)
	{
		if (bufDescriptor->alloc_format & MALI_GRALLOC_INTFMT_AFBCENABLE_MASK)
		{
			if (bufDescriptor->alloc_format & MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS)
			{
				bufDescriptor->alloc_sizes[0] = GRALLOC_ALIGN(bufDescriptor->alloc_sizes[0], 4096);
			}
			else
			{
				bufDescriptor->alloc_sizes[0] = GRALLOC_ALIGN(bufDescriptor->alloc_sizes[0], 128);
			}
		}

		bufDescriptor->alloc_sizes[0] *= bufDescriptor->layer_count;
	}

	/* MFC requires EXT_SIZE padding */
	bufDescriptor->alloc_sizes[0] += EXT_SIZE;

	if ((usage & GRALLOC_USAGE_HW_VIDEO_ENCODER) && (usage & GRALLOC_USAGE_GOOGLE_IP_BW))
	{
		/* BW HW requires extra padding bytes */
		bufDescriptor->alloc_sizes[0] += BW_EXT_SIZE;
	}

	return 0;
}

int mali_gralloc_buffer_allocate(const gralloc_buffer_descriptor_t *descriptors,
                                 uint32_t numDescriptors, buffer_handle_t *pHandle, bool *shared_backend,
                                 bool use_placeholder)
{
	std::string atrace_log = __FUNCTION__;
	if (ATRACE_ENABLED()) {
		buffer_descriptor_t * const bufDescriptor = (buffer_descriptor_t *)(descriptors[0]);
		std::stringstream ss;
		ss << __FUNCTION__ << "(f=0x" << std::hex << bufDescriptor->hal_format << ", u=0x" <<
			bufDescriptor->producer_usage << ", w=" << std::dec << bufDescriptor->width << ", h=" << bufDescriptor->height << ")";
		atrace_log = ss.str();
	}
	ATRACE_NAME(atrace_log.c_str());

	bool shared = false;
	uint64_t backing_store_id = 0x0;
	int err;

	for (uint32_t i = 0; i < numDescriptors; i++)
	{
		buffer_descriptor_t * const bufDescriptor = (buffer_descriptor_t *)(descriptors[i]);

		assert(bufDescriptor->producer_usage == bufDescriptor->consumer_usage);
		uint64_t usage = bufDescriptor->producer_usage;
		if (((usage & hidl_common::BufferUsage::VIDEO_DECODER)||(usage & hidl_common::BufferUsage::VIDEO_ENCODER)) &&
		    (usage & GRALLOC_USAGE_GOOGLE_IP_BIG))
		{
			usage = update_usage_for_BIG(usage);
			bufDescriptor->producer_usage = usage;
			bufDescriptor->consumer_usage = usage;
		}

		/* Derive the buffer size from descriptor parameters */
		err = mali_gralloc_derive_format_and_size(bufDescriptor);
		if (err != 0)
		{
			return err;
		}
	}

	/* Allocate ION backing store memory */
	err = mali_gralloc_ion_allocate(descriptors, numDescriptors, pHandle, &shared, use_placeholder);
	if (err < 0)
	{
		return err;
	}

	if (shared)
	{
		backing_store_id = getUniqueId();
	}

	for (uint32_t i = 0; i < numDescriptors; i++)
	{
		private_handle_t *hnd = (private_handle_t *)pHandle[i];

		if (shared)
		{
			/*each buffer will share the same backing store id.*/
			hnd->backing_store_id = backing_store_id;
		}
		else
		{
			/* each buffer will have an unique backing store id.*/
			hnd->backing_store_id = getUniqueId();
		}
	}

	if (NULL != shared_backend)
	{
		*shared_backend = shared;
	}

	return 0;
}

int mali_gralloc_buffer_free(buffer_handle_t pHandle)
{
	auto *hnd = const_cast<private_handle_t *>(
	    reinterpret_cast<const private_handle_t *>(pHandle));

	if (hnd == nullptr)
	{
		return -1;
	}

	native_handle_close(hnd);
	native_handle_delete(hnd);

	return 0;
}
