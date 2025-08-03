/*
 * Copyright (C) 2019 - 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
/*
 * This defines a simple mainloop for reading from multiple sockets and handling
 * the one that becomes readable. Note that on Windows, it can currently only
 * handle sockets, not arbitrary descriptors, since we only need it for RPC.
 */
#ifndef _USFSTL_LOOP_H_
#define _USFSTL_LOOP_H_
#include <unistd.h>
#include <string.h>
#include <stdint.h>
#include "list.h"

#ifdef _WIN32
typedef uintptr_t usfstl_fd_t;
#else
typedef int usfstl_fd_t;
#endif

/**
 * struct usfstl_loop_entry - main loop entry
 * @list: private
 * @handler: handler to call when fd is readable
 * @fd: file descriptor
 * @priority: priority, higher is handled earlier;
 *	must not change while the entry is registered
 * @data: user data
 */
struct usfstl_loop_entry {
	struct usfstl_list_entry list;
	void (*handler)(struct usfstl_loop_entry *);
	usfstl_fd_t fd;
	int priority;
	void *data;
};

extern struct usfstl_list g_usfstl_loop_entries;

/**
 * g_usfstl_loop_pre_handler_fn - pre-handler function
 *
 * If assigned (defaults to %NULL) this handler will be called
 * before every loop handler's handling function, e.g. in order
 * to synchronize time with the wallclock scheduler.
 */
extern void (*g_usfstl_loop_pre_handler_fn)(void *data);
extern void *g_usfstl_loop_pre_handler_fn_data;

/**
 * usfstl_loop_register - add an entry to the mainloop
 * @entry: the entry to add, must be fully set up including
 *	the priority
 */
void usfstl_loop_register(struct usfstl_loop_entry *entry);

/**
 * usfstl_loop_unregister - remove an entry from the mainloop
 * @entry: the entry to remove
 */
void usfstl_loop_unregister(struct usfstl_loop_entry *entry);

/**
 * usfstl_loop_wait_and_handle - wait and handle a single event
 *
 * Wait for, and handle, a single event, then return.
 */
void usfstl_loop_wait_and_handle(void);

/**
 * usfstl_loop_for_each_entry - iterate main loop entries
 */
#define usfstl_loop_for_each_entry(entry) \
	usfstl_for_each_list_item(entry, &g_usfstl_loop_entries, list)

/**
 * usfstl_loop_for_each_entry_safe - iterate main loop entries safely
 *
 * Where "safely" means safe to concurrent modification.
 */
#define usfstl_loop_for_each_entry_safe(entry, tmp) \
	usfstl_for_each_list_item_safe(entry, tmp, &g_usfstl_loop_entries, list)

#endif // _USFSTL_LOOP_H_
