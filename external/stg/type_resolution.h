// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2023 Google LLC
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

#ifndef STG_TYPE_RESOLUTION_H_
#define STG_TYPE_RESOLUTION_H_

#include <vector>

#include "graph.h"
#include "runtime.h"
#include "unification.h"

namespace stg {

void ResolveTypes(Runtime& runtime, Graph& graph, Unification& unification,
                  const std::vector<Id>& roots);

}  // namespace stg

#endif  // STG_TYPE_RESOLUTION_H_
