/*
 * Copyright (C) 2023 The Android Open Source Project
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

/* Ensure that we don't try to use the metrics backend */
#undef STORAGE_ENABLE_ERROR_REPORTING
#include "error_reporting.h"

struct mock_storage_error_report {
    enum stats_trusty_storage_error_type type;
    const char* fs_name;
    enum error_report_block_type block_type;
    struct list_node node;
};

struct mock_storage_error_report* mock_error_report_next(void);

void mock_error_report_clear(void);

void expect_errors(enum stats_trusty_storage_error_type type, int count);
