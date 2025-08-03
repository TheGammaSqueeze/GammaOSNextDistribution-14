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

#include "icing/join/qualified-id-join-index-impl-v2.h"

#include <cstdint>
#include <memory>
#include <numeric>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/filesystem.h"
#include "icing/file/persistent-storage.h"
#include "icing/file/posting_list/posting-list-identifier.h"
#include "icing/join/document-id-to-join-info.h"
#include "icing/join/qualified-id-join-index.h"
#include "icing/schema/joinable-property.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/document-id.h"
#include "icing/store/key-mapper.h"
#include "icing/store/namespace-fingerprint-identifier.h"
#include "icing/store/namespace-id.h"
#include "icing/store/persistent-hash-map-key-mapper.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"
#include "icing/util/crc32.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::HasSubstr;
using ::testing::IsEmpty;
using ::testing::IsTrue;
using ::testing::Lt;
using ::testing::Ne;
using ::testing::Not;
using ::testing::Pointee;
using ::testing::SizeIs;

using Crcs = PersistentStorage::Crcs;
using Info = QualifiedIdJoinIndexImplV2::Info;

static constexpr int32_t kCorruptedValueOffset = 3;

class QualifiedIdJoinIndexImplV2Test : public ::testing::Test {
 protected:
  void SetUp() override {
    base_dir_ = GetTestTempDir() + "/icing";
    ASSERT_THAT(filesystem_.CreateDirectoryRecursively(base_dir_.c_str()),
                IsTrue());

    working_path_ = base_dir_ + "/qualified_id_join_index_impl_v2_test";
  }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(base_dir_.c_str());
  }

  Filesystem filesystem_;
  std::string base_dir_;
  std::string working_path_;
};

libtextclassifier3::StatusOr<
    std::vector<QualifiedIdJoinIndexImplV2::JoinDataType>>
GetJoinData(const QualifiedIdJoinIndexImplV2& index,
            SchemaTypeId schema_type_id,
            JoinablePropertyId joinable_property_id) {
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<QualifiedIdJoinIndex::JoinDataIteratorBase> iter,
      index.GetIterator(schema_type_id, joinable_property_id));

  std::vector<QualifiedIdJoinIndexImplV2::JoinDataType> result;
  while (iter->Advance().ok()) {
    result.push_back(iter->GetCurrent());
  }

  return result;
}

TEST_F(QualifiedIdJoinIndexImplV2Test, InvalidWorkingPath) {
  EXPECT_THAT(QualifiedIdJoinIndexImplV2::Create(
                  filesystem_, "/dev/null/qualified_id_join_index_impl_v2_test",
                  /*pre_mapping_fbv=*/false),
              StatusIs(libtextclassifier3::StatusCode::INTERNAL));
}

TEST_F(QualifiedIdJoinIndexImplV2Test, InitializeNewFiles) {
  {
    // Create new qualified id join index
    ASSERT_FALSE(filesystem_.DirectoryExists(working_path_.c_str()));
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
        QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                           /*pre_mapping_fbv=*/false));
    EXPECT_THAT(index, Pointee(IsEmpty()));

    ICING_ASSERT_OK(index->PersistToDisk());
  }

  // Metadata file should be initialized correctly for both info and crcs
  // sections.
  const std::string metadata_file_path =
      absl_ports::StrCat(working_path_, "/metadata");
  auto metadata_buffer = std::make_unique<uint8_t[]>(
      QualifiedIdJoinIndexImplV2::kMetadataFileSize);
  ASSERT_THAT(
      filesystem_.PRead(metadata_file_path.c_str(), metadata_buffer.get(),
                        QualifiedIdJoinIndexImplV2::kMetadataFileSize,
                        /*offset=*/0),
      IsTrue());

  // Check info section
  const Info* info = reinterpret_cast<const Info*>(
      metadata_buffer.get() +
      QualifiedIdJoinIndexImplV2::kInfoMetadataBufferOffset);
  EXPECT_THAT(info->magic, Eq(Info::kMagic));
  EXPECT_THAT(info->num_data, Eq(0));
  EXPECT_THAT(info->last_added_document_id, Eq(kInvalidDocumentId));

  // Check crcs section
  const Crcs* crcs = reinterpret_cast<const Crcs*>(
      metadata_buffer.get() +
      QualifiedIdJoinIndexImplV2::kCrcsMetadataBufferOffset);
  // There are some initial info in KeyMapper, so storages_crc should be
  // non-zero.
  EXPECT_THAT(crcs->component_crcs.storages_crc, Ne(0));
  EXPECT_THAT(crcs->component_crcs.info_crc,
              Eq(Crc32(std::string_view(reinterpret_cast<const char*>(info),
                                        sizeof(Info)))
                     .Get()));
  EXPECT_THAT(crcs->all_crc,
              Eq(Crc32(std::string_view(
                           reinterpret_cast<const char*>(&crcs->component_crcs),
                           sizeof(Crcs::ComponentCrcs)))
                     .Get()));
}

TEST_F(QualifiedIdJoinIndexImplV2Test,
       InitializationShouldFailWithoutPersistToDiskOrDestruction) {
  NamespaceFingerprintIdentifier id1(/*namespace_id=*/1, /*fingerprint=*/12);
  NamespaceFingerprintIdentifier id2(/*namespace_id=*/1, /*fingerprint=*/34);
  NamespaceFingerprintIdentifier id3(/*namespace_id=*/1, /*fingerprint=*/56);
  NamespaceFingerprintIdentifier id4(/*namespace_id=*/1, /*fingerprint=*/78);

  // Create new qualified id join index
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  // Insert some data.
  ICING_ASSERT_OK(index->Put(
      /*schema_type_id=*/2, /*joinable_property_id=*/1, /*document_id=*/5,
      /*ref_namespace_fingerprint_ids=*/{id2, id1}));
  ICING_ASSERT_OK(index->PersistToDisk());
  ICING_ASSERT_OK(index->Put(
      /*schema_type_id=*/3, /*joinable_property_id=*/10, /*document_id=*/6,
      /*ref_namespace_fingerprint_ids=*/{id3}));
  ICING_ASSERT_OK(index->Put(
      /*schema_type_id=*/2, /*joinable_property_id=*/1, /*document_id=*/12,
      /*ref_namespace_fingerprint_ids=*/{id4}));

  // Without calling PersistToDisk, checksums will not be recomputed or synced
  // to disk, so initializing another instance on the same files should fail.
  EXPECT_THAT(QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                                 /*pre_mapping_fbv=*/false),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
}

