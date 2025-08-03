//
// Copyright (C) 2021 The Android Open Source Project
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
//

#include <algorithm>
#include <memory>
#include <string>
#include <utility>

#include <fcntl.h>
#include <sys/sendfile.h>
#include <unistd.h>

#include <android-base/strings.h>
#include <brillo/data_encoding.h>
#include <brillo/message_loops/fake_message_loop.h>
#include <bsdiff/bsdiff.h>
#include <gtest/gtest.h>
#include <liblp/builder.h>
#include <fs_mgr.h>
#include <liblp/liblp.h>

#include "update_engine/aosp/boot_control_android.h"
#include "update_engine/aosp/daemon_state_android.h"
#include "update_engine/aosp/update_attempter_android.h"
#include "update_engine/common/constants.h"
#include "update_engine/common/error.h"
#include "update_engine/common/fake_boot_control.h"
#include "update_engine/common/fake_hardware.h"
#include "update_engine/common/hash_calculator.h"
#include "update_engine/common/prefs.h"
#include "update_engine/common/test_utils.h"
#include "update_engine/common/testing_constants.h"
#include "update_engine/common/utils.h"
#include "update_engine/payload_consumer/file_descriptor.h"
#include "update_engine/payload_consumer/install_plan.h"
#include "update_engine/payload_consumer/payload_constants.h"
#include "update_engine/payload_generator/delta_diff_generator.h"
#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/payload_generator/payload_file.h"
#include "update_engine/payload_generator/payload_signer.h"
#include "update_engine/update_metadata.pb.h"
#include "update_engine/update_status_utils.h"

