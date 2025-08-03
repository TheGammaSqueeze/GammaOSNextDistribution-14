/*
 * Copyright (c) 2008 Travis Geiselbrecht
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
#ifndef __LIST_H
#define __LIST_H

#include <lk/compiler.h>
#include <lk/macros.h>
#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

__BEGIN_CDECLS;

struct list_node {
    struct list_node *prev;
    struct list_node *next;
};

#define LIST_INITIAL_VALUE(list) { &(list), &(list) }
#define LIST_INITIAL_CLEARED_VALUE { NULL, NULL }

static inline void list_initialize(struct list_node *list)
{
    list->prev = list->next = list;
}

static inline void list_clear_node(struct list_node *item)
{
    item->prev = item->next = 0;
}

static inline bool list_in_list(struct list_node *item)
{
    if (item->prev == 0 && item->next == 0)
        return false;
    else
        return true;
}

static inline void list_add_head(struct list_node *list, struct list_node *item)
{
    item->next = list->next;
    item->prev = list;
    list->next->prev = item;
    list->next = item;
}

#define list_add_after(entry, new_entry) list_add_head(entry, new_entry)

static inline void list_add_tail(struct list_node *list, struct list_node *item)
{
    item->prev = list->prev;
    item->next = list;
    list->prev->next = item;
    list->prev = item;
}

#define list_add_before(entry, new_entry) list_add_tail(entry, new_entry)

static inline void list_delete(struct list_node *item)
{
    item->next->prev = item->prev;
    item->prev->next = item->next;
    item->prev = item->next = 0;
}

static inline struct list_node *list_remove_head(struct list_node *list)
{
    if (list->next != list) {
        struct list_node *item = list->next;
        list_delete(item);
        return item;
    } else {
        return NULL;
    }
}

#define list_remove_head_type(list, type, element) \
    containerof_null_safe(list_remove_head(list), type, element)

static inline struct list_node *list_remove_tail(struct list_node *list)
{
    if (list->prev != list) {
        struct list_node *item = list->prev;
        list_delete(item);
        return item;
    } else {
        return NULL;
    }
}

#define list_remove_tail_type(list, type, element) \
    containerof_null_safe(list_remove_tail(list), type, element)

static inline struct list_node *list_peek_head(struct list_node *list)
{
    if (list->next != list) {
        return list->next;
    } else {
        return NULL;
    }
}

#define list_peek_head_type(list, type, element) \
    containerof_null_safe(list_peek_head(list), type, element)

static inline struct list_node *list_peek_tail(struct list_node *list)
{
    if (list->prev != list) {
        return list->prev;
    } else {
        return NULL;
    }
}

#define list_peek_tail_type(list, type, element) \
    containerof_null_safe(list_peek_tail(list), type, element)

static inline struct list_node *list_prev(struct list_node *list, struct list_node *item)
{
    if (item->prev != list)
        return item->prev;
    else
        return NULL;
}

#define list_prev_type(list, item, type, element) \
    containerof_null_safe(list_prev(list, item), type, element)

static inline struct list_node *list_prev_wrap(struct list_node *list, struct list_node *item)
{
    if (item->prev != list)
        return item->prev;
    else if (item->prev->prev != list)
        return item->prev->prev;
    else
        return NULL;
}

#define list_prev_wrap_type(list, item, type, element) \
    containerof_null_safe(list_prev_wrap(list, item), type, element)

static inline struct list_node *list_next(struct list_node *list, struct list_node *item)
{
    if (item->next != list)
        return item->next;
    else
        return NULL;
}

#define list_next_type(list, item, type, element) \
    containerof_null_safe(list_next(list, item), type, element)

static inline struct list_node *list_next_wrap(struct list_node *list, struct list_node *item)
{
    if (item->next != list)
        return item->next;
    else if (item->next->next != list)
        return item->next->next;
    else
        return NULL;
}

#define list_next_wrap_type(list, item, type, element) \
    containerof_null_safe(list_next_wrap(list, item), type, element)

// iterates over the list, node should be struct list_node*
#define list_for_every(list, node) \
    for(node = (list)->next; node != (list); node = node->next)

// iterates over the list in a safe way for deletion of current node
// node and temp_node should be struct list_node*
#define list_for_every_safe(list, node, temp_node) \
    for(node = (list)->next, temp_node = (node)->next;\
    node != (list);\
    node = temp_node, temp_node = (node)->next)

// iterates over the list, entry should be the container structure type *
//
// Iterating over (entry) rather than the list can add UB when the list node is
// not inside of the enclosing type, as may be the case for the list terminator.
// We avoid this by iterating over the list node and only constructing the new
// entry if it was not the list terminator, otherwise setting it to null.
#define list_for_every_entry(list, entry, type, member) \
    for (struct list_node *_list_for_every_cursor = (list)->next; \
            ((entry) = NULL, _list_for_every_cursor != (list)) && \
            ((entry) = containerof(_list_for_every_cursor, type, member)); \
            _list_for_every_cursor = _list_for_every_cursor->next)


// iterates over the list in a safe way for deletion of current node
// entry should be the container structure type *
// See list_for_every_entry to see why we don't iterate over entries
#define list_for_every_entry_safe(list, entry, unused, type, member) \
    (void) unused; \
    for(struct list_node *_list_for_every_cursor = (list)->next; \
            ((entry) = NULL, _list_for_every_cursor != (list)) && \
            ((entry) = containerof(_list_for_every_cursor, type, member)) && \
            (_list_for_every_cursor = _list_for_every_cursor->next);)

static inline bool list_is_empty(struct list_node *list)
{
    return (list->next == list) ? true : false;
}

static inline size_t list_length(struct list_node *list)
{
    size_t cnt = 0;
    struct list_node *node = list;
    list_for_every(list, node) {
        cnt++;
    }

    return cnt;
}

/**
 * list_splice_after - Move all entries from one list to another list.
 * @dest_item:  Items from @src_list are inserted after this item.
 * @src_list:   List containing items to be moved.
 *
 * This function also serves as the helper function for list_splice_before,
 * list_splice_head and list_splice_tail. In this case @dest_item can be the
 * list node instead of an item in the list.
 *
 * Empty source and destination lists are both supported. The destination list
 * or item should not be in the source list.
 */
