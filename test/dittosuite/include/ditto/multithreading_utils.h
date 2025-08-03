// Copyright (C) 2023 The Android Open Source Project
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

#pragma once

#ifdef __ANDROID__
#include <benchmark.pb.h>
#else
#include "schema/benchmark.pb.h"
#endif

#include <ditto/logger.h>

#include <sys/syscall.h>
#include <unistd.h>

namespace dittosuite {

enum SchedPolicy {
  SchedNormal = 0,
  SchedFifo = 1,
  SchedRr = 2,
  SchedBatch = 3,
  /* SchedIso: reserved but not implemented yet */
  SchedIdle = 5,
  SchedDeadline = 6,
};

struct SchedAttr__ {
  uint32_t size;         /* Size of this structure */
  uint32_t sched_policy; /* Policy (SCHED_*) */
  uint64_t sched_flags;  /* Flags */

  int32_t sched_nice;      /* Nice value (SCHED_OTHER,
                              SCHED_BATCH) */
  uint32_t sched_priority; /* Static priority (SCHED_FIFO,
                              SCHED_RR) */
  /* Remaining fields are for SCHED_DEADLINE */
  uint64_t sched_runtime;
  uint64_t sched_deadline;
  uint64_t sched_period;
};

std::string to_string(const SchedAttr__& attr);

class SchedAttr {
  bool initialized_ = false;
  SchedAttr__ sched_attr_;

 public:
  void Set() const;
  bool IsSet() const;

  SchedAttr& operator=(const dittosuiteproto::SchedAttr& pb);
};

class SchedAffinity {
  bool initialized_ = false;
  uint64_t mask_;

 public:
  void Set() const;
  bool IsSet() const;

  SchedAffinity& operator=(const uint64_t mask);
};


struct MultithreadingParams {
  const std::string name_;
  SchedAttr sched_attr_;
  SchedAffinity sched_affinity_;

  MultithreadingParams(const std::string& name, const SchedAttr& sched_attr,
                       const SchedAffinity &sched_affinity)
      : name_(name), sched_attr_(sched_attr), sched_affinity_(sched_affinity) {}
};

void setproctitle(int argc, char** argv, const char* title);

}  // namespace dittosuite
