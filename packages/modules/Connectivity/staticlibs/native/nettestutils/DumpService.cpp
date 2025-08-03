/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "nettestutils/DumpService.h"

#include <android-base/file.h>
#include <android/binder_status.h>

#include <sstream>
#include <thread>

// Version for code using libbinder (e.g., AIDL interfaces with the C++ backend).
android::status_t dumpService(const android::sp<android::IBinder>& binder,
                              const std::vector<std::string>& args,
                              std::vector<std::string>& outputLines) {
  if (!outputLines.empty()) return -EUCLEAN;

  android::base::unique_fd localFd, remoteFd;
  if (!Pipe(&localFd, &remoteFd)) return -errno;

  android::Vector<android::String16> str16Args;
  for (const auto& arg : args) {
    str16Args.push(android::String16(arg.c_str()));
  }
  android::status_t ret;
  // dump() blocks until another thread has consumed all its output.
  std::thread dumpThread =
      std::thread([&ret, binder, remoteFd{std::move(remoteFd)}, str16Args]() {
        ret = binder->dump(remoteFd, str16Args);
      });

  std::string dumpContent;
  if (!android::base::ReadFdToString(localFd.get(), &dumpContent)) {
    return -errno;
  }
  dumpThread.join();
  if (ret != android::OK) return ret;

  std::stringstream dumpStream(std::move(dumpContent));
  std::string line;
  while (std::getline(dumpStream, line)) {
    outputLines.push_back(line);
  }

  return android::OK;
}

// Version for code using libbinder_ndk (e.g., AIDL interfaces with the NDK backend)..
android::status_t dumpService(const ndk::SpAIBinder& binder,
                              const char** args,
                              uint32_t num_args,
                              std::vector<std::string>& outputLines) {
  if (!outputLines.empty()) return -EUCLEAN;

  android::base::unique_fd localFd, remoteFd;
  if (!Pipe(&localFd, &remoteFd)) return -errno;

  android::status_t ret;
  // dump() blocks until another thread has consumed all its output.
  std::thread dumpThread =
      std::thread([&ret, binder, remoteFd{std::move(remoteFd)}, args, num_args]() {
        ret = AIBinder_dump(binder.get(), remoteFd, args, num_args);
  });

  std::string dumpContent;
  if (!android::base::ReadFdToString(localFd.get(), &dumpContent)) {
    return -errno;
  }
  dumpThread.join();
  if (ret != android::OK) return ret;

  std::stringstream dumpStream(dumpContent);
  std::string line;
  while (std::getline(dumpStream, line)) {
    outputLines.push_back(std::move(line));
  }

  return android::OK;
}
