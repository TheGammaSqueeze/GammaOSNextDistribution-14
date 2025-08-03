/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <lk/compiler.h>
#include <stddef.h>
#include <stdint.h>

__BEGIN_CDECLS

/*
 * These function abstract device-specific details of HWRNG and must be defined
 * per platform.
 */

/*
 * hwrng_dev_init() - initialize HWRNG devices
 *
 * Return: NO_ERROR on success, a negative error code otherwise.
 */
int hwrng_dev_init(void);

/*
 * hwrng_dev_get_rng_data() - get hardware-generated random data
 * @buf: buffer to be filled up
 * @buf_len: requested amount of random data
 *
 * Return: NO_ERROR on success, a negative error code otherwise.
 */
int hwrng_dev_get_rng_data(uint8_t* buf, size_t buf_len);

__END_CDECLS
