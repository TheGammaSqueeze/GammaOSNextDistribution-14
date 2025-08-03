/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "android-modules-utils/sdk_level.h"
#include "android-modules-utils/unbounded_sdk_level.h"

namespace android {
namespace modules {
namespace sdklevel {
namespace nostl {

// This file should be built without libc++. Even without the references below
// it should not build while including the library headers if there were unmet
// dependencies in the first place, but this guards against moving the
// IsAtLeastX methods somewhere else and losing the check.
bool IsAtLeastR() { return android::modules::sdklevel::IsAtLeastR(); }
bool IsAtLeastS() { return android::modules::sdklevel::IsAtLeastS(); }
bool IsAtLeastT() { return android::modules::sdklevel::IsAtLeastT(); }

bool IsAtLeast(const char *version) {
  return android::modules::sdklevel::unbounded::IsAtLeast(version);
};
bool IsAtMost(const char *version) {
  return android::modules::sdklevel::unbounded::IsAtMost(version);
};

} // namespace nostl
} // namespace sdklevel
} // namespace modules
} // namespace android
