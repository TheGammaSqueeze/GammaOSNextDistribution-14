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

#include "base/sdk_version.h"
#include "dex/art_dex_file_loader.h"
#include "hidden_api.h"
#include "jni.h"
#include "runtime.h"
#include "ti-agent/scoped_utf_chars.h"

namespace art {

extern "C" JNIEXPORT void JNICALL Java_Main_enableHiddenApiChecks(JNIEnv*, jclass) {
  Runtime* runtime = Runtime::Current();
  runtime->SetHiddenApiEnforcementPolicy(hiddenapi::EnforcementPolicy::kEnabled);
}

}  // namespace art
