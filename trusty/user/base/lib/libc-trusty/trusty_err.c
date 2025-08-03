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

#include <trusty_err.h>

int lk_err_to_errno(int lk_err) {
    switch (lk_err) {
    case ERR_INVALID_ARGS:
        return EINVAL;

    case ERR_FAULT:
        return EFAULT;

    case ERR_BUSY:
        return EBUSY;

    case ERR_BAD_HANDLE:
        return EBADFD;

    case ERR_TIMED_OUT:
        return ETIMEDOUT;

    case ERR_NO_MEMORY:
        return ENOMEM;

    default:
        /* unhandled */
        return EINVAL;
    }
}

int errno_to_lk_err(int err) {
    switch (err) {
    case EINVAL:
        return ERR_INVALID_ARGS;

    case EFAULT:
        return ERR_FAULT;

    case EBUSY:
        return ERR_BUSY;

    case EBADFD:
        return ERR_BAD_HANDLE;

    case ETIMEDOUT:
        return ERR_TIMED_OUT;

    case ENOMEM:
        return ERR_NO_MEMORY;

    default:
        return ERR_GENERIC;
    }
}
