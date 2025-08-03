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

#ifndef ART_RUNTIME_RUNTIME_IMAGE_H_
#define ART_RUNTIME_RUNTIME_IMAGE_H_

#include <string>

#include "base/macros.h"

namespace art HIDDEN {

class RuntimeImage {
 public:
    // Writes an app image for the currently running process.
  static bool WriteImageToDisk(std::string* error_msg);

  // Gets the path where a runtime-generated app image is stored.
  //
  // If any of the arguments is a valid glob (a pattern that contains '**' or those documented in
  // glob(7)), returns a valid glob.
  EXPORT static std::string GetRuntimeImagePath(const std::string& app_data_dir,
                                                const std::string& dex_location,
                                                const std::string& isa);

  // Same as above, but takes data dir and ISA from the runtime.
  static std::string GetRuntimeImagePath(const std::string& dex_location);

  // Gets the directory that stores runtime-generated app images. Note that the return value
  // contains a trailing '/'.
  //
  // If the argument is a valid glob (a pattern that contains '**' or those documented in glob(7)),
  // returns a valid glob.
  EXPORT static std::string GetRuntimeImageDir(const std::string& app_data_dir);
};

}  // namespace art

#endif  // ART_RUNTIME_RUNTIME_IMAGE_H_
