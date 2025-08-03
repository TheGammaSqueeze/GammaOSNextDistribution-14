/*
 * Copyright (C) 2020 Arm Limited. All rights reserved.
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

#ifndef GRALLOC_COMMON_MAPPER_METADATA_H
#define GRALLOC_COMMON_MAPPER_METADATA_H

#include <inttypes.h>
#include "mali_gralloc_log.h"
#include "core/mali_gralloc_bufferdescriptor.h"
#include "mali_gralloc_buffer.h"
#include "mali_gralloc_error.h"
#include <cstring>

#include "hidl_common/hidl_common.h"
#include <algorithm>
#include <iterator>

#include <aidl/arm/graphics/Compression.h>
#include <aidl/arm/graphics/ArmMetadataType.h>

namespace arm
{
namespace mapper
{
namespace common
{
using android::hardware::hidl_vec;
using aidl::android::hardware::graphics::common::ExtendableType;

#define GRALLOC_ARM_COMPRESSION_TYPE_NAME "arm.graphics.Compression"
const static ExtendableType Compression_AFBC{ GRALLOC_ARM_COMPRESSION_TYPE_NAME,
                                                  static_cast<int64_t>(aidl::arm::graphics::Compression::AFBC) };

#define GRALLOC_ARM_METADATA_TYPE_NAME "arm.graphics.ArmMetadataType"


class MetadataType {
 public:
	std::string name;
	uint64_t value;
#if defined(GRALLOC_MAPPER_4)
	MetadataType(const IMapper::MetadataType &meta) {
		name = meta.name;
		value = meta.value;
	}
	operator IMapper::MetadataType() const {
		IMapper::MetadataType meta;
		meta.name = name;
		meta.value = value;
		return meta;
	}
#elif defined(GRALLOC_MAPPER_5)
	MetadataType(const AIMapper_MetadataType &meta) {
			name = meta.name;
			value = meta.value;
	}
	operator AIMapper_MetadataType() const {
			AIMapper_MetadataType meta;
			meta.name = strdup(name.c_str());
			meta.value = value;
			return meta;
	}
#endif // GRALLOC_MAPPER_4 or GRALLOC_MAPPER_5

	MetadataType() {}
	MetadataType(std::string strname, uint64_t val) {
		name = strname;
		value = val;
	}
	MetadataType(StandardMetadataType meta) : MetadataType(GRALLOC4_STANDARD_METADATA_TYPE, static_cast<uint64_t>(meta)) {}
};

const static MetadataType ArmMetadataType_PLANE_FDS = MetadataType(GRALLOC_ARM_METADATA_TYPE_NAME, static_cast<int64_t>(aidl::arm::graphics::ArmMetadataType::PLANE_FDS));

constexpr int RES_SIZE = 32;

struct MetadataTypeDescription {
	MetadataType metadataType;
	const char* description;
	bool isGettable;
	bool isSettable;
#if defined(GRALLOC_MAPPER_4)
	MetadataTypeDescription(const IMapper::MetadataTypeDescription &desc) {
		metadataType = desc.metadataType;
		description = (desc.description).c_str();
		isGettable = desc.isGettable;
		isSettable = desc.isSettable;
	}
	operator IMapper::MetadataTypeDescription() const {
		IMapper::MetadataTypeDescription desc;
		desc.metadataType = static_cast<IMapper::MetadataType>(metadataType);
		desc.description = description;
		desc.isGettable = isGettable;
		desc.isSettable = isSettable;
		return desc;
	}
#elif defined(GRALLOC_MAPPER_5)
	MetadataTypeDescription(const AIMapper_MetadataTypeDescription &desc) {
		metadataType = desc.metadataType;
		description = strdup(desc.description);
		isGettable = desc.isGettable;
		isSettable = desc.isSettable;
	}
	operator AIMapper_MetadataTypeDescription() const {
		AIMapper_MetadataTypeDescription desc;
		desc.metadataType = static_cast<AIMapper_MetadataType>(metadataType);
		desc.isGettable = isGettable;
		desc.isSettable = isSettable;
		desc.description = strdup(description);
		return desc;
	}

#endif // GRALLOC_MAPPER_4 or GRALLOC_MAPPER_5
	MetadataTypeDescription(MetadataType meta, const char* desc, bool gettable, bool settable) {
		metadataType = meta;
		description = strdup(desc);
		isGettable = gettable;
		isSettable = settable;
	}
};

struct MetadataDump {
	MetadataType metadataType;
	std::vector<uint8_t> metadata;
#if defined(GRALLOC_MAPPER_4)
	MetadataDump(const IMapper::MetadataDump &meta) {
		metadataType = MetadataType(meta.metadataType);
		metadata = static_cast<std::vector<uint8_t> >(metadata);
	}
	operator IMapper::MetadataDump() const {
		IMapper::MetadataDump dump;
		dump.metadataType = static_cast<IMapper::MetadataType>(metadataType);
		dump.metadata = hidl_vec(metadata);
		return dump;
	}
#endif // GRALLOC_MAPPER_4
	MetadataDump() {}
	MetadataDump(MetadataType metaType, std::vector<uint8_t> &meta) {
		metadataType = metaType;
		metadata = meta;
	}
};

struct BufferDump {
	std::vector<MetadataDump> metadataDump;
#if defined(GRALLOC_MAPPER_4)
	BufferDump(const IMapper::BufferDump &dump) {
		for (auto meta : dump.metadataDump)
			metadataDump.push_back(MetadataDump(meta));
	}
	operator IMapper::BufferDump() const {
		IMapper::BufferDump bufferdump;
		std::vector<IMapper::MetadataDump> metaDump;
		for (auto meta : metadataDump) {
			metaDump.push_back(static_cast<IMapper::MetadataDump>(meta));
		}
		bufferdump.metadataDump = metaDump;
		return bufferdump;
	}
#endif // GRALLOC_MAPPER_4
	BufferDump(std::vector<MetadataDump> &meta) { metadataDump = meta; }
	BufferDump() {}
};


int get_num_planes(const private_handle_t *hnd);

static std::vector<std::vector<PlaneLayoutComponent>> plane_layout_components_from_handle(const private_handle_t *hnd);

android::status_t get_plane_layouts(const private_handle_t *handle, std::vector<PlaneLayout> *layouts);

bool isStandardMetadataType(const MetadataType &metadataType);

/**
 * Retrieves a Buffer's metadata value.
 *
 * @param handle       [in] The private handle of the buffer to query for metadata.
 * @param metadataType [in] The type of metadata queried.
 * @param hidl_cb      [in] HIDL callback function generating -
 *                          error: NONE on success.
 *                                 UNSUPPORTED on error when reading or unsupported metadata type.
 *                          metadata: Vector of bytes representing the metadata value.
 */
