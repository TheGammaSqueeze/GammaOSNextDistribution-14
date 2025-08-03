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

#ifndef ICING_JOIN_DOCUMENT_ID_TO_JOIN_INFO_H_
#define ICING_JOIN_DOCUMENT_ID_TO_JOIN_INFO_H_

#include <utility>

#include "icing/store/document-id.h"

namespace icing {
namespace lib {

// DocumentIdToJoinInfo is composed of document_id and its join info.
// - QualifiedId join: join info is the referenced document's namespace_id +
//   fingerprint(uri).
// - String join: join info is the term id.
// - Integer join: join info is the integer.
//
// DocumentIdToJoinInfo will be stored in posting list.
template <typename JoinInfoType>
class DocumentIdToJoinInfo {
 public:
  static DocumentIdToJoinInfo<JoinInfoType> GetInvalid() {
    return DocumentIdToJoinInfo<JoinInfoType>(kInvalidDocumentId,
                                              JoinInfoType());
  }

  explicit DocumentIdToJoinInfo(DocumentId document_id, JoinInfoType join_info)
      : document_id_(document_id), join_info_(std::move(join_info)) {}

  DocumentId document_id() const { return document_id_; }
  const JoinInfoType& join_info() const { return join_info_; }

  bool is_valid() const { return IsDocumentIdValid(document_id_); }

  bool operator<(const DocumentIdToJoinInfo<JoinInfoType>& other) const {
    if (document_id_ != other.document_id_) {
      return document_id_ < other.document_id_;
    }
    return join_info_ < other.join_info_;
  }

  bool operator==(const DocumentIdToJoinInfo<JoinInfoType>& other) const {
    return document_id_ == other.document_id_ && join_info_ == other.join_info_;
  }

 private:
  DocumentId document_id_;
  JoinInfoType join_info_;
} __attribute__((packed));

}  // namespace lib
}  // namespace icing

#endif  // ICING_JOIN_DOCUMENT_ID_TO_JOIN_INFO_H_
