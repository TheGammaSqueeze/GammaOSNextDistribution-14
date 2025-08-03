/*
 * Copyright 2022 The Android Open Source Project
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

#include "metrics/utils.h"

#include <base/files/file_util.h>
#include <base/strings/string_util.h>

namespace bluetooth {
namespace metrics {

namespace {
// The path to the kernel's boot_id.
const char kBootIdPath[] = "/proc/sys/kernel/random/boot_id";
}  // namespace

bool GetBootId(std::string* boot_id) {
  if (!base::ReadFileToString(base::FilePath(kBootIdPath), boot_id)) {
    return false;
  }
  base::TrimWhitespaceASCII(*boot_id, base::TRIM_TRAILING, boot_id);
  return true;
}

int GetArgumentTypeFromList(
    std::vector<std::pair<os::ArgumentType, int>>& argument_list, os::ArgumentType argumentType) {
  for (std::pair<os::ArgumentType, int> argumentPair : argument_list) {
    if (argumentPair.first == argumentType) {
      return argumentPair.second;
    }
  }
  return -1;
}

os::LeConnectionType GetLeConnectionTypeFromCID(int fixed_cid) {
  switch(fixed_cid) {
    case 3: {
      return os::LeConnectionType::CONNECTION_TYPE_L2CAP_FIXED_CHNL_AMP;
    }
    case 4: {
      return os::LeConnectionType::CONNECTION_TYPE_L2CAP_FIXED_CHNL_ATT;
    }
    case 5: {
      return os::LeConnectionType::CONNECTION_TYPE_L2CAP_FIXED_CHNL_LE_SIGNALLING;
    }
    case 6: {
      return os::LeConnectionType::CONNECTION_TYPE_L2CAP_FIXED_CHNL_SMP;
    }
    case 7: {
      return os::LeConnectionType::CONNECTION_TYPE_L2CAP_FIXED_CHNL_SMP_BR_EDR;
    }
    default: {
      return os::LeConnectionType::CONNECTION_TYPE_UNSPECIFIED;
    }
  }
}



}  // namespace metrics
}  // namespace bluetooth
