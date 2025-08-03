// Copyright (C) 2023 Google LLC
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

#include "icing/schema/schema-property-iterator.h"

#include <initializer_list>
#include <string>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/proto/schema.pb.h"
#include "icing/schema-builder.h"
#include "icing/schema/schema-util.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

using portable_equals_proto::EqualsProto;
using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::IsFalse;
using ::testing::IsTrue;

TEST(SchemaPropertyIteratorTest,
     SingleLevelSchemaTypeConfigShouldIterateInCorrectOrder) {
  std::string schema_type_name = "Schema";

  SchemaTypeConfigProto schema_type_config =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name)
          .AddProperty(
              PropertyConfigBuilder().SetName("Google").SetDataTypeString(
                  TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(PropertyConfigBuilder().SetName("Youtube").SetDataType(
              TYPE_BYTES))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("Alphabet")
                           .SetDataTypeInt64(NUMERIC_MATCH_UNKNOWN))
          .Build();
  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_type_name, schema_type_config}};

  SchemaPropertyIterator iterator(schema_type_config, type_config_map);
  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Alphabet"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config.properties(2)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Google"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config.properties(0)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Youtube"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config.properties(1)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(iterator.unknown_indexable_nested_property_paths(), IsEmpty());
}

TEST(SchemaPropertyIteratorTest,
     NestedSchemaTypeConfigShouldIterateInCorrectOrder) {
  std::string schema_type_name1 = "SchemaOne";
  std::string schema_type_name2 = "SchemaTwo";
  std::string schema_type_name3 = "SchemaThree";

  SchemaTypeConfigProto schema_type_config1 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name1)
          .AddProperty(
              PropertyConfigBuilder().SetName("Google").SetDataTypeString(
                  TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(PropertyConfigBuilder().SetName("Youtube").SetDataType(
              TYPE_BYTES))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("Alphabet")
                           .SetDataTypeInt64(NUMERIC_MATCH_RANGE))
          .Build();
  SchemaTypeConfigProto schema_type_config2 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name2)
          .AddProperty(PropertyConfigBuilder().SetName("Foo").SetDataTypeString(
              TERM_MATCH_UNKNOWN, TOKENIZER_NONE))
          .AddProperty(
              PropertyConfigBuilder().SetName("Bar").SetDataTypeDocument(
                  schema_type_name1, /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto schema_type_config3 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name3)
          .AddProperty(
              PropertyConfigBuilder().SetName("Hello").SetDataTypeString(
                  TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder().SetName("World").SetDataTypeDocument(
                  schema_type_name1, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder().SetName("Icing").SetDataTypeDocument(
                  schema_type_name2, /*index_nested_properties=*/true))
          .Build();
  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_type_name1, schema_type_config1},
      {schema_type_name2, schema_type_config2},
      {schema_type_name3, schema_type_config3}};

  // SchemaThree: {
  //   "Hello": TYPE_STRING,
  //   "World": TYPE_DOCUMENT SchemaOne {
  //     "Google": TYPE_STRING,
  //     "Youtube": TYPE_BYTES,
  //     "Alphabet": TYPE_INT64,
  //   },
  //   "Icing": TYPE_DOCUMENT SchemaTwo {
  //     "Foo": TYPE_STRING,
  //     "Bar": TYPE_DOCUMENT SchemaOne {
  //       "Google": TYPE_STRING,
  //       "Youtube": TYPE_BYTES,
  //       "Alphabet": TYPE_INT64,
  //     },
  //   },
  // }
  SchemaPropertyIterator iterator(schema_type_config3, type_config_map);
  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Hello"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config3.properties(0)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Icing.Bar.Alphabet"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(2)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Icing.Bar.Google"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Icing.Bar.Youtube"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Icing.Foo"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(0)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("World.Alphabet"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(2)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("World.Google"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("World.Youtube"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(iterator.unknown_indexable_nested_property_paths(), IsEmpty());
}

TEST(SchemaPropertyIteratorTest,
     NonExistingNestedSchemaTypeConfigShouldGetNotFoundError) {
  std::string schema_type_name1 = "SchemaOne";
  std::string schema_type_name2 = "SchemaTwo";

  SchemaTypeConfigProto schema_type_config1 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name1)
          .AddProperty(PropertyConfigBuilder().SetName("Google").SetDataType(
              TYPE_STRING))
          .AddProperty(PropertyConfigBuilder().SetName("Youtube").SetDataType(
              TYPE_BYTES))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("Alphabet")
                           .SetDataType(TYPE_INT64))
          .Build();
  SchemaTypeConfigProto schema_type_config2 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name2)
          .AddProperty(
              PropertyConfigBuilder().SetName("Foo").SetDataTypeDocument(
                  schema_type_name1, /*index_nested_properties=*/true))
          .Build();
  // Remove the second level (schema_type_config1) from type_config_map.
  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_type_name2, schema_type_config2}};

  SchemaPropertyIterator iterator(schema_type_config2, type_config_map);
  // Since Foo is a document type property with schema type = "SchemaOne" and
  // "SchemaOne" is not in type_config_map, Advance() should return NOT_FOUND
  // error.
  EXPECT_THAT(iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST(SchemaPropertyIteratorTest,
     SchemaTypeConfigWithEmptyPropertyShouldGetOutOfRangeErrorAtFirstAdvance) {
  std::string schema_type_name = "Schema";

  SchemaTypeConfigProto schema_type_config =
      SchemaTypeConfigBuilder().SetType(schema_type_name).Build();
  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_type_name, schema_type_config}};

  SchemaPropertyIterator iterator(schema_type_config, type_config_map);
  EXPECT_THAT(iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(iterator.unknown_indexable_nested_property_paths(), IsEmpty());
}

