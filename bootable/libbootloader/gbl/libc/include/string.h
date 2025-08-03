/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef __STDLIB_STRING_H__
#define __STDLIB_STRING_H__

#include <stddef.h>

size_t strlen(const char * str);
void *memchr(const void *ptr, int ch, size_t count);
char *strrchr(const char *str, int c);
size_t strnlen(const char *s, size_t maxlen);

#endif
