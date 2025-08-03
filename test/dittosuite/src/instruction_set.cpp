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

#include <ditto/instruction_set.h>

#include <variant>

#include <ditto/logger.h>
#include <ditto/shared_variables.h>

namespace dittosuite {

template <class... Ts>
struct overloaded : Ts... {
  using Ts::operator()...;
};
template <class... Ts>
overloaded(Ts...) -> overloaded<Ts...>;

InstructionSet::InstructionSet(const Params& params,
                               std::vector<std::unique_ptr<Instruction>> instructions, int list_key,
                               int item_key, Order access_order, Reseeding reseeding, uint32_t seed)
    : Instruction(kName, params),
      instructions_(std::move(instructions)),
      list_key_(list_key),
      item_key_(item_key),
      access_order_(access_order),
      reseeding_(reseeding),
      seed_(seed),
      gen_(seed) {}

InstructionSet::InstructionSet(const Params& params,
                               std::vector<std::unique_ptr<Instruction>> instructions)
    : Instruction(kName, params),
      instructions_(std::move(instructions)),
      list_key_(-1),
      item_key_(-1) {}

void InstructionSet::SetUp() {
  if (reseeding_ == Reseeding::kEachRoundOfCycles) {
    gen_.seed(seed_);
  }
  Instruction::SetUp();
}

void InstructionSet::SetUpSingle() {
  if (reseeding_ == Reseeding::kEachCycle) {
    gen_.seed(seed_);
  }
  Instruction::SetUpSingle();
}

void InstructionSet::RunSingle() {
  if (list_key_ != -1 && item_key_ != -1) {
    std::visit(overloaded{[&](const std::vector<std::string>& list) {
                            std::uniform_int_distribution<> uniform_distribution(0,
                                                                                 list.size() - 1);
                            for (std::size_t i = 0; i < list.size(); ++i) {
                              switch (access_order_) {
                                case Order::kSequential: {
                                  SharedVariables::Set(item_key_, list[i]);
                                  break;
                                }
                                case Order::kRandom: {
                                  SharedVariables::Set(item_key_, list[uniform_distribution(gen_)]);
                                  break;
                                }
                              }
                              RunInstructions();
                            }
                          },
                          [](int) {
                            LOGE("Input for InstructionSet is not iterable.");
                            exit(EXIT_FAILURE);
                          },
                          [](const std::string&) {
                            LOGE("Input for InstructionSet is not iterable.");
                            exit(EXIT_FAILURE);
                          }},
               SharedVariables::Get(list_key_));
  } else {
    RunInstructions();
  }
}

void InstructionSet::RunInstructions() {
  for (const auto& instruction : instructions_) {
    instruction->SetUp();
    instruction->Run();
    instruction->TearDown();
  }
}

// The duration for an instruction set is a sum of its child instruction durations.
std::unique_ptr<Result> InstructionSet::CollectResults(const std::string& prefix) {
  auto result = std::make_unique<Result>(prefix + name_, repeat_);
  std::vector<double> duration;
  for (const auto& instruction : instructions_) {
    auto sub_result = instruction->CollectResults("");
    auto samples = sub_result->GetSamples("duration");
    auto repeat = sub_result->GetRepeat();

    if (duration.empty()) {
      duration.resize(samples.size() / repeat);
    }

    for (std::size_t i = 0; i < samples.size() / repeat; ++i) {
      for (int j = 0; j < repeat; ++j) {
        duration[i] += samples[i * repeat + j];
      }
    }

    result->AddSubResult(std::move(sub_result));
  }
  result->AddMeasurement("duration", duration);
  return result;
}

}  // namespace dittosuite
