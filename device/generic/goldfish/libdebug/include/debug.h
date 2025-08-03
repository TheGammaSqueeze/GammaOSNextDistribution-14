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

#if 1

#include <log/log.h>

#ifdef FAILURE_DEBUG_PREFIX

#define FAILURE(X) \
    (ALOGE("%s:%s:%d failure: %s", FAILURE_DEBUG_PREFIX, __func__, __LINE__, #X), X)

#define FAILURE_V(X, FMT, ...) \
    (ALOGE("%s:%s:%d failure: " FMT, FAILURE_DEBUG_PREFIX, __func__, __LINE__, __VA_ARGS__), X)

#else

#define FAILURE(X) \
    (ALOGE("%s:%d failure: %s", __func__, __LINE__, #X), X)

#define FAILURE_V(X, FMT, ...) \
    (ALOGE("%s:%d failure: " FMT, __func__, __LINE__, __VA_ARGS__), X)

#endif

#else

#define FAILURE(X) X
#define FAILURE_V(X, ...) X

#endif
