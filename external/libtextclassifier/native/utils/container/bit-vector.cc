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

#include "utils/container/bit-vector.h"

#include <math.h>

#include <algorithm>
#include <memory>
#include <vector>

#include "utils/base/logging.h"
#include "utils/container/bit-vector_generated.h"

namespace libtextclassifier3 {

BitVector::BitVector(const BitVectorData* bit_vector_data)
    : bit_vector_data_(bit_vector_data) {}

bool BitVector::GetFromSparseData(int32 index) const {
  return std::binary_search(
      bit_vector_data_->sparse_data()->sorted_indices_32()->begin(),
      bit_vector_data_->sparse_data()->sorted_indices_32()->end(), index);
}

bool BitVector::GetFromDenseData(int32 index) const {
  if (index >= bit_vector_data_->dense_data()->size()) {
    return false;
  }
  int32 byte_index = index / 8;
  uint8 extracted_byte =
      bit_vector_data_->dense_data()->data()->Get(byte_index);
  uint8 bit_index = index % 8;
  return extracted_byte & (1 << bit_index);
}

bool BitVector::Get(int32 index) const {
  TC3_DCHECK(index >= 0);

  if (bit_vector_data_ == nullptr) {
    return false;
  }
  if (bit_vector_data_->dense_data() != nullptr) {
    return GetFromDenseData(index);
  }
  return GetFromSparseData(index);
}

std::unique_ptr<BitVectorDataT> BitVector::CreateSparseBitVectorData(
    const std::vector<int32>& indices) {
  auto bit_vector_data = std::make_unique<BitVectorDataT>();
  bit_vector_data->sparse_data =
      std::make_unique<libtextclassifier3::SparseBitVectorDataT>();
  bit_vector_data->sparse_data->sorted_indices_32 = indices;
  return bit_vector_data;
}

std::unique_ptr<BitVectorDataT> BitVector::CreateDenseBitVectorData(
    const std::vector<bool>& data) {
  uint8_t temp = 0;
  std::vector<uint8_t> result;
  for (int i = 0; i < data.size(); i++) {
    if (i != 0 && (i % 8) == 0) {
      result.push_back(temp);
      temp = 0;
    }
    if (data[i]) {
      temp += (1 << (i % 8));
    }
  }
  if ((data.size() % 8) != 0) {
    result.push_back(temp);
  }

  auto bit_vector_data = std::make_unique<BitVectorDataT>();
  bit_vector_data->dense_data =
      std::make_unique<libtextclassifier3::DenseBitVectorDataT>();
  bit_vector_data->dense_data->data = result;
  bit_vector_data->dense_data->size = data.size();
  return bit_vector_data;
}

}  // namespace libtextclassifier3
