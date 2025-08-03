/*
 * Copyright (C) 2021 The Android Open Source Project
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

#if USER_TASK
#error "lk/trusty_log.h should never be included from a user task"
#endif

#include <debug.h>

#define _tlog(level, fmt, x...) dprintf(level, fmt, ##x)
#define _vtlog(level, fmt, args) vdprintf(level, fmt, args)

#define TLOG(fmt, x...) _tlog(ALWAYS, "%s: %d: " fmt, TLOG_TAG, __LINE__, ##x)

/* debug  */
#define TLOGD(fmt, x...) _tlog(SPEW, "%s: %d: " fmt, TLOG_TAG, __LINE__, ##x)

/* info */
#define TLOGI(fmt, x...) _tlog(SPEW, "%s: %d: " fmt, TLOG_TAG, __LINE__, ##x)

/* warning */
#define TLOGW(fmt, x...) _tlog(INFO, "%s: %d: " fmt, TLOG_TAG, __LINE__, ##x)

/* error */
#define TLOGE(fmt, x...) \
    _tlog(CRITICAL, "%s: %d: " fmt, TLOG_TAG, __LINE__, ##x)

/* critical */
#define TLOGC(fmt, x...) \
    _tlog(CRITICAL, "%s: %d: " fmt, TLOG_TAG, __LINE__, ##x)
