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

#include <interface/keybox/keybox.h>
#include <lk/compiler.h>
#include <stddef.h>
#include <stdint.h>
#include <trusty_ipc.h>

__BEGIN_CDECLS

/**
 * keybox_unwrap() - Unwraps a keybox.
 *
 * @wrapped_keybox:            Pointer to a wrapped keybox.
 * @wrapped_keybox_size:       Size of the wrapped keybox.
 * @unwrapped_keybox:          Buffer to unwrap into.
 * @unwrapped_keybox_buf_size: Size of the buffer to unwrap into.
 * @unwrapped_keybox_size:     Out parameter for amount of the buffer used.
 *
 * Unwraps a keybox using device secrets via device-specific means.
 *
 * Returns: 0 on success, negative error code is from uapi/err.h, positive
 *          error code is from the &enum keybox_status in the keybox
 *          interface.
 */
int keybox_unwrap(const uint8_t* wrapped_keybox,
                  size_t wrapped_keybox_size,
                  uint8_t* unwrapped_keybox,
                  size_t unwrapped_keybox_buf_size,
                  size_t* unwrapped_keybox_size);

__END_CDECLS
