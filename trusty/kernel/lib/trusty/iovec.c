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

#include <err.h>
#include <lib/trusty/uio.h>
#include <string.h>

ssize_t membuf_to_kern_iovec(const struct iovec_kern* iov,
                             uint iov_cnt,
                             const uint8_t* buf,
                             size_t len) {
    size_t copied = 0;

    if (unlikely(iov_cnt == 0 || len == 0))
        return 0;

    if (unlikely(iov == NULL || buf == NULL))
        return (ssize_t)ERR_INVALID_ARGS;

    for (uint i = 0; i < iov_cnt; i++, iov++) {
        size_t to_copy = len;
        if (to_copy > iov->iov_len)
            to_copy = iov->iov_len;

        if (unlikely(to_copy == 0))
            continue;

        if (unlikely(iov->iov_base == NULL))
            return (ssize_t)ERR_INVALID_ARGS;

        memcpy(iov->iov_base, buf, to_copy);

        copied += to_copy;
        buf += to_copy;
        len -= to_copy;

        if (len == 0)
            break;
    }

    return (ssize_t)copied;
}

ssize_t kern_iovec_to_membuf(uint8_t* buf,
                             size_t len,
                             const struct iovec_kern* iov,
                             uint iov_cnt) {
    size_t copied = 0;

    if (unlikely(iov_cnt == 0 || len == 0))
        return 0;

    if (unlikely(buf == NULL || iov == NULL))
        return (ssize_t)ERR_INVALID_ARGS;

    for (uint i = 0; i < iov_cnt; i++, iov++) {
        size_t to_copy = len;
        if (to_copy > iov->iov_len)
            to_copy = iov->iov_len;

        if (unlikely(to_copy == 0))
            continue;

        if (unlikely(iov->iov_base == NULL))
            return (ssize_t)ERR_INVALID_ARGS;

        memcpy(buf, iov->iov_base, to_copy);

        copied += to_copy;
        buf += to_copy;
        len -= to_copy;

        if (len == 0)
            break;
    }

    return (ssize_t)copied;
}

ssize_t membuf_to_user_iovec(user_addr_t iov_uaddr,
                             uint iov_cnt,
                             const uint8_t* buf,
                             size_t len) {
    status_t ret;
    size_t copied = 0;
    struct iovec_user uiov;

    if (unlikely(iov_cnt == 0 || len == 0))
        return 0;

    if (unlikely(buf == NULL))
        return (ssize_t)ERR_INVALID_ARGS;

    /* for each iov entry */
    for (uint i = 0; i < iov_cnt; i++) {
        /* copy user iovec from user space into local buffer */
        ret = copy_from_user(&uiov, iov_uaddr + i * sizeof(struct iovec_user),
                             sizeof(struct iovec_user));
        if (unlikely(ret != NO_ERROR))
            return (ssize_t)ret;

        size_t to_copy = len;
        if (to_copy > uiov.iov_len)
            to_copy = uiov.iov_len;

        /* copy data to user space */
        ret = copy_to_user(uiov.iov_base, buf, to_copy);
        if (unlikely(ret != NO_ERROR))
            return (ssize_t)ret;

        copied += to_copy;
        buf += to_copy;
        len -= to_copy;

        if (len == 0)
            break;
        ;
    }

    return (ssize_t)copied;
}

ssize_t user_iovec_to_membuf_iter(uint8_t* buf,
                                  size_t buf_len,
                                  user_addr_t iov_uaddr,
                                  struct iovec_iter* iter) {
    status_t ret;
    size_t copied = 0;
    struct iovec_user uiov;

    if (unlikely(buf_len == 0))
        return 0;

    if (unlikely(buf == NULL))
        return (ssize_t)ERR_INVALID_ARGS;

    while (buf_len > 0 && iovec_iter_has_next(iter)) {
        /* copy user iovec from user space into local buffer */
        ret = copy_from_user(
                &uiov, iov_uaddr + iter->iov_index * sizeof(struct iovec_user),
                sizeof(struct iovec_user));
        if (unlikely(ret != NO_ERROR))
            return (ssize_t)ret;

        /* we've re-read the iov from userspace, it may have changed */
        if (uiov.iov_len < iter->data_offset)
            return (ssize_t)ERR_INVALID_ARGS;

        /* figure out how much to copy */
        size_t to_copy = uiov.iov_len - iter->data_offset;
        if (to_copy > buf_len)
            to_copy = buf_len;

        /* copy data from user space */
        ret = copy_from_user(buf, uiov.iov_base + iter->data_offset, to_copy);
        if (unlikely(ret != NO_ERROR))
            return (ssize_t)ret;

        /* update the input state */
        iter->data_offset += to_copy;
        if (iter->data_offset >= uiov.iov_len) {
            iter->iov_index += 1;
            iter->data_offset = 0;
        }

        /* update the output state */
        copied += to_copy;
        buf += to_copy;
        buf_len -= to_copy;
    }

    return (ssize_t)copied;
}

ssize_t user_iovec_to_membuf(uint8_t* buf,
                             size_t buf_len,
                             user_addr_t iov_uaddr,
                             uint iov_cnt) {
    struct iovec_iter iter = iovec_iter_create(iov_cnt);
    return user_iovec_to_membuf_iter(buf, buf_len, iov_uaddr, &iter);
}
