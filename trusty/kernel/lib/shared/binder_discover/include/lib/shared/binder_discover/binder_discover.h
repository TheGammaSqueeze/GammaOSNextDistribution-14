/*
 * Copyright (c) 2022, Google, Inc. All rights reserved
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

#include <binder/IBinder.h>
#include <lk/compiler.h>

/**
 * binder_discover_get_service() - Get the root binder for a port from
 *                                 the discovery service.
 * @port: Port to retrieve the binder for.
 * @ib: Reference to store the binder into.
 *
 * Return: %OK in case of success, error code otherwise.
 */
int binder_discover_get_service(const char* port,
                                android::sp<android::IBinder>& ib);

/**
 * binder_discover_add_service() - Add a binder to the discovery service.
 * @port: Port for the new binder.
 * @ib: Root binder for the service.
 *
 * Return: %OK in case of success, %ALREADY_EXISTS if the port name has already
 *         been added, another error code otherwise.
 */
int binder_discover_add_service(const char* port,
                                const android::sp<android::IBinder>& ib);

/**
 * binder_discover_remove_service() - Remove a binder from the discovery
 *                                    service.
 * @port: Port for binder to remove.
 *
 * Return: %OK in case of success, %NAME_NOT_FOUND if the port
 *         has not been added previously, error code otherwise.
 */
int binder_discover_remove_service(const char* port);
