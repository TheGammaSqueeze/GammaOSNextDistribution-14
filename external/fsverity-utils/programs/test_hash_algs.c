// SPDX-License-Identifier: MIT
/*
 * Test the hash algorithm-related libfsverity APIs.
 *
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

#include "utils.h"

#define SHA256_DIGEST_SIZE 32
#define SHA512_DIGEST_SIZE 64

int main(void)
{
	install_libfsverity_error_handler();

	ASSERT(libfsverity_get_digest_size(0) == -1);
	ASSERT(libfsverity_get_hash_name(0) == NULL);
	ASSERT(libfsverity_find_hash_alg_by_name("bad") == 0);
	ASSERT(libfsverity_find_hash_alg_by_name(NULL) == 0);

	ASSERT(libfsverity_get_digest_size(100) == -1);
	ASSERT(libfsverity_get_hash_name(100) == NULL);

	ASSERT(libfsverity_get_digest_size(FS_VERITY_HASH_ALG_SHA256) ==
	       SHA256_DIGEST_SIZE);
	ASSERT(!strcmp("sha256",
		       libfsverity_get_hash_name(FS_VERITY_HASH_ALG_SHA256)));
	ASSERT(libfsverity_find_hash_alg_by_name("sha256") ==
	       FS_VERITY_HASH_ALG_SHA256);

	ASSERT(libfsverity_get_digest_size(FS_VERITY_HASH_ALG_SHA512) ==
	       SHA512_DIGEST_SIZE);
	ASSERT(!strcmp("sha512",
		       libfsverity_get_hash_name(FS_VERITY_HASH_ALG_SHA512)));
	ASSERT(libfsverity_find_hash_alg_by_name("sha512") ==
	       FS_VERITY_HASH_ALG_SHA512);

	printf("test_hash_algs passed\n");
	return 0;
}
