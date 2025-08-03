/*
 * Copyright (c) 2013-2016 Google Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
#ifndef __LIB_SM_SMCALL_H
#define __LIB_SM_SMCALL_H

#include <interface/smc/smc_def.h>

#define SMC_NUM_ARGS 4
#define SMC_NUM_PARAMS (SMC_NUM_ARGS - 1)

/* FC = Fast call, SC = Standard call */
#define SMC_SC_RESTART_LAST SMC_STDCALL_NR(SMC_ENTITY_SECURE_MONITOR, 0)
#define SMC_SC_LOCKED_NOP SMC_STDCALL_NR(SMC_ENTITY_SECURE_MONITOR, 1)

/**
 * SMC_SC_RESTART_FIQ - Re-enter trusty after it was interrupted by an fiq
 *
 * No arguments, no return value.
 *
 * Re-enter trusty after returning to ns to process an fiq. Must be called iff
 * trusty returns SM_ERR_FIQ_INTERRUPTED.
 *
 * Enable by selecting api version TRUSTY_API_VERSION_RESTART_FIQ (1) or later.
 */
#define SMC_SC_RESTART_FIQ SMC_STDCALL_NR(SMC_ENTITY_SECURE_MONITOR, 2)

/**
 * SMC_SC_NOP - Enter trusty to run pending work.
 *
 * No arguments.
 *
 * Returns SM_ERR_NOP_INTERRUPTED or SM_ERR_NOP_DONE.
 * If SM_ERR_NOP_INTERRUPTED is returned, the call must be repeated.
 *
 * Enable by selecting api version TRUSTY_API_VERSION_SMP (2) or later.
 */
#define SMC_SC_NOP SMC_STDCALL_NR(SMC_ENTITY_SECURE_MONITOR, 3)

/**
 * SMC API for supporting shared-memory based trusty-linux info exchange
 *
 * SMC_SC_SCHED_SHARE_REGISTER - enter trusty to establish a shared-memory
 * region
 * Arguments:
 * param[0]: shared-memory client-id
 * param[1]: shared-memory buffer-id
 * param[2]: shared-memory block size
 *
 * returns:
 * SM_ERR_INTERNAL_FAILURE on failure.
 * 0 on success.
 */
#define SMC_SC_SCHED_SHARE_REGISTER SMC_STDCALL_NR(SMC_ENTITY_SECURE_MONITOR, 4)

/**
 * SMC API for supporting shared-memory based trusty-linux info exchange
 *
 * SMC_SC_SCHED_SHARE_UNREGISTER - enter trusty to release the shared-memory
 * region
 * Arguments:
 * param[0]: shared-memory client-id
 * param[1]: shared-memory buffer-id
 *
 * returns:
 * SM_ERR_INTERNAL_FAILURE on failure.
 * 0 on success.
 */
#define SMC_SC_SCHED_SHARE_UNREGISTER \
    SMC_STDCALL_NR(SMC_ENTITY_SECURE_MONITOR, 5)

/*
 * Return from secure os to non-secure os with return value in r1
 */
#define SMC_SC_NS_RETURN SMC_STDCALL_NR(SMC_ENTITY_SECURE_MONITOR, 0)

#define SMC_FC_RESERVED SMC_FASTCALL_NR(SMC_ENTITY_SECURE_MONITOR, 0)
#define SMC_FC_FIQ_EXIT SMC_FASTCALL_NR(SMC_ENTITY_SECURE_MONITOR, 1)
#define SMC_FC_REQUEST_FIQ SMC_FASTCALL_NR(SMC_ENTITY_SECURE_MONITOR, 2)

#define TRUSTY_IRQ_TYPE_NORMAL (0)
#define TRUSTY_IRQ_TYPE_PER_CPU (1)
#define TRUSTY_IRQ_TYPE_DOORBELL (2)
#define SMC_FC_GET_NEXT_IRQ SMC_FASTCALL_NR(SMC_ENTITY_SECURE_MONITOR, 3)

#define SMC_FC_FIQ_ENTER SMC_FASTCALL_NR(SMC_ENTITY_SECURE_MONITOR, 4)

