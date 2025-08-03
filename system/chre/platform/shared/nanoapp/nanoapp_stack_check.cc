/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "chre/util/nanoapp/log.h"
#include "chre_api/chre/re.h"
#include "chre_api/chre/toolchain.h"

/**
 * @file
 * Stack check support.
 *
 * The symbols defined in this file are needed when the code is compiled with
 * the -fstack-protector flag.
 */

#ifndef LOG_TAG
#define LOG_TAG "[STACK CHECK]"
#endif  // LOG_TAG

#ifdef __cplusplus
extern "C" {
#endif

uintptr_t __stack_chk_guard = 0x56494342;

// Terminate a function in case of stack overflow.
void __stack_chk_fail(void) CHRE_NO_RETURN;
void __stack_chk_fail(void) {
  LOGE("Stack corruption detected");
  chreAbort(/*abortCode=*/0);
}

#ifdef __cplusplus
}
#endif