TEST_F(QualifiedIdJoinIndexImplV2Test,
       InitializationShouldSucceedWithPersistToDisk) {
  NamespaceFingerprintIdentifier id1(/*namespace_id=*/1, /*fingerprint=*/12);
  NamespaceFingerprintIdentifier id2(/*namespace_id=*/1, /*fingerprint=*/34);
  NamespaceFingerprintIdentifier id3(/*namespace_id=*/1, /*fingerprint=*/56);
  NamespaceFingerprintIdentifier id4(/*namespace_id=*/1, /*fingerprint=*/78);

  // Create new qualified id join index
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index1,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  // Insert some data.
  ICING_ASSERT_OK(index1->Put(
      /*schema_type_id=*/2, /*joinable_property_id=*/1, /*document_id=*/5,
      /*ref_namespace_fingerprint_ids=*/{id2, id1}));
  ICING_ASSERT_OK(index1->Put(
      /*schema_type_id=*/3, /*joinable_property_id=*/10, /*document_id=*/6,
      /*ref_namespace_fingerprint_ids=*/{id3}));
  ICING_ASSERT_OK(index1->Put(
      /*schema_type_id=*/2, /*joinable_property_id=*/1, /*document_id=*/12,
      /*ref_namespace_fingerprint_ids=*/{id4}));
  ASSERT_THAT(index1, Pointee(SizeIs(4)));

  // After calling PersistToDisk, all checksums should be recomputed and synced
  // correctly to disk, so initializing another instance on the same files
  // should succeed, and we should be able to get the same contents.
  ICING_EXPECT_OK(index1->PersistToDisk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index2,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));
  EXPECT_THAT(index2, Pointee(SizeIs(4)));
  EXPECT_THAT(
      GetJoinData(*index2, /*schema_type_id=*/2, /*joinable_property_id=*/1),
      IsOkAndHolds(
          ElementsAre(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                          /*document_id=*/12, /*join_info=*/id4),
                      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                          /*document_id=*/5, /*join_info=*/id2),
                      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                          /*document_id=*/5, /*join_info=*/id1))));
  EXPECT_THAT(
      GetJoinData(*index2, /*schema_type_id=*/3, /*joinable_property_id=*/10),
      IsOkAndHolds(
          ElementsAre(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/6, /*join_info=*/id3))));
}

TEST_F(QualifiedIdJoinIndexImplV2Test,
       InitializationShouldSucceedAfterDestruction) {
  NamespaceFingerprintIdentifier id1(/*namespace_id=*/1, /*fingerprint=*/12);
  NamespaceFingerprintIdentifier id2(/*namespace_id=*/1, /*fingerprint=*/34);
  NamespaceFingerprintIdentifier id3(/*namespace_id=*/1, /*fingerprint=*/56);
  NamespaceFingerprintIdentifier id4(/*namespace_id=*/1, /*fingerprint=*/78);

  {
    // Create new qualified id join index
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
        QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                           /*pre_mapping_fbv=*/false));

    // Insert some data.
    ICING_ASSERT_OK(index->Put(
        /*schema_type_id=*/2, /*joinable_property_id=*/1, /*document_id=*/5,
        /*ref_namespace_fingerprint_ids=*/{id2, id1}));
    ICING_ASSERT_OK(index->Put(
        /*schema_type_id=*/3, /*joinable_property_id=*/10, /*document_id=*/6,
        /*ref_namespace_fingerprint_ids=*/{id3}));
    ICING_ASSERT_OK(index->Put(
        /*schema_type_id=*/2, /*joinable_property_id=*/1, /*document_id=*/12,
        /*ref_namespace_fingerprint_ids=*/{id4}));
    ASSERT_THAT(index, Pointee(SizeIs(4)));
  }

  {
    // The previous instance went out of scope and was destructed. Although we
    // didn't call PersistToDisk explicitly, the destructor should invoke it and
    // thus initializing another instance on the same files should succeed, and
    // we should be able to get the same contents.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
        QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                           /*pre_mapping_fbv=*/false));
    EXPECT_THAT(index, Pointee(SizeIs(4)));
    EXPECT_THAT(
        GetJoinData(*index, /*schema_type_id=*/2, /*joinable_property_id=*/1),
        IsOkAndHolds(
            ElementsAre(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                            /*document_id=*/12, /*join_info=*/id4),
                        DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                            /*document_id=*/5, /*join_info=*/id2),
                        DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                            /*document_id=*/5, /*join_info=*/id1))));
    EXPECT_THAT(
        GetJoinData(*index, /*schema_type_id=*/3, /*joinable_property_id=*/10),
        IsOkAndHolds(
            ElementsAre(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                /*document_id=*/6, /*join_info=*/id3))));
  }
}

TEST_F(QualifiedIdJoinIndexImplV2Test,
       InitializeExistingFilesWithDifferentMagicShouldFail) {
  {
    // Create new qualified id join index
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
        QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                           /*pre_mapping_fbv=*/false));
    ICING_ASSERT_OK(index->Put(
        /*schema_type_id=*/2, /*joinable_property_id=*/1, /*document_id=*/5,
        /*ref_namespace_fingerprint_ids=*/
        {NamespaceFingerprintIdentifier(/*namespace_id=*/1,
                                        /*fingerprint=*/12)}));

    ICING_ASSERT_OK(index->PersistToDisk());
  }

  {
    const std::string metadata_file_path =
        absl_ports::StrCat(working_path_, "/metadata");
    ScopedFd metadata_sfd(filesystem_.OpenForWrite(metadata_file_path.c_str()));
    ASSERT_THAT(metadata_sfd.is_valid(), IsTrue());

    auto metadata_buffer = std::make_unique<uint8_t[]>(
        QualifiedIdJoinIndexImplV2::kMetadataFileSize);
    ASSERT_THAT(filesystem_.PRead(metadata_sfd.get(), metadata_buffer.get(),
                                  QualifiedIdJoinIndexImplV2::kMetadataFileSize,
                                  /*offset=*/0),
                IsTrue());

    // Manually change magic and update checksum
    Crcs* crcs = reinterpret_cast<Crcs*>(
        metadata_buffer.get() +
        QualifiedIdJoinIndexImplV2::kCrcsMetadataBufferOffset);
    Info* info = reinterpret_cast<Info*>(
        metadata_buffer.get() +
        QualifiedIdJoinIndexImplV2::kInfoMetadataBufferOffset);
    info->magic += kCorruptedValueOffset;
    crcs->component_crcs.info_crc = info->ComputeChecksum().Get();
    crcs->all_crc = crcs->component_crcs.ComputeChecksum().Get();
    ASSERT_THAT(filesystem_.PWrite(
                    metadata_sfd.get(), /*offset=*/0, metadata_buffer.get(),
                    QualifiedIdJoinIndexImplV2::kMetadataFileSize),
                IsTrue());
  }

  // Attempt to create the qualified id join index with different magic. This
  // should fail.
  EXPECT_THAT(QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                                 /*pre_mapping_fbv=*/false),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION,
                       HasSubstr("Incorrect magic value")));
}

TEST_F(QualifiedIdJoinIndexImplV2Test,
       InitializeExistingFilesWithWrongAllCrcShouldFail) {
  {
    // Create new qualified id join index
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
        QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                           /*pre_mapping_fbv=*/false));
    ICING_ASSERT_OK(index->Put(
        /*schema_type_id=*/2, /*joinable_property_id=*/1, /*document_id=*/5,
        /*ref_namespace_fingerprint_ids=*/
        {NamespaceFingerprintIdentifier(/*namespace_id=*/1,
                                        /*fingerprint=*/12)}));

    ICING_ASSERT_OK(index->PersistToDisk());
  }

  {
    const std::string metadata_file_path =
        absl_ports::StrCat(working_path_, "/metadata");
    ScopedFd metadata_sfd(filesystem_.OpenForWrite(metadata_file_path.c_str()));
    ASSERT_THAT(metadata_sfd.is_valid(), IsTrue());

    auto metadata_buffer = std::make_unique<uint8_t[]>(
        QualifiedIdJoinIndexImplV2::kMetadataFileSize);
    ASSERT_THAT(filesystem_.PRead(metadata_sfd.get(), metadata_buffer.get(),
                                  QualifiedIdJoinIndexImplV2::kMetadataFileSize,
                                  /*offset=*/0),
                IsTrue());

    // Manually corrupt all_crc
    Crcs* crcs = reinterpret_cast<Crcs*>(
        metadata_buffer.get() +
        QualifiedIdJoinIndexImplV2::kCrcsMetadataBufferOffset);
    crcs->all_crc += kCorruptedValueOffset;

    ASSERT_THAT(filesystem_.PWrite(
                    metadata_sfd.get(), /*offset=*/0, metadata_buffer.get(),
                    QualifiedIdJoinIndexImplV2::kMetadataFileSize),
                IsTrue());
  }

  // Attempt to create the qualified id join index with metadata containing
  // corrupted all_crc. This should fail.
  EXPECT_THAT(QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                                 /*pre_mapping_fbv=*/false),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION,
                       HasSubstr("Invalid all crc")));
}

TEST_F(QualifiedIdJoinIndexImplV2Test,
       InitializeExistingFilesWithCorruptedInfoShouldFail) {
  {
    // Create new qualified id join index
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
        QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                           /*pre_mapping_fbv=*/false));
    ICING_ASSERT_OK(index->Put(
        /*schema_type_id=*/2, /*joinable_property_id=*/1, /*document_id=*/5,
        /*ref_namespace_fingerprint_ids=*/
        {NamespaceFingerprintIdentifier(/*namespace_id=*/1,
                                        /*fingerprint=*/12)}));

    ICING_ASSERT_OK(index->PersistToDisk());
  }

  {
    const std::string metadata_file_path =
        absl_ports::StrCat(working_path_, "/metadata");
    ScopedFd metadata_sfd(filesystem_.OpenForWrite(metadata_file_path.c_str()));
    ASSERT_THAT(metadata_sfd.is_valid(), IsTrue());

    auto metadata_buffer = std::make_unique<uint8_t[]>(
        QualifiedIdJoinIndexImplV2::kMetadataFileSize);
    ASSERT_THAT(filesystem_.PRead(metadata_sfd.get(), metadata_buffer.get(),
                                  QualifiedIdJoinIndexImplV2::kMetadataFileSize,
                                  /*offset=*/0),
                IsTrue());

    // Modify info, but don't update the checksum. This would be similar to
    // corruption of info.
    Info* info = reinterpret_cast<Info*>(
        metadata_buffer.get() +
        QualifiedIdJoinIndexImplV2::kInfoMetadataBufferOffset);
    info->last_added_document_id += kCorruptedValueOffset;

    ASSERT_THAT(filesystem_.PWrite(
                    metadata_sfd.get(), /*offset=*/0, metadata_buffer.get(),
                    QualifiedIdJoinIndexImplV2::kMetadataFileSize),
                IsTrue());
  }

  // Attempt to create the qualified id join index with info that doesn't match
  // its checksum. This should fail.
  EXPECT_THAT(QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                                 /*pre_mapping_fbv=*/false),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION,
                       HasSubstr("Invalid info crc")));
}

TEST_F(
    QualifiedIdJoinIndexImplV2Test,
    InitializeExistingFilesWithCorruptedSchemaJoinableIdToPostingListMapperShouldFail) {
  {
    // Create new qualified id join index
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
        QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                           /*pre_mapping_fbv=*/false));
    ICING_ASSERT_OK(index->Put(
        /*schema_type_id=*/2, /*joinable_property_id=*/1, /*document_id=*/5,
        /*ref_namespace_fingerprint_ids=*/
        {NamespaceFingerprintIdentifier(/*namespace_id=*/1,
                                        /*fingerprint=*/12)}));

    ICING_ASSERT_OK(index->PersistToDisk());
  }

  // Corrupt schema_joinable_id_to_posting_list_mapper manually.
  {
    std::string mapper_working_path = absl_ports::StrCat(
        working_path_, "/schema_joinable_id_to_posting_list_mapper");
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<KeyMapper<PostingListIdentifier>> mapper,
        PersistentHashMapKeyMapper<PostingListIdentifier>::Create(
            filesystem_, std::move(mapper_working_path),
            /*pre_mapping_fbv=*/false));
    ICING_ASSERT_OK_AND_ASSIGN(Crc32 old_crc, mapper->ComputeChecksum());
    ICING_ASSERT_OK(mapper->Put("foo", PostingListIdentifier::kInvalid));
    ICING_ASSERT_OK(mapper->PersistToDisk());
    ICING_ASSERT_OK_AND_ASSIGN(Crc32 new_crc, mapper->ComputeChecksum());
    ASSERT_THAT(old_crc, Not(Eq(new_crc)));
  }

  // Attempt to create the qualified id join index with corrupted
  // doc_join_info_mapper. This should fail.
  EXPECT_THAT(QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                                 /*pre_mapping_fbv=*/false),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION,
                       HasSubstr("Invalid storages crc")));
}

