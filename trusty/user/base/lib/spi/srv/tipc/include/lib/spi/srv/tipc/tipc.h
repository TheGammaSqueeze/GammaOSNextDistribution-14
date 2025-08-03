/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <lib/tipc/tipc_srv.h>
#include <lk/compiler.h>

__BEGIN_CDECLS

/**
 * add_spi_service() - Add new SPI service to service set
 * @hset:      pointer to handle set to add service to
 * @ports:     an array of &struct tipc_port describing ports for this
 *             service
 * @num_ports: number of ports in array pointed by @ports
 *
 * Caller must provide a pointer to &struct spi_dev_ctx as port-specific private
 * data in @priv field of &struct tipc_port.
 *
 * This routine can be called multiple times to register multiple services.
 *
 * Each port in @ports may have at most one active connection.
 *
 * Return: 0 on success, negative error code otherwise
 */
int add_spi_service(struct tipc_hset* hset,
                    const struct tipc_port* ports,
                    size_t num_ports);

__END_CDECLS
