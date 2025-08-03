/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "./string-utils.h"

#include <errno.h>
#include <stdlib.h>
#include <string.h>

namespace shell_as {

bool StringToUInt32(const char* s, uint32_t* i) {
  uint64_t value = 0;
  if (!StringToUInt64(s, &value)) {
    return false;
  }
  if (value > UINT_MAX) {
    return false;
  }
  *i = value;
  return true;
}

bool StringToUInt64(const char* s, uint64_t* i) {
  char* endptr = nullptr;
  // Reset errno to a non-error value since strtoul does not clear errno.
  errno = 0;
  *i = strtoul(s, &endptr, 10);
  // strtoul will return 0 if the value cannot be parsed as an unsigned long. If
  // this occurs, ensure that the ID actually was zero. This is done by ensuring
  // that the end pointer was advanced and that it now points to the end of the
  // string (a null byte).
  return errno == 0 && (*i != 0 || (endptr != s && *endptr == '\0'));
}

bool SplitIdsAndSkip(char* line, const char* separators, int num_to_skip,
                     std::vector<uid_t>* ids) {
  if (line == nullptr) {
    return false;
  }

  ids->clear();
  for (char* id_string = strtok(line, separators); id_string != nullptr;
       id_string = strtok(nullptr, separators)) {
    if (num_to_skip > 0) {
      num_to_skip--;
      continue;
    }

    gid_t id;
    if (!StringToUInt32(id_string, &id)) {
      return false;
    }
    ids->push_back(id);
  }
  return true;
}

}  // namespace shell_as
