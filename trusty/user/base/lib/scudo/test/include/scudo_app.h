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

#include <sys/types.h>

enum scudo_command {
    SCUDO_NOP,
    SCUDO_ONE_MALLOC,
    SCUDO_ONE_CALLOC,
    SCUDO_ONE_REALLOC,
    SCUDO_MANY_MALLOC,
    SCUDO_ONE_NEW,
    SCUDO_ONE_NEW_ARR,
    SCUDO_MALLOC_AND_NEW,
    SCUDO_DOUBLE_FREE,
    SCUDO_REALLOC_AFTER_FREE,
    SCUDO_DEALLOC_TYPE_MISMATCH,
    SCUDO_REALLOC_TYPE_MISMATCH,
    SCUDO_ALLOC_LARGE,
    SCUDO_ALLOC_BENCHMARK,
    SCUDO_UNTAGGED_MEMREF_SMALL,
    SCUDO_TAGGED_MEMREF_SMALL,
    SCUDO_UNTAGGED_MEMREF_LARGE,
    SCUDO_TAGGED_MEMREF_LARGE,
    SCUDO_MEMTAG_MISMATCHED_READ,
    SCUDO_MEMTAG_MISMATCHED_WRITE,
    SCUDO_MEMTAG_READ_AFTER_FREE,
    SCUDO_MEMTAG_WRITE_AFTER_FREE,
    SCUDO_TEST_FAIL,
    SCUDO_BAD_CMD
};

struct scudo_msg {
    uint8_t cmd;
};