TEST_F(QualifiedIdJoinIndexImplV2Test, InvalidPut) {
  NamespaceFingerprintIdentifier id(/*namespace_id=*/1, /*fingerprint=*/12);

  // Create new qualified id join index
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  EXPECT_THAT(
      index->Put(/*schema_type_id=*/-1, /*joinable_property_id=*/1,
                 /*document_id=*/5, /*ref_namespace_fingerprint_ids=*/{id}),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(
      index->Put(/*schema_type_id=*/2, /*joinable_property_id=*/-1,
                 /*document_id=*/5, /*ref_namespace_fingerprint_ids=*/{id}),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(index->Put(/*schema_type_id=*/2, /*joinable_property_id=*/1,
                         /*document_id=*/kInvalidDocumentId,
                         /*ref_namespace_fingerprint_ids=*/{id}),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(QualifiedIdJoinIndexImplV2Test, InvalidGetIterator) {
  // Create new qualified id join index
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  EXPECT_THAT(
      index->GetIterator(/*schema_type_id=*/-1, /*joinable_property_id=*/1),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(
      index->GetIterator(/*schema_type_id=*/2, /*joinable_property_id=*/-1),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(QualifiedIdJoinIndexImplV2Test,
       PutEmptyRefNamespaceFingerprintIdsShouldReturnOk) {
  SchemaTypeId schema_type_id = 2;
  JoinablePropertyId joinable_property_id = 1;

  // Create new qualified id join index
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/5,
                 /*ref_namespace_fingerprint_ids=*/{}),
      IsOk());
  EXPECT_THAT(index, Pointee(IsEmpty()));

  EXPECT_THAT(GetJoinData(*index, schema_type_id, joinable_property_id),
              IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(GetJoinData(*index, schema_type_id + 1, joinable_property_id),
              IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(GetJoinData(*index, schema_type_id, joinable_property_id + 1),
              IsOkAndHolds(IsEmpty()));
}

TEST_F(QualifiedIdJoinIndexImplV2Test,
       PutAndGetSingleSchemaTypeAndJoinableProperty) {
  SchemaTypeId schema_type_id = 2;
  JoinablePropertyId joinable_property_id = 1;

  NamespaceFingerprintIdentifier id1(/*namespace_id=*/3, /*fingerprint=*/12);
  NamespaceFingerprintIdentifier id2(/*namespace_id=*/1, /*fingerprint=*/34);
  NamespaceFingerprintIdentifier id3(/*namespace_id=*/2, /*fingerprint=*/56);
  NamespaceFingerprintIdentifier id4(/*namespace_id=*/0, /*fingerprint=*/78);

  {
    // Create new qualified id join index
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
        QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                           /*pre_mapping_fbv=*/false));

    EXPECT_THAT(
        index->Put(schema_type_id, joinable_property_id, /*document_id=*/5,
                   /*ref_namespace_fingerprint_ids=*/{id2, id1}),
        IsOk());
    EXPECT_THAT(
        index->Put(schema_type_id, joinable_property_id, /*document_id=*/6,
                   /*ref_namespace_fingerprint_ids=*/{id3}),
        IsOk());
    EXPECT_THAT(
        index->Put(schema_type_id, joinable_property_id, /*document_id=*/12,
                   /*ref_namespace_fingerprint_ids=*/{id4}),
        IsOk());
    EXPECT_THAT(index, Pointee(SizeIs(4)));

    EXPECT_THAT(GetJoinData(*index, schema_type_id, joinable_property_id),
                IsOkAndHolds(ElementsAre(
                    DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                        /*document_id=*/12, /*join_info=*/id4),
                    DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                        /*document_id=*/6, /*join_info=*/id3),
                    DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                        /*document_id=*/5, /*join_info=*/id1),
                    DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                        /*document_id=*/5, /*join_info=*/id2))));
    EXPECT_THAT(GetJoinData(*index, schema_type_id + 1, joinable_property_id),
                IsOkAndHolds(IsEmpty()));
    EXPECT_THAT(GetJoinData(*index, schema_type_id, joinable_property_id + 1),
                IsOkAndHolds(IsEmpty()));

    ICING_ASSERT_OK(index->PersistToDisk());
  }

  // Verify we can get all of them after destructing and re-initializing.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));
  EXPECT_THAT(index, Pointee(SizeIs(4)));
  EXPECT_THAT(GetJoinData(*index, schema_type_id, joinable_property_id),
              IsOkAndHolds(ElementsAre(
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/12, /*join_info=*/id4),
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/6, /*join_info=*/id3),
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/5, /*join_info=*/id1),
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/5, /*join_info=*/id2))));
  EXPECT_THAT(GetJoinData(*index, schema_type_id + 1, joinable_property_id),
              IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(GetJoinData(*index, schema_type_id, joinable_property_id + 1),
              IsOkAndHolds(IsEmpty()));
}

TEST_F(QualifiedIdJoinIndexImplV2Test,
       PutAndGetMultipleSchemaTypesAndJoinableProperties) {
  SchemaTypeId schema_type_id1 = 2;
  SchemaTypeId schema_type_id2 = 4;

  JoinablePropertyId joinable_property_id1 = 1;
  JoinablePropertyId joinable_property_id2 = 10;

  NamespaceFingerprintIdentifier id1(/*namespace_id=*/3, /*fingerprint=*/12);
  NamespaceFingerprintIdentifier id2(/*namespace_id=*/1, /*fingerprint=*/34);
  NamespaceFingerprintIdentifier id3(/*namespace_id=*/2, /*fingerprint=*/56);
  NamespaceFingerprintIdentifier id4(/*namespace_id=*/0, /*fingerprint=*/78);

  {
    // Create new qualified id join index
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
        QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                           /*pre_mapping_fbv=*/false));

    EXPECT_THAT(
        index->Put(schema_type_id1, joinable_property_id1, /*document_id=*/5,
                   /*ref_namespace_fingerprint_ids=*/{id1}),
        IsOk());
    EXPECT_THAT(
        index->Put(schema_type_id1, joinable_property_id2, /*document_id=*/5,
                   /*ref_namespace_fingerprint_ids=*/{id2}),
        IsOk());
    EXPECT_THAT(
        index->Put(schema_type_id2, joinable_property_id1, /*document_id=*/12,
                   /*ref_namespace_fingerprint_ids=*/{id3}),
        IsOk());
    EXPECT_THAT(
        index->Put(schema_type_id2, joinable_property_id2, /*document_id=*/12,
                   /*ref_namespace_fingerprint_ids=*/{id4}),
        IsOk());
    EXPECT_THAT(index, Pointee(SizeIs(4)));

    EXPECT_THAT(GetJoinData(*index, schema_type_id1, joinable_property_id1),
                IsOkAndHolds(ElementsAre(
                    DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                        /*document_id=*/5, /*join_info=*/id1))));
    EXPECT_THAT(GetJoinData(*index, schema_type_id1, joinable_property_id2),
                IsOkAndHolds(ElementsAre(
                    DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                        /*document_id=*/5, /*join_info=*/id2))));
    EXPECT_THAT(GetJoinData(*index, schema_type_id2, joinable_property_id1),
                IsOkAndHolds(ElementsAre(
                    DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                        /*document_id=*/12, /*join_info=*/id3))));
    EXPECT_THAT(GetJoinData(*index, schema_type_id2, joinable_property_id2),
                IsOkAndHolds(ElementsAre(
                    DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                        /*document_id=*/12, /*join_info=*/id4))));

    ICING_ASSERT_OK(index->PersistToDisk());
  }

  // Verify we can get all of them after destructing and re-initializing.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));
  EXPECT_THAT(index, Pointee(SizeIs(4)));
  EXPECT_THAT(GetJoinData(*index, schema_type_id1, joinable_property_id1),
              IsOkAndHolds(ElementsAre(
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/5, /*join_info=*/id1))));
  EXPECT_THAT(GetJoinData(*index, schema_type_id1, joinable_property_id2),
              IsOkAndHolds(ElementsAre(
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/5, /*join_info=*/id2))));
  EXPECT_THAT(GetJoinData(*index, schema_type_id2, joinable_property_id1),
              IsOkAndHolds(ElementsAre(
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/12, /*join_info=*/id3))));
  EXPECT_THAT(GetJoinData(*index, schema_type_id2, joinable_property_id2),
              IsOkAndHolds(ElementsAre(
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/12, /*join_info=*/id4))));
}

