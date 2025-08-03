/*
 * Copyright (C) 2022 The Android Open Source Project
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
#pragma once

/* ARM DEN 0028A(0.9.0) mandates that bits 23:16 must be zero for fast calls
 * (when bit 31 == 1) */
#define ILLEGAL_SMC ((long)0x80FF0000)

/* SMC numbers defined by ATF */
#define SMC_NR(entity, fn, fastcall, smc64)                               \
    (((((uint32_t)(fastcall)) & 0x1U) << 31U) | (((smc64)&0x1U) << 30U) | \
     (((entity)&0x3FU) << 24U) | ((fn)&0xFFFFU))

#define SMC_FASTCALL_NR(entity, fn) SMC_NR((entity), (fn), 1U, 0U)
#define SMC_FASTCALL64_NR(entity, fn) SMC_NR((entity), (fn), 1U, 1U)

#define SMC_ENTITY_PLATFORM_MONITOR 61

/*
 * Write character in r1 to debug console
 */
#define SMC_FC_DEBUG_PUTC SMC_FASTCALL_NR(SMC_ENTITY_PLATFORM_MONITOR, 0x0)

/*
 * Get register base address
 * r1: SMC_GET_GIC_BASE_GICD or SMC_GET_GIC_BASE_GICC
 */
#define SMC_GET_GIC_BASE_GICD 0
#define SMC_GET_GIC_BASE_GICC 1
#define SMC_FC_GET_REG_BASE SMC_FASTCALL_NR(SMC_ENTITY_PLATFORM_MONITOR, 0x1)

#define GICD_BASE 0x8000000
#define GICC_BASE 0x8010000

/*
 * Echo smc fastcall number and the first argument. Helpful for testing.
 */
#define SMC_FC_ECHO_ONE_ARG SMC_FASTCALL_NR(SMC_ENTITY_PLATFORM_MONITOR, 0x2)
#define SMC_FC64_ECHO_ONE_ARG \
    SMC_FASTCALL64_NR(SMC_ENTITY_PLATFORM_MONITOR, 0x2)

/*
 * Echo smc fastcall number and the first three arguments. Helpful for testing.
 * r1: SMC_ACCESS_CONTROL_ALLOW_ARGS or SMC_ACCESS_CONTROL_VALIDATE_ARGS
 * r2: non-zero when r1 == SMC_ACCESS_CONTROL_VALIDATE_ARGS
 */
#define SMC_ACCESS_CONTROL_ALLOW_ARGS 0
#define SMC_ACCESS_CONTROL_VALIDATE_ARGS 1
#define SMC_FC_ECHO_THREE_ARGS SMC_FASTCALL_NR(SMC_ENTITY_PLATFORM_MONITOR, 0x3)
#define SMC_FC64_ECHO_THREE_ARGS \
    SMC_FASTCALL64_NR(SMC_ENTITY_PLATFORM_MONITOR, 0x3)
