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

#ifndef SHELL_AS_TEST_APP_H_
#define SHELL_AS_TEST_APP_H_

#include <sys/types.h>

namespace shell_as {

// Installs and launches the embedded shell-as test app. The test app requests
// and is granted all non-system permissions defined by the OS. The test_app_pid
// parameter is set to the process ID of the running test app. Returns true if
// successful.
bool SetupAndStartTestApp(pid_t *test_app_pid);
}

#endif  // SHELL_AS_TEST_APP_H_
