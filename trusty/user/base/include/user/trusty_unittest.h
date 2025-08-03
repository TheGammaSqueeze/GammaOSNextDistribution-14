/*
 * Copyright (C) 2014-2015 The Android Open Source Project
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

#include <trusty_log.h>

#define trusty_unittest_printf(args...) \
    do {                                \
        _tlog(args);                    \
    } while (0)

#if !DISABLE_TRUSTY_UNITTEST_MACROS
#include <lk/trusty_unittest.h>
#endif

#ifdef TRUSTY_USERSPACE
#include <lib/unittest/unittest.h>
#endif