TEST_F(QualifiedIdJoinIndexImplV2Test, SetLastAddedDocumentId) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  EXPECT_THAT(index->last_added_document_id(), Eq(kInvalidDocumentId));

  constexpr DocumentId kDocumentId = 100;
  index->set_last_added_document_id(kDocumentId);
  EXPECT_THAT(index->last_added_document_id(), Eq(kDocumentId));

  constexpr DocumentId kNextDocumentId = 123;
  index->set_last_added_document_id(kNextDocumentId);
  EXPECT_THAT(index->last_added_document_id(), Eq(kNextDocumentId));
}

TEST_F(
    QualifiedIdJoinIndexImplV2Test,
    SetLastAddedDocumentIdShouldIgnoreNewDocumentIdNotGreaterThanTheCurrent) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  constexpr DocumentId kDocumentId = 123;
  index->set_last_added_document_id(kDocumentId);
  ASSERT_THAT(index->last_added_document_id(), Eq(kDocumentId));

  constexpr DocumentId kNextDocumentId = 100;
  ASSERT_THAT(kNextDocumentId, Lt(kDocumentId));
  index->set_last_added_document_id(kNextDocumentId);
  // last_added_document_id() should remain unchanged.
  EXPECT_THAT(index->last_added_document_id(), Eq(kDocumentId));
}

TEST_F(QualifiedIdJoinIndexImplV2Test, Optimize) {
  // General test for Optimize().
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  SchemaTypeId schema_type_id1 = 2;
  SchemaTypeId schema_type_id2 = 5;

  JoinablePropertyId joinable_property_id1 = 11;
  JoinablePropertyId joinable_property_id2 = 15;

  NamespaceFingerprintIdentifier id1(/*namespace_id=*/2, /*fingerprint=*/101);
  NamespaceFingerprintIdentifier id2(/*namespace_id=*/3, /*fingerprint=*/102);
  NamespaceFingerprintIdentifier id3(/*namespace_id=*/4, /*fingerprint=*/103);
  NamespaceFingerprintIdentifier id4(/*namespace_id=*/0, /*fingerprint=*/104);
  NamespaceFingerprintIdentifier id5(/*namespace_id=*/0, /*fingerprint=*/105);
  NamespaceFingerprintIdentifier id6(/*namespace_id=*/1, /*fingerprint=*/106);
  NamespaceFingerprintIdentifier id7(/*namespace_id=*/3, /*fingerprint=*/107);
  NamespaceFingerprintIdentifier id8(/*namespace_id=*/2, /*fingerprint=*/108);

  EXPECT_THAT(
      index->Put(schema_type_id1, joinable_property_id1, /*document_id=*/3,
                 /*ref_namespace_fingerprint_ids=*/{id1, id2, id3}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id2, joinable_property_id2, /*document_id=*/5,
                 /*ref_namespace_fingerprint_ids=*/{id4}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id2, joinable_property_id2, /*document_id=*/8,
                 /*ref_namespace_fingerprint_ids=*/{id5, id6}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id1, joinable_property_id1, /*document_id=*/13,
                 /*ref_namespace_fingerprint_ids=*/{id7}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id1, joinable_property_id1, /*document_id=*/21,
                 /*ref_namespace_fingerprint_ids=*/{id8}),
      IsOk());
  index->set_last_added_document_id(21);

  ASSERT_THAT(index, Pointee(SizeIs(8)));

  // Delete doc id = 5, 13, compress and keep the rest.
  std::vector<DocumentId> document_id_old_to_new(22, kInvalidDocumentId);
  document_id_old_to_new[3] = 0;
  document_id_old_to_new[8] = 1;
  document_id_old_to_new[21] = 2;

  // Delete namespace id 1, 2 (and invalidate id1, id6, id8). Reorder namespace
  // ids [0, 3, 4] to [1, 2, 0].
  std::vector<NamespaceId> namespace_id_old_to_new(5, kInvalidNamespaceId);
  namespace_id_old_to_new[0] = 1;
  namespace_id_old_to_new[3] = 2;
  namespace_id_old_to_new[4] = 0;

  DocumentId new_last_added_document_id = 2;
  EXPECT_THAT(index->Optimize(document_id_old_to_new, namespace_id_old_to_new,
                              new_last_added_document_id),
              IsOk());
  EXPECT_THAT(index, Pointee(SizeIs(3)));
  EXPECT_THAT(index->last_added_document_id(), Eq(new_last_added_document_id));

  // Verify GetIterator API should work normally after Optimize().
  // 1) schema_type_id1, joinable_property_id1:
  //   - old_doc_id=21, old_ref_namespace_id=2: NOT FOUND
  //   - old_doc_id=13, old_ref_namespace_id=3: NOT FOUND
  //   - old_doc_id=3, old_ref_namespace_id=4:
  //     become new_doc_id=0, new_ref_namespace_id=0
  //   - old_doc_id=3, old_ref_namespace_id=3:
  //     become new_doc_id=0, new_ref_namespace_id=2
  //   - old_doc_id=3, old_ref_namespace_id=2: NOT FOUND
  //
  // For new_doc_id=0, it should reorder due to posting list restriction.
  EXPECT_THAT(
      GetJoinData(*index, schema_type_id1, joinable_property_id1),
      IsOkAndHolds(ElementsAre(
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/0, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/2, /*fingerprint=*/102)),
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/0, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/0, /*fingerprint=*/103)))));

  // 2) schema_type_id2, joinable_property_id2:
  //   - old_doc_id=8, old_ref_namespace_id=1: NOT FOUND
  //   - old_doc_id=8, old_ref_namespace_id=0:
  //     become new_doc_id=1, new_ref_namespace_id=1
  //   - old_doc_id=5, old_ref_namespace_id=0: NOT FOUND
  EXPECT_THAT(
      GetJoinData(*index, schema_type_id2, joinable_property_id2),
      IsOkAndHolds(
          ElementsAre(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/1, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/1, /*fingerprint=*/105)))));

  // Verify Put API should work normally after Optimize().
  NamespaceFingerprintIdentifier id9(/*namespace_id=*/1, /*fingerprint=*/109);
  EXPECT_THAT(
      index->Put(schema_type_id1, joinable_property_id1, /*document_id=*/99,
                 /*ref_namespace_fingerprint_ids=*/{id9}),
      IsOk());
  index->set_last_added_document_id(99);

  EXPECT_THAT(index, Pointee(SizeIs(4)));
  EXPECT_THAT(index->last_added_document_id(), Eq(99));
  EXPECT_THAT(
      GetJoinData(*index, schema_type_id1, joinable_property_id1),
      IsOkAndHolds(ElementsAre(
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/99, /*join_info=*/id9),
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/0, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/2, /*fingerprint=*/102)),
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/0, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/0, /*fingerprint=*/103)))));
}

