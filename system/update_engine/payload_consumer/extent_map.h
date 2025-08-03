//
// Copyright (C) 2021 The Android Open Source Project
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
//

#ifndef UPDATE_ENGINE_PAYLOAD_CONSUMER_EXTENT_MAP_H_
#define UPDATE_ENGINE_PAYLOAD_CONSUMER_EXTENT_MAP_H_

#include <map>
#include <utility>
#include <vector>

#include "update_engine/common/utils.h"
#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/payload_generator/extent_utils.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {

// Data structure for storing a disjoint set of extents.
// Currently the only usecase is for VABCPartitionWriter to keep track of which
// block belongs to which merge operation. Therefore this class only contains
// the minimal set of functions needed.
template <typename T, typename Comparator = ExtentLess>
class ExtentMap {
 public:
  bool AddExtent(const Extent& extent, T&& value) {
    if (set_.OverlapsWithExtent(extent)) {
      return false;
    }
    const auto& [it, inserted] = map_.insert({extent, std::forward<T>(value)});
    if (inserted) {
      set_.AddExtent(extent);
    }
    return inserted;
  }

  size_t size() const { return map_.size(); }

  // Return a pointer to entry which is intersecting |extent|. If T is already
  // a pointer type, return T on success. This function always return
  // |nullptr| on failure. Therefore you cannot store nullptr as an entry.
  std::optional<T> Get(const Extent& extent) const {
    const auto it = map_.find(extent);
    if (it == map_.end()) {
      for (const auto& ext : set_.GetCandidateRange(extent)) {
        // Sometimes there are operations like
        // map.AddExtent({0, 5}, 42);
        // map.Get({2, 1})
        // If the querying extent is completely covered within the key, we still
        // consdier this to be a valid query.

        if (ExtentContains(ext, extent)) {
          return map_.at(ext);
        }
        if (ExtentRanges::ExtentsOverlap(ext, extent)) {
          LOG(WARNING) << "Looking up a partially intersecting extent isn't "
                          "supported by "
                          "this data structure. Querying extent: "
                       << extent << ", partial match in map: " << ext;
        }
      }
      return {};
    }
    return {it->second};
  }

  // Return a set of extents that are contained in this extent map.
  // If |extent| is completely covered by this extent map, a vector of itself
  // will be returned.
  // If only a subset of |extent| is covered by this extent map, a vector of
  // parts in this map will be returned.
  // If |extent| has no intersection with this map, an empty vector will be
  // returned.
  // E.g. extent map contains [0,5] and [10,15], GetIntersectingExtents([3, 12])
  // would return [3,5] and [10,12]
  std::vector<Extent> GetIntersectingExtents(const Extent& extent) const {
    return set_.GetIntersectingExtents(extent);
  }

  // Complement of |GetIntersectingExtents|, return vector of extents which are
  // part of |extent| but not covered by this map.
  std::vector<Extent> GetNonIntersectingExtents(const Extent& extent) const {
    return FilterExtentRanges({extent}, set_);
  }

 private:
  // Get a range of exents that potentially intersect with parameter |extent|
  std::map<Extent, T, Comparator> map_;
  ExtentRanges set_{false};
};
}  // namespace chromeos_update_engine

#endif  // UPDATE_ENGINE_PAYLOAD_CONSUMER_EXTENT_MAP_H_
