// SPDX-License-Identifier: MIT
/*
 * Test libfsverity_compute_digest().
 *
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

#include "utils.h"

#include <ctype.h>
#include <inttypes.h>
#include <openssl/sha.h>

struct mem_file {
	u8 *data;
	size_t size;
	size_t offset;
};

static int read_fn(void *fd, void *buf, size_t count)
{
	struct mem_file *f = fd;

	ASSERT(count <= f->size - f->offset);
	memcpy(buf, &f->data[f->offset], count);
	f->offset += count;
	return 0;
}

static int error_read_fn(void *fd __attribute__((unused)),
			 void *buf __attribute__((unused)),
			 size_t count __attribute__((unused)))
{
	return -EIO;
}

static int zeroes_read_fn(void *fd __attribute__((unused)),
			  void *buf, size_t count)
{
	memset(buf, 0, count);
	return 0;
}

static const struct test_case {
	u32 hash_algorithm;
	u32 block_size;
	const char *salt;
	u64 file_size;
	const char *digest;
} test_cases[] = {
	{ /* large file */
		.hash_algorithm = FS_VERITY_HASH_ALG_SHA256,
		.file_size = 1000000,
		.block_size = 4096,
		.digest = "\x48\xdf\x0c\x46\x23\x29\xcd\x87"
			  "\x96\x61\xbd\x05\xb3\x9a\xa8\x1b"
			  "\x05\xcc\x16\xaf\xd2\x7a\x71\x96"
			  "\xa5\x59\xda\x83\x53\x1d\x39\xd9",
	}, { /* small file */
		.hash_algorithm = FS_VERITY_HASH_ALG_SHA256,
		.file_size = 100000,
		.block_size = 4096,
		.digest = "\xf2\x09\x6a\x36\xc5\xcd\xca\x4f"
			  "\xa3\x3e\xe8\x85\x28\x33\x15\x0b"
			  "\xb3\x24\x99\x2e\x54\x17\xa9\xd5"
			  "\x71\xf1\xbf\xff\xf7\x3b\x9e\xfc",
	}, { /* single-block file */
		.hash_algorithm = FS_VERITY_HASH_ALG_SHA256,
		.file_size = 4096,
		.block_size = 4096,
		.digest = "\x6a\xc3\x99\x79\x01\x6e\x3d\xdf"
			  "\x3d\x39\xff\xf6\xcb\x98\x4f\x7c"
			  "\x11\x8a\xcd\xf1\x85\x29\x19\xf5"
			  "\xc1\x00\xc4\xb1\x42\xc1\x81\x8e",
	}, { /* tiny file */
		.hash_algorithm = FS_VERITY_HASH_ALG_SHA256,
		.file_size = 1,
		.block_size = 4096,
		.digest = "\xb8\x03\x42\x95\x03\xd9\x59\x15"
			  "\x82\x9b\x29\xfd\xbc\x8b\xba\xd1"
			  "\x42\xf3\xab\xfd\x11\xb1\xca\xdf"
			  "\x55\x26\x58\x2e\x68\x5c\x05\x51",
	}, { /* empty file */
		.hash_algorithm = FS_VERITY_HASH_ALG_SHA256,
		.file_size = 0,
		.block_size = 4096,
		.digest = "\x3d\x24\x8c\xa5\x42\xa2\x4f\xc6"
			  "\x2d\x1c\x43\xb9\x16\xea\xe5\x01"
			  "\x68\x78\xe2\x53\x3c\x88\x23\x84"
			  "\x80\xb2\x61\x28\xa1\xf1\xaf\x95",
	}, { /* salt */
		.hash_algorithm = FS_VERITY_HASH_ALG_SHA256,
		.file_size = 1000000,
		.block_size = 4096,
		.salt = "abcd",
		.digest = "\x91\x79\x00\xb0\xd2\x99\x45\x4a"
			  "\xa3\x04\xd5\xde\xbc\x6f\x39\xe4"
			  "\xaf\x7b\x5a\xbe\x33\xbd\xbc\x56"
			  "\x8d\x5d\x8f\x1e\x5c\x4d\x86\x52",
	}, { /* max length salt (32 bytes) */
		.hash_algorithm = FS_VERITY_HASH_ALG_SHA256,
		.file_size = 1000000,
		.block_size = 4096,
		.salt = "0123456789:;<=>?@ABCDEFGHIJKLMNO",
		.digest = "\xbc\x2d\x70\x32\x4c\x04\x8c\x22"
			  "\x0a\x2c\xb1\x90\x83\x21\x40\x86"
			  "\x3e\xb2\x68\xe6\x80\x42\x79\x39"
			  "\xe5\xd4\x67\xbe\xa5\xec\x5a\xd9",
	}, { /* 1K block size */
		.hash_algorithm = FS_VERITY_HASH_ALG_SHA256,
		.file_size = 1000000,
		.block_size = 1024,
		.digest = "\xe9\xdf\x92\x7c\x14\xfc\xb9\x61"
			  "\xd5\xf5\x1c\x66\x6d\x8a\xe4\xc1"
			  "\x4f\xe4\xff\x98\xa3\x74\xc7\x33"
			  "\xe8\x98\xd0\x0c\x9e\x74\xa8\xe3",
	}, { /* 512-byte block size */
		.hash_algorithm = FS_VERITY_HASH_ALG_SHA256,
		.file_size = 1000000,
		.block_size = 512,
		.digest = "\x03\x93\xee\x3d\xfd\x4a\x28\x96"
			  "\x6e\x2a\xf4\xe0\x7c\xfa\x5b\x03"
			  "\x2c\x30\xda\x5b\xb8\xe8\xef\x63"
			  "\xb9\xa5\x5b\xf9\x63\x26\x23\x34",
	}, { /* 64K block size */
		.hash_algorithm = FS_VERITY_HASH_ALG_SHA256,
		.file_size = 1000000,
		.block_size = 65536,
		.digest = "\xf3\xb6\x41\x8f\x26\xd4\xd0\xe7"
			  "\x47\x28\x19\x3b\xae\x76\xf1\x5c"
			  "\xb4\xbb\x2c\xe9\x77\x74\x48\xd7"
			  "\x6b\xd8\x13\x8b\x69\xec\x61\xc2",
	}, { /* SHA-512 */
		.hash_algorithm = FS_VERITY_HASH_ALG_SHA512,
		.file_size = 1000000,
		.block_size = 4096,
		.salt = "abcd",
		.digest = "\x84\x25\xc6\xd0\xc9\x4f\x84\xed"
			  "\x90\x4c\x12\x93\x68\x45\xfb\xb7"
			  "\xaf\x99\x53\x75\x37\x89\x71\x2d"
			  "\xcc\x3b\xe1\x42\xdb\x3d\x4b\x6b"
			  "\x47\xa3\x99\xad\x52\xaa\x60\x92"
			  "\x56\xce\x29\xa9\x60\xbf\x4b\xb0"
			  "\xe5\x95\xec\x38\x6c\xa5\x8c\x06"
			  "\x51\x9d\x54\x6d\xc5\xb1\x97\xbb",
	}, { /* default hash algorithm (SHA-256) and block size (4096) */
		.file_size = 100000,
		.digest = "\xf2\x09\x6a\x36\xc5\xcd\xca\x4f"
			  "\xa3\x3e\xe8\x85\x28\x33\x15\x0b"
			  "\xb3\x24\x99\x2e\x54\x17\xa9\xd5"
			  "\x71\xf1\xbf\xff\xf7\x3b\x9e\xfc",
	},
};

