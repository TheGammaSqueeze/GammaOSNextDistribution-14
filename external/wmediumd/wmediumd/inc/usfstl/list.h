/*
 * Copyright (C) 2019 - 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
#ifndef _USFSTL_LIST_H_
#define _USFSTL_LIST_H_
#include <stddef.h>
#include <stdbool.h>

#ifndef offsetof
#define offsetof __builtin_offsetof
#endif

#ifndef container_of
#define container_of(ptr, type, member) ((type *)(void *)((char *)ptr - offsetof(type, member)))
#endif

struct usfstl_list_entry {
	struct usfstl_list_entry *next, *prev;
};

struct usfstl_list {
	struct usfstl_list_entry list;
};

#define USFSTL_LIST_INIT(name) {	\
	.list.next = &(name).list,	\
	.list.prev = &(name).list,	\
}
#define USFSTL_LIST(name) struct usfstl_list name = USFSTL_LIST_INIT(name)

static inline void usfstl_list_init(struct usfstl_list *list)
{
	list->list.next = &list->list;
	list->list.prev = &list->list;
}

static inline void usfstl_list_insert_before(struct usfstl_list_entry *existing,
					     struct usfstl_list_entry *new_entry)
{
	new_entry->prev = existing->prev;
	existing->prev->next = new_entry;
	existing->prev = new_entry;
	new_entry->next = existing;
}

static inline void usfstl_list_append(struct usfstl_list *list,
				      struct usfstl_list_entry *new_entry)
{
	usfstl_list_insert_before(&list->list, new_entry);
}

#define usfstl_list_item(element, type, member) \
	((type *)container_of(element, type, member))

#define usfstl_next_item(_list, entry, type, member) \
	((entry)->member.next != &(_list)->list ? \
		usfstl_list_item((entry)->member.next, type, member) :\
		NULL)

#define usfstl_for_each_list_item(item, _list, member) \
	for (item = usfstl_list_first_item(_list, typeof(*item), member); \
	     item; \
	     item = usfstl_next_item(_list, item, typeof(*item), member))

#define usfstl_for_each_list_item_safe(item, next, _list, member) \
	for (item = usfstl_list_first_item(_list, typeof(*item), member), \
	     next = item ? usfstl_next_item(_list, item, typeof(*next), member) : NULL; \
	     item; \
	     item = next, \
	     next = item ? usfstl_next_item(_list, next, typeof(*next), member) : NULL)

#define usfstl_for_each_list_item_continue_safe(item, next, _list, member) \
	for (item = item ? usfstl_next_item(_list, item, typeof(*item), member) : \
			   usfstl_list_first_item(_list, typeof(*item), member), \
	     next = item ? usfstl_next_item(_list, item, typeof(*item), member) : NULL; \
	     item; \
	     item = next, next = item ? usfstl_next_item(_list, next, typeof(*item), member) : NULL)

static inline bool usfstl_list_empty(const struct usfstl_list *list)
{
	return list->list.next == &list->list;
}

#define usfstl_list_first_item(_list, type, member) \
	(usfstl_list_empty(_list) ? NULL : usfstl_list_item((_list)->list.next, type, member))

static inline void usfstl_list_item_remove(struct usfstl_list_entry *entry)
{
	entry->next->prev = entry->prev;
	entry->prev->next = entry->next;
	entry->next = NULL;
	entry->prev = NULL;
}

#endif // _USFSTL_LIST_H_
