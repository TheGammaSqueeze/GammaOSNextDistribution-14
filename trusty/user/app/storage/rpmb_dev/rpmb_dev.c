/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "rpmb_dev.h"

#include <assert.h>
#include <errno.h>
#include <lk/compiler.h>
#include <openssl/hmac.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>

/* verbose is an int for getopt */
int verbose = false;

#if OPENSSL_VERSION_NUMBER < 0x10100000L

HMAC_CTX* HMAC_CTX_new(void) {
    HMAC_CTX* ctx = malloc(sizeof(*ctx));
    if (ctx != NULL) {
        HMAC_CTX_init(ctx);
    }
    return ctx;
}

void HMAC_CTX_free(HMAC_CTX* ctx) {
    if (ctx != NULL) {
        HMAC_CTX_cleanup(ctx);
        free(ctx);
    }
}

#endif

/* TODO: move to common location */
static int rpmb_mac(struct rpmb_key key,
                    struct rpmb_packet* packet,
                    size_t packet_count,
                    struct rpmb_key* mac) {
    size_t i;
    int hmac_ret;
    unsigned int md_len;
    HMAC_CTX* hmac_ctx;

    hmac_ctx = HMAC_CTX_new();
    hmac_ret = HMAC_Init_ex(hmac_ctx, &key, sizeof(key), EVP_sha256(), NULL);
    if (!hmac_ret) {
        fprintf(stderr, "HMAC_Init_ex failed\n");
        goto err;
    }
    for (i = 0; i < packet_count; i++) {
        hmac_ret = HMAC_Update(hmac_ctx, packet[i].data, 284);
        if (!hmac_ret) {
            fprintf(stderr, "HMAC_Update failed\n");
            goto err;
        }
    }
    hmac_ret = HMAC_Final(hmac_ctx, mac->byte, &md_len);
    if (md_len != sizeof(mac->byte)) {
        fprintf(stderr, "bad md_len %d != %zd\n", md_len, sizeof(mac->byte));
        exit(1);
    }
    if (!hmac_ret) {
        fprintf(stderr, "HMAC_Final failed\n");
        goto err;
    }

err:
    HMAC_CTX_free(hmac_ctx);
    return hmac_ret ? 0 : -1;
}

static int rpmb_file_seek(struct rpmb_dev_state* s, uint16_t addr) {
    int ret;
    int pos = addr * RPMB_PACKET_DATA_SIZE + sizeof(s->header);
    ret = lseek(s->data_fd, pos, SEEK_SET);
    if (ret != pos) {
        fprintf(stderr, "rpmb_dev: seek to %d failed, got %d\n", pos, ret);
        return -1;
    }
    return 0;
}

static uint16_t rpmb_dev_program_key(struct rpmb_dev_state* s) {
    int ret;

    if (s->header.key_programmed) {
        return RPMB_RES_WRITE_FAILURE;
    }

    s->header.key = s->cmd[0].key_mac;
    s->header.key_programmed = 1;

    ret = lseek(s->data_fd, 0, SEEK_SET);
    if (ret) {
        fprintf(stderr, "rpmb_dev: Failed to seek rpmb data file\n");
        return RPMB_RES_WRITE_FAILURE;
    }

    ret = write(s->data_fd, &s->header, sizeof(s->header));
    if (ret != sizeof(s->header)) {
        fprintf(stderr, "rpmb_dev: Failed to write rpmb key: %d, %s\n", ret,
                strerror(errno));

        return RPMB_RES_WRITE_FAILURE;
    }

    return RPMB_RES_OK;
}

static uint16_t rpmb_dev_get_counter(struct rpmb_dev_state* s) {
    if (s->fail_next_get_counters > 0) {
        if (verbose) {
            fprintf(stderr,
                    "rpmb_dev: failing to get RPMB counter as requested by debug state\n");
        }
        s->fail_next_get_counters--;
        return RPMB_RES_COUNT_FAILURE;
    }

    s->res[0].write_counter = rpmb_u32(s->header.write_counter);

    return RPMB_RES_OK;
}

