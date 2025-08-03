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

/** Utils for testing with minimal dependencies. */

#ifndef ART_LIBARTBASE_BASE_TESTING_H_
#define ART_LIBARTBASE_BASE_TESTING_H_

#include <string>
#include <vector>

namespace art {
namespace testing {

// Gets the names of the libcore modules.
// If `core_only` is true, only returns the names of CORE_IMG_JARS in Android.common_path.mk.
std::vector<std::string> GetLibCoreModuleNames(bool core_only = false);

// Gets the paths of the libcore dex files for given modules.
std::vector<std::string> GetLibCoreDexFileNames(const std::vector<std::string>& modules);

// Gets the paths of the libcore dex files.
// If `core_only` is true, only returns the filenames of CORE_IMG_JARS in Android.common_path.mk.
std::vector<std::string> GetLibCoreDexFileNames(bool core_only = false);

}  // namespace testing
}  // namespace art

#endif  // ART_LIBARTBASE_BASE_TESTING_H_
