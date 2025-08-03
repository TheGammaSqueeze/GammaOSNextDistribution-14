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

#ifndef __USERCOPY_H
#define __USERCOPY_H

#include <compiler.h>
#include <sys/types.h>
#include <arch/usercopy.h>

static inline __WARN_UNUSED_RESULT status_t copy_from_user(void *kdest, user_addr_t usrc, size_t len)
{
	return arch_copy_from_user(kdest, usrc, len);
}

static inline __WARN_UNUSED_RESULT status_t copy_to_user(user_addr_t udest, const void *ksrc, size_t len)
{
	return arch_copy_to_user(udest, ksrc, len);
}

static inline __WARN_UNUSED_RESULT ssize_t strlcpy_from_user(char *kdest, user_addr_t usrc, size_t len)
{
	return arch_strlcpy_from_user(kdest, usrc, len);
}

#endif /* __USERCOPY_H */