static uint16_t rpmb_dev_data_write(struct rpmb_dev_state* s) {
    uint16_t addr = rpmb_get_u16(s->cmd[0].address);
    uint16_t block_count = s->cmd_count;
    uint32_t write_counter;
    int ret;

    if (s->fail_next_writes > 0 && !s->commit_failed_writes) {
        if (verbose) {
            fprintf(stderr,
                    "rpmb_dev: failing write as requested by debug state\n");
        }
        s->fail_next_writes--;
        return RPMB_RES_WRITE_FAILURE;
    }

    if (s->header.write_counter == MAX_WRITE_COUNTER) {
        if (verbose) {
            fprintf(stderr, "rpmb_dev: Write counter expired\n");
        }
        return RPMB_RES_WRITE_FAILURE;
    }

    write_counter = rpmb_get_u32(s->cmd[0].write_counter);
    if (s->header.write_counter != write_counter) {
        if (verbose) {
            fprintf(stderr,
                    "rpmb_dev: Invalid write counter %u. Expected: %u\n",
                    write_counter, s->header.write_counter);
        }
        return RPMB_RES_COUNT_FAILURE;
    }

    ret = rpmb_file_seek(s, addr);
    if (ret) {
        fprintf(stderr, "rpmb_dev: Failed to seek rpmb data file\n");
        return RPMB_RES_WRITE_FAILURE;
    }

    for (int i = 0; i < block_count; i++) {
        ret = write(s->data_fd, s->cmd[i].data, RPMB_PACKET_DATA_SIZE);
        if (ret != RPMB_PACKET_DATA_SIZE) {
            fprintf(stderr,
                    "rpmb_dev: Failed to write rpmb data file: %d, %s\n", ret,
                    strerror(errno));
            return RPMB_RES_WRITE_FAILURE;
        }
    }

    s->header.write_counter++;

    if (s->fail_next_writes > 0) {
        if (verbose) {
            fprintf(stderr,
                    "rpmb_dev: Failing write after commit as requested by debug state\n");
        }
        s->fail_next_writes--;
        return RPMB_RES_WRITE_FAILURE;
    }

    ret = lseek(s->data_fd, 0, SEEK_SET);
    if (ret) {
        fprintf(stderr, "rpmb_dev: Failed to seek rpmb data file\n");
        return RPMB_RES_WRITE_FAILURE;
    }

    ret = write(s->data_fd, &s->header.write_counter,
                sizeof(s->header.write_counter));
    if (ret != sizeof(s->header.write_counter)) {
        fprintf(stderr,
                "rpmb_dev: Failed to write rpmb write counter: %d, %s\n", ret,
                strerror(errno));

        return RPMB_RES_WRITE_FAILURE;
    }

    s->res[0].write_counter = rpmb_u32(s->header.write_counter);
    return RPMB_RES_OK;
}

static uint16_t rpmb_dev_data_read(struct rpmb_dev_state* s) {
    uint16_t addr;
    uint16_t block_count;
    int ret;

    if (s->fail_next_reads > 0) {
        if (verbose) {
            fprintf(stderr,
                    "rpmb_dev: failing read as requested by debug state\n");
        }
        s->fail_next_reads--;
        return RPMB_RES_READ_FAILURE;
    }

    addr = rpmb_get_u16(s->cmd[0].address);
    block_count = s->res_count;

    rpmb_file_seek(s, addr);

    for (int i = 0; i < block_count; i++) {
        ret = read(s->data_fd, s->res[i].data, RPMB_PACKET_DATA_SIZE);
        if (ret != 0 && ret != RPMB_PACKET_DATA_SIZE) {
            fprintf(stderr, "rpmb_dev: Failed to read rpmb data file: %d, %s\n",
                    ret, strerror(errno));
            return RPMB_RES_READ_FAILURE;
        }
    }

    return RPMB_RES_OK;
}

struct rpmb_dev_cmd {
    uint16_t (*func)(struct rpmb_dev_state* s);
    uint16_t resp;
    bool key_mac_is_key;
    bool check_mac;
    bool check_result_read;
    bool check_key_programmed;
    bool check_addr;
    bool multi_packet_cmd;
    bool multi_packet_res;
    bool res_mac;
};

static struct rpmb_dev_cmd rpmb_dev_cmd_table[] = {
        [RPMB_REQ_PROGRAM_KEY] =
                {
                        .func = rpmb_dev_program_key,
                        .resp = RPMB_RESP_PROGRAM_KEY,
                        .key_mac_is_key = true,
                        .check_result_read = true,
                },
        [RPMB_REQ_GET_COUNTER] =
                {
                        .func = rpmb_dev_get_counter,
                        .resp = RPMB_RESP_GET_COUNTER,
                        .check_key_programmed = true,
                        .res_mac = true,
                },
        [RPMB_REQ_DATA_WRITE] =
                {
                        .func = rpmb_dev_data_write,
                        .resp = RPMB_RESP_DATA_WRITE,
                        .check_mac = true,
                        .check_result_read = true,
                        .check_key_programmed = true,
                        .check_addr = true,
                        .multi_packet_cmd = true,
                        .res_mac = true,
                },
        [RPMB_REQ_DATA_READ] =
                {
                        .func = rpmb_dev_data_read,
                        .resp = RPMB_RESP_DATA_READ,
                        .check_key_programmed = true,
                        .check_addr = true,
                        .multi_packet_res = true,
                        .res_mac = true,
                },
};

