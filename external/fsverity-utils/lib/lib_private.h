/* SPDX-License-Identifier: MIT */
/*
 * Private header for libfsverity
 *
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */
#ifndef LIB_LIB_PRIVATE_H
#define LIB_LIB_PRIVATE_H

#include "libfsverity.h"
#include "../common/common_defs.h"
#include "../common/fsverity_uapi.h"

#include <stdarg.h>

#define LIBEXPORT	__attribute__((visibility("default")))

/* The hash algorithm that libfsverity assumes when none is specified */
#define FS_VERITY_HASH_ALG_DEFAULT	FS_VERITY_HASH_ALG_SHA256

/* The block size that libfsverity assumes when none is specified */
#define FS_VERITY_BLOCK_SIZE_DEFAULT	4096

/* hash_algs.c */

struct fsverity_hash_alg {
	const char *name;
	unsigned int digest_size;
	unsigned int block_size;
	struct hash_ctx *(*create_ctx)(const struct fsverity_hash_alg *alg);
};

const struct fsverity_hash_alg *libfsverity_find_hash_alg_by_num(u32 alg_num);

struct hash_ctx {
	const struct fsverity_hash_alg *alg;
	void (*init)(struct hash_ctx *ctx);
	void (*update)(struct hash_ctx *ctx, const void *data, size_t size);
	void (*final)(struct hash_ctx *ctx, u8 *out);
	void (*free)(struct hash_ctx *ctx);
};

void libfsverity_hash_init(struct hash_ctx *ctx);
void libfsverity_hash_update(struct hash_ctx *ctx, const void *data,
			     size_t size);
void libfsverity_hash_final(struct hash_ctx *ctx, u8 *digest);
void libfsverity_hash_full(struct hash_ctx *ctx, const void *data, size_t size,
			   u8 *digest);
void libfsverity_free_hash_ctx(struct hash_ctx *ctx);

/* utils.c */

void *libfsverity_zalloc(size_t size);
void *libfsverity_memdup(const void *mem, size_t size);

__cold void
libfsverity_do_error_msg(const char *format, va_list va);

__printf(1, 2) __cold void
libfsverity_error_msg(const char *format, ...);

__cold void
libfsverity_warn_on(const char *condition, const char *file, int line);

#define WARN_ON(condition)						\
({									\
	bool c = (condition);						\
									\
	if (c)								\
		libfsverity_warn_on(#condition, __FILE__, __LINE__);	\
	c;								\
})

__cold void
libfsverity_bug_on(const char *condition, const char *file, int line);

#define BUG_ON(condition)						\
({									\
	bool c = (condition);						\
									\
	if (c)								\
		libfsverity_bug_on(#condition, __FILE__, __LINE__);	\
	c;								\
})

bool libfsverity_mem_is_zeroed(const void *mem, size_t size);

#endif /* LIB_LIB_PRIVATE_H */
