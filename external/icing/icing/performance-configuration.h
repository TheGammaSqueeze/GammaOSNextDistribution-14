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

#ifndef ICING_PERFORMANCE_CONFIGURATION_H_
#define ICING_PERFORMANCE_CONFIGURATION_H_

namespace icing {
namespace lib {

// Stores key thresholds that affect performance of Icing search engine.
struct PerformanceConfiguration {
  // Loads default configuration.
  PerformanceConfiguration();

  PerformanceConfiguration(int max_query_length_in, int max_num_total_hits)
      : max_query_length(max_query_length_in),
        max_num_total_hits(max_num_total_hits) {}

  // Search performance

  // Maximum length of query to execute in IndexProcessor.
  int max_query_length;

  // Memory

  // Maximum number of ScoredDocumentHits to cache in the ResultStateManager at
  // one time.
  int max_num_total_hits;
};

// TODO(b/149040810): Consider creating a class to manage performance
// configurations according to different devices/platforms/clients and even
// real-time memory usage.

}  // namespace lib
}  // namespace icing

#endif  // ICING_PERFORMANCE_CONFIGURATION_H_
