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

#define TLOG_TAG "app_manifest"

#include <assert.h>
#include <inttypes.h>
#include <lib/app_manifest/app_manifest.h>
#include <lk/macros.h>
#include <string.h>
#include <trusty_log.h>
#include <uapi/err.h>

int app_manifest_iterator_reset(struct app_manifest_iterator* iterator,
                                const char* manifest_data,
                                size_t manifest_size) {
    assert(iterator);

    iterator->manifest_data = manifest_data;
    iterator->manifest_size = manifest_size;
    iterator->index = 0;
    iterator->app_name = "<unknown>";
    iterator->error = NO_ERROR;

    return NO_ERROR;
}

/*
 * Helper function that gets the pointer to the next object in the manifest
 * after checking that the object does not extend past the end of the manifest
 */
static inline const void* app_manifest_get_ptr(
        struct app_manifest_iterator* iterator,
        size_t size) {
    const void* curr = &iterator->manifest_data[iterator->index];
    if (size > iterator->manifest_size - iterator->index) {
        iterator->error = ERR_NOT_VALID;
        return NULL;
    }
    iterator->index += size;
    return curr;
}

/*
 * Helper function that reads a sized object from the manifest after
 * checking that the object does not extend past the end of the manifest
 */
static inline void app_manifest_read_ptr(struct app_manifest_iterator* iterator,
                                         void* dest,
                                         size_t size) {
    const void* src = app_manifest_get_ptr(iterator, size);
    if (src) {
        memcpy(dest, src, size);
    }
}

/* Helper function that reads one entry and advances the iterator */
static inline uint32_t app_manifest_read_entry(
        struct app_manifest_iterator* iterator) {
    uint32_t entry = 0;
    app_manifest_read_ptr(iterator, &entry, sizeof(entry));
    return entry;
}

static inline const char* app_manifest_read_string(
        struct app_manifest_iterator* iterator,
        uint32_t string_size,
        const char* string_name) {
    uint32_t string_entries;
    size_t string_length;
    const char* string;

    /* string size with padding */
    string_entries = round_up(string_size, sizeof(uint32_t));
    if (string_entries == 0) {
        TLOGE("invalid %s-size 0x%" PRIx32 "/0x%" PRIx32 "\n", string_name,
              string_size, string_entries);
        return NULL;
    }

    string = app_manifest_get_ptr(iterator, string_entries);
    if (!string) {
        TLOGE("manifest too small %zu, missing %s, "
              "%s-size 0x%" PRIx32 "/0x%" PRIx32 "\n",
              iterator->manifest_size, string_name, string_name, string_size,
              string_entries);
        return NULL;
    }

    /*
     * Make sure that the string has exactly one \0 and it's the last character
     */
    string_length = strnlen(string, string_size);
    if (string_length != string_size - 1) {
        TLOGE("%s-length invalid 0x%zx, %s-size 0x%" PRIx32 "/0x%" PRIx32 "\n",
              string_name, string_length, string_name, string_size,
              string_entries);
        return NULL;
    }

    return string;
}

