/*
 * Copyright (c) 2022, Google, Inc. All rights reserved
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

#include <debug.h>
#include <err.h>
#include <sys/uio.h>
#include <trusty/io_handle.h>
#include <trusty/uio.h>

ssize_t trusty_writev(int fd, const struct iovec* iov, int iovcnt) {
    bool should_output = LK_DEBUGLEVEL_INFO <= LK_LOGLEVEL;

    io_handle_t* io_handle = fd_io_handle(fd);
    if (io_handle == NULL) {
        return ERR_BAD_HANDLE;
    }

    if (should_output) {
        io_lock(io_handle);
    }

    ssize_t ret = 0;
    ssize_t total_bytes = 0;
    if (should_output) {
        for (int i = 0; i < iovcnt; i++, iov++) {
            if (iov == NULL) {
                ret = ERR_INVALID_ARGS;
                goto write_done;
            }
            ret = io_write(io_handle, (const void*)iov->iov_base, iov->iov_len);
            if (ret < 0) {
                goto write_done;
            }
            total_bytes += ret;
        }
    }
    ret = total_bytes;
write_done:
    if (should_output) {
        io_write_commit(io_handle);
        io_unlock(io_handle);
    }
    return ret;
}
