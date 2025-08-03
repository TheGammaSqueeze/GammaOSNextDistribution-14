/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <fcntl.h>
#include <grp.h>
#include <selinux/selinux.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>
#include <unistd.h>

#include "android_filesystem_config.h"
#include "seccomp_policy.h"

static bool set_groups(const gid_t gid) {
  const gid_t groups[] = {gid, AID_EVERYBODY, AID_MISC};
  const size_t num_groups = sizeof(groups) / sizeof(gid_t);

  if (setgroups(num_groups, groups) != 0) {
    fprintf(stderr, "setgroups failed\n");
    return false;
  }

  if (setresgid(gid, gid, gid) != 0) {
    fprintf(stderr, "setresgid failed\n");
    return false;
  }

  return true;
}

static bool set_user(const uid_t uid) {
  if (setresuid(uid, uid, uid) != 0) {
    fprintf(stderr, "setresuid failed\n");
    return false;
  }

  if (prctl(PR_SET_PDEATHSIG, SIGKILL, 0, 0, 0)) {
    fprintf(stderr, "prctl failed\n");
    return false;
  }

  return true;
}

static bool enter_app_sandbox() {
  if (!set_groups(AID_APP_START)) {
    return false;
  }

  if (!set_app_seccomp_filter()) {
    return false;
  }

  if (!set_user(AID_APP_START)) {
    return false;
  };

  // TODO: figure out the correct value or make this configurable.
  setcon("u:r:untrusted_app:s0:c512,c768");

  return true;
}

static bool enter_system_sandbox() {
  if (!set_groups(AID_SYSTEM)) {
    return false;
  }

  if (!set_system_seccomp_filter()) {
    return false;
  }

  if (!set_user(AID_SYSTEM)) {
    return false;
  };

  return true;
}

void print_usage(char** argv) {
  fprintf(stderr, "usage: %s <app|system> <file>\n", argv[0]);
}

int main(int argc, char** argv) {
  if (argc != 3) {
    print_usage(argv);
    return 1;
  }

  if (!strcmp(argv[1], "app")) {
    if (!enter_app_sandbox()) {
      return 1;
    }
  } else if (!strcmp(argv[1], "system")) {
    if (!enter_system_sandbox()) {
      return 1;
    }
  } else {
    print_usage(argv);
    return 1;
  }

  if (open(argv[2], O_RDONLY) == -1) {
    fprintf(stderr, "failed to open %s\n", argv[2]);
    return 1;
  }

  return 0;
}
