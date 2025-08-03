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

#include <interface/spi/spi.h>
#include <lib/spi/common/utils.h>
#include <lk/compiler.h>

__BEGIN_CDECLS

/**
 * struct spi_dev_ctx - opaque SPI device context structure
 */
struct spi_dev_ctx;

/**
 * spi_batch_state - tracks state associated with SPI batch being processed
 * @cs:       CS state resulting from the SPI batch
 * @num_cmds: number of commands successfully processed. Also corresponds to the
 *            index of the failed command if an error occurred.
 */
struct spi_batch_state {
    bool cs;
    size_t num_cmds;
};

/**
 * spi_srv_handle_batch() - handle batch of SPI requests
 * @spi:       handle to SPI device
 * @mb:        memory buffer containing the batch of SPI requests
 * @batch_req: metadata about the batch of SPI requests
 * @state:     keeps track of device state as the batch is being processed
 *
 * Return: 0 on success, negative error code otherwise
 */
int spi_srv_handle_batch(struct spi_dev_ctx* spi,
                         struct mem_buf* mb,
                         struct spi_batch_req* batch_req,
                         struct spi_batch_state* state);

__END_CDECLS
