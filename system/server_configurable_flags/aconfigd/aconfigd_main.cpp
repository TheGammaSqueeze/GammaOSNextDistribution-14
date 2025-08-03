/*
 * Copyright (C) 2024 The Android Open Source Project
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


#include <android-base/logging.h>
#include <android-base/unique_fd.h>
#include <cutils/sockets.h>
#include <sys/un.h>

#include "aconfigd.h"

using namespace android::aconfigd;

int main(int argc, char** argv) {
  (void)argc;
  android::base::InitLogging(argv, &android::base::KernelLogger);

  auto init_result = InitializePlatformStorage();
  if (!init_result.ok()) {
    LOG(ERROR) << "failed to initialize storage records: " << init_result.error();
    return 1;
  }

  pid_t pid = fork();
  if (pid < 0) {
    PLOG(ERROR) << "failed to fork";
    return 1;
  } else if (pid != 0) {
    return 0;
  }

  auto aconfigd_fd = android_get_control_socket(kAconfigdSocket);
  if (aconfigd_fd == -1) {
    PLOG(ERROR) << "failed to get aconfigd socket";
    return 1;
  }

  if (listen(aconfigd_fd, 8) < 0) {
    PLOG(ERROR) << "failed to listen to socket";
    return 1;
  };

  while(true) {
    auto client_fd = accept4(aconfigd_fd, nullptr, nullptr, SOCK_CLOEXEC);
    if (client_fd == -1) {
      PLOG(ERROR) << "failed to establish connection";
      break;
    }

    char buffer[kBufferSize] = {};
    auto num_bytes = TEMP_FAILURE_RETRY(recv(client_fd, buffer, sizeof(buffer), 0));
    if (num_bytes < 0) {
      PLOG(ERROR) << "failed to read from aconfigd socket";
      break;
    } else if (num_bytes == 0) {
      LOG(ERROR) << "failed to read from aconfigd socket, empty message";
      break;
    }
    auto msg = std::string(buffer, num_bytes);

    HandleSocketRequest(msg);
  }

  return 1;
}
