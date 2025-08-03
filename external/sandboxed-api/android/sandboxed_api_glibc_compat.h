//
// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#pragma once

// Typo in old glibc
#define PTRACE_EVENT_SECCOMP PTRAVE_EVENT_SECCOMP

// From aosp/599933
/*
 * Older glibc builds predate seccomp inclusion.  These arches are the ones
 * AOSP needs and doesn't provide anything newer.  All other targets can upgrade
 * their kernel headers.
 */
#ifndef SYS_seccomp
# if defined(__x86_64__)
#  define SYS_seccomp 317
# elif defined(__i386__)
#  define SYS_seccomp 354
# elif defined(__aarch64__)
#  define SYS_seccomp 277
# elif defined(__arm__)
#  define SYS_seccomp 383
# else
#  error "Update your kernel headers"
# endif
#endif

#ifndef PTRACE_O_EXITKILL
#define PTRACE_O_EXITKILL (1 << 20)
#endif
