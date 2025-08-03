
#include "qux_generated.h"
#include "root.h"
#include "root_generated.h"

namespace testing {

class QuxTestDataClass : public DumpsysTestDataClass {
 public:
  TableAddFunction GetTable(flatbuffers::FlatBufferBuilder& fb_builder) override {
    auto name = fb_builder.CreateString("Qux Module String");

    QuxTestSchemaBuilder builder(fb_builder);
    builder.add_qux_int_private(123);
    builder.add_qux_int_opaque(456);
    builder.add_qux_int_anonymized(789);
    builder.add_qux_int_any(0xabc);
    builder.add_qux_string_name(name);

    auto qux_table = builder.Finish();

    return [qux_table](DumpsysTestDataRootBuilder* builder) { builder->add_qux_module_data(qux_table); };
  }
};

}  // namespace testing
