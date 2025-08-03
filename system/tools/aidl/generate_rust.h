/*
 * Copyright (C) 2020, The Android Open Source Project
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

#include "aidl_language.h"
#include "io_delegate.h"
#include "options.h"

#include <string>

namespace android {
namespace aidl {
namespace rust {

void GenerateRust(const string& filename, const Options& options, const AidlTypenames& types,
                  const AidlDefinedType& defined_type, const IoDelegate& io_delegate);

}  // namespace rust
}  // namespace aidl
}  // namespace android
