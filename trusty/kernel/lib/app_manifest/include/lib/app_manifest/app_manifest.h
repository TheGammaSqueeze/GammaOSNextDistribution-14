/*
 * Copyright (c) 2021 Google Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#pragma once

#include <lk/compiler.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <uapi/trusty_uuid.h>

__BEGIN_CDECLS

/**
 * enum app_manifest_config_key - Manifest configuration entry keys
 * @APP_MANIFEST_CONFIG_KEY_MIN_STACK_SIZE: Key for "min_stack"
 * @APP_MANIFEST_CONFIG_KEY_MIN_HEAP_SIZE: Key for "min_heap"
 * @APP_MANIFEST_CONFIG_KEY_MAP_MEM: Key for memory maps
 * @APP_MANIFEST_CONFIG_KEY_MGMT_FLAGS: Key for "mgmt_flags"
 * @APP_MANIFEST_CONFIG_KEY_START_PORT: Key for "start_ports"
 * @APP_MANIFEST_CONFIG_KEY_PINNED_CPU: Key for "pinned_cpu"
 * @APP_MANIFEST_CONFIG_KEY_VERSION: Key for "version"
 * @APP_MANIFEST_CONFIG_KEY_MIN_SHADOW_STACK_SIZE: Key for "min_shadow_stack"
 * @APP_MANIFEST_CONFIG_KEY_APPLOADER: Key for "apploader_flags"
 * @APP_MANIFEST_CONFIG_KEY_PRIORITY: Key for "priority" value
 * @APP_MANIFEST_CONFIG_KEY_UUID: Pseudo-key for "uuid"
 * @APP_MANIFEST_CONFIG_KEY_APP_NAME: Pseudo-key for name of application
 * @APP_MANIFEST_CONFIG_KEY_MIN_VERSION: Key for "min_version"
 */
enum app_manifest_config_key {
    APP_MANIFEST_CONFIG_KEY_MIN_STACK_SIZE = 1,
    APP_MANIFEST_CONFIG_KEY_MIN_HEAP_SIZE = 2,
    APP_MANIFEST_CONFIG_KEY_MAP_MEM = 3,
    APP_MANIFEST_CONFIG_KEY_MGMT_FLAGS = 4,
    APP_MANIFEST_CONFIG_KEY_START_PORT = 5,
    APP_MANIFEST_CONFIG_KEY_PINNED_CPU = 6,
    APP_MANIFEST_CONFIG_KEY_VERSION = 7,
    APP_MANIFEST_CONFIG_KEY_MIN_SHADOW_STACK_SIZE = 8,
    APP_MANIFEST_CONFIG_KEY_APPLOADER_FLAGS = 9,
    APP_MANIFEST_CONFIG_KEY_PRIORITY = 10,
    APP_MANIFEST_CONFIG_KEY_MIN_VERSION = 11,

    /* Pseudo-keys for required entries */
    APP_MANIFEST_CONFIG_KEY_UUID = 0xfffffffeU,
    APP_MANIFEST_CONFIG_KEY_APP_NAME = 0xffffffffU,
};

/**
 * enum app_manifest_mgmt_flags - Masks for mgmt_flags configuration value
 * @APP_MANIFEST_MGMT_FLAGS_NONE:
 *      No flags
 * @APP_MANIFEST_MGMT_FLAGS_RESTART_ON_EXIT:
 *      Restart the application on exit
 * @APP_MANIFEST_MGMT_FLAGS_DEFERRED_START:
 *      Don't start the application at boot
 * @APP_MANIFEST_MGMT_FLAGS_NON_CRITICAL_APP:
 *      Exit application if application crashes or exit with a non-0 exit code
 */
enum app_manifest_mgmt_flags {
    APP_MANIFEST_MGMT_FLAGS_NONE = 0u,
    APP_MANIFEST_MGMT_FLAGS_RESTART_ON_EXIT = (1u << 0),
    APP_MANIFEST_MGMT_FLAGS_DEFERRED_START = (1u << 1),
    APP_MANIFEST_MGMT_FLAGS_NON_CRITICAL_APP = (1u << 2),
};

