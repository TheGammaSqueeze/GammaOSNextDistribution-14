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

#include "icing/store/document-log-creator.h"

#include <memory>
#include <string>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/annotate.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/file-backed-proto-log.h"
#include "icing/file/filesystem.h"
#include "icing/file/portable-file-backed-proto-log.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/document_wrapper.pb.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

// Used in DocumentId mapper to mark a document as deleted
constexpr char kDocumentLogFilename[] = "document_log";

std::string DocumentLogFilenameV0() {
  // Originally only had this one version, no suffix.
  return kDocumentLogFilename;
}

std::string DocumentLogFilenameV1() {
  return absl_ports::StrCat(kDocumentLogFilename, "_v1");
}

std::string MakeDocumentLogFilenameV0(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", DocumentLogFilenameV0());
}

std::string MakeDocumentLogFilenameV1(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", DocumentLogFilenameV1());
}

}  // namespace

std::string DocumentLogCreator::GetDocumentLogFilename() {
  // This should always return the latest version of the document log in use.
  // The current latest version is V1.
  return DocumentLogFilenameV1();
}

libtextclassifier3::StatusOr<DocumentLogCreator::CreateResult>
DocumentLogCreator::Create(const Filesystem* filesystem,
                           const std::string& base_dir,
                           int32_t compression_level) {
  bool v0_exists =
      filesystem->FileExists(MakeDocumentLogFilenameV0(base_dir).c_str());
  bool v1_exists =
      filesystem->FileExists(MakeDocumentLogFilenameV1(base_dir).c_str());

  bool new_file = false;
  int preexisting_file_version = kCurrentVersion;
  if (v0_exists && !v1_exists) {
    ICING_RETURN_IF_ERROR(
        MigrateFromV0ToV1(filesystem, base_dir, compression_level));

    // Need to regenerate derived files since documents may be written to a
    // different file offset in the log.
    preexisting_file_version = 0;
  } else if (!v1_exists) {
    // First time initializing a v1 log. There are no existing derived files at
    // this point, so we should generate some. "regenerate" here also means
    // "generate for the first time", i.e. we shouldn't expect there to be any
    // existing derived files.
    new_file = true;
  }

  ICING_ASSIGN_OR_RETURN(
      PortableFileBackedProtoLog<DocumentWrapper>::CreateResult
          log_create_result,
      PortableFileBackedProtoLog<DocumentWrapper>::Create(
          filesystem, MakeDocumentLogFilenameV1(base_dir),
          PortableFileBackedProtoLog<DocumentWrapper>::Options(
              /*compress_in=*/true,
              PortableFileBackedProtoLog<DocumentWrapper>::kMaxProtoSize,
              compression_level)));

  CreateResult create_result = {std::move(log_create_result),
                                preexisting_file_version, new_file};
  return create_result;
}

libtextclassifier3::Status DocumentLogCreator::MigrateFromV0ToV1(
    const Filesystem* filesystem, const std::string& base_dir,
    int32_t compression_level) {
  ICING_VLOG(1) << "Migrating from v0 to v1 document log.";

  // Our v0 proto log was non-portable, create it so we can read protos out from
  // it.
  auto v0_create_result_or = FileBackedProtoLog<DocumentWrapper>::Create(
      filesystem, MakeDocumentLogFilenameV0(base_dir),
      FileBackedProtoLog<DocumentWrapper>::Options(/*compress_in=*/true));
  if (!v0_create_result_or.ok()) {
    return absl_ports::Annotate(
        v0_create_result_or.status(),
        "Failed to initialize v0 document log while migrating.");
    return v0_create_result_or.status();
  }
  FileBackedProtoLog<DocumentWrapper>::CreateResult v0_create_result =
      std::move(v0_create_result_or).ValueOrDie();
  std::unique_ptr<FileBackedProtoLog<DocumentWrapper>> v0_proto_log =
      std::move(v0_create_result.proto_log);

  // Create a v1 portable proto log that we will write our protos to.
  auto v1_create_result_or =
      PortableFileBackedProtoLog<DocumentWrapper>::Create(
          filesystem, MakeDocumentLogFilenameV1(base_dir),
          PortableFileBackedProtoLog<DocumentWrapper>::Options(
              /*compress_in=*/true,
              /*max_proto_size_in=*/
              PortableFileBackedProtoLog<DocumentWrapper>::kMaxProtoSize,
              /*compression_level_in=*/compression_level));
  if (!v1_create_result_or.ok()) {
    return absl_ports::Annotate(
        v1_create_result_or.status(),
        "Failed to initialize v1 document log while migrating.");
  }
  PortableFileBackedProtoLog<DocumentWrapper>::CreateResult v1_create_result =
      std::move(v1_create_result_or).ValueOrDie();
  std::unique_ptr<PortableFileBackedProtoLog<DocumentWrapper>> v1_proto_log =
      std::move(v1_create_result.proto_log);

  // Dummy empty document to be used when copying over deleted documents.
  DocumentProto empty_document;

  // Start reading out from the old log and putting them in the new log.
  ICING_ASSIGN_OR_RETURN(FileBackedProtoLog<DocumentWrapper>::Iterator iterator,
                         v0_proto_log->GetIterator());
  auto iterator_status = iterator.Advance();
  while (iterator_status.ok()) {
    libtextclassifier3::StatusOr<DocumentWrapper> document_wrapper_or =
        v0_proto_log->ReadProto(iterator.GetOffset());

    bool deleted_document = false;
    DocumentWrapper document_wrapper;
    if (absl_ports::IsNotFound(document_wrapper_or.status())) {
      // Proto was erased, we can skip copying this into our new log.
      *document_wrapper.mutable_document() = empty_document;
      deleted_document = true;
    } else if (!document_wrapper_or.ok()) {
      // Some real error, pass up
      return document_wrapper_or.status();
    } else {
      document_wrapper = std::move(document_wrapper_or).ValueOrDie();
    }

    auto offset_or = v1_proto_log->WriteProto(document_wrapper);
    if (!offset_or.ok()) {
      return absl_ports::Annotate(
          offset_or.status(),
          "Failed to write proto to v1 document log while migrating.");
    }

    // If the original document was deleted, erase the proto we just wrote.
    // We do this to maintain the document_ids, i.e. we still want document_id 2
    // to point to a deleted document even though we may not have the document
    // contents anymore. DocumentStore guarantees that the document_ids don't
    // change unless an Optimize is triggered.
    if (deleted_document) {
      int64_t offset = offset_or.ValueOrDie();
      auto erased_status = v1_proto_log->EraseProto(offset);
      if (!erased_status.ok()) {
        return absl_ports::Annotate(
            erased_status,
            "Failed to erase proto in v1 document log while migrating.");
      }
    }

    iterator_status = iterator.Advance();
  }

  // Close out our file log pointers.
  v0_proto_log.reset();
  v1_proto_log.reset();

  return libtextclassifier3::Status::OK;
}

}  // namespace lib
}  // namespace icing
