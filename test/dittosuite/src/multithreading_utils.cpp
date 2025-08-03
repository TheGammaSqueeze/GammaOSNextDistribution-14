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

#include <ditto/multithreading_utils.h>

#include <sched.h>
#include <unistd.h>

namespace dittosuite {

std::string to_string(const SchedAttr__& attr) {
  std::string ret;

  ret += "size: " + std::to_string(attr.size);
  ret += ", policy: " + std::to_string(attr.sched_policy);
  ret += ", flags: " + std::to_string(attr.sched_flags);
  ret += ", nice: " + std::to_string(attr.sched_nice);
  ret += ", priority: " + std::to_string(attr.sched_priority);
  ret += ", runtime: " + std::to_string(attr.sched_runtime);
  ret += ", deadline: " + std::to_string(attr.sched_deadline);
  ret += ", period: " + std::to_string(attr.sched_period);

  return ret;
}

bool SchedAttr::IsSet() const { return initialized_; }

void SchedAttr::Set() const {
  if (!initialized_) {
    LOGF("Setting uninitialized scheduling attributes");
  }

  LOGD("Setting scheduling policy [" + std::to_string(sched_attr_.sched_policy) +
       "] to thread: " + std::to_string(gettid()));

  int ret = syscall(SYS_sched_setattr, 0 /* self */, &sched_attr_, 0 /* still not implemented */);
  if (ret) {
    PLOGF("Failed setting scheduling attributes \n" + to_string(sched_attr_) + "\n");
  }
}

SchedAttr& SchedAttr::operator=(const dittosuiteproto::SchedAttr& pb) {
  typedef dittosuiteproto::SchedAttr::AttributesCase SchedType;

  sched_attr_.size = sizeof(sched_attr_);

  sched_attr_.sched_flags = pb.flags();

  switch (pb.attributes_case()) {
    case SchedType::kOther:
      switch (pb.other().policy()) {
        case dittosuiteproto::SchedAttr::SchedOther::OTHER:
          sched_attr_.sched_policy = SchedPolicy::SchedNormal;
          break;
        case dittosuiteproto::SchedAttr::SchedOther::BATCH:
          sched_attr_.sched_policy = SchedPolicy::SchedBatch;
          break;
      }
      sched_attr_.sched_nice = pb.other().nice();
      break;
    case SchedType::kRt:
      switch (pb.rt().policy()) {
        case dittosuiteproto::SchedAttr::SchedRt::FIFO:
          sched_attr_.sched_policy = SchedPolicy::SchedFifo;
          break;
        case dittosuiteproto::SchedAttr::SchedRt::RR:
          sched_attr_.sched_policy = SchedPolicy::SchedRr;
          break;
      }
      if (pb.rt().priority() < 1 || pb.rt().priority() > 99) {
        LOGF("Scheduling priority should be in the range [1, 99]");
      }
      sched_attr_.sched_priority = pb.rt().priority();
      break;
    case SchedType::kDeadline:
      sched_attr_.sched_policy = SchedPolicy::SchedDeadline;
      sched_attr_.sched_runtime = pb.deadline().runtime();
      sched_attr_.sched_deadline = pb.deadline().deadline();
      sched_attr_.sched_period = pb.deadline().period();
      break;
    case SchedType::ATTRIBUTES_NOT_SET:
      LOGF("Missing scheduling attribute");
      break;
  }

  initialized_ = true;

  return *this;
}

void SchedAffinity::Set() const {
  if (!initialized_) {
    LOGF("Setting uninitialized affinity attributes");
  }

  LOGD("Setting affinity mask [" + std::to_string(mask_) +
       "] to thread: " + std::to_string(gettid()));

  cpu_set_t mask;
  CPU_ZERO(&mask);
  uint64_t tmp_bitset = mask_;
  for (unsigned int i=0; i<sizeof(mask_) * 8; ++i) {
    if (tmp_bitset & 1) {
      LOGD("Enabling on CPU: " + std::to_string(i));
      CPU_SET(i, &mask);
    }
    tmp_bitset >>= 1;
  }

  int ret = sched_setaffinity(0 /* self */, sizeof(mask), &mask);
  if (ret) {
    PLOGF("Failed setting scheduling affinity");
  }
}

bool SchedAffinity::IsSet() const {
  return initialized_;
}

SchedAffinity& SchedAffinity::operator=(const uint64_t mask) {
  if (mask == 0) {
    LOGF("Empty CPU affinity mask");
  }

  mask_ = mask;
  initialized_ = true;

  return *this;
}

/*
 * Create a copy of environ and return the new argv[0] size
 *
 * The stack looks pretty much as follows:
 *
 * | [...]
 * |
 * | argc
 * | argv[0] (pointer)
 * | argv[1] (pointer)
 * | ...
 * | NULL
 * | environ[0] (pointer)
 * | environ[1] (pointer)
 * | ...
 * | NULL
 * |
 * | [...]
 * |
 * | *argv[0] (string)
 * | *argv[1] (string)
 * | ...
 * | *environ[0] (string)
 * | *environ[1] (string)
 * | ...
 *
 * After this function is call, all the *environ[*] strings will be moved in
 * dynamic memory, and the old environ strings space in the stack can be used
 * to extend *argv[*].
 */
int move_environ(int argc, char**argv) {
  // Count the number of items in environ
  int env_last_id = -1;
  if (environ) {
    while (environ[++env_last_id])
      ;
  }

  unsigned int argv_strings_size;
  if (env_last_id > 0) {
    // If there is something in environ (it exists and there is something more
    // than the terminating NULL), size is the total size that will be usable
    // for argv after environ is moved.  In fact, this would be the size of all
    // the argv strings, plus the size of all the current environ strings.
    // More specifically, this is:
    // - the address of the last element of environ,
    // - plus its content size, so now we have the very last byte of environ,
    // - subtracted the address of the first string of argv.
    argv_strings_size = environ[env_last_id - 1] + strlen(environ[env_last_id - 1]) - argv[0];
  } else {
    // Otherwise, this is just the size of all the argv strings.
    argv_strings_size = argv[argc - 1] + strlen(argv[argc - 1]) - argv[0];
  }

  if (environ) {
    // Create a copy of environ in dynamic memory
    char** new_environ = static_cast<char**>(malloc(env_last_id * sizeof(char*)));

    // Create a copy in dynamic memory for all the environ strings
    unsigned int i = -1;
    while (environ[++i]) {
      new_environ[i] = strdup(environ[i]);
    }

    // Also, update the environ pointer
    environ = new_environ;
  }

  return argv_strings_size;
}

/*
 * Update the title of the calling process by updating argv[0] (may be
 * destructive for the following argv[1..])
 *
 * If the length of the new title is larger than the previously allocated
 * argv[0] in the stack, then this function will overwrite the next argv
 * strings.
 * If the length of the new title is larger than all the argv strings, this
 * function will move environ and use its reclaimed space.
 */
void setproctitle(int argc, char** argv, const char* title) {
  size_t available_size = strlen(argv[0]);
  size_t argv_size = argv[argc - 1] + strlen(argv[argc - 1]) - argv[0];

  if (available_size < argv_size) {
    available_size = move_environ(argc, argv);
  }

  memset(argv[0], 0, available_size);
  snprintf(argv[0], available_size - 1, "%s", title);
}

}  // namespace dittosuite
