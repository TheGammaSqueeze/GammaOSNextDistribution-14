/*
 * Copyright (C) 2017-2020 ARM Limited. All rights reserved.
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
#ifndef MALI_GRALLOC_BUFFER_H_
#define MALI_GRALLOC_BUFFER_H_

#include <stdint.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <sys/mman.h>
#include <cutils/native_handle.h>
#include <string.h>
#include <log/log.h>
#include <inttypes.h>

/* the max string size of GRALLOC_HARDWARE_GPU0 & GRALLOC_HARDWARE_FB0
 * 8 is big enough for "gpu0" & "fb0" currently
 */
#define MALI_GRALLOC_HARDWARE_MAX_STR_LEN 8

/* Define number of shared file descriptors. Not guaranteed to be constant for a private_handle_t object
 * as fds that do not get initialized may instead be treated as integers.
 */

#define NUM_INTS_IN_PRIVATE_HANDLE ((sizeof(struct private_handle_t) - sizeof(native_handle)) / sizeof(int))

#define SZ_4K 0x00001000
#define SZ_2M 0x00200000

/*
 * Maximum number of pixel format planes.
 * Plane [0]: Single plane formats (inc. RGB, YUV) and Y
 * Plane [1]: U/V, UV
 * Plane [2]: V/U
 */
#define MAX_PLANES 3

/*
 * Maximum number of fds in a private_handle_t.
 */
#define MAX_FDS 4

/*
 * One fd is reserved for metadata dmabuf.
 */
#define MAX_BUFFER_FDS MAX_FDS - 1

/*
 * In the worst case, there will be one plane per fd.
 */
static_assert(MAX_BUFFER_FDS == MAX_PLANES, "MAX_PLANES and MAX_BUFFER_FDS defines do not match");

#ifdef __cplusplus
#define DEFAULT_INITIALIZER(x) = x
#else
#define DEFAULT_INITIALIZER(x)
#endif

typedef struct plane_info {

	/*
	 * Offset to plane (in bytes),
	 * from the start of the allocation.
	 */
	int64_t offset;

	uint32_t fd_idx;
	uint64_t size;

	/*
	 * Byte Stride: number of bytes between two vertically adjacent
	 * pixels in given plane. This can be mathematically described by:
	 *
	 * byte_stride = ALIGN((alloc_width * bpp)/8, alignment)
	 *
	 * where,
	 *
	 * alloc_width: width of plane in pixels (c.f. pixel_stride)
	 * bpp: average bits per pixel
	 * alignment (in bytes): dependent upon pixel format and usage
	 *
	 * For uncompressed allocations, byte_stride might contain additional
	 * padding beyond the alloc_width. For AFBC, alignment is zero.
	 */
	uint64_t byte_stride;

	/*
	 * Dimensions of plane (in pixels).
	 *
	 * For single plane formats, pixels equates to luma samples.
	 * For multi-plane formats, pixels equates to the number of sample sites
	 * for the corresponding plane, even if subsampled.
	 *
	 * AFBC compressed formats: requested width/height are rounded-up
	 * to a whole AFBC superblock/tile (next superblock at minimum).
	 * Uncompressed formats: dimensions typically match width and height
	 * but might require pixel stride alignment.
	 *
	 * See 'byte_stride' for relationship between byte_stride and alloc_width.
	 *
	 * Any crop rectangle defined by GRALLOC_ARM_BUFFER_ATTR_CROP_RECT must
	 * be wholly within the allocation dimensions. The crop region top-left
	 * will be relative to the start of allocation.
	 */
	uint64_t alloc_width;
	uint64_t alloc_height;
} plane_info_t;

struct private_handle_t;

#ifndef __cplusplus
/* C99 with pedantic don't allow anonymous unions which is used in below struct
 * Disable pedantic for C for this struct only.
 */
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wpedantic"
#endif

#ifdef __cplusplus
struct private_handle_t : public native_handle
{
private:
	/* Having a default constructor makes sure that we zero out the padding
	 * which prevents data leak. */
	private_handle_t() = default;

public:

#else
struct private_handle_t
{
	struct native_handle nativeHandle;
#endif

#ifdef __cplusplus
	/* Never intended to be used from C code */
	enum
	{
		PRIV_FLAGS_USES_2PRIVATE_DATA = 1U << 4,
		PRIV_FLAGS_USES_3PRIVATE_DATA = 1U << 5,
	};

	enum
	{
		LOCK_STATE_WRITE = 1 << 31,
		LOCK_STATE_MAPPED = 1 << 30,
		LOCK_STATE_READ_MASK = 0x3FFFFFFF
	};
#endif

	/*
	 * Shared file descriptor for dma_buf sharing. This must be the first element in the
	 * structure so that binder knows where it is and can properly share it between
	 * processes.
	 * DO NOT MOVE THIS ELEMENT!
	 */
	union {
		int fds[MAX_FDS];
	};

