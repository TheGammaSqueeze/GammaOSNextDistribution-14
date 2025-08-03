// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2022 Google LLC
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

#include <string>

#include "error.h"
#include "graph.h"
#include "proto_reader.h"

extern "C" int LLVMFuzzerTestOneInput(char* data, size_t size) {
  try {
    stg::Graph graph;
    stg::proto::ReadFromString(graph, std::string_view(data, size));
  } catch (const stg::Exception&) {
    // Pass as this is us catching invalid proto properly.
  }
  return 0;
}