TEST_F(QualifiedIdJoinIndexImplV2Test, OptimizeDocumentIdChange) {
  // Specific test for Optimize(): document id compaction.

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  SchemaTypeId schema_type_id = 2;
  JoinablePropertyId joinable_property_id = 1;

  NamespaceFingerprintIdentifier id1(/*namespace_id=*/1, /*fingerprint=*/101);
  NamespaceFingerprintIdentifier id2(/*namespace_id=*/1, /*fingerprint=*/102);
  NamespaceFingerprintIdentifier id3(/*namespace_id=*/1, /*fingerprint=*/103);
  NamespaceFingerprintIdentifier id4(/*namespace_id=*/1, /*fingerprint=*/104);
  NamespaceFingerprintIdentifier id5(/*namespace_id=*/1, /*fingerprint=*/105);
  NamespaceFingerprintIdentifier id6(/*namespace_id=*/1, /*fingerprint=*/106);

  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/3,
                 /*ref_namespace_fingerprint_ids=*/{id1, id2}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/5,
                 /*ref_namespace_fingerprint_ids=*/{id3}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/8,
                 /*ref_namespace_fingerprint_ids=*/{id4}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/13,
                 /*ref_namespace_fingerprint_ids=*/{id5}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/21,
                 /*ref_namespace_fingerprint_ids=*/{id6}),
      IsOk());
  index->set_last_added_document_id(21);

  ASSERT_THAT(index, Pointee(SizeIs(6)));

  // Delete doc id = 5, 8, compress and keep the rest.
  std::vector<DocumentId> document_id_old_to_new(22, kInvalidDocumentId);
  document_id_old_to_new[3] = 0;
  document_id_old_to_new[13] = 1;
  document_id_old_to_new[21] = 2;

  // No change for namespace id.
  std::vector<NamespaceId> namespace_id_old_to_new = {0, 1};

  DocumentId new_last_added_document_id = 2;
  EXPECT_THAT(index->Optimize(document_id_old_to_new, namespace_id_old_to_new,
                              new_last_added_document_id),
              IsOk());
  EXPECT_THAT(index, Pointee(SizeIs(4)));
  EXPECT_THAT(index->last_added_document_id(), Eq(new_last_added_document_id));

  // Verify GetIterator API should work normally after Optimize().
  // - old_doc_id=21, join_info=id6: become doc_id=2, join_info=id6
  // - old_doc_id=13, join_info=id5: become doc_id=1, join_info=id5
  // - old_doc_id=8, join_info=id4: NOT FOUND
  // - old_doc_id=5, join_info=id3: NOT FOUND
  // - old_doc_id=3, join_info=id2: become doc_id=0, join_info=id2
  // - old_doc_id=3, join_info=id1: become doc_id=0, join_info=id1
  EXPECT_THAT(GetJoinData(*index, schema_type_id, joinable_property_id),
              IsOkAndHolds(ElementsAre(
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/2, /*join_info=*/id6),
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/1, /*join_info=*/id5),
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/0, /*join_info=*/id2),
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/0, /*join_info=*/id1))));

  // Verify Put API should work normally after Optimize().
  NamespaceFingerprintIdentifier id7(/*namespace_id=*/1, /*fingerprint=*/107);
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/99,
                 /*ref_namespace_fingerprint_ids=*/{id7}),
      IsOk());
  index->set_last_added_document_id(99);

  EXPECT_THAT(index, Pointee(SizeIs(5)));
  EXPECT_THAT(index->last_added_document_id(), Eq(99));
  EXPECT_THAT(GetJoinData(*index, schema_type_id, joinable_property_id),
              IsOkAndHolds(ElementsAre(
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/99, /*join_info=*/id7),
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/2, /*join_info=*/id6),
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/1, /*join_info=*/id5),
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/0, /*join_info=*/id2),
                  DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                      /*document_id=*/0, /*join_info=*/id1))));
}

TEST_F(QualifiedIdJoinIndexImplV2Test, OptimizeOutOfRangeDocumentId) {
  // Specific test for Optimize() for out of range document id.

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  SchemaTypeId schema_type_id = 2;
  JoinablePropertyId joinable_property_id = 1;
  NamespaceFingerprintIdentifier id(/*namespace_id=*/1, /*fingerprint=*/101);

  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/99,
                 /*ref_namespace_fingerprint_ids=*/{id}),
      IsOk());
  index->set_last_added_document_id(99);

  // Create document_id_old_to_new with size = 1. Optimize should handle out of
  // range DocumentId properly.
  std::vector<DocumentId> document_id_old_to_new = {kInvalidDocumentId};
  std::vector<NamespaceId> namespace_id_old_to_new = {0, 1};

  // There shouldn't be any error due to vector index.
  EXPECT_THAT(
      index->Optimize(document_id_old_to_new, namespace_id_old_to_new,
                      /*new_last_added_document_id=*/kInvalidDocumentId),
      IsOk());
  EXPECT_THAT(index->last_added_document_id(), Eq(kInvalidDocumentId));

  // Verify all data are discarded after Optimize().
  EXPECT_THAT(index, Pointee(IsEmpty()));
  EXPECT_THAT(GetJoinData(*index, schema_type_id, joinable_property_id),
              IsOkAndHolds(IsEmpty()));
}

TEST_F(QualifiedIdJoinIndexImplV2Test, OptimizeDeleteAllDocuments) {
  // Specific test for Optimize(): delete all document ids.

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  SchemaTypeId schema_type_id = 2;
  JoinablePropertyId joinable_property_id = 1;

  NamespaceFingerprintIdentifier id1(/*namespace_id=*/1, /*fingerprint=*/101);
  NamespaceFingerprintIdentifier id2(/*namespace_id=*/1, /*fingerprint=*/102);
  NamespaceFingerprintIdentifier id3(/*namespace_id=*/1, /*fingerprint=*/103);
  NamespaceFingerprintIdentifier id4(/*namespace_id=*/1, /*fingerprint=*/104);
  NamespaceFingerprintIdentifier id5(/*namespace_id=*/1, /*fingerprint=*/105);
  NamespaceFingerprintIdentifier id6(/*namespace_id=*/1, /*fingerprint=*/106);

  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/3,
                 /*ref_namespace_fingerprint_ids=*/{id1, id2}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/5,
                 /*ref_namespace_fingerprint_ids=*/{id3}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/8,
                 /*ref_namespace_fingerprint_ids=*/{id4}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/13,
                 /*ref_namespace_fingerprint_ids=*/{id5}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/21,
                 /*ref_namespace_fingerprint_ids=*/{id6}),
      IsOk());
  index->set_last_added_document_id(21);

  ASSERT_THAT(index, Pointee(SizeIs(6)));

  // Delete all documents.
  std::vector<DocumentId> document_id_old_to_new(22, kInvalidDocumentId);

  // No change for namespace id.
  std::vector<NamespaceId> namespace_id_old_to_new = {0, 1};

  EXPECT_THAT(
      index->Optimize(document_id_old_to_new, namespace_id_old_to_new,
                      /*new_last_added_document_id=*/kInvalidDocumentId),
      IsOk());
  EXPECT_THAT(index->last_added_document_id(), Eq(kInvalidDocumentId));

  // Verify all data are discarded after Optimize().
  EXPECT_THAT(index, Pointee(IsEmpty()));
  EXPECT_THAT(GetJoinData(*index, schema_type_id, joinable_property_id),
              IsOkAndHolds(IsEmpty()));
}

