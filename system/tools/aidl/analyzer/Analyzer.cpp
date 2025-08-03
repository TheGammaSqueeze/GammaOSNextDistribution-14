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

#include "include/Analyzer.h"

using std::unique_ptr;
using std::unordered_map;

namespace android {
namespace aidl {

using analyzeFn = android::status_t (*)(uint32_t _aidl_code, const android::Parcel& _aidl_data,
                                        const android::Parcel& _aidl_reply);

Analyzer::Analyzer(const std::string& package, const std::string& interface, analyzeFn function)
    : mPackageName(package), mInterfaceName(interface), mAnalyzeFunction(function) {}

const std::string& Analyzer::getPackageName() const {
  return mPackageName;
}

const std::string& Analyzer::getInterfaceName() const {
  return mInterfaceName;
}

const analyzeFn& Analyzer::getAnalyzeFunction() const {
  return mAnalyzeFunction;
}

unordered_map<std::string, unique_ptr<Analyzer>>& Analyzer::getAnalyzers() {
  static unordered_map<std::string, unique_ptr<Analyzer>> gAnalyzers;
  return gAnalyzers;
}

void Analyzer::installAnalyzer(std::unique_ptr<Analyzer> install) {
  getAnalyzers().insert_or_assign(install->getPackageName(), std::move(install));
}

}  // namespace aidl
}  // namespace android
