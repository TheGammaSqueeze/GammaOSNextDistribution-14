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

#include "icing/performance-configuration.h"

#include "icing/scoring/scored-document-hit.h"

namespace icing {
namespace lib {

namespace {
// Search-related thresholds:
// Search performance mainly involves the following components:
// 1. QueryProcessor
//    Running time is O(query_length) according to results of
//    //icing/query:query-processor_benchmark.
// 2. ScoringProcessor and Ranker
//    Running time is O(num_to_score) according to results of
//    //icing/scoring:score-and-rank_benchmark. Note that the
//    process includes scoring, building a heap, and popping results from the
//    heap.
// 3. ResultRetriever
//    Running time is O(page_size). Assuming page_size is always a small number,
//    it's actually O(1).
//
// Overall Search performance goal: 33 ms. On a 60FPS screen it's the time of
// rendering 2 frames.
//
// With the information above, we then try to choose default values for
// query_length so that the overall time can comfortably fit in with our goal
// (note that num_to_score will be decided by the client, which is specified in
// ResultSpecProto).
// 1. Set query_length to 23000 so that any query can be executed by
//    QueryProcessor within 15 ms on a Pixel 3 XL according to results of
//    //icing/query:query-processor_benchmark.
//
// In the worse-case scenario, we still have [33 ms - 15 ms - 3 ms] = 15 ms left
// for all the other things like proto parsing, document fetching, and even
// Android Binder calls if Icing search engine runs in a separate process.
constexpr int kMaxQueryLength = 23000;

// New Android devices nowadays all allow more than 16 MB memory per app. Using
// that as a guideline and being more conservative, we set 4 MB as the safe
// memory threshold.
// TODO(b/150029642): Android apps / framework have better understanding of how
// much memory is allowed, so it would be better to let clients pass in this
// value.
constexpr int kSafeMemoryUsage = 4 * 1024 * 1024;  // 4MB

// The maximum number of hits that can fit below the kSafeMemoryUsage threshold.
constexpr int kMaxNumTotalHits = kSafeMemoryUsage / sizeof(ScoredDocumentHit);

}  // namespace

PerformanceConfiguration::PerformanceConfiguration()
    : PerformanceConfiguration(kMaxQueryLength, kMaxNumTotalHits) {}

}  // namespace lib
}  // namespace icing
