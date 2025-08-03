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

#include <err.h>
#include <errno.h>
#include <kernel/thread.h>
#include <stdlib.h>
#include <trusty/libc_state.h>
#include "locale_impl.h"

static struct libc_state* libc_state_create(void) {
    struct libc_state* state = calloc(1, sizeof(struct libc_state));
    if (state != NULL) {
        state->errno_val = 0;
        state->locale = C_LOCALE;
    }
    return state;
}

int libc_state_thread_init(thread_t* t) {
    if (t == NULL) {
        return ERR_INVALID_ARGS;
    }
    struct libc_state* state = libc_state_create();
    if (state == NULL) {
        return ERR_NO_RESOURCES;
    }
    thread_tls_set(t, TLS_ENTRY_LIBC, (uintptr_t)state);
    return NO_ERROR;
}

int libc_state_thread_free(thread_t* t) {
    if (t == NULL) {
        return ERR_INVALID_ARGS;
    }
    struct libc_state* state =
            (struct libc_state*)thread_tls_get(t, TLS_ENTRY_LIBC);
    ASSERT(state);
    free(state);

    /* Replace the old TLS entry with NULL to remove the dangling pointer and
     * catch double frees */
    thread_tls_set(t, TLS_ENTRY_LIBC, (uintptr_t)NULL);

    return NO_ERROR;
}

struct libc_state* current_thread_libc_state(void) {
    return (struct libc_state*)tls_get(TLS_ENTRY_LIBC);
}

int* __errno_location(void) {
    struct libc_state* state = current_thread_libc_state();
    DEBUG_ASSERT(state);
    return &state->errno_val;
}

weak_alias(__errno_location, ___errno_location);
