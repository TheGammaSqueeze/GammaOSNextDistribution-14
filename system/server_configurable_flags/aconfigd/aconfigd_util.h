/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <string>
#include <android-base/result.h>

namespace android {
  namespace aconfigd {

  /// Copy file
  base::Result<void> CopyFile(const std::string& src, const std::string& dst);

  /// Get a file's timestamp
  base::Result<int> GetFileTimeStamp(const std::string& file);

  /// Check if file exists
  bool FileExists(const std::string& file);

  }// namespace aconfig
} // namespace android