	// ints
	int magic DEFAULT_INITIALIZER(sMagic);
	int flags DEFAULT_INITIALIZER(0);

	/*
	 * Number of dmabuf fds, NOT including the metadata fd
	 */
	int fd_count DEFAULT_INITIALIZER(1);

	/*
	 * Input properties.
	 *
	 * req_format: Pixel format, base + private modifiers.
	 * width/height: Buffer dimensions.
	 * producer/consumer_usage: Buffer usage (indicates IP)
	 */
	int width DEFAULT_INITIALIZER(0);
	int height DEFAULT_INITIALIZER(0);
	int req_format DEFAULT_INITIALIZER(0);
	uint64_t producer_usage DEFAULT_INITIALIZER(0);
	uint64_t consumer_usage DEFAULT_INITIALIZER(0);

	/*
	 * DEPRECATED members.
	 * Equivalent information can be obtained from other fields:
	 *
	 * - 'internal_format' --> alloc_format
	 * - 'stride' (pixel stride) ~= plane_info[0].alloc_width
	 * - 'byte_stride' ~= plane_info[0].byte_stride
	 * - 'internalWidth' ~= plane_info[0].alloc_width
	 * - 'internalHeight' ~= plane_info[0].alloc_height
	 *
	 * '~=' (approximately equal) is used because the fields were either previously
	 * incorrectly populated by gralloc or the meaning has slightly changed.
	 *
	 * NOTE: 'stride' values sometimes vary significantly from plane_info[0].alloc_width.
	 */
	uint64_t stride DEFAULT_INITIALIZER(0);

	/*
	 * Allocation properties.
	 *
	 * alloc_format: Pixel format (base + modifiers). NOTE: base might differ from requested
	 *               format (req_format) where fallback to single-plane format was required.
	 * plane_info:   Per plane allocation information.
	 * size:         Total bytes allocated for buffer (inc. all planes, layers. etc.).
	 * layer_count:  Number of layers allocated to buffer.
	 *               All layers are the same size (in bytes).
	 *               Multi-layers supported in v1.0, where GRALLOC1_CAPABILITY_LAYERED_BUFFERS is enabled.
	 *               Layer size: 'size' / 'layer_count'.
	 *               Layer (n) offset: n * ('size' / 'layer_count'), n=0 for the first layer.
	 *
	 */
	uint64_t alloc_format DEFAULT_INITIALIZER(0);
	plane_info_t plane_info[MAX_PLANES] DEFAULT_INITIALIZER({});
	uint32_t layer_count DEFAULT_INITIALIZER(0);

	uint64_t backing_store_id DEFAULT_INITIALIZER(0x0);
	int cpu_read DEFAULT_INITIALIZER(0);               /**< Buffer is locked for CPU read when non-zero. */
	int cpu_write DEFAULT_INITIALIZER(0);              /**< Buffer is locked for CPU write when non-zero. */
	// locally mapped shared attribute area

	int ion_handles[MAX_BUFFER_FDS];
	uint64_t alloc_sizes[MAX_BUFFER_FDS];

	off_t offset    __attribute__((aligned (8))) DEFAULT_INITIALIZER(0);

	/* Size of the attribute shared region in bytes. */
	uint64_t attr_size __attribute__((aligned (8))) DEFAULT_INITIALIZER(0);

	uint64_t reserved_region_size DEFAULT_INITIALIZER(0);

	uint64_t imapper_version DEFAULT_INITIALIZER(0);

#ifdef __cplusplus
	/*
	 * We track the number of integers in the structure. There are 16 unconditional
	 * integers (magic - pid, yuv_info, fd and offset). Note that the fd element is
	 * considered an int not an fd because it is not intended to be used outside the
	 * surface flinger process. The GRALLOC_ARM_NUM_INTS variable is used to track the
	 * number of integers that are conditionally included. Similar considerations apply
	 * to the number of fds.
	 */
	static const int sMagic = 0x3141592;

	private_handle_t(
		int _flags,
		uint64_t _alloc_sizes[MAX_BUFFER_FDS],
		uint64_t _consumer_usage, uint64_t _producer_usage,
		int _fds[MAX_FDS], int _fd_count,
		int _req_format, uint64_t _alloc_format,
		int _width, int _height, uint64_t _stride,
		uint64_t _layer_count, plane_info_t _plane_info[MAX_PLANES])
	    : private_handle_t()
	{
		flags = _flags;
		fd_count = _fd_count;
		width = _width;
		height = _height;
		req_format = _req_format;
		producer_usage = _producer_usage;
		consumer_usage = _consumer_usage;
		stride = _stride;
		alloc_format = _alloc_format;
		layer_count = _layer_count;
		version = sizeof(native_handle);
		set_numfds(fd_count);
		memcpy(plane_info, _plane_info, sizeof(plane_info_t) * MAX_PLANES);

		if (_fds)
			memcpy(fds, _fds, sizeof(fds));
		else
			memset(fds, -1, sizeof(fds));

		if (_alloc_sizes)
			memcpy(alloc_sizes, _alloc_sizes, sizeof(alloc_sizes));

		memset(ion_handles, 0, sizeof(ion_handles));
	}

