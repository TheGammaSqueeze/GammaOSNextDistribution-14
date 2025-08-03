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

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#pragma once

#define BOOT_DONE_PORT "com.android.trusty.boot_done.tidl"

__BEGIN_CDECLS

/**
 * boot_done_set_boot_done()
 *
 * @return: 0 on success, or an error code < 0 on failure.
 */
int boot_done_set_boot_done(void);

__END_CDECLS
