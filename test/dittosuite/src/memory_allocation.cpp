// Copyright (C) 2023 The Android Open Source Project
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

#include <ditto/memory_allocation.h>

#include <ditto/logger.h>

#include <unistd.h>

namespace dittosuite {

MemoryAllocation::MemoryAllocation(const Params& params, const uint64_t size)
    : Instruction(kName, params), size_(size), allocated_memory_(nullptr) {}

MemoryAllocation::~MemoryAllocation() {
  free(allocated_memory_);
}

void MemoryAllocation::RunSingle() {
  int page_size = getpagesize();
  allocated_memory_ = static_cast<char*>(malloc(size_));

  for (size_t i = 0; i < size_; i += page_size) {
    allocated_memory_[i] = 1;
  }
}

}  // namespace dittosuite