TEST(SchemaPropertyIteratorTest, NestedIndexable) {
  std::string schema_type_name1 = "SchemaOne";
  std::string schema_type_name2 = "SchemaTwo";
  std::string schema_type_name3 = "SchemaThree";
  std::string schema_type_name4 = "SchemaFour";

  SchemaTypeConfigProto schema_type_config1 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name1)
          .AddProperty(
              PropertyConfigBuilder().SetName("Google").SetDataTypeString(
                  TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config2 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name2)
          .AddProperty(
              PropertyConfigBuilder().SetName("Bar").SetDataTypeDocument(
                  schema_type_name1, /*index_nested_properties=*/true))
          .AddProperty(PropertyConfigBuilder().SetName("Foo").SetDataTypeString(
              TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config3 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name3)
          .AddProperty(
              PropertyConfigBuilder().SetName("Bar").SetDataTypeDocument(
                  schema_type_name1,
                  /*index_nested_properties=*/false))
          .AddProperty(PropertyConfigBuilder().SetName("Foo").SetDataTypeString(
              TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config4 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name4)
          .AddProperty(
              PropertyConfigBuilder().SetName("Baz1").SetDataTypeDocument(
                  schema_type_name2, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder().SetName("Baz2").SetDataTypeDocument(
                  schema_type_name2, /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder().SetName("Baz3").SetDataTypeDocument(
                  schema_type_name3, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder().SetName("Baz4").SetDataTypeDocument(
                  schema_type_name3, /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder().SetName("Hello1").SetDataTypeDocument(
                  schema_type_name1, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder().SetName("Hello2").SetDataTypeDocument(
                  schema_type_name1, /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder().SetName("World").SetDataTypeString(
                  TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_type_name1, schema_type_config1},
      {schema_type_name2, schema_type_config2},
      {schema_type_name3, schema_type_config3},
      {schema_type_name4, schema_type_config4}};

  // SchemaFour: {
  //   "Baz1": TYPE_DOCUMENT INDEX_NESTED_PROPERTIES=true SchemaTwo {
  //     "Bar": TYPE_DOCUMENT INDEX_NESTED_PROPERTIES=true SchemaOne {
  //       "Google": TYPE_STRING INDEXABLE,
  //     },
  //     "Foo": TYPE_STRING INDEXABLE,
  //   },
  //   "Baz2": TYPE_DOCUMENT INDEX_NESTED_PROPERTIES=false SchemaTwo {
  //     "Bar": TYPE_DOCUMENT INDEX_NESTED_PROPERTIES=true SchemaOne {
  //       "Google": TYPE_STRING INDEXABLE,
  //     },
  //     "Foo": TYPE_STRING INDEXABLE,
  //   },
  //   "Baz3": TYPE_DOCUMENT INDEX_NESTED_PROPERTIES=true SchemaThree {
  //     "Bar": TYPE_DOCUMENT INDEX_NESTED_PROPERTIES=false SchemaOne {
  //       "Google": TYPE_STRING INDEXABLE,
  //     },
  //     "Foo": TYPE_STRING INDEXABLE,
  //   },
  //   "Baz4": TYPE_DOCUMENT INDEX_NESTED_PROPERTIES=false SchemaThree {
  //     "Bar": TYPE_DOCUMENT INDEX_NESTED_PROPERTIES=false SchemaOne {
  //       "Google": TYPE_STRING INDEXABLE,
  //     },
  //     "Foo": TYPE_STRING INDEXABLE,
  //   },
  //   "Hello": TYPE_DOCUMENT INDEX_NESTED_PROPERTIES=false SchemaOne {
  //     "Google": TYPE_STRING INDEXABLE,
  //   },
  //   "World": TYPE_STRING INDEXABLE,
  // }
  SchemaPropertyIterator iterator(schema_type_config4, type_config_map);

  // Baz1 to Baz4: 2 levels of nested document type property.
  // For Baz1, all levels set index_nested_properties = true, so all leaf
  // properties should be nested indexable.
  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Baz1.Bar.Google"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Baz1.Foo"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(1)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsTrue());

  // For Baz2, the parent level sets index_nested_properties = false, so all
  // leaf properties in child levels should be nested unindexable even if
  // they've set their index_nested_properties = true.
  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Baz2.Bar.Google"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Baz2.Foo"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(1)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsFalse());

  // For Baz3, the parent level sets index_nested_properties = true, but the
  // child level sets index_nested_properties = false.
  // - Leaf properties in the parent level should be nested indexable.
  // - Leaf properties in the child level should be nested unindexable.
  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Baz3.Bar.Google"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Baz3.Foo"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(1)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsTrue());

  // For Baz4, all levels set index_nested_properties = false, so all leaf
  // properties should be nested unindexable.
  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Baz4.Bar.Google"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Baz4.Foo"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(1)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsFalse());

  // Verify 1 and 0 level of nested document type properties.
  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Hello1.Google"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("Hello2.Google"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(iterator.Advance(), IsOk());
  EXPECT_THAT(iterator.GetCurrentPropertyPath(), Eq("World"));
  EXPECT_THAT(iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config4.properties(6)));
  EXPECT_THAT(iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(iterator.unknown_indexable_nested_property_paths(), IsEmpty());
}

TEST(SchemaPropertyIteratorTest,
     IndexableNestedPropertiesList_singleNestedLevel) {
  std::string schema_type_name1 = "SchemaOne";
  std::string schema_type_name2 = "SchemaTwo";

  SchemaTypeConfigProto schema_type_config1 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name1)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop1")
                  .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop2")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop3")
                  .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schema1prop4")
                           .SetDataTypeInt64(NUMERIC_MATCH_RANGE))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schema1prop5")
                           .SetDataType(TYPE_BOOLEAN))
          .Build();
  SchemaTypeConfigProto schema_type_config2 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name2)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema2prop1")
                  .SetDataTypeDocument(
                      schema_type_name1,
                      /*indexable_nested_properties_list=*/{"schema1prop2",
                                                            "schema1prop3",
                                                            "schema1prop5"}))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema2prop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schema2prop3")
                           .SetDataTypeInt64(NUMERIC_MATCH_UNKNOWN))
          .Build();
  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_type_name1, schema_type_config1},
      {schema_type_name2, schema_type_config2}};

  // Order of iteration for Schema2:
  // {"schema2prop1.schema1prop1", "schema2prop1.schema1prop2",
  // "schema2prop1.schema1prop3", "schema2prop1.schema1prop4",
  // "schema2prop1.schema1prop5", "schema2prop2", "schema2prop3"}
  //
  // Indexable properties:
  // {"schema2prop1.schema1prop2", "schema2prop1.schema1prop3",
  // "schema2prop1.schema1prop5", "schema2prop2"}.
  //
  // "schema2prop1.schema1prop4" is indexable by its indexing-config, but is not
  // considered indexable for Schema2 because Schema2 sets its
  // index_nested_properties config to false, and "schema1prop4" is not
  // in the indexable_nested_properties_list for schema2prop1.
  //
  // "schema2prop1.schema1prop1", "schema2prop1.schema1prop3" and
  // "schema2prop1.schema1prop5" are non-indexable by its indexing-config.
  // However "schema2prop1.schema1prop3" and "schema2prop1.schema1prop5" are
  // indexed as it appears in the indexable_list.
  SchemaPropertyIterator schema2_iterator(schema_type_config2, type_config_map);

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop1"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop2"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop3"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(2)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop4"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(3)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop5"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(4)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(), Eq("schema2prop2"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(1)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(), Eq("schema2prop3"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(2)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema2_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema2_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Iterate through schema1 properties. Schema1 only has non-document type leaf
  // properties, so its properties will be assigned indexable or not according
  // to their indexing configs.
  SchemaPropertyIterator schema1_iterator(schema_type_config1, type_config_map);

  EXPECT_THAT(schema1_iterator.Advance(), IsOk());
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyPath(), Eq("schema1prop1"));
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema1_iterator.Advance(), IsOk());
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyPath(), Eq("schema1prop2"));
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema1_iterator.Advance(), IsOk());
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyPath(), Eq("schema1prop3"));
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(2)));
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema1_iterator.Advance(), IsOk());
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyPath(), Eq("schema1prop4"));
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(3)));
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema1_iterator.Advance(), IsOk());
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyPath(), Eq("schema1prop5"));
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(4)));
  EXPECT_THAT(schema1_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema1_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema1_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());
}

TEST(SchemaPropertyIteratorTest,
     IndexableNestedPropertiesList_indexBooleanTrueDoesNotAffectOtherLevels) {
  std::string schema_type_name1 = "SchemaOne";
  std::string schema_type_name2 = "SchemaTwo";
  std::string schema_type_name3 = "SchemaThree";

  SchemaTypeConfigProto schema_type_config1 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name1)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop1")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop2")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop3")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config2 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name2)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema2prop1")
                  .SetDataTypeDocument(schema_type_name1,
                                       /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema2prop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema2prop3")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config3 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name3)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema3prop3")
                  .SetDataTypeDocument(
                      schema_type_name1,
                      /*indexable_nested_properties_list=*/{"schema1prop1",
                                                            "schema1prop3"}))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schema3prop1")
                           .SetDataTypeDocument(
                               schema_type_name2,
                               /*indexable_nested_properties_list=*/
                               {"schema2prop2", "schema2prop1.schema1prop1",
                                "schema2prop1.schema1prop3"}))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema3prop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_type_name1, schema_type_config1},
      {schema_type_name2, schema_type_config2},
      {schema_type_name3, schema_type_config3}};

  // Order of iteration for Schema3:
  // {"schema3prop1.schema2prop1.schema1prop1",
  // "schema3prop1.schema2prop1.schema1prop2",
  // "schema3prop1.schema2prop1.schema1prop3",
  // "schema3prop1.schema2prop2", "schema3prop1.schema2prop3", "schema3prop2",
  // "schema3prop3.schema1prop1", "schema3prop3.schema1prop2",
  // "schema3prop3.schema1prop3"}.
  //
  // Indexable properties:
  // {"schema3prop1.schema2prop1.schema1prop1",
  // "schema3prop1.schema2prop1.schema1prop3",
  // "schema3prop1.schema2prop2", "schema3prop2", "schema3prop3.schema1prop1",
  // "schema3prop3.schema1prop3"}
  //
  // Schema2 setting index_nested_properties=true does not affect nested
  // properties indexing for Schema3.
  SchemaPropertyIterator schema3_iterator(schema_type_config3, type_config_map);

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop1.schema1prop1"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop1.schema1prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop1.schema1prop3"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(2)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop3"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(2)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(), Eq("schema3prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config3.properties(2)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop3.schema1prop1"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop3.schema1prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop3.schema1prop3"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(2)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema3_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for Schema2:
  // {"schema2prop1.schema1prop1", "schema2prop1.schema1prop2",
  // "schema2prop1.schema1prop3", "schema2prop2", "schema2prop3"}
  //
  // Indexable properties:
  // {"schema2prop1.schema1prop1", "schema2prop1.schema1prop2",
  // "schema2prop1.schema1prop3", "schema2prop2", "schema2prop3"}
  //
  // All properties are indexed because index_nested_properties=true for
  // Schema2.schema2prop1. Schema3's indexable_nested_properties setting does
  // not affect this.
  SchemaPropertyIterator schema2_iterator(schema_type_config2, type_config_map);

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop1"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop2"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop3"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(2)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(), Eq("schema2prop2"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(1)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(), Eq("schema2prop3"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(2)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema2_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());
}

