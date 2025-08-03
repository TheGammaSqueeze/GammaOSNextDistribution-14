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

#ifndef CHRE_HOST_FILE_STREAM_H_
#define CHRE_HOST_FILE_STREAM_H_

#include <vector>

namespace android {
namespace chre {

/**
 * Reads a file and stores it into a buffer.
 *
 * @param filename The name of the file.
 * @param buffer The buffer to store the contents of the file into.
 * @return true if successfully read and stored.
 */
bool readFileContents(const char *filename, std::vector<uint8_t> &buffer);

}  // namespace chre
}  // namespace android

#endif  // CHRE_HOST_FILE_STREAM_H_
