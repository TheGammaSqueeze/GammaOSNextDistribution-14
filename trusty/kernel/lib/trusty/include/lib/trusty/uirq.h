/*
 * Copyright (c) 2019, Google, Inc. All rights reserved
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

#include <stdint.h>
#include <sys/types.h>

#include <lib/trusty/event.h>
#include <lib/trusty/handle.h>
#include <lib/trusty/uuid.h>

/**
 * struct uirq - UIRQ descriptor
 * @name: UIRQ name (must be non-empty)
 * @uuids: pointer to array of &struct uuids that represents
 *         client uuids that are allowed to open this UIRQ object.
 * @uuids_num: number of entries in array pointed by @uuids parameter
 * @cfg_flags: reserved, must be set to 0
 */
struct uirq {
    const char* name;
    const struct uuid* uuids;
    const unsigned int uuids_num;
    const unsigned int cfg_flags;
};

#define UIRQ_INITIALIZER(nm, uu, uunum, cfg) \
    { .name = nm, .uuids = uu, .uuids_num = uunum, .cfg_flags = cfg, }

/**
 * uirq_register_sw_irq() - register SW IRQ backed UIRQ object
 * @uirq:    pointer to &struct uirq describing object to create
 * @ops:     pointer to &struct event_source_ops
 * @ops_arg: an optional argument that will be passed to callbacks
 *           specified by @ops parameter
 * @ph:      pointer to &struct handle to return handle to caller
 *
 * SW IRQ is not backed by normal interrupt but rather some sort of
 * software object that implements &struct event_source_ops ops
 *
 * Return: 0 on success, negative error otherwise
 */
int uirq_register_sw_irq(const struct uirq* uirq,
                         const struct event_source_ops* ops,
                         const void* ops_arg,
                         struct handle** ph);

/**
 * uirq_register_hw_irq() - register hardware interrupt backed UIRQ object
 * @vector:  hardware interrupt
 * @uirq:    pointer to &struct uirq describing object to create
 * @ph:      pointer to &struct handle to return handle to caller
 *
 * Return: 0 on success, negative error otherwise
 */
int uirq_register_hw_irq(unsigned int vector,
                         const struct uirq* uirq,
                         struct handle** ph);
