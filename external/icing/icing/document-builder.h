// Copyright (C) 2019 Google LLC
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

#ifndef ICING_DOCUMENT_BUILDER_H_
#define ICING_DOCUMENT_BUILDER_H_

#include <cstdint>
#include <initializer_list>
#include <string>
#include <string_view>
#include <utility>

#include "icing/proto/document.pb.h"

namespace icing {
namespace lib {

class DocumentBuilder {
 public:
  DocumentBuilder() = default;
  explicit DocumentBuilder(DocumentProto document)
      : document_(std::move(document)) {}

  DocumentBuilder& SetNamespace(std::string name_space) {
    document_.set_namespace_(std::move(name_space));
    return *this;
  }

  DocumentBuilder& SetUri(std::string uri) {
    document_.set_uri(std::move(uri));
    return *this;
  }

  DocumentBuilder& SetKey(std::string name_space, std::string uri) {
    return SetNamespace(std::move(name_space)).SetUri(std::move(uri));
  }

  DocumentBuilder& SetSchema(std::string schema) {
    document_.set_schema(std::move(schema));
    return *this;
  }

  DocumentBuilder& SetCreationTimestampMs(uint64_t creation_timestamp_ms) {
    document_.set_creation_timestamp_ms(creation_timestamp_ms);
    return *this;
  }

  DocumentBuilder& SetScore(int32_t score) {
    document_.set_score(score);
    return *this;
  }

  DocumentBuilder& SetTtlMs(uint64_t ttl_ms) {
    document_.set_ttl_ms(ttl_ms);
    return *this;
  }

  DocumentBuilder& ClearProperties() {
    document_.clear_properties();
    return *this;
  }

  // Takes a property name and any number of string values.
  template <typename... V>
  DocumentBuilder& AddStringProperty(std::string property_name,
                                     V... string_values) {
    return AddStringProperty(std::move(property_name), {string_values...});
  }

  // Takes a property name and iterator of int64_t values.
  template <typename InputIt>
  DocumentBuilder& AddInt64Property(std::string property_name, InputIt first,
                                    InputIt last) {
    auto property = document_.add_properties();
    property->set_name(std::move(property_name));
    for (InputIt it = first; it != last; ++it) {
      property->mutable_int64_values()->Add(*it);
    }
    return *this;
  }

  // Takes a property name and any number of int64_t values.
  template <typename... V>
  DocumentBuilder& AddInt64Property(std::string property_name,
                                    V... int64_values) {
    std::initializer_list<int64_t> int64_values_list = {int64_values...};
    return AddInt64Property(std::move(property_name), int64_values_list.begin(),
                            int64_values_list.end());
  }

  // Takes a property name and any number of double values.
  template <typename... V>
  DocumentBuilder& AddDoubleProperty(std::string property_name,
                                     V... double_values) {
    return AddDoubleProperty(std::move(property_name), {double_values...});
  }

  // Takes a property name and any number of boolean values.
  template <typename... V>
  DocumentBuilder& AddBooleanProperty(std::string property_name,
                                      V... boolean_values) {
    return AddBooleanProperty(std::move(property_name), {boolean_values...});
  }

  // Takes a property name and any number of bytes values.
  template <typename... V>
  DocumentBuilder& AddBytesProperty(std::string property_name,
                                    V... bytes_values) {
    return AddBytesProperty(std::move(property_name), {bytes_values...});
  }
  // Takes a property name and any number of document values.
  template <typename... V>
  DocumentBuilder& AddDocumentProperty(std::string property_name,
                                       V&&... document_values) {
    return AddDocumentProperty(std::move(property_name), {document_values...});
  }

  DocumentProto Build() const { return document_; }

 private:
  DocumentProto document_;

  DocumentBuilder& AddStringProperty(
      std::string property_name,
      std::initializer_list<std::string_view> string_values) {
    auto property = document_.add_properties();
    property->set_name(std::move(property_name));
    for (std::string_view string_value : string_values) {
      property->mutable_string_values()->Add(std::string(string_value));
    }
    return *this;
  }

  DocumentBuilder& AddDoubleProperty(
      std::string property_name, std::initializer_list<double> double_values) {
    auto property = document_.add_properties();
    property->set_name(std::move(property_name));
    for (double double_value : double_values) {
      property->mutable_double_values()->Add(double_value);
    }
    return *this;
  }

  DocumentBuilder& AddBooleanProperty(
      std::string property_name, std::initializer_list<bool> boolean_values) {
    auto property = document_.add_properties();
    property->set_name(std::move(property_name));
    for (bool boolean_value : boolean_values) {
      property->mutable_boolean_values()->Add(boolean_value);
    }
    return *this;
  }

  DocumentBuilder& AddBytesProperty(
      std::string property_name,
      std::initializer_list<std::string> bytes_values) {
    auto property = document_.add_properties();
    property->set_name(std::move(property_name));
    for (const std::string& bytes_value : bytes_values) {
      property->mutable_bytes_values()->Add(std::string(bytes_value));
    }
    return *this;
  }

  DocumentBuilder& AddDocumentProperty(
      std::string property_name,
      std::initializer_list<DocumentProto> document_values) {
    auto property = document_.add_properties();
    property->set_name(std::move(property_name));
    for (DocumentProto document_value : document_values) {
      property->mutable_document_values()->Add(std::move(document_value));
    }
    return *this;
  }
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_DOCUMENT_BUILDER_H_
