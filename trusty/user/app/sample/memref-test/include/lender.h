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

#pragma once

#include <sys/types.h>

enum lender_command {
    LENDER_LEND_BSS,
    LENDER_LEND_RW,
    LENDER_SUICIDE,
    LENDER_READ_BSS,
    LENDER_WRITE_BSS,
    LENDER_INVALID_COMMAND = -1,
};

struct lender_region {
    size_t offset;
    size_t size;
};

struct lender_msg {
    enum lender_command cmd;
    struct lender_region region;
};
