/*
 * Copyright 2022, The Android Open Source Project
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

__BEGIN_CDECLS

/**
 * add_metrics_consumer_service() - initialize metrics consumer service
 * @hset: pointer to &struct tipc_hset
 *
 * Return: 0 on success, negative error code on error
 */
int add_metrics_consumer_service(struct tipc_hset* hset);

__END_CDECLS
