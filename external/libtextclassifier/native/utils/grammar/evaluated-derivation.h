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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_EVALUATED_DERIVATION_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_EVALUATED_DERIVATION_H_

#include "utils/grammar/parsing/derivation.h"
#include "utils/grammar/semantics/value.h"

namespace libtextclassifier3::grammar {

// A parse tree for a root rule and its semantic value.
struct EvaluatedDerivation : public Derivation {
  const SemanticValue* value;
};

};  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_EVALUATED_DERIVATION_H_
