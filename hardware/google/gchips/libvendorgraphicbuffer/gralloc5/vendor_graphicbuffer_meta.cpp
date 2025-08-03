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

#define LOG_TAG "VendorGraphicBuffer"

#include <android/hardware/graphics/mapper/IMapper.h>
#include <android/hardware/graphics/mapper/utils/IMapperMetadataTypes.h>
#include <android/hardware/graphics/mapper/utils/IMapperProvider.h>
#include <gralloctypes/Gralloc4.h>
#include <log/log.h>

#include "VendorGraphicBuffer.h"
#include "mali_gralloc_buffer.h"
#include "mali_gralloc_formats.h"
#include "hidl_common/SharedMetadata.h"
#include "hidl_common/SharedMetadata_struct.h"
#include "hidl_common/hidl_common.h"
#include "exynos_format.h"

#include <pixel-gralloc/metadata.h>
#include <pixel-gralloc/mapper.h>
#include <vndksupport/linker.h>
#include <dlfcn.h>

using namespace android;
using namespace vendor::graphics;
using arm::mapper::common::shared_metadata;
using aidl::android::hardware::graphics::common::Dataspace;
using aidl::android::hardware::graphics::common::StandardMetadataType;
using namespace ::android::hardware::graphics::mapper;
using AIMapper_loadIMapperFn = decltype(AIMapper_loadIMapper) *;

constexpr const char *kAIMapper_loadIMapper_symbol_name =
    "AIMapper_loadIMapper";

#define UNUSED(x) ((void)x)
#define SZ_4k 0x1000

constexpr const char* STANDARD_METADATA_NAME =
    "android.hardware.graphics.common.StandardMetadataType";

typedef AIMapper_MetadataType MapperMetadataType;

AIMapper_MetadataType getMetaType(StandardMetadataType meta) {
	return {STANDARD_METADATA_NAME, static_cast<int64_t>(meta)};
}

// TODO(b/191912915): This is a non-thread safe version of the validate function
// from mapper-4.0-impl library. Ideally this should be decoupled from `impl`
// libraries and should depend upon HAL (and it's extension) to call into
// Gralloc.
int mali_gralloc_reference_validate(buffer_handle_t handle) {
	if (!handle) return -1;
	return private_handle_t::validate(handle);
}

const private_handle_t* convertNativeHandleToPrivateHandle(buffer_handle_t handle) {
	if (mali_gralloc_reference_validate(handle) < 0) {
		return nullptr;
	}

	return static_cast<const private_handle_t*>(handle);
}

static AIMapper &get_mapper()
{
	static AIMapper *cached_service = []() -> AIMapper* {
		AIMapper *mapper = nullptr;
		std::string so_name = "mapper.pixel.so";
		void *so_lib = android_load_sphal_library(so_name.c_str(), RTLD_LOCAL | RTLD_NOW);
		if (!so_lib) {
			ALOGE("android_load_sphal_library failed - so_name: %s", so_name.c_str());
			return nullptr;
		}
		auto load_fn = reinterpret_cast<AIMapper_loadIMapperFn>(dlsym(so_lib, kAIMapper_loadIMapper_symbol_name));
		if (!load_fn) {
			ALOGE("Failed to dlsym AIMapper_loadIMapperFn");
			return nullptr;
		}
		load_fn(&mapper);
		if (!mapper) {
			ALOGE("Failed to get Mapper!");
		}
		return mapper;
	}();
	return *cached_service;
}

template <StandardMetadataType meta>
static int get_metadata(buffer_handle_t& handle, typename StandardMetadata<meta>::value_type& outVal) {
	assert(handle);
	auto mapper = get_mapper();

	int size_or_error = 0;
	std::vector<uint8_t> metabuf;
	size_or_error = mapper.v5.getMetadata(handle, getMetaType(meta), nullptr, 0);

	if (size_or_error <= 0) {
		return size_or_error;
	}
	metabuf.resize(size_or_error);
	size_or_error = mapper.v5.getMetadata(handle, getMetaType(meta), metabuf.data(), metabuf.size());
	auto result = StandardMetadata<meta>::value::decode(metabuf.data(), metabuf.size());
	if (!result.has_value()) {
		return -EINVAL;
	}
	outVal = result.value();
	return 0;
}

int VendorGraphicBufferMeta::get_video_metadata_fd(buffer_handle_t /*hnd*/) {
	ALOGE("%s function is obsolete and should not be used", __FUNCTION__);
	__builtin_trap();
}

int VendorGraphicBufferMeta::get_dataspace(buffer_handle_t hnd) {
	if (!hnd) {
		return -EINVAL;
	}
	Dataspace dataspace;
	int err = get_metadata<StandardMetadataType::DATASPACE>(hnd, dataspace);
	if (err < 0) {
		return -EINVAL;
	}

	return static_cast<int>(dataspace);
}

