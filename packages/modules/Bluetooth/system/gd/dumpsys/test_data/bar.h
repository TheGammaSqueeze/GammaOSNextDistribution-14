/**
 *
 **/
#include "bar_generated.h"
#include "root.h"
#include "root_generated.h"

namespace testing {

class BarTestDataClass : public DumpsysTestDataClass {
 public:
  TableAddFunction GetTable(flatbuffers::FlatBufferBuilder& /* fb_builder */) override {
    return [](DumpsysTestDataRootBuilder* /* builder */) {};
  }
};

}  // namespace testing
