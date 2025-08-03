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

#include <interface/hwwsk/hwwsk.h>

#include <err.h>
#include <stdint.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <trusty_ipc.h>

/*
 * Client for Hardware wrapped storage key (HWWSK) service
 */

__BEGIN_CDECLS

/**
 * hwwsk_generate_key() - creates new persistent key
 * @chan: IPC channel to HWWSK service
 * @buf: pointer to the buffers to store resulting key blob
 * @buf_sz: size of the @buf buffer
 * @key_size: key size in bits
 * @key_flag: a combination of &enum hwwsk_key_flags to specify an additional
 *            properties of generated key.
 * @raw_key: pointer to the buffer containing raw key data for import operation
 * @raw_key_len: size of key specified by @raw_key parameter
 *
 * This routine creates new hardware wrapped storage key by either generating
 * a new random key or importing raw key material if specified by caller.
 * The resulting key must be persistent.
 *
 * Return: number of bytes placed into @buf buffer on success,
 *         negative error code otherwise
 *
 */
int hwwsk_generate_key(handle_t chan,
                       void* buf,
                       size_t buf_sz,
                       uint32_t key_size,
                       uint32_t key_flags,
                       const void* raw_key,
                       size_t raw_key_len);

/**
 * hwwsk_export_key() - rewrap specified SK key with ESK
 * @chan: IPC channel to HWWSK service
 * @buf: pointer to the buffers to store resulting key blob
 * @buf_sz: size of the @buf buffer
 * @key_blob: pointer to key blob to rewrap
 * @key_blob_len: size of key blob specified by @key_blob
 *
 * This routine rewraps specified persistent SK key with ephemeral storage
 * key (ESK). The resulting key is only good for current session.
 *
 * Return: number of bytes placed into @buf buffer on success,
 *         negative  error code otherwise
 */
int hwwsk_export_key(handle_t chan,
                     void* buf,
                     size_t buf_sz,
                     const void* key_blob,
                     size_t key_blob_len);

__END_CDECLS
