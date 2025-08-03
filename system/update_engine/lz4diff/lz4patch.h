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

#ifndef UPDATE_ENGINE_LZ4DIFF_LZ4PATCH_H_
#define UPDATE_ENGINE_LZ4DIFF_LZ4PATCH_H_

#include "lz4diff/lz4diff_compress.h"
#include "lz4diff_format.h"

namespace chromeos_update_engine {

bool Lz4Patch(std::string_view src_data,
              std::string_view patch_data,
              const SinkFunc& sink);

bool Lz4Patch(std::string_view src_data,
              std::string_view patch_data,
              Blob* output);
bool Lz4Patch(const Blob& src_data, const Blob& patch_data, Blob* output);

std::ostream& operator<<(std::ostream& out, const CompressionAlgorithm& info);

std::ostream& operator<<(std::ostream& out, const Lz4diffHeader&);

template <typename T>
std::ostream& operator<<(std::ostream& out,
                         const google::protobuf::RepeatedPtrField<T>& arr) {
  if (arr.empty()) {
    out << "[]";
    return out;
  }
  out << "[";
  auto begin = arr.begin();
  out << *begin;
  ++begin;
  for (; begin != arr.end(); ++begin) {
    out << ", " << *begin;
  }
  out << "]";

  return out;
}

}  // namespace chromeos_update_engine

#endif
