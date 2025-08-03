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

#ifndef UPDATE_ENGINE_INSTALL_OPERATION_EXECUTOR_H
#define UPDATE_ENGINE_INSTALL_OPERATION_EXECUTOR_H

#include <memory>

#include "update_engine/payload_consumer/extent_writer.h"
#include "update_engine/payload_consumer/file_descriptor.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {

class InstallOperationExecutor {
 public:
  explicit InstallOperationExecutor(size_t block_size)
      : block_size_(block_size) {}

  // data should point to the memory of operation.data_length() bytes
  bool ExecuteReplaceOperation(const InstallOperation& operation,
                               std::unique_ptr<ExtentWriter> writer,
                               const void* data);
  bool ExecuteZeroOrDiscardOperation(const InstallOperation& operation,
                                     std::unique_ptr<ExtentWriter> writer);
  bool ExecuteSourceCopyOperation(const InstallOperation& operation,
                                  std::unique_ptr<ExtentWriter> writer,
                                  FileDescriptorPtr source_fd);

  bool ExecuteDiffOperation(const InstallOperation& operation,
                            std::unique_ptr<ExtentWriter> writer,
                            FileDescriptorPtr source_fd,
                            const void* data,
                            size_t count);

 private:
  bool ExecuteSourceBsdiffOperation(const InstallOperation& operation,
                                    std::unique_ptr<ExtentWriter> writer,
                                    FileDescriptorPtr source_fd,
                                    const void* data,
                                    size_t count);
  bool ExecutePuffDiffOperation(const InstallOperation& operation,
                                std::unique_ptr<ExtentWriter> writer,
                                FileDescriptorPtr source_fd,
                                const void* data,
                                size_t count);
  bool ExecuteZucchiniOperation(const InstallOperation& operation,
                                std::unique_ptr<ExtentWriter> writer,
                                FileDescriptorPtr source_fd,
                                const void* data,
                                size_t count);
  bool ExecuteLz4diffOperation(const InstallOperation& operation,
                               std::unique_ptr<ExtentWriter> writer,
                               FileDescriptorPtr source_fd,
                               const void* data,
                               size_t count);

  size_t block_size_;
};

}  // namespace chromeos_update_engine

#endif
