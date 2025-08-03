/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "utils/grammar/parsing/parse-tree.h"

#include <algorithm>
#include <stack>

namespace libtextclassifier3::grammar {

void Traverse(const ParseTree* root,
              const std::function<bool(const ParseTree*)>& node_fn) {
  std::stack<const ParseTree*> open;
  open.push(root);

  while (!open.empty()) {
    const ParseTree* node = open.top();
    open.pop();
    if (!node_fn(node) || node->IsLeaf()) {
      continue;
    }
    open.push(node->rhs2);
    if (node->rhs1 != nullptr) {
      open.push(node->rhs1);
    }
  }
}

std::vector<const ParseTree*> SelectAll(
    const ParseTree* root,
    const std::function<bool(const ParseTree*)>& pred_fn) {
  std::vector<const ParseTree*> result;
  Traverse(root, [&result, pred_fn](const ParseTree* node) {
    if (pred_fn(node)) {
      result.push_back(node);
    }
    return true;
  });
  return result;
}

}  // namespace libtextclassifier3::grammar
