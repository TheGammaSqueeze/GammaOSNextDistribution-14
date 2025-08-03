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

// x86-64 specific constants.

.equ SYS_WRITE,    0x01
.equ SYS_OPEN,     0x02
.equ SYS_CLOSE,    0x03
.equ SYS_GETPID,   0x27
.equ SYS_KILL,     0x3e
.equ SYS_MPROTECT, 0x0a
