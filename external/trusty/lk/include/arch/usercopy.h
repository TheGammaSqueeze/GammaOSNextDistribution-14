/*
 * Copyright (c) 2017, Google Inc. All rights reserved
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

#ifndef __ARCH_USERCOPY_H
#define __ARCH_USERCOPY_H

#include <sys/types.h>
#include <inttypes.h>

#if IS_64BIT && USER_32BIT
typedef uint32_t user_addr_t;
typedef uint32_t user_size_t;

#define PRIxPTR_USER "x"
#define PRIxSIZE_USER "x"
#define PRIuSIZE_USER "u"
#else
typedef vaddr_t user_addr_t;
typedef size_t user_size_t;

#define PRIxPTR_USER PRIxVADDR
#define PRIxSIZE_USER "zx"
#define PRIuSIZE_USER "zu"
#endif

status_t arch_copy_from_user(void *kdest, user_addr_t usrc, size_t len);
status_t arch_copy_to_user(user_addr_t udest, const void *ksrc, size_t len);
ssize_t arch_strlcpy_from_user(char *kdst, user_addr_t usrc, size_t len);

#endif /* __ARCH_USERCOPY_H */
