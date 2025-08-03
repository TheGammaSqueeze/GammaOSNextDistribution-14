/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#if defined(__QL_TIPC__)
#include <trusty/sysdeps.h>
#else
#include <uapi/err.h>
#endif

__BEGIN_CDECLS
static __ALWAYS_INLINE char* lk_strerror(int errnum) {
    switch (errnum) {
    case NO_ERROR:
        return (char*)"NO_ERROR";

    case ERR_ALREADY_EXISTS:
        return (char*)"ERR_ALREADY_EXISTS";

    case ERR_CHANNEL_CLOSED:
        return (char*)"ERR_CHANNEL_CLOSED";

    case ERR_OFFLINE:
        return (char*)"ERR_OFFLINE";

    case ERR_NOT_ALLOWED:
        return (char*)"ERR_NOT_ALLOWED";

    case ERR_BAD_PATH:
        return (char*)"ERR_BAD_PATH";

    case ERR_ALREADY_MOUNTED:
        return (char*)"ERR_ALREADY_MOUNTED";

    case ERR_IO:
        return (char*)"ERR_IO";

    case ERR_NOT_DIR:
        return (char*)"ERR_NOT_DIR";

    case ERR_NOT_FILE:
        return (char*)"ERR_NOT_FILE";

    case ERR_RECURSE_TOO_DEEP:
        return (char*)"ERR_RECURSE_TOO_DEEP";

    case ERR_NOT_SUPPORTED:
        return (char*)"ERR_NOT_SUPPORTED";

    case ERR_TOO_BIG:
        return (char*)"ERR_TOO_BIG";

    case ERR_CANCELLED:
        return (char*)"ERR_CANCELLED";

    case ERR_NOT_IMPLEMENTED:
        return (char*)"ERR_NOT_IMPLEMENTED";

    case ERR_CHECKSUM_FAIL:
        return (char*)"ERR_CHECKSUM_FAIL";

    case ERR_CRC_FAIL:
        return (char*)"ERR_CRC_FAIL";

    case ERR_CMD_UNKNOWN:
        return (char*)"ERR_CMD_UNKNOWN";

    case ERR_BAD_STATE:
        return (char*)"ERR_BAD_STATE";

    case ERR_BAD_LEN:
        return (char*)"ERR_BAD_LEN";

    case ERR_BUSY:
        return (char*)"ERR_BUSY";

    case ERR_THREAD_DETACHED:
        return (char*)"ERR_THREAD_DETACHED";

    case ERR_I2C_NACK:
        return (char*)"ERR_I2C_NACK";

    case ERR_ALREADY_EXPIRED:
        return (char*)"ERR_ALREADY_EXPIRED";

    case ERR_OUT_OF_RANGE:
        return (char*)"ERR_OUT_OF_RANGE";

    case ERR_NOT_CONFIGURED:
        return (char*)"ERR_NOT_CONFIGURED";

    case ERR_NOT_MOUNTED:
        return (char*)"ERR_NOT_MOUNTED";

    case ERR_FAULT:
        return (char*)"ERR_FAULT";

    case ERR_NO_RESOURCES:
        return (char*)"ERR_NO_RESOURCES";

    case ERR_BAD_HANDLE:
        return (char*)"ERR_BAD_HANDLE";

    case ERR_ACCESS_DENIED:
        return (char*)"ERR_ACCESS_DENIED";

    case ERR_PARTIAL_WRITE:
        return (char*)"ERR_PARTIAL_WRITE";

    default:
        if (errnum < ERR_USER_BASE) {
            return (char*)"User Error";
        } else {
            return (char*)"General Error";
        }
    }
};

__END_CDECLS
