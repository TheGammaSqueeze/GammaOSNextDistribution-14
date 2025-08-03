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

#pragma once

// Check .dynamic section in an ELF and apply necessary relocation.
//
// The API assumes that program_base and dynamic section address are properly aligned.
// For 64bit applications, alignment should be at least multiple of 8bytes.
//
// @program_base: Absolute address the program loaded to.
// @dynamic_section: Absolute address of the dynamic section
//
// Return true on success.
extern "C" bool ApplyRelocation(uintptr_t program_base, uintptr_t dynamic_section);
