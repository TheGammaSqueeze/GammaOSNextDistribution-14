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

#ifndef ICING_UTIL_DOCUMENT_VALIDATOR_H_
#define ICING_UTIL_DOCUMENT_VALIDATOR_H_

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/proto/document.pb.h"
#include "icing/schema/schema-store.h"

namespace icing {
namespace lib {

// This class validates DocumentProto based on the corresponding
// SchemaTypeConfigProto in the given type config map.
class DocumentValidator {
 public:
  explicit DocumentValidator(const SchemaStore* schema_store);
  DocumentValidator() = delete;

  // This function validates:
  //  1. DocumentProto.namespace is not empty
  //  2. DocumentProto.uri is not empty in top-level documents. Nested documents
  //     may have empty uris.
  //  3. DocumentProto.schema is not empty
  //  4. DocumentProto.schema matches one of SchemaTypeConfigProto.schema_type
  //     in the given SchemaProto in constructor
  //  5. Each PropertyProto.name in DocumentProto.properties is not empty
  //  6. Each PropertyProto.name is unique
  //  7. Each PropertyProto.name matches one of
  //     PropertyConfigProto.property_name in the given SchemaProto in
  //     constructor
  //  8. For each PropertyProto, the size of repeated value field matches
  //     PropertyConfigProto.cardinality defined in the given SchemaProto in
  //     constructor (e.g. OPTIONAL means 0 or 1, REQUIRED means 1)
  //  9. For each PropertyProto with nested DocumentProto,
  //     DocumentProto.schema (nested) matches the current
  //     PropertyConfigProto.schema_type
  // 10. All PropertyProto with REQUIRED cardinality in the corresponding
  //     PropertyConfigProto present in the DocumentProto
  // 11. DocumentProto.score is not negative
  // 12. DocumentProto.creation_timestamp_ms is not negative
  // 13. DocumentProto.ttl_ms is not negative
  //
  // In addition, all nested DocumentProto will also be validated towards the
  // requirements above.
  //
  // 'depth' indicates what nesting level the document may be at. A top-level
  // document has a nesting depth of 0.
  //
  // Returns:
  //   OK on success
  //   FAILED_PRECONDITION if no schema is set yet
  //   INVALID_ARGUMENT if any of case 1, 2, 3, 5, 8, 9, 10, 11, 12, 13 fails
  //   NOT_FOUND if case 4 or 7 fails
  //   ALREADY_EXISTS if case 6 fails
  //   INTERNAL on any I/O error
  libtextclassifier3::Status Validate(const DocumentProto& document,
                                      int depth = 0);

  void UpdateSchemaStore(const SchemaStore* schema_store) {
    schema_store_ = schema_store;
  }

 private:
  const SchemaStore* schema_store_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_UTIL_DOCUMENT_VALIDATOR_H_
