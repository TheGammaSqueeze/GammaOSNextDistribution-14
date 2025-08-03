

#pragma once

#include "flatbuffers/flatbuffers.h"
#include "root_generated.h"

using TableAddFunction = std::function<void(testing::DumpsysTestDataRootBuilder* root_builder)>;

namespace testing {

struct DumpsysTestDataClass {
  virtual TableAddFunction GetTable(flatbuffers::FlatBufferBuilder& fb_builder) = 0;
  virtual ~DumpsysTestDataClass() = default;
};

}  // namespace testing
