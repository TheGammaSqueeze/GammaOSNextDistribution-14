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

#include <string.h>
#include <errno.h>
#include <inttypes.h>
#include <pthread.h>
#include <stdlib.h>
#include <limits.h>

#include <log/log.h>
#include <cutils/atomic.h>
#include <utils/Trace.h>

#include <linux/dma-buf.h>
#include <vector>
#include <sys/ioctl.h>

#include <hardware/hardware.h>
#include <hardware/gralloc1.h>

#include <BufferAllocator/BufferAllocator.h>
#include "mali_gralloc_buffer.h"
#include "gralloc_helper.h"
#include "mali_gralloc_formats.h"
#include "mali_gralloc_usages.h"
#include "core/format_info.h"
#include "core/mali_gralloc_bufferdescriptor.h"
#include "core/mali_gralloc_bufferallocation.h"

#include "mali_gralloc_ion.h"

#include <array>
#include <cassert>
#include <string>

static const char kDmabufSensorDirectHeapName[] = "sensor_direct_heap";
static const char kDmabufFaceauthTpuHeapName[] = "faceauth_tpu-secure";
static const char kDmabufFaceauthImgHeapName[] = "faimg-secure";
static const char kDmabufFaceauthRawImgHeapName[] = "farawimg-secure";
static const char kDmabufFaceauthEvalHeapName[] = "faeval-secure";
static const char kDmabufFaceauthPrevHeapName[] = "faprev-secure";
static const char kDmabufFaceauthModelHeapName[] = "famodel-secure";
static const char kDmabufVframeSecureHeapName[] = "vframe-secure";
static const char kDmabufVstreamSecureHeapName[] = "vstream-secure";
static const char kDmabufVscalerSecureHeapName[] = "vscaler-secure";
static const char kDmabufFramebufferSecureHeapName[] = "framebuffer-secure";
static const char kDmabufGcmaCameraHeapName[] = "gcma_camera";
static const char kDmabufGcmaCameraUncachedHeapName[] = "gcma_camera-uncached";

BufferAllocator& get_allocator() {
		static BufferAllocator allocator;
		return allocator;
}

std::string find_first_available_heap(const std::initializer_list<std::string>&& options) {
	static auto available_heaps = BufferAllocator::GetDmabufHeapList();

	for (const auto& heap: options)
		if (available_heaps.find(heap) != available_heaps.end())
			return heap;

	return "";
}