namespace chromeos_update_engine {

class UpdateAttempterAndroidIntegrationTest : public ::testing::Test,
                                              public ServiceObserverInterface {
  void AddFakePartitionGroup() {
    dynamic_control_ = boot_control_.dynamic_control_.get();
    auto super_device = dynamic_control_->GetSuperDevice();
    ASSERT_TRUE(super_device.has_value());
    super_device_ = super_device->value();
    builder_ = android::fs_mgr::MetadataBuilder::New(
        super_device->value(), boot_control_.GetCurrentSlot());
    ASSERT_NE(builder_, nullptr);

    // Remove dangling fake partitions, if test crashed before they might not
    // get cleaned up properly.
    RemoveFakePartitionGroup();
    ASSERT_TRUE(builder_->AddGroup("fake_group", kFakePartitionSize * 2));
    ExportPartitionTable();
  }
  void RemoveFakePartitionGroup() {
    builder_->RemovePartition("fake_a");
    builder_->RemovePartition("fake_b");
    builder_->RemoveGroupAndPartitions("fake_group");
  }

  // Fill partition |path| with arbitrary data.
  void FillPartition(const std::string& path, const bool is_source) {
    std::array<uint8_t, kBlockSize> data;
    EintrSafeFileDescriptor fd;
    fd.Open(path.c_str(), O_RDWR);
    for (size_t i = 0; i < kFakePartitionSize / kBlockSize; i++) {
      if (is_source) {
        std::fill(data.begin(), data.end(), i);
      } else {
        std::fill(
            data.begin(), data.end(), kFakePartitionSize / kBlockSize - i - 1);
      }
      fd.Write(data.data(), kBlockSize);
    }
  }

  void SetUp() override {
    FillPartition(old_part_.path(), true);
    FillPartition(new_part_.path(), false);
    ASSERT_TRUE(boot_control_.Init());
    if (!DynamicPartitionEnabled()) {
      return;
    }
    ASSERT_NO_FATAL_FAILURE(AddFakePartitionGroup());
    message_loop_.SetAsCurrent();
    // Set official build to false so that hash checks are non-mandatory
    hardware_.SetIsOfficialBuild(false);
    truncate64(blob_file_.path().c_str(), 0);

    update_attempter_android_.set_update_certificates_path(
        test_utils::GetBuildArtifactsPath(kUnittestOTACertsPath));

    // Basic setup to create VABC OTA
    manifest_.set_partial_update(true);
    manifest_.set_minor_version(kPartialUpdateMinorPayloadVersion);
    auto dap_group =
        manifest_.mutable_dynamic_partition_metadata()->add_groups();
    dap_group->set_name("fake_group");
    dap_group->add_partition_names("fake");

    manifest_.mutable_dynamic_partition_metadata()->set_snapshot_enabled(true);
    manifest_.mutable_dynamic_partition_metadata()->set_vabc_enabled(true);
    manifest_.mutable_dynamic_partition_metadata()->set_cow_version(
        android::snapshot::kCowVersionMajor);
  }

  void TearDown() override {
    if (!builder_ || !DynamicPartitionEnabled()) {
      return;
    }
    auto dynamic_control = boot_control_.GetDynamicPartitionControl();
    if (dynamic_control) {
      dynamic_control->UnmapAllPartitions();
      dynamic_control->ResetUpdate(&prefs_);
    }
    RemoveFakePartitionGroup();
    ExportPartitionTable();
  }

  void CreateFakePartition() {
    // Create a fake partition for testing purposes
    auto partition_a = builder_->AddPartition(
        boot_control_.GetCurrentSlot() == 0 ? "fake_a" : "fake_b",
        "fake_group",
        0);
    ASSERT_NE(partition_a, nullptr);
    ASSERT_TRUE(builder_->ResizePartition(partition_a, kFakePartitionSize));
    ExportPartitionTable();
    std::string source_part;
    ASSERT_TRUE(
        dynamic_control_->GetPartitionDevice("fake",
                                             boot_control_.GetCurrentSlot(),
                                             boot_control_.GetCurrentSlot(),
                                             &source_part));
    int out_fd = open(source_part.c_str(), O_RDWR);
    ScopedFdCloser closer{&out_fd};
    ASSERT_GE(out_fd, 0) << android::base::ErrnoNumberAsString(errno);
    ASSERT_TRUE(utils::SendFile(out_fd, old_part_.fd(), kFakePartitionSize));
  }

  void SendStatusUpdate(
      const update_engine::UpdateEngineStatus& update_engine_status) override {
    LOG(INFO) << UpdateStatusToString(update_engine_status.status) << ", "
              << update_engine_status.progress;
  }

  // Called whenever an update attempt is completed.
  void SendPayloadApplicationComplete(ErrorCode error_code) override {
    completion_code_ = error_code;
  }

  void ExportPartitionTable() {
    auto metadata = builder_->Export();
    ASSERT_NE(metadata, nullptr);
    android::fs_mgr::UpdatePartitionTable(
        super_device_, *metadata, boot_control_.GetCurrentSlot());
  }

 public:
  bool DynamicPartitionEnabled() {
    auto dynamic_control = boot_control_.GetDynamicPartitionControl();
    return dynamic_control &&
           dynamic_control->GetDynamicPartitionsFeatureFlag().IsEnabled();
  }
  void AddSignatureInfoToPayload(DeltaArchiveManifest* manifest,
                                 const std::string& private_key_path) {
    size_t total_blob_size = 0;
    for (const auto& part : manifest->partitions()) {
      for (const auto& op : part.operations()) {
        if (!op.has_data_offset())
          continue;
        ASSERT_EQ(total_blob_size, op.data_offset())
            << "Ops not ordered by blob isze";
        total_blob_size += op.data_length();
      }
    }
    // Signatures appear at the end of the blobs. Note the offset in the
    // |manifest_|.
    uint64_t signature_blob_length = 0;
    if (!private_key_path.empty()) {
      ASSERT_TRUE(PayloadSigner::SignatureBlobLength({private_key_path},
                                                     &signature_blob_length));
      PayloadSigner::AddSignatureToManifest(
          total_blob_size, signature_blob_length, manifest);
    }
  }

  // Generate blob data according to ops specified in the manifest.
  // Also update |new_part_|'s content to match expectation of ops.
  void HydratePayload(DeltaArchiveManifest* manifest) {
    for (auto& partition : *manifest->mutable_partitions()) {
      for (auto& op : *partition.mutable_operations()) {
        if (op.type() == InstallOperation::REPLACE) {
          ASSERT_GE(lseek64(blob_file_.fd(), op.data_offset(), SEEK_SET), 0);
          ASSERT_TRUE(utils::SendFile(
              new_part_.fd(), blob_file_.fd(), op.data_length()));
        } else if (op.type() == InstallOperation::BROTLI_BSDIFF) {
          brillo::Blob old_data;
          ASSERT_TRUE(utils::ReadExtents(
              old_part_.path(), op.src_extents(), &old_data, kBlockSize))
              << "Failed to read source data: "
              << android::base::ErrnoNumberAsString(errno);
          brillo::Blob new_data;
          ASSERT_TRUE(utils::ReadExtents(
              new_part_.path(), op.dst_extents(), &new_data, kBlockSize))
              << "Failed to read target data: "
              << android::base::ErrnoNumberAsString(errno);
          ScopedTempFile patch_file{"bspatch.XXXXXX", true};
          ASSERT_EQ(bsdiff::bsdiff(old_data.data(),
                                   old_data.size(),
                                   new_data.data(),
                                   new_data.size(),
                                   patch_file.path().c_str(),
                                   nullptr),
                    0);
          op.set_data_length(utils::FileSize(patch_file.fd()));
          const auto offset = lseek64(blob_file_.fd(), 0, SEEK_CUR);
          ASSERT_GE(offset, 0);
          op.set_data_offset(offset);
          brillo::Blob src_data_hash;
          HashCalculator::RawHashOfData(old_data, &src_data_hash);
          op.set_src_sha256_hash(src_data_hash.data(), src_data_hash.size());
          utils::SendFile(blob_file_.fd(), patch_file.fd(), op.data_length());

        } else if (op.type() == InstallOperation::ZERO) {
          auto zero = utils::GetReadonlyZeroString(
              utils::BlocksInExtents(op.dst_extents()) * kBlockSize);
          for (const auto& ext : op.dst_extents()) {
            utils::PWriteAll(new_part_.fd(),
                             zero.data(),
                             ext.num_blocks() * kBlockSize,
                             ext.start_block() * kBlockSize);
          }
        } else if (op.type() == InstallOperation::SOURCE_COPY) {
          brillo::Blob data;
          ASSERT_TRUE(utils::ReadExtents(
              old_part_.path(), op.src_extents(), &data, kBlockSize));
          ASSERT_TRUE(utils::WriteExtents(
              new_part_.path(), op.dst_extents(), data, kBlockSize));
        } else {
          FAIL() << "Unsupported install op type: " << op.type();
        }
      }
    }
  }

  void ApplyPayload(DeltaArchiveManifest* manifest) {
    ASSERT_FALSE(manifest->partitions().empty());
    ASSERT_NO_FATAL_FAILURE(HydratePayload(manifest));
    const auto private_key_path =
        test_utils::GetBuildArtifactsPath(kUnittestPrivateKeyPath);
    ASSERT_NO_FATAL_FAILURE(
        AddSignatureInfoToPayload(manifest, private_key_path));

    brillo::Blob hash;
    HashCalculator::RawHashOfFile(new_part_.path(), &hash);
    auto partition = &manifest->mutable_partitions()->at(0);
    partition->mutable_new_partition_info()->set_size(kFakePartitionSize);
    partition->mutable_new_partition_info()->set_hash(hash.data(), hash.size());
    const bool source_exist =
        std::any_of(partition->operations().begin(),
                    partition->operations().end(),
                    [](const auto& op) { return op.src_extents_size() > 0; });
    if (source_exist) {
      HashCalculator::RawHashOfFile(old_part_.path(), &hash);
      partition->mutable_old_partition_info()->set_size(kFakePartitionSize);
      partition->mutable_old_partition_info()->set_hash(hash.data(),
                                                        hash.size());
      // Only create fake partition if the update is incremental
      LOG(INFO) << "Creating fake partition";
      ASSERT_NO_FATAL_FAILURE(CreateFakePartition());
    }
    uint64_t metadata_size = 0;
    ASSERT_TRUE(PayloadFile::WritePayload(payload_file_.path(),
                                          blob_file_.path(),
                                          private_key_path,
                                          kBrilloMajorPayloadVersion,
                                          *manifest,
                                          &metadata_size));
    LOG(INFO) << "Signature offset: " << manifest->signatures_offset()
              << ", Signature size: " << manifest->signatures_size();
    Error error;
    HashCalculator::RawHashOfFile(payload_file_.path(), &hash);
    daemon_state_.AddObserver(this);
    ASSERT_TRUE(update_attempter_android_.ApplyPayload(
        "file://" + payload_file_.path(),
        0,
        utils::FileSize(payload_file_.path()),
        {kPayloadPropertyMetadataSize + ("=" + std::to_string(metadata_size)),
         kPayloadPropertyFileHash +
             ("=" + brillo::data_encoding::Base64Encode(hash))},
        &error));
    brillo::MessageLoop::current()->Run();
    if (error.error_code != ErrorCode::kSuccess) {
      LOG(ERROR) << error.message;
    }
    ASSERT_EQ(error.error_code, ErrorCode::kSuccess) << error.message;
    ASSERT_EQ(completion_code_, ErrorCode::kSuccess);
  }

  // Compare contents of fake_b partition to |new_part_| and print difference
  void DumpTargetPartitionDiff() {
    dynamic_control_->MapAllPartitions();
    auto partition_device =
        dynamic_control_->GetPartitionDevice("fake",
                                             1 - boot_control_.GetCurrentSlot(),
                                             boot_control_.GetCurrentSlot(),
                                             false);
    if (!partition_device.has_value()) {
      LOG(INFO) << "Failed to get target fake partition, skip diff report";
      return;
    }

    EintrSafeFileDescriptor actual_part;
    CHECK(actual_part.Open(partition_device->readonly_device_path.c_str(),
                           O_RDONLY));
    EintrSafeFileDescriptor expected_part;
    CHECK(expected_part.Open(new_part_.path().c_str(), O_RDONLY));

    std::array<uint8_t, kBlockSize> actual_block;
    std::array<uint8_t, kBlockSize> expected_block;
    for (size_t i = 0; i < kFakePartitionSize / kBlockSize; i++) {
      actual_part.Read(actual_block.data(), actual_block.size());
      expected_part.Read(expected_block.data(), expected_block.size());
      if (actual_block != expected_block) {
        LOG(ERROR) << "Block " << i << " differs.";
      }
    }
  }
  // use 25MB max to avoid super not having enough space
  static constexpr size_t kFakePartitionSize = 1024 * 1024 * 25;
  static_assert(kFakePartitionSize % kBlockSize == 0);
  BootControlAndroid boot_control_;

  std::unique_ptr<android::fs_mgr::MetadataBuilder> builder_;
  std::string super_device_;
  FakeHardware hardware_;
  ScopedTempFile payload_file_;
  ScopedTempFile blob_file_{"blob_file.XXXXXX", true};
  // Contains expected data for old partition. Will be copied to fake_a on test
  // start.
  ScopedTempFile old_part_{"old_part.XXXXXX", true, kFakePartitionSize};
  // Expected data for new partition, will be compared against actual data in
  // fake_b once test finishes.
  ScopedTempFile new_part_{"new_part.XXXXXX", true, kFakePartitionSize};
  DaemonStateAndroid daemon_state_;
  MemoryPrefs prefs_;
  ErrorCode completion_code_;
  DynamicPartitionControlAndroid* dynamic_control_{nullptr};
  brillo::FakeMessageLoop message_loop_{nullptr};

  DeltaArchiveManifest manifest_;
  UpdateAttempterAndroid update_attempter_android_{
      &daemon_state_, &prefs_, &boot_control_, &hardware_, nullptr};
};

namespace {

TEST_F(UpdateAttempterAndroidIntegrationTest, NewPartitionTest) {
  if (!DynamicPartitionEnabled()) {
    return;
  }
  auto partition = manifest_.add_partitions();
  partition->set_partition_name("fake");
  partition->set_estimate_cow_size(kFakePartitionSize);
  {
    auto op = partition->add_operations();
    op->set_type(InstallOperation::REPLACE);
    *op->add_dst_extents() = ExtentForRange(0, 1);
    op->set_data_offset(0);
    op->set_data_length(kBlockSize);
    truncate(blob_file_.path().c_str(), kBlockSize);
  }
  {
    auto op = partition->add_operations();
    op->set_type(InstallOperation::ZERO);
    *op->add_dst_extents() =
        ExtentForRange(1, kFakePartitionSize / kBlockSize - 1);
  }

  ApplyPayload(&manifest_);
  if (completion_code_ == ErrorCode::kNewRootfsVerificationError) {
    DumpTargetPartitionDiff();
  }
}

TEST_F(UpdateAttempterAndroidIntegrationTest, XorOpsTest) {
  if (!DynamicPartitionEnabled()) {
    return;
  }
  auto partition = manifest_.add_partitions();
  partition->set_partition_name("fake");
  partition->set_estimate_cow_size(kFakePartitionSize);
  {
    auto op = partition->add_operations();
    op->set_type(InstallOperation::BROTLI_BSDIFF);
    *op->add_src_extents() = ExtentForRange(0, 10);
    *op->add_dst_extents() = ExtentForRange(0, 10);
  }
  {
    auto op = partition->add_operations();
    op->set_type(InstallOperation::BROTLI_BSDIFF);
    *op->add_src_extents() = ExtentForRange(10, 10);
    *op->add_dst_extents() = ExtentForRange(10, 10);
  }
  {
    auto op = partition->add_operations();
    op->set_type(InstallOperation::SOURCE_COPY);
    *op->add_src_extents() =
        ExtentForRange(20, kFakePartitionSize / kBlockSize - 20);
    *op->add_dst_extents() =
        ExtentForRange(20, kFakePartitionSize / kBlockSize - 20);
  }
  {
    auto op = partition->add_merge_operations();
    op->set_type(CowMergeOperation::COW_XOR);
    op->set_src_offset(123);
    *op->mutable_src_extent() = ExtentForRange(2, 8);
    *op->mutable_dst_extent() = ExtentForRange(0, 8);
  }
  {
    auto op = partition->add_merge_operations();
    op->set_type(CowMergeOperation::COW_XOR);
    op->set_src_offset(456);
    *op->mutable_src_extent() = ExtentForRange(10, 8);
    *op->mutable_dst_extent() = ExtentForRange(12, 8);
  }

  ApplyPayload(&manifest_);
  if (completion_code_ == ErrorCode::kNewRootfsVerificationError) {
    DumpTargetPartitionDiff();
  }
}

}  // namespace

}  // namespace chromeos_update_engine
