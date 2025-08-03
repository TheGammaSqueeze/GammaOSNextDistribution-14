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

#include "aidl_to_common.h"

namespace android {
namespace aidl {

bool ShouldForceDowngradeFor(CommunicationSide e) {
  return kDowngradeCommunicationBitmap & static_cast<int>(e);
}

void GenerateAutoGenHeader(CodeWriter& out, const Options& options) {
  out << "/*\n";
  out << " * This file is auto-generated.  DO NOT MODIFY.\n";
  out << " * Using: " << options.RawArgs() << "\n";
  out << " */\n";
}

}  // namespace aidl
}  // namespace android
