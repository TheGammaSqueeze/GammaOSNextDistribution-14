//
// Copyright (C) 2018 The Android Open Source Project
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

#include "update_engine/payload_consumer/verity_writer_android.h"

#include <fcntl.h>

#include <algorithm>
#include <memory>
#include <utility>

#include <base/logging.h>
#include <base/posix/eintr_wrapper.h>
#include <fec/ecc.h>
extern "C" {
#include <fec.h>
}

#include "update_engine/common/utils.h"
#include "update_engine/payload_consumer/cached_file_descriptor.h"
#include "update_engine/payload_consumer/file_descriptor.h"

namespace chromeos_update_engine {

bool IncrementalEncodeFEC::Init(const uint64_t _data_offset,
                                const uint64_t _data_size,
                                const uint64_t _fec_offset,
                                const uint64_t _fec_size,
                                const uint64_t _fec_roots,
                                const uint64_t _block_size,
                                const bool _verify_mode) {
  current_step_ = EncodeFECStep::kInitFDStep;
  data_offset_ = _data_offset;
  data_size_ = _data_size;
  fec_offset_ = _fec_offset;
  fec_size_ = _fec_size;
  fec_roots_ = _fec_roots;
  block_size_ = _block_size;
  verify_mode_ = _verify_mode;
  current_round_ = 0;
  // This is the N in RS(M, N), which is the number of bytes for each rs block.
  rs_n_ = FEC_RSM - fec_roots_;
  rs_char_.reset(init_rs_char(FEC_PARAMS(fec_roots_)));
  rs_blocks_.resize(block_size_ * rs_n_);
  buffer_.resize(block_size_, 0);
  fec_.resize(block_size_ * fec_roots_);
  fec_read_.resize(fec_.size());
  TEST_AND_RETURN_FALSE(data_size_ % block_size_ == 0);
  TEST_AND_RETURN_FALSE(fec_roots_ >= 0 && fec_roots_ < FEC_RSM);

  num_rounds_ = utils::DivRoundUp(data_size_ / block_size_, rs_n_);
  TEST_AND_RETURN_FALSE(num_rounds_ * fec_roots_ * block_size_ == fec_size_);
  TEST_AND_RETURN_FALSE(rs_char_ != nullptr);
  return true;
}

bool IncrementalEncodeFEC::Compute(FileDescriptor* _read_fd,
                                   FileDescriptor* _write_fd) {
  if (current_step_ == EncodeFECStep::kInitFDStep) {
    read_fd_ = _read_fd;
    write_fd_ = _write_fd;
    cache_fd_.SetFD(write_fd_);
    write_fd_ = &cache_fd_;
  } else if (current_step_ == EncodeFECStep::kEncodeRoundStep) {
    // Encodes |block_size| number of rs blocks each round so that we can read
    // one block each time instead of 1 byte to increase random read
    // performance. This uses about 1 MiB memory for 4K block size.
    for (size_t j = 0; j < rs_n_; j++) {
      uint64_t offset = fec_ecc_interleave(
          current_round_ * rs_n_ * block_size_ + j, rs_n_, num_rounds_);
      // Don't read past |data_size|, treat them as 0.
      if (offset >= data_size_) {
        std::fill(buffer_.begin(), buffer_.end(), 0);
      } else {
        ssize_t bytes_read = 0;
        TEST_AND_RETURN_FALSE(utils::PReadAll(read_fd_,
                                              buffer_.data(),
                                              buffer_.size(),
                                              data_offset_ + offset,
                                              &bytes_read));
        TEST_AND_RETURN_FALSE(bytes_read >= 0);
        TEST_AND_RETURN_FALSE(static_cast<size_t>(bytes_read) ==
                              buffer_.size());
      }
      for (size_t k = 0; k < buffer_.size(); k++) {
        rs_blocks_[k * rs_n_ + j] = buffer_[k];
      }
    }
    for (size_t j = 0; j < block_size_; j++) {
      // Encode [j * rs_n_ : (j + 1) * rs_n_) in |rs_blocks| and write
      // |fec_roots| number of parity bytes to |j * fec_roots| in |fec|.
      encode_rs_char(rs_char_.get(),
                     rs_blocks_.data() + j * rs_n_,
                     fec_.data() + j * fec_roots_);
    }

    if (verify_mode_) {
      ssize_t bytes_read = 0;
      TEST_AND_RETURN_FALSE(utils::PReadAll(read_fd_,
                                            fec_read_.data(),
                                            fec_read_.size(),
                                            fec_offset_,
                                            &bytes_read));
      TEST_AND_RETURN_FALSE(bytes_read >= 0);
      TEST_AND_RETURN_FALSE(static_cast<size_t>(bytes_read) ==
                            fec_read_.size());
      TEST_AND_RETURN_FALSE(fec_ == fec_read_);
    } else {
      CHECK(write_fd_);
      write_fd_->Seek(fec_offset_, SEEK_SET);
      if (!utils::WriteAll(write_fd_, fec_.data(), fec_.size())) {
        PLOG(ERROR) << "EncodeFEC write() failed";
        return false;
      }
    }
    fec_offset_ += fec_.size();
    current_round_++;
  } else if (current_step_ == EncodeFECStep::kWriteStep) {
    write_fd_->Flush();
  }
  UpdateState();
  return true;
}
// update the current state of EncodeFEC. Can be changed to have smaller steps
void IncrementalEncodeFEC::UpdateState() {
  if (current_step_ == EncodeFECStep::kInitFDStep) {
    current_step_ = EncodeFECStep::kEncodeRoundStep;
  } else if (current_step_ == EncodeFECStep::kEncodeRoundStep &&
             current_round_ == num_rounds_) {
    current_step_ = EncodeFECStep::kWriteStep;
  } else if (current_step_ == EncodeFECStep::kWriteStep) {
    current_step_ = EncodeFECStep::kComplete;
  }
}

bool IncrementalEncodeFEC::Finished() const {
  return current_step_ == EncodeFECStep::kComplete;
}

double IncrementalEncodeFEC::ReportProgress() const {
  if (num_rounds_ == 0) {
    return 1.0;
  }
  return static_cast<double>(current_round_) / num_rounds_;
}

namespace verity_writer {
std::unique_ptr<VerityWriterInterface> CreateVerityWriter() {
  return std::make_unique<VerityWriterAndroid>();
}
}  // namespace verity_writer

bool VerityWriterAndroid::Init(const InstallPlan::Partition& partition) {
  partition_ = &partition;
  LOG(INFO) << "Initializing Incremental EncodeFEC";
  TEST_AND_RETURN_FALSE(encodeFEC_.Init(partition_->fec_data_offset,
                                        partition_->fec_data_size,
                                        partition_->fec_offset,
                                        partition_->fec_size,
                                        partition_->fec_roots,
                                        partition_->block_size,
                                        false /* verify_mode */));
  hash_tree_written_ = false;
  if (partition_->hash_tree_size != 0) {
    auto hash_function =
        HashTreeBuilder::HashFunction(partition_->hash_tree_algorithm);
    if (hash_function == nullptr) {
      LOG(ERROR) << "Verity hash algorithm not supported: "
                 << partition_->hash_tree_algorithm;
      return false;
    }
    hash_tree_builder_ = std::make_unique<HashTreeBuilder>(
        partition_->block_size, hash_function);
    TEST_AND_RETURN_FALSE(hash_tree_builder_->Initialize(
        partition_->hash_tree_data_size, partition_->hash_tree_salt));
    if (hash_tree_builder_->CalculateSize(partition_->hash_tree_data_size) !=
        partition_->hash_tree_size) {
      LOG(ERROR) << "Verity hash tree size does not match, stored: "
                 << partition_->hash_tree_size << ", calculated: "
                 << hash_tree_builder_->CalculateSize(
                        partition_->hash_tree_data_size);
      return false;
    }
  }
  total_offset_ = 0;
  return true;
}

bool VerityWriterAndroid::Update(const uint64_t offset,
                                 const uint8_t* buffer,
                                 size_t size) {
  if (offset != total_offset_) {
    LOG(ERROR) << "Sequential read expected, expected to read at: "
               << total_offset_ << " actual read occurs at: " << offset;
    return false;
  }
  if (partition_->hash_tree_size != 0) {
    const uint64_t hash_tree_data_end =
        partition_->hash_tree_data_offset + partition_->hash_tree_data_size;
    const uint64_t start_offset =
        std::max(offset, partition_->hash_tree_data_offset);
    if (offset + size > hash_tree_data_end) {
      LOG(WARNING)
          << "Reading past hash_tree_data_end, something is probably "
             "wrong, might cause incorrect hash of partitions. offset: "
          << offset << " size: " << size
          << " hash_tree_data_end: " << hash_tree_data_end;
    }
    const uint64_t end_offset = std::min(offset + size, hash_tree_data_end);
    if (start_offset < end_offset) {
      TEST_AND_RETURN_FALSE(hash_tree_builder_->Update(
          buffer + start_offset - offset, end_offset - start_offset));

      if (end_offset == hash_tree_data_end) {
        LOG(INFO)
            << "Read everything before hash tree. Ready to write hash tree.";
      }
    }
  }
  total_offset_ += size;

  return true;
}
bool VerityWriterAndroid::Finalize(FileDescriptor* read_fd,
                                   FileDescriptor* write_fd) {
  const auto hash_tree_data_end =
      partition_->hash_tree_data_offset + partition_->hash_tree_data_size;
  if (total_offset_ < hash_tree_data_end) {
    LOG(ERROR) << "Read up to " << total_offset_
               << " when we are expecting to read everything "
                  "before "
               << hash_tree_data_end;
    return false;
  }
  // All hash tree data blocks has been hashed, write hash tree to disk.
  LOG(INFO) << "Writing verity hash tree to "
            << partition_->readonly_target_path;
  if (hash_tree_builder_) {
    TEST_AND_RETURN_FALSE(hash_tree_builder_->BuildHashTree());
    TEST_AND_RETURN_FALSE_ERRNO(
        write_fd->Seek(partition_->hash_tree_offset, SEEK_SET));
    auto success =
        hash_tree_builder_->WriteHashTree([write_fd](auto data, auto size) {
          return utils::WriteAll(write_fd, data, size);
        });
    // hashtree builder already prints error messages.
    TEST_AND_RETURN_FALSE(success);
    hash_tree_builder_.reset();
  }
  if (partition_->fec_size != 0) {
    LOG(INFO) << "Writing verity FEC to " << partition_->readonly_target_path;
    TEST_AND_RETURN_FALSE(EncodeFEC(read_fd,
                                    write_fd,
                                    partition_->fec_data_offset,
                                    partition_->fec_data_size,
                                    partition_->fec_offset,
                                    partition_->fec_size,
                                    partition_->fec_roots,
                                    partition_->block_size,
                                    false /* verify_mode */));
  }
  return true;
}

bool VerityWriterAndroid::IncrementalFinalize(FileDescriptor* read_fd,
                                              FileDescriptor* write_fd) {
  if (!hash_tree_written_) {
    LOG(INFO) << "Completing prework in Finalize";
    const auto hash_tree_data_end =
        partition_->hash_tree_data_offset + partition_->hash_tree_data_size;
    if (total_offset_ < hash_tree_data_end) {
      LOG(ERROR) << "Read up to " << total_offset_
                 << " when we are expecting to read everything "
                    "before "
                 << hash_tree_data_end;
      return false;
    }
    // All hash tree data blocks has been hashed, write hash tree to disk.
    LOG(INFO) << "Writing verity hash tree to "
              << partition_->readonly_target_path;
    if (hash_tree_builder_) {
      TEST_AND_RETURN_FALSE(hash_tree_builder_->BuildHashTree());
      TEST_AND_RETURN_FALSE_ERRNO(
          write_fd->Seek(partition_->hash_tree_offset, SEEK_SET));
      auto success =
          hash_tree_builder_->WriteHashTree([write_fd](auto data, auto size) {
            return utils::WriteAll(write_fd, data, size);
          });
      // hashtree builder already prints error messages.
      TEST_AND_RETURN_FALSE(success);
      hash_tree_builder_.reset();
    }
    hash_tree_written_ = true;
    if (partition_->fec_size != 0) {
      LOG(INFO) << "Writing verity FEC to " << partition_->readonly_target_path;
    }
  }
  if (partition_->fec_size != 0) {
    TEST_AND_RETURN_FALSE(encodeFEC_.Compute(read_fd, write_fd));
  }
  return true;
}
bool VerityWriterAndroid::FECFinished() const {
  if ((encodeFEC_.Finished() || partition_->fec_size == 0) &&
      hash_tree_written_) {
    return true;
  }
  return false;
}

double VerityWriterAndroid::GetProgress() {
  return encodeFEC_.ReportProgress();
}

bool VerityWriterAndroid::EncodeFEC(FileDescriptor* read_fd,
                                    FileDescriptor* write_fd,
                                    uint64_t data_offset,
                                    uint64_t data_size,
                                    uint64_t fec_offset,
                                    uint64_t fec_size,
                                    uint32_t fec_roots,
                                    uint32_t block_size,
                                    bool verify_mode) {
  TEST_AND_RETURN_FALSE(data_size % block_size == 0);
  TEST_AND_RETURN_FALSE(fec_roots >= 0 && fec_roots < FEC_RSM);
  // This is the N in RS(M, N), which is the number of bytes for each rs
  // block.
  size_t rs_n = FEC_RSM - fec_roots;
  uint64_t rounds = utils::DivRoundUp(data_size / block_size, rs_n);
  TEST_AND_RETURN_FALSE(rounds * fec_roots * block_size == fec_size);

  std::unique_ptr<void, decltype(&free_rs_char)> rs_char(
      init_rs_char(FEC_PARAMS(fec_roots)), &free_rs_char);
  TEST_AND_RETURN_FALSE(rs_char != nullptr);
  // Cache at most 1MB of fec data, in VABC, we need to re-open fd if we
  // perform a read() operation after write(). So reduce the number of writes
  // can save unnecessary re-opens.
  UnownedCachedFileDescriptor cache_fd(write_fd, 1 * (1 << 20));
  write_fd = &cache_fd;

  for (size_t i = 0; i < rounds; i++) {
    // Encodes |block_size| number of rs blocks each round so that we can read
    // one block each time instead of 1 byte to increase random read
    // performance. This uses about 1 MiB memory for 4K block size.
    brillo::Blob rs_blocks(block_size * rs_n);
    for (size_t j = 0; j < rs_n; j++) {
      brillo::Blob buffer(block_size, 0);
      uint64_t offset =
          fec_ecc_interleave(i * rs_n * block_size + j, rs_n, rounds);
      // Don't read past |data_size|, treat them as 0.
      if (offset < data_size) {
        ssize_t bytes_read = 0;
        TEST_AND_RETURN_FALSE(utils::PReadAll(read_fd,
                                              buffer.data(),
                                              buffer.size(),
                                              data_offset + offset,
                                              &bytes_read));
        TEST_AND_RETURN_FALSE(bytes_read >= 0);
        TEST_AND_RETURN_FALSE(static_cast<size_t>(bytes_read) == buffer.size());
      }
      for (size_t k = 0; k < buffer.size(); k++) {
        rs_blocks[k * rs_n + j] = buffer[k];
      }
    }
    brillo::Blob fec(block_size * fec_roots);
    for (size_t j = 0; j < block_size; j++) {
      // Encode [j * rs_n : (j + 1) * rs_n) in |rs_blocks| and write
      // |fec_roots| number of parity bytes to |j * fec_roots| in |fec|.
      encode_rs_char(rs_char.get(),
                     rs_blocks.data() + j * rs_n,
                     fec.data() + j * fec_roots);
    }

    if (verify_mode) {
      brillo::Blob fec_read(fec.size());
      ssize_t bytes_read = 0;
      TEST_AND_RETURN_FALSE(utils::PReadAll(
          read_fd, fec_read.data(), fec_read.size(), fec_offset, &bytes_read));
      TEST_AND_RETURN_FALSE(bytes_read >= 0);
      TEST_AND_RETURN_FALSE(static_cast<size_t>(bytes_read) == fec_read.size());
      TEST_AND_RETURN_FALSE(fec == fec_read);
    } else {
      CHECK(write_fd);
      write_fd->Seek(fec_offset, SEEK_SET);
      if (!utils::WriteAll(write_fd, fec.data(), fec.size())) {
        PLOG(ERROR) << "EncodeFEC write() failed";
        return false;
      }
    }
    fec_offset += fec.size();
  }
  write_fd->Flush();
  return true;
}

bool VerityWriterAndroid::EncodeFEC(const std::string& path,
                                    uint64_t data_offset,
                                    uint64_t data_size,
                                    uint64_t fec_offset,
                                    uint64_t fec_size,
                                    uint32_t fec_roots,
                                    uint32_t block_size,
                                    bool verify_mode) {
  EintrSafeFileDescriptor fd;
  TEST_AND_RETURN_FALSE(fd.Open(path.c_str(), verify_mode ? O_RDONLY : O_RDWR));
  return EncodeFEC(&fd,
                   &fd,
                   data_offset,
                   data_size,
                   fec_offset,
                   fec_size,
                   fec_roots,
                   block_size,
                   verify_mode);
}
}  // namespace chromeos_update_engine
