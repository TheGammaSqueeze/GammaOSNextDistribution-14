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
// Author: Siddharth Nayyar

#ifndef STG_POST_PROCESSING_H_
#define STG_POST_PROCESSING_H_

#include <cstddef>
#include <string>
#include <vector>

namespace stg {

std::vector<std::string> PostProcess(const std::vector<std::string>& report,
                                     size_t max_crc_only_changes);

}  // namespace stg

#endif  // STG_POST_PROCESSING_H_
