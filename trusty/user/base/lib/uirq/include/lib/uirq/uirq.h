/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <trusty_ipc.h>

/**
 * DOC: Theory of Operation
 *
 * UIRQ is a kernel object (identified by name non-empty string) that is
 * designed to expose hardware or logical software interrupts (interrupt
 * source) to a user space applications where it is represented as a UIRQ
 * handle.
 *
 * An application obtains UIRQ handle by invoking uirq_open() routine.
 * Successfully obtaining handle unmasks underlying interrupt source making it
 * possible to generate interrupts.
 *
 * An application can wait on UIRQ handle using standard wait() call to
 * get notified when underlying interrupt fires. When underlying interrupt
 * fires it gets masked and a notification is delivered to user space as
 * an event.
 *
 * In response for receiving interrupt notification an application should
 * perform appropriate actions to handle it and then invoke uirq_ack_handled()
 * routine to indicate that interrupt source can be unmasked again preparing
 * for next interrupt delivery.
 *
 * The same UIRQ object can be opened multiple times by the same or different
 * applications. In such case, an interrupt notification event will be
 * delivered to every UIRQ handle instance and must be handled independently.
 * The underlying interrupt source will be unmasked only when all clients has
 * completed their interrupt handling.
 *
 * An application can invoke close() call to dispose a UIRQ handle. When
 * the last instance of UIRQ handle is closed the underlying interrupt source
 * gets masked preventing it from generating interrupts.
 */

/**
 * uirq_open() - open UIRQ object
 * @name: UIRQ object to open
 * @flags: reserved must be 0
 *
 * Return: handle of UIRQ object on success, negative error code otherwise.
 */
handle_t uirq_open(const char* name, uint32_t flags);

/**
 * uirq_ack_handled() - ACK interrupt
 * @h: uirq handle returned by uirq_open() call
 *
 * Return: 0 on success, negative error code otherwise.
 */
int uirq_ack_handled(handle_t h);
