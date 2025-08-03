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

#include "utils/sentencepiece/test_utils.h"

#include <memory>

#include "utils/base/integral_types.h"
#include "utils/container/double-array-trie.h"
#include "utils/strings/stringpiece.h"

namespace libtextclassifier3 {

SentencePieceNormalizer NormalizerFromSpec(StringPiece spec,
                                           bool add_dummy_prefix,
                                           bool remove_extra_whitespaces,
                                           bool escape_whitespaces) {
  const uint32 trie_blob_size = reinterpret_cast<const uint32*>(spec.data())[0];
  spec.RemovePrefix(sizeof(trie_blob_size));
  const TrieNode* trie_blob = reinterpret_cast<const TrieNode*>(spec.data());
  spec.RemovePrefix(trie_blob_size);
  const int num_nodes = trie_blob_size / sizeof(TrieNode);
  return SentencePieceNormalizer(
      DoubleArrayTrie(trie_blob, num_nodes),
      /*charsmap_normalized=*/StringPiece(spec.data(), spec.size()),
      add_dummy_prefix, remove_extra_whitespaces, escape_whitespaces);
}

}  // namespace libtextclassifier3