Error get_metadata(const private_handle_t *handle, const MetadataType &metadataType, std::vector<uint8_t> &outVec);

/**
 * Sets a Buffer's metadata value.
 *
 * @param handle       [in] The private handle of the buffer for which to modify metadata.
 * @param metadataType [in] The type of metadata to modify.
 * @param metadata     [in] Vector of bytes representing the new value for the metadata associated with the buffer.
 *
 * @return Error::NONE on success.
 *         Error::UNSUPPORTED on error when writing or unsupported metadata type.
 */
Error set_metadata(const private_handle_t *handle, const MetadataType &metadataType, const frameworks_vec<uint8_t> &metadata);

/**
 * Query basic metadata information about a buffer form its descriptor before allocation.
 *
 * @param description  [in] The buffer descriptor.
 * @param metadataType [in] The type of metadata to query
 * @param hidl_cb      [in] HIDL callback function generating -
 *                          error: NONE on success.
 *                                 UNSUPPORTED on unsupported metadata type.
 *                          metadata: Vector of bytes representing the metadata value.
 */
#if defined(GRALLOC_MAPPER_4)
Error getFromBufferDescriptorInfo(IMapper::BufferDescriptorInfo const &description, MetadataType const &metadataType, std::vector<uint8_t> &outVec);
#endif // GRALLOC_MAPPER_4

} // namespace common
} // namespace mapper
} // namespace arm

#endif
