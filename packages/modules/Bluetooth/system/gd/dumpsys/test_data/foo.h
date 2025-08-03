
#include "foo_generated.h"
#include "root.h"
#include "root_generated.h"

namespace testing {

class FooTestDataClass : public DumpsysTestDataClass {
 public:
  TableAddFunction GetTable(flatbuffers::FlatBufferBuilder& fb_builder) override {
    auto int_string = fb_builder.CreateString("123");
    auto float_string = fb_builder.CreateString("123.456");
    auto bool_string = fb_builder.CreateString("true");

    FooTestSchemaBuilder builder(fb_builder);
    builder.add_foo_int_private(123);
    builder.add_foo_int_opaque(123);
    builder.add_foo_int_anonymized(123);
    builder.add_foo_int_any(123);
    builder.add_foo_int_string(int_string);

    builder.add_foo_float_private(123.456);
    builder.add_foo_float_opaque(123.456);
    builder.add_foo_float_anonymized(123.456);
    builder.add_foo_float_any(123.456);
    builder.add_foo_float_string(float_string);

    builder.add_foo_bool_private(true);
    builder.add_foo_bool_opaque(true);
    builder.add_foo_bool_anonymized(true);
    builder.add_foo_bool_any(true);
    builder.add_foo_bool_string(bool_string);

    auto foo_table = builder.Finish();

    return [foo_table](DumpsysTestDataRootBuilder* builder) { builder->add_foo_module_data(foo_table); };
  }
};

}  // namespace testing