static void fix_digest_and_print(const struct test_case *t,
				 const struct libfsverity_digest *d)
{
	char alg_name[32] = {};
	size_t i;

	strncpy(alg_name, libfsverity_get_hash_name(t->hash_algorithm),
		sizeof(alg_name) - 1);
	for (i = 0; i < sizeof(alg_name) - 1; i++)
		alg_name[i] = toupper((u8)alg_name[i]);

	printf("\t}, {\n"
	       "\t\t.hash_algorithm = FS_VERITY_HASH_ALG_%s,\n"
	       "\t\t.file_size = %" PRIu64 ",\n"
	       "\t\t.block_size = %u,\n",
	       alg_name, t->file_size, t->block_size);
	if (t->salt != NULL)
		printf("\t\t.salt = \"%s\",\n", t->salt);
	for (i = 0; i < d->digest_size; i++) {
		if (i == 0)
			printf("\t\t.digest = \"");
		else if (i % 8 == 0)
			printf("\t\t\t  \"");
		printf("\\x%02x", d->digest[i]);
		if (i + 1 == d->digest_size)
			printf("\",\n");
		else if (i % 8 == 7)
			printf("\"\n");
	}
}

static void test_invalid_params(void)
{
	struct mem_file f = { .data = (u8 *)"abcd", .size = 4 };
	struct libfsverity_merkle_tree_params good_params = {
		.version = 1,
		.hash_algorithm = FS_VERITY_HASH_ALG_SHA256,
		.file_size = 4,
		.block_size = 4096,
	};
	struct libfsverity_merkle_tree_params params;
	struct libfsverity_digest *d = NULL;

	libfsverity_set_error_callback(NULL);

	ASSERT(libfsverity_compute_digest(&f, read_fn, &good_params, &d) == 0);
	f.offset = 0;
	free(d);
	d = NULL;

	/* missing required arguments */
	ASSERT(libfsverity_compute_digest(&f, NULL, &good_params, &d) == -EINVAL);
	ASSERT(libfsverity_compute_digest(&f, read_fn, NULL, &d) == -EINVAL);
	ASSERT(libfsverity_compute_digest(&f, read_fn, &good_params, NULL) == -EINVAL);

	/* bad version */
	params = good_params;
	params.version = 0;
	ASSERT(libfsverity_compute_digest(&f, read_fn, &params, &d) == -EINVAL);
	params.version = 1000;
	ASSERT(libfsverity_compute_digest(&f, read_fn, &params, &d) == -EINVAL);

	/* bad hash_algorithm */
	params = good_params;
	params.hash_algorithm = 1000;
	ASSERT(libfsverity_compute_digest(&f, read_fn, &params, &d) == -EINVAL);

	/* bad block_size */
	params = good_params;
	params.block_size = 1;
	ASSERT(libfsverity_compute_digest(&f, read_fn, &params, &d) == -EINVAL);
	params.block_size = 4097;
	ASSERT(libfsverity_compute_digest(&f, read_fn, &params, &d) == -EINVAL);

	/* bad salt_size */
	params = good_params;
	params.salt_size = 1000;
	ASSERT(libfsverity_compute_digest(&f, read_fn, &params, &d) == -EINVAL);
	params.salt = (u8 *)"";
	ASSERT(libfsverity_compute_digest(&f, read_fn, &params, &d) == -EINVAL);

	/* bad reserved fields */
	params = good_params;
	params.reserved1[0] = 1;
	ASSERT(libfsverity_compute_digest(&f, read_fn, &params, &d) == -EINVAL);
	params = good_params;
	params.reserved1[ARRAY_SIZE(params.reserved1) - 1] = 1;
	ASSERT(libfsverity_compute_digest(&f, read_fn, &params, &d) == -EINVAL);
	params = good_params;
	params.reserved2[0] = 1;
	ASSERT(libfsverity_compute_digest(&f, read_fn, &params, &d) == -EINVAL);
	params = good_params;
	params.reserved2[ARRAY_SIZE(params.reserved2) - 1] = 1;
	ASSERT(libfsverity_compute_digest(&f, read_fn, &params, &d) == -EINVAL);

	/* error reading file */
	ASSERT(libfsverity_compute_digest(&f, error_read_fn, &good_params, &d) == -EIO);

	ASSERT(d == NULL);
}

