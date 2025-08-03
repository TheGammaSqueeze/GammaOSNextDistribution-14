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

enum crasher_command {
    CRASHER_NOP,
    CRASHER_CORRECT,
    CRASHER_ENTRY,
    CRASHER_EXCLUDE_WRONG_TYPE,
    CRASHER_EXCLUDE_NOT_ENTRY,
    CRASHER_WRONG_TYPE,
    CRASHER_NOT_ENTRY,
    CRASHER_VOID,
    CRASHER_BAD_CMD
};

struct crasher_msg {
    uint8_t cmd;
};
