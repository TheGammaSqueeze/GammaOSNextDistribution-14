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

#include "chre_host/file_stream.h"

#include <fstream>
#include "chre_host/log.h"

namespace android {
namespace chre {

bool readFileContents(const char *filename, std::vector<uint8_t> &buffer) {
  bool success = false;
  std::ifstream file(filename, std::ios::binary | std::ios::ate);
  if (!file) {
    LOGE("Couldn't open file '%s': %d (%s)", filename, errno, strerror(errno));
  } else {
    ssize_t size = file.tellg();
    file.seekg(0, std::ios::beg);

    buffer.resize(size);
    if (!file.read(reinterpret_cast<char *>(buffer.data()), size)) {
      LOGE("Couldn't read from file '%s': %d (%s)", filename, errno,
           strerror(errno));
    } else {
      success = true;
    }
  }

  return success;
}

}  // namespace chre
}  // namespace android
