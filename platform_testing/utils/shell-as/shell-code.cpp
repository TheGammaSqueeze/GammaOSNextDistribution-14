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

#include "./shell-code.h"

#include <sys/mman.h>
#include <sys/types.h>

#define PAGE_START(addr) ((uintptr_t)addr & ~(PAGE_SIZE - 1))

// Shell code that sets the SELinux context of the current process.
//
// The shell code expects a null-terminated SELinux context string to be placed
// immediately after it in memory. After the SELinux context has been changed
// the shell code will stop the current process with SIGSTOP.
//
// This shell code must be self-contained and position-independent.
extern "C" void __setcon_shell_code_start();
extern "C" void __setcon_shell_code_end();

// Shell code that stops execution of the current process by raising a signal.
// The specific signal that is raised is given in __trap_shell_code_signal.
//
// This shell code can be used to inject break points into a traced process.
//
// The shell code must not modify any registers other than the program counter.
extern "C" void __trap_shell_code_start();
extern "C" void __trap_shell_code_end();
extern "C" int __trap_shell_code_signal;

namespace shell_as {

namespace {
void EnsureShellcodeReadable(void (*start)(), void (*end)()) {
  mprotect((void*)PAGE_START(start),
           PAGE_START(end) - PAGE_START(start) + PAGE_SIZE,
           PROT_READ | PROT_EXEC);
}
}  // namespace

std::unique_ptr<uint8_t[]> GetSELinuxShellCode(
    char* selinux_context, size_t* total_size) {
  EnsureShellcodeReadable(&__setcon_shell_code_start, &__setcon_shell_code_end);

  size_t shell_code_size = (uintptr_t)&__setcon_shell_code_end -
                           (uintptr_t)&__setcon_shell_code_start;
  size_t selinux_context_size = strlen(selinux_context) + 1 /* null byte */;
  *total_size = shell_code_size + selinux_context_size;

  std::unique_ptr<uint8_t[]> shell_code(new uint8_t[*total_size]);
  memcpy(shell_code.get(), (void*)&__setcon_shell_code_start, shell_code_size);
  memcpy(shell_code.get() + shell_code_size, selinux_context,
         selinux_context_size);
  return shell_code;
}

std::unique_ptr<uint8_t[]> GetTrapShellCode(int* expected_signal,
                                            size_t* total_size) {
  EnsureShellcodeReadable(&__trap_shell_code_start, &__trap_shell_code_end);

  *expected_signal = __trap_shell_code_signal;

  *total_size =
      (uintptr_t)&__trap_shell_code_end - (uintptr_t)&__trap_shell_code_start;
  std::unique_ptr<uint8_t[]> shell_code(new uint8_t[*total_size]);
  memcpy(shell_code.get(), (void*)&__trap_shell_code_start, *total_size);
  return shell_code;
}
}  // namespace shell_as
