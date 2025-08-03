/*
 * Copyright (C) 2020 The Android Open Source Project
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

#pragma once

#include <stdint.h>

#define APPLOADER_SECURE_PORT "com.android.trusty.apploader.secure"

/**
 * Secure world-only commands
 */
enum apploader_secure_command : uint32_t {
    APPLOADER_SECURE_REQ_SHIFT = 1,
    APPLOADER_SECURE_RESP_BIT = 1,

    APPLOADER_SECURE_CMD_GET_MEMORY = (0 << APPLOADER_SECURE_REQ_SHIFT),
    APPLOADER_SECURE_CMD_LOAD_APPLICATION = (1 << APPLOADER_SECURE_REQ_SHIFT),
};

/**
 * apploader_secure_header - Serial header for communicating with apploader
 * @cmd: the command; one of &enum apploader_secure_command values.
 */
struct apploader_secure_header {
    uint32_t cmd;
} __PACKED;

/**
 * apploader_secure_get_memory_req - Serial header for GET_MEMORY command
 * @package_size: the size of the application package.
 *
 * Request a kernel memory region of size @package_size from the service.
 *
 * The response is a &struct apploader_secure_resp with the error code or
 * %APPLOADER_NO_ERROR on success. The response also contains exactly one
 * handle to the memref for the allocated region.
 */
struct apploader_secure_get_memory_req {
    uint64_t package_size;
} __PACKED;

/**
 * apploader_secure_load_app_req - Serial header for LOAD_APPLICATION command
 * @manifest_start: offset of the start of the manifest, relative to the start
 *                  of the package.
 * @manifest_end: offset of the end of the manifest.
 * @img_start: offset of the start of the ELF image.
 * @img_end: offset of the end of the ELF image.
 *
 * Load an application from a memory region previously returned by
 * apploader_secure_get_memory_req. The service will internally keep a
 * reference to this region across requests. @manifest_start and the other
 * offsets point into this region.
 *
 * The response is a &struct apploader_secure_resp with the error code or
 * %APPLOADER_NO_ERROR on success.
 */
struct apploader_secure_load_app_req {
    uint64_t manifest_start;
    uint64_t manifest_end;
    uint64_t img_start;
    uint64_t img_end;
} __PACKED;

/**
 * apploader_resp - Common header for all secure world apploader responses
 * @hdr - header with command value.
 * @error - error code returned by peer; one of &enum apploader_error values.
 *
 * This structure is followed by the response-specific payload, if the command
 * has one.
 */
struct apploader_secure_resp {
    struct apploader_secure_header hdr;
    uint32_t error;
} __PACKED;
