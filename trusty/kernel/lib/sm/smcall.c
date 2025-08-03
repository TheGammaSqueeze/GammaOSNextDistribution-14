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

/* Reference:
 * ARM document DEN 0028A: SMC CALLING CONVENTION
 * version: 0.9.0
 */

#include <arch/ops.h>
#include <compiler.h>
#include <debug.h>
#include <err.h>
#include <kernel/mutex.h>
#include <lib/sm.h>
#include <lib/sm/sm_err.h>
#include <lib/sm/smcall.h>
#include <lib/sm/trusty_sched_share_api.h>
#include <lk/init.h>
#include <string.h>
#include <trace.h>
#include <version.h>

#define LOCAL_TRACE 1

static mutex_t smc_table_lock = MUTEX_INITIAL_VALUE(smc_table_lock);

#define TRACE_SMC(msg, args)                                                \
    do {                                                                    \
        u_int _i;                                                           \
        LTRACEF("%s\n", msg);                                               \
        LTRACEF("SMC: 0x%x (%s entity %d function 0x%x)\n", (args)->smc_nr, \
                SMC_IS_FASTCALL(args->smc_nr) ? "Fastcall" : "Stdcall",     \
                SMC_ENTITY(args->smc_nr), SMC_FUNCTION(args->smc_nr));      \
        for (_i = 0; _i < SMC_NUM_PARAMS; _i++)                             \
            LTRACEF("param%d: 0x%x\n", _i, (args)->params[_i]);             \
    } while (0)

long smc_undefined(struct smc32_args* args) {
    TRACE_SMC("Undefined monitor call!", args);
    return SM_ERR_UNDEFINED_SMC;
}

/* Restarts should never be dispatched like this */
static long smc_restart_stdcall(struct smc32_args* args) {
    TRACE_SMC("Unexpected stdcall restart!", args);
    return SM_ERR_UNEXPECTED_RESTART;
}

/*
 * Switch to secure mode and return. This function does no work on its own,
 * but if an interrupt is pending, it will be handled, and can in turn trigger a
 * context switch that will perform other secure work.
 */
static long smc_nop_stdcall(struct smc32_args* args) {
    return 0;
}

/*
 * parameterized nop call handler
 */
static long smc_nop_secure_monitor(struct smc32_args* args) {
    return (!args->params[0]) ? 0 : SM_ERR_UNDEFINED_SMC;
}

static smc32_handler_t sm_stdcall_function_table[] = {
        [SMC_FUNCTION(SMC_SC_RESTART_LAST)] = smc_restart_stdcall,
        [SMC_FUNCTION(SMC_SC_LOCKED_NOP)] = smc_nop_stdcall,
        [SMC_FUNCTION(SMC_SC_RESTART_FIQ)] = smc_restart_stdcall,
        [SMC_FUNCTION(SMC_SC_SCHED_SHARE_REGISTER)] =
                smc_trusty_sched_share_register,
        [SMC_FUNCTION(SMC_SC_SCHED_SHARE_UNREGISTER)] =
                smc_trusty_sched_share_unregister,
        /* reserve slot in table, not called */
        [SMC_FUNCTION(SMC_SC_NOP)] = smc_undefined,
};

static long smc_stdcall_secure_monitor(struct smc32_args* args) {
    u_int function = SMC_FUNCTION(args->smc_nr);
    smc32_handler_t handler_fn = NULL;

    if (function < countof(sm_stdcall_function_table))
        handler_fn = sm_stdcall_function_table[function];

    if (!handler_fn)
        handler_fn = smc_undefined;

    return handler_fn(args);
}

static long smc_fiq_enter(struct smc32_args* args) {
    return sm_intc_fiq_enter();
}

#if !WITH_LIB_SM_MONITOR
static long smc_cpu_suspend(struct smc32_args* args) {
    lk_init_level_all(args->params[0] ? LK_INIT_FLAG_CPU_OFF
                                      : LK_INIT_FLAG_CPU_ENTER_IDLE);

    return 0;
}

static long smc_cpu_resume(struct smc32_args* args) {
    lk_init_level_all(args->params[0] ? LK_INIT_FLAG_CPU_ON
                                      : LK_INIT_FLAG_CPU_EXIT_IDLE);

    return 0;
}
#endif

