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

#include <lib/coverage/common/cov_shm.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/list.h>
#include <stddef.h>
#include <string.h>
#include <trusty/uuid.h>

/* Assume we have no more than 256 TAs running at the same time */
#define MAX_NUM_APPS 256

/**
 * struct srv_state - global state of the coverage server
 * @hset:                 handle set
 * @coverage_record_list: list of coverage records
 * @mailbox:              mailbox used to broadcast events
 */
struct srv_state {
    struct tipc_hset* hset;
    /*
     * TODO: A hash map would be nice. But we only have a list implementation
     * that's readily available. It should be good enough though, since the list
     * is short (<100) and lookups/insertions are very infrequent.
     */
    struct list_node coverage_record_list;
    struct cov_shm mailbox;
};

static inline void set_srv_state(struct tipc_port* port,
                                 struct srv_state* state) {
    port->priv = state;
}

static inline struct srv_state* get_srv_state(const struct tipc_port* port) {
    return (struct srv_state*)(port->priv);
}

/**
 * struct coverage_record - code coverage record about a given TA
 * @node:       list node
 * @uuid:       UUID of target TA
 * @idx:        unique index assigned to this record and corresponding TA
 * @data:       shared memory region holding the coverage record
 * @record_len: length of coverage record within @data
 */
struct coverage_record {
    struct list_node node;
    struct uuid uuid;
    size_t idx;
    struct cov_shm data;
    size_t record_len;
};

static inline bool equal_uuid(const struct uuid* a, const struct uuid* b) {
    return memcmp(a, b, sizeof(struct uuid)) == 0;
}

static inline struct coverage_record* find_coverage_record(
        struct list_node* head,
        const struct uuid* uuid) {
    struct coverage_record* record;

    list_for_every_entry(head, record, struct coverage_record, node) {
        if (equal_uuid(&record->uuid, uuid)) {
            return record;
        }
    }
    return NULL;
}

/**
 * coverage_aggregator_init() - initialize coverage aggregator service
 * @state: pointer to global &struct srv_state
 *
 * Return: 0 on success, negative error code on error
 */
int coverage_aggregator_init(struct srv_state* state);

/**
 * coverage_client_init() - initialize coverage client service
 * @state: pointer to global &struct srv_state
 *
 * Return: 0 on success, negative error code on error
 */
int coverage_client_init(struct srv_state* state);
