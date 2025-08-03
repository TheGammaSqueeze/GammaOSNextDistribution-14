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

#define TLOG_TAG "crasher"

#include <lk/compiler.h>
#include <trusty_log.h>

__SECTION(".rodata") void crasher_rodata_func(void) {
    TLOG("function in rodata ran\n");
};

__SECTION(".data") void crasher_data_func(void) {
    TLOG("function in data ran\n");
};
