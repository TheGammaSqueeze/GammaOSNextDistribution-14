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
#include "./shell-code/constants-x86.S"

.globl __setcon_shell_code_start
.globl __setcon_shell_code_end

__setcon_shell_code_start:

  // x86 does not have RIP relative addressing. To work around this, relative
  // calls are used to obtain the runtime address of a label. Once the location
  // of one label is known, other labels can be addressed relative to the known
  // label.
  call constant_relative_address
constant_relative_address:
  pop %esi

  // Ensure that the context and SELinux /proc file are readable. This assumes
  // that the max length of these two strings is shorter than 0x1000.
  //
  // mprotect(context & ~0xFFF, 0x2000, PROT_READ | PROT_EXEC)
  mov $SYS_MPROTECT, %eax
  mov $~0xFFF, %ebx
  and %esi, %ebx
  mov $0x2000, %ecx
  mov $(PROT_READ | PROT_EXEC), %edx
  int $0x80

  // ebx = open("/proc/self/attr/current", O_WRONLY, O_WRONLY)
  mov $SYS_OPEN, %eax
  lea (selinux_proc_file - constant_relative_address)(%esi), %ebx
  mov $O_WRONLY, %ecx
  mov $O_WRONLY, %edx
  int $0x80
  mov %eax, %ebx

  // write(ebx, context, strlen(context))
  xor %edx, %edx
  leal (context - constant_relative_address)(%esi), %ecx
strlen_start:
  movb (%ecx, %edx), %al
  test %al, %al
  jz strlen_done
  inc %edx
  jmp strlen_start
strlen_done:
  mov $SYS_WRITE, %eax
  int $0x80

  // close(ebx)
  mov $SYS_CLOSE, %eax
  int $0x80

  // ebx = getpid()
  mov $SYS_GETPID, %eax
  int $0x80
  mov %eax, %ebx

  // kill(ebx, SIGSTOP)
  mov $SYS_KILL, %eax
  mov $SIGSTOP, %ecx
  int $0x80

selinux_proc_file:
  .asciz "/proc/self/attr/current"

context:
__setcon_shell_code_end:
