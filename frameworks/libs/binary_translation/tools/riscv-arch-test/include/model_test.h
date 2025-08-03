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

#ifndef _COMPLIANCE_MODEL_H
#define _COMPLIANCE_MODEL_H

#define RVMODEL_DATA_SECTION

// For all 8-byte records between begin_signature and end_signature we call
// syscall(/* SYS_write */ 64, /* stderr */ 2, /* data_pointer */, /* size */ 8)
// to write it to stderr. This way stdout can still be used for tracing/debugging.
// After that we call syscall(/* SYS_exit */ 93, /* exit code */ 0)
// RV_COMPLIANCE_HALT
#define RVMODEL_HALT                  \
  li a7, 64;                          \
  li a0, 2;                           \
  lui a1, % hi(begin_signature);      \
  addi a1, a1, % lo(begin_signature); \
  li a2, 8;                           \
  lui a3, % hi(end_signature);        \
  addi a3, a3, % lo(end_signature);   \
  write_to_stderr:                    \
  ecall;                              \
  li a0, 2;                           \
  addi a1, a1, 8;                     \
  bgt a3, a1, write_to_stderr;        \
  li a7, 93;                          \
  li a0, 0;                           \
  ecall;

#define RVMODEL_BOOT

// RV_COMPLIANCE_DATA_BEGIN
#define RVMODEL_DATA_BEGIN      \
  RVMODEL_DATA_SECTION.align 8; \
  .global begin_signature;      \
  begin_signature:

// We add placeholder data to ensure the compiler keeps the label in place.
// RV_COMPLIANCE_DATA_END
#define RVMODEL_DATA_END \
  .align 8;              \
  .global end_signature; \
  end_signature:         \
  .zero 8;

// RVTEST_IO_INIT
#define RVMODEL_IO_INIT
// RVTEST_IO_WRITE_STR
#define RVMODEL_IO_WRITE_STR(_R, _STR)
// RVTEST_IO_CHECK
#define RVMODEL_IO_CHECK()
// RVTEST_IO_ASSERT_GPR_EQ
#define RVMODEL_IO_ASSERT_GPR_EQ(_S, _R, _I)

// RVTEST_IO_ASSERT_SFPR_EQ
#define RVMODEL_IO_ASSERT_SFPR_EQ(_F, _R, _I)
// RVTEST_IO_ASSERT_DFPR_EQ
#define RVMODEL_IO_ASSERT_DFPR_EQ(_D, _R, _I)

#define RVMODEL_SET_MSW_INT

#define RVMODEL_CLEAR_MSW_INT

#define RVMODEL_CLEAR_MTIMER_INT

#define RVMODEL_CLEAR_MEXT_INT

#endif  // _COMPLIANCE_MODEL_H
