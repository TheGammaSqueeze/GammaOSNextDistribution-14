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

#ifndef ICING_SCHEMA_BACKUP_SCHEMA_PRODUCER_H_
#define ICING_SCHEMA_BACKUP_SCHEMA_PRODUCER_H_

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/proto/schema.pb.h"
#include "icing/schema/section-manager.h"
#include "icing/schema/section.h"

namespace icing {
namespace lib {

class BackupSchemaProducer {
 public:
  // Creates a BackupSchemaProducer based off of schema.
  // If schema doesn't require a backup schema (because it is fully
  // rollback-proof) then no copies will be made and `is_backup_necessary` will
  // return false.
  // If schema *does* require a backup schema, then `is_backup_necessary` will
  // return true and the backup schema can be retrieved by calling `Produce`.
  // Returns:
  //   - On success, a BackupSchemaProducer
  //   - INTERNAL_ERROR if the schema is inconsistent with the type_manager.
  static libtextclassifier3::StatusOr<BackupSchemaProducer> Create(
      const SchemaProto& schema, const SectionManager& type_manager);

  SchemaProto Produce() && { return std::move(cached_schema_); }

  bool is_backup_necessary() const { return !cached_schema_.types().empty(); }

 private:
  BackupSchemaProducer() = default;
  explicit BackupSchemaProducer(SchemaProto&& schema)
      : cached_schema_(std::move(schema)) {}

  SchemaProto cached_schema_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCHEMA_BACKUP_SCHEMA_PRODUCER_H_
