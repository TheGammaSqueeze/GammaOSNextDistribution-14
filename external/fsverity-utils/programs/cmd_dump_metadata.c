// SPDX-License-Identifier: MIT
/*
 * The 'fsverity dump_metadata' command
 *
 * Copyright 2021 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

#include "fsverity.h"

#include <fcntl.h>
#include <getopt.h>
#include <sys/ioctl.h>
#include <unistd.h>

static const struct option longopts[] = {
	{"offset",	required_argument, NULL, OPT_OFFSET},
	{"length",	required_argument, NULL, OPT_LENGTH},
	{NULL, 0, NULL, 0}
};

static const struct {
	const char *name;
	int val;
} metadata_types[] = {
	{"merkle_tree", FS_VERITY_METADATA_TYPE_MERKLE_TREE},
	{"descriptor", FS_VERITY_METADATA_TYPE_DESCRIPTOR},
	{"signature", FS_VERITY_METADATA_TYPE_SIGNATURE},
};

static bool parse_metadata_type(const char *name, __u64 *val_ret)
{
	size_t i;

	for (i = 0; i < ARRAY_SIZE(metadata_types); i++) {
		if (strcmp(name, metadata_types[i].name) == 0) {
			*val_ret = metadata_types[i].val;
			return true;
		}
	}
	error_msg("unknown metadata type: %s", name);
	fputs("       Expected", stderr);
	for (i = 0; i < ARRAY_SIZE(metadata_types); i++) {
		if (i != 0 && ARRAY_SIZE(metadata_types) > 2)
			putc(',', stderr);
		putc(' ', stderr);
		if (i != 0 && i == ARRAY_SIZE(metadata_types) - 1)
			fputs("or ", stderr);
		fprintf(stderr, "\"%s\"", metadata_types[i].name);
	}
	fprintf(stderr, "\n");
	return false;
}

/* Dump the fs-verity metadata of the given file. */
int fsverity_cmd_dump_metadata(const struct fsverity_command *cmd,
			       int argc, char *argv[])
{
	bool offset_specified = false;
	bool length_specified = false;
	struct filedes file = { .fd = -1 };
	struct filedes stdout_filedes = { .fd = STDOUT_FILENO,
					  .name = "stdout" };
	struct fsverity_read_metadata_arg arg = { .length = 32768 };
	void *buf = NULL;
	char *tmp;
	int c;
	int status;
	int bytes_read;

	while ((c = getopt_long(argc, argv, "", longopts, NULL)) != -1) {
		switch (c) {
		case OPT_OFFSET:
			if (offset_specified) {
				error_msg("--offset can only be specified once");
				goto out_usage;
			}
			errno = 0;
			arg.offset = strtoull(optarg, &tmp, 10);
			if (errno || *tmp) {
				error_msg("invalid value for --offset");
				goto out_usage;
			}
			offset_specified = true;
			break;
		case OPT_LENGTH:
			if (length_specified) {
				error_msg("--length can only be specified once");
				goto out_usage;
			}
			errno = 0;
			arg.length = strtoull(optarg, &tmp, 10);
			if (errno || *tmp || arg.length > SIZE_MAX) {
				error_msg("invalid value for --length");
				goto out_usage;
			}
			length_specified = true;
			break;
		default:
			goto out_usage;
		}
	}

	argv += optind;
	argc -= optind;

	if (argc != 2)
		goto out_usage;

	if (!parse_metadata_type(argv[0], &arg.metadata_type))
		goto out_usage;

	if (length_specified && !offset_specified) {
		error_msg("--length specified without --offset");
		goto out_usage;
	}
	if (offset_specified && !length_specified) {
		error_msg("--offset specified without --length");
		goto out_usage;
	}

	buf = xzalloc(arg.length);
	arg.buf_ptr = (uintptr_t)buf;

	if (!open_file(&file, argv[1], O_RDONLY, 0))
		goto out_err;

	/*
	 * If --offset and --length were specified, then do only the single read
	 * requested.  Otherwise read until EOF.
	 */
	do {
		bytes_read = ioctl(file.fd, FS_IOC_READ_VERITY_METADATA, &arg);
		if (bytes_read < 0) {
			error_msg_errno("FS_IOC_READ_VERITY_METADATA failed on '%s'",
					file.name);
			goto out_err;
		}
		if (bytes_read == 0)
			break;
		if (!full_write(&stdout_filedes, buf, bytes_read))
			goto out_err;
		arg.offset += bytes_read;
	} while (!length_specified);

	status = 0;
out:
	free(buf);
	filedes_close(&file);
	return status;

out_err:
	status = 1;
	goto out;

out_usage:
	usage(cmd, stderr);
	status = 2;
	goto out;
}
