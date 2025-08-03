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

#include <assert.h>
#include <lk/compiler.h>
#include <stdint.h>

__BEGIN_CDECLS

/* Alignment for structures sent to SPI server via shared memory */
#define SPI_CMD_SHM_ALIGN 8

/**
 * enum spi_srv_err - collection of error codes returned by SPI server
 * @SPI_SRV_NO_ERROR:            all OK
 * @SPI_SRV_ERR_GENERIC:         unknown error. Can occur when there's an
 *                               internal server error
 * @SPI_SRV_ERR_INVALID_ARGS:    invalid arguments in the request
 * @SPI_SRV_ERR_NOT_SUPPORTED:   command operation not supported in the
 *                               request
 * @SPI_SRV_ERR_NOT_IMPLEMENTED: requested command not implemented
 * @SPI_SRV_ERR_BUSY:            pending request for this channel
 * @SPI_SRV_ERR_TOO_BIG:         not enough space to handle request
 */
enum spi_srv_err {
    SPI_SRV_NO_ERROR = 0,
    SPI_SRV_ERR_GENERIC,
    SPI_SRV_ERR_INVALID_ARGS,
    SPI_SRV_ERR_NOT_SUPPORTED,
    SPI_SRV_ERR_NOT_IMPLEMENTED,
    SPI_SRV_ERR_BUSY,
    SPI_SRV_ERR_TOO_BIG,
};

/**
 * enum spi_cmd_common - common command identifiers for SPI operations
 * @SPI_CMD_RESP_BIT_SHIFT: response bit shift
 * @SPI_CMD_RESP_BIT:       response bit set as part of response
 * @SPI_CMD_OP_SHIFT:       operation bit shift
 * @SPI_CMD_OP_MASK:        operation mask bit
 */
enum spi_cmd_common {
    SPI_CMD_RESP_BIT_SHIFT = 0,
    SPI_CMD_RESP_BIT = (0x1u << SPI_CMD_RESP_BIT_SHIFT),
    SPI_CMD_OP_SHIFT = 1,
    SPI_CMD_OP_MASK = (0x7Fu << SPI_CMD_OP_SHIFT),
};

/**
 * enum spi_cmd_msg - command identifiers for operations sent as TIPC messages
 * @SPI_CMD_MSG_OP_SHM_MAP:    operation code for mapping shared memory
 * @SPI_CMD_MSG_OP_BATCH_EXEC: operation code for execution of a batch of
 *                             commands
 */
enum spi_cmd_msg {
    SPI_CMD_MSG_OP_SHM_MAP = (0x1u << SPI_CMD_OP_SHIFT),
    SPI_CMD_MSG_OP_BATCH_EXEC = (0x2u << SPI_CMD_OP_SHIFT),
};

/**
 * enum spi_cmd_shm - command identifiers for operations sent in shared memory
 * @SPI_CMD_SHM_OP_XFER:        operation code for data transfer
 * @SPI_CMD_SHM_OP_CS_ASSERT:   operation code for chip select assert
 * @SPI_CMD_SHM_OP_CS_DEASSERT: operation code for chip select deassert
 * @SPI_CMD_SHM_OP_SET_CLK:     operation code for setting SPI clock
 * @SPI_CMD_SHM_OP_DELAY:       operation code for delays between commands
 */
enum spi_cmd_shm {
    SPI_CMD_SHM_OP_XFER = (0x1u << SPI_CMD_OP_SHIFT),
    SPI_CMD_SHM_OP_CS_ASSERT = (0x2u << SPI_CMD_OP_SHIFT),
    SPI_CMD_SHM_OP_CS_DEASSERT = (0x3u << SPI_CMD_OP_SHIFT),
    SPI_CMD_SHM_OP_SET_CLK = (0x4u << SPI_CMD_OP_SHIFT),
    SPI_CMD_SHM_OP_DELAY = (0x5u << SPI_CMD_OP_SHIFT),
};

/**
 * enum spi_xfer_flags - flag identifiers for data xfer operation
 * @SPI_XFER_FLAGS_TX: flag to indicate transmitting data
 * @SPI_XFER_FLAGS_RX: flag to indicate receiving data
 */
enum spi_xfer_flags {
    SPI_XFER_FLAGS_TX = (0x1u << 0),
    SPI_XFER_FLAGS_RX = (0x1u << 1),
};

/**
 * struct spi_msg_req - SPI request header for TIPC messages
 * @cmd: command identifier - one of &enum spi_cmd_msg
 */
struct spi_msg_req {
    uint32_t cmd;
};

/**
 * struct spi_msg_resp - SPI response header for TIPC messages
 * @cmd:    command identifier - %SPI_CMD_RESP_BIT or'ed with a cmd in
 *          one of &enum spi_cmd_msg
 * @status: response status of the SPI operation
 */
struct spi_msg_resp {
    uint32_t cmd;
    uint32_t status;
};

/**
 * struct spi_shm_hdr - SPI header for commands in shared memory
 * @cmd:    command identifier. Requests contain one of &enum spi_cmd_shm.
 *          Server responses contain one of &enum spi_cmd_shm or'ed with
 *          %SPI_CMD_RESP_BIT.
 * @status: response status of the SPI operation
 */
struct spi_shm_hdr {
    uint32_t cmd;
    uint32_t status;
};

/**
 * struct spi_shm_map_req - arguments for %SPI_CMD_MSG_OP_SHM_MAP request
 * @len: length of shared memory region in bytes, must be page-aligned
 */
struct spi_shm_map_req {
    uint32_t len;
};

/**
 * struct spi_batch_req - arguments for %SPI_CMD_MSG_OP_BATCH_EXEC request
 * @len: total length of SPI requests, arguments, and data
 * @num_cmds: number of commands in the batch
 */
struct spi_batch_req {
    uint32_t len;
    uint32_t num_cmds;
};

/**
 * struct spi_batch_resp - arguments for %SPI_CMD_MSG_OP_BATCH_EXEC response
 * @len:    total length of SPI responses, arguments, and data
 * @failed: index of failed command if an error occurred
 */
struct spi_batch_resp {
    uint32_t len;
    uint32_t failed;
};

/**
 * struct spi_xfer_args - arguments for %SPI_CMD_SHM_OP_XFER request and
 *                        response
 * @len:   data length in bytes
 * @flags: configuration flags - see &enum spi_xfer_flags
 *
 * @len bytes of data is allocated directly after &struct spi_xfer_args in
 * shared memory. TX and RX buffers, configured by @flags, are always set to use
 * that data (i.e. they may point to the same location). If neither TX nor RX
 * are configured, data is still allocated, but not used.
 *
 * @len also controls the number of clock cycles sent the SPI bus. If the
 * word-size is a multiple of 8 bits, the number of SPI clock cycles are
 * round_up(@len * 8, word-size). Otherwise, details TBD.
 */
struct spi_xfer_args {
    uint32_t len;
    uint32_t flags;
};

/**
 * struct spi_clk_args - arguments for %SPI_CMD_SHM_OP_CLK request and response
 * @clk_hz: SPI clock speed, in Hz. Request contains clock speed requested by
 *          the client. Response contains actual clock speed that was set.
 */
struct spi_clk_args {
    uint64_t clk_hz;
};

/**
 * struct spi_clk_args - arguments for %SPI_CMD_SHM_OP_DELAY request and
 *                       response
 * @delay_ns: delay, in ns. Request contains amount of delay time requested by
 *            the client. Response must be zero.
 */
struct spi_delay_args {
    uint64_t delay_ns;
};
__END_CDECLS