void rpmb_dev_process_cmd(struct rpmb_dev_state* s) {
    assert(s->cmd_count > 0);
    assert(s->res_count > 0);
    uint16_t req_resp = rpmb_get_u16(s->cmd[0].req_resp);
    uint16_t addr = rpmb_get_u16(s->cmd[0].address);
    uint16_t sub_req;
    uint16_t cmd_index = req_resp < countof(rpmb_dev_cmd_table) ? req_resp : 0;
    struct rpmb_dev_cmd* cmd = &rpmb_dev_cmd_table[cmd_index];
    uint16_t result = RPMB_RES_GENERAL_FAILURE;
    struct rpmb_key mac;
    uint16_t block_count = 0;

    if (cmd->check_result_read) {
        sub_req = rpmb_get_u16(s->cmd[s->cmd_count - 1].req_resp);
        if (sub_req != RPMB_REQ_RESULT_READ) {
            if (verbose) {
                fprintf(stderr,
                        "rpmb_dev: Request %d, missing result read request, got %d, cmd_count %d\n",
                        req_resp, sub_req, s->cmd_count);
            }
            goto err;
        }
        assert(s->cmd_count > 1);
        s->cmd_count--;
    }

    if (cmd->check_mac) {
        if (rpmb_mac(s->header.key, s->cmd, s->cmd_count, &mac) != 0) {
            fprintf(stderr, "rpmb_dev: failed to caclulate mac\n");
            goto err;
        }
    } else if (cmd->key_mac_is_key) {
        mac = s->cmd[s->cmd_count - 1].key_mac;
    } else {
        memset(mac.byte, 0, sizeof(mac.byte));
    }

    if (memcmp(&mac, s->cmd[s->cmd_count - 1].key_mac.byte, sizeof(mac))) {
        if (verbose) {
            fprintf(stderr, "rpmb_dev: Request %d, invalid MAC, cmd_count %d\n",
                    req_resp, s->cmd_count);
        }
        if (cmd->check_mac) {
            result = RPMB_RES_AUTH_FAILURE;
        }
        goto err;
    }

    if (cmd->multi_packet_cmd) {
        block_count = s->cmd_count;
    }
    if (cmd->multi_packet_res) {
        block_count = s->res_count;
    }

    if (cmd->check_addr && (addr + block_count > s->header.max_block + 1)) {
        if (verbose) {
            fprintf(stderr,
                    "rpmb_dev: Request %d, invalid addr: 0x%x count 0x%x, Out of bounds. Max addr 0x%x\n",
                    req_resp, addr, block_count, s->header.max_block + 1);
        }
        result = RPMB_RES_ADDR_FAILURE;
        goto err;
    }
    if (!cmd->check_addr && addr) {
        if (verbose) {
            fprintf(stderr, "rpmb_dev: Request %d, invalid addr: 0x%x != 0\n",
                    req_resp, addr);
        }
        goto err;
    }

    for (int i = 1; i < s->cmd_count; i++) {
        sub_req = rpmb_get_u16(s->cmd[i].req_resp);
        if (sub_req != req_resp) {
            if (verbose) {
                fprintf(stderr,
                        "rpmb_dev: Request %d, sub-request mismatch, %d, at %d\n",
                        req_resp, i, sub_req);
            }
            goto err;
        }
    }
    if (!cmd->multi_packet_cmd && s->cmd_count != 1) {
        if (verbose) {
            fprintf(stderr,
                    "rpmb_dev: Request %d, bad cmd count %d, expected 1\n",
                    req_resp, s->cmd_count);
        }
        goto err;
    }
    if (!cmd->multi_packet_res && s->res_count != 1) {
        if (verbose) {
            fprintf(stderr,
                    "rpmb_dev: Request %d, bad res count %d, expected 1\n",
                    req_resp, s->res_count);
        }
        goto err;
    }

    if (cmd->check_key_programmed && !s->header.key_programmed) {
        if (verbose) {
            fprintf(stderr, "rpmb_dev: Request %d, key is not programmed\n",
                    req_resp);
        }
        s->res[0].result = rpmb_u16(RPMB_RES_NO_AUTH_KEY);
        return;
    }

    if (!cmd->func) {
        if (verbose) {
            fprintf(stderr, "rpmb_dev: Unsupported request: %d\n", req_resp);
        }
        goto err;
    }

    result = cmd->func(s);

err:
    if (s->header.write_counter == MAX_WRITE_COUNTER) {
        result |= RPMB_RES_WRITE_COUNTER_EXPIRED;
    }

    for (int i = 0; i < s->res_count; i++) {
        s->res[i].nonce = s->cmd[0].nonce;
        s->res[i].address = rpmb_u16(addr);
        s->res[i].block_count = rpmb_u16(block_count);
        s->res[i].result = rpmb_u16(result);
        s->res[i].req_resp = rpmb_u16(cmd->resp);
    }
    if (cmd->res_mac) {
        rpmb_mac(s->header.key, s->res, s->res_count,
                 &s->res[s->res_count - 1].key_mac);
    }
}
