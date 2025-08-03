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

#ifndef SHELL_AS_CONTEXT_H_
#define SHELL_AS_CONTEXT_H_

#include <selinux/selinux.h>
#include <sys/capability.h>

#include <memory>
#include <optional>
#include <vector>

namespace shell_as {

// Enumeration of the possible seccomp filters that Android may apply to a
// process.
//
// This should be kept in sync with the policies defined in:
// bionic/libc/seccomp/include/seccomp_policy.h
enum SeccompFilter {
  kAppFilter = 0,
  kAppZygoteFilter = 1,
  kSystemFilter = 2,
};

typedef struct SecurityContext {
  std::optional<uid_t> user_id;
  std::optional<gid_t> group_id;
  std::optional<std::vector<gid_t>> supplementary_group_ids;
  std::optional<char *> selinux_context;
  std::optional<SeccompFilter> seccomp_filter;
  std::optional<cap_t> capabilities;
} SecurityContext;

// Infers the appropriate seccomp filter from a user ID.
//
// This mimics the behavior of the zygote process and provides a sane default
// method of picking a filter. However, it is not 100% accurate since it does
// not assign the app zygote filter and would not return an appropriate value
// for processes not started by the zygote.
SeccompFilter SeccompFilterFromUserId(uid_t user_id);

// Derives a complete security context from a given process.
//
// If unable to determine any field of the context this method will return false
// and not modify the given context.
bool SecurityContextFromProcess(pid_t process_id, SecurityContext* context);

// Derives a complete security context from the bundled test app.
//
// If unable to determine any field of the context this method will return false
// and not modify the given context.
bool SecurityContextFromTestApp(SecurityContext* context);

}  // namespace shell_as

#endif  // SHELL_AS_CONTEXT_H_