static inline void list_splice_after(struct list_node *dest_item,
                                     struct list_node *src_list)
{
    /*
     * We need to read prev from @src_list before writing to src_next->prev in
     * case @src_list is empty (src_next->prev and src_list->prev are the same
     * in that case).
     */
    struct list_node *src_next = src_list->next;
    struct list_node *src_prev = src_list->prev;

    /*
     * Link to @dest_item at the start of &src_list and &dest_item->next at the
     * end of @src_list. If src_list is empty, this will stash the existing
     * @dest_item and @dest_item->next values into the head of source_list.
     */
    src_next->prev = dest_item;
    src_prev->next = dest_item->next;

    /*
     * List @src_list after @dest_item. If @src_list was empty this is a nop as
     * we are reading back the same values we stored.
     */
    dest_item->next->prev = src_list->prev;
    dest_item->next = src_list->next;

    /* Clear &src_list so we don't leave it in a corrupt state */
    list_initialize(src_list);
}

/**
 * list_splice_before - Move all entries from one list to another list.
 * @dest_item:  Items from @src_list are inserted before this item.
 * @src_list:   List containing times to be moved.
 */
static inline void list_splice_before(struct list_node *dest_item,
                                      struct list_node *src_list)
{
    list_splice_after(dest_item->prev, src_list);
}

/**
 * list_splice_head - Move all entries from one list to another list.
 * @dest_list:  Items from @src_list are inserted at the head of this list.
 * @src_list:   List containing times to be moved.
 */
static inline void list_splice_head(struct list_node *dest_list,
                                    struct list_node *src_list)
{
    list_splice_after(dest_list, src_list);
}

/**
 * list_splice_tail - Move all entries from one list to another list.
 * @dest_list:  Items from @src_list are inserted at the tail of this list.
 * @src_list:   List containing times to be moved.
 */
static inline void list_splice_tail(struct list_node *dest_list,
                                    struct list_node *src_list)
{
    list_splice_after(dest_list->prev, src_list);
}

__END_CDECLS;

#endif
