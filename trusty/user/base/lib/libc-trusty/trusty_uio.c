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

#include <sys/uio.h>
#include <trusty_err.h>
#include <trusty_syscalls.h>
#include <unistd.h>

ssize_t readv(int fd, const struct iovec* iov, int iov_cnt) {
    ssize_t res = _trusty_readv(fd, iov, iov_cnt);
    if (res < 0) {
        errno = lk_err_to_errno((int)res);
        res = -1;
    }
    return res;
}

ssize_t read(int fd, void* buf, size_t count) {
    struct iovec iov = {.iov_base = buf, .iov_len = count};
    return readv(fd, &iov, 1);
}

ssize_t writev(int fd, const struct iovec* iov, int iov_cnt) {
    ssize_t res = _trusty_writev(fd, iov, iov_cnt);
    if (res < 0) {
        errno = lk_err_to_errno(res);
        res = -1;
    }
    return res;
}

ssize_t write(int fd, const void* buf, size_t count) {
    struct iovec iov = {.iov_base = buf, .iov_len = count};
    return writev(fd, &iov, 1);
}

ssize_t trusty_readv(int fd, const struct iovec* iov, int iov_cnt) {
    return _trusty_readv(fd, iov, iov_cnt);
}

ssize_t trusty_read(int fd, void* buf, size_t count) {
    struct iovec iov = {.iov_base = buf, .iov_len = count};
    return _trusty_readv(fd, &iov, 1);
}

ssize_t trusty_writev(int fd, const struct iovec* iov, int iov_cnt) {
    return _trusty_writev(fd, iov, iov_cnt);
}

ssize_t trusty_write(int fd, const void* buf, size_t count) {
    struct iovec iov = {.iov_base = buf, .iov_len = count};
    return trusty_writev(fd, &iov, 1);
}
