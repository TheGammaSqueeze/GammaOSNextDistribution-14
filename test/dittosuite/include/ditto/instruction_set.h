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
#include <memory>
#include <random>
#include <vector>

#include <ditto/instruction.h>

namespace dittosuite {

class InstructionSet : public Instruction {
 public:
  inline static const std::string kName = "instruction_set";

  explicit InstructionSet(const Params& params,
                          std::vector<std::unique_ptr<Instruction>> instructions, int list_key,
                          int item_key, Order order, Reseeding reseeding, uint32_t seed);
  explicit InstructionSet(const Params& params,
                          std::vector<std::unique_ptr<Instruction>> instructions);

  std::unique_ptr<Result> CollectResults(const std::string& prefix) override;

 private:
  void SetUp() override;
  void SetUpSingle() override;
  void RunSingle() override;
  void RunInstructions();

  std::vector<std::unique_ptr<Instruction>> instructions_;
  int list_key_;
  int item_key_;
  Order access_order_;
  Reseeding reseeding_;
  uint32_t seed_;
  std::mt19937_64 gen_;
};

}  // namespace dittosuite
