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

#include <string>
#include <ditto/instruction_set.h>

#ifdef __ANDROID__
#include <benchmark.pb.h>
#else
#include "schema/benchmark.pb.h"
#endif

namespace dittosuite {

class Parser {
 public:
  Parser(Parser& other) = delete;
  void operator=(const Parser&) = delete;

  static Parser& GetParser();
  std::unique_ptr<dittosuiteproto::Benchmark> Parse(const std::string& file_path, const std::vector<std::string>& parameters);
  std::unique_ptr<Instruction> GetInit();
  std::unique_ptr<Instruction> GetMain();
  std::unique_ptr<Instruction> GetCleanUp();

 private:
  Parser(){};

  std::unique_ptr<Instruction> init_;
  std::unique_ptr<Instruction> main_;
  std::unique_ptr<Instruction> clean_up_;
};

} // namespace dittosuite
