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

#include <pthread.h>

#include <ditto/instruction.h>
#include <ditto/multithreading_utils.h>

namespace dittosuite {

class Multithreading : public Instruction {
 public:
  inline static const std::string kName = "multithreading";

  explicit Multithreading(const Instruction::Params& params,
                          std::vector<std::unique_ptr<Instruction>> instructions,
                          std::vector<MultithreadingParams> thread_params);

  std::unique_ptr<Result> CollectResults(const std::string& prefix) override;

 private:
  void SetUpSingle() override;
  void RunSingle() override;
  void TearDownSingle(bool is_last) override;

  std::vector<std::unique_ptr<Instruction>> instructions_;
  std::vector<std::thread> threads_;
  std::vector<MultithreadingParams> thread_params_;
  pthread_barrier_t barrier_;
};

}  // namespace dittosuite