TEST(SchemaPropertyIteratorTest,
     IndexableNestedPropertiesList_indexBooleanFalseDoesNotAffectOtherLevels) {
  std::string schema_type_name1 = "SchemaOne";
  std::string schema_type_name2 = "SchemaTwo";
  std::string schema_type_name3 = "SchemaThree";

  SchemaTypeConfigProto schema_type_config1 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name1)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop1")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop2")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config2 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name2)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema2prop1")
                  .SetDataTypeDocument(schema_type_name1,
                                       /*index_nested_properties=*/false))
          .Build();
  SchemaTypeConfigProto schema_type_config3 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name3)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema3prop1")
                  .SetDataTypeDocument(schema_type_name2,
                                       /*indexable_nested_properties_list=*/
                                       std::initializer_list<std::string>{
                                           "schema2prop1.schema1prop2"}))
          .Build();
  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_type_name1, schema_type_config1},
      {schema_type_name2, schema_type_config2},
      {schema_type_name3, schema_type_config3}};

  // Order of iteration for Schema3:
  // {"schema3prop1.schema2prop1.schema1prop1",
  // "schema3prop1.schema2prop1.schema1prop2"}.
  //
  // Indexable properties: {"schema3prop1.schema2prop1.schema1prop2"}
  //
  // Schema2 setting index_nested_properties=false, does not affect Schema3's
  // indexable list.
  SchemaPropertyIterator schema3_iterator(schema_type_config3, type_config_map);

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop1.schema1prop1"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop1.schema1prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema3_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for Schema2:
  // {"schema2prop1.schema1prop1", "schema2prop1.schema1prop2"}
  //
  // Indexable properties: None
  //
  // The indexable list for Schema3 does not propagate to Schema2.
  SchemaPropertyIterator schema2_iterator(schema_type_config2, type_config_map);

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop1"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop2"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema2_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema2_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());
}

TEST(SchemaPropertyIteratorTest,
     IndexableNestedPropertiesList_indexableSetDoesNotAffectOtherLevels) {
  std::string schema_type_name1 = "SchemaOne";
  std::string schema_type_name2 = "SchemaTwo";
  std::string schema_type_name3 = "SchemaThree";

  SchemaTypeConfigProto schema_type_config1 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name1)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop1")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop2")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop3")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config2 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name2)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema2prop1")
                  .SetDataTypeDocument(
                      schema_type_name1,
                      /*indexable_nested_properties_list=*/
                      std::initializer_list<std::string>{"schema1prop2"}))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema2prop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema2prop3")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config3 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name3)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema3prop3")
                  .SetDataTypeDocument(
                      schema_type_name1,
                      /*indexable_nested_properties_list=*/{"schema1prop1",
                                                            "schema1prop3"}))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schema3prop1")
                           .SetDataTypeDocument(
                               schema_type_name2,
                               /*indexable_nested_properties_list=*/
                               {"schema2prop2", "schema2prop1.schema1prop1",
                                "schema2prop1.schema1prop3"}))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema3prop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_type_name1, schema_type_config1},
      {schema_type_name2, schema_type_config2},
      {schema_type_name3, schema_type_config3}};

  // Order of iteration for Schema3:
  // {"schema3prop1.schema2prop1.schema1prop1",
  // "schema3prop1.schema2prop1.schema1prop2",
  // "schema3prop1.schema2prop1.schema1prop3",
  // "schema3prop1.schema2prop2", "schema3prop1.schema2prop3", "schema3prop2",
  // "schema3prop3.schema1prop1", "schema3prop3.schema1prop2",
  // "schema3prop3.schema1prop3"}.
  //
  // Indexable properties:
  // {"schema3prop1.schema2prop1.schema1prop1",
  // "schema3prop1.schema2prop1.schema1prop3",
  // "schema3prop1.schema2prop2", "schema3prop2", "schema3prop3.schema1prop1",
  // "schema3prop3.schema1prop3"}
  //
  // Schema2 setting indexable_nested_properties_list={schema1prop2} does not
  // affect nested properties indexing for Schema3.
  SchemaPropertyIterator schema3_iterator(schema_type_config3, type_config_map);

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop1.schema1prop1"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop1.schema1prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop1.schema1prop3"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(2)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop3"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(2)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(), Eq("schema3prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config3.properties(2)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop3.schema1prop1"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop3.schema1prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop3.schema1prop3"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(2)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema3_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for Schema2:
  // {"schema2prop1.schema1prop1", "schema2prop1.schema1prop2",
  // "schema2prop1.schema1prop3", "schema2prop2", "schema2prop3"}
  //
  // Indexable properties:
  // {"schema2prop1.schema1prop2", "schema2prop2", "schema2prop3"}
  //
  // Indexable_nested_properties set for Schema3.schema3prop1 does not propagate
  // to Schema2.
  SchemaPropertyIterator schema2_iterator(schema_type_config2, type_config_map);

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop1"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop2"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop3"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(2)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(), Eq("schema2prop2"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(1)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(), Eq("schema2prop3"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(2)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema2_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());
}

TEST(
    SchemaPropertyIteratorTest,
    IndexableNestedPropertiesList_upperLevelIndexTrueIndexesListOfNestedLevel) {
  std::string schema_type_name1 = "SchemaOne";
  std::string schema_type_name2 = "SchemaTwo";
  std::string schema_type_name3 = "SchemaThree";
  std::string schema_type_name4 = "SchemaFour";

  SchemaTypeConfigProto schema_type_config1 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name1)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop1")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop2")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config2 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name2)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema2prop1")
                  .SetDataTypeDocument(
                      schema_type_name1,
                      /*indexable_nested_properties_list=*/
                      std::initializer_list<std::string>{"schema1prop2"}))
          .Build();
  SchemaTypeConfigProto schema_type_config3 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name3)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema3prop1")
                  .SetDataTypeDocument(schema_type_name2,
                                       /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto schema_type_config4 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name4)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema4prop1")
                  .SetDataTypeDocument(schema_type_name3,
                                       /*index_nested_properties=*/true))
          .Build();
  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_type_name1, schema_type_config1},
      {schema_type_name2, schema_type_config2},
      {schema_type_name3, schema_type_config3},
      {schema_type_name4, schema_type_config4}};

  // Order of iteration for Schema4:
  // {"schema4prop1.schema3prop1.schema2prop1.schema1prop1",
  // "schema4prop1.schema3prop1.schema2prop1.schema1prop2"}.
  //
  // Indexable properties: {schema4prop1.schema3prop1.schema2prop1.schema1prop2}
  //
  // Both Schema4 and Schema3 sets index_nested_properties=true, so they both
  // want to follow the indexing behavior of its subtype.
  // Schema2 is the first subtype to define an indexing config, so we index its
  // list for both Schema3 and Schema4 even though it sets
  // index_nested_properties=false.
  SchemaPropertyIterator schema4_iterator(schema_type_config4, type_config_map);

  EXPECT_THAT(schema4_iterator.Advance(), IsOk());
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyPath(),
              Eq("schema4prop1.schema3prop1.schema2prop1.schema1prop1"));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema4_iterator.Advance(), IsOk());
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyPath(),
              Eq("schema4prop1.schema3prop1.schema2prop1.schema1prop2"));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema4_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema4_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for Schema3:
  // {"schema3prop1.schema2prop1.schema1prop1",
  // "schema3prop1.schema2prop1.schema1prop2"}.
  //
  // Indexable properties: {schema3prop1.schema2prop1.schema1prop2}
  SchemaPropertyIterator schema3_iterator(schema_type_config3, type_config_map);

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop1.schema1prop1"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop1.schema1prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema3_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for Schema2:
  // {"schema2prop1.schema1prop1", "schema2prop1.schema1prop2"}
  //
  // Indexable properties:
  // {"schema2prop1.schema1prop2"}
  //
  // Schema3 setting index_nested_properties=true does not propagate to Schema2.
  SchemaPropertyIterator schema2_iterator(schema_type_config2, type_config_map);

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop1"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop2"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema2_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());
}

