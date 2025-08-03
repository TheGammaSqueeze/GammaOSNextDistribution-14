
#include "baz_generated.h"
#include "root.h"
#include "root_generated.h"

namespace testing {

class BazTestDataClass : public DumpsysTestDataClass {
 public:
  TableAddFunction GetTable(flatbuffers::FlatBufferBuilder& fb_builder) override {
    auto sub_name_private = fb_builder.CreateString("Baz Subtable Private");
    auto sub_name_opaque = fb_builder.CreateString("Baz Subtable Opaque");
    auto sub_name_anonymized = fb_builder.CreateString("Baz Subtable Anonymized");
    auto sub_name_any = fb_builder.CreateString("Baz Subtable Any");

    auto private_subtable = CreateBazSubTablePrivate(fb_builder, 1, sub_name_private);
    auto opaque_subtable = CreateBazSubTableOpaque(fb_builder, 1, sub_name_opaque);
    auto anonymized_subtable = CreateBazSubTableAnonymized(fb_builder, 1, sub_name_anonymized);
    auto any_subtable = CreateBazSubTableAny(fb_builder, 1, 2, 3, 4, sub_name_any);

    BazTestSchemaBuilder builder(fb_builder);
    builder.add_sub_table_private(private_subtable);
    builder.add_sub_table_opaque(opaque_subtable);
    builder.add_sub_table_anonymized(anonymized_subtable);
    builder.add_sub_table_any(any_subtable);
    auto baz_table = builder.Finish();

    return [baz_table](DumpsysTestDataRootBuilder* builder) { builder->add_baz_module_data(baz_table); };
  }
};

}  // namespace testing
