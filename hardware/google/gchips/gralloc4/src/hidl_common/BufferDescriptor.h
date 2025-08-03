/*
 * Copyright (C) 2020 ARM Limited. All rights reserved.
 *
 * Copyright 2016 The Android Open Source Project
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
#ifndef _GRALLOC_BUFFER_DESCRIPTOR_H_
#define _GRALLOC_BUFFER_DESCRIPTOR_H_

#include "core/mali_gralloc_bufferdescriptor.h"
#include "hidl_common.h"

#include <assert.h>
#include <inttypes.h>
#include <string.h>

namespace arm {
namespace mapper {
namespace common {

const size_t DESCRIPTOR_32BIT_FIELDS = 5;
const size_t DESCRIPTOR_64BIT_FIELDS = 2;

const uint64_t validUsageBits =
		static_cast<uint64_t>(BufferUsage::GPU_CUBE_MAP) |
		static_cast<uint64_t>(BufferUsage::GPU_MIPMAP_COMPLETE) |
		static_cast<uint64_t>(BufferUsage::CPU_READ_MASK) | static_cast<uint64_t>(BufferUsage::CPU_WRITE_MASK) |
		static_cast<uint64_t>(BufferUsage::GPU_TEXTURE) | static_cast<uint64_t>(BufferUsage::GPU_RENDER_TARGET) |
		static_cast<uint64_t>(BufferUsage::COMPOSER_OVERLAY) | static_cast<uint64_t>(BufferUsage::COMPOSER_CLIENT_TARGET) |
		static_cast<uint64_t>(BufferUsage::CAMERA_INPUT) | static_cast<uint64_t>(BufferUsage::CAMERA_OUTPUT) |
		static_cast<uint64_t>(BufferUsage::PROTECTED) |
		static_cast<uint64_t>(BufferUsage::COMPOSER_CURSOR) |
		static_cast<uint64_t>(BufferUsage::VIDEO_ENCODER) |
		static_cast<uint64_t>(BufferUsage::RENDERSCRIPT) |
		static_cast<uint64_t>(BufferUsage::VIDEO_DECODER) |
		static_cast<uint64_t>(BufferUsage::SENSOR_DIRECT_DATA) |
		static_cast<uint64_t>(BufferUsage::GPU_DATA_BUFFER) |
		static_cast<uint64_t>(BufferUsage::VENDOR_MASK) |
		static_cast<uint64_t>(BufferUsage::VENDOR_MASK_HI);

template<typename BufferDescriptorInfoT>
static bool validateDescriptorInfo(const BufferDescriptorInfoT &descriptorInfo)
{
	if (descriptorInfo.width == 0 || descriptorInfo.height == 0 || descriptorInfo.layerCount == 0)
	{
		return false;
	}

	if (static_cast<int32_t>(descriptorInfo.format) == 0)
	{
		return false;
	}

	return true;
}

template <typename vecT>
static void push_descriptor_uint32(frameworks_vec<vecT> *vec, size_t *pos, uint32_t val)
{
	static_assert(sizeof(val) % sizeof(vecT) == 0, "Unsupported vector type");
	memcpy(vec->data() + *pos, &val, sizeof(val));
	*pos += sizeof(val) / sizeof(vecT);
}

template <typename vecT>
static uint32_t pop_descriptor_uint32(const frameworks_vec<vecT> &vec, size_t *pos)
{
	uint32_t val;
	static_assert(sizeof(val) % sizeof(vecT) == 0, "Unsupported vector type");
	memcpy(&val, vec.data() + *pos, sizeof(val));
	*pos += sizeof(val) / sizeof(vecT);
	return val;
}

template <typename vecT>
static void push_descriptor_uint64(frameworks_vec<vecT> *vec, size_t *pos, uint64_t val)
{
	static_assert(sizeof(val) % sizeof(vecT) == 0, "Unsupported vector type");
	memcpy(vec->data() + *pos, &val, sizeof(val));
	*pos += sizeof(val) / sizeof(vecT);
}

template <typename vecT>
static uint64_t pop_descriptor_uint64(const frameworks_vec<vecT> &vec, size_t *pos)
{
	uint64_t val;
	static_assert(sizeof(val) % sizeof(vecT) == 0, "Unsupported vector type");
	memcpy(&val, vec.data() + *pos, sizeof(val));
	*pos += sizeof(val) / sizeof(vecT);
	return val;
}

// There can only be one string at the end of the descriptor
static void push_descriptor_string(frameworks_vec<uint8_t> *vec, size_t *pos, const std::string &str)
{
	strcpy(reinterpret_cast<char *>(vec->data() + *pos), str.c_str());
	*pos += strlen(str.c_str()) + 1;
}

static std::string pop_descriptor_string(const frameworks_vec<uint8_t> &vec, size_t *pos)
{
	const char* charstr = reinterpret_cast<const char *>(vec.data() + *pos);
	charstr += '\0';
	std::string str(charstr);
	str.resize(strlen(charstr));
	return str;
}

#if defined(GRALLOC_MAPPER_4)
template <typename vecT, typename BufferDescriptorInfoT>
static const frameworks_vec<vecT> grallocEncodeBufferDescriptor(const BufferDescriptorInfoT &descriptorInfo)
{
	frameworks_vec<vecT> descriptor;

	static_assert(sizeof(uint32_t) % sizeof(vecT) == 0, "Unsupported vector type");
	size_t dynamic_size = 0;
	constexpr size_t static_size = (DESCRIPTOR_32BIT_FIELDS * sizeof(uint32_t) / sizeof(vecT)) +
	                               (DESCRIPTOR_64BIT_FIELDS * sizeof(uint64_t) / sizeof(vecT));

	/* Include the name and '\0' in the descriptor. */
	dynamic_size += strlen(descriptorInfo.name.c_str()) + 1;

	size_t pos = 0;
	descriptor.resize(dynamic_size + static_size);
	push_descriptor_uint32(&descriptor, &pos, HIDL_MAPPER_VERSION_SCALED / 10);
	push_descriptor_uint32(&descriptor, &pos, descriptorInfo.width);
	push_descriptor_uint32(&descriptor, &pos, descriptorInfo.height);
	push_descriptor_uint32(&descriptor, &pos, descriptorInfo.layerCount);
	push_descriptor_uint32(&descriptor, &pos, static_cast<uint32_t>(descriptorInfo.format));
	push_descriptor_uint64(&descriptor, &pos, static_cast<uint64_t>(descriptorInfo.usage));

	push_descriptor_uint64(&descriptor, &pos, descriptorInfo.reservedSize);

	assert(pos == static_size);

	push_descriptor_string(&descriptor, &pos, descriptorInfo.name);

	return descriptor;
}

