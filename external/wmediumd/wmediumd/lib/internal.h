/*
 * Copyright (C) 2018 - 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
#ifndef _USFSTL_INTERNAL_H_
#define _USFSTL_INTERNAL_H_
#include <stdarg.h>
#include <stdbool.h>
#include <setjmp.h>
#include <stdint.h>
#include <usfstl/sched.h>

/* byteswap helper */
#define __swap32(v)			\
	((((v) & 0xff000000) >> 24) |	\
	 (((v) & 0x00ff0000) >>  8) |	\
	 (((v) & 0x0000ff00) <<  8) |	\
	 (((v) & 0x000000ff) << 24))

static inline uint32_t swap32(uint32_t v)
{
	return __swap32(v);
}

#define DIV_ROUND_UP(a, b) ({	\
	typeof(a) _a = a;	\
	typeof(b) _b = b;	\
	(_a + _b - 1) / _b;	\
})

/* scheduler */
void _usfstl_sched_set_time(struct usfstl_scheduler *sched, uint64_t time);

/* main loop */
extern struct usfstl_list g_usfstl_loop_entries;

#endif // _USFSTL_INTERNAL_H_
