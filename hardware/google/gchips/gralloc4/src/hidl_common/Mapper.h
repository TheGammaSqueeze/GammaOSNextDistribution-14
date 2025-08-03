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

#ifndef GRALLOC_COMMON_MAPPER_H
#define GRALLOC_COMMON_MAPPER_H

#include <inttypes.h>
#include "mali_gralloc_log.h"
#include "core/mali_gralloc_bufferdescriptor.h"
#include <android/rect.h>

#include "MapperMetadata.h"
#include "hidl_common.h"
#include "mali_gralloc_error.h"

namespace arm
{
namespace mapper
{
namespace common
{


using aidl::android::hardware::graphics::common::Rect;

using android::hardware::Void;

class GrallocRect {
	public:
	int left;
	int top;
	int right;
	int bottom;
	GrallocRect(Rect rect) {
		left = rect.left;
		top = rect.top;
		right = rect.right;
		bottom = rect.bottom;
	}
#if defined(GRALLOC_MAPPER_4)
	GrallocRect(IMapper::Rect rect) {
		left = rect.left;
		top = rect.top;
		right = rect.left + rect.width;
		bottom = rect.top + rect.height;
	}
#elif defined(GRALLOC_MAPPER_5)
	GrallocRect(ARect rect) {
		left = rect.left;
		top = rect.top;
		right = rect.right;
		bottom = rect.bottom;
	}
#endif // GRALLOC_MAPPER_4 or GRALLOC_MAPPER_5
};

/**
 * Imports a raw buffer handle to create an imported buffer handle for use with
 * the rest of the mapper or with other in-process libraries.
 *
 * @param bufferHandle [in] Buffer handle to import.
 * @param outBuffer [in] imported Buffer
 */
Error importBuffer(const native_handle_t *inBuffer, buffer_handle_t *outBuffer);


/**
 * Frees a buffer handle and releases all the resources associated with it
 *
 * @param buffer [in] Imported buffer to free
 *
 * @return Error::BAD_BUFFER for an invalid buffer / when failed to free the buffer
 *         Error::NONE on successful free
 */
Error freeBuffer(buffer_handle_t buffer);

buffer_handle_t getBuffer(void *buffer);
native_handle_t* removeBuffer(void **buffer);

/**
 * Locks the given buffer for the specified CPU usage.
 *
 * @param buffer       [in] Buffer to lock
 * @param cpuUsage     [in] Specifies one or more CPU usage flags to request
 * @param accessRegion [in] Portion of the buffer that the client intends to access
 * @param acquireFence [in] Handle for aquire fence object
 * @param hidl_cb      [in] HIDL callback function generating -
 *                          error:          NONE upon success. Otherwise,
 *                                          BAD_BUFFER for an invalid buffer
 *                                          BAD_VALUE for an invalid input parameters
 *                          data:           CPU-accessible pointer to the buffer data
 *                          bytesPerPixel:  v3.X Only. Number of bytes per pixel in the buffer
 *                          bytesPerStride: v3.X Only. Bytes per stride of the buffer
 */
Error lock(buffer_handle_t buffer, uint64_t cpuUsage, const GrallocRect &accessRegion, int acquireFence, void **outData);

/**
 * Unlocks a buffer to indicate all CPU accesses to the buffer have completed
 *
 * @param buffer       [in] Buffer to lock.
 * @param hidl_cb      [in] HIDL callback function generating -
 *                          error:        NONE upon success. Otherwise,
 *                                        BAD_BUFFER for an invalid buffer
 *                          releaseFence: Referrs to a sync fence object
 */
Error unlock(const native_handle_t *buffer, int *releaseFence);

/**
 * Validates the buffer against specified descriptor attributes
 *
 * @param buffer          [in] Buffer which needs to be validated.
 * @param descriptorInfo  [in] Required attributes of the buffer
 * @param in_stride       [in] Buffer stride returned by IAllocator::allocate,
 *                             or zero if unknown.
 *
 * @return Error::NONE upon success. Otherwise,
 *         Error::BAD_BUFFER upon bad buffer input
 *         Error::BAD_VALUE when any of the specified attributes are invalid
 */
#ifdef GRALLOC_MAPPER_4
Error validateBufferSize(void *buffer, const IMapper::BufferDescriptorInfo &descriptorInfo, uint32_t stride);
#endif

/**
 * Get the transport size of a buffer
 *
 * @param buffer       [in] Buffer for computing transport size
 * @param hidl_cb      [in] HIDL callback function generating -
 *                          error:   NONE upon success. Otherwise,
 *                                   BAD_BUFFER for an invalid buffer
 *                          numFds:  Number of file descriptors needed for transport
 *                          numInts: Number of integers needed for transport
 */
Error getTransportSize(buffer_handle_t bufferHandle, uint32_t *outNumFds, uint32_t *outNumInts);

/**
 * Test whether the given BufferDescriptorInfo is allocatable.
 *
 * @param description       [in] Description for the buffer
 * @param hidl_cb           [in] HIDL callback function generating -
 *                          error:     NONE, for supported description
 *                                     BAD_VALUE, Otherwise,
 *                          supported: Whether the description can be allocated
 */
#ifdef GRALLOC_MAPPER_4
bool isSupported(const IMapper::BufferDescriptorInfo &description);
#endif
/* TODO: implement this feature for exynos */
/**
 * Flushes the CPU caches of a mapped buffer.
 *
 * @param buffer   [in] Locked buffer which needs to have CPU caches flushed.
 * @param hidl_cb  [in] HIDL callback function generating -
 *                      error:        NONE upon success. Otherwise, BAD_BUFFER for an invalid buffer or a buffer that
 *                                    has not been locked.
 *                      releaseFence: Empty fence signaling completion as all work is completed within the call.
 */
Error flushLockedBuffer(buffer_handle_t buffer);

/* TODO: implement this feature for exynos */
/**
 * Invalidates the CPU caches of a mapped buffer.
 *
 * @param buffer [in] Locked buffer which needs to have CPU caches invalidated.
 *
 * @return Error::NONE upon success.
 *         Error::BAD_BUFFER for an invalid buffer or a buffer that has not been locked.
 */
Error rereadLockedBuffer(buffer_handle_t handle);

/**
 * Retrieves a Buffer's metadata value.
 *
 * @param buffer       [in] The buffer to query for metadata.
 * @param metadataType [in] The type of metadata queried.
 * @param hidl_cb      [in] HIDL callback function generating -
 *                          error: NONE on success.
 *                                 BAD_BUFFER on invalid buffer argument.
 *                                 UNSUPPORTED on error when reading or unsupported metadata type.
 *                          metadata: Vector of bytes representing the metadata value.
 */
Error get(buffer_handle_t buffer, const MetadataType &metadataType, std::vector<uint8_t> &vec);

/**
 * Sets a Buffer's metadata value.
 *
 * @param buffer       [in] The buffer for which to modify metadata.
 * @param metadataType [in] The type of metadata to modify.
 * @param metadata     [in] Vector of bytes representing the new value for the metadata associated with the buffer.
 *
 * @return Error::NONE on success.
 *         Error::BAD_BUFFER on invalid buffer argument.
 *         Error::UNSUPPORTED on error when writing or unsupported metadata type.
 */
Error set(buffer_handle_t buffer, const MetadataType &metadataType, const frameworks_vec<uint8_t> &metadata);

/**
 * Lists all the MetadataTypes supported by IMapper as well as a description
 * of each supported MetadataType. For StandardMetadataTypes, the description
 * string can be left empty.
 *
 *  @param hidl_cb [in] HIDL callback function generating -
 *                     error: Error status of the call, which may be
 *                           - NONE upon success.
 *                           - NO_RESOURCES if the get cannot be fullfilled due to unavailability of
 *                             resources.
 *                     descriptions: vector of MetadataTypeDescriptions that represent the
 *                                   MetadataTypes supported by the device.
 */
std::vector<MetadataTypeDescription> listSupportedMetadataTypes();

/**
 * Dumps a buffer's metadata.
 *
 * @param buffer  [in] Buffer that is being dumped
 * @param hidl_cb [in] HIDL callback function generating -
 *                     error: Error status of the call, which may be
 *                           - NONE upon success.
 *                           - BAD_BUFFER if the raw handle is invalid.
 *                           - NO_RESOURCES if the get cannot be fullfilled due to unavailability of
 *                             resources.
 *                     bufferDump: Struct representing the metadata being dumped
 */
Error dumpBuffer(buffer_handle_t buffer, BufferDump &out);

/**
 * Dumps the metadata for all the buffers in the current process.
 *
 * @param hidl_cb [in] HIDL callback function generating -
 *                     error: Error status of the call, which may be
 *                           - NONE upon success.
 *                           - NO_RESOURCES if the get cannot be fullfilled due to unavailability of
 *                             resources.
 *                     bufferDumps: Vector of structs representing the buffers being dumped
 */
std::vector<BufferDump> dumpBuffers();

/**
 * Returns the region of shared memory associated with the buffer that is
 * reserved for client use.
 *
 * The shared memory may be allocated from any shared memory allocator.
 * The shared memory must be CPU-accessible and virtually contiguous. The
 * starting address must be word-aligned.
 *
 * This function may only be called after importBuffer() has been called by the
 * client. The reserved region must remain accessible until freeBuffer() has
 * been called. After freeBuffer() has been called, the client must not access
 * the reserved region.
 *
 * This reserved memory may be used in future versions of Android to
 * help clients implement backwards compatible features without requiring
 * IAllocator/IMapper updates.
 *
 * @param buffer Imported buffer handle.
 * @param hidl_cb [in] HIDL callback function generating -
 *                     error: Error status of the call, which may be
 *                           - NONE upon success.
 *                           - BAD_BUFFER if the buffer is invalid.
 *                     reservedRegion: CPU-accessible pointer to the reserved region
 *                     reservedSize: the size of the reservedRegion that was requested
 *                                  in the BufferDescriptorInfo.
 */
Error getReservedRegion(buffer_handle_t buffer, void **outReservedRegion, uint64_t &outReservedSize);

} // namespace common
} // namespace mapper
} // namespace arm

#endif /* GRALLOC_COMMON_MAPPER_H */
