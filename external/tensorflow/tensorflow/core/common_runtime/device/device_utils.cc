/* Copyright 2021 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/
#include "tensorflow/core/common_runtime/device/device_utils.h"

#include <regex>

#include "tensorflow/core/platform/status.h"
#include "tensorflow/core/platform/strcat.h"
#include "tensorflow/core/platform/stringpiece.h"

namespace tensorflow {
namespace device_utils {
constexpr const char* kTfDeviceTypeRegEx = "[A-Z][A-Z_]*";

Status ValidateDeviceType(StringPiece type) {
  std::string device_type(type);
  std::smatch match_results;
  std::regex regex_exp(kTfDeviceTypeRegEx);
  bool matches = std::regex_match(device_type, match_results, regex_exp);
  if (!matches) {
    return Status(error::FAILED_PRECONDITION,
                  strings::StrCat("Device name/type '", type, "' must match ",
                                  kTfDeviceTypeRegEx, "."));
  }
  return OkStatus();
}

}  // namespace device_utils
}  // namespace tensorflow
