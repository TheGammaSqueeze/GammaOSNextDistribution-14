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
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <trusty_ipc.h>

__BEGIN_CDECLS

/**
 * struct spi_dev - SPI device tracking structure
 * @h:                 SPI server connection handle
 * @shm:               state of memory region shared with SPI server
 * @max_num_cmds:      maximum number of commands in one batch
 * @num_cmds:          number of commands in the current batch
 * @max_total_payload: maximum total number of bytes of payload in one batch
 * @total_payload:     total number of bytes of payload in the current batch
 * @config_err:        whether an error occurred during command configuration
 *
 * All members of this structure should be considered private
 */
struct spi_dev {
    handle_t h;
    struct mem_buf shm;
    size_t max_num_cmds;
    size_t num_cmds;
    size_t max_total_payload;
    size_t total_payload;
    bool config_err;
};

/**
 * spi_dev_open() - open specified SPI device
 * @dev:               pointer to &struct spi_dev
 * @name:              name of device to open
 * @max_num_cmds:      maximum number of commands in one batch
 * @max_total_payload: maximum total payload size
 *
 * Open connection to specified device service, configure command batch limits
 * and establish a shared memory region with SPI server
 *
 * Return: 0 on success, negative error code on error
 */
int spi_dev_open(struct spi_dev* dev,
                 const char* name,
                 size_t max_num_cmds,
                 size_t max_total_payload);
/**
 * TODO: We don't have a way to return dynamically allocated shared memory yet.
 * spi_dev_close() - close connection to specified SPI service
 * @dev: SPI device to close. It should be previously opened with
 *       spi_dev_open() call.
 *
 * void spi_dev_close(struct spi_dev* dev);
 */

/**
 * spi_clear_cmds() - clear SPI commands previously configured for a device
 * @dev: handle of SPI device previously opened with spi_dev_open()
 */
void spi_clear_cmds(struct spi_dev* dev);

/**
 * spi_exec_cmds() - execute series of SPI commands
 * @dev:    handle of SPI device previously opened with spi_dev_open()
 * @failed: points to location to store index of failed command if an error
 *          occurred.
 *
 * This routine implicitly clears all SPI commands. See spi_clear_cmds().
 * If an error occurs, @failed param is set the index of failed command. If
 * @failed is equal to the number of commands in the sent batch, that means that
 * the batch was successfully deserialized by SPI server, but failed to commit.
 *
 * Return: 0 if all command were successful, negative error code otherwise
 */
int spi_exec_cmds(struct spi_dev* dev, size_t* failed);

/**
 * spi_add_data_xfer_cmd() - configure SPI data command
 * @dev: handle of SPI device previously opened with spi_dev_open()
 * @tx:  return pointer for data to send
 * @rx:  return pointer for buffer to store received data
 * @len: number of bytes to send/receive
 *
 * This routine configures command to exchange data with specified device.
 *
 * CS must be asserted for data transfer to succeed.
 *
 * Either @tx or @rx may be NULL to indicate that send-only or receive-only
 * operation is required. If both @tx and @rx are NULL, then @len bytes of data
 * is still allocated in shared memory, but not used. Both @tx and @rx may point
 * to the same memory location.
 *
 * @len also controls the number of clock cycles sent the SPI bus. If the
 * word-size is a multiple of 8 bits, the number of SPI clock cycles are
 * round_up(@len * 8, word-size). Otherwise, details TBD.
 *
 * If this routine fails, all subsequent calls to spi_exec_cmds() and
 * spi_add_*() routines will fail until spi_clear_cmds() is called.
 *
 * Return: 0 on success, negative error code otherwise.
 */
int spi_add_data_xfer_cmd(struct spi_dev* dev,
                          void** tx,
                          void** rx,
                          size_t len);

/**
 * spi_add_cs_assert_cmd() - configure SPI chip select assert command
 * @dev: handle of SPI device previously opened with spi_dev_open()
 *
 * This routine builds a command to assert chip select with
 * specified device.
 *
 * If this routine fails, all subsequent calls to spi_exec_cmds() and
 * spi_add_*() routines will fail until spi_clear_cmds() is called.
 *
 * Return: 0 on success, negative error code otherwise.
 */
int spi_add_cs_assert_cmd(struct spi_dev* dev);

/**
 * spi_add_cs_deassert_cmd() - configure SPI chip select deassert command
 * @dev: handle of SPI device previously opened with spi_dev_open()
 *
 * This routine builds a command to deassert chip select with
 * specified device.
 *
 * If this routine fails, all subsequent calls to spi_exec_cmds() and
 * spi_add_*() routines will fail until spi_clear_cmds() is called.
 *
 * Return: 0 on success, negative error code otherwise.
 */
int spi_add_cs_deassert_cmd(struct spi_dev* dev);

/**
 * spi_add_set_clk_cmd() - configure SPI set clock speed command
 * @dev:        handle of SPI device previously opened with spi_dev_open()
 * @clk_hz_in:  requested SPI clock speed, in Hz
 * @clk_hz_out: output pointer to actual SPI clock speed that was set, in Hz.
 *              Value is set after spi_exec_cmds() returns. Clients can pass
 *              %NULL if they don't need to know the actual clock rate used.
 *              Actual clock rate must be @clk_hz_in or less.
 *
 * This routine builds a command to set clock speed for specified device.
 *
 * If this routine fails, all subsequent calls to spi_exec_cmds() and
 * spi_add_*() routines will fail until spi_clear_cmds() is called.
 *
 * Return: 0 on success, or negative error code otherwise.
 */
int spi_add_set_clk_cmd(struct spi_dev* dev,
                        uint64_t clk_hz_in,
                        uint64_t** clk_hz_out);

/**
 * spi_add_set_delay_cmd() - configure SPI delay command
 * @dev:      handle of SPI device previously opened with spi_dev_open()
 * @delay_ns: amount of time to delay remaining SPI requests by, in ns
 *
 * This routine builds a command to delay remaining SPI requests for specified
 * device.
 *
 * There is no way to guarantee exact delays due to scheduling constraints.
 * Execution of this command is done on a best-effort basis. Actual delay time
 * must be larger than @delay_ns. If a SPI sequence fails due to unsatisfied
 * timing requirements, clients may retry.
 *
 * If this routine fails, all subsequent calls to spi_exec_cmds() and
 * spi_add_*() routines will fail until spi_clear_cmds() is called.
 *
 * Return: 0 on success, or negative error code otherwise.
 */
int spi_add_delay_cmd(struct spi_dev* dev, uint64_t delay_ns);

__END_CDECLS
