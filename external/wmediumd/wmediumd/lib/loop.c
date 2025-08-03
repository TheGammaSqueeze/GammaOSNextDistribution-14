/*
 * Copyright (C) 2019 - 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
#include <stddef.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <usfstl/loop.h>
#include <usfstl/list.h>
#include <assert.h>
#ifdef _WIN32
#undef _WIN32_WINNT
#define _WIN32_WINNT 0x0600
#include <winsock2.h>
#include <windows.h>
#else
#include <sys/select.h>
#endif

struct usfstl_list g_usfstl_loop_entries =
	USFSTL_LIST_INIT(g_usfstl_loop_entries);
void (*g_usfstl_loop_pre_handler_fn)(void *);
void *g_usfstl_loop_pre_handler_fn_data;


void usfstl_loop_register(struct usfstl_loop_entry *entry)
{
	struct usfstl_loop_entry *tmp;

	usfstl_loop_for_each_entry(tmp) {
		if (entry->priority >= tmp->priority) {
			usfstl_list_insert_before(&tmp->list, &entry->list);
			return;
		}
	}

	usfstl_list_append(&g_usfstl_loop_entries, &entry->list);
}

void usfstl_loop_unregister(struct usfstl_loop_entry *entry)
{
	usfstl_list_item_remove(&entry->list);
}

void usfstl_loop_wait_and_handle(void)
{
	while (true) {
		struct usfstl_loop_entry *tmp;
		fd_set rd_set, exc_set;
		unsigned int max = 0, num;

		FD_ZERO(&rd_set);
		FD_ZERO(&exc_set);

		usfstl_loop_for_each_entry(tmp) {
			FD_SET(tmp->fd, &rd_set);
			FD_SET(tmp->fd, &exc_set);
			if ((unsigned int)tmp->fd > max)
				max = tmp->fd;
		}

		num = select(max + 1, &rd_set, NULL, &exc_set, NULL);
		assert(num > 0);

		usfstl_loop_for_each_entry(tmp) {
			void *data = g_usfstl_loop_pre_handler_fn_data;

			if (!FD_ISSET(tmp->fd, &rd_set) &&
			    !FD_ISSET(tmp->fd, &exc_set))
				continue;

			if (g_usfstl_loop_pre_handler_fn)
				g_usfstl_loop_pre_handler_fn(data);
			tmp->handler(tmp);
			return;
		}
	}
}
