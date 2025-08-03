// SPDX-License-Identifier: MIT
/*
 * Test libfsverity_sign_digest().
 *
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

#include "utils.h"

#include <fcntl.h>

#define SHA256_DIGEST_SIZE 32

int main(void)
{
	struct libfsverity_digest *d = xzalloc(sizeof(*d) + SHA256_DIGEST_SIZE);
	const struct libfsverity_signature_params params = {
		.keyfile = "testdata/key.pem",
		.certfile = "testdata/cert.pem",
	};
	u8 *sig;
	size_t sig_size;
	struct filedes file;
	u8 *expected_sig;
	u64 expected_sig_size;
	int err;

	install_libfsverity_error_handler();

	d->digest_algorithm = FS_VERITY_HASH_ALG_SHA256;
	d->digest_size = SHA256_DIGEST_SIZE;
	memcpy(d->digest,
	       "\x91\x79\x00\xb0\xd2\x99\x45\x4a\xa3\x04\xd5\xde\xbc\x6f\x39"
	       "\xe4\xaf\x7b\x5a\xbe\x33\xbd\xbc\x56\x8d\x5d\x8f\x1e\x5c\x4d"
	       "\x86\x52", SHA256_DIGEST_SIZE);

	err = libfsverity_sign_digest(d, &params, &sig, &sig_size);
	ASSERT(err == 0);

	ASSERT(open_file(&file, "testdata/file.sig", O_RDONLY, 0));
	ASSERT(get_file_size(&file, &expected_sig_size));
	ASSERT(sig_size == expected_sig_size);
	expected_sig = xmalloc(sig_size);
	ASSERT(full_read(&file, expected_sig, sig_size));
	ASSERT(!memcmp(sig, expected_sig, sig_size));

	free(d);
	free(sig);
	free(expected_sig);
	filedes_close(&file);
	printf("test_sign_digest passed\n");
	return 0;
}
