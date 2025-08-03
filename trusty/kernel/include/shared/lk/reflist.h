/*
 * Copyright (c) 2013, Google, Inc. All rights reserved
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

#ifndef __REFLIST_H
#define __REFLIST_H

#include <assert.h>
#include <lk/compiler.h>
#include <lk/list.h>

struct obj_ref {
    struct list_node ref_node;
};

struct obj {
    struct list_node ref_list;
};

typedef void (*obj_destroy_func)(struct obj* obj);

#define OBJ_REF_INITIAL_VALUE(r) \
    { .ref_node = LIST_INITIAL_CLEARED_VALUE }

static inline __ALWAYS_INLINE void obj_ref_init(struct obj_ref* ref) {
    *ref = (struct obj_ref)OBJ_REF_INITIAL_VALUE(*ref);
}

static inline __ALWAYS_INLINE bool obj_ref_active(struct obj_ref* ref) {
    return list_in_list(&ref->ref_node);
}

static inline __ALWAYS_INLINE void obj_init(struct obj* obj,
                                            struct obj_ref* ref) {
    list_initialize(&obj->ref_list);
    list_add_tail(&obj->ref_list, &ref->ref_node);
}

static inline __ALWAYS_INLINE bool obj_has_ref(struct obj* obj) {
    return !list_is_empty(&obj->ref_list);
}

static inline __ALWAYS_INLINE bool obj_has_only_ref(struct obj* obj,
                                                    struct obj_ref* ref) {
    assert(obj_has_ref(obj));
    assert(list_in_list(&ref->ref_node));
    struct list_node* head = list_peek_head(&obj->ref_list);
    struct list_node* tail = list_peek_tail(&obj->ref_list);
    if (head == tail) {
        assert(head == &ref->ref_node);
        return head == &ref->ref_node;
    }
    return false;
}

/*
 * Only use if you are intentionally reusing a possibly unreferenced
 * object. A cache is an example of this use case, where the destroy
 * callback may not actually free the object, and the code may wish to
 * reuse it by adding a reference after it hits zero.
 */
static inline __ALWAYS_INLINE void obj_add_ref_allow_unreferenced_obj(
        struct obj* obj, struct obj_ref* ref) {
    assert(!list_in_list(&ref->ref_node));
    list_add_tail(&obj->ref_list, &ref->ref_node);
}

static inline __ALWAYS_INLINE void obj_add_ref(struct obj* obj,
                                               struct obj_ref* ref) {
    assert(obj_has_ref(obj));
    obj_add_ref_allow_unreferenced_obj(obj, ref);
}

static inline __ALWAYS_INLINE bool obj_del_ref(struct obj* obj,
                                               struct obj_ref* ref,
                                               obj_destroy_func destroy) {
    bool dead;

    assert(list_in_list(&ref->ref_node));

    list_delete(&ref->ref_node);
    dead = list_is_empty(&obj->ref_list);
    if (dead && destroy)
        destroy(obj);
    return dead;
}

static inline __ALWAYS_INLINE void obj_ref_transfer(struct obj_ref* dst,
                                                    struct obj_ref* src) {
    struct list_node* prev;

    assert(!list_in_list(&dst->ref_node));
    assert(list_in_list(&src->ref_node));

    prev = src->ref_node.prev;
    list_delete(&src->ref_node);
    list_add_after(prev, &dst->ref_node);
}

#endif
