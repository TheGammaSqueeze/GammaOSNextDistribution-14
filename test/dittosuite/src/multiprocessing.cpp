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

#include <ditto/multiprocessing.h>

#include <ditto/logger.h>

#include <sys/mman.h>
#include <sys/prctl.h>
#include <sys/types.h>
#include <unistd.h>

namespace dittosuite {

Multiprocessing::Multiprocessing(const Instruction::Params& params,
                                 std::vector<std::unique_ptr<Instruction>> instructions,
                                 std::vector<MultithreadingParams> thread_params)
    : Instruction(kName, params),
      instructions_(std::move(instructions)),
      thread_params_(std::move(thread_params)) {}

void Multiprocessing::SetUpSingle() {
  pthread_barrierattr_t barrier_attr;
  pthread_barrierattr_init(&barrier_attr);
  pthread_barrierattr_setpshared(&barrier_attr, PTHREAD_PROCESS_SHARED);
  barrier_execution_ = static_cast<pthread_barrier_t*>(mmap(nullptr, sizeof(*barrier_execution_),
                                                            PROT_READ | PROT_WRITE,
                                                            MAP_SHARED | MAP_ANONYMOUS, -1, 0));
  if (barrier_execution_ == MAP_FAILED) {
    LOGF("mmap() failed");
  }
  pthread_barrier_init(barrier_execution_, &barrier_attr, instructions_.size());

  barrier_execution_end_ = static_cast<pthread_barrier_t*>(
      mmap(nullptr, sizeof(*barrier_execution_end_), PROT_READ | PROT_WRITE,
           MAP_SHARED | MAP_ANONYMOUS, -1, 0));
  if (barrier_execution_end_ == MAP_FAILED) {
    LOGF("mmap() failed");
  }
  pthread_barrier_init(barrier_execution_end_, &barrier_attr, instructions_.size());

  initialization_mutex_ = static_cast<pthread_mutex_t*>(
      mmap(nullptr, sizeof(*initialization_mutex_), PROT_READ | PROT_WRITE,
           MAP_SHARED | MAP_ANONYMOUS, -1, 0));
  if (initialization_mutex_ == MAP_FAILED) {
    LOGF("mmap() failed");
  }
  pthread_mutexattr_t mutex_attr;
  pthread_mutexattr_init(&mutex_attr);
  pthread_mutexattr_setpshared(&mutex_attr, PTHREAD_PROCESS_SHARED);
  pthread_mutex_init(initialization_mutex_, &mutex_attr);

  pipe_fds_.resize(instructions_.size());
  for (unsigned int i = 0; i < instructions_.size(); i++) {
    if (pipe(pipe_fds_[i].data()) == -1) {
      LOGF("Pipe Failed");
    } else {
      LOGD("Created pipe for: " + std::to_string(i));
    }
  }

  instruction_id_ = 0;
  for (unsigned int i = 0; i < instructions_.size(); i++) {
    // LOGD("Not the last... Can fork");
    pid_t child_pid_ = fork();
    if (child_pid_) {
      // I'm the manager, will continue spawning processes
      is_manager_ = true;
      continue;
    }
    is_manager_ = false;
    instruction_id_ = i;
    LOGD("Child process created: " + std::to_string(instruction_id_) +
         " pid: " + std::to_string(getpid()));

    break;
  }

  // Close the write pipes that do not belong to this process
  for (unsigned int p = 0; p < instructions_.size(); p++) {
    if (is_manager_ || p != instruction_id_) {
      close(pipe_fds_[p][1]);
    }
  }

  if (!is_manager_) {
    pthread_mutex_lock(initialization_mutex_);
    LOGD("Trying to set the name for instruction: " + std::to_string(instruction_id_) +
         "; process: " + std::to_string(getpid()) +
         "; new name: " + thread_params_[instruction_id_].name_);
    setproctitle(argc_, argv_, (thread_params_[instruction_id_].name_.c_str()));

    if (thread_params_[instruction_id_].sched_attr_.IsSet()) {
      thread_params_[instruction_id_].sched_attr_.Set();
    }
    if (thread_params_[instruction_id_].sched_affinity_.IsSet()) {
      thread_params_[instruction_id_].sched_affinity_.Set();
    }

    LOGD("Process initializing instruction: " + std::to_string(instruction_id_) +
         " pid: " + std::to_string(getpid()));
    instructions_[instruction_id_]->SetUp();
    LOGD("Process initializing done, unlocking: " + std::to_string(instruction_id_) +
         " pid: " + std::to_string(getpid()));
    pthread_mutex_unlock(initialization_mutex_);
  }

  Instruction::SetUpSingle();
}

void Multiprocessing::RunSingle() {
  if (!is_manager_) {
    LOGD("Waiting for the barrier... " + std::to_string(getpid()));
    pthread_barrier_wait(barrier_execution_);
    LOGD("Barrier passed! Executing: " + std::to_string(getpid()));
    instructions_[instruction_id_]->Run();
    LOGD("Waiting for all the processes to finish... " + std::to_string(getpid()));
    pthread_barrier_wait(barrier_execution_end_);
    LOGD("All the processes finished... " + std::to_string(getpid()));
  }
}

void Multiprocessing::TearDownSingle(bool is_last) {
  Instruction::TearDownSingle(is_last);

  if (!is_manager_) {
    instructions_[instruction_id_]->TearDown();
  }
}

std::unique_ptr<Result> Multiprocessing::CollectResults(const std::string& prefix) {
  // TODO this was copied from Multithreading and should be adapted with some
  // shared memory solution.
  auto result = std::make_unique<Result>(prefix + name_, repeat_);
  dittosuiteproto::Result result_pb;

  LOGD("Collecting results... " + std::to_string(getpid()));

  result->AddMeasurement("duration", TimespecToDoubleNanos(time_sampler_.GetSamples()));

  if (is_manager_) {
    LOGD("Parent reading... " + std::to_string(getpid()));
    for (unsigned int i = 0; i < instructions_.size(); i++) {
      LOGD("Parent reading from pipe: " + std::to_string(i));
      std::array<char, 100> buffer;
      dittosuiteproto::Result sub_result_pb;
      std::string serialized;

      while (true) {
        ssize_t chars_read = read(pipe_fds_[i][0], buffer.data(), buffer.size());
        if (chars_read == -1) {
          PLOGF("Error reading from pipe");
        } else if (chars_read == 0) {
          // Finished reading from pipe
          LOGD("Finished reading, time to decode the PB");
          if (!sub_result_pb.ParseFromString(serialized)) {
            LOGF("Failed decoding PB from pipe");
          }
          //PrintPb(sub_result_pb);
          result->AddSubResult(Result::FromPb(sub_result_pb));
          break;
        }
        LOGD("Parent received: " + std::to_string(chars_read) + " bytes: \"" +
             std::string(buffer.data()) + "\"");
        serialized.append(buffer.data(), chars_read);
        LOGD("PB so far: \"" + serialized + "\"");
      }
    }
  } else {
    LOGD("Child writing... " + std::to_string(getpid()));
    result->AddSubResult(
        instructions_[instruction_id_]->CollectResults(std::to_string(instruction_id_) + "/"));

    result_pb = result->ToPb();

    std::string serialized;
    // It is safe to pick result_pb.sub_result()[0] because it will be the "multiprocessing"
    // instruction.
    if (!result_pb.sub_result()[0].SerializeToString(&serialized)) {
      LOGF("Failed decoding PB from pipe");
    }
    ssize_t chars_sent = 0;
    while (chars_sent < static_cast<ssize_t>(serialized.size())) {
      ssize_t chars_written = write(pipe_fds_[instruction_id_][1], &serialized.data()[chars_sent],
                                    serialized.size() - chars_sent);
      if (chars_written == -1) {
        PLOGF("Error writing to pipe");
      }
      chars_sent += chars_written;
    }
    LOGD("Child closing pipe: " + std::to_string(getpid()));
    close(pipe_fds_[instruction_id_][1]);
  }

  if (!is_manager_) {
    // Stop every child
    LOGD("Child stopping: " + std::to_string(getpid()));
    exit(0);
  } else {
    pthread_mutex_destroy(initialization_mutex_);
    pthread_barrier_destroy(barrier_execution_);
    pthread_barrier_destroy(barrier_execution_end_);
    LOGD("Parent finished: " + std::to_string(getpid()));
  }
  return result;
}

}  // namespace dittosuite
