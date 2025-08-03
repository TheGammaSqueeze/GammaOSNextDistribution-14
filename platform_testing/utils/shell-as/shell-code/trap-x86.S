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

#include "./shell-code/constants.S"

.globl __trap_shell_code_start
.globl __trap_shell_code_end
.globl __trap_shell_code_signal

__trap_shell_code_start:
int $0x03
__trap_shell_code_end:

__trap_shell_code_signal:
.int SIGTRAP
