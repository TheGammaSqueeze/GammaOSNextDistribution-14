// Copyright (C) 2019 Google LLC
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

#include "icing/index/term-property-id.h"

namespace icing {
namespace lib {
namespace {

enum TermPropertyId {
  // Property id for whether the term has hits in prefix sections
  kHasHitsInPrefixSection = 0,

  // Property id for whether the term has no exact hits. No exact hits means
  // that the term is for prefix matching only. This kind of term is used only
  // in main index.
  kHasNoExactHits = 1,

  // The smallest property id for namespace. One namespace id will be mapped
  // to one property id, so that all the property ids that are greater than
  // this will all be property ids for namespaces.
  kNamespace = 2,
};

}  // namespace

uint32_t GetHasHitsInPrefixSectionPropertyId() {
  return kHasHitsInPrefixSection;
}

uint32_t GetHasNoExactHitsPropertyId() { return kHasNoExactHits; }

uint32_t GetNamespacePropertyId(NamespaceId namespace_id) {
  return kNamespace + namespace_id;
}

}  // namespace lib
}  // namespace icing
