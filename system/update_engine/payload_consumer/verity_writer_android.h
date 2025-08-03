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

#ifndef UPDATE_ENGINE_PAYLOAD_CONSUMER_VERITY_WRITER_ANDROID_H_
#define UPDATE_ENGINE_PAYLOAD_CONSUMER_VERITY_WRITER_ANDROID_H_

#include <memory>
#include <string>

#include <verity/hash_tree_builder.h>
#include <base/logging.h>
#include <base/posix/eintr_wrapper.h>
#include <fec/ecc.h>
extern "C" {
#include <fec.h>
}

#include "payload_consumer/file_descriptor.h"
#include "update_engine/payload_consumer/cached_file_descriptor.h"
#include "update_engine/payload_consumer/verity_writer_interface.h"

namespace chromeos_update_engine {
enum class EncodeFECStep {
  kInitFDStep,
  kEncodeRoundStep,
  kWriteStep,
  kComplete
};
class IncrementalEncodeFEC {
 public:
  IncrementalEncodeFEC()
      : rs_char_(nullptr, &free_rs_char), cache_fd_(nullptr, 1 * (1 << 20)) {}
  // Initialize all member variables needed to performe FEC Computation
  bool Init(const uint64_t _data_offset,
            const uint64_t _data_size,
            const uint64_t _fec_offset,
            const uint64_t _fec_size,
            const uint64_t _fec_roots,
            const uint64_t _block_size,
            const bool _verify_mode);
  bool Compute(FileDescriptor* _read_fd, FileDescriptor* _write_fd);
  void UpdateState();
  bool Finished() const;
  void Reset();
  double ReportProgress() const;

 private:
  brillo::Blob rs_blocks_;
  brillo::Blob buffer_;
  brillo::Blob fec_;
  brillo::Blob fec_read_;
  EncodeFECStep current_step_;
  size_t current_round_;
  size_t num_rounds_;
  FileDescriptor* read_fd_;
  FileDescriptor* write_fd_;
  uint64_t data_offset_;
  uint64_t data_size_;
  uint64_t fec_offset_;
  uint64_t fec_size_;
  uint64_t fec_roots_;
  uint64_t block_size_;
  size_t rs_n_;
  bool verify_mode_;
  std::unique_ptr<void, decltype(&free_rs_char)> rs_char_;
  UnownedCachedFileDescriptor cache_fd_;
};

class VerityWriterAndroid : public VerityWriterInterface {
 public:
  VerityWriterAndroid() = default;
  ~VerityWriterAndroid() override = default;

  bool Init(const InstallPlan::Partition& partition);
  bool Update(uint64_t offset, const uint8_t* buffer, size_t size) override;
  bool Finalize(FileDescriptor* read_fd, FileDescriptor* write_fd) override;
  bool IncrementalFinalize(FileDescriptor* read_fd,
                           FileDescriptor* write_fd) override;
  double GetProgress() override;
  bool FECFinished() const override;
  // Read [data_offset : data_offset + data_size) from |path| and encode FEC
  // data, if |verify_mode|, then compare the encoded FEC with the one in
  // |path|, otherwise write the encoded FEC to |path|. We can't encode as we go
  // in each Update() like hash tree, because for every rs block, its data are
  // spreaded across entire |data_size|, unless we can cache all data in
  // memory, we have to re-read them from disk.
  static bool EncodeFEC(FileDescriptor* read_fd,
                        FileDescriptor* write_fd,
                        uint64_t data_offset,
                        uint64_t data_size,
                        uint64_t fec_offset,
                        uint64_t fec_size,
                        uint32_t fec_roots,
                        uint32_t block_size,
                        bool verify_mode);
  static bool EncodeFEC(const std::string& path,
                        uint64_t data_offset,
                        uint64_t data_size,
                        uint64_t fec_offset,
                        uint64_t fec_size,
                        uint32_t fec_roots,
                        uint32_t block_size,
                        bool verify_mode);

 private:
  // stores the state of EncodeFEC
  IncrementalEncodeFEC encodeFEC_;
  bool hash_tree_written_ = false;
  const InstallPlan::Partition* partition_ = nullptr;

  std::unique_ptr<HashTreeBuilder> hash_tree_builder_;
  uint64_t total_offset_ = 0;
  DISALLOW_COPY_AND_ASSIGN(VerityWriterAndroid);
};

}  // namespace chromeos_update_engine

#endif  // UPDATE_ENGINE_PAYLOAD_CONSUMER_VERITY_WRITER_ANDROID_H_
