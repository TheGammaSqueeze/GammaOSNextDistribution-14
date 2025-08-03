/*
 *  Copyright 2020 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

#pragma once

#include <cstdint>
// This header contains functions for Controller Module to invoke

void l2cu_set_non_flushable_pbf(bool is_supported);

void l2c_link_init(const uint16_t acl_buffer_count_classic);

void l2c_link_processs_ble_num_bufs(uint16_t num_lm_acl_bufs);

void l2cu_device_reset(void);
