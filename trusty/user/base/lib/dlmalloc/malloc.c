/*
 * Copyright (C) 2013 The Android Open Source Project
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

/*
 * TODO: temporary, replace this with pass-throughs between errno style
 * numbers and LK errors.
 */

#define LACKS_TIME_H
#define LACKS_SYS_MMAN_H
#define LACKS_FCNTL_H
#define LACKS_UNISTD_H
#define LACKS_SYS_PARAM_H
#define LACKS_ERRNO_H

#define MALLOC_FAILURE_ACTION \
    do {                      \
    } while (0)
#define ABORT abort()
#define HAVE_MMAP 0
#define MORECORE sbrk

#include <sys/auxv.h>
#define malloc_getpagesize getauxval(AT_PAGESZ)

#define ENOMEM ERR_NO_MEMORY
#define EINVAL ERR_INVALID_ARGS

#include <errno.h>
#include <lk/macros.h>
#include <stddef.h>
#include <stdlib.h>
#include <trusty_syscalls.h>
#include <uapi/err.h>
#include <unistd.h>

static char* __libc_brk;

#define SBRK_ALIGN 32
void* sbrk(ptrdiff_t increment) {
    char* new_brk;
    char* start;
    char* end;

    if (!__libc_brk)
        __libc_brk = (char*)_trusty_brk(0);

    start = (char*)round_up((uintptr_t)__libc_brk, SBRK_ALIGN);
    end = (char*)round_up((uintptr_t)(start + increment), SBRK_ALIGN);

    new_brk = (char*)_trusty_brk(end);
    if (new_brk < end) {
        errno = ENOMEM;
        return (void*)-1;
    }

    __libc_brk = new_brk;
    return start;
}

#include "dlmalloc.c"
