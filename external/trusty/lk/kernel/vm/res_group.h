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

#pragma once

#include <reflist.h>
#include <stdint.h>
#include <sys/types.h>

struct res_group {
    struct obj obj;
    size_t reserved_pages;
    size_t used_pages;
    bool is_shutdown;
};

/**
 * res_group_create() - Create a resource group.
 * @pages: the number of pages to reserve.
 * @ref: the `obj_ref` for the group.
 */
struct res_group* res_group_create(size_t pages, struct obj_ref* ref);

/**
 * res_group_add_ref() - Add a reference to the resource group.
 * @res_group: the resource group to operate on.
 * @ref: the `obj_ref` to add.
 */
void res_group_add_ref(struct res_group* res_group, struct obj_ref* ref);

/**
 * res_group_del_ref() - Remove a reference from the resource group.
 * @res_group: the resource group to operate on.
 * @ref: the `obj_ref` to remove.
 */
void res_group_del_ref(struct res_group* res_group, struct obj_ref* ref);

/**
 * res_group_shutdown() - Mark the resource group as shutdown. Unreserves
 *                        unused memory and prevents new allocations.
 * @res_group: the resource group to operate on.
 */
status_t res_group_shutdown(struct res_group* res_group);

/**
 * res_group_take_mem() - Take memory.
 * @res_group: the resource group to operate on.
 * @pages: the number of pages to take.
 */
status_t res_group_take_mem(struct res_group* res_group, size_t pages);

/**
 * res_group_release_mem() - Release memory.
 * @res_group: the resource group to operate on.
 * @pages: the number of pages to release.
 */
void res_group_release_mem(struct res_group* res_group, size_t pages);

