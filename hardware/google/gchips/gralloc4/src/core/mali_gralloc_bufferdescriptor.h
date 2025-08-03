/*
 * Copyright (C) 2016-2020 Arm Limited. All rights reserved.
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

#ifndef MALI_GRALLOC_BUFFERDESCRIPTOR_H_
#define MALI_GRALLOC_BUFFERDESCRIPTOR_H_

#include "mali_gralloc_buffer.h"
#include "mali_gralloc_formats.h"
#include <string>

typedef uint64_t gralloc_buffer_descriptor_t;

std::string describe_usage(uint64_t usage);

/* A buffer_descriptor contains the requested parameters for the buffer
 * as well as the calculated parameters that are passed to the allocator.
 */
struct buffer_descriptor_t
{
	/* For validation. */
	uint32_t signature;

	/* Requested parameters from IAllocator. */
	uint32_t width;
	uint32_t height;
	uint64_t producer_usage;
	uint64_t consumer_usage;
	uint64_t hal_format;
	uint32_t layer_count;
	mali_gralloc_format_type format_type;
	std::string name;
	uint64_t reserved_size;

	/*
	 * Calculated values that will be passed to the allocator in order to
	 * allocate the buffer.
	 */
	uint64_t alloc_sizes[MAX_PLANES];
	uint64_t pixel_stride;
	uint64_t alloc_format;
	uint32_t fd_count;
	uint32_t plane_count;
	plane_info_t plane_info[MAX_PLANES];

	buffer_descriptor_t() :
	    signature(0),
	    width(0),
	    height(0),
	    producer_usage(0),
	    consumer_usage(0),
	    hal_format(0),
	    layer_count(0),
	    format_type(MALI_GRALLOC_FORMAT_TYPE_USAGE),
	    reserved_size(0),
	    pixel_stride(0),
	    alloc_format(0),
	    fd_count(1),
	    plane_count(1)
	{
		memset(plane_info, 0, sizeof(plane_info_t) * MAX_PLANES);
		memset(alloc_sizes, 0, sizeof(alloc_sizes));
	}

	void dump(const std::string &str) const {
		ALOGI("buffer_descriptor: %s "
			"wh(%u %u) "
			"usage_pc(%s 0x%" PRIx64 " %s 0x%" PRIx64 ") "
			"hal_format(0x%" PRIx64 ") "
			"layer_count(%u) "
			"format_type(%u) "
			"name(%s)"
			"reserved_size(%" PRIu64 ") "
			"alloc_sizes(%" PRIu64 ", %" PRIu64 ", %" PRIu64 ")"
			"pixel_stride(%" PRIu64 ") alloc_format(0x%" PRIx64 ") fd_count(%d) "
			"plane_count(%u) "
			"plane[0](offset %" PRId64 ", idx %u, size %" PRIu64 " byte_stride %" PRIu64 ", wh %" PRIu64 " %" PRIu64 ")"
			"plane[1](offset %" PRId64 ", idx %u, size %" PRIu64 " byte_stride %" PRIu64 ", wh %" PRIu64 " %" PRIu64 ")"
			"plane[2](offset %" PRId64 ", idx %u, size %" PRIu64 " byte_stride %" PRIu64 ", wh %" PRIu64 " %" PRIu64 ")"
			"\n",
			str.c_str(),
			width, height,
			describe_usage(producer_usage).c_str(), producer_usage,
			describe_usage(consumer_usage).c_str(), consumer_usage,
			hal_format,
			layer_count,
			format_type,
			name.c_str(),
			reserved_size,
			alloc_sizes[0], alloc_sizes[1], alloc_sizes[2],
			pixel_stride, alloc_format, fd_count,
			plane_count,
			plane_info[0].offset, plane_info[0].fd_idx, plane_info[0].size, plane_info[0].byte_stride, plane_info[0].alloc_width, plane_info[0].alloc_height,
			plane_info[1].offset, plane_info[1].fd_idx, plane_info[1].size, plane_info[1].byte_stride, plane_info[1].alloc_width, plane_info[1].alloc_height,
			plane_info[2].offset, plane_info[2].fd_idx, plane_info[2].size, plane_info[2].byte_stride, plane_info[2].alloc_width, plane_info[2].alloc_height
		);
	}
};

#endif /* MALI_GRALLOC_BUFFERDESCRIPTOR_H_ */
