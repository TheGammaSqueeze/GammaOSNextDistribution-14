/*
 * Copyright (C) 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "utils.h"

#include <errno.h>
#include <time.h>

#include "stats_statsdsocketlog.h"

int64_t get_elapsed_realtime_ns() {
    struct timespec t;
    t.tv_sec = t.tv_nsec = 0;
    clock_gettime(CLOCK_BOOTTIME, &t);
    return (int64_t)t.tv_sec * 1000000000LL + t.tv_nsec;
}

int toSocketLossError(int errno_code) {
    using namespace android::os::statsdsocket;

    // compile time checks
    static_assert(STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_ON_WRITE_EPERM == -EPERM,
                  "Socket Loss Error codes mapping function needs to be updated");
    static_assert(STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_ON_WRITE_EINTR == -EINTR,
                  "Socket Loss Error codes mapping function needs to be updated");
    static_assert(STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_ON_WRITE_EIO == -EIO,
                  "Socket Loss Error codes mapping function needs to be updated");
    static_assert(STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_ON_WRITE_EBADF == -EBADF,
                  "Socket Loss Error codes mapping function needs to be updated");
    static_assert(
            STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_ON_WRITE_EAGAIN == -EAGAIN,
            "Socket Loss Error codes mapping function needs to be updated");  // same as EWOULDBLOCK
    static_assert(STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_ON_WRITE_EFAULT == -EFAULT,
                  "Socket Loss Error codes mapping function needs to be updated");
    static_assert(STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_ON_WRITE_ENODEV == -ENODEV,
                  "Socket Loss Error codes mapping function needs to be updated");
    static_assert(STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_ON_WRITE_EINVAL == -EINVAL,
                  "Socket Loss Error codes mapping function needs to be updated");
    static_assert(STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_ON_WRITE_EFBIG == -EFBIG,
                  "Socket Loss Error codes mapping function needs to be updated");
    static_assert(STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_ON_WRITE_ENOSPC == -ENOSPC,
                  "Socket Loss Error codes mapping function needs to be updated");
    static_assert(STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_ON_WRITE_EPIPE == -EPIPE,
                  "Socket Loss Error codes mapping function needs to be updated");
    static_assert(STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_ON_WRITE_EDESTADDRREQ ==
                          -EDESTADDRREQ,
                  "Socket Loss Error codes mapping function needs to be updated");
    static_assert(STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_ON_WRITE_EDQUOT == -EDQUOT,
                  "Socket Loss Error codes mapping function needs to be updated");

    switch (errno_code) {
        case EPERM:
        case EINTR:
        case EIO:
        case EBADF:
        case EAGAIN:
        case EFAULT:
        case ENODEV:
        case EINVAL:
        case EFBIG:
        case ENOSPC:
        case EPIPE:
        case EDESTADDRREQ:
        case EDQUOT:
            return -errno_code;
        default:
            return STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_UNKNOWN;
    }
}
