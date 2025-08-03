/*
 * Copyright (c) 2019 LK Trusty Authors. All Rights Reserved.
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
#include <arch/x86.h>
#include <arch/usercopy.h>
#include <assert.h>
#include <string.h>
#include <kernel/vm.h>
#include <kernel/thread.h>
#include <err.h>

static bool is_permission_legal(vmm_aspace_t *vmm_aspace, vaddr_t user_addr,
                                bool copy_to) {
    arch_aspace_t aspace;
    status_t ret = NO_ERROR;
    uint flags = 0;

    aspace = vmm_aspace->arch_aspace;

    ret = arch_mmu_query(&aspace, user_addr, NULL, &flags);
    if (NO_ERROR != ret) {
        return false;
    }

    /*
     * ARCH_MMU_FLAG_PERM_USER should be always set.
     * If copies from user, no more condition check.
     * If copies to user, ARCH_MMU_FLAG_PERM_RO must not be set.
     */
    if (!(flags & ARCH_MMU_FLAG_PERM_USER)) {
        return false;
    }
    if (copy_to && (flags & ARCH_MMU_FLAG_PERM_RO)) {
        return false;
    }
    return true;
}

static status_t is_valid_to_copy(vmm_aspace_t *aspace, vaddr_t addr,
                                 bool copy_to, size_t len) {
    vmm_region_t* region;
    vaddr_t src = addr;
    size_t rest = len;
    size_t check_len = len;
    status_t ret = NO_ERROR;

    while (rest) {
        region = vmm_find_region(aspace, src);

        if (region && is_permission_legal(aspace, src, copy_to)) {
            check_len = MIN(rest, (region->obj_slice.size - (src - region->base)));
        } else {
            ret = ERR_FAULT;
            break;
        }

        rest = rest - check_len;
        src += check_len;
    }

    return ret;
}

status_t arch_copy_from_user(void *kdest, user_addr_t usrc, size_t len)
{
    status_t ret = NO_ERROR;
    vmm_aspace_t *aspace = get_current_thread()->aspace;

    vmm_lock_aspace(aspace);

    /*
     * The address space needs to be locked so that is does not change
     * from the start of is_valid_to_copy to the end of memcpy
     */
    ret = is_valid_to_copy(aspace, usrc, false, len);

    if (NO_ERROR == ret) {
        /*
         * Add memory fence to avoid speculative execution.
         * Ensure nothing would be copied unless if condition check
         * passed, since out-of-order might lead to data leakage.
         */
        smp_mb();
        x86_allow_explicit_smap();
        memcpy(kdest, (void *)usrc, len);
        x86_disallow_explicit_smap();
    } else {
        memset(kdest, 0, len);
    }

    vmm_unlock_aspace(aspace);

    return ret;
}

status_t arch_copy_to_user(user_addr_t udest, const void *ksrc, size_t len)
{
    status_t ret = NO_ERROR;
    vmm_aspace_t *aspace = get_current_thread()->aspace;

    vmm_lock_aspace(aspace);

    /*
     * The address space needs to be locked so that is does not change
     * from the start of is_valid_to_copy to the end of memcpy
     */
    ret = is_valid_to_copy(aspace, udest, true, len);

    if (NO_ERROR == ret) {
        /*
         * Add memory fence to avoid speculative execution.
         * Ensure nothing would be copied unless if condition check
         * passed, since out-of-order might lead to data leakage.
         */
        smp_mb();
        x86_allow_explicit_smap();
        memcpy((void *)udest, ksrc, len);
        x86_disallow_explicit_smap();
    }

    vmm_unlock_aspace(aspace);

    return ret;
}

static ssize_t x86_user_strlen(vmm_aspace_t *aspace, const char *src_in) {
    size_t scan_len = 0;
    vmm_region_t *region;
    bool continue_scan = false;
    const char *src = src_in;

    do {
        region = vmm_find_region(aspace, (vaddr_t)src);
        if (!region) {
            return ERR_FAULT;
        }

        if (!is_permission_legal(aspace, (vaddr_t)src, false)) {
            return ERR_FAULT;
        }

        scan_len = region->obj_slice.size - ((vaddr_t)src - region->base);
        DEBUG_ASSERT(scan_len > 0);

        x86_allow_explicit_smap();
        while(scan_len && (*src++ != '\0')) {
            scan_len--;
        }

        continue_scan = *(src-1) != '\0';

        x86_disallow_explicit_smap();
    } while(continue_scan);

    return src - src_in  - 1;
}

ssize_t arch_strlcpy_from_user(char *kdst, user_addr_t usrc, size_t len)
{
    size_t copy_len = len;
    ssize_t user_str_len = 0;
    char* src = (char *)usrc;
    char* dest = kdst;
    vmm_aspace_t *aspace = get_current_thread()->aspace;

    vmm_lock_aspace(aspace);

    /*
     * The address space needs to be locked so that is does not change
     * from the start of is_valid_to_copy to the end of string copy.
     *
     * Check whether user string is legal to copy, if it is legal to
     * copy, returns string length of user string. If illegal to copy,
     * returns ERR_FAULT.
     */
    user_str_len = x86_user_strlen(aspace, src);

    if ((len == 0) || (user_str_len < 0)) {
        memset(kdst, 0, len);
        goto err;
    }

    /*
     * Calculate length of non-null characters, null terminator would be
     * always placed at the destination string.
     */
    copy_len = MIN((size_t)user_str_len, len - 1);

    /*
     * Add memory fence to avoid speculative execution.
     * Ensure nothing would be copied unless if condition check
     * passed, since out-of-order might lead to data leakage.
     */
    smp_mb();

    x86_allow_explicit_smap();
    while (copy_len) {
        if ((*dest++ = *src++) == '\0') {
            break;
        }
        copy_len--;
    }
    x86_disallow_explicit_smap();

    /* Ensure kernel buffer is always 0 terminated. */
    *dest = '\0';

    /*
     * If the pages readable from user-space contain a 0 terminated string,
     * strlcpy_from_user should return the length of that string.
     * If content in user pages changed so that the new string fit in the
     * buffer. And returns actually length copied.
     */
    if (copy_len) {
        user_str_len = (dest - kdst - 1);
    }

err:
    vmm_unlock_aspace(aspace);

    return user_str_len;
}
