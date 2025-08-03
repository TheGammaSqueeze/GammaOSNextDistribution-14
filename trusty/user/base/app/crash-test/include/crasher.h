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

enum crasher_command {
    CRASHER_NOP,
    CRASHER_EXIT_SUCCESS,
    CRASHER_EXIT_FAILURE,
    CRASHER_READ_NULL_PTR,
    CRASHER_READ_BAD_PTR,
    CRASHER_WRITE_BAD_PTR,
    CRASHER_WRITE_RO_PTR,
    CRASHER_EXEC_RODATA,
    CRASHER_EXEC_DATA,
    CRASHER_BRK,
};

struct crasher_msg {
    uint8_t cmd;
};