std::string select_dmabuf_heap(uint64_t usage)
{
	struct HeapSpecifier
	{
		uint64_t      usage_bits;
		std::string   name;
	};

	static const std::array<HeapSpecifier, 8> exact_usage_heaps =
	{{
		// Faceauth heaps
		{ // faceauth_evaluation_heap - used mostly on debug builds
			GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_HW_CAMERA_WRITE | GRALLOC_USAGE_HW_CAMERA_READ |
			GS101_GRALLOC_USAGE_FACEAUTH_RAW_EVAL,
			kDmabufFaceauthEvalHeapName
		},
		{ // isp_image_heap
			GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_HW_CAMERA_WRITE | GS101_GRALLOC_USAGE_TPU_INPUT,
			kDmabufFaceauthImgHeapName
		},
		{ // isp_internal_heap
			GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_HW_CAMERA_WRITE | GRALLOC_USAGE_HW_CAMERA_READ,
			kDmabufFaceauthRawImgHeapName
		},
		{ // isp_preview_heap
			GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_HW_CAMERA_WRITE | GRALLOC_USAGE_HW_COMPOSER |
            GRALLOC_USAGE_HW_TEXTURE,
			kDmabufFaceauthPrevHeapName
		},
		{ // ml_model_heap
			GRALLOC_USAGE_PROTECTED | GS101_GRALLOC_USAGE_TPU_INPUT,
			kDmabufFaceauthModelHeapName
		},
		{ // tpu_heap
			GRALLOC_USAGE_PROTECTED | GS101_GRALLOC_USAGE_TPU_OUTPUT | GS101_GRALLOC_USAGE_TPU_INPUT,
			kDmabufFaceauthTpuHeapName
		},

		{
			GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_RENDER |
			GRALLOC_USAGE_HW_COMPOSER | GRALLOC_USAGE_HW_FB,
			find_first_available_heap({kDmabufFramebufferSecureHeapName, kDmabufVframeSecureHeapName})
		},

		{
			GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_RENDER |
			GRALLOC_USAGE_HW_COMPOSER,
			find_first_available_heap({kDmabufFramebufferSecureHeapName, kDmabufVframeSecureHeapName})
		},
	}};

	static const std::array<HeapSpecifier, 8> inexact_usage_heaps =
	{{
		// If GPU, use vframe-secure
		{
			GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_HW_TEXTURE,
			kDmabufVframeSecureHeapName
		},
		{
			GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_HW_RENDER,
			kDmabufVframeSecureHeapName
		},

		// If HWC but not GPU
		{
			GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_HW_COMPOSER,
			kDmabufVscalerSecureHeapName
		},

		// Catchall for protected
		{
			GRALLOC_USAGE_PROTECTED,
			kDmabufVframeSecureHeapName
		},

		// Sensor heap
		{
			GRALLOC_USAGE_SENSOR_DIRECT_DATA,
			kDmabufSensorDirectHeapName
		},

		// Camera GCMA heap
		{
			GRALLOC_USAGE_HW_CAMERA_WRITE,
			find_first_available_heap({kDmabufGcmaCameraUncachedHeapName, kDmabufSystemUncachedHeapName})
		},

		// Camera GCMA heap
		{
			GRALLOC_USAGE_HW_CAMERA_READ,
			find_first_available_heap({kDmabufGcmaCameraUncachedHeapName, kDmabufSystemUncachedHeapName})
		},

		// Catchall to system
		{
			0,
			kDmabufSystemUncachedHeapName
		}
	}};

	for (const HeapSpecifier &heap : exact_usage_heaps)
	{
		if (usage == heap.usage_bits)
		{
			return heap.name;
		}
	}

	for (const HeapSpecifier &heap : inexact_usage_heaps)
	{
		if ((usage & heap.usage_bits) == heap.usage_bits)
		{
			if (heap.name == kDmabufGcmaCameraUncachedHeapName &&
			    ((usage & GRALLOC_USAGE_SW_READ_MASK) == GRALLOC_USAGE_SW_READ_OFTEN))
				return kDmabufGcmaCameraHeapName;
			else if (heap.name == kDmabufSystemUncachedHeapName &&
			    ((usage & GRALLOC_USAGE_SW_READ_MASK) == GRALLOC_USAGE_SW_READ_OFTEN))
				return kDmabufSystemHeapName;

			return heap.name;
		}
	}

	return "";
}

int alloc_from_dmabuf_heap(uint64_t usage, size_t size, const std::string& buffer_name = "", bool use_placeholder = false)
{
	ATRACE_CALL();
	if (size == 0) { return -1; }

	auto heap_name = use_placeholder ? "system" : select_dmabuf_heap(usage);
	if (use_placeholder) size = 1;

	if (heap_name.empty()) {
			MALI_GRALLOC_LOGW("No heap found for usage: %s (0x%" PRIx64 ")", describe_usage(usage).c_str(), usage);
			return -EINVAL;
	}

	std::stringstream tag;
	tag << "heap: " << heap_name << ", bytes: " << size;
	ATRACE_NAME(tag.str().c_str());
	int shared_fd = get_allocator().Alloc(heap_name, size, 0);
	if (shared_fd < 0)
	{
		ALOGE("Allocation failed for heap %s error: %d\n", heap_name.c_str(), shared_fd);
	}

	if (!buffer_name.empty()) {
		if (get_allocator().DmabufSetName(shared_fd, buffer_name)) {
			ALOGW("Unable to set buffer name %s: %s", buffer_name.c_str(), strerror(errno));
		}
	}

	return shared_fd;
}

SyncType sync_type_for_flags(const bool read, const bool write)
{
	if (read && !write)
	{
		return SyncType::kSyncRead;
	}
	else if (write && !read)
	{
		return SyncType::kSyncWrite;
	}
	else
	{
		// Deliberately also allowing "not sure" to map to ReadWrite.
		return SyncType::kSyncReadWrite;
	}
}

int sync(const int fd, const bool read, const bool write, const bool start)
{
	if (start)
	{
		return get_allocator().CpuSyncStart(fd, sync_type_for_flags(read, write));
	}
	else
	{
		return get_allocator().CpuSyncEnd(fd, sync_type_for_flags(read, write));
	}
}

int mali_gralloc_ion_sync(const private_handle_t * const hnd,
                                       const bool read,
                                       const bool write,
                                       const bool start)
{
	if (hnd == NULL)
	{
		return -EINVAL;
	}

	for (int i = 0; i < hnd->fd_count; i++)
	{
		const int fd = hnd->fds[i];
		if (const int ret = sync(fd, read, write, start))
		{
			return ret;
		}
	}

	return 0;
}


