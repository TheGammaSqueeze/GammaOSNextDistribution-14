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

#ifndef LIBTEXTCLASSIFIER_UTILS_CONTAINER_BIT_VECTOR_H_
#define LIBTEXTCLASSIFIER_UTILS_CONTAINER_BIT_VECTOR_H_

#include <set>
#include <vector>

#include "utils/base/integral_types.h"
#include "utils/container/bit-vector_generated.h"

namespace libtextclassifier3 {

// A read-only bit vector. It does not own the data and it is like a view on
// the given data. There are two internal representations, sparse and dense.
// The dense one stores every bits. The sparse stores only the indices of
// elements that are 1.
class BitVector {
 public:
  explicit BitVector(const BitVectorData* bit_vector_data);

  // Gets a particular bit. If the underlying data does not contain the
  // value of the asked bit, false is returned.
  const bool operator[](int index) const { return Get(index); }

  // Creates a BitVectorDataT using the dense representation.
  static std::unique_ptr<BitVectorDataT> CreateDenseBitVectorData(
      const std::vector<bool>& data);

  // Creates a BitVectorDataT using the sparse representation.
  static std::unique_ptr<BitVectorDataT> CreateSparseBitVectorData(
      const std::vector<int32>& indices);

 private:
  const BitVectorData* bit_vector_data_;

  bool Get(int index) const;
  bool GetFromSparseData(int index) const;
  bool GetFromDenseData(int index) const;
};

}  // namespace libtextclassifier3
#endif  // LIBTEXTCLASSIFIER_UTILS_CONTAINER_BIT_VECTOR_H_