static struct {
	u64 merkle_tree_size;
	u64 merkle_tree_block;
	u64 descriptor;
} metadata_callback_counts;

static int handle_merkle_tree_size(void *ctx, u64 size)
{
	metadata_callback_counts.merkle_tree_size++;

	/* Test that the ctx argument is passed through correctly. */
	ASSERT(ctx == (void *)1);

	/* Test that the expected Merkle tree size is reported. */
	ASSERT(size == 5 * 1024);
	return 0;
}

static int handle_merkle_tree_block(void *ctx, const void *block, size_t size,
				    u64 offset)
{
	u8 digest[SHA256_DIGEST_LENGTH];
	u64 count = metadata_callback_counts.merkle_tree_block++;
	const char *expected_digest;

	/* Test that ->merkle_tree_size() was called first. */
	ASSERT(metadata_callback_counts.merkle_tree_size == 1);

	/* Test that the ctx argument is passed through correctly. */
	ASSERT(ctx == (void *)1);

	/*
	 * Test that this Merkle tree block has the expected size, offset, and
	 * contents.  The 4 blocks at "level 0" should be reported first, in
	 * order; then the 1 block at "level 1" should be reported last (but the
	 * level 1 block should have the smallest offset).
	 */
	ASSERT(size == 1024);
	SHA256(block, size, digest);
	if (count == 4) {
		/* 1 block at level 1 */
		ASSERT(offset == 0);
		expected_digest = "\x68\xc5\x38\xe1\x19\x58\xd6\x5d"
				  "\x68\xb6\xfe\x8e\x9f\xb8\xcc\xab"
				  "\xec\xfd\x92\x8b\x01\xd0\x63\x44"
				  "\xe2\x23\xed\x41\xdd\xc4\x54\x4a";
	} else {
		/* 4 blocks at level 0 */
		ASSERT(offset == 1024 + (count * 1024));
		if (count < 3) {
			expected_digest = "\xf7\x89\xba\xab\x53\x85\x9f\xaf"
					  "\x36\xd6\xd7\x5d\x10\x42\x06\x42"
					  "\x94\x20\x2d\x6e\x13\xe7\x71\x6f"
					  "\x39\x4f\xba\x43\x4c\xcc\x49\x86";
		} else {
			expected_digest = "\x00\xfe\xd0\x3c\x5d\x6e\xab\x21"
					  "\x31\x43\xf3\xd9\x6a\x5c\xa3\x1c"
					  "\x2b\x89\xf5\x68\x4e\x6c\x8e\x07"
					  "\x87\x3e\x5e\x97\x65\x17\xb4\x8f";
		}
	}
	ASSERT(!memcmp(digest, expected_digest, SHA256_DIGEST_LENGTH));
	return 0;
}

