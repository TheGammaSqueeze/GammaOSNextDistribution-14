/*
 * Copyright (c) 2015, Google, Inc. All rights reserved
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

#include <kernel/usercopy.h>
#include <sys/types.h>

#define MAX_SYS_FD_HADLERS 10

struct sys_fd_ops {
    ssize_t (*readv)(uint32_t fd, user_addr_t iov_uaddr, uint32_t iov_cnt);
    ssize_t (*writev)(uint32_t fd, user_addr_t iov_uaddr, uint32_t iov_cnt);
    int32_t (*ioctl)(uint32_t fd, uint32_t cmd, user_addr_t user_ptr);
};

status_t install_sys_fd_handler(uint32_t fd, const struct sys_fd_ops* ops);