static long smc_get_version_str(struct smc32_args* args) {
    int32_t index = (int32_t)args->params[0];
    size_t version_len = strlen(lk_version);

    if (index == -1)
        return version_len;

    if ((size_t)index >= version_len)
        return SM_ERR_INVALID_PARAMETERS;

    return lk_version[index];
}

static smc32_handler_t sm_fastcall_function_table[] = {
        [SMC_FUNCTION(SMC_FC_GET_NEXT_IRQ)] = smc_intc_get_next_irq,
        [SMC_FUNCTION(SMC_FC_FIQ_ENTER)] = smc_fiq_enter,
#if !WITH_LIB_SM_MONITOR
        [SMC_FUNCTION(SMC_FC_CPU_SUSPEND)] = smc_cpu_suspend,
        [SMC_FUNCTION(SMC_FC_CPU_RESUME)] = smc_cpu_resume,
#endif
        [SMC_FUNCTION(SMC_FC_GET_VERSION_STR)] = smc_get_version_str,
        [SMC_FUNCTION(SMC_FC_API_VERSION)] = smc_sm_api_version,
        [SMC_FUNCTION(SMC_FC_GET_SMP_MAX_CPUS)] = smc_get_smp_max_cpus,
};

static long smc_fastcall_secure_monitor(struct smc32_args* args) {
    smc32_handler_t func = NULL;
    uint16_t index = SMC_FUNCTION(args->smc_nr);

    if (index < countof(sm_fastcall_function_table)) {
        func = sm_fastcall_function_table[index];
    }

    if (func == NULL) {
        func = smc_undefined;
    }

    return func(args);
}

/* SMC dispatch tables */
smc32_handler_t sm_fastcall_table[SMC_NUM_ENTITIES] = {
        [0 ... SMC_ENTITY_SECURE_MONITOR - 1] = smc_undefined,
        [SMC_ENTITY_SECURE_MONITOR] = smc_fastcall_secure_monitor,
        [SMC_ENTITY_SECURE_MONITOR + 1 ... SMC_NUM_ENTITIES - 1] =
                smc_undefined};

smc32_handler_t sm_nopcall_table[SMC_NUM_ENTITIES] = {
        [0] = smc_nop_secure_monitor,
        [1 ... SMC_NUM_ENTITIES - 1] = smc_undefined};

smc32_handler_t sm_stdcall_table[SMC_NUM_ENTITIES] = {
        [0 ... SMC_ENTITY_SECURE_MONITOR - 1] = smc_undefined,
        [SMC_ENTITY_SECURE_MONITOR] = smc_stdcall_secure_monitor,
        [SMC_ENTITY_SECURE_MONITOR + 1 ... SMC_NUM_ENTITIES - 1] =
                smc_undefined};

status_t sm_register_entity(uint entity_nr, struct smc32_entity* entity) {
    status_t err = NO_ERROR;

    if (entity_nr >= SMC_NUM_ENTITIES)
        return ERR_INVALID_ARGS;

    if (entity_nr >= SMC_ENTITY_RESERVED && entity_nr < SMC_ENTITY_TRUSTED_APP)
        return ERR_NOT_ALLOWED;

    if (!entity)
        return ERR_INVALID_ARGS;

    if (!entity->fastcall_handler && !entity->stdcall_handler &&
        !entity->nopcall_handler)
        return ERR_NOT_VALID;

    mutex_acquire(&smc_table_lock);

    /* Check if entity is already claimed */
    if (sm_fastcall_table[entity_nr] != smc_undefined ||
        sm_nopcall_table[entity_nr] != smc_undefined ||
        sm_stdcall_table[entity_nr] != smc_undefined) {
        err = ERR_ALREADY_EXISTS;
        goto unlock;
    }

    if (entity->fastcall_handler)
        sm_fastcall_table[entity_nr] = entity->fastcall_handler;

    if (entity->nopcall_handler)
        sm_nopcall_table[entity_nr] = entity->nopcall_handler;

    if (entity->stdcall_handler)
        sm_stdcall_table[entity_nr] = entity->stdcall_handler;
unlock:
    mutex_release(&smc_table_lock);
    return err;
}