TEST(SchemaPropertyIteratorTest,
     IndexableNestedPropertiesList_unknownPropPaths) {
  std::string schema_type_name1 = "SchemaOne";
  std::string schema_type_name2 = "SchemaTwo";
  std::string schema_type_name3 = "SchemaThree";
  std::string schema_type_name4 = "SchemaFour";

  SchemaTypeConfigProto schema_type_config1 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name1)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop1")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop2")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config2 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name2)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema2prop1")
                  .SetDataTypeDocument(schema_type_name1,
                                       /*indexable_nested_properties_list=*/
                                       {"schema1prop2", "schema1prop2.foo",
                                        "foo.bar", "zzz", "aaa.zzz"}))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema2prop2")
                  .SetDataTypeDocument(
                      schema_type_name1,
                      /*indexable_nested_properties_list=*/
                      {"schema1prop1", "schema1prop2", "unknown.path"}))
          .Build();
  SchemaTypeConfigProto schema_type_config3 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name3)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema3prop1")
                  .SetDataTypeDocument(
                      schema_type_name2,
                      /*indexable_nested_properties_list=*/
                      {"schema3prop1", "schema2prop1", "schema1prop2",
                       "schema2prop1.schema1prop2", "schema2prop1.zzz", "zzz"}))
          .Build();
  SchemaTypeConfigProto schema_type_config4 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name4)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema4prop1")
                  .SetDataTypeDocument(schema_type_name3,
                                       /*index_nested_properties=*/true))
          .Build();
  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_type_name1, schema_type_config1},
      {schema_type_name2, schema_type_config2},
      {schema_type_name3, schema_type_config3},
      {schema_type_name4, schema_type_config4}};

  // Order of iteration for Schema4:
  // "schema4prop1.schema3prop1.schema2prop1.schema1prop1",
  // "schema4prop1.schema3prop1.schema2prop1.schema1prop2" (indexable),
  // "schema4prop1.schema3prop1.schema2prop2.schema1prop1",
  // "schema4prop1.schema3prop1.schema2prop2.schema1prop2"
  //
  // Unknown property paths from schema3 will also be included for schema4,
  // since schema4 sets index_nested_properties=true.
  // This includes everything in schema3prop1's list except
  // "schema2prop1.schema1prop2".
  SchemaPropertyIterator schema4_iterator(schema_type_config4, type_config_map);

  EXPECT_THAT(schema4_iterator.Advance(), IsOk());
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyPath(),
              Eq("schema4prop1.schema3prop1.schema2prop1.schema1prop1"));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema4_iterator.Advance(), IsOk());
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyPath(),
              Eq("schema4prop1.schema3prop1.schema2prop1.schema1prop2"));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema4_iterator.Advance(), IsOk());
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyPath(),
              Eq("schema4prop1.schema3prop1.schema2prop2.schema1prop1"));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema4_iterator.Advance(), IsOk());
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyPath(),
              Eq("schema4prop1.schema3prop1.schema2prop2.schema1prop2"));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema4_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema4_iterator.unknown_indexable_nested_property_paths(),
              testing::ElementsAre("schema4prop1.schema3prop1.schema1prop2",
                                   "schema4prop1.schema3prop1.schema2prop1",
                                   "schema4prop1.schema3prop1.schema2prop1.zzz",
                                   "schema4prop1.schema3prop1.schema3prop1",
                                   "schema4prop1.schema3prop1.zzz"));

  // Order of iteration for Schema3:
  // "schema3prop1.schema2prop1.schema1prop1",
  // "schema3prop1.schema2prop1.schema1prop2" (indexable),
  // "schema3prop1.schema2prop2.schema1prop1",
  // "schema3prop1.schema2prop2.schema1prop2"
  //
  // Unknown properties (in order):
  // "schema3prop1.schema1prop2", "schema3prop1.schema2prop1" (not a leaf prop),
  // "schema3prop1.schema2prop1.zzz", "schema3prop1.schema3prop1",
  // "schema3prop1.zzz"
  SchemaPropertyIterator schema3_iterator(schema_type_config3, type_config_map);

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop1.schema1prop1"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop1.schema1prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop2.schema1prop1"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop2.schema1prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema3_iterator.unknown_indexable_nested_property_paths(),
              testing::ElementsAre(
                  "schema3prop1.schema1prop2", "schema3prop1.schema2prop1",
                  "schema3prop1.schema2prop1.zzz", "schema3prop1.schema3prop1",
                  "schema3prop1.zzz"));

  // Order of iteration for Schema2:
  // "schema2prop1.schema1prop1",
  // "schema2prop1.schema1prop2" (indexable),
  // "schema2prop2.schema1prop1" (indexable),
  // "schema2prop2.schema1prop2" (indexable)
  //
  // Unknown properties (in order):
  // "schema2prop1.aaa.zzz", "schema2prop1.foo.bar",
  // "schema2prop1.schema1prop2.foo", "schema2prop1.zzz",
  // "schema2prop2.unknown.path"
  SchemaPropertyIterator schema2_iterator(schema_type_config2, type_config_map);

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop1"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop2"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop2.schema1prop1"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop2.schema1prop2"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(
      schema2_iterator.unknown_indexable_nested_property_paths(),
      testing::ElementsAre("schema2prop1.aaa.zzz", "schema2prop1.foo.bar",
                           "schema2prop1.schema1prop2.foo", "schema2prop1.zzz",
                           "schema2prop2.unknown.path"));
}

TEST(SchemaPropertyIteratorTest,
     IndexableNestedPropertiesListDuplicateElements) {
  std::string schema_type_name1 = "SchemaOne";
  std::string schema_type_name2 = "SchemaTwo";
  std::string schema_type_name3 = "SchemaThree";
  std::string schema_type_name4 = "SchemaFour";

  SchemaTypeConfigProto schema_type_config1 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name1)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop1")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema1prop2")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config2 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name2)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema2prop1")
                  .SetDataTypeDocument(
                      schema_type_name1,
                      /*indexable_nested_properties_list=*/
                      {"schema1prop2", "schema1prop2", "schema1prop2.foo",
                       "schema1prop2.foo", "foo.bar", "foo.bar", "foo.bar",
                       "zzz", "zzz", "aaa.zzz", "schema1prop2"}))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schema2prop2")
                           .SetDataTypeDocument(
                               schema_type_name1,
                               /*indexable_nested_properties_list=*/
                               {"schema1prop1", "schema1prop2", "unknown.path",
                                "unknown.path", "unknown.path", "unknown.path",
                                "schema1prop1"}))
          .Build();
  SchemaTypeConfigProto schema_type_config3 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name3)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema3prop1")
                  .SetDataTypeDocument(
                      schema_type_name2,
                      /*indexable_nested_properties_list=*/
                      {"schema3prop1", "schema3prop1", "schema2prop1",
                       "schema2prop1", "schema1prop2", "schema1prop2",
                       "schema2prop1.schema1prop2", "schema2prop1.schema1prop2",
                       "schema2prop1.zzz", "zzz", "zzz"}))
          .Build();
  SchemaTypeConfigProto schema_type_config4 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name4)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schema4prop1")
                  .SetDataTypeDocument(schema_type_name3,
                                       /*index_nested_properties=*/true))
          .Build();
  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_type_name1, schema_type_config1},
      {schema_type_name2, schema_type_config2},
      {schema_type_name3, schema_type_config3},
      {schema_type_name4, schema_type_config4}};

  // The results of this test case is the same as the previous test case. This
  // is to test that the indexable-list is deduped correctly.

  // Order of iteration for Schema4:
  // "schema4prop1.schema3prop1.schema2prop1.schema1prop1",
  // "schema4prop1.schema3prop1.schema2prop1.schema1prop2" (indexable),
  // "schema4prop1.schema3prop1.schema2prop2.schema1prop1",
  // "schema4prop1.schema3prop1.schema2prop2.schema1prop2"
  //
  // Unknown property paths from schema3 will also be included for schema4,
  // since schema4 sets index_nested_properties=true.
  // This includes everything in schema3prop1's list except
  // "schema2prop1.schema1prop2".
  SchemaPropertyIterator schema4_iterator(schema_type_config4, type_config_map);

  EXPECT_THAT(schema4_iterator.Advance(), IsOk());
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyPath(),
              Eq("schema4prop1.schema3prop1.schema2prop1.schema1prop1"));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema4_iterator.Advance(), IsOk());
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyPath(),
              Eq("schema4prop1.schema3prop1.schema2prop1.schema1prop2"));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema4_iterator.Advance(), IsOk());
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyPath(),
              Eq("schema4prop1.schema3prop1.schema2prop2.schema1prop1"));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema4_iterator.Advance(), IsOk());
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyPath(),
              Eq("schema4prop1.schema3prop1.schema2prop2.schema1prop2"));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema4_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema4_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema4_iterator.unknown_indexable_nested_property_paths(),
              testing::ElementsAre("schema4prop1.schema3prop1.schema1prop2",
                                   "schema4prop1.schema3prop1.schema2prop1",
                                   "schema4prop1.schema3prop1.schema2prop1.zzz",
                                   "schema4prop1.schema3prop1.schema3prop1",
                                   "schema4prop1.schema3prop1.zzz"));

  // Order of iteration for Schema3:
  // "schema3prop1.schema2prop1.schema1prop1",
  // "schema3prop1.schema2prop1.schema1prop2" (indexable),
  // "schema3prop1.schema2prop2.schema1prop1",
  // "schema3prop1.schema2prop2.schema1prop2"
  //
  // Unknown properties (in order):
  // "schema2prop1.aaa.zzz", "schema2prop1.foo.bar",
  // "schema2prop1.schema1prop2.foo", "schema2prop1.zzz",
  // "schema2prop2.unknown.path"
  SchemaPropertyIterator schema3_iterator(schema_type_config3, type_config_map);

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop1.schema1prop1"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop1.schema1prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop2.schema1prop1"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("schema3prop1.schema2prop2.schema1prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema3_iterator.unknown_indexable_nested_property_paths(),
              testing::ElementsAre(
                  "schema3prop1.schema1prop2", "schema3prop1.schema2prop1",
                  "schema3prop1.schema2prop1.zzz", "schema3prop1.schema3prop1",
                  "schema3prop1.zzz"));

  // Order of iteration for Schema2:
  // "schema2prop1.schema1prop1",
  // "schema2prop1.schema1prop2" (indexable),
  // "schema2prop2.schema1prop1" (indexable),
  // "schema2prop2.schema1prop2" (indexable)
  //
  // Unknown properties (in order):
  // "schema2prop1.aaa.zzz", "schema2prop1.foo.bar",
  // "schema2prop1.schema1prop2.foo", "schema2prop1.zzz",
  // "schema2prop2.unknown.path"
  SchemaPropertyIterator schema2_iterator(schema_type_config2, type_config_map);

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop1"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop1.schema1prop2"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop2.schema1prop1"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(),
              Eq("schema2prop2.schema1prop2"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(
      schema2_iterator.unknown_indexable_nested_property_paths(),
      testing::ElementsAre("schema2prop1.aaa.zzz", "schema2prop1.foo.bar",
                           "schema2prop1.schema1prop2.foo", "schema2prop1.zzz",
                           "schema2prop2.unknown.path"));
}

