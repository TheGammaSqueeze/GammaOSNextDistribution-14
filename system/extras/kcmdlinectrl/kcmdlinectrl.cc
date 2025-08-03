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

#include <getopt.h>
#include <unistd.h>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <bootloader_message/bootloader_message.h>

#include <functional>
#include <iostream>

using namespace std;

void PrintUsage(const char* progname) {
  std::cerr << "USAGE: " << progname << " PROPERTY_NAME [VALUE]" << endl;
}

int main(int argc, char** argv) {
  char *property_name, *new_value;
  if (argc == 2) {
    // Read property.
    property_name = argv[1];
    new_value = NULL;
  } else if (argc == 3) {
    // Write property.
    property_name = argv[1];
    new_value = argv[2];
  } else {
    PrintUsage(*argv);
    return 1;
  }

  misc_kcmdline_message m = {.version = MISC_KCMDLINE_MESSAGE_VERSION,
                             .magic = MISC_KCMDLINE_MAGIC_HEADER};

  std::string err;
  if (!ReadMiscKcmdlineMessage(&m, &err)) {
    LOG(ERROR) << "Failed to read from misc: " << err << endl;
    return 1;
  }

  if (m.magic != MISC_KCMDLINE_MAGIC_HEADER || m.version != MISC_KCMDLINE_MESSAGE_VERSION) {
    cout << "kcmdline message is invalid, resetting it" << endl;
    m = {.version = MISC_KCMDLINE_MESSAGE_VERSION,
         .magic = MISC_KCMDLINE_MAGIC_HEADER,
         .kcmdline_flags = 0};
  }

  if (!strcmp(property_name, "binder")) {
    if (new_value == NULL) {
      bool use_rust_binder = (m.kcmdline_flags & MISC_KCMDLINE_BINDER_RUST) != 0;
      const char* binder_value = use_rust_binder ? "rust" : "c";
      cout << "binder=" << binder_value << endl;
      return 0;
    } else if (!strcmp(new_value, "rust")) {
      m.kcmdline_flags |= MISC_KCMDLINE_BINDER_RUST;
    } else if (!strcmp(new_value, "c")) {
      m.kcmdline_flags &= !MISC_KCMDLINE_BINDER_RUST;
    } else {
      LOG(ERROR) << "Binder property can only by 'c' or 'rust', but got " << new_value << endl;
      return 1;
    }
  } else {
    LOG(ERROR) << "No such property name " << property_name << endl;
    return 1;
  }

  if (!WriteMiscKcmdlineMessage(m, &err)) {
    LOG(ERROR) << "Failed to write to misc: " << err << endl;
    return 1;
  }

  return 0;
}
