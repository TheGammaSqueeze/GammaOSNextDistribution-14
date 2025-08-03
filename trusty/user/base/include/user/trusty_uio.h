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

#pragma once

#include <sys/uio.h>
#include <trusty_err.h>

/*
 * The following routines are functionally equivalent to libc read{v}/write{v}
 * except how an error condition is handled. On error:
 *    read{v}/write{v} returns -1 and sets errno
 *    trusty_read(v)/trusty_write{v} return negative LK error instead
 */
ssize_t trusty_readv(int fd, const struct iovec* iov, int iovcnt);
ssize_t trusty_read(int fd, void* buf, size_t count);
ssize_t trusty_writev(int fd, const struct iovec* iov, int iovcnt);
ssize_t trusty_write(int fd, const void* buf, size_t count);
