/* SPDX-License-Identifier: MIT */
/*
 * Private header for the 'fsverity' program
 *
 * Copyright 2018 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */
#ifndef PROGRAMS_FSVERITY_H
#define PROGRAMS_FSVERITY_H

#include "utils.h"
#include "../common/fsverity_uapi.h"

/*
 * Largest digest size among all hash algorithms supported by fs-verity.
 * This can be increased if needed.
 */
#define FS_VERITY_MAX_DIGEST_SIZE	64

enum {
	OPT_BLOCK_SIZE,
	OPT_CERT,
	OPT_COMPACT,
	OPT_FOR_BUILTIN_SIG,
	OPT_HASH_ALG,
	OPT_KEY,
	OPT_LENGTH,
	OPT_OFFSET,
	OPT_OUT_DESCRIPTOR,
	OPT_OUT_MERKLE_TREE,
	OPT_PKCS11_ENGINE,
	OPT_PKCS11_KEYID,
	OPT_PKCS11_MODULE,
	OPT_SALT,
	OPT_SIGNATURE,
};

struct fsverity_command;

/* cmd_digest.c */
int fsverity_cmd_digest(const struct fsverity_command *cmd,
			int argc, char *argv[]);

/* cmd_dump_metadata.c */
int fsverity_cmd_dump_metadata(const struct fsverity_command *cmd,
			       int argc, char *argv[]);

/* cmd_enable.c */
int fsverity_cmd_enable(const struct fsverity_command *cmd,
			int argc, char *argv[]);

/* cmd_measure.c */
int fsverity_cmd_measure(const struct fsverity_command *cmd,
			 int argc, char *argv[]);

/* cmd_sign.c */
int fsverity_cmd_sign(const struct fsverity_command *cmd,
		      int argc, char *argv[]);

/* fsverity.c */
void usage(const struct fsverity_command *cmd, FILE *fp);
bool parse_tree_param(int opt_char, const char *arg,
		      struct libfsverity_merkle_tree_params *params);
bool destroy_tree_params(struct libfsverity_merkle_tree_params *params);

#endif /* PROGRAMS_FSVERITY_H */
