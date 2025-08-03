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

#pragma once

#include <binder/Parcel.h>
#include <unordered_map>

using std::unique_ptr;
using std::unordered_map;

using analyzeFn = android::status_t (*)(uint32_t _aidl_code, const android::Parcel& _aidl_data,
                                        const android::Parcel& _aidl_reply);

namespace android {
namespace aidl {

class Analyzer {
 public:
  Analyzer(const std::string& package, const std::string& interface, analyzeFn function);

  const std::string& getPackageName() const;
  const std::string& getInterfaceName() const;
  const analyzeFn& getAnalyzeFunction() const;

  static unordered_map<std::string, unique_ptr<Analyzer>>& getAnalyzers();
  static void installAnalyzer(std::unique_ptr<Analyzer> install);

 private:
  std::string mPackageName;
  std::string mInterfaceName;
  analyzeFn mAnalyzeFunction;
};

}  // namespace aidl
}  // namespace android