#define SMC_FC64_SET_FIQ_HANDLER SMC_FASTCALL64_NR(SMC_ENTITY_SECURE_MONITOR, 5)
#define SMC_FC64_GET_FIQ_REGS SMC_FASTCALL64_NR(SMC_ENTITY_SECURE_MONITOR, 6)

#define SMC_FC_CPU_SUSPEND SMC_FASTCALL_NR(SMC_ENTITY_SECURE_MONITOR, 7)
#define SMC_FC_CPU_RESUME SMC_FASTCALL_NR(SMC_ENTITY_SECURE_MONITOR, 8)

#define SMC_FC_AARCH_SWITCH SMC_FASTCALL_NR(SMC_ENTITY_SECURE_MONITOR, 9)
#define SMC_FC_GET_VERSION_STR SMC_FASTCALL_NR(SMC_ENTITY_SECURE_MONITOR, 10)

/**
 * SMC_FC_API_VERSION - Find and select supported API version.
 *
 * @r1: Version supported by client.
 *
 * Returns version supported by trusty.
 *
 * If multiple versions are supported, the client should start by calling
 * SMC_FC_API_VERSION with the largest version it supports. Trusty will then
 * return a version it supports. If the client does not support the version
 * returned by trusty and the version returned is less than the version
 * requested, repeat the call with the largest supported version less than the
 * last returned version.
 *
 * This call must be made before any calls that are affected by the api version.
 */
#define TRUSTY_API_VERSION_RESTART_FIQ (1)
#define TRUSTY_API_VERSION_SMP (2)
#define TRUSTY_API_VERSION_SMP_NOP (3)
#define TRUSTY_API_VERSION_PHYS_MEM_OBJ (4)
#define TRUSTY_API_VERSION_MEM_OBJ (5)
#define TRUSTY_API_VERSION_CURRENT (5)
#define SMC_FC_API_VERSION SMC_FASTCALL_NR(SMC_ENTITY_SECURE_MONITOR, 11)

#define SMC_FC_FIQ_RESUME SMC_FASTCALL_NR(SMC_ENTITY_SECURE_MONITOR, 12)

/**
 * SMC_FC_GET_SMP_MAX_CPUS - Find max number of cpus supported by Trusty.
 *
 * This call must be made before booting secondary cpus as Trusty
 * may support less number of cpus and crash if execution switches to them
 */
#define SMC_FC_GET_SMP_MAX_CPUS SMC_FASTCALL_NR(SMC_ENTITY_SECURE_MONITOR, 13)

/* TRUSTED_OS entity calls */
#define SMC_SC_VIRTIO_GET_DESCR SMC_STDCALL_NR(SMC_ENTITY_TRUSTED_OS, 20)
#define SMC_SC_VIRTIO_START SMC_STDCALL_NR(SMC_ENTITY_TRUSTED_OS, 21)
#define SMC_SC_VIRTIO_STOP SMC_STDCALL_NR(SMC_ENTITY_TRUSTED_OS, 22)

#define SMC_SC_VDEV_RESET SMC_STDCALL_NR(SMC_ENTITY_TRUSTED_OS, 23)
#define SMC_SC_VDEV_KICK_VQ SMC_STDCALL_NR(SMC_ENTITY_TRUSTED_OS, 24)
#define SMC_NC_VDEV_KICK_VQ SMC_STDCALL_NR(SMC_ENTITY_TRUSTED_OS, 25)

/* Simplified (Queueless) IPC interface */
#define SMC_SC_CREATE_QL_TIPC_DEV SMC_STDCALL_NR(SMC_ENTITY_TRUSTED_OS, 30)
#define SMC_SC_SHUTDOWN_QL_TIPC_DEV SMC_STDCALL_NR(SMC_ENTITY_TRUSTED_OS, 31)
#define SMC_SC_HANDLE_QL_TIPC_DEV_CMD SMC_STDCALL_NR(SMC_ENTITY_TRUSTED_OS, 32)
#define SMC_FC_HANDLE_QL_TIPC_DEV_CMD SMC_FASTCALL_NR(SMC_ENTITY_TRUSTED_OS, 32)

#endif /* __LIB_SM_SMCALL_H */