/**
 * enum app_manifest_apploader_flags - Masks for apploader_flags configuration
 *                                     value
 * @APP_MANIFEST_APPLOADER_FLAGS_NONE:
 *      No flags
 * @APP_MANIFEST_APPLOADER_FLAGS_REQUIRES_ENCRYPTION:
 *      Unless apploading is unlocked, require that the ELF image was encrypted
 */
enum app_manifest_apploader_flags {
    APP_MANIFEST_APPLOADER_FLAGS_NONE = 0u,
    APP_MANIFEST_APPLOADER_FLAGS_REQUIRES_ENCRYPTION = (1u << 0),
};

#define APP_MANIFEST_PINNED_CPU_NONE (-1)

/**
 * struct app_manifest_config_entry - Manifest configuration entry
 * @key: Key for this entry, one of &enum app_manifest_config_key
 * @value: The value for this key
 * @value.uuid: Value of "uuid"
 * @value.app_name: Name of application
 * @value.min_stack_size: Value of "min_stack"
 * @value.min_heap_size: Value of "min_heap"
 * @value.mem_map: Values for "mem_map" key
 * @value.mem_map.id: Value of "mem_map.id"
 * @value.mem_map.arch_mmu_flags: Flags for memory mapping
 * @value.mem_map.offset: Value of "mem_map.addr"
 * @value.mem_map.size: Value of "mem_map.size"
 * @value.mgmt_flags: Encoded value of "mgmt_flags", bitwise OR
 *                    of values from &enum app_manifest_mgmt_flags
 * @value.start_port: Values for "start_port" key
 * @value.start_port.name_size: Size of @start_port.name
 * @value.start_port.flags: Encoded value of "start_ports[...].flags"
 * @value.start_port.name: Value of "start_ports[...].name"
 * @value.pinned_cpu: Value of "pinned_cpu"
 * @value.version: Value of "version"
 * @value.min_version: Values ot "min_version"
 * @value.apploader_flags: Flags for apploading
 * @value.priority: Value of "priority"
 */
struct app_manifest_config_entry {
    enum app_manifest_config_key key;
    union {
        uuid_t uuid;
        const char* app_name;
        uint32_t min_stack_size;
        uint32_t min_shadow_stack_size;
        uint32_t min_heap_size;
        struct {
            uint32_t id;
            uint32_t arch_mmu_flags;
            uint64_t offset;
            uint64_t size;
        } mem_map;
        uint32_t mgmt_flags;
        struct {
            uint32_t name_size;
            uint32_t flags;
            const char* name;
        } start_port;
        int pinned_cpu;
        uint32_t version;
        uint32_t min_version;
        uint32_t apploader_flags;
        uint32_t priority;
    } value;
};

/**
 * struct app_manifest_iterator - Iterator over manifest contents
 * @manifest_data: Pointer to start of manifest
 * @manifest_size: Size of manifest in bytes
 * @index: Index of the next entry in the manifest
 * @app_name: Name of the application
 * @error: Error code set by internal parsing functions
 */
struct app_manifest_iterator {
    const char* manifest_data;
    size_t manifest_size;
    size_t index;
    const char* app_name;
    int error;
};

/**
 * app_manifest_iterator_reset - Resets an iterator for a new iteration round
 * @iterator: Pointer to output iterator to reset
 * @manifest_data: Pointer to start of manifest
 * @manifest_size: Size of manifest in bytes
 *
 * Return: %NO_ERROR if successful, an error value otherwise.
 */
int app_manifest_iterator_reset(struct app_manifest_iterator* iterator,
                                const char* manifest_data,
                                size_t manifest_size);

/**
 * app_manifest_iterator_next - Advance the iterator to the next entry
 *                              and read it into @entry
 * @iterator: The iterator
 * @entry: The next entry
 * @out_error: If not %NULL, pointer to variable to store error code into
 *
 * Return: %true if the iterator was successfully advanced and an entry was
 * read, %false otherwise. The function will set @out_error (if not %NULL) to
 * %NO_ERROR in case the manifest ran out of entries, or to the actual error
 * in case an error occurred, e.g., %ERR_NOT_VALID in case of invalid manifest.
 */
bool app_manifest_iterator_next(struct app_manifest_iterator* iterator,
                                struct app_manifest_config_entry* entry,
                                int* out_error);

__END_CDECLS
