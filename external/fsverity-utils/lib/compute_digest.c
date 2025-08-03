// SPDX-License-Identifier: MIT
/*
 * Implementation of libfsverity_compute_digest().
 *
 * Copyright 2018 Google LLC
 * Copyright (C) 2020 Facebook
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

#include "lib_private.h"

#include <stdlib.h>
#include <string.h>

#define FS_VERITY_MAX_LEVELS	64

struct block_buffer {
	u32 filled;
	u8 *data;
};

/*
 * Hash a block, writing the result to the next level's pending block buffer.
 */
static void hash_one_block(struct hash_ctx *hash, struct block_buffer *cur,
			   u32 block_size, const u8 *salt, u32 salt_size)
{
	struct block_buffer *next = cur + 1;

	/* Zero-pad the block if it's shorter than block_size. */
	memset(&cur->data[cur->filled], 0, block_size - cur->filled);

	libfsverity_hash_init(hash);
	libfsverity_hash_update(hash, salt, salt_size);
	libfsverity_hash_update(hash, cur->data, block_size);
	libfsverity_hash_final(hash, &next->data[next->filled]);

	next->filled += hash->alg->digest_size;
	cur->filled = 0;
}

static bool block_is_full(const struct block_buffer *block, u32 block_size,
			  struct hash_ctx *hash)
{
	/* Would the next hash put us over the limit? */
	return block->filled + hash->alg->digest_size > block_size;
}

static int report_merkle_tree_size(const struct libfsverity_metadata_callbacks *cbs,
				   u64 size)
{
	if (cbs && cbs->merkle_tree_size) {
		int err = cbs->merkle_tree_size(cbs->ctx, size);

		if (err) {
			libfsverity_error_msg("error processing Merkle tree size");
			return err;
		}
	}
	return 0;
}

static int report_merkle_tree_block(const struct libfsverity_metadata_callbacks *cbs,
				    const struct block_buffer *block,
				    u32 block_size, u64 *level_offset)
{

	if (cbs && cbs->merkle_tree_block) {
		int err = cbs->merkle_tree_block(cbs->ctx, block->data,
						 block_size,
						 *level_offset * block_size);

		if (err) {
			libfsverity_error_msg("error processing Merkle tree block");
			return err;
		}
		(*level_offset)++;
	}
	return 0;
}

static int report_descriptor(const struct libfsverity_metadata_callbacks *cbs,
			     const void *descriptor, size_t size)
{
	if (cbs && cbs->descriptor) {
		int err = cbs->descriptor(cbs->ctx, descriptor, size);

		if (err) {
			libfsverity_error_msg("error processing fs-verity descriptor");
			return err;
		}
	}
	return 0;
}

/*
 * Compute the file's Merkle tree root hash using the given hash algorithm,
 * block size, and salt.
 */
static int compute_root_hash(void *fd, libfsverity_read_fn_t read_fn,
			     u64 file_size, struct hash_ctx *hash,
			     u32 block_size, const u8 *salt, u32 salt_size,
			     const struct libfsverity_metadata_callbacks *metadata_cbs,
			     u8 *root_hash)
{
	const u32 hashes_per_block = block_size / hash->alg->digest_size;
	const u32 padded_salt_size = roundup(salt_size, hash->alg->block_size);
	u8 *padded_salt = NULL;
	u64 blocks;
	int num_levels = 0;
	int level;
	u64 level_offset[FS_VERITY_MAX_LEVELS];
	struct block_buffer _buffers[1 + FS_VERITY_MAX_LEVELS + 1] = {};
	struct block_buffer *buffers = &_buffers[1];
	u64 offset;
	int err = 0;

	/* Root hash of empty file is all 0's */
	if (file_size == 0) {
		memset(root_hash, 0, hash->alg->digest_size);
		return report_merkle_tree_size(metadata_cbs, 0);
	}

	if (salt_size != 0) {
		padded_salt = libfsverity_zalloc(padded_salt_size);
		if (!padded_salt)
			return -ENOMEM;
		memcpy(padded_salt, salt, salt_size);
	}

	/* Compute number of levels and the number of blocks in each level. */
	blocks = DIV_ROUND_UP(file_size, block_size);
	while (blocks > 1)  {
		if (WARN_ON(num_levels >= FS_VERITY_MAX_LEVELS)) {
			err = -EINVAL;
			goto out;
		}
		blocks = DIV_ROUND_UP(blocks, hashes_per_block);
		/*
		 * Temporarily use level_offset[] to store the number of blocks
		 * in each level.  It will be overwritten later.
		 */
		level_offset[num_levels++] = blocks;
	}

	/*
	 * Compute the starting block of each level, using the convention where
	 * the root level is first, i.e. the convention used by
	 * FS_IOC_READ_VERITY_METADATA.  At the same time, compute the total
	 * size of the Merkle tree.  These values are only needed for the
	 * metadata callbacks (if they were given), as the hash computation
	 * itself doesn't prescribe an ordering of the levels and doesn't
	 * prescribe any special meaning to the total size of the Merkle tree.
	 */
	offset = 0;
	for (level = num_levels - 1; level >= 0; level--) {
		blocks = level_offset[level];
		level_offset[level] = offset;
		offset += blocks;
	}
	err = report_merkle_tree_size(metadata_cbs, offset * block_size);
	if (err)
		goto out;

	/*
	 * Allocate the block buffers.  Buffer "-1" is for data blocks.
	 * Buffers 0 <= level < num_levels are for the actual tree levels.
	 * Buffer 'num_levels' is for the root hash.
	 */
	for (level = -1; level < num_levels; level++) {
		buffers[level].data = libfsverity_zalloc(block_size);
		if (!buffers[level].data) {
			err = -ENOMEM;
			goto out;
		}
	}
	buffers[num_levels].data = root_hash;

	/* Hash each data block, also hashing the tree blocks as they fill up */
	for (offset = 0; offset < file_size; offset += block_size) {
		buffers[-1].filled = min(block_size, file_size - offset);

		err = read_fn(fd, buffers[-1].data, buffers[-1].filled);
		if (err) {
			libfsverity_error_msg("error reading file");
			goto out;
		}

		hash_one_block(hash, &buffers[-1], block_size,
			       padded_salt, padded_salt_size);
		for (level = 0; level < num_levels; level++) {
			if (!block_is_full(&buffers[level], block_size, hash))
				break;
			hash_one_block(hash, &buffers[level], block_size,
				       padded_salt, padded_salt_size);
			err = report_merkle_tree_block(metadata_cbs,
						       &buffers[level],
						       block_size,
						       &level_offset[level]);
			if (err)
				goto out;
		}
	}
	/* Finish all nonempty pending tree blocks */
	for (level = 0; level < num_levels; level++) {
		if (buffers[level].filled != 0) {
			hash_one_block(hash, &buffers[level], block_size,
				       padded_salt, padded_salt_size);
			err = report_merkle_tree_block(metadata_cbs,
						       &buffers[level],
						       block_size,
						       &level_offset[level]);
			if (err)
				goto out;
		}
	}

	/* Root hash was filled by the last call to hash_one_block() */
	if (WARN_ON(buffers[num_levels].filled != hash->alg->digest_size)) {
		err = -EINVAL;
		goto out;
	}
	err = 0;
out:
	for (level = -1; level < num_levels; level++)
		free(buffers[level].data);
	free(padded_salt);
	return err;
}

