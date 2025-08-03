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

#ifndef STG_INPUT_H_
#define STG_INPUT_H_

#include <memory>

#include "filter.h"
#include "graph.h"
#include "reader_options.h"
#include "runtime.h"

namespace stg {

enum class InputFormat { ABI, BTF, ELF, STG };

Id Read(Runtime& runtime, Graph& graph, InputFormat format, const char* input,
        ReadOptions options, const std::unique_ptr<Filter>& file_filter);

}  // namespace stg

#endif  // STG_INPUT_H_