int VendorGraphicBufferMeta::set_dataspace(buffer_handle_t hnd,
					   android_dataspace_t dataspace) {
	native_handle_t* handle = const_cast<native_handle_t*>(hnd);
	if (!handle) {
		ALOGE("Handle is invalid");
		return -EINVAL;
	}

	using dataspace_value = StandardMetadata<StandardMetadataType::DATASPACE>::value;
	int size_required =
		dataspace_value::encode(static_cast<Dataspace>(dataspace), nullptr, 0);
	if (size_required < 0) {
		ALOGE("Encode failed");
		return -EINVAL;
	}
	std::vector<uint8_t> buffer;
	buffer.resize(size_required);
	size_required = dataspace_value::encode(static_cast<Dataspace>(dataspace), buffer.data(), buffer.size());
	int err = get_mapper().v5.setMetadata(
	    hnd, getMetaType(StandardMetadataType::DATASPACE), buffer.data(),
	    buffer.size());

	if (err < 0) {
		ALOGE("Failed to set datasapce");
		return -EINVAL;
	}

	return 0;
}

int VendorGraphicBufferMeta::is_afbc(buffer_handle_t hnd) {
	const auto* gralloc_hnd = convertNativeHandleToPrivateHandle(hnd);

	if (!gralloc_hnd) {
		return 0;
	}

	if (gralloc_hnd->alloc_format & MALI_GRALLOC_INTFMT_AFBCENABLE_MASK) {
		return 1;
	}

	return 0;
}

int VendorGraphicBufferMeta::is_sbwc(buffer_handle_t hnd) {
	return is_sbwc_format(
	    VendorGraphicBufferMeta::get_internal_format(hnd));
}

#define GRALLOC_META_GETTER(__type__, __name__, __member__)    \
	__type__ VendorGraphicBufferMeta::get_##__name__(      \
	    buffer_handle_t hnd) {                             \
		const private_handle_t* gralloc_hnd =          \
		    static_cast<const private_handle_t*>(hnd); \
		if (!gralloc_hnd) return 0;                    \
		return gralloc_hnd->__member__;                \
	}

uint32_t VendorGraphicBufferMeta::get_format(buffer_handle_t hnd) {
	const auto* gralloc_hnd = convertNativeHandleToPrivateHandle(hnd);

	if (!gralloc_hnd) return 0;

	return static_cast<uint32_t>(gralloc_hnd->alloc_format);
}

uint64_t VendorGraphicBufferMeta::get_internal_format(buffer_handle_t hnd) {
	const auto* gralloc_hnd = convertNativeHandleToPrivateHandle(hnd);
	;

	if (!gralloc_hnd) return 0;

	return static_cast<uint64_t>(gralloc_hnd->alloc_format &
				     MALI_GRALLOC_INTFMT_FMT_MASK);
}

GRALLOC_META_GETTER(uint64_t, frameworkFormat, req_format);

GRALLOC_META_GETTER(int, width, width);
GRALLOC_META_GETTER(int, height, height);
GRALLOC_META_GETTER(uint32_t, stride, stride);
GRALLOC_META_GETTER(uint32_t, stride_in_bytes, plane_info[0].byte_stride);
GRALLOC_META_GETTER(uint32_t, vstride, plane_info[0].alloc_height);

GRALLOC_META_GETTER(uint64_t, producer_usage, producer_usage);
GRALLOC_META_GETTER(uint64_t, consumer_usage, consumer_usage);

GRALLOC_META_GETTER(uint64_t, flags, flags);

int VendorGraphicBufferMeta::get_fd(buffer_handle_t hnd, int num) {
	const auto* gralloc_hnd = convertNativeHandleToPrivateHandle(hnd);

	if (!gralloc_hnd) {
		return -1;
	}

	if (num > 2) {
		return -1;
	}

	return gralloc_hnd->fds[num];
}

int VendorGraphicBufferMeta::get_size(buffer_handle_t hnd, int num) {
	const auto* gralloc_hnd = convertNativeHandleToPrivateHandle(hnd);

	if (!gralloc_hnd) {
		return 0;
	}

	if (num > 2) {
		return 0;
	}

	return gralloc_hnd->alloc_sizes[num];
}

uint64_t VendorGraphicBufferMeta::get_usage(buffer_handle_t hnd) {
	const auto* gralloc_hnd = convertNativeHandleToPrivateHandle(hnd);

	if (!gralloc_hnd) return 0;

	return gralloc_hnd->producer_usage | gralloc_hnd->consumer_usage;
}

void* decodePointer(const std::vector<uint8_t>& tmpVec) {
	constexpr uint8_t kPtrSize = sizeof(void*);
	assert(tmpVec.size() == kPtrSize);

	void* data_ptr;
	std::memcpy(&data_ptr, tmpVec.data(), kPtrSize);

	return data_ptr;
}