static const u8 expected_file_digest[SHA256_DIGEST_LENGTH] =
	"\x09\xcb\xba\xee\xd2\xa0\x4c\x2d\xa2\x42\xc1\x0e\x15\x68\xd9\x6f"
	"\x35\x8a\x16\xaa\x1e\xbe\x8c\xf0\x28\x61\x20\xc1\x3c\x93\x66\xd1";

static int handle_descriptor(void *ctx, const void *descriptor, size_t size)
{
	u8 digest[SHA256_DIGEST_LENGTH];

	metadata_callback_counts.descriptor++;
	/* Test that the ctx argument is passed through correctly. */
	ASSERT(ctx == (void *)1);

	/* Test that the fs-verity descriptor is reported correctly. */
	ASSERT(size == 256);
	SHA256(descriptor, size, digest);
	ASSERT(!memcmp(digest, expected_file_digest, SHA256_DIGEST_LENGTH));
	return 0;
}

static const struct libfsverity_metadata_callbacks metadata_callbacks = {
	.ctx = (void *)1, /* arbitrary value for testing purposes */
	.merkle_tree_size = handle_merkle_tree_size,
	.merkle_tree_block = handle_merkle_tree_block,
	.descriptor = handle_descriptor,
};

/* Test that the libfsverity_metadata_callbacks work correctly. */
static void test_metadata_callbacks(void)
{
	/*
	 * For a useful test, we want a file whose Merkle tree will have at
	 * least 2 levels (this one will have exactly 2).  The contents of the
	 * file aren't too important.
	 */
	struct libfsverity_merkle_tree_params params = {
		.version = 1,
		.hash_algorithm = FS_VERITY_HASH_ALG_SHA256,
		.block_size = 1024,
		.file_size = 100000,
		.metadata_callbacks = &metadata_callbacks,
	};
	struct libfsverity_digest *d;

	ASSERT(libfsverity_compute_digest(NULL, zeroes_read_fn,
					  &params, &d) == 0);

	/* Test that the callbacks were called the correct number of times. */
	ASSERT(metadata_callback_counts.merkle_tree_size == 1);
	ASSERT(metadata_callback_counts.merkle_tree_block == 5);
	ASSERT(metadata_callback_counts.descriptor == 1);

	/* Test that the computed file digest is as expected. */
	ASSERT(d->digest_algorithm == FS_VERITY_HASH_ALG_SHA256);
	ASSERT(d->digest_size == SHA256_DIGEST_LENGTH);
	ASSERT(!memcmp(d->digest, expected_file_digest, SHA256_DIGEST_LENGTH));

	free(d);
}

int main(int argc, char *argv[])
{
	const bool update = (argc == 2 && !strcmp(argv[1], "--update"));
	size_t i;
	struct mem_file f = {};
	struct libfsverity_merkle_tree_params params;
	struct libfsverity_digest *d;
	int err;

	install_libfsverity_error_handler();

	for (i = 0; i < ARRAY_SIZE(test_cases); i++)
		f.size = max(f.size, test_cases[i].file_size);

	f.data = xmalloc(f.size);
	for (i = 0; i < f.size; i++)
		f.data[i] = (i % 11) + (i % 439) + (i % 1103);

	for (i = 0; i < ARRAY_SIZE(test_cases); i++) {
		u32 expected_alg = test_cases[i].hash_algorithm ?:
				   FS_VERITY_HASH_ALG_SHA256;

		memset(&params, 0, sizeof(params));
		params.version = 1;
		params.hash_algorithm = test_cases[i].hash_algorithm;
		params.file_size = test_cases[i].file_size;
		params.block_size = test_cases[i].block_size;
		if (test_cases[i].salt) {
			params.salt = (const u8 *)test_cases[i].salt;
			params.salt_size = strlen(test_cases[i].salt);
		}

		f.size = test_cases[i].file_size;
		f.offset = 0;

		err = libfsverity_compute_digest(&f, read_fn, &params, &d);
		ASSERT(err == 0);

		ASSERT(d->digest_algorithm == expected_alg);
		ASSERT(d->digest_size ==
		       libfsverity_get_digest_size(expected_alg));
		if (update)
			fix_digest_and_print(&test_cases[i], d);
		else
			ASSERT(!memcmp(d->digest, test_cases[i].digest,
				       d->digest_size));
		free(d);
		d = NULL;
	}
	free(f.data);
	if (update) {
		printf("\t}\n");
		return 1;
	}

	test_invalid_params();
	test_metadata_callbacks();
	printf("test_compute_digest passed\n");
	return 0;
}
