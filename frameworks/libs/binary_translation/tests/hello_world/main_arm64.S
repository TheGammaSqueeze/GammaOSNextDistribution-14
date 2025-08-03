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
//

.section .text
.globl _start
_start:
    // SYS_write
    mov x8, #0x40
    // fd is stdout
    mov x0, #0x1
    // pc-relative address of the string
    adrp x1, hello_string
    add x1, x1, :lo12:hello_string
    // string size
    mov x2, #0x6
    svc #0x0

    // SYS_exit
    mov x8, #93
    // value to return from main
    mov x0, #0x0
    svc #0x0

.section .rodata
hello_string:
   .asciz "Hello\n"
