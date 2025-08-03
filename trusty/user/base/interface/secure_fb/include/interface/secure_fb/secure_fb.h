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
#include <stddef.h>
#include <stdint.h>

__BEGIN_CDECLS

/*
 * Maximum number of instances that can be created by the secure fb server
 * implementation.
 */
#define SECURE_FB_MAX_INST 4

/*
 * The name of the created secure fb service will be
 * "SECURE_FB_PORT_NAME"."idx", where idx is 0, 1, ..., SECURE_FB_MAX_INST-1
 * The number of the instance is a variable depends on how many physical screen
 * the system has. The index of the instances is consecutive and should start
 * from 0 to SECURE_FB_MAX_INST-1.
 */
#define SECURE_FB_PORT_NAME "com.android.trusty.secure_fb"

/*
 * The maximum size of the secure_fb port name. The size includes the base name
 * with ".", "x" and a null terminator where x is the single digit for
 * identifying instances.
 */
#define SECURE_FB_MAX_PORT_NAME_SIZE (strlen(SECURE_FB_PORT_NAME) + 3)

/*
 * Maximum number of framebuffers that can be allocated by one request to get
 * framebuffers. Reasonable implementations would use one or two buffers.
 * However, we also give some room for more exotic implementations.
 */
#define SECURE_FB_MAX_FBS 4

/**
 * enum secure_fb_cmd - command identifiers for secure_fb interface
 * @SECURE_FB_CMD_RESP_BIT:
 *      Message is a response.
 * @SECURE_FB_CMD_REQ_SHIFT:
 *      Number of bits used by @SECURE_FB_CMD_RESP_BIT.
 * @SECURE_FB_CMD_GET_FBS:
 *      Allocate up to %SECURE_FB_MAX_FBS framebuffers and send them to the
 *      caller using shared memory handles.
 * @SECURE_FB_CMD_DISPLAY_FB:
 *      Select one framebuffer as active scan out region or update the screen
 *      with the selected framebuffer. On the first call of a session, the
 *      service must finalize the initialization of the secure output pipeline.
 * @SECURE_FB_CMD_RELEASE:
 *      Free up all resources and relinquish control over the secure output
 *      pipeline.
 */
enum secure_fb_cmd {
    SECURE_FB_CMD_RESP_BIT = 1,
    SECURE_FB_CMD_REQ_SHIFT = 1,
    SECURE_FB_CMD_GET_FBS = (1 << SECURE_FB_CMD_REQ_SHIFT),
    SECURE_FB_CMD_DISPLAY_FB = (2 << SECURE_FB_CMD_REQ_SHIFT),
    SECURE_FB_CMD_RELEASE = (3 << SECURE_FB_CMD_REQ_SHIFT),
};

/**
 * enum secure_fb_pixel_format - idenitifiers for pixel format
 * @TTUI_PF_INVALID:
 *      Denotes invalid value.
 * @TTUI_PF_RGBA8:
 *      Pixel format with 8 bits per channel such that a pixel can be
 *      represented as uint32_t 0xAABBGGRR with:
 *      AA - Alpha channel
 *      BB - Blue channel
 *      GG - Green channel
 *      RR - Red channel
 */
enum secure_fb_pixel_format {
    TTUI_PF_INVALID = 0,
    TTUI_PF_RGBA8 = 1,
};

/*
 * @brief drawing rotation
 */
/**
 * enum secure_fb_rotation - secure fb draw rotation
 * @TTUI_DRAW_ROTATION_0:
 *      no rotation needed.
 * @TTUI_DRAW_ROTATION_90:
 *      90" draw rotation required.
 * @TTUI_DRAW_ROTATION_180:
 *      180" draw rotation required.
 * @TTUI_DRAW_ROTATION_270:
 *      270" draw rotation required.
 */
enum secure_fb_rotation {
    TTUI_DRAW_ROTATION_0,
    TTUI_DRAW_ROTATION_90,
    TTUI_DRAW_ROTATION_180,
    TTUI_DRAW_ROTATION_270,
};

/**
 * struct secure_fb_info - information about framebuffer's topology
 * @buffer:        Start of the framebuffer. Unused when used as wire type.
 * @size:          Size of the framebuffer in bytes.
 * @pixel_stride:  Distance between the beginning of two adjacent pixels in
 *                 bytes.
 * @line_stride:   Distance between the beginning of two lines in bytes.
 * @width:         Width of the framebuffer in pixels.
 * @height:        Height of the framebuffer in pixels.
 * @pixel_format:  Pixel format. (should be TTUI_PF_RGBA8)
 * @rotation:      Draw rotation. (should be secure_fb_rotation)
 * @display_index: Index of the display. The first display is 0.
 */
