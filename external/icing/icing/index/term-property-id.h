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

#ifndef ICING_INDEX_TERM_PROPERTY_ID_H_
#define ICING_INDEX_TERM_PROPERTY_ID_H_

#include <cstdint>

#include "icing/legacy/index/icing-dynamic-trie.h"
#include "icing/store/namespace-id.h"

namespace icing {
namespace lib {

// A property in IcingDynamicTrie represents some attribute of the terms, e.g.
// whether the terms have prefix hit sections. A property id is something
// defined by the index to keep track of what properties the terms have. This
// class provides functions to get all the property ids we use in the index
// lexicon.

// Returns the id of term property 'HasPrefixHits'.
uint32_t GetHasHitsInPrefixSectionPropertyId();

// Returns the id of term property 'HasNoExactHits'.
uint32_t GetHasNoExactHitsPropertyId();

// Returns the id of term property 'HasNamespaceX'. We have one property id for
// each namespace id.
// TODO(b/153091745): This approach requires one property bitmap per namespace
// id, which doesn't scale well. Try to optimize the space usage.
uint32_t GetNamespacePropertyId(NamespaceId namespace_id);

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_TERM_PROPERTY_ID_H_
