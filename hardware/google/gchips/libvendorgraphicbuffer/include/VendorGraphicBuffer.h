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

#ifndef VENDOR_GRAPHIC_BUFFER_H_
#define VENDOR_GRAPHIC_BUFFER_H_

#include <cstdint>
#include <ui/GraphicTypes.h>
#include <ui/GraphicBufferMapper.h>
#include <ui/GraphicBufferAllocator.h>
#include <ui/Rect.h>
#include <ui/PixelFormat.h>

/* Gralloc1 usages enum is provided here to ensure backward compatibility
 * This enum will be deprecated in Android S so please consider switching to
 * BufferUsage::<USAGE> found in
 * hardware/interfaces/1.0(or 1.1, 1.2)/types.hal
 */
#include <hardware/gralloc1.h>

namespace vendor {
namespace graphics {

/* Android default usages */
typedef android::hardware::graphics::common::V1_2::BufferUsage BufferUsage;

enum VendorGraphicBufferUsage {
/* S.LSI specific usages */
	NO_AFBC                         = 1ULL << 29,
	MFC                             = 1ULL << 50,  /* identical to GRALLOC_USAGE_GOOGLE_IP_MFC */
	BW                              = 1ULL << 51,  /* identical to GRALLOC_USAGE_GOOGLE_IP_BW */
	ROIINFO                         = 1ULL << 52,
	AFBC_PADDING                    = 1ULL << 53,
	FORCE_BACKBUFFER                = 1ULL << 54,
	FRONTBUFFER                     = 1ULL << 55,
	NOZEROED                        = 1ULL << 58,
	PRIVATE_NONSECURE               = 1ULL << 59,
	VIDEO_PRIVATE_DATA              = 1ULL << 60,
/* Google-specific usages */
	CAMERA_STATS                    = 1ULL << 30,
	TPU_OUTPUT                      = 1ULL << 31,
	TPU_INPUT                       = 1ULL << 62
};


/* A wrapper class to gralloc private handle */
class VendorGraphicBufferMeta {
private:
	VendorGraphicBufferMeta() {};

public:
	enum
	{
		PRIV_FLAGS_USES_2PRIVATE_DATA = 1U << 4,
		PRIV_FLAGS_USES_3PRIVATE_DATA = 1U << 5,
	};

	union {
		int fds[3];

		struct {
			int fd  = -1;
			int fd1 = -1;
			int fd2 = -1;
		};
	};

	union {
		int sizes[3];

		struct {
			int size  = 0;
			int size1 = 0;
			int size2 = 0;
		};
	};

	union {
		uint32_t offsets[3];

		struct {
			uint32_t offset  = 0;
			uint32_t offset1 = 0;
			uint32_t offset2 = 0;
		};
	};

	union
	{
		uint32_t format;
		uint64_t internal_format = 0llu;
	};
	int frameworkFormat = 0;

	int width  = 0;
	int height = 0;
	uint32_t stride  = 0;
	uint32_t vstride = 0;

	uint64_t producer_usage = 0llu;
	uint64_t consumer_usage = 0llu;

	int flags = 0;

	uint64_t unique_id = 0;

	VendorGraphicBufferMeta(const buffer_handle_t handle);

	void init(const buffer_handle_t handle);

	static int get_fd(buffer_handle_t, int num);
	static int get_size(buffer_handle_t, int num);
	static uint32_t get_format(buffer_handle_t);
	static uint64_t get_internal_format(buffer_handle_t);
	static uint64_t get_frameworkFormat(buffer_handle_t);
	static int get_width(buffer_handle_t);
	static int get_height(buffer_handle_t);
	static uint32_t get_stride(buffer_handle_t);
	static uint32_t get_stride_in_bytes(buffer_handle_t);
	static uint32_t get_vstride(buffer_handle_t);
	static uint64_t get_producer_usage(buffer_handle_t);
	static uint64_t get_consumer_usage(buffer_handle_t);
	static uint64_t get_flags(buffer_handle_t);

	static uint64_t get_usage(buffer_handle_t);
	static int is_afbc(buffer_handle_t);
	static int is_sbwc(buffer_handle_t);
	static void* get_video_metadata(buffer_handle_t);

	static uint32_t get_format_fourcc(buffer_handle_t);
	static uint64_t get_format_modifier(buffer_handle_t);

	/* get_video_metadata_roiinfo is only supported with gralloc4
	 * When gralloc3 is used, will always return nullptr
	 */
	static void* get_video_metadata_roiinfo(buffer_handle_t);
	static int get_dataspace(buffer_handle_t);
	static int set_dataspace(buffer_handle_t hnd, android_dataspace_t dataspace);

	// There should be no users of this function. It'll generate a trap.
	static int get_video_metadata_fd(buffer_handle_t);

	static buffer_handle_t import_buffer(buffer_handle_t);
	static int free_buffer(buffer_handle_t);
};

typedef class android::GraphicBufferMapper VendorGraphicBufferMapper;
typedef class android::GraphicBufferAllocator VendorGraphicBufferAllocator;

} /* namespace graphics */
} /* namespace vendor */

#endif /* GRALLOC_PRIV_INTERFACE_H_ */
