/*
 * Copyright 2020, The Android Open Source Project
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

#include <lib/secure_fb/srv/dev.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/compiler.h>
#include <stdint.h>

__BEGIN_CDECLS

/**
 * add_secure_fb_service() - Add secure_fb service.
 * @hset: Handle set created by tipc_hset_create()
 * @impl_ops: Array of the secure_fb_impl_ops pointers.
 * @num_ops: Number of instances to be added.
 *
 * The caller should call tipc_run_event_loop() at some point after this call
 * returns.
 *
 * Return: 0 on success, or an error code < 0 on failure.
 */
int add_secure_fb_service(struct tipc_hset* hset,
                          const struct secure_fb_impl_ops* impl_ops,
                          uint32_t num_ops);

__END_CDECLS
