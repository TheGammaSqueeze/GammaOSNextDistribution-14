/*
 * Copyright (C) 2016-2020 ARM Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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
#include <errno.h>
#include <inttypes.h>
#include <inttypes.h>
/* For error codes. */
#include <hardware/gralloc1.h>

#include "mali_gralloc_buffer.h"
#include "mali_gralloc_formats.h"
#include "mali_gralloc_usages.h"
#include "mali_gralloc_reference.h"
#include "allocator/mali_gralloc_ion.h"
#include "gralloc_helper.h"
#include "format_info.h"


enum tx_direction
{
	TX_NONE = 0,
	TX_TO_DEVICE,
	TX_FROM_DEVICE,
	TX_BOTH,
};


static enum tx_direction get_tx_direction(const uint64_t usage)
{
	const bool read = (usage & GRALLOC_USAGE_SW_READ_MASK) ? true : false;
	const bool write = (usage & GRALLOC_USAGE_SW_WRITE_MASK) ? true : false;
	enum tx_direction dir = TX_NONE;

	if (read && write)
	{
		dir = TX_BOTH;
	}
	else if (write)
	{
		dir = TX_TO_DEVICE;
	}
	else if (read)
	{
		dir = TX_FROM_DEVICE;
	}

	return dir;
}

static void buffer_sync(private_handle_t * const hnd,
                        const enum tx_direction direction)
{
	if (direction != TX_NONE)
	{
		hnd->cpu_read = (direction == TX_FROM_DEVICE || direction == TX_BOTH) ? 1 : 0;
		hnd->cpu_write = (direction == TX_TO_DEVICE || direction == TX_BOTH) ? 1 : 0;

#if defined(GRALLOC_ION_SYNC_ON_LOCK) && GRALLOC_ION_SYNC_ON_LOCK == 1
		const int status = mali_gralloc_ion_sync_start(hnd,
		                                               hnd->cpu_read ? true : false,
		                                               hnd->cpu_write ? true : false);
		if (status < 0)
		{
			return;
		}
#endif
	}
	else if (hnd->cpu_read || hnd->cpu_write)
	{
#if defined(GRALLOC_ION_SYNC_ON_LOCK) && GRALLOC_ION_SYNC_ON_LOCK == 1
		const int status = mali_gralloc_ion_sync_end(hnd,
		                                             hnd->cpu_read ? true : false,
		                                             hnd->cpu_write ? true : false);
		if (status < 0)
		{
			return;
		}
#endif
		hnd->cpu_read = 0;
		hnd->cpu_write = 0;
	}
}


/*
 *  Validates input parameters of lock request.
 *
 * @param buffer   [in]    The buffer to lock.
 * @param l        [in]    Access region left offset (in pixels).
 * @param t        [in]    Access region top offset (in pixels).
 * @param w        [in]    Access region requested width (in pixels).
 * @param h        [in]    Access region requested height (in pixels).
 * @param usage    [in]    Lock request (producer and consumer combined) usage.
 *
 * @return 0,for valid input parameters;
 *         -EINVAL, for erroneous input parameters
 */
int validate_lock_input_parameters(const buffer_handle_t buffer, const int l,
                                   const int t, const int w, const int h,
                                   uint64_t usage)
{
	const private_handle_t * const hnd = (private_handle_t *)buffer;

	/* TODO: do not check access region for blob formats.
	 * This is because codec2 attempts to lock with wrong access region.
	 * Ask Google to fix codec2
	 */
	if (hnd->get_alloc_format() != HAL_PIXEL_FORMAT_BLOB)
	{
		if ((l < 0) || (t < 0) || (w < 0) || (h < 0))
		{
			MALI_GRALLOC_LOGW("Negative values for access region (l = %d t = %d w = %d and "
			     "h = %d) in buffer lock request are invalid.", l, t, w, h);
			return -EINVAL;
		}

		/* Test overflow conditions on access region parameters */
		if (((l + w) < 0) || ((t + h) < 0))
		{
			MALI_GRALLOC_LOGW("Encountered overflow with access region (l = %d t = %d w = %d and"
			     " h = %d) in buffer lock request.", l, t, w, h);
			return -EINVAL;
		}

		/* Region of interest shall be inside the allocated buffer */
		if (((t + h) > hnd->height)  || ((l + w) > hnd->width))
		{
			MALI_GRALLOC_LOGW("Buffer lock access region (l = %d t = %d w = %d "
			     "and h = %d) is outside allocated buffer (width = %d and height = %d)",
			     l, t, w, h, hnd->width, hnd->height);
			return -EINVAL;
		}
	}

	/* Reject lock requests for AFBC (compressed format) enabled buffers */
	if ((hnd->alloc_format & MALI_GRALLOC_INTFMT_EXT_MASK) != 0)
	{
		MALI_GRALLOC_LOGE("Lock is not supported for AFBC enabled buffers."
		     "Internal Format:0x%" PRIx64, hnd->alloc_format);

		return GRALLOC1_ERROR_UNSUPPORTED;
	}

	/* Producer and consumer usage is verified in gralloc1 specific code. */
	GRALLOC_UNUSED(usage);

	return 0;
}


/*
 *  Locks the given buffer for the specified CPU usage.
 *
 * @param m        [in]    Gralloc module.
 * @param buffer   [in]    The buffer to lock.
 * @param usage    [in]    Producer and consumer combined usage.
 * @param l        [in]    Access region left offset (in pixels).
 * @param t        [in]    Access region top offset (in pixels).
 * @param w        [in]    Access region requested width (in pixels).
 * @param h        [in]    Access region requested height (in pixels).
 * @param vaddr    [out]   To be filled with a CPU-accessible pointer to
 *                         the buffer data for CPU usage.
 *
 * @return 0, when the locking is successful;
 *         Appropriate error, otherwise
 *
 * @Note:  There is no way to ascertain whether buffer data is valid or not (for
 *         example, establishing if the h/w needs to finish rendering or if CPU
 *         caches need to be synchronized).
 *
 * @Note:  Locking a buffer simultaneously for write or read/write leaves the
 *         buffer's content in an indeterminate state.
 */
int mali_gralloc_lock(buffer_handle_t buffer,
                      uint64_t usage, int l, int t, int w, int h, void **vaddr)
{
	int status;

	if (mali_gralloc_reference_validate(buffer))
	{
		MALI_GRALLOC_LOGE("Locking invalid buffer %p, returning error", buffer);
		return -EINVAL;
	}

	/* Validate input parameters for lock request */
	status = validate_lock_input_parameters(buffer, l, t, w, h, usage);
	if (status != 0)
	{
		return status;
	}

	private_handle_t *hnd = (private_handle_t *)buffer;

	const int32_t format_idx = get_format_index(hnd->alloc_format & MALI_GRALLOC_INTFMT_FMT_MASK);
	if (format_idx == -1)
	{
		MALI_GRALLOC_LOGE("Corrupted buffer format (%s 0x%" PRIx64 ") of buffer %p",
			format_name(hnd->alloc_format), hnd->alloc_format, hnd);
		return -EINVAL;
	}

	/* Populate CPU-accessible pointer when requested for CPU usage */
	if ((usage & (GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK)) != 0)
	{
		if (vaddr == NULL)
		{
			return -EINVAL;
		}

		if (mali_gralloc_reference_map(buffer) != 0) {
			return -EINVAL;
		}

		std::optional<void*> buf_addr = mali_gralloc_reference_get_buf_addr(buffer);
		if (!buf_addr.has_value()) {
			MALI_GRALLOC_LOGE("BUG: Invalid buffer address on a just mapped buffer");
			return -EINVAL;
		}
		*vaddr = buf_addr.value();

		buffer_sync(hnd, get_tx_direction(usage));
	}

	return 0;
}


/*
 *  Unlocks the given buffer.
 *
 * @param m           [in]   Gralloc module.
 * @param buffer      [in]   The buffer to unlock.
 *
 * @return 0, when the locking is successful;
 *         Appropriate error, otherwise
 *
 * Note: unlocking a buffer which is not locked results in an unexpected behaviour.
 *       Though it is possible to create a state machine to track the buffer state to
 *       recognize erroneous conditions, it is expected of client to adhere to API
 *       call sequence
 */
int mali_gralloc_unlock(buffer_handle_t buffer)
{
	if (private_handle_t::validate(buffer) < 0)
	{
		MALI_GRALLOC_LOGE("Unlocking invalid buffer %p, returning error", buffer);
		return -EINVAL;
	}

	private_handle_t *hnd = (private_handle_t *)buffer;
	buffer_sync(hnd, TX_NONE);

	return 0;
}
