// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2021-2022 Google LLC
//
// Licensed under the Apache License v2.0 with LLVM Exceptions (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     https://llvm.org/LICENSE.txt
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Author: Matthias Maennich
// Author: Aleksei Vetrov

#include <sstream>
#include <vector>

#include "elf_reader.h"
#include "error.h"
#include "graph.h"
#include "reader_options.h"
#include "runtime.h"

extern "C" int LLVMFuzzerTestOneInput(char* data, size_t size) {
  try {
    // Fuzzer forbids changing "data", but libdwfl, used in elf::Read, requires
    // read and write access to memory.
    // Luckily, such trivial copy can be easily tracked by fuzzer.
    std::ostringstream os;
    stg::Runtime runtime(os, false);
    stg::Graph graph;
    std::vector<char> data_copy(data, data + size);
    stg::elf::Read(runtime, graph, data_copy.data(), size, stg::ReadOptions(),
                   nullptr);
  } catch (const stg::Exception&) {
    // Pass as this is us catching invalid ELF properly.
  }
  return 0;
}
