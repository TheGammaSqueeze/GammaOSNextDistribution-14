/*
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Disassembles an APF program into a human-readable format.
 *
 * @param program the program bytecode.
 * @param program_len the length of the program bytecode.
 * @param ptr2pc pointer to the program counter which points to the current instruction.
 *           After function call, the program counter will be updated to point to the
 *           next instruction.
 *
 * @return pointer to static buffer which contains human readable text.
 */
const char* apf_disassemble(const uint8_t* program, uint32_t program_len, uint32_t* ptr2pc);

#ifdef __cplusplus
}
#endif
