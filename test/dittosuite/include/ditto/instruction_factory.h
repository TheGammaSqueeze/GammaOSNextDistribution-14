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

#include <list>
#include <string>

#include <ditto/instruction.h>
#include <ditto/instruction_set.h>
#include <ditto/read_write_file.h>

#ifdef __ANDROID__
#include <benchmark.pb.h>
#else
#include "schema/benchmark.pb.h"
#endif

namespace dittosuite {

class InstructionFactory {
 public:
  static std::unique_ptr<InstructionSet> CreateFromProtoInstructionSet(
      const dittosuite::Instruction::Params& params, const std::list<int>& thread_ids,
      const dittosuiteproto::InstructionSet& proto_instruction_set);
  static std::unique_ptr<Instruction> CreateFromProtoInstruction(
      const std::list<int>& thread_ids, const dittosuiteproto::Instruction& proto_instruction);
  static int GenerateThreadId();

 private:
  InstructionFactory();

  static Reseeding ConvertReseeding(dittosuiteproto::Reseeding proto_reseeding);
  static Order ConvertOrder(dittosuiteproto::Order proto_order);
  static int ConvertReadFAdvise(Order access_order,
                                dittosuiteproto::ReadFile_ReadFAdvise proto_fadvise);

  static int current_thread_id_;
};

}  // namespace dittosuite
