/*
 * Copyright (C) 2020 Arm Limited. All rights reserved.
 *
 * Copyright 2016 The Android Open Source Project
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

#include "GrallocMapper.h"
#include "hidl_common/BufferDescriptor.h"
#include "hidl_common/MapperMetadata.h"
#include "hidl_common/Mapper.h"

#include "allocator/mali_gralloc_ion.h"

namespace arm
{
namespace mapper
{

namespace hidl {
using android::hardware::graphics::mapper::V4_0::Error;
} // namespace hidl
using android::hardware::graphics::mapper::V4_0::BufferDescriptor;
using android::hardware::graphics::mapper::V4_0::IMapper;
using android::hardware::Return;
using android::hardware::hidl_handle;
using android::hardware::hidl_vec;
using android::hardware::Void;

GrallocMapper::GrallocMapper() {}

GrallocMapper::~GrallocMapper() {}

Return<void> GrallocMapper::createDescriptor(const BufferDescriptorInfo &descriptorInfo, createDescriptor_cb hidl_cb)
{
	if (common::validateDescriptorInfo(descriptorInfo))
	{
		hidl_cb(hidl::Error::NONE, common::grallocEncodeBufferDescriptor<uint8_t>(descriptorInfo));
	}
	else
	{
		MALI_GRALLOC_LOGE("Invalid attributes to create descriptor for Mapper 3.0");
		hidl_cb(hidl::Error::BAD_VALUE, BufferDescriptor());
	}

	return Void();
}

Return<void> GrallocMapper::importBuffer(const hidl_handle &rawHandle, importBuffer_cb hidl_cb)
{
	if (!rawHandle.getNativeHandle()) {
		hidl_cb(hidl::Error::BAD_BUFFER, nullptr);
		return Void();
	}

	auto *inHandle = const_cast<native_handle_t *>(rawHandle.getNativeHandle());
	buffer_handle_t outHandle = nullptr;

	hidl::Error err = static_cast<hidl::Error>(
		common::importBuffer(inHandle, &outHandle));

	hidl_cb(err, static_cast<void*>(const_cast<native_handle_t*>(outHandle)));
	return Void();
}

Return<hidl::Error> GrallocMapper::freeBuffer(void *buffer)
{
	buffer_handle_t handle = common::getBuffer(buffer);
	if (handle == nullptr) return hidl::Error::BAD_BUFFER;
	return static_cast<hidl::Error>(common::freeBuffer(handle));
}

Return<hidl::Error> GrallocMapper::validateBufferSize(void *buffer, const BufferDescriptorInfo &descriptorInfo,
                                                uint32_t in_stride)
{
	/* All Gralloc allocated buffers must be conform to local descriptor validation */
	if (!common::validateDescriptorInfo<BufferDescriptorInfo>(descriptorInfo))
	{
		MALI_GRALLOC_LOGE("Invalid descriptor attributes for validating buffer size");
		return hidl::Error::BAD_VALUE;
	}
	return static_cast<hidl::Error>(common::validateBufferSize(buffer, descriptorInfo, in_stride));
}

static bool getFenceFd(const hidl_handle &fenceHandle, int *outFenceFd) {
	auto const handle = fenceHandle.getNativeHandle();
	if (handle && handle->numFds > 1) {
		MALI_GRALLOC_LOGE("Invalid fence handle with %d fds",
				  handle->numFds);
		return false;
	}

	*outFenceFd = (handle && handle->numFds == 1) ? handle->data[0] : -1;
	return true;
}

Return<void> GrallocMapper::lock(void *buffer, uint64_t cpuUsage, const IMapper::Rect &accessRegion,
                                 const hidl_handle &acquireFence, lock_cb hidl_cb)
{
	void *outData = nullptr;
	int fenceFd;
	if (!getFenceFd(acquireFence, &fenceFd)) {
		hidl_cb(hidl::Error::BAD_VALUE, nullptr);
		return Void();
	}

	hidl::Error err = static_cast<hidl::Error>(
			common::lock(static_cast<buffer_handle_t>(buffer), cpuUsage,
			common::GrallocRect(accessRegion), fenceFd, &outData));
	if (err != hidl::Error::NONE) outData = nullptr;
	hidl_cb(err, outData);
	return Void();
}

/*
 * Populates the HIDL fence handle for the given fence object
 *
 * @param fenceFd       [in] Fence file descriptor
 * @param handleStorage [in] HIDL handle storage for fence
 *
 * @return HIDL fence handle
 */
static hidl_handle buildFenceHandle(int fenceFd, char *handleStorage) {
	native_handle_t *handle = nullptr;
	if (fenceFd >= 0) {
		handle = native_handle_init(handleStorage, 1, 0);
		handle->data[0] = fenceFd;
	}

	return hidl_handle(handle);
}

Return<void> GrallocMapper::unlock(void *buffer, unlock_cb hidl_cb)
{
	int fenceFd = -1;
	const native_handle_t *handle = common::getBuffer(buffer);

	if (handle == nullptr) {
		hidl_cb(hidl::Error::BAD_BUFFER, nullptr);
	}

	hidl::Error err =
	    static_cast<hidl::Error>(common::unlock(handle, &fenceFd));
	if (err == hidl::Error::NONE) {
		NATIVE_HANDLE_DECLARE_STORAGE(fenceStorage, 1, 0);
		hidl_cb(err, buildFenceHandle(fenceFd, fenceStorage));

		if (fenceFd >= 0) {
			close(fenceFd);
		}
	} else {
		hidl_cb(err, nullptr);
	}
	return Void();
}