TEST_F(QualifiedIdJoinIndexImplV2Test, OptimizeNamespaceIdChange) {
  // Specific test for Optimize(): referenced namespace id compaction.

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  SchemaTypeId schema_type_id = 2;
  JoinablePropertyId joinable_property_id = 1;

  NamespaceFingerprintIdentifier id1(/*namespace_id=*/3, /*fingerprint=*/101);
  NamespaceFingerprintIdentifier id2(/*namespace_id=*/5, /*fingerprint=*/102);
  NamespaceFingerprintIdentifier id3(/*namespace_id=*/4, /*fingerprint=*/103);
  NamespaceFingerprintIdentifier id4(/*namespace_id=*/0, /*fingerprint=*/104);
  NamespaceFingerprintIdentifier id5(/*namespace_id=*/2, /*fingerprint=*/105);
  NamespaceFingerprintIdentifier id6(/*namespace_id=*/1, /*fingerprint=*/106);

  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/2,
                 /*ref_namespace_fingerprint_ids=*/{id1}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/3,
                 /*ref_namespace_fingerprint_ids=*/{id2}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/5,
                 /*ref_namespace_fingerprint_ids=*/{id3}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/8,
                 /*ref_namespace_fingerprint_ids=*/{id4}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/13,
                 /*ref_namespace_fingerprint_ids=*/{id5}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/21,
                 /*ref_namespace_fingerprint_ids=*/{id6}),
      IsOk());
  index->set_last_added_document_id(21);

  ASSERT_THAT(index, Pointee(SizeIs(6)));

  // No change for document id.
  std::vector<DocumentId> document_id_old_to_new(22);
  std::iota(document_id_old_to_new.begin(), document_id_old_to_new.end(), 0);

  // Delete namespace id 2, 4. Reorder namespace id [0, 1, 3, 5] to [2, 3, 1,
  // 0].
  std::vector<NamespaceId> namespace_id_old_to_new(6, kInvalidNamespaceId);
  namespace_id_old_to_new[0] = 2;
  namespace_id_old_to_new[1] = 3;
  namespace_id_old_to_new[3] = 1;
  namespace_id_old_to_new[5] = 0;

  DocumentId new_last_added_document_id = 21;
  EXPECT_THAT(index->Optimize(document_id_old_to_new, namespace_id_old_to_new,
                              new_last_added_document_id),
              IsOk());
  EXPECT_THAT(index, Pointee(SizeIs(4)));
  EXPECT_THAT(index->last_added_document_id(), Eq(new_last_added_document_id));

  // Verify GetIterator API should work normally after Optimize().
  // - id6 (old_namespace_id=1): new_namespace_id=3 (document_id = 21)
  // - id5 (old_namespace_id=2): NOT FOUND
  // - id4 (old_namespace_id=0): new_namespace_id=2 (document_id = 8)
  // - id3 (old_namespace_id=4): NOT FOUND
  // - id2 (old_namespace_id=5): new_namespace_id=0 (document_id = 3)
  // - id1 (old_namespace_id=3): new_namespace_id=1 (document_id = 2)
  EXPECT_THAT(
      GetJoinData(*index, schema_type_id, joinable_property_id),
      IsOkAndHolds(ElementsAre(
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/21, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/3, /*fingerprint=*/106)),
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/8, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/2, /*fingerprint=*/104)),
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/3, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/0, /*fingerprint=*/102)),
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/2, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/1, /*fingerprint=*/101)))));

  // Verify Put API should work normally after Optimize().
  NamespaceFingerprintIdentifier id7(/*namespace_id=*/1, /*fingerprint=*/107);
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/99,
                 /*ref_namespace_fingerprint_ids=*/{id7}),
      IsOk());
  index->set_last_added_document_id(99);

  EXPECT_THAT(index, Pointee(SizeIs(5)));
  EXPECT_THAT(index->last_added_document_id(), Eq(99));
  EXPECT_THAT(
      GetJoinData(*index, schema_type_id, joinable_property_id),
      IsOkAndHolds(ElementsAre(
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/99, /*join_info=*/id7),
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/21, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/3, /*fingerprint=*/106)),
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/8, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/2, /*fingerprint=*/104)),
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/3, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/0, /*fingerprint=*/102)),
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/2, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/1, /*fingerprint=*/101)))));
}

TEST_F(QualifiedIdJoinIndexImplV2Test, OptimizeNamespaceIdChangeShouldReorder) {
  // Specific test for Optimize(): referenced namespace id reorder.

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  SchemaTypeId schema_type_id = 2;
  JoinablePropertyId joinable_property_id = 1;

  NamespaceFingerprintIdentifier id1(/*namespace_id=*/0, /*fingerprint=*/101);
  NamespaceFingerprintIdentifier id2(/*namespace_id=*/1, /*fingerprint=*/102);
  NamespaceFingerprintIdentifier id3(/*namespace_id=*/2, /*fingerprint=*/103);
  NamespaceFingerprintIdentifier id4(/*namespace_id=*/1, /*fingerprint=*/104);

  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/0,
                 /*ref_namespace_fingerprint_ids=*/{id1, id2, id3}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/1,
                 /*ref_namespace_fingerprint_ids=*/{id4}),
      IsOk());
  index->set_last_added_document_id(1);

  ASSERT_THAT(index, Pointee(SizeIs(4)));

  // No change for document id.
  std::vector<DocumentId> document_id_old_to_new = {0, 1};

  // Reorder namespace id [0, 1, 2] to [2, 0, 1].
  std::vector<NamespaceId> namespace_id_old_to_new = {2, 0, 1};

  DocumentId new_last_added_document_id = 1;
  EXPECT_THAT(index->Optimize(document_id_old_to_new, namespace_id_old_to_new,
                              new_last_added_document_id),
              IsOk());
  EXPECT_THAT(index, Pointee(SizeIs(4)));
  EXPECT_THAT(index->last_added_document_id(), Eq(new_last_added_document_id));

  // Verify GetIterator API should work normally after Optimize().
  // - id4 (old_namespace_id=1): new_namespace_id=0 (document_id = 1)
  // - id3 (old_namespace_id=2): new_namespace_id=1 (document_id = 0)
  // - id2 (old_namespace_id=1): new_namespace_id=0 (document_id = 0)
  // - id1 (old_namespace_id=0): new_namespace_id=2 (document_id = 0)
  //
  // Should reorder to [id4, id1, id3, id2] due to posting list restriction.
  EXPECT_THAT(
      GetJoinData(*index, schema_type_id, joinable_property_id),
      IsOkAndHolds(ElementsAre(
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/1, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/0, /*fingerprint=*/104)),
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/0, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/2, /*fingerprint=*/101)),
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/0, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/1, /*fingerprint=*/103)),
          DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/0, /*join_info=*/NamespaceFingerprintIdentifier(
                  /*namespace_id=*/0, /*fingerprint=*/102)))));
}

