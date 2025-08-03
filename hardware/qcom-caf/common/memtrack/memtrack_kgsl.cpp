/*
 * Copyright (c) 2021 The Linux Foundation. All rights reserved.
 * Not a contribution
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Changes from Qualcomm Innovation Center are provided under the following
 * license:
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */
#include "memtrack_kgsl.h"
#include <errno.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <log/log.h>

#define DEBUG 0

namespace vendor {
namespace qti {
namespace hardware {
namespace memtrack {

int getKgslMemory(int pid, MemtrackType type,
                  std::vector<MemtrackRecord> *records) {
  char syspath[128];
  size_t accounted_size = 0;
  size_t unaccounted_size = 0;
  FILE *fp;
  int ret;
  if (type == MemtrackType::GL) {
    snprintf(syspath, sizeof(syspath),
             "/sys/class/kgsl/kgsl/proc/%d/gpumem_mapped", pid);
    fp = fopen(syspath, "r");
    if (fp == NULL)
      return -errno;
    ret = fscanf(fp, "%zu", &accounted_size);
    if (ret != 1) {
      fclose(fp);
      return -EINVAL;
    }
    fclose(fp);
    snprintf(syspath, sizeof(syspath),
             "/sys/class/kgsl/kgsl/proc/%d/gpumem_unmapped", pid);
    fp = fopen(syspath, "r");
    if (fp == NULL) {
      return -errno;
    }
    ret = fscanf(fp, "%zu", &unaccounted_size);
    if (ret != 1) {
      fclose(fp);
      return -EINVAL;
    }
    fclose(fp);

    if (accounted_size > 0) {
        MemtrackRecord rec;
        rec.flags = MemtrackRecord::FLAG_SMAPS_ACCOUNTED |
                    MemtrackRecord::FLAG_NONSECURE |
                    MemtrackRecord::FLAG_PRIVATE;
        rec.sizeInBytes = accounted_size;
        records->push_back(rec);
    }
    if (unaccounted_size > 0) {
        MemtrackRecord rec;
        rec.flags = MemtrackRecord::FLAG_SMAPS_UNACCOUNTED |
                    MemtrackRecord::FLAG_NONSECURE |
                    MemtrackRecord::FLAG_PRIVATE;
        rec.sizeInBytes = unaccounted_size;
        records->push_back(rec);
    }



  } else if (type == MemtrackType::GRAPHICS) {
    snprintf(syspath, sizeof(syspath),
             "/sys/class/kgsl/kgsl/proc/%d/imported_mem", pid);
    fp = fopen(syspath, "r");
    if (fp == NULL)
      return -errno;
    ret = fscanf(fp, "%zu", &unaccounted_size);
    if (ret != 1) {
      fclose(fp);
      return -EINVAL;
    }
    fclose(fp);
    if (unaccounted_size > 0) {
        MemtrackRecord rec;
        rec.flags = MemtrackRecord::FLAG_SMAPS_UNACCOUNTED |
                    MemtrackRecord::FLAG_NONSECURE |
                    MemtrackRecord::FLAG_PRIVATE;
        rec.sizeInBytes = unaccounted_size;
        records->push_back(rec);
    }
  }
  if (records && !records->empty()) {
    for (auto &v : *records) {
      ALOGI_IF(DEBUG, "getKgslMemory: pid: %d type: %s flags: 0x%x size: %ld bytes", pid,
            aidl::android::hardware::memtrack::toString(type).c_str(), v.flags,
            v.sizeInBytes);
    }
  }
  return 0;
}

} // namespace memtrack
} // namespace hardware
} // namespace qti
} // namespace vendor
