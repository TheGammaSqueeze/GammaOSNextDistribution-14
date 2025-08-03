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

#pragma once

#include <libelf64/parse.h>

#include <functional>

namespace android {
namespace elf64 {

using Elf64Callback = std::function<void(const Elf64Binary&)>;

// Public APIs
/**
 * Returns the number of ELF files were processed successfully.
 */
int ForEachElf64FromDir(const std::string& path, const Elf64Callback& callback);

}  // namespace elf64
}  // namespace android
