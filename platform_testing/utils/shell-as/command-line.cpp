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

#include "./command-line.h"

#include <getopt.h>

#include <iostream>
#include <string>

#include "./context.h"
#include "./string-utils.h"

namespace shell_as {

namespace {
const std::string kUsage =
    R"(Usage: shell-as [options] [<program> <arguments>...]

shell-as executes a program in a specified Android security context. The default
program that is executed if none is specified is `/bin/system/sh`.

The following options can be used to define the target security context.

--verbose, -v                      Enables verbose logging.
--uid <uid>, -u <uid>              The target real and effective user ID.
--gid <gid>, -g <gid>              The target real and effective group ID.
--groups <gid1,2,..>, -G <1,2,..>  A comma separated list of supplementary group
                                   IDs.
--nogroups                         Specifies that all supplementary groups should
                                   be cleared.
--selinux <context>, -s <context>  The target SELinux context.
--seccomp <filter>, -f <filter>    The target seccomp filter. Valid values of
                                   filter are 'none', 'uid-inferred', 'app',
                                   'app-zygote', and 'system'.
--caps <capabilities>              A libcap textual expression that describes
                                   the desired capability sets. The only
                                   capability set that matters is the permitted
                                   set, the other sets are ignored.

                                   Examples:

                                     "="                  - Clear all capabilities
                                     "=p"                 - Raise all capabilities
                                     "23,CAP_SYS_ADMIN+p" - Raise CAP_SYS_ADMIN
                                                            and capability 23.

                                   For a full description of the possible values
                                   see `man 3 cap_from_text` (the libcap-dev
                                   package provides this man page).
--pid <pid>, -p <pid>              Infer the target security context from a
                                   running process with the given process ID.
                                   This option implies --seccomp uid_inferred.
                                   This option infers the capability from the
                                   target process's permitted capability set.
--profile <profile>, -P <profile>  Infer the target security context from a
                                   predefined security profile. Using this
                                   option will install and execute a test app on
                                   the device. Currently, the only valid profile
                                   is 'untrusted-app' which corresponds to an
                                   untrusted app which has been granted every
                                   non-system permission.

Options are evaluated in the order that they are given. For example, the
following will set the target context to that of process 1234 but override the
user ID to 0:

    shell-as --pid 1234 --uid 0
)";

const char* kShellExecvArgs[] = {"/system/bin/sh", nullptr};

bool ParseGroups(char* line, std::vector<gid_t>* ids) {
  // Allow a null line as a valid input since this method is used to handle both
  // --groups and --nogroups.
  if (line == nullptr) {
    return true;
  }
  return SplitIdsAndSkip(line, ",", /*num_to_skip=*/0, ids);
}
}  // namespace

bool ParseOptions(const int argc, char* const argv[], bool* verbose,
                  SecurityContext* context, char* const* execv_args[]) {
  char short_options[] = "+s:hp:u:g:G:f:c:vP:";
  struct option long_options[] = {
      {"selinux", true, nullptr, 's'}, {"help", false, nullptr, 'h'},
      {"uid", true, nullptr, 'u'},     {"gid", true, nullptr, 'g'},
      {"pid", true, nullptr, 'p'},     {"verbose", false, nullptr, 'v'},
      {"groups", true, nullptr, 'G'},  {"nogroups", false, nullptr, 'G'},
      {"seccomp", true, nullptr, 'f'}, {"caps", true, nullptr, 'c'},
      {"profile", true, nullptr, 'P'},
  };
  int option;
  bool infer_seccomp_filter = false;
  SecurityContext working_context;
  std::vector<gid_t> supplementary_group_ids;
  uint32_t working_id = 0;
  while ((option = getopt_long(argc, argv, short_options, long_options,
                               nullptr)) != -1) {
    switch (option) {
      case 'v':
        *verbose = true;
        break;
      case 'h':
        std::cerr << kUsage;
        return false;
      case 'u':
        if (!StringToUInt32(optarg, &working_id)) {
          return false;
        }
        working_context.user_id = working_id;
        break;
      case 'g':
        if (!StringToUInt32(optarg, &working_id)) {
          return false;
        }
        working_context.group_id = working_id;
        break;
      case 'c':
        working_context.capabilities = cap_from_text(optarg);
        if (working_context.capabilities.value() == nullptr) {
          std::cerr << "Unable to parse capabilities" << std::endl;
          return false;
        }
        break;
      case 'G':
        supplementary_group_ids.clear();
        if (!ParseGroups(optarg, &supplementary_group_ids)) {
          std::cerr << "Unable to parse supplementary groups" << std::endl;
          return false;
        }
        working_context.supplementary_group_ids = supplementary_group_ids;
        break;
      case 's':
        working_context.selinux_context = optarg;
        break;
      case 'f':
        infer_seccomp_filter = false;
        if (strcmp(optarg, "uid-inferred") == 0) {
          infer_seccomp_filter = true;
        } else if (strcmp(optarg, "app") == 0) {
          working_context.seccomp_filter = kAppFilter;
        } else if (strcmp(optarg, "app-zygote") == 0) {
          working_context.seccomp_filter = kAppZygoteFilter;
        } else if (strcmp(optarg, "system") == 0) {
          working_context.seccomp_filter = kSystemFilter;
        } else if (strcmp(optarg, "none") == 0) {
          working_context.seccomp_filter.reset();
        } else {
          std::cerr << "Invalid value for --seccomp: " << optarg << std::endl;
          return false;
        }
        break;
      case 'p':
        if (!SecurityContextFromProcess(atoi(optarg), &working_context)) {
          return false;
        }
        infer_seccomp_filter = true;
        break;
      case 'P':
        if (strcmp(optarg, "untrusted-app") == 0) {
          if (!SecurityContextFromTestApp(&working_context)) {
            return false;
          }
        } else {
          std::cerr << "Invalid value for --profile: " << optarg << std::endl;
          return false;
        }
        infer_seccomp_filter = true;
        break;
      default:
        std::cerr << "Unknown option '" << (char)optopt << "'" << std::endl;
        return false;
    }
  }

  if (infer_seccomp_filter) {
    if (!working_context.user_id.has_value()) {
      std::cerr << "No user ID; unable to infer appropriate seccomp filter."
                << std::endl;
      return false;
    }
    working_context.seccomp_filter =
        SeccompFilterFromUserId(working_context.user_id.value());
  }

  *context = working_context;
  if (optind < argc) {
    *execv_args = argv + optind;
  } else {
    *execv_args = (char**)kShellExecvArgs;
  }
  return true;
}

}  // namespace shell_as
