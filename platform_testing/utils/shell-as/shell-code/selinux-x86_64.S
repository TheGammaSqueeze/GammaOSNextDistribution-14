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
#include "./shell-code/constants-x86_64.S"

.globl __setcon_shell_code_start
.globl __setcon_shell_code_end

__setcon_shell_code_start:

  // Ensure that the context and SELinux /proc file are readable. This assumes
  // that the max length of these two strings is shorter than 0x1000.
  //
  // mprotect(context & ~0xFFF, 0x2000, PROT_READ | PROT_EXEC)
  mov $SYS_MPROTECT, %rax
  lea context(%rip), %rdi
  and $~0xFFF, %rdi
  mov $0x2000, %rsi
  mov $(PROT_READ | PROT_EXEC), %rdx
  syscall

  // rdi = open("/proc/self/attr/current", O_WRONLY, O_WRONLY)
  mov $SYS_OPEN, %eax
  lea selinux_proc_file(%rip), %rdi
  mov $O_WRONLY, %rsi
  mov $O_WRONLY, %rdx
  syscall
  mov %rax, %rdi

  // write(rdi, context, strlen(context))
  xor %rdx, %rdx
  lea context(%rip), %rsi
strlen_start:
  movb (%rsi, %rdx), %al
  test %al, %al
  jz strlen_done
  inc %rdx
  jmp strlen_start
strlen_done:
  mov $SYS_WRITE, %rax
  syscall

  // close(rdi)
  mov $SYS_CLOSE, %rax
  syscall

  // rdi = getpid()
  mov $SYS_GETPID, %rax
  syscall
  mov %rax, %rdi

  // kill(rdi, SIGSTOP)
  mov $SYS_KILL, %rax
  mov $SIGSTOP, %rsi
  syscall

selinux_proc_file:
  .asciz "/proc/self/attr/current"

context:
__setcon_shell_code_end:
