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

#include <ditto/invalidate_cache.h>

#include <ditto/logger.h>

namespace dittosuite {

InvalidateCache::InvalidateCache(const Params& params) : Instruction(kName, params) {}

void InvalidateCache::RunSingle() {
  syscall_.Sync();
  std::string cache_file = "/proc/sys/vm/drop_caches";

  int fd = syscall_.Open(cache_file, O_WRONLY, 0);
  if (fd == -1) {
    PLOGF("Cannot open \"" + cache_file + "\", which requires root privileges");
  }

  char data = '3';
  if (syscall_.Write(fd, &data, sizeof(char), 0) == -1) {
    PLOGF("Error while calling write() in InvalidateCache. Make sure to run the benchmark as root");
  }

  if (syscall_.Close(fd) == -1) {
    PLOGF("Error while calling close() in InvalidateCache. Make sure to run the benchmark as root");
  }
}

}  // namespace dittosuite
