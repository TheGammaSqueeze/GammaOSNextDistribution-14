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

#define TLOG_TAG "hwasan-rt"

#include "hwasan.h"
#include <lib/hwasan/hwasan_shadow.h>

#include <assert.h>
#include <lk/compiler.h>
#include <lk/macros.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/auxv.h>
#include <trusty_log.h>
#include <uapi/err.h>

uintptr_t __hwasan_shadow_memory_dynamic_address = 0;

static inline uint8_t get_tag(uintptr_t ptr) {
    return (uint64_t)ptr >> hwasan_addr_tag_shift;
}

static inline uintptr_t remove_ptr_tag(uintptr_t ptr) {
    return ptr & ~hwasan_addr_tag_mask;
}

static inline uintptr_t tag_ptr(uintptr_t ptr, uint8_t tag) {
    uint64_t tag_mask = (uint64_t)tag << hwasan_addr_tag_shift;
    return remove_ptr_tag(ptr) | tag_mask;
}

/* Tagging granularity is 1 tag per 16 bytes */
static inline uintptr_t mem_to_shadow(uintptr_t ptr) {
    ptr = remove_ptr_tag(ptr);
    return (ptr >> HWASAN_SHADOW_SCALE) +
           __hwasan_shadow_memory_dynamic_address;
}

static inline bool is_valid_user_ptr(uintptr_t ptr) {
    ptr = remove_ptr_tag(ptr);
    return !ptr || (ptr >= USER_ASPACE_BASE &&
                    ptr < USER_ASPACE_BASE + USER_ASPACE_SIZE);
}

__WEAK void __hwasan_report_error(uintptr_t far) {
    /* TODO(b/149918767): unwind stack, analyze, and report error */
    uint8_t ptr_tag = get_tag(far);
    uint8_t* shadow_tag = (uint8_t*)mem_to_shadow(far);

    TLOGE("Failed address check. Fault address: 0x%16lx, pointer tag: 0x%x, "
          "expected tag: 0x%x\n",
          far, ptr_tag, *shadow_tag);

    abort();
}

static void check_address(uintptr_t ptr, size_t size) {
    assert(is_valid_user_ptr(ptr));

    if (!ptr || size == 0) {
        /* Pass the address check since NULL pointer dereference segfaults
         * anyways */
        return;
    }

    uint8_t ptr_tag = get_tag(ptr);
    uint8_t* first_tag = (uint8_t*)mem_to_shadow(ptr);
    uint8_t* last_tag = (uint8_t*)mem_to_shadow(ptr + size - 1);
    for (uint8_t* tag = first_tag; tag <= last_tag; tag++) {
        if (ptr_tag != *tag) {
            uintptr_t far =
                    ptr + ((uintptr_t)(tag - first_tag) << HWASAN_SHADOW_SCALE);
            __hwasan_report_error(far);
        }
    }
}

void __hwasan_init(void) {
    if (!__hwasan_shadow_memory_dynamic_address) {
        __hwasan_shadow_memory_dynamic_address =
                getauxval(TRUSTY_AT_HWASAN_SHADOW);
        assert(__hwasan_shadow_memory_dynamic_address);
    }
}

void* __hwasan_memset(uintptr_t ptr, int val, size_t size) {
    check_address(ptr, size);
    return memset((void*)ptr, val, size);
}

void* __hwasan_memcpy(uintptr_t dst, const uintptr_t src, size_t size) {
    check_address(dst, size);
    check_address(src, size);
    return memcpy((void*)dst, (void*)src, size);
}

void* __hwasan_memmove(uintptr_t dst, const uintptr_t src, size_t size) {
    check_address(dst, size);
    check_address(src, size);
    return memmove((void*)dst, (void*)src, size);
}

void __hwasan_loadN(uintptr_t ptr, size_t size) {
    check_address(ptr, size);
}

void __hwasan_storeN(uintptr_t ptr, size_t size) {
    check_address(ptr, size);
}

static uint8_t hwasan_generate_tag() {
    static uint8_t tag = 0;
    /* 0 tag corresponds to untagged memory, so avoid generating it */
    if (__builtin_add_overflow(tag, 1, &tag)) {
        tag = 1;
    }
    return tag;
}

static uintptr_t hwasan_tag_memory_etc(uintptr_t ptr,
                                       size_t size,
                                       uint8_t tag) {
    assert(IS_ALIGNED(ptr, 1U << HWASAN_SHADOW_SCALE));
    assert(is_valid_user_ptr(ptr));

    /*
     * It's possible that dlmalloc() fails to allocate memory, in which case it
     * passes NULL here.
     */
    if (!ptr || !size) {
        return ptr;
    }

    uint8_t* first_tag = (uint8_t*)mem_to_shadow(ptr);
    uint8_t* last_tag = (uint8_t*)mem_to_shadow(ptr + size - 1);
    for (uint8_t* i = first_tag; i <= last_tag; i++) {
        *i = tag;
    }
    return tag_ptr(ptr, tag);
}

void* hwasan_tag_memory(void* ptr, size_t size) {
    return (void*)hwasan_tag_memory_etc((uintptr_t)ptr, size,
                                        hwasan_generate_tag());
}

void hwasan_untag_memory(void* ptr, size_t size) {
    hwasan_tag_memory_etc((uintptr_t)ptr, size, 0);
}

void* hwasan_remove_ptr_tag(void* ptr) {
    return (void*)remove_ptr_tag((uintptr_t)ptr);
}
