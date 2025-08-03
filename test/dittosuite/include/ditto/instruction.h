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

#pragma once

#include <pthread.h>

#include <memory>
#include <string>
#include <thread>

#include <ditto/multithreading_utils.h>
#include <ditto/result.h>
#include <ditto/sampler.h>
#include <ditto/syscall.h>
#include <ditto/tracer.h>

namespace dittosuite {

enum class Order { kSequential, kRandom };
enum class Reseeding { kOnce, kEachRoundOfCycles, kEachCycle };

class Instruction {
 public:
  struct Params {
    Params(SyscallInterface& syscall, int repeat = 1, uint64_t period_us = 0)
        : syscall_(syscall), repeat_(repeat), period_us_(period_us) {}
    SyscallInterface& syscall_;
    int repeat_;
    uint64_t period_us_;
  };

  explicit Instruction(const std::string& name, const Params& params);
  virtual ~Instruction() = default;

  virtual void SetUp();
  void Run();
  void RunSynchronized(pthread_barrier_t* barrier, const MultithreadingParams& params);
  std::thread SpawnThread(pthread_barrier_t* barrier, const MultithreadingParams& params);
  virtual void TearDown();

  virtual std::unique_ptr<Result> CollectResults(const std::string& prefix);

  static void SetAbsolutePathKey(int absolute_path_key);
  static void SetArgv(char** argv);
  static void SetArgc(int argc);

 protected:
  virtual void SetUpSingle();
  virtual void RunSingle() = 0;
  /* This function is executed after every RunSingle(). In some cases, for
   * example in the implementation of a producer-consumer, the consumer should
   * know at what time it should stop with its execution, and this can be
   * handled by the producer to send a special message at the last
   * TearDownSingle. The last iteration of TearDownSingle has the `is_last`
   * value set to true, false otherwise. */
  virtual void TearDownSingle(bool is_last);

  std::string GetAbsolutePath();

  static int absolute_path_key_;
  static char **argv_;
  static int argc_;
  std::string name_;
  SyscallInterface& syscall_;
  int repeat_;
  uint64_t period_us_;
  TimeSampler time_sampler_;
  Tracer tracer_;

 private:
  timespec next_awake_time_;
};

}  // namespace dittosuite
