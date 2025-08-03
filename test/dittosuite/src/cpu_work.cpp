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

#include <ditto/cpu_work.h>

#include <ditto/logger.h>

namespace dittosuite {

CpuWork::CpuWork(const std::string& name, const Params& params) : Instruction(name, params) {}

CpuWorkUtilization::CpuWorkUtilization(const Params& params, double utilization)
    : CpuWork(kName, params), utilization_(utilization) {
  if (utilization < 0 || utilization > 1) {
    LOGF("Utilization value must be in the range [0,1]");
  }
  if (params.period_us_ <= 0) {
    LOGF("The period of the instruction must be greater than 0");
  }
}

void CpuWorkUtilization::RunSingle() {
  timespec time_now, time_end, work_time;
  work_time = MicrosToTimespec(period_us_ * utilization_);

  if (clock_gettime(CLOCK_THREAD_CPUTIME_ID, &time_now)) {
    LOGF("Error getting current time");
  }

  time_end = time_now + work_time;

  do {
    if (clock_gettime(CLOCK_THREAD_CPUTIME_ID, &time_now)) {
      LOGF("Error getting current time");
    }
  } while (time_now < time_end);
}

CpuWorkCycles::CpuWorkCycles(const Params& params, uint64_t cycles)
    : CpuWork(kName, params), cycles_(cycles) {}

void CpuWorkCycles::RunSingle() {
  volatile int target = -1;

  for (uint64_t counter = 0; counter < cycles_; ++counter) {
    target = ~target;
  }
}

}  // namespace dittosuite
