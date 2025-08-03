// Copyright (C) 2021 Google LLC
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

#ifndef ICING_STORE_DOCUMENT_LOG_CREATOR_H_
#define ICING_STORE_DOCUMENT_LOG_CREATOR_H_

#include <string>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/file/filesystem.h"
#include "icing/file/portable-file-backed-proto-log.h"
#include "icing/proto/document_wrapper.pb.h"

namespace icing {
namespace lib {

// Handles creation of the document log and any underlying migrations that may
// be necessary.
class DocumentLogCreator {
 public:
  // Version 0 refers to FileBackedProtoLog
  // Version 1 refers to PortableFileBackedProtoLog with kFileFormatVersion = 0
  static constexpr int32_t kCurrentVersion = 1;
  struct CreateResult {
    // The create result passed up from the PortableFileBackedProtoLog::Create.
    // Contains the document log.
    PortableFileBackedProtoLog<DocumentWrapper>::CreateResult log_create_result;

    // The version number of the pre-existing document log file.
    // If there is no document log file, it will be set to kCurrentVersion.
    int preexisting_file_version;

    // Whether the created file is new.
    bool new_file;
  };

  // Creates the document log in the base_dir. Will create one if it doesn't
  // already exist.
  //
  // This also handles any potential migrations from old document log versions.
  // At the end of this call, the most up-to-date log will be returned and will
  // be usable.
  //
  // Returns:
  //   CreateResult on success.
  //   INTERNAL on any I/O error.
  static libtextclassifier3::StatusOr<DocumentLogCreator::CreateResult> Create(
      const Filesystem* filesystem, const std::string& base_dir,
      int32_t compression_level);

  // Returns the filename of the document log, without any directory prefixes.
  // Used mainly for testing purposes.
  static std::string GetDocumentLogFilename();

 private:
  // Handles migrating a v0 document log (not portable) to a v1 document log
  // (portable). This will initialize the log in the beginning, and close it
  // when migration is done. Callers will need to reinitialize the log on their
  // own.
  //
  // Returns:
  //   OK on success.
  //   INVALID_ARGUMENT if some invalid option was passed to the document log.
  //   INTERNAL on I/O error.
  static libtextclassifier3::Status MigrateFromV0ToV1(
      const Filesystem* filesystem, const std::string& base_dir,
      int32_t compression_level);
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_STORE_DOCUMENT_LOG_CREATOR_H_
