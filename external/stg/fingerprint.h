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
// Author: Giuliano Procida

#ifndef STG_FINGERPRINT_H_
#define STG_FINGERPRINT_H_

#include <cstdint>
#include <unordered_map>

#include "graph.h"
#include "hashing.h"
#include "runtime.h"

namespace stg {

// Fingerprint is a node hasher that hashes all nodes reachable from a given
// root node. It will almost always succeed in distinguishing unequal nodes.
//
// Given any mutual dependencies between hashes, it falls back to a very poor
// but safe hash for the affected nodes: the size of the SCC.
std::unordered_map<Id, HashValue> Fingerprint(
    Runtime& runtime, const Graph& graph, Id root);

}  // namespace stg

#endif  // STG_FINGERPRINT_H_
