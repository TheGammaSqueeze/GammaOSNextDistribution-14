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

#include <ditto/instruction.h>

#include <ditto/shared_variables.h>
#include <ditto/logger.h>
#include <ditto/tracer.h>

namespace dittosuite {


Instruction::Instruction(const std::string& name, const Params& params)
    : name_(name),
      syscall_(params.syscall_),
      repeat_(params.repeat_),
      period_us_(params.period_us_) {}

void Instruction::SetUp() {}

void Instruction::Run() {
  if (period_us_) {
    if (clock_gettime(CLOCK_MONOTONIC, &next_awake_time_)) {
      PLOGF("Unable to get current time");
    }
  }
  for (int i = 0; i < repeat_; i++) {
    SetUpSingle();
    RunSingle();
    TearDownSingle(i == repeat_ - 1);
  }
}

void Instruction::RunSynchronized(pthread_barrier_t* barrier, const MultithreadingParams& params) {
  int ret = pthread_setname_np(pthread_self(), params.name_.c_str());
  if (ret) {
    if (ret == ERANGE) {
      LOGF("Name too long for thread, max 15 chars allowed: " + params.name_);
    } else {
      LOGF("Error setting thread name: " + std::to_string(ret));
    }
  }

  if (params.sched_attr_.IsSet()) {
    params.sched_attr_.Set();
  }
  if (params.sched_affinity_.IsSet()) {
    params.sched_affinity_.Set();
  }

  pthread_barrier_wait(barrier);
  Instruction::Run();
}

std::thread Instruction::SpawnThread(pthread_barrier_t* barrier,
                                     const MultithreadingParams& params) {
  return std::thread([=, this] { RunSynchronized(barrier, params); });
}

void Instruction::TearDown() {}

void Instruction::SetUpSingle() {
  tracer_.Start(name_);
  time_sampler_.MeasureStart();
}

void Instruction::TearDownSingle(bool /*is_last*/) {
  time_sampler_.MeasureEnd();
  tracer_.End(name_);

  if (!period_us_) {
    return;
  }

  next_awake_time_ = next_awake_time_ + MicrosToTimespec(period_us_);
  if (clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &next_awake_time_, nullptr)) {
    PLOGF("Period clock interrupted");
  }
}

std::unique_ptr<Result> Instruction::CollectResults(const std::string& prefix) {
  auto result = std::make_unique<Result>(prefix + name_, repeat_);
  result->AddMeasurement("duration", TimespecToDoubleNanos(time_sampler_.GetSamples()));
  return result;
}

void Instruction::SetAbsolutePathKey(int absolute_path_key) {
  absolute_path_key_ = absolute_path_key;
}

void Instruction::SetArgv(char** argv) {
  argv_ = argv;
}

void Instruction::SetArgc(int argc) {
  argc_ = argc;
}

std::string Instruction::GetAbsolutePath() {
  return std::get<std::string>(SharedVariables::Get(absolute_path_key_));
}

int Instruction::absolute_path_key_;
char** Instruction::argv_;
int Instruction::argc_;

}  // namespace dittosuite
