/*
 * Copyright (C) 2019 - 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
#include <stddef.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <errno.h>
#include <usfstl/assert.h>
#include <usfstl/loop.h>
#include <usfstl/list.h>
#include "internal.h"

struct usfstl_uds_server {
	struct usfstl_loop_entry entry;
	void (*connected)(int fd, void *data);
	void *data;
	char name[];
};

void usfstl_uds_accept_handler(struct usfstl_loop_entry *entry)
{
	struct usfstl_uds_server *uds;
	int fd;

	uds = container_of(entry, struct usfstl_uds_server, entry);
	fd = accept(uds->entry.fd, NULL, NULL);

	uds->connected(fd, uds->data);
}

void usfstl_uds_create(const char *path, void (*connected)(int, void *),
		       void *data)
{
	struct usfstl_uds_server *uds = malloc(sizeof(*uds) + strlen(path) + 1);
	struct stat buf;
	int ret = stat(path, &buf), fd;
	struct sockaddr_un un = {
		.sun_family = AF_UNIX,
	};

	USFSTL_ASSERT(uds);
	strcpy(uds->name, path);
	uds->data = data;
	uds->connected = connected;

	if (ret == 0) {
		USFSTL_ASSERT_EQ((int)(buf.st_mode & S_IFMT), S_IFSOCK, "%d");
		USFSTL_ASSERT_EQ(unlink(path), 0, "%d");
	} else {
		USFSTL_ASSERT_EQ(errno, ENOENT, "%d");
	}

	fd = socket(AF_UNIX, SOCK_STREAM, 0);
	uds->entry.fd = fd;

	strcpy(un.sun_path, path);
	USFSTL_ASSERT_EQ(bind(fd, (void *)&un, sizeof(un)), 0, "%d");

	USFSTL_ASSERT_EQ(listen(fd, 1000), 0, "%d");

	uds->entry.handler = usfstl_uds_accept_handler;

	usfstl_loop_register(&uds->entry);
}

void usfstl_uds_remove(const char *path)
{
	struct usfstl_loop_entry *tmp;
	struct usfstl_uds_server *uds, *found = NULL;

	usfstl_loop_for_each_entry(tmp) {
		if (tmp->handler != usfstl_uds_accept_handler)
			continue;

		uds = container_of(tmp, struct usfstl_uds_server, entry);
		if (strcmp(uds->name, path) == 0) {
			found = uds;
			break;
		}
	}

	USFSTL_ASSERT(found);

	close(found->entry.fd);
	usfstl_loop_unregister(&found->entry);
	unlink(path);
	free(found);
}

struct usfstl_uds_client {
	struct usfstl_loop_entry entry;
	void (*readable)(int fd, void *data);
	void *data;
};

void usfstl_uds_readable_handler(struct usfstl_loop_entry *entry)
{
	struct usfstl_uds_client *uds;

	uds = container_of(entry, struct usfstl_uds_client, entry);

	uds->readable(uds->entry.fd, uds->data);
}

int usfstl_uds_connect(const char *path, void (*readable)(int, void *),
		       void *data)
{
	struct usfstl_uds_client *client;
	struct sockaddr_un sock;
	int fd, err;

	client = calloc(1, sizeof(*client));
	USFSTL_ASSERT(client);
	client->entry.handler = usfstl_uds_readable_handler;
	client->readable = readable;
	client->data = data;

	sock.sun_family = AF_UNIX;
	strcpy(sock.sun_path, path);

	fd = socket(AF_UNIX, SOCK_STREAM, 0);
	USFSTL_ASSERT(fd >= 0);

	err = connect(fd, (struct sockaddr *) &sock, sizeof(sock));
	USFSTL_ASSERT(err == 0);

	client->entry.fd = fd;
	usfstl_loop_register(&client->entry);

	return fd;
}

void usfstl_uds_disconnect(int fd)
{
	struct usfstl_loop_entry *tmp;
	struct usfstl_uds_client *uds, *found = NULL;

	usfstl_loop_for_each_entry(tmp) {
		if (tmp->handler != usfstl_uds_readable_handler)
			continue;

		uds = container_of(tmp, struct usfstl_uds_client, entry);
		if (uds->entry.fd == fd) {
			found = uds;
			break;
		}
	}

	USFSTL_ASSERT(found);

	close(fd);
	usfstl_loop_unregister(&found->entry);
	free(found);
}
