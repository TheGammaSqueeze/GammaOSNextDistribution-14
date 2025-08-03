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

#include <ditto/delete_file.h>

#include <ditto/logger.h>
#include <ditto/shared_variables.h>

namespace dittosuite {

DeleteFile::DeleteFile(const Params& params, const std::string& path_name)
    : Instruction(kName, params), path_name_(GetAbsolutePath() + path_name), input_key_(-1) {}

DeleteFile::DeleteFile(const Params& params, int input_key)
    : Instruction(kName, params), input_key_(input_key) {}

void DeleteFile::SetUpSingle() {
  if (input_key_ != -1) {
    path_name_ = std::get<std::string>(SharedVariables::Get(input_key_));
  }
  Instruction::SetUpSingle();
}

void DeleteFile::RunSingle() {
  if (syscall_.Unlink(path_name_) == -1) {
    LOGF("Error while calling unlink()");
  }
}

}  // namespace dittosuite
