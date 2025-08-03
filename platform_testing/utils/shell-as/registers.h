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

#ifndef SHELL_AS_REGISTERS_H_
#define SHELL_AS_REGISTERS_H_

#if defined(__aarch64__)

#define REGISTER_STRUCT struct user_pt_regs
#define PROGRAM_COUNTER(regs) (regs.pc)

#elif defined(__i386__)

#include "sys/user.h"
#define REGISTER_STRUCT struct user_regs_struct
#define PROGRAM_COUNTER(regs) (regs.eip)

#elif defined(__x86_64__)

#include "sys/user.h"
#define REGISTER_STRUCT struct user_regs_struct
#define PROGRAM_COUNTER(regs) (regs.rip)

#elif defined(__arm__)

#define REGISTER_STRUCT struct user_regs
#define PROGRAM_COUNTER(regs) (regs.ARM_pc)

#endif

#endif  // SHELL_AS_REGISTERS_H_
