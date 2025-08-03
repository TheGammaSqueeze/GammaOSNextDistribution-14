// SPDX-License-Identifier: MIT
/*
 * Implementation of libfsverity_enable() and libfsverity_enable_with_sig().
 *
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

#include "lib_private.h"

#include <sys/ioctl.h>

LIBEXPORT int
libfsverity_enable(int fd, const struct libfsverity_merkle_tree_params *params)
{
	return libfsverity_enable_with_sig(fd, params, NULL, 0);
}

LIBEXPORT int
libfsverity_enable_with_sig(int fd,
			    const struct libfsverity_merkle_tree_params *params,
			    const uint8_t *sig, size_t sig_size)
{
	struct fsverity_enable_arg arg = {};

	if (!params) {
		libfsverity_error_msg("missing required parameters for enable");
		return -EINVAL;
	}

	if (params->version != 1) {
		libfsverity_error_msg("unsupported version (%u)",
				      params->version);
		return -EINVAL;
	}

	arg.version = 1;
	arg.hash_algorithm =
		params->hash_algorithm ?: FS_VERITY_HASH_ALG_DEFAULT;
	arg.block_size =
		params->block_size ?: FS_VERITY_BLOCK_SIZE_DEFAULT;
	arg.salt_size = params->salt_size;
	arg.salt_ptr = (uintptr_t)params->salt;
	arg.sig_size = sig_size;
	arg.sig_ptr = (uintptr_t)sig;

	if (ioctl(fd, FS_IOC_ENABLE_VERITY, &arg) != 0)
		return -errno;
	return 0;
}
