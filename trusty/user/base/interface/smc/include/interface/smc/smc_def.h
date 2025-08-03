/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define SMC_IS_FASTCALL(smc_nr) ((smc_nr)&0x80000000)
#define SMC_IS_SMC64(smc_nr) ((smc_nr)&0x40000000)
#define SMC_ENTITY(smc_nr) (((smc_nr)&0x3F000000) >> 24)
#define SMC_FUNCTION(smc_nr) ((smc_nr)&0x0000FFFF)

#define SMC_NR(entity, fn, fastcall, smc64)               \
    ((((fastcall)&0x1U) << 31) | (((smc64)&0x1U) << 30) | \
     (((entity)&0x3FU) << 24) | ((fn)&0xFFFFU))

#define SMC_FASTCALL_NR(entity, fn) SMC_NR((entity), (fn), 1, 0)
#define SMC_STDCALL_NR(entity, fn) SMC_NR((entity), (fn), 0, 0)
#define SMC_FASTCALL64_NR(entity, fn) SMC_NR((entity), (fn), 1, 1)
#define SMC_STDCALL64_NR(entity, fn) SMC_NR((entity), (fn), 0, 1)

/* ARM Architecture calls */
#define SMC_ENTITY_ARCH 0
/* CPU Service calls */
#define SMC_ENTITY_CPU 1
/* SIP Service calls */
#define SMC_ENTITY_SIP 2
/* OEM Service calls */
#define SMC_ENTITY_OEM 3
/* Standard Service calls */
#define SMC_ENTITY_STD 4
/* Reserved for future use */
#define SMC_ENTITY_RESERVED 5
/* Trusted Application calls */
#define SMC_ENTITY_TRUSTED_APP 48
/* Trusted OS calls */
#define SMC_ENTITY_TRUSTED_OS 50
/* Used for secure -> nonsecure logging */
#define SMC_ENTITY_LOGGING 51
/* Used for secure -> nonsecure tests */
#define SMC_ENTITY_TEST 52
/* Trusted OS calls internal to secure monitor */
#define SMC_ENTITY_SECURE_MONITOR 60

#define SMC_NUM_ENTITIES 64
