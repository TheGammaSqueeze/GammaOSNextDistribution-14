/*
 * Copyright (C) 2022 The Android Open Source Project
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

enum dlmalloc_test_command {
    DLMALLOC_TEST_NOP,
    DLMALLOC_TEST_ONE_MALLOC,
    DLMALLOC_TEST_ONE_CALLOC,
    DLMALLOC_TEST_ONE_REALLOC,
    DLMALLOC_TEST_MANY_MALLOC,
    DLMALLOC_TEST_ONE_NEW,
    DLMALLOC_TEST_ONE_NEW_ARR,
    DLMALLOC_TEST_MALLOC_AND_NEW,
    DLMALLOC_TEST_DOUBLE_FREE,
    DLMALLOC_TEST_REALLOC_AFTER_FREE,
    DLMALLOC_TEST_DEALLOC_TYPE_MISMATCH,
    DLMALLOC_TEST_ALLOC_LARGE,
    DLMALLOC_TEST_BAD_CMD
};

struct dlmalloc_test_msg {
    uint8_t cmd;
};
