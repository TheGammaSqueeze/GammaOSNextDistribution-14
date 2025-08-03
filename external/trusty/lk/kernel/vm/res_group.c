/*
 * Copyright (c) 2022 Google Inc. All rights reserved
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

#include "res_group.h"
#include <err.h>
#include <kernel/mutex.h>
#include <kernel/vm.h>
#include <stdlib.h>
#include <trace.h>
#include "vm_priv.h"

#define LOCAL_TRACE 0

static mutex_t res_group_lock = MUTEX_INITIAL_VALUE(res_group_lock);

static status_t res_group_destroy(struct res_group* res_group) {
    ASSERT(res_group);
    ASSERT(res_group->is_shutdown);
    ASSERT(!res_group->used_pages);
    ASSERT(!obj_has_ref(&res_group->obj));
    if (res_group->reserved_pages) {
        pmm_unreserve_pages(res_group->reserved_pages);
    }
    free(res_group);
    return NO_ERROR;
}

struct res_group* res_group_create(size_t pages, struct obj_ref* ref) {
    ASSERT(ref);
    struct res_group* new_grp = calloc(1, sizeof(struct res_group));
    if (!new_grp) {
        return NULL;
    }
    if (pmm_reserve_pages(pages)) {
        free(new_grp);
        return NULL;
    }
    obj_init(&new_grp->obj, ref);
    new_grp->reserved_pages = pages;

    return new_grp;
}

void res_group_add_ref(struct res_group* res_group, struct obj_ref* ref) {
    ASSERT(ref);
    mutex_acquire(&res_group_lock);
    obj_add_ref(&res_group->obj, ref);
    mutex_release(&res_group_lock);
}

void res_group_del_ref(struct res_group* res_group, struct obj_ref* ref) {
    ASSERT(ref);
    bool destroy;
    mutex_acquire(&res_group_lock);
    destroy = obj_del_ref(&res_group->obj, ref, NULL);
    mutex_release(&res_group_lock);
    if (destroy) {
        res_group_destroy(res_group);
    }
}

status_t res_group_shutdown(struct res_group* res_group) {
    ASSERT(res_group);
    mutex_acquire(&res_group_lock);
    ASSERT(!res_group->is_shutdown);
    res_group->is_shutdown = true;
    size_t unused_pages = res_group->reserved_pages - res_group->used_pages;
    res_group->reserved_pages -= unused_pages;
    mutex_release(&res_group_lock);
    pmm_unreserve_pages(unused_pages);
    return NO_ERROR;
}

static status_t check_take(struct res_group* res_group,
                     size_t pages) {
    if (res_group->is_shutdown) {
        return ERR_OBJECT_DESTROYED;
    }
    size_t total_pages;
    if (__builtin_add_overflow(res_group->used_pages, pages, &total_pages)) {
        return ERR_NO_MEMORY;
    }
    if (total_pages > res_group->reserved_pages) {
        return ERR_NO_MEMORY;
    }
    return NO_ERROR;
}

status_t res_group_take_mem(struct res_group* res_group, size_t pages) {
    ASSERT(res_group);
    mutex_acquire(&res_group_lock);
    status_t ret = check_take(res_group, pages);
    if (!ret) {
        res_group->used_pages += pages;
    }
    mutex_release(&res_group_lock);
    return ret;
}

void res_group_release_mem(struct res_group* res_group, size_t pages) {
    ASSERT(res_group);
    mutex_acquire(&res_group_lock);
    ASSERT(res_group->used_pages >= pages);
    res_group->used_pages -= pages;
    mutex_release(&res_group_lock);
}

