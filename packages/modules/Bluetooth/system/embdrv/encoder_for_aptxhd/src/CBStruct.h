/**
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
/*------------------------------------------------------------------------------
 *
 * Structure required to implement a circular buffer.
 *
 *-----------------------------------------------------------------------------*/

#ifndef CBSTRUCT_H
#define CBSTRUCT_H
#ifdef _GCC
#pragma GCC visibility push(hidden)
#endif

typedef struct circularBuffer_t {
  /* Buffer storage */
  int32_t buffer[48];
  /* Pointer to current buffer location */
  uint32_t pointer;
  /* Modulo length of circular buffer */
  uint32_t modulo;
} circularBuffer;

#ifdef _GCC
#pragma GCC visibility pop
#endif
#endif  // CBSTRUCT_H
