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

#ifndef APP_MGMT_H
#define APP_MGMT_H

#include <stdio.h>
#include <trusty_ipc.h>
#include <trusty_log.h>

#define MAX_CMD_LEN 1

enum {
    CMD_NOP = 0,
    CMD_CLOSE_PORT = 1,
    CMD_EXIT = 2,
    CMD_OPEN_PORT = 3,
};

enum {
    RSP_OK = 0,
    RSP_CMD_FAILED = 1,
    RSP_INVALID_CMD = 2,
};

#endif
