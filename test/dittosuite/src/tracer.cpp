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

#include <ditto/logger.h>
#include <ditto/tracer.h>

#include <google/protobuf/util/json_util.h>

#include <cstdarg>
#include <unistd.h>

namespace dittosuite {

std::string random_string(int len) {
  std::string char_set;
  for (char c = '0'; c <= '9'; ++c) {
    char_set += c;
  }
  for (char c = 'a'; c <= 'z'; ++c) {
    char_set += c;
  }
  for (char c = 'A'; c <= 'Z'; ++c) {
    char_set += c;
  }

  std::string ret;

  ret.reserve(len);
  for (int i = 0; i < len; ++i) {
    ret += char_set[rand() % (char_set.length())];
  }

  return ret;
}

void Tracer::StartSession(std::unique_ptr<dittosuiteproto::Benchmark> benchmark) {
  if (!trace_marker_.good()) {
    return;
  }

  std::string benchmark_dump;
  google::protobuf::util::JsonPrintOptions options;

  auto status = google::protobuf::util::MessageToJsonString(*benchmark, &benchmark_dump, options);

  if (!status.ok()) {
    LOGF("Unable to dump benchmark into string");
  }

  id_ = random_string(16);

  trace_marker_ << trace_format("B", std::to_string(getpid()), "Session", benchmark_dump, id_);
  trace_marker_.write("\0", 1);
  trace_marker_.flush();
}

void Tracer::Start(const std::string& splice) {
  if (!trace_marker_.good()) {
    return;
  }

  trace_marker_ << trace_format("B", std::to_string(getpid()), splice);
  trace_marker_.write("\0", 1);
  trace_marker_.flush();
}

void Tracer::End(const std::string& splice) {
  if (!trace_marker_.good()) {
    return;
  }

  trace_marker_ << trace_format("E", std::to_string(getpid()), splice);
  trace_marker_.write("\0", 1);
  trace_marker_.flush();
}

Tracer::Tracer() {
  trace_marker_.open("/sys/kernel/tracing/trace_marker",
                     std::ofstream::out | std::ofstream::binary);
  if (!trace_marker_.good()) {
    LOGW("Unable to open trace_marker");
  }
}

Tracer::~Tracer() {
  if (!trace_marker_.good()) {
    return;
  }

  trace_marker_ << trace_format("E", std::to_string(getpid()), "Session", id_);
  trace_marker_.write("\0", 1);
  trace_marker_.flush();

  trace_marker_.close();
}

}  // namespace dittosuite
