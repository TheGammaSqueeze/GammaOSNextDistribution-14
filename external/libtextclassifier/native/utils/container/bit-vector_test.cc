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

#include <memory>

#include "utils/container/bit-vector_generated.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {
namespace {

TEST(BitVectorTest, Dense) {
  std::vector<bool> data = {false, true, true,  true,  false,
                            false, true, false, false, true};

  std::unique_ptr<BitVectorDataT> mutable_bit_vector_data =
      BitVector::CreateDenseBitVectorData(data);
  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(BitVectorData::Pack(builder, mutable_bit_vector_data.get()));
  const flatbuffers::DetachedBuffer buffer = builder.Release();
  const BitVectorData* bit_vector_data =
      flatbuffers::GetRoot<BitVectorData>(buffer.data());

  BitVector bit_vector(bit_vector_data);
  EXPECT_EQ(bit_vector[0], false);
  EXPECT_EQ(bit_vector[1], true);
  EXPECT_EQ(bit_vector[2], true);
  EXPECT_EQ(bit_vector[3], true);
  EXPECT_EQ(bit_vector[4], false);
  EXPECT_EQ(bit_vector[5], false);
  EXPECT_EQ(bit_vector[6], true);
  EXPECT_EQ(bit_vector[7], false);
  EXPECT_EQ(bit_vector[8], false);
  EXPECT_EQ(bit_vector[9], true);
  EXPECT_EQ(bit_vector[10], false);
}

TEST(BitVectorTest, Sparse) {
  std::vector<int32> sorted_indices = {3, 7};

  std::unique_ptr<BitVectorDataT> mutable_bit_vector_data =
      BitVector::CreateSparseBitVectorData(sorted_indices);
  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(BitVectorData::Pack(builder, mutable_bit_vector_data.get()));
  const flatbuffers::DetachedBuffer buffer = builder.Release();
  const BitVectorData* bit_vector_data =
      flatbuffers::GetRoot<BitVectorData>(buffer.data());

  BitVector bit_vector(bit_vector_data);
  EXPECT_EQ(bit_vector[0], false);
  EXPECT_EQ(bit_vector[1], false);
  EXPECT_EQ(bit_vector[2], false);
  EXPECT_EQ(bit_vector[3], true);
  EXPECT_EQ(bit_vector[4], false);
  EXPECT_EQ(bit_vector[5], false);
  EXPECT_EQ(bit_vector[6], false);
  EXPECT_EQ(bit_vector[7], true);
  EXPECT_EQ(bit_vector[8], false);
}

TEST(BitVectorTest, Null) {
  BitVector bit_vector(nullptr);

  EXPECT_EQ(bit_vector[0], false);
  EXPECT_EQ(bit_vector[1], false);
}

}  // namespace
}  // namespace libtextclassifier3
