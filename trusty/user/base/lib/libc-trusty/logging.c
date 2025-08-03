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

#include <stdarg.h>
#include <stdio.h>
#include <trusty_log.h>

/* Should be preempted by the definition in libunittest, if available. */
__attribute__((__weak__)) int _vtlog(const char* fmt, va_list args) {
    return vfprintf(stderr, fmt, args);
}

int _tlog(const char* fmt, ...) {
    int rc;
    va_list ap;
    va_start(ap, fmt);
    rc = _vtlog(fmt, ap);
    va_end(ap);
    return rc;
}