	~private_handle_t()
	{
		magic = 0;
	}

	/* Set the number of allocated fds in the handle to n*/
	void set_numfds(int n)
	{
		int total_ints =
			(sizeof(struct private_handle_t) - sizeof(native_handle)) / sizeof(int);

		numFds = n;
		numInts = total_ints - n;
	}

	/* Increments number of allocated fds in the handle by n */
	int incr_numfds(int n)
	{
		numFds += n;
		numInts -= n;

		return numFds;
	}

	static int validate(const native_handle *h)
	{
		const private_handle_t *hnd = (const private_handle_t *)h;

		// Always validate for native_handle members before validating
		// private_handle_t members
		if (!h || h->version != sizeof(native_handle) ||
		    h->numFds + h->numInts != NUM_INTS_IN_PRIVATE_HANDLE)
		{
			return -EINVAL;
		}

		if (hnd->magic != sMagic)
		{
			return -EINVAL;
		}

		return 0;
	}

	bool is_multi_plane() const
	{
		/* For multi-plane, the byte stride for the second plane will always be non-zero. */
		return (plane_info[1].byte_stride != 0);
	}

	uint64_t get_usage() const
	{
		return producer_usage | consumer_usage;
	}

	int get_share_attr_fd_index() const
	{
		/* share_attr can be at idx 1 to MAX_FDS */
		if (fd_count <= 0 || fd_count > MAX_FDS)
			return -1;

		return fd_count;
	}

	int get_share_attr_fd() const
	{
		int idx = get_share_attr_fd_index();

		if (idx <= 0)
			return -1;

		return fds[idx];
	}

	void set_share_attr_fd(int fd)
	{
		int idx = get_share_attr_fd_index();

		if (idx <= 0)
			return;

		fds[idx] = fd;
	}

	void close_share_attr_fd()
	{
		int fd = get_share_attr_fd();

		if (fd < 0)
			return;

		close(fd);
	}

	void dump(const char *str) const
	{
		ALOGE("[%s] "
			"numInts(%d) numFds(%d) fd_count(%d) "
			"fd(%d %d %d %d) "
			"flags(%d) "
			"wh(%d %d) "
			"req_format(%#x) alloc_format(%#" PRIx64 ") "
			"usage_pc(0x%" PRIx64 " 0x%" PRIx64 ") "
			"stride(%" PRIu64 ") "
			"psize(%" PRIu64 ") byte_stride(%" PRIu64 ") internal_wh(%" PRIu64 " %" PRIu64 ") "
			"psize1(%" PRIu64 ") byte_stride1(%" PRIu64 ") internal_wh1(%" PRIu64 " %" PRIu64 ") "
			"psize2(%" PRIu64 ") byte_stride2(%" PRIu64 ") internal_wh2(%" PRIu64 " %" PRIu64 ") "
			"alloc_format(0x%" PRIx64 ") "
			"alloc_sizes(%" PRIu64 " %" PRIu64 " %" PRIu64 ") "
			"layer_count(%d) "
			"\n",
			str,
			numInts, numFds, fd_count,
			fds[0], fds[1], fds[2], fds[3],
			flags,
			width, height,
			req_format, alloc_format,
			producer_usage, consumer_usage,
			stride,
			plane_info[0].size, plane_info[0].byte_stride, plane_info[0].alloc_width, plane_info[0].alloc_height,
			plane_info[1].size, plane_info[1].byte_stride, plane_info[1].alloc_width, plane_info[1].alloc_height,
			plane_info[2].size, plane_info[2].byte_stride, plane_info[2].alloc_width, plane_info[2].alloc_height,
			alloc_format,
			alloc_sizes[0], alloc_sizes[1], alloc_sizes[2],
			layer_count
		);
	}

	int get_alloc_format() const
	{
		return (int)(alloc_format & 0x00000000ffffffffULL);
	}

	static private_handle_t *dynamicCast(const native_handle *in)
	{
		if (validate(in) == 0)
		{
			return (private_handle_t *)in;
		}

		return NULL;
	}
#endif
};
#ifndef __cplusplus
/* Restore previous diagnostic for pedantic */
#pragma GCC diagnostic pop
#endif

// The size of private_handle_t is calculated manually. This check ensures that private_handle_t has
// the same layout for 32-bit and 64-bit processes.
static_assert(sizeof(private_handle_t) == 328);

#endif /* MALI_GRALLOC_BUFFER_H_ */
