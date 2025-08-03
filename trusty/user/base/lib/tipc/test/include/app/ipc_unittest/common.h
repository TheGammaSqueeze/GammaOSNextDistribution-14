/*
 * Copyright (C) 2015 The Android Open Source Project
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

/* Expected limits: should be in sync with kernel settings */
#define USER_BASE_HANDLE 1000  /* first user handle id */
#define MAX_USER_HANDLES IPC_MAX_HANDLES
#define MAX_PORT_PATH_LEN 64   /* max length of port path name   */
#define MAX_PORT_BUF_NUM 32    /* max number of per port buffers */
#define MAX_PORT_BUF_SIZE 4096 /* max size of per port buffer    */

#define FIRST_FREE_HANDLE (3)

#define MSEC 1000000ULL
#define SRV_PATH_BASE "com.android.ipc-unittest"

#include <trusty_log.h>
