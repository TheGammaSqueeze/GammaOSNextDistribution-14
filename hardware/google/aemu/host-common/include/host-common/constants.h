// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

/* Maximum numbers of emulators that can run concurrently without
 * forcing their port numbers with the -port option.
 * This is not a hard limit. Instead, when starting up, the program
 * will try to bind to localhost ports 5554 + n*2, for n in
 * [0..MAX_EMULATORS), if it fails, it will refuse to start.
 * You can route around this by using the -port or -ports options
 * to specify the ports manually.
 */
#define MAX_ANDROID_EMULATORS  16

#define ANDROID_CONSOLE_BASEPORT 5554

/* The name of the .ini file that contains the initial hardware
 * properties for the AVD. This file is specific to the AVD and
 * is in the AVD's directory.
 */
#define CORE_CONFIG_INI "config.ini"

/* The name of the .ini file that contains the complete hardware
 * properties for the AVD. This file is specific to the AVD and
 * is in the AVD's directory. This will be used to launch the
 * corresponding core from the UI.
 */
#define CORE_HARDWARE_INI "hardware-qemu.ini"

/* The name of the snapshot lock file that is used to serialize
 * snapshot operations on the same AVD across multiple emulator
 * instances.
 */
#define SNAPSHOT_LOCK "snapshot.lock"

#define MULTIINSTANCE_LOCK "multiinstance.lock"

/* The file where the crash reporter holds a copy of
 * the hardware properties. This file is in a temporary
 * directory set up by the crash reporter.
 */
#define CRASH_AVD_HARDWARE_INFO "avd_info.txt"
#define CRASH_AVD_INI "avd_ini_path"