TEST_F(QualifiedIdJoinIndexImplV2Test, OptimizeOutOfRangeNamespaceId) {
  // Specific test for Optimize(): out of range referenced namespace id.

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  SchemaTypeId schema_type_id = 2;
  JoinablePropertyId joinable_property_id = 1;
  NamespaceFingerprintIdentifier id(/*namespace_id=*/99, /*fingerprint=*/101);

  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/0,
                 /*ref_namespace_fingerprint_ids=*/{id}),
      IsOk());
  index->set_last_added_document_id(0);

  // Create namespace_id_old_to_new with size = 1. Optimize should handle out of
  // range NamespaceId properly.
  std::vector<DocumentId> document_id_old_to_new = {0};
  std::vector<NamespaceId> namespace_id_old_to_new = {kInvalidNamespaceId};

  // There shouldn't be any error due to vector index.
  EXPECT_THAT(
      index->Optimize(document_id_old_to_new, namespace_id_old_to_new,
                      /*new_last_added_document_id=*/kInvalidDocumentId),
      IsOk());
  EXPECT_THAT(index->last_added_document_id(), Eq(kInvalidDocumentId));

  // Verify all data are discarded after Optimize().
  EXPECT_THAT(index, Pointee(IsEmpty()));
  EXPECT_THAT(GetJoinData(*index, schema_type_id, joinable_property_id),
              IsOkAndHolds(IsEmpty()));
}

TEST_F(QualifiedIdJoinIndexImplV2Test, OptimizeDeleteAllNamespaces) {
  // Specific test for Optimize(): delete all referenced namespace ids.

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));

  SchemaTypeId schema_type_id = 2;
  JoinablePropertyId joinable_property_id = 1;

  NamespaceFingerprintIdentifier id1(/*namespace_id=*/0, /*fingerprint=*/101);
  NamespaceFingerprintIdentifier id2(/*namespace_id=*/1, /*fingerprint=*/102);
  NamespaceFingerprintIdentifier id3(/*namespace_id=*/2, /*fingerprint=*/103);

  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/0,
                 /*ref_namespace_fingerprint_ids=*/{id1}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/1,
                 /*ref_namespace_fingerprint_ids=*/{id2}),
      IsOk());
  EXPECT_THAT(
      index->Put(schema_type_id, joinable_property_id, /*document_id=*/2,
                 /*ref_namespace_fingerprint_ids=*/{id3}),
      IsOk());
  index->set_last_added_document_id(3);

  ASSERT_THAT(index, Pointee(SizeIs(3)));

  // No change for document id.
  std::vector<DocumentId> document_id_old_to_new = {0, 1, 2};

  // Delete all namespaces.
  std::vector<NamespaceId> namespace_id_old_to_new(3, kInvalidNamespaceId);

  EXPECT_THAT(
      index->Optimize(document_id_old_to_new, namespace_id_old_to_new,
                      /*new_last_added_document_id=*/kInvalidDocumentId),
      IsOk());
  EXPECT_THAT(index->last_added_document_id(), Eq(kInvalidDocumentId));

  // Verify all data are discarded after Optimize().
  EXPECT_THAT(index, Pointee(IsEmpty()));
  EXPECT_THAT(GetJoinData(*index, schema_type_id, joinable_property_id),
              IsOkAndHolds(IsEmpty()));
}

TEST_F(QualifiedIdJoinIndexImplV2Test, Clear) {
  NamespaceFingerprintIdentifier id1(/*namespace_id=*/1, /*fingerprint=*/12);
  NamespaceFingerprintIdentifier id2(/*namespace_id=*/1, /*fingerprint=*/34);
  NamespaceFingerprintIdentifier id3(/*namespace_id=*/1, /*fingerprint=*/56);
  NamespaceFingerprintIdentifier id4(/*namespace_id=*/1, /*fingerprint=*/78);

  // Create new qualified id join index
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexImplV2> index,
      QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                         /*pre_mapping_fbv=*/false));
  // Insert some data.
  ICING_ASSERT_OK(index->Put(
      /*schema_type_id=*/2, /*joinable_property_id=*/1, /*document_id=*/5,
      /*ref_namespace_fingerprint_ids=*/{id2, id1}));
  ICING_ASSERT_OK(index->Put(
      /*schema_type_id=*/3, /*joinable_property_id=*/10, /*document_id=*/6,
      /*ref_namespace_fingerprint_ids=*/{id3}));
  ICING_ASSERT_OK(index->Put(
      /*schema_type_id=*/2, /*joinable_property_id=*/1, /*document_id=*/12,
      /*ref_namespace_fingerprint_ids=*/{id4}));
  ASSERT_THAT(index, Pointee(SizeIs(4)));
  index->set_last_added_document_id(12);
  ASSERT_THAT(index->last_added_document_id(), Eq(12));

  // After Clear(), last_added_document_id should be set to kInvalidDocumentId,
  // and the previous added data should be deleted.
  EXPECT_THAT(index->Clear(), IsOk());
  EXPECT_THAT(index, Pointee(IsEmpty()));
  EXPECT_THAT(index->last_added_document_id(), Eq(kInvalidDocumentId));
  EXPECT_THAT(
      GetJoinData(*index, /*schema_type_id=*/2, /*joinable_property_id=*/1),
      IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(
      GetJoinData(*index, /*schema_type_id=*/3, /*joinable_property_id=*/10),
      IsOkAndHolds(IsEmpty()));

  // Join index should be able to work normally after Clear().
  ICING_ASSERT_OK(index->Put(
      /*schema_type_id=*/2, /*joinable_property_id=*/1, /*document_id=*/20,
      /*ref_namespace_fingerprint_ids=*/{id4, id2, id1, id3}));
  index->set_last_added_document_id(20);

  EXPECT_THAT(index, Pointee(SizeIs(4)));
  EXPECT_THAT(index->last_added_document_id(), Eq(20));
  EXPECT_THAT(
      GetJoinData(*index, /*schema_type_id=*/2, /*joinable_property_id=*/1),
      IsOkAndHolds(
          ElementsAre(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                          /*document_id=*/20, /*join_info=*/id4),
                      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                          /*document_id=*/20, /*join_info=*/id3),
                      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                          /*document_id=*/20, /*join_info=*/id2),
                      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                          /*document_id=*/20, /*join_info=*/id1))));

  ICING_ASSERT_OK(index->PersistToDisk());
  index.reset();

  // Verify index after reconstructing.
  ICING_ASSERT_OK_AND_ASSIGN(
      index, QualifiedIdJoinIndexImplV2::Create(filesystem_, working_path_,
                                                /*pre_mapping_fbv=*/false));
  EXPECT_THAT(index->last_added_document_id(), Eq(20));
  EXPECT_THAT(
      GetJoinData(*index, /*schema_type_id=*/2, /*joinable_property_id=*/1),
      IsOkAndHolds(
          ElementsAre(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                          /*document_id=*/20, /*join_info=*/id4),
                      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                          /*document_id=*/20, /*join_info=*/id3),
                      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                          /*document_id=*/20, /*join_info=*/id2),
                      DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
                          /*document_id=*/20, /*join_info=*/id1))));
}

}  // namespace

}  // namespace lib
}  // namespace icing
