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

#include <stdbool.h>
#include <stddef.h>

struct spi_dev_ctx;

/**
 * struct spi_bus_ctx - context structure for SPI devices
 * @owner:    pointer to &struct spi_dev_ctx currently active on this bus
 * @num_devs: number of SPI devices attached to this bus
 */
struct spi_bus_ctx {
    struct spi_dev_ctx* owner;
    size_t num_devs;
};

/**
 * struct spi_seq_entry - individual entry in a sequence of SPI requests
 * @exec: is invoked when SPI sequence is committed
 * @priv: command-specific private data
 *
 * Sequence of SPI requests is represented by an array of &struct spi_seq_entry,
 * with each SPI request saved in a &struct spi_seq_entry.
 *
 * Implementing spi_seq_*() is not required by SPI driver interface and is not
 * strictly necessary for this software SPI device. We implement this behavior
 * for testing purposes.
 */
struct spi_seq_entry {
    void (*exec)(struct spi_dev_ctx* dev, void* priv);
    void* priv;
};

/**
 * struct spi_dev_ctx - context structure for SPI devices
 * @bus:      pointer to &struct spi_bus_ctx that this device is attached to
 * @cmds:     pointer to an array of &struct spi_seq_entry representing a
 *            sequence of SPI requests
 * @num_cmds: number of SPI commands in array pointed to by @cmds
 * @curr_cmd: index of SPI command to be filled out
 * @loopback: whether this is a loopback device or not
 */
struct spi_dev_ctx {
    struct spi_bus_ctx* bus;
    struct spi_seq_entry* cmds;
    size_t num_cmds;
    size_t curr_cmd;
    bool loopback;
};