LIBEXPORT int
libfsverity_compute_digest(void *fd, libfsverity_read_fn_t read_fn,
			   const struct libfsverity_merkle_tree_params *params,
			   struct libfsverity_digest **digest_ret)
{
	u32 alg_num;
	u32 block_size;
	const struct fsverity_hash_alg *hash_alg;
	struct hash_ctx *hash = NULL;
	struct libfsverity_digest *digest;
	struct fsverity_descriptor desc;
	int err;

	if (!read_fn || !params || !digest_ret) {
		libfsverity_error_msg("missing required parameters for compute_digest");
		return -EINVAL;
	}
	if (params->version != 1) {
		libfsverity_error_msg("unsupported version (%u)",
				      params->version);
		return -EINVAL;
	}

	alg_num = params->hash_algorithm ?: FS_VERITY_HASH_ALG_DEFAULT;
	block_size = params->block_size ?: FS_VERITY_BLOCK_SIZE_DEFAULT;

	if (!is_power_of_2(block_size)) {
		libfsverity_error_msg("unsupported block size (%u)",
				      block_size);
		return -EINVAL;
	}
	if (params->salt_size > sizeof(desc.salt)) {
		libfsverity_error_msg("unsupported salt size (%u)",
				      params->salt_size);
		return -EINVAL;
	}
	if (params->salt_size && !params->salt) {
		libfsverity_error_msg("salt_size specified, but salt is NULL");
		return -EINVAL;
	}
	if (!libfsverity_mem_is_zeroed(params->reserved1,
				       sizeof(params->reserved1)) ||
	    !libfsverity_mem_is_zeroed(params->reserved2,
				       sizeof(params->reserved2))) {
		libfsverity_error_msg("reserved bits set in merkle_tree_params");
		return -EINVAL;
	}

	hash_alg = libfsverity_find_hash_alg_by_num(alg_num);
	if (!hash_alg) {
		libfsverity_error_msg("unknown hash algorithm: %u", alg_num);
		return -EINVAL;
	}

	if (block_size < 2 * hash_alg->digest_size) {
		libfsverity_error_msg("block size (%u) too small for hash algorithm %s",
				      block_size, hash_alg->name);
		return -EINVAL;
	}

	hash = hash_alg->create_ctx(hash_alg);
	if (!hash)
		return -ENOMEM;

	memset(&desc, 0, sizeof(desc));
	desc.version = 1;
	desc.hash_algorithm = alg_num;
	desc.log_blocksize = ilog2(block_size);
	desc.data_size = cpu_to_le64(params->file_size);
	if (params->salt_size != 0) {
		memcpy(desc.salt, params->salt, params->salt_size);
		desc.salt_size = params->salt_size;
	}

	err = compute_root_hash(fd, read_fn, params->file_size, hash,
				block_size, params->salt, params->salt_size,
				params->metadata_callbacks, desc.root_hash);
	if (err)
		goto out;

	err = report_descriptor(params->metadata_callbacks,
				&desc, sizeof(desc));
	if (err)
		goto out;

	digest = libfsverity_zalloc(sizeof(*digest) + hash_alg->digest_size);
	if (!digest) {
		err = -ENOMEM;
		goto out;
	}
	digest->digest_algorithm = alg_num;
	digest->digest_size = hash_alg->digest_size;
	libfsverity_hash_full(hash, &desc, sizeof(desc), digest->digest);
	*digest_ret = digest;
	err = 0;
out:
	libfsverity_free_hash_ctx(hash);
	return err;
}
