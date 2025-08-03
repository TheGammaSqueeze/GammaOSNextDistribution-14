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

#include "icing/tokenization/reverse_jni/reverse-jni-break-iterator.h"

#include <jni.h>

#include <cassert>
#include <cctype>
#include <cmath>
#include <map>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/text_classifier/lib3/utils/java/jni-base.h"
#include "icing/text_classifier/lib3/utils/java/jni-helper.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/jni/jni-cache.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {
// Chosen based on results in go/reverse-jni-benchmarks
static constexpr int kBatchSize = 100;
}  // namespace

// -----------------------------------------------------------------------------
// Implementations that call out to JVM. Behold the beauty.
// -----------------------------------------------------------------------------
libtextclassifier3::StatusOr<std::unique_ptr<ReverseJniBreakIterator>>
ReverseJniBreakIterator::Create(const JniCache* jni_cache,
                                std::string_view text,
                                std::string_view locale) {
  if (jni_cache == nullptr) {
    return absl_ports::InvalidArgumentError(
        "Create must be called with a valid JniCache pointer!");
  }

  ICING_ASSIGN_OR_RETURN(
      libtextclassifier3::ScopedLocalRef<jstring> java_text,
      jni_cache->ConvertToJavaString(text.data(), text.length()));
  if (java_text.get() == nullptr) {
    return absl_ports::AbortedError("Failed to create Java String from input.");
  }

  ICING_ASSIGN_OR_RETURN(
      libtextclassifier3::ScopedLocalRef<jstring> java_locale_string,
      jni_cache->ConvertToJavaString(locale.data(), locale.length()));
  if (java_locale_string.get() == nullptr) {
    return absl_ports::AbortedError(
        "Failed to create Java String from locale.");
  }

  JNIEnv* jenv = jni_cache->GetEnv();
  ICING_ASSIGN_OR_RETURN(
      libtextclassifier3::ScopedLocalRef<jobject> java_locale,
      libtextclassifier3::JniHelper::NewObject(
          jenv, jni_cache->locale_class.get(), jni_cache->locale_constructor,
          java_locale_string.get()));
  if (java_locale.get() == nullptr) {
    return absl_ports::AbortedError(
        "Failed to create Java Locale from locale.");
  }

  ICING_ASSIGN_OR_RETURN(
      libtextclassifier3::ScopedLocalRef<jobject> local_iterator_batcher,
      libtextclassifier3::JniHelper::NewObject(
          jenv, jni_cache->breakiterator_class.get(),
          jni_cache->breakiterator_constructor, java_locale.get()));
  libtextclassifier3::ScopedGlobalRef<jobject> iterator_batcher =
      libtextclassifier3::MakeGlobalRef(local_iterator_batcher.get(), jenv,
                                        jni_cache->jvm);
  if (iterator_batcher.get() == nullptr) {
    return absl_ports::AbortedError(
        "Failed to create Java BreakIteratorBatcher.");
  }

  ICING_RETURN_IF_ERROR(libtextclassifier3::JniHelper::CallVoidMethod(
      jenv, iterator_batcher.get(), jni_cache->breakiterator_settext,
      java_text.get()));
  return std::unique_ptr<ReverseJniBreakIterator>(
      new ReverseJniBreakIterator(jni_cache, std::move(iterator_batcher)));
}

ReverseJniBreakIterator::ReverseJniBreakIterator(
    const JniCache* jni_cache,
    libtextclassifier3::ScopedGlobalRef<jobject> iterator_batcher)
    : jni_cache_(jni_cache),
      iterator_batcher_(std::move(iterator_batcher)),
      is_done_(false),
      is_almost_done_(false) {}

int ReverseJniBreakIterator::Next() {
  if (is_done_) {
    return ReverseJniBreakIterator::kDone;
  }
  if (break_indices_cache_.empty()) {
    if (FetchNextBatch() == ReverseJniBreakIterator::kDone) {
      // Either there were no more results or an error occurred. Either way,
      // mark ourselves as done and return.
      is_done_ = true;
      return ReverseJniBreakIterator::kDone;
    }
    is_almost_done_ = break_indices_cache_.size() < kBatchSize;
  }
  int break_index = break_indices_cache_.front();
  break_indices_cache_.pop();
  is_done_ = is_almost_done_ && break_indices_cache_.empty();
  return break_index;
}

int ReverseJniBreakIterator::First() {
  const int first_index = jni_cache_->GetEnv()->CallIntMethod(
      iterator_batcher_.get(), jni_cache_->breakiterator_first);
  if (jni_cache_->ExceptionCheckAndClear()) {
    return ReverseJniBreakIterator::kDone;
  }
  ClearCache();
  return first_index;
}

int ReverseJniBreakIterator::Preceding(int offset) {
  const int preceding_index = jni_cache_->GetEnv()->CallIntMethod(
      iterator_batcher_.get(), jni_cache_->breakiterator_preceding, offset);
  if (jni_cache_->ExceptionCheckAndClear()) {
    return ReverseJniBreakIterator::kDone;
  }
  ClearCache();
  return preceding_index;
}

int ReverseJniBreakIterator::Following(int offset) {
  const int following_index = jni_cache_->GetEnv()->CallIntMethod(
      iterator_batcher_.get(), jni_cache_->breakiterator_following, offset);
  if (jni_cache_->ExceptionCheckAndClear()) {
    return ReverseJniBreakIterator::kDone;
  }
  ClearCache();
  return following_index;
}

int ReverseJniBreakIterator::FetchNextBatch() {
  ICING_ASSIGN_OR_RETURN(
      libtextclassifier3::ScopedLocalRef<jintArray> break_indices,
      libtextclassifier3::JniHelper::CallObjectMethod<jintArray>(
          jni_cache_->GetEnv(), iterator_batcher_.get(),
          jni_cache_->breakiterator_next, kBatchSize),
      ReverseJniBreakIterator::kDone);
  if (break_indices == nullptr || jni_cache_->ExceptionCheckAndClear()) {
    return ReverseJniBreakIterator::kDone;
  }
  jint num_indices = jni_cache_->GetEnv()->GetArrayLength(break_indices.get());
  if (num_indices == 0) {
    return ReverseJniBreakIterator::kDone;
  }
  jint* break_indices_arr =
      static_cast<jint*>(jni_cache_->GetEnv()->GetPrimitiveArrayCritical(
          break_indices.get(), nullptr));
  for (int i = 0; i < num_indices; ++i) {
    break_indices_cache_.push(break_indices_arr[i]);
  }
  jni_cache_->GetEnv()->ReleasePrimitiveArrayCritical(break_indices.get(),
                                                      break_indices_arr,
                                                      /*mode=*/0);
  return num_indices;
}

void ReverseJniBreakIterator::ClearCache() {
  break_indices_cache_ = std::queue<int>();
  is_done_ = false;
  is_almost_done_ = false;
}

}  // namespace lib
}  // namespace icing
