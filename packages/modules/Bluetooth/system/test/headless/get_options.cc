/*
 * Copyright 2020 The Android Open Source Project
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

#include "test/headless/get_options.h"

#include <base/logging.h>
#include <getopt.h>
#include <stdio.h>
#include <unistd.h>

#include <list>
#include <string>

#include "include/check.h"
#include "os/log.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

namespace {
enum OptionType {
  kOptionDevice = 0,
  kOptionLoop = 1,
  kOptionUuid = 2,
  kOptionMsleep = 3,
  kOptionStdErr = 4,
  kOptionFlags = 5,
  kOptionClear = 6,
};

constexpr struct option long_options[] = {
    {"device", required_argument, 0, 0},  // kOptionDevice
    {"loop", required_argument, 0, 0},    // kOptionLoop/
    {"uuid", required_argument, 0, 0},    // kOptionUuid
    {"msleep", required_argument, 0, 0},  // kOptionMsleep
    {"stderr", no_argument, 0, 0},        // kOptionStdErr
    {"flags", required_argument, 0, 0},   // kOptionFlags
    {"clear", no_argument, 0, 0},         // kOptionDevice
    {0, 0, 0, 0}};

const char* kShortArgs = "cd:l:u:";

}  // namespace

void bluetooth::test::headless::GetOpt::Usage() const {
  fprintf(stdout, "%s: Usage:\n", name_);
  fprintf(stdout, "%s  -c  Clear logcat logs\n", name_);
  fprintf(stdout,
          "%s  --device=<device,>  Comma separated list of remote devices\n",
          name_);
  fprintf(stdout,
          "%s  --flags=<flags,>  Comma separated list of gd init flags\n",
          name_);
  fprintf(stdout, "%s  --uuid=<uuid,>      Comma separated list of uuids\n",
          name_);
  fprintf(stdout, "%s  --loop=<loop>       Number of loops\n", name_);
  fprintf(stdout, "%s  --msleep=<msecs>    Sleep msec between loops\n", name_);
  fprintf(stdout, "%s  --stderr            Dump stderr to stdout\n", name_);
  fflush(nullptr);
}

void bluetooth::test::headless::GetOpt::ParseValue(
    char* optarg, std::list<std::string>& string_list) {
  CHECK(optarg != nullptr);
  char* p = optarg;
  char* pp = optarg;
  while (*p != '\0') {
    if (*p == ',') {
      *p = 0;
      string_list.push_back(std::string(pp));
      pp = p + 1;
    }
    p++;
  }
  if (pp != p) string_list.push_back(std::string(pp));
}

std::vector<std::string> bluetooth::test::headless::GetOpt::Split(
    std::string s) {
  std::stringstream ss(s);
  std::vector<std::string> values;
  std::string item;
  while (std::getline(ss, item, '=')) {
    values.push_back(item);
  }
  return values;
}

void bluetooth::test::headless::GetOpt::ProcessOption(int option_index,
                                                      char* optarg) {
  std::list<std::string> string_list;
  OptionType option_type = static_cast<OptionType>(option_index);

  switch (option_type) {
    case kOptionDevice:
      if (!optarg) return;
      ParseValue(optarg, string_list);
      for (auto& entry : string_list) {
        if (RawAddress::IsValidAddress(entry)) {
          RawAddress address;
          RawAddress::FromString(entry, address);
          device_.push_back(address);
        }
      }
      break;
    case kOptionLoop:
      loop_ = std::stoul(optarg, nullptr, 0);
      break;
    case kOptionUuid:
      if (!optarg) return;
      ParseValue(optarg, string_list);
      for (auto& entry : string_list) {
        uuid_.push_back(
            bluetooth::Uuid::From16Bit(std::stoul(entry.c_str(), nullptr, 0)));
      }
      break;
    case kOptionMsleep:
      if (!optarg) return;
      msec_ = std::stoul(optarg, nullptr, 0);
      break;
    case kOptionStdErr:
      close_stderr_ = false;
      break;
    case kOptionFlags:
      if (!optarg) return;
      ParseValue(optarg, string_list);
      for (auto& flag : string_list) {
        init_flags_.push_back(flag);
      }
      break;
    case kOptionClear:
      clear_logcat_ = true;
      break;
    default:
      fflush(nullptr);
      valid_ = false;
      return;
      break;
  }
}

void bluetooth::test::headless::GetOpt::ParseStackInitFlags() {
  if (init_flags_.size() == 0) return;

  ASSERT(stack_init_flags_ == nullptr);

  unsigned idx = 0;
  stack_init_flags_ = (const char**)calloc(sizeof(char*), init_flags_.size());
  for (const std::string& flag : init_flags_)
    stack_init_flags_[idx++] = flag.c_str();
  stack_init_flags_[idx] = nullptr;
}

const char** bluetooth::test::headless::GetOpt::StackInitFlags() const {
  return stack_init_flags_;
}

bluetooth::test::headless::GetOpt::GetOpt(int argc, char** argv)
    : name_(argv[0]) {
  while (1) {
    int option_index = 0;
    int c =
        getopt_long_only(argc, argv, kShortArgs, long_options, &option_index);
    if (c == -1) break;

    switch (c) {
      case 0:
        ProcessOption(static_cast<OptionType>(option_index), optarg);
        break;
      case '?':
        Usage();
        valid_ = false;
        return;
      default:
        printf("?? getopt returned character code 0%o ??\n", c);
    }
  }

  while (optind < argc) {
    non_options_.push_back(argv[optind++]);
  }

  ParseStackInitFlags();

  fflush(nullptr);
}

bluetooth::test::headless::GetOpt::~GetOpt() { free(stack_init_flags_); }
