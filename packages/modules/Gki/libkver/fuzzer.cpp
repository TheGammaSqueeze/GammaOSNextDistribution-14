//
// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include <stddef.h>
#include <stdint.h>

#include <string>

#include <android-base/logging.h>
#include <kver/kernel_release.h>
#include <kver/kmi_version.h>

using android::kver::KernelRelease;
using android::kver::KmiVersion;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  std::string string_data(reinterpret_cast<const char*>(data), size);

  auto kmi_version = KmiVersion::Parse(string_data);
  if (UNLIKELY(kmi_version.has_value())) {
    CHECK(string_data == kmi_version->string());
  }

  auto kernel_release = KernelRelease::Parse(string_data);
  if (UNLIKELY(kernel_release.has_value())) {
    CHECK(string_data == kernel_release->string());
    CHECK(!kernel_release->kmi_version().string().empty());
  }

  return 0;
}
