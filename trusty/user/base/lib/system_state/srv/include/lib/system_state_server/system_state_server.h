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

#include <interface/system_state/system_state.h>
#include <lk/compiler.h>
#include <sys/types.h>

__BEGIN_CDECLS

struct tipc_hset;

/**
 * system_state_server_get_flag() - Get the current value of a system flag
 * @flag:   Identifier for flag to get. One of @enum system_state_flag.
 * @valuep: Pointer to return value in.
 *
 * Must be implemented by client of lib_system_state_server.
 *
 * Return: 0 on success, or an error code < 0 on failure.
 */
int system_state_server_get_flag(uint32_t flag, uint64_t* valuep);

/**
 * add_system_state_service() - Add system_state service
 * @hset: Handle set created by tipc_hset_create()
 *
 * Client should call tipc_run_event_loop at some point after this call returns.
 *
 * Return: 0 on success, or an error code < 0 on failure.
 */
int add_system_state_service(struct tipc_hset* hset);

__END_CDECLS
