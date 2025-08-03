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

// Shell code that sets the current SELinux context to a given string.
//
// The desired SELinux context is appended to the payload as a null-terminated
// string.
//
// After the SELinux context has been updated the current process will raise
// SIGSTOP.

#include "./shell-code/constants.S"
#include "./shell-code/constants-arm64.S"

.globl __setcon_shell_code_start
.globl __setcon_shell_code_end

__setcon_shell_code_start:
  // Ensure that the context and SELinux /proc file are readable. This assumes
  // that the max length of these two strings is shorter than 0x1000.
  //
  // mprotect(context & ~0xFFF, 0x2000, PROT_READ | PROT_EXEC)
  mov x8, SYS_MPROTECT
  adr X0, __setcon_shell_code_end
  and x0, x0, ~0xFFF
  mov x1, 0x2000
  mov x2, (PROT_READ | PROT_EXEC)
  svc 0

  // x10 = openat(AT_FDCWD, "/proc/self/attr/current", O_WRONLY, O_WRONLY)
  mov x8, SYS_OPENAT
  mov x0, AT_FDCWD
  adr x1, selinux_proc_file
  mov x2, O_WRONLY
  mov x3, O_WRONLY
  svc 0
  mov x10, x0

  // x11 = strlen(context)
  mov x11, 0
  adr x0, context
strlen_start:
  ldrb w1, [x0, x11]
  cmp w1, 0
  b.eq strlen_done
  add x11, x11, 1
  b strlen_start
strlen_done:

  // write(x10, context, x11)
  mov x8, SYS_WRITE
  mov x0, x10
  adr x1, context
  mov x2, x11
  svc 0

  // close(x10)
  mov x8, SYS_CLOSE
  mov x0, x10
  svc 0

  // x0 = getpid()
  mov x8, SYS_GETPID
  svc 0

  // kill(x0, SIGSTOP)
  mov x8, SYS_KILL
  mov x1, SIGSTOP
  svc 0

selinux_proc_file:
  .asciz "/proc/thread-self/attr/current"

context:
__setcon_shell_code_end:
