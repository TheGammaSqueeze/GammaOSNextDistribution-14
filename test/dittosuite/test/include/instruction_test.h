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

#include "mock_syscall.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <ditto/instruction.h>
#include <ditto/shared_variables.h>

#ifdef __ANDROID__
#include <android-base/file.h>
const std::string absolute_path = android::base::GetExecutableDirectory();
#else
const std::string absolute_path = "";
#endif

class InstructionTest : public ::testing::Test {
 protected:
  MockSyscall syscall_;
  std::list<int> thread_ids;

  // Set absolute_path
  virtual void SetUp() override {
    dittosuite::SharedVariables::ClearKeys();
    thread_ids.push_back(0);
    auto absolute_path_key = dittosuite::SharedVariables::GetKey(thread_ids, "absolute_path");
    dittosuite::SharedVariables::Set(absolute_path_key, absolute_path);
    dittosuite::Instruction::SetAbsolutePathKey(absolute_path_key);
  }
};

template <class T>
class InstructionTestWithParam : public InstructionTest, public ::testing::WithParamInterface<T> {};
