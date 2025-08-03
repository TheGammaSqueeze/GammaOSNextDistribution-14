/*
 * Copyright (C) 2023, The Android Open Source Project
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

#include <string>

#include "aidl_language.h"
#include "code_writer.h"
#include "options.h"

// This is used to help generate code targeting to any language

namespace android {
namespace aidl {

enum class CommunicationSide {
  WRITE = 0x1,
  READ = 0x2,
  BOTH = WRITE | READ,
};

constexpr int kDowngradeCommunicationBitmap = static_cast<int>(CommunicationSide::BOTH);

// This is used when adding the trunk stable downgrade to unfrozen interfaces.
// The kDowngradeCommunicationBitmap constant can be used to only modify one side of
// the generated interface libraries so we can make sure both sides are forced
// to behave like the previous unfrozen version.
// BOTH is standard operating config, but can be switched for testing.
bool ShouldForceDowngradeFor(CommunicationSide e);

// currently relies on all backends having the same comment style, but we
// could take a comment type argument in the future
void GenerateAutoGenHeader(CodeWriter& out, const Options& options);

}  // namespace aidl
}  // namespace android
