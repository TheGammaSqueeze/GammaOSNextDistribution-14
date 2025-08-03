/*
 * Copyright 2020 The Android Open Source Project
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

#include "dumpsys/filter.h"

#include <memory>

#include "common/init_flags.h"
#include "dumpsys/internal/filter_internal.h"
#include "os/log.h"

using namespace bluetooth;
using namespace dumpsys;

class Filter {
 public:
  Filter(const dumpsys::ReflectionSchema& reflection_schema) : reflection_schema_(reflection_schema) {}

  virtual ~Filter() = default;

  virtual void FilterInPlace(char* dumpsys_data) = 0;

  static std::unique_ptr<Filter> Factory(
      dumpsys::FilterType filter_type, const dumpsys::ReflectionSchema& reflection_schema);

 protected:
  /**
   * Given both reflection field data and the populated flatbuffer table data, if any,
   * filter the contents of the field based upon the filtering privacy level.
   *
   * Primitives and composite strings may be successfully processed at this point.
   * Other composite types (e.g. structs or tables) must be expanded into the
   * respective grouping of subfields.
   *
   * @param field The reflection field information from the bundled schema
   * @param table The populated field data, if any
   *
   * @return true if field was filtered successfully, false otherwise.
   */
  virtual bool FilterField(const reflection::Field* /* field */, flatbuffers::Table* /* table */) {
    return false;
  }

  /**
   * Given both reflection object data and the populated flatbuffer table data, if any,
   * filter the object fields based upon the filtering privacy level.
   *
   * @param object The reflection object information from the bundled schema
   * @param table The populated field data, if any
   *
   */
  virtual void FilterObject(
      const reflection::Object* /* object */, flatbuffers::Table* /* table */){};

  /**
   * Given both reflection field data and the populated table data, if any,
   * filter the contents of the table based upon the filtering privacy level.
   *
   * @param schema The reflection schema information from the bundled schema
   * @param table The populated field data, if any
   *
   */
  virtual void FilterTable(
      const reflection::Schema* /* schema */, flatbuffers::Table* /* table */){};

  const dumpsys::ReflectionSchema& reflection_schema_;
};

class DeveloperPrivacyFilter : public Filter {
 public:
  DeveloperPrivacyFilter(const dumpsys::ReflectionSchema& reflection_schema) : Filter(reflection_schema) {}
  void FilterInPlace(char* /* dumpsys_data */) override { /* Nothing to do in this mode */
  }
};

class UserPrivacyFilter : public Filter {
 public:
  UserPrivacyFilter(const dumpsys::ReflectionSchema& reflection_schema) : Filter(reflection_schema) {}
  void FilterInPlace(char* dumpsys_data) override;

 protected:
  bool FilterField(const reflection::Field* field, flatbuffers::Table* table) override;
  void FilterObject(const reflection::Object* object, flatbuffers::Table* table) override;
  void FilterTable(const reflection::Schema* schema, flatbuffers::Table* table) override;
};

bool UserPrivacyFilter::FilterField(const reflection::Field* field, flatbuffers::Table* table) {
  ASSERT(field != nullptr);
  ASSERT(table != nullptr);
  internal::PrivacyLevel privacy_level = internal::FindFieldPrivacyLevel(*field);

  const auto type = static_cast<flatbuffers::BaseType>(field->type()->base_type());
  switch (type) {
    case flatbuffers::BASE_TYPE_INT:
      return internal::FilterTypeInteger(*field, table, privacy_level);
      break;
    case flatbuffers::BASE_TYPE_FLOAT:
      return internal::FilterTypeFloat(*field, table, privacy_level);
      break;
    case flatbuffers::BASE_TYPE_STRING:
      return internal::FilterTypeString(*field, table, privacy_level);
      break;
    case flatbuffers::BASE_TYPE_STRUCT:
      return internal::FilterTypeStruct(*field, table, privacy_level);
      break;
    case flatbuffers::BASE_TYPE_BOOL:
      return internal::FilterTypeBool(*field, table, privacy_level);
      break;
    case flatbuffers::BASE_TYPE_LONG:
      return internal::FilterTypeLong(*field, table, privacy_level);
      break;
    default:
      LOG_WARN("Unsupported base type:%s", internal::FlatbufferTypeText(type).c_str());
      break;
  }
  return false;
}

void UserPrivacyFilter::FilterObject(const reflection::Object* object, flatbuffers::Table* table) {
  ASSERT(object != nullptr);
  if (table == nullptr) {
    return;  // table data is not populated
  }
  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    if (!FilterField(*it, table)) {
      LOG_ERROR("%s Unable to filter field from an object when it's expected it will work", __func__);
    };
  }
}

void UserPrivacyFilter::FilterTable(const reflection::Schema* schema, flatbuffers::Table* table) {
  if (schema == nullptr) {
    LOG_WARN("%s schema is nullptr...probably ok", __func__);
    return;
  }

  const reflection::Object* object = schema->root_table();
  if (object == nullptr) {
    LOG_WARN("%s reflection object is nullptr...is ok ?", __func__);
    return;
  }

  if (table == nullptr) {
    return;  // table not populated
  }

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    if (FilterField(*it, table)) {
      continue;  // Field successfully filtered
    }
    // Get the index of this complex non-string object from the schema which is
    // also the same index into the data table.
    int32_t index = it->type()->index();
    ASSERT(index != -1);

    flatbuffers::Table* sub_table = table->GetPointer<flatbuffers::Table*>(it->offset());
    const reflection::Schema* sub_schema =
        reflection_schema_.FindInReflectionSchema(schema->objects()->Get(index)->name()->str());

    if (sub_schema != nullptr) {
      FilterTable(sub_schema, sub_table);  // Top level schema
    } else {
      // Leaf node schema
      const flatbuffers::String* name = schema->objects()->Get(index)->name();
      const reflection::Object* sub_object = internal::FindReflectionObject(schema->objects(), name);
      if (sub_object != nullptr) {
        FilterObject(sub_object, sub_table);
      } else {
        LOG_ERROR("Unable to find reflection sub object:%s\n", name->c_str());
      }
    }
  }
}

void UserPrivacyFilter::FilterInPlace(char* dumpsys_data) {
  ASSERT(dumpsys_data != nullptr);
  const reflection::Schema* root_schema = reflection_schema_.FindInReflectionSchema(reflection_schema_.GetRootName());
  flatbuffers::Table* table = const_cast<flatbuffers::Table*>(flatbuffers::GetRoot<flatbuffers::Table>(dumpsys_data));
  FilterTable(root_schema, table);
}

std::unique_ptr<Filter> Filter::Factory(
    dumpsys::FilterType filter_type, const dumpsys::ReflectionSchema& reflection_schema) {
  switch (filter_type) {
    case dumpsys::FilterType::AS_DEVELOPER:
      return std::make_unique<DeveloperPrivacyFilter>(reflection_schema);
    default:
      return std::make_unique<UserPrivacyFilter>(reflection_schema);
  }
}

void bluetooth::dumpsys::FilterInPlace(
    FilterType filter_type, const ReflectionSchema& reflection_schema, std::string* dumpsys_data) {
  auto filter = Filter::Factory(filter_type, reflection_schema);
  filter->FilterInPlace(dumpsys_data->data());
}
