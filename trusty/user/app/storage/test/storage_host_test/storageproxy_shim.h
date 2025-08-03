/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <stdbool.h>

bool init_rpmb_state(const char* backing_filename);
void destroy_rpmb_state();

/**
 * fail_next_rpmb_writes - Fail the next @count RPMB write commands
 * @count -         Number of subsequent RPMB write commands to fail
 * @commit_writes - If %true, the RPMB device will actually perform the write(s)
 *                  but reply with failure. This simulates issues either in the
 *                  flash chip or in the kernel where the write occurs but
 *                  storage does not receive a valid reply.
 *
 * Used for testing failure conditions
 */
void fail_next_rpmb_writes(int count, bool commit_writes);

/**
 * fail_next_rpmb_reads - Fail the next @count RPMB read commands
 * @count - Number of subsequent RPMB read commands to fail
 *
 * Used for testing failure conditions
 */
void fail_next_rpmb_reads(int count);

/**
 * fail_next_rpmb_get_counters - Fail the next @count RPMB get counter commands
 * @count - Number of subsequent RPMB get counter commands to fail
 *
 * Used for testing failure conditions
 */
void fail_next_rpmb_get_counters(int count);

/**
 * ignore_next_ns_writes - Silently ignore the next @count writes to NS backing
 *                         files
 * @count:      Number of subsequent NS writes to ignore. If %INT_MAX, ignore
 *              all subsequent writes.
 *
 * Used for testing failure conditions
 */
void ignore_next_ns_writes(int count);