int mali_gralloc_ion_sync_start(const private_handle_t * const hnd,
                                const bool read,
                                const bool write)
{
	return mali_gralloc_ion_sync(hnd, read, write, true);
}


int mali_gralloc_ion_sync_end(const private_handle_t * const hnd,
                              const bool read,
                              const bool write)
{
	return mali_gralloc_ion_sync(hnd, read, write, false);
}


void mali_gralloc_ion_free(private_handle_t * const hnd)
{
	for (int i = 0; i < hnd->fd_count; i++)
	{
		close(hnd->fds[i]);
		hnd->fds[i] = -1;
	}
	delete hnd;
}

void mali_gralloc_ion_free_internal(buffer_handle_t * const pHandle,
                                           const uint32_t num_hnds)
{
	for (uint32_t i = 0; i < num_hnds; i++)
	{
		if (pHandle[i] != NULL)
		{
			private_handle_t * const hnd = (private_handle_t * const)pHandle[i];
			mali_gralloc_ion_free(hnd);
		}
	}
}

int mali_gralloc_ion_allocate_attr(private_handle_t *hnd)
{
	ATRACE_CALL();

	int idx = hnd->get_share_attr_fd_index();
	uint64_t usage = GRALLOC_USAGE_SW_READ_OFTEN | GRALLOC_USAGE_SW_WRITE_OFTEN;

	hnd->fds[idx] = alloc_from_dmabuf_heap(usage, hnd->attr_size);
	if (hnd->fds[idx] < 0)
	{
		MALI_GRALLOC_LOGE("ion_alloc failed");
		return -1;
	}

	hnd->incr_numfds(1);

	return 0;
}

/*
 *  Allocates ION buffers
 *
 * @param descriptors     [in]    Buffer request descriptors
 * @param numDescriptors  [in]    Number of descriptors
 * @param pHandle         [out]   Handle for each allocated buffer
 * @param shared_backend  [out]   Shared buffers flag
 *
 * @return File handle which can be used for allocation, on success
 *         -1, otherwise.
 */
int mali_gralloc_ion_allocate(const gralloc_buffer_descriptor_t *descriptors,
                              uint32_t numDescriptors, buffer_handle_t *pHandle,
                              bool *shared_backend, bool use_placeholder)
{
	ATRACE_CALL();
	GRALLOC_UNUSED(shared_backend);

	unsigned int priv_heap_flag = 0;
	uint64_t usage;
	uint32_t i;

	for (i = 0; i < numDescriptors; i++)
	{
		buffer_descriptor_t *bufDescriptor = reinterpret_cast<buffer_descriptor_t *>(descriptors[i]);
		assert(bufDescriptor);
		assert(bufDescriptor->fd_count >= 0);
		assert(bufDescriptor->fd_count <= MAX_FDS);

		auto hnd = new private_handle_t(
		    priv_heap_flag,
		    bufDescriptor->alloc_sizes,
		    bufDescriptor->consumer_usage, bufDescriptor->producer_usage,
		    nullptr, bufDescriptor->fd_count,
		    bufDescriptor->hal_format, bufDescriptor->alloc_format,
		    bufDescriptor->width, bufDescriptor->height, bufDescriptor->pixel_stride,
		    bufDescriptor->layer_count, bufDescriptor->plane_info);

		/* Reset the number of valid filedescriptors, we will increment
		 * it each time a valid fd is added, so we can rely on the
		 * cleanup functions to close open fds. */
		hnd->set_numfds(0);

		if (nullptr == hnd)
		{
			MALI_GRALLOC_LOGE("Private handle could not be created for descriptor:%d in non-shared usecase", i);
			mali_gralloc_ion_free_internal(pHandle, i);
			return -1;
		}

		pHandle[i] = hnd;
		usage = bufDescriptor->consumer_usage | bufDescriptor->producer_usage;

		for (uint32_t fidx = 0; fidx < bufDescriptor->fd_count; fidx++)
		{
			int& fd = hnd->fds[fidx];

			fd = alloc_from_dmabuf_heap(usage, bufDescriptor->alloc_sizes[fidx], bufDescriptor->name, use_placeholder);

			if (fd < 0)
			{
				MALI_GRALLOC_LOGE("ion_alloc failed for fds[%u] = %d", fidx, fd);
				mali_gralloc_ion_free_internal(pHandle, i + 1);
				return -1;
			}

			hnd->incr_numfds(1);
		}
	}

	if (use_placeholder) return 0;

#if defined(GRALLOC_INIT_AFBC) && (GRALLOC_INIT_AFBC == 1)
	ATRACE_NAME("AFBC init block");
	unsigned char *cpu_ptr = NULL;
	for (i = 0; i < numDescriptors; i++)
	{
		buffer_descriptor_t *bufDescriptor = reinterpret_cast<buffer_descriptor_t *>(descriptors[i]);
		const private_handle_t *hnd = static_cast<const private_handle_t *>(pHandle[i]);

		usage = bufDescriptor->consumer_usage | bufDescriptor->producer_usage;

		if ((bufDescriptor->alloc_format & MALI_GRALLOC_INTFMT_AFBCENABLE_MASK)
			&& !(usage & GRALLOC_USAGE_PROTECTED))
		{
			{
				ATRACE_NAME("mmap");
				/* TODO: only map for AFBC buffers */
				cpu_ptr =
				    (unsigned char *)mmap(NULL, bufDescriptor->alloc_sizes[0], PROT_READ | PROT_WRITE, MAP_SHARED, hnd->fds[0], 0);

				if (MAP_FAILED == cpu_ptr)
				{
					MALI_GRALLOC_LOGE("mmap failed for fd ( %d )", hnd->fds[0]);
					mali_gralloc_ion_free_internal(pHandle, numDescriptors);
					return -1;
				}

				mali_gralloc_ion_sync_start(hnd, true, true);
			}

			{
				ATRACE_NAME("data init");
				/* For separated plane YUV, there is a header to initialise per plane. */
				const plane_info_t *plane_info = bufDescriptor->plane_info;
				assert(plane_info);
				const bool is_multi_plane = hnd->is_multi_plane();
				for (int i = 0; i < MAX_PLANES && (i == 0 || plane_info[i].byte_stride != 0); i++)
				{
					init_afbc(cpu_ptr + plane_info[i].offset,
					          bufDescriptor->alloc_format,
					          is_multi_plane,
					          plane_info[i].alloc_width,
					          plane_info[i].alloc_height);
				}
			}

			{
				ATRACE_NAME("munmap");
				mali_gralloc_ion_sync_end(hnd, true, true);
				munmap(cpu_ptr, bufDescriptor->alloc_sizes[0]);
			}
		}
	}
#endif

	return 0;
}

