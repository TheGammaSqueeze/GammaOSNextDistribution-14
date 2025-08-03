/*
 * Copyright (C) 2020 The Android Open Source Project
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

    .globl berberis_RawSyscallImpl
    .balign 16

berberis_RawSyscallImpl:

    .cfi_startproc
    push %ebx
    .cfi_adjust_cfa_offset 4
    .cfi_rel_offset ebx, 0
    push %esi
    .cfi_adjust_cfa_offset 4
    .cfi_rel_offset esi, 0
    push %edi
    .cfi_adjust_cfa_offset 4
    .cfi_rel_offset edi, 0
    push %ebp
    .cfi_adjust_cfa_offset 4
    .cfi_rel_offset ebp, 0

    // The params are shifted 16 bytes after the saved regs
    mov 20(%esp), %eax
    mov 24(%esp), %ebx
    mov 28(%esp), %ecx
    mov 32(%esp), %edx
    mov 36(%esp), %esi
    mov 40(%esp), %edi
    mov 44(%esp), %ebp

    int $0x80

    pop %ebp
    .cfi_adjust_cfa_offset -4
    .cfi_restore ebp
    pop %edi
    .cfi_adjust_cfa_offset -4
    .cfi_restore edi
    pop %esi
    .cfi_adjust_cfa_offset -4
    .cfi_restore esi
    pop %ebx
    .cfi_adjust_cfa_offset -4
    .cfi_restore ebx

    ret
    .cfi_endproc
