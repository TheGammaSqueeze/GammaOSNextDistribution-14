/*
 * Copyright 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <lk/compiler.h>
#include <stdint.h>

#include <interface/secure_fb/secure_fb.h>

__BEGIN_CDECLS

typedef enum {
    TTUI_ERROR_OK = 0,
    TTUI_ERROR_NO_FRAMEBUFFER,
    TTUI_ERROR_MEMORY_ALLOCATION_FAILED,
    TTUI_ERROR_UNEXPECTED_NULL_PTR,
    TTUI_ERROR_NO_SERVICE,
} secure_fb_error;

typedef void* secure_fb_handle_t;

/**
 * secure_fb_open() - Open a new secure framebuffer session. If returns
 * TUI_ERROR_OK, the given @fb_info is filled with valid framebuffer
 * information. Valid means:
 * * fb_info->buffer points to a writable region of memory of at least
 *   fb_info->size bytes length.
 * * fb_info->pixel_stride is greater or equal to the required width for the
 *   pixel format indicated in fb_info->pixel_format.
 * * fb_info->width * fb_info->pixel_stride <= fb_info->line_stride.
 * * fb_info->height * fb_info->line_stride <= fb_info->size.
 *
 * Above this, the frame buffer dimensions must be such that the frame buffer
 * fills the whole primary device screen.
 *
 * @session: A pointer that will be initialized with the new session context.
 * @fb_info: Output parameter that holds the framebuffer description of the
 *           next framebuffer that will be displayed on the next call to
 *           secure_fb_display_next().
 * @idx:     Index of the secure_fb corresponding to each physical display.
 *           The index starts from 0 and up to SECURE_FB_MAX_INST-1. The client
 *           should call the function with idx starting from 0 and keep
 *           increasing the idx up to either SECURE_FB_MAX_INST-1 or the
 *           function returns TTUI_ERROR_NO_SERVICE.
 * Return:
 * TTUI_ERROR_OK - on success.
 * TTUI_ERROR_NO_FRAMEBUFFER - if no next framebuffer could be found.
 * TTUI_ERROR_MEMORY_ALLOCATION_FAILED - if any memory allocation failed.
 * TTUI_ERROR_UNEXPECTED_NULL_PTR - if the a parameter was NULL.
 * TTUI_ERROR_NO_SERVICE - if the idx exceeds the maximum number of the physical
 *                         display that the systen supports.
 */
secure_fb_error secure_fb_open(secure_fb_handle_t* session,
                               struct secure_fb_info* fb_info,
                               uint32_t idx);

/**
 * secure_fb_display_next() - Indicates to the subsystem that the next buffer
 * is ready to be displayed. The next buffer is always the last buffer returned
 * by secure_fb_open() or secure_fb_display_next(). The content of the
 * structure pointed to by @fb_info is ignored and replaced with a new
 * off-screen framebuffer, that the caller can use to render the next frame. If
 * return TUI_ERROR_OK:
 * * The last buffer returned by secure_fb_open() or secure_fb_display_next()
 *   gets displayed.
 * * The first call to this routine starts the TUI session, i.e. the secure
 *   output path is configured and verified:
 * * The power supply to the display panel and controller gets sanitized and
 *   locked.
 * * The display controller's secure resources get locked and configured for
 *   secure output.
 * * The display controller's state gets sanitized.
 * * The framebuffer gets configured as the secure scanout region.
 *
 * @session: A session handle as created by secure_fb_open().
 * @fb_info: Output parameter that holds the frame buffer description for the
 *           next frame buffer.
 *
 * Return:
 * TTUI_ERROR_OK - on success.
 * TTUI_ERROR_NO_FRAMEBUFFER - if no next framebuffer could be found.
 * TTUI_ERROR_MEMORY_ALLOCATION_FAILED - if any memory allocation failed.
 * TTUI_ERROR_UNEXPECTED_NULL_PTR - if the a parameter was NULL.
 */
secure_fb_error secure_fb_display_next(secure_fb_handle_t session,
                                       struct secure_fb_info* fb_info);

/**
 * secure_fb_close() -  Wipe the secure frame buffers. Relinquishes control over
 * secure display resources. If secure_fb_close() encounters any irregularity it
 * does not return but causes the SOC to reset.
 *
 * @session: A session handle as created by secure_fb_open.
 */
void secure_fb_close(secure_fb_handle_t session);

__END_CDECLS
