// Copyright (C) 2021 The Android Open Source Project
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

#pragma once

#include <random>
#include <string>

#include <ditto/instruction.h>

namespace dittosuite {

class OpenFile : public Instruction {
 public:
  inline static const std::string kName = "open_file";
  enum class AccessMode { kReadOnly, kWriteOnly, kReadWrite };

  explicit OpenFile(const Params& params, const std::string& path_name, bool create, bool direct_io,
                    int output_fd_key, AccessMode access_mode);
  explicit OpenFile(const Params& params, int input_key, bool create, bool direct_io,
                    int output_fd_key, AccessMode access_mode);
  explicit OpenFile(const Params& params, bool create, bool direct_io, int output_fd_key,
                    AccessMode access_mode);

 private:
  void SetUpSingle() override;
  void RunSingle() override;

  bool random_name_;
  std::string path_name_;
  bool create_;
  bool direct_io_;
  int input_key_;
  int output_fd_key_;
  std::mt19937_64 gen_;
  AccessMode access_mode_;
};

}  // namespace dittosuite