TEST(SchemaPropertyIteratorTest,
     IndexableNestedProperties_duplicatePropertyNamesInDifferentProperties) {
  std::string schema_type_name1 = "SchemaOne";
  std::string schema_type_name2 = "SchemaTwo";
  std::string schema_type_name3 = "SchemaThree";

  SchemaTypeConfigProto schema_type_config1 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name1)
          .AddProperty(
              PropertyConfigBuilder().SetName("prop1").SetDataTypeString(
                  TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder().SetName("prop2").SetDataTypeString(
                  TERM_MATCH_PREFIX, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder().SetName("prop3").SetDataTypeString(
                  TERM_MATCH_PREFIX, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config2 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name2)
          .AddProperty(
              PropertyConfigBuilder().SetName("prop1").SetDataTypeDocument(
                  schema_type_name1,
                  /*indexable_nested_properties_list=*/
                  std::initializer_list<std::string>{"prop2"}))
          .AddProperty(
              PropertyConfigBuilder().SetName("prop2").SetDataTypeString(
                  TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder().SetName("prop3").SetDataTypeString(
                  TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config3 =
      SchemaTypeConfigBuilder()
          .SetType(schema_type_name3)
          .AddProperty(
              PropertyConfigBuilder().SetName("prop3").SetDataTypeDocument(
                  schema_type_name1,
                  /*indexable_nested_properties_list=*/
                  {"prop1", "prop3"}))
          .AddProperty(
              PropertyConfigBuilder().SetName("prop1").SetDataTypeDocument(
                  schema_type_name2,
                  /*indexable_nested_properties_list=*/
                  {"prop2", "prop1.prop1", "prop1.prop3"}))
          .AddProperty(
              PropertyConfigBuilder().SetName("prop2").SetDataTypeString(
                  TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder().SetName("prop4").SetDataTypeDocument(
                  schema_type_name1,
                  /*indexable_nested_properties_list=*/
                  {"prop2", "prop3"}))
          .Build();
  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_type_name1, schema_type_config1},
      {schema_type_name2, schema_type_config2},
      {schema_type_name3, schema_type_config3}};

  // Order of iteration for Schema3:
  // {"prop1.prop1.prop1", "prop1.prop1.prop2", "prop1.prop1.prop3",
  // "prop1.prop2", "prop1.prop3", "prop2",
  // "prop3.prop1", "prop3.prop2", "prop3.prop3",
  // "prop4.prop1", "prop4.prop2", "prop4.prop3"}.
  //
  // Indexable properties:
  // {"prop1.prop1.prop1", "prop1.prop1.prop3", "prop1.prop2", "prop2",
  // "prop3.prop1", "prop3.prop3", "prop4.prop2", "prop4.prop3"}
  //
  // Properties do not affect other properties with the same name from different
  // properties.
  SchemaPropertyIterator schema3_iterator(schema_type_config3, type_config_map);

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("prop1.prop1.prop1"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("prop1.prop1.prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(),
              Eq("prop1.prop1.prop3"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(2)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(), Eq("prop1.prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(), Eq("prop1.prop3"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(2)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(), Eq("prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config3.properties(2)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(), Eq("prop3.prop1"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(), Eq("prop3.prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(), Eq("prop3.prop3"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(2)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(), Eq("prop4.prop1"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(), Eq("prop4.prop2"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(), IsOk());
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyPath(), Eq("prop4.prop3"));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(2)));
  EXPECT_THAT(schema3_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema3_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema3_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for Schema2:
  // {"prop1.prop1", "prop1.prop2",
  // "prop1.prop3", "prop2", "prop3"}
  //
  // Indexable properties:
  // {"prop1.prop2", "prop1.prop3", "prop2", "prop3"}
  //
  // Indexable_nested_properties set for Schema3.prop1 does not propagate
  // to Schema2.
  SchemaPropertyIterator schema2_iterator(schema_type_config2, type_config_map);

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(), Eq("prop1.prop1"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(0)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(), Eq("prop1.prop2"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(1)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(), Eq("prop1.prop3"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config1.properties(2)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(), Eq("prop2"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(1)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(), IsOk());
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyPath(), Eq("prop3"));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config2.properties(2)));
  EXPECT_THAT(schema2_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema2_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema2_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());
}
TEST(SchemaPropertyIteratorTest, SingleLevelCycle) {
  std::string schema_a = "A";
  std::string schema_b = "B";

  // Create schema with A -> B -> B -> B...
  SchemaTypeConfigProto schema_type_config_a =
      SchemaTypeConfigBuilder()
          .SetType(schema_a)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaAprop1")
                           .SetDataTypeDocument(
                               schema_b, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaAprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config_b =
      SchemaTypeConfigBuilder()
          .SetType(schema_b)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaBprop1")
                           .SetDataTypeDocument(
                               schema_b, /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaBprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();

  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_a, schema_type_config_a}, {schema_b, schema_type_config_b}};

  // Order of iteration for schema A:
  // {"schemaAprop1.schemaBprop2", "schemaAprop2"}, both indexable
  SchemaPropertyIterator schema_a_iterator(schema_type_config_a,
                                           type_config_map);

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(), Eq("schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_a_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for schema B:
  // {"schemaBprop2"}, indexable.
  SchemaPropertyIterator schema_b_iterator(schema_type_config_b,
                                           type_config_map);

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(), Eq("schemaBprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_b_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());
}

TEST(SchemaPropertyIteratorTest, MultipleLevelCycle) {
  std::string schema_a = "A";
  std::string schema_b = "B";
  std::string schema_c = "C";

  // Create schema with A -> B -> C -> A -> B -> C...
  SchemaTypeConfigProto schema_type_config_a =
      SchemaTypeConfigBuilder()
          .SetType(schema_a)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaAprop1")
                           .SetDataTypeDocument(
                               schema_b, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaAprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config_b =
      SchemaTypeConfigBuilder()
          .SetType(schema_b)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaBprop1")
                           .SetDataTypeDocument(
                               schema_c, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaBprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config_c =
      SchemaTypeConfigBuilder()
          .SetType(schema_c)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaCprop1")
                           .SetDataTypeDocument(
                               schema_a, /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaCprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();

  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_a, schema_type_config_a},
      {schema_b, schema_type_config_b},
      {schema_c, schema_type_config_c}};

  // Order of iteration for schema A:
  // {"schemaAprop1.schemaBprop1.schemaCprop2", "schemaAprop1.schemaBprop2",
  // "schemaAprop2"}, all indexable
  SchemaPropertyIterator schema_a_iterator(schema_type_config_a,
                                           type_config_map);

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(), Eq("schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_a_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for schema B:
  // {"schemaBprop1.schemaCprop1.schemaAprop2", "schemaBprop1.schemaCprop2",
  // "schemaBprop2"}
  //
  // Indexable properties: {"schemaBprop1.schemaCprop2", "schemaBprop2"}
  SchemaPropertyIterator schema_b_iterator(schema_type_config_b,
                                           type_config_map);

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(), Eq("schemaBprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_b_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for schema C:
  // {"schemaCprop1.schemaAprop1.schemaBprop2", "schemaCprop1.schemaAprop2",
  // "schemaCprop2"}
  //
  // Indexable properties: {"schemaCprop2"}
  SchemaPropertyIterator schema_c_iterator(schema_type_config_c,
                                           type_config_map);

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(), Eq("schemaCprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_c_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());
}

TEST(SchemaPropertyIteratorTest, SingleLevelCycleWithIndexableList) {
  std::string schema_a = "A";
  std::string schema_b = "B";

  // Create schema with A -> B -> B -> B...
  SchemaTypeConfigProto schema_type_config_a =
      SchemaTypeConfigBuilder()
          .SetType(schema_a)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaAprop1")
                           .SetDataTypeDocument(
                               schema_b, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaAprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config_b =
      SchemaTypeConfigBuilder()
          .SetType(schema_b)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaBprop1")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaBprop2")
                           .SetDataTypeDocument(
                               schema_b, /*indexable_nested_properties_list=*/
                               {"schemaBprop1", "schemaBprop2.schemaBprop1",
                                "schemaBprop2.schemaBprop3",
                                "schemaBprop2.schemaBprop2.schemaBprop3"}))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaBprop3")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();

  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_a, schema_type_config_a}, {schema_b, schema_type_config_b}};

  // Order of iteration and whether each property is indexable for schema A:
  // {"schemaAprop1.schemaBprop1" (true),
  // "schemaAprop1.schemaBprop2.schemaBprop1" (true),
  // "schemaAprop1.schemaBprop2.schemaBprop2.schemaBprop1" (true),
  // "schemaAprop1.schemaBprop2.schemaBprop2.schemaBprop2.schemaBprop1" (false),
  // "schemaAprop1.schemaBprop2.schemaBprop2.schemaBprop2.schemaBprop3" (true),
  // "schemaAprop1.schemaBprop2.schemaBprop2.schemaBprop3" (true),
  // "schemaAprop1.schemaBprop2.schemaBprop3" (false),
  // "schemaAprop1.schemaBprop3" (true),
  // "schemaAprop2" (true)}
  SchemaPropertyIterator schema_a_iterator(schema_type_config_a,
                                           type_config_map);

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop1"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(0)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop2.schemaBprop1"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(0)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop2.schemaBprop2.schemaBprop1"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(0)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_a_iterator.GetCurrentPropertyPath(),
      Eq("schemaAprop1.schemaBprop2.schemaBprop2.schemaBprop2.schemaBprop1"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(0)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_a_iterator.GetCurrentPropertyPath(),
      Eq("schemaAprop1.schemaBprop2.schemaBprop2.schemaBprop2.schemaBprop3"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(2)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop2.schemaBprop2.schemaBprop3"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(2)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop2.schemaBprop3"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(2)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop3"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(2)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(), Eq("schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_a_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for schema B:
  // {"schemaBprop1" (true),
  // "schemaBprop2.schemaBprop1" (true),
  // "schemaBprop2.schemaBprop2.schemaBprop1" (true),
  // "schemaBprop2.schemaBprop2.schemaBprop2.schemaBprop1" (false),
  // "schemaBprop2.schemaBprop2.schemaBprop2.schemaBprop3" (true),
  // "schemaBprop2.schemaBprop2.schemaBprop3" (true),
  // "schemaBprop2.schemaBprop3" (false),
  // "schemaBprop3" (true)}
  SchemaPropertyIterator schema_b_iterator(schema_type_config_b,
                                           type_config_map);

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(), Eq("schemaBprop1"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(0)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop2.schemaBprop1"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(0)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop2.schemaBprop2.schemaBprop1"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(0)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop2.schemaBprop2.schemaBprop2.schemaBprop1"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(0)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop2.schemaBprop2.schemaBprop2.schemaBprop3"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(2)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop2.schemaBprop2.schemaBprop3"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(2)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop2.schemaBprop3"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(2)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(), Eq("schemaBprop3"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(2)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_b_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());
}

TEST(SchemaPropertyIteratorTest, MultipleCycles) {
  std::string schema_a = "A";
  std::string schema_b = "B";
  std::string schema_c = "C";
  std::string schema_d = "D";

  // Create the following schema:
  // D <--> A <--- C
  //         \    ^
  //          v  /
  //           B
  // Schema type A has two cycles: A-B-C-A and A-D-A
  SchemaTypeConfigProto schema_type_config_a =
      SchemaTypeConfigBuilder()
          .SetType(schema_a)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaAprop1")
                           .SetDataTypeDocument(
                               schema_b, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaAprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaAprop3")
                           .SetDataTypeDocument(
                               schema_d, /*index_nested_properties=*/true))
          .Build();
  SchemaTypeConfigProto schema_type_config_b =
      SchemaTypeConfigBuilder()
          .SetType(schema_b)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaBprop1")
                           .SetDataTypeDocument(
                               schema_c, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaBprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config_c =
      SchemaTypeConfigBuilder()
          .SetType(schema_c)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaCprop1")
                           .SetDataTypeDocument(
                               schema_a, /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaCprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config_d =
      SchemaTypeConfigBuilder()
          .SetType(schema_d)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaDprop1")
                           .SetDataTypeDocument(
                               schema_a, /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaDprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();

  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_a, schema_type_config_a},
      {schema_b, schema_type_config_b},
      {schema_c, schema_type_config_c},
      {schema_d, schema_type_config_d}};

  // Order of iteration for schema A:
  // {"schemaAprop1.schemaBprop1.schemaCprop2", "schemaAprop1.schemaBprop2",
  // "schemaAprop2", "schemaAprop3.schemaDprop2"}, all indexable
  SchemaPropertyIterator schema_a_iterator(schema_type_config_a,
                                           type_config_map);

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(), Eq("schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_a_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for schema B:
  // {"schemaBprop1.schemaCprop1.schemaAprop2",
  // "schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2",
  // "schemaBprop1.schemaCprop2", "schemaBprop2"}
  //
  // Indexable properties: {"schemaBprop1.schemaCprop2", "schemaBprop2"}
  SchemaPropertyIterator schema_b_iterator(schema_type_config_b,
                                           type_config_map);

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(), Eq("schemaBprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_b_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for schema C:
  // {"schemaCprop1.schemaAprop1.schemaBprop2", "schemaCprop1.schemaAprop2",
  // "schemaCprop1.schemaAprop3.schemaDprop2", "schemaCprop2"}
  //
  // Indexable properties: {"schemaCprop2"}
  SchemaPropertyIterator schema_c_iterator(schema_type_config_c,
                                           type_config_map);

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(), Eq("schemaCprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_c_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for schema D:
  // {"schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2",
  // "schemaDprop1.schemaAprop1.schemaBprop2", "schemaDprop1.schemaAprop2",
  // "schemaDprop2"}
  //
  // Indexable properties: {"schemaDprop2"}
  SchemaPropertyIterator schema_d_iterator(schema_type_config_d,
                                           type_config_map);

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(), Eq("schemaDprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_d_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());
}

TEST(SchemaPropertyIteratorTest, MultipleCyclesWithIndexableList) {
  std::string schema_a = "A";
  std::string schema_b = "B";
  std::string schema_c = "C";
  std::string schema_d = "D";

  // Create the following schema:
  // D <--> A <--- C
  //         \    ^
  //          v  /
  //           B
  // Schema type A has two cycles: A-B-C-A and A-D-A
  SchemaTypeConfigProto schema_type_config_a =
      SchemaTypeConfigBuilder()
          .SetType(schema_a)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaAprop1")
                  .SetDataTypeDocument(
                      schema_b, /*indexable_nested_properties_list=*/
                      {"schemaBprop2", "schemaBprop1.schemaCprop1.schemaAprop2",
                       "schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop2",
                       "schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2",
                       "schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1."
                       "schemaAprop2"}))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaAprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaAprop3")
                  .SetDataTypeDocument(
                      schema_d, /*indexable_nested_properties_list=*/
                      {"schemaDprop2", "schemaDprop1.schemaAprop2",
                       "schemaDprop1.schemaAprop1.schemaBprop2",
                       "schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2",
                       "schemaDprop1.schemaAprop3.schemaDprop2"}))
          .Build();
  SchemaTypeConfigProto schema_type_config_b =
      SchemaTypeConfigBuilder()
          .SetType(schema_b)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaBprop1")
                           .SetDataTypeDocument(
                               schema_c, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaBprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config_c =
      SchemaTypeConfigBuilder()
          .SetType(schema_c)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaCprop1")
                           .SetDataTypeDocument(
                               schema_a, /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaCprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config_d =
      SchemaTypeConfigBuilder()
          .SetType(schema_d)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaDprop1")
                           .SetDataTypeDocument(
                               schema_a, /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaDprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();

  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_a, schema_type_config_a},
      {schema_b, schema_type_config_b},
      {schema_c, schema_type_config_c},
      {schema_d, schema_type_config_d}};

  // Order of iteration and whether each property is indexable for schema A:
  // "schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop2" (true),
  // "schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop2" (true),
  // "schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop2"
  // (true), "schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2"
  // (true), "schemaAprop1.schemaBprop1.schemaCprop2" (false),
  // "schemaAprop1.schemaBprop2" (true),
  // "schemaAprop2" (true),
  // "schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2" (true),
  // "schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop2" (true),
  // "schemaAprop3.schemaDprop1.schemaAprop2" (true),
  // "schemaAprop3.schemaDprop1.schemaAprop3.schemaDprop2" (true),
  // "schemaAprop3.schemaDprop2" (true)
  SchemaPropertyIterator schema_a_iterator(schema_type_config_a,
                                           type_config_map);

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_a_iterator.GetCurrentPropertyPath(),
      Eq("schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3."
                 "schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_a_iterator.GetCurrentPropertyPath(),
      Eq("schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(), Eq("schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_a_iterator.GetCurrentPropertyPath(),
      Eq("schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop3.schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop3.schemaDprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_a_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration and whether each property is indexable for schema B:
  // "schemaBprop1.schemaCprop1.schemaAprop2" (false),
  // "schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2" (false),
  // "schemaBprop1.schemaCprop2" (true),
  // "schemaBprop2" (true)
  SchemaPropertyIterator schema_b_iterator(schema_type_config_b,
                                           type_config_map);

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(), Eq("schemaBprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_b_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for schema C:
  // "schemaCprop1.schemaAprop1.schemaBprop2" (false),
  // "schemaCprop1.schemaAprop2" (false),
  // "schemaCprop1.schemaAprop3.schemaDprop2" (false),
  // "schemaCprop2" (true)
  SchemaPropertyIterator schema_c_iterator(schema_type_config_c,
                                           type_config_map);

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(), Eq("schemaCprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_c_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for schema D:
  // "schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2" (false),
  // "schemaDprop1.schemaAprop1.schemaBprop2" (false),
  // "schemaDprop1.schemaAprop2" (false),
  // "schemaDprop2" (true)
  SchemaPropertyIterator schema_d_iterator(schema_type_config_d,
                                           type_config_map);

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(), Eq("schemaDprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_d_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());
}

TEST(SchemaPropertyIteratorTest, MultipleCyclesWithIndexableList_allIndexTrue) {
  std::string schema_a = "A";
  std::string schema_b = "B";
  std::string schema_c = "C";
  std::string schema_d = "D";

  // Create the following schema:
  // D <--> A <--- C
  //         \    ^
  //          v  /
  //           B
  // Schema type A has two cycles: A-B-C-A and A-D-A
  SchemaTypeConfigProto schema_type_config_a =
      SchemaTypeConfigBuilder()
          .SetType(schema_a)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaAprop1")
                  .SetDataTypeDocument(
                      schema_b, /*indexable_nested_properties_list=*/
                      {"schemaBprop2", "schemaBprop1.schemaCprop1.schemaAprop2",
                       "schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop2",
                       "schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2",
                       "schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1."
                       "schemaAprop2"}))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaAprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaAprop3")
                  .SetDataTypeDocument(
                      schema_d, /*indexable_nested_properties_list=*/
                      {"schemaDprop2", "schemaDprop1.schemaAprop2",
                       "schemaDprop1.schemaAprop1.schemaBprop2",
                       "schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2",
                       "schemaDprop1.schemaAprop3.schemaDprop2"}))
          .Build();
  SchemaTypeConfigProto schema_type_config_b =
      SchemaTypeConfigBuilder()
          .SetType(schema_b)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaBprop1")
                           .SetDataTypeDocument(
                               schema_c, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaBprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config_c =
      SchemaTypeConfigBuilder()
          .SetType(schema_c)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaCprop1")
                           .SetDataTypeDocument(
                               schema_a, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaCprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config_d =
      SchemaTypeConfigBuilder()
          .SetType(schema_d)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaDprop1")
                           .SetDataTypeDocument(
                               schema_a, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaDprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();

  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_a, schema_type_config_a},
      {schema_b, schema_type_config_b},
      {schema_c, schema_type_config_c},
      {schema_d, schema_type_config_d}};

  // Order of iteration and whether each property is indexable for schema A:
  // "schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop2" (true),
  // "schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop2" (true),
  // "schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop2"
  // (true), "schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2"
  // (true), "schemaAprop1.schemaBprop1.schemaCprop2" (false),
  // "schemaAprop1.schemaBprop2" (true),
  // "schemaAprop2" (true),
  // "schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2" (true),
  // "schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop2" (true),
  // "schemaAprop3.schemaDprop1.schemaAprop2" (true),
  // "schemaAprop3.schemaDprop1.schemaAprop3.schemaDprop2" (true),
  // "schemaAprop3.schemaDprop2" (true)
  SchemaPropertyIterator schema_a_iterator(schema_type_config_a,
                                           type_config_map);

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_a_iterator.GetCurrentPropertyPath(),
      Eq("schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3."
                 "schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_a_iterator.GetCurrentPropertyPath(),
      Eq("schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(), Eq("schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_a_iterator.GetCurrentPropertyPath(),
      Eq("schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop3.schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop3.schemaDprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_a_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration and whether each property is indexable for schema B:
  // "schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop2"
  // (true),
  // "schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop2"
  // (true),
  // "schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop2"
  // (true),
  // "schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2"
  // (true), "schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop2"
  // (false), "schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop2" (true),
  // "schemaBprop1.schemaCprop1.schemaAprop2" (true),
  // "schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2"
  // (true),
  // "schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop2"
  // (true), "schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop2"
  // (true),
  // "schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop3.schemaDprop2"
  // (true), "schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2" (true)
  // "schemaBprop1.schemaCprop2" (true)
  // "schemaBprop2" (true)

  SchemaPropertyIterator schema_b_iterator(schema_type_config_b,
                                           type_config_map);

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop1."
                 "schemaCprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop1."
                 "schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop1."
                 "schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop1."
                 "schemaCprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_b_iterator.GetCurrentPropertyPath(),
      Eq("schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1."
                 "schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1."
                 "schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_b_iterator.GetCurrentPropertyPath(),
      Eq("schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1."
                 "schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(), Eq("schemaBprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_b_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration and whether each property is indexable for schema C:
  // "schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop2"
  // (true), "schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop2"
  // (true),
  // "schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop2"
  // (true),
  // "schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2"
  // (true),
  // "schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop2" (false),
  // "schemaCprop1.schemaAprop1.schemaBprop2" (true),
  // "schemaCprop1.schemaAprop2" (true),
  // "schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2"
  // (true),
  // "schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop2" (true),
  // "schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop2" (true),
  // "schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop3.schemaDprop2" (true),
  // "schemaCprop1.schemaAprop3.schemaDprop2" (true)
  // "schemaCprop2" (true)
  SchemaPropertyIterator schema_c_iterator(schema_type_config_c,
                                           type_config_map);

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop1."
                 "schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_c_iterator.GetCurrentPropertyPath(),
      Eq("schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop1."
                 "schemaAprop3.schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop1."
                 "schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop1."
                 "schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_c_iterator.GetCurrentPropertyPath(),
      Eq("schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_c_iterator.GetCurrentPropertyPath(),
      Eq("schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(), Eq("schemaCprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_c_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration and whether each property is indexable for schema D:
  // "schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop2"
  // (true), "schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop2"
  // (true),
  // "schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop2"
  // (true),
  // "schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2"
  // (true), "schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2" (false),
  // "schemaDprop1.schemaAprop1.schemaBprop2" (true),
  // "schemaDprop1.schemaAprop2" (true),
  // "schemaDprop1.schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2"
  // (true), "schemaDprop1.schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop2"
  // (true), "schemaDprop1.schemaAprop3.schemaDprop1.schemaAprop2" (true),
  // "schemaDprop1.schemaAprop3.schemaDprop1.schemaAprop3.schemaDprop2" (true),
  // "schemaDprop1.schemaAprop3.schemaDprop2" (true),
  // "schemaDprop2" (true)
  SchemaPropertyIterator schema_d_iterator(schema_type_config_d,
                                           type_config_map);

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop1."
                 "schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_d_iterator.GetCurrentPropertyPath(),
      Eq("schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop1."
                 "schemaAprop3.schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop1."
                 "schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop3.schemaDprop1.schemaAprop1."
                 "schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_d_iterator.GetCurrentPropertyPath(),
      Eq("schemaDprop1.schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop3.schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_d_iterator.GetCurrentPropertyPath(),
      Eq("schemaDprop1.schemaAprop3.schemaDprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(), Eq("schemaDprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_d_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());
}

TEST(SchemaPropertyIteratorTest,
     MultipleCyclesWithIndexableList_unknownPropPaths) {
  std::string schema_a = "A";
  std::string schema_b = "B";
  std::string schema_c = "C";
  std::string schema_d = "D";

  // Create the following schema:
  // D <--> A <--- C
  //         \    ^
  //          v  /
  //           B
  // Schema type A has two cycles: A-B-C-A and A-D-A
  SchemaTypeConfigProto schema_type_config_a =
      SchemaTypeConfigBuilder()
          .SetType(schema_a)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaAprop1")
                  .SetDataTypeDocument(
                      schema_b, /*indexable_nested_properties_list=*/
                      {"schemaBprop2", "schemaBprop1.schemaCprop1.schemaAprop2",
                       "schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop2",
                       "schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2",
                       "schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1."
                       "schemaAprop2",
                       "schemaBprop1.schemaCprop1",
                       "schemaBprop1.schemaCprop1.schemaAprop3", "schemaAprop2",
                       "schemaBprop2.schemaCprop2", "schemaBprop1.foo.bar",
                       "foo", "foo", "bar"}))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaAprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaAprop3")
                  .SetDataTypeDocument(
                      schema_d, /*indexable_nested_properties_list=*/
                      {"schemaDprop2", "schemaDprop1.schemaAprop2",
                       "schemaDprop1.schemaAprop1.schemaBprop2",
                       "schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2",
                       "schemaDprop1.schemaAprop3.schemaDprop2", "schemaBprop2",
                       "bar", "schemaDprop2.foo", "schemaDprop1",
                       "schemaAprop3.schemaDprop2"}))
          .Build();
  SchemaTypeConfigProto schema_type_config_b =
      SchemaTypeConfigBuilder()
          .SetType(schema_b)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaBprop1")
                           .SetDataTypeDocument(
                               schema_c, /*index_nested_properties=*/true))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaBprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config_c =
      SchemaTypeConfigBuilder()
          .SetType(schema_c)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaCprop1")
                           .SetDataTypeDocument(
                               schema_a, /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaCprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config_d =
      SchemaTypeConfigBuilder()
          .SetType(schema_d)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaDprop1")
                           .SetDataTypeDocument(
                               schema_a, /*index_nested_properties=*/false))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaDprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();

  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_a, schema_type_config_a},
      {schema_b, schema_type_config_b},
      {schema_c, schema_type_config_c},
      {schema_d, schema_type_config_d}};

  // Order of iteration and whether each property is indexable for schema A:
  // "schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop2" (true),
  // "schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop2" (true),
  // "schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop1.schemaAprop2"
  // (true), "schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2"
  // (true), "schemaAprop1.schemaBprop1.schemaCprop2" (false),
  // "schemaAprop1.schemaBprop2" (true),
  // "schemaAprop2" (true),
  // "schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2" (true),
  // "schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop2" (true),
  // "schemaAprop3.schemaDprop1.schemaAprop2" (true),
  // "schemaAprop3.schemaDprop1.schemaAprop3.schemaDprop2" (true),
  // "schemaAprop3.schemaDprop2" (true)
  //
  // The following properties listed in the indexable_list are not defined
  // in the schema and should not be seen during iteration. These should appear
  // in the unknown_indexable_nested_properties_ set.
  // "schemaAprop1.bar",
  // "schemaAprop1.foo",
  // "schemaAprop1.schemaAprop2",
  // "schemaAprop1.schemaBprop1.foo.bar",
  // "schemaAprop1.schemaBprop1.schemaCprop1",
  // "schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3",
  // "schemaAprop1.schemaBprop2.schemaCprop2",
  // "schemaAprop3.bar",
  // "schemaAprop3.schemaAprop3.schemaDprop2",
  // "schemaAprop3.schemaBprop2",
  // "schemaAprop3.schemaDprop1",
  // "schemaAprop3.schemaDprop2.foo"
  SchemaPropertyIterator schema_a_iterator(schema_type_config_a,
                                           type_config_map);

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_a_iterator.GetCurrentPropertyPath(),
      Eq("schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3."
                 "schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_a_iterator.GetCurrentPropertyPath(),
      Eq("schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(), Eq("schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(
      schema_a_iterator.GetCurrentPropertyPath(),
      Eq("schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop3.schemaDprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop3.schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop3.schemaDprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(
      schema_a_iterator.unknown_indexable_nested_property_paths(),
      ElementsAre(
          "schemaAprop1.bar", "schemaAprop1.foo", "schemaAprop1.schemaAprop2",
          "schemaAprop1.schemaBprop1.foo.bar",
          "schemaAprop1.schemaBprop1.schemaCprop1",
          "schemaAprop1.schemaBprop1.schemaCprop1.schemaAprop3",
          "schemaAprop1.schemaBprop2.schemaCprop2", "schemaAprop3.bar",
          "schemaAprop3.schemaAprop3.schemaDprop2", "schemaAprop3.schemaBprop2",
          "schemaAprop3.schemaDprop1", "schemaAprop3.schemaDprop2.foo"));

  // Order of iteration and whether each property is indexable for schema B:
  // "schemaBprop1.schemaCprop1.schemaAprop2" (false),
  // "schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2" (false),
  // "schemaBprop1.schemaCprop2" (true),
  // "schemaBprop2" (true)
  SchemaPropertyIterator schema_b_iterator(schema_type_config_b,
                                           type_config_map);

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(),
              Eq("schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyPath(), Eq("schemaBprop2"));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_b_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_b_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_b_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for schema C:
  // "schemaCprop1.schemaAprop1.schemaBprop2" (false),
  // "schemaCprop1.schemaAprop2" (false),
  // "schemaCprop1.schemaAprop3.schemaDprop2" (false),
  // "schemaCprop2" (true)
  SchemaPropertyIterator schema_c_iterator(schema_type_config_c,
                                           type_config_map);

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(),
              Eq("schemaCprop1.schemaAprop3.schemaDprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_c_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyPath(), Eq("schemaCprop2"));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_c_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_c_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_c_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());

  // Order of iteration for schema D:
  // "schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2" (false),
  // "schemaDprop1.schemaAprop1.schemaBprop2" (false),
  // "schemaDprop1.schemaAprop2" (false),
  // "schemaDprop2" (true)
  SchemaPropertyIterator schema_d_iterator(schema_type_config_d,
                                           type_config_map);

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop1.schemaBprop1.schemaCprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_c.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(),
              Eq("schemaDprop1.schemaAprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_d_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyPath(), Eq("schemaDprop2"));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_d.properties(1)));
  EXPECT_THAT(schema_d_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_d_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_d_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());
}

TEST(SchemaPropertyIteratorTest, TopLevelCycleWithMultipleIndexableLists) {
  std::string schema_a = "A";
  std::string schema_b = "B";
  std::string schema_c = "C";
  std::string schema_d = "D";

  // Create the following schema:
  // A <-> A -> B
  // A has a top-level property that is a self-reference.
  SchemaTypeConfigProto schema_type_config_a =
      SchemaTypeConfigBuilder()
          .SetType(schema_a)
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaAprop1")
                           .SetDataTypeDocument(
                               schema_b, /*indexable_nested_properties_list=*/
                               {"schemaBprop1", "schemaBprop2"}))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("schemaAprop2")
                           .SetDataTypeDocument(
                               schema_a, /*indexable_nested_properties_list=*/
                               {"schemaAprop1.schemaBprop2",
                                "schemaAprop1.schemaBprop3"}))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaAprop3")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto schema_type_config_b =
      SchemaTypeConfigBuilder()
          .SetType(schema_b)
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaBprop1")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaBprop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("schemaBprop3")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN))
          .Build();

  SchemaUtil::TypeConfigMap type_config_map = {
      {schema_a, schema_type_config_a}, {schema_b, schema_type_config_b}};

  // Order of iteration for Schema A:
  // "schemaAprop1.schemaBprop1" (true)
  // "schemaAprop1.schemaBprop2" (true)
  // "schemaAprop1.schemaBprop3" (false)
  // "schemaAprop2.schemaAprop1.schemaBprop1" (false)
  // "schemaAprop2.schemaAprop1.schemaBprop2" (true)
  // "schemaAprop2.schemaAprop1.schemaBprop3" (true)
  // "schemaAprop2.schemaAprop3" (false)
  // "schemaAprop3" (true)
  SchemaPropertyIterator schema_a_iterator(schema_type_config_a,
                                           type_config_map);

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop1"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(0)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop1.schemaBprop3"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(2)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop2.schemaAprop1.schemaBprop1"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(0)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop2.schemaAprop1.schemaBprop2"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(1)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop2.schemaAprop1.schemaBprop3"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_b.properties(2)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(),
              Eq("schemaAprop2.schemaAprop3"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(2)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsFalse());

  EXPECT_THAT(schema_a_iterator.Advance(), IsOk());
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyPath(), Eq("schemaAprop3"));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyConfig(),
              EqualsProto(schema_type_config_a.properties(2)));
  EXPECT_THAT(schema_a_iterator.GetCurrentPropertyIndexable(), IsTrue());

  EXPECT_THAT(schema_a_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(schema_a_iterator.unknown_indexable_nested_property_paths(),
              IsEmpty());
}

}  // namespace

}  // namespace lib
}  // namespace icing