struct secure_fb_info {
    uint8_t* buffer;
    uint32_t size;
    uint32_t pixel_stride;
    uint32_t line_stride;
    uint32_t width;
    uint32_t height;
    uint32_t pixel_format;
    uint32_t rotation;
    uint32_t display_index;
};

/**
 * struct secure_fb_desc - framebuffer descriptor
 * @buffer_id:
 *      Numeric identifier of the buffer. This id is used to select the next
 *      active buffer using %SECURE_FB_CMD_DISPLAY_FB.
 * @handle_index:
 *      An allocation response may result in up to %SECURE_FB_MAX_FBS allocated
 *      buffers. These may be transmitted using up to %SECURE_FB_MAX_FBS
 *      handles. However, multiple buffers may be transmitted with a single
 *      handle. To this end each buffer has a handle index and offset. The
 *      handle_index indicates which allocation this buffer is part of, and the
 *      offset indicates how from the beginning of that allocation this buffer
 *      starts in bytes.
 * @offset:
 *      See handle_index above.
 * @fb_info:
 *      Describes buffer's topology.
 */
struct secure_fb_desc {
    uint32_t buffer_id;
    uint32_t handle_index;
    uint32_t offset;
    struct secure_fb_info fb_info;
};

/**
 * struct secure_fb_req - common structure for secure_fb requests.
 * @cmd: Command identifier - one of &enum secure_fb_cmd.
 */
struct secure_fb_req {
    uint32_t cmd;
};

/**
 * struct secure_fb_resp - common structure for secure_fb responses.
 * @cmd:    Command identifier - %SECURE_FB_CMD_RESP_BIT or'ed with the command
 *          identifier of the corresponding request.
 * @status: Status of requested operation. One of &enum secure_fb_service_error.
 */
struct secure_fb_resp {
    uint32_t cmd;
    int32_t status;
};

/**
 * struct secure_fb_get_fbs_resp - payload for %SECURE_FB_CMD_GET_FBS response
 * @num_fbs: Number of framebuffers, at most %SECURE_FB_MAX_FBS.
 * @fbs:     Descriptors of allocated framebuffers.
 */
struct secure_fb_get_fbs_resp {
    uint32_t num_fbs;
    struct secure_fb_desc fbs[];
};

/**
 * struct secure_fb_display_fb_req - payload for %SECURE_FB_CMD_DISPLAY_FB
 *                                   request
 * @buffer_id: ID of a framebuffer previously allocated with
 *             %SECURE_FB_CMD_GET_FBS.
 */
struct secure_fb_display_fb_req {
    uint32_t buffer_id;
};

enum secure_fb_service_error {
    SECURE_FB_ERROR_OK = 0,
    SECURE_FB_ERROR_UNINITIALIZED = -2,
    SECURE_FB_ERROR_PARAMETERS = -3,
    SECURE_FB_ERROR_INVALID_REQUEST = -4,
    SECURE_FB_ERROR_MEMORY_ALLOCATION = -5,
    SECURE_FB_ERROR_SHARED_MEMORY = -6,
    SECURE_FB_ERROR_DMA = -7,
    SECURE_FB_ERROR_OUT_OF_RANGE = -8,
    SECURE_FB_ERROR_UNSUPPORTED_PANEL = -9,
    SECURE_FB_ERROR_HARDWARE_ERROR = -10000,
};

/**
 * hardware_error() - Is used to propagate driver errors to secure_fb client.
 * Hardware/Driver errors in the range of (-10000, -1] get mapped to the range
 * (-20000, -10001]. All other codes get mapped to the generic hardware error
 * SECURE_FB_ERROR_HARDWARE_ERROR = -10000.
 *
 * @e: A hardware error value.
 *
 * Return: Recoded hardware error.
 */
static inline int32_t hardware_error(int32_t e) {
    if (e < 0 && e > SECURE_FB_ERROR_HARDWARE_ERROR) {
        return SECURE_FB_ERROR_HARDWARE_ERROR + e;
    } else {
        return SECURE_FB_ERROR_HARDWARE_ERROR;
    }
}

__END_CDECLS