std::array<void*, MAX_BUFFER_FDS> mali_gralloc_ion_map(private_handle_t *hnd)
{
	std::array<void*, MAX_BUFFER_FDS> vaddrs;
	vaddrs.fill(nullptr);

	uint64_t usage = hnd->producer_usage | hnd->consumer_usage;
	/* Do not allow cpu access to secure buffers */
	if (usage & (GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_NOZEROED)
			&& !(usage & GRALLOC_USAGE_PRIVATE_NONSECURE))
	{
		return vaddrs;
	}

	for (int fidx = 0; fidx < hnd->fd_count; fidx++) {
		unsigned char *mappedAddress =
			(unsigned char *)mmap(NULL, hnd->alloc_sizes[fidx], PROT_READ | PROT_WRITE,
					MAP_SHARED, hnd->fds[fidx], 0);

		if (MAP_FAILED == mappedAddress)
		{
			int err = errno;
			MALI_GRALLOC_LOGE("mmap( fds[%d]:%d size:%" PRIu64 " ) failed with %s",
					fidx, hnd->fds[fidx], hnd->alloc_sizes[fidx], strerror(err));
			hnd->dump("map fail");

			for (int cidx = 0; cidx < fidx; fidx++)
			{
				munmap((void*)vaddrs[cidx], hnd->alloc_sizes[cidx]);
				vaddrs[cidx] = 0;
			}

			return vaddrs;
		}

		vaddrs[fidx] = mappedAddress;
	}

	return vaddrs;
}

void mali_gralloc_ion_unmap(private_handle_t *hnd, std::array<void*, MAX_BUFFER_FDS>& vaddrs)
{
	for (int i = 0; i < hnd->fd_count; i++)
	{
		int err = 0;

		if (vaddrs[i])
		{
			err = munmap(vaddrs[i], hnd->alloc_sizes[i]);
		}

		if (err)
		{
			MALI_GRALLOC_LOGE("Could not munmap base:%p size:%" PRIu64 " '%s'",
					(void*)vaddrs[i], hnd->alloc_sizes[i], strerror(errno));
		}
		else
		{
			vaddrs[i] = 0;
		}
	}

	hnd->cpu_read = 0;
	hnd->cpu_write = 0;
}
