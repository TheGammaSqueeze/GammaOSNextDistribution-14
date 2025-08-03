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

#ifndef ICING_TOKENIZATION_REVERSE_JNI_REVERSE_JNI_BREAK_ITERATOR_H_
#define ICING_TOKENIZATION_REVERSE_JNI_REVERSE_JNI_BREAK_ITERATOR_H_

#include <jni.h>

#include <queue>
#include <string>

#include "icing/text_classifier/lib3/utils/java/jni-base.h"
#include "icing/jni/jni-cache.h"

namespace icing {
namespace lib {

// A class that handles the cross-JNI interactions with BreakIteratorBatcher and
// hides the batching element to provide an interface akin to
// java.text.BreakIterator.
//
// Example:
// std::string text = "我每天走路去上班。";
// ASSERT_THAT(text, SizeIs(27));
// std::unique_ptr<ReverseJniBreakIterator> itr =
//     ReverseJniBreakIterator::Create(jni_cache, text, locale);
// std::vector<int> nexts;
// int next = itr->Next();
// while (next != ReverseJniBreakIterator::kDone) {
//   nexts.push_back(next);
//   next = itr->Next();
// }
// EXPECT_THAT(nexts, ElementsAre(1, 3, 5, 6, 8));
class ReverseJniBreakIterator {
 public:
  static constexpr int kDone = -1;

  // Creates a ReverseJniBreakiterator with the given text and locale.
  //
  // Returns:
  //   A ReverseJniBreakIterator on success
  //   INVALID_ARGUMENT if jni_cache isn't a valid JniCache pointer
  //   INTERNAL if unable to create any of the required Java objects
  static libtextclassifier3::StatusOr<std::unique_ptr<ReverseJniBreakIterator>>
  Create(const JniCache* jni_cache, std::string_view text,
         std::string_view locale);

  // Returns the UTF-16 boundary following the current boundary. If the current
  // boundary is the last text boundary, it returns
  // ReverseJniBreakIterator::kDONE.
  //
  // NOTE: The 'boundary' refers to the UTF-16 boundary - NOT the UTF-8
  // boundary. Callers interested in the UTF-8 boundary are required to maintain
  // whatever state is necessary to translate from UTF-16 to UTF-8 boundaries.
  int Next();

  // Returns the first UTF-16 boundary. The iterator's current position is set
  // to the first text boundary and any cached data is cleared.
  int First();

  // Returns the position of the first UTF-16 boundary preceding the UTF-16
  // offset. If there is no boundary preceding the specified offset, then
  // ReverseJniBreakIterator::kDone is returned.
  //
  // The iterator's current position is set to the segment whose boundary was
  // returned and any cached data is cleared.
  int Preceding(int offset);

  // Returns the position of the first UTF-16 boundary following the UTF-16
  // offset. If there is no boundary following the specified offset, then
  // ReverseJniBreakIterator::kDone is returned.
  //
  // The iterator's current position is set to the segment whose boundary
  // was returned and any cached data is cleared.
  int Following(int offset);

 private:
  ReverseJniBreakIterator(
      const JniCache* jni_cache,
      libtextclassifier3::ScopedGlobalRef<jobject> iterator_batcher);

  // Fetches the results of up to kBatchSize next calls and stores them in
  // break_indices_cache_. Returns the number of results or kDone if no more
  // results could be fetched.
  int FetchNextBatch();

  // Empties the cache and sets is_done_ and is_almost_done_ to false.
  void ClearCache();

  // Keeps track of references to Java classes and methods. Does NOT own.
  const JniCache* jni_cache_;

  // The reference to the actual instance of BreakIteratorBatcher that
  // this class interacts with.
  libtextclassifier3::ScopedGlobalRef<jobject> iterator_batcher_;

  // The cache holding the most recent batch of return values from
  // BreakIteratorBatcher#next.
  std::queue<int> break_indices_cache_;

  bool is_done_;

  // The last batch was incomplete (< kBatchSize results were returned). The
  // next call to BreakIteratorBatcher#next is guaranteed to return an
  // empty array. Once the results from the last batch are evicted from
  // break_indices_cache, ReverseJniBreakIterator will transition to is_done_.
  bool is_almost_done_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TOKENIZATION_REVERSE_JNI_REVERSE_JNI_BREAK_ITERATOR_H_
