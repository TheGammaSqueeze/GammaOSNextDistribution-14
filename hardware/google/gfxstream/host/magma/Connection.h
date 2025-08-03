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

#include <memory>
#include <optional>
#include <unordered_map>

#include "DrmContext.h"

namespace gfxstream {
namespace magma {

class DrmDevice;

// A Connection represents an unique magma object ID namespace.
// Magma objects from different connections may share the same ID.
class Connection {
   public:
    Connection(DrmDevice& device);
    ~Connection() = default;
    DISALLOW_COPY_AND_ASSIGN(Connection);
    Connection(Connection&&) noexcept = default;
    Connection& operator=(Connection&&) = delete;

    // Get the parent device for this connection.
    DrmDevice& getDevice();

    // Creates a new context and returns its ID. Returns nullopt on error.
    std::optional<uint32_t> createContext();

    // Returns the context for the given ID, or nullptr if invalid.
    DrmContext* getContext(uint32_t id);

   private:
    friend class DrmContext;

    DrmDevice& mDevice;

    // Maps context IDs to contexts.
    std::unordered_map<uint32_t, DrmContext> mContexts;
};

}  // namespace magma
}  // namespace gfxstream
