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

#include "./context.h"

#include <private/android_filesystem_config.h>  // For AID_APP_START.
#include <stdio.h>
#include <stdlib.h>

#include <iostream>
#include <string>

#include "./string-utils.h"
#include "./test-app.h"

namespace shell_as {

namespace {

bool ParseIdFromProcStatusLine(char* line, uid_t* id) {
  // The user and group ID lines of the status file look like:
  //
  // Uid: <real> <effective> <saved> <filesystem>
  // Gid: <real> <effective> <saved> <filesystem>
  std::vector<uid_t> ids;
  if (!SplitIdsAndSkip(line, "\t\n ", /*num_to_skip=*/1, &ids) ||
      ids.size() < 1) {
    return false;
  }
  *id = ids[0];
  return true;
}

bool ParseGroupsFromProcStatusLine(char* line, std::vector<gid_t>* ids) {
  // The supplementary groups line of the status file looks like:
  //
  // Groups: <group1> <group2> <group3> ...
  return SplitIdsAndSkip(line, "\t\n ", /*num_to_skip=*/1, ids);
}

bool ParseProcStatusFile(const pid_t process_id, uid_t* real_user_id,
                         gid_t* real_group_id,
                         std::vector<gid_t>* supplementary_group_ids) {
  std::string proc_status_path =
      std::string("/proc/") + std::to_string(process_id) + "/status";
  FILE* status_file = fopen(proc_status_path.c_str(), "r");
  if (status_file == nullptr) {
    std::cerr << "Unable to open '" << proc_status_path << "'" << std::endl;
  }
  bool parsed_user = false;
  bool parsed_group = false;
  bool parsed_supplementary_groups = false;
  while (true) {
    size_t line_length = 0;
    char* line = nullptr;
    if (getline(&line, &line_length, status_file) < 0) {
      free(line);
      break;
    }
    if (strncmp("Uid:", line, 4) == 0) {
      parsed_user = ParseIdFromProcStatusLine(line, real_user_id);
    } else if (strncmp("Gid:", line, 4) == 0) {
      parsed_group = ParseIdFromProcStatusLine(line, real_group_id);
    } else if (strncmp("Groups:", line, 7) == 0) {
      parsed_supplementary_groups =
          ParseGroupsFromProcStatusLine(line, supplementary_group_ids);
    }
    free(line);
  }
  fclose(status_file);
  return parsed_user && parsed_group && parsed_supplementary_groups;
}

}  // namespace

bool SecurityContextFromProcess(const pid_t process_id,
                                SecurityContext* context) {
  char* selinux_context;
  if (getpidcon(process_id, &selinux_context) != 0) {
    std::cerr << "Unable to obtain SELinux context from process " << process_id
              << std::endl;
    return false;
  }

  cap_t capabilities = cap_get_pid(process_id);
  if (capabilities == nullptr) {
    std::cerr << "Unable to obtain capability set from process " << process_id
              << std::endl;
    return false;
  }

  uid_t user_id = 0;
  gid_t group_id = 0;
  std::vector<gid_t> supplementary_group_ids;
  if (!ParseProcStatusFile(process_id, &user_id, &group_id,
                           &supplementary_group_ids)) {
    std::cerr << "Unable to obtain user and group IDs from process "
              << process_id << std::endl;
    return false;
  }

  context->selinux_context = selinux_context;
  context->user_id = user_id;
  context->group_id = group_id;
  context->supplementary_group_ids = supplementary_group_ids;
  context->capabilities = capabilities;
  return true;
}

bool SecurityContextFromTestApp(SecurityContext* context) {
  pid_t test_app_pid = 0;
  if (!SetupAndStartTestApp(&test_app_pid)) {
    std::cerr << "Unable to install test app." << std::endl;
    return false;
  }
  return SecurityContextFromProcess(test_app_pid, context);
}

SeccompFilter SeccompFilterFromUserId(uid_t user_id) {
  // Copied from:
  // frameworks/base/core/jni/com_android_internal_os_Zygote.cpp
  return user_id >= AID_APP_START ? kAppFilter : kSystemFilter;
}

}  // namespace shell_as
