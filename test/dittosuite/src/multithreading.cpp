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

#include <ditto/multithreading.h>

namespace dittosuite {

Multithreading::Multithreading(const Instruction::Params& params,
                               std::vector<std::unique_ptr<Instruction>> instructions,
                               std::vector<MultithreadingParams> thread_params)
    : Instruction(kName, params),
      instructions_(std::move(instructions)),
      thread_params_(std::move(thread_params)) {}

void Multithreading::SetUpSingle() {
  for (const auto& instruction : instructions_) {
    instruction->SetUp();
  }
  threads_.clear();

  Instruction::SetUpSingle();
}

void Multithreading::RunSingle() {
  pthread_barrier_init(&barrier_, NULL, instructions_.size());
  for (size_t i = 0; i < instructions_.size(); ++i) {
    threads_.push_back(std::move(instructions_[i]->SpawnThread(&barrier_, thread_params_[i])));
  }
}

void Multithreading::TearDownSingle(bool is_last) {
  for (auto& thread : threads_) {
    thread.join();
  }

  Instruction::TearDownSingle(is_last);

  pthread_barrier_destroy(&barrier_);
  for (const auto& instruction : instructions_) {
    instruction->TearDown();
  }
}

std::unique_ptr<Result> Multithreading::CollectResults(const std::string& prefix) {
  auto result = std::make_unique<Result>(prefix + name_, repeat_);
  result->AddMeasurement("duration", TimespecToDoubleNanos(time_sampler_.GetSamples()));
  for (std::size_t i = 0; i < instructions_.size(); ++i) {
    result->AddSubResult(instructions_[i]->CollectResults(std::to_string(i) + "/"));
  }
  return result;
}

}  // namespace dittosuite
