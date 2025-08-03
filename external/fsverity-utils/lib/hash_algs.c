// SPDX-License-Identifier: MIT
/*
 * fs-verity hash algorithms
 *
 * Copyright 2018 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

#include "lib_private.h"

#include <openssl/evp.h>
#include <stdlib.h>
#include <string.h>

/* ========== libcrypto (OpenSSL) wrappers ========== */

struct openssl_hash_ctx {
	struct hash_ctx base;	/* must be first */
	EVP_MD_CTX *md_ctx;
	const EVP_MD *md;
};

static void openssl_digest_init(struct hash_ctx *_ctx)
{
	struct openssl_hash_ctx *ctx = (void *)_ctx;
	int ret;

	ret = EVP_DigestInit_ex(ctx->md_ctx, ctx->md, NULL);
	BUG_ON(ret != 1);
}

static void openssl_digest_update(struct hash_ctx *_ctx,
				  const void *data, size_t size)
{
	struct openssl_hash_ctx *ctx = (void *)_ctx;
	int ret;

	ret = EVP_DigestUpdate(ctx->md_ctx, data, size);
	BUG_ON(ret != 1);
}

static void openssl_digest_final(struct hash_ctx *_ctx, u8 *digest)
{
	struct openssl_hash_ctx *ctx = (void *)_ctx;
	int ret;

	ret = EVP_DigestFinal_ex(ctx->md_ctx, digest, NULL);
	BUG_ON(ret != 1);
}

static void openssl_digest_ctx_free(struct hash_ctx *_ctx)
{
	struct openssl_hash_ctx *ctx = (void *)_ctx;

	/*
	 * OpenSSL 1.1.0 renamed EVP_MD_CTX_destroy() to EVP_MD_CTX_free() but
	 * kept the old name as a macro.  Use the old name for compatibility
	 * with older OpenSSL versions.
	 */
	EVP_MD_CTX_destroy(ctx->md_ctx);
	free(ctx);
}

static struct hash_ctx *
openssl_digest_ctx_create(const struct fsverity_hash_alg *alg, const EVP_MD *md)
{
	struct openssl_hash_ctx *ctx;

	ctx = libfsverity_zalloc(sizeof(*ctx));
	if (!ctx)
		return NULL;

	ctx->base.alg = alg;
	ctx->base.init = openssl_digest_init;
	ctx->base.update = openssl_digest_update;
	ctx->base.final = openssl_digest_final;
	ctx->base.free = openssl_digest_ctx_free;
	/*
	 * OpenSSL 1.1.0 renamed EVP_MD_CTX_create() to EVP_MD_CTX_new() but
	 * kept the old name as a macro.  Use the old name for compatibility
	 * with older OpenSSL versions.
	 */
	ctx->md_ctx = EVP_MD_CTX_create();
	if (!ctx->md_ctx) {
		libfsverity_error_msg("failed to allocate EVP_MD_CTX");
		goto err1;
	}

	ctx->md = md;
	if (WARN_ON(EVP_MD_size(md) != alg->digest_size))
		goto err2;

	return &ctx->base;

err2:
	EVP_MD_CTX_destroy(ctx->md_ctx);
err1:
	free(ctx);
	return NULL;
}

static struct hash_ctx *create_sha256_ctx(const struct fsverity_hash_alg *alg)
{
	return openssl_digest_ctx_create(alg, EVP_sha256());
}

static struct hash_ctx *create_sha512_ctx(const struct fsverity_hash_alg *alg)
{
	return openssl_digest_ctx_create(alg, EVP_sha512());
}

/* ========== Hash utilities ========== */

void libfsverity_hash_init(struct hash_ctx *ctx)
{
	ctx->init(ctx);
}

void libfsverity_hash_update(struct hash_ctx *ctx, const void *data,
			     size_t size)
{
	ctx->update(ctx, data, size);
}

void libfsverity_hash_final(struct hash_ctx *ctx, u8 *digest)
{
	ctx->final(ctx, digest);
}

/* ->init(), ->update(), and ->final() all in one step */
void libfsverity_hash_full(struct hash_ctx *ctx, const void *data, size_t size,
			   u8 *digest)
{
	libfsverity_hash_init(ctx);
	libfsverity_hash_update(ctx, data, size);
	libfsverity_hash_final(ctx, digest);
}

void libfsverity_free_hash_ctx(struct hash_ctx *ctx)
{
	if (ctx)
		ctx->free(ctx);
}

/* ========== Hash algorithm definitions ========== */

static const struct fsverity_hash_alg fsverity_hash_algs[] = {
	[FS_VERITY_HASH_ALG_SHA256] = {
		.name = "sha256",
		.digest_size = 32,
		.block_size = 64,
		.create_ctx = create_sha256_ctx,
	},
	[FS_VERITY_HASH_ALG_SHA512] = {
		.name = "sha512",
		.digest_size = 64,
		.block_size = 128,
		.create_ctx = create_sha512_ctx,
	},
};

LIBEXPORT u32
libfsverity_find_hash_alg_by_name(const char *name)
{
	int i;

	if (!name)
		return 0;

	for (i = 1; i < ARRAY_SIZE(fsverity_hash_algs); i++) {
		if (fsverity_hash_algs[i].name &&
		    !strcmp(name, fsverity_hash_algs[i].name))
			return i;
	}
	return 0;
}

const struct fsverity_hash_alg *libfsverity_find_hash_alg_by_num(u32 alg_num)
{
	if (alg_num < ARRAY_SIZE(fsverity_hash_algs) &&
	    fsverity_hash_algs[alg_num].name)
		return &fsverity_hash_algs[alg_num];

	return NULL;
}

LIBEXPORT int
libfsverity_get_digest_size(u32 alg_num)
{
	const struct fsverity_hash_alg *alg =
		libfsverity_find_hash_alg_by_num(alg_num);

	return alg ? alg->digest_size : -1;
}

LIBEXPORT const char *
libfsverity_get_hash_name(u32 alg_num)
{
	const struct fsverity_hash_alg *alg =
		libfsverity_find_hash_alg_by_num(alg_num);

	return alg ? alg->name : NULL;
}