Return<void> GrallocMapper::flushLockedBuffer(void *buffer, flushLockedBuffer_cb hidl_cb)
{
	hidl::Error err = static_cast<hidl::Error>(common::flushLockedBuffer(static_cast<buffer_handle_t>(buffer)));
	hidl_cb(err, hidl_handle{});
	return Void();
}

Return<hidl::Error> GrallocMapper::rereadLockedBuffer(void *buffer)
{
	return static_cast<hidl::Error>(common::rereadLockedBuffer(common::getBuffer(buffer)));
}

Return<void> GrallocMapper::get(void *buffer, const MetadataType &metadataType, IMapper::get_cb hidl_cb)
{
	std::vector<uint8_t> vec;
	hidl::Error err = static_cast<hidl::Error>(
	    common::get(common::getBuffer(buffer), common::MetadataType(metadataType), vec));
	hidl_cb(err, hidl_vec(vec));
	return Void();
}

Return<hidl::Error> GrallocMapper::set(void *buffer, const MetadataType &metadataType, const hidl_vec<uint8_t> &metadata)
{
	buffer_handle_t bufferHandle = common::getBuffer(buffer);
	return static_cast<hidl::Error>(common::set(bufferHandle, common::MetadataType(metadataType), metadata));
}

Return<void> GrallocMapper::getFromBufferDescriptorInfo(const BufferDescriptorInfo &description,
                                                        const MetadataType &metadataType,
                                                        getFromBufferDescriptorInfo_cb hidl_cb)
{
	std::vector<uint8_t> vec;
	hidl::Error err = static_cast<hidl::Error>(common::getFromBufferDescriptorInfo(
		description, common::MetadataType(metadataType), vec));
	hidl_cb(err, vec);
	return Void();
}

Return<void> GrallocMapper::getTransportSize(void *buffer, getTransportSize_cb hidl_cb)
{
	uint32_t outNumFds = 0, outNumInts = 0;
	buffer_handle_t bufferHandle = common::getBuffer(buffer);
	hidl::Error err = static_cast<hidl::Error>(common::getTransportSize(bufferHandle, &outNumFds, &outNumInts));
	hidl_cb(err, outNumFds, outNumInts);
	return Void();
}

Return<void> GrallocMapper::isSupported(const IMapper::BufferDescriptorInfo &description, isSupported_cb hidl_cb)
{
	if (!common::validateDescriptorInfo<BufferDescriptorInfo>(description))
	{
		MALI_GRALLOC_LOGE("Invalid descriptor attributes for validating buffer size");
		hidl_cb(hidl::Error::BAD_VALUE, false);
	}
	hidl_cb(hidl::Error::NONE, common::isSupported(description));
	return Void();
}

Return<void> GrallocMapper::listSupportedMetadataTypes(listSupportedMetadataTypes_cb hidl_cb)
{
	std::vector<common::MetadataTypeDescription> desc = common::listSupportedMetadataTypes();
	std::vector<IMapper::MetadataTypeDescription> hidl_description(desc.size());
	std::copy(desc.begin(), desc.end(), hidl_description.begin());
	hidl_cb(hidl::Error::NONE, hidl_vec(hidl_description));
	return Void();
}

Return<void> GrallocMapper::dumpBuffer(void *buffer, dumpBuffer_cb hidl_cb)
{
	common::BufferDump out;
	hidl::Error err = static_cast<hidl::Error>(common::dumpBuffer(common::getBuffer(buffer), out));
	hidl_cb(err, static_cast<IMapper::BufferDump>(out));
	return Void();
}

Return<void> GrallocMapper::dumpBuffers(dumpBuffers_cb hidl_cb)
{
	auto bufferDump = common::dumpBuffers();
	std::vector<IMapper::BufferDump> outBufDump;
	for (auto dump : bufferDump) {
		outBufDump.push_back(static_cast<IMapper::BufferDump>(dump));
	}
	hidl_cb(hidl::Error::NONE, hidl_vec(outBufDump));
	return Void();
}

Return<void> GrallocMapper::getReservedRegion(void *buffer, getReservedRegion_cb hidl_cb)
{
	void *reservedRegion = nullptr;
	uint64_t reservedSize = 0;
	hidl::Error err = static_cast<hidl::Error>(
				common::getReservedRegion(static_cast<buffer_handle_t>(buffer),
				&reservedRegion, reservedSize));
	if (err != hidl::Error::NONE) {
		reservedRegion = nullptr;
		reservedSize = 0;
	}
	hidl_cb(err, reservedRegion, reservedSize);
	return Void();
}

} // namespace mapper
} // namespace arm

extern "C" IMapper *HIDL_FETCH_IMapper(const char * /* name */)
{
	MALI_GRALLOC_LOGV("Arm Module IMapper %d.%d , pid = %d", GRALLOC_VERSION_MAJOR,
	                  (HIDL_MAPPER_VERSION_SCALED - (GRALLOC_VERSION_MAJOR * 100)) / 10, getpid());

	return new arm::mapper::GrallocMapper();
}
