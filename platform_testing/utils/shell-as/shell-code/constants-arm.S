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

// Arm specific constants.

.equ SYS_OPEN,     0x000005
.equ SYS_CLOSE,    0x000006
.equ SYS_WRITE,    0x000004
.equ SYS_KILL,     0x000025
.equ SYS_GETPID,   0x000014
.equ SYS_MPROTECT, 0x00007d
