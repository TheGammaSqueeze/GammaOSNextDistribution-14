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

#include <cstdint>
#include <random>

#include <ditto/instruction.h>

namespace dittosuite {

class ResizeFile : public Instruction {
 public:
  inline static const std::string kName = "resize_file";

  explicit ResizeFile(const Params& params, int64_t size, int input_fd_key);

 protected:
  void RunSingle() override;

  int64_t size_;
  int input_fd_key_;
};

class ResizeFileRandom : public ResizeFile {
 public:
  explicit ResizeFileRandom(const Params& params, int64_t min, int64_t max, uint64_t seed,
                            Reseeding reseeding, int input_fd_key);

 private:
  void SetUp() override;
  void SetUpSingle() override;

  int64_t min_;
  int64_t max_;
  std::mt19937_64 gen_;
  uint64_t seed_;
  Reseeding reseeding_;
};

}  // namespace dittosuite
