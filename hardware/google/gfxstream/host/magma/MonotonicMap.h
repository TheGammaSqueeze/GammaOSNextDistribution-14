// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License") override;
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include <cstdint>
#include <limits>
#include <optional>
#include <type_traits>
#include <unordered_map>

namespace gfxstream {
namespace magma {

// A container with automatic monotonically increasing key values.
template <typename K, typename V, std::enable_if_t<std::is_integral_v<K>, bool> = true>
class MonotonicMap {
   public:
    MonotonicMap() {
        static_assert(std::numeric_limits<K>::max() >= std::numeric_limits<int64_t>::max(),
                      "Key type should be sufficiently large so as to not overflow.");
    }

    // Creates a new object using the provided parameters with the constructor, and returns the new
    // key associated with it.
    template <typename... Params>
    K create(Params&&... params) {
        assert(mNextKey < std::numeric_limits<K>::max());
        auto key = mNextKey++;
        auto [it, emplaced] = mMap.emplace(key, V(std::forward<Params>(params)...));
        return key;
    }

    // Returns a pointer to the object associated with the given key, or nullptr if the key is
    // invalid. The pointer remains valid until the container is destroyed or the key is removed.
    V* get(const K& key) {
        auto it = mMap.find(key);
        if (it == mMap.end()) {
            return nullptr;
        }
        return &it->second;
    }

    // Destroys the object with the associated key and removes it from the map. Returns true iff the
    // key was valid.
    bool erase(const K& key) { return mMap.erase(key) > 0; }

   private:
    K mNextKey = 1;
    std::unordered_map<K, V> mMap;
};

}  // namespace magma
}  // namespace gfxstream