#endif // GRALLOC_MAPPER_4

template <typename vecT>
static bool grallocDecodeBufferDescriptor(const frameworks_vec<vecT> &androidDescriptor, buffer_descriptor_t &grallocDescriptor)
{
	static_assert(sizeof(uint32_t) % sizeof(vecT) == 0, "Unsupported vector type");
	size_t pos = 0;

	if (((DESCRIPTOR_32BIT_FIELDS * sizeof(uint32_t) / sizeof(vecT)) +
	     (DESCRIPTOR_64BIT_FIELDS * sizeof(uint64_t) / sizeof(vecT))) +
	     sizeof('\0') > androidDescriptor.size())
	{
		MALI_GRALLOC_LOGE("Descriptor is too small");
		return false;
	}

	if (static_cast<char>(androidDescriptor[androidDescriptor.size() - 1]) != '\0') {
		MALI_GRALLOC_LOGE("Descriptor does not contain an ending null character");
		return false;
	}

	if (pop_descriptor_uint32(androidDescriptor, &pos) != HIDL_MAPPER_VERSION_SCALED / 10)
	{
		MALI_GRALLOC_LOGE("Corrupted buffer version in descriptor = %p, pid = %d ", &androidDescriptor, getpid());
		return false;
	}

	grallocDescriptor.width = pop_descriptor_uint32(androidDescriptor, &pos);
	grallocDescriptor.height = pop_descriptor_uint32(androidDescriptor, &pos);
	grallocDescriptor.layer_count = pop_descriptor_uint32(androidDescriptor, &pos);
	grallocDescriptor.hal_format = static_cast<uint64_t>(pop_descriptor_uint32(androidDescriptor, &pos));
	grallocDescriptor.producer_usage = pop_descriptor_uint64(androidDescriptor, &pos);
	grallocDescriptor.consumer_usage = grallocDescriptor.producer_usage;
	grallocDescriptor.format_type = MALI_GRALLOC_FORMAT_TYPE_USAGE;
	grallocDescriptor.signature = sizeof(buffer_descriptor_t);
	grallocDescriptor.reserved_size = pop_descriptor_uint64(androidDescriptor, &pos);
	grallocDescriptor.name = pop_descriptor_string(androidDescriptor, &pos);

	return true;
}

} // namespace common
} // namespace mapper
} // namespace arm
#endif
