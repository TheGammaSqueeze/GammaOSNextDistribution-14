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
#include "./shell-code/constants-arm.S"

.thumb

.globl __setcon_shell_code_start
.globl __setcon_shell_code_end

__setcon_shell_code_start:
  // Ensure that the context and SELinux /proc file are readable. This assumes
  // that the max length of these two strings is shorter than 0x1000.
  //
  // mprotect(context & ~0xFFF, 0x2000, PROT_READ | PROT_EXEC)
  mov r7, SYS_MPROTECT
  adr r0, context
  movw r2, 0xF000
  movt r2, 0xFFFF
  and r0, r0, r2
  mov r1, 0x2000
  mov r2, (PROT_READ | PROT_EXEC)
  swi 0

  // r10 = open("/proc/self/attr/current", O_WRONLY, O_WRONLY)
  mov r7, SYS_OPEN
  adr r0, selinux_proc_file
  mov r1, O_WRONLY
  mov r2, O_WRONLY
  swi 0
  mov r10, r0

  // r11 = strlen(context)
  mov r11, 0
  adr r0, context
strlen_start:
  ldrb r1, [r0, r11]
  cmp r1, 0
  beq strlen_done
  add r11, r11, 1
  b strlen_start
strlen_done:

  // write(r10, context, r11)
  mov r7, SYS_WRITE
  mov r0, r10
  adr r1, context
  mov r2, r11
  swi 0

  // close(r10)
  mov r7, SYS_CLOSE
  mov r0, r10
  swi 0

  // r0 = getpid()
  mov r7, SYS_GETPID
  swi 0

  // kill(r0, SIGSTOP)
  mov r7, SYS_KILL
  mov r1, SIGSTOP
  swi 0

selinux_proc_file:
  .asciz "/proc/thread-self/attr/current"

context:
__setcon_shell_code_end:
