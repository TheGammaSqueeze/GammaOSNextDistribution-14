/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <lk/compiler.h>
#include <stdio.h>

#define TLOG_LVL_NONE 0
#define TLOG_LVL_CRIT 1
#define TLOG_LVL_ERROR 2
#define TLOG_LVL_WARN 3
#define TLOG_LVL_INFO 4
#define TLOG_LVL_DEBUG 5

#ifndef TLOG_LVL
#ifdef TLOG_LVL_DEFAULT
#define TLOG_LVL TLOG_LVL_DEFAULT
#else
#define TLOG_LVL TLOG_LVL_INFO
#endif
#endif

__BEGIN_CDECLS

#ifdef TRUSTY_USERSPACE

/* Defined in libc and libunittest, whichever is statically linked first will be
 * used, so libunittest must always come before libc in the link order. */
int _tlog(const char* fmt, ...) __PRINTFLIKE(1, 2);
int _vtlog(const char* fmt, va_list args);

#else

/* TLOG is also called from host code, where we don't provide a definition of
 * _tlog. In this case, just printf */
#define _tlog(fmt, ...) fprintf(stderr, fmt, ##__VA_ARGS__)
#define _vtlog(fmt, args) vfprintf(stderr, fmt, args)
#endif

__END_CDECLS

#define TLOG(fmt, ...)                                            \
    do {                                                          \
        _tlog("%s: %d: " fmt, TLOG_TAG, __LINE__, ##__VA_ARGS__); \
    } while (0)

/* debug  */
#define TLOGD(x...)                       \
    do {                                  \
        if (TLOG_LVL >= TLOG_LVL_DEBUG) { \
            TLOG(x);                      \
        }                                 \
    } while (0)

/* info */
#define TLOGI(x...)                      \
    do {                                 \
        if (TLOG_LVL >= TLOG_LVL_INFO) { \
            TLOG(x);                     \
        }                                \
    } while (0)

/* warning */
#define TLOGW(x...)                      \
    do {                                 \
        if (TLOG_LVL >= TLOG_LVL_WARN) { \
            TLOG(x);                     \
        }                                \
    } while (0)

/* error */
#define TLOGE(x...)                       \
    do {                                  \
        if (TLOG_LVL >= TLOG_LVL_ERROR) { \
            TLOG(x);                      \
        }                                 \
    } while (0)

/* critical */
#define TLOGC(x...)                      \
    do {                                 \
        if (TLOG_LVL >= TLOG_LVL_CRIT) { \
            TLOG(x);                     \
        }                                \
    } while (0)
