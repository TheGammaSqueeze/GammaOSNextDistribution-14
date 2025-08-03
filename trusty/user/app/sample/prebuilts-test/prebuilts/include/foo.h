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

#include <lk/compiler.h>
#include <stddef.h>

__BEGIN_CDECLS

extern const char* foo;

/**
 * get_foo() - write string @foo into given buffer
 * @buf: buffer to place the result in
 * @len: length of @buf
 *
 * Return: 0 on success, negative error code on error
 */
int get_foo(void* buf, size_t len);

__END_CDECLS
