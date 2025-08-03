/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#pragma once

#include <uapi/trusty_uuid.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx */
#define UUID_STR_SIZE (37)

/**
 * uuid_to_str() - Converts from a uuid to its string representation
 * @uuid: The uuid to convert
 * @str: Buffer to put the string representation in. This buffer must be at
 *       least UUID_STRT_SIZE bytes long
 */
void uuid_to_str(const struct uuid* uuid, char* str);

/**
 * str_to_uuid() - Converts a string into a uuid
 * @uuid_str: String representation of the uuid
 * @uuid: &strcut uuid to fill with the converted uuid
 *
 * Return: 0 on success or -1 if str did not contain a valid uuid.
 *
 */
__attribute__((warn_unused_result)) int str_to_uuid(const char* str,
                                                    struct uuid* uuid);

#ifdef __cplusplus
}
#endif
