/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "./test-app.h"

#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include <iostream>
#include <string>

#include "./string-utils.h"

namespace shell_as {

// Returns a pointer to bytes of the test app APK along with the length in bytes
// of the APK.
//
// This function is defined by the shell-as-test-app-apk-cpp genrule.
void GetTestApk(uint8_t **apk, size_t *length);

namespace {

// The staging path for the test app APK.
const char kTestAppApkStagingPath[] = "/data/local/tmp/shell-as-test-app.apk";

// Writes the test app to a staging location and then installs the APK via the
// 'pm' utility. The app is granted runtime permissions on installation. Returns
// true if the app is installed successfully.
bool InstallTestApp() {
  uint8_t *apk = nullptr;
  size_t apk_size = 0;
  GetTestApk(&apk, &apk_size);

  int staging_file = open(kTestAppApkStagingPath, O_WRONLY | O_CREAT | O_TRUNC,
                          S_IRUSR | S_IWUSR);
  if (staging_file == -1) {
    std::cerr << "Unable to open staging APK path." << std::endl;
    return false;
  }

  size_t bytes_written = write(staging_file, apk, apk_size);
  close(staging_file);
  if (bytes_written != apk_size) {
    std::cerr << "Unable to write entire test app APK." << std::endl;
    return false;
  }

  const char cmd_template[] = "pm install -g %s > /dev/null 2> /dev/null";
  char system_cmd[sizeof(cmd_template) + sizeof(kTestAppApkStagingPath) + 1] =
      {};
  sprintf(system_cmd, cmd_template, kTestAppApkStagingPath);
  return system(system_cmd) == 0;
}

// Uninstalls the test app if it is installed. This method is a no-op if the app
// is not installed.
void UninstallTestApp() {
  system(
      "pm uninstall com.android.google.tools.security.shell_as"
      " > /dev/null 2> /dev/null");
}

// Starts the main activity of the test app. This is necessary as some aspects
// of the security context can only be inferred from a running process.
bool StartTestApp() {
  return system(
             "am start-activity "
             "com.android.google.tools.security.shell_as/"
             ".MainActivity"
             " > /dev/null 2> /dev/null") == 0;
}

// Obtain the process ID of the test app and returns true if it is running.
// Returns false otherwise.
bool GetTestAppProcessId(pid_t *test_app_pid) {
  FILE *pgrep = popen(
      "pgrep -f "
      "com.android.google.tools.security.shell_as",
      "r");
  if (!pgrep) {
    std::cerr << "Unable to execute pgrep." << std::endl;
    return false;
  }

  char pgrep_output[128];
  memset(pgrep_output, 0, sizeof(pgrep_output));
  int bytes_read = fread(pgrep_output, 1, sizeof(pgrep_output) - 1, pgrep);
  pclose(pgrep);
  if (bytes_read <= 0) {
    // Unable to find the process. This may happen if the app is still starting
    // up.
    return false;
  }
  return StringToUInt32(pgrep_output, (uint32_t *)test_app_pid);
}
}  // namespace

bool SetupAndStartTestApp(pid_t *test_app_pid) {
  UninstallTestApp();

  if (!InstallTestApp()) {
    std::cerr << "Unable to install test app." << std::endl;
    return false;
  }

  if (!StartTestApp()) {
    std::cerr << "Unable to start and obtain test app PID." << std::endl;
    return false;
  }

  for (int i = 0; i < 5; i++) {
    if (GetTestAppProcessId(test_app_pid)) {
      return true;
    }
    sleep(1);
  }
  return false;
}
}  // namespace shell_as
