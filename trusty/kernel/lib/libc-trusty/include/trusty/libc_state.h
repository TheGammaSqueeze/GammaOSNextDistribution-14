/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include <locale.h>

/* We can't include kernel/thread.h because it indirectly defines macros that
 * conflict with macros indirectly defined by musl/src/internal/locale_impl.h
 * which needs to include this header. Instead we redeclare thread_t since we
 * don't need it's definition */
typedef struct thread thread_t;

struct libc_state {
    int errno_val;
    locale_t locale;
};

struct libc_state* current_thread_libc_state(void);

int libc_state_thread_init(thread_t* t);
int libc_state_thread_free(thread_t* t);
