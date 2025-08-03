/*
 * Copyright (c) 2016 Google Inc. All rights reserved
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

#include <arch/arch_ops.h>
#include <arch/aspace.h>
#include <assert.h>
#include <bits.h>
#include <trace.h>
#include <kernel/thread.h>
#include <inttypes.h>

#define LOCAL_TRACE 0

#if ARCH_ASPACE_HAS_ASID

static uint64_t last_asid;
static bool old_asid_active;
static struct arch_aspace *active_aspace[SMP_MAX_CPUS];
static uint64_t active_asid_version[SMP_MAX_CPUS];

static bool vmm_asid_current(struct arch_aspace *aspace, uint64_t ref,
                             uint64_t asid_mask)
{
    if (!aspace) {
        return true;
    }
    if (!aspace->asid) {
        LTRACEF("unallocated asid for aspace %p\n", aspace);
        return false;
    }
    if (((aspace->asid ^ ref) & ~asid_mask)) {
        LTRACEF("old asid for aspace %p, 0x%" PRIxASID ", ref 0x%" PRIx64 ", mask 0x%" PRIx64 "\n",
                aspace, aspace->asid, ref, asid_mask);
        return false;
    }
    return true;
}

static void vmm_asid_allocate(struct arch_aspace *aspace, uint cpu,
                              uint64_t asid_mask)
{
    uint i;

    active_aspace[cpu] = aspace;

    if (vmm_asid_current(aspace, last_asid, asid_mask)) {
        return;
    }

    if (old_asid_active) {
        for (i = 0; i < SMP_MAX_CPUS; i++) {
            if (i == cpu) {
                continue;
            }
            if (active_aspace[i] == aspace) {
                /*
                 * Don't allocate a new asid if aspace is active on another
                 * CPU. That CPU could perform asid specific tlb invalidate
                 * broadcasts, that would be missed if the asid does not match.
                 */
                return;
            }
        }
    }

    aspace->asid = ++last_asid;
    LTRACEF("cpu %d: aspace %p, new asid 0x%" PRIxASID "\n", cpu, aspace, aspace->asid);
    if (!(last_asid & asid_mask)) {
        old_asid_active = true;
    }

    if (old_asid_active) {
        i = 0;
        old_asid_active = false;
        while (i < SMP_MAX_CPUS) {
            if (!vmm_asid_current(active_aspace[i], last_asid, asid_mask)) {
                old_asid_active = true;
                if (!((active_aspace[i]->asid ^ last_asid) & asid_mask)) {
                    /* Skip asid in use by other CPUs */
                    aspace->asid = ++last_asid;
                    LTRACEF("cpu %d: conflict asid 0x%" PRIxASID " at cpu %d, new asid 0x%" PRIxASID "\n",
                            cpu, active_aspace[i]->asid, i, aspace->asid);
                    i = 0;
                    continue;
                }
            }
            i++;
        }
    }
}

/**
 * vmm_asid_activate - Activate asid for aspace
 * @aspace:     Arch aspace struct where asid is stored, or %NULL if no aspace
 *              should be active.
 * @asid_bits:  Number of bits in asid used by hardware.
 *
 * Called by arch_mmu_context_switch to allocate and activate an asid for
 * @aspace.
 *
 * Return: %true TLBs needs to be flushed on this cpu, %false otherwise.
 */
bool vmm_asid_activate(struct arch_aspace *aspace, uint asid_bits)
{
    uint cpu = arch_curr_cpu_num();
    uint64_t asid_mask = BIT_MASK(asid_bits);

    DEBUG_ASSERT(thread_lock_held());

    vmm_asid_allocate(aspace, cpu, asid_mask);

    if (vmm_asid_current(aspace, active_asid_version[cpu], asid_mask)) {
        return false;
    }
    DEBUG_ASSERT(aspace); /* NULL aspace is always current */

    active_asid_version[cpu] = aspace->asid & ~asid_mask;
    LTRACEF("cpu %d: aspace %p, asid 0x%" PRIxASID "\n", cpu, aspace, aspace->asid);

    return true;
}

#endif
