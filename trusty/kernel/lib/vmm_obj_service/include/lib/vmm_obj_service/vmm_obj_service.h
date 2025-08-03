/*
 * Copyright (c) 2022, Google Inc. All rights reserved
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

#include <kernel/vm.h>
#include <lib/ktipc/ktipc.h>
#include <lk/compiler.h>

__BEGIN_CDECLS

struct vmm_obj_service;

/**
 * vmm_obj_service_create_ro() - Creates a new read-only vmm_obj_service.
 * @port: Name of the port for the new service.
 * @acl: ACLs for the service.
 * @obj: VMM object to serve over IPC.
 * @offset: Offset into @obj.
 * @size: Size of slice of @obj to serve.
 * @srv_out: Pointer to struct vmm_obj_service to receive
 *           the created object.
 *
 * @offset and @size must be multiples of the page size, otherwise
 * the function will return %ERR_INVALID_ARGS.
 *
 * The function increments the reference count of @obj.
 *
 * The protocol for services created with this function works as follows:
 * * The client opens a connection to the service,
 *   without sending anything
 * * The service immediately responds with a single message containing:
 *   * An 8-byte unsigned integer encoding the size of the object
 *   * A single memref handle for the underlying vmm_obj object
 * * The client can then close the connection; any further messages
 *   will cause the service to close the channel on its end.
 *
 * Return: %NO_ERROR in case of success, an error code otherwise.
 */
int vmm_obj_service_create_ro(const char* port,
                              const struct ktipc_port_acl* acl,
                              struct vmm_obj* obj,
                              size_t offset,
                              size_t size,
                              struct vmm_obj_service** srv_out);

/**
 * vmm_obj_service_destroy() - Destroys a vmm_obj_service created by
 *                             vmm_obj_service_create().
 * @srv: Pointer to a pointer to a service.
 *
 * This function destroys the service pointed to by the pointer at @srv
 * and then sets the pointer to %NULL to prevent double frees.
 */
void vmm_obj_service_destroy(struct vmm_obj_service** srv);

/**
 * vmm_obj_service_add() - Add a struct ktipc_port initialized by
 *                         vmm_obj_service_init() to a server.
 * @srv: Pointer to service.
 * @server: ktipc server object to add the new service to.
 *
 * The caller is responsible for calling ktipc_server_start() to
 * start @server. The caller must also preserve the lifetimes of
 * the port names and ACL structure as long as the service is alive.
 *
 * Return: %NO_ERROR in case of success, an error code otherwise.
 */
int vmm_obj_service_add(struct vmm_obj_service* srv,
                        struct ktipc_server* server);

__END_CDECLS