bool app_manifest_iterator_next(struct app_manifest_iterator* iterator,
                                struct app_manifest_config_entry* entry,
                                int* out_error) {
    assert(entry);

    if (iterator->index >= iterator->manifest_size) {
        if (out_error) {
            *out_error = NO_ERROR;
        }
        return false;
    }

    /*
     * uuid and app_name are special cases: we return them
     * using pseudo-keys because they're not present in the entries
     */
    uint32_t manifest_key;
    if (iterator->index == 0) {
        manifest_key = APP_MANIFEST_CONFIG_KEY_UUID;
    } else if (iterator->index == sizeof(uuid_t)) {
        manifest_key = APP_MANIFEST_CONFIG_KEY_APP_NAME;
    } else {
        manifest_key = app_manifest_read_entry(iterator);
    }

    uint32_t string_size;
    const char* string;
    switch (manifest_key) {
    case APP_MANIFEST_CONFIG_KEY_MIN_STACK_SIZE:
        entry->value.min_stack_size = app_manifest_read_entry(iterator);
        if (iterator->error != NO_ERROR) {
            TLOGE("manifest missing MIN_STACK_SIZE value of app %s\n",
                  iterator->app_name);
            goto error;
        }
        break;
    case APP_MANIFEST_CONFIG_KEY_MIN_SHADOW_STACK_SIZE:
        entry->value.min_shadow_stack_size = app_manifest_read_entry(iterator);
        if (iterator->error != NO_ERROR) {
            TLOGE("manifest missing MIN_SHADOW_STACK_SIZE value of app %s\n",
                  iterator->app_name);
            goto error;
        }
        break;

    case APP_MANIFEST_CONFIG_KEY_MIN_HEAP_SIZE:
        entry->value.min_heap_size = app_manifest_read_entry(iterator);
        if (iterator->error != NO_ERROR) {
            TLOGE("manifest missing MIN_HEAP_SIZE value of app %s\n",
                  iterator->app_name);
            goto error;
        }
        break;

    case APP_MANIFEST_CONFIG_KEY_MAP_MEM:
        entry->value.mem_map.id = app_manifest_read_entry(iterator);
        /*
         * TODO: add big endian support? The manifest_compiler wrote the
         * next two entries as 64 bit numbers with the byte order of the
         * build machine. The code below assumes the manifest data and
         * device are both little-endian.
         */
        entry->value.mem_map.offset = app_manifest_read_entry(iterator);
        entry->value.mem_map.offset |=
                (uint64_t)app_manifest_read_entry(iterator) << 32;
        entry->value.mem_map.size = app_manifest_read_entry(iterator);
        entry->value.mem_map.size |= (uint64_t)app_manifest_read_entry(iterator)
                                     << 32;
        entry->value.mem_map.arch_mmu_flags = app_manifest_read_entry(iterator);
        if (iterator->error != NO_ERROR) {
            TLOGE("manifest missing MAP_MEM value of app %s\n",
                  iterator->app_name);
            goto error;
        }
        break;

    case APP_MANIFEST_CONFIG_KEY_MGMT_FLAGS:
        entry->value.mgmt_flags = app_manifest_read_entry(iterator);
        if (iterator->error != NO_ERROR) {
            TLOGE("manifest missing MGMT_FLAGS value of app %s\n",
                  iterator->app_name);
            goto error;
        }
        break;

    case APP_MANIFEST_CONFIG_KEY_START_PORT:
        entry->value.start_port.flags = app_manifest_read_entry(iterator);

        string_size = app_manifest_read_entry(iterator);
        if (iterator->error != NO_ERROR) {
            TLOGE("manifest missing START_PORT values of app %s\n",
                  iterator->app_name);
            goto error;
        }

        string = app_manifest_read_string(iterator, string_size,
                                          "start-port-name");
        if (!string) {
            goto error;
        }

        entry->value.start_port.name_size = string_size;
        entry->value.start_port.name = string;
        break;

    case APP_MANIFEST_CONFIG_KEY_PINNED_CPU:
        entry->value.pinned_cpu = (int)app_manifest_read_entry(iterator);
        if (iterator->error != NO_ERROR) {
            TLOGE("manifest missing PINNED_CPU value of app %s\n",
                  iterator->app_name);
            goto error;
        }
        break;

    case APP_MANIFEST_CONFIG_KEY_PRIORITY:
        entry->value.priority = (int)app_manifest_read_entry(iterator);
        if (iterator->error != NO_ERROR) {
            TLOGE("manifest missing PRIORITY value of app %s\n",
                  iterator->app_name);
            goto error;
        }
        break;

    case APP_MANIFEST_CONFIG_KEY_VERSION:
        entry->value.version = app_manifest_read_entry(iterator);
        if (iterator->error != NO_ERROR) {
            TLOGE("manifest missing VERSION value of app %s\n",
                  iterator->app_name);
            goto error;
        }
        break;

    case APP_MANIFEST_CONFIG_KEY_MIN_VERSION:
        entry->value.min_version = app_manifest_read_entry(iterator);
        if (iterator->error != NO_ERROR) {
            TLOGE("manifest missing MIN_VERSION value of app %s\n",
                  iterator->app_name);
            goto error;
        }
        break;

    case APP_MANIFEST_CONFIG_KEY_APPLOADER_FLAGS:
        entry->value.apploader_flags = app_manifest_read_entry(iterator);
        if (iterator->error != NO_ERROR) {
            TLOGE("manifest missing APPLOADER_FLAGS value of app %s\n",
                  iterator->app_name);
            goto error;
        }
        break;

    case APP_MANIFEST_CONFIG_KEY_UUID:
        app_manifest_read_ptr(iterator, &entry->value.uuid,
                              sizeof(entry->value.uuid));
        if (iterator->error != NO_ERROR) {
            TLOGE("manifest too small %zu, missing uuid\n",
                  iterator->manifest_size);
            goto error;
        }
        break;

    case APP_MANIFEST_CONFIG_KEY_APP_NAME:
        string_size = app_manifest_read_entry(iterator);
        string = app_manifest_read_string(iterator, string_size, "app-name");
        if (!string) {
            goto error;
        }

        entry->value.app_name = string;
        iterator->app_name = string;
        break;

    default:
        TLOGE("manifest contains unknown config key %" PRIu32 " for app %s\n",
              manifest_key, iterator->app_name);
        goto error;
    }

    assert(iterator->error == NO_ERROR);
    entry->key = manifest_key;
    if (out_error) {
        *out_error = NO_ERROR;
    }
    return true;

error:
    if (out_error) {
        if (iterator->error != NO_ERROR) {
            *out_error = iterator->error;
        } else {
            *out_error = ERR_NOT_VALID;
        }
    }
    return false;
}