void* VendorGraphicBufferMeta::get_video_metadata(buffer_handle_t hnd) {
	if (!hnd) {
		return nullptr;
	}

	auto output = ::pixel::graphics::mapper::get<::pixel::graphics::MetadataType::VIDEO_HDR>(hnd);

	if (!output.has_value()) {
		ALOGE("Failed to get video HDR metadata");
		return nullptr;
	}

	return output.value();
}

void* VendorGraphicBufferMeta::get_video_metadata_roiinfo(buffer_handle_t hnd) {
	if (!hnd) {
		return nullptr;
	}

	auto output = ::pixel::graphics::mapper::get<::pixel::graphics::MetadataType::VIDEO_ROI>(hnd);

	if (!output.has_value()) {
		ALOGE("Failed to get video ROI metadata");
		return nullptr;
	}

	return output.value();
}

uint32_t VendorGraphicBufferMeta::get_format_fourcc(buffer_handle_t hnd) {
	if (!hnd) {
		return DRM_FORMAT_INVALID;
	}

	uint32_t fourcc;
	int err = get_metadata<StandardMetadataType::PIXEL_FORMAT_FOURCC>(
	    hnd, fourcc);

	if (err < 0) {
		ALOGE("Failed to get fourcc");
		return DRM_FORMAT_INVALID;
	}

	return fourcc;
}

uint64_t VendorGraphicBufferMeta::get_format_modifier(buffer_handle_t hnd) {
	if (!hnd) {
		return DRM_FORMAT_MOD_INVALID;
	}

	uint64_t modifier;
	int err = get_metadata<StandardMetadataType::PIXEL_FORMAT_MODIFIER>(
	    hnd, modifier);

	if (err < 0) {
		ALOGE("Failed to get format modifier");
		return DRM_FORMAT_MOD_INVALID;
	}

	return modifier;
}

void VendorGraphicBufferMeta::init(const buffer_handle_t handle) {
	const auto* gralloc_hnd = convertNativeHandleToPrivateHandle(handle);

	if (!gralloc_hnd) return;

	fd = gralloc_hnd->fds[0];
	fd1 = gralloc_hnd->fds[1];
	fd2 = gralloc_hnd->fds[2];

	size = gralloc_hnd->alloc_sizes[0];
	size1 = gralloc_hnd->alloc_sizes[1];
	size2 = gralloc_hnd->alloc_sizes[2];

	offsets[0] = gralloc_hnd->plane_info[0].offset;
	offsets[1] = gralloc_hnd->plane_info[1].offset;
	offsets[2] = gralloc_hnd->plane_info[2].offset;

	uint64_t usage =
	    gralloc_hnd->producer_usage | gralloc_hnd->consumer_usage;
	if (usage & VendorGraphicBufferUsage::VIDEO_PRIVATE_DATA) {
		switch (gralloc_hnd->get_share_attr_fd_index()) {
			case 1:
				size1 = gralloc_hnd->attr_size;
				break;
			case 2:
				size2 = gralloc_hnd->attr_size;
				break;
		}
	}

	internal_format = gralloc_hnd->alloc_format;
	frameworkFormat = gralloc_hnd->req_format;

	width = gralloc_hnd->width;
	height = gralloc_hnd->height;
	stride = gralloc_hnd->stride;
	vstride = gralloc_hnd->plane_info[0].alloc_height;

	producer_usage = gralloc_hnd->producer_usage;
	consumer_usage = gralloc_hnd->consumer_usage;

	flags = gralloc_hnd->flags;

	unique_id = gralloc_hnd->backing_store_id;
}

buffer_handle_t VendorGraphicBufferMeta::import_buffer(buffer_handle_t hnd) {
	native_handle_t* handle = const_cast<native_handle_t*>(hnd);
	if (!handle) {
		return nullptr;
	}

	buffer_handle_t bufferHandle = nullptr;
	AIMapper_Error error = AIMapper_Error::AIMAPPER_ERROR_NONE;
	error = get_mapper().v5.importBuffer(handle, &bufferHandle);

	if (error != AIMapper_Error::AIMAPPER_ERROR_NONE) {
		ALOGE("[%s] Unable to import buffer", __FUNCTION__);
		return nullptr;
	}

	return bufferHandle;
}

int VendorGraphicBufferMeta::free_buffer(buffer_handle_t hnd) {
	if (!hnd) {
		return -EINVAL;
	}
	AIMapper_Error error = get_mapper().v5.freeBuffer(hnd);
	if (error != AIMapper_Error::AIMAPPER_ERROR_NONE) {
		ALOGE("[%s] Failed to free buffer", __FUNCTION__);
		return -EINVAL;
	}
	return 0;
}

VendorGraphicBufferMeta::VendorGraphicBufferMeta(buffer_handle_t handle) {
	init(handle);
}

