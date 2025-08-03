/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef BERBERIS_BASE_CONFIG_GLOBALS_H_
#define BERBERIS_BASE_CONFIG_GLOBALS_H_

#include <cstdint>
#include <string_view>

namespace berberis {

class ConfigStr {
 public:
  ConfigStr(const char* env_name, const char* prop_name);
  [[nodiscard]] const char* get() const { return value_; }

 private:
  const char* value_ = nullptr;
};

void SetMainExecutableRealPath(std::string_view path);
const char* GetMainExecutableRealPath();

void SetAppPackageName(std::string_view name);
const char* GetAppPackageName();

void SetAppPrivateDir(std::string_view name);
const char* GetAppPrivateDir();

const char* GetTracingConfig();

const char* GetTranslationModeConfig();

const char* GetProfilingConfig();

uintptr_t GetEntryPointOverride();

enum ConfigFlag {
  kTopByteIgnore,
  kDisableRegMap,
  kEnableDisjointRegionsTranslation,
  kVerboseTranslation,
  kAccurateSigsegv,
  kNumConfigFlags
};

[[nodiscard]] bool IsConfigFlagSet(ConfigFlag flag);

}  // namespace berberis

#endif  // BERBERIS_BASE_CONFIG_GLOBALS_H_
