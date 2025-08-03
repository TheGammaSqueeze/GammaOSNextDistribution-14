/*
 * Copyright 2024, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <lib/tipc/tipc_srv.h>
#include <string.h>

__BEGIN_CDECLS

/**
 * struct srv_state - global state of the metrics TA
 * @ns_handle:              Channel corresponding to Android metrics_d
 */
struct srv_state {
    handle_t ns_handle;
};

static inline bool is_ns_connected(struct srv_state* state) {
    return state->ns_handle != INVALID_IPC_HANDLE;
}

static inline void set_srv_state(struct tipc_port* port,
                                 struct srv_state* state) {
    port->priv = state;
}

static inline struct srv_state* get_srv_state(const struct tipc_port* port) {
    return (struct srv_state*)(port->priv);
}

static inline bool equal_uuid(const struct uuid* a, const struct uuid* b) {
    return memcmp(a, b, sizeof(struct uuid)) == 0;
}

__END_CDECLS

