/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <test-runner-arch.h>
#include <trusty/sysdeps.h>

/* Size limits for bump allocators (trusty_calloc and trusty_alloc_pages) */
#define HEAP_SIZE (56 + 6 * 4)
#define PAGE_COUNT (3)

static uint8_t heap[HEAP_SIZE];
static int heap_allocated = 0;

static uint8_t pages[PAGE_COUNT * PAGE_SIZE] __ALIGNED(PAGE_SIZE);
static int pages_allocated = 0;

extern int trusty_encode_page_info(struct ns_mem_page_info* page_info,
                                   void* vaddr);

/* libc functions that the compiler may generate calls to */
void* memcpy(void* dest, const void* src, size_t count) {
    uint8_t* d = dest;
    const uint8_t* s = src;
    while (count--) {
        *d++ = *s++;
    }
    return dest;
}

void* memset(void* dest, int c, size_t count) {
    uint8_t* d = dest;
    while (count--) {
        *d++ = c;
    }
    return dest;
}

int memcmp(const void* lhs, const void* rhs, size_t count) {
    const uint8_t* l = lhs;
    const uint8_t* r = rhs;
    while (count--) {
        if (*l != *r) {
            return *l > *r ? +1 : -1;
        }
        ++l, ++r;
    }
    return 0;
}

char* strcpy(char* dest, char const* src) {
    char* ret = dest;
    while ((*dest++ = *src++) != '\0') {
    }
    return ret;
}

size_t strlen(char const* s) {
    size_t ret;
    for (ret = 0; *s++; ret++) {
    }
    return ret;
}

int strcmp(char const* cs, char const* ct) {
    int res;
    const unsigned char* su1 = (const unsigned char*)cs;
    const unsigned char* su2 = (const unsigned char*)ct;

    while (1) {
        if ((res = *su1 - *su2++) != 0 || !*su1++)
            break;
    }

    return res;
}

/* ql-tipc sysdeps functions */

void trusty_lock(struct trusty_dev* dev) {}

void trusty_unlock(struct trusty_dev* dev) {}

__attribute__((weak)) void trusty_local_irq_disable(unsigned long* state) {}

void trusty_local_irq_restore(unsigned long* state) {}

void trusty_abort(void) {
    host_exit(2);
    __builtin_unreachable();
}

void trusty_printf(const char* format, ...) {}

void* trusty_memcpy(void* dest, const void* src, size_t n) {
    return memcpy(dest, src, n);
}

void* trusty_memset(void* dest, const int c, size_t n) {
    return memset(dest, c, n);
}

char* trusty_strcpy(char* dest, const char* src) {
    return strcpy(dest, src);
}

size_t trusty_strlen(const char* str) {
    return strlen(str);
}

int trusty_strcmp(const char* str1, const char* str2) {
    return strcmp(str1, str2);
}

void* trusty_calloc(size_t n, size_t size) {
    void* ret;
    size_t asize = n * size;
    if (heap_allocated + asize > HEAP_SIZE) {
        return NULL;
    }
    ret = heap + heap_allocated;
    heap_allocated += asize;
    return ret;
}

void trusty_free(void* addr) {
}

void* trusty_alloc_pages(unsigned count) {
    void* ret;
    if (pages_allocated + count > PAGE_COUNT)
        return NULL;
    ret = pages + pages_allocated * PAGE_SIZE;
    pages_allocated += count;
    return ret;
}

void trusty_free_pages(void* va, unsigned count) {
    /*
     * We don't have a real allocator. Make sure we don't trigger any
     * code-paths that need dynamic memory.
     */
    trusty_abort();
}
