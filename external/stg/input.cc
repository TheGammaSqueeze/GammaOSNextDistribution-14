// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2022-2023 Google LLC
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
// Author: Giuliano Procida

#include "input.h"

#include <memory>
#include <sstream>

#include "abigail_reader.h"
#include "btf_reader.h"
#include "elf_reader.h"
#include "error.h"
#include "filter.h"
#include "graph.h"
#include "proto_reader.h"
#include "reader_options.h"
#include "runtime.h"

namespace stg {

namespace {

Id ReadInternal(Runtime& runtime, Graph& graph, InputFormat format,
                const char* input, ReadOptions options,
                const std::unique_ptr<Filter>& file_filter) {
  switch (format) {
    case InputFormat::ABI: {
      const Time read(runtime, "read ABI");
      return abixml::Read(runtime, graph, input);
    }
    case InputFormat::BTF: {
      const Time read(runtime, "read BTF");
      return btf::ReadFile(graph, input, options);
    }
    case InputFormat::ELF: {
      const Time read(runtime, "read ELF");
      return elf::Read(runtime, graph, input, options, file_filter);
    }
    case InputFormat::STG: {
      const Time read(runtime, "read STG");
      return proto::Read(graph, input);
    }
  }
}

}  // namespace

Id Read(Runtime& runtime, Graph& graph, InputFormat format, const char* input,
        ReadOptions options, const std::unique_ptr<Filter>& file_filter) {
  try {
    return ReadInternal(runtime, graph, format, input, options, file_filter);
  } catch (Exception& e) {
    std::ostringstream os;
    os << "processing file '" << input << '\'';
    e.Add(os.str());
    throw;
  }
}

}  // namespace stg
