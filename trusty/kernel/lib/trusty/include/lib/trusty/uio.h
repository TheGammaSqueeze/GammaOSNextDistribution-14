/*
 * Copyright (c) 2013, Google, Inc. All rights reserved
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

#ifndef __LIB_TRUSTY_UIO_H
#define __LIB_TRUSTY_UIO_H

#include <kernel/usercopy.h>
#include <sys/types.h>

struct iovec_kern {
    void* iov_base;
    size_t iov_len;
};

struct iovec_user {
    user_addr_t iov_base;
    user_size_t iov_len;
};

struct iovec_iter {
    uint iov_index;
    uint iov_cnt;
    size_t data_offset;
};

static inline struct iovec_iter iovec_iter_create(uint iov_cnt) {
    struct iovec_iter iter = {.iov_cnt = iov_cnt};
    return iter;
}

static inline int iovec_iter_has_next(const struct iovec_iter* iter) {
    return iter->iov_index < iter->iov_cnt;
}

ssize_t membuf_to_kern_iovec(const struct iovec_kern* iov,
                             uint iov_cnt,
                             const uint8_t* buf,
                             size_t len);

ssize_t kern_iovec_to_membuf(uint8_t* buf,
                             size_t len,
                             const struct iovec_kern* iov,
                             uint iov_cnt);

ssize_t membuf_to_user_iovec(user_addr_t iov_uaddr,
                             uint iov_cnt,
                             const uint8_t* buf,
                             size_t cb);

ssize_t user_iovec_to_membuf(uint8_t* buf,
                             size_t len,
                             user_addr_t iov_uaddr,
                             uint iov_cnt);

ssize_t user_iovec_to_membuf_iter(uint8_t* buf,
                                  size_t len,
                                  user_addr_t iov_uaddr,
                                  struct iovec_iter* iter);
#endif
