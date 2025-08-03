/*
 * Copyright (c) 2020, ARM Limited and Contributors. All rights reserved.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

#include <assert.h>
#include <common/debug.h>
#include <errno.h>
#include <inttypes.h>
#include <platform_def.h>
#include <stddef.h>
#include <trusty/arm_ffa.h>
#include <trusty/ffa_helpers.h>

#define NS_DRAM0_BITMAP_SIZE DIV_ROUND_UP_2EVAL(NS_DRAM0_SIZE, PAGE_SIZE * 8)
static uint8_t trusty_shmem_shared[NS_DRAM0_BITMAP_SIZE];
static uint8_t trusty_shmem_secure[NS_DRAM0_BITMAP_SIZE];

static bool read_bit(uint8_t *bit_mask, size_t bit_num)
{
	size_t i = bit_num / 8;
	size_t m = 1U << (bit_num % 8);
	return !!(bit_mask[i] & m);
}

static void write_bit(uint8_t *bit_mask, size_t bit_num, bool val)
{
	size_t i = bit_num / 8;
	size_t m = 1U << (bit_num % 8);
	if (val) {
		bit_mask[i] |= m;
	} else {
		bit_mask[i] &= ~m;
	}
}

static int mem_set_shared(bool shared, bool secure, unsigned long long base_pa,
			  size_t size)
{
	unsigned long long page;
	size_t i;

	assert((size % PAGE_SIZE) == 0);

	if (base_pa < NS_DRAM0_BASE ||
	    (base_pa - NS_DRAM0_BASE) + size > NS_DRAM0_SIZE) {
		NOTICE("%s(%d, %d, 0x%llx, 0x%zx) invalid address range\n",
		       __func__, shared, secure, base_pa, size);
		return -EINVAL;
	}
	for (page = (base_pa - NS_DRAM0_BASE) / PAGE_SIZE, i = 0;
	     i < size / PAGE_SIZE; page++, i++) {
		bool was_shared = read_bit(trusty_shmem_shared, page);
		bool was_secure = read_bit(trusty_shmem_secure, page);
		assert(!was_secure || was_shared);
		if (was_shared == shared) {
			/* already shared or reclaimed */
			NOTICE("%s(%d, %d, 0x%llx, 0x%zx) already set\n",
			       __func__, shared, secure, base_pa, size);
			goto err;
		}
		assert(was_secure == (secure && !shared));
		write_bit(trusty_shmem_shared, page, shared);
		if (secure) {
			/*
			 * For emulator testing purposes the memory
			 * is marked as secure, and communicated to
			 * Trusty as such, even though it is not.
			 */
			write_bit(trusty_shmem_secure, page, shared);
		}
	}
	return 0;

err:
	while (i > 0) {
		i--;
		page--;
		write_bit(trusty_shmem_shared, page, !shared);
		if (secure) {
			write_bit(trusty_shmem_secure, page, !shared);
		}
	}
	return -EBUSY;
}


int plat_mem_set_shared(struct ffa_mtd *mtd, bool shared)
{
	struct ffa_comp_mrd *comp = trusty_ffa_mtd_get_comp_mrd(mtd);
	size_t count = comp->address_range_count;
	struct ffa_cons_mrd *cons_mrd;
	int ret = 0;
	size_t i;
	bool secure;

	secure = trusty_ffa_should_be_secure(mtd);

	for (i = 0, cons_mrd = comp->address_range_array; i < count;
	     i++, cons_mrd++) {
		ret = mem_set_shared(shared, secure, cons_mrd->address,
				     cons_mrd->page_count * PAGE_SIZE);
		if (ret) {
			goto err;
		}
	}

	if (secure) {
		mtd->memory_region_attributes &= ~FFA_MEM_ATTR_NONSECURE;
	}
	return 0;

err:
	NOTICE("%s: %zu/%zu: failed to set shared %d secure %d for %"
	       PRIx64 " (%x)\n",
	       __func__, i, count, shared, secure, cons_mrd->address,
	       cons_mrd->page_count);
	while (i > 0) {
		i--;
		cons_mrd--;
		if (mem_set_shared(!shared, secure, cons_mrd->address,
				   cons_mrd->page_count * PAGE_SIZE)) {
			/* Failed to revert change */
			panic();
		}
	}
	return ret;
}
