/*
 * Copyright (C) 2020 Google Inc. All rights reserved.
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

#include "mali_gralloc_bufferdescriptor.h"
#include "mali_gralloc_usages.h"

#include <sstream>

using android::hardware::graphics::common::V1_2::BufferUsage;

#define BUFFERUSAGE(n)     { static_cast<uint64_t>(BufferUsage::n), #n }
#define USAGE(prefix, n)   { prefix ## n, #n }
static struct usage_name {
	uint64_t usage;
	const char *name;
} usage_names[] = {
// graphics common v1.0 usages
	BUFFERUSAGE(GPU_TEXTURE),
	BUFFERUSAGE(GPU_RENDER_TARGET),
	BUFFERUSAGE(COMPOSER_OVERLAY),
	BUFFERUSAGE(COMPOSER_CLIENT_TARGET),
	BUFFERUSAGE(PROTECTED),
	BUFFERUSAGE(COMPOSER_CURSOR),
	BUFFERUSAGE(VIDEO_ENCODER),
	BUFFERUSAGE(CAMERA_OUTPUT),
	BUFFERUSAGE(CAMERA_INPUT),
	BUFFERUSAGE(RENDERSCRIPT),
	BUFFERUSAGE(VIDEO_DECODER),
	BUFFERUSAGE(SENSOR_DIRECT_DATA),
	BUFFERUSAGE(GPU_DATA_BUFFER),
// graphics common v1.1 usages
	BUFFERUSAGE(GPU_CUBE_MAP),
	BUFFERUSAGE(GPU_MIPMAP_COMPLETE),
// graphics common v1.2 usages
	BUFFERUSAGE(HW_IMAGE_ENCODER),
// Google usages
	USAGE(GRALLOC_USAGE_, GOOGLE_IP_BIG),
	USAGE(GRALLOC_USAGE_, GOOGLE_IP_MFC),
	USAGE(GS101_GRALLOC_USAGE_, TPU_INPUT),
	USAGE(GS101_GRALLOC_USAGE_, TPU_OUTPUT),
	USAGE(GS101_GRALLOC_USAGE_, CAMERA_STATS),
// Exynos specific usages
	USAGE(GRALLOC_USAGE_, PRIVATE_NONSECURE),
	USAGE(GRALLOC_USAGE_, NOZEROED),
	USAGE(GRALLOC_USAGE_, VIDEO_PRIVATE_DATA),
};

std::string describe_usage(uint64_t usage)
{
	std::ostringstream stream;
	switch (static_cast<BufferUsage>(usage & BufferUsage::CPU_READ_MASK)) {
		case BufferUsage::CPU_READ_NEVER:
			stream << "CPU_READ_NEVER";
			break;
		case BufferUsage::CPU_READ_RARELY:
			stream << "CPU_READ_RARELY";
			break;
		case BufferUsage::CPU_READ_OFTEN:
			stream << "CPU_READ_OFTEN";
			break;
		default:
			stream << "<unknown CPU read value 0x" << std::hex << (usage & 0x0full) << ">";
			break;
	}
	stream << "|";
	switch (static_cast<BufferUsage>(usage & BufferUsage::CPU_WRITE_MASK)) {
		case BufferUsage::CPU_WRITE_NEVER:
			stream << "CPU_WRITE_NEVER";
			break;
		case BufferUsage::CPU_WRITE_RARELY:
			stream << "CPU_WRITE_RARELY";
			break;
		case BufferUsage::CPU_WRITE_OFTEN:
			stream << "CPU_WRITE_OFTEN";
			break;
		default:
			stream << "<unknown CPU write value 0x" << std::hex << (usage & 0xf0ull) << ">";
			break;
	}
	usage &= ~(0xffull);
	for (uint64_t i = 0;
		 i < (sizeof(usage_names) / sizeof(usage_name)) && usage;
		 ++i)
	{
		if ((usage & usage_names[i].usage) == usage_names[i].usage)
		{
			usage = usage & (~usage_names[i].usage);
			stream << "|";
			stream << usage_names[i].name;
		}
	}
	if (usage) {
		stream << std::dec;
		for (uint64_t i = 0; (i < 64) && usage; ++i)
		{
			if (usage & (1 << i))
			{
				stream << "|(1<<" << i << ")";
				usage &= ~(1 << i);
			}
		}
	}
	return stream.str();
}
