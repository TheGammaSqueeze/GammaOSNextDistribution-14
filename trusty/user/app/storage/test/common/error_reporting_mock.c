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

#include <assert.h>
#include <lk/list.h>
#include <malloc.h>
#include <stdio.h>

#include "fs.h"

#include "error_reporting_mock.h"

static struct list_node error_reports = LIST_INITIAL_VALUE(error_reports);

struct mock_storage_error_report* mock_error_report_next(void) {
    return list_remove_head_type(&error_reports,
                                 struct mock_storage_error_report, node);
}

void mock_error_report_clear(void) {
    struct mock_storage_error_report* report;
    while (!list_is_empty(&error_reports)) {
        report = list_remove_head_type(&error_reports,
                                       struct mock_storage_error_report, node);
        assert(report);
        free(report);
    }
}

void expect_errors(enum stats_trusty_storage_error_type type, int count) {
    int i;
    struct mock_storage_error_report* err_report;

    for (i = 0; i < count; i++) {
        err_report = mock_error_report_next();
        assert(err_report && err_report->type == type);
        free(err_report);
    }
    assert(!mock_error_report_next());
}

void do_error_report(enum stats_trusty_storage_error_type type,
                     const char* fs_name,
                     enum error_report_block_type block_type) {
    struct mock_storage_error_report* report = malloc(sizeof(*report));
    assert(report);
    report->type = type;
    report->fs_name = fs_name;
    report->block_type = block_type;

    list_add_tail(&error_reports, &report->node);
}